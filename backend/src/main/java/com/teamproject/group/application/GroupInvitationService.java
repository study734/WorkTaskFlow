package com.teamproject.group.application;

import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.authentication.infrastructure.mail.MailService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.dto.GroupDtos.InvitationResponse;
import com.teamproject.group.application.dto.GroupDtos.InviteLinkResponse;
import com.teamproject.group.application.dto.GroupDtos.MemberResponse;
import com.teamproject.group.domain.*;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class GroupInvitationService {
    private final SecureRandom random = new SecureRandom();
    private final GroupAuthorization authorization;
    private final GroupInvitationRepository invitations;
    private final GroupInviteLinkRepository inviteLinks;
    private final GroupMemberRepository members;
    private final UserRepository users;
    private final HashService hashes;
    private final MailService mail;
    private final String frontendUrl;
    private final long invitationHours;

    public GroupInvitationService(GroupAuthorization authorization, GroupInvitationRepository invitations,
            GroupInviteLinkRepository inviteLinks, GroupMemberRepository members,
            UserRepository users, HashService hashes, MailService mail,
            @Value("${app.frontend-url}") String frontendUrl,
            @Value("${app.group.invitation-hours}") long invitationHours) {
        this.authorization = authorization;
        this.invitations = invitations;
        this.inviteLinks = inviteLinks;
        this.members = members;
        this.users = users;
        this.hashes = hashes;
        this.mail = mail;
        this.frontendUrl = frontendUrl;
        this.invitationHours = invitationHours;
    }

    @Transactional
    public InvitationResponse invite(Long userId, Long groupId, String rawEmail) {
        GroupMember inviter = requireTeamLeader(groupId, userId);
        String email = normalizeEmail(rawEmail);
        users.findByEmailIgnoreCase(email).ifPresent(user -> {
            if (members.findByGroupIdAndUserIdAndStatus(groupId, user.getId(), GroupMember.Status.ACTIVE).isPresent()) {
                throw new ApplicationException("GROUP_MEMBER_EXISTS", HttpStatus.CONFLICT,
                        "이미 그룹에 참여 중인 사용자입니다.");
            }
        });
        invitations.findFirstByGroupIdAndEmailIgnoreCaseAndStatusOrderByCreatedAtDesc(
                groupId, email, GroupInvitation.Status.PENDING).ifPresent(existing -> {
            if (existing.isPendingAt(LocalDateTime.now())) {
                throw new ApplicationException("INVITATION_ALREADY_PENDING", HttpStatus.CONFLICT,
                        "이미 대기 중인 초대가 있습니다.");
            }
            existing.expire();
        });

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(32));
        GroupInvitation invitation = invitations.save(new GroupInvitation(inviter.getGroup(), email, inviter,
                hashes.sha256(token), LocalDateTime.now().plusHours(invitationHours)));
        String link = frontendUrl + "/group-invitations/accept?token=" + token;
        mail.sendBestEffort(email, "[퇴사] 그룹 초대", inviter.getGroup().getName()
                + " 그룹에 초대되었습니다.\n" + link + "\n" + invitationHours + "시간 안에 수락해 주세요.");
        return response(invitation);
    }

    @Transactional
    public InviteLinkResponse createLink(Long userId, Long groupId) {
        GroupMember inviter = requireTeamLeader(groupId, userId);
        LocalDateTime now = LocalDateTime.now();
        inviteLinks.findAllByGroupIdAndStatusOrderByCreatedAtDesc(
                groupId, GroupInviteLink.Status.ACTIVE).forEach(existing -> {
                    if (existing.isActiveAt(now)) existing.revoke();
                    else existing.expire();
                });
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random.generateSeed(32));
        GroupInviteLink inviteLink = inviteLinks.save(new GroupInviteLink(inviter.getGroup(), inviter,
                hashes.sha256(token), now.plusHours(invitationHours)));
        return response(inviteLink, frontendUrl + "/group-invitations/accept?token=" + token);
    }

    @Transactional
    public List<InviteLinkResponse> listLinks(Long userId, Long groupId) {
        requireTeamLeader(groupId, userId);
        LocalDateTime now = LocalDateTime.now();
        return inviteLinks.findAllByGroupIdAndStatusOrderByCreatedAtDesc(
                groupId, GroupInviteLink.Status.ACTIVE).stream()
                .peek(value -> { if (!value.isActiveAt(now)) value.expire(); })
                .filter(value -> value.getStatus() == GroupInviteLink.Status.ACTIVE)
                .map(value -> response(value, null))
                .toList();
    }

    @Transactional
    public void revokeLink(Long userId, Long groupId, Long linkId) {
        requireTeamLeader(groupId, userId);
        GroupInviteLink inviteLink = inviteLinks.findByIdAndGroupId(linkId, groupId)
                .orElseThrow(() -> new ApplicationException("INVITE_LINK_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "초대 링크를 찾을 수 없습니다."));
        if (!inviteLink.isActiveAt(LocalDateTime.now())) {
            throw new ApplicationException("INVITE_LINK_NOT_ACTIVE", HttpStatus.BAD_REQUEST,
                    "사용 중인 초대 링크만 해제할 수 있습니다.");
        }
        inviteLink.revoke();
    }

    @Transactional
    public List<InvitationResponse> list(Long userId, Long groupId) {
        authorization.requireLeader(groupId, userId);
        LocalDateTime now = LocalDateTime.now();
        return invitations.findAllByGroupIdOrderByCreatedAtDesc(groupId).stream()
                .peek(value -> { if (!value.isPendingAt(now) && value.getStatus() == GroupInvitation.Status.PENDING) value.expire(); })
                .map(this::response).toList();
    }

    @Transactional
    public void cancel(Long userId, Long groupId, Long invitationId) {
        authorization.requireLeader(groupId, userId);
        GroupInvitation invitation = invitations.findByIdAndGroupId(invitationId, groupId)
                .orElseThrow(() -> new ApplicationException("INVITATION_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "초대를 찾을 수 없습니다."));
        if (!invitation.isPendingAt(LocalDateTime.now())) {
            throw new ApplicationException("INVITATION_NOT_PENDING", HttpStatus.BAD_REQUEST,
                    "대기 중인 초대만 취소할 수 있습니다.");
        }
        invitation.cancel();
    }

    @Transactional
    public MemberResponse accept(Long userId, String token) {
        User user = users.findById(userId).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        String tokenHash = hashes.sha256(token);
        LocalDateTime now = LocalDateTime.now();
        var emailInvitation = invitations.findByTokenHash(tokenHash);
        if (emailInvitation.isPresent()) {
            GroupInvitation invitation = emailInvitation.get();
            if (!invitation.isUsable(tokenHash, now)) throw invalidInvitation();
            if (!invitation.getEmail().equalsIgnoreCase(user.getEmail())) {
                throw new ApplicationException("INVITATION_EMAIL_MISMATCH", HttpStatus.FORBIDDEN,
                        "초대받은 이메일 계정으로 로그인해 주세요.");
            }
            GroupMember member = join(invitation.getGroup(), user);
            invitation.accept(now);
            return response(member);
        }
        GroupInviteLink inviteLink = inviteLinks.findByTokenHash(tokenHash)
                .orElseThrow(this::invalidInvitation);
        if (!inviteLink.isUsable(tokenHash, now)) throw invalidInvitation();
        GroupMember member = join(inviteLink.getGroup(), user);
        inviteLink.use();
        return response(member);
    }

    private GroupMember join(Group group, User user) {
        Long groupId = group.getId();
        Long userId = user.getId();
        if (members.findByGroupIdAndUserIdAndStatus(groupId, userId, GroupMember.Status.ACTIVE).isPresent()) {
            throw new ApplicationException("GROUP_MEMBER_EXISTS", HttpStatus.CONFLICT,
                    "이미 그룹에 참여 중인 사용자입니다.");
        }
        return members.findByGroupIdAndUserId(groupId, userId)
                .map(existing -> { existing.reactivateAsMember(); return existing; })
                .orElseGet(() -> members.save(GroupMember.member(group, user)));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> members(Long userId, Long groupId) {
        authorization.requireActiveMember(groupId, userId);
        return members.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(groupId, GroupMember.Status.ACTIVE)
                .stream().map(this::response).toList();
    }

    private String normalizeEmail(String email) { return email.trim().toLowerCase(Locale.ROOT); }
    private GroupMember requireTeamLeader(Long groupId, Long userId) {
        GroupMember inviter = authorization.requireLeader(groupId, userId);
        if (inviter.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST,
                    "개인 그룹에는 멤버를 초대할 수 없습니다.");
        }
        return inviter;
    }
    private ApplicationException invalidInvitation() {
        return new ApplicationException("INVITATION_INVALID", HttpStatus.BAD_REQUEST,
                "초대가 올바르지 않거나 만료되었습니다.");
    }
    private InvitationResponse response(GroupInvitation value) {
        return new InvitationResponse(value.getId(), value.getGroup().getId(), value.getEmail(),
                value.getStatus().name(), value.getExpiresAt(), value.getAcceptedAt(), value.getCreatedAt());
    }
    private InviteLinkResponse response(GroupInviteLink value, String url) {
        return new InviteLinkResponse(value.getId(), value.getGroup().getId(), value.getStatus().name(),
                url, value.getExpiresAt(), value.getUsedCount(), value.getCreatedAt());
    }
    private MemberResponse response(GroupMember value) {
        return new MemberResponse(value.getId(), value.getUser().getId(), value.getUser().getNickname(),
                value.getUser().getProfileImageUrl(), value.getRole().name(), value.getStatus().name(), value.getJoinedAt());
    }
}
