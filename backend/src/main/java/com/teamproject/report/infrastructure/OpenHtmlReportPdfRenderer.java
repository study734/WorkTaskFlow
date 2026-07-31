package com.teamproject.report.infrastructure;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.ReportPdfRenderer;
import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;

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

    @Override
    public byte[] renderWeeklyAiV72(AiWeeklyReportView report) {
        StringBuilder body = new StringBuilder();

        // ---------------- PAGE 1: 확정 업무 현황 (Confirmed Task Status) ----------------
        LocalDate toInclusive = report.toExclusive() != null ? report.toExclusive().minusDays(1) : report.toExclusive();
        String periodDisplay = report.from() + " ~ " + toInclusive;

        body.append("<div class='page'>")
                .append("<p class='eyebrow'>WorkTaskFlow · AI WEEKLY REPORT v7-2 · R").append(report.revision()).append("</p>")
                .append("<h1>1. 확정 업무 현황</h1>")
                .append("<p class='meta'>기간: ").append(periodDisplay).append(" · 모드: ").append(report.analysisMode()).append("</p>");

        if (report.metrics() != null) {
            body.append("<table class='metrics'><tr>")
                    .append(metric("전체 업무", report.metrics().periodTaskCount()))
                    .append(metric("완료율", percent(report.metrics().completionRatePercent())))
                    .append(metric("기한 준수율", percent(report.metrics().onTimeRatePercent())))
                    .append(metric("지연 업무", report.metrics().delayedCount()))
                    .append(metric("평균 소요", report.metrics().averageLeadTimeHours() == null ? "-" : report.metrics().averageLeadTimeHours() + "시간"))
                    .append("</tr></table>");
        }

        if (report.workflow() != null) {
            body.append("<h2>워크플로우 상태 현황</h2>")
                    .append("<table class='facts'><tr>")
                    .append(fact("요청됨", report.workflow().requestedCount()))
                    .append(fact("미지정 할일", report.workflow().todoUnassignedCount()))
                    .append(fact("담당 할일", report.workflow().todoAssignedCount()))
                    .append(fact("진행 중", report.workflow().inProgressCount()))
                    .append(fact("보류 중", report.workflow().onHoldCount()))
                    .append(fact("완료됨", report.workflow().completedCount()))
                    .append("</tr></table>");
        }

        body.append("<h2>업무 상세 목록</h2>");
        if (report.tasks() == null || report.tasks().isEmpty()) {
            body.append(empty("해당 기간의 업무가 없습니다."));
        } else {
            body.append("<table><thead><tr><th>업무명</th><th>상태</th><th>우선순위</th><th>담당자</th><th>마감일</th></tr></thead><tbody>");
            for (var task : report.tasks()) {
                body.append("<tr><td>").append(html(task.realTitle())).append("</td><td>")
                        .append(html(task.status())).append("</td><td>")
                        .append(html(orDash(task.priority()))).append("</td><td>")
                        .append(html(orDash(task.assigneeName()))).append("</td><td>")
                        .append(html(orDash(task.dueAt()))).append("</td></tr>");
            }
            body.append("</tbody></table>");
        }
        body.append("</div>"); // end page 1

        body.append("<div class='page-break'></div>");

        // ---------------- PAGE 2: 이번 주 핵심 (Executive Summary) ----------------
        body.append("<div class='page'>")
                .append("<p class='eyebrow'>WorkTaskFlow · AI WEEKLY REPORT v7-2 · R").append(report.revision()).append("</p>")
                .append("<h1>2. 이번 주 핵심</h1>");

        if (report.executiveJudgment() != null) {
            body.append("<div class='card'>")
                    .append("<h2>").append(html(report.executiveJudgment().headline())).append("</h2>")
                    .append("<p class='summary'>").append(html(report.executiveJudgment().interpretation())).append("</p>")
                    .append("<p class='meta'>신뢰도: ").append(html(report.executiveJudgment().confidence())).append("</p>")
                    .append("</div>");
        }

        body.append("<h2>주간 비교</h2>");
        if (report.comparison() != null && "NO_BASELINE".equals(report.comparison().status())) {
            body.append("<div class='baseline'><b>BASELINE</b> 첫 리포트라 지난주 비교 기준이 아직 없습니다.</div>");
        } else if (report.comparison() != null) {
            body.append("<table class='metrics'><tr>")
                    .append(metric("업무 수 변화", deltaStr(report.comparison().taskCountDiff())))
                    .append(metric("완료율 변화", deltaPercent(report.comparison().completionRateDiffPercent())))
                    .append(metric("기한준수율 변화", deltaPercent(report.comparison().onTimeRateDiffPercent())))
                    .append(metric("지연 수 변화", deltaStr(report.comparison().delayedCountDiff())))
                    .append("</tr></table>");
        }

        if (report.achievement() != null) {
            body.append("<h2>주요 성과</h2>")
                    .append("<div class='card'>")
                    .append("<b>").append(html(report.achievement().headline())).append("</b>")
                    .append("<p>").append(html(report.achievement().summary())).append("</p>")
                    .append("</div>");
        }

        if (report.issues() != null && !report.issues().isEmpty()) {
            body.append("<h2>핵심 위험 요약</h2>");
            int count = 0;
            for (var issue : report.issues()) {
                if (count++ >= 3) break;
                body.append("<div class='card risk'>")
                        .append("<b>[").append(html(issue.priority())).append(" / ").append(html(issue.severity())).append("] ").append(html(issue.title())).append("</b>")
                        .append("<p>").append(html(issue.impact())).append("</p>")
                        .append("</div>");
            }
        }

        if (report.calendarConstraints() != null && !report.calendarConstraints().isEmpty()) {
            body.append("<h2>다음 주 주요 일정</h2>");
            int cCount = 0;
            for (var cal : report.calendarConstraints()) {
                if (cCount++ >= 3) break;
                body.append("<div class='card'>")
                        .append("<b>").append(html(cal.realTitle())).append("</b> (").append(html(cal.eventType())).append(")<br/>")
                        .append("<span class='meta'>일시: ").append(html(cal.startAt())).append(" ~ ").append(html(cal.endAt())).append("</span>")
                        .append("</div>");
            }
        }
        body.append("</div>"); // end page 2

        body.append("<div class='page-break'></div>");

        // ---------------- PAGE 3: 조치가 필요한 업무 (Action Required Tasks) ----------------
        body.append("<div class='page'>")
                .append("<p class='eyebrow'>WorkTaskFlow · AI WEEKLY REPORT v7-2 · R").append(report.revision()).append("</p>")
                .append("<h1>3. 조치가 필요한 업무</h1>");

        if (report.issues() == null || report.issues().isEmpty()) {
            body.append(empty("조치가 필요한 위험 업무가 없습니다."));
        } else {
            for (var issue : report.issues()) {
                body.append("<div class='card risk'>")
                        .append("<h2>[").append(html(issue.priority())).append("] ").append(html(issue.realTaskTitle())).append("</h2>")
                        .append("<p><b>원인/현상:</b> ").append(html(issue.title())).append("</p>")
                        .append("<p><b>영향:</b> ").append(html(issue.impact())).append("</p>")
                        .append("<p><b>통합 판단:</b> ").append(html(issue.integratedJudgment())).append("</p>");
                if (issue.missingEvidence() != null && !issue.missingEvidence().isEmpty()) {
                    body.append("<p class='meta'><b>부족한 근거:</b> ").append(html(String.join(", ", issue.missingEvidence()))).append("</p>");
                }
                body.append("</div>");
            }
        }
        body.append("</div>"); // end page 3

        body.append("<div class='page-break'></div>");

        // ---------------- PAGE 4: 결정과 실행 (Decisions and Actions) ----------------
        body.append("<div class='page'>")
                .append("<p class='eyebrow'>WorkTaskFlow · AI WEEKLY REPORT v7-2 · R").append(report.revision()).append("</p>")
                .append("<h1>4. 결정과 실행</h1>");

        if (report.issues() == null || report.issues().isEmpty()) {
            body.append(empty("리더 결정 사항이 없습니다."));
        } else {
            int dCount = 0;
            for (var issue : report.issues()) {
                if (issue.decision() == null) continue;
                if (dCount++ >= 3) break;
                var d = issue.decision();
                body.append("<div class='card'>")
                        .append("<h2>[").append(html(issue.priority())).append("] ").append(html(d.title())).append("</h2>")
                        .append("<p><b>결정 질문:</b> ").append(html(d.question())).append("</p>")
                        .append("<p><b>권고안:</b> ").append(html(d.recommendation())).append("</p>")
                        .append("<p class='meta'>결정 주체: ").append(html(orDash(d.decisionMakerRole()))).append(" · 실행 담당: ").append(html(orDash(d.actionOwnerRole()))).append("</p>");

                if (d.executionStepCodes() != null && !d.executionStepCodes().isEmpty()) {
                    body.append("<p><b>실행 단계:</b> ").append(html(String.join(" -> ", d.executionStepCodes()))).append("</p>");
                }
                if (d.completionSignalCodes() != null && !d.completionSignalCodes().isEmpty()) {
                    body.append("<p><b>완료 신호:</b> ").append(html(String.join(", ", d.completionSignalCodes()))).append("</p>");
                }
                body.append("</div>");
            }
        }

        body.append("<p class='notice'>이 문서는 WorkTaskFlow v7-2 엔진으로 생성된 4페이지 AI 주간 리포트입니다.</p>");
        body.append("</div>"); // end page 4

        return render(report.executiveJudgment() != null ? report.executiveJudgment().headline() : "v7-2 AI Weekly Report", "ko", body.toString());
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

    private String fact(String label, Object value) {
        return metric(label, value);
    }

    private String empty(String text) {
        return "<p class='empty'>" + html(text) + "</p>";
    }

    private String percent(Number value) {
        return value == null ? "-" : value + "%";
    }

    private String deltaStr(Integer value) {
        if (value == null) return "-";
        return value >= 0 ? "+" + value : String.valueOf(value);
    }

    private String deltaPercent(Integer value) {
        if (value == null) return "-";
        return value >= 0 ? "+" + value + "%p" : value + "%p";
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
