package com.teamproject.report;

import com.teamproject.report.infrastructure.OpenHtmlReportPdfRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 주간 리포트의 서버 PDF 경로는 없앴다. 렌더러(openhtmltopdf)가 CSS 2.1까지만 이해해서
 * 그 문서의 grid·flex 레이아웃을 그리지 못했고, 대신 내려가던 별도 문서가 리포트 정본과 달라
 * 오히려 혼란을 만들었다. AI 리포트는 HTML로 내려주고 PDF 저장은 브라우저 인쇄가 한다.
 * 표 기반이라 렌더러 한계에 걸리지 않는 기본 리포트만 서버에서 PDF로 만든다.
 */
class OpenHtmlReportPdfRendererTest {

    private final OpenHtmlReportPdfRenderer renderer = new OpenHtmlReportPdfRenderer();

    @Test
    @DisplayName("기본 리포트를 %PDF 매직 바이트로 시작하는 문서로 렌더링한다")
    void rendersTheBasicReportAsAPdf() {
        var document = new com.teamproject.report.application.ReportPdfRenderer.BasicReportDocument(
                "기본 리포트", "ko", "ALL", "WEEKLY",
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27),
                5, 2, 0, List.of()
        );

        byte[] pdfBytes = renderer.renderBasic(document);

        assertThat(pdfBytes).isNotEmpty();
        assertThat(pdfBytes[0]).isEqualTo((byte) 0x25); // '%'
        assertThat(pdfBytes[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(pdfBytes[2]).isEqualTo((byte) 0x44); // 'D'
        assertThat(pdfBytes[3]).isEqualTo((byte) 0x46); // 'F'
    }
}
