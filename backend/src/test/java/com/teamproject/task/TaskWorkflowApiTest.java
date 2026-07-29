package com.teamproject.task;

import com.jayway.jsonpath.JsonPath;
import com.teamproject.TeamProjectApplication;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class TaskWorkflowApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;

    @Test
    void onlyLeaderAcceptsAndAssignsActiveGroupMember() throws Exception {
        String ownerToken = signupAndLogin("flow_owner", "flow-owner@example.com");
        long groupId = createTeam(ownerToken, "승인 팀");
        String memberToken = signupAndLogin("flow_member", "flow-member@example.com");
        GroupMember member = addMember(groupId, "flow_member");
        long taskId = createTask(memberToken, groupId, "팀원 제안");

        transition(memberToken, taskId, "ACCEPT", null, 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
        transition(ownerToken, taskId, "ACCEPT", null, 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.approverMemberId").isNumber())
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                        .header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + member.getId() + ",\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeMemberId").value(member.getId()))
                .andExpect(jsonPath("$.version").value(2));

        transition(ownerToken, taskId, "START", null, 2)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_ASSIGNEE_REQUIRED"));
    }

    @Test
    void activeMemberClaimsAnUnassignedTodoOnlyOnce() throws Exception {
        String ownerToken = signupAndLogin("claim_owner", "claim-owner@example.com");
        long groupId = createTeam(ownerToken, "자율 배정 팀");
        String memberToken = signupAndLogin("claim_member", "claim-member@example.com");
        GroupMember member = addMember(groupId, "claim_member");
        String otherToken = signupAndLogin("claim_other", "claim-other@example.com");
        addMember(groupId, "claim_other");
        long taskId = createTask(ownerToken, groupId, "자율 선택 업무");

        mvc.perform(put("/api/v1/tasks/{taskId}/assignee/me", taskId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":0}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_CLAIM_UNAVAILABLE"));

        transition(ownerToken, taskId, "ACCEPT", null, 0).andExpect(status().isOk());
        mvc.perform(put("/api/v1/tasks/{taskId}/assignee/me", taskId)
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeMemberId").value(member.getId()))
                .andExpect(jsonPath("$.version").value(2));

        mvc.perform(put("/api/v1/tasks/{taskId}/assignee/me", taskId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedVersion\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_CLAIM_UNAVAILABLE"));
    }

    @Test
    void assigneeRunsTodoThroughHoldResumeAndCompletionWithHistories() throws Exception {
        String ownerToken = signupAndLogin("lifecycle_owner", "lifecycle-owner@example.com");
        long ownerId = users.findByUsernameIgnoreCase("lifecycle_owner").orElseThrow().getId();
        long groupId = createTeam(ownerToken, "수행 팀");
        long ownerMemberId = members.findByGroupIdAndUserIdAndStatus(
                groupId, ownerId, GroupMember.Status.ACTIVE).orElseThrow().getId();
        long taskId = createTask(ownerToken, groupId, "완료할 업무");

        transition(ownerToken, taskId, "ACCEPT", null, 0).andExpect(status().isOk());
        assign(ownerToken, taskId, ownerMemberId, 1).andExpect(status().isOk());
        transition(ownerToken, taskId, "START", null, 2)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.startAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(3));
        transition(ownerToken, taskId, "HOLD", null, 3)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_REASON_REQUIRED"));
        transition(ownerToken, taskId, "HOLD", "외부 검토 대기", 3)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ON_HOLD"))
                .andExpect(jsonPath("$.holdReason").value("외부 검토 대기"))
                .andExpect(jsonPath("$.version").value(4));
        transition(ownerToken, taskId, "RESUME", null, 4)
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(5));
        transition(ownerToken, taskId, "COMPLETE", null, 5)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.delayed").value(false))
                .andExpect(jsonPath("$.version").value(6));
        transition(ownerToken, taskId, "REOPEN", null, 6)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_REASON_REQUIRED"));
        transition(ownerToken, taskId, "REOPEN", "추가 검토 필요", 6)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.completedAt").doesNotExist())
                .andExpect(jsonPath("$.version").value(7));

        mvc.perform(get("/api/v1/tasks/{taskId}/histories", taskId)
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(7))
                .andExpect(jsonPath("$[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$[0].toStatus").value("REQUESTED"))
                .andExpect(jsonPath("$[3].toStatus").value("ON_HOLD"))
                .andExpect(jsonPath("$[3].reason").value("외부 검토 대기"))
                .andExpect(jsonPath("$[5].toStatus").value("COMPLETED"))
                .andExpect(jsonPath("$[6].toStatus").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[6].reason").value("추가 검토 필요"));
    }

    @Test
    void rejectionAndCancellationRequireReasonAndFollowRoleRules() throws Exception {
        String ownerToken = signupAndLogin("stop_owner", "stop-owner@example.com");
        long groupId = createTeam(ownerToken, "종료 팀");
        String requesterToken = signupAndLogin("stop_requester", "stop-requester@example.com");
        addMember(groupId, "stop_requester");
        String otherToken = signupAndLogin("stop_other", "stop-other@example.com");
        addMember(groupId, "stop_other");

        long rejectedTaskId = createTask(requesterToken, groupId, "반려 대상");
        transition(ownerToken, rejectedTaskId, "REJECT", null, 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TASK_REASON_REQUIRED"));
        transition(ownerToken, rejectedTaskId, "REJECT", "요건 부족", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.stopReason").value("요건 부족"));
        transition(ownerToken, rejectedTaskId, "ACCEPT", null, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_TRANSITION_INVALID"));

        long cancelledTaskId = createTask(requesterToken, groupId, "취소 대상");
        transition(otherToken, cancelledTaskId, "CANCEL", "관계없는 취소", 0)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_CANCEL_FORBIDDEN"));
        transition(requesterToken, cancelledTaskId, "CANCEL", "제안 철회", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.stopReason").value("제안 철회"));
    }

    @Test
    void staleVersionAndMemberFromAnotherGroupAreRejected() throws Exception {
        String ownerToken = signupAndLogin("version_owner", "version-owner@example.com");
        long groupId = createTeam(ownerToken, "버전 팀");
        long taskId = createTask(ownerToken, groupId, "버전 업무");

        transition(ownerToken, taskId, "ACCEPT", null, 99)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_VERSION_CONFLICT"));

        long ownerId = users.findByUsernameIgnoreCase("version_owner").orElseThrow().getId();
        long personalMemberId = members.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
                        ownerId, GroupMember.Status.ACTIVE).stream()
                .filter(value -> !value.getGroup().getId().equals(groupId)).findFirst().orElseThrow().getId();
        assign(ownerToken, taskId, personalMemberId, 0)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_MEMBER_NOT_FOUND"));
    }

    @Test
    void requesterAndLeaderEditRequestedTaskButOtherMemberAndAcceptedStateCannot() throws Exception {
        String ownerToken = signupAndLogin("edit_owner", "edit-owner@example.com");
        long groupId = createTeam(ownerToken, "수정 팀");
        String requesterToken = signupAndLogin("edit_requester", "edit-requester@example.com");
        addMember(groupId, "edit_requester");
        String otherToken = signupAndLogin("edit_other", "edit-other@example.com");
        addMember(groupId, "edit_other");
        long taskId = createTask(requesterToken, groupId, "수정 전");

        update(otherToken, taskId,
                "{\"title\":\"권한 없음\",\"expectedVersion\":0}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("TASK_EDIT_FORBIDDEN"));
        update(requesterToken, taskId,
                "{\"title\":\"요청자 수정\",\"description\":\"상세 내용\",\"priority\":\"HIGH\","
                        + "\"dueAt\":\"2030-05-10T18:00:00\",\"expectedVersion\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("요청자 수정"))
                .andExpect(jsonPath("$.description").value("상세 내용"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.dueAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
        update(ownerToken, taskId,
                "{\"description\":\"\",\"clearDueAt\":true,\"expectedVersion\":1}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").doesNotExist())
                .andExpect(jsonPath("$.dueAt").doesNotExist())
                .andExpect(jsonPath("$.version").value(2));
        transition(ownerToken, taskId, "ACCEPT", null, 2).andExpect(status().isOk());
        update(requesterToken, taskId,
                "{\"title\":\"승인 후 수정\",\"expectedVersion\":3}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_EDIT_STATE_INVALID"));
    }

    @Test
    void personalTaskCanBeEditedUntilItIsTerminalAndRejectsStaleVersion() throws Exception {
        String token = signupAndLogin("edit_personal", "edit-personal@example.com");
        long userId = users.findByUsernameIgnoreCase("edit_personal").orElseThrow().getId();
        GroupMember personalMember = members.findAllByUserIdAndStatusOrderByGroupTypeAscGroupNameAsc(
                        userId, GroupMember.Status.ACTIVE).stream()
                .filter(value -> value.getGroup().getType() == com.teamproject.group.domain.Group.Type.PERSONAL)
                .findFirst().orElseThrow();
        long taskId = createTask(token, personalMember.getGroup().getId(), "개인 수정 전");

        update(token, taskId,
                "{\"title\":\"개인 수정 후\",\"priority\":\"URGENT\",\"expectedVersion\":0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("개인 수정 후"))
                .andExpect(jsonPath("$.version").value(1));
        update(token, taskId,
                "{\"title\":\"오래된 수정\",\"expectedVersion\":0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_VERSION_CONFLICT"));
        transition(token, taskId, "CANCEL", "개인 취소", 1).andExpect(status().isOk());
        update(token, taskId,
                "{\"title\":\"종료 후 수정\",\"expectedVersion\":2}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_EDIT_STATE_INVALID"));
    }

    private org.springframework.test.web.servlet.ResultActions transition(
            String token, long taskId, String action, String reason, long expectedVersion) throws Exception {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        String blockerJson = "HOLD".equals(action) && reason != null
                ? ",\"blockerType\":\"EXTERNAL\","
                        + "\"blockerNextActionType\":\"FOLLOW_UP\","
                        + "\"blockerReviewDate\":\"2099-12-31\""
                : "";
        return mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\",\"reason\":" + reasonJson
                        + blockerJson + ",\"expectedVersion\":" + expectedVersion + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions assign(
            String token, long taskId, long memberId, long expectedVersion) throws Exception {
        return mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"assigneeMemberId\":" + memberId + ",\"expectedVersion\":" + expectedVersion + "}"));
    }

    private org.springframework.test.web.servlet.ResultActions update(
            String token, long taskId, String body) throws Exception {
        return mvc.perform(patch("/api/v1/tasks/{taskId}", taskId)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long createTask(String token, long groupId, String title) throws Exception {
        var result = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createTeam(String token, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private GroupMember addMember(long groupId, String username) {
        return members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),
                users.findByUsernameIgnoreCase(username).orElseThrow()));
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "상태 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }
}
