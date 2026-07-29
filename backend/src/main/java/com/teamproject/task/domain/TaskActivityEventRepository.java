package com.teamproject.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskActivityEventRepository extends JpaRepository<TaskActivityEvent, Long> {
    long countByTaskId(Long taskId);
    List<TaskActivityEvent> findAllByTaskIdOrderByOccurredAtAscIdAsc(Long taskId);
    Optional<TaskActivityEvent> findFirstByGroupIdAndEventTypeOrderByOccurredAtAsc(
            Long groupId, TaskActivityEvent.Type eventType);
    Optional<TaskActivityEvent> findFirstByGroupIdOrderByOccurredAtAsc(Long groupId);
    Optional<TaskActivityEvent> findFirstByGroupIdAndSnapshotVersionOrderByOccurredAtAscIdAsc(
            Long groupId, int snapshotVersion);

    List<TaskActivityEvent> findAllByGroupIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
            Long groupId, LocalDateTime from, LocalDateTime to);

    List<TaskActivityEvent> findAllByTaskIdInAndOccurredAtLessThanOrderByOccurredAtAscIdAsc(
            Collection<Long> taskIds, LocalDateTime to);
}
