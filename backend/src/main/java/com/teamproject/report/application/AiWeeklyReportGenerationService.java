package com.teamproject.report.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.report.application.AiWeeklyReportAnalysisValidator.ValidationResult;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.port.AiWeeklyReportGateway;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import com.teamproject.common.exception.ApplicationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
    /** revision 번호 충돌은 동시 요청 수만큼만 일어난다. 몇 번이면 충분하고, 무한 재시도는 하지 않는다. */
    private static final int SAVE_ATTEMPTS = 3;
    /**
     * 같은 기간·언어로 만들 수 있는 revision 수. 명세 §15가 정한 값이다.
     * 한 기간을 반복해 다시 만드는 낭비만 막는다. 기간을 옮겨 가며 부르는 총 지출은
     * 이 상한으로 묶이지 않는다. 그쪽은 토큰 사용량이 쌓인 뒤 정한다.
     */
    private static final int MAX_REVISIONS_PER_PERIOD = 3;

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

    /**
     * @param sourceChanged 저장본을 재사용할 때, 그 이후 업무 데이터가 바뀌었는지.
     *                      새로 만든 경우에는 의미가 없으므로 false다.
     */
    public record GenerationResult(AiWeeklyReportRevision revision, boolean createdNew,
            boolean sourceChanged) {

        static GenerationResult created(AiWeeklyReportRevision revision) {
            return new GenerationResult(revision, true, false);
        }

        static GenerationResult reused(AiWeeklyReportRevision revision, boolean sourceChanged) {
            return new GenerationResult(revision, false, sourceChanged);
        }
    }

    public AiWeeklyReportRevision generate(AiWeeklyReportSnapshotV1 rawSnapshot, GenerateCommand command) {
        return generateResult(rawSnapshot, command).revision();
    }

    /**
     * 트랜잭션을 걸지 않는다. 명세 §14는 OpenAI 호출 동안 DB transaction과 connection을 잡지
     * 말라고 정한다. 전에는 이 메서드 전체가 하나의 트랜잭션이라 30초짜리 외부 호출이 끝날
     * 때까지 커넥션을 물고 있었고, 동시 생성 몇 건이면 풀이 마른다.
     *
     * <p>여기서 트랜잭션을 열지 않으므로 DB 작업은 Spring Data repository가 호출마다 짧은
     * 트랜잭션으로 처리하고 곧바로 커넥션을 돌려준다. 이 클래스 안에서 @Transactional을 다시
     * 붙여도 자기 호출이라 프록시를 타지 않는다.
     */
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

        // 3-0. 유료 호출이다. 같은 기간을 무한히 다시 만들지 못하게 막는다. 프런트의 재생성
        // 확인 모달은 클라이언트 쪽 제동일 뿐이라 API를 직접 부르면 우회된다.
        if (command.regenerate()) {
            int made = revisionRepository.findMaxRevision(command.groupId(), command.periodFrom(),
                    command.periodToExclusive(), command.language()).orElse(0);
            if (made >= MAX_REVISIONS_PER_PERIOD) {
                throw new ApplicationException("AI_REPORT_WEEKLY_LIMIT", HttpStatus.CONFLICT,
                        "같은 기간의 AI 리포트는 " + MAX_REVISIONS_PER_PERIOD + "회까지 생성할 수 있습니다.");
            }
        }

        // 3. Deduplication check if regenerate=false
        if (!command.regenerate()) {
            Optional<AiWeeklyReportRevision> existing = findLatest(command);
            if (existing.isPresent()) {
                // 지문은 방금 계산했다. 저장본의 것과 비교하면 그 뒤 업무가 바뀌었는지 알 수
                // 있다. 유료 재생성을 사용자가 감으로 정하지 않게 이 사실을 함께 돌려준다.
                boolean changed = !fingerprint.equals(existing.get().getSourceFingerprint());
                return GenerationResult.reused(existing.get(), changed);
            }
        }

        // 5. OpenAI Gateway call with fallback safety
        // revision 번호는 호출 뒤에 정한다. 30초 걸리는 외부 호출 앞에서 잡아 두면
        // 그 사이에 다른 요청이 같은 번호를 먼저 저장한다.
        AiWeeklyReportAnalysisV1 analysis;
        String mode = "OPENAI";
        Integer inputTokens = null;
        Integer outputTokens = null;

        try {
            AiWeeklyReportGateway.Analysis answer = gateway.analyze(snapshot);
            analysis = answer.analysis();
            inputTokens = answer.inputTokens();
            outputTokens = answer.outputTokens();
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

        return saveNewRevision(command, mode, fingerprint, snapshotJson, analysisJson, inputTokens, outputTokens);
    }

    /**
     * revision 번호는 (group, period, language) 안에서 유니크하다. 잠금이 없으므로 두 요청이
     * 같은 번호를 계산할 수 있고, 그때 뒤늦은 쪽이 제약 위반으로 터진다. 외부 호출이 이미
     * 끝난 뒤라 유료 결과를 버리고 500을 주게 된다. 번호를 다시 세어 몇 번 재시도한다.
     *
     * <p>OpenAI 호출은 이 밖에서 끝났다. 30초짜리 외부 호출을 DB 경합 구간에 두지 않는다.
     */
    private GenerationResult saveNewRevision(GenerateCommand command, String mode,
            String fingerprint, String snapshotJson, String analysisJson,
            Integer inputTokens, Integer outputTokens) {
        for (int attempt = 1; attempt <= SAVE_ATTEMPTS; attempt++) {
            // 호출 중에 다른 요청이 먼저 저장했을 수 있다. 재생성을 고르지 않았다면 그것을 쓴다.
            if (!command.regenerate()) {
                Optional<AiWeeklyReportRevision> concurrent = findLatest(command);
                if (concurrent.isPresent()) {
                    return GenerationResult.reused(concurrent.get(), false);
                }
            }

            int nextRevision = revisionRepository.findMaxRevision(
                    command.groupId(), command.periodFrom(), command.periodToExclusive(),
                    command.language()).orElse(0) + 1;

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
                    inputTokens,
                    outputTokens,
                    now,
                    now
            );

            try {
                return GenerationResult.created(revisionRepository.saveAndFlush(newRevision));
            } catch (DataIntegrityViolationException collision) {
                log.warn("AI weekly report revision number collided, retrying: groupId={} period={}..{} revision={} attempt={}",
                        command.groupId(), command.periodFrom(), command.periodToExclusive(),
                        nextRevision, attempt);
                if (attempt == SAVE_ATTEMPTS) {
                    throw new ApplicationException("AI_REPORT_CONCURRENT_GENERATION",
                            HttpStatus.CONFLICT,
                            "같은 기간의 리포트가 동시에 생성되고 있습니다. 잠시 후 다시 시도해 주세요.");
                }
            }
        }
        throw new IllegalStateException("unreachable");
    }

    private Optional<AiWeeklyReportRevision> findLatest(GenerateCommand command) {
        return revisionRepository
                .findTopByGroupIdAndPeriodFromAndPeriodToExclusiveAndLanguageOrderByRevisionDesc(
                        command.groupId(), command.periodFrom(), command.periodToExclusive(),
                        command.language());
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
