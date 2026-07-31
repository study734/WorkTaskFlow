package com.teamproject.report;

import com.teamproject.report.infrastructure.OpenHtmlReportPdfRenderer;
import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlReportPdfRendererV72Test {

    private final OpenHtmlReportPdfRenderer renderer = new OpenHtmlReportPdfRenderer();

    @Test
    @DisplayName("v7-2 AI 주간 리포트 뷰를 %PDF 마법사 바이트로 정상 렌더링한다")
    void renderWeeklyAiV72ProducesValidPdf() {
        AiWeeklyReportView view = new AiWeeklyReportView(
                1L,
                10L,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                1,
                "FINALIZED",
                "OPENAI",
                LocalDateTime.now(),
                "/api/v1/groups/10/reports/ai-weekly/1/pdf",
                new ExecutiveJudgmentView("헤드라인 <script>", "해석 내용", List.of(), List.of(), List.of(), "HIGH", List.of()),
                new AchievementView("NONE", "성과 헤드라인", "성과 요약", List.of(), List.of()),
                List.of(new IssueView("P1", "CAND-1", "HIGH", "위험 제목", "실제 업무 제목 <tag>", "영향", "HIGH", List.of(), List.of(), List.of(), List.of(), "통합 판단", "필요 결정", null)),
                List.of(),
                new SnapshotMetricsView(10, 80, 100, 1, 12),
                new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null),
                new SnapshotWorkflowView(1, 1, 2, 3, 0, 3),
                List.of(new SnapshotTaskView("TASK-1", "실제 업무 <script>", "안전 라벨", "IN_PROGRESS", "HIGH", "MEMBER-1", "홍길동", "2026-07-20", "2026-07-27", null, "UPCOMING", null, null, null, List.of())),
                List.of(new SnapshotMemberView("MEMBER-1", "홍길동", "LEADER", 5, 2, 3, 0, 100, 1)),
                List.of(new CalendarConstraintView("EVENT-1", "주간 회의", "MEETING", "주간 회의", "2026-07-21", "2026-07-21", List.of()))
        );

        byte[] pdfBytes = renderer.renderWeeklyAiV72(view);

        assertThat(pdfBytes).isNotEmpty();
        // Check %PDF magic bytes (0x25, 0x50, 0x44, 0x46)
        assertThat(pdfBytes[0]).isEqualTo((byte) 0x25); // '%'
        assertThat(pdfBytes[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(pdfBytes[2]).isEqualTo((byte) 0x44); // 'D'
        assertThat(pdfBytes[3]).isEqualTo((byte) 0x46); // 'F'
    }

    @Test
    @DisplayName("renderBasic 기본 리포트 렌더링이 변경 없이 정상 작동한다")
    void renderBasicWorksUnchanged() {
        var doc = new com.teamproject.report.application.ReportPdfRenderer.BasicReportDocument(
                "기본 리포트", "ko", "ALL", "WEEKLY",
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27),
                5, 2, 0, List.of()
        );

        byte[] pdfBytes = renderer.renderBasic(doc);

        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes[0]).isEqualTo((byte) 0x25); // '%'
    }
}
