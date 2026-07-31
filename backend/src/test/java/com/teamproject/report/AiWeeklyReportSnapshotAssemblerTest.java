package com.teamproject.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import com.teamproject.calendar.domain.CalendarEvent;
import com.teamproject.calendar.domain.CalendarEventRepository;
import com.teamproject.comment.domain.CommentMention;
import com.teamproject.comment.domain.CommentMentionRepository;
import com.teamproject.comment.domain.TaskComment;
import com.teamproject.comment.domain.TaskCommentRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiWeeklyReportSnapshotAssembler;
import com.teamproject.report.application.dto.AiWeeklyReportDtos;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.ComparisonStatus;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.DueState;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.HoldReasonCategory;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.Language;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.SnapshotTask;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.TaskStatus;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskActivityEventRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 조립한 Snapshot이 M0에서 고정한 JSON Schema를 실제로 만족하는지, 그리고 개인정보 원문이
 * 직렬화 결과에 섞이지 않는지 확인한다.
 */
@SpringBootTest
@Transactional
class AiWeeklyReportSnapshotAssemblerTest {
    private static final LocalDate FROM = LocalDate.of(2026, 7, 20);
    private static final LocalDate TO_EXCLUSIVE = FROM.plusDays(7);
    private static final String SECRET_TITLE = "대외비 고객사 계약서 최종 검토";
    private static final String SECRET_COMMENT = "김민준 담당자에게 직접 전달 바랍니다";

    @Autowired AiWeeklyReportSnapshotAssembler assembler;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired TaskActivityEventRepository activityEvents;
    @Autowired TaskCommentRepository comments;
    @Autowired CommentMentionRepository mentions;
    @Autowired CalendarEventRepository calendarEvents;
    @Autowired EntityManager entityManager;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    @DisplayName("조립한 Snapshot이 ai-weekly-report-snapshot.v1 계약을 만족한다")
    void assembledSnapshotSatisfiesTheContract() {
        Fixture fixture = fixture();
        overdueUnassignedTask(fixture);
        completedTask(fixture);

        AiWeeklyReportSnapshotV1 snapshot = assemble(fixture);

        assertThat(validate(snapshot)).isEmpty();
        assertThat(snapshot.schemaVersion())
                .isEqualTo(AiWeeklyReportDtos.SNAPSHOT_SCHEMA_VERSION);
        assertThat(snapshot.reportContext().groupRef())
                .isEqualTo("GROUP-" + fixture.group.getId());
        assertThat(snapshot.reportContext().period().from()).isEqualTo("2026-07-20");
        assertThat(snapshot.reportContext().period().toExclusive()).isEqualTo("2026-07-27");
        assertThat(snapshot.reportContext().period().timezone()).isEqualTo("Asia/Seoul");
    }

    /** 가장 중요한 회귀 방어선. 원문이 하나라도 새면 v7-2 개인정보 경계가 깨진다. */
    @Test
    @DisplayName("직렬화 결과에 업무 제목·댓글 원문·실명이 없다")
    void carriesNoRawTextIntoTheSerialisedSnapshot() {
        Fixture fixture = fixture();
        Task task = overdueUnassignedTask(fixture);
        TaskComment comment = comments.save(
                new TaskComment(task, fixture.leader, SECRET_COMMENT));
        mentions.save(new CommentMention(comment, fixture.member));
        calendarEvents.save(new CalendarEvent(fixture.group, fixture.leader,
                CalendarEvent.Type.MEETING, SECRET_TITLE, "비공개 설명",
                FROM.plusDays(1).atStartOfDay(), FROM.plusDays(1).atTime(11, 0), false, null));
        flush();

        String serialised = write(assemble(fixture));

        assertThat(serialised)
                .doesNotContain(SECRET_TITLE)
                .doesNotContain(SECRET_COMMENT)
                .doesNotContain("대외비")
                .doesNotContain("김민준")
                .doesNotContain("비공개 설명")
                .doesNotContain(fixture.leaderName)
                .doesNotContain(fixture.memberName);
    }

    @Test
    @DisplayName("업무 사실을 dueState·checklist·협업 집계로 옮긴다")
    void mapsTaskFactsIntoTheSnapshot() {
        Fixture fixture = fixture();
        Task task = overdueUnassignedTask(fixture);
        comments.save(new TaskComment(task, fixture.leader, "확인 부탁드립니다"));
        flush();

        SnapshotTask view = assemble(fixture).tasks().get(0);

        assertThat(view.taskRef()).isEqualTo("TASK-1");
        assertThat(view.status()).isEqualTo(TaskStatus.TODO);
        assertThat(view.assigneeRef()).isNull();
        assertThat(view.dueState()).isEqualTo(DueState.OVERDUE);
        assertThat(view.checklist().total()).isEqualTo(4);
        assertThat(view.checklist().completed()).isZero();
        assertThat(view.collaboration().commentCount()).isEqualTo(1);
        assertThat(view.history().holdReasonCategory()).isEqualTo(HoldReasonCategory.NONE);
        assertThat(view.safeLabel())
                .isEqualTo("승인 후 담당자가 없는 체크리스트가 시작되지 않은 지연된 업무");
    }

    @Test
    @DisplayName("보류 업무의 구조화 blocker를 hold category로 옮긴다")
    void mapsStructuredBlockerToHoldCategory() {
        Fixture fixture = fixture();
        Task task = new Task(fixture.group, fixture.leader, "보류 업무", null,
                Task.Priority.NORMAL, FROM.plusDays(9).atStartOfDay());
        ReflectionTestUtils.setField(task, "createdAt", FROM.plusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(task, "updatedAt", FROM.plusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(task, "status", Task.Status.ON_HOLD);
        ReflectionTestUtils.setField(task, "blockerType", Task.BlockerType.EXTERNAL);
        tasks.save(task);
        activityEvents.save(new TaskActivityEvent(task, fixture.leader,
                TaskActivityEvent.Type.STATUS_CHANGED,
                FROM.plusDays(2).atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                6, 1, true));
        flush();

        SnapshotTask view = assemble(fixture).tasks().get(0);

        assertThat(view.history().holdReasonCategory())
                .isEqualTo(HoldReasonCategory.EXTERNAL_FEEDBACK);
        assertThat(view.safeLabel()).startsWith("외부 회신을 기다리는");
    }

    @Test
    @DisplayName("이전 기간에 업무가 없으면 NO_BASELINE으로 두고 delta를 만들지 않는다")
    void reportsNoBaselineWithoutAPreviousPeriod() {
        Fixture fixture = fixture();
        overdueUnassignedTask(fixture);

        AiWeeklyReportSnapshotV1 snapshot = assemble(fixture);

        assertThat(snapshot.comparison().status()).isEqualTo(ComparisonStatus.NO_BASELINE);
        assertThat(snapshot.comparison().completionRatePointDelta()).isNull();
        assertThat(snapshot.comparison().previousFrom()).isNull();
        assertThat(validate(snapshot)).isEmpty();
    }

    @Test
    @DisplayName("팀원 지표는 ref로만 식별하고 역할과 집계만 담는다")
    void describesMembersByRefOnly() {
        Fixture fixture = fixture();
        completedTask(fixture);

        var member = assemble(fixture).members().stream()
                .filter(value -> value.memberRef().equals("MEMBER-1")).findFirst().orElseThrow();

        assertThat(member.role()).isEqualTo("LEADER");
        assertThat(member.assignedCount()).isEqualTo(1);
        assertThat(member.completedCount()).isEqualTo(1);
        assertThat(member.onTimeRatePercent()).isEqualTo(100);
    }

    @Test
    @DisplayName("일정은 비식별 라벨로만 담고 마감이 겹치는 업무를 연결한다")
    void linksCalendarConstraintsToTasksByDueWindow() {
        Fixture fixture = fixture();
        task(fixture, "마감 업무", Task.Status.IN_PROGRESS, fixture.leader,
                FROM.plusDays(2).atTime(15, 0), null, 0, 0, FROM.plusDays(1));
        calendarEvents.save(new CalendarEvent(fixture.group, fixture.leader,
                CalendarEvent.Type.MEETING, SECRET_TITLE, null,
                FROM.plusDays(2).atTime(14, 0), FROM.plusDays(2).atTime(16, 0), false, null));
        flush();

        AiWeeklyReportSnapshotV1 snapshot = assemble(fixture);

        assertThat(snapshot.calendarConstraints()).hasSize(1);
        assertThat(snapshot.calendarConstraints().get(0).eventRef()).isEqualTo("EVENT-1");
        assertThat(snapshot.calendarConstraints().get(0).safeLabel()).isEqualTo("확정된 회의 일정");
        assertThat(snapshot.calendarConstraints().get(0).relatedTaskRefs())
                .containsExactly("TASK-1");
        assertThat(snapshot.tasks().get(0).calendarEventRefs()).containsExactly("EVENT-1");
    }

    /** 위험 후보와 신호 산출은 policy engine(M3) 범위다. 여기서는 비워 둔다. */
    @Test
    @DisplayName("위험 후보와 신호 코드는 아직 비어 있고 계약은 여전히 유효하다")
    void leavesPolicyOutputsEmptyForNow() {
        Fixture fixture = fixture();
        overdueUnassignedTask(fixture);

        AiWeeklyReportSnapshotV1 snapshot = assemble(fixture);

        assertThat(snapshot.riskCandidates()).isEmpty();
        assertThat(snapshot.tasks().get(0).signalCodes()).isEmpty();
        assertThat(snapshot.tasks().get(0).allowedDecisionOptionCodes()).isEmpty();
        assertThat(validate(snapshot)).isEmpty();
    }

    @Test
    @DisplayName("업무가 하나도 없어도 계약을 만족하는 Snapshot을 만든다")
    void assemblesEmptyPeriodWithoutBreakingTheContract() {
        AiWeeklyReportSnapshotV1 snapshot = assemble(fixture());

        assertThat(snapshot.tasks()).isEmpty();
        assertThat(snapshot.metrics().periodTaskCount()).isZero();
        assertThat(snapshot.metrics().completionRatePercent()).isNull();
        assertThat(validate(snapshot)).isEmpty();
    }

    @Test
    @DisplayName("월요일이 아니거나 7일이 아닌 기간을 거부한다")
    void rejectsPeriodsThatAreNotACompletedMondayWeek() {
        Fixture fixture = fixture();

        assertThatThrownBy(() -> assembler.assemble(fixture.group.getId(),
                FROM.plusDays(1), TO_EXCLUSIVE.plusDays(1), Language.KO, "v7-2-prompt-001"))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.code()).isEqualTo("AI_REPORT_WEEK_INVALID"));

        assertThatThrownBy(() -> assembler.assemble(fixture.group.getId(),
                FROM, FROM.plusDays(5), Language.KO, "v7-2-prompt-001"))
                .isInstanceOfSatisfying(ApplicationException.class, exception ->
                        assertThat(exception.code()).isEqualTo("AI_REPORT_WEEK_INVALID"));
    }

    // ---------- fixture ----------

    private AiWeeklyReportSnapshotV1 assemble(Fixture fixture) {
        flush();
        return assembler.assemble(fixture.group.getId(), FROM, TO_EXCLUSIVE,
                Language.KO, "v7-2-prompt-001");
    }

    private Task overdueUnassignedTask(Fixture fixture) {
        return task(fixture, SECRET_TITLE, Task.Status.TODO, null,
                FROM.plusDays(3).atTime(18, 0), null, 4, 0, FROM.plusDays(1));
    }

    private Task completedTask(Fixture fixture) {
        return task(fixture, "완료 업무", Task.Status.COMPLETED, fixture.leader,
                FROM.plusDays(4).atTime(18, 0), FROM.plusDays(3).atTime(9, 0),
                3, 3, FROM.plusDays(3));
    }

    /**
     * 활동 이벤트는 저장 시점의 업무 상태를 복사한다. 따라서 상태·담당·완료 시각을 먼저
     * 심고 나서 이벤트를 만들어야 스냅샷에 그대로 실린다.
     */
    private Task task(Fixture fixture, String title, Task.Status status, GroupMember assignee,
            LocalDateTime dueAt, LocalDateTime completedAt,
            int checklistTotal, int checklistCompleted, LocalDate occurredOn) {
        Task task = new Task(fixture.group, fixture.leader, title, "비공개 설명",
                Task.Priority.HIGH, dueAt);
        ReflectionTestUtils.setField(task, "createdAt", FROM.plusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(task, "updatedAt", FROM.plusDays(1).atStartOfDay());
        ReflectionTestUtils.setField(task, "status", status);
        ReflectionTestUtils.setField(task, "assignee", assignee);
        ReflectionTestUtils.setField(task, "completedAt", completedAt);
        tasks.save(task);
        activityEvents.save(new TaskActivityEvent(task, fixture.leader,
                TaskActivityEvent.Type.STATUS_CHANGED,
                occurredOn.atTime(12, 0).atZone(ZoneId.of("Asia/Seoul")).toInstant(),
                checklistTotal, checklistCompleted, true));
        return task;
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String leaderName = "리더" + suffix;
        String memberName = "팀원" + suffix;
        User leaderUser = users.save(new User("leader_" + suffix,
                "leader-" + suffix + "@test.local", "hash", leaderName, true));
        User memberUser = users.save(new User("member_" + suffix,
                "member-" + suffix + "@test.local", "hash", memberName, true));
        Group group = groups.save(Group.team("그룹-" + suffix, null, "Asia/Seoul", leaderUser));
        GroupMember leader = members.save(GroupMember.leader(group, leaderUser));
        GroupMember member = members.save(GroupMember.member(group, memberUser));
        return new Fixture(group, leader, member, leaderName, memberName);
    }

    private record Fixture(Group group, GroupMember leader, GroupMember member,
            String leaderName, String memberName) {}

    // ---------- schema ----------

    private String write(AiWeeklyReportSnapshotV1 snapshot) {
        try {
            return json.writeValueAsString(snapshot);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private Set<ValidationMessage> validate(AiWeeklyReportSnapshotV1 snapshot) {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .formatAssertionsEnabled(true).build();
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        try (InputStream stream = getClass()
                .getResourceAsStream("/ai/ai-weekly-report-snapshot-v1.schema.json")) {
            JsonSchema schema = factory.getSchema(stream, config);
            JsonNode document = json.readTree(write(snapshot));
            return schema.validate(document);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
