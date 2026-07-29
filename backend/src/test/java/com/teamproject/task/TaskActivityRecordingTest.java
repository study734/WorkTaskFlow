package com.teamproject.task;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.task.application.TaskService;
import com.teamproject.task.application.dto.TaskDtos.AssignTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.ClaimTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.CreateTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.TaskResponse;
import com.teamproject.task.application.dto.TaskDtos.TransitionTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.UpdateTaskRequest;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskActivityEventRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TaskActivityRecordingTest {
    @Autowired TaskService tasks;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskActivityEventRepository events;

    @Test
    void recordsExactlyOneEventForEverySuccessfulTaskMutation() {
        Fixture fixture = fixture();
        TaskResponse task = create(fixture, "전체 변경 경로");
        assertTypes(task.id(), TaskActivityEvent.Type.TASK_CREATED);

        task = tasks.update(fixture.leaderUserId(), task.id(), new UpdateTaskRequest(
                "수정 제목", "수정 설명", "HIGH",
                LocalDateTime.of(2026, 8, 1, 18, 0), false, task.version()));
        assertTypes(task.id(), TaskActivityEvent.Type.TASK_CREATED,
                TaskActivityEvent.Type.DETAILS_CHANGED);

        task = transition(fixture.leaderUserId(), task, "ACCEPT", null);
        task = tasks.assign(fixture.leaderUserId(), task.id(),
                new AssignTaskRequest(fixture.member().getId(), task.version()));
        task = transition(fixture.memberUserId(), task, "START", null);
        task = transition(fixture.memberUserId(), task, "HOLD", "대기 사유");
        task = transition(fixture.memberUserId(), task, "RESUME", null);
        task = transition(fixture.memberUserId(), task, "COMPLETE", null);
        task = transition(fixture.leaderUserId(), task, "REOPEN", "재개 사유");
        task = transition(fixture.leaderUserId(), task, "CANCEL", "취소 사유");

        assertTypes(task.id(),
                TaskActivityEvent.Type.TASK_CREATED,
                TaskActivityEvent.Type.DETAILS_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.ASSIGNEE_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.STATUS_CHANGED);

        TaskResponse rejected = create(fixture, "거절 경로");
        rejected = transition(fixture.leaderUserId(), rejected, "REJECT", "거절 사유");
        assertTypes(rejected.id(), TaskActivityEvent.Type.TASK_CREATED,
                TaskActivityEvent.Type.STATUS_CHANGED);

        TaskResponse claimed = create(fixture, "claim 경로");
        claimed = transition(fixture.leaderUserId(), claimed, "ACCEPT", null);
        claimed = tasks.claim(fixture.memberUserId(), claimed.id(),
                new ClaimTaskRequest(claimed.version()));
        assertTypes(claimed.id(), TaskActivityEvent.Type.TASK_CREATED,
                TaskActivityEvent.Type.STATUS_CHANGED,
                TaskActivityEvent.Type.ASSIGNEE_CHANGED);
    }

    @Test
    void rejectedOrStaleMutationDoesNotRecordAnEvent() {
        Fixture fixture = fixture();
        TaskResponse task = create(fixture, "실패 경로");
        long before = events.countByTaskId(task.id());

        assertThatThrownBy(() -> tasks.update(fixture.leaderUserId(), task.id(),
                new UpdateTaskRequest("변경 실패", null, null, null, false, 99L)))
                .isInstanceOf(ApplicationException.class);

        assertThat(events.countByTaskId(task.id())).isEqualTo(before);
    }

    private TaskResponse create(Fixture fixture, String title) {
        return tasks.create(fixture.leaderUserId(), fixture.group().getId(),
                new CreateTaskRequest(title, null, "NORMAL", null));
    }

    private TaskResponse transition(Long actorUserId, TaskResponse task,
            String action, String reason) {
        long before = events.countByTaskId(task.id());
        TransitionTaskRequest request = "HOLD".equals(action)
                ? new TransitionTaskRequest(
                        action, reason, "EXTERNAL", "FOLLOW_UP",
                        LocalDate.of(2099, 12, 31), task.version())
                : new TransitionTaskRequest(action, reason, task.version());
        TaskResponse changed = tasks.transition(actorUserId, task.id(),
                request);
        assertThat(events.countByTaskId(task.id())).isEqualTo(before + 1);
        return changed;
    }

    private void assertTypes(Long taskId, TaskActivityEvent.Type... expected) {
        List<TaskActivityEvent.Type> actual = events
                .findAllByTaskIdOrderByOccurredAtAscIdAsc(taskId)
                .stream()
                .map(TaskActivityEvent::getEventType)
                .toList();
        assertThat(actual).containsExactly(expected);
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User leaderUser = users.save(new User("activity_leader_" + suffix,
                "activity_leader_" + suffix + "@example.com", "hash", "활동 팀장", true));
        User memberUser = users.save(new User("activity_member_" + suffix,
                "activity_member_" + suffix + "@example.com", "hash", "활동 팀원", true));
        Group group = groups.save(Group.team("활동 기록 팀", null, "Asia/Seoul", leaderUser));
        GroupMember leader = members.save(GroupMember.leader(group, leaderUser));
        GroupMember member = members.save(GroupMember.member(group, memberUser));
        return new Fixture(group, leader, member, leaderUser.getId(), memberUser.getId());
    }

    private record Fixture(Group group, GroupMember leader, GroupMember member,
            Long leaderUserId, Long memberUserId) {}
}
