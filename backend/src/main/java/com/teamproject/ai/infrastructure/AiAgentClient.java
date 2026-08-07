package com.teamproject.ai.infrastructure;

import com.teamproject.ai.application.dto.AiAgentDtos.*;
import com.teamproject.common.exception.ApplicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Python AI 서버 호출 클라이언트.
 *
 * <p>Python 서버는 내부망에만 있고 사용자 토큰을 스스로 검증하지 않는다.
 * 신원 판단은 여기까지가 전부이고, Python 은 그 토큰으로 다시 이 서버의
 * API 를 부른다. 그래서 AI 경로가 사용자보다 큰 권한을 가질 수 없다.
 */
@Component
public class AiAgentClient {

    private static final Logger log = LoggerFactory.getLogger(AiAgentClient.class);
    private static final String SECRET_HEADER = "X-Internal-Secret";

    private final AiAgentProperties properties;
    private final RestClient client;

    public AiAgentClient(AiAgentProperties properties) {
        this.properties = properties;
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Math.min(Integer.MAX_VALUE, 5_000));
        factory.setReadTimeout((int) Math.min(Integer.MAX_VALUE, properties.requestTimeout().toMillis()));
        this.client = RestClient.builder().requestFactory(factory).baseUrl(properties.baseUrl()).build();
    }

    public TurnResponse chat(String userToken, Long groupId, String message, String threadId) {
        // 키 이름은 Python 쪽 모델과 정확히 같아야 한다. 어긋나면 값이 조용히 null 이 된다.
        var body = new java.util.HashMap<String, Object>();
        body.put("groupId", groupId);
        body.put("message", message);
        body.put("threadId", threadId);
        return post("/internal/chat", userToken, body, TurnResponse.class);
    }

    public TurnResponse resume(String userToken, Long groupId, String threadId, boolean approved, String note) {
        return post("/internal/resume", userToken,
                Map.of("groupId", groupId, "threadId", threadId, "approved", approved,
                        "note", note == null ? "" : note),
                TurnResponse.class);
    }

    public IndexResponse reindex(String userToken, Long groupId) {
        return post("/internal/groups/" + groupId + "/index", userToken, Map.of(), IndexResponse.class);
    }

    public AgentHealthResponse health() {
        requireEnabled();
        try {
            return client.get().uri("/internal/health")
                    .header(SECRET_HEADER, properties.internalSecret())
                    .retrieve().body(AgentHealthResponse.class);
        } catch (ResourceAccessException exception) {
            return new AgentHealthResponse("unreachable", false, List.of("AI 서버에 연결하지 못했습니다."));
        }
    }

    private <T> T post(String path, String userToken, Object body, Class<T> type) {
        requireEnabled();
        try {
            return client.post().uri(path)
                    .header("Authorization", "Bearer " + userToken)
                    .header(SECRET_HEADER, properties.internalSecret())
                    .body(body)
                    .exchange((request, response) -> {
                        if (response.getStatusCode().isError()) {
                            throw translate(response.getStatusCode().value(), readDetail(response));
                        }
                        return response.bodyTo(type);
                    });
        } catch (ResourceAccessException exception) {
            log.warn("AI 서버 호출 실패 path={} error={}", path, exception.getClass().getSimpleName());
            throw new ApplicationException("AI_AGENT_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private String readDetail(RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
        try {
            Map<?, ?> body = response.bodyTo(Map.class);
            Object detail = body == null ? null : body.get("detail");
            return detail == null ? null : detail.toString();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /** Python 이 준 상태 코드를 그대로 살린다. 권한 판단은 이미 Spring 규칙을 통과한 결과다. */
    private ApplicationException translate(int status, String detail) {
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved == null || resolved.is5xxServerError()) {
            return new ApplicationException("AI_AGENT_FAILED", HttpStatus.BAD_GATEWAY,
                    detail == null ? "AI 처리 중 오류가 발생했습니다." : detail);
        }
        return new ApplicationException("AI_AGENT_REJECTED", resolved,
                detail == null ? "AI 요청을 처리할 수 없습니다." : detail);
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new ApplicationException("AI_AGENT_DISABLED", HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 기능이 꺼져 있습니다.");
        }
        if (!properties.hasInternalSecret()) {
            log.warn("app.ai-agent.internal-secret is missing");
            throw new ApplicationException("AI_AGENT_DISABLED", HttpStatus.SERVICE_UNAVAILABLE,
                    "AI 기능이 설정되지 않았습니다.");
        }
    }
}
