package com.teamproject.report.infrastructure;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.ReportContracts.ActionNarrativeItemView;
import com.teamproject.report.application.ReportContracts.DecisionNarrativeItemView;
import com.teamproject.report.application.ReportContracts.EvidenceValue;
import com.teamproject.report.application.ReportContracts.LocalReference;
import com.teamproject.report.application.ReportContracts.NarrativeItemView;
import com.teamproject.report.application.ReportContracts.RiskNarrativeItemView;
import com.teamproject.report.application.ReportContracts.WeeklyReportView;
import com.teamproject.report.application.ReportPdfRenderer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

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
        return render(document.title(), document.language(), body.toString());
    }

    @Override
    public byte[] renderWeeklyAi(WeeklyReportView report) {
        boolean ko = "ko".equals(report.language());
        var analysis = report.analysis();
        StringBuilder body = new StringBuilder();
        body.append("<p class='eyebrow'>").append(html(report.operations().groupName()))
                .append(" · AI WEEKLY REPORT · R").append(report.revision()).append("</p>")
                .append("<h1>").append(html(analysis.headline())).append("</h1>")
                .append("<p class='summary'>").append(html(analysis.summary().text())).append("</p>")
                .append(evidenceParagraph(evidence(
                        analysis.summary().evidenceKeys(),
                        analysis.summary().taskRefs(),
                        analysis.summary().objectiveRefs(),
                        report.evidence(),
                        ko)))
                .append("<table class='facts'><tr>")
                .append(fact(ko ? "상태" : "Status", report.operations().healthStatus()))
                .append(fact(ko ? "기간" : "Period",
                        report.periodStart() + " ~ " + report.periodEnd()))
                .append(fact(ko ? "확신" : "Confidence",
                        report.operations().confidenceLevel()))
                .append(fact(ko ? "발행" : "Publication", report.publicationStatus()))
                .append("</tr></table>");
        if (!report.comparison().available()) {
            body.append("<div class='baseline'><b>BASELINE</b> ")
                    .append(ko ? "첫 리포트라 지난주 비교 기준이 아직 없습니다."
                            : "This is the first report; no previous-week baseline is available.")
                    .append("</div>");
        }
        body.append("<table class='metrics'><tr>")
                .append(metric(ko ? "전체 업무" : "Tasks", report.metrics().totalTasks()))
                .append(metric(ko ? "완료율" : "Completion",
                        percent(report.metrics().completionRatePercent())))
                .append(metric(ko ? "기한 준수율" : "On-time rate",
                        percent(report.metrics().onTimeRatePercent())))
                .append(metric(ko ? "지연" : "Overdue", report.metrics().statuses().delayed()))
                .append("</tr></table>");

        body.append("<h2>").append(ko ? "서버 확인 위험 신호" : "Server-confirmed risk signals")
                .append("</h2>");
        if (report.metrics().riskSignals().isEmpty()) {
            body.append(empty(ko ? "서버 규칙으로 확인된 위험 신호가 없습니다."
                    : "No risk signal was confirmed by server rules."));
        } else {
            for (var risk : report.metrics().riskSignals()) {
                body.append(card(risk.severity(), riskCode(risk.code(), ko),
                        evidence(risk.evidenceKeys(), List.of(), List.of(), report.evidence(), ko)));
            }
        }

        body.append("<h2>").append(ko ? "AI 위험 후보" : "AI risk candidates").append("</h2>")
                .append("<p class='section-note'>")
                .append(ko ? "서버 근거를 바탕으로 AI가 작성한 해석이며 원인을 단정하지 않습니다."
                        : "AI-written interpretations of server evidence; causes are not asserted.")
                .append("</p>");
        appendRisks(body, analysis.risks(), report.evidence(), ko);

        body.append("<h2>").append(ko ? "완료·진척 하이라이트" : "Progress highlights")
                .append("</h2>");
        appendNarratives(body, analysis.achievements(),
                ko ? "확인된 항목이 없습니다." : "No item was identified.",
                report.evidence(), ko);

        body.append("<h2>").append(ko ? "다음 주 권고 행동" : "Recommended next-week actions")
                .append("</h2>");
        appendActions(body, analysis.topActions(), report.evidence(), ko);

        body.append("<h2>").append(ko ? "리더 결정 사항" : "Leader decisions").append("</h2>");
        appendDecisions(body, analysis.leaderDecisions(),
                ko ? "추가 결정 사항이 없습니다." : "No additional decision is required.",
                report.evidence(), ko);

        body.append("<h2>").append(ko ? "근거 업무" : "Evidence tasks").append("</h2>");
        if (report.operations().tasks().isEmpty()) {
            body.append(empty(ko ? "표시할 업무가 없습니다." : "No task is available."));
        } else {
            body.append("<table><thead><tr><th>").append(ko ? "업무" : "Task")
                    .append("</th><th>").append(ko ? "상태" : "Status")
                    .append("</th><th>").append(ko ? "우선순위" : "Priority")
                    .append("</th><th>").append(ko ? "담당" : "Owner")
                    .append("</th></tr></thead><tbody>");
            for (var task : report.operations().tasks()) {
                body.append("<tr><td>").append(html(task.task().label())).append("</td><td>")
                        .append(html(task.status())).append("</td><td>")
                        .append(html(task.priority())).append("</td><td>")
                        .append(html(task.assignee() == null ? "-" : task.assignee().label()))
                        .append("</td></tr>");
            }
            body.append("</tbody></table>");
        }

        if (!analysis.limitations().isEmpty()) {
            body.append("<h2>").append(ko ? "데이터 해석 유의점" : "Data caveats").append("</h2>");
            appendNarratives(body, analysis.limitations(), "", report.evidence(), ko);
        }
        body.append("<p class='notice'>")
                .append(ko
                        ? "수치와 서버 위험 신호는 저장된 스냅샷이며, AI 위험과 권고는 근거 기반 해석입니다."
                        : "Metrics and server risks are stored snapshots; AI risks and recommendations are evidence-based interpretations.")
                .append("</p>");
        return render(analysis.headline(), report.language(), body.toString());
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
                    h1 { margin:4px 0 6px; color:#292538; font-size:22pt; }
                    h2 { margin:22px 0 8px; padding-bottom:5px; border-bottom:1px solid #d9d5e4; color:#343046; font-size:13pt; }
                    p { margin:5px 0; } table { width:100%%; border-collapse:collapse; margin:10px 0; }
                    th, td { padding:7px; border-bottom:1px solid #dedbe5; text-align:left; vertical-align:top; font-size:8.5pt; }
                    th { background:#f0eef7; } tr { page-break-inside:avoid; }
                    .meta, .eyebrow, .section-note { color:#716b7b; font-size:8.5pt; }
                    .eyebrow { font-weight:bold; letter-spacing:.05em; }
                    .summary { margin:10px 0 16px; font-size:11pt; }
                    .metrics td, .facts td { width:25%%; border:1px solid #ded9eb; background:#f7f5fc; }
                    .metrics b, .facts b { display:block; margin-top:4px; font-size:14pt; }
                    .card { page-break-inside:avoid; margin:7px 0; padding:9px 11px; border-left:3px solid #675bbb; background:#f7f5fc; }
                    .card.risk { border-left-color:#b45e32; background:#fff7f1; }
                    .baseline, .notice { margin:14px 0; padding:10px 12px; background:#f0edff; border:1px solid #cfc8ef; }
                    .notice { margin-top:24px; color:#5e5868; background:#f5f4f8; border-color:#dedbe4; font-size:8.5pt; }
                    .empty { color:#77717f; }
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

    private void appendNarratives(StringBuilder body, List<NarrativeItemView> items,
            String emptyText, Map<String, EvidenceValue> evidenceValues, boolean ko) {
        if (items.isEmpty()) {
            if (!emptyText.isBlank()) body.append(empty(emptyText));
            return;
        }
        items.forEach(item -> body.append(card(null, item.text(), evidence(
                item.evidenceKeys(), item.taskRefs(), item.objectiveRefs(), evidenceValues, ko))));
    }

    private void appendRisks(StringBuilder body, List<RiskNarrativeItemView> items,
            Map<String, EvidenceValue> evidenceValues, boolean ko) {
        if (items.isEmpty()) {
            body.append(empty(ko ? "AI가 제안한 위험 후보가 없습니다."
                    : "AI suggested no risk candidate."));
            return;
        }
        items.forEach(item -> body.append(card(item.severity(), item.text(), evidence(
                item.evidenceKeys(), item.taskRefs(), item.objectiveRefs(), evidenceValues, ko))));
    }

    private void appendActions(StringBuilder body, List<ActionNarrativeItemView> items,
            Map<String, EvidenceValue> evidenceValues, boolean ko) {
        if (items.isEmpty()) {
            body.append(empty(ko ? "제안된 행동이 없습니다." : "No action was suggested."));
            return;
        }
        items.forEach(item -> {
            String detail = item.reason() + (item.owner() == null ? "" : " · "
                    + (ko ? "담당: " : "Owner: ") + item.owner().label());
            body.append(card("P" + item.priority(), item.action(), withEvidence(detail, evidence(
                    item.evidenceKeys(), item.taskRefs(), item.objectiveRefs(), evidenceValues, ko))));
        });
    }

    private void appendDecisions(StringBuilder body, List<DecisionNarrativeItemView> items,
            String emptyText, Map<String, EvidenceValue> evidenceValues, boolean ko) {
        if (items.isEmpty()) {
            body.append(empty(emptyText));
            return;
        }
        items.forEach(item -> body.append(card(null, item.question(), withEvidence(item.impact(), evidence(
                item.evidenceKeys(), item.taskRefs(), item.objectiveRefs(), evidenceValues, ko)))));
    }

    private String metric(String label, Object value) {
        return "<td>" + html(label) + "<b>" + html(String.valueOf(value)) + "</b></td>";
    }

    private String fact(String label, Object value) {
        return metric(label, value);
    }

    private String card(String badge, String text, String detail) {
        return "<div class='card" + (badge != null && !badge.startsWith("P") ? " risk" : "") + "'>"
                + (badge == null ? "" : "<b>" + html(badge) + "</b> ")
                + html(text)
                + (detail == null || detail.isBlank() ? "" : "<p>" + html(detail) + "</p>")
                + "</div>";
    }

    private String empty(String text) {
        return "<p class='empty'>" + html(text) + "</p>";
    }

    private String percent(Number value) {
        return value == null ? "-" : value + "%";
    }

private String evidence(List<String> evidenceKeys, List<LocalReference> taskRefs,
            List<LocalReference> objectiveRefs, Map<String, EvidenceValue> evidenceValues, boolean ko) {
        List<String> parts = new ArrayList<>();
        if (evidenceKeys != null) {
            for (String key : evidenceKeys) {
                EvidenceValue value = evidenceValues.get(key);
                parts.add(value == null ? key : value.label() + ": " + value.value());
            }
        }
        addReferences(parts, taskRefs, ko ? "업무" : "Task");
        addReferences(parts, objectiveRefs, ko ? "목표" : "Objective");
        return parts.isEmpty() ? null : (ko ? "근거: " : "Evidence: ") + String.join(" · ", parts);
    }

    private void addReferences(List<String> parts, List<LocalReference> references, String label) {
        if (references == null) return;
        for (LocalReference reference : references) {
            parts.add(label + ": " + reference.label());
        }
    }

    private String withEvidence(String detail, String evidence) {
        if (evidence == null || evidence.isBlank()) return detail;
        return detail == null || detail.isBlank() ? evidence : detail + " · " + evidence;
    }

    private String evidenceParagraph(String evidence) {
        return evidence == null || evidence.isBlank()
                ? ""
                : "<p class='meta'>" + html(evidence) + "</p>";
    }

    private String riskCode(String code, boolean ko) {
        return switch (code) {
            case "OVERDUE_PRESENT", "DELAYED_TASKS" ->
                    ko ? "지연 업무가 있습니다." : "Overdue tasks are present.";
            case "ON_HOLD_PRESENT", "ON_HOLD_TASKS" ->
                    ko ? "보류 업무가 있습니다." : "On-hold tasks are present.";
            case "HIGH_PRIORITY_PRESENT", "HIGH_PRIORITY_TASKS" ->
                    ko ? "높은 우선순위 업무가 있습니다." : "High-priority tasks are present.";
            default -> code;
        };
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
