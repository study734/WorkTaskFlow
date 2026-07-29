package com.teamproject.report.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.List;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {
    Optional<WeeklyReport> findFirstByGroupIdAndTypeAndPeriodStartAndPeriodEndOrderByRevisionDesc(
            Long groupId, WeeklyReport.Type type, LocalDate periodStart, LocalDate periodEnd);

    default Optional<WeeklyReport> findByGroupIdAndTypeAndPeriodStartAndPeriodEnd(
            Long groupId, WeeklyReport.Type type, LocalDate periodStart, LocalDate periodEnd) {
        return findFirstByGroupIdAndTypeAndPeriodStartAndPeriodEndOrderByRevisionDesc(
                groupId, type, periodStart, periodEnd);
    }

    Optional<WeeklyReport>
            findByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageAndRevision(
                    Long groupId, WeeklyReport.Type type, LocalDate periodStart,
                    LocalDate periodEnd, String language, int revision);

    Optional<WeeklyReport>
            findFirstByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageOrderByRevisionDesc(
                    Long groupId, WeeklyReport.Type type, LocalDate periodStart,
                    LocalDate periodEnd, String language);

    List<WeeklyReport>
    findAllByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageOrderByRevisionDesc(
            Long groupId, WeeklyReport.Type type, LocalDate periodStart,
            LocalDate periodEnd, String language);

    long countByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageAndStatus(
            Long groupId, WeeklyReport.Type type, LocalDate periodStart,
            LocalDate periodEnd, String language, WeeklyReport.Status status);

    Optional<WeeklyReport> findByIdAndGroupId(Long id, Long groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select report from WeeklyReport report
            where report.group.id = :groupId
              and report.type = :type
              and report.periodStart = :periodStart
              and report.periodEnd = :periodEnd
              and report.language = :language
              and report.revision = :revision
            """)
    Optional<WeeklyReport> findForUpdate(
            @Param("groupId") Long groupId,
            @Param("type") WeeklyReport.Type type,
            @Param("periodStart") LocalDate periodStart,
            @Param("periodEnd") LocalDate periodEnd,
            @Param("language") String language,
            @Param("revision") int revision);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select report from WeeklyReport report where report.id = :id")
    Optional<WeeklyReport> findByIdForUpdate(@Param("id") Long id);
}
