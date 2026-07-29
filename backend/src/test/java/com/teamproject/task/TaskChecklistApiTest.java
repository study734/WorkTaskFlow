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
import com.teamproject.task.domain.TaskActivityEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class TaskChecklistApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskActivityEventRepository activityEvents;

    @Test
    void leaderAndAssigneeManageItemsAndProgress() throws Exception {
        Fixture fixture = fixture("manage");
        org.assertj.core.api.Assertions.assertThat(activityEvents.countByTaskId(fixture.taskId())).isEqualTo(3);
        long firstId = createItem(fixture.ownerToken(), fixture.taskId(), "요구사항 확인", null);
        long secondId = createItem(fixture.ownerToken(), fixture.taskId(), "구현 완료", null);
        org.assertj.core.api.Assertions.assertThat(activityEvents.countByTaskId(fixture.taskId())).isEqualTo(5);

        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.completedCount").value(0))
                .andExpect(jsonPath("$.progressPercent").value(0))
                .andExpect(jsonPath("$.items[0].id").value(firstId))
                .andExpect(jsonPath("$.items[0].sortOrder").value(0))
                .andExpect(jsonPath("$.items[1].sortOrder").value(1));

        mvc.perform(patch("/api/v1/checklist-items/{itemId}", firstId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.completedByMemberId").value(fixture.memberId()))
                .andExpect(jsonPath("$.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.version").value(1));
        org.assertj.core.api.Assertions.assertThat(activityEvents.countByTaskId(fixture.taskId())).isEqualTo(6);

        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.progressPercent").value(50));

        mvc.perform(patch("/api/v1/checklist-items/{itemId}", firstId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"요구사항 재확인\",\"sortOrder\":2,\"expectedVersion\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("요구사항 재확인"))
                .andExpect(jsonPath("$.version").value(2));
        org.assertj.core.api.Assertions.assertThat(activityEvents.countByTaskId(fixture.taskId())).isEqualTo(7);
        mvc.perform(delete("/api/v1/checklist-items/{itemId}", secondId)
                        .param("expectedVersion", "0")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isNoContent());
        org.assertj.core.api.Assertions.assertThat(activityEvents.countByTaskId(fixture.taskId())).isEqualTo(8);
        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.progressPercent").value(100));
    }

    @Test
    void activeMemberReadsButCannotWriteAndOutsiderCannotRead() throws Exception {
        Fixture fixture = fixture("access");
        long itemId = createItem(fixture.ownerToken(), fixture.taskId(), "읽기 전용", null);
        String viewerToken = signupAndLogin("check_viewer", "check-viewer@example.com");
        addMember(fixture.groupId(), "check_viewer");

        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].content").value("읽기 전용"));
        mvc.perform(post("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"권한 없음\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHECKLIST_WRITE_FORBIDDEN"));
        mvc.perform(patch("/api/v1/checklist-items/{itemId}", itemId)
                        .header("Authorization", bearer(viewerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/checklist-items/{itemId}", itemId)
                        .param("expectedVersion", "0")
                        .header("Authorization", bearer(viewerToken)))
                .andExpect(status().isForbidden());

        String outsiderToken = signupAndLogin("check_outsider", "check-outsider@example.com");
        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void emptyChecklistHasNoPercentageAndStaleOrBlankUpdateIsRejected() throws Exception {
        Fixture fixture = fixture("version");
        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(0))
                .andExpect(jsonPath("$.progressPercent").doesNotExist());
        long itemId = createItem(fixture.ownerToken(), fixture.taskId(), "버전 항목", 5);

        mvc.perform(patch("/api/v1/checklist-items/{itemId}", itemId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"expectedVersion\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKLIST_VERSION_CONFLICT"));
        mvc.perform(patch("/api/v1/checklist-items/{itemId}", itemId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \",\"expectedVersion\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("CHECKLIST_CONTENT_REQUIRED"));
        mvc.perform(delete("/api/v1/checklist-items/{itemId}", itemId)
                        .param("expectedVersion", "8")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isConflict());
    }

    @Test
    void terminalTaskChecklistIsReadOnly() throws Exception {
        Fixture fixture = fixture("terminal");
        long itemId = createItem(fixture.ownerToken(), fixture.taskId(), "종료 전 항목", null);
        transition(fixture.memberToken(), fixture.taskId(), "START", null, 2);
        transition(fixture.memberToken(), fixture.taskId(), "COMPLETE", null, 3);

        mvc.perform(get("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(itemId));
        mvc.perform(post("/api/v1/tasks/{taskId}/checklist-items", fixture.taskId())
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"종료 후 추가\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHECKLIST_TASK_TERMINAL"));
        mvc.perform(patch("/api/v1/checklist-items/{itemId}", itemId)
                        .header("Authorization", bearer(fixture.memberToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completed\":true,\"expectedVersion\":0}"))
                .andExpect(status().isConflict());
    }

    private Fixture fixture(String suffix) throws Exception {
        String ownerUsername = "check_owner_" + suffix;
        String ownerToken = signupAndLogin(ownerUsername, ownerUsername + "@example.com");
        long groupId = createTeam(ownerToken, "체크리스트 " + suffix);
        String memberUsername = "check_member_" + suffix;
        String memberToken = signupAndLogin(memberUsername, memberUsername + "@example.com");
        GroupMember member = addMember(groupId, memberUsername);
        long taskId = createTask(ownerToken, groupId, "체크리스트 업무 " + suffix);
        transition(ownerToken, taskId, "ACCEPT", null, 0);
        mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + member.getId() + ",\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        return new Fixture(ownerToken, memberToken, groupId, member.getId(), taskId);
    }

    private long createItem(String token, long taskId, String content, Integer sortOrder) throws Exception {
        String order = sortOrder == null ? "" : ",\"sortOrder\":" + sortOrder;
        var result = mvc.perform(post("/api/v1/tasks/{taskId}/checklist-items", taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + content + "\"" + order + "}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void transition(String token, long taskId, String action, String reason, long version) throws Exception {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"" + action + "\",\"reason\":" + reasonJson
                                + ",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());
    }

    private long createTask(String token, long groupId, String title) throws Exception {
        var result = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"" + title + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createTeam(String token, String name) throws Exception {
        var result = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private GroupMember addMember(long groupId, String username) {
        return members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),
                users.findByUsernameIgnoreCase(username).orElseThrow()));
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "체크 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Fixture(String ownerToken, String memberToken, long groupId, long memberId, long taskId) {}
}
