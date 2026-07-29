package com.teamproject.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {
    List<TaskChecklistItem> findAllByTaskIdOrderBySortOrderAscIdAsc(Long taskId);
    long countByTaskId(Long taskId);
    long countByTaskIdAndCompletedTrue(Long taskId);

    @Query("select coalesce(max(item.sortOrder), -1) from TaskChecklistItem item where item.task.id = :taskId")
    int findMaxSortOrderByTaskId(@Param("taskId") Long taskId);
}
