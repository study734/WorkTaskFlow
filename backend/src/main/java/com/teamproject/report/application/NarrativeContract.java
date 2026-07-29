package com.teamproject.report.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.common.exception.ApplicationException;
import com.teamproject.report.application.ReportContracts.ActionNarrativeItem;
import com.teamproject.report.application.ReportContracts.ActionNarrativeItemView;
import com.teamproject.report.application.ReportContracts.AiReportContext;
import com.teamproject.report.application.ReportContracts.DecisionNarrativeItem;
import com.teamproject.report.application.ReportContracts.DecisionNarrativeItemView;
import com.teamproject.report.application.ReportContracts.EvidenceValue;
import com.teamproject.report.application.ReportContracts.LocalReference;
import com.teamproject.report.application.ReportContracts.MetricsSnapshot;
import com.teamproject.report.application.ReportContracts.Narrative;
import com.teamproject.report.application.ReportContracts.NarrativeItem;
import com.teamproject.report.application.ReportContracts.NarrativeItemView;
import com.teamproject.report.application.ReportContracts.NarrativeView;
import com.teamproject.report.application.ReportContracts.ReferenceIndex;
import com.teamproject.report.application.ReportContracts.ReportSnapshot;
import com.teamproject.report.application.ReportContracts.RiskNarrativeItem;
import com.teamproject.report.application.ReportContracts.RiskNarrativeItemView;
import com.teamproject.report.application.ReportContracts.TaskContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 버전별 저장 JSON 계약과 provider-safe 입력 형식을 한곳에서 관리한다. 저장된 구버전 payload의
 * upcast와 현재 출력 검증도 이 경계가 책임진다.
 */
@Component
public class NarrativeContract {
    public static final String PROMPT_VERSION = "v6";
    public static final String SCHEMA_VERSION = "v4";
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([A-Za-z0-9._-]+)}}");
    private static final Pattern MEMBER_ALIAS = Pattern.compile("\\bMEMBER-\\d+\\b");
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH");

    private final ObjectMapper json;

    public NarrativeContract(ObjectMapper json) {
        this.json = json;
    }

    public String promptVersion() { return PROMPT_VERSION; }
    public String schemaVersion() { return SCHEMA_VERSION; }

    public String instructions(String language) {
        return """
                You are a project operations analyst and chief of staff to a team leader. You
                produce a paid weekly decision brief, not a metric summary. The team leader must
                understand the situation, decide, and assign follow-up work within 30 seconds.
                The server establishes facts; you explain their operational meaning; a human
                approves actions.
                Write in this editorial order: dominant change, operational consequence, decision,
                then action. Prioritize the largest negative movement, highest-severity signal, or
                blocked objective; mention a positive result only when it changes the decision.
                The headline must be one decisive, non-numeric sentence naming the single thing
                most worth the leader's attention. The summary must not restate the headline in
                other words. It names the dominant signal, gives the specific diagnosis behind it,
                and says why it matters now, using at least two supplied evidence keys when the
                input provides them. Never use generic filler such as "check the workflow",
                "monitor progress", or "review priorities" without naming the affected signal,
                consequence, and next checkpoint.
                Put directly observed facts in changes and connect each change to its direction
                or operational consequence. Put only evidence-backed outcomes in achievements.
                Each risk states the signal, the likely operational impact, and the observation
                that would confirm or rule it out, without claiming an unproven cause. Do not
                repeat a server risk label as the whole risk. Leader decisions must be answerable
                choices or approvals with the consequence of delaying the decision. Propose no
                more than three prioritized, non-duplicative actions.
                Do not restate the same finding across summary, changes, risks, decisions, and
                actions. Each section must add a new layer: diagnosis, movement, consequence,
                choice, or ownership.
                Every action must set ownerRef to one supplied MEMBER- reference and state what
                to do, when it should be checked, and which risk it reduces. State the checkpoint
                as an evidence placeholder or an explicit relative window such as "before the next
                weekly review"; never leave the timing implied.
                Use only supplied evidence keys and de-identified TASK-, GOAL-, and MEMBER- refs.
                Never invent or calculate a number or date. Any numeric or date claim must use an
                exact placeholder such as {{tasks.delayed}} or {{task.TASK-01.blockerReviewDate}}.
                Put every placeholder key in the same item's evidenceKeys.
                Do not rank, score, praise, blame, or infer attitude or productivity of a person.
                Reference affected taskRefs and objectiveRefs when known.
                When coverage is partial, include a limitation citing coverage.partial.
                Do not use markdown or numbered-list prefixes in text fields.
                Return the report in %s.
                """.formatted("en".equals(language) ? "English" : "Korean");
    }

    public Map<String, Object> responseFormat() {
        Map<String, Object> narrativeItem = itemSchema("textTemplate");
        Map<String, Object> riskItem = objectSchema(Map.of(
                "severity", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH")),
                "textTemplate", narrativeTextSchema(600),
                "evidenceKeys", stringArray(1, 8),
                "taskRefs", stringArray(0, 5),
                "objectiveRefs", stringArray(0, 3)),
                List.of("severity", "textTemplate", "evidenceKeys", "taskRefs", "objectiveRefs"));
        Map<String, Object> actionItem = objectSchema(Map.of(
                "priority", Map.of("type", "integer", "enum", List.of(1, 2, 3)),
                "actionTemplate", narrativeTextSchema(500),
                "reasonTemplate", narrativeTextSchema(500),
                "ownerRef", Map.of("type", "string"),
                "evidenceKeys", stringArray(1, 8),
                "taskRefs", stringArray(0, 5),
                "objectiveRefs", stringArray(0, 3)),
                List.of("priority", "actionTemplate", "reasonTemplate", "ownerRef",
                        "evidenceKeys", "taskRefs", "objectiveRefs"));
        Map<String, Object> decisionItem = objectSchema(Map.of(
                "questionTemplate", narrativeTextSchema(500),
                "impactTemplate", narrativeTextSchema(500),
                "evidenceKeys", stringArray(1, 8),
                "taskRefs", stringArray(0, 5),
                "objectiveRefs", stringArray(0, 3)),
                List.of("questionTemplate", "impactTemplate",
                        "evidenceKeys", "taskRefs", "objectiveRefs"));
        Map<String, Object> schema = objectSchema(Map.of(
                "headlineTemplate", narrativeTextSchema(120),
                "summary", narrativeItem,
                "changes", arraySchema(narrativeItem, 0, 3),
                "achievements", arraySchema(narrativeItem, 0, 3),
                "risks", arraySchema(riskItem, 0, 3),
                "topActions", arraySchema(actionItem, 1, 3),
                "leaderDecisions", arraySchema(decisionItem, 0, 3),
                "limitations", arraySchema(narrativeItem, 0, 3)),
                List.of("headlineTemplate", "summary", "changes", "achievements",
                        "risks", "topActions", "leaderDecisions", "limitations"));
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("type", "json_schema");
        format.put("name", "weekly_operational_report_v4");
        format.put("strict", true);
        format.put("schema", schema);
        return format;
    }

    public String writeMetrics(MetricsSnapshot metrics) { return write(metrics); }
    // Provider에는 별칭과 집계값만 직렬화하고 로컬 제목·이름 같은 개인정보 참조는 넘기지 않는다.
    public String writeAiContext(AiReportContext context) {
        MetricsSnapshot metrics = context.metrics();
        MetricsSnapshot privateMetrics = new MetricsSnapshot(
                metrics.periodStart(), metrics.periodEnd(), metrics.totalTasks(),
                metrics.statuses(), metrics.completionRatePercent(),
                metrics.onTimeRatePercent(), metrics.averageCompletionHours(),
                metrics.historyCoverage(), metrics.checklist(), metrics.daily(),
                List.of(), metrics.riskSignals(), metrics.evidence());
        return write(new AiReportContext(privateMetrics, context.comparison(),
                context.tasks(), context.objectives(), context.evidenceKeys(),
                context.memberRefs() == null ? Set.of() : context.memberRefs()));
    }
    public String writeReferenceIndex(ReferenceIndex references) { return write(references); }
    public String writeEvidence(Map<String, EvidenceValue> evidence) { return write(evidence); }
    public String writeNarrative(Narrative narrative) { return write(narrative); }
    public Class<GeneratedNarrative> responseType() { return GeneratedNarrative.class; }
    public Narrative fromGenerated(GeneratedNarrative value) {
        if (value == null) throw payloadInvalid();
        Narrative narrative = value.toNarrative();
        if (narrative.topActions() == null || narrative.topActions().stream()
                .anyMatch(item -> blank(item.ownerRef()))) {
            throw new IllegalArgumentException("Generated action owner is required");
        }
        return narrative;
    }

    public MetricsSnapshot readMetrics(String schemaVersion, String value) {
        return switch (schemaVersion) {
            case "v2", "v3", "v4" -> read(value, MetricsSnapshot.class);
            default -> throw unsupportedSchema();
        };
    }

    // 초기 저장본의 nullable memberRefs를 현재 계약으로 복구해 재생성 경로를 보존한다.
    public AiReportContext readAiContext(String schemaVersion, String value) {
        if (!Set.of("v3", "v4").contains(schemaVersion)) throw unsupportedSchema();
        AiReportContext context = read(value, AiReportContext.class);
        if (context.memberRefs() != null) return context;
        Set<String> memberRefs = context.tasks().stream()
                .map(TaskContext::memberRef)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (memberRefs.isEmpty() && context.metrics().members() != null) {
            context.metrics().members().stream()
                    .map(ReportContracts.MemberMetric::memberLabel)
                    .filter(Objects::nonNull)
                    .forEach(memberRefs::add);
        }
        return new AiReportContext(
                context.metrics(), context.comparison(), context.tasks(), context.objectives(),
                context.evidenceKeys(), Set.copyOf(memberRefs));
    }

    public ReferenceIndex readReferenceIndex(String schemaVersion, String value) {
        if (!Set.of("v3", "v4").contains(schemaVersion)) throw unsupportedSchema();
        return read(value, ReferenceIndex.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, EvidenceValue> readEvidence(String schemaVersion, String value) {
        if (!Set.of("v3", "v4").contains(schemaVersion)) throw unsupportedSchema();
        try {
            return json.readValue(value, json.getTypeFactory().constructMapType(
                    LinkedHashMap.class, String.class, EvidenceValue.class));
        } catch (JsonProcessingException exception) {
            throw payloadInvalid();
        }
    }

    public Narrative readNarrative(String schemaVersion, String value) {
        return switch (schemaVersion) {
            case "v3" -> {
                Narrative narrative = read(value, Narrative.class);
                validateShape(narrative, false);
                yield narrative;
            }
            case "v4" -> {
                Narrative narrative = read(value, Narrative.class);
                validateShape(narrative, true);
                yield narrative;
            }
            case "v2" -> legacy(read(value, LegacyNarrative.class));
            default -> throw unsupportedSchema();
        };
    }

    public void validateGenerated(ReportSnapshot snapshot, Narrative narrative) {
        validate(snapshot, narrative, HttpStatus.BAD_GATEWAY);
    }

    public void validateGenerated(MetricsSnapshot metrics, Narrative narrative) {
        Map<String, EvidenceValue> evidence = new LinkedHashMap<>();
        metrics.evidence().forEach((key, value) -> evidence.put(key,
                new EvidenceValue(key, key, Integer.toString(value), "LEGACY")));
        var comparison = new ReportContracts.ComparisonMetrics(
                false, null, null, null, null, null, null);
        var context = new AiReportContext(
                metrics, comparison, List.of(), List.of(), evidence.keySet());
        validateGenerated(new ReportSnapshot(metrics, comparison, context,
                new ReferenceIndex(List.of()), evidence), narrative);
    }

    public void validateDraft(ReportSnapshot snapshot, Narrative narrative) {
        validate(snapshot, narrative, HttpStatus.BAD_REQUEST);
    }

    public Narrative compatibleNarrative(Narrative narrative, ReportSnapshot snapshot) {
        Map<String, TaskContext> tasks = taskContexts(snapshot);
        return new Narrative(
                narrative.headlineTemplate(),
                compatible(narrative.summary(), tasks),
                narrative.changes().stream().map(item -> compatible(item, tasks)).toList(),
                narrative.achievements().stream().map(item -> compatible(item, tasks)).toList(),
                narrative.risks().stream().map(item -> compatible(item, tasks)).toList(),
                narrative.topActions().stream().map(item -> compatible(item, tasks)).toList(),
                narrative.leaderDecisions().stream().map(item -> compatible(item, tasks)).toList(),
                narrative.limitations().stream().map(item -> compatible(item, tasks)).toList());
    }

    public NarrativeView view(Narrative narrative, ReportSnapshot snapshot) {
        Map<String, LocalReference> references = snapshot.references().references().stream()
                .collect(Collectors.toMap(LocalReference::ref, value -> value));
        return new NarrativeView(
                render(narrative.headlineTemplate(), snapshot.evidence()),
                itemView(narrative.summary(), snapshot.evidence(), references),
                narrative.changes().stream()
                        .map(item -> itemView(item, snapshot.evidence(), references)).toList(),
                narrative.achievements().stream()
                        .map(item -> itemView(item, snapshot.evidence(), references)).toList(),
                narrative.risks().stream()
                        .map(item -> riskView(item, snapshot.evidence(), references)).toList(),
                narrative.topActions().stream()
                        .map(item -> actionView(item, snapshot.evidence(), references)).toList(),
                narrative.leaderDecisions().stream()
                        .map(item -> decisionView(item, snapshot.evidence(), references)).toList(),
                narrative.limitations().stream()
                        .map(item -> itemView(item, snapshot.evidence(), references)).toList());
    }

    public ReportContracts.OperationalView operationalView(
            String groupName, ReportSnapshot snapshot) {
        Map<String, LocalReference> references = snapshot.references().references().stream()
                .collect(Collectors.toMap(LocalReference::ref, value -> value));
        List<ReportContracts.MemberWorkView> members = snapshot.metrics().members().stream()
                .map(item -> new ReportContracts.MemberWorkView(
                        references.getOrDefault(item.memberLabel(),
                                new LocalReference(item.memberLabel(), "MEMBER",
                                        item.memberLabel(), null, null)),
                        item.assigned(), item.active(), item.completed(), item.delayed(),
                        item.onTimeRatePercent()))
                .toList();
        List<ReportContracts.TaskWorkView> tasks = snapshot.aiContext().tasks().stream()
                .map(item -> new ReportContracts.TaskWorkView(
                        references.get(item.taskRef()),
                        references.get(item.memberRef()),
                        references.get(item.objectiveRef()),
                        item.status(), item.priority(), item.dueState(),
                        item.checklistTotal(), item.checklistCompleted(),
                        item.blockerType(), item.blockerNextActionType(),
                        item.blockerReviewWindow(), item.changes()))
                .toList();
        return new ReportContracts.OperationalView(
                groupName,
                healthStatus(snapshot.metrics()),
                confidenceLevel(snapshot),
                members.size(),
                Math.toIntExact(members.stream().filter(item -> item.assigned() > 0).count()),
                members,
                tasks);
    }

    public ReportSnapshot snapshot(MetricsSnapshot metrics, AiReportContext context,
            ReferenceIndex references, Map<String, EvidenceValue> evidence) {
        return new ReportSnapshot(metrics, context.comparison(), context, references, evidence);
    }

    private String healthStatus(MetricsSnapshot metrics) {
        if (metrics.riskSignals().stream().anyMatch(item -> "HIGH".equals(item.severity()))) {
            return "AT_RISK";
        }
        if (metrics.statuses().delayed() > 0 || metrics.statuses().onHold() > 0
                || metrics.riskSignals().stream()
                        .anyMatch(item -> "MEDIUM".equals(item.severity()))) {
            return "NEEDS_ATTENTION";
        }
        return "ON_TRACK";
    }

    private String confidenceLevel(ReportSnapshot snapshot) {
        if (snapshot.metrics().historyCoverage().partial()) return "LOW";
        return snapshot.comparison().available() ? "HIGH" : "MEDIUM";
    }

    private void validate(ReportSnapshot snapshot, Narrative narrative, HttpStatus status) {
        validateShape(narrative, true);
        Set<String> allowedEvidence = snapshot.evidence().keySet();
        Set<String> allowedTasks = snapshot.aiContext().tasks().stream()
                .map(ReportContracts.TaskContext::taskRef).collect(Collectors.toSet());
        Map<String, TaskContext> taskContexts = taskContexts(snapshot);
        Set<String> allowedObjectives = snapshot.aiContext().objectives().stream()
                .map(ReportContracts.ObjectiveContext::objectiveRef).collect(Collectors.toSet());
        Set<String> allowedMembers = new HashSet<>(snapshot.metrics().members().stream()
                .map(ReportContracts.MemberMetric::memberLabel).collect(Collectors.toSet()));
        if (snapshot.aiContext().memberRefs() != null) {
            allowedMembers.addAll(snapshot.aiContext().memberRefs());
        }
        if (containsLiteralNumber(narrative.headlineTemplate())
                || PLACEHOLDER.matcher(narrative.headlineTemplate()).find()) {
            throw invalid("AI_REPORT_NUMERIC_TEXT_INVALID", status,
                    "리포트 제목에는 숫자나 날짜를 직접 입력할 수 없습니다.");
        }

        List<TemplatePart> parts = new ArrayList<>();
        parts.add(part(narrative.summary().textTemplate(), narrative.summary()));
        narrative.changes().forEach(item -> parts.add(part(item.textTemplate(), item)));
        narrative.achievements().forEach(item -> parts.add(part(item.textTemplate(), item)));
        narrative.risks().forEach(item -> parts.add(part(item.textTemplate(), item)));
        narrative.topActions().forEach(item -> {
            parts.add(part(item.actionTemplate(), item));
            parts.add(part(item.reasonTemplate(), item));
        });
        narrative.leaderDecisions().forEach(item -> {
            parts.add(part(item.questionTemplate(), item));
            parts.add(part(item.impactTemplate(), item));
        });
        narrative.limitations().forEach(item -> parts.add(part(item.textTemplate(), item)));

        for (TemplatePart part : parts) {
            validatePart(part, allowedEvidence, allowedTasks,
                    allowedObjectives, allowedMembers, status);
            validateTaskReferences(part, taskContexts, status);
        }
        if (narrative.risks().stream().anyMatch(item -> !SEVERITIES.contains(item.severity()))) {
            throw invalid("AI_REPORT_RESPONSE_INVALID", status, "위험 수준을 확인해 주세요.");
        }
        for (int index = 0; index < narrative.topActions().size(); index++) {
            if (narrative.topActions().get(index).priority() != index + 1) {
                throw invalid("AI_REPORT_ACTION_PRIORITY_INVALID", status,
                        "우선 행동의 순서를 확인해 주세요.");
            }
        }
        if (snapshot.metrics().historyCoverage().partial()
                && narrative.limitations().stream().noneMatch(
                        item -> item.evidenceKeys().contains("coverage.partial"))) {
            throw invalid("AI_REPORT_EVIDENCE_INVALID", status,
                    "부분 이력 제한사항이 누락되었습니다.");
        }
    }

    private void validatePart(TemplatePart part, Set<String> allowedEvidence,
            Set<String> allowedTasks, Set<String> allowedObjectives,
            Set<String> allowedMembers, HttpStatus status) {
        if (MEMBER_ALIAS.matcher(part.template()).find()) {
            throw invalid("AI_REPORT_PERSON_COMPARISON_INVALID", status,
                    "팀원 식별자는 구조화된 담당자 필드에서만 사용할 수 있습니다.");
        }
        if (containsLiteralNumber(removePlaceholders(part.template()))) {
            throw invalid("AI_REPORT_NUMERIC_TEXT_INVALID", status,
                    "숫자와 날짜는 서버 근거 placeholder로만 입력해 주세요.");
        }
        if (!allowedEvidence.containsAll(part.evidenceKeys())
                || !allowedTasks.containsAll(part.taskRefs())
                || !allowedObjectives.containsAll(part.objectiveRefs())
                || !allowedMembers.containsAll(part.memberRefs())) {
            throw invalid("AI_REPORT_EVIDENCE_INVALID", status,
                    "리포트 근거 또는 업무 참조를 확인해 주세요.");
        }
        Matcher matcher = PLACEHOLDER.matcher(part.template());
        while (matcher.find()) {
            String key = matcher.group(1);
            if (!allowedEvidence.contains(key) || !part.evidenceKeys().contains(key)) {
                throw invalid("AI_REPORT_PLACEHOLDER_INVALID", status,
                        "허용되지 않은 근거 placeholder가 있습니다.");
            }
        }
        if (part.evidenceKeys().isEmpty()) {
            throw invalid("AI_REPORT_EVIDENCE_INVALID", status,
                    "각 리포트 항목에는 근거가 필요합니다.");
        }
    }

    private void validateTaskReferences(TemplatePart part,
            Map<String, TaskContext> tasks, HttpStatus status) {
        if (part.taskRefs().stream().map(tasks::get)
                .anyMatch(task -> !taskMatchesEvidence(task, part.evidenceKeys()))) {
            throw invalid("AI_REPORT_EVIDENCE_INVALID", status,
                    "근거 업무가 해당 지표의 의미와 일치하지 않습니다.");
        }
    }

    private Map<String, TaskContext> taskContexts(ReportSnapshot snapshot) {
        return snapshot.aiContext().tasks().stream()
                .collect(Collectors.toMap(TaskContext::taskRef, value -> value));
    }

    private boolean taskMatchesEvidence(TaskContext task, List<String> evidenceKeys) {
        if (task == null) return false;
        return evidenceKeys.stream().allMatch(key -> switch (key) {
            case "tasks.completed" -> "COMPLETED".equals(task.status());
            case "tasks.active" ->
                    Set.of("IN_PROGRESS", "ON_HOLD").contains(task.status());
            case "tasks.onHold" -> "ON_HOLD".equals(task.status());
            case "tasks.delayed" -> "OVERDUE".equals(task.dueState());
            case "tasks.highPriority" ->
                    Set.of("HIGH", "URGENT").contains(task.priority());
            case "checklist.total" -> task.checklistTotal() > 0;
            case "checklist.completed" -> task.checklistCompleted() > 0;
            default -> true;
        });
    }

    private List<String> compatibleTaskRefs(List<String> taskRefs,
            List<String> evidenceKeys, Map<String, TaskContext> tasks) {
        if (taskRefs == null) return List.of();
        return taskRefs.stream()
                .filter(ref -> taskMatchesEvidence(tasks.get(ref), evidenceKeys))
                .toList();
    }

    private NarrativeItem compatible(NarrativeItem item, Map<String, TaskContext> tasks) {
        return new NarrativeItem(item.textTemplate(), item.evidenceKeys(),
                compatibleTaskRefs(item.taskRefs(), item.evidenceKeys(), tasks),
                item.objectiveRefs());
    }

    private RiskNarrativeItem compatible(
            RiskNarrativeItem item, Map<String, TaskContext> tasks) {
        return new RiskNarrativeItem(item.severity(), item.textTemplate(), item.evidenceKeys(),
                compatibleTaskRefs(item.taskRefs(), item.evidenceKeys(), tasks),
                item.objectiveRefs());
    }

    private ActionNarrativeItem compatible(
            ActionNarrativeItem item, Map<String, TaskContext> tasks) {
        return new ActionNarrativeItem(
                item.priority(), item.actionTemplate(), item.reasonTemplate(), item.ownerRef(),
                item.evidenceKeys(),
                compatibleTaskRefs(item.taskRefs(), item.evidenceKeys(), tasks),
                item.objectiveRefs());
    }

    private DecisionNarrativeItem compatible(
            DecisionNarrativeItem item, Map<String, TaskContext> tasks) {
        return new DecisionNarrativeItem(
                item.questionTemplate(), item.impactTemplate(), item.evidenceKeys(),
                compatibleTaskRefs(item.taskRefs(), item.evidenceKeys(), tasks),
                item.objectiveRefs());
    }

    private void validateShape(Narrative narrative, boolean requireActionOwner) {
        boolean invalid = narrative == null
                || blank(narrative.headlineTemplate())
                || invalid(narrative.summary())
                || narrative.changes() == null || narrative.changes().size() > 3
                || narrative.achievements() == null || narrative.achievements().size() > 3
                || narrative.risks() == null || narrative.risks().size() > 3
                || narrative.topActions() == null || narrative.topActions().isEmpty()
                || narrative.topActions().size() > 3
                || narrative.leaderDecisions() == null
                || narrative.leaderDecisions().size() > 3
                || narrative.limitations() == null || narrative.limitations().size() > 3
                || narrative.changes().stream().anyMatch(this::invalid)
                || narrative.achievements().stream().anyMatch(this::invalid)
                || narrative.risks().stream().anyMatch(this::invalid)
                || narrative.topActions().stream()
                        .anyMatch(item -> invalid(item, requireActionOwner))
                || narrative.leaderDecisions().stream().anyMatch(this::invalid)
                || narrative.limitations().stream().anyMatch(this::invalid);
        if (invalid) {
            throw new ApplicationException("AI_REPORT_RESPONSE_INVALID", HttpStatus.BAD_GATEWAY,
                    "AI 리포트 응답을 확인하지 못했습니다.");
        }
    }

    private boolean invalid(NarrativeItem item) {
        return item == null || blank(item.textTemplate())
                || invalidRefs(item.evidenceKeys(), item.taskRefs(), item.objectiveRefs());
    }

    private boolean invalid(RiskNarrativeItem item) {
        return item == null || blank(item.severity()) || blank(item.textTemplate())
                || invalidRefs(item.evidenceKeys(), item.taskRefs(), item.objectiveRefs());
    }

    private boolean invalid(ActionNarrativeItem item, boolean requireOwner) {
        return item == null || item.priority() < 1 || item.priority() > 3
                || blank(item.actionTemplate()) || blank(item.reasonTemplate())
                || (requireOwner && blank(item.ownerRef()))
                || invalidRefs(item.evidenceKeys(), item.taskRefs(), item.objectiveRefs());
    }

    private boolean invalid(DecisionNarrativeItem item) {
        return item == null || blank(item.questionTemplate()) || blank(item.impactTemplate())
                || invalidRefs(item.evidenceKeys(), item.taskRefs(), item.objectiveRefs());
    }

    private boolean invalidRefs(List<String> evidenceKeys,
            List<String> taskRefs, List<String> objectiveRefs) {
        return evidenceKeys == null || taskRefs == null || objectiveRefs == null
                || evidenceKeys.stream().anyMatch(Objects::isNull)
                || taskRefs.stream().anyMatch(Objects::isNull)
                || objectiveRefs.stream().anyMatch(Objects::isNull);
    }

    private NarrativeItemView itemView(NarrativeItem item,
            Map<String, EvidenceValue> evidence, Map<String, LocalReference> references) {
        return new NarrativeItemView(render(item.textTemplate(), evidence), item.evidenceKeys(),
                resolve(item.taskRefs(), references), resolve(item.objectiveRefs(), references));
    }

    private RiskNarrativeItemView riskView(RiskNarrativeItem item,
            Map<String, EvidenceValue> evidence, Map<String, LocalReference> references) {
        return new RiskNarrativeItemView(item.severity(),
                render(item.textTemplate(), evidence), item.evidenceKeys(),
                resolve(item.taskRefs(), references), resolve(item.objectiveRefs(), references));
    }

    private ActionNarrativeItemView actionView(ActionNarrativeItem item,
            Map<String, EvidenceValue> evidence, Map<String, LocalReference> references) {
        return new ActionNarrativeItemView(item.priority(),
                render(item.actionTemplate(), evidence),
                render(item.reasonTemplate(), evidence),
                references.get(item.ownerRef()), item.evidenceKeys(),
                resolve(item.taskRefs(), references), resolve(item.objectiveRefs(), references));
    }

    private DecisionNarrativeItemView decisionView(DecisionNarrativeItem item,
            Map<String, EvidenceValue> evidence, Map<String, LocalReference> references) {
        return new DecisionNarrativeItemView(
                render(item.questionTemplate(), evidence),
                render(item.impactTemplate(), evidence), item.evidenceKeys(),
                resolve(item.taskRefs(), references), resolve(item.objectiveRefs(), references));
    }

    private List<LocalReference> resolve(
            List<String> refs, Map<String, LocalReference> references) {
        return refs.stream().map(references::get).filter(Objects::nonNull).toList();
    }

    private String render(String template, Map<String, EvidenceValue> evidence) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            EvidenceValue value = evidence.get(matcher.group(1));
            matcher.appendReplacement(rendered,
                    Matcher.quoteReplacement(value == null ? matcher.group() : value.value()));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private String removePlaceholders(String value) {
        return PLACEHOLDER.matcher(value).replaceAll("");
    }

    private boolean containsLiteralNumber(String value) {
        return value != null && value.codePoints().anyMatch(Character::isDigit);
    }

    private TemplatePart part(String template, NarrativeItem item) {
        return new TemplatePart(template, item.evidenceKeys(), item.taskRefs(),
                item.objectiveRefs(), List.of());
    }

    private TemplatePart part(String template, RiskNarrativeItem item) {
        return new TemplatePart(template, item.evidenceKeys(), item.taskRefs(),
                item.objectiveRefs(), List.of());
    }

    private TemplatePart part(String template, ActionNarrativeItem item) {
        return new TemplatePart(template, item.evidenceKeys(), item.taskRefs(),
                item.objectiveRefs(), blank(item.ownerRef())
                        ? List.of() : List.of(item.ownerRef()));
    }

    private TemplatePart part(String template, DecisionNarrativeItem item) {
        return new TemplatePart(template, item.evidenceKeys(), item.taskRefs(),
                item.objectiveRefs(), List.of());
    }

    private Narrative legacy(LegacyNarrative legacy) {
        NarrativeItem summary = new NarrativeItem(legacy.executiveSummary(),
                List.of(), List.of(), List.of());
        return new Narrative(legacy.headline(), summary,
                List.of(), legacy.highlights().stream().map(this::legacyItem).toList(),
                legacy.risks().stream().map(item -> new RiskNarrativeItem(
                        "MEDIUM", item.text(), item.evidenceKeys(), List.of(), List.of())).toList(),
                legacy.nextWeekActions().stream()
                        .limit(3)
                        .map(item -> new ActionNarrativeItem(
                                legacy.nextWeekActions().indexOf(item) + 1,
                                item.text(), "기존 리포트에서 생성된 행동입니다.",
                                item.evidenceKeys(), List.of(), List.of()))
                        .toList(),
                List.of(), legacy.dataLimitations().stream().map(this::legacyItem).toList());
    }

    private NarrativeItem legacyItem(LegacyItem item) {
        return new NarrativeItem(item.text(), item.evidenceKeys(), List.of(), List.of());
    }

    private Map<String, Object> itemSchema(String textProperty) {
        return objectSchema(Map.of(
                textProperty, narrativeTextSchema(800),
                "evidenceKeys", stringArray(1, 8),
                "taskRefs", stringArray(0, 5),
                "objectiveRefs", stringArray(0, 3)),
                List.of(textProperty, "evidenceKeys", "taskRefs", "objectiveRefs"));
    }

    private Map<String, Object> narrativeTextSchema(int maxLength) {
        return Map.of(
                "type", "string",
                "minLength", 1,
                "maxLength", maxLength,
                "description", "Use exact {{evidence.key}} placeholders for numeric/date claims.");
    }

    private Map<String, Object> stringArray(int min, int max) {
        return arraySchema(Map.of("type", "string"), min, max);
    }

    private Map<String, Object> objectSchema(
            Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> arraySchema(Object items, int min, int max) {
        return Map.of("type", "array", "items", items, "minItems", min, "maxItems", max);
    }

    private ApplicationException unsupportedSchema() {
        return new ApplicationException("AI_REPORT_SCHEMA_UNSUPPORTED",
                HttpStatus.INTERNAL_SERVER_ERROR, "지원하지 않는 AI 리포트 스키마입니다.");
    }

    private ApplicationException payloadInvalid() {
        return new ApplicationException("AI_REPORT_PAYLOAD_INVALID",
                HttpStatus.INTERNAL_SERVER_ERROR, "저장된 AI 리포트를 읽지 못했습니다.");
    }

    private ApplicationException invalid(String code, HttpStatus status, String message) {
        return new ApplicationException(code, status, message);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Report JSON serialization failed", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw payloadInvalid();
        }
    }

    public static final class GeneratedNarrative {
        public String headlineTemplate;
        public GeneratedNarrativeItem summary;
        public List<GeneratedNarrativeItem> changes;
        public List<GeneratedNarrativeItem> achievements;
        public List<GeneratedRiskItem> risks;
        public List<GeneratedActionItem> topActions;
        public List<GeneratedDecisionItem> leaderDecisions;
        public List<GeneratedNarrativeItem> limitations;

        Narrative toNarrative() {
            return new Narrative(headlineTemplate,
                    summary == null ? null : summary.toNarrative(),
                    map(changes, GeneratedNarrativeItem::toNarrative),
                    map(achievements, GeneratedNarrativeItem::toNarrative),
                    map(risks, GeneratedRiskItem::toRiskNarrative),
                    map(topActions, GeneratedActionItem::toNarrative),
                    map(leaderDecisions, GeneratedDecisionItem::toNarrative),
                    map(limitations, GeneratedNarrativeItem::toNarrative));
        }
    }

    public static class GeneratedNarrativeItem {
        public String textTemplate;
        public List<String> evidenceKeys;
        public List<String> taskRefs;
        public List<String> objectiveRefs;

        NarrativeItem toNarrative() {
            return new NarrativeItem(textTemplate, evidenceKeys, taskRefs, objectiveRefs);
        }
    }

    public static final class GeneratedRiskItem extends GeneratedNarrativeItem {
        public GeneratedSeverity severity;

        RiskNarrativeItem toRiskNarrative() {
            return new RiskNarrativeItem(
                    severity == null ? null : severity.name(),
                    textTemplate, evidenceKeys, taskRefs, objectiveRefs);
        }
    }

    public static final class GeneratedActionItem {
        public GeneratedPriority priority;
        public String actionTemplate;
        public String reasonTemplate;
        public String ownerRef;
        public List<String> evidenceKeys;
        public List<String> taskRefs;
        public List<String> objectiveRefs;

        ActionNarrativeItem toNarrative() {
            return new ActionNarrativeItem(priority == null ? 0 : priority.value,
                    actionTemplate, reasonTemplate, ownerRef,
                    evidenceKeys, taskRefs, objectiveRefs);
        }
    }

    public enum GeneratedSeverity { LOW, MEDIUM, HIGH }
    public enum GeneratedPriority {
        P1(1), P2(2), P3(3);

        private final int value;
        GeneratedPriority(int value) { this.value = value; }
    }

    public static final class GeneratedDecisionItem {
        public String questionTemplate;
        public String impactTemplate;
        public List<String> evidenceKeys;
        public List<String> taskRefs;
        public List<String> objectiveRefs;

        DecisionNarrativeItem toNarrative() {
            return new DecisionNarrativeItem(questionTemplate, impactTemplate,
                    evidenceKeys, taskRefs, objectiveRefs);
        }
    }

    private static <T, R> List<R> map(
            List<T> source, java.util.function.Function<T, R> mapper) {
        return source == null ? null : source.stream().map(mapper).toList();
    }

    private record TemplatePart(String template, List<String> evidenceKeys,
            List<String> taskRefs, List<String> objectiveRefs,
            List<String> memberRefs) {}
    private record LegacyItem(String text, List<String> evidenceKeys) {}
    private record LegacyNarrative(String headline, String executiveSummary,
            List<LegacyItem> highlights, List<LegacyItem> risks,
            List<LegacyItem> nextWeekActions, List<LegacyItem> dataLimitations) {}
}
