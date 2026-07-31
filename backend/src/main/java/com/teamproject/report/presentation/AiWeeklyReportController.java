package com.teamproject.report.presentation;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.report.application.AiWeeklyReportAccessService;
import com.teamproject.report.application.AiWeeklyReportGenerationService;
import com.teamproject.report.application.AiWeeklyReportGenerationService.GenerateCommand;
import com.teamproject.report.application.AiWeeklyReportSnapshotAssembler;
import com.teamproject.report.application.AiWeeklyReportViewProjector;
import com.teamproject.report.application.ReportPdfRenderer;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.Language;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import com.teamproject.report.domain.WeeklyReportRepository;
import com.teamproject.report.infrastructure.openai.OpenAiReportProperties;
import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * v7-2 AI 주간 리포트 공식 HTTP REST Controller (M8).
 */
@RestController
@RequestMapping("/api/v1/groups/{groupId}/reports/ai-weekly")
public class AiWeeklyReportController {

    private final AiWeeklyReportAccessService accessService;
    private final AiWeeklyReportSnapshotAssembler snapshotAssembler;
    private final AiWeeklyReportGenerationService generationService;
    private final AiWeeklyReportRevisionRepository revisionRepository;
    private final WeeklyReportRepository legacyReportRepository;
    private final AiWeeklyReportViewProjector viewProjector;
    private final ReportPdfRenderer pdfRenderer;
    private final OpenAiReportProperties properties;
    private final Clock clock;

    public AiWeeklyReportController(
            AiWeeklyReportAccessService accessService,
            AiWeeklyReportSnapshotAssembler snapshotAssembler,
            AiWeeklyReportGenerationService generationService,
            AiWeeklyReportRevisionRepository revisionRepository,
            WeeklyReportRepository legacyReportRepository,
            AiWeeklyReportViewProjector viewProjector,
            ReportPdfRenderer pdfRenderer,
            OpenAiReportProperties properties,
            Clock clock
    ) {
        this.accessService = accessService;
        this.snapshotAssembler = snapshotAssembler;
        this.generationService = generationService;
        this.revisionRepository = revisionRepository;
        this.legacyReportRepository = legacyReportRepository;
        this.viewProjector = viewProjector;
        this.pdfRenderer = pdfRenderer;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping
    public ResponseEntity<GenerateReportResponse> generate(
            Authentication authentication,
            @PathVariable Long groupId,
            @Valid @RequestBody GenerateReportRequest request
    ) {
        Long userId = (Long) authentication.getPrincipal();
        GroupMember leader = accessService.requirePaidTeamLeader(groupId, userId);

        validatePeriod(request.from(), request.toExclusive(), leader.getGroup().getTimezone());

        Language language = parseLanguage(request.language());
        String promptVersion = properties.promptVersion();
        String model = properties.model();

        AiWeeklyReportSnapshotV1 rawSnapshot = snapshotAssembler.assemble(
                groupId, request.from(), request.toExclusive(), language, promptVersion
        );

        GenerateCommand command = new GenerateCommand(
                groupId,
                request.from(),
                request.toExclusive(),
                language.name(),
                request.regenerate(),
                promptVersion,
                model
        );

        AiWeeklyReportGenerationService.GenerationResult result = generationService.generateResult(rawSnapshot, command);
        AiWeeklyReportRevision revision = result.revision();
        HttpStatus status = result.createdNew() ? HttpStatus.CREATED : HttpStatus.OK;

        String downloadUrl = String.format("/api/v1/groups/%d/reports/ai-weekly/%d/pdf", groupId, revision.getId());

        GenerateReportResponse response = new GenerateReportResponse(
                revision.getId(),
                groupId,
                revision.getPeriodFrom(),
                revision.getPeriodToExclusive(),
                revision.getRevision(),
                revision.getStatus(),
                revision.getAnalysisMode(),
                revision.getGeneratedAt(),
                downloadUrl
        );

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{reportId}")
    @Transactional(readOnly = true)
    public ResponseEntity<AiWeeklyReportView> getById(
            Authentication authentication,
            @PathVariable Long groupId,
            @PathVariable Long reportId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        accessService.requireActiveMember(groupId, userId);

        AiWeeklyReportRevision revision = findRevisionOrCheckLegacy(groupId, reportId);
        AiWeeklyReportView view = viewProjector.project(revision);

        return ResponseEntity.ok(view);
    }

    @GetMapping(value = "/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> downloadPdf(
            Authentication authentication,
            @PathVariable Long groupId,
            @PathVariable Long reportId
    ) {
        Long userId = (Long) authentication.getPrincipal();
        accessService.requireActiveMember(groupId, userId);

        AiWeeklyReportRevision revision = findRevisionOrCheckLegacy(groupId, reportId);
        AiWeeklyReportView view = viewProjector.project(revision);

        byte[] pdfBytes = pdfRenderer.renderWeeklyAiV72(view);

        String filename = String.format("ai-weekly-report-%s-r%d.pdf", revision.getPeriodFrom(), revision.getRevision());
        String disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build().toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdfBytes.length)
                .body(pdfBytes);
    }

    private AiWeeklyReportRevision findRevisionOrCheckLegacy(Long groupId, Long reportId) {
        var revisionOpt = revisionRepository.findById(reportId);
        if (revisionOpt.isPresent()) {
            AiWeeklyReportRevision rev = revisionOpt.get();
            if (!rev.getGroupId().equals(groupId)) {
                throw new ApplicationException("REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다.");
            }
            return rev;
        }

        // Legacy check
        var legacyOpt = legacyReportRepository.findById(reportId);
        if (legacyOpt.isPresent()) {
            throw new ApplicationException("AI_REPORT_LEGACY_REVISION", HttpStatus.GONE, "Legacy AI report revisions are no longer accessible under v7-2 contract.");
        }

        throw new ApplicationException("REPORT_NOT_FOUND", HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다.");
    }

    private void validatePeriod(LocalDate from, LocalDate toExclusive, String timezoneStr) {
        if (from == null || from.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ApplicationException("AI_REPORT_WEEK_INVALID", HttpStatus.BAD_REQUEST, "주간 기간은 월요일부터 시작해야 합니다.");
        }
        if (toExclusive == null || ChronoUnit.DAYS.between(from, toExclusive) != 7) {
            throw new ApplicationException("AI_REPORT_WEEK_INVALID", HttpStatus.BAD_REQUEST, "주간 기간은 정확히 7일이어야 합니다.");
        }
        ZoneId zone = ZoneId.of(timezoneStr != null ? timezoneStr : "Asia/Seoul");
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (toExclusive.isAfter(today)) {
            throw new ApplicationException("AI_REPORT_WEEK_INCOMPLETE", HttpStatus.BAD_REQUEST, "완료된 주간만 AI 리포트를 생성할 수 있습니다.");
        }
    }

    private Language parseLanguage(String langStr) {
        if (langStr == null || langStr.isBlank() || langStr.equalsIgnoreCase("KO")) {
            return Language.KO;
        }
        if (langStr.equalsIgnoreCase("EN")) {
            return Language.EN;
        }
        throw new ApplicationException("AI_REPORT_LANGUAGE_INVALID", HttpStatus.BAD_REQUEST, "지원하지 않는 언어입니다.");
    }
}
