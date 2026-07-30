package com.teamproject.group.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.dto.GroupDtos.CreateGroupRequest;
import com.teamproject.group.application.dto.GroupDtos.GroupResponse;
import com.teamproject.group.application.dto.GroupDtos.UpdateGroupRequest;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import com.teamproject.authentication.infrastructure.crypto.HashService;
import com.teamproject.common.storage.ImageStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.security.SecureRandom;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;

@Service
public class GroupService {
    private static final Logger securityLog = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final char[] JOIN_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final GroupRepository groups;
    private final GroupMemberRepository members;
    private final GroupAuthorization authorization;
    private final HashService hashes;
    private final ImageStorageService images;
    private final boolean testPlanSwitchEnabled;

    public GroupService(UserRepository users, GroupRepository groups, GroupMemberRepository members,
            GroupAuthorization authorization, HashService hashes, ImageStorageService images,
            @Value("${app.membership.test-switch-enabled:false}") boolean testPlanSwitchEnabled) {
        this.users = users;
        this.groups = groups;
        this.members = members;
        this.authorization = authorization;
        this.hashes = hashes;
        this.images = images;
        this.testPlanSwitchEnabled = testPlanSwitchEnabled;
    }

    @Transactional
    public GroupResponse uploadImage(Long userId, Long groupId, MultipartFile file) {
        GroupMember member = authorization.requireLeader(groupId, userId);
        String previous = member.getGroup().getImageUrl();
        member.getGroup().updateImage(images.store(file, "groups"));
        groups.flush();
        images.deleteManagedAfterCommit(previous);
        return response(member);
    }

    @Transactional
    public GroupResponse switchTestMembership(Long userId, Long groupId, String rawPlan) {
        if (!testPlanSwitchEnabled) {
            throw new ApplicationException("TEST_MEMBERSHIP_SWITCH_DISABLED", HttpStatus.FORBIDDEN,
                    "현재 환경에서는 테스트 멤버십 전환을 사용할 수 없습니다.");
        }
        GroupMember member = requireTeamLeader(groupId, userId);
        Group.MembershipPlan plan;
        try {
            plan = Group.MembershipPlan.valueOf(rawPlan.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new ApplicationException("MEMBERSHIP_PLAN_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 그룹 멤버십을 선택해 주세요.");
        }
        member.getGroup().switchTestMembership(plan, LocalDateTime.now());
        securityLog.info("event=GROUP_TEST_PLAN_CHANGED outcome=SUCCESS actorUserId={} groupId={} plan={}",
                userId, groupId, plan);
        return response(member);
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> list(Long userId) {
        return members.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(userId, GroupMember.Status.ACTIVE)
                .stream().map(this::response).toList();
    }

    @Transactional
    public GroupResponse createTeam(Long userId, CreateGroupRequest request) {
        User creator = users.findById(userId).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        String timezone = normalizeTimezone(request.timezone());
        String description = request.description() == null || request.description().isBlank()
                ? null : request.description().trim();
        String rawCode = newJoinCode();
        Group group = groups.save(Group.team(request.name().trim(), description, timezone, hashes.sha256(rawCode), creator));
        return response(members.save(GroupMember.leader(group, creator)), rawCode);
    }

    @Transactional
    public GroupResponse join(Long userId, String rawCode) {
        String code = rawCode == null ? "" : rawCode.replaceAll("\\s+", "").toUpperCase();
        Group group = groups.findByJoinCodeHash(hashes.sha256(code)).orElseThrow(() ->
                new ApplicationException("GROUP_JOIN_CODE_INVALID", HttpStatus.NOT_FOUND,
                        "그룹 키를 확인해 주세요."));
        if (group.getType() != Group.Type.TEAM) {
            throw new ApplicationException("GROUP_JOIN_CODE_INVALID", HttpStatus.NOT_FOUND,
                    "그룹 키를 확인해 주세요.");
        }
        User user = users.findById(userId).orElseThrow(() ->
                new ApplicationException("USER_NOT_FOUND", HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        GroupMember membership = members.findByGroupIdAndUserId(group.getId(), userId).orElse(null);
        if (membership != null && membership.getStatus() == GroupMember.Status.ACTIVE) {
            throw new ApplicationException("GROUP_ALREADY_JOINED", HttpStatus.CONFLICT,
                    "이미 참여 중인 그룹입니다.");
        }
        if (membership == null) membership = GroupMember.member(group, user);
        else membership.reactivateAsMember();
        return response(members.save(membership));
    }

    @Transactional(readOnly = true)
    public GroupResponse get(Long userId, Long groupId) {
        return response(authorization.requireActiveMember(groupId, userId));
    }

    @Transactional
    public GroupResponse update(Long userId, Long groupId, UpdateGroupRequest request) {
        GroupMember member = authorization.requireLeader(groupId, userId);
        Group group = member.getGroup();
        String name = request.name() == null ? group.getName() : requireName(request.name());
        String description = request.description() == null
                ? group.getDescription() : blankToNull(request.description());
        String timezone = request.timezone() == null
                ? group.getTimezone() : normalizeTimezone(request.timezone());
        Group.DashboardVisibility visibility = request.dashboardVisibility() == null
                ? group.getDashboardVisibility() : visibility(request.dashboardVisibility());
        if (group.getType() == Group.Type.PERSONAL && visibility != Group.DashboardVisibility.MEMBERS) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST,
                    "개인 그룹의 공개 범위는 변경할 수 없습니다.");
        }
        group.updateSettings(name, description, timezone, visibility);
        return response(member);
    }

    @Transactional
    public GroupResponse createJoinCode(Long userId, Long groupId) {
        GroupMember member = requireTeamLeader(groupId, userId);
        if (member.getGroup().getJoinCodeHash() != null) {
            throw new ApplicationException("GROUP_JOIN_CODE_EXISTS", HttpStatus.CONFLICT,
                    "이미 활성화된 그룹 키가 있습니다. 재발급을 이용해 주세요.");
        }
        String rawCode = newJoinCode();
        member.getGroup().issueJoinCodeHash(hashes.sha256(rawCode));
        securityLog.info("event=GROUP_KEY_CREATED outcome=SUCCESS actorUserId={} groupId={}", userId, groupId);
        return response(member, rawCode);
    }

    @Transactional
    public GroupResponse rotateJoinCode(Long userId, Long groupId) {
        GroupMember member = requireTeamLeader(groupId, userId);
        String rawCode = newJoinCode();
        member.getGroup().issueJoinCodeHash(hashes.sha256(rawCode));
        securityLog.info("event=GROUP_KEY_ROTATED outcome=SUCCESS actorUserId={} groupId={}", userId, groupId);
        return response(member, rawCode);
    }

    @Transactional
    public void revokeJoinCode(Long userId, Long groupId) {
        GroupMember member = requireTeamLeader(groupId, userId);
        member.getGroup().revokeJoinCode();
        securityLog.info("event=GROUP_KEY_REVOKED outcome=SUCCESS actorUserId={} groupId={}", userId, groupId);
    }

    private GroupMember requireTeamLeader(Long groupId, Long userId) {
        GroupMember member = authorization.requireLeader(groupId, userId);
        if (member.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST,
                    "개인 그룹에는 그룹 키를 사용할 수 없습니다.");
        }
        return member;
    }

    private String normalizeTimezone(String value) {
        String timezone = value == null || value.isBlank() ? "Asia/Seoul" : value.trim();
        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (DateTimeException exception) {
            throw new ApplicationException("TIMEZONE_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 timezone을 입력해 주세요.");
        }
    }

    private String requireName(String value) {
        if (value.isBlank()) {
            throw new ApplicationException("GROUP_NAME_REQUIRED", HttpStatus.BAD_REQUEST,
                    "그룹 이름을 입력해 주세요.");
        }
        return value.trim();
    }

    private String blankToNull(String value) { return value.isBlank() ? null : value.trim(); }

    private String newJoinCode() {
        String code;
        do {
            StringBuilder value = new StringBuilder(8);
            for (int index = 0; index < 8; index++) {
                value.append(JOIN_CODE_ALPHABET[random.nextInt(JOIN_CODE_ALPHABET.length)]);
            }
            code = value.toString();
        } while (groups.existsByJoinCodeHash(hashes.sha256(code)));
        return code;
    }

    private Group.DashboardVisibility visibility(String value) {
        try {
            return Group.DashboardVisibility.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new ApplicationException("DASHBOARD_VISIBILITY_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 대시보드 공개 범위를 입력해 주세요.");
        }
    }

    private GroupResponse response(GroupMember member) {
        return response(member, (String) null);
    }

    private GroupResponse response(GroupMember member, String newlyIssuedJoinCode) {
        Group group = member.getGroup();
        return new GroupResponse(group.getId(), group.getType().name(), group.getName(), group.getDescription(),
                group.getImageUrl(),
                group.getTimezone(), group.getDashboardVisibility().name(), group.getMembershipPlan().name(),
                group.getJoinCodeHash() != null,
                member.getRole() == GroupMember.Role.LEADER ? newlyIssuedJoinCode : null, member.getId(),
                member.getRole().name(), group.getPaidStartedAt(), group.getPaidUntil(),
                group.getNextBillingAt(), testPlanSwitchEnabled, group.getCreatedAt(), group.getUpdatedAt());
    }
}
