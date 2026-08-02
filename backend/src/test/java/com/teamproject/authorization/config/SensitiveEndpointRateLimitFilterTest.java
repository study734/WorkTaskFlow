package com.teamproject.authorization.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * AI 리포트 생성은 한 번이 유료 provider 호출인데 이 필터의 대상이 아니었다. 서버 상한은
 * (그룹, 기간, 언어)당 3회뿐이라 과거 기간을 순회하면 호출 수에 제한이 없다.
 */
class SensitiveEndpointRateLimitFilterTest {

    private final SensitiveEndpointRateLimitFilter filter =
            new SensitiveEndpointRateLimitFilter(true, 2, 60);

    @Test
    @DisplayName("AI 리포트 생성 POST는 상한을 넘기면 429로 막힌다")
    void limitsAiReportGeneration() throws Exception {
        String path = "/api/v1/groups/20/reports/ai-weekly";

        assertThat(status(path, "POST")).isEqualTo(200);
        assertThat(status(path, "POST")).isEqualTo(200);
        assertThat(status(path, "POST")).isEqualTo(429);
    }

    /** 조회와 다운로드는 저장본을 읽을 뿐이라 과금되지 않는다. 막지 않는다. */
    @Test
    @DisplayName("리포트 조회 GET은 상한 대상이 아니다")
    void leavesReadsAlone() throws Exception {
        String path = "/api/v1/groups/20/reports/ai-weekly";

        for (int i = 0; i < 5; i++) {
            assertThat(status(path, "GET")).isEqualTo(200);
        }
    }

    /** 그룹 id가 들어가는 경로라 정확히 이 엔드포인트만 걸려야 한다. */
    @Test
    @DisplayName("다른 리포트 경로는 상한 대상이 아니다")
    void matchesOnlyTheGenerationEndpoint() throws Exception {
        assertThat(status("/api/v1/groups/20/reports/download", "POST")).isEqualTo(200);
        assertThat(status("/api/v1/groups/20/reports/ai-weekly/9/download", "POST")).isEqualTo(200);
        assertThat(status("/api/v1/groups/20/reports/download", "POST")).isEqualTo(200);
    }

    private int status(String path, String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("203.0.113.7");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response.getStatus();
    }
}
