package com.teamproject.task.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.task.application.dto.ChecklistDtos.ChecklistItemResponse;
import com.teamproject.task.application.dto.ChecklistDtos.ChecklistResponse;
import com.teamproject.task.application.dto.ChecklistDtos.CreateChecklistItemRequest;
import com.teamproject.task.application.dto.ChecklistDtos.UpdateChecklistItemRequest;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskChecklistItem;
import com.teamproject.task.domain.TaskChecklistItemRepository;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.task.domain.TaskActivityEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class TaskChecklistService {
    private final TaskRepository tasks;
    private final TaskChecklistItemRepository items;
    private final GroupAuthorization authorization;
    private final TaskActivityRecorder activity;

    public TaskChecklistService(TaskRepository tasks, TaskChecklistItemRepository items,
            GroupAuthorization authorization, TaskActivityRecorder activity) {
        this.tasks = tasks;
        this.items = items;
        this.authorization = authorization;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public ChecklistResponse list(Long userId, Long taskId) {
        Task task = task(taskId);
        authorization.requireActiveMember(task.getGroup().getId(), userId);
        return checklist(items.findAllByTaskIdOrderBySortOrderAscIdAsc(taskId));
    }

    @Transactional
    public ChecklistItemResponse create(Long userId, Long taskId, CreateChecklistItemRequest request) {
        Task task = task(taskId);
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireWritable(task, actor);
        int sortOrder = request.sortOrder() == null
                ? items.findMaxSortOrderByTaskId(taskId) + 1 : request.sortOrder();
        TaskChecklistItem item = items.saveAndFlush(
                new TaskChecklistItem(task, request.content().trim(), sortOrder));
        activity.record(task, actor, TaskActivityEvent.Type.CHECKLIST_CHANGED);
        return response(item);
    }

    @Transactional
    public ChecklistItemResponse update(Long userId, Long itemId, UpdateChecklistItemRequest request) {
        TaskChecklistItem item = item(itemId);
        Task task = item.getTask();
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireWritable(task, actor);
        requireVersion(item, request.expectedVersion());
        String content = request.content() == null ? null : requireContent(request.content());
        item.update(content, request.sortOrder());
        if (request.completed() != null && request.completed() != item.isCompleted()) {
            item.changeCompletion(request.completed(), actor);
        }
        items.flush();
        activity.record(task, actor, TaskActivityEvent.Type.CHECKLIST_CHANGED);
        return response(item);
    }

    @Transactional
    public void delete(Long userId, Long itemId, Long expectedVersion) {
        TaskChecklistItem item = item(itemId);
        Task task = item.getTask();
        GroupMember actor = authorization.requireActiveMember(task.getGroup().getId(), userId);
        requireWritable(task, actor);
        requireVersion(item, expectedVersion);
        items.delete(item);
        items.flush();
        activity.record(task, actor, TaskActivityEvent.Type.CHECKLIST_CHANGED);
    }

    private void requireWritable(Task task, GroupMember actor) {
        if (isTerminal(task.getStatus())) {
            throw new ApplicationException("CHECKLIST_TASK_TERMINAL", HttpStatus.CONFLICT,
                    "종료된 업무의 체크리스트는 변경할 수 없습니다.");
        }
        boolean assignee = task.getAssignee() != null && task.getAssignee().getId().equals(actor.getId());
        if (!assignee && actor.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("CHECKLIST_WRITE_FORBIDDEN", HttpStatus.FORBIDDEN,
                    "업무 담당자 또는 그룹 팀장만 체크리스트를 변경할 수 있습니다.");
        }
    }

    private void requireVersion(TaskChecklistItem item, Long expectedVersion) {
        if (item.getVersion() != expectedVersion) {
            throw new ApplicationException("CHECKLIST_VERSION_CONFLICT", HttpStatus.CONFLICT,
                    "체크리스트가 이미 변경되었습니다. 새로고침 후 다시 시도해 주세요.");
        }
    }

    private String requireContent(String value) {
        if (value.isBlank()) {
            throw new ApplicationException("CHECKLIST_CONTENT_REQUIRED", HttpStatus.BAD_REQUEST,
                    "체크리스트 내용을 입력해 주세요.");
        }
        return value.trim();
    }

    private ChecklistResponse checklist(List<TaskChecklistItem> values) {
        int completed = (int) values.stream().filter(TaskChecklistItem::isCompleted).count();
        Integer progress = values.isEmpty() ? null : completed * 100 / values.size();
        return new ChecklistResponse(values.stream().map(this::response).toList(),
                values.size(), completed, progress);
    }

    private ChecklistItemResponse response(TaskChecklistItem item) {
        return new ChecklistItemResponse(item.getId(), item.getTask().getId(), item.getContent(),
                item.isCompleted(), item.getCompletedBy() == null ? null : item.getCompletedBy().getId(),
                item.getCompletedAt(), item.getSortOrder(), item.getVersion(),
                item.getCreatedAt(), item.getUpdatedAt());
    }

    private Task task(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new ApplicationException(
                "TASK_NOT_FOUND", HttpStatus.NOT_FOUND, "업무를 찾을 수 없습니다."));
    }
    private TaskChecklistItem item(Long itemId) {
        return items.findById(itemId).orElseThrow(() -> new ApplicationException(
                "CHECKLIST_ITEM_NOT_FOUND", HttpStatus.NOT_FOUND, "체크리스트 항목을 찾을 수 없습니다."));
    }
    private boolean isTerminal(Task.Status status) {
        return status == Task.Status.COMPLETED || status == Task.Status.REJECTED
                || status == Task.Status.CANCELLED;
    }
}
