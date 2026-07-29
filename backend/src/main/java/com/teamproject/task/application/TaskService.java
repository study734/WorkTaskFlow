package com.teamproject.task.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.task.application.dto.TaskDtos.CreateTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.AssignTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.ClaimTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.TaskHistoryResponse;
import com.teamproject.task.application.dto.TaskDtos.TaskResponse;
import com.teamproject.task.application.dto.TaskDtos.TransitionTaskRequest;
import com.teamproject.task.application.dto.TaskDtos.UpdateTaskRequest;
import com.teamproject.group.domain.Group;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.domain.TaskStatusHistory;
import com.teamproject.task.domain.TaskStatusHistoryRepository;
import com.teamproject.task.domain.TaskActivityEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TaskService {
    private final GroupAuthorization authorization;
    private final TaskRepository tasks;
    private final TaskStatusHistoryRepository histories;
    private final NotificationService notifications;
    private final TaskActivityRecorder activity;
    private final Clock clock;

    public TaskService(GroupAuthorization authorization, TaskRepository tasks,
            TaskStatusHistoryRepository histories, NotificationService notifications,
            TaskActivityRecorder activity, Clock clock) {
        this.authorization = authorization;
        this.tasks = tasks;
        this.histories = histories;
        this.notifications = notifications;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(Long userId, Long groupId, CreateTaskRequest request) {
        GroupMember requester = authorization.requireActiveMember(groupId, userId);
        Task task = tasks.save(new Task(requester.getGroup(), requester, request.title().trim(),
                blankToNull(request.description()), priority(request.priority()), request.dueAt()));
        histories.save(new TaskStatusHistory(task, null, task.getStatus(), requester, null));
        activity.record(task, requester, TaskActivityEvent.Type.TASK_CREATED);
        notifications.taskRequested(task, requester);
        return response(task);
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(Long userId, Long groupId) {
        authorization.requireActiveMember(groupId, userId);
        return tasks.findAllByGroupIdOrderByCreatedAtDesc(groupId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse get(Long userId, Long taskId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> notFound());
        authorization.requireActiveMember(task.getGroup().getId(), userId);
        return response(task);
    }

    @Transactional
    public TaskResponse transition(Long userId, Long taskId, TransitionTaskRequest request) {
        Task task = task(taskId);
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireVersion(task, request.expectedVersion());
        Task.Status from = task.getStatus();
        String action = request.action().trim().toUpperCase();
        String reason = blankToNull(request.reason());
        switch (action) {
            case "ACCEPT" -> {
                requireLeader(actor);
                requireStatus(task, Task.Status.REQUESTED);
                task.accept(actor);
            }
            case "REJECT" -> {
                requireLeader(actor);
                requireStatus(task, Task.Status.REQUESTED);
                task.reject(actor, requireReason(reason));
            }
            case "START" -> {
                requireAssignee(task, actor);
                requireStatus(task, Task.Status.TODO);
                task.start();
            }
            case "HOLD" -> {
                requireAssignee(task, actor);
                requireStatus(task, Task.Status.IN_PROGRESS);
                String requiredReason = requireReason(reason);
                Task.BlockerType blockerType = blockerType(request.blockerType());
                Task.BlockerNextActionType nextAction =
                        blockerNextActionType(request.blockerNextActionType());
                LocalDate reviewDate = requireReviewDate(
                        request.blockerReviewDate(), task.getGroup().getTimezone());
                task.hold(requiredReason, blockerType, nextAction, reviewDate);
            }
            case "RESUME" -> {
                requireAssignee(task, actor);
                requireStatus(task, Task.Status.ON_HOLD);
                task.resume();
            }
            case "COMPLETE" -> {
                requireAssignee(task, actor);
                requireStatus(task, Task.Status.IN_PROGRESS);
                task.complete();
            }
            case "REOPEN" -> {
                requireReopenPermission(task, actor);
                requireStatus(task, Task.Status.COMPLETED);
                requireReason(reason);
                task.reopen();
            }
            case "CANCEL" -> {
                requireCancelable(task, actor);
                if (isTerminal(task.getStatus())) invalidTransition();
                task.cancel(requireReason(reason));
            }
            default -> throw new ApplicationException("TASK_ACTION_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 업무 상태 변경 동작을 입력해 주세요.");
        }
        tasks.flush();
        histories.save(new TaskStatusHistory(task, from, task.getStatus(), actor, reason));
        activity.record(task, actor, TaskActivityEvent.Type.STATUS_CHANGED);
        notifications.taskStatusChanged(task, actor, from);
        return response(task);
    }

    @Transactional
    public TaskResponse assign(Long userId, Long taskId, AssignTaskRequest request) {
        Task task = task(taskId);
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireLeader(actor);
        requireVersion(task, request.expectedVersion());
        if (isTerminal(task.getStatus())) invalidTransition();
        GroupMember assignee = authorization.requireActiveMemberById(
                task.getGroup().getId(), request.assigneeMemberId());
        task.assign(assignee);
        tasks.flush();
        activity.record(task, actor, TaskActivityEvent.Type.ASSIGNEE_CHANGED);
        notifications.taskAssigned(task, actor, assignee);
        return response(task);
    }

    @Transactional
    public TaskResponse claim(Long userId, Long taskId, ClaimTaskRequest request) {
        Task task = task(taskId);
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireVersion(task, request.expectedVersion());
        if (task.getStatus() != Task.Status.TODO || task.getAssignee() != null) {
            throw new ApplicationException("TASK_CLAIM_UNAVAILABLE", HttpStatus.CONFLICT,
                    "이미 담당자가 있거나 지금은 담당할 수 없는 업무입니다.");
        }
        task.assign(actor);
        tasks.flush();
        activity.record(task, actor, TaskActivityEvent.Type.ASSIGNEE_CHANGED);
        notifications.taskAssigned(task, actor, actor);
        return response(task);
    }

    @Transactional
    public TaskResponse update(Long userId, Long taskId, UpdateTaskRequest request) {
        Task task = task(taskId);
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireVersion(task, request.expectedVersion());
        requireEditable(task, actor);
        String title = request.title() == null ? task.getTitle() : requireTitle(request.title());
        String description = request.description() == null
                ? task.getDescription() : blankToNull(request.description());
        Task.Priority priority = request.priority() == null
                ? task.getPriority() : priority(request.priority());
        LocalDateTime dueAt = Boolean.TRUE.equals(request.clearDueAt())
                ? null : request.dueAt() == null ? task.getDueAt() : request.dueAt();
        task.updateDetails(title, description, priority, dueAt);
        tasks.flush();
        activity.record(task, actor, TaskActivityEvent.Type.DETAILS_CHANGED);
        return response(task);
    }

    @Transactional(readOnly = true)
    public List<TaskHistoryResponse> histories(Long userId, Long taskId) {
        Task task = task(taskId);
        authorization.requireActiveMember(task.getGroup().getId(), userId);
        return histories.findAllByTaskIdOrderByCreatedAtAsc(taskId).stream().map(history ->
                new TaskHistoryResponse(history.getId(),
                        history.getFromStatus() == null ? null : history.getFromStatus().name(),
                        history.getToStatus().name(), history.getChangedBy().getId(),
                        history.getReason(), history.getCreatedAt())).toList();
    }

    private Task.Priority priority(String value) {
        if (value == null || value.isBlank()) return Task.Priority.NORMAL;
        try {
            return Task.Priority.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException("TASK_PRIORITY_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 업무 우선순위를 입력해 주세요.");
        }
    }

    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private ApplicationException notFound() {
        return new ApplicationException("TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다.");
    }
    private Task task(Long taskId) { return tasks.findById(taskId).orElseThrow(this::notFound); }
    private void requireVersion(Task task, Long expectedVersion) {
        if (task.getVersion() != expectedVersion) {
            throw new ApplicationException("TASK_VERSION_CONFLICT", HttpStatus.CONFLICT,
                    "업무가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
    }
    private void requireLeader(GroupMember actor) {
        if (actor.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("GROUP_LEADER_REQUIRED", HttpStatus.FORBIDDEN,
                    "그룹 팀장 권한이 필요합니다.");
        }
    }
    private void requireAssignee(Task task, GroupMember actor) {
        if (task.getAssignee() == null || !task.getAssignee().getId().equals(actor.getId())) {
            throw new ApplicationException("TASK_ASSIGNEE_REQUIRED", HttpStatus.FORBIDDEN,
                    "업무 담당자만 수행 상태를 변경할 수 있습니다.");
        }
    }
    private void requireCancelable(Task task, GroupMember actor) {
        boolean leader = actor.getRole() == GroupMember.Role.LEADER;
        boolean requestedByActor = task.getStatus() == Task.Status.REQUESTED
                && task.getRequester().getId().equals(actor.getId());
        if (!leader && !requestedByActor) {
            throw new ApplicationException("TASK_CANCEL_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "이 업무를 취소할 권한이 없습니다.");
        }
    }
    private void requireReopenPermission(Task task, GroupMember actor) {
        if (task.getGroup().getType() == Group.Type.TEAM && actor.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("TASK_REOPEN_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "완료 업무는 그룹 팀장만 재개할 수 있습니다.");
        }
    }
    private void requireEditable(Task task, GroupMember actor) {
        if (task.getGroup().getType() == Group.Type.PERSONAL) {
            if (isTerminal(task.getStatus())) {
                throw new ApplicationException("TASK_EDIT_STATE_INVALID", HttpStatus.CONFLICT,
                        "종료된 개인 업무는 수정할 수 없습니다.");
            }
            return;
        }
        if (task.getStatus() != Task.Status.REQUESTED) {
            throw new ApplicationException("TASK_EDIT_STATE_INVALID", HttpStatus.CONFLICT,
                    "승인 대기 중인 업무만 내용을 수정할 수 있습니다.");
        }
        boolean requester = task.getRequester().getId().equals(actor.getId());
        if (!requester && actor.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("TASK_EDIT_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "업무 요청자 또는 그룹 팀장만 수정할 수 있습니다.");
        }
    }
    private String requireTitle(String value) {
        if (value.isBlank()) {
            throw new ApplicationException("TASK_TITLE_REQUIRED", HttpStatus.BAD_REQUEST,
                    "업무 제목을 입력해 주세요.");
        }
        return value.trim();
    }
    private void requireStatus(Task task, Task.Status expected) {
        if (task.getStatus() != expected) invalidTransition();
    }
    private String requireReason(String reason) {
        if (reason == null) {
            throw new ApplicationException("TASK_REASON_REQUIRED", HttpStatus.BAD_REQUEST,
                    "상태 변경 사유를 입력해 주세요.");
        }
        return reason;
    }
    private Task.BlockerType blockerType(String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException("TASK_BLOCKER_TYPE_REQUIRED", HttpStatus.BAD_REQUEST,
                    "보류 사유 유형을 선택해 주세요.");
        }
        try {
            return Task.BlockerType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException("TASK_BLOCKER_TYPE_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 보류 사유 유형을 선택해 주세요.");
        }
    }
    private Task.BlockerNextActionType blockerNextActionType(String value) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException("TASK_BLOCKER_NEXT_ACTION_REQUIRED",
                    HttpStatus.BAD_REQUEST, "보류 해소를 위한 다음 조치를 선택해 주세요.");
        }
        try {
            return Task.BlockerNextActionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApplicationException("TASK_BLOCKER_NEXT_ACTION_INVALID",
                    HttpStatus.BAD_REQUEST, "올바른 다음 조치 유형을 선택해 주세요.");
        }
    }
    private LocalDate requireReviewDate(LocalDate value, String timezone) {
        if (value == null) {
            throw new ApplicationException("TASK_BLOCKER_REVIEW_DATE_REQUIRED",
                    HttpStatus.BAD_REQUEST, "보류 상태를 다시 확인할 날짜를 선택해 주세요.");
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(timezone)));
        if (value.isBefore(today)) {
            throw new ApplicationException("TASK_BLOCKER_REVIEW_DATE_PAST",
                    HttpStatus.BAD_REQUEST, "보류 확인일은 오늘 이후여야 합니다.");
        }
        return value;
    }
    private boolean isTerminal(Task.Status status) {
        return status == Task.Status.COMPLETED || status == Task.Status.REJECTED
                || status == Task.Status.CANCELLED;
    }
    private void invalidTransition() {
        throw new ApplicationException("TASK_TRANSITION_INVALID", HttpStatus.CONFLICT,
                "현재 상태에서는 요청한 업무 상태 변경을 할 수 없습니다.");
    }
    private TaskResponse response(Task task) {
        return new TaskResponse(task.getId(), task.getGroup().getId(), task.getRequester().getId(),
                task.getApprover() == null ? null : task.getApprover().getId(),
                task.getAssignee() == null ? null : task.getAssignee().getId(), task.getTitle(),
                task.getDescription(), task.getPriority().name(), task.getStatus().name(),
                task.getStartAt(), task.getDueAt(), task.getCompletedAt(),
                task.getHoldReason(),
                task.getBlockerType() == null ? null : task.getBlockerType().name(),
                task.getBlockerNextActionType() == null
                        ? null : task.getBlockerNextActionType().name(),
                task.getBlockerReviewDate(), task.getStopReason(),
                task.isDelayed(LocalDateTime.now()), task.getVersion(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
