package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.report.application.AiWeeklyReportAnalysisValidator;
import com.teamproject.report.application.AiWeeklyReportFallbackFactory;
import com.teamproject.report.application.AiWeeklyReportGenerationService;
import com.teamproject.report.application.AiWeeklyReportGenerationService.GenerateCommand;
import com.teamproject.report.application.AiWeeklyReportGenerationService.GenerationResult;
import com.teamproject.report.application.AiWeeklyReportPolicyEngine;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.*;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import com.teamproject.report.application.port.AiWeeklyReportGateway;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.domain.AiWeeklyReportRevisionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.teamproject.common.exception.ApplicationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.withSettings;

@DataJpaTest
class AiWeeklyReportGenerationServiceTest {

    @Autowired
    private AiWeeklyReportRevisionRepository revisionRepository;

    private final ObjectMapper json = new ObjectMapper();
    private final AiWeeklyReportPolicyEngine policyEngine = new AiWeeklyReportPolicyEngine();
    private final AiWeeklyReportAnalysisValidator validator = new AiWeeklyReportAnalysisValidator();
    private final AiWeeklyReportFallbackFactory fallbackFactory = new AiWeeklyReportFallbackFactory();

    private AiWeeklyReportSnapshotV1 initialRawSnapshot;
    private GenerateCommand baseCommand;

    @BeforeEach
    void setUp() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/ai/ai-weekly-report-snapshot-v1.example.json");
        initialRawSnapshot = json.readValue(stream, AiWeeklyReportSnapshotV1.class);

        baseCommand = new GenerateCommand(
                7L,
                LocalDate.of(2026, 7, 20),
                LocalDate.of(2026, 7, 27),
                "KO",
                false,
                "v7-2-prompt-001",
                "gpt-4o"
        );
    }

    @Test
    @DisplayName("Gateway 정상 성공 시 OPENAI 모드로 FINALIZED revision이 저장된다")
    void normalOpenAiSuccess() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway fakeGateway = snapshot -> {
            callCount.incrementAndGet();
            return fallbackFactory.create(snapshot); // valid analysis
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, fakeGateway, validator, fallbackFactory, revisionRepository, json
        );

        AiWeeklyReportRevision revision = service.generate(initialRawSnapshot, baseCommand);

        assertThat(revision.getRevision()).isEqualTo(1);
        assertThat(revision.getStatus()).isEqualTo("FINALIZED");
        assertThat(revision.getAnalysisMode()).isEqualTo("OPENAI");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Gateway 예외 발생 시 SERVER_FALLBACK 모드로 전환되어 저장된다")
    void gatewayFailureTriggersFallback() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway failingGateway = snapshot -> {
            callCount.incrementAndGet();
            throw new RuntimeException("Gateway network failure");
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, failingGateway, validator, fallbackFactory, revisionRepository, json
        );

        AiWeeklyReportRevision revision = service.generate(initialRawSnapshot, baseCommand);

        assertThat(revision.getRevision()).isEqualTo(1);
        assertThat(revision.getStatus()).isEqualTo("FINALIZED");
        assertThat(revision.getAnalysisMode()).isEqualTo("SERVER_FALLBACK");
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("Gateway 결과가 Validator를 실패하면 SERVER_FALLBACK 모드로 전환된다")
    void validatorFailureTriggersFallback() {
        AiWeeklyReportGateway invalidOutputGateway = snapshot -> new AiWeeklyReportAnalysisV1(
                "ai-weekly-report-analysis.v1",
                AnalysisStatus.NORMAL,
                new ExecutiveJudgment("H", "I", List.of(), List.of("NON_EXISTENT_TASK"), Confidence.HIGH, List.of()),
                Achievement.none(),
                List.of(),
                List.of()
        );

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, invalidOutputGateway, validator, fallbackFactory, revisionRepository, json
        );

        AiWeeklyReportRevision revision = service.generate(initialRawSnapshot, baseCommand);

        assertThat(revision.getStatus()).isEqualTo("FINALIZED");
        assertThat(revision.getAnalysisMode()).isEqualTo("SERVER_FALLBACK");
    }

    @Test
    @DisplayName("동일 source에 regenerate=false일 때 Gateway를 0회 호출하고 기존 revision을 반환한다")
    void deduplicationReusesExistingRevisionWithoutGatewayCall() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway gateway = snapshot -> {
            callCount.incrementAndGet();
            return fallbackFactory.create(snapshot);
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, gateway, validator, fallbackFactory, revisionRepository, json
        );

        // First creation
        AiWeeklyReportRevision rev1 = service.generate(initialRawSnapshot, baseCommand);
        assertThat(callCount.get()).isEqualTo(1);

        // Second creation with same input
        AiWeeklyReportRevision rev2 = service.generate(initialRawSnapshot, baseCommand);

        assertThat(callCount.get()).isEqualTo(1); // Not called again!
        assertThat(rev2.getId()).isEqualTo(rev1.getId());
        assertThat(rev2.getRevision()).isEqualTo(1);
    }

    @Test
    @DisplayName("regenerate=true 시 새 revision이 생성되며 Gateway가 다시 호출된다")
    void regenerateCreatesNewRevision() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway gateway = snapshot -> {
            callCount.incrementAndGet();
            return fallbackFactory.create(snapshot);
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, gateway, validator, fallbackFactory, revisionRepository, json
        );

        // First creation
        AiWeeklyReportRevision rev1 = service.generate(initialRawSnapshot, baseCommand);
        assertThat(rev1.getRevision()).isEqualTo(1);

        // Regenerate command
        GenerateCommand regenCommand = new GenerateCommand(
                baseCommand.groupId(),
                baseCommand.periodFrom(),
                baseCommand.periodToExclusive(),
                baseCommand.language(),
                true, // regenerate=true
                baseCommand.promptVersion(),
                baseCommand.model()
        );

        AiWeeklyReportRevision rev2 = service.generate(initialRawSnapshot, regenCommand);

        assertThat(callCount.get()).isEqualTo(2);
        assertThat(rev2.getRevision()).isEqualTo(2);
        assertThat(rev2.getId()).isNotEqualTo(rev1.getId());
    }

    @Test
    @DisplayName("Gateway에 전달되는 Snapshot JSON에 사용자 실명 및 원문이 포함되지 않는다")
    void gatewayReceivesPrivacyProtectedSnapshot() {
        AtomicInteger callCount = new AtomicInteger(0);
        AiWeeklyReportGateway gateway = snapshot -> {
            callCount.incrementAndGet();
            // Verify snapshot tasks do not contain raw titles/emails/names
            for (SnapshotTask task : snapshot.tasks()) {
                assertThat(task.safeLabel()).doesNotContain("@");
                assertThat(task.safeLabel()).doesNotContain("김민준");
            }
            return fallbackFactory.create(snapshot);
        };

        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, gateway, validator, fallbackFactory, revisionRepository, json
        );

        service.generate(initialRawSnapshot, baseCommand);
        assertThat(callCount.get()).isEqualTo(1);
    }

    /**
     * revision 번호는 잠금 없이 계산한다. 두 요청이 같은 번호를 잡으면 뒤늦은 쪽이 유니크
     * 제약에 걸리는데, 그 시점에는 OpenAI 호출이 이미 끝나 돈이 나간 뒤다. 예전에는 그대로
     * 500이 났다(DataIntegrityViolationException 핸들러도 없다). 번호를 다시 세어 저장한다.
     */
    @Test
    @DisplayName("revision 번호가 충돌하면 다시 세어 저장한다")
    void retriesWhenTheRevisionNumberCollides() {
        AiWeeklyReportRevisionRepository flaky = collidingOnceRepository();
        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, fallbackFactory::create, validator, fallbackFactory, flaky, json);

        AiWeeklyReportRevision revision = service.generate(initialRawSnapshot, baseCommand);

        assertThat(revision.getRevision()).isEqualTo(1);
        assertThat(revisionRepository.findAll()).hasSize(1);
    }

    /** 재시도를 다 써도 실패하면 500이 아니라 계약을 지키는 409를 준다. */
    @Test
    @DisplayName("충돌이 계속되면 500 대신 409 코드로 알린다")
    void reportsAConflictInsteadOfCrashing() {
        AiWeeklyReportRevisionRepository alwaysColliding = alwaysCollidingRepository();
        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, fallbackFactory::create, validator, fallbackFactory, alwaysColliding, json);

        assertThatThrownBy(() -> service.generate(initialRawSnapshot, baseCommand))
                .isInstanceOf(ApplicationException.class)
                .satisfies(thrown -> {
                    ApplicationException e = (ApplicationException) thrown;
                    assertThat(e.code()).isEqualTo("AI_REPORT_CONCURRENT_GENERATION");
                    assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    /** 첫 저장만 제약 위반으로 튕기고 두 번째는 실제 저장으로 넘긴다. */
    private AiWeeklyReportRevisionRepository collidingOnceRepository() {
        AtomicInteger saves = new AtomicInteger(0);
        AiWeeklyReportRevisionRepository spy = mock(AiWeeklyReportRevisionRepository.class,
                withSettings().defaultAnswer(invocation -> invocation.getMethod()
                        .invoke(revisionRepository, invocation.getArguments())));
        doAnswer(invocation -> {
            if (saves.getAndIncrement() == 0) {
                throw new DataIntegrityViolationException("duplicate revision");
            }
            return revisionRepository.saveAndFlush(invocation.getArgument(0));
        }).when(spy).saveAndFlush(any());
        return spy;
    }

    private AiWeeklyReportRevisionRepository alwaysCollidingRepository() {
        AiWeeklyReportRevisionRepository spy = mock(AiWeeklyReportRevisionRepository.class,
                withSettings().defaultAnswer(invocation -> invocation.getMethod()
                        .invoke(revisionRepository, invocation.getArguments())));
        doThrow(new DataIntegrityViolationException("duplicate revision")).when(spy).saveAndFlush(any());
        return spy;
    }

    /**
     * source_fingerprint를 계산·저장·색인까지 해 두고 아무도 읽지 않았다. 저장본을 돌려줄 때
     * 그 뒤 업무가 바뀌었는지 서버는 알 수 있는데 알려 주지 않았고, 사용자는 유료 재생성을
     * 감으로 결정했다. 지문은 중복 검사 직전에 이미 계산돼 있어 비교 비용이 사실상 없다.
     */
    @Test
    @DisplayName("저장본을 재사용할 때 그 뒤 데이터가 바뀌었는지 알려 준다")
    void tellsWhetherTheSourceChangedSinceTheStoredRevision() {
        AiWeeklyReportGenerationService service = new AiWeeklyReportGenerationService(
                policyEngine, fallbackFactory::create, validator, fallbackFactory, revisionRepository, json);

        GenerationResult created = service.generateResult(initialRawSnapshot, baseCommand);
        assertThat(created.createdNew()).isTrue();
        assertThat(created.sourceChanged()).isFalse();

        // 같은 snapshot을 다시 요청하면 데이터가 그대로다.
        GenerationResult sameData = service.generateResult(initialRawSnapshot, baseCommand);
        assertThat(sameData.createdNew()).isFalse();
        assertThat(sameData.sourceChanged()).isFalse();

        // 업무 하나를 덜어내면 지문이 달라진다.
        AiWeeklyReportSnapshotV1 changed = new AiWeeklyReportSnapshotV1(
                initialRawSnapshot.schemaVersion(), initialRawSnapshot.reportContext(),
                initialRawSnapshot.metrics(), initialRawSnapshot.comparison(),
                initialRawSnapshot.workflow(), initialRawSnapshot.members(),
                initialRawSnapshot.tasks().subList(0, initialRawSnapshot.tasks().size() - 1),
                initialRawSnapshot.calendarConstraints(), initialRawSnapshot.riskCandidates());

        GenerationResult afterChange = service.generateResult(changed, baseCommand);
        assertThat(afterChange.createdNew()).isFalse();
        assertThat(afterChange.sourceChanged()).isTrue();
    }
}
