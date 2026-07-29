package com.teamproject.report.manual;

import com.teamproject.TeamProjectApplication;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiNarrativeGenerator;
import com.teamproject.report.application.ReportContracts.ActionNarrativeItem;
import com.teamproject.report.application.ReportContracts.AiGenerationInput;
import com.teamproject.report.application.ReportContracts.AiGenerationResult;
import com.teamproject.report.application.ReportContracts.DecisionNarrativeItem;
import com.teamproject.report.application.ReportContracts.Narrative;
import com.teamproject.report.application.ReportContracts.NarrativeItem;
import com.teamproject.report.application.ReportContracts.RiskNarrativeItem;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskActivityEventRepository;
import com.teamproject.task.domain.TaskChecklistItem;
import com.teamproject.task.domain.TaskChecklistItemRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

public final class ManualAiReportApplication {
    private static final String USERNAME = "ai_report_tester";
    private static final String PASSWORD = "password123!";
    private static final ZoneId GROUP_ZONE = ZoneId.of("Asia/Seoul");

    private ManualAiReportApplication() {}

    public static void main(String[] args) {
        new SpringApplicationBuilder(TeamProjectApplication.class, ManualConfiguration.class)
                .run(args);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ManualConfiguration {
        @Bean
        @Primary
        AiNarrativeGenerator manualFakeAiNarrativeGenerator() {
                return input -> new AiGenerationResult(
                        practicalNarrative(input),
                        "fake-ai-manual-test",
                    120,
                    80,
                    200);
        }

        @Bean
        ApplicationRunner manualAiReportFixtureRunner(
                UserRepository users,
                GroupRepository groups,
                GroupMemberRepository members,
                TaskRepository tasks,
                TaskChecklistItemRepository checklistItems,
                TaskActivityEventRepository activityEvents,
                PasswordEncoder passwordEncoder,
                DataSource dataSource,
                PlatformTransactionManager transactionManager) {
            return args -> {
                requireIsolatedManualDatabase(dataSource);
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    if (users.existsByUsernameIgnoreCase(USERNAME)) return;

                    User leaderUser = users.save(new User(
                            USERNAME,
                            "ai-report-tester@local.test",
                            passwordEncoder.encode(PASSWORD),
                            "AI 리포트 팀장",
                            true));

                    Group group = Group.team(
                            "AI 주간 리포트 테스트팀",
                            "Fake AI와 격리 데이터베이스를 사용하는 수동 테스트 그룹",
                            GROUP_ZONE.getId(),
                            leaderUser);
                    ReflectionTestUtils.setField(group, "membershipPlan", Group.MembershipPlan.PAID);
                    group = groups.save(group);
                    GroupMember leader = members.save(GroupMember.leader(group, leaderUser));
                    List<GroupMember> team = new ArrayList<>();
                    team.add(leader);
                    for (int index = 1; index <= 4; index++) {
                        User memberUser = users.save(new User(
                                "ai_report_member" + index,
                                "ai-report-member" + index + "@local.test",
                                passwordEncoder.encode(PASSWORD),
                                "테스트 팀원 " + index,
                                true));
                        team.add(members.save(GroupMember.member(group, memberUser)));
                    }

                    LocalDate reportWeekStart = LocalDate.now(GROUP_ZONE)
                            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                            .minusWeeks(1);
                    seedWeeklyActivity(
                            group,
                            team,
                            reportWeekStart,
                            tasks,
                            checklistItems,
                            activityEvents);
                });
            };
        }
    }

    private static void requireIsolatedManualDatabase(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String jdbcUrl = connection.getMetaData().getURL();
            String expectedUrl = "jdbc:mysql://127.0.0.1:13307/worktaskflow_ai_manual";
            if (jdbcUrl == null
                    || (!jdbcUrl.equals(expectedUrl) && !jdbcUrl.startsWith(expectedUrl + "?"))) {
                throw new IllegalStateException(
                        "AI weekly report manual fixture requires the isolated local MySQL database.");
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Could not verify the AI weekly report manual fixture database.", exception);
        }
    }

    private static Narrative practicalNarrative(AiGenerationInput input) {
        boolean english = "en".equals(input.language());
        boolean partial = input.metrics().historyCoverage().partial();
        List<String> taskRefs = input.context().tasks().stream()
                .limit(1)
                .map(task -> task.taskRef())
                .toList();
        List<String> completedTaskRefs = input.context().tasks().stream()
                .filter(task -> "COMPLETED".equals(task.status()))
                .limit(3)
                .map(task -> task.taskRef())
                .toList();
        List<String> delayedTaskRefs = input.context().tasks().stream()
                .filter(task -> "OVERDUE".equals(task.dueState()))
                .limit(3)
                .map(task -> task.taskRef())
                .toList();
        String ownerRef = input.context().memberRefs().stream()
                .findFirst()
                .orElse("MEMBER-01");
        List<String> limitations = partial
                ? List.of("coverage.partial")
                : List.of();
        NarrativeItem summary = new NarrativeItem(
                english
                        ? "The confirmed workflow needs a focused execution review."
                        : "확정된 업무 흐름을 실행 관점에서 점검해야 합니다.",
                List.of("tasks.total"), taskRefs, List.of());
        List<NarrativeItem> changes = List.of(new NarrativeItem(
                english
                        ? "Completion flow and remaining work should be reviewed together."
                        : "완료 흐름과 남은 업무를 함께 비교해 봐야 합니다.",
                List.of("tasks.completed", "tasks.total"), taskRefs, List.of()));
        List<NarrativeItem> achievements = List.of(new NarrativeItem(
                 english
                         ? "Completed work provides a concrete base for the next plan."
                         : "완료된 업무가 다음 실행 계획의 근거가 됩니다.",
                List.of("tasks.completed"), completedTaskRefs, List.of()));
        List<RiskNarrativeItem> risks = List.of(new RiskNarrativeItem(
                "HIGH",
                 english
                         ? "Delayed work may block the next execution window."
                         : "지연 업무가 다음 실행 구간의 병목이 될 수 있습니다.",
                List.of("tasks.delayed"), delayedTaskRefs, List.of()));
        List<ActionNarrativeItem> actions = List.of(new ActionNarrativeItem(
                1,
                english
                        ? "Review the delayed work and confirm its next action."
                        : "지연 업무의 다음 조치와 검토 시점을 확정하세요.",
                english
                        ? "A concrete follow-up prevents the blocker from carrying forward."
                        : "구체적인 후속 조치가 병목의 다음 주 이월을 막습니다.",
                 ownerRef,
                List.of("tasks.delayed"), delayedTaskRefs, List.of()));
        List<DecisionNarrativeItem> decisions = List.of(new DecisionNarrativeItem(
                english
                        ? "Should the leader change the priority of delayed work?"
                        : "지연 업무의 우선순위를 조정할지 결정하시겠습니까?",
                english
                         ? "The decision changes which work receives attention first."
                         : "이 결정에 따라 다음 실행 순서가 달라집니다.",
                List.of("tasks.delayed"), delayedTaskRefs, List.of()));
        List<NarrativeItem> limitationItems = limitations.stream()
                .map(key -> new NarrativeItem(
                        english
                                ? "Some activity history is outside the complete tracking range."
                                : "일부 활동은 완전한 추적 범위 밖의 데이터일 수 있습니다.",
                        List.of(key), List.of(), List.of()))
                .toList();
        return new Narrative(
                english
                        ? "Weekly execution priorities are ready for review."
                        : "이번 주 실행 우선순위를 검토하세요.",
                summary, changes, achievements, risks, actions, decisions, limitationItems);
    }

    private static Narrative narrative(AiGenerationInput input) {
        boolean partial = input.metrics().historyCoverage().partial();
        if ("en".equals(input.language())) {
            return new Narrative(
                    "Weekly workflow priorities are ready for review.",
                    "Completion is moving forward while overdue work and remaining checklist items need attention.",
                    List.of(new NarrativeItem(
                            "Completed work is visible in the weekly flow.",
                            List.of("tasks.completed"))),
                    List.of(new NarrativeItem(
                            "Overdue work should be reviewed first.",
                            List.of("tasks.delayed"))),
                    List.of(new NarrativeItem(
                            "Reorder active work and unfinished checklist items.",
                            List.of("checklist.completed"))),
                    partial
                            ? List.of(new NarrativeItem(
                                    "Some activity history is outside the complete tracking range.",
                                    List.of("coverage.partial")))
                            : List.of());
        }
        return new Narrative(
                "이번 주 업무 흐름의 우선순위를 확인하세요.",
                "완료 흐름은 이어지고 있으며 지연 업무와 남은 체크리스트를 함께 점검해야 합니다.",
                List.of(new NarrativeItem(
                        "완료된 업무 흐름이 확인됩니다.",
                        List.of("tasks.completed"))),
                List.of(new NarrativeItem(
                        "지연 업무를 먼저 확인해야 합니다.",
                        List.of("tasks.delayed"))),
                List.of(new NarrativeItem(
                        "진행 중 업무와 남은 체크리스트의 우선순위를 조정하세요.",
                        List.of("checklist.completed"))),
                partial
                        ? List.of(new NarrativeItem(
                                "일부 업무는 활동 이력 수집 범위가 제한됩니다.",
                                List.of("coverage.partial")))
                        : List.of());
    }

    private static void seedWeeklyActivity(
            Group group,
            List<GroupMember> team,
            LocalDate reportWeekStart,
            TaskRepository tasks,
            TaskChecklistItemRepository checklistItems,
            TaskActivityEventRepository activityEvents) {
        List<TaskFixture> fixtures = List.of(
                // 이전 주 18건
                new TaskFixture(-1, "요구사항 범위 확정", Task.Status.COMPLETED, Task.Priority.HIGH, 0, 0, 2, 2, 3, 3),
                new TaskFixture(-1, "로그인 화면 오류 수정", Task.Status.COMPLETED, Task.Priority.URGENT, 1, 0, 1, 2, 2, 2),
                new TaskFixture(-1, "데이터베이스 인덱스 검토", Task.Status.COMPLETED, Task.Priority.NORMAL, 2, 1, 4, 4, 2, 2),
                new TaskFixture(-1, "대시보드 지표 정의", Task.Status.COMPLETED, Task.Priority.HIGH, 3, 1, 3, 5, 3, 2),
                new TaskFixture(-1, "모바일 메뉴 개선", Task.Status.COMPLETED, Task.Priority.NORMAL, 4, 2, 5, 5, 2, 2),
                new TaskFixture(-1, "알림 정책 문서화", Task.Status.COMPLETED, Task.Priority.LOW, 0, 2, 6, 6, 2, 2),
                new TaskFixture(-1, "캘린더 동기화 점검", Task.Status.COMPLETED, Task.Priority.NORMAL, 1, 3, 5, 6, 3, 3),
                new TaskFixture(-1, "권한 매트릭스 검증", Task.Status.COMPLETED, Task.Priority.HIGH, 2, 3, 4, 4, 2, 2),
                new TaskFixture(-1, "API 오류 응답 통일", Task.Status.COMPLETED, Task.Priority.NORMAL, 3, 4, 6, 6, 2, 1),
                new TaskFixture(-1, "QA 시나리오 보강", Task.Status.COMPLETED, Task.Priority.NORMAL, 4, 4, 6, 6, 4, 4),
                new TaskFixture(-1, "배포 체크리스트 정리", Task.Status.IN_PROGRESS, Task.Priority.HIGH, 0, 1, 3, null, 3, 1),
                new TaskFixture(-1, "사용자 피드백 분류", Task.Status.IN_PROGRESS, Task.Priority.NORMAL, 1, 2, 4, null, 2, 1),
                new TaskFixture(-1, "성능 측정 기준 수립", Task.Status.ON_HOLD, Task.Priority.HIGH, 2, 2, 3, null, 2, 1),
                new TaskFixture(-1, "이메일 템플릿 수정", Task.Status.TODO, Task.Priority.LOW, 3, 3, 5, null, 1, 0),
                new TaskFixture(-1, "접근성 수동 검사", Task.Status.TODO, Task.Priority.NORMAL, 4, 4, 6, null, 2, 0),
                new TaskFixture(-1, "운영 FAQ 초안", Task.Status.REQUESTED, Task.Priority.LOW, -1, 5, 6, null, 1, 0),
                new TaskFixture(-1, "보안 헤더 확인", Task.Status.COMPLETED, Task.Priority.URGENT, 0, 0, 1, 3, 2, 2),
                new TaskFixture(-1, "테스트 데이터 정리", Task.Status.CANCELLED, Task.Priority.LOW, 1, 5, 6, null, 1, 0),

                // 리포트 주 24건
                new TaskFixture(0, "주간 리포트 정보 구조 확정", Task.Status.COMPLETED, Task.Priority.HIGH, 0, 0, 2, 2, 3, 3),
                new TaskFixture(0, "무료 기본 PDF 검증", Task.Status.COMPLETED, Task.Priority.HIGH, 1, 0, 1, 1, 2, 2),
                new TaskFixture(0, "AI PDF 한글 출력 검증", Task.Status.COMPLETED, Task.Priority.URGENT, 2, 0, 2, 4, 3, 3),
                new TaskFixture(0, "리포트 상세 화면 구현", Task.Status.COMPLETED, Task.Priority.HIGH, 3, 1, 3, 3, 4, 4),
                new TaskFixture(0, "대시보드 요약 카드 정리", Task.Status.COMPLETED, Task.Priority.NORMAL, 4, 1, 2, 2, 2, 2),
                new TaskFixture(0, "멤버 조회 권한 검증", Task.Status.COMPLETED, Task.Priority.HIGH, 0, 1, 4, 4, 3, 3),
                new TaskFixture(0, "OpenAI 입력 익명화 확인", Task.Status.COMPLETED, Task.Priority.URGENT, 1, 2, 3, 3, 2, 2),
                new TaskFixture(0, "리포트 결과 스키마 검증", Task.Status.COMPLETED, Task.Priority.HIGH, 2, 2, 4, 5, 3, 2),
                new TaskFixture(0, "다운로드 파일명 개선", Task.Status.COMPLETED, Task.Priority.NORMAL, 3, 3, 5, 5, 2, 2),
                new TaskFixture(0, "모바일 상세 화면 조정", Task.Status.COMPLETED, Task.Priority.NORMAL, 4, 3, 6, 6, 3, 3),
                new TaskFixture(0, "AI 생성 timeout 처리", Task.Status.IN_PROGRESS, Task.Priority.URGENT, 0, 1, 2, null, 3, 1),
                new TaskFixture(0, "재생성 실패 복구 검증", Task.Status.IN_PROGRESS, Task.Priority.HIGH, 1, 2, 3, null, 3, 2),
                new TaskFixture(0, "PDF 레이아웃 회귀 확인", Task.Status.IN_PROGRESS, Task.Priority.HIGH, 2, 3, 4, null, 4, 2),
                new TaskFixture(0, "리포트 목록 정렬 개선", Task.Status.IN_PROGRESS, Task.Priority.NORMAL, 3, 4, 5, null, 2, 1),
                new TaskFixture(0, "데이터 유의점 문구 검토", Task.Status.IN_PROGRESS, Task.Priority.NORMAL, 4, 4, 6, null, 2, 1),
                new TaskFixture(0, "위험 신호 기준 검토", Task.Status.ON_HOLD, Task.Priority.URGENT, 0, 2, 2, null, 3, 1),
                new TaskFixture(0, "운영 승인 대기", Task.Status.ON_HOLD, Task.Priority.HIGH, 1, 3, 4, null, 2, 1),
                new TaskFixture(0, "모델 비용 정책 확인", Task.Status.ON_HOLD, Task.Priority.NORMAL, 2, 4, 5, null, 2, 0),
                new TaskFixture(0, "리포트 도움말 작성", Task.Status.TODO, Task.Priority.NORMAL, 3, 4, 6, null, 2, 0),
                new TaskFixture(0, "영문 번역 최종 검토", Task.Status.TODO, Task.Priority.LOW, 4, 5, 6, null, 2, 0),
                new TaskFixture(0, "차주 회의 안건 정리", Task.Status.TODO, Task.Priority.LOW, 0, 5, 6, null, 1, 0),
                new TaskFixture(0, "외부 공유 정책 확인", Task.Status.REQUESTED, Task.Priority.NORMAL, -1, 5, 6, null, 1, 0),
                new TaskFixture(0, "중복 생성 테스트 정리", Task.Status.CANCELLED, Task.Priority.LOW, 1, 3, 5, null, 1, 0),
                new TaskFixture(0, "실패 요청 로그 검토", Task.Status.REJECTED, Task.Priority.NORMAL, 2, 4, 6, null, 1, 0));

        GroupMember leader = team.get(0);
        TaskActivityEvent.Type[] eventTypes = {
                TaskActivityEvent.Type.TASK_CREATED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.DETAILS_CHANGED,
                TaskActivityEvent.Type.ASSIGNEE_CHANGED,
                TaskActivityEvent.Type.CHECKLIST_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.DETAILS_CHANGED,
                TaskActivityEvent.Type.CHECKLIST_CHANGED
        };

        for (int index = 0; index < fixtures.size(); index++) {
            TaskFixture fixture = fixtures.get(index);
            LocalDate weekStart = reportWeekStart.plusWeeks(fixture.weekOffset());
            GroupMember assignee = fixture.assigneeIndex() < 0
                    ? null
                    : team.get(fixture.assigneeIndex());
            LocalDateTime createdAt = weekStart.plusDays(fixture.createdDay()).atTime(9, 0);
            LocalDateTime dueAt = fixture.dueDay() == null
                    ? null
                    : weekStart.plusDays(fixture.dueDay()).atTime(18, 0);
            LocalDateTime completedAt = fixture.completedDay() == null
                    ? null
                    : weekStart.plusDays(fixture.completedDay()).atTime(16, 0);

            Task task = new Task(
                    group,
                    leader,
                    fixture.title(),
                    "AI 주간 리포트 수동 테스트를 위한 비식별 업무 설명",
                    fixture.priority(),
                    dueAt);
            ReflectionTestUtils.setField(task, "approver", leader);
            ReflectionTestUtils.setField(task, "assignee", assignee);
            ReflectionTestUtils.setField(task, "status", fixture.status());
            ReflectionTestUtils.setField(task, "createdAt", createdAt);
            ReflectionTestUtils.setField(task, "updatedAt", completedAt == null ? createdAt.plusHours(2) : completedAt);
            ReflectionTestUtils.setField(task, "startAt",
                    fixture.status() == Task.Status.TODO ? null : createdAt.plusHours(1));
            ReflectionTestUtils.setField(task, "completedAt", completedAt);
            if (fixture.status() == Task.Status.ON_HOLD) {
                ReflectionTestUtils.setField(task, "holdReason", "외부 확인을 기다리는 중");
            }
            task = tasks.saveAndFlush(task);

            List<TaskChecklistItem> savedChecklist = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < fixture.checklistTotal(); itemIndex++) {
                TaskChecklistItem item = new TaskChecklistItem(
                        task,
                        "체크리스트 항목 " + (itemIndex + 1),
                        itemIndex);
                if (itemIndex < fixture.checklistCompleted()) {
                    item.changeCompletion(true, leader);
                }
                savedChecklist.add(item);
            }
            checklistItems.saveAllAndFlush(savedChecklist);

            activityEvents.save(new TaskActivityEvent(
                    task,
                    leader,
                    eventTypes[index % eventTypes.length],
                    weekStart.plusDays(Math.min(fixture.createdDay() + 1, 6))
                            .atTime(12, 0)
                            .atZone(GROUP_ZONE)
                            .toInstant(),
                    fixture.checklistTotal(),
                    fixture.checklistCompleted(),
                    index != 0));
        }
    }

    private record TaskFixture(
            int weekOffset,
            String title,
            Task.Status status,
            Task.Priority priority,
            int assigneeIndex,
            int createdDay,
            Integer dueDay,
            Integer completedDay,
            int checklistTotal,
            int checklistCompleted) {}
}
