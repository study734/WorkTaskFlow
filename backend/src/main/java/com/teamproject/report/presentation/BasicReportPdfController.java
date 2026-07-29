package com.teamproject.report.presentation;

import com.teamproject.report.application.BasicReportPdfService;
import com.teamproject.report.application.GeneratedPdf;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups/{groupId}/reports")
public class BasicReportPdfController {
    private final BasicReportPdfService reports;

    public BasicReportPdfController(BasicReportPdfService reports) {
        this.reports = reports;
    }

    @PostMapping(value = "/basic.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> download(Authentication authentication, @PathVariable Long groupId,
            @Valid @RequestBody BasicReportPdfRequest request) {
        GeneratedPdf pdf = reports.generate((Long) authentication.getPrincipal(), groupId,
                request.scope(), request.periodType(), request.from(), request.to(),
                request.language());
        return pdf(pdf);
    }

    private ResponseEntity<byte[]> pdf(GeneratedPdf pdf) {
        String disposition = ContentDisposition.attachment()
                .filename(pdf.filename(), StandardCharsets.UTF_8)
                .build().toString();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(pdf.content().length)
                .body(pdf.content());
    }

    record BasicReportPdfRequest(
            @NotNull @Pattern(regexp = "GROUP|MY") String scope,
            @NotNull @Pattern(regexp = "WEEKLY|MONTHLY|YEARLY") String periodType,
            @NotNull LocalDate from,
            @NotNull LocalDate to,
            @NotNull @Pattern(regexp = "ko|en") String language) {}
}
