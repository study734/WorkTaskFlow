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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger log = LoggerFactory.getLogger(NarrativeContract.class);
    public static final String PROMPT_VERSION = "v8";
    public static final String SCHEMA_VERSION = "v4";
    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\{\\{([A-Za-z0-9._-]+)}}");
    private static final Pattern MEMBER_ALIAS = Pattern.compile("\\bMEMBER-\\d+\\b");
    // 별칭은 reference index를 거쳐야 실제 제목·이름으로 해석된다. 본문에 남으면 독자에게
    // "TASK-12"가 그대로 보이고, 별칭의 숫자가 리터럴 숫자 검사에도 걸린다.
    private static final Pattern LOCAL_ALIAS = Pattern.compile("\\b(?:TASK|GOAL)-\\d+\\b");
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
                The leader already knows this week's totals. Completion rate, task counts and
                overdue counts are printed on the dashboard, so restating them is not a finding.
                Prioritize what no screen shows: how long work has been stuck and who is affected.
                Lead with these keys whenever the input supplies them, before any headline metric —
                task.*.blockedHours (time a task has sat on hold, counted from before this week),
                task.*.idleDays (days with no activity), task.*.approvalWaitHours (time waiting to
                be approved), task.*.startLagHours, task.*.reopenCount,
                task.*.assigneeChangeCount, flow.overdueReviewCount (on-hold tasks whose review
                date has already passed), flow.idleOverThreeDays, flow.longestBlockedHours,
                flow.longestApprovalWaitHours. A task blocked for many hours whose review date has
                passed is the single most useful thing you can surface; say how long, name the
                consequence of it staying stuck, and give the action that unblocks it.
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
                The server graded each member from its own frozen metrics with a fixed rule. You did
                not compute those grades and must not dispute or recompute them. When member keys
                are supplied, route ownership by them: give the action to the owner of the work that
                is actually stuck, and where a grade explains why that work needs attention, cite
                member.<ref>.grade together with the rate key behind it
                (member.<ref>.completionRate, .onTimeRate or .checklistRate). NOT_RATED means the
                member had no measurable assigned work; state that plainly and never imply fault or
                treat it as a low grade. Describe work and outcomes only — never a person's
                attitude, motivation, diligence or ability, and never compare two members in one
                sentence except through the server's own member.*.rank or members.*Grade keys.
                The key families named above are conditional: a key exists only when its value is
                non-zero, so most tasks have none of them. Never assemble a key name from a pattern.
                Copy keys verbatim from the supplied evidenceKeys list and use nothing else — one
                invented key discards the entire report.
                Use only supplied evidence keys and de-identified TASK-, GOAL-, and MEMBER- refs.
                Refs belong exclusively in the structured taskRefs, objectiveRefs, and ownerRef
                fields. Never write TASK-, GOAL-, or MEMBER- inside any text field; the reader sees
                resolved titles and names from those structured fields, so an alias in prose is
                unreadable. Describe the work in words instead ("기한이 지난 검토 업무").
                Never invent or calculate a number or date. Any numeric or date claim must use an
                exact placeholder such as {{tasks.delayed}} or {{task.TASK-01.blockerReviewDate}}.
                Put every placeholder key in the same item's evidenceKeys.
                A placeholder is replaced by a server-rendered string that carries no unit of its
                own unless listed below, so you must write the unit and the surrounding grammar.
                Rendered forms: tasks.*, checklist.*, objective.*, task.*.checklist*, daily.*,
                flow.peakCompletedCount and flow.zeroCompletionDays give a bare integer ("12");
                time.averageCompletionHours gives a bare integer counting hours ("60");
                rates.* gives an integer with a percent sign ("42%%"); comparison count deltas give
                a signed integer ("+6"); comparison.completionRateDelta,
                comparison.checklistRateDelta and comparison.onTimeRateDelta give a signed integer
                with a percentage-point suffix ("-19%%p"); comparison.avgCompletionHoursDelta gives
                a signed integer counting hours ("-10"); task.*.blockedHours,
                task.*.approvalWaitHours, task.*.startLagHours,
                flow.longestBlockedHours and flow.longestApprovalWaitHours give a bare integer
                counting hours ("84"); task.*.idleDays gives a bare integer counting days ("4");
                member.*.completionRate, member.*.onTimeRate and member.*.checklistRate give an
                integer with a percent sign; member.*.score gives an integer from 0 to 100;
                member.*.grade, members.topGrade and members.lowestGrade give a single letter or
                the word NOT_RATED; member.*.rank gives a pre-rendered position such as "2/5";
                task.*.dueDate, task.*.blockerReviewDate and flow.peakCompletedDay give an ISO date.
                Always name the unit a bare integer counts, and never let a placeholder stand alone
                as a sentence subject: write "지연 업무 건수가 {{tasks.delayed}}건" and not
                "{{tasks.delayed}}가".
                When writing Korean, attach the counter noun before the particle
                ("{{tasks.delayed}}건으로", "{{time.averageCompletionHours}}시간으로",
                "{{comparison.avgCompletionHoursDelta}}시간") so the particle follows that noun.
                For a rendered value that already ends in a symbol, choose the particle for how it
                is read aloud: "{{rates.completion}}로" because 퍼센트 ends in a vowel, never
                "{{rates.completion}}으로".
                No digit may appear in any text field except inside a placeholder. This rejects
                years, calendar dates, counts, percentages, hours, and ordinals written out as
                digits: write "{{flow.peakCompletedDay}}" not a literal calendar date,
                "{{tasks.delayed}}건" not "6건", "{{rates.onTime}}" not a literal percentage
                such as eighty-three percent. Spell small quantities as words instead
                ("두 건 남았다") only when no evidence key covers them.
                Do not rank, score, praise, blame, or infer attitude or productivity of a person.
                Reference affected taskRefs and objectiveRefs when known. List a taskRef only when
                at least one evidence key cited by that same item actually covers it: a
                task.<thatRef>.* key, an objective key for the objective it belongs to, or a status
                metric matching its own status or due state. Drop a task you cannot back this way
                rather than adding it to the list.
                Include a limitation citing coverage.partial only when that exact key appears in the
                supplied evidenceKeys list. If it is absent the history is complete — say nothing
                about data coverage and do not name the key.
                Do not use markdown or numbered-list prefixes in text fields.
                Return the report in %s.
                """.formatted("en".equals(language) ? "English" : "Korean");
    }

    public Map<String, Object> responseFormat() {
        Map<String, Object> narrativeItem = itemSchema("textTemplate");
        Map<String, Object> riskItem = objectSchema(Map.of(
                "severity", Map.of("type", "string", "enum", List.of("LOW", "MEDIUM", "HIGH")),
                "textTemplate", narrativeTextSchema(600),
                "evidenceKeys", stringArray(1, 10),
                "taskRefs", stringArray(0, 5),
                "objectiveRefs", stringArray(0, 3)),
                List.of("severity", "textTemplate", "evidenceKeys", "taskRefs", "objectiveRefs"));
        Map<String, Object> actionItem = objectSchema(Map.of(
                "priority", Map.of("type", "integer", "enum", List.of(1, 2, 3)),
                "actionTemplate", narrativeTextSchema(500),
                "reasonTemplate", narrativeTextSchema(500),
                "ownerRef", Map.of("type", "string"),
                "evidenceKeys", stringArray(1, 10),
                "taskRefs", stringArray(0, 5),
                "objectiveRefs", stringArray(0, 3)),
                List.of("priority", "actionTemplate", "reasonTemplate", "ownerRef",
                        "evidenceKeys", "taskRefs", "objectiveRefs"));
        Map<String, Object> decisionItem = objectSchema(Map.of(
                "questionTemplate", narrativeTextSchema(500),
                "impactTemplate", narrativeTextSchema(500),
                "evidenceKeys", stringArray(1, 10),
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
        // Narrative가 null 리스트를 List.of()로 정규화하므로(구버전 행 보호) 슬롯 누락은
        // 정규화 전 raw payload에서 잡아야 한다. 그러지 않으면 슬롯을 빼먹은 응답이 통과한다.
        String missing = missingGeneratedSlot(value);
        if (missing != null) {
            log.warn("event=AI_REPORT_VALIDATION outcome=GENERATED_SLOT_MISSING slot={}", missing);
            throw new IllegalArgumentException("Generated narrative slot is missing: " + missing);
        }
        Narrative narrative = value.toNarrative();
        if (narrative.topActions().stream().anyMatch(item -> blank(item.ownerRef()))) {
            throw new IllegalArgumentException("Generated action owner is required");
        }
        return withRenumberedActions(clampSections(narrative));
    }

    private String missingGeneratedSlot(GeneratedNarrative value) {
        if (blank(value.headlineTemplate)) return "headlineTemplate";
        if (value.summary == null) return "summary";
        if (value.changes == null) return "changes";
        if (value.achievements == null) return "achievements";
        if (value.risks == null) return "risks";
        if (value.topActions == null || value.topActions.isEmpty()) return "topActions";
        if (value.leaderDecisions == null) return "leaderDecisions";
        if (value.limitations == null) return "limitations";
        return null;
    }

    /**
     * 응답 스키마의 maxItems는 provider가 항상 지키지는 않는다. 초과분만 잘라 계약 상한에 맞춘다.
     * 통째로 버리면 한 번의 초과가 생성 전체를 실패로 만든다.
     */
    private Narrative clampSections(Narrative narrative) {
        return new Narrative(narrative.headlineTemplate(), narrative.summary(),
                clamp(narrative.changes()), clamp(narrative.achievements()),
                clamp(narrative.risks()), narrative.topActions(),
                clamp(narrative.leaderDecisions()), clamp(narrative.limitations()));
    }

    private <T> List<T> clamp(List<T> items) {
        return items == null || items.size() <= 3 ? items : List.copyOf(items.subList(0, 3));
    }

    /**
     * priority는 읽기 순서를 뜻하므로 모델이 매긴 값 순으로 정렬한 뒤 1..n으로 다시 부여한다.
     * 순서 정보는 그대로 보존되고, 중복·건너뛴 번호 때문에 생성 전체가 버려지는 일만 없어진다.
     */
    private Narrative withRenumberedActions(Narrative narrative) {
        List<ActionNarrativeItem> ordered = narrative.topActions().stream()
                .sorted(java.util.Comparator.comparingInt(ActionNarrativeItem::priority))
                .limit(3)
                .toList();
        List<ActionNarrativeItem> renumbered = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ActionNarrativeItem item = ordered.get(index);
            renumbered.add(new ActionNarrativeItem(index + 1, item.actionTemplate(),
                    item.reasonTemplate(), item.ownerRef(), item.evidenceKeys(),
                    item.taskRefs(), item.objectiveRefs()));
        }
        return new Narrative(narrative.headlineTemplate(), narrative.summary(),
                narrative.changes(), narrative.achievements(), narrative.risks(),
                List.copyOf(renumbered), narrative.leaderDecisions(), narrative.limitations());
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
                        item.onTimeRatePercent(),
                        frozenText(snapshot, "member." + item.memberLabel() + ".grade"),
                        frozenInt(snapshot, "member." + item.memberLabel() + ".score"),
                        frozenText(snapshot, "member." + item.memberLabel() + ".rank")))
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

    // 등급·순위는 동결 evidence만 읽는다. 키가 없는 구버전 리포트는 자동으로 null이 된다.
    private String frozenText(ReportSnapshot snapshot, String key) {
        EvidenceValue value = snapshot.evidence().get(key);
        return value == null ? null : value.value();
    }

    private Integer frozenInt(ReportSnapshot snapshot, String key) {
        String value = frozenText(snapshot, key);
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
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
        // 별칭 검사는 placeholder를 제거한 산문에만 적용한다. {{task.TASK-01.dueDate}} 같은 근거
        // placeholder는 키로 따로 검증되고 렌더 결과는 값이므로 별칭이 독자에게 노출되지 않는다.
        String prose = removePlaceholders(part.template());
        if (MEMBER_ALIAS.matcher(prose).find()) {
            throw invalid("AI_REPORT_PERSON_COMPARISON_INVALID", status,
                    "팀원 식별자는 구조화된 담당자 필드에서만 사용할 수 있습니다.");
        }
        if (LOCAL_ALIAS.matcher(prose).find()) {
            log.warn("event=AI_REPORT_VALIDATION outcome=ALIAS_IN_TEXT itemKeys={}",
                    part.evidenceKeys());
            throw invalid("AI_REPORT_REFERENCE_TEXT_INVALID", status,
                    "업무·목표 식별자는 구조화된 참조 필드에서만 사용할 수 있습니다.");
        }
        if (containsLiteralNumber(prose)) {
            // 위반한 숫자 토큰만 남긴다. 서술 본문은 로그에 넣지 않는다.
            log.warn("event=AI_REPORT_VALIDATION outcome=LITERAL_NUMBER digits={} itemKeys={}",
                    literalNumbers(prose), part.evidenceKeys());
            throw invalid("AI_REPORT_NUMERIC_TEXT_INVALID", status,
                    "숫자와 날짜는 서버 근거 placeholder로만 입력해 주세요.");
        }
        if (!allowedEvidence.containsAll(part.evidenceKeys())
                || !allowedTasks.containsAll(part.taskRefs())
                || !allowedObjectives.containsAll(part.objectiveRefs())
                || !allowedMembers.containsAll(part.memberRefs())) {
            // 모델이 지어낸 키·참조만 남긴다. 어떤 계열을 조립하려 했는지가 프롬프트 수정의 단서다.
            log.warn("event=AI_REPORT_VALIDATION outcome=UNKNOWN_REFERENCE "
                    + "keys={} taskRefs={} objectiveRefs={} memberRefs={}",
                    part.evidenceKeys().stream().filter(key -> !allowedEvidence.contains(key))
                            .toList(),
                    part.taskRefs().stream().filter(ref -> !allowedTasks.contains(ref)).toList(),
                    part.objectiveRefs().stream()
                            .filter(ref -> !allowedObjectives.contains(ref)).toList(),
                    part.memberRefs().stream()
                            .filter(ref -> !allowedMembers.contains(ref)).toList());
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
        for (String ref : part.taskRefs()) {
            if (taskMatchesEvidence(tasks.get(ref), part.evidenceKeys())) continue;
            TaskContext task = tasks.get(ref);
            // 별칭과 근거 키만 남긴다. 서술 본문은 로그에 넣지 않는다.
            log.warn("event=AI_REPORT_VALIDATION outcome=EVIDENCE_TASK_MISMATCH "
                    + "taskRef={} status={} dueState={} itemKeys={} itemRefs={}",
                    ref, task == null ? "UNKNOWN" : task.status(),
                    task == null ? "UNKNOWN" : task.dueState(),
                    part.evidenceKeys(), part.taskRefs());
            throw invalid("AI_REPORT_EVIDENCE_INVALID", status,
                    "근거 업무가 해당 지표의 의미와 일치하지 않습니다.");
        }
    }

    private Map<String, TaskContext> taskContexts(ReportSnapshot snapshot) {
        return snapshot.aiContext().tasks().stream()
                .collect(Collectors.toMap(TaskContext::taskRef, value -> value));
    }

    /**
     * 인용된 근거 중 업무 단위 의미가 있는 키가 하나도 없으면 참조를 제약하지 않고, 있으면 그중
     * 최소 하나와 맞아야 한다. 전부와 맞기를 요구하면 "지연·보류가 함께 늘었다"처럼 신호를 여럿
     * 인용하는 문장이 구조적으로 통과할 수 없다. 무관한 업무를 붙이는 것은 여전히 막힌다.
     */
    private boolean taskMatchesEvidence(TaskContext task, List<String> evidenceKeys) {
        if (task == null) return false;
        boolean applicable = false;
        for (String key : evidenceKeys) {
            switch (fit(task, key)) {
                case MATCH -> { return true; }
                case MISMATCH -> applicable = true;
                case NOT_APPLICABLE -> { }
            }
        }
        return !applicable;
    }

    /**
     * 근거 키가 한 업무에 대해 어떤 뜻인지 판정한다. 새 키를 추가할 때 여기에 case를 넣지 않으면
     * 그 키는 업무 참조에 아무 제약을 걸지 못하고 근거-업무 연결이 장식이 된다.
     */
    private EvidenceFit fit(TaskContext task, String key) {
        if (key.startsWith("task.")) {
            return scopedRef(key).equals(task.taskRef()) && taskScopedMatches(task, key)
                    ? EvidenceFit.MATCH : EvidenceFit.MISMATCH;
        }
        if (key.startsWith("objective.")) {
            return scopedRef(key).equals(task.objectiveRef())
                    ? EvidenceFit.MATCH : EvidenceFit.MISMATCH;
        }
        // 팀원 범위 근거는 그 팀원이 담당한 업무만 함께 참조할 수 있다. members.* 집계는 해당 없음.
        if (key.startsWith("member.")) {
            return scopedRef(key).equals(task.memberRef())
                    ? EvidenceFit.MATCH : EvidenceFit.MISMATCH;
        }
        // daily.<date>.created는 TaskContext에 생성일이 없어 업무 단위로 확인할 수 없다.
        if (key.startsWith("daily.")) {
            return key.endsWith(".completed")
                    ? fit("COMPLETED".equals(task.status())) : EvidenceFit.NOT_APPLICABLE;
        }
        return switch (key) {
            case "tasks.completed" -> fit("COMPLETED".equals(task.status()));
            case "tasks.active" ->
                    fit(Set.of("IN_PROGRESS", "ON_HOLD").contains(task.status()));
            case "tasks.onHold" -> fit("ON_HOLD".equals(task.status()));
            case "tasks.delayed" -> fit("OVERDUE".equals(task.dueState()));
            case "tasks.highPriority" ->
                    fit(Set.of("HIGH", "URGENT").contains(task.priority()));
            case "tasks.requested" -> fit("REQUESTED".equals(task.status()));
            case "tasks.todo" -> fit("TODO".equals(task.status()));
            case "tasks.inProgress" -> fit("IN_PROGRESS".equals(task.status()));
            case "tasks.rejected" -> fit("REJECTED".equals(task.status()));
            case "tasks.cancelled" -> fit("CANCELLED".equals(task.status()));
            case "checklist.total" -> fit(task.checklistTotal() > 0);
            case "checklist.completed" -> fit(task.checklistCompleted() > 0);
            case "time.averageCompletionHours", "flow.peakCompletedDay",
                    "flow.peakCompletedCount" -> fit("COMPLETED".equals(task.status()));
            case "flow.longestBlockedHours" -> fit(blocked(task));
            case "flow.overdueReviewCount" -> fit(task.blockerReviewWindow() != null);
            case "flow.reopenedTaskCount" -> fit(changed(task, "REOPENED"));
            default -> EvidenceFit.NOT_APPLICABLE;
        };
    }

    private EvidenceFit fit(boolean matched) {
        return matched ? EvidenceFit.MATCH : EvidenceFit.MISMATCH;
    }

    private boolean taskScopedMatches(TaskContext task, String key) {
        return switch (key.substring(key.lastIndexOf('.') + 1)) {
            case "dueDate" -> !"NONE".equals(task.dueState());
            case "blockerReviewDate" -> task.blockerReviewWindow() != null;
            case "checklistTotal", "checklistCompleted" -> task.checklistTotal() > 0;
            case "blockedHours" -> blocked(task);
            case "reopenCount" -> changed(task, "REOPENED");
            case "assigneeChangeCount" -> changed(task, "ASSIGNEE_CHANGED");
            default -> true;
        };
    }

    private boolean blocked(TaskContext task) {
        return "ON_HOLD".equals(task.status()) || changed(task, "BLOCKED");
    }

    private boolean changed(TaskContext task, String change) {
        return task.changes() != null && task.changes().contains(change);
    }

    private enum EvidenceFit { MATCH, MISMATCH, NOT_APPLICABLE }

    // task.TASK-01.dueDate / objective.GOAL-02.delayed 처럼 두 번째 구간이 별칭이다.
    private String scopedRef(String key) {
        int start = key.indexOf('.') + 1;
        int end = key.indexOf('.', start);
        return end < 0 ? key.substring(start) : key.substring(start, end);
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
        String reason = shapeViolation(narrative, requireActionOwner);
        if (reason != null) {
            // 위반한 슬롯 이름만 남긴다. 서술 본문은 로그에 넣지 않는다.
            log.warn("event=AI_REPORT_VALIDATION outcome=SHAPE_INVALID reason={}", reason);
            throw new ApplicationException("AI_REPORT_RESPONSE_INVALID", HttpStatus.BAD_GATEWAY,
                    "AI 리포트 응답을 확인하지 못했습니다.");
        }
    }

    private String shapeViolation(Narrative narrative, boolean requireActionOwner) {
        if (narrative == null) return "narrative=null";
        if (blank(narrative.headlineTemplate())) return "headline=blank";
        if (invalid(narrative.summary())) return "summary";
        if (narrative.changes() == null || narrative.changes().size() > 3) return "changes=size";
        if (narrative.achievements() == null || narrative.achievements().size() > 3) {
            return "achievements=size";
        }
        if (narrative.risks() == null || narrative.risks().size() > 3) return "risks=size";
        if (narrative.topActions() == null || narrative.topActions().isEmpty()
                || narrative.topActions().size() > 3) {
            return "topActions=size:" + (narrative.topActions() == null
                    ? "null" : narrative.topActions().size());
        }
        if (narrative.leaderDecisions() == null || narrative.leaderDecisions().size() > 3) {
            return "leaderDecisions=size";
        }
        if (narrative.limitations() == null || narrative.limitations().size() > 3) {
            return "limitations=size";
        }
        if (narrative.changes().stream().anyMatch(this::invalid)) return "changes=item";
        if (narrative.achievements().stream().anyMatch(this::invalid)) return "achievements=item";
        if (narrative.risks().stream().anyMatch(this::invalid)) return "risks=item";
        if (narrative.topActions().stream()
                .anyMatch(item -> invalid(item, requireActionOwner))) {
            return "topActions=item";
        }
        if (narrative.leaderDecisions().stream().anyMatch(this::invalid)) {
            return "leaderDecisions=item";
        }
        if (narrative.limitations().stream().anyMatch(this::invalid)) return "limitations=item";
        return null;
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

    private List<String> literalNumbers(String value) {
        Matcher matcher = Pattern.compile("\\d+").matcher(value);
        List<String> found = new ArrayList<>();
        while (matcher.find()) found.add(matcher.group());
        return found;
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
                "evidenceKeys", stringArray(1, 10),
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
