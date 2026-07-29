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
    public record MemberMetric(String memberLabel, long assigned, long active, long completed,
            long delayed, Integer onTimeRatePercent) {}
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
            Integer checklistCompletionRateDeltaPercent) {}

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

    public record NarrativeItem(
            String textTemplate,
            List<String> evidenceKeys,
            List<String> taskRefs,
            List<String> objectiveRefs) {
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
            List<String> objectiveRefs) {}

    public record ActionNarrativeItem(
            int priority,
            String actionTemplate,
            String reasonTemplate,
            String ownerRef,
            List<String> evidenceKeys,
            List<String> taskRefs,
            List<String> objectiveRefs) {
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
            List<String> objectiveRefs) {}

    public record Narrative(
            String headlineTemplate,
            NarrativeItem summary,
            List<NarrativeItem> changes,
            List<NarrativeItem> achievements,
            List<RiskNarrativeItem> risks,
            List<ActionNarrativeItem> topActions,
            List<DecisionNarrativeItem> leaderDecisions,
            List<NarrativeItem> limitations) {
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

    public record MemberWorkView(
            LocalReference member,
            long assigned,
            long active,
            long completed,
            long delayed,
            Integer onTimeRatePercent) {}

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
            Narrative draft) {}

    public record RevisionSummary(
            Long reportId,
            int revision,
            String status,
            String publicationStatus,
            LocalDateTime generatedAt,
            LocalDateTime finalizedAt) {}
}
