package com.teamproject.report;

import com.teamproject.dashboard.application.DashboardService;
import com.teamproject.dashboard.application.dto.DashboardDtos.DashboardTaskResponse;
import com.teamproject.dashboard.application.dto.DashboardDtos.GroupDashboardResponse;
import com.teamproject.dashboard.application.dto.DashboardDtos.StatusCounts;
import com.teamproject.report.application.ReportDocumentService;
import com.teamproject.report.application.ReportDocumentService.Language;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportDocumentServiceTest {

    @Test
    void rendersBrandedCoreReportWithEscapedTaskData() {
        DashboardService dashboards = mock(DashboardService.class);
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 8, 1);
        DashboardTaskResponse task = new DashboardTaskResponse(
                1L, 7L, "퇴사 팀", "발표 자료 <최종>", "IN_PROGRESS", "HIGH",
                LocalDateTime.of(2026, 7, 31, 18, 0), false,
                LocalDateTime.of(2026, 7, 2, 9, 0), null, null, 3L, "홍길동");
        GroupDashboardResponse dashboard = new GroupDashboardResponse(
                Instant.parse("2026-07-30T00:00:00Z"), 7L, "퇴사 팀", "Asia/Seoul", "MEMBERS",
                from, to, 4, new StatusCounts(0, 1, 2, 0, 1, 0, 0, 0), 50,
                4, 1, 25, 1, 1, 100, 12L,
                List.of(), List.of(), List.of(task), List.of());
        when(dashboards.group(11L, 7L, from, to)).thenReturn(dashboard);

        var document = new ReportDocumentService(dashboards).generate(11L, 7L, from, to, Language.KO);
        String html = document.html();

        assertThat(html)
                .contains("TOESA · 퇴사")
                .contains("BASIC WORK REPORT")
                .contains("이번 기간 한눈에 보기")
                .contains("status-in-progress")
                .contains("발표 자료 &lt;최종&gt;")
                .doesNotContain("발표 자료 <최종>");
        assertThat(document.filename()).isEqualTo("toesa-7-2026-07-01-2026-07-31-ko.html");
    }
}
