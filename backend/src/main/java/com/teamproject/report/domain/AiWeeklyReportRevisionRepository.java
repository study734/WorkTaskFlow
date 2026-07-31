package com.teamproject.report.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;

/**
 * v7-2 AI 주간 리포트 revision 저장소 (M5).
 */
public interface AiWeeklyReportRevisionRepository extends JpaRepository<AiWeeklyReportRevision, Long> {

    @Query("SELECT MAX(r.revision) FROM AiWeeklyReportRevision r " +
           "WHERE r.groupId = :groupId AND r.periodFrom = :periodFrom " +
           "AND r.periodToExclusive = :periodToExclusive AND r.language = :language")
    Optional<Integer> findMaxRevision(
            @Param("groupId") Long groupId,
            @Param("periodFrom") LocalDate periodFrom,
            @Param("periodToExclusive") LocalDate periodToExclusive,
            @Param("language") String language
    );

    Optional<AiWeeklyReportRevision> findByGroupIdAndPeriodFromAndPeriodToExclusiveAndLanguageAndSourceFingerprint(
            Long groupId,
            LocalDate periodFrom,
            LocalDate periodToExclusive,
            String language,
            String sourceFingerprint
    );

    Optional<AiWeeklyReportRevision> findTopByGroupIdAndPeriodFromAndPeriodToExclusiveAndLanguageOrderByRevisionDesc(
            Long groupId,
            LocalDate periodFrom,
            LocalDate periodToExclusive,
            String language
    );
}
