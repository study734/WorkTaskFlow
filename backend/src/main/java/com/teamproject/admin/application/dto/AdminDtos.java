package com.teamproject.admin.application.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}
    public record OverviewResponse(long users, long activeUsers, long suspendedUsers,
            long groups, long teamGroups, long subscriptions, long paymentAttempts,
            long failedPayments, long reportDownloads, long reportDeliveries, long failedReportDeliveries) {}
    public record AdminUserResponse(Long id, String username, String maskedEmail, String nickname,
            String role, String status, LocalDateTime createdAt, LocalDateTime lastLoginAt) {}
    public record AdminGroupResponse(Long id, String name, String type, String membershipPlan,
            long activeMembers, String subscriptionStatus, boolean reportScheduleActive,
            LocalDateTime paidUntil, LocalDateTime createdAt) {}
    public record AdminPaymentResponse(Long id, Long userId, String operation, String orderId,
            Long amount, String status, Integer httpStatus, String providerCode, String providerMessage,
            int retryCount, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record AdminSubscriptionResponse(Long id, Long groupId, String groupName, String status,
            String conversionChoice, long amount, LocalDateTime currentPeriodEnd,
            LocalDateTime decisionDeadline) {}
    public record AdminAuditResponse(Long id, Long actorUserId, String method, String path,
            int status, String outcome, String ipAddress, String requestId, LocalDateTime occurredAt) {}
    public record AdminReportDownloadResponse(Long id, Long groupId, String groupName,
            Long requestedByUserId, String scope, String periodType, LocalDateTime createdAt) {}
    public record AdminReportDeliveryResponse(Long id, Long groupId, String groupName,
            String periodType, String language, String status, int retryCount, String errorCode,
            LocalDateTime lastAttemptAt, LocalDateTime nextRetryAt, LocalDateTime sentAt,
            LocalDateTime createdAt) {}
    public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
    public record RolloutNoticeRequest(@NotNull @Future LocalDateTime decisionDeadline) {}
    public record RolloutNoticeResponse(int notifiedGroups, LocalDateTime decisionDeadline) {}
}
