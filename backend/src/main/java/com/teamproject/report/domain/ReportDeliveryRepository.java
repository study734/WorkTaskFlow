package com.teamproject.report.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ReportDeliveryRepository extends JpaRepository<ReportDelivery, Long> {
    boolean existsByEventKey(String eventKey);
    List<ReportDelivery> findAllByStatusAndNextRetryAtLessThanEqual(
            ReportDelivery.Status status, LocalDateTime now);
    Page<ReportDelivery> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(ReportDelivery.Status status);
}
