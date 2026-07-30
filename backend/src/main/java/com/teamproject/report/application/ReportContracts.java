package com.teamproject.report.application;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReportContracts {
    private ReportContracts() {}

    public record GenerateWeeklyReport(
            Long userId, Long groupId, LocalDate weekStart, String language) {}
    public record FindWeeklyReport(
            Long userId, Long groupId, LocalDate weekStart, String language) {}
    public record FindWeeklyReportById(Long userId, Long groupId, Long reportId) {}
    public record EditWeeklyReportDraft(
            Long userId, Long groupId, Long reportId, long expectedEditorVersion,
            Narrative content) {}
    public record RegenerateWeeklyReport(
            Long userId, Long groupId, Long reportId, long expectedEditorVersion) {}
    public record FinalizeWeeklyReport(
            Long userId, Long groupId, Long reportId, long expectedEditorVersion) {}

    public record StatusMetrics(long requested, long todo, long inProgress, long onHold,
            long completed, long rejected, long cancelled, long delayed) {}
    public record DailyMetric(LocalDate date, long created, long completed) {}
    // 구버전 metrics_json은 신규 컴포넌트를 0/null로 역직렬화한다. 등급은 evidence에
    // member.*.grade 키가 있을 때만 노출하므로 과거 리포트에 지어낸 등급이 생기지 않는다.
    public record MemberMetric(String memberLabel, long assigned, long active, long completed,
            long delayed, Integer onTimeRatePercent,
            long onHold, long checklistTotal, long checklistCompleted,
            Integer completionRatePercent) {
        public MemberMetric(String memberLabel, long assigned, long active, long completed,
                long delayed, Integer onTimeRatePercent) {
            this(memberLabel, assigned, active, completed, delayed, onTimeRatePercent,
                    0, 0, 0, null);
        }
    }
    public record RiskSignal(String code, String severity, List<String> evidenceKeys) {}
    public enum HistoryCoverageStatus { COMPLETE, PARTIAL }
    public record HistoryCoverage(HistoryCoverageStatus status, Instant trackingStartedAt) {
        public boolean partial() { return status == HistoryCoverageStatus.PARTIAL; }
    }
    public record ChecklistMetrics(long total, long completed, Integer completionRatePercent) {}

    public record MetricsSnapshot(
            LocalDate periodStart,
            LocalDate periodEnd,
            long totalTasks,
            StatusMetrics statuses,
            Integer completionRatePercent,
            Integer onTimeRatePercent,
            Long averageCompletionHours,
            HistoryCoverage historyCoverage,
            ChecklistMetrics checklist,
            List<DailyMetric> daily,
            List<MemberMetric> members,
            List<RiskSignal> riskSignals,
            Map<String, Integer> evidence) {
        public MetricsSnapshot(LocalDate periodStart, LocalDate periodEnd, long totalTasks,
                StatusMetrics statuses, Integer completionRatePercent, Integer onTimeRatePercent,
                Long averageCompletionHours, List<DailyMetric> daily, List<MemberMetric> members,
                List<RiskSignal> riskSignals, Map<String, Integer> evidence) {
            this(periodStart, periodEnd, totalTasks, statuses, completionRatePercent,
                    onTimeRatePercent, averageCompletionHours,
                    new HistoryCoverage(HistoryCoverageStatus.PARTIAL, null),
                    new ChecklistMetrics(0, 0, null),
                    daily, members, riskSignals, evidence);
        }
    }

    public record ComparisonMetrics(
            boolean available,
            Integer totalTasksDelta,
            Integer completedTasksDelta,
            Integer delayedTasksDelta,
            Integer onHoldTasksDelta,
            Integer completionRateDeltaPercent,
            Integer checklistCompletionRateDeltaPercent,
            Integer onTimeRateDeltaPercent,
            Integer averageCompletionHoursDelta) {
        // 신규 컴포넌트가 없는 저장본은 null로 역직렬화되고, 근거 키도 그때는 생성되지 않는다.
        public ComparisonMetrics(boolean available, Integer totalTasksDelta,
                Integer completedTasksDelta, Integer delayedTasksDelta,
                Integer onHoldTasksDelta, Integer completionRateDeltaPercent,
                Integer checklistCompletionRateDeltaPercent) {
            this(available, totalTasksDelta, completedTasksDelta, delayedTasksDelta,
                    onHoldTasksDelta, completionRateDeltaPercent,
                    checklistCompletionRateDeltaPercent, null, null);
        }
    }

    public record EvidenceValue(String key, String label, String value, String kind) {}

    public record TaskContext(
            String taskRef,
            String status,
            String priority,
            String dueState,
            int checklistTotal,
            int checklistCompleted,
            String blockerType,
            String blockerNextActionType,
            String blockerReviewWindow,
            String objectiveRef,
            String memberRef,
            List<String> changes) {}

    public record ObjectiveContext(
            String objectiveRef,
            int taskTotal,
            int completed,
            int onHold,
            int delayed) {}

    public record AiReportContext(
            MetricsSnapshot metrics,
            ComparisonMetrics comparison,
            List<TaskContext> tasks,
            List<ObjectiveContext> objectives,
            Set<String> evidenceKeys,
            Set<String> memberRefs) {
        public AiReportContext(MetricsSnapshot metrics, ComparisonMetrics comparison,
                List<TaskContext> tasks, List<ObjectiveContext> objectives,
                Set<String> evidenceKeys) {
            this(metrics, comparison, tasks, objectives, evidenceKeys,
                    metrics.members().stream()
                            .map(MemberMetric::memberLabel)
                            .collect(java.util.stream.Collectors.toUnmodifiableSet()));
        }
    }

    public record LocalReference(
            String ref,
            String type,
            String label,
            String url,
            String secondaryLabel) {}

    public record ReferenceIndex(List<LocalReference> references) {}

    public record ReportSnapshot(
            MetricsSnapshot metrics,
            ComparisonMetrics comparison,
            AiReportContext aiContext,
            ReferenceIndex references,
            Map<String, EvidenceValue> evidence) {}

    // 구버전 저장본과 provider 응답 모두 참조 배열을 생략할 수 있다. 여기서 정규화하지 않으면
    // invalidRefs가 null을 거부해 과거 리포트가 영구히 읽히지 않는다.
    private static List<String> refs(List<String> values) {
        return values == null ? List.of() : values;
    }

    public record NarrativeItem(
            String textTemplate,
            List<String> evidenceKeys,
            List<String> taskRefs,
            List<String> objectiveRefs) {
        public NarrativeItem {
            evidenceKeys = refs(evidenceKeys);
            taskRefs = refs(taskRefs);
            objectiveRefs = refs(objectiveRefs);
        }
        public NarrativeItem(String textTemplate, List<String> evidenceKeys) {
            this(textTemplate, evidenceKeys, List.of(), List.of());
        }
        public String text() { return textTemplate; }
    }

    public record RiskNarrativeItem(
            String severity,
            String textTemplate,
            List<String> evidenceKeys,
            List<String> taskRefs,
            List<String> objectiveRefs) {
        public RiskNarrativeItem {
            evidenceKeys = refs(evidenceKeys);
            taskRefs = refs(taskRefs);
            objectiveRefs = refs(objectiveRefs);
        }
    }

    public record ActionNarrativeItem(
            int priority,
            String actionTemplate,
            String reasonTemplate,
            String ownerRef,
            List<String> evidenceKeys,
            List<String> taskRefs,
            List<String> objectiveRefs) {
        public ActionNarrativeItem {
            evidenceKeys = refs(evidenceKeys);
            taskRefs = refs(taskRefs);
            objectiveRefs = refs(objectiveRefs);
        }
        public ActionNarrativeItem(int priority, String actionTemplate, String reasonTemplate,
                List<String> evidenceKeys, List<String> taskRefs,
                List<String> objectiveRefs) {
            this(priority, actionTemplate, reasonTemplate, null,
                    evidenceKeys, taskRefs, objectiveRefs);
        }
    }

    public record DecisionNarrativeItem(
            String questionTemplate,
            String impactTemplate,
            List<String> evidenceKeys,
            List<String> taskRefs,
            List<String> objectiveRefs) {
        public DecisionNarrativeItem {
            evidenceKeys = refs(evidenceKeys);
            taskRefs = refs(taskRefs);
            objectiveRefs = refs(objectiveRefs);
        }
    }

    public record Narrative(
            String headlineTemplate,
            NarrativeItem summary,
            List<NarrativeItem> changes,
            List<NarrativeItem> achievements,
            List<RiskNarrativeItem> risks,
            List<ActionNarrativeItem> topActions,
            List<DecisionNarrativeItem> leaderDecisions,
            List<NarrativeItem> limitations) {
        // 신규 슬롯이 추가되면 구버전 행에서 null로 역직렬화된다. validateShape가 null 리스트를
        // 거부하므로 여기서 정규화해야 view·PDF·초안편집 경로가 모두 과거 리포트를 계속 읽는다.
        public Narrative {
            changes = changes == null ? List.of() : changes;
            achievements = achievements == null ? List.of() : achievements;
            risks = risks == null ? List.of() : risks;
            topActions = topActions == null ? List.of() : topActions;
            leaderDecisions = leaderDecisions == null ? List.of() : leaderDecisions;
            limitations = limitations == null ? List.of() : limitations;
        }

        public Narrative(String headline, String executiveSummary,
                List<NarrativeItem> highlights, List<NarrativeItem> risks,
                List<NarrativeItem> nextWeekActions, List<NarrativeItem> dataLimitations) {
            this(headline,
                    new NarrativeItem(executiveSummary, summaryEvidence(
                            highlights, risks, nextWeekActions, dataLimitations)),
                    List.of(),
                    highlights,
                    risks.stream().map(item -> new RiskNarrativeItem(
                            "MEDIUM", item.textTemplate(), item.evidenceKeys(),
                            item.taskRefs(), item.objectiveRefs())).toList(),
                    actions(nextWeekActions,
                            summaryEvidence(highlights, risks, nextWeekActions, dataLimitations)),
                    List.of(),
                    dataLimitations);
        }

        private static List<String> summaryEvidence(List<NarrativeItem>... groups) {
            return java.util.Arrays.stream(groups)
                    .flatMap(List::stream)
                    .flatMap(item -> item.evidenceKeys().stream())
                    .distinct()
                    .limit(8)
                    .toList();
        }

        private static List<ActionNarrativeItem> actions(List<NarrativeItem> source,
                List<String> fallbackEvidence) {
            if (source.isEmpty()) {
                return List.of(new ActionNarrativeItem(1,
                        "근거가 있는 업무를 우선 점검하세요.",
                        "변화와 위험을 실행 계획에 반영해야 합니다.",
                        fallbackEvidence, List.of(), List.of()));
            }
            return java.util.stream.IntStream.range(0, source.size())
                    .mapToObj(index -> {
                        NarrativeItem item = source.get(index);
                        return new ActionNarrativeItem(index + 1,
                                item.textTemplate(),
                                "근거를 확인하고 실행 계획을 구체화하세요.",
                                item.evidenceKeys(), item.taskRefs(),
                                item.objectiveRefs());
                    }).limit(3).toList();
        }

        public String headline() { return headlineTemplate; }
        public String executiveSummary() { return summary.textTemplate(); }
        public List<NarrativeItem> highlights() { return achievements; }
        public List<NarrativeItem> nextWeekActions() {
            return topActions.stream().map(item -> new NarrativeItem(
                    item.actionTemplate(), item.evidenceKeys(),
                    item.taskRefs(), item.objectiveRefs())).toList();
        }
        public List<NarrativeItem> dataLimitations() { return limitations; }
    }

    public record NarrativeItemView(
            String text,
            List<String> evidenceKeys,
            List<LocalReference> taskRefs,
            List<LocalReference> objectiveRefs) {}

    public record RiskNarrativeItemView(
            String severity,
            String text,
            List<String> evidenceKeys,
            List<LocalReference> taskRefs,
            List<LocalReference> objectiveRefs) {}

    public record ActionNarrativeItemView(
            int priority,
            String action,
            String reason,
            LocalReference owner,
            List<String> evidenceKeys,
            List<LocalReference> taskRefs,
            List<LocalReference> objectiveRefs) {}

    public record DecisionNarrativeItemView(
            String question,
            String impact,
            List<String> evidenceKeys,
            List<LocalReference> taskRefs,
            List<LocalReference> objectiveRefs) {}

    public record NarrativeView(
            String headline,
            NarrativeItemView summary,
            List<NarrativeItemView> changes,
            List<NarrativeItemView> achievements,
            List<RiskNarrativeItemView> risks,
            List<ActionNarrativeItemView> topActions,
            List<DecisionNarrativeItemView> leaderDecisions,
            List<NarrativeItemView> limitations) {
        public String executiveSummary() { return summary.text(); }
        public List<NarrativeItemView> highlights() { return achievements; }
        public List<NarrativeItemView> nextWeekActions() {
            return topActions.stream().map(item -> new NarrativeItemView(
                    item.action(), item.evidenceKeys(),
                    item.taskRefs(), item.objectiveRefs())).toList();
        }
        public List<NarrativeItemView> dataLimitations() { return limitations; }
    }

    // grade/score/rank는 evidence에 member.*.grade 키가 있을 때만 채운다. 구버전 리포트는 null이고
    // 화면이 등급 열을 숨긴다 — 0/null로 역직렬화된 구 지표에서 등급을 지어내지 않기 위한 장치다.
    public record MemberWorkView(
            LocalReference member,
            long assigned,
            long active,
            long completed,
            long delayed,
            Integer onTimeRatePercent,
            String grade,
            Integer score,
            String rank) {
        public MemberWorkView(LocalReference member, long assigned, long active, long completed,
                long delayed, Integer onTimeRatePercent) {
            this(member, assigned, active, completed, delayed, onTimeRatePercent, null, null, null);
        }
    }

    public record TaskWorkView(
            LocalReference task,
            LocalReference assignee,
            LocalReference objective,
            String status,
            String priority,
            String dueState,
            int checklistTotal,
            int checklistCompleted,
            String blockerType,
            String blockerNextActionType,
            String blockerReviewWindow,
            List<String> changes) {}

    public record OperationalView(
            String groupName,
            String healthStatus,
            String confidenceLevel,
            int memberCount,
            int activeMemberCount,
            List<MemberWorkView> members,
            List<TaskWorkView> tasks) {}

    public record AiGenerationInput(AiReportContext context, String language) {
        public AiGenerationInput(MetricsSnapshot metrics, String language) {
            this(new AiReportContext(metrics,
                    new ComparisonMetrics(false, null, null, null, null, null, null),
                    List.of(), List.of(), metrics.evidence().keySet()), language);
        }
        public MetricsSnapshot metrics() { return context.metrics(); }
    }
    public record AiGenerationResult(Narrative narrative, String model,
            int inputTokens, int outputTokens, int totalTokens) {}

    public record WeeklyReportView(
            Long reportId,
            String status,
            String publicationStatus,
            LocalDate periodStart,
            LocalDate periodEnd,
            String language,
            LocalDateTime generatedAt,
            LocalDateTime finalizedAt,
            int revision,
            long editorVersion,
            boolean cached,
            MetricsSnapshot metrics,
            ComparisonMetrics comparison,
            Map<String, EvidenceValue> evidence,
            OperationalView operations,
            NarrativeView analysis,
            Narrative draft,
            String gradeRule) {
        public WeeklyReportView(Long reportId, String status, String publicationStatus,
                LocalDate periodStart, LocalDate periodEnd, String language,
                LocalDateTime generatedAt, LocalDateTime finalizedAt, int revision,
                long editorVersion, boolean cached, MetricsSnapshot metrics,
                ComparisonMetrics comparison, Map<String, EvidenceValue> evidence,
                OperationalView operations, NarrativeView analysis, Narrative draft) {
            this(reportId, status, publicationStatus, periodStart, periodEnd, language,
                    generatedAt, finalizedAt, revision, editorVersion, cached, metrics,
                    comparison, evidence, operations, analysis, draft, null);
        }
    }

    public record RevisionSummary(
            Long reportId,
            int revision,
            String status,
            String publicationStatus,
            LocalDateTime generatedAt,
            LocalDateTime finalizedAt) {}
}
