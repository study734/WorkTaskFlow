package com.teamproject.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeeklyObjectiveRepository extends JpaRepository<WeeklyObjective, Long> {
    List<WeeklyObjective> findAllByGroupIdAndWeekStartOrderByPositionAscIdAsc(
            Long groupId, LocalDate weekStart);
    Optional<WeeklyObjective> findByIdAndGroupId(Long id, Long groupId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select objective from WeeklyObjective objective where objective.id = :id")
    Optional<WeeklyObjective> findByIdForUpdate(@Param("id") Long id);
    long countByGroupIdAndWeekStart(Long groupId, LocalDate weekStart);
    boolean existsByGroupIdAndWeekStartAndPositionAndIdNot(
            Long groupId, LocalDate weekStart, int position, Long id);
}
