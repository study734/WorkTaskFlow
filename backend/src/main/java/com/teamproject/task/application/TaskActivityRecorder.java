package com.teamproject.task.application;

import com.teamproject.group.domain.GroupMember;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskActivityEvent;
import com.teamproject.task.domain.TaskActivityEventRepository;
import com.teamproject.task.domain.TaskChecklistItemRepository;
import com.teamproject.task.domain.TaskWeeklyObjectiveLinkRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;

@Component
public class TaskActivityRecorder {
    private final TaskActivityEventRepository events;
    private final TaskChecklistItemRepository checklist;
    private final TaskWeeklyObjectiveLinkRepository objectiveLinks;
    private final Clock clock;

    public TaskActivityRecorder(TaskActivityEventRepository events,
            TaskChecklistItemRepository checklist,
            TaskWeeklyObjectiveLinkRepository objectiveLinks, Clock clock) {
        this.events = events;
        this.checklist = checklist;
        this.objectiveLinks = objectiveLinks;
        this.clock = clock;
    }

    public void record(Task task, GroupMember actor, TaskActivityEvent.Type type) {
        int total = Math.toIntExact(checklist.countByTaskId(task.getId()));
        int completed = Math.toIntExact(checklist.countByTaskIdAndCompletedTrue(task.getId()));
        Instant occurredAt = Instant.now(clock);
        LocalDate weekStart = occurredAt.atZone(ZoneId.of(task.getGroup().getTimezone()))
                .toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Long objectiveId = objectiveLinks.findByTaskIdAndWeekStart(task.getId(), weekStart)
                .map(link -> link.getObjective().getId())
                .orElse(null);
        events.save(new TaskActivityEvent(task, actor, type, occurredAt,
                total, completed, true, objectiveId));
    }
}
