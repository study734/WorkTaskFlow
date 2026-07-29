package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.ReportContracts.FindWeeklyReportById;
import com.teamproject.report.application.ReportContracts.WeeklyReportView;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyReportPdfService {
    private final WeeklyReportModule reports;
    private final ReportPdfRenderer renderer;

    public WeeklyReportPdfService(WeeklyReportModule reports, ReportPdfRenderer renderer) {
        this.reports = reports;
        this.renderer = renderer;
    }

    @Transactional(readOnly = true)
    public GeneratedPdf generate(Long userId, Long groupId, Long reportId) {
        WeeklyReportView report = reports.findWeeklyAiReportById(
                new FindWeeklyReportById(userId, groupId, reportId));
        if (!"FINALIZED".equals(report.publicationStatus())) {
            throw new ApplicationException("AI_REPORT_NOT_FINALIZED", HttpStatus.CONFLICT,
                    "확정된 AI 리포트만 PDF로 다운로드할 수 있습니다.");
        }
        return new GeneratedPdf(
                "ai-weekly-report-" + report.periodStart() + "-r" + report.revision() + ".pdf",
                renderer.renderWeeklyAi(report));
    }
}
