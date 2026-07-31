package com.teamproject.report;

import com.teamproject.calendar.domain.CalendarEvent;
import com.teamproject.calendar.domain.CalendarEventRepository;
import com.teamproject.comment.domain.CommentMention;
import com.teamproject.comment.domain.CommentMentionRepository;
import com.teamproject.comment.domain.TaskComment;
import com.teamproject.comment.domain.TaskCommentRepository;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiWeeklyReportEvidenceQuery;
import com.teamproject.report.application.AiWeeklyReportEvidenceQuery.TaskCollaborationCounts;
import com.teamproject.resource.domain.GroupResource;
import com.teamproject.resource.domain.GroupResourceRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Transactional
class AiWeeklyReportEvidenceQueryTest {
    @Autowired AiWeeklyReportEvidenceQuery evidence;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired TaskCommentRepository comments;
    @Autowired CommentMentionRepository mentions;
    @Autowired GroupResourceRepository resources;
    @Autowired CalendarEventRepository calendarEvents;
    @Autowired EntityManager entityManager;

    @Test
    @DisplayName("업무별 댓글·자료 수를 한 번에 집계하고 근거 없는 업무는 0으로 채운다")
    void aggregatesCommentAndResourceCountsPerTask() {
        Fixture fixture = fixture();
        Task busy = task(fixture, "busy");
        Task quiet = task(fixture, "quiet");
        comment(busy, fixture.leader);
        comment(busy, fixture.member);
        resource(fixture, busy);
        flush();

        Map<Long, TaskCollaborationCounts> counts =
                evidence.loadCollaborationCounts(List.of(busy.getId(), quiet.getId()));

        assertThat(counts.get(busy.getId()).commentCount()).isEqualTo(2);
        assertThat(counts.get(busy.getId()).resourceLinkCount()).isEqualTo(1);
        assertThat(counts.get(quiet.getId())).isEqualTo(TaskCollaborationCounts.NONE);
    }

    @Test
    @DisplayName("삭제된 댓글과 자료는 집계에서 빠진다")
    void ignoresSoftDeletedRows() {
        Fixture fixture = fixture();
        Task task = task(fixture, "task");
        TaskComment removed = comment(task, fixture.leader);
        GroupResource removedResource = resource(fixture, task);
        ReflectionTestUtils.setField(removed, "deletedAt", LocalDateTime.now());
        ReflectionTestUtils.setField(removedResource, "deletedAt", LocalDateTime.now());
        flush();

        TaskCollaborationCounts counts =
                evidence.loadCollaborationCounts(List.of(task.getId())).get(task.getId());

        assertThat(counts.commentCount()).isZero();
        assertThat(counts.resourceLinkCount()).isZero();
    }

    /** 멘션 대상자가 이후에 댓글을 달았으면 응답이 온 것으로 본다. */
    @Test
    @DisplayName("멘션 이후 대상자가 댓글을 달면 미해결에서 제외한다")
    void countsOnlyMentionsWithoutALaterReply() {
        Fixture fixture = fixture();
        Task answered = task(fixture, "answered");
        Task waiting = task(fixture, "waiting");

        LocalDateTime asked = LocalDateTime.of(2026, 7, 21, 10, 0);
        mention(comment(answered, fixture.leader, asked), fixture.member);
        comment(answered, fixture.member, asked.plusMinutes(5));

        mention(comment(waiting, fixture.leader, asked), fixture.member);
        flush();

        Map<Long, TaskCollaborationCounts> counts = evidence.loadCollaborationCounts(
                List.of(answered.getId(), waiting.getId()));

        assertThat(counts.get(answered.getId()).unresolvedMentionCount()).isZero();
        assertThat(counts.get(waiting.getId()).unresolvedMentionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("기간과 겹치는 일정만 제목 없이 반환한다")
    void loadsOverlappingCalendarWindowsWithoutTitles() {
        Fixture fixture = fixture();
        LocalDateTime from = LocalDateTime.of(2026, 7, 20, 0, 0);
        LocalDateTime to = from.plusDays(7);
        calendarEvent(fixture, "대외비 고객사 미팅", from.plusDays(1), from.plusDays(1).plusHours(2));
        calendarEvent(fixture, "범위 밖 일정", to.plusDays(3), to.plusDays(3).plusHours(1));
        flush();

        List<AiWeeklyReportEvidenceQuery.CalendarWindow> windows =
                evidence.loadCalendarWindows(fixture.group.getId(), from, to);

        assertThat(windows).hasSize(1);
        assertThat(windows.get(0).eventType()).isEqualTo("MEETING");
        assertThat(windows.get(0).ownerMemberId()).isEqualTo(fixture.leader.getId());
        assertThat(windows.get(0).toString()).doesNotContain("대외비");
    }

    @Test
    @DisplayName("업무가 늘어도 협업 집계 쿼리 횟수는 그대로다")
    void keepsQueryCountConstantAsTaskCountGrows() {
        Fixture fixture = fixture();
        long threeTasks = countQueries(() -> evidence.loadCollaborationCounts(
                taskIds(fixture, 3)));
        long twentyTasks = countQueries(() -> evidence.loadCollaborationCounts(
                taskIds(fixture, 20)));

        assertThat(threeTasks).isEqualTo(twentyTasks);
        // 댓글 / 미해결 멘션 / 자료 세 집계뿐이다. 늘어나면 N+1 회귀를 의심한다.
        assertThat(threeTasks).isEqualTo(3);
    }

    @Test
    @DisplayName("빈 업무 목록은 쿼리를 실행하지 않는다")
    void skipsQueriesForEmptyInput() {
        assertThat(countQueries(() -> evidence.loadCollaborationCounts(List.of()))).isZero();
        assertThat(evidence.loadCollaborationCounts(List.of())).isEmpty();
    }

    private List<Long> taskIds(Fixture fixture, int count) {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Task task = task(fixture, "task-" + index);
            comment(task, fixture.leader);
            ids.add(task.getId());
        }
        flush();
        return ids;
    }

    private long countQueries(Runnable action) {
        Statistics statistics = entityManager.unwrap(Session.class)
                .getSessionFactory().getStatistics();
        statistics.clear();
        action.run();
        return statistics.getQueryExecutionCount();
    }

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User leaderUser = users.save(new User(
                "leader_" + suffix, "leader-" + suffix + "@test.local", "hash", "리더", true));
        User memberUser = users.save(new User(
                "member_" + suffix, "member-" + suffix + "@test.local", "hash", "팀원", true));
        Group group = groups.save(Group.team(
                "그룹-" + suffix, null, "Asia/Seoul", leaderUser));
        GroupMember leader = members.save(GroupMember.leader(group, leaderUser));
        GroupMember member = members.save(GroupMember.member(group, memberUser));
        return new Fixture(group, leader, member);
    }

    private Task task(Fixture fixture, String title) {
        return tasks.save(new Task(fixture.group, fixture.leader, title, null,
                Task.Priority.NORMAL, null));
    }

    private TaskComment comment(Task task, GroupMember author) {
        return comments.save(new TaskComment(task, author, "내용"));
    }

    /** createdAt은 updatable=false라 저장 전에 심어야 한다. */
    private TaskComment comment(Task task, GroupMember author, LocalDateTime createdAt) {
        TaskComment comment = new TaskComment(task, author, "내용");
        ReflectionTestUtils.setField(comment, "createdAt", createdAt);
        return comments.save(comment);
    }

    private CommentMention mention(TaskComment comment, GroupMember member) {
        CommentMention value = new CommentMention(comment, member);
        ReflectionTestUtils.setField(value, "createdAt", comment.getCreatedAt());
        return mentions.save(value);
    }

    private GroupResource resource(Fixture fixture, Task task) {
        return resources.save(GroupResource.link(fixture.group, task, fixture.leader,
                "자료", "https://example.invalid/doc"));
    }

    private CalendarEvent calendarEvent(Fixture fixture, String title,
            LocalDateTime start, LocalDateTime end) {
        return calendarEvents.save(new CalendarEvent(fixture.group, fixture.leader,
                CalendarEvent.Type.MEETING, title, null, start, end, false, null));
    }

    private record Fixture(Group group, GroupMember leader, GroupMember member) {}
}
