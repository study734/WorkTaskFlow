package com.teamproject.notification;

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
import com.teamproject.notification.application.TaskReminderScheduler;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class NotificationApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskReminderScheduler reminders;

    @Test
    void soleLeaderReceivesOwnApprovalAndSelfAssignmentNotifications() throws Exception {
        Fixture fixture = fixture("self_flow");
        long taskId = createTask(fixture.ownerToken(), fixture.groupId(), "혼자 관리하는 업무");
        transition(fixture.ownerToken(), taskId, "ACCEPT", 0);
        assign(fixture.ownerToken(), taskId, fixture.ownerMemberId(), 1);

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.items[1].type").value("TASK_REQUESTED"))
                .andExpect(jsonPath("$.unreadCount").value(2));
    }

    @Test
    void taskRequestCreatesLeaderNotificationAndReadEndpointsAreConsistent() throws Exception {
        Fixture fixture = fixture("request");
        long taskId = createTask(fixture.memberToken(), fixture.groupId(), "승인 요청 업무");

        var result = mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("TASK_REQUESTED"))
                .andExpect(jsonPath("$.items[0].taskId").value(taskId))
                .andExpect(jsonPath("$.items[0].actorUserId").isNumber())
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.unreadCount").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn();
        long notificationId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.items[0].id")).longValue();

        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mvc.perform(patch("/api/v1/notifications/read-all")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0));
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(delete("/api/v1/notifications/{id}", notificationId)
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void assigneeAndMentionOverlapCreatesOneCommentNotificationAndSelfIsExcluded() throws Exception {
        Fixture fixture = fixture("dedupe");
        long taskId = createTask(fixture.memberToken(), fixture.groupId(), "중복 방지 업무");
        transition(fixture.ownerToken(), taskId, "ACCEPT", 0);
        assign(fixture.ownerToken(), taskId, fixture.memberId(), 1);

        mvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"담당자 확인\",\"mentionedMemberIds\":["
                                + fixture.memberId() + "," + fixture.memberId() + "]}"))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("COMMENT_MENTIONED"))
                .andExpect(jsonPath("$.items[1].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.unreadCount").value(2));
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void nicknameTypedInCommentCreatesMentionNotificationWithoutPickerIds() throws Exception {
        Fixture fixture = fixture("typed_mention");
        long taskId = createTask(fixture.ownerToken(), fixture.groupId(), "텍스트 멘션 업무");

        mvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"@알림 사용자 확인 부탁드립니다.\",\"mentionedMemberIds\":[]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mentions.length()").value(2));

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("COMMENT_MENTIONED"))
                .andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    void newlyAddedEditMentionNotifiesOnceAndAnotherUserCannotReadIt() throws Exception {
        Fixture fixture = fixture("edit_mention");
        String thirdToken = signupAndLogin("notification_third", "notification-third@example.com");
        GroupMember third = addMember(fixture.groupId(), "notification_third");
        long taskId = createTask(fixture.ownerToken(), fixture.groupId(), "수정 멘션 업무");
        long commentId = createComment(fixture.ownerToken(), taskId, fixture.memberId());

        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"추가 멘션\",\"mentionedMemberIds\":["
                                + fixture.memberId() + "," + third.getId() + "],\"expectedVersion\":0}"))
                .andExpect(status().isOk());

        var result = mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(thirdToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].type").value("COMMENT_MENTIONED"))
                .andReturn();
        long notificationId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.items[0].id")).longValue();

        mvc.perform(patch("/api/v1/notifications/{id}/read", notificationId)
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"멘션 제거\",\"mentionedMemberIds\":["
                                + fixture.memberId() + "],\"expectedVersion\":1}"))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/v1/comments/{commentId}", commentId)
                        .header("Authorization", bearer(fixture.ownerToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"다시 멘션\",\"mentionedMemberIds\":["
                                + fixture.memberId() + "," + third.getId() + "],\"expectedVersion\":2}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(thirdToken)))
                .andExpect(jsonPath("$.items.length()").value(2));
    }

    @Test
    void cursorPaginationUsesStableDescendingIds() throws Exception {
        Fixture fixture = fixture("paging");
        createTask(fixture.memberToken(), fixture.groupId(), "페이지 업무 1");
        createTask(fixture.memberToken(), fixture.groupId(), "페이지 업무 2");
        createTask(fixture.memberToken(), fixture.groupId(), "페이지 업무 3");

        var first = mvc.perform(get("/api/v1/notifications").param("size", "2")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.hasNext").value(true))
                .andExpect(jsonPath("$.nextCursor").isNumber())
                .andReturn();
        Number cursor = JsonPath.read(first.getResponse().getContentAsString(), "$.nextCursor");
        mvc.perform(get("/api/v1/notifications").param("size", "2").param("cursor", cursor.toString())
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    @Test
    void completionNotifiesLeaderAndRejectOrCancelNotifiesRequester() throws Exception {
        Fixture fixture = fixture("status");
        long completedTask = createTask(fixture.memberToken(), fixture.groupId(), "완료 알림 업무");
        transition(fixture.ownerToken(), completedTask, "ACCEPT", 0);
        assign(fixture.ownerToken(), completedTask, fixture.memberId(), 1);
        transition(fixture.memberToken(), completedTask, "START", 2);
        transition(fixture.memberToken(), completedTask, "COMPLETE", 3);

        long rejectedTask = createTask(fixture.memberToken(), fixture.groupId(), "반려 알림 업무");
        transitionWithReason(fixture.ownerToken(), rejectedTask, "REJECT", 0, "범위 재검토");
        long cancelledTask = createTask(fixture.memberToken(), fixture.groupId(), "취소 알림 업무");
        transitionWithReason(fixture.ownerToken(), cancelledTask, "CANCEL", 0, "요청 취소");

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].type").value("TASK_STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[0].taskId").value(cancelledTask))
                .andExpect(jsonPath("$.items[1].taskId").value(rejectedTask))
                .andExpect(jsonPath("$.items[2].type").value("TASK_ASSIGNED"));
        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[2].type").value("TASK_STATUS_CHANGED"))
                .andExpect(jsonPath("$.items[2].taskId").value(completedTask));
    }

    @Test
    void holdAndResumeNotifyGroupLeader() throws Exception {
        Fixture fixture = fixture("hold_resume");
        long taskId = createTask(fixture.memberToken(), fixture.groupId(), "상태 공유 업무");
        transition(fixture.ownerToken(), taskId, "ACCEPT", 0);
        assign(fixture.ownerToken(), taskId, fixture.memberId(), 1);
        transition(fixture.memberToken(), taskId, "START", 2);
        transitionWithReason(fixture.memberToken(), taskId, "HOLD", 3, "외부 확인 대기");
        transition(fixture.memberToken(), taskId, "RESUME", 4);

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].message").value("'상태 공유 업무' 업무가 재개 상태가 되었습니다."))
                .andExpect(jsonPath("$.items[1].message").value("'상태 공유 업무' 업무가 보류 상태가 되었습니다."));
    }

    @Test
    void dueSoonSchedulerNotifiesAssigneeOnlyOnce() throws Exception {
        Fixture fixture = fixture("due_soon");
        long taskId = createTask(fixture.memberToken(), fixture.groupId(), "곧 마감 업무",
                LocalDateTime.now(ZoneId.of("Asia/Seoul")).plusHours(2).withNano(0).toString());
        transition(fixture.ownerToken(), taskId, "ACCEPT", 0);
        assign(fixture.ownerToken(), taskId, fixture.memberId(), 1);

        reminders.sendDueSoonNotifications();
        reminders.sendDueSoonNotifications();

        mvc.perform(get("/api/v1/notifications")
                        .header("Authorization", bearer(fixture.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].type").value("TASK_DUE_SOON"))
                .andExpect(jsonPath("$.items[0].title").value("중요 마감일 임박"))
                .andExpect(jsonPath("$.items[1].type").value("TASK_ASSIGNED"));
    }

    private Fixture fixture(String suffix) throws Exception {
        String ownerUsername = "notification_owner_" + suffix;
        String ownerToken = signupAndLogin(ownerUsername, ownerUsername + "@example.com");
        var groupResult = mvc.perform(post("/api/v1/groups")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"알림 " + suffix + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = ((Number) JsonPath.read(groupResult.getResponse().getContentAsString(), "$.id")).longValue();
        long ownerMemberId = ((Number) JsonPath.read(groupResult.getResponse().getContentAsString(), "$.memberId")).longValue();
        String memberUsername = "notification_member_" + suffix;
        String memberToken = signupAndLogin(memberUsername, memberUsername + "@example.com");
        GroupMember member = addMember(groupId, memberUsername);
        return new Fixture(ownerToken, memberToken, groupId, ownerMemberId, member.getId());
    }

    private GroupMember addMember(long groupId, String username) {
        return members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),
                users.findByUsernameIgnoreCase(username).orElseThrow()));
    }

    private long createTask(String token, long groupId, String title) throws Exception {
        return createTask(token, groupId, title, null);
    }

    private long createTask(String token, long groupId, String title, String dueAt) throws Exception {
        String due = dueAt == null ? "" : ",\"dueAt\":\"" + dueAt + "\"";
        var result = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\"" + due + "}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private long createComment(String token, long taskId, long mentionedMemberId) throws Exception {
        var result = mvc.perform(post("/api/v1/tasks/{taskId}/comments", taskId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"첫 멘션\",\"mentionedMemberIds\":[" + mentionedMemberId + "]}"))
                .andExpect(status().isCreated()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$.id")).longValue();
    }

    private void transition(String token, long taskId, String action, long version) throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());
    }

    private void transitionWithReason(String token, long taskId, String action, long version, String reason) throws Exception {
        String blockerJson = "HOLD".equals(action)
                ? ",\"blockerType\":\"EXTERNAL\","
                        + "\"blockerNextActionType\":\"FOLLOW_UP\","
                        + "\"blockerReviewDate\":\"2099-12-31\""
                : "";
        mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version
                        + ",\"reason\":\"" + reason + "\"" + blockerJson + "}"))
                .andExpect(status().isOk());
    }

    private void assign(String token, long taskId, long memberId, long version) throws Exception {
        mvc.perform(put("/api/v1/tasks/{taskId}/assignee", taskId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeMemberId\":" + memberId + ",\"expectedVersion\":" + version + "}"))
                .andExpect(status().isOk());
    }

    private String signupAndLogin(String username, String email) {
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "알림 사용자", "password123!", code));
        return sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
    }

    private String bearer(String token) { return "Bearer " + token; }
    private record Fixture(String ownerToken, String memberToken, long groupId, long ownerMemberId, long memberId) {}
}
