package com.teamproject.group.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class GroupDtos {
    private GroupDtos() {}

    public record CreateGroupRequest(
            @NotBlank @Size(max = 80) String name,
            @Size(max = 500) String description,
            @Size(max = 50) String timezone) {}

    public record UpdateGroupRequest(
            @Size(max = 80) String name,
            @Size(max = 500) String description,
            @Size(max = 50) String timezone,
            String dashboardVisibility) {}

    public record GroupResponse(
            Long id, String type, String name, String description, String imageUrl, String timezone,
            String dashboardVisibility, String membershipPlan, boolean joinCodeActive, String joinCode,
            Long memberId, String role,
            LocalDateTime paidStartedAt, LocalDateTime paidUntil, LocalDateTime nextBillingAt,
            boolean testPlanSwitchEnabled, LocalDateTime createdAt, LocalDateTime updatedAt) {}

    public record JoinGroupRequest(@NotBlank @Size(min = 8, max = 12) String code) {}

    public record ReportAccessRequest(@NotBlank String scope, @NotBlank String periodType) {}

    public record ReportAccessResponse(boolean allowed, String membershipPlan, String scope,
            String periodType, Integer remainingThisWeek) {}

    public record TestMembershipRequest(@NotBlank String plan) {}

    public record CreateInvitationRequest(@NotBlank @Email @Size(max = 255) String email) {}

    public record InvitationResponse(
            Long id, Long groupId, String email, String status,
            LocalDateTime expiresAt, LocalDateTime acceptedAt, LocalDateTime createdAt) {}

    public record InviteLinkResponse(
            Long id, Long groupId, String status, String url,
            LocalDateTime expiresAt, int usedCount, LocalDateTime createdAt) {}

    public record MemberResponse(
            Long id, Long userId, String nickname, String profileImageUrl,
            String role, String status, LocalDateTime joinedAt) {}

    public record ChangeMemberRoleRequest(@NotBlank String role) {}
}
