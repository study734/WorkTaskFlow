package com.teamproject.report.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.report.application.AiWeeklyReportAnalysisValidator.ValidationResult;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.port.AiWeeklyReportGateway;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

/**
 * v7-2 AI 주간 리포트 생성 및 오케스트레이션 서비스 (M6).
 * Snapshot 조립, Policy Engine 위험 생성, OpenAI Gateway 호출, 서버 Fallback 전환,
 * 중복 생성 방지(source_fingerprint 기반) 및 V34 revision 저장 전체 라이프사이클을 관리한다.
 */
@Service
public class AiWeeklyReportGenerationService {

    private static final Logger log = LoggerFactory.getLogger(AiWeeklyReportGenerationService.class);

    private final AiWeeklyReportPolicyEngine policyEngine;
    private final AiWeeklyReportGateway gateway;
    private final AiWeeklyReportAnalysisValidator validator;
    private final AiWeeklyReportFallbackFactory fallbackFactory;
    private final AiWeeklyReportRevisionRepository revisionRepository;
    private final ObjectMapper objectMapper;

    public record GenerateCommand(
            Long groupId,
            LocalDate periodFrom,
            LocalDate periodToExclusive,
            String language,
            boolean regenerate,
            String promptVersion,
            String model
    ) {}

    public AiWeeklyReportGenerationService(
            AiWeeklyReportPolicyEngine policyEngine,
            AiWeeklyReportGateway gateway,
            AiWeeklyReportAnalysisValidator validator,
            AiWeeklyReportFallbackFactory fallbackFactory,
            AiWeeklyReportRevisionRepository revisionRepository,
            ObjectMapper objectMapper
    ) {
        this.policyEngine = policyEngine;
        this.gateway = gateway;
        this.validator = validator;
        this.fallbackFactory = fallbackFactory;
        this.revisionRepository = revisionRepository;
        this.objectMapper = objectMapper;
    }

    public record GenerationResult(AiWeeklyReportRevision revision, boolean createdNew) {}

    @Transactional
    public AiWeeklyReportRevision generate(AiWeeklyReportSnapshotV1 rawSnapshot, GenerateCommand command) {
        return generateResult(rawSnapshot, command).revision();
    }

    @Transactional
    public GenerationResult generateResult(AiWeeklyReportSnapshotV1 rawSnapshot, GenerateCommand command) {
        if (rawSnapshot == null || command == null) {
            throw new IllegalArgumentException("Snapshot and command must not be null");
        }

        // 1. Policy Engine execution
        AiWeeklyReportSnapshotV1 snapshot = policyEngine.evaluate(rawSnapshot);

        // 2. Serialize snapshot and compute fingerprint
        String snapshotJson;
        try {
            snapshotJson = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Snapshot serialization failed", e);
        }

        String fingerprint = computeFingerprint(snapshotJson, command.promptVersion(), command.model());

        // 3. Deduplication check if regenerate=false
        if (!command.regenerate()) {
            Optional<AiWeeklyReportRevision> existing = revisionRepository
                    .findTopByGroupIdAndPeriodFromAndPeriodToExclusiveAndLanguageOrderByRevisionDesc(
                            command.groupId(),
                            command.periodFrom(),
                            command.periodToExclusive(),
                            command.language()
                    );
            if (existing.isPresent()) {
                return new GenerationResult(existing.get(), false);
            }
        }

        // 4. Determine revision number
        int maxRevision = revisionRepository.findMaxRevision(
                command.groupId(),
                command.periodFrom(),
                command.periodToExclusive(),
                command.language()
        ).orElse(0);

        int nextRevision = maxRevision + 1;

        // 5. OpenAI Gateway call with fallback safety
        AiWeeklyReportAnalysisV1 analysis;
        String mode = "OPENAI";

        try {
            analysis = gateway.analyze(snapshot);
            ValidationResult validationResult = validator.validate(snapshot, analysis);
            if (!validationResult.valid()) {
                // 왜 fallback으로 내려갔는지 남기지 않으면 운영에서 원인을 찾을 방법이 없다.
                // 오류 문구는 ref와 코드만 담는다. 원문 제목·이름은 들어가지 않는다.
                log.warn("AI weekly report analysis rejected, using server fallback: groupId={} period={}..{} errors={}",
                        command.groupId(), command.periodFrom(), command.periodToExclusive(),
                        validationResult.errors());
                analysis = fallbackFactory.create(snapshot);
                mode = "SERVER_FALLBACK";
            }
        } catch (Exception ex) {
            log.warn("AI weekly report gateway call failed, using server fallback: groupId={} period={}..{} cause={}",
                    command.groupId(), command.periodFrom(), command.periodToExclusive(),
                    ex.getClass().getSimpleName());
            analysis = fallbackFactory.create(snapshot);
            mode = "SERVER_FALLBACK";
        }

        String analysisJson;
        try {
            analysisJson = objectMapper.writeValueAsString(analysis);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Analysis serialization failed", e);
        }

        LocalDateTime now = LocalDateTime.now();

        AiWeeklyReportRevision newRevision = new AiWeeklyReportRevision(
                command.groupId(),
                command.periodFrom(),
                command.periodToExclusive(),
                command.language(),
                nextRevision,
                "FINALIZED",
                mode,
                fingerprint,
                snapshotJson,
                analysisJson,
                command.promptVersion(),
                command.model(),
                null,
                null,
                now,
                now
        );

        return new GenerationResult(revisionRepository.save(newRevision), true);
    }

    public String computeFingerprint(String snapshotJson, String promptVersion, String model) {
        String content = snapshotJson + ":" + (promptVersion != null ? promptVersion : "") + ":" + (model != null ? model : "");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
