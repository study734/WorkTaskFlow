package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.TeamProjectApplication;
import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.jwt.JwtService;
import com.teamproject.report.application.AiWeeklyReportFallbackFactory;
import com.teamproject.report.application.AiWeeklyReportPolicyEngine;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;
import com.teamproject.report.application.port.AiWeeklyReportGateway;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import com.teamproject.report.domain.WeeklyReport;
import com.teamproject.report.domain.WeeklyReportRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = TeamProjectApplication.class)
@AutoConfigureMockMvc
@Transactional
class AiWeeklyReportApiTest {

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository userRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private GroupMemberRepository memberRepository;
    @Autowired private AiWeeklyReportRevisionRepository revisionRepository;
    @Autowired private WeeklyReportRepository legacyReportRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private JwtService jwtService;
    @Autowired private com.teamproject.authentication.application.AccessSessionIssuer sessionIssuer;

    @MockBean private AiWeeklyReportGateway gateway;

    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();
    private final AiWeeklyReportFallbackFactory fallbackFactory = new AiWeeklyReportFallbackFactory();

    private User leaderUser;
    private User memberUser;
    private User nonMemberUser;
    private Group paidTeamGroup;
    private Group freeGroup;
    private String leaderToken;
    private String memberToken;
    private String nonMemberToken;
    private AtomicInteger gatewayCallCount;

    @BeforeEach
    void setUp() {
        gatewayCallCount = new AtomicInteger(0);
        when(gateway.analyze(any())).thenAnswer(invocation -> {
            gatewayCallCount.incrementAndGet();
            AiWeeklyReportSnapshotV1 snapshot = invocation.getArgument(0);
            return AiWeeklyReportGateway.Analysis.of(fallbackFactory.create(snapshot));
        });

        leaderUser = userRepository.save(new User("leader_api", "leader_api@test.local", "pass123!", "리더", true));
        memberUser = userRepository.save(new User("member_api", "member_api@test.local", "pass123!", "팀원", true));
        nonMemberUser = userRepository.save(new User("nonmember_api", "nonmember@test.local", "pass123!", "외부인", true));

        paidTeamGroup = Group.team("유료팀", "설명", "Asia/Seoul", leaderUser);
        paidTeamGroup.switchTestMembership(Group.MembershipPlan.PAID, LocalDateTime.now());
        paidTeamGroup = groupRepository.save(paidTeamGroup);

        freeGroup = groupRepository.save(Group.team("무료그룹", "설명", "Asia/Seoul", leaderUser));

        memberRepository.save(GroupMember.leader(paidTeamGroup, leaderUser));
        memberRepository.save(GroupMember.member(paidTeamGroup, memberUser));
        memberRepository.save(GroupMember.leader(freeGroup, leaderUser));

        leaderToken = sessionIssuer.issue(leaderUser).response().accessToken();
        memberToken = sessionIssuer.issue(memberUser).response().accessToken();
        nonMemberToken = sessionIssuer.issue(nonMemberUser).response().accessToken();
    }

    @Test
    @DisplayName("유료 팀 리더가 정상 기간으로 생성 요청 시 201 Created를 반환한다")
    void normalGenerateReturns201() throws Exception {
        String body = """
                {
                  "from": "2026-07-20",
                  "toExclusive": "2026-07-27",
                  "language": "KO",
                  "regenerate": false
                }
                """;

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportId").isNotEmpty())
                .andExpect(jsonPath("$.revision").value(1))
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());

        assertThat(gatewayCallCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 source에 regenerate=false일 때 200 OK를 반환하고 기존 revision을 재사용하며 Gateway를 추가 호출하지 않는다")
    void duplicateGenerateReusesExistingRevision() throws Exception {
        String body = """
                {
                  "from": "2026-07-20",
                  "toExclusive": "2026-07-27",
                  "language": "KO",
                  "regenerate": false
                }
                """;

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        assertThat(gatewayCallCount.get()).isEqualTo(1);

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revision").value(1));

        assertThat(gatewayCallCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("regenerate=true 시 201 Created를 반환하고 revision이 2로 증가하며 Gateway가 추가 호출된다")
    void regenerateCreatesNewRevision() throws Exception {
        String body = """
                {
                  "from": "2026-07-20",
                  "toExclusive": "2026-07-27",
                  "language": "KO",
                  "regenerate": false
                }
                """;

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        String regenBody = """
                {
                  "from": "2026-07-20",
                  "toExclusive": "2026-07-27",
                  "language": "KO",
                  "regenerate": true
                }
                """;

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(regenBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.revision").value(2));

        assertThat(gatewayCallCount.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("무료 그룹에서 생성 요청 시 403 Forbidden (AI_REPORT_PAID_REQUIRED)을 반환한다")
    void freeGroupCreationReturns403() throws Exception {
        String body = """
                {
                  "from": "2026-07-20",
                  "toExclusive": "2026-07-27",
                  "language": "KO",
                  "regenerate": false
                }
                """;

        mvc.perform(post("/api/v1/groups/" + freeGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AI_REPORT_PAID_REQUIRED"));
    }

    @Test
    @DisplayName("일반 MEMBER가 생성 요청 시 403 Forbidden (GROUP_LEADER_REQUIRED)을 반환한다")
    void regularMemberCreationReturns403() throws Exception {
        String body = """
                {
                  "from": "2026-07-20",
                  "toExclusive": "2026-07-27",
                  "language": "KO",
                  "regenerate": false
                }
                """;

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("GROUP_LEADER_REQUIRED"));
    }

    @Test
    @DisplayName("from이 toExclusive 이후이거나 같은 잘못된 기간은 400 Bad Request를 반환한다")
    void invalidPeriodReturns400() throws Exception {
        String invalidBody = """
                {
                  "from": "2026-07-28",
                  "toExclusive": "2026-07-21",
                  "language": "KO",
                  "regenerate": false
                }
                """;

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_REPORT_WEEK_INVALID"));
    }

    @Test
    @DisplayName("그룹 timezone 기준 toExclusive가 미래인 미완료 주간 생성 시 400 Bad Request (AI_REPORT_WEEK_INCOMPLETE)를 반환하고 Gateway 0회 호출되며 revision은 저장되지 않는다")
    void uncompletedWeekGenerateReturns400AndZeroGatewayCall() throws Exception {
        int preCallCount = gatewayCallCount.get();
        long preRevCount = revisionRepository.count();

        LocalDate futureMonday = LocalDate.now().plusWeeks(2).with(java.time.DayOfWeek.MONDAY);
        LocalDate futureToExclusive = futureMonday.plusDays(7);

        String body = String.format("""
                {
                  "from": "%s",
                  "toExclusive": "%s",
                  "language": "KO",
                  "regenerate": false
                }
                """, futureMonday, futureToExclusive);

        mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AI_REPORT_WEEK_INCOMPLETE"));

        assertThat(gatewayCallCount.get()).isEqualTo(preCallCount);
        assertThat(revisionRepository.count()).isEqualTo(preRevCount);
    }

    @Test
    @DisplayName("팀원이 리포트를 조회할 수 있으며 Gateway 호출은 0회이다")
    void memberCanGetReportByIdWithoutGatewayCall() throws Exception {
        AiWeeklyReportRevision rev = revisionRepository.save(new AiWeeklyReportRevision(
                paidTeamGroup.getId(), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27),
                "KO", 1, "FINALIZED", "OPENAI", "FP1", "{\"schemaVersion\":\"ai-weekly-report-snapshot.v1\",\"reportContext\":{\"groupId\":\"GROUP-1\",\"period\":{\"from\":\"2026-07-20\",\"toExclusive\":\"2026-07-27\",\"timezone\":\"Asia/Seoul\"},\"snapshotAt\":\"2026-07-27T00:00:00Z\",\"language\":\"KO\",\"promptVersion\":\"v7-2\"},\"metrics\":{\"periodTaskCount\":5,\"completionRatePercent\":80,\"onTimeRatePercent\":100,\"delayedCount\":0,\"averageLeadTimeHours\":12},\"comparison\":{\"status\":\"NO_BASELINE\"},\"workflow\":{\"requestedCount\":0,\"todoUnassignedCount\":0,\"todoAssignedCount\":0,\"inProgressCount\":1,\"onHoldCount\":0,\"completedCount\":4},\"members\":[],\"tasks\":[],\"calendarConstraints\":[],\"riskCandidates\":[]}", "{\"schemaVersion\":\"ai-weekly-report-analysis.v1\",\"analysisStatus\":\"NORMAL\",\"executiveJudgment\":{\"headline\":\"H\",\"interpretation\":\"I\",\"metricRefs\":[],\"evidenceTaskRefs\":[],\"confidence\":\"HIGH\",\"missingEvidence\":[]},\"achievement\":{\"status\":\"NONE\",\"headline\":\"\",\"summary\":\"\",\"evidenceTaskRefs\":[]},\"issues\":[],\"globalMissingEvidence\":[]}",
                "v7-2-prompt-001", "gpt-4o", 100, 200, LocalDateTime.now(), LocalDateTime.now()
        ));

        int preCallCount = gatewayCallCount.get();

        mvc.perform(get("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly/" + rev.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportId").value(rev.getId()))
                .andExpect(jsonPath("$.status").value("FINALIZED"))
                .andExpect(jsonPath("$.inputTokens").doesNotExist())
                .andExpect(jsonPath("$.rawSnapshotJson").doesNotExist());

        assertThat(gatewayCallCount.get()).isEqualTo(preCallCount);
    }

    /**
     * 상세 화면이 통째로 500이 되던 경로다. 생성 직후 저장된 revision을 그대로 다시 읽어
     * 업무·지표·workflow가 응답에 실리는지, 조회 중 Gateway를 부르지 않는지 확인한다.
     */
    @Test
    @DisplayName("생성한 revision을 곧바로 조회하면 200과 함께 업무·지표·workflow를 반환한다")
    void generatedRevisionIsReadableWithTasksMetricsAndWorkflow() throws Exception {
        Task task = taskRepository.save(periodTask());

        String body = """
                {
                  "from": "2026-07-20",
                  "toExclusive": "2026-07-27",
                  "language": "KO",
                  "regenerate": false
                }
                """;

        String created = mvc.perform(post("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long reportId = json.readTree(created).get("reportId").asLong();

        int preCallCount = gatewayCallCount.get();

        mvc.perform(get("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly/" + reportId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysisMode").isNotEmpty())
                .andExpect(jsonPath("$.from").value("2026-07-20"))
                .andExpect(jsonPath("$.toExclusive").value("2026-07-27"))
                .andExpect(jsonPath("$.metrics.periodTaskCount").value(1))
                .andExpect(jsonPath("$.workflow.inProgressCount").value(1))
                .andExpect(jsonPath("$.tasks[0].taskRef").value("TASK-" + task.getId()))
                .andExpect(jsonPath("$.tasks[0].realTitle").value(task.getTitle()))
                .andExpect(jsonPath("$.executiveJudgment.headline").isNotEmpty());

        assertThat(gatewayCallCount.get()).isEqualTo(preCallCount);
    }

    private Task periodTask() {
        Task task = new Task(paidTeamGroup, memberRepository
                .findByGroupIdAndUserIdAndStatus(paidTeamGroup.getId(), leaderUser.getId(),
                        GroupMember.Status.ACTIVE).orElseThrow(),
                "모바일 화면 최종 점검", null, Task.Priority.NORMAL,
                LocalDate.of(2026, 7, 25).atTime(18, 0));
        ReflectionTestUtils.setField(task, "createdAt", LocalDate.of(2026, 7, 21).atStartOfDay());
        ReflectionTestUtils.setField(task, "updatedAt", LocalDate.of(2026, 7, 21).atStartOfDay());
        ReflectionTestUtils.setField(task, "status", Task.Status.IN_PROGRESS);
        return task;
    }

    @Test
    @DisplayName("존재하지 않는 ID 조회 시 404 Not Found를 반환한다")
    void nonExistentReportIdReturns404() throws Exception {
        mvc.perform(get("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly/999999")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_FOUND"));
    }

    @Test
    @DisplayName("비멤버가 조회 요청 시 404/403을 반환한다")
    void nonMemberCannotReadReport() throws Exception {
        AiWeeklyReportRevision rev = revisionRepository.save(new AiWeeklyReportRevision(
                paidTeamGroup.getId(), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27),
                "KO", 1, "FINALIZED", "OPENAI", "FP1", "{\"schemaVersion\":\"ai-weekly-report-snapshot.v1\",\"reportContext\":{\"groupId\":\"GROUP-1\",\"period\":{\"from\":\"2026-07-20\",\"toExclusive\":\"2026-07-27\",\"timezone\":\"Asia/Seoul\"},\"snapshotAt\":\"2026-07-27T00:00:00Z\",\"language\":\"KO\",\"promptVersion\":\"v7-2\"},\"metrics\":{\"periodTaskCount\":0,\"completionRatePercent\":0,\"onTimeRatePercent\":0,\"delayedCount\":0},\"comparison\":{\"status\":\"NO_BASELINE\"},\"workflow\":{\"requestedCount\":0,\"todoUnassignedCount\":0,\"todoAssignedCount\":0,\"inProgressCount\":0,\"onHoldCount\":0,\"completedCount\":0},\"members\":[],\"tasks\":[],\"calendarConstraints\":[],\"riskCandidates\":[]}", "{\"schemaVersion\":\"ai-weekly-report-analysis.v1\",\"analysisStatus\":\"NORMAL\",\"issues\":[],\"globalMissingEvidence\":[]}",
                "v7-2-prompt-001", "gpt-4o", 100, 200, LocalDateTime.now(), LocalDateTime.now()
        ));

        mvc.perform(get("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly/" + rev.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + nonMemberToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Legacy report ID 조회 시 410 Gone (AI_REPORT_LEGACY_REVISION)을 반환한다")
    void legacyReportIdReturns410() throws Exception {
        GroupMember leaderMem = memberRepository.findByGroupIdAndUserIdAndStatus(paidTeamGroup.getId(), leaderUser.getId(), GroupMember.Status.ACTIVE).orElseThrow();
        WeeklyReport legacyReport = legacyReportRepository.save(new WeeklyReport(
                paidTeamGroup, leaderMem, LocalDate.of(2026, 7, 13),
                LocalDate.of(2026, 7, 19), "ko", 1, WeeklyReport.TriggerType.USER, "{}",
                "v1", "v1", LocalDateTime.now()
        ));

        mvc.perform(get("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly/" + legacyReport.getId())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("AI_REPORT_LEGACY_REVISION"));
    }

    @Test
    @DisplayName("PDF 다운로드 시 Content-Type, Cache-Control, Content-Disposition 헤더가 정상 설정되며 Gateway는 호출되지 않는다")
    void downloadPdfHasRequiredHeadersAndZeroGatewayCall() throws Exception {
        AiWeeklyReportRevision rev = revisionRepository.save(new AiWeeklyReportRevision(
                paidTeamGroup.getId(), LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 27),
                "KO", 1, "FINALIZED", "OPENAI", "FP1", "{\"schemaVersion\":\"ai-weekly-report-snapshot.v1\",\"reportContext\":{\"groupId\":\"GROUP-1\",\"period\":{\"from\":\"2026-07-20\",\"toExclusive\":\"2026-07-27\",\"timezone\":\"Asia/Seoul\"},\"snapshotAt\":\"2026-07-27T00:00:00Z\",\"language\":\"KO\",\"promptVersion\":\"v7-2\"},\"metrics\":{\"periodTaskCount\":0,\"completionRatePercent\":0,\"onTimeRatePercent\":0,\"delayedCount\":0},\"comparison\":{\"status\":\"NO_BASELINE\"},\"workflow\":{\"requestedCount\":0,\"todoUnassignedCount\":0,\"todoAssignedCount\":0,\"inProgressCount\":0,\"onHoldCount\":0,\"completedCount\":0},\"members\":[],\"tasks\":[],\"calendarConstraints\":[],\"riskCandidates\":[]}", "{\"schemaVersion\":\"ai-weekly-report-analysis.v1\",\"analysisStatus\":\"NORMAL\",\"issues\":[],\"globalMissingEvidence\":[]}",
                "v7-2-prompt-001", "gpt-4o", 100, 200, LocalDateTime.now(), LocalDateTime.now()
        ));

        int preCallCount = gatewayCallCount.get();

        mvc.perform(get("/api/v1/groups/" + paidTeamGroup.getId() + "/reports/ai-weekly/" + rev.getId() + "/pdf")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + memberToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().stringValues(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.hasItem("private, no-store")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("ai-weekly-report-2026-07-20-r1.pdf")));

        assertThat(gatewayCallCount.get()).isEqualTo(preCallCount);
    }
}
