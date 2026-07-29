package com.teamproject.task;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.task.application.WeeklyObjectiveModule;
import com.teamproject.task.application.WeeklyObjectiveModule.ObjectiveView;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskActivityEventRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.domain.TaskWeeklyObjectiveLinkRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WeeklyObjectiveModuleTest {
    @Autowired WeeklyObjectiveModule objectives;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired TaskWeeklyObjectiveLinkRepository links;
    @Autowired TaskActivityEventRepository events;

    @Test
    void limitsEachGroupWeekToThreeObjectives() {
        Fixture fixture = fixture();

        objectives.create(
                fixture.user().getId(), fixture.group().getId(),
                fixture.weekStart(), "첫 번째 목표", 1);
        objectives.create(
                fixture.user().getId(), fixture.group().getId(),
                fixture.weekStart(), "두 번째 목표", 2);
        objectives.create(
                fixture.user().getId(), fixture.group().getId(),
                fixture.weekStart(), "세 번째 목표", 3);

        assertThatThrownBy(() -> objectives.create(
                fixture.user().getId(), fixture.group().getId(),
                fixture.weekStart(), "초과 목표", 1))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("WEEKLY_OBJECTIVE_LIMIT"));
    }

    @Test
    void rejectsChangesAfterTheGroupWeekCloses() {
        Fixture fixture = fixture();
        LocalDate closedWeek = fixture.weekStart().minusWeeks(1);

        assertThatThrownBy(() -> objectives.create(
                fixture.user().getId(), fixture.group().getId(),
                closedWeek, "닫힌 주간 목표", 1))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("WEEKLY_OBJECTIVE_WEEK_CLOSED"));
    }

    @Test
    void keepsOneTaskLinkPerWeekAndRecordsEveryEffectiveObjectiveChange() {
        Fixture fixture = fixture();
        Task task = tasks.save(new Task(
                fixture.group(), fixture.leader(), "목표 연결 업무", null,
                Task.Priority.NORMAL, fixture.weekStart().plusDays(4).atTime(18, 0)));
        ObjectiveView first = objectives.create(
                fixture.user().getId(), fixture.group().getId(),
                fixture.weekStart(), "첫 번째 목표", 1);
        ObjectiveView second = objectives.create(
                fixture.user().getId(), fixture.group().getId(),
                fixture.weekStart(), "두 번째 목표", 2);

        objectives.linkTask(
                fixture.user().getId(), task.getId(), fixture.weekStart(), first.id());
        objectives.linkTask(
                fixture.user().getId(), task.getId(), fixture.weekStart(), second.id());

        assertThat(links.findByTaskIdAndWeekStart(task.getId(), fixture.weekStart()))
                .get()
                .extracting(link -> link.getObjective().getId())
                .isEqualTo(second.id());
        assertThat(events.countByTaskId(task.getId())).isEqualTo(2);

        ObjectiveView updated = objectives.update(
                fixture.user().getId(), second.id(), "수정된 두 번째 목표",
                second.position(), second.version());
        assertThat(events.countByTaskId(task.getId())).isEqualTo(3);

        objectives.delete(
                fixture.user().getId(), updated.id(), updated.version());

        assertThat(links.findByTaskIdAndWeekStart(task.getId(), fixture.weekStart()))
                .isEmpty();
        assertThat(events.findAllByTaskIdOrderByOccurredAtAscIdAsc(task.getId()))
                .extracting(TaskActivityEvent::getEventType)
                .containsOnly(TaskActivityEvent.Type.OBJECTIVE_CHANGED);
        assertThat(events.countByTaskId(task.getId())).isEqualTo(4);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User(
                "objective_" + suffix,
                "objective_" + suffix + "@example.com",
                "hash", "목표 팀장", true));
        Group group = groups.save(
                Group.team("주간 목표 팀", null, "Asia/Seoul", user));
        GroupMember leader = members.save(GroupMember.leader(group, user));
        LocalDate weekStart = LocalDate.now(ZoneId.of(group.getTimezone()))
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new Fixture(user, group, leader, weekStart);
    }

    private record Fixture(
            User user, Group group, GroupMember leader, LocalDate weekStart) {}
}
