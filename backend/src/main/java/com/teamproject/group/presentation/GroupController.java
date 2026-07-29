package com.teamproject.group.presentation;

import com.teamproject.group.application.GroupService;
import com.teamproject.group.application.GroupInvitationService;
import com.teamproject.group.application.GroupMemberService;
import com.teamproject.report.application.BasicReportAccessService;
import com.teamproject.group.application.dto.GroupDtos.CreateGroupRequest;
import com.teamproject.group.application.dto.GroupDtos.GroupResponse;
import com.teamproject.group.application.dto.GroupDtos.UpdateGroupRequest;
import com.teamproject.group.application.dto.GroupDtos.CreateInvitationRequest;
import com.teamproject.group.application.dto.GroupDtos.InvitationResponse;
import com.teamproject.group.application.dto.GroupDtos.InviteLinkResponse;
import com.teamproject.group.application.dto.GroupDtos.MemberResponse;
import com.teamproject.group.application.dto.GroupDtos.ChangeMemberRoleRequest;
import com.teamproject.group.application.dto.GroupDtos.JoinGroupRequest;
import com.teamproject.group.application.dto.GroupDtos.ReportAccessRequest;
import com.teamproject.group.application.dto.GroupDtos.ReportAccessResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/v1/groups")
public class GroupController {
    private final GroupService groups;
    private final GroupInvitationService invitations;
    private final GroupMemberService members;
    private final BasicReportAccessService reports;

    public GroupController(GroupService groups, GroupInvitationService invitations, GroupMemberService members,
            BasicReportAccessService reports) {
        this.groups = groups;
        this.invitations = invitations;
        this.members = members;
        this.reports = reports;
    }

    @GetMapping
    List<GroupResponse> list(Authentication authentication) {
        return groups.list((Long) authentication.getPrincipal());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse create(Authentication authentication, @Valid @RequestBody CreateGroupRequest request) {
        return groups.createTeam((Long) authentication.getPrincipal(), request);
    }

    @PostMapping("/join")
    GroupResponse join(Authentication authentication, @Valid @RequestBody JoinGroupRequest request) {
        return groups.join((Long) authentication.getPrincipal(), request.code());
    }

    @PostMapping("/{groupId}/reports/access")
    ReportAccessResponse reportAccess(Authentication authentication, @PathVariable Long groupId,
            @Valid @RequestBody ReportAccessRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        reports.validate(userId, groupId, request.scope(), request.periodType());
        return reports.record(userId, groupId, request.scope(), request.periodType());
    }

    @GetMapping("/{groupId}")
    GroupResponse get(Authentication authentication, @PathVariable Long groupId) {
        return groups.get((Long) authentication.getPrincipal(), groupId);
    }

    @PatchMapping("/{groupId}")
    GroupResponse update(Authentication authentication, @PathVariable Long groupId,
            @Valid @RequestBody UpdateGroupRequest request) {
        return groups.update((Long) authentication.getPrincipal(), groupId, request);
    }

    @PostMapping("/{groupId}/image")
    GroupResponse uploadImage(Authentication authentication, @PathVariable Long groupId,
            @RequestPart("file") MultipartFile file) {
        return groups.uploadImage((Long) authentication.getPrincipal(), groupId, file);
    }

    @PostMapping("/{groupId}/join-code")
    @ResponseStatus(HttpStatus.CREATED)
    GroupResponse createJoinCode(Authentication authentication, @PathVariable Long groupId) {
        return groups.createJoinCode((Long) authentication.getPrincipal(), groupId);
    }

    @PutMapping("/{groupId}/join-code")
    GroupResponse rotateJoinCode(Authentication authentication, @PathVariable Long groupId) {
        return groups.rotateJoinCode((Long) authentication.getPrincipal(), groupId);
    }

    @DeleteMapping("/{groupId}/join-code")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeJoinCode(Authentication authentication, @PathVariable Long groupId) {
        groups.revokeJoinCode((Long) authentication.getPrincipal(), groupId);
    }

    @GetMapping("/{groupId}/members")
    List<MemberResponse> members(Authentication authentication, @PathVariable Long groupId) {
        return invitations.members((Long) authentication.getPrincipal(), groupId);
    }

    @PostMapping("/{groupId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResponse invite(Authentication authentication, @PathVariable Long groupId,
            @Valid @RequestBody CreateInvitationRequest request) {
        return invitations.invite((Long) authentication.getPrincipal(), groupId, request.email());
    }

    @GetMapping("/{groupId}/invitations")
    List<InvitationResponse> invitations(Authentication authentication, @PathVariable Long groupId) {
        return invitations.list((Long) authentication.getPrincipal(), groupId);
    }

    @PostMapping("/{groupId}/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    InviteLinkResponse createInviteLink(Authentication authentication, @PathVariable Long groupId) {
        return invitations.createLink((Long) authentication.getPrincipal(), groupId);
    }

    @GetMapping("/{groupId}/invite-links")
    List<InviteLinkResponse> inviteLinks(Authentication authentication, @PathVariable Long groupId) {
        return invitations.listLinks((Long) authentication.getPrincipal(), groupId);
    }

    @DeleteMapping("/{groupId}/invite-links/{linkId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeInviteLink(Authentication authentication, @PathVariable Long groupId,
            @PathVariable Long linkId) {
        invitations.revokeLink((Long) authentication.getPrincipal(), groupId, linkId);
    }

    @DeleteMapping("/{groupId}/invitations/{invitationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(Authentication authentication, @PathVariable Long groupId, @PathVariable Long invitationId) {
        invitations.cancel((Long) authentication.getPrincipal(), groupId, invitationId);
    }

    @PatchMapping("/{groupId}/members/{memberId}/role")
    MemberResponse changeRole(Authentication authentication, @PathVariable Long groupId,
            @PathVariable Long memberId, @Valid @RequestBody ChangeMemberRoleRequest request) {
        return members.changeRole((Long) authentication.getPrincipal(), groupId, memberId, request.role());
    }

    @DeleteMapping("/{groupId}/members/{memberId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(Authentication authentication, @PathVariable Long groupId, @PathVariable Long memberId) {
        members.remove((Long) authentication.getPrincipal(), groupId, memberId);
    }

    @DeleteMapping("/{groupId}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void leave(Authentication authentication, @PathVariable Long groupId) {
        members.leave((Long) authentication.getPrincipal(), groupId);
    }
}
