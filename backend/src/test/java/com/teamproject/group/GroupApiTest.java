package com.teamproject.group;

import com.teamproject.TeamProjectApplication;
import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class GroupApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupMemberRepository members;
    @Autowired GroupRepository groups;

    @Test
    void signupCreatesExactlyOnePersonalGroupAndListsIt() throws Exception {
        String token = signupAndLogin("personal_user", "personal@example.com");
        Long userId = users.findByUsernameIgnoreCase("personal_user").orElseThrow().getId();
        assertThat(members.countByUserIdAndGroupType(userId, Group.Type.PERSONAL)).isEqualTo(1);

        mvc.perform(get("/api/v1/groups").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("PERSONAL"))
                .andExpect(jsonPath("$[0].role").value("LEADER"))
                .andExpect(jsonPath("$[0].name").value("그룹 사용자의 개인 일정"));
    }

    @Test
    void authenticatedUserCreatesTeamAsLeaderAndInvalidTimezoneIsRejected() throws Exception {
        String token = signupAndLogin("team_owner", "owner@example.com");

        mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"스터디팀\",\"description\":\"주간 프로젝트\",\"timezone\":\"Asia/Seoul\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TEAM"))
                .andExpect(jsonPath("$.role").value("LEADER"))
                .andExpect(jsonPath("$.name").value("스터디팀"));

        mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"잘못된 그룹\",\"timezone\":\"Not/AZone\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TIMEZONE_INVALID"));
    }

    @Test
    void groupApiRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/groups"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void leaderReadsAndUpdatesTeamSettings() throws Exception {
        String token = signupAndLogin("settings_owner", "settings-owner@example.com");
        var created = mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"설정 전 팀\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(get("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("설정 전 팀"));
        mvc.perform(patch("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"설정 완료 팀\",\"description\":\"수정된 설명\",\"dashboardVisibility\":\"LEADER_ONLY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("설정 완료 팀"))
                .andExpect(jsonPath("$.description").value("수정된 설명"))
                .andExpect(jsonPath("$.dashboardVisibility").value("LEADER_ONLY"));
    }

    @Test
    void leaderCanSwitchPaidTestPeriodWithoutPaymentAndReturnToFree() throws Exception {
        String token = signupAndLogin("plan_owner", "plan-owner@example.com");
        var created = mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"플랜 테스트 팀\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();

        mvc.perform(put("/api/v1/groups/{groupId}/membership/test-plan", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"PAID\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipPlan").value("PAID"))
                .andExpect(jsonPath("$.paidStartedAt").isString())
                .andExpect(jsonPath("$.paidUntil").isString())
                .andExpect(jsonPath("$.nextBillingAt").isString())
                .andExpect(jsonPath("$.testPlanSwitchEnabled").value(true));

        mvc.perform(put("/api/v1/groups/{groupId}/membership/test-plan", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plan\":\"FREE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipPlan").value("FREE"))
                .andExpect(jsonPath("$.paidStartedAt").doesNotExist())
                .andExpect(jsonPath("$.paidUntil").doesNotExist())
                .andExpect(jsonPath("$.nextBillingAt").doesNotExist());
    }

    @Test
    void leaderCanRotateDeleteAndCreateJoinCode() throws Exception {
        String token = signupAndLogin("key_owner", "key-owner@example.com");
        var created = mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"키 관리 팀\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinCode").isString())
                .andReturn();
        long groupId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();
        String original = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.joinCode");

        var rotated = mvc.perform(put("/api/v1/groups/{groupId}/join-code", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinCode").isString())
                .andReturn();
        String replacement = com.jayway.jsonpath.JsonPath.read(rotated.getResponse().getContentAsString(), "$.joinCode");
        assertThat(replacement).isNotEqualTo(original);

        mvc.perform(delete("/api/v1/groups/{groupId}/join-code", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinCode").doesNotExist());
        mvc.perform(post("/api/v1/groups/{groupId}/join-code", groupId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.joinCode").isString());
    }

    @Test
    void memberCanReadButOnlyLeaderCanUpdateAndNonMemberSeesNotFound() throws Exception {
        String ownerToken = signupAndLogin("access_owner", "access-owner@example.com");
        long ownerId = users.findByUsernameIgnoreCase("access_owner").orElseThrow().getId();
        var created = mvc.perform(post("/api/v1/groups").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"권한 테스트 팀\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = ((Number) com.jayway.jsonpath.JsonPath.read(
                created.getResponse().getContentAsString(), "$.id")).longValue();
        assertThat(ownerId).isPositive();

        String memberToken = signupAndLogin("access_member", "access-member@example.com");
        var memberUser = users.findByUsernameIgnoreCase("access_member").orElseThrow();
        members.save(GroupMember.member(groups.findById(groupId).orElseThrow(), memberUser));

        mvc.perform(get("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MEMBER"));
        mvc.perform(patch("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"권한 없음\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));

        String outsiderToken = signupAndLogin("access_outsider", "access-outsider@example.com");
        mvc.perform(get("/api/v1/groups/{groupId}", groupId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "그룹 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
