package com.teamproject.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.NarrativeContract;
import com.teamproject.report.application.ReportContracts.ActionNarrativeItem;
import com.teamproject.report.application.ReportContracts.AiReportContext;
import com.teamproject.report.application.ReportContracts.ChecklistMetrics;
import com.teamproject.report.application.ReportContracts.ComparisonMetrics;
import com.teamproject.report.application.ReportContracts.EvidenceValue;
import com.teamproject.report.application.ReportContracts.HistoryCoverage;
import com.teamproject.report.application.ReportContracts.HistoryCoverageStatus;
import com.teamproject.report.application.ReportContracts.MemberMetric;
import com.teamproject.report.application.ReportContracts.MetricsSnapshot;
import com.teamproject.report.application.ReportContracts.Narrative;
import com.teamproject.report.application.ReportContracts.NarrativeItem;
import com.teamproject.report.application.ReportContracts.ReferenceIndex;
import com.teamproject.report.application.ReportContracts.ReportSnapshot;
import com.teamproject.report.application.ReportContracts.RiskNarrativeItem;
import com.teamproject.report.application.ReportContracts.StatusMetrics;
import com.teamproject.report.application.ReportContracts.TaskContext;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NarrativeContractTest {
    private final NarrativeContract contract =
            new NarrativeContract(new ObjectMapper().findAndRegisterModules());

    @Test
    void continuesToReadStoredV2Narrative() {
        String storedJson = """
                {
                  "headline":"주간 흐름",
                  "executiveSummary":"확정 지표 기반 서술",
                  "highlights":[{"text":"업무 흐름이 있습니다.","evidenceKeys":["tasks.total"]}],
                  "risks":[],
                  "nextWeekActions":[],
                  "dataLimitations":[]
                }
                """;

        Narrative decoded = contract.readNarrative("v2", storedJson);

        assertThat(decoded.headline()).isEqualTo("주간 흐름");
        assertThat(decoded.highlights()).extracting(NarrativeItem::text)
                .containsExactly("업무 흐름이 있습니다.");
    }

    @Test
    void continuesToReadStoredV3NarrativeWithoutActionOwner() {
        String storedJson = """
                {
                  "headlineTemplate": "주간 흐름",
                  "summary": {
                    "textTemplate": "확정 지표 기반 서술",
                    "evidenceKeys": ["tasks.total"],
                    "taskRefs": [],
                    "objectiveRefs": []
                  },
                  "changes": [],
                  "achievements": [],
                  "risks": [],
                  "topActions": [{
                    "priority": 1,
                    "actionTemplate": "실행 계획을 점검하세요.",
                    "reasonTemplate": "확정된 업무 흐름을 반영해야 합니다.",
                    "evidenceKeys": ["tasks.total"],
                    "taskRefs": [],
                    "objectiveRefs": []
                  }],
                  "leaderDecisions": [],
                  "limitations": []
                }
                """;

        Narrative decoded = contract.readNarrative("v3", storedJson);

        assertThat(decoded.topActions().getFirst().ownerRef()).isNull();
    }

    // 신규 슬롯이 추가되면 구버전 행에는 그 키가 없다. 저장본은 계속 읽혀야 한다.
    @Test
    void continuesToReadStoredNarrativeWithMissingSectionArrays() {
        String storedJson = """
                {
                  "headlineTemplate": "주간 흐름",
                  "summary": {
                    "textTemplate": "확정 지표 기반 서술",
                    "evidenceKeys": ["tasks.total"],
                    "taskRefs": [],
                    "objectiveRefs": []
                  },
                  "topActions": [{
                    "priority": 1,
                    "actionTemplate": "실행 계획을 점검하세요.",
                    "reasonTemplate": "확정된 업무 흐름을 반영해야 합니다.",
                    "ownerRef": "MEMBER-01",
                    "evidenceKeys": ["tasks.total"],
                    "taskRefs": [],
                    "objectiveRefs": []
                  }],
                  "limitations": []
                }
                """;

        Narrative decoded = contract.readNarrative("v4", storedJson);

        assertThat(decoded.changes()).isEmpty();
        assertThat(decoded.achievements()).isEmpty();
        assertThat(decoded.risks()).isEmpty();
        assertThat(decoded.leaderDecisions()).isEmpty();
        assertThat(decoded.topActions().getFirst().objectiveRefs()).isEmpty();
    }

    // 반대 방향: 정규화가 생성 응답의 슬롯 누락까지 덮어주면 안 된다.
    @Test
    void rejectsGeneratedNarrativeMissingSectionArray() {
        var generated = new NarrativeContract.GeneratedNarrative();
        generated.headlineTemplate = "실행 우선순위를 검토하세요";
        generated.summary = generatedItem("확정 업무 흐름을 점검했습니다.");
        generated.achievements = List.of();
        generated.risks = List.of();
        generated.leaderDecisions = List.of();
        generated.limitations = List.of();
        generated.topActions = List.of(
                generatedAction(NarrativeContract.GeneratedPriority.P1, "첫 번째"));
        generated.changes = null;

        assertThatThrownBy(() -> contract.fromGenerated(generated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("changes");
    }

    @Test
    void restoresMemberReferencesWhenReadingStoredV3Context() throws Exception {
        ReportSnapshot snapshot = completedAndDelayedTaskSnapshot();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        var stored = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(
                contract.writeAiContext(snapshot.aiContext()));
        stored.remove("memberRefs");

        AiReportContext decoded = contract.readAiContext("v3", stored.toString());

        assertThat(decoded.memberRefs()).containsExactly("MEMBER-01");
    }

    // 지침은 String.format으로 만들어지므로 본문의 %가 런타임에만 터진다. 두 언어 모두 확인한다.
    @Test
    void buildsInstructionsForBothLanguagesWithoutFormatErrors() {
        assertThat(contract.instructions("ko")).contains("Korean");
        assertThat(contract.instructions("en")).contains("English");
        // %는 %%로 이스케이프해야 렌더된 지침에 한 글자로 남는다.
        assertThat(contract.instructions("ko")).contains("42%").doesNotContain("%%");
    }

    @Test
    void renumbersGeneratedActionPrioritiesByDeclaredOrder() {
        var generated = new NarrativeContract.GeneratedNarrative();
        generated.headlineTemplate = "실행 우선순위를 검토하세요";
        generated.summary = generatedItem("확정 업무 흐름을 점검했습니다.");
        generated.changes = List.of();
        generated.achievements = List.of();
        generated.risks = List.of();
        generated.leaderDecisions = List.of();
        generated.limitations = List.of();
        generated.topActions = List.of(
                generatedAction(NarrativeContract.GeneratedPriority.P3, "세 번째"),
                generatedAction(NarrativeContract.GeneratedPriority.P1, "첫 번째"),
                generatedAction(NarrativeContract.GeneratedPriority.P3, "중복 세 번째"));

        Narrative narrative = contract.fromGenerated(generated);

        assertThat(narrative.topActions()).extracting(ActionNarrativeItem::priority)
                .containsExactly(1, 2, 3);
        assertThat(narrative.topActions()).extracting(ActionNarrativeItem::actionTemplate)
                .containsExactly("첫 번째", "세 번째", "중복 세 번째");
    }

    private NarrativeContract.GeneratedNarrativeItem generatedItem(String text) {
        var item = new NarrativeContract.GeneratedNarrativeItem();
        item.textTemplate = text;
        item.evidenceKeys = List.of("tasks.total");
        item.taskRefs = List.of();
        item.objectiveRefs = List.of();
        return item;
    }

    private NarrativeContract.GeneratedActionItem generatedAction(
            NarrativeContract.GeneratedPriority priority, String action) {
        var item = new NarrativeContract.GeneratedActionItem();
        item.priority = priority;
        item.actionTemplate = action;
        item.reasonTemplate = "근거를 확인해야 합니다.";
        item.ownerRef = "MEMBER-01";
        item.evidenceKeys = List.of("tasks.total");
        item.taskRefs = List.of();
        item.objectiveRefs = List.of();
        return item;
    }

    @Test
    void rejectsUnknownStoredSchemaVersionExplicitly() {
        assertThatThrownBy(() -> contract.readNarrative("v999", "{}"))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_SCHEMA_UNSUPPORTED"));
    }

    @Test
    void requiresPartialCoverageLimitationEvidence() {
        MetricsSnapshot metrics = new MetricsSnapshot(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 1,
                new StatusMetrics(1, 0, 0, 0, 0, 0, 0, 0),
                0, null, null, List.of(),
                List.of(new MemberMetric("MEMBER-01", 1, 1, 0, 0, null)),
                List.of(),
                Map.of("tasks.total", 1, "coverage.partial", 1));
        Narrative narrative = validNarrative();

        assertThatThrownBy(() -> contract.validateGenerated(metrics, narrative))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_EVIDENCE_INVALID"));
    }

    @Test
    void rejectsUnknownEvidencePlaceholder() {
        Narrative narrative = validNarrative();
        Narrative invalid = new Narrative(
                narrative.headlineTemplate(),
                new NarrativeItem(
                        "확정 업무는 {{tasks.unknown}}입니다.",
                        List.of("tasks.unknown")),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                narrative.topActions(), narrative.leaderDecisions(), narrative.limitations());

        assertContractError(invalid, "AI_REPORT_EVIDENCE_INVALID");
    }

    @Test
    void rejectsUnknownTaskAlias() {
        Narrative narrative = validNarrative();
        ActionNarrativeItem action = narrative.topActions().getFirst();
        Narrative invalid = new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                List.of(new ActionNarrativeItem(
                        action.priority(), action.actionTemplate(), action.reasonTemplate(),
                        action.ownerRef(), action.evidenceKeys(), List.of("TASK-99"),
                        action.objectiveRefs())),
                narrative.leaderDecisions(), narrative.limitations());

        assertContractError(invalid, "AI_REPORT_EVIDENCE_INVALID");
    }

    @Test
    void rejectsCompletedTaskAsDelayedEvidenceReference() {
        Narrative narrative = validNarrative();
        Narrative invalid = new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(),
                List.of(new RiskNarrativeItem(
                        "HIGH",
                        "지연 업무를 우선 점검해야 합니다.",
                        List.of("tasks.delayed"),
                        List.of("TASK-01"),
                        List.of())),
                narrative.topActions(), narrative.leaderDecisions(), narrative.limitations());

        assertThatThrownBy(() -> contract.validateGenerated(
                completedAndDelayedTaskSnapshot(), invalid))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_EVIDENCE_INVALID"));
    }

    @Test
    void removesCompletedTaskFromStoredDelayedEvidenceReference() {
        Narrative narrative = validNarrative();
        Narrative stored = new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(),
                List.of(new RiskNarrativeItem(
                        "HIGH",
                        "지연 업무를 우선 점검해야 합니다.",
                        List.of("tasks.delayed"),
                        List.of("TASK-01", "TASK-02"),
                        List.of())),
                narrative.topActions(), narrative.leaderDecisions(), narrative.limitations());

        Narrative compatible = contract.compatibleNarrative(
                stored, completedAndDelayedTaskSnapshot());

        assertThat(compatible.risks().getFirst().taskRefs())
                .containsExactly("TASK-02");
    }

    @Test
    void keepsMultiSignalItemWhenOneCitedMetricMatchesTheTask() {
        Narrative narrative = riskNarrative(
                "지연·보류가 함께 늘어 실행 구간이 좁아졌습니다.",
                List.of("tasks.delayed", "tasks.onHold"), List.of("TASK-02"));

        contract.validateGenerated(completedAndDelayedTaskSnapshot(), narrative);

        assertThat(contract.compatibleNarrative(
                narrative, completedAndDelayedTaskSnapshot())
                .risks().getFirst().taskRefs()).containsExactly("TASK-02");
    }

    @Test
    void rejectsItemWhenNoCitedMetricMatchesTheTask() {
        assertSnapshotError(riskNarrative(
                "보류 업무를 먼저 확인해야 합니다.",
                List.of("tasks.onHold"), List.of("TASK-02")));
    }

    @Test
    void allowsAggregateOnlyEvidenceToReferenceAnyTask() {
        Narrative narrative = riskNarrative(
                "전체 업무량이 실행 여력을 넘어섰습니다.",
                List.of("tasks.total"), List.of("TASK-01", "TASK-02"));

        contract.validateGenerated(completedAndDelayedTaskSnapshot(), narrative);
    }

    @Test
    void rejectsTaskScopedEvidenceReferencingAnotherTask() {
        assertSnapshotError(riskNarrative(
                "남은 체크리스트를 먼저 확인해야 합니다.",
                List.of("task.TASK-02.checklistTotal"), List.of("TASK-01")));
    }

    @Test
    void rejectsCompletedTaskAsInProgressEvidenceReference() {
        assertSnapshotError(riskNarrative(
                "진행 중 업무의 병목을 확인해야 합니다.",
                List.of("tasks.inProgress"), List.of("TASK-01")));
    }

    @Test
    void keepsTaskScopedEvidenceOnItsOwnTask() {
        Narrative narrative = riskNarrative(
                "남은 체크리스트를 먼저 확인해야 합니다.",
                List.of("task.TASK-02.checklistTotal"), List.of("TASK-02"));

        contract.validateGenerated(completedAndDelayedTaskSnapshot(), narrative);

        assertThat(contract.compatibleNarrative(
                narrative, completedAndDelayedTaskSnapshot())
                .risks().getFirst().taskRefs()).containsExactly("TASK-02");
    }

    @Test
    void rejectsLiteralNumericClaim() {
        Narrative narrative = validNarrative();
        Narrative invalid = new Narrative(
                "업무 3건을 우선 점검",
                narrative.summary(), narrative.changes(), narrative.achievements(),
                narrative.risks(), narrative.topActions(),
                narrative.leaderDecisions(), narrative.limitations());

        assertContractError(invalid, "AI_REPORT_NUMERIC_TEXT_INVALID");
    }

    @Test
    void rejectsUnknownActionOwnerAlias() {
        Narrative narrative = validNarrative();
        ActionNarrativeItem action = narrative.topActions().getFirst();
        Narrative invalid = new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                List.of(new ActionNarrativeItem(
                        action.priority(), action.actionTemplate(), action.reasonTemplate(),
                        "MEMBER-99", action.evidenceKeys(), action.taskRefs(),
                        action.objectiveRefs())),
                narrative.leaderDecisions(), narrative.limitations());

        assertContractError(invalid, "AI_REPORT_EVIDENCE_INVALID");
    }

    @Test
    void rejectsDraftActionWithoutOwner() {
        Narrative narrative = validNarrative();
        ActionNarrativeItem action = narrative.topActions().getFirst();
        Narrative invalid = new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                List.of(new ActionNarrativeItem(
                        action.priority(), action.actionTemplate(), action.reasonTemplate(),
                        null, action.evidenceKeys(), action.taskRefs(),
                        action.objectiveRefs())),
                narrative.leaderDecisions(), narrative.limitations());

        assertThatThrownBy(() -> contract.validateDraft(completeSnapshot(), invalid))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_RESPONSE_INVALID"));
    }

    @Test
    void rejectsMemberAliasInNarrativeBody() {
        Narrative narrative = validNarrative();
        Narrative invalid = new Narrative(
                narrative.headlineTemplate(),
                new NarrativeItem("MEMBER-01의 업무를 재배분하세요.",
                        List.of("tasks.total")),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                narrative.topActions(), narrative.leaderDecisions(), narrative.limitations());

        assertContractError(invalid, "AI_REPORT_PERSON_COMPARISON_INVALID");
    }

    // 별칭 금지 규칙이 근거 placeholder까지 잡으면 업무·팀원 범위 수치를 한 글자도 쓸 수 없다.
    @Test
    void acceptsTaskScopedEvidencePlaceholderInNarrativeBody() {
        Narrative narrative = riskNarrative(
                "마감일 {{task.TASK-01.dueDate}}을 넘긴 항목부터 확인해야 합니다.",
                List.of("task.TASK-01.dueDate"), List.of("TASK-01"));

        contract.validateGenerated(completedAndDelayedTaskSnapshot(), narrative);

        assertThat(contract.view(narrative, completedAndDelayedTaskSnapshot())
                .risks().getFirst().text())
                .isEqualTo("마감일 2026-07-24을 넘긴 항목부터 확인해야 합니다.");
    }

    @Test
    void rejectsTaskAliasInNarrativeBody() {
        Narrative narrative = validNarrative();
        Narrative invalid = new Narrative(
                narrative.headlineTemplate(),
                new NarrativeItem("TASK-01의 기한을 다시 확인하세요.",
                        List.of("tasks.total")),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                narrative.topActions(), narrative.leaderDecisions(), narrative.limitations());

        assertContractError(invalid, "AI_REPORT_REFERENCE_TEXT_INVALID");
    }

    @Test
    void sendsMemberReferencesWithoutIndividualPerformanceMetrics() throws Exception {
        ReportSnapshot snapshot = completeSnapshot();
        AiReportContext context = new AiReportContext(
                snapshot.metrics(), snapshot.comparison(), List.of(), List.of(),
                Set.of("tasks.total"), Set.of("MEMBER-01"));

        var payload = new ObjectMapper().findAndRegisterModules()
                .readTree(contract.writeAiContext(context));

        assertThat(payload.path("memberRefs")).extracting(node -> node.asText())
                .containsExactly("MEMBER-01");
        assertThat(payload.path("metrics").path("members").isArray()).isTrue();
        assertThat(payload.path("metrics").path("members")).isEmpty();
    }

    private Narrative riskNarrative(
            String text, List<String> evidenceKeys, List<String> taskRefs) {
        Narrative narrative = validNarrative();
        return new Narrative(
                narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(),
                List.of(new RiskNarrativeItem(
                        "MEDIUM", text, evidenceKeys, taskRefs, List.of())),
                narrative.topActions(), narrative.leaderDecisions(), narrative.limitations());
    }

    private void assertSnapshotError(Narrative narrative) {
        assertThatThrownBy(() -> contract.validateGenerated(
                completedAndDelayedTaskSnapshot(), narrative))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("AI_REPORT_EVIDENCE_INVALID"));
    }

    private void assertContractError(Narrative narrative, String code) {
        assertThatThrownBy(() -> contract.validateGenerated(completeSnapshot(), narrative))
                .isInstanceOfSatisfying(ApplicationException.class,
                        exception -> assertThat(exception.code()).isEqualTo(code));
    }

    private Narrative validNarrative() {
        return new Narrative(
                "실행 중심 주간 요약",
                new NarrativeItem("확정 업무 흐름을 점검했습니다.", List.of("tasks.total")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ActionNarrativeItem(
                        1,
                        "진행 업무를 우선 점검하세요.",
                        "확정 업무량을 실행 계획에 반영해야 합니다.",
                        "MEMBER-01",
                        List.of("tasks.total"),
                        List.of(),
                        List.of())),
                List.of(),
                List.of());
    }

    private Narrative legacyNarrative(
            List<NarrativeItem> highlights, List<NarrativeItem> limitations) {
        return new Narrative(
                "주간 흐름",
                "확정 지표 기반 서술",
                highlights,
                List.of(),
                List.of(new NarrativeItem(
                        "실행 계획을 점검하세요.", List.of("tasks.total"))),
                limitations);
    }

    private ReportSnapshot completeSnapshot() {
        MetricsSnapshot metrics = new MetricsSnapshot(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 1,
                new StatusMetrics(1, 0, 0, 0, 0, 0, 0, 0),
                0, null, null,
                new HistoryCoverage(
                        HistoryCoverageStatus.COMPLETE,
                        Instant.parse("2026-07-01T00:00:00Z")),
                new ChecklistMetrics(0, 0, null),
                List.of(), List.of(), List.of(), Map.of("tasks.total", 1));
        ComparisonMetrics comparison =
                new ComparisonMetrics(false, null, null, null, null, null, null);
        AiReportContext context =
                new AiReportContext(metrics, comparison, List.of(), List.of(),
                        Set.of("tasks.total"), Set.of("MEMBER-01"));
        Map<String, EvidenceValue> evidence = Map.of(
                "tasks.total",
                new EvidenceValue("tasks.total", "전체 업무", "1", "NUMBER"));
        return new ReportSnapshot(
                metrics, comparison, context, new ReferenceIndex(List.of()), evidence);
    }

    private ReportSnapshot completedAndDelayedTaskSnapshot() {
        MetricsSnapshot metrics = new MetricsSnapshot(
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 26), 2,
                new StatusMetrics(0, 0, 1, 0, 1, 0, 0, 1),
                50, 100, null,
                new HistoryCoverage(
                        HistoryCoverageStatus.COMPLETE,
                        Instant.parse("2026-07-01T00:00:00Z")),
                new ChecklistMetrics(0, 0, null),
                List.of(), List.of(), List.of(),
                Map.of("tasks.total", 2, "tasks.delayed", 1));
        ComparisonMetrics comparison =
                new ComparisonMetrics(false, null, null, null, null, null, null);
        AiReportContext context = new AiReportContext(
                metrics,
                comparison,
                List.of(
                        new TaskContext(
                                "TASK-01", "COMPLETED", "HIGH", "COMPLETED_ON_TIME",
                                0, 0, null, null, null, null, "MEMBER-01", List.of()),
                        new TaskContext(
                                "TASK-02", "IN_PROGRESS", "URGENT", "OVERDUE",
                                3, 1, null, null, null, null, "MEMBER-01", List.of())),
                List.of(),
                Set.of("tasks.total", "tasks.delayed", "tasks.inProgress",
                        "tasks.onHold", "task.TASK-02.checklistTotal",
                        "task.TASK-01.dueDate"),
                Set.of("MEMBER-01"));
        Map<String, EvidenceValue> evidence = Map.of(
                "tasks.total",
                new EvidenceValue("tasks.total", "전체 업무", "2", "NUMBER"),
                "tasks.delayed",
                new EvidenceValue("tasks.delayed", "지연 업무", "1", "NUMBER"),
                "tasks.inProgress",
                new EvidenceValue("tasks.inProgress", "진행 중 업무", "1", "COUNT"),
                "tasks.onHold",
                new EvidenceValue("tasks.onHold", "보류 업무", "0", "COUNT"),
                "task.TASK-02.checklistTotal",
                new EvidenceValue("task.TASK-02.checklistTotal",
                        "TASK-02 체크리스트 전체", "3", "COUNT"),
                "task.TASK-01.dueDate",
                new EvidenceValue("task.TASK-01.dueDate",
                        "TASK-01 마감일", "2026-07-24", "DATE"));
        return new ReportSnapshot(
                metrics, comparison, context, new ReferenceIndex(List.of()), evidence);
    }
}
