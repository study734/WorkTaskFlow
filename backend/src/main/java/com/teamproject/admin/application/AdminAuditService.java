package com.teamproject.admin.application;

import com.teamproject.admin.domain.*;
import com.teamproject.user.domain.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class AdminAuditService {
    private final AdminAuditLogRepository logs;
    private final UserRepository users;
    public AdminAuditService(AdminAuditLogRepository logs, UserRepository users) {
        this.logs = logs; this.users = users;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long actorId, String method, String path, int status,
            String ip, String userAgent, String requestId) {
        logs.save(new AdminAuditLog(actorId == null ? null : users.findById(actorId).orElse(null),
                method, path, status, ip, userAgent, requestId));
    }
    @Transactional(readOnly = true)
    public com.teamproject.admin.application.dto.AdminDtos.PageResponse<
            com.teamproject.admin.application.dto.AdminDtos.AdminAuditResponse> list(int page, int size) {
        var result = logs.findAllByOrderByOccurredAtDescIdDesc(
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        var items = result.getContent().stream().map(value ->
                new com.teamproject.admin.application.dto.AdminDtos.AdminAuditResponse(
                        value.getId(), value.getActor() == null ? null : value.getActor().getId(),
                        value.getHttpMethod(), value.getRequestPath(), value.getHttpStatus(),
                        value.getOutcome(), value.getIpAddress(), value.getRequestId(), value.getOccurredAt())).toList();
        return new com.teamproject.admin.application.dto.AdminDtos.PageResponse<>(
                items, result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
