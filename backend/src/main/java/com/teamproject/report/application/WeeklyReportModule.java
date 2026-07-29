package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.report.application.ReportContracts.FindWeeklyReport;
import com.teamproject.report.application.ReportContracts.FindWeeklyReportById;
import com.teamproject.report.application.ReportContracts.GenerateWeeklyReport;
import com.teamproject.report.application.ReportContracts.EditWeeklyReportDraft;
import com.teamproject.report.application.ReportContracts.RegenerateWeeklyReport;
import com.teamproject.report.application.ReportContracts.FinalizeWeeklyReport;
import com.teamproject.report.application.ReportContracts.RevisionSummary;
import com.teamproject.report.application.ReportContracts.WeeklyReportView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;

@Service
public class WeeklyReportModule {
    private final GroupAuthorization authorization;
    private final WeeklyReportGenerationModule generation;
    private final Clock clock;

    public WeeklyReportModule(GroupAuthorization authorization,
            WeeklyReportGenerationModule generation, Clock clock) {
        this.authorization = authorization;
        this.generation = generation;
        this.clock = clock;
    }

    public WeeklyReportView generateWeeklyAiReport(GenerateWeeklyReport command) {
        GroupMember leader = requirePaidTeamLeader(command.userId(), command.groupId());
        ReportPeriod period = ReportPeriod.completedWeek(
                command.weekStart(), leader.getGroup().getTimezone(), clock);
        return generation.generate(leader, period, language(command.language()));
    }

    public WeeklyReportView findWeeklyAiReport(FindWeeklyReport query) {
        GroupMember leader = requirePaidTeamLeader(query.userId(), query.groupId());
        ReportPeriod period = ReportPeriod.completedWeek(
                query.weekStart(), leader.getGroup().getTimezone(), clock);
        return generation.find(query.groupId(), period, language(query.language()));
    }

    public WeeklyReportView findWeeklyAiReportById(FindWeeklyReportById query) {
        GroupMember member = requirePaidTeamMember(query.userId(), query.groupId());
        WeeklyReportView report = generation.findById(query.groupId(), query.reportId());
        if (member.getRole() != GroupMember.Role.LEADER
                && !"FINALIZED".equals(report.publicationStatus())) {
            throw new ApplicationException("AI_REPORT_NOT_FINALIZED", HttpStatus.FORBIDDEN,
                    "팀원은 확정된 AI 리포트만 조회할 수 있습니다.");
        }
        return report;
    }

    public List<RevisionSummary> listWeeklyAiReportRevisions(FindWeeklyReport query) {
        GroupMember leader = requirePaidTeamLeader(query.userId(), query.groupId());
        ReportPeriod period = ReportPeriod.completedWeek(
                query.weekStart(), leader.getGroup().getTimezone(), clock);
        return generation.revisions(query.groupId(), period, language(query.language()));
    }

    public WeeklyReportView editWeeklyAiReportDraft(EditWeeklyReportDraft command) {
        GroupMember leader = requirePaidTeamLeader(command.userId(), command.groupId());
        return generation.edit(leader, command);
    }

    public WeeklyReportView regenerateWeeklyAiReport(RegenerateWeeklyReport command) {
        GroupMember leader = requirePaidTeamLeader(command.userId(), command.groupId());
        return generation.regenerate(leader, command.reportId(), command.expectedEditorVersion());
    }

    public WeeklyReportView finalizeWeeklyAiReport(FinalizeWeeklyReport command) {
        GroupMember leader = requirePaidTeamLeader(command.userId(), command.groupId());
        return generation.finalizeReport(
                leader, command.reportId(), command.expectedEditorVersion());
    }

    private GroupMember requirePaidTeamLeader(Long userId, Long groupId) {
        GroupMember leader = requirePaidTeamMember(userId, groupId);
        if (leader.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("GROUP_LEADER_REQUIRED", HttpStatus.FORBIDDEN,
                    "AI 리포트 생성과 편집은 팀장만 수행할 수 있습니다.");
        }
        return leader;
    }

    private GroupMember requirePaidTeamMember(Long userId, Long groupId) {
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        Group group = member.getGroup();
        if (group.getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST,
                    "팀 그룹 리포트만 생성할 수 있습니다.");
        }
        if (group.getMembershipPlan() != Group.MembershipPlan.PAID) {
            throw new ApplicationException("AI_REPORT_PAID_REQUIRED", HttpStatus.FORBIDDEN,
                    "유료 그룹에서만 AI 주간 리포트를 사용할 수 있습니다.");
        }
        return member;
    }

    private String language(String value) {
        if (!"ko".equals(value) && !"en".equals(value)) {
            throw new ApplicationException("AI_REPORT_LANGUAGE_INVALID", HttpStatus.BAD_REQUEST,
                    "지원하지 않는 리포트 언어입니다.");
        }
        return value;
    }
}
