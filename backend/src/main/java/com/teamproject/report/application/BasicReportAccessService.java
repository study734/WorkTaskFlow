package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.dto.GroupDtos.ReportAccessResponse;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupReportDownload;
import com.teamproject.group.domain.GroupReportDownloadRepository;
import com.teamproject.group.domain.GroupRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.TemporalAdjusters;

@Service
public class BasicReportAccessService {
    private static final int FREE_GROUP_WEEKLY_LIMIT = 2;
    private final GroupAuthorization authorization;
    private final GroupReportDownloadRepository downloads;
    private final GroupRepository groups;

    public BasicReportAccessService(GroupAuthorization authorization, GroupReportDownloadRepository downloads,
            GroupRepository groups) {
        this.authorization = authorization;
        this.downloads = downloads;
        this.groups = groups;
    }

@Transactional(readOnly = true)
    public ReportAccessResponse validate(Long userId, Long groupId, String rawScope,
            String rawPeriodType) {
        return access(userId, groupId, rawScope, rawPeriodType, false);
    }

    @Transactional
    public ReportAccessResponse record(Long userId, Long groupId, String rawScope,
            String rawPeriodType) {
        return access(userId, groupId, rawScope, rawPeriodType, true);
    }

    private ReportAccessResponse access(Long userId, Long groupId, String rawScope,
            String rawPeriodType, boolean record) {
        Group group = (record ? groups.findByIdForUpdate(groupId) : groups.findById(groupId))
                .orElseThrow(() -> new ApplicationException("GROUP_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "그룹을 찾을 수 없습니다."));
        GroupMember member = authorization.requireActiveMember(groupId, userId);
        if (group.getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST,
                    "팀 그룹 리포트만 생성할 수 있습니다.");
        }
        GroupReportDownload.Scope scope = scope(rawScope);
        GroupReportDownload.PeriodType periodType = periodType(rawPeriodType);
        if (scope == GroupReportDownload.Scope.GROUP
                && member.getRole() != GroupMember.Role.LEADER) {
            throw new ApplicationException("GROUP_LEADER_REQUIRED", HttpStatus.FORBIDDEN,
                    "그룹 전체 리포트는 팀장만 생성할 수 있습니다.");
        }

        Integer remaining = null;
        if (group.getMembershipPlan() == Group.MembershipPlan.FREE
                && scope == GroupReportDownload.Scope.GROUP) {
            ZoneId zone = ZoneId.of(group.getTimezone());
            LocalDate today = LocalDate.now(zone);
            LocalDate weekStart = today.with(
                    TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            LocalDateTime from = weekStart.atStartOfDay();
            LocalDateTime to = weekStart.plusDays(7).atStartOfDay();
            long used = downloads
                    .countByGroupIdAndScopeAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                            groupId, scope, from, to);
            if (used >= FREE_GROUP_WEEKLY_LIMIT) {
                throw new ApplicationException("FREE_REPORT_WEEKLY_LIMIT",
                        HttpStatus.TOO_MANY_REQUESTS,
                        "무료 그룹 리포트는 주 2회까지 생성할 수 있습니다.");
            }
            remaining = Math.toIntExact(FREE_GROUP_WEEKLY_LIMIT - used - (record ? 1 : 0));
        }
        if (record) {
            downloads.save(new GroupReportDownload(group, member, scope, periodType));
        }
        return new ReportAccessResponse(true, group.getMembershipPlan().name(), scope.name(),
                periodType.name(), remaining);
    }

    private GroupReportDownload.Scope scope(String value) {
        try {
            return GroupReportDownload.Scope.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new ApplicationException("REPORT_SCOPE_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 리포트 범위를 선택해 주세요.");
        }
    }

    private GroupReportDownload.PeriodType periodType(String value) {
        try {
            return GroupReportDownload.PeriodType.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new ApplicationException("REPORT_PERIOD_INVALID", HttpStatus.BAD_REQUEST,
                    "올바른 리포트 기간을 선택해 주세요.");
        }
    }
}
