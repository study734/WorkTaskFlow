package com.teamproject.report.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ReportScheduleRepository extends JpaRepository<ReportSchedule, Long> {
    Optional<ReportSchedule> findByGroupId(Long groupId);
    List<ReportSchedule> findAllByActiveTrue();
}
