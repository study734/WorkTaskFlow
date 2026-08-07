package com.teamproject.ai.presentation;

import com.teamproject.ai.application.dto.AiAgentDtos.*;
import com.teamproject.ai.infrastructure.AiAgentClient;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.group.application.GroupAuthorization;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * AI Agent 프록시.
 *
 * <p>브라우저는 이 경로만 본다. Python 서버는 외부에 노출하지 않는다.
 * 덕분에 기존 JWT 필터, 데모 읽기 전용 차단, CORS, CSP 를 그대로 재사용한다.
 *
 * <p>그룹 소속 확인을 여기서 먼저 한다. Python 도 같은 확인을 하지만,
 * 소속되지 않은 그룹 ID 로 들어온 요청이 외부 호출까지 가지 않게 막는 편이 낫다.
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiAgentController {

    private final AiAgentClient agent;
    private final GroupAuthorization authorization;

    public AiAgentController(AiAgentClient agent, GroupAuthorization authorization) {
        this.agent = agent;
        this.authorization = authorization;
    }

    @PostMapping("/chat")
    TurnResponse chat(Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ChatRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        authorization.requireActiveMember(request.groupId(), userId);
        return agent.chat(bearer(authorizationHeader), request.groupId(),
                request.message(), request.threadId());
    }

    @PostMapping("/resume")
    TurnResponse resume(Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader,
            @Valid @RequestBody ResumeRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        authorization.requireActiveMember(request.groupId(), userId);
        return agent.resume(bearer(authorizationHeader), request.groupId(),
                request.threadId(), request.approved(), request.note());
    }

    @PostMapping("/groups/{groupId}/index")
    IndexResponse reindex(Authentication authentication,
            @RequestHeader("Authorization") String authorizationHeader,
            @PathVariable Long groupId) {
        Long userId = (Long) authentication.getPrincipal();
        // 색인은 그룹 전체 자료를 훑는 무거운 작업이라 팀장만 실행한다.
        authorization.requireLeader(groupId, userId);
        return agent.reindex(bearer(authorizationHeader), groupId);
    }

    @GetMapping("/health")
    AgentHealthResponse health() {
        return agent.health();
    }

    private String bearer(String header) {
        if (header == null || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ApplicationException("AUTHENTICATION_REQUIRED", HttpStatus.UNAUTHORIZED,
                    "로그인이 필요합니다.");
        }
        return header.substring(7);
    }
}
