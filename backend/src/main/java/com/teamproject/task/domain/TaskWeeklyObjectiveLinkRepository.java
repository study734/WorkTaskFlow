package com.teamproject.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TaskWeeklyObjectiveLinkRepository
        extends JpaRepository<TaskWeeklyObjectiveLink, Long> {
    Optional<TaskWeeklyObjectiveLink> findByTaskIdAndWeekStart(Long taskId, LocalDate weekStart);
    List<TaskWeeklyObjectiveLink> findAllByObjectiveId(Long objectiveId);
    List<TaskWeeklyObjectiveLink> findAllByTaskIdInAndWeekStart(
            List<Long> taskIds, LocalDate weekStart);
}
