package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI 주간 리포트 v7-2 권한 검증 전용 서비스 (M8).
 */
@Service
public class AiWeeklyReportAccessService {

    private final GroupMemberRepository members;

    public AiWeeklyReportAccessService(GroupMemberRepository members) {
        this.members = members;
    }

    public GroupMember requireActiveMember(Long groupId, Long userId) {
        return members.findByGroupIdAndUserIdAndStatus(groupId, userId, GroupMember.Status.ACTIVE)
                .orElseThrow(() -> new ApplicationException("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND, "그룹을 찾을 수 없거나 접근 권한이 없습니다."));
    }

    public GroupMember requirePaidTeamLeader(Long groupId, Long userId) {
        GroupMember member = requireActiveMember(groupId, userId);
        Group group = member.getGroup();

        if (group.getType() != Group.Type.TEAM || group.getMembershipPlan() != Group.MembershipPlan.PAID) {
            throw new ApplicationException("AI_REPORT_PAID_REQUIRED", HttpStatus.FORBIDDEN, "유료 팀 플랜 그룹에서만 AI 주간 리포트를 사용할 수 있습니다.");
        }

        if (member.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("GROUP_LEADER_REQUIRED", HttpStatus.FORBIDDEN, "AI 주간 리포트 생성은 팀장 권한이 필요합니다.");
        }

        return member;
    }
}
