package com.teamproject.ai;

import com.teamproject.TeamProjectApplication;
import com.teamproject.ai.application.dto.AiAgentDtos.AgentHealthResponse;
import com.teamproject.ai.application.dto.AiAgentDtos.IndexResponse;
import com.teamproject.ai.application.dto.AiAgentDtos.TurnResponse;
import com.teamproject.ai.infrastructure.AiAgentClient;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 프록시의 권한 경계를 확인한다. Python 서버는 대역으로 세운다.
 * 여기서 보려는 것은 AI 응답의 내용이 아니라 "누가 어디까지 부를 수 있는가"다.
 */
@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class AiAgentApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupMemberRepository members;
    @Autowired GroupRepository groups;
    @MockBean AiAgentClient agent;

    @Test
    void aiApiRequiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/ai/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":1,\"message\":\"안녕\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verify(agent, never()).chat(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void memberChatIsProxiedWithTheCallersToken() throws Exception {
        String token = signupAndLogin("ai_leader", "ai-leader@example.com");
        long groupId = createTeam(token, "AI 팀");
        when(agent.chat(eq(token), eq(groupId), eq("이번 주 내 업무 알려줘"), any()))
                .thenReturn(new TurnResponse("thread-1", "completed", "업무 3건이 있습니다.", null));

        mvc.perform(post("/api/v1/ai/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + ",\"message\":\"이번 주 내 업무 알려줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.threadId").value("thread-1"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.reply").value("업무 3건이 있습니다."));
    }

    @Test
    void writeToolStopsForApprovalAndResumeCarriesTheDecision() throws Exception {
        String token = signupAndLogin("ai_approver", "ai-approver@example.com");
        long groupId = createTeam(token, "승인 팀");
        when(agent.chat(anyString(), anyLong(), anyString(), any())).thenReturn(new TurnResponse(
                "thread-2", "awaiting_approval", "업무 #7을 START 처리합니다.",
                Map.of("type", "approval_request", "action", "transition_task",
                        "summary", "업무 #7을 START 처리합니다.", "details", Map.of("taskId", 7))));

        mvc.perform(post("/api/v1/ai/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + ",\"message\":\"7번 시작해줘\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("awaiting_approval"))
                .andExpect(jsonPath("$.pending.action").value("transition_task"))
                .andExpect(jsonPath("$.pending.details.taskId").value(7));

        when(agent.resume(eq(token), eq(groupId), eq("thread-2"), eq(false), eq("아직 아니에요")))
                .thenReturn(new TurnResponse("thread-2", "completed", "변경하지 않았습니다.", null));
        mvc.perform(post("/api/v1/ai/resume").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + ",\"threadId\":\"thread-2\","
                                + "\"approved\":false,\"note\":\"아직 아니에요\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("변경하지 않았습니다."));
    }

    @Test
    void outsiderIsBlockedBeforeTheAgentIsCalled() throws Exception {
        String ownerToken = signupAndLogin("ai_owner", "ai-owner@example.com");
        long groupId = createTeam(ownerToken, "닫힌 팀");
        String outsiderToken = signupAndLogin("ai_outsider", "ai-outsider@example.com");

        mvc.perform(post("/api/v1/ai/chat").header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + ",\"message\":\"업무 보여줘\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
        mvc.perform(post("/api/v1/ai/resume").header("Authorization", "Bearer " + outsiderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + ",\"threadId\":\"t\",\"approved\":true}"))
                .andExpect(status().isNotFound());

        verify(agent, never()).chat(anyString(), anyLong(), anyString(), any());
        verify(agent, never()).resume(anyString(), anyLong(), anyString(), anyBoolean(), any());
    }

    @Test
    void onlyTheLeaderCanReindexTheGroupCorpus() throws Exception {
        String leaderToken = signupAndLogin("index_leader", "index-leader@example.com");
        long groupId = createTeam(leaderToken, "색인 팀");
        String memberToken = signupAndLogin("index_member", "index-member@example.com");
        members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),
                users.findByUsernameIgnoreCase("index_member").orElseThrow()));

        mvc.perform(post("/api/v1/ai/groups/{groupId}/index", groupId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
        verify(agent, never()).reindex(anyString(), anyLong());

        when(agent.reindex(eq(leaderToken), eq(groupId)))
                .thenReturn(new IndexResponse(4, 1, 0, 2, List.of()));
        mvc.perform(post("/api/v1/ai/groups/{groupId}/index", groupId)
                        .header("Authorization", "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indexed").value(4))
                .andExpect(jsonPath("$.unsupported").value(2));
    }

    @Test
    void blankMessageIsRejectedBeforeTheAgentIsCalled() throws Exception {
        String token = signupAndLogin("ai_blank", "ai-blank@example.com");
        long groupId = createTeam(token, "빈 입력 팀");

        mvc.perform(post("/api/v1/ai/chat").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"groupId\":" + groupId + ",\"message\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verify(agent, never()).chat(anyString(), anyLong(), anyString(), any());
    }

    @Test
    void healthIsReadableByAnySignedInUser() throws Exception {
        String token = signupAndLogin("ai_health", "ai-health@example.com");
        when(agent.health()).thenReturn(new AgentHealthResponse("disabled", false, List.of("OPENAI_API_KEY")));

        mvc.perform(get("/api/v1/ai/health").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.missing[0]").value("OPENAI_API_KEY"));
    }

    private long createTeam(String token, String name) throws Exception {
        var created = mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "AI 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
