package com.teamproject.report.application;

import com.teamproject.common.exception.ApplicationException;
import com.teamproject.dashboard.application.DashboardService;
import com.teamproject.dashboard.application.dto.DashboardDtos.DashboardTaskResponse;
import com.teamproject.report.application.ReportPdfRenderer.BasicReportDocument;
import com.teamproject.report.application.ReportPdfRenderer.BasicReportTask;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 권한·한도를 먼저 검증하고 렌더링 성공 뒤에만 사용량을 기록한다. 실패한 다운로드를 차감하지
 * 않으며 PDF 렌더링 동안 그룹 잠금을 유지하지 않는다.
 */
@Service
public class BasicReportPdfService {
    private final BasicReportAccessService access;
    private final DashboardService dashboards;
    private final ReportPdfRenderer renderer;

    public BasicReportPdfService(BasicReportAccessService access, DashboardService dashboards,
            ReportPdfRenderer renderer) {
        this.access = access;
        this.dashboards = dashboards;
        this.renderer = renderer;
    }

    public GeneratedPdf generate(Long userId, Long groupId, String scope, String periodType,
            LocalDate from, LocalDate to, String language) {
        String normalizedLanguage = language(language);
        access.validate(userId, groupId, scope, periodType);

        String normalizedScope = scope.trim().toUpperCase();
        String groupName;
        List<DashboardTaskResponse> tasks;
        if ("MY".equals(normalizedScope)) {
            var report = dashboards.memberReport(userId, groupId, from, to);
            groupName = report.groupName();
            tasks = report.tasks();
        } else {
            var report = dashboards.group(userId, groupId, from, to);
            groupName = report.groupName();
            tasks = report.periodTasks();
        }

        List<BasicReportTask> rows = tasks.stream().map(task -> new BasicReportTask(
                task.title(),
                task.status(),
                task.priority(),
                task.assigneeNickname(),
                task.dueAt() == null ? null : task.dueAt().toString()))
                .toList();
        long completed = tasks.stream().filter(task -> "COMPLETED".equals(task.status())).count();
        long active = tasks.stream()
                .filter(task -> "IN_PROGRESS".equals(task.status())
                        || "ON_HOLD".equals(task.status()))
                .count();
        long delayed = tasks.stream().filter(DashboardTaskResponse::delayed).count();
        String title = groupName + ("MY".equals(normalizedScope)
                ? text(normalizedLanguage, " 내 업무 리포트", " My task report")
                : text(normalizedLanguage, " 그룹 리포트", " group report"));
        BasicReportDocument document = new BasicReportDocument(
                title, normalizedLanguage, normalizedScope, periodType.trim().toUpperCase(),
                from, to, completed, active, delayed, rows);
        byte[] pdf = renderer.renderBasic(document);
        access.record(userId, groupId, scope, periodType);
        return new GeneratedPdf(
                "basic-report-" + groupId + "-" + from + "-" + to + ".pdf",
                pdf);
    }

    private String language(String value) {
        if (!"ko".equals(value) && !"en".equals(value)) {
            throw new ApplicationException("REPORT_LANGUAGE_INVALID", HttpStatus.BAD_REQUEST,
                    "지원하지 않는 리포트 언어입니다.");
        }
        return value;
    }

    private String text(String language, String ko, String en) {
        return "ko".equals(language) ? ko : en;
    }
}
