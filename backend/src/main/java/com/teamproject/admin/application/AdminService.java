package com.teamproject.admin.application;

import com.teamproject.admin.application.dto.AdminDtos.*;
import com.teamproject.authentication.domain.token.RefreshToken;
import com.teamproject.authentication.domain.token.RefreshTokenRepository;
import com.teamproject.authentication.infrastructure.mail.MailService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.*;
import com.teamproject.notification.application.NotificationService;
import com.teamproject.payment.domain.*;
import com.teamproject.report.domain.*;
import com.teamproject.subscription.domain.*;
import com.teamproject.user.domain.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
public class AdminService {
    private final UserRepository users;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupSubscriptionRepository subscriptions;
    private final PaymentAttemptRepository attempts;
    private final GroupReportDownloadRepository reportDownloads;
    private final ReportDeliveryRepository reportDeliveries;
    private final ReportScheduleRepository reportSchedules;
    private final RefreshTokenRepository refreshTokens;
    private final NotificationService notifications;
    private final MailService mail;
    private final long teamMonthlyPrice;
    public AdminService(UserRepository users, GroupRepository groups, GroupMemberRepository members,
            GroupSubscriptionRepository subscriptions, PaymentAttemptRepository attempts,
            GroupReportDownloadRepository reportDownloads, ReportDeliveryRepository reportDeliveries,
            ReportScheduleRepository reportSchedules,
            RefreshTokenRepository refreshTokens, NotificationService notifications, MailService mail,
            @Value("${app.subscription.team-monthly-price:9900}") long teamMonthlyPrice) {
        this.users = users; this.groups = groups; this.members = members; this.subscriptions = subscriptions;
        this.attempts = attempts; this.reportDownloads = reportDownloads; this.reportDeliveries = reportDeliveries;
        this.reportSchedules = reportSchedules; this.refreshTokens = refreshTokens;
        this.notifications = notifications; this.mail = mail;
        this.teamMonthlyPrice = teamMonthlyPrice;
    }
    @Transactional(readOnly = true)
    public OverviewResponse overview() {
        return new OverviewResponse(users.count(), users.countByStatus(User.Status.ACTIVE),
                users.countByStatus(User.Status.SUSPENDED), groups.count(),
                groups.findAllByTypeOrderByCreatedAtDesc(Group.Type.TEAM).size(),
                subscriptions.count(), attempts.count(), attempts.countByStatus(PaymentAttempt.Status.FAILED),
                reportDownloads.count(), reportDeliveries.count(),
                reportDeliveries.countByStatus(ReportDelivery.Status.FAILED));
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> users(int page, int size) {
        var result = users.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage(page), safeSize(size)));
        return new PageResponse<>(result.map(user -> new AdminUserResponse(user.getId(), user.getUsername(),
                mask(user.getEmail()), user.getNickname(), user.getSystemRole().name(), user.getStatus().name(),
                user.getCreatedAt(), user.getLastLoginAt())).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @Transactional
    public AdminUserResponse changeStatus(Long actorId, Long userId, String status) {
        if (actorId.equals(userId)) throw new ApplicationException("ADMIN_SELF_STATUS_FORBIDDEN", HttpStatus.CONFLICT, "본인 운영자 계정 상태는 변경할 수 없습니다.");
        User user = users.findById(userId).orElseThrow(() -> new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        if (user.getSystemRole() == User.SystemRole.ADMIN) throw new ApplicationException("ADMIN_STATUS_FORBIDDEN", HttpStatus.FORBIDDEN, "다른 운영자 계정 상태는 변경할 수 없습니다.");
        if ("SUSPENDED".equalsIgnoreCase(status)) {
            user.suspend(); user.invalidateSessions();
            refreshTokens.findAllByUserId(userId).forEach(RefreshToken::revoke);
        } else if ("ACTIVE".equalsIgnoreCase(status)) user.activate();
        else throw new ApplicationException("USER_STATUS_INVALID", HttpStatus.BAD_REQUEST, "계정 상태를 확인해 주세요.");
        return new AdminUserResponse(user.getId(), user.getUsername(), mask(user.getEmail()), user.getNickname(),
                user.getSystemRole().name(), user.getStatus().name(), user.getCreatedAt(), user.getLastLoginAt());
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminGroupResponse> groups(int page, int size) {
        var result = groups.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage(page), safeSize(size)));
        var subscriptionByGroup = subscriptions.findAll().stream().collect(java.util.stream.Collectors.toMap(
                value -> value.getGroup().getId(), value -> value, (left, right) -> left));
        var scheduleByGroup = reportSchedules.findAll().stream().collect(java.util.stream.Collectors.toMap(
                value -> value.getGroup().getId(), value -> value, (left, right) -> left));
        var groupIds = result.getContent().stream().map(Group::getId).toList();
        var memberCountByGroup = groupIds.isEmpty() ? java.util.Map.<Long, Long>of()
                : members.countByGroupIdsAndStatus(groupIds, GroupMember.Status.ACTIVE).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                GroupMemberRepository.GroupMemberCount::getGroupId,
                                GroupMemberRepository.GroupMemberCount::getMemberCount));
        var items = result.getContent().stream().map(group -> {
            GroupSubscription subscription = subscriptionByGroup.get(group.getId());
            ReportSchedule schedule = scheduleByGroup.get(group.getId());
            return new AdminGroupResponse(group.getId(), group.getName(), group.getType().name(),
                    group.getMembershipPlan().name(),
                    memberCountByGroup.getOrDefault(group.getId(), 0L),
                    subscription == null ? "FREE" : subscription.getStatus().name(),
                    schedule != null && schedule.isActive(), group.getPaidUntil(), group.getCreatedAt());
        }).toList();
        return new PageResponse<>(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminPaymentResponse> payments(int page, int size) {
        var result = attempts.findAllByOrderByCreatedAtDesc(PageRequest.of(safePage(page), safeSize(size)));
        return new PageResponse<>(result.map(value -> new AdminPaymentResponse(value.getId(), value.getUser().getId(),
                value.getOperationType().name(), value.getOrderId(), value.getAmount(), value.getStatus().name(),
                value.getHttpStatus(), value.getProviderCode(), value.getProviderMessage(), value.getRetryCount(),
                value.getCreatedAt(), value.getUpdatedAt())).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminReportDownloadResponse> reportDownloads(int page, int size) {
        var result = reportDownloads.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage(page), safeSize(size)));
        return new PageResponse<>(result.map(value -> new AdminReportDownloadResponse(value.getId(),
                value.getGroup().getId(), value.getGroup().getName(),
                value.getRequestedBy().getUser().getId(), value.getScope().name(),
                value.getPeriodType().name(), value.getCreatedAt())).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public PageResponse<AdminReportDeliveryResponse> reportDeliveries(int page, int size) {
        var result = reportDeliveries.findAllByOrderByCreatedAtDesc(
                PageRequest.of(safePage(page), safeSize(size)));
        return new PageResponse<>(result.map(value -> new AdminReportDeliveryResponse(value.getId(),
                value.getSchedule().getGroup().getId(), value.getSchedule().getGroup().getName(),
                value.getPeriodType().name(), value.getLanguage().name(), value.getStatus().name(),
                value.getRetryCount(), value.getErrorCode(), value.getLastAttemptAt(), value.getNextRetryAt(),
                value.getSentAt(), value.getCreatedAt())).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
    @Transactional(readOnly = true)
    public java.util.List<AdminSubscriptionResponse> subscriptions() {
        return subscriptions.findAll().stream().map(value -> new AdminSubscriptionResponse(value.getId(),
                value.getGroup().getId(), value.getGroup().getName(), value.getStatus().name(),
                value.getConversionChoice().name(), value.getAmount(), value.getCurrentPeriodEnd(),
                value.getDecisionDeadline())).toList();
    }
    @Transactional
    public RolloutNoticeResponse announce(LocalDateTime deadline) {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;
        for (Group group : groups.findAllByTypeOrderByCreatedAtDesc(Group.Type.TEAM)) {
            GroupMember leader = members.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(
                    group.getId(), GroupMember.Status.ACTIVE).stream()
                    .filter(value -> value.getRole() == GroupMember.Role.LEADER).findFirst().orElse(null);
            if (leader == null) continue;
            GroupSubscription subscription = subscriptions.findByGroupId(group.getId())
                    .orElseGet(() -> subscriptions.save(new GroupSubscription(group, leader.getUser(), "TEAM", teamMonthlyPrice)));
            subscription.announce(now, deadline);
            String eventKey = "SUBSCRIPTION_ROLLOUT:" + group.getId() + ":" + deadline.toLocalDate();
            notifications.subscriptionRollout(leader.getUser(), group, eventKey, deadline);
            mail.sendBestEffort(leader.getUser().getEmail(), "[퇴사] 구독 정책 변경 사전 안내",
                    "퇴사 유료 구독 전환 예정 안내입니다.\n결정 기한: " + deadline
                            + "\n기한까지 무료 유지 또는 유료 구독 전환을 선택할 수 있습니다. 선택하지 않으면 무료 상태를 유지하며 그룹 데이터는 삭제되지 않습니다.");
            count++;
        }
        return new RolloutNoticeResponse(count, deadline);
    }
    private int safePage(int page) { return Math.max(0, page); }
    private int safeSize(int size) { return Math.min(100, Math.max(1, size)); }
    private String mask(String email) {
        int at = email.indexOf('@');
        return at <= 1 ? "***" : email.substring(0, 1) + "***" + email.substring(at);
    }
}
