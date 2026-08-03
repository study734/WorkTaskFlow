package com.teamproject.report.infrastructure;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.ReportPdfRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class OpenHtmlReportPdfRenderer implements ReportPdfRenderer {
    private final byte[] font;

    public OpenHtmlReportPdfRenderer() {
        try {
            font = new ClassPathResource("fonts/NanumGothic-Regular.ttf")
                    .getInputStream().readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("PDF font could not be loaded.", exception);
        }
    }

    @Override
    public byte[] renderBasic(BasicReportDocument document) {
        boolean ko = "ko".equals(document.language());
        StringBuilder body = new StringBuilder();
        body.append("<h1>").append(html(document.title())).append("</h1>")
                .append("<p class='meta'>").append(document.from()).append(" ~ ")
                .append(document.to()).append(" · ")
                .append(ko ? "AI를 사용하지 않는 기본 리포트" : "Basic report without AI")
                .append("</p>")
                .append("<table class='metrics'><tr>")
                .append(metric(ko ? "기간 업무" : "Tasks", document.tasks().size()))
                .append(metric(ko ? "완료" : "Completed", document.completed()))
                .append(metric(ko ? "진행·보류" : "Active / on hold", document.active()))
                .append(metric(ko ? "지연" : "Overdue", document.delayed()))
                .append("</tr></table>")
                .append("<h2>").append(ko ? "업무 목록" : "Task list").append("</h2>")
                .append("<table><thead><tr><th>").append(ko ? "업무" : "Task")
                .append("</th><th>").append(ko ? "상태" : "Status")
                .append("</th><th>").append(ko ? "우선순위" : "Priority")
                .append("</th><th>").append(ko ? "담당" : "Owner")
                .append("</th><th>").append(ko ? "마감" : "Due")
                .append("</th></tr></thead><tbody>");
        for (BasicReportTask task : document.tasks()) {
            body.append("<tr><td>").append(html(task.title())).append("</td><td>")
                    .append(html(task.status())).append("</td><td>")
                    .append(html(task.priority())).append("</td><td>")
                    .append(html(orDash(task.assignee()))).append("</td><td>")
                    .append(html(orDash(task.dueAt()))).append("</td></tr>");
        }
        if (document.tasks().isEmpty()) {
            body.append("<tr><td colspan='5'>")
                    .append(ko ? "해당 기간의 업무가 없습니다." : "No tasks in this period.")
                    .append("</td></tr>");
        }
        body.append("</tbody></table>")
                .append("<p class='notice'>")
                .append(ko
                        ? "이 문서는 저장된 업무 데이터로 서버에서 생성되었으며 OpenAI API를 사용하지 않습니다."
                        : "This document is generated from stored task data and does not use the OpenAI API.")
                .append("</p>");
        return render(document.title(), "ko", body.toString());
    }

    private byte[] render(String title, String language, String body) {
        String document = """
                <!DOCTYPE html>
                <html xmlns="http://www.w3.org/1999/xhtml" lang="%s">
                <head>
                  <meta charset="UTF-8" />
                  <title>%s</title>
                  <style>
                    @page { size: A4; margin: 18mm 16mm 18mm; }
                    body { font-family: NanumGothic, sans-serif; color:#25232c; font-size:10pt; line-height:1.55; }
                    h1 { margin:4px 0 6px; color:#292538; font-size:18pt; }
                    h2 { margin:16px 0 6px; padding-bottom:4px; border-bottom:1px solid #d9d5e4; color:#343046; font-size:12pt; }
                    p { margin:4px 0; } table { width:100%%; border-collapse:collapse; margin:8px 0; }
                    th, td { padding:6px; border-bottom:1px solid #dedbe5; text-align:left; vertical-align:top; font-size:8.5pt; }
                    th { background:#f0eef7; } tr { page-break-inside:avoid; }
                    .meta, .eyebrow, .section-note { color:#716b7b; font-size:8.5pt; }
                    .eyebrow { font-weight:bold; letter-spacing:.05em; }
                    .summary { margin:8px 0 12px; font-size:10.5pt; }
                    .metrics td, .facts td { width:20%%; border:1px solid #ded9eb; background:#f7f5fc; }
                    .metrics b, .facts b { display:block; margin-top:3px; font-size:13pt; }
                    .card { page-break-inside:avoid; margin:6px 0; padding:8px 10px; border-left:3px solid #675bbb; background:#f7f5fc; }
                    .card.risk { border-left-color:#b45e32; background:#fff7f1; }
                    .baseline, .notice { margin:10px 0; padding:8px 10px; background:#f0edff; border:1px solid #cfc8ef; }
                    .notice { margin-top:16px; color:#5e5868; background:#f5f4f8; border-color:#dedbe4; font-size:8.5pt; }
                    .empty { color:#77717f; }
                    .page-break { page-break-after: always; }
                  </style>
                </head>
                <body>%s</body>
                </html>
                """.formatted(html(language), html(title), body);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.useFont(() -> new ByteArrayInputStream(font), "NanumGothic");
            builder.withHtmlContent(document, null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        } catch (Exception exception) {
            throw new ApplicationException("REPORT_PDF_GENERATION_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR, "PDF 리포트를 생성하지 못했습니다.");
        }
    }

    private String metric(String label, Object value) {
        return "<td>" + html(label) + "<b>" + html(String.valueOf(value)) + "</b></td>";
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
