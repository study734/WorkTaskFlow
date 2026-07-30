package com.teamproject.report.application;

import com.teamproject.dashboard.application.DashboardService;
import com.teamproject.dashboard.application.dto.DashboardDtos.*;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportDocumentService {
    private final DashboardService dashboards;
    public ReportDocumentService(DashboardService dashboards) { this.dashboards = dashboards; }

    public ReportDocument generate(Long userId, Long groupId, LocalDate from, LocalDate to, Language language) {
        GroupDashboardResponse report = dashboards.group(userId, groupId, from, to);
        String html = render(report, language);
        String suffix = language == Language.KO ? "ko" : "en";
        return new ReportDocument(html.getBytes(StandardCharsets.UTF_8),
                "toesa-" + groupId + "-" + from + "-" + to.minusDays(1) + "-" + suffix + ".html",
                subject(report.groupName(), from, to, language), html);
    }
    private String render(GroupDashboardResponse value, Language language) {
        boolean ko = language == Language.KO;
        StringBuilder rows = new StringBuilder();
        for (DashboardTaskResponse task : value.periodTasks()) {
            rows.append("<tr><td><strong class=\"task-title\">").append(escape(task.title())).append("</strong></td><td>")
                    .append("<span class=\"status status-").append(statusClass(task.status())).append("\">")
                    .append(label(task.status(), ko)).append("</span></td><td>")
                    .append(escape(task.assigneeNickname() == null ? (ko ? "미지정" : "Unassigned") : task.assigneeNickname()))
                    .append("</td><td class=\"due\">").append(task.dueAt() == null ? "-" : task.dueAt().toString().replace('T', ' '))
                    .append("</td></tr>");
        }
        if (rows.isEmpty()) rows.append("<tr><td class=\"empty\" colspan=\"4\">")
                .append(ko ? "해당 기간에 등록된 업무가 없습니다." : "No tasks were recorded in this period.")
                .append("</td></tr>");
        List<String> insights = insights(value, ko);
        StringBuilder insightHtml = new StringBuilder();
        insights.forEach(text -> insightHtml.append("<li>").append(escape(text)).append("</li>"));
        return """
                <!doctype html><html lang="%s"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>%s</title>
                <style>
                @page{size:A4;margin:12mm}*{box-sizing:border-box}body{margin:0;background:#f3f1ec;color:#292731;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI","Noto Sans KR",Arial,sans-serif;line-height:1.55}
                .report{width:min(920px,calc(100%% - 32px));margin:28px auto;background:#fff;border:1px solid #e7e3dc;border-radius:24px;box-shadow:0 18px 55px rgba(52,45,70,.09);overflow:hidden}
                header{position:relative;padding:30px 34px 32px;background:linear-gradient(135deg,#fff 0%%,#f5f1ff 58%%,#eee9ff 100%%);border-bottom:1px solid #e6e0f3}
                header:after{content:"";position:absolute;right:-54px;top:-84px;width:220px;height:220px;border-radius:50%%;border:42px solid rgba(103,84,176,.08)}
                .brand{display:flex;align-items:center;gap:10px;margin-bottom:30px;color:#5e55b7;font-size:12px;font-weight:800;letter-spacing:.13em}.brand-mark{display:inline-grid;place-items:center;width:32px;height:32px;border-radius:10px;color:#fff;background:#6657bd;font-size:15px;letter-spacing:0}
                .eyebrow{margin:0 0 8px;color:#746a90;font-size:11px;font-weight:800;letter-spacing:.14em;text-transform:uppercase}h1{max-width:650px;margin:0;color:#292333;font-size:31px;line-height:1.28;letter-spacing:-.035em}
                .meta{display:flex;flex-wrap:wrap;gap:7px 15px;margin-top:14px;color:#726d79;font-size:13px}.meta span{display:inline-flex;align-items:center;gap:6px}.meta span:before{content:"";width:5px;height:5px;border-radius:50%%;background:#8b7bd2}
                main{padding:30px 34px 34px}.metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.metric{min-height:112px;padding:17px;border:1px solid #e8e4ee;border-radius:16px;background:#fcfbfd}
                .metric small{display:block;margin-bottom:10px;color:#7d7785;font-size:11px;font-weight:700}.metric strong{display:block;color:#302b39;font-size:27px;line-height:1}.metric.risk{background:#fff9f7;border-color:#f1ded7}.metric.risk strong{color:#c15b44}
                .progress-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin-top:12px}.progress-card{padding:15px 17px;border-radius:14px;background:#f7f5fa}.progress-head{display:flex;justify-content:space-between;gap:16px;color:#686272;font-size:12px}.progress-head strong{color:#4f466b;font-size:13px}.bar{height:7px;margin-top:10px;border-radius:99px;background:#e4dfee;overflow:hidden}.bar span{display:block;height:100%%;border-radius:inherit;background:linear-gradient(90deg,#6958bd,#8d7bd5)}
                .section{margin-top:29px}.section-heading{display:flex;align-items:end;justify-content:space-between;gap:20px;margin-bottom:12px}.section-heading h2{margin:0;color:#332e3c;font-size:18px;letter-spacing:-.02em}.section-heading p{margin:0;color:#8a8490;font-size:11px}
                .summary{padding:18px 20px;border:1px solid #e6e0f2;border-radius:16px;background:#faf8ff}.summary ol{display:grid;gap:10px;margin:0;padding:0;list-style:none;counter-reset:item}.summary li{position:relative;padding-left:31px;color:#5f5967;font-size:13px}.summary li:before{counter-increment:item;content:counter(item);position:absolute;left:0;top:0;display:grid;place-items:center;width:21px;height:21px;border-radius:7px;color:#6658ad;background:#ece7fb;font-size:10px;font-weight:800}
                .table-wrap{border:1px solid #e8e4ea;border-radius:16px;overflow:hidden}table{width:100%%;border-collapse:collapse;font-size:12px}th{padding:11px 13px;color:#756f7d;background:#f7f5f8;font-size:10px;letter-spacing:.04em;text-align:left}td{padding:12px 13px;border-top:1px solid #eeeaf0;color:#625d68;text-align:left;vertical-align:middle}.task-title{color:#35303c;font-weight:700}.due{white-space:nowrap}.status{display:inline-flex;padding:4px 8px;border-radius:99px;color:#5f5869;background:#eeeaf2;font-size:10px;font-weight:750;white-space:nowrap}.status-completed{color:#32735b;background:#e5f4ec}.status-in-progress{color:#3f63a6;background:#e7eefb}.status-requested{color:#8a681c;background:#fff2ce}.status-on-hold{color:#8b5f31;background:#f7eadb}.status-rejected,.status-cancelled{color:#9d514d;background:#f9e8e6}.empty{padding:34px;text-align:center;color:#918b96}
                footer{display:flex;justify-content:space-between;gap:18px;margin-top:26px;padding-top:18px;border-top:1px solid #ece8ee;color:#8a8490;font-size:10px}footer strong{color:#655c73;white-space:nowrap}
                @media print{body{background:#fff}.report{width:100%%;margin:0;border:0;border-radius:0;box-shadow:none}header{padding:24px 26px}main{padding:24px 26px}.section,.table-wrap,tr{break-inside:avoid}thead{display:table-header-group}}
                @media(max-width:700px){.report{width:100%%;margin:0;border:0;border-radius:0}.metrics{grid-template-columns:repeat(2,1fr)}.progress-grid{grid-template-columns:1fr}header,main{padding:24px 20px}h1{font-size:25px}.table-wrap{overflow-x:auto}table{min-width:620px}footer{flex-direction:column}}
                </style></head><body><article class="report"><header><div class="brand"><span class="brand-mark">✓</span> TOESA · 퇴사</div><p class="eyebrow">%s</p><h1>%s</h1><div class="meta"><span>%s</span><span>%s ~ %s</span></div></header><main>
                <section class="metrics">
                <div class="metric"><small>%s</small><strong>%d</strong></div>
                <div class="metric"><small>%s</small><strong>%s</strong></div>
                <div class="metric"><small>%s</small><strong>%s</strong></div>
                <div class="metric risk"><small>%s</small><strong>%d</strong></div>
                </section><section class="progress-grid"><div class="progress-card"><div class="progress-head"><span>%s</span><strong>%s</strong></div><div class="bar"><span style="width:%s"></span></div></div><div class="progress-card"><div class="progress-head"><span>%s</span><strong>%s</strong></div><div class="bar"><span style="width:%s"></span></div></div></section>
                <section class="section"><div class="section-heading"><h2>%s</h2><p>%s</p></div><div class="summary"><ol>%s</ol></div></section>
                <section class="section"><div class="section-heading"><h2>%s</h2><p>%s</p></div><div class="table-wrap"><table><thead><tr><th>%s</th><th>%s</th><th>%s</th><th>%s</th></tr></thead><tbody>%s</tbody></table></div></section>
                <footer><span>%s</span><strong>TOESA · WORK SMARTER, LEAVE ON TIME</strong></footer></main></article></body></html>
                """.formatted(ko ? "ko" : "en", escape(subject(value.groupName(), value.periodFrom(), value.periodTo(), language)),
                ko ? "BASIC WORK REPORT" : "BASIC WORK REPORT",
                escape(value.groupName() + (ko ? " 업무 리포트" : " Work Report")),
                ko ? "보고 기간" : "Reporting period", value.periodFrom(), value.periodTo().minusDays(1),
                ko ? "기간 업무" : "Period tasks", value.periodTasks().size(),
                ko ? "완료율" : "Completion rate", percent(value.periodCompletionRatePercent()),
                ko ? "기한 준수율" : "On-time rate", percent(value.onTimeRatePercent()),
                ko ? "지연 업무" : "Overdue tasks", value.statuses().delayed(),
                ko ? "업무 완료율" : "Task completion", percent(value.periodCompletionRatePercent()), barPercent(value.periodCompletionRatePercent()),
                ko ? "기한 준수율" : "On-time delivery", percent(value.onTimeRatePercent()), barPercent(value.onTimeRatePercent()),
                ko ? "이번 기간 한눈에 보기" : "Period at a glance",
                ko ? "확정된 업무 데이터 기준" : "Based on confirmed task data", insightHtml,
                ko ? "업무 상세" : "Task details",
                ko ? value.periodTasks().size() + "건" : value.periodTasks().size() + " tasks",
                ko ? "업무" : "Task", ko ? "상태" : "Status",
                ko ? "담당자" : "Assignee", ko ? "마감" : "Due", rows,
                ko ? "이 기본 리포트는 퇴사에 저장된 확정 업무 데이터로 생성되며 AI 추론을 사용하지 않습니다."
                        : "This core report uses confirmed task data stored in toesa and does not use AI inference.");
    }
    private List<String> insights(GroupDashboardResponse value, boolean ko) {
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        if (value.periodTasks().isEmpty()) result.add(ko ? "기간 내 업무가 없어 추세를 판단하지 않습니다." : "There are no tasks in this period, so no trend is inferred.");
        if (value.statuses().delayed() > 0) result.add(ko
                ? "지연 업무 " + value.statuses().delayed() + "건의 마감일과 담당자를 우선 확인하세요."
                : "Review due dates and owners for " + value.statuses().delayed() + " overdue task(s).");
        if (value.statuses().requested() > 0) result.add(ko
                ? "승인 대기 업무 " + value.statuses().requested() + "건이 있어 팀장의 확인이 필요합니다."
                : value.statuses().requested() + " task request(s) need a leader decision.");
        if (value.periodCompletionRatePercent() != null && value.periodCompletionRatePercent() >= 80)
            result.add(ko ? "선택 기간의 신규 업무 완료율이 80% 이상입니다." : "Completion of newly created tasks is at least 80%.");
        if (result.isEmpty()) result.add(ko ? "현재 확정 지표에서 즉시 조치가 필요한 위험 신호는 없습니다." : "Confirmed metrics show no immediate risk signal.");
        return result;
    }
    private String subject(String groupName, LocalDate from, LocalDate to, Language language) {
        return language == Language.KO
                ? "[퇴사] " + groupName + " 업무 리포트 (" + from + " ~ " + to.minusDays(1) + ")"
                : "[toesa] " + groupName + " Work Report (" + from + " – " + to.minusDays(1) + ")";
    }
    private String label(String status, boolean ko) {
        if (!ko) return status.replace('_', ' ');
        return switch (status) {
            case "REQUESTED" -> "승인 대기"; case "TODO" -> "할 일"; case "IN_PROGRESS" -> "진행 중";
            case "ON_HOLD" -> "보류"; case "COMPLETED" -> "완료"; case "REJECTED" -> "반려";
            case "CANCELLED" -> "취소"; default -> status;
        };
    }
    private String statusClass(String status) {
        return status == null ? "unknown" : status.toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
    private String barPercent(Integer value) {
        return value == null ? "0%" : Math.max(0, Math.min(100, value)) + "%";
    }
    private String percent(Integer value) { return value == null ? "-" : value + "%"; }
    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    public enum Language { KO, EN }
    public record ReportDocument(byte[] content, String filename, String subject, String html) {}
}
