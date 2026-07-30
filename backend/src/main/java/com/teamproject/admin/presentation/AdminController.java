package com.teamproject.admin.presentation;

import com.teamproject.admin.application.AdminService;
import com.teamproject.admin.application.dto.AdminDtos.*;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {
    private final AdminService admin;
    private final com.teamproject.admin.application.AdminAuditService audit;
    public AdminController(AdminService admin, com.teamproject.admin.application.AdminAuditService audit) {
        this.admin = admin; this.audit = audit;
    }
    @GetMapping("/overview") OverviewResponse overview() { return admin.overview(); }
    @GetMapping("/users")
    PageResponse<AdminUserResponse> users(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) { return admin.users(page, size); }
    @PatchMapping("/users/{userId}/status")
    AdminUserResponse status(Authentication auth, @PathVariable Long userId,
            @RequestBody Map<String, String> request) {
        return admin.changeStatus((Long) auth.getPrincipal(), userId, request.getOrDefault("status", ""));
    }
    @GetMapping("/groups")
    PageResponse<AdminGroupResponse> groups(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return admin.groups(page, size); }
    @GetMapping("/payments")
    PageResponse<AdminPaymentResponse> payments(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) { return admin.payments(page, size); }
    @GetMapping("/subscriptions") List<AdminSubscriptionResponse> subscriptions() { return admin.subscriptions(); }
    @GetMapping("/report-downloads")
    PageResponse<AdminReportDownloadResponse> reportDownloads(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return admin.reportDownloads(page, size); }
    @GetMapping("/report-deliveries")
    PageResponse<AdminReportDeliveryResponse> reportDeliveries(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) { return admin.reportDeliveries(page, size); }
    @PostMapping("/subscriptions/rollout-notice")
    RolloutNoticeResponse announce(@Valid @RequestBody RolloutNoticeRequest request) {
        return admin.announce(request.decisionDeadline());
    }
    @GetMapping("/audit-logs")
    PageResponse<AdminAuditResponse> auditLogs(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return audit.list(page, size);
    }
}
