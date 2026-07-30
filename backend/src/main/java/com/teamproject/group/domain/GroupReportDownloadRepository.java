package com.teamproject.group.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface GroupReportDownloadRepository extends JpaRepository<GroupReportDownload, Long> {
    long countByGroupIdAndScopeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            Long groupId, GroupReportDownload.Scope scope, LocalDateTime from, LocalDateTime to);
    Page<GroupReportDownload> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
