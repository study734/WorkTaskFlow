package com.teamproject.report.application;

import com.teamproject.authentication.infrastructure.mail.MailService;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.common.scheduling.DatabaseJobLock;
import com.teamproject.group.application.GroupAuthorization;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.report.application.ReportDocumentService.Language;
import com.teamproject.report.application.dto.ReportDtos.*;
import com.teamproject.report.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
public class ReportScheduleService {
    private static final int WEEKLY_MINIMUM_DAYS = 7;
    private static final int MONTHLY_MINIMUM_DAYS = 14;
    private final GroupAuthorization authorization;
    private final ReportScheduleRepository schedules;
    private final ReportDeliveryRepository deliveries;
    private final ReportDocumentService documents;
    private final MailService mail;
    private final DatabaseJobLock jobLock;
    public ReportScheduleService(GroupAuthorization authorization, ReportScheduleRepository schedules,
            ReportDeliveryRepository deliveries, ReportDocumentService documents, MailService mail,
            DatabaseJobLock jobLock) {
        this.authorization = authorization; this.schedules = schedules; this.deliveries = deliveries;
        this.documents = documents; this.mail = mail; this.jobLock = jobLock;
    }
    @Transactional
    public ReportScheduleResponse get(Long userId, Long groupId) {
        GroupMember leader = requireTeamLeader(userId, groupId);
        ReportSchedule schedule = schedules.findByGroupId(groupId)
                .orElseGet(() -> schedules.save(new ReportSchedule(leader.getGroup(), leader.getUser())));
        return response(schedule);
    }
    @Transactional
    public ReportScheduleResponse update(Long userId, Long groupId, UpdateReportScheduleRequest request) {
        GroupMember leader = requireTeamLeader(userId, groupId);
        if ((request.weeklyEnabled() || request.monthlyEnabled())
                && leader.getGroup().getMembershipPlan() != Group.MembershipPlan.PAID) {
            throw new ApplicationException("PAID_SUBSCRIPTION_REQUIRED", HttpStatus.PAYMENT_REQUIRED,
                    "자동 리포트 메일은 무료 체험 또는 유료 구독 그룹에서 사용할 수 있습니다.");
        }
        boolean weeklyEligible = eligible(leader.getGroup(), WEEKLY_MINIMUM_DAYS);
        boolean monthlyEligible = eligible(leader.getGroup(), MONTHLY_MINIMUM_DAYS);
        if (request.weeklyEnabled() && !weeklyEligible) throw notEligible(WEEKLY_MINIMUM_DAYS);
        if (request.monthlyEnabled() && !monthlyEligible) throw notEligible(MONTHLY_MINIMUM_DAYS);
        DayOfWeek weeklyDay = null;
        ReportSchedule.Language language;
        try {
            if (request.weeklyEnabled()) weeklyDay = DayOfWeek.valueOf(request.weeklyDay().trim().toUpperCase());
            language = ReportSchedule.Language.valueOf(request.language().trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new ApplicationException("REPORT_SCHEDULE_INVALID", HttpStatus.BAD_REQUEST, "리포트 일정과 언어를 확인해 주세요.");
        }
        if (request.monthlyEnabled() && request.monthlyDay() == null) {
            throw new ApplicationException("REPORT_MONTHLY_DAY_REQUIRED", HttpStatus.BAD_REQUEST, "월간 리포트 발송일을 선택해 주세요.");
        }
        ReportSchedule schedule = schedules.findByGroupId(groupId)
                .orElseGet(() -> schedules.save(new ReportSchedule(leader.getGroup(), leader.getUser())));
        schedule.update(request.recipientEmail().trim(), request.weeklyEnabled(), weeklyDay,
                request.monthlyEnabled(), request.monthlyDay(), language);
        return response(schedule);
    }
    @Scheduled(cron = "${app.report.mail-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    @Transactional
    public void deliverScheduledReports() {
        if (!jobLock.acquire("report-mail", Duration.ofHours(6))) return;
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        schedules.findAllByActiveTrue().forEach(schedule -> {
            if (schedule.isWeeklyEnabled() && schedule.getWeeklyDay() == today.getDayOfWeek()) {
                deliver(schedule, ReportDelivery.PeriodType.WEEKLY, today.minusDays(7), today);
            }
            if (schedule.isMonthlyEnabled() && schedule.getMonthlyDay() == today.getDayOfMonth()) {
                LocalDate from = today.minusMonths(1).withDayOfMonth(1);
                deliver(schedule, ReportDelivery.PeriodType.MONTHLY, from, today.withDayOfMonth(1));
            }
        });
    }
    @Scheduled(cron = "${app.report.retry-cron:0 20 * * * *}", zone = "Asia/Seoul")
    @Transactional
    public void retryFailedReports() {
        if (!jobLock.acquire("report-mail-retry", Duration.ofMinutes(55))) return;
        deliveries.findAllByStatusAndNextRetryAtLessThanEqual(
                ReportDelivery.Status.FAILED, LocalDateTime.now()).forEach(this::retry);
    }
    private void deliver(ReportSchedule schedule, ReportDelivery.PeriodType type, LocalDate from, LocalDate to) {
        if (schedule.getGroup().getMembershipPlan() != Group.MembershipPlan.PAID) return;
        List<ReportDelivery.Language> languages = schedule.getLanguage() == ReportSchedule.Language.BOTH
                ? List.of(ReportDelivery.Language.KO, ReportDelivery.Language.EN)
                : List.of(schedule.getLanguage() == ReportSchedule.Language.KO
                        ? ReportDelivery.Language.KO : ReportDelivery.Language.EN);
        for (ReportDelivery.Language language : languages) {
            String key = "REPORT:" + schedule.getId() + ":" + type + ":" + from + ":" + language;
            if (deliveries.existsByEventKey(key)) continue;
            ReportDelivery delivery = deliveries.save(new ReportDelivery(schedule, type, from, to, language, key));
            try {
                var document = documents.generate(schedule.getRecipient().getId(), schedule.getGroup().getId(),
                        from, to, language == ReportDelivery.Language.KO ? Language.KO : Language.EN);
                if (mail.sendHtmlBestEffort(schedule.getRecipientEmail(), document.subject(), document.html())) delivery.sent();
                else delivery.failed("MAIL_DELIVERY_FAILED");
            } catch (RuntimeException exception) {
                delivery.failed("REPORT_GENERATION_FAILED");
            }
        }
    }
    private void retry(ReportDelivery delivery) {
        ReportSchedule schedule = delivery.getSchedule();
        if (!schedule.isActive() || schedule.getGroup().getMembershipPlan() != Group.MembershipPlan.PAID) {
            delivery.abandon("SCHEDULE_INACTIVE");
            return;
        }
        try {
            var document = documents.generate(schedule.getRecipient().getId(), schedule.getGroup().getId(),
                    delivery.getPeriodStart(), delivery.getPeriodEnd(),
                    delivery.getLanguage() == ReportDelivery.Language.KO ? Language.KO : Language.EN);
            if (mail.sendHtmlBestEffort(schedule.getRecipientEmail(), document.subject(), document.html())) delivery.sent();
            else delivery.failed("MAIL_DELIVERY_FAILED");
        } catch (RuntimeException exception) {
            delivery.failed("REPORT_GENERATION_FAILED");
        }
    }
    private GroupMember requireTeamLeader(Long userId, Long groupId) {
        GroupMember member = authorization.requireLeader(groupId, userId);
        if (member.getGroup().getType() != Group.Type.TEAM) {
            throw new ApplicationException("PERSONAL_GROUP_RESTRICTED", HttpStatus.BAD_REQUEST, "팀 그룹에서만 리포트 일정을 설정할 수 있습니다.");
        }
        return member;
    }
    private boolean eligible(Group group, int days) {
        return !group.getCreatedAt().toLocalDate().isAfter(LocalDate.now().minusDays(days));
    }
    private ApplicationException notEligible(int days) {
        return new ApplicationException("REPORT_USAGE_PERIOD_REQUIRED", HttpStatus.CONFLICT,
                "그룹을 " + days + "일 이상 사용한 뒤 선택할 수 있습니다.");
    }
    private ReportScheduleResponse response(ReportSchedule value) {
        return new ReportScheduleResponse(value.getId(), value.getGroup().getId(), value.getRecipientEmail(),
                value.isWeeklyEnabled(), value.getWeeklyDay() == null ? null : value.getWeeklyDay().name(),
                value.isMonthlyEnabled(), value.getMonthlyDay(), value.getLanguage().name(), value.isActive(),
                eligible(value.getGroup(), WEEKLY_MINIMUM_DAYS), eligible(value.getGroup(), MONTHLY_MINIMUM_DAYS),
                WEEKLY_MINIMUM_DAYS, MONTHLY_MINIMUM_DAYS);
    }
}
