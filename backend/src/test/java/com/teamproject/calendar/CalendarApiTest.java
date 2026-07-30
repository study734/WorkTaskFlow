package com.teamproject.calendar;

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
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class CalendarApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void personalEventCrudStoresUtcAndReturnsGroupLocalTime() throws Exception {
        Account account = account("personal");
        long personalGroupId = personalGroupId(account.token());
        var created = mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", personalGroupId)
                        .header("Authorization", bearer(account.token())).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("SCHEDULE", "개인 일정", "2026-08-01T09:00:00", "2026-08-01T10:30:00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("EVENT"))
                .andExpect(jsonPath("$.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.startAt").value("2026-08-01T09:00:00"))
                .andExpect(jsonPath("$.startAtUtc").value("2026-08-01T00:00:00Z"))
                .andExpect(jsonPath("$.version").value(0)).andReturn();
        long eventId = number(created, "$.eventId");

        mvc.perform(patch("/api/v1/calendar-events/{eventId}", eventId)
                        .header("Authorization", bearer(account.token())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"수정 일정\",\"location\":\"회의실\",\"expectedVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정 일정"))
                .andExpect(jsonPath("$.location").value("회의실"))
                .andExpect(jsonPath("$.version").value(1));
        mvc.perform(delete("/api/v1/calendar-events/{eventId}", eventId).param("expectedVersion", "1")
                        .header("Authorization", bearer(account.token())))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(personalGroupId))
                        .param("from", "2026-08-01").param("to", "2026-08-02")
                        .header("Authorization", bearer(account.token())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void teamMemberCanReadButOnlyLeaderCanMutateAndOutsiderCannotRead() throws Exception {
        Team team = team("permission", "Asia/Seoul");
        long eventId = createEvent(team.ownerToken(), team.groupId(), "회의");

        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(team.groupId()))
                        .param("from", "2026-08-01").param("to", "2026-09-01")
                        .header("Authorization", bearer(team.memberToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].eventId").value(eventId));
        mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", team.groupId())
                        .header("Authorization", bearer(team.memberToken())).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("MEETING", "권한 없음", "2026-08-02T09:00:00", "2026-08-02T10:00:00")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
        mvc.perform(patch("/api/v1/calendar-events/{eventId}", eventId)
                        .header("Authorization", bearer(team.memberToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"권한 없음\",\"expectedVersion\":0}"))
                .andExpect(status().isForbidden());

        Account outsider = account("outsider");
        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(team.groupId()))
                        .param("from", "2026-08-01").param("to", "2026-09-01")
                        .header("Authorization", bearer(outsider.token())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("GROUP_NOT_FOUND"));
    }

    @Test
    void taskDeadlineIsComposedWithoutCopyAndMovesImmediatelyAfterTaskUpdate() throws Exception {
        Team team = team("deadline", "Asia/Seoul");
        long taskId = createTask(team.memberToken(), team.groupId(), "마감 업무", "2026-08-05T18:00:00");

        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(team.groupId()))
                        .param("from", "2026-08-01").param("to", "2026-09-01")
                        .header("Authorization", bearer(team.memberToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].source").value("TASK_DEADLINE"))
                .andExpect(jsonPath("$.items[0].sourceTaskId").value(taskId))
                .andExpect(jsonPath("$.items[0].startAtUtc").value("2026-08-05T09:00:00Z"));

        mvc.perform(patch("/api/v1/tasks/{taskId}", taskId)
                        .header("Authorization", bearer(team.memberToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dueAt\":\"2026-09-03T12:00:00\",\"expectedVersion\":0}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(team.groupId()))
                        .param("from", "2026-08-01").param("to", "2026-09-01")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(team.groupId()))
                        .param("from", "2026-09-01").param("to", "2026-10-01")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(jsonPath("$.items[0].sourceTaskId").value(taskId))
                .andExpect(jsonPath("$.items[0].startAt").value("2026-09-03T12:00:00"));
    }

    @Test
    void rejectedTaskDeadlineDisappearsAfterRetentionWindowButTaskRecordRemains() throws Exception {
        Team team = team("rejected_deadline", "Asia/Seoul");
        long taskId = createTask(team.memberToken(), team.groupId(), "반려 일정", "2026-08-05T18:00:00");
        transition(team.ownerToken(), taskId, "REJECT", 0, "진행하지 않음");

        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(team.groupId()))
                        .param("from", "2026-08-01").param("to", "2026-09-01")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourceTaskId").value(taskId));

        jdbc.update("update tasks set updated_at = ? where id = ?",
                LocalDateTime.now().minusHours(25), taskId);
        entityManager.clear();

        mvc.perform(get("/api/v1/calendars/events").param("groupId", String.valueOf(team.groupId()))
                        .param("from", "2026-08-01").param("to", "2026-09-01")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/tasks/{taskId}", taskId)
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    void unifiedCalendarIncludesEveryActiveGroupAndRangeOrVersionErrorsAreStable() throws Exception {
        Team team = team("unified", "Asia/Seoul");
        long personalGroupId = personalGroupId(team.ownerToken());
        createEvent(team.ownerToken(), team.groupId(), "팀 일정");
        createEvent(team.ownerToken(), personalGroupId, "개인 일정");

        mvc.perform(get("/api/v1/calendars/events").param("from", "2026-08-01").param("to", "2026-09-01")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2));
        mvc.perform(get("/api/v1/calendars/events").param("from", "2026-09-01").param("to", "2026-08-01")
                        .header("Authorization", bearer(team.ownerToken())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CALENDAR_RANGE_INVALID"));

        long eventId = createEvent(team.ownerToken(), team.groupId(), "버전 일정");
        mvc.perform(patch("/api/v1/calendar-events/{eventId}", eventId)
                        .header("Authorization", bearer(team.ownerToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"오래된 수정\",\"expectedVersion\":4}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CALENDAR_VERSION_CONFLICT"));
    }

    @Test
    void invalidOrderAllDayBoundaryAndDstGapAreRejected() throws Exception {
        Team team = team("timezone", "America/New_York");
        mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", team.groupId())
                        .header("Authorization", bearer(team.ownerToken())).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("SCHEDULE", "역순", "2026-08-01T11:00:00", "2026-08-01T10:00:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CALENDAR_TIME_INVALID"));
        mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", team.groupId())
                        .header("Authorization", bearer(team.ownerToken())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"VACATION\",\"title\":\"잘못된 종일\",\"startAt\":\"2026-08-01T09:00:00\",\"endAt\":\"2026-08-02T00:00:00\",\"allDay\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CALENDAR_ALL_DAY_TIME_INVALID"));
        mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", team.groupId())
                        .header("Authorization", bearer(team.ownerToken())).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("SCHEDULE", "DST 공백", "2026-03-08T02:30:00", "2026-03-08T04:00:00")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CALENDAR_LOCAL_TIME_INVALID"));
    }

    private Team team(String suffix, String timezone) throws Exception {
        Account owner = account("owner_" + suffix);
        var result = mvc.perform(post("/api/v1/groups").header("Authorization", bearer(owner.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"캘린더 " + suffix + "\",\"timezone\":\"" + timezone + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        long groupId = number(result, "$.id");
        Account member = account("member_" + suffix);
        GroupMember membership = members.save(GroupMember.member(groups.findById(groupId).orElseThrow(),
                users.findByUsernameIgnoreCase(member.username()).orElseThrow()));
        return new Team(owner.token(), member.token(), groupId, membership.getId());
    }

    private Account account(String suffix) {
        String username = "calendar_" + suffix;
        String email = username + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "캘린더 사용자", "password123!", code));
        return new Account(username, sessions.login(new LoginRequest(username, "password123!")).response().accessToken());
    }

    private long personalGroupId(String token) throws Exception {
        var result = mvc.perform(get("/api/v1/groups").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), "$[0].id")).longValue();
    }

    private long createEvent(String token, long groupId, String title) throws Exception {
        var result = mvc.perform(post("/api/v1/groups/{groupId}/calendar-events", groupId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(eventJson("MEETING", title, "2026-08-10T09:00:00", "2026-08-10T10:00:00")))
                .andExpect(status().isCreated()).andReturn();
        return number(result, "$.eventId");
    }

    private long createTask(String token, long groupId, String title, String dueAt) throws Exception {
        var result = mvc.perform(post("/api/v1/groups/{groupId}/tasks", groupId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"dueAt\":\"" + dueAt + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return number(result, "$.id");
    }

    private void transition(String token, long taskId, String action, long version, String reason) throws Exception {
        mvc.perform(post("/api/v1/tasks/{taskId}/transitions", taskId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"" + action + "\",\"expectedVersion\":" + version
                                + ",\"reason\":\"" + reason + "\"}"))
                .andExpect(status().isOk());
    }

    private String eventJson(String type, String title, String startAt, String endAt) {
        return "{\"type\":\"" + type + "\",\"title\":\"" + title + "\",\"startAt\":\""
                + startAt + "\",\"endAt\":\"" + endAt + "\",\"allDay\":false}";
    }
    private long number(org.springframework.test.web.servlet.MvcResult result, String path) throws Exception {
        return ((Number) JsonPath.read(result.getResponse().getContentAsString(), path)).longValue();
    }
    private String bearer(String token) { return "Bearer " + token; }
    private record Account(String username, String token) {}
    private record Team(String ownerToken, String memberToken, long groupId, long memberId) {}
}
