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

    /**
     * 선언 문자열을 그대로 박아 두면 값 하나만 손대도 깨지고, 정작 지켜야 할 성질은 검사하지
     * 못한다. 여기서 고정하는 것은 활자 척도의 성질이다.
     *
     * <p>배경: 크기가 10·10.5·11·11.5로 갈리고 행간이 1.5·1.55·1.58·1.62로 흩어져 있었다.
     * 0.5px 단위는 렌더러가 반올림해 기기마다 다르게 찍히고, 650·750·800 굵기는 한글 폴백
     * 글꼴에 없어 브라우저가 합성한다. 인쇄본에서 특히 티가 난다.
     */
    @Test
    @DisplayName("문서 활자는 정수 크기·소수의 행간·400과 700 굵기만 쓴다")
    void keepsTheTypeScaleSmallAndPrintable() {
        String css = styleBlock(renderWithStatuses("KO", "TODO"));

        Set<String> sizes = matches(css, "font-size:([0-9.]+)px");
        assertThat(sizes).as("소수점 글자 크기").noneMatch(v -> v.contains("."));
        assertThat(sizes.stream().mapToDouble(Double::parseDouble).min().orElse(0))
                .as("가장 작은 글자").isGreaterThanOrEqualTo(11);
        assertThat(sizes).as("글자 크기 단계 수").hasSizeLessThanOrEqualTo(10);

        assertThat(matches(css, "font-weight:([0-9]+)"))
                .as("글꼴 굵기").containsExactlyInAnyOrder("400", "700");
        assertThat(matches(css, "line-height:([0-9.]+)(?![a-z%])"))
                .as("행간 단계 수").hasSizeLessThanOrEqualTo(4);
        assertThat(matches(css, "letter-spacing:(-?[0-9.]+em)"))
                .as("자간 단계 수").hasSizeLessThanOrEqualTo(2);
    }

    /**
     * 한글은 기본 줄바꿈이 어절 중간도 끊는다. {@code overflow-wrap:anywhere}까지 걸려 있으면
     * "담당자와" 가 "담당자" / "와" 로 갈린다. keep-all로 어절을 지키고, 정말 안 들어가는
     * 토막만 흘린다.
     */
    @Test
    @DisplayName("한글 어절을 끊지 않는 줄바꿈 규칙을 쓴다")
    void breaksKoreanLinesBetweenWords() {
        String css = styleBlock(renderWithStatuses("KO", "TODO"));

        assertThat(css).contains("word-break:keep-all");
        assertThat(css).doesNotContain("overflow-wrap:anywhere");
    }

    /**
     * 섹션마다 새 장으로 넘기면 매 섹션이 한 장을 조금씩 넘겨 뒷장이 거의 빈 채로 쌓인다.
     * 대신 카드와 표 행은 장 경계에서 쪼개지지 않아야 한다.
     */
    @Test
    @DisplayName("인쇄에서 섹션은 이어 흐르고 카드는 장 경계에서 쪼개지지 않는다")
    void keepsCardsWholeAcrossPrintedSheets() {
        String css = styleBlock(renderWithStatuses("KO", "TODO"));

        assertThat(css).doesNotContain(".page{break-before:page");
        assertThat(css).contains(".page-title,.page-desc,.section-heading{break-after:avoid");

        // break-inside:avoid를 선언한 규칙의 선택자 목록을 뽑아 실제로 그 블록이 들어 있는지 본다.
        String selectors = Pattern.compile("([^{}]+)\\{[^}]*break-inside:avoid")
                .matcher(css).results().map(r -> r.group(1)).collect(Collectors.joining(","));
        assertThat(selectors).as("break-inside:avoid 선언").isNotEmpty();
        for (String block : List.of(".signal", ".issue", ".decision-card", ".timeline-item", "tr")) {
            assertThat(selectors).as("%s 가 장 경계에서 쪼개지지 않는다", block).contains(block);
        }
    }

    private String styleBlock(String html) {
        int open = html.indexOf("<style>");
        int close = html.indexOf("</style>", open);
        assertThat(open).as("stylesheet 위치").isGreaterThan(-1);
        return html.substring(open, close);
    }

    private Set<String> matches(String css, String regex) {
        return Pattern.compile(regex).matcher(css).results()
                .map(r -> r.group(1)).collect(Collectors.toSet());
    }

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

    /**
     * 위험 카드가 AI가 쓴 위험 제목 대신 근거 업무 목록의 첫 업무를 제목으로 찍었다. 업무 13건에
     * 걸친 편중 위험이 "주간 리포트 버그 수정"으로 보였고, 대표 업무가 겹치는 서로 다른 위험 둘이
     * 같은 카드를 두 번 낸 것처럼 읽혔다. 결정 카드는 같은 자리에서 decision.title()을 쓰고 있었다.
     */
    @Test
    @DisplayName("위험 카드 제목은 근거 업무가 아니라 위험 제목을 쓴다")
    void titlesRiskCardsWithTheRiskNotTheFirstEvidenceTask() {
        String html = renderWithIssueTitle("업무 편중", "주간 리포트 버그 수정");

        assertThat(html).contains("<h3>업무 편중</h3>");
        assertThat(html).doesNotContain("<h3>주간 리포트 버그 수정</h3>");
        // 근거 목록과 "적용 업무"에는 업무 제목이 그대로 남아야 한다.
        assertThat(html).contains("주간 리포트 버그 수정");
    }

    /**
     * 결정 카드의 "적용 업무"가 근거 첫 업무만 적어, 13건에 걸친 재배분 결정이 한 건짜리로 보였다.
     * 같은 카드의 질문 문장은 13건을 모두 부르고 있어서 한 카드가 두 개의 범위를 말했다.
     */
    @Test
    @DisplayName("결정 카드의 적용 업무는 근거가 여럿이면 나머지 건수를 밝힌다")
    void countsEveryTaskADecisionAppliesTo() {
        assertThat(renderWithAppliedTasks("KO", "재배분 대상", "검색 버그 수정", "결제 API 계약 정리"))
                .contains("재배분 대상 외 2건");
        assertThat(renderWithAppliedTasks("EN", "재배분 대상", "검색 버그 수정", "결제 API 계약 정리"))
                .contains("재배분 대상 and 2 more");
    }

    /** 근거가 한 건이면 건수를 덧붙이지 않는다. */
    @Test
    @DisplayName("근거가 한 건인 결정은 업무 제목만 적는다")
    void namesTheSingleTaskADecisionApplies() {
        assertThat(renderWithAppliedTasks("KO", "검색 버그 수정"))
                .contains("검색 버그 수정").doesNotContain("외 0건");
    }

    @Test
    @DisplayName("중복 업무 제목은 근거에서만 마감일로 구분한다")
    void disambiguatesDuplicateTaskTitlesOnlyWhereTheTitleStandsAlone() {
        String title = "주간 리포트 문구 검토";
        SnapshotTaskView completed = task("TASK-1", title, "COMPLETED",
                "2026-07-15T07:00:00Z", "COMPLETED_ON_TIME");
        SnapshotTaskView todo = task("TASK-2", title, "TODO",
                "2026-07-24T04:00:00Z", "DUE_SOON");
        SnapshotTaskView unique = task("TASK-3", "캘린더 배포 준비", "COMPLETED",
                "2026-07-20T01:00:00Z", "COMPLETED_ON_TIME");
        AchievementView achievement = new AchievementView("AVAILABLE", "시연 기반을 확보했습니다.",
                "완료 결과를 확인했습니다.",
                List.of("TASK-1", "TASK-3"), List.of(title, "캘린더 배포 준비"));
        IssueView issue = new IssueView("P1", "RISK-001", "MEDIUM", "체크리스트 미착수", title,
                "영향", "HIGH", List.of("TASK-2"), List.of(title),
                List.of("CHECKLIST_NOT_STARTED"), List.of(), "통합 판단", "착수 기준 확정", null);
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 22), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 22, 9, 0), "/download",
                null, achievement, List.of(issue), List.of(),
                new SnapshotMetricsView(3, 67, 100, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 1, 0, 0, 2),
                List.of(completed, todo, unique), List.of(), List.of(), List.of());

        String html = service.generate(view, "퇴사 팀", "Asia/Seoul", "KO").html();

        // 마감일 안의 하이픈 뒤에는 word joiner가 들어간다(줄바꿈 방지). 눈에 보이는 글자만 본다.
        assertThat(visibleText(html))
                .contains(title + "(07-15 마감)")
                .contains(title + "(07-24 마감)")
                .contains("캘린더 배포 준비");
        assertThat(html).contains("<strong class=\"task-title\">" + title + "</strong>")
                .doesNotContain("<strong class=\"task-title\">" + title + "(");
    }

    /** 폭이 0인 조판용 문자(word joiner 등)를 걷어낸, 독자가 실제로 보는 글자만 남긴다. */
    private String visibleText(String html) {
        return html.replace("⁠", "").replace("​", "");
    }

    private SnapshotTaskView task(String ref, String title, String status, String dueAt, String dueState) {
        return new SnapshotTaskView(ref, title, "라벨", status, "NORMAL",
                null, "개발자", null, dueAt, null, dueState,
                null, null, null, List.of());
    }

    private String renderWithAppliedTasks(String language, String... taskTitles) {
        List<String> titles = List.of(taskTitles);
        List<String> refs = new java.util.ArrayList<>();
        List<SnapshotTaskView> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < titles.size(); i++) {
            refs.add("TASK-" + i);
            tasks.add(new SnapshotTaskView("TASK-" + i, titles.get(i), "라벨", "TODO", "NORMAL",
                    null, "개발자", null, "2026-07-23T15:00:00Z", null, "DUE_SOON",
                    null, null, null, List.of()));
        }
        DecisionView decision = new DecisionView("재배분해야 합니다", "재배분할까요?",
                "REBALANCE_WORK", "권고", "LEADER", "SELECTED_MEMBER", null,
                List.of("REBALANCE_ASSIGNEE"), List.of("ASSIGNEE_SET"));
        IssueView issue = new IssueView("P1", "RISK-001", "HIGH", "업무 편중", titles.get(0),
                "영향", "HIGH", refs, titles,
                List.of("WORKLOAD_CONCENTRATION"), List.of(), "통합 판단", "필요 결정", decision);
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(issue), List.of(),
                new SnapshotMetricsView(titles.size(), 0, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, titles.size(), 0, 0, 0),
                tasks, List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", language).html();
    }

    /** 위험 제목이 비어 오면 카드가 제목 없이 나가지 않도록 기존 값으로 돌아간다. */
    @Test
    @DisplayName("위험 제목이 비면 근거 업무 제목으로 돌아간다")
    void fallsBackToTheTaskTitleWhenTheRiskTitleIsBlank() {
        assertThat(renderWithIssueTitle(" ", "주간 리포트 버그 수정"))
                .contains("<h3>주간 리포트 버그 수정</h3>");
    }

    private String renderWithIssueTitle(String issueTitle, String taskTitle) {
        SnapshotTaskView task = new SnapshotTaskView("TASK-1", taskTitle, "라벨",
                "TODO", "NORMAL", null, "개발자", null, "2026-07-23T15:00:00Z", null, "DUE_SOON",
                null, null, null, List.of());
        IssueView issue = new IssueView("P1", "RISK-001", "HIGH", issueTitle, taskTitle,
                "영향", "HIGH", List.of("TASK-1"), List.of(taskTitle),
                List.of("WORKLOAD_CONCENTRATION"), List.of(), "통합 판단", "필요 결정", null);
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(issue), List.of(),
                new SnapshotMetricsView(1, 0, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 1, 0, 0, 0),
                List.of(task), List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", "KO").html();
    }

    /**
     * 업무표 상태 칩만 REJECTED·CANCELLED를 원문 그대로 찍고 있었다. 나머지 칸이 "보류",
     * "진행 중"인 표에서 두 칸만 영문 코드였다. {@link #SCREAMING_CASE}는 밑줄을 요구해서
     * 밑줄 없는 이 두 코드를 지나쳤다. 상태 코드는 따로 확인한다.
     */
    @Test
    @DisplayName("반려·취소 업무의 상태 칩에 영문 코드를 찍지 않는다")
    void labelsClosedTaskStatusesInProse() {
        String ko = renderWithStatuses("KO");
        assertThat(ko).contains(">반려<").contains(">취소<");
        assertThat(ko).doesNotContain("REJECTED").doesNotContain("CANCELLED");

        String en = renderWithStatuses("EN");
        assertThat(en).contains(">Rejected<").contains(">Cancelled<");
        assertThat(en).doesNotContain("REJECTED").doesNotContain("CANCELLED");
    }

    /** 계약에 없는 상태가 흘러와도 코드를 그대로 노출하지 않는다. */
    @Test
    @DisplayName("계약 밖 상태 값이 와도 문서에 코드를 노출하지 않는다")
    void neverPrintsAnUnknownStatusCode() {
        assertThat(renderWithStatuses("KO", "ARCHIVED")).doesNotContain("ARCHIVED");
    }

    private String renderWithStatuses(String language, String... statuses) {
        String[] values = statuses.length == 0
                ? new String[] {"REJECTED", "CANCELLED"} : statuses;
        List<SnapshotTaskView> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < values.length; i++) {
            tasks.add(new SnapshotTaskView("TASK-" + i, "닫힌 업무 " + i, "라벨", values[i],
                    "NORMAL", null, "개발자", null, "2026-07-23T15:00:00Z", null,
                    "CLOSED_UNFINISHED", null, null, null, List.of()));
        }
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(), List.of(),
                new SnapshotMetricsView(tasks.size(), 0, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 0, 0, 0, 0),
                tasks, List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", language).html();
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

    /**
     * "기간 직후 3일 일정"이 기간 안 지난 일정만 보여 줬다. Snapshot의 일정 창은 기간 시작부터
     * 열려 있고(마감이 일정 구간에 걸치는지 보려면 그 범위가 필요하다) 앞에서 세 건을 자르니
     * 정작 마감과 겹칠 다음 일정이 밀려났다. 같은 리포트가 "디자인 리뷰와 일정이 겹친다"고
     * 쓰면서 표에는 디자인 리뷰가 없는 상태였다.
     */
    @Test
    @DisplayName("기간 직후 일정만 표시하고 기간 안 일정은 빼놓는다")
    void showsOnlyTheEventsAfterThePeriod() {
        List<CalendarConstraintView> events = List.of(
                event("EVENT-1", "회고", "2026-07-22T02:00:00Z"),            // 07-22 11:00 KST, 기간 안
                event("EVENT-2", "스프린트 계획", "2026-07-26T16:00:00Z"),   // 07-27 01:00 KST, 기간 종료일
                event("EVENT-3", "디자인 리뷰", "2026-07-28T03:00:00Z"));    // 07-28 12:00 KST, 기간 이후

        String html = renderWithEvents(events, "KO");

        assertThat(html).contains("스프린트 계획").contains("디자인 리뷰");
        assertThat(html).doesNotContain("회고");
    }

    /** 걸러 낸 뒤 남는 일정이 없으면 잘림 안내가 아니라 없음 안내가 나가야 한다. */
    @Test
    @DisplayName("기간 직후 일정이 하나도 없으면 없다고 밝힌다")
    void saysSoWhenNothingFollowsThePeriod() {
        List<CalendarConstraintView> inPeriodOnly = List.of(
                event("EVENT-1", "회고", "2026-07-21T02:00:00Z"),
                event("EVENT-2", "중간 점검", "2026-07-22T02:00:00Z"));

        assertThat(renderWithEvents(inPeriodOnly, "KO"))
                .contains("기간 직후 3일 안에 확정된 일정이 없습니다.")
                .doesNotContain("건 표시");
    }

    /** 잘림 안내의 모수는 조회한 전체가 아니라 걸러 낸 뒤의 개수여야 한다. */
    @Test
    @DisplayName("잘림 안내는 기간 직후 일정만 세어 밝힌다")
    void countsTruncationAgainstTheFilteredEvents() {
        List<CalendarConstraintView> events = new java.util.ArrayList<>();
        events.add(event("EVENT-0", "기간 안 회고", "2026-07-22T02:00:00Z"));
        for (int i = 1; i <= 4; i++) {
            events.add(event("EVENT-" + i, "이후 일정 " + i, "2026-07-28T01:00:00Z"));
        }

        assertThat(renderWithEvents(events, "KO")).contains("확정 일정 4건 중 3건 표시");
    }

    /**
     * 결정 기한 배지가 계약의 네 값 중 MEETING_END만 빠뜨려 코드를 그대로 찍었다. 상태 칩과
     * 같은 종류의 누락이다. LEADER_DECISION_REQUIRED는 "기한을 팀장이 정해야 한다"는 뜻인데
     * "회의 종료 전"으로 찍혀, 정해지지 않은 기한을 정해진 것처럼 말하고 있었다.
     */
    @Test
    @DisplayName("결정 기한 배지가 계약의 네 값을 모두 사람이 읽는 말로 바꾼다")
    void labelsEveryDeadlineSource() {
        assertThat(renderWithDeadline("MEETING_END", null)).contains("회의 종료 전");
        assertThat(renderWithDeadline("LEADER_DECISION_REQUIRED", null)).contains("팀장 결정 필요");
        assertThat(renderWithDeadline("TASK_DUE", null)).contains("업무 마감 기준");
        assertThat(renderWithDeadline("CALENDAR_EVENT", "스프린트 계획")).contains("스프린트 계획 전");

        for (String source : List.of("MEETING_END", "LEADER_DECISION_REQUIRED", "TASK_DUE")) {
            assertThat(leakedCodes(renderWithDeadline(source, null)))
                    .as("%s 배지에 남은 코드", source).isEmpty();
        }
    }

    /** 계약 밖 값이 와도 코드를 노출하지 않는다. */
    @Test
    @DisplayName("계약 밖 기한 값이 와도 배지에 코드를 찍지 않는다")
    void neverPrintsAnUnknownDeadlineCode() {
        assertThat(renderWithDeadline("SOMEDAY", null)).doesNotContain("SOMEDAY");
    }

    private String renderWithDeadline(String source, String referenceTitle) {
        DecisionView decision = new DecisionView("결정 제목", "질문?", "KEEP_CURRENT_PLAN",
                "권고", "LEADER", "CURRENT_ASSIGNEE",
                new DeadlineView(source, referenceTitle == null ? null : "EVENT-1", referenceTitle),
                List.of("START_CHECKLIST"), List.of("CHECKLIST_STARTED"));
        SnapshotTaskView task = new SnapshotTaskView("TASK-1", "검색 버그 수정", "라벨", "TODO",
                "NORMAL", null, "개발자", null, "2026-07-23T15:00:00Z", null, "DUE_SOON",
                null, null, null, List.of());
        IssueView issue = new IssueView("P1", "RISK-001", "HIGH", "체크리스트 미착수",
                "검색 버그 수정", "영향", "HIGH", List.of("TASK-1"), List.of("검색 버그 수정"),
                List.of("CHECKLIST_NOT_STARTED"), List.of(), "통합 판단", "체크리스트 착수 확인",
                decision);
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(issue), List.of(),
                new SnapshotMetricsView(1, 0, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 1, 0, 0, 0),
                List.of(task), List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", "KO").html();
    }

    /**
     * 진행률 카드가 "8/14"로 기간 업무 전체를 모수로 썼다. 지표 카드의 완료율은 반려·취소를 뺀
     * 모수로 계산되므로, 같은 페이지가 두 개의 모수를 말하고 있었다. 요약도 완료·진행·보류만
     * 적어 합이 기간 업무 수에 못 미쳤다.
     */
    @Test
    @DisplayName("진행률 카드와 요약이 완료율 모수를 기간 업무 수와 구분해 밝힌다")
    void ratesProgressAgainstTheActionableTasks() {
        // 기간 업무 14건 = workflow 11건 + 반려·취소 3건
        String html = renderWithWorkflow("KO", 14, new SnapshotWorkflowView(0, 0, 1, 2, 0, 8));

        assertThat(html).contains("<strong>8/11</strong>");
        assertThat(html).doesNotContain("<strong>8/14</strong>");
        assertThat(html).contains("기간 업무 14건 중 완료 8건, 진행 중 2건, 착수 전 1건입니다. "
                + "반려·취소 3건은 완료율 모수에서 제외했습니다.");
        assertThat(html).doesNotContain("기간 업무 14건 중 완료 8건, 진행 중 2건, 보류 0건");
    }

    /** 반려·취소가 없으면 군더더기를 붙이지 않는다. */
    @Test
    @DisplayName("반려·취소가 없으면 모수 설명을 덧붙이지 않는다")
    void staysQuietWhenNothingWasRejectedOrCancelled() {
        String html = renderWithWorkflow("KO", 11, new SnapshotWorkflowView(0, 0, 1, 2, 0, 8));

        assertThat(html).contains("<strong>8/11</strong>");
        assertThat(html).doesNotContain("완료율 모수에서 제외");
    }

    /**
     * 모수를 바꾸기 전에 저장된 revision은 completionRatePercent가 기간 업무 전체 기준이다.
     * 그 문서에서 분수만 새 모수로 그리면 지표 카드와 막대가 서로 다른 답을 말한다.
     */
    @Test
    @DisplayName("저장된 완료율과 어긋나면 분수 대신 완료 건수만 적는다")
    void dropsTheFractionWhenItWouldContradictTheStoredRate() {
        // 옛 계산(8/14=57%)으로 저장된 값. 새 모수(11)로 그리면 73%라 어긋난다.
        String html = renderWithWorkflow("KO", 14, new SnapshotWorkflowView(0, 0, 1, 2, 0, 8), 57);

        assertThat(html).contains("<strong>완료 8건</strong>");
        assertThat(html).doesNotContain("8/11").doesNotContain("8/14");
    }

    @Test
    @DisplayName("기한 준수율 원인과 전 기간 대비 방향을 사람이 읽는 문장으로 밝힌다")
    void explainsLateCompletionAndRateDirection() {
        SnapshotTaskView onTime1 = task("TASK-1", "완료 1", "COMPLETED",
                "2026-07-18T01:00:00Z", "COMPLETED_ON_TIME");
        SnapshotTaskView onTime2 = task("TASK-2", "완료 2", "COMPLETED",
                "2026-07-19T01:00:00Z", "COMPLETED_ON_TIME");
        SnapshotTaskView late = task("TASK-3", "완료 3", "COMPLETED",
                "2026-07-20T01:00:00Z", "COMPLETED_LATE");
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 22), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 22, 9, 0), "/download",
                null, null, List.of(), List.of(),
                new SnapshotMetricsView(3, 100, 67, 0, null),
                new SnapshotComparisonView("AVAILABLE", null, null, null, 7, -23, null),
                new SnapshotWorkflowView(0, 0, 0, 0, 0, 3),
                List.of(onTime1, onTime2, late), List.of(), List.of(), List.of());

        String html = service.generate(view, "퇴사 팀", "Asia/Seoul", "KO").html();

        assertThat(html)
                .contains("완료 업무 중 1건이 마감 후 완료되어 기한 준수율이 67%입니다.")
                .contains("완료율은 지난 기간 대비 7%p 개선되었습니다.")
                .contains("기한 준수율은 지난 기간 대비 23%p 하락했습니다.")
                .doesNotContain("%p 변화했습니다.");
    }

    private String renderWithWorkflow(String language, int periodTaskCount,
            SnapshotWorkflowView workflow) {
        return renderWithWorkflow(language, periodTaskCount, workflow, 73);
    }

    private String renderWithWorkflow(String language, int periodTaskCount,
            SnapshotWorkflowView workflow, Integer completionRatePercent) {
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(), List.of(),
                new SnapshotMetricsView(periodTaskCount, completionRatePercent, 63, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                workflow, List.of(), List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", language).html();
    }

    private CalendarConstraintView event(String ref, String title, String startAtUtc) {
        return new CalendarConstraintView(ref, title, "MEETING", "확정 회의",
                startAtUtc, startAtUtc, List.of());
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
                List.of(), List.of(), events, List.of());
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
                List.of(), List.of(), List.of(), List.of());

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
                List.of(), List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", "KO");
    }

    /**
     * MAX_TASKS(100) 때문에 AI가 본 업무가 기간 전체보다 적을 수 있다. 연간처럼 긴 기간에서는
     * 거의 항상 그렇다. 표와 일정은 잘림을 밝히는데 정작 분석 대상이 잘린 것만 감추면
     * 전 기간을 본 결론으로 읽힌다.
     */
    @Test
    @DisplayName("분석이 본 업무가 기간 전체보다 적으면 문서에 밝힌다")
    void disclosesThatTheAnalysisSawFewerTasksThanThePeriod() {
        assertThat(renderWithTaskCounts(500, 100, "KO"))
                .contains("AI 분석은 기간 업무 500건 중 100건을 대상으로 했습니다");
        assertThat(renderWithTaskCounts(500, 100, "EN"))
                .contains("covered 100 of 500 tasks");
        // 잘리지 않았으면 아무 말도 붙이지 않는다.
        assertThat(renderWithTaskCounts(12, 12, "KO")).doesNotContain("건을 대상으로 했습니다");
    }

    /**
     * 회의록 표준은 결정마다 담당과 기한을 사람이 적게 둔다. 이 문서는 AI 권고까지만 찍혀
     * 있어 종이로 뽑으면 적을 자리가 없었다. 화면에는 빈 칸이 필요 없으므로 인쇄에만 띄운다.
     */
    @Test
    @DisplayName("결정마다 인쇄본에만 나오는 기록란이 붙는다")
    void printsARecordingBlockOnEveryDecision() {
        String html = render(List.of("OVERDUE"), List.of("ASSIGNEE_SET"), "KO");

        assertThat(html).contains("회의 기록").contains("결정 &#9744; 승인");
        // 화면에서는 숨고 인쇄에서만 뜬다. 두 규칙이 다 있어야 한다.
        assertThat(html).contains(".record{display:none}").contains(".record{display:block");

        assertThat(render(List.of("OVERDUE"), List.of("ASSIGNEE_SET"), "EN"))
                .contains("Recorded in the meeting").contains("Owner");
    }

    /**
     * 마감이 날짜까지만 찍혀 "오늘 오전"과 "오늘 마감 직전"이 같아 보였다. 기본 리포트는 시각을
     * 찍는데 AI 리포트만 빠져 있었다. 자정은 시각 미지정의 저장 형태라 날짜만 적는다.
     */
    @Test
    @DisplayName("마감 시각이 있으면 시각까지 적고 자정이면 날짜만 적는다")
    void printsTheDueTimeWhenItCarriesInformation() {
        // 09:00 KST = 전날 00:00 UTC. 그룹 시간대로 환산해서 판정해야 한다.
        assertThat(renderWithDue("2026-07-24T09:00:00Z")).contains("07-24 18:00");
        assertThat(renderWithDue("2026-07-23T15:00:00Z"))
                .contains("07-24").doesNotContain("07-24 00:00");
    }

    private String renderWithDue(String dueAtUtc) {
        SnapshotTaskView task = new SnapshotTaskView("TASK-1", "결제 실패 로그 확인", "라벨",
                "IN_PROGRESS", "NORMAL", null, "개발자", null, dueAtUtc, null, "OVERDUE",
                null, null, null, List.of());
        IssueView issue = new IssueView("P1", "RISK-001", "HIGH", "제목", "결제 실패 로그 확인",
                "영향", "MEDIUM", List.of("TASK-1"), List.of("결제 실패 로그 확인"),
                List.of("OVERDUE"), List.of(), "통합 판단", "필요 결정", null);
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(issue), List.of(),
                new SnapshotMetricsView(1, 0, null, 1, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 0, 1, 0, 0),
                List.of(task), List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", "KO").html();
    }

    /**
     * 업무표 캡션이 잘린 배열을 모수로 써서, 같은 페이지 KPI가 105건인데 표는 "기간 업무
     * 100건 중 N건 표시"라고 말했다. 한 페이지가 두 개의 전체 수를 말하는 셈이다.
     */
    @Test
    @DisplayName("업무표 캡션의 모수는 잘린 배열이 아니라 확정 지표를 따른다")
    void countsTheTaskTableAgainstTheConfirmedMetric() {
        assertThat(renderWithTaskCounts(105, 100, "KO")).contains("기간 업무 105건 중 12건 표시");
        assertThat(renderWithTaskCounts(105, 100, "EN")).contains("12 of 105 tasks shown");
    }

    private String renderWithTaskCounts(int periodTaskCount, int analyzedTasks, String language) {
        List<SnapshotTaskView> tasks = new java.util.ArrayList<>();
        for (int i = 1; i <= analyzedTasks; i++) {
            tasks.add(new SnapshotTaskView("TASK-" + i, "업무 " + i, "라벨", "TODO", "NORMAL",
                    null, null, null, null, null, null, null, null, null, List.of()));
        }
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 1, 2, 9, 0), "/download",
                null, null, List.of(), List.of(),
                new SnapshotMetricsView(periodTaskCount, 10, null, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 0, 1, 0, 0),
                tasks, List.of(), List.of(), List.of());
        return service.generate(view, "퇴사 팀", "Asia/Seoul", language).html();
    }

    /**
     * 위험 후보가 없으면 3페이지가 "없습니다" 한 줄이었다. A4 4장 중 한 장이 거의 백지가 되고,
     * 유료 사용자에게는 AI가 아무 일도 안 한 것으로 읽힌다. policy engine은 실제로 항목
     * 전체를 검사하므로 무엇을 봤는지 그대로 보여 준다.
     */
    @Test
    @DisplayName("위험이 없으면 무엇을 검사했는지 항목별로 보여 준다")
    void showsWhatWasCheckedWhenNothingNeedsAction() {
        List<RiskCheckView> checks = List.of(
                new RiskCheckView("APPROVED_UNASSIGNED", "담당자 미지정", 0),
                new RiskCheckView("OVERDUE_ACTIVE", "마감 초과", 0),
                new RiskCheckView("APPROVAL_PENDING", "승인 대기", 0));

        String html = renderWithChecks(checks, "KO");
        assertThat(html)
                .contains("확인한 위험 항목 3개")
                .contains("담당자 미지정")
                .contains("마감 초과")
                .contains("승인 대기")
                .doesNotContain("조치가 필요한 위험 업무가 없습니다");

        assertThat(renderWithChecks(checks, "EN")).contains("Checked 3 risk signals");
        // 검사 목록이 없는 옛 revision은 예전 문구로 되돌아간다.
        assertThat(renderWithChecks(List.of(), "KO")).contains("조치가 필요한 위험 업무가 없습니다");
    }

    private String renderWithChecks(List<RiskCheckView> checks, String language) {
        AiWeeklyReportView view = new AiWeeklyReportView(1L, 7L,
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27), 1, "FINALIZED", "OPENAI",
                LocalDateTime.of(2026, 7, 27, 9, 0), "/download",
                null, null, List.of(), List.of(),
                new SnapshotMetricsView(4, 100, 100, 0, null),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(0, 0, 0, 0, 0, 4),
                List.of(), List.of(), List.of(), checks);
        return service.generate(view, "퇴사 팀", "Asia/Seoul", language).html();
    }
}
