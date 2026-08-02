package com.teamproject.report.application;

import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.*;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * v7-2 AI 주간 리포트를 브라우저에서 열고 인쇄할 수 있는 HTML 문서로 만든다.
 *
 * <p>기본 리포트({@link ReportDocumentService})와 같은 방식이다. 서버 PDF 렌더러는 CSS 2.1만
 * 이해해서 grid·flex를 쓸 수 없다. 같은 디자인을 두 벌 유지하지 않으려면 AI 리포트도 HTML로
 * 내려보내고 인쇄는 브라우저에 맡기는 편이 낫다.
 *
 * <p>문서 언어는 revision에 저장된 언어를 따른다. 요청 시점의 화면 언어를 쓰면 EN revision을
 * 한국어 껍데기로 감싸는 잡탕이 된다.
 */
@Service
public class AiWeeklyReportDocumentService {

    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    /** 계약상 issues는 최대 3개다. 이 상한은 계약이 늘어나도 조용히 자르지 않게 두는 안전장치다. */
    private static final int MAX_SIGNALS = 3;
    private static final int MAX_TIMELINE = 3;
    /**
     * A4 인쇄 1장에 들어가는 표 행 수. 인쇄 영역 1043px에서 헤더와 지표·요약이 834px를 쓰고,
     * 행 높이가 29px이라 17행이 한계다. 넘기면 1장이 밀려 전체가 5장이 된다.
     */
    /**
     * 1페이지가 A4 한 장에 들어가야 한다. 15행이면 여유가 14px뿐이라, 그룹 이름이 두 줄로
     * 감기거나(흔하다) 요약이 한 줄 늘면 곧바로 5장이 된다. 한 행이 29px이므로 12행이면
     * 87px을 돌려받아 두 경우가 겹쳐도 버틴다. 브라우저 인쇄 폭(189mm)에서 실측했다.
     */
    private static final int MAX_TABLE_ROWS = 12;

    /** 문서 한 벌을 만드는 데 필요한 값. 메서드마다 같은 인자를 끌고 다니지 않으려고 묶었다. */
    private record Doc(AiWeeklyReportView report, String groupName, ZoneId zone, boolean ko) {
        /** 문서 곳곳의 "주간"·"이번 주" 문구가 실제 기간을 따라가게 한다. */
        PeriodKind kind() { return PeriodKind.of(report.from(), report.toExclusive()); }
    }

    /**
     * 기간 제약을 완화한 뒤로 주간이 아닌 기간도 생성된다(달 기준 주차, 월간, 연간).
     * 문구가 "주간"으로 굳어 있으면 지난달 리포트 제목이 "주간 업무 리포트"로 나간다.
     */
    private enum PeriodKind {
        WEEK, MONTH, YEAR, RANGE;

        static PeriodKind of(LocalDate from, LocalDate toExclusive) {
            if (from == null || toExclusive == null) return RANGE;
            if (from.getDayOfYear() == 1 && toExclusive.equals(from.plusYears(1))) return YEAR;
            if (from.getDayOfMonth() == 1 && toExclusive.equals(from.plusMonths(1))) return MONTH;
            if (toExclusive.equals(from.plusDays(7))) return WEEK;
            return RANGE;
        }

        String noun(boolean ko) {
            return switch (this) {
                case WEEK -> ko ? "주간" : "Weekly";
                case MONTH -> ko ? "월간" : "Monthly";
                case YEAR -> ko ? "연간" : "Yearly";
                case RANGE -> ko ? "기간" : "Period";
            };
        }

        /** "이번 주 핵심"처럼 기간을 가리키는 말. 주간이 아니면 중립적으로 쓴다. */
        String thisPeriod(boolean ko) {
            return switch (this) {
                case WEEK -> ko ? "이번 주" : "This week";
                case MONTH -> ko ? "이번 달" : "This month";
                case YEAR -> ko ? "올해" : "This year";
                case RANGE -> ko ? "이번 기간" : "This period";
            };
        }

        String slug() {
            return switch (this) {
                case WEEK -> "weekly";
                case MONTH -> "monthly";
                case YEAR -> "yearly";
                case RANGE -> "period";
            };
        }
    }

    public ReportDocumentService.ReportDocument generate(AiWeeklyReportView report, String groupName,
            String timezone, String language) {
        boolean ko = !"EN".equalsIgnoreCase(language);
        Doc doc = new Doc(report, groupName, zone(timezone), ko);
        String html = render(doc);
        return new ReportDocumentService.ReportDocument(
                html.getBytes(StandardCharsets.UTF_8),
                "toesa-ai-" + doc.kind().slug() + "-" + report.groupId() + "-" + report.from()
                        + "-r" + report.revision() + "-" + (ko ? "ko" : "en") + ".html",
                subject(doc),
                html);
    }

    private String subject(Doc doc) {
        return doc.ko
                ? "[퇴사] " + doc.groupName + " AI " + doc.kind().noun(true) + " 리포트 (" + period(doc.report) + ")"
                : "[toesa] " + doc.groupName + " AI " + doc.kind().noun(false) + " Report (" + period(doc.report) + ")";
    }

    private String render(Doc doc) {
        return """
                <!doctype html><html lang="%s"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width,initial-scale=1">
                <title>%s</title><style>%s</style></head>
                <body><article class="report">
                <header class="report-header">
                  <div class="brand"><span class="brand-mark">✓</span> %s</div>
                  <p class="eyebrow">AI MEETING REPORT</p>
                  <h1>%s</h1>
                  <div class="meta"><span>%s %s</span><span>v7-2 · R%d · %s</span></div>
                </header>
                <main>%s%s%s%s</main>
                </article></body></html>
                """.formatted(
                doc.ko ? "ko" : "en",
                escape(subject(doc)),
                styles(),
                doc.ko ? "TOESA · 퇴사" : "TOESA",
                escape(doc.groupName + (doc.ko ? " " + doc.kind().noun(true) + " 업무 리포트"
                        : " " + doc.kind().noun(false) + " Work Report")),
                doc.ko ? "보고 기간" : "Reporting period",
                period(doc.report),
                doc.report.revision(),
                escape(modeLabel(doc)),
                pageOne(doc),
                pageTwo(doc),
                pageThree(doc),
                pageFour(doc));
    }

    // ---------- PAGE 1 : 확정 업무 현황 ----------

    private String pageOne(Doc doc) {
        AiWeeklyReportView report = doc.report;
        SnapshotMetricsView metrics = report.metrics();
        SnapshotWorkflowView workflow = report.workflow();
        long total = metrics == null ? 0 : metrics.periodTaskCount();
        int completed = workflow == null ? 0 : workflow.completedCount();

        StringBuilder rows = new StringBuilder();
        List<SnapshotTaskView> tasks = report.tasks() == null ? List.of() : report.tasks();
        // 표에는 15건만 실린다. id 순으로 자르면 그 15건이 하필 급한 업무일 이유가 없다.
        // 기본 리포트는 전량을 싣기 때문에 순서가 문제되지 않지만, 여기서는 순서가 곧 선택이다.
        tasks = tasks.stream()
                .sorted(Comparator.comparingInt((SnapshotTaskView t) -> dueRank(t.dueState()))
                        .thenComparing(t -> orDash(t.dueAt()))
                        .thenComparing(t -> orDash(t.realTitle())))
                .toList();
        int shown = 0;
        for (SnapshotTaskView task : tasks) {
            if (shown++ >= MAX_TABLE_ROWS) break;
            rows.append("<tr><td><strong class=\"task-title\">").append(escape(task.realTitle()))
                    .append("</strong></td><td><span class=\"status status-").append(statusClass(task))
                    .append("\">").append(escape(statusLabel(doc, task))).append("</span></td><td>")
                    .append(escape(task.assigneeName() == null
                            ? (doc.ko ? "미지정" : "Unassigned") : task.assigneeName()))
                    .append("</td><td class=\"due\">").append(escape(dueText(task.dueAt(), doc.zone)))
                    .append("</td></tr>");
        }
        if (tasks.isEmpty()) {
            rows.append("<tr><td class=\"empty\" colspan=\"4\">")
                    .append(doc.ko ? "해당 기간에 집계된 업무가 없습니다." : "No tasks were recorded in this period.")
                    .append("</td></tr>");
        }
        // 모수는 tasks가 아니라 확정 지표다. tasks는 Snapshot 상한(100건)에서 잘려 있어
        // 그대로 쓰면 같은 페이지 KPI가 105건인데 표는 "기간 업무 100건"이라고 말한다.
        // 지표가 없는 저장본은 예전처럼 표에 실린 수를 쓴다.
        long tableTotal = metrics == null ? tasks.size() : total;
        String tableNote = tasks.size() > MAX_TABLE_ROWS
                ? (doc.ko
                        ? "기간 업무 " + tableTotal + "건 중 " + MAX_TABLE_ROWS + "건 표시 · 전체는 앱에서 확인"
                        : MAX_TABLE_ROWS + " of " + tableTotal + " tasks shown · see the app for all")
                : (doc.ko ? "기간 업무 " + tableTotal + "건" : tableTotal + " tasks");

        StringBuilder insights = new StringBuilder();
        for (String line : insights(doc)) {
            insights.append("<li>").append(escape(line)).append("</li>");
        }

        return """
                <section class="page first">
                  <section class="metrics">
                    <div class="metric"><small>%s</small><strong>%d</strong></div>
                    <div class="metric"><small>%s</small><strong>%s</strong></div>
                    <div class="metric"><small>%s</small><strong>%s</strong></div>
                    <div class="metric risk"><small>%s</small><strong>%d</strong></div>
                  </section>
                  <section class="progress-grid">
                    <div class="progress-card"><div class="progress-head"><span>%s</span><strong>%d/%d</strong></div><div class="bar"><span style="width:%s"></span></div></div>
                    <div class="progress-card"><div class="progress-head"><span>%s</span><strong>%s</strong></div><div class="bar"><span style="width:%s"></span></div></div>
                  </section>
                  <section class="section">
                    <div class="section-heading"><h2>%s</h2><p>%s</p></div>
                    <div class="summary"><ol>%s</ol></div>
                  </section>
                  <section class="section">
                    <div class="section-heading"><h2>%s</h2><p>%s</p></div>
                    <div class="table-wrap"><table><thead><tr><th>%s</th><th>%s</th><th>%s</th><th>%s</th></tr></thead><tbody>%s</tbody></table></div>
                  </section>
                  <div class="report-footer"><span>%s</span><strong>TOESA · WORK SMARTER, LEAVE ON TIME</strong></div>
                </section>
                """.formatted(
                doc.ko ? "기간 업무" : "Period tasks", total,
                doc.ko ? "완료율" : "Completion rate",
                percent(metrics == null ? null : metrics.completionRatePercent()),
                doc.ko ? "기한 준수율" : "On-time rate",
                percent(metrics == null ? null : metrics.onTimeRatePercent()),
                doc.ko ? "지연 업무" : "Overdue tasks", metrics == null ? 0 : metrics.delayedCount(),
                doc.ko ? "업무 완료율" : "Task completion", completed, total,
                bar(metrics == null ? null : metrics.completionRatePercent()),
                doc.ko ? "기한 준수율" : "On-time delivery",
                escape(completed == 0
                        ? (doc.ko ? "완료 업무 없음" : "No completed tasks")
                        : (doc.ko ? "완료 " + completed + "건 기준" : "based on " + completed + " completed")),
                bar(metrics == null ? null : metrics.onTimeRatePercent()),
                doc.ko ? "이번 기간 한눈에 보기" : "Period at a glance",
                doc.ko ? "확정된 업무 데이터 기준" : "Based on confirmed task data",
                insights,
                doc.ko ? "업무 상세" : "Task details", escape(tableNote),
                doc.ko ? "업무" : "Task", doc.ko ? "상태" : "Status",
                doc.ko ? "담당자" : "Assignee", doc.ko ? "마감" : "Due", rows,
                doc.ko ? "기본 지표는 서버의 확정 업무 데이터에서 계산했습니다."
                        : "Core metrics are computed from confirmed task data on the server.");
    }

    /** Snapshot에 이미 확정된 수치만 문장으로 옮긴다. 새 숫자는 만들지 않는다. */
    private List<String> insights(Doc doc) {
        SnapshotMetricsView metrics = doc.report.metrics();
        SnapshotWorkflowView workflow = doc.report.workflow();
        List<String> lines = new ArrayList<>();
        if (metrics == null || metrics.periodTaskCount() == 0) {
            lines.add(doc.ko
                    ? "이 기간에 집계된 확정 업무가 없어 추세를 판단하지 않았습니다."
                    : "No confirmed tasks in this period, so no trend was inferred.");
            return lines;
        }
        if (workflow != null) {
            lines.add(doc.ko
                    ? "기간 업무 " + metrics.periodTaskCount() + "건 중 완료 " + workflow.completedCount()
                            + "건, 진행 중 " + workflow.inProgressCount() + "건, 보류 "
                            + workflow.onHoldCount() + "건입니다."
                    : "Of " + metrics.periodTaskCount() + " tasks, " + workflow.completedCount()
                            + " completed, " + workflow.inProgressCount() + " in progress, "
                            + workflow.onHoldCount() + " on hold.");
        }
        if (metrics.delayedCount() > 0) {
            lines.add(doc.ko
                    ? "지연 업무 " + metrics.delayedCount() + "건의 담당자와 새 마감을 먼저 확인해야 합니다."
                    : "Review the owner and new due date for " + metrics.delayedCount() + " overdue task(s) first.");
        }
        if (workflow != null && workflow.requestedCount() > 0) {
            lines.add(doc.ko
                    ? "승인 대기 업무 " + workflow.requestedCount() + "건이 남아 팀장의 결정이 필요합니다."
                    : workflow.requestedCount() + " task request(s) still need a leader decision.");
        }
        if (workflow != null && workflow.todoUnassignedCount() > 0) {
            lines.add(doc.ko
                    ? "담당자가 지정되지 않은 업무 " + workflow.todoUnassignedCount() + "건이 있습니다."
                    : workflow.todoUnassignedCount() + " task(s) have no assignee.");
        }
        // 분석이 본 업무가 기간 전체보다 적으면 반드시 밝힌다. 표와 일정은 잘림을 알리는데
        // 정작 AI 판단의 대상이 잘린 것만 감추면, 읽는 쪽은 전 기간을 본 결론으로 받아들인다.
        // 연간처럼 긴 기간에서 흔하다.
        int analyzed = doc.report.tasks() == null ? 0 : doc.report.tasks().size();
        if (analyzed > 0 && metrics.periodTaskCount() > analyzed) {
            lines.add(doc.ko
                    ? "AI 분석은 기간 업무 " + metrics.periodTaskCount() + "건 중 " + analyzed
                            + "건을 대상으로 했습니다. 수치는 전체 기간 기준입니다."
                    : "The AI analysis covered " + analyzed + " of " + metrics.periodTaskCount()
                            + " tasks. The metrics above cover the whole period.");
        }
        SnapshotComparisonView comparison = doc.report.comparison();
        if (comparison != null && "AVAILABLE".equals(comparison.status())
                && comparison.completionRateDiffPercent() != null) {
            lines.add(doc.ko
                    ? "완료율은 지난 기간 대비 " + signed(comparison.completionRateDiffPercent()) + "%p 변화했습니다."
                    : "Completion rate changed " + signed(comparison.completionRateDiffPercent())
                            + "%p from the previous period.");
        }
        if (lines.size() == 1 && metrics.delayedCount() == 0) {
            lines.add(doc.ko
                    ? "확정 수치 기준으로 즉시 조치가 필요한 지연 업무는 없습니다."
                    : "Confirmed metrics show no overdue task needing immediate action.");
        }
        return lines;
    }

    // ---------- PAGE 2 : 기간 핵심 ----------

    private String pageTwo(Doc doc) {
        return """
                <section class="page">
                  <h2 class="page-title">%s</h2>
                  <p class="page-desc">%s</p>
                  %s
                  <section class="section">
                    <div class="section-heading"><h2>%s</h2><p>%s</p></div>
                    %s
                  </section>
                  <section class="section">
                    <div class="section-heading"><h2>%s</h2><p>%s</p></div>
                    %s
                  </section>
                  <section class="section">
                    <div class="section-heading"><h2>%s</h2><p>%s</p></div>
                    %s
                  </section>
                  <section class="section">
                    <div class="section-heading"><h2>%s</h2><p>%s</p></div>
                    %s
                  </section>
                  <div class="report-footer"><span>%s</span><strong>TOESA · MEETING BRIEF</strong></div>
                </section>
                """.formatted(
                doc.ko ? doc.kind().thisPeriod(true) + " 핵심" : doc.kind().thisPeriod(false) + " at the top",
                doc.ko ? "회의 시작 전에 반드시 공유할 판단, 흐름, 성과, 위험만 남겼습니다."
                        : "Only the judgment, flow, achievement, and risks to share before the meeting.",
                hero(doc),
                doc.ko ? "업무 흐름" : "Work flow",
                doc.ko ? "요청 → 승인 → 담당 → 진행 → 완료" : "Requested → assigned → in progress → done",
                flow(doc),
                doc.ko ? doc.kind().thisPeriod(true) + " 성과" : "Achievement",
                doc.ko ? "완료 업무의 결과를 한 문장으로 압축" : "Completed work in a single sentence",
                achievement(doc),
                doc.ko ? "회의 전 확인할 위험" : "Risks to review before the meeting",
                doc.ko ? "상세 근거와 조치는 다음 페이지" : "Evidence and actions on the next page",
                signals(doc),
                doc.ko ? "기간 직후 3일 일정" : "Next three days",
                // 스냅샷이 담는 창이 기간 종료 후 3일이다. 제목이 "다음 기간"이면 다음 주
                // 전체를 보여줄 것처럼 읽히는데 실제로는 3일치다. 창을 넓히면 AI 입력 계약이
                // 함께 바뀌므로, 문서는 담고 있는 것을 그대로 말한다.
                doc.ko ? "마감과 겹칠 수 있는 확정 일정만 표시"
                        : "Confirmed events that may collide with due dates",
                timeline(doc),
                doc.ko ? "AI 판단은 표시된 근거 업무와 확정 지표를 기준으로 작성했습니다."
                        : "AI judgments are based on the linked evidence tasks and confirmed metrics.");
    }

    private String hero(Doc doc) {
        ExecutiveJudgmentView judgment = doc.report.executiveJudgment();
        String label = doc.ko ? "AI 핵심 판단" : "AI KEY JUDGMENT";
        if (judgment == null) {
            return "<div class=\"ai-hero\"><b>" + label + "</b><p>"
                    + (doc.ko ? "이번 기간의 핵심 판단이 생성되지 않았습니다."
                            : "No key judgment was generated for this period.")
                    + "</p></div>";
        }
        StringBuilder meta = new StringBuilder();
        meta.append("<span class=\"").append(confidenceClass(judgment.confidence())).append("\">")
                .append(escape(confidenceLabel(doc, judgment.confidence()))).append("</span>");
        if (judgment.evidenceTaskTitles() != null && !judgment.evidenceTaskTitles().isEmpty()) {
            meta.append("<span>").append(doc.ko ? "근거 " : "Evidence ")
                    .append(escape(String.join(" · ", judgment.evidenceTaskTitles()))).append("</span>");
        }
        meta.append("<span>").append(doc.ko ? "분석 상태 " : "Analysis ")
                .append(escape(modeLabel(doc))).append("</span>");
        return "<div class=\"ai-hero\"><b>" + label + "</b><p>" + escape(judgment.headline())
                + "</p><em>" + escape(judgment.interpretation()) + "</em><div class=\"ai-meta\">"
                + meta + "</div></div>";
    }

    /** 흐름 단계별 전 기간 델타는 계약에 없다. 있는 수치만 보조 문구로 붙인다. */
    private String flow(Doc doc) {
        SnapshotWorkflowView workflow = doc.report.workflow();
        if (workflow == null) return "";
        SnapshotComparisonView comparison = doc.report.comparison();
        boolean baseline = comparison != null && "AVAILABLE".equals(comparison.status());

        String countCaption = baseline && comparison.taskCountDiff() != null
                ? "<small class=\"" + deltaClass(comparison.taskCountDiff()) + "\">"
                        + (doc.ko ? "전 기간 대비 " + signed(comparison.taskCountDiff()) + "건"
                                : signed(comparison.taskCountDiff()) + " vs previous")
                        + "</small>"
                : "<small>" + (doc.ko ? "확정 요청 기준" : "Confirmed requests") + "</small>";
        String doneCaption = baseline && comparison.completionRateDiffPercent() != null
                ? "<small class=\"" + deltaClass(comparison.completionRateDiffPercent()) + "\">"
                        + (doc.ko ? "완료율 " + signed(comparison.completionRateDiffPercent()) + "%p"
                                : "rate " + signed(comparison.completionRateDiffPercent()) + "%p")
                        + "</small>"
                : "<small>" + (doc.ko ? "확정 완료 기준" : "Confirmed completions") + "</small>";

        return "<div class=\"flow\">"
                + step(workflow.requestedCount(), doc.ko ? "승인 대기" : "Requested", countCaption)
                + step(workflow.todoUnassignedCount(), doc.ko ? "담당 미지정" : "Unassigned",
                        workflow.todoUnassignedCount() > 0
                                ? "<small class=\"delta-down\">"
                                        + (doc.ko ? "담당 연결 필요" : "needs an owner") + "</small>"
                                : "<small>" + (doc.ko ? "없음" : "none") + "</small>")
                + step(workflow.todoAssignedCount(), doc.ko ? "착수 전" : "Not started",
                        "<small>" + (doc.ko ? "담당자 지정 완료" : "owner assigned") + "</small>")
                + step(workflow.inProgressCount() + workflow.onHoldCount(),
                        doc.ko ? "진행·보류" : "Active / hold",
                        "<small>" + (doc.ko ? "보류 " + workflow.onHoldCount() + "건"
                                : workflow.onHoldCount() + " on hold") + "</small>")
                + step(workflow.completedCount(), doc.ko ? "완료" : "Completed", doneCaption)
                + "</div>";
    }

    private String step(int value, String label, String caption) {
        return "<div class=\"flow-step\"><b>" + value + "</b><span>" + escape(label) + "</span>"
                + caption + "</div>";
    }

    private String achievement(Doc doc) {
        AchievementView value = doc.report.achievement();
        String label = doc.ko ? doc.kind().thisPeriod(true) + " 성과" : "ACHIEVEMENT";
        if (value == null || !"AVAILABLE".equals(value.status())) {
            return "<div class=\"success-card\"><b>" + label + "</b><div><strong>"
                    + (doc.ko ? "완료 근거가 확인된 성과가 없습니다." : "No achievement with confirmed evidence.")
                    + "</strong><p>"
                    + (doc.ko ? "완료 상태와 근거가 함께 확인된 업무가 있을 때 표시합니다."
                            : "Shown when a task is both completed and evidenced.")
                    + "</p></div><span>-</span></div>";
        }
        String refs = value.evidenceTaskTitles() == null || value.evidenceTaskTitles().isEmpty()
                ? "-" : String.join(" · ", value.evidenceTaskTitles());
        return "<div class=\"success-card\"><b>" + label + "</b><div><strong>"
                + escape(value.headline()) + "</strong><p>" + escape(value.summary())
                + "</p></div><span>" + escape(refs) + "</span></div>";
    }

    private String signals(Doc doc) {
        List<IssueView> issues = doc.report.issues() == null ? List.of() : doc.report.issues();
        if (issues.isEmpty()) {
            return note(doc.ko
                    ? "확정 수치 기준으로 회의 전에 확인할 위험 업무가 선정되지 않았습니다."
                    : "Confirmed metrics selected no risk task to review before the meeting.");
        }
        StringBuilder html = new StringBuilder("<div class=\"signal-grid\">");
        int count = 0;
        for (IssueView issue : issues) {
            if (count++ >= MAX_SIGNALS) break;
            html.append("<article class=\"signal").append(isRisk(issue) ? " risk" : "").append("\">")
                    .append("<small>").append(escape(severityLabel(doc, issue.severity()))).append("</small>")
                    .append("<h3>").append(escape(issue.realTaskTitle())).append("</h3>")
                    .append("<p>").append(escape(issue.impact())).append("</p>")
                    .append("<footer>").append(escape(issue.priority()))
                    .append(doc.ko ? " · 근거 " : " · evidence ")
                    .append(escape(joinTitles(issue))).append("</footer></article>");
        }
        return html.append("</div>").toString();
    }

    private String timeline(Doc doc) {
        List<CalendarConstraintView> events = doc.report.calendarConstraints() == null
                ? List.of() : doc.report.calendarConstraints();
        if (events.isEmpty()) {
            return note(doc.ko ? "기간 직후 3일 안에 확정된 일정이 없습니다."
                    : "No confirmed events in the three days after the period.");
        }
        StringBuilder html = new StringBuilder("<div class=\"timeline\">");
        int count = 0;
        for (CalendarConstraintView event : events) {
            if (count++ >= MAX_TIMELINE) break;
            html.append("<div class=\"timeline-item\"><time>")
                    .append(escape(dayTime(event.startAt(), doc.zone))).append("</time><strong>")
                    .append(escape(event.realTitle())).append("</strong><p>")
                    .append(escape(event.safeLabel())).append("</p></div>");
        }
        // 업무표와 같은 규칙을 쓴다. 말없이 자르면 남은 일정이 없는 것으로 읽힌다.
        if (events.size() > MAX_TIMELINE) {
            html.append("<div class=\"timeline-item\"><time>·</time><strong>")
                    .append(doc.ko
                            ? "확정 일정 " + events.size() + "건 중 " + MAX_TIMELINE + "건 표시"
                            : MAX_TIMELINE + " of " + events.size() + " confirmed events shown")
                    .append("</strong><p>")
                    .append(doc.ko ? "전체는 캘린더에서 확인" : "See the calendar for all")
                    .append("</p></div>");
        }
        return html.append("</div>").toString();
    }

    // ---------- PAGE 3 : 조치가 필요한 업무 ----------

    private String pageThree(Doc doc) {
        List<IssueView> issues = doc.report.issues() == null ? List.of() : doc.report.issues();
        StringBuilder cards = new StringBuilder();
        if (issues.isEmpty()) {
            cards.append(riskCheckSummary(doc));
        }
        for (IssueView issue : issues) {
            SnapshotTaskView task = taskOf(doc.report, issue);
            cards.append("<article class=\"issue").append(isRisk(issue) ? " risk" : "").append("\">")
                    .append("<div class=\"issue-head\"><h3>").append(escape(issue.realTaskTitle()))
                    .append("</h3><small>").append(escape(severityLabel(doc, issue.severity())))
                    .append("</small></div><div class=\"issue-grid\">")
                    .append(cell(doc.ko ? "담당·상태" : "Owner · status", task == null ? "-"
                            : (task.assigneeName() == null ? (doc.ko ? "미지정" : "Unassigned")
                                    : task.assigneeName()) + " · " + statusLabel(doc, task)))
                    .append(cell(doc.ko ? "협업 근거" : "Collaboration", collaboration(doc, task)))
                    .append(cell(doc.ko ? "일정" : "Schedule", dueState(doc, task)))
                    .append(cell(doc.ko ? "영향" : "Impact", issue.impact()))
                    .append(cell(doc.ko ? "결정권자·실행주체" : "Decider · owner", roles(doc, issue)))
                    .append(cell(doc.ko ? "필요 결정" : "Decision needed", issue.requiredDecision()))
                    .append("</div><div class=\"issue-action\"><b>")
                    .append(doc.ko ? "AI 통합 판단" : "AI integrated judgment").append("</b> ")
                    .append(escape(issue.integratedJudgment()))
                    .append("<div class=\"evidence-line\"><strong>")
                    .append(doc.ko ? "근거" : "Evidence").append("</strong> ")
                    .append(escape(joinTitles(issue)));
            if (issue.missingEvidence() != null && !issue.missingEvidence().isEmpty()) {
                cards.append(" · <strong>").append(doc.ko ? "추가 확인 필요" : "Needs confirmation")
                        .append("</strong> ").append(escape(String.join(", ", issue.missingEvidence())));
            }
            cards.append("</div></div></article>");
        }
        return """
                <section class="page">
                  <h2 class="page-title">%s</h2>
                  <p class="page-desc">%s</p>
                  <section class="section"><div class="issue-list">%s</div></section>
                  <div class="report-footer"><span>%s</span><strong>TOESA · ACTION REVIEW</strong></div>
                </section>
                """.formatted(
                doc.ko ? "조치가 필요한 업무" : "Tasks requiring action",
                doc.ko ? "사람, 체크리스트, 댓글·멘션, 자료, 일정 신호를 업무 단위로 합쳤습니다."
                        : "Owner, checklist, comments, resources, and schedule signals merged per task.",
                cards,
                doc.ko ? "근거가 부족한 항목은 단정하지 않고 추가 확인 사항을 표시했습니다."
                        : "Items with thin evidence are flagged for confirmation instead of asserted.");
    }

    /**
     * 위험 후보가 없을 때 "없습니다" 한 줄만 남기면 아무 일도 안 한 것으로 읽힌다. 유료
     * 문서에서 특히 나쁘다. policy engine은 실제로 항목 전체를 검사하므로 무엇을 봤고 각
     * 항목이 몇 건이었는지 그대로 보여 준다. 지어내는 것이 아니라 이미 한 일을 밝히는 것이다.
     */
    private String riskCheckSummary(Doc doc) {
        List<RiskCheckView> checks = doc.report.riskChecks() == null ? List.of() : doc.report.riskChecks();
        if (checks.isEmpty()) {
            return note(doc.ko ? "조치가 필요한 위험 업무가 없습니다." : "No task requires action.");
        }

        StringBuilder grid = new StringBuilder();
        for (RiskCheckView check : checks) {
            grid.append("<div><b>").append(escape(check.label())).append("</b><span>")
                    .append(check.candidateCount()).append(doc.ko ? "건" : "").append("</span></div>");
        }

        int tasks = doc.report.tasks() == null ? 0 : doc.report.tasks().size();
        return "<div class=\"summary\"><strong>"
                + (doc.ko
                        ? "확인한 위험 항목 " + checks.size() + "개 · 업무 " + tasks + "건 · 조치가 필요한 항목 없음"
                        : "Checked " + checks.size() + " risk signals across " + tasks + " tasks · none require action")
                + "</strong><div class=\"decision-grid\">" + grid + "</div><p class=\"limit\">"
                + (doc.ko
                        ? "각 항목은 확정 데이터로 판정했습니다. 0건은 해당 신호가 없었다는 뜻입니다."
                        : "Each signal was evaluated against confirmed data. Zero means the signal did not occur.")
                + "</p></div>";
    }

    // ---------- PAGE 4 : 결정과 실행 ----------

    private String pageFour(Doc doc) {
        List<IssueView> issues = doc.report.issues() == null ? List.of() : doc.report.issues();
        StringBuilder cards = new StringBuilder();
        int decisions = 0;
        for (IssueView issue : issues) {
            DecisionView decision = issue.decision();
            if (decision == null) continue;
            decisions++;
            cards.append("<article class=\"decision-card\"><div class=\"decision-top\">")
                    .append("<span class=\"priority\">").append(escape(issue.priority())).append("</span><div>")
                    .append("<h3>").append(escape(decision.title())).append("</h3>")
                    .append("<p class=\"decision-question\">").append(escape(decision.question())).append("</p>")
                    .append("<p class=\"recommendation\"><b>")
                    .append(doc.ko ? "AI 권고" : "AI recommendation").append("</b> ")
                    .append(escape(decision.recommendation()))
                    .append("</p></div><span class=\"deadline\">").append(escape(deadlineLabel(doc, decision)))
                    .append("</span></div><div class=\"decision-grid\">")
                    .append(cell(doc.ko ? "결정권자·실행주체" : "Decider · owner", roleText(doc, decision)))
                    .append(cell(doc.ko ? "적용 업무" : "Applies to", issue.realTaskTitle()))
                    .append(cell(doc.ko ? "판단 근거" : "Evidence", joinCodes(doc, issue.evidenceCodes())))
                    .append("</div><div class=\"completion\"><b>")
                    .append(doc.ko ? "완료 조건" : "Completion criteria").append("</b> ")
                    .append(escape(completion(doc, decision))).append("</div>")
                    .append(record(doc)).append("</article>");
        }
        if (decisions == 0) {
            cards.append(note(doc.ko ? "팀장이 결정할 사항이 없습니다." : "No leader decision is required."));
        }

        List<String> missing = new ArrayList<>();
        if (doc.report.globalMissingEvidence() != null) missing.addAll(doc.report.globalMissingEvidence());
        for (IssueView issue : issues) {
            if (issue.missingEvidence() != null) missing.addAll(issue.missingEvidence());
        }

        return """
                <section class="page">
                  <h2 class="page-title">%s</h2>
                  <p class="page-desc">%s</p>
                  <section class="section"><div class="decision-list">%s</div></section>
                  <div class="final-strip">
                    <div class="ref-box"><strong>%s %s</strong><br>%s<br>%s%s</div>
                    <div class="trust-box"><strong>%s</strong><br>%s<div class="limit">%s</div></div>
                  </div>
                  <div class="report-footer"><span>%s</span><strong>TOESA · WORK SMARTER, LEAVE ON TIME</strong></div>
                </section>
                """.formatted(
                doc.ko ? "결정과 실행" : "Decisions and actions",
                doc.ko ? "AI 권고는 참고안으로 제공하며 최종 배정·기한·범위는 팀장이 결정합니다."
                        : "AI recommendations are advisory; the leader decides assignment, dates, and scope.",
                cards,
                doc.ko ? "분석 상태" : "Analysis", escape(modeLabel(doc)),
                doc.ko
                        ? "핵심 판단 " + (doc.report.executiveJudgment() == null ? 0 : 1) + "개 · 위험 "
                                + issues.size() + "개 · 결정 " + decisions + "개"
                        : (doc.report.executiveJudgment() == null ? 0 : 1) + " judgment · "
                                + issues.size() + " risks · " + decisions + " decisions",
                doc.ko ? "모든 AI 판단에 근거 업무가 연결되었습니다."
                        : "Every AI judgment is linked to an evidence task.",
                // 항목이 완결 문장이라 쉼표로 이으면 "없습니다., 마감"이 된다. 줄로 나눈다.
                missing.isEmpty() ? ""
                        : "<br>" + (doc.ko ? "추가 확인 필요: " : "Needs confirmation: ")
                                + missing.stream().map(this::escape)
                                        .collect(java.util.stream.Collectors.joining("<br>· ", "<br>· ", "")),
                doc.ko ? "회의 종료 조건" : "Meeting exit criteria",
                doc.ko ? "결정 항목의 상태, 담당자, 기한, 완료 조건이 WorkTaskFlow에 기록됨"
                        : "Status, owner, due date, and completion criteria recorded in WorkTaskFlow",
                doc.ko ? "수치는 확정 업무 데이터입니다. AI는 해석과 권고만 제공하며 최종 결정은 팀장이 수행합니다. "
                        + "댓글 원문·첨부파일 내용·기록되지 않은 협의는 반영하지 않았습니다."
                        : "Figures come from confirmed task data. AI provides interpretation and advice only; "
                        + "the leader decides. Raw comments, attachment contents, and unrecorded discussions are excluded.",
                doc.ko ? "모든 AI 판단은 근거 업무와 연결되며, 근거가 부족한 항목은 추가 확인으로 표시합니다."
                        : "Every AI judgment links to evidence; thin evidence is flagged for confirmation.");
    }

    // ---------- 값 변환 ----------

    private String note(String text) {
        return "<div class=\"summary\"><p style=\"margin:0;font-size:9px;color:#5f5967\">"
                + escape(text) + "</p></div>";
    }

    private String cell(String label, String value) {
        return "<div><b>" + escape(label) + "</b><span>" + escape(orDash(value)) + "</span></div>";
    }

    private SnapshotTaskView taskOf(AiWeeklyReportView report, IssueView issue) {
        if (report.tasks() == null || issue.taskRefs() == null || issue.taskRefs().isEmpty()) return null;
        String ref = issue.taskRefs().get(0);
        return report.tasks().stream().filter(task -> ref.equals(task.taskRef())).findFirst().orElse(null);
    }

    private String collaboration(Doc doc, SnapshotTaskView task) {
        if (task == null) return "-";
        List<String> parts = new ArrayList<>();
        if (task.checklist() != null) {
            parts.add((doc.ko ? "체크리스트 " : "checklist ")
                    + task.checklist().completedCount() + "/" + task.checklist().totalCount());
        }
        if (task.collaboration() != null) {
            parts.add(doc.ko ? "댓글 " + task.collaboration().commentCount() + "건"
                    : task.collaboration().commentCount() + " comments");
            if (task.collaboration().unresolvedMentionCount() > 0) {
                parts.add(doc.ko ? "미응답 멘션 " + task.collaboration().unresolvedMentionCount() + "건"
                        : task.collaboration().unresolvedMentionCount() + " unresolved mentions");
            }
        }
        return parts.isEmpty() ? "-" : String.join(", ", parts);
    }

    /**
     * 인쇄본에만 나오는 기록란. 회의록 표준은 결정마다 담당과 기한을 사람이 적게 두는데,
     * 이 문서는 AI 권고까지만 찍혀 있어 종이로 뽑으면 여백에 갈겨쓰게 된다.
     *
     * <p>화면에서는 숨긴다. 화면에는 눌러서 쓸 수 없는 빈 칸이 필요 없다.
     */
    private String record(Doc doc) {
        return "<div class=\"record\"><b>" + (doc.ko ? "회의 기록" : "Recorded in the meeting")
                + "</b><div class=\"record-row\"><span>"
                + (doc.ko ? "결정 &#9744; 승인 &#9744; 보류 &#9744; 변경"
                          : "Decision &#9744; approve &#9744; hold &#9744; change")
                + "</span><span class=\"record-line\"></span></div>"
                + "<div class=\"record-row\"><span>" + (doc.ko ? "담당" : "Owner")
                + "</span><span class=\"record-line\"></span><span>"
                + (doc.ko ? "기한" : "Due") + "</span><span class=\"record-line\"></span></div></div>";
    }

    /** 회의에서 먼저 봐야 하는 순서. 지난 것, 곧 올 것, 멈춘 것, 나머지, 끝난 것. */
    private int dueRank(String dueState) {
        return switch (orDash(dueState)) {
            case "OVERDUE" -> 0;
            case "DUE_SOON" -> 1;
            case "UPCOMING" -> 2;
            case "NO_DUE" -> 3;
            case "COMPLETED_LATE" -> 4;
            case "COMPLETED_ON_TIME" -> 5;
            default -> 6;
        };
    }

    /** 상태만 쓰면 "언제까지"가 문서에 없다. 회의에서 바로 필요한 값이라 마감을 함께 적는다. */
    private String dueState(Doc doc, SnapshotTaskView task) {
        String state = dueStateLabel(doc, task);
        if (task == null || task.dueAt() == null) return state;
        String due = dueText(task.dueAt(), doc.zone);
        return "-".equals(due) ? state : state + " · " + due;
    }

    private String dueStateLabel(Doc doc, SnapshotTaskView task) {
        if (task == null) return "-";
        return switch (orDash(task.dueState())) {
            case "OVERDUE" -> doc.ko ? "마감 초과 · 새 기한 결정 필요" : "Overdue · new due date needed";
            case "DUE_SOON" -> doc.ko ? "마감 임박" : "Due soon";
            case "UPCOMING" -> doc.ko ? "마감 여유" : "On schedule";
            case "COMPLETED_ON_TIME" -> doc.ko ? "기한 내 완료" : "Completed on time";
            case "COMPLETED_LATE" -> doc.ko ? "기한 초과 완료" : "Completed late";
            case "NO_DUE" -> doc.ko ? "마감 미설정" : "No due date";
            default -> "-";
        };
    }

    private String roles(Doc doc, IssueView issue) {
        return issue.decision() == null ? "-" : roleText(doc, issue.decision());
    }

    private String roleText(Doc doc, DecisionView decision) {
        return roleLabel(doc, decision.decisionMakerRole()) + " · " + roleLabel(doc, decision.actionOwnerRole());
    }

    private String roleLabel(Doc doc, String role) {
        if (role == null) return "-";
        return switch (role) {
            case "LEADER" -> doc.ko ? "팀장" : "Leader";
            case "GROUP_ADMIN" -> doc.ko ? "그룹 관리자" : "Group admin";
            case "CURRENT_ASSIGNEE" -> doc.ko ? "현재 담당자" : "Current assignee";
            case "SELECTED_MEMBER" -> doc.ko ? "지정 팀원" : "Selected member";
            case "REQUESTER" -> doc.ko ? "요청자" : "Requester";
            case "TEAM" -> doc.ko ? "팀 전체" : "Whole team";
            // 계약 밖 값이 들어와도 사용자 문서에 코드를 그대로 찍지 않는다.
            default -> doc.ko ? "확인할 수 없는 역할" : "Unidentified role";
        };
    }

    private String deadlineLabel(Doc doc, DecisionView decision) {
        if (decision.deadline() == null || decision.deadline().source() == null) {
            return doc.ko ? "팀장 결정" : "Leader decides";
        }
        return switch (decision.deadline().source()) {
            case "LEADER_DECISION_REQUIRED" -> doc.ko ? "회의 종료 전" : "Before the meeting ends";
            case "TASK_DUE" -> doc.ko ? "업무 마감 기준" : "By the task due date";
            case "CALENDAR_EVENT" -> doc.ko
                    ? orDash(decision.deadline().referenceTitle()) + " 전"
                    : "Before " + orDash(decision.deadline().referenceTitle());
            default -> decision.deadline().source();
        };
    }

    private String completion(Doc doc, DecisionView decision) {
        if (decision.completionSignalCodes() == null || decision.completionSignalCodes().isEmpty()) {
            return doc.ko ? "결정 결과와 사유를 업무 기록에 남깁니다."
                    : "Record the decision and its reason on the task.";
        }
        List<String> parts = new ArrayList<>();
        for (String code : decision.completionSignalCodes()) {
            parts.add(completionLabel(doc, code));
        }
        return String.join(", ", parts);
    }

    /** 계약(ai-weekly-report-analysis-v1.schema.json)의 completionSignalCodes 9개를 모두 덮는다. */
    private String completionLabel(Doc doc, String code) {
        String label = AiWeeklyReportCodeVocabulary.label(code, doc.ko);
        return label != null ? label : (doc.ko ? "기타 완료 조건" : "other criterion");
    }

    /** 계약의 evidenceCodes(SignalCode) 15개. 사용자 문서에 영문 상수를 내보내지 않는다. */
    private String signalLabel(Doc doc, String code) {
        String label = AiWeeklyReportCodeVocabulary.label(code, doc.ko);
        return label != null ? label : (doc.ko ? "기타 신호" : "other signal");
    }

    private String joinCodes(Doc doc, List<String> codes) {
        if (codes == null || codes.isEmpty()) return "-";
        List<String> parts = new ArrayList<>();
        for (String code : codes) {
            parts.add(signalLabel(doc, code));
        }
        return String.join(", ", parts);
    }

    private String joinTitles(IssueView issue) {
        if (issue.taskTitles() == null || issue.taskTitles().isEmpty()) return orDash(issue.realTaskTitle());
        return String.join(" · ", issue.taskTitles());
    }

    private boolean isRisk(IssueView issue) {
        return "HIGH".equals(issue.severity()) || "CRITICAL".equals(issue.severity());
    }

    private String severityLabel(Doc doc, String severity) {
        if (severity == null) return doc.ko ? "확인 필요" : "Review";
        return switch (severity) {
            case "CRITICAL" -> doc.ko ? "매우 높음" : "Critical";
            case "HIGH" -> doc.ko ? "높음" : "High";
            case "MEDIUM" -> doc.ko ? "보통" : "Medium";
            case "LOW" -> doc.ko ? "낮음" : "Low";
            default -> severity;
        };
    }

    private String confidenceLabel(Doc doc, String confidence) {
        if (confidence == null) return doc.ko ? "근거 보통" : "Moderate evidence";
        return switch (confidence) {
            case "HIGH" -> doc.ko ? "근거 충분" : "Strong evidence";
            case "MEDIUM" -> doc.ko ? "근거 보통" : "Moderate evidence";
            case "INSUFFICIENT_EVIDENCE" -> doc.ko ? "근거 부족" : "Insufficient evidence";
            default -> confidence;
        };
    }

    private String confidenceClass(String confidence) {
        return "HIGH".equals(confidence) ? "confidence-high" : "confidence-medium";
    }

    private String modeLabel(Doc doc) {
        boolean fallback = "SERVER_FALLBACK".equals(doc.report.analysisMode());
        if (doc.ko) return fallback ? "서버 기본 분석" : "정상";
        return fallback ? "Server fallback" : "Normal";
    }

    private String statusLabel(Doc doc, SnapshotTaskView task) {
        String base = switch (orDash(task.status())) {
            case "REQUESTED" -> doc.ko ? "승인 대기" : "Requested";
            case "TODO" -> doc.ko ? "할 일" : "To do";
            case "IN_PROGRESS" -> doc.ko ? "진행 중" : "In progress";
            case "ON_HOLD" -> doc.ko ? "보류" : "On hold";
            case "COMPLETED" -> doc.ko ? "완료" : "Completed";
            default -> orDash(task.status());
        };
        if (!"OVERDUE".equals(task.dueState())) return base;
        return doc.ko ? base + "·지연" : base + " · overdue";
    }

    private String statusClass(SnapshotTaskView task) {
        if ("OVERDUE".equals(task.dueState()) && !"ON_HOLD".equals(task.status())) return "risk";
        if (task.status() == null) return "todo";
        return task.status().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Snapshot의 시각은 UTC ISO 문자열이고 화면에는 그룹 시간대로 보여 준다.
     * 마감은 날짜만으로는 부족하다 — 같은 날 오전과 마감 직전은 회의에서 다른 얘기다.
     * 자정은 "시각 미지정"의 저장 형태라 날짜만 적는다. 기본 리포트가 그 경우에도 00:00을
     * 찍는 것과 다른데, 정보가 늘지 않는 표기라 뺀다.
     */
    private String dueText(String isoInstant, ZoneId zone) {
        Instant instant = instant(isoInstant);
        if (instant == null) return "-";
        var zoned = instant.atZone(zone);
        return (zoned.getHour() == 0 && zoned.getMinute() == 0)
                ? MONTH_DAY.format(zoned) : DAY_TIME.format(zoned);
    }

    private String dayTime(String isoInstant, ZoneId zone) {
        Instant instant = instant(isoInstant);
        return instant == null ? "-" : DAY_TIME.format(instant.atZone(zone));
    }

    private Instant instant(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            try {
                return LocalDate.parse(value).atStartOfDay(ZoneId.of("UTC")).toInstant();
            } catch (RuntimeException alsoIgnored) {
                return null;
            }
        }
    }

    private ZoneId zone(String timezone) {
        try {
            return ZoneId.of(timezone == null || timezone.isBlank() ? "Asia/Seoul" : timezone);
        } catch (RuntimeException ignored) {
            return ZoneId.of("Asia/Seoul");
        }
    }

    private String period(AiWeeklyReportView report) {
        return report.from() + " ~ " + (report.toExclusive() == null ? "-" : report.toExclusive().minusDays(1));
    }

    private String bar(Integer value) {
        return value == null ? "0%" : Math.max(0, Math.min(100, value)) + "%";
    }

    private String percent(Integer value) {
        return value == null ? "-" : value + "%";
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String deltaClass(int value) {
        return value < 0 ? "delta-down" : "delta-up";
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    /**
     * 이 CSS는 사용자가 내려받는 파일에 그대로 실린다. 설명은 여기 Javadoc에만 둔다.
     *
     * <p>{@code print-color-adjust:exact}가 필요한 이유: Chrome 인쇄 대화상자의 "배경 그래픽"은
     * 기본으로 꺼져 있어, 켜지 않으면 카드·상태 pill·진행바 색이 전부 흰색으로 빠진다.
     */
    private String styles() {
        return """
                @page{size:A4;margin:10.5mm}
                /* keep card and status colours when printing */
                *{box-sizing:border-box;-webkit-print-color-adjust:exact;print-color-adjust:exact}
                body{margin:0;background:#f3f1ec;color:#292731;font-family:"Noto Sans KR",-apple-system,BlinkMacSystemFont,"Segoe UI",Arial,sans-serif;line-height:1.46}
                .report{width:min(920px,calc(100% - 32px));margin:28px auto;background:#fff;border:1px solid #e7e3dc;border-radius:24px;box-shadow:0 18px 55px rgba(52,45,70,.09);overflow:hidden}
                .report-header{position:relative;padding:28px 32px;background:linear-gradient(135deg,#fff 0%,#f5f1ff 58%,#eee9ff 100%);border-bottom:1px solid #e6e0f3;overflow:hidden}
                .report-header:after{content:"";position:absolute;right:-54px;top:-84px;width:220px;height:220px;border-radius:50%;border:42px solid rgba(103,84,176,.08)}
                .brand{display:flex;align-items:center;gap:10px;margin-bottom:23px;color:#5e55b7;font-size:11px;font-weight:800;letter-spacing:.13em}.brand-mark{display:inline-grid;place-items:center;width:31px;height:31px;border-radius:10px;color:#fff;background:#6657bd;font-size:14px;letter-spacing:0}
                .eyebrow{margin:0 0 7px;color:#746a90;font-size:9.5px;font-weight:800;letter-spacing:.14em;text-transform:uppercase}h1{max-width:690px;margin:0;color:#292333;font-size:29px;line-height:1.28;letter-spacing:-.035em}
                .meta{display:flex;flex-wrap:wrap;gap:7px 14px;margin-top:12px;color:#726d79;font-size:10.5px}.meta span{display:inline-flex;align-items:center;gap:6px}.meta span:before{content:"";width:5px;height:5px;border-radius:50%;background:#8b7bd2}
                main{padding:22px 25px 25px}.page{break-before:page;padding-top:1px}.page.first{break-before:auto}.page-title{margin:0 0 3px;color:#332e3c;font-size:18px;letter-spacing:-.03em}.page-desc{margin:0 0 12px;color:#8a8490;font-size:8.4px}
                .metrics{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:9px}.metric{min-height:82px;padding:13px;border:1px solid #e8e4ee;border-radius:14px;background:#fcfbfd}.metric small{display:block;margin-bottom:8px;color:#7d7785;font-size:9px;font-weight:700}.metric strong{display:block;color:#302b39;font-size:23px;line-height:1}.metric.risk{background:#fff9f7;border-color:#f1ded7}.metric.risk strong{color:#c15b44}
                .progress-grid{display:grid;grid-template-columns:1fr 1fr;gap:9px;margin-top:9px}.progress-card{padding:11px 13px;border-radius:12px;background:#f7f5fa}.progress-head{display:flex;justify-content:space-between;gap:14px;color:#686272;font-size:9px}.progress-head strong{color:#4f466b;font-size:10px}.bar{height:6px;margin-top:7px;border-radius:99px;background:#e4dfee;overflow:hidden}.bar span{display:block;height:100%;border-radius:inherit;background:linear-gradient(90deg,#6958bd,#8d7bd5)}
                .section{margin-top:15px}.section-heading{display:flex;align-items:end;justify-content:space-between;gap:16px;margin-bottom:7px}.section-heading h2{margin:0;color:#332e3c;font-size:14px;letter-spacing:-.02em}.section-heading p{margin:0;color:#8a8490;font-size:7.8px}
                .summary{padding:12px 14px;border:1px solid #e6e0f2;border-radius:14px;background:#faf8ff}.summary ol{display:grid;gap:6px;margin:0;padding:0;list-style:none;counter-reset:item}.summary li{position:relative;padding-left:25px;color:#5f5967;font-size:9px}.summary li:before{counter-increment:item;content:counter(item);position:absolute;left:0;top:0;display:grid;place-items:center;width:18px;height:18px;border-radius:6px;color:#6658ad;background:#ece7fb;font-size:8px;font-weight:800}
                .table-wrap{border:1px solid #e8e4ea;border-radius:14px;overflow:hidden}table{width:100%;border-collapse:collapse;font-size:7.8px}th{padding:6px 7px;color:#756f7d;background:#f7f5f8;font-size:7px;letter-spacing:.04em;text-align:left}td{padding:6px 7px;border-top:1px solid #eeeaf0;color:#625d68;text-align:left;vertical-align:middle}.task-title{color:#35303c;font-weight:700}.due{white-space:nowrap}.empty{padding:26px;text-align:center;color:#918b96}
                .status{display:inline-flex;padding:3px 6px;border-radius:99px;color:#5f5869;background:#eeeaf2;font-size:7px;font-weight:750;white-space:nowrap}.status-completed{color:#32735b;background:#e5f4ec}.status-in-progress{color:#3f63a6;background:#e7eefb}.status-requested{color:#8a681c;background:#fff2ce}.status-on-hold{color:#8b5f31;background:#f7eadb}.status-risk{color:#9d514d;background:#f9e8e6}.status-todo{color:#655c73;background:#f0edf4}
                .ai-hero{padding:15px 16px;border:1px solid #e6e0f2;border-radius:14px;background:#faf8ff}.ai-hero b{display:block;margin-bottom:5px;color:#6658ad;font-size:8.2px;letter-spacing:.08em}.ai-hero p{margin:0;color:#403a49;font-size:12px;font-weight:700;line-height:1.57}.ai-hero em{display:block;margin-top:7px;color:#746d7d;font-size:8px;font-style:normal;font-weight:500}.ai-meta{display:flex;flex-wrap:wrap;gap:5px 8px;margin-top:8px;padding-top:7px;border-top:1px solid #e8e2ef;color:#706879;font-size:6.8px}.ai-meta span{padding:3px 6px;border-radius:99px;background:#fff}.confidence-high{color:#32735b!important;background:#e5f4ec!important}.confidence-medium{color:#8a681c!important;background:#fff2ce!important}.recommendation{margin:5px 0 0;padding:6px 8px;border-radius:9px;background:#fff;color:#514b58;font-size:7.1px}.recommendation b{color:#6658ad}.evidence-line{margin-top:5px;color:#746d7d;font-size:6.7px}.evidence-line strong{color:#6658ad}.delta-down{color:#a85243!important}.delta-up{color:#32735b!important}
                .flow{display:grid;grid-template-columns:repeat(5,1fr);gap:6px}.flow-step{position:relative;padding:9px 6px;border:1px solid #e6e0f2;border-radius:12px;background:#faf8ff;text-align:center}.flow-step b{display:block;color:#332e3c;font-size:16px}.flow-step span{display:block;margin-top:2px;color:#7d7785;font-size:7.2px}.flow-step small{display:block;margin-top:3px;color:#675e73;font-size:6.7px}.flow-step:not(:last-child):after{content:">";position:absolute;right:-6px;top:50%;transform:translateY(-50%);color:#8b7bd2;font-size:11px;font-weight:800;z-index:2}
                .success-card{display:grid;grid-template-columns:105px 1fr auto;gap:11px;align-items:center;padding:12px 14px;border:1px solid #dceadf;border-radius:14px;background:#f7fcf8}.success-card b{color:#32735b;font-size:8px;letter-spacing:.08em}.success-card strong{display:block;color:#35303c;font-size:10.5px}.success-card p{margin:3px 0 0;color:#625d68;font-size:7.7px}.success-card span{padding:4px 7px;border-radius:99px;color:#32735b;background:#e5f4ec;font-size:6.8px;font-weight:800;white-space:nowrap}
                .signal-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:8px}.signal{padding:11px 12px;border:1px solid #e8e4ee;border-radius:13px;background:#fcfbfd}.signal.risk{border-color:#f1ded7;background:#fff9f7}.signal small{display:block;color:#7d7785;font-size:6.8px;font-weight:800;letter-spacing:.05em}.signal h3{margin:4px 0;color:#332e3c;font-size:10.4px}.signal p{margin:0;color:#625d68;font-size:7.5px}.signal footer{margin-top:6px;padding-top:5px;border-top:1px solid #eeeaf0;color:#6658ad;font-size:6.8px;font-weight:800}
                .issue-list{display:grid;gap:9px}.issue{padding:12px 13px;border:1px solid #e8e4ee;border-radius:14px;background:#fcfbfd}.issue.risk{border-color:#f1ded7;background:#fff9f7}.issue-head{display:grid;grid-template-columns:1fr auto;gap:10px;align-items:start}.issue-head h3{margin:0;color:#332e3c;font-size:11px}.issue-head small{color:#7c7482;font-size:6.8px;font-weight:800;letter-spacing:.04em}.issue-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:6px 11px;margin-top:8px}.issue-grid div{border-top:1px solid #eeeaf0;padding-top:4px}.issue-grid b{display:block;color:#8a8490;font-size:6.5px}.issue-grid span{display:block;margin-top:2px;color:#514b58;font-size:7.4px;font-weight:650}.issue-action{margin-top:7px;padding:7px 9px;border-radius:10px;background:#fff;color:#514b58;font-size:7.3px}.issue-action b{color:#6658ad}
                .timeline{display:grid;grid-template-columns:repeat(3,1fr);gap:7px}.timeline-item{padding:9px 10px;border:1px solid #e8e4ee;border-radius:12px;background:#fcfbfd}.timeline-item time{display:block;color:#6658ad;font-size:7px;font-weight:800}.timeline-item strong{display:block;margin-top:4px;color:#3d3745;font-size:8.5px}.timeline-item p{margin:3px 0 0;color:#756e7a;font-size:7px}
                .decision-list{display:grid;gap:9px}.decision-card{padding:12px 13px;border:1px solid #e6e0f2;border-radius:15px;background:#faf8ff}.decision-top{display:grid;grid-template-columns:34px 1fr auto;gap:9px;align-items:start}.priority{display:grid;place-items:center;width:32px;height:32px;border-radius:10px;color:#fff;background:#6657bd;font-size:9px;font-weight:800}.decision-card h3{margin:0;color:#332e3c;font-size:10.8px}.decision-question{margin:3px 0 0;color:#6658ad;font-size:8px;font-weight:700}.deadline{padding:3px 6px;border-radius:99px;color:#8a681c;background:#fff2ce;font-size:6.8px;font-weight:800;white-space:nowrap}.decision-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:5px 10px;margin-top:8px}.decision-grid div{border-top:1px solid #e9e4ef;padding-top:4px}.decision-grid b{display:block;color:#8a8490;font-size:6.4px}.decision-grid span{display:block;margin-top:2px;color:#514b58;font-size:7.2px;font-weight:650}.completion{margin-top:7px;padding:7px 9px;border-radius:10px;color:#514b58;background:#fff;font-size:7.2px}.completion b{color:#6658ad}
                .record{display:none}
                .final-strip{display:grid;grid-template-columns:1.15fr .85fr;gap:8px;margin-top:12px}.ref-box,.trust-box{padding:9px 11px;border:1px solid #e8e4ee;border-radius:12px;background:#fcfbfd;color:#5f5967;font-size:7.2px}.ref-box strong,.trust-box strong{color:#6658ad}.trust-box{background:#faf8ff;border-color:#e6e0f2}.limit{margin-top:7px;padding-top:7px;border-top:1px solid #e8e4ee;color:#7b7480;font-size:6.7px}.report-footer{display:flex;justify-content:space-between;gap:16px;margin-top:12px;padding-top:11px;border-top:1px solid #ece8ee;color:#8a8490;font-size:7px}.report-footer strong{color:#655c73;white-space:nowrap}
                @media print{body{background:#fff}.record{display:block;margin-top:7px;padding:7px 9px;border:1px dashed #c9c2d8;border-radius:10px}.record b{display:block;margin-bottom:5px;color:#6658ad;font-size:6.4px;letter-spacing:.05em}.record-row{display:flex;align-items:flex-end;gap:7px;margin-top:6px;color:#514b58;font-size:7.2px}.record-line{flex:1;border-bottom:1px solid #b8b1c6;height:11px}.report{width:100%;margin:0;border:0;border-radius:0;box-shadow:none}.report-header{padding:20px 23px}main{padding:18px 22px 21px}.page{break-before:page}.page.first{break-before:auto}tr,.signal,.issue,.decision-card,.timeline-item{break-inside:avoid}thead{display:table-header-group}}
                @media(max-width:700px){.report{width:100%;margin:0;border:0;border-radius:0}.metrics{grid-template-columns:repeat(2,1fr)}.progress-grid,.flow,.signal-grid,.timeline,.issue-grid,.decision-grid,.final-strip,.success-card{grid-template-columns:1fr}.flow-step:not(:last-child):after{display:none}.table-wrap{overflow-x:auto}table{min-width:560px}}
                """;
    }
}
