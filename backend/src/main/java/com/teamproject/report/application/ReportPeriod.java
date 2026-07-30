package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

public record ReportPeriod(
        LocalDate start,
        LocalDate end,
        ZoneId zone,
        Instant fromInclusive,
        Instant toExclusive) {

    /**
     * 주차는 달의 1일부터 7일씩 끊는다(요일 무시). 마지막 주차는 월말에서 잘려 1~3일이 될 수 있다.
     * 대시보드 기간 필터와 같은 기준이며, 같은 "N주차"가 두 화면에서 같은 기간을 가리키게 한다.
     */
    private static final int[] WEEK_START_DAYS = { 1, 8, 15, 22, 29 };

    /** 이전 주차. 1일 시작이면 지난달의 마지막 주차로 넘어간다. */
    public ReportPeriod previous() {
        LocalDate previousStart = start.getDayOfMonth() > 1
                ? start.minusDays(7)
                : lastWeekStartOf(start.minusMonths(1));
        return of(previousStart, zone);
    }

    /** 마지막 주차가 잘린 경우 이전 주차와 길이가 달라 주간 비교가 성립하지 않는다. */
    public boolean sameLengthAs(ReportPeriod other) {
        return start.until(end).getDays() == other.start().until(other.end()).getDays();
    }

    /**
     * 달 기준(1·8·15·22·29일)과 ISO 월요일 시작을 모두 받는다. 프론트가 아직 월요일을 보내는
     * 화면이 있어 한쪽만 받으면 생성이 전부 거부된다. 월요일 주는 항상 7일이므로 아래 계산이 그대로
     * 성립하고, 프론트가 달 기준으로 옮겨가면 이 분기만 좁히면 된다.
     */
    public static ReportPeriod completedWeek(LocalDate weekStart, String timezone, Clock clock) {
        if (weekStart == null
                || !(isWeekStart(weekStart) || weekStart.getDayOfWeek() == DayOfWeek.MONDAY)) {
            throw new ApplicationException("AI_REPORT_WEEK_INVALID", HttpStatus.BAD_REQUEST,
                    "주간 시작일은 매월 1·8·15·22·29일 또는 월요일이어야 합니다.");
        }
        ZoneId zone = ZoneId.of(timezone);
        ReportPeriod period = of(weekStart, zone);
        if (!period.end().isBefore(LocalDate.now(clock.withZone(zone)))) {
            throw new ApplicationException("AI_REPORT_WEEK_INCOMPLETE", HttpStatus.BAD_REQUEST,
                    "완료된 주간만 AI 리포트를 생성할 수 있습니다.");
        }
        return period;
    }

    /** 오늘이 속한 주차의 직전 주차 시작일. 직전 주차는 항상 완료돼 있다. */
    public static LocalDate lastCompletedWeekStart(String timezone, Clock clock) {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(timezone)));
        int startDay = 1 + 7 * ((today.getDayOfMonth() - 1) / 7);
        return startDay > 1
                ? today.withDayOfMonth(startDay - 7)
                : lastWeekStartOf(today.minusMonths(1));
    }

    private static ReportPeriod of(LocalDate weekStart, ZoneId zone) {
        // 달 기준 주차만 월말에서 자른다. 월요일 시작 주를 자르면 7일이 5일로 줄어 비교가 깨진다.
        LocalDate end = isWeekStart(weekStart)
                ? weekStart.withDayOfMonth(
                        Math.min(weekStart.getDayOfMonth() + 6, weekStart.lengthOfMonth()))
                : weekStart.plusDays(6);
        return new ReportPeriod(weekStart, end, zone,
                weekStart.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private static boolean isWeekStart(LocalDate value) {
        for (int day : WEEK_START_DAYS) {
            if (value.getDayOfMonth() == day) return day <= value.lengthOfMonth();
        }
        return false;
    }

    private static LocalDate lastWeekStartOf(LocalDate month) {
        return month.withDayOfMonth(1 + 7 * ((month.lengthOfMonth() - 1) / 7));
    }
}
