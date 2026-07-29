package com.teamproject.report;

import com.teamproject.group.domain.Group;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.group.domain.GroupRepository;
import com.teamproject.report.application.AiNarrativeGenerator;
import com.teamproject.report.application.ReportContracts.AiGenerationResult;
import com.teamproject.report.application.ReportContracts.ActionNarrativeItem;
import com.teamproject.report.application.ReportContracts.AiGenerationInput;
import com.teamproject.report.application.ReportContracts.GenerateWeeklyReport;
import com.teamproject.report.application.ReportContracts.Narrative;
import com.teamproject.report.application.ReportContracts.NarrativeItem;
import com.teamproject.report.application.ReportContracts.WeeklyReportView;
import com.teamproject.report.application.WeeklyReportModule;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.domain.WeeklyReport;
import com.teamproject.report.domain.WeeklyReportRepository;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import com.teamproject.user.domain.User;
import com.teamproject.user.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(properties = {
        "app.ai-report.prompt-version=v2",
        "app.ai-report.schema-version=v2"
})
class WeeklyReportModuleTest {
    @Autowired WeeklyReportModule reports;
    @Autowired UserRepository users;
    @Autowired GroupRepository groups;
    @Autowired GroupMemberRepository members;
    @Autowired TaskRepository tasks;
    @Autowired WeeklyReportRepository weeklyReports;
    @MockBean AiNarrativeGenerator narrativeGenerator;

    @Test
    void paidLeaderGeneratesOneCompletedWeeklyReportAndReusesIt() {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "외부 전송 금지 제목",
                "외부 전송 금지 설명", Task.Priority.HIGH, weekStart.plusDays(3).atTime(18, 0));
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);

        Narrative narrative = new Narrative(
                "주간 흐름 요약",
                "확정된 지표를 바탕으로 작성한 요약입니다.",
                List.of(new NarrativeItem("업무 흐름이 확인됩니다.", List.of("tasks.total"))),
                List.of(),
                List.of(new NarrativeItem("지연 위험을 계속 확인하세요.", List.of("tasks.highPriority"))),
                partialLimitations());
        when(narrativeGenerator.generate(any())).thenReturn(
                new AiGenerationResult(withOwner(narrative), "gpt-5.6-luna", 120, 80, 200));

        WeeklyReportView created = reports.generateWeeklyAiReport(
                new GenerateWeeklyReport(fixture.user().getId(), fixture.group().getId(), weekStart, "ko"));
        WeeklyReportView cached = reports.generateWeeklyAiReport(
                new GenerateWeeklyReport(fixture.user().getId(), fixture.group().getId(), weekStart, "ko"));

        assertThat(created.cached()).isFalse();
        assertThat(created.language()).isEqualTo("ko");
        assertThat(created.metrics().evidence()).containsEntry("tasks.total", 1);
        assertThat(cached.cached()).isTrue();
        assertThat(cached.reportId()).isEqualTo(created.reportId());
        assertThat(cached.language()).isEqualTo("ko");
        assertThat(created.operations().groupName()).isEqualTo(fixture.group().getName());
        assertThat(created.operations().members()).isNotEmpty();
        assertThat(created.operations().tasks())
                .extracting(value -> value.task().label())
                .containsExactly("외부 전송 금지 제목");
        assertThat(cached.operations()).isEqualTo(created.operations());
        verify(narrativeGenerator, times(1)).generate(any());
    }

    @Test
    void storesKoreanAndEnglishAsIndependentReportsForTheSameWeek() {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "언어별 리포트 대상",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);
        Narrative narrative = new Narrative("주간 흐름", "확정 지표 기반 서술",
                List.of(new NarrativeItem("업무 흐름이 있습니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        when(narrativeGenerator.generate(any())).thenReturn(
                new AiGenerationResult(withOwner(narrative), "gpt-5.6-luna", 1, 1, 2));

        WeeklyReportView korean = reports.generateWeeklyAiReport(
                new GenerateWeeklyReport(fixture.user().getId(), fixture.group().getId(),
                        weekStart, "ko"));
        WeeklyReportView english = reports.generateWeeklyAiReport(
                new GenerateWeeklyReport(fixture.user().getId(), fixture.group().getId(),
                        weekStart, "en"));

        assertThat(english.reportId()).isNotEqualTo(korean.reportId());
        assertThat(korean.language()).isEqualTo("ko");
        assertThat(english.language()).isEqualTo("en");
        assertThat(weeklyReports
                .findByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageAndRevision(
                        fixture.group().getId(), WeeklyReport.Type.WEEKLY_AI,
                        weekStart, weekStart.plusDays(6), "ko", 1))
                .isPresent();
        assertThat(weeklyReports
                .findByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageAndRevision(
                        fixture.group().getId(), WeeklyReport.Type.WEEKLY_AI,
                        weekStart, weekStart.plusDays(6), "en", 1))
                .isPresent();
    }

    @Test
    void reclaimsExpiredGeneratingLeaseAndIncrementsAttemptCount() {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "lease 회수 대상",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);

        Instant startedAt = Instant.now();
        WeeklyReport stale = new WeeklyReport(fixture.group(), fixture.leader(),
                weekStart, weekStart.plusDays(6), "ko", 1, WeeklyReport.TriggerType.USER,
                "{}", "v2", "v2", LocalDateTime.now());
        stale.start("{}", "v2", "v2", startedAt, LocalDateTime.now());
        ReflectionTestUtils.setField(stale, "generationStartedAt",
                Instant.now().minusSeconds(121));
        stale = weeklyReports.saveAndFlush(stale);
        Long staleId = stale.getId();
        assertThat(stale.getAttemptCount()).isEqualTo(1);

        Narrative narrative = new Narrative("회수 완료", "정성 서술을 완료했습니다.",
                List.of(new NarrativeItem("업무 흐름이 있습니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        when(narrativeGenerator.generate(any())).thenReturn(
                new AiGenerationResult(withOwner(narrative), "gpt-5.6-luna", 1, 1, 2));

        WeeklyReportView completed = reports.generateWeeklyAiReport(
                new GenerateWeeklyReport(fixture.user().getId(), fixture.group().getId(),
                        weekStart, "ko"));

        WeeklyReport reclaimed = weeklyReports.findById(staleId).orElseThrow();
        assertThat(completed.reportId()).isEqualTo(staleId);
        assertThat(reclaimed.getStatus()).isEqualTo(WeeklyReport.Status.COMPLETED);
        assertThat(reclaimed.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void reclaimedAttemptCannotBeOverwrittenByTheExpiredWorker() throws Exception {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "lease 경합 대상",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);

        Narrative expiredNarrative = new Narrative("만료된 생성", "오래된 작업의 결과입니다.",
                List.of(new NarrativeItem("업무 흐름이 있습니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        Narrative reclaimedNarrative = new Narrative("회수된 생성", "새 작업의 결과입니다.",
                List.of(new NarrativeItem("업무 흐름이 있습니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        CountDownLatch expiredWorkerStarted = new CountDownLatch(1);
        CountDownLatch releaseExpiredWorker = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        doAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) {
                expiredWorkerStarted.countDown();
                if (!releaseExpiredWorker.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("expired worker release timed out");
                }
                return new AiGenerationResult(
                        withOwner(expiredNarrative), "expired-model", 1, 1, 2);
            }
            return new AiGenerationResult(
                    withOwner(reclaimedNarrative), "reclaimed-model", 2, 2, 4);
        }).when(narrativeGenerator).generate(any());
        GenerateWeeklyReport command = new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), weekStart, "ko");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<WeeklyReportView> expiredWorker =
                    executor.submit(() -> reports.generateWeeklyAiReport(command));
            assertThat(expiredWorkerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            WeeklyReport generating = weeklyReports
                    .findByGroupIdAndTypeAndPeriodStartAndPeriodEndAndLanguageAndRevision(
                            fixture.group().getId(), WeeklyReport.Type.WEEKLY_AI,
                            weekStart, weekStart.plusDays(6), "ko", 1)
                    .orElseThrow();
            ReflectionTestUtils.setField(generating, "generationStartedAt",
                    Instant.now().minusSeconds(121));
            weeklyReports.saveAndFlush(generating);

            WeeklyReportView reclaimed = reports.generateWeeklyAiReport(command);
            releaseExpiredWorker.countDown();
            WeeklyReportView expiredResult = expiredWorker.get(5, TimeUnit.SECONDS);
            WeeklyReport stored = weeklyReports.findById(reclaimed.reportId()).orElseThrow();

            assertThat(reclaimed.analysis().headline()).isEqualTo("회수된 생성");
            assertThat(expiredResult.analysis().headline()).isEqualTo("회수된 생성");
            assertThat(expiredResult.cached()).isTrue();
            assertThat(stored.getModel()).isEqualTo("reclaimed-model");
            assertThat(stored.getAttemptCount()).isEqualTo(2);
        } finally {
            releaseExpiredWorker.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void snapshotUsesInclusiveMondayThroughSundayGroupLocalDates() {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task sunday = new Task(fixture.group(), fixture.leader(), "일요일 경계",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(sunday, "createdAt", weekStart.plusDays(6).atTime(23, 59));
        ReflectionTestUtils.setField(sunday, "updatedAt", weekStart.plusDays(6).atTime(23, 59));
        tasks.save(sunday);
        Task nextMonday = new Task(fixture.group(), fixture.leader(), "다음 주 경계",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(nextMonday, "createdAt", weekStart.plusDays(7).atStartOfDay());
        ReflectionTestUtils.setField(nextMonday, "updatedAt", weekStart.plusDays(7).atStartOfDay());
        tasks.save(nextMonday);
        Narrative narrative = new Narrative("주간 경계", "그룹 로컬 날짜 기준입니다.",
                List.of(new NarrativeItem("주간 업무가 있습니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        when(narrativeGenerator.generate(any())).thenReturn(
                new AiGenerationResult(withOwner(narrative), "gpt-5.6-luna", 1, 1, 2));

        WeeklyReportView result = reports.generateWeeklyAiReport(new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), weekStart, "ko"));

        assertThat(result.periodEnd()).isEqualTo(weekStart.plusDays(6));
        assertThat(result.metrics().totalTasks()).isEqualTo(1);
        assertThat(result.metrics().daily()).hasSize(7);
    }

    @Test
    void failedGenerationCanRetryButInvalidEvidenceCannotBeStored() {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "비식별 집계 대상",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);

        Narrative invalid = new Narrative("요약", "설명",
                List.of(new NarrativeItem("근거 오류", List.of())),
                List.of(), List.of(), List.of());
        Narrative valid = new Narrative("재시도 성공", "확정 지표만 사용",
                List.of(new NarrativeItem("업무가 있습니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        when(narrativeGenerator.generate(any()))
                .thenThrow(new ApplicationException("AI_REPORT_PROVIDER_UNAVAILABLE",
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "일시 장애"))
                .thenReturn(new AiGenerationResult(
                        withOwner(invalid), "gpt-5.6-luna", 1, 1, 2))
                .thenReturn(new AiGenerationResult(
                        withOwner(valid), "gpt-5.6-luna", 1, 1, 2));

        GenerateWeeklyReport command = new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), weekStart, "ko");
        GenerateWeeklyReport retryInDifferentLanguage = new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), weekStart, "en");
        assertThatThrownBy(() -> reports.generateWeeklyAiReport(command))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_REPORT_PROVIDER_UNAVAILABLE"));
        assertThatThrownBy(() -> reports.generateWeeklyAiReport(retryInDifferentLanguage))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_REPORT_EVIDENCE_INVALID"));

        WeeklyReportView completed = reports.generateWeeklyAiReport(retryInDifferentLanguage);

        assertThat(completed.analysis().headline()).isEqualTo("재시도 성공");
        assertThat(completed.language()).isEqualTo("en");
        ArgumentCaptor<AiGenerationInput> inputs = ArgumentCaptor.forClass(AiGenerationInput.class);
        verify(narrativeGenerator, times(3)).generate(inputs.capture());
        assertThat(inputs.getAllValues()).extracting(AiGenerationInput::language)
                .containsExactly("ko", "en", "en");
    }

    @ParameterizedTest
    @ValueSource(strings = {"업무 1건 요약", "완료율 50% 요약", "2026-07-20 기준 요약"})
    void numericNarrativeIsRejectedAndItsUsageIsStoredForTheFailedAttempt(String numericHeadline) {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "비식별 집계 대상",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);

        Narrative numeric = new Narrative(numericHeadline, "확정 지표를 인용한 설명입니다.",
                List.of(new NarrativeItem("업무 흐름이 확인됩니다.", List.of("tasks.total"))),
                List.of(), List.of(), List.of());
        when(narrativeGenerator.generate(any())).thenReturn(
                new AiGenerationResult(
                        withOwner(numeric), "gpt-5.6-luna", 608, 94, 702));

        GenerateWeeklyReport command = new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), weekStart, "ko");
        assertThatThrownBy(() -> reports.generateWeeklyAiReport(command))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_REPORT_NUMERIC_TEXT_INVALID"));

        WeeklyReport failed = weeklyReports.findByGroupIdAndTypeAndPeriodStartAndPeriodEnd(
                fixture.group().getId(), WeeklyReport.Type.WEEKLY_AI,
                weekStart, weekStart.plusDays(6)).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(WeeklyReport.Status.FAILED);
        assertThat(failed.getFailureCode()).isEqualTo("AI_REPORT_NUMERIC_TEXT_INVALID");
        assertThat(failed.getModel()).isEqualTo("gpt-5.6-luna");
        assertThat(failed.getInputTokens()).isEqualTo(608);
        assertThat(failed.getOutputTokens()).isEqualTo(94);
        assertThat(failed.getTotalTokens()).isEqualTo(702);
    }

    @Test
    void retryKeepsOnlyTheLatestAttemptMetadataAndCompletesWithV2Contract() {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "재시도 집계 대상",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);

        Narrative numeric = new Narrative("업무 1건 요약", "숫자 계약 위반",
                List.of(new NarrativeItem("업무 흐름이 확인됩니다.", List.of("tasks.total"))),
                List.of(), List.of(), List.of());
        Narrative valid = new Narrative("재시도 완료", "정성 서술만 저장했습니다.",
                List.of(new NarrativeItem("업무 흐름이 확인됩니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        when(narrativeGenerator.generate(any()))
                .thenReturn(new AiGenerationResult(
                        withOwner(numeric), "first-model", 608, 94, 702))
                .thenThrow(new ApplicationException("AI_REPORT_PROVIDER_UNAVAILABLE",
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "일시 장애"))
                .thenReturn(new AiGenerationResult(
                        withOwner(valid), "second-model", 420, 80, 500));

        GenerateWeeklyReport command = new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), weekStart, "ko");
        assertThatThrownBy(() -> reports.generateWeeklyAiReport(command))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_REPORT_NUMERIC_TEXT_INVALID"));
        assertThat(storedReport(fixture, weekStart).getModel()).isEqualTo("first-model");

        assertThatThrownBy(() -> reports.generateWeeklyAiReport(command))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo("AI_REPORT_PROVIDER_UNAVAILABLE"));
        WeeklyReport providerFailed = storedReport(fixture, weekStart);
        assertThat(providerFailed.getModel()).isNull();
        assertThat(providerFailed.getInputTokens()).isNull();
        assertThat(providerFailed.getOutputTokens()).isNull();
        assertThat(providerFailed.getTotalTokens()).isNull();

        WeeklyReportView completed = reports.generateWeeklyAiReport(command);

        assertThat(completed.status()).isEqualTo("COMPLETED");
        WeeklyReport stored = storedReport(fixture, weekStart);
        assertThat(stored.getModel()).isEqualTo("second-model");
        assertThat(stored.getInputTokens()).isEqualTo(420);
        assertThat(stored.getOutputTokens()).isEqualTo(80);
        assertThat(stored.getTotalTokens()).isEqualTo(500);
        assertThat(stored.getPromptVersion()).isEqualTo("v5");
        assertThat(stored.getSchemaVersion()).isEqualTo("v4");
    }

    @Test
    void concurrentFailedRetriesAllowOnlyOneNewGeneration() throws Exception {
        Fixture fixture = paidTeam();
        LocalDate weekStart = LocalDate.now().with(
                TemporalAdjusters.previous(DayOfWeek.MONDAY)).minusWeeks(1);
        Task task = new Task(fixture.group(), fixture.leader(), "동시 요청 집계",
                null, Task.Priority.NORMAL, null);
        ReflectionTestUtils.setField(task, "createdAt", weekStart.plusDays(1).atTime(9, 0));
        ReflectionTestUtils.setField(task, "updatedAt", weekStart.plusDays(1).atTime(9, 0));
        tasks.save(task);
        Narrative narrative = new Narrative("동시 생성 완료", "첫 요청의 결과입니다.",
                List.of(new NarrativeItem("업무가 있습니다.", List.of("tasks.total"))),
                List.of(), List.of(), partialLimitations());
        CountDownLatch generationStarted = new CountDownLatch(1);
        CountDownLatch releaseGeneration = new CountDownLatch(1);
        GenerateWeeklyReport command = new GenerateWeeklyReport(
                fixture.user().getId(), fixture.group().getId(), weekStart, "ko");
        when(narrativeGenerator.generate(any())).thenThrow(new ApplicationException(
                "AI_REPORT_PROVIDER_UNAVAILABLE",
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "일시 장애"));
        assertThatThrownBy(() -> reports.generateWeeklyAiReport(command))
                .isInstanceOf(ApplicationException.class);

        doAnswer(invocation -> {
            generationStarted.countDown();
            if (!releaseGeneration.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test generation release timed out");
            }
            return new AiGenerationResult(
                    withOwner(narrative), "gpt-5.6-luna", 1, 1, 2);
        }).when(narrativeGenerator).generate(any());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<WeeklyReportView> first = executor.submit(() -> reports.generateWeeklyAiReport(command));
            assertThat(generationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<WeeklyReportView> second = executor.submit(() -> reports.generateWeeklyAiReport(command));

            ExecutionException collision = assertThrows(ExecutionException.class,
                    () -> second.get(5, TimeUnit.SECONDS));
            assertThat(collision.getCause()).isInstanceOfSatisfying(ApplicationException.class,
                    exception -> assertThat(exception.code()).isEqualTo("AI_REPORT_GENERATING"));
            releaseGeneration.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo("COMPLETED");
        } finally {
            releaseGeneration.countDown();
            executor.shutdownNow();
        }
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

    private Fixture paidTeam() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user = users.save(new User("report_" + suffix, "report_" + suffix + "@example.com",
                "hash", "리포트 팀장", true));
        Group group = Group.team("AI 리포트 팀", null, "Asia/Seoul", user);
        ReflectionTestUtils.setField(group, "membershipPlan", Group.MembershipPlan.PAID);
        group = groups.save(group);
        GroupMember leader = members.save(GroupMember.leader(group, user));
        return new Fixture(user, group, leader);
    }

    private WeeklyReport storedReport(Fixture fixture, LocalDate weekStart) {
        return weeklyReports.findByGroupIdAndTypeAndPeriodStartAndPeriodEnd(
                fixture.group().getId(), WeeklyReport.Type.WEEKLY_AI,
                weekStart, weekStart.plusDays(6)).orElseThrow();
    }

    private List<NarrativeItem> partialLimitations() {
        return List.of(new NarrativeItem(
                "활동 이력 수집 전 데이터가 포함될 수 있습니다.",
                List.of("coverage.partial")));
    }

    private record Fixture(User user, Group group, GroupMember leader) {}
}
