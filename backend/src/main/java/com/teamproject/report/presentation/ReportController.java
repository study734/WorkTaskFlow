package com.teamproject.report.presentation;

import com.teamproject.report.application.*;
import com.teamproject.report.application.ReportDocumentService.Language;
import com.teamproject.report.application.dto.ReportDtos.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/reports")
public class ReportController {
    private final ReportScheduleService schedules;
    private final ReportDocumentService documents;
    public ReportController(ReportScheduleService schedules, ReportDocumentService documents) {
        this.schedules = schedules; this.documents = documents;
    }
    @GetMapping("/schedule")
    ReportScheduleResponse schedule(Authentication auth, @PathVariable Long groupId) {
        return schedules.get((Long) auth.getPrincipal(), groupId);
    }
    @PutMapping("/schedule")
    ReportScheduleResponse schedule(Authentication auth, @PathVariable Long groupId,
            @Valid @RequestBody UpdateReportScheduleRequest request) {
        return schedules.update((Long) auth.getPrincipal(), groupId, request);
    }
    @GetMapping("/download")
    ResponseEntity<byte[]> download(Authentication auth, @PathVariable Long groupId,
            @RequestParam LocalDate from, @RequestParam LocalDate to,
            @RequestParam(defaultValue = "KO") String language) {
        Language value;
        try { value = Language.valueOf(language.trim().toUpperCase()); }
        catch (RuntimeException exception) { value = Language.KO; }
        var document = documents.generate((Long) auth.getPrincipal(), groupId, from, to, value);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.filename(), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(document.content());
    }
}
