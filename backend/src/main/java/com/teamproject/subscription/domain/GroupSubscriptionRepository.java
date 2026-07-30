package com.teamproject.subscription.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface GroupSubscriptionRepository extends JpaRepository<GroupSubscription, Long> {
    Optional<GroupSubscription> findByGroupId(Long groupId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroupSubscription s where s.group.id = :groupId")
    Optional<GroupSubscription> findByGroupIdForUpdate(@Param("groupId") Long groupId);
    List<GroupSubscription> findAllByStatusInAndCurrentPeriodEndLessThanEqual(
            List<GroupSubscription.Status> statuses, LocalDateTime now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from GroupSubscription s where s.status in ('ACTIVE', 'PAST_DUE') and s.nextBillingAt <= :now")
    List<GroupSubscription> findDueActiveForUpdate(@Param("now") LocalDateTime now);
    List<GroupSubscription> findAllByStatusAndPastDueSinceLessThanEqual(
            GroupSubscription.Status status, LocalDateTime deadline);
}
