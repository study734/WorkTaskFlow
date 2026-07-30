package com.teamproject.report.application.dto;

import jakarta.validation.constraints.*;

public final class ReportDtos {
    private ReportDtos() {}
    public record UpdateReportScheduleRequest(
            @Email @NotBlank String recipientEmail,
            boolean weeklyEnabled, String weeklyDay,
            boolean monthlyEnabled, @Min(1) @Max(28) Integer monthlyDay,
            @NotBlank String language) {}
    public record ReportScheduleResponse(Long id, Long groupId, String recipientEmail,
            boolean weeklyEnabled, String weeklyDay, boolean monthlyEnabled, Integer monthlyDay,
            String language, boolean active, boolean weeklyEligible, boolean monthlyEligible,
            int weeklyMinimumDays, int monthlyMinimumDays) {}
}
