package com.teamproject.report.application;

import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.AiWeeklyReportView;
import java.time.LocalDate;
import java.util.List;

public interface ReportPdfRenderer {
    byte[] renderBasic(BasicReportDocument document);
    byte[] renderWeeklyAiV72(AiWeeklyReportView view);

    record BasicReportDocument(
            String title,
            String language,
            String scope,
            String periodType,
            LocalDate from,
            LocalDate to,
            long completed,
            long active,
            long delayed,
            List<BasicReportTask> tasks) {}

    record BasicReportTask(
            String title,
            String status,
            String priority,
            String assignee,
            String dueAt) {}
}
