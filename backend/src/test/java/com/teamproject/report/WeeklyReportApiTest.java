package com.teamproject.report;

import com.teamproject.authentication.application.SessionService;
import com.teamproject.authentication.application.SignupService;
import com.teamproject.authentication.application.dto.SessionDtos.LoginRequest;
import com.teamproject.authentication.application.dto.SignupDtos.SignupRequest;
import com.teamproject.authentication.application.token.OneTimeTokenService;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiNarrativeGenerator;
import com.teamproject.report.application.ReportContracts.*;
import com.teamproject.report.domain.WeeklyReport;
import com.teamproject.report.domain.WeeklyReportRepository;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WeeklyReportApiTest {
    @Autowired MockMvc mvc;
    @Autowired SignupService signup;
    @Autowired SessionService sessions;
    @Autowired OneTimeTokenService oneTimeTokens;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired WeeklyReportRepository reports;
    @Autowired ObjectMapper objectMapper;
    @MockBean AiNarrativeGenerator narrativeGenerator;

    @Test
    void freeGroupIsLockedAndPaidLeaderCreatesThenReadsCachedReport() throws Exception {
        Fixture free = team(false);
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        mvc.perform(post("/api/v1/groups/{groupId}/reports/ai-weekly", free.group().getId())
                        .header("Authorization", bearer(free.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(weekStart)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_REPORT_PAID_REQUIRED"));

        Fixture paid = team(true);
        Task task = new Task(paid.group(), paid.leader(), "내부 업무 제목", "내부 설명",
                Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(10, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(10, 0));
        tasks.save(task);
        when(narrativeGenerator.generate(any())).thenReturn(new AiGenerationResult(
                withOwner(new Narrative("주간 요약", "확정 지표 요약",
                        List.of(new NarrativeItem("업무가 등록되었습니다.", List.of("tasks.total"))),
                        List.of(), List.of(),
                        List.of(new NarrativeItem("이력 제한이 있습니다.",
                                List.of("coverage.partial"))))),
                "gpt-5.6-luna", 10, 20, 30));

        MvcResult generated = mvc.perform(post("/api/v1/groups/{groupId}/reports/ai-weekly",
                        paid.group().getId())
                        .header("Authorization", bearer(paid.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(weekStart)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cached").value(false))
                .andExpect(jsonPath("$.language").value("ko"))
                .andExpect(jsonPath("$.metrics.evidence['tasks.total']").value(1))
                .andExpect(jsonPath("$.analysis.headline").value("주간 요약"))
                .andReturn();
        long reportId = objectMapper.readTree(generated.getResponse().getContentAsByteArray())
                .path("reportId").asLong();
        String memberToken = addMember(paid.group());
        mvc.perform(get("/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}",
                        paid.group().getId(), reportId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_REPORT_NOT_FINALIZED"));

        mvc.perform(post("/api/v1/groups/{groupId}/reports/ai-weekly", paid.group().getId())
                        .header("Authorization", bearer(paid.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(weekStart)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true));

        mvc.perform(get("/api/v1/groups/{groupId}/reports/ai-weekly", paid.group().getId())
                        .param("weekStart", weekStart.toString())
                        .param("language", "ko")
                        .header("Authorization", bearer(paid.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cached").value(true))
                .andExpect(jsonPath("$.analysis.headline").value("주간 요약"));

        mvc.perform(post("/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/finalization",
                        paid.group().getId(), reportId)
                        .header("Authorization", bearer(paid.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expectedEditorVersion\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("FINALIZED"));

        mvc.perform(get("/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}",
                        paid.group().getId(), reportId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicationStatus").value("FINALIZED"));

        byte[] pdf = mvc.perform(get(
                        "/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/pdf",
                        paid.group().getId(), reportId)
                        .header("Authorization", bearer(paid.token())))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(MediaType.APPLICATION_PDF_VALUE.equals(
                        result.getResponse().getContentType())))
                .andExpect(result -> assertTrue(result.getResponse()
                        .getHeader("Content-Disposition").contains("attachment")))
                .andReturn().getResponse().getContentAsByteArray();
        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                .startsWith("%PDF-"));
        assertTrue(pdfText(pdf).contains("주간 요약"));
        assertTrue(pdfText(pdf).contains("근거:"));
        assertTrue(pdfText(pdf).contains("전체 업무"));

        mvc.perform(get("/api/v1/groups/{groupId}/reports/ai-weekly/{reportId}/pdf",
                        paid.group().getId(), reportId)
                        .header("Authorization", bearer(memberToken)))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(MediaType.APPLICATION_PDF_VALUE.equals(
                        result.getResponse().getContentType())));
    }

    @Test
    void basicReportDownloadsAsPdfWithoutOpenAi() throws Exception {
        Fixture free = team(false);
        LocalDate from = LocalDate.now().minusDays(7);
        LocalDate to = LocalDate.now().plusDays(1);
        Task task = new Task(free.group(), free.leader(), "한국어 기본 리포트 업무", null,
                Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", from.plusDays(1).atTime(10, 0));
        ReflectionTestUtils.setField(task, "updatedAt", from.plusDays(1).atTime(10, 0));
        tasks.save(task);

        byte[] pdf = mvc.perform(post("/api/v1/groups/{groupId}/reports/basic.pdf",
                        free.group().getId())
                        .header("Authorization", bearer(free.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"scope":"GROUP","periodType":"WEEKLY",
                                 "from":"%s","to":"%s","language":"ko"}
                                """.formatted(from, to)))
                .andExpect(status().isOk())
                .andExpect(result -> assertTrue(MediaType.APPLICATION_PDF_VALUE.equals(
                        result.getResponse().getContentType())))
                .andExpect(result -> assertTrue(result.getResponse()
                        .getHeader("Content-Disposition").contains("attachment")))
                .andReturn().getResponse().getContentAsByteArray();

        assertTrue(pdf.length > 100);
        assertTrue(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)
                .startsWith("%PDF-"));
        assertTrue(pdfText(pdf).contains("한국어 기본 리포트 업무"));
    }

    @Test
    void mapsGenerationConflictAndProviderFailuresToHttpContract() throws Exception {
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Fixture generating = team(true);
        java.time.Instant startedAt = java.time.Instant.now();
        WeeklyReport inProgress = new WeeklyReport(generating.group(), generating.leader(),
                weekStart, weekStart.plusDays(6), "ko", 1, WeeklyReport.TriggerType.USER,
                "{}", "v1", "v1", java.time.LocalDateTime.now());
        inProgress.start("{}", "v1", "v1", startedAt, java.time.LocalDateTime.now());
        reports.save(inProgress);

        mvc.perform(post("/api/v1/groups/{groupId}/reports/ai-weekly", generating.group().getId())
                        .header("Authorization", bearer(generating.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(weekStart)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AI_REPORT_GENERATING"));

        Fixture providerFailure = team(true);
        Task task = new Task(providerFailure.group(), providerFailure.leader(), "집계 대상", null,
                Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(10, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(10, 0));
        tasks.save(task);
        when(narrativeGenerator.generate(any()))
                .thenThrow(new ApplicationException("AI_REPORT_PROVIDER_UNAVAILABLE",
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "일시 장애"))
                .thenThrow(new ApplicationException("AI_REPORT_TIMEOUT",
                        org.springframework.http.HttpStatus.GATEWAY_TIMEOUT, "시간 초과"));

        mvc.perform(post("/api/v1/groups/{groupId}/reports/ai-weekly", providerFailure.group().getId())
                        .header("Authorization", bearer(providerFailure.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(weekStart)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AI_REPORT_PROVIDER_UNAVAILABLE"));

        mvc.perform(post("/api/v1/groups/{groupId}/reports/ai-weekly", providerFailure.group().getId())
                        .header("Authorization", bearer(providerFailure.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(weekStart)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("AI_REPORT_TIMEOUT"));
    }

    private Narrative withOwner(Narrative narrative) {
        return new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                narrative.topActions().stream()
                        .map(action -> new ActionNarrativeItem(
                                action.priority(), action.actionTemplate(),
                                action.reasonTemplate(), "MEMBER-01",
                                action.evidenceKeys(), action.taskRefs(),
                                action.objectiveRefs()))
                        .toList(),
                narrative.leaderDecisions(), narrative.limitations());
    }

    private Fixture team(boolean paid) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "ai_api_" + suffix;
        String email = username + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "AI 팀장", "password123!", code));
        String token = sessions.login(new LoginRequest(username, "password123!")).response().accessToken();
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        Group group = Group.team(paid ? "유료 AI 팀" : "무료 AI 팀", null, "Asia/Seoul", user);
        if (paid) ReflectionTestUtils.setField(group, "membershipPlan", Group.MembershipPlan.PAID);
        group = groups.save(group);
        GroupMember leader = members.save(GroupMember.leader(group, user));
        return new Fixture(token, group, leader);
    }

    private String body(LocalDate weekStart) {
        return "{\"weekStart\":\"" + weekStart + "\",\"language\":\"ko\"}";
    }

    private String addMember(Group group) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "ai_member_" + suffix;
        String email = username + "@example.com";
        String code = oneTimeTokens.issueCode(email);
        signup.signup(new SignupRequest(username, email, "AI 팀원", "password123!", code));
        String token = sessions.login(new LoginRequest(username, "password123!"))
                .response().accessToken();
        User user = users.findByUsernameIgnoreCase(username).orElseThrow();
        members.save(GroupMember.member(group, user));
        return token;
    }

    private String bearer(String token) { return "Bearer " + token; }
    private String pdfText(byte[] pdf) throws Exception {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }
    private record Fixture(String token, Group group, GroupMember leader) {}
}
