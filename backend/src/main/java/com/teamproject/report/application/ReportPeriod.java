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

    public ReportPeriod previous() {
        LocalDate previousStart = start.minusWeeks(1);
        LocalDate previousEnd = end.minusWeeks(1);
        return new ReportPeriod(previousStart, previousEnd, zone,
                previousStart.atStartOfDay(zone).toInstant(),
                previousEnd.plusDays(1).atStartOfDay(zone).toInstant());
    }

    public static ReportPeriod completedWeek(LocalDate weekStart, String timezone, Clock clock) {
        if (weekStart == null || weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ApplicationException("AI_REPORT_WEEK_INVALID", HttpStatus.BAD_REQUEST,
                    "주간 시작일은 월요일이어야 합니다.");
        }
        ZoneId zone = ZoneId.of(timezone);
        LocalDate end = weekStart.plusDays(6);
        if (!end.isBefore(LocalDate.now(clock.withZone(zone)))) {
            throw new ApplicationException("AI_REPORT_WEEK_INCOMPLETE", HttpStatus.BAD_REQUEST,
                    "완료된 주간만 AI 리포트를 생성할 수 있습니다.");
        }
        return new ReportPeriod(weekStart, end, zone,
                weekStart.atStartOfDay(zone).toInstant(),
                end.plusDays(1).atStartOfDay(zone).toInstant());
    }
}
