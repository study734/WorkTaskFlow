package com.teamproject.report;

import com.teamproject.report.application.AiWeeklyReportDocumentService;
import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사용자 문서에 내부 enum 코드가 그대로 나가지 않는지 본다.
 *
 * <p>역할 라벨만 손보고 나머지 코드 계열은 두어서 실제 문서에 {@code NEXT_REVIEW_DATE_SET},
 * {@code REQUESTED_PENDING} 같은 영문 상수가 찍혔다. {@code completion()}의 사전은 계약에
 * 없는 이름({@code DUE_SET}, {@code STATUS_CHANGED})을 담고 있어 실제 값은 하나도 못 맞췄다.
 * 계약 Schema에서 코드 목록을 직접 읽어 전부 라벨이 붙는지 검사한다.
 */
class AiWeeklyReportDocumentCodeLabelTest {

    private static final Pattern SCREAMING_CASE = Pattern.compile("\\b[A-Z][A-Z]+(?:_[A-Z]+)+\\b");

    private final AiWeeklyReportDocumentService service = new AiWeeklyReportDocumentService();

    /**
     * docs/contracts/ai-weekly-report-analysis-v1.schema.json의 목록을 그대로 옮긴 것이다.
     * docs/는 개인 브랜치 산출물이라 테스트가 파일을 읽으면 팀 main에서 깨진다.
     * 계약에 코드가 늘어나면 여기도 함께 늘려야 한다.
     */
    private static final List<String> EVIDENCE_CODES = List.of(
            "APPROVED_UNASSIGNED", "REQUESTED_PENDING", "OVERDUE", "DUE_SOON", "ON_HOLD",
            "CHECKLIST_NOT_STARTED", "CHECKLIST_STALLED", "RESOURCE_MISSING", "UNRESOLVED_MENTION",
            "WORKLOAD_CONCENTRATION", "NO_EFFORT_ESTIMATE", "NO_COMPLETION_CRITERIA",
            "CALENDAR_CONFLICT", "COMPLETED", "ON_TIME_COMPLETED");

    private static final List<String> COMPLETION_SIGNAL_CODES = List.of(
            "ASSIGNEE_SET", "DUE_AT_SET", "CHECKLIST_STARTED", "RESOURCE_LINKED", "MENTION_RESOLVED",
            "HOLD_STATE_RECORDED", "TASK_RESUMED", "SCOPE_DECISION_RECORDED", "NEXT_REVIEW_DATE_SET");

    @Test
    @DisplayName("계약의 모든 신호·완료 조건 코드가 문서에서 사람이 읽는 말로 바뀐다")
    void labelsEveryContractCode() {
        List<String> evidenceCodes = EVIDENCE_CODES;
        List<String> completionCodes = COMPLETION_SIGNAL_CODES;

        for (String language : List.of("KO", "EN")) {
            String html = render(evidenceCodes, completionCodes, language);
            assertThat(leakedCodes(html))
                    .as("%s 문서에 남은 내부 코드", language)
                    .isEmpty();
        }
    }

    /** 역할 enum도 같은 자리에 찍힌다. 계약 밖 값이 와도 코드를 노출하지 않는다. */
    @Test
    @DisplayName("계약 밖 역할 값이 와도 문서에 코드를 노출하지 않는다")
    void neverPrintsAnUnknownRoleCode() {
        String html = render(List.of("OVERDUE"), List.of("ASSIGNEE_SET"), "KO", "MEMBER");
        assertThat(leakedCodes(html)).isEmpty();
        assertThat(html).contains("확인할 수 없는 역할");
    }

    /** 업무표는 잘리면 밝히는데 일정표는 말없이 3건만 실었다. 같은 문서에서 규칙이 달랐다. */
    @Test
    @DisplayName("다음 기간 일정이 잘리면 몇 건 중 몇 건인지 밝힌다")
    void disclosesTruncatedTimeline() {
        List<CalendarConstraintView> events = new java.util.ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            events.add(new CalendarConstraintView("EVENT-" + i, "일정 " + i, "MEETING",
                    "확정 회의", "2026-07-28T01:00:00Z", "2026-07-28T02:00:00Z", List.of()));
        }

        assertThat(renderWithEvents(events, "KO")).contains("확정 일정 8건 중 3건 표시");
        assertThat(renderWithEvents(events, "EN")).contains("3 of 8 confirmed events shown");
        assertThat(renderWithEvents(events.subList(0, 3), "KO")).doesNotContain("중 3건 표시");
    }

    private Set<String> leakedCodes(String html) {
        // 문서 골격의 영문 장식 문구(TOESA · ACTION REVIEW 등)는 밑줄이 없어 걸리지 않는다.
        Matcher matcher = SCREAMING_CASE.matcher(html);
        return matcher.results().map(java.util.regex.MatchResult::group).collect(Collectors.toSet());
    }

    private String render(List<String> evidenceCodes, List<String> completionCodes, String language) {
        return render(evidenceCodes, completionCodes, language, "CURRENT_ASSIGNEE");
    }

    private String renderWithEvents(List<CalendarConstraintView> events, String language) {
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(), List.of(),
                new SnapshotMetricsView(1, 0, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 0, 1, 0, 0),
                List.of(), List.of(), events);
        return service.generate(view, "퇴사 팀", "Asia/Seoul", language).html();
    }

    private String render(List<String> evidenceCodes, List<String> completionCodes,
            String language, String actionOwnerRole) {
        DecisionView decision = new DecisionView("결정", "결정 질문", "KEEP_CURRENT_PLAN", "권고",
                "LEADER", actionOwnerRole, new DeadlineView("LEADER_DECISION_REQUIRED", null, null),
                List.of(), completionCodes);
        IssueView issue = new IssueView("P1", "RISK-001", "HIGH", "제목", "실제 업무", "영향",
                "MEDIUM", List.of(), List.of("실제 업무"), evidenceCodes, List.of(),
                "통합 판단", "필요 결정", decision);

        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(issue), List.of(),
                new SnapshotMetricsView(1, 0, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 0, 1, 0, 0),
                List.of(), List.of(), List.of());

        return service.generate(view, "퇴사 팀", "Asia/Seoul", language).html();
    }


    /**
     * 기간 제약 완화 뒤로 주간이 아닌 기간도 생성되는데 문구가 "주간"으로 굳어 있었다.
     * 지난달 리포트 제목이 "주간 업무 리포트"로, 파일명이 ai-weekly-로 나갔다.
     */
    @Test
    @DisplayName("문서 문구와 파일명이 실제 기간 종류를 따른다")
    void namesTheDocumentAfterTheActualPeriod() {
        var week = document(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27));
        assertThat(week.filename()).contains("toesa-ai-weekly-");
        assertThat(week.html()).contains("주간 업무 리포트").contains("이번 주 핵심");

        var month = document(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 7, 1));
        assertThat(month.filename()).contains("toesa-ai-monthly-");
        assertThat(month.html()).contains("월간 업무 리포트").contains("이번 달 핵심")
                .doesNotContain("주간 업무 리포트");

        var year = document(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
        assertThat(year.filename()).contains("toesa-ai-yearly-");
        assertThat(year.html()).contains("연간 업무 리포트").contains("올해 핵심");

        // 달 기준 5주차처럼 어느 단위에도 안 맞는 기간은 중립적으로 쓴다.
        var partial = document(LocalDate.of(2026, 7, 29), LocalDate.of(2026, 8, 1));
        assertThat(partial.filename()).contains("toesa-ai-period-");
        assertThat(partial.html()).contains("기간 업무 리포트").contains("이번 기간 핵심");
    }

    private com.teamproject.report.application.ReportDocumentService.ReportDocument document(
            LocalDate from, LocalDate toExclusive) {
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L, from, toExclusive, 1,
                "FINALIZED", "OPENAI", LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(), List.of(),
                new SnapshotMetricsView(1, 0, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 0, 1, 0, 0),
                List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", "KO");
    }
}
