package com.teamproject.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface TaskChecklistItemRepository extends JpaRepository<TaskChecklistItem, Long> {
    List<TaskChecklistItem> findAllByTaskIdOrderBySortOrderAscIdAsc(Long taskId);
    long countByTaskId(Long taskId);
    long countByTaskIdAndCompletedTrue(Long taskId);

    @Query("select coalesce(max(item.sortOrder), -1) from TaskChecklistItem item where item.task.id = :taskId")
    int findMaxSortOrderByTaskId(@Param("taskId") Long taskId);

    /** 리포트가 업무 수십~수백 건의 체크리스트를 한 번에 센다. 건별 count는 N+1이 된다. */
    @Query("""
            select item.task.id, count(item), sum(case when item.completed = true then 1 else 0 end)
            from TaskChecklistItem item
            where item.task.id in :taskIds
            group by item.task.id
            """)
    List<Object[]> countByTaskIds(@Param("taskIds") Collection<Long> taskIds);
}
