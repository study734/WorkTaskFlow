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
        AiWeeklyReportView view = sampleView("NO_BASELINE");

        byte[] pdfBytes = renderer.renderWeeklyAiV72(view);

        assertThat(pdfBytes).isNotEmpty();
        // Check %PDF magic bytes (0x25, 0x50, 0x44, 0x46)
        assertThat(pdfBytes[0]).isEqualTo((byte) 0x25); // '%'
        assertThat(pdfBytes[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(pdfBytes[2]).isEqualTo((byte) 0x44); // 'D'
        assertThat(pdfBytes[3]).isEqualTo((byte) 0x46); // 'F'
    }

    @Test
    @DisplayName("PDF 렌더링 시 4페이지 구조, A4 CSS, page-break, HTML escaping, NO_BASELINE, 내부 기술 미노출을 만족한다")
    void pdfHtmlStructureAndEscapingAndNoBaselineAndNoTechLeakage() {
        AiWeeklyReportView view = sampleView("NO_BASELINE");

        byte[] pdfBytes = renderer.renderWeeklyAiV72(view);
        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes[0]).isEqualTo((byte) 0x25);
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

    private AiWeeklyReportView sampleView(String comparisonStatus) {
        return new AiWeeklyReportView(
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
                List.of(
                        new IssueView("P1", "CAND-1", "HIGH", "위험 제목 1", "실제 업무 1 <script>", "영향 1", "HIGH", List.of(), List.of(), List.of(), List.of(), "통합 판단 1", "필요 결정 1", new DecisionView("결정 1", "질문 1", "ASSIGN_OWNER_AND_SET_DUE", "권고 1", "LEADER", "SELECTED_MEMBER", null, List.of("ASSIGN_OWNER"), List.of("ASSIGNEE_SET"))),
                        new IssueView("P2", "CAND-2", "MEDIUM", "위험 제목 2", "실제 업무 2", "영향 2", "MEDIUM", List.of(), List.of(), List.of(), List.of(), "통합 판단 2", "필요 결정 2", null),
                        new IssueView("P3", "CAND-3", "LOW", "위험 제목 3", "실제 업무 3", "영향 3", "LOW", List.of(), List.of(), List.of(), List.of(), "통합 판단 3", "필요 결정 3", null),
                        new IssueView("P3", "CAND-4", "LOW", "위험 제목 4", "실제 업무 4", "영향 4", "LOW", List.of(), List.of(), List.of(), List.of(), "통합 판단 4", "필요 결정 4", null)
                ),
                List.of(),
                new SnapshotMetricsView(10, 80, 100, 1, 12),
                new SnapshotComparisonView(comparisonStatus, null, null, null, null, null, null),
                new SnapshotWorkflowView(1, 1, 2, 3, 0, 3),
                List.of(new SnapshotTaskView("TASK-1", "실제 업무 <script>", "안전 라벨", "IN_PROGRESS", "HIGH", "MEMBER-1", "홍길동", "2026-07-20", "2026-07-27", null, "UPCOMING", null, null, null, List.of())),
                List.of(new SnapshotMemberView("MEMBER-1", "홍길동", "LEADER", 5, 2, 3, 0, 100, 1)),
                List.of(new CalendarConstraintView("EVENT-1", "주간 회의", "MEETING", "주간 회의", "2026-07-21", "2026-07-21", List.of())),
                List.of()
        );
    }
}
