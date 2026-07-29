package com.teamproject.report.presentation;

import com.teamproject.report.application.ReportContracts.FindWeeklyReport;
import com.teamproject.report.application.ReportContracts.FindWeeklyReportById;
import com.teamproject.report.application.ReportContracts.GenerateWeeklyReport;
import com.teamproject.report.application.ReportContracts.EditWeeklyReportDraft;
import com.teamproject.report.application.ReportContracts.FinalizeWeeklyReport;
import com.teamproject.report.application.ReportContracts.Narrative;
import com.teamproject.report.application.ReportContracts.RegenerateWeeklyReport;
import com.teamproject.report.application.ReportContracts.RevisionSummary;
import com.teamproject.report.application.ReportContracts.WeeklyReportView;
import com.teamproject.report.application.WeeklyReportModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.nio.charset.StandardCharsets;
import com.teamproject.report.application.GeneratedPdf;
import com.teamproject.report.application.WeeklyReportPdfService;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/reports/ai-weekly")
public class WeeklyReportController {
    private final WeeklyReportModule reports;
    private final WeeklyReportPdfService pdfs;

    public WeeklyReportController(WeeklyReportModule reports, WeeklyReportPdfService pdfs) {
        this.reports = reports;
        this.pdfs = pdfs;
    }

    @GetMapping
    WeeklyReportView find(Authentication authentication, @PathVariable Long groupId,
            @RequestParam LocalDate weekStart,
            @RequestParam @Pattern(regexp = "ko|en") String language) {
        return reports.findWeeklyAiReport(new FindWeeklyReport(
                (Long) authentication.getPrincipal(), groupId, weekStart, language));
    }

    @PostMapping
    ResponseEntity<WeeklyReportView> generate(Authentication authentication, @PathVariable Long groupId,
            @Valid @RequestBody GenerateWeeklyReportRequest request) {
        WeeklyReportView report = reports.generateWeeklyAiReport(new GenerateWeeklyReport(
                (Long) authentication.getPrincipal(), groupId, request.weekStart(), request.language()));
        return ResponseEntity.status(report.cached() ? HttpStatus.OK : HttpStatus.CREATED).body(report);
    }

    @GetMapping("/{reportId}")
    WeeklyReportView findById(Authentication authentication, @PathVariable Long groupId,
            @PathVariable Long reportId) {
        return reports.findWeeklyAiReportById(new FindWeeklyReportById(
                (Long) authentication.getPrincipal(), groupId, reportId));
    }

    @GetMapping(value = "/{reportId}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> downloadPdf(Authentication authentication, @PathVariable Long groupId,
            @PathVariable Long reportId) {
        GeneratedPdf pdf = pdfs.generate(
                (Long) authentication.getPrincipal(), groupId, reportId);
        String disposition = ContentDisposition.attachment()
                .filename(pdf.filename(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.content().length)
                .body(pdf.content());
    }

    @GetMapping("/revisions")
    List<RevisionSummary> revisions(Authentication authentication, @PathVariable Long groupId,
            @RequestParam LocalDate weekStart,
            @RequestParam @Pattern(regexp = "ko|en") String language) {
        return reports.listWeeklyAiReportRevisions(new FindWeeklyReport(
                (Long) authentication.getPrincipal(), groupId, weekStart, language));
    }

    @PatchMapping("/{reportId}/draft")
    WeeklyReportView editDraft(Authentication authentication, @PathVariable Long groupId,
            @PathVariable Long reportId, @Valid @RequestBody EditDraftRequest request) {
        return reports.editWeeklyAiReportDraft(new EditWeeklyReportDraft(
                (Long) authentication.getPrincipal(), groupId, reportId,
                request.expectedEditorVersion(), request.content()));
    }

    @PostMapping("/{reportId}/regenerations")
    ResponseEntity<WeeklyReportView> regenerate(Authentication authentication,
            @PathVariable Long groupId, @PathVariable Long reportId,
            @Valid @RequestBody VersionedActionRequest request) {
        WeeklyReportView report = reports.regenerateWeeklyAiReport(new RegenerateWeeklyReport(
                (Long) authentication.getPrincipal(), groupId, reportId,
                request.expectedEditorVersion()));
        return ResponseEntity.status(HttpStatus.CREATED).body(report);
    }

    @PostMapping("/{reportId}/finalization")
    WeeklyReportView finalizeReport(Authentication authentication, @PathVariable Long groupId,
            @PathVariable Long reportId,
            @Valid @RequestBody VersionedActionRequest request) {
        return reports.finalizeWeeklyAiReport(new FinalizeWeeklyReport(
                (Long) authentication.getPrincipal(), groupId, reportId,
                request.expectedEditorVersion()));
    }

    record GenerateWeeklyReportRequest(
            @NotNull LocalDate weekStart,
            @NotNull @Pattern(regexp = "ko|en") String language) {}

    record EditDraftRequest(
            @NotNull @PositiveOrZero Long expectedEditorVersion,
            @NotNull Narrative content) {}

    record VersionedActionRequest(@NotNull @PositiveOrZero Long expectedEditorVersion) {}
}
