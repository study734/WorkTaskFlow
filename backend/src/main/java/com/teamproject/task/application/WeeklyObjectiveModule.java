package com.teamproject.task.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.domain.TaskWeeklyObjectiveLink;
import com.teamproject.task.domain.TaskWeeklyObjectiveLinkRepository;
import com.teamproject.task.domain.WeeklyObjective;
import com.teamproject.task.domain.WeeklyObjectiveRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class WeeklyObjectiveModule {
    private static final int MAX_OBJECTIVES = 3;

    private final GroupAuthorization authorization;
    private final GroupRepository groups;
    private final WeeklyObjectiveRepository objectives;
    private final TaskWeeklyObjectiveLinkRepository links;
    private final TaskRepository tasks;
    private final TaskActivityRecorder activity;
    private final Clock clock;

    public WeeklyObjectiveModule(GroupAuthorization authorization, GroupRepository groups,
            WeeklyObjectiveRepository objectives, TaskWeeklyObjectiveLinkRepository links,
            TaskRepository tasks, TaskActivityRecorder activity, Clock clock) {
        this.authorization = authorization;
        this.groups = groups;
        this.objectives = objectives;
        this.links = links;
        this.tasks = tasks;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ObjectiveView> list(Long userId, Long groupId, LocalDate weekStart) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        validateWeekStart(weekStart);
        return objectives.findAllByGroupIdAndWeekStartOrderByPositionAscIdAsc(groupId, weekStart)
                .stream().map(this::view).toList();
    }

    @Transactional
    public ObjectiveView create(Long userId, Long groupId, LocalDate weekStart,
            String title, int position) {
        GroupMember leader = authorization.requireLeader(groupId, userId);
        Group group = groups.findByIdForUpdate(groupId).orElseThrow(this::groupNotFound);
        requireEditableWeek(group, weekStart);
        validatePosition(position);
        if (objectives.countByGroupIdAndWeekStart(groupId, weekStart) >= MAX_OBJECTIVES) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_LIMIT", HttpStatus.CONFLICT,
                    "주간 목표는 최대 3개까지 만들 수 있습니다.");
        }
        if (objectives.existsByGroupIdAndWeekStartAndPositionAndIdNot(
                groupId, weekStart, position, -1L)) {
            throw positionConflict();
        }
        WeeklyObjective objective = objectives.save(new WeeklyObjective(
                group, weekStart, requireTitle(title), position, leader, nowLocal()));
        return view(objective);
    }

    @Transactional
    public ObjectiveView update(Long userId, Long objectiveId, String title,
            int position, long expectedVersion) {
        WeeklyObjective objective = objective(objectiveId);
        GroupMember leader = authorization.requireLeader(objective.getGroup().getId(), userId);
        groups.findByIdForUpdate(leader.getGroup().getId()).orElseThrow(this::groupNotFound);
        requireEditableWeek(objective.getGroup(), objective.getWeekStart());
        requireVersion(objective, expectedVersion);
        validatePosition(position);
        if (objectives.existsByGroupIdAndWeekStartAndPositionAndIdNot(
                objective.getGroup().getId(), objective.getWeekStart(), position, objectiveId)) {
            throw positionConflict();
        }
        objective.update(requireTitle(title), position, nowLocal());
        List<TaskWeeklyObjectiveLink> affected = links.findAllByObjectiveId(objectiveId);
        affected.forEach(link -> activity.record(
                link.getTask(), leader, TaskActivityEvent.Type.OBJECTIVE_CHANGED));
        objectives.flush();
        return view(objective);
    }

    @Transactional
    public void delete(Long userId, Long objectiveId, long expectedVersion) {
        WeeklyObjective objective = objective(objectiveId);
        GroupMember leader = authorization.requireLeader(objective.getGroup().getId(), userId);
        groups.findByIdForUpdate(leader.getGroup().getId()).orElseThrow(this::groupNotFound);
        requireEditableWeek(objective.getGroup(), objective.getWeekStart());
        requireVersion(objective, expectedVersion);
        List<TaskWeeklyObjectiveLink> affected = links.findAllByObjectiveId(objectiveId);
        links.deleteAll(affected);
        links.flush();
        affected.forEach(link -> activity.record(
                link.getTask(), leader, TaskActivityEvent.Type.OBJECTIVE_CHANGED));
        objectives.delete(objective);
    }

    @Transactional
    public TaskObjectiveView linkTask(Long userId, Long taskId, LocalDate weekStart,
            Long objectiveId) {
        Task task = tasks.findById(taskId).orElseThrow(this::taskNotFound);
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireLinkPermission(task, actor);
        requireEditableWeek(task.getGroup(), weekStart);
        TaskWeeklyObjectiveLink existing =
                links.findByTaskIdAndWeekStart(taskId, weekStart).orElse(null);

        if (objectiveId == null) {
            if (existing != null) {
                links.delete(existing);
                links.flush();
                activity.record(task, actor, TaskActivityEvent.Type.OBJECTIVE_CHANGED);
            }
            return new TaskObjectiveView(taskId, weekStart, null);
        }

        WeeklyObjective objective = objectives.findByIdAndGroupId(
                        objectiveId, task.getGroup().getId())
                .orElseThrow(this::objectiveNotFound);
        if (!objective.getWeekStart().equals(weekStart)) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_WEEK_MISMATCH",
                    HttpStatus.BAD_REQUEST, "업무와 목표의 주간이 일치하지 않습니다.");
        }
        if (existing != null && existing.getObjective().getId().equals(objectiveId)) {
            return new TaskObjectiveView(taskId, weekStart, view(objective));
        }
        if (existing == null) {
            links.save(new TaskWeeklyObjectiveLink(task, objective, actor, nowLocal()));
        } else {
            existing.changeObjective(objective, actor, nowLocal());
        }
        links.flush();
        activity.record(task, actor, TaskActivityEvent.Type.OBJECTIVE_CHANGED);
        return new TaskObjectiveView(taskId, weekStart, view(objective));
    }

    @Transactional(readOnly = true)
    public TaskObjectiveView findTaskLink(Long userId, Long taskId, LocalDate weekStart) {
        Task task = tasks.findById(taskId).orElseThrow(this::taskNotFound);
        authorization.requireActiveMember(task.getGroup().getId(), userId);
        validateWeekStart(weekStart);
        return links.findByTaskIdAndWeekStart(taskId, weekStart)
                .map(link -> new TaskObjectiveView(taskId, weekStart, view(link.getObjective())))
                .orElseGet(() -> new TaskObjectiveView(taskId, weekStart, null));
    }

    private ObjectiveView view(WeeklyObjective objective) {
        return new ObjectiveView(objective.getId(), objective.getWeekStart(),
                objective.getTitle(), objective.getPosition(), objective.getVersion());
    }

    private WeeklyObjective objective(Long objectiveId) {
        return objectives.findByIdForUpdate(objectiveId).orElseThrow(this::objectiveNotFound);
    }

    private void requireEditableWeek(Group group, LocalDate weekStart) {
        validateWeekStart(weekStart);
        ZoneId zone = ZoneId.of(group.getTimezone());
        Instant endExclusive = weekStart.plusDays(7).atStartOfDay(zone).toInstant();
        if (!clock.instant().isBefore(endExclusive)) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_WEEK_CLOSED",
                    HttpStatus.CONFLICT, "종료된 주간의 목표는 변경할 수 없습니다.");
        }
    }

    private void validateWeekStart(LocalDate weekStart) {
        if (weekStart == null || weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_WEEK_INVALID",
                    HttpStatus.BAD_REQUEST, "주간 시작일은 월요일이어야 합니다.");
        }
    }

    private void validatePosition(int position) {
        if (position < 1 || position > MAX_OBJECTIVES) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_POSITION_INVALID",
                    HttpStatus.BAD_REQUEST, "목표 순서는 1부터 3까지 입력해 주세요.");
        }
    }

    private void requireLinkPermission(Task task, GroupMember actor) {
        boolean leader = actor.getRole() == GroupMember.Role.LEADER;
        boolean assignee = task.getAssignee() != null
                && task.getAssignee().getId().equals(actor.getId());
        if (!leader && !assignee) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_LINK_FORBIDDEN",
                    HttpStatus.FORBIDDEN, "팀장 또는 업무 담당자만 목표를 연결할 수 있습니다.");
        }
    }

    private void requireVersion(WeeklyObjective objective, long expectedVersion) {
        if (objective.getVersion() != expectedVersion) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_VERSION_CONFLICT",
                    HttpStatus.CONFLICT, "주간 목표가 이미 변경되었습니다.");
        }
    }

    private String requireTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_TITLE_REQUIRED",
                    HttpStatus.BAD_REQUEST, "주간 목표를 입력해 주세요.");
        }
        String normalized = title.trim();
        if (normalized.length() > 120) {
            throw new ApplicationException("WEEKLY_OBJECTIVE_TITLE_TOO_LONG",
                    HttpStatus.BAD_REQUEST, "주간 목표는 120자 이내로 입력해 주세요.");
        }
        return normalized;
    }

    private LocalDateTime nowLocal() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private ApplicationException groupNotFound() {
        return new ApplicationException("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND,
                "그룹을 찾을 수 없습니다.");
    }

    private ApplicationException taskNotFound() {
        return new ApplicationException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND,
                "업무를 찾을 수 없습니다.");
    }

    private ApplicationException objectiveNotFound() {
        return new ApplicationException("WEEKLY_OBJECTIVE_NOT_FOUND", HttpStatus.NOT_FOUND,
                "주간 목표를 찾을 수 없습니다.");
    }

    private ApplicationException positionConflict() {
        return new ApplicationException("WEEKLY_OBJECTIVE_POSITION_CONFLICT",
                HttpStatus.CONFLICT, "같은 순서의 주간 목표가 이미 있습니다.");
    }

    public record ObjectiveView(Long id, LocalDate weekStart, String title,
            int position, long version) {}
    public record TaskObjectiveView(Long taskId, LocalDate weekStart, ObjectiveView objective) {}
}
