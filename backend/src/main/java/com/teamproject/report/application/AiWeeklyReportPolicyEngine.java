package com.teamproject.report.application;

import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * v7-2 AI 주간 리포트의 위험 후보 생성 및 업무 신호 부여 정책 엔진 (M3).
 * Snapshot을 전달받아 결정론적 규칙에 따라 각 업무에 신호/허용 조치 코드를 부여하고,
 * 최상위 위험 후보(RiskCandidates, 최대 10개)를 생성해 새 Snapshot을 반환한다.
 */
@Component
public class AiWeeklyReportPolicyEngine {

    private static final Map<String, Integer> RISK_CODE_PRECEDENCE = Map.ofEntries(
            Map.entry("APPROVED_UNASSIGNED_OVERDUE", 1),
            Map.entry("APPROVED_UNASSIGNED", 2),
            Map.entry("OVERDUE_ACTIVE", 3),
            Map.entry("WORKLOAD_CONCENTRATION", 4),
            Map.entry("COMPLETION_RATE_DROP", 5),
            Map.entry("SCHEDULE_CONFLICT", 6),
            Map.entry("APPROVAL_PENDING", 7),
            Map.entry("CHECKLIST_NOT_STARTED", 8),
            Map.entry("BACKLOG_GROWTH", 9),
            Map.entry("UNRESOLVED_MENTION", 10),
            Map.entry("ON_HOLD_LONG", 11),
            Map.entry("RESOURCE_MISSING", 12)
    );

    public AiWeeklyReportSnapshotV1 evaluate(AiWeeklyReportSnapshotV1 snapshot) {
        if (snapshot == null) {
            return null;
        }

        List<SnapshotTask> updatedTasks = new ArrayList<>();
        List<RawRiskCandidate> rawCandidates = new ArrayList<>();

        for (SnapshotTask task : snapshot.tasks()) {
            List<SignalCode> signals = new ArrayList<>();
            List<DecisionOptionCode> options = new ArrayList<>();
            List<ExecutionStepCode> steps = new ArrayList<>();
            List<CompletionSignalCode> completions = new ArrayList<>();

            TaskStatus status = task.status();
            DueState dueState = task.dueState();
            boolean isCompleted = (status == TaskStatus.COMPLETED);

            if (status == TaskStatus.REQUESTED) {
                signals.add(SignalCode.REQUESTED_PENDING);
                options.add(DecisionOptionCode.APPROVE_SCOPE);
                steps.add(ExecutionStepCode.RECORD_SCOPE_DECISION);
                completions.add(CompletionSignalCode.SCOPE_DECISION_RECORDED);
            }

            if (status == TaskStatus.TODO && task.assigneeRef() == null) {
                signals.add(SignalCode.APPROVED_UNASSIGNED);
                options.add(DecisionOptionCode.ASSIGN_OWNER_AND_SET_DUE);
                steps.add(ExecutionStepCode.ASSIGN_OWNER);
                steps.add(ExecutionStepCode.SET_DUE);
                completions.add(CompletionSignalCode.ASSIGNEE_SET);
                completions.add(CompletionSignalCode.DUE_AT_SET);
            }

            if (task.assigneeRef() == null && dueState == DueState.OVERDUE && !isCompleted) {
                if (!signals.contains(SignalCode.APPROVED_UNASSIGNED)) {
                    signals.add(SignalCode.APPROVED_UNASSIGNED);
                }
                if (!signals.contains(SignalCode.OVERDUE)) {
                    signals.add(SignalCode.OVERDUE);
                }
                options.add(DecisionOptionCode.ASSIGN_OWNER_AND_SET_DUE);
                steps.add(ExecutionStepCode.ASSIGN_OWNER);
                steps.add(ExecutionStepCode.SET_DUE);
                completions.add(CompletionSignalCode.ASSIGNEE_SET);
                completions.add(CompletionSignalCode.DUE_AT_SET);
            } else if (dueState == DueState.OVERDUE && !isCompleted) {
                signals.add(SignalCode.OVERDUE);
                options.add(DecisionOptionCode.DEFER_SCOPE);
                options.add(DecisionOptionCode.KEEP_CURRENT_PLAN);
                steps.add(ExecutionStepCode.SET_DUE);
                steps.add(ExecutionStepCode.SET_NEXT_REVIEW_DATE);
                completions.add(CompletionSignalCode.DUE_AT_SET);
                completions.add(CompletionSignalCode.NEXT_REVIEW_DATE_SET);
            }

            if (dueState == DueState.DUE_SOON && !isCompleted) {
                signals.add(SignalCode.DUE_SOON);
            }

            if (status == TaskStatus.ON_HOLD) {
                signals.add(SignalCode.ON_HOLD);
                options.add(DecisionOptionCode.DEFINE_HOLD_EXIT_CRITERIA);
                steps.add(ExecutionStepCode.SET_HOLD_EXIT_CRITERIA);
                steps.add(ExecutionStepCode.RESUME_TASK);
                completions.add(CompletionSignalCode.HOLD_STATE_RECORDED);
                completions.add(CompletionSignalCode.TASK_RESUMED);
            }

            if (task.checklist() != null && task.checklist().total() > 0 && task.checklist().completed() == 0 && !isCompleted) {
                signals.add(SignalCode.CHECKLIST_NOT_STARTED);
                options.add(DecisionOptionCode.KEEP_CURRENT_PLAN);
                steps.add(ExecutionStepCode.START_CHECKLIST);
                completions.add(CompletionSignalCode.CHECKLIST_STARTED);
            }

            if (task.collaboration() != null && task.collaboration().unresolvedMentionCount() > 0) {
                signals.add(SignalCode.UNRESOLVED_MENTION);
                options.add(DecisionOptionCode.REQUEST_MORE_EVIDENCE);
                steps.add(ExecutionStepCode.RESOLVE_MENTION);
                completions.add(CompletionSignalCode.MENTION_RESOLVED);
            }

            if (task.calendarEventRefs() != null && !task.calendarEventRefs().isEmpty() && !isCompleted) {
                signals.add(SignalCode.CALENDAR_CONFLICT);
                options.add(DecisionOptionCode.DEFER_SCOPE);
                options.add(DecisionOptionCode.KEEP_CURRENT_PLAN);
                steps.add(ExecutionStepCode.SET_NEXT_REVIEW_DATE);
                completions.add(CompletionSignalCode.NEXT_REVIEW_DATE_SET);
            }

            if (isCompleted) {
                signals.add(SignalCode.COMPLETED);
                if (dueState == DueState.COMPLETED_ON_TIME) {
                    signals.add(SignalCode.ON_TIME_COMPLETED);
                }
                options.add(DecisionOptionCode.KEEP_CURRENT_PLAN);
                steps.add(ExecutionStepCode.SET_NEXT_REVIEW_DATE);
                completions.add(CompletionSignalCode.NEXT_REVIEW_DATE_SET);
            }

            List<DecisionOptionCode> distinctOptions = options.stream().distinct().collect(Collectors.toList());
            if (distinctOptions.isEmpty()) {
                distinctOptions.add(DecisionOptionCode.KEEP_CURRENT_PLAN);
            }
            List<ExecutionStepCode> distinctSteps = steps.stream().distinct().collect(Collectors.toList());
            if (distinctSteps.isEmpty()) {
                distinctSteps.add(ExecutionStepCode.SET_NEXT_REVIEW_DATE);
            }
            List<CompletionSignalCode> distinctCompletions = completions.stream().distinct().collect(Collectors.toList());
            if (distinctCompletions.isEmpty()) {
                distinctCompletions.add(CompletionSignalCode.NEXT_REVIEW_DATE_SET);
            }
            List<SignalCode> distinctSignals = signals.stream().distinct().collect(Collectors.toList());

            SnapshotTask updatedTask = new SnapshotTask(
                    task.taskRef(),
                    task.safeLabel(),
                    task.status(),
                    task.priority(),
                    task.assigneeRef(),
                    task.createdAt(),
                    task.dueAt(),
                    task.completedAt(),
                    task.dueState(),
                    task.checklist(),
                    task.collaboration(),
                    task.history(),
                    task.calendarEventRefs(),
                    distinctSignals,
                    distinctOptions,
                    distinctSteps,
                    distinctCompletions
            );
            updatedTasks.add(updatedTask);

            // Risk candidate collection per task
            boolean isInactive = (status == TaskStatus.COMPLETED || status == TaskStatus.REJECTED || status == TaskStatus.CANCELLED);
            if (!isInactive) {
                if (task.assigneeRef() == null && dueState == DueState.OVERDUE) {
                    addRawCandidate(rawCandidates, "APPROVED_UNASSIGNED_OVERDUE", Severity.HIGH, task.taskRef(),
                            List.of(SignalCode.APPROVED_UNASSIGNED, SignalCode.OVERDUE),
                            List.of(DecisionOptionCode.ASSIGN_OWNER_AND_SET_DUE),
                            List.of(ExecutionStepCode.ASSIGN_OWNER, ExecutionStepCode.SET_DUE),
                            List.of(CompletionSignalCode.ASSIGNEE_SET, CompletionSignalCode.DUE_AT_SET));
                } else if (status == TaskStatus.TODO && task.assigneeRef() == null) {
                    addRawCandidate(rawCandidates, "APPROVED_UNASSIGNED", Severity.HIGH, task.taskRef(),
                            List.of(SignalCode.APPROVED_UNASSIGNED),
                            List.of(DecisionOptionCode.ASSIGN_OWNER_AND_SET_DUE),
                            List.of(ExecutionStepCode.ASSIGN_OWNER, ExecutionStepCode.SET_DUE),
                            List.of(CompletionSignalCode.ASSIGNEE_SET, CompletionSignalCode.DUE_AT_SET));
                } else if (dueState == DueState.OVERDUE) {
                    addRawCandidate(rawCandidates, "OVERDUE_ACTIVE", Severity.HIGH, task.taskRef(),
                            List.of(SignalCode.OVERDUE),
                            List.of(DecisionOptionCode.DEFER_SCOPE, DecisionOptionCode.KEEP_CURRENT_PLAN),
                            List.of(ExecutionStepCode.SET_DUE, ExecutionStepCode.SET_NEXT_REVIEW_DATE),
                            List.of(CompletionSignalCode.DUE_AT_SET, CompletionSignalCode.NEXT_REVIEW_DATE_SET));
                }

                if (status == TaskStatus.REQUESTED) {
                    addRawCandidate(rawCandidates, "APPROVAL_PENDING", Severity.MEDIUM, task.taskRef(),
                            List.of(SignalCode.REQUESTED_PENDING),
                            List.of(DecisionOptionCode.APPROVE_SCOPE, DecisionOptionCode.KEEP_CURRENT_PLAN),
                            List.of(ExecutionStepCode.RECORD_SCOPE_DECISION),
                            List.of(CompletionSignalCode.SCOPE_DECISION_RECORDED));
                }

                if (task.checklist() != null && task.checklist().total() > 0 && task.checklist().completed() == 0) {
                    addRawCandidate(rawCandidates, "CHECKLIST_NOT_STARTED", Severity.MEDIUM, task.taskRef(),
                            List.of(SignalCode.CHECKLIST_NOT_STARTED),
                            List.of(DecisionOptionCode.KEEP_CURRENT_PLAN),
                            List.of(ExecutionStepCode.START_CHECKLIST),
                            List.of(CompletionSignalCode.CHECKLIST_STARTED));
                }

                if (task.collaboration() != null && task.collaboration().unresolvedMentionCount() > 0) {
                    addRawCandidate(rawCandidates, "UNRESOLVED_MENTION", Severity.MEDIUM, task.taskRef(),
                            List.of(SignalCode.UNRESOLVED_MENTION),
                            List.of(DecisionOptionCode.REQUEST_MORE_EVIDENCE),
                            List.of(ExecutionStepCode.RESOLVE_MENTION),
                            List.of(CompletionSignalCode.MENTION_RESOLVED));
                }

                if (task.calendarEventRefs() != null && !task.calendarEventRefs().isEmpty()) {
                    addRawCandidate(rawCandidates, "SCHEDULE_CONFLICT", Severity.MEDIUM, task.taskRef(),
                            List.of(SignalCode.CALENDAR_CONFLICT),
                            List.of(DecisionOptionCode.DEFER_SCOPE, DecisionOptionCode.KEEP_CURRENT_PLAN),
                            List.of(ExecutionStepCode.SET_NEXT_REVIEW_DATE),
                            List.of(CompletionSignalCode.NEXT_REVIEW_DATE_SET));
                }
            }
        }

        // WORKLOAD_CONCENTRATION: members >= 2
        if (snapshot.members() != null && snapshot.members().size() >= 2) {
            int maxActive = snapshot.members().stream().mapToInt(SnapshotMember::activeCount).max().orElse(0);
            double avgActive = snapshot.members().stream().mapToInt(SnapshotMember::activeCount).average().orElse(0.0);

            if (maxActive >= 3 && maxActive > avgActive * 1.2) {
                Set<String> concentratedMembers = snapshot.members().stream()
                        .filter(m -> m.activeCount() == maxActive)
                        .map(SnapshotMember::memberRef)
                        .collect(Collectors.toSet());

                List<String> concentratedTasks = updatedTasks.stream()
                        .filter(t -> t.assigneeRef() != null && concentratedMembers.contains(t.assigneeRef()))
                        .filter(t -> t.status() != TaskStatus.COMPLETED && t.status() != TaskStatus.REJECTED && t.status() != TaskStatus.CANCELLED)
                        .map(SnapshotTask::taskRef)
                        .collect(Collectors.toList());

                if (!concentratedTasks.isEmpty()) {
                    rawCandidates.add(new RawRiskCandidate(
                            "WORKLOAD_CONCENTRATION",
                            Severity.HIGH,
                            concentratedTasks,
                            List.of(SignalCode.WORKLOAD_CONCENTRATION),
                            List.of(DecisionOptionCode.REBALANCE_WORK),
                            List.of(ExecutionStepCode.REBALANCE_ASSIGNEE),
                            List.of(CompletionSignalCode.ASSIGNEE_SET)
                    ));
                }
            }
        }

        // Comparison based risks
        if (snapshot.comparison() != null && snapshot.comparison().status() == ComparisonStatus.AVAILABLE) {
            SnapshotComparison comp = snapshot.comparison();
            List<String> activeTasks = updatedTasks.stream()
                    .filter(t -> t.status() != TaskStatus.COMPLETED && t.status() != TaskStatus.REJECTED && t.status() != TaskStatus.CANCELLED)
                    .map(SnapshotTask::taskRef)
                    .limit(5)
                    .collect(Collectors.toList());

            if (activeTasks.isEmpty()) {
                activeTasks = updatedTasks.stream().map(SnapshotTask::taskRef).limit(5).collect(Collectors.toList());
            }

            if (comp.completionRatePointDelta() != null && comp.completionRatePointDelta() < 0) {
                if (!activeTasks.isEmpty()) {
                    rawCandidates.add(new RawRiskCandidate(
                            "COMPLETION_RATE_DROP",
                            Severity.HIGH,
                            activeTasks,
                            List.of(SignalCode.OVERDUE),
                            List.of(DecisionOptionCode.REQUEST_MORE_EVIDENCE, DecisionOptionCode.KEEP_CURRENT_PLAN),
                            List.of(ExecutionStepCode.SET_NEXT_REVIEW_DATE),
                            List.of(CompletionSignalCode.NEXT_REVIEW_DATE_SET)
                    ));
                }
            }

            if (comp.delayedCountDelta() != null && comp.delayedCountDelta() > 0) {
                if (!activeTasks.isEmpty()) {
                    rawCandidates.add(new RawRiskCandidate(
                            "BACKLOG_GROWTH",
                            Severity.MEDIUM,
                            activeTasks,
                            List.of(SignalCode.REQUESTED_PENDING),
                            List.of(DecisionOptionCode.REQUEST_MORE_EVIDENCE, DecisionOptionCode.KEEP_CURRENT_PLAN),
                            List.of(ExecutionStepCode.SET_NEXT_REVIEW_DATE),
                            List.of(CompletionSignalCode.NEXT_REVIEW_DATE_SET)
                    ));
                }
            }
        }

        rawCandidates.sort((c1, c2) -> {
            int sComp = c1.severity.compareTo(c2.severity);
            if (sComp != 0) return sComp;

            int p1 = RISK_CODE_PRECEDENCE.getOrDefault(c1.riskCode, 99);
            int p2 = RISK_CODE_PRECEDENCE.getOrDefault(c2.riskCode, 99);
            if (p1 != p2) return Integer.compare(p1, p2);

            String t1 = c1.taskRefs.isEmpty() ? "" : c1.taskRefs.get(0);
            String t2 = c2.taskRefs.isEmpty() ? "" : c2.taskRefs.get(0);
            return t1.compareTo(t2);
        });

        List<RiskCandidate> finalCandidates = new ArrayList<>();
        int index = 1;
        for (RawRiskCandidate raw : rawCandidates) {
            if (finalCandidates.size() >= 10) break;

            String candidateRef = String.format("RISK-%03d", index++);
            finalCandidates.add(new RiskCandidate(
                    candidateRef,
                    raw.riskCode,
                    raw.severity,
                    raw.taskRefs.stream().distinct().collect(Collectors.toList()),
                    raw.evidenceCodes.stream().distinct().collect(Collectors.toList()),
                    raw.allowedOptionCodes.stream().distinct().collect(Collectors.toList()),
                    raw.allowedExecutionStepCodes.stream().distinct().collect(Collectors.toList()),
                    raw.allowedCompletionSignalCodes.stream().distinct().collect(Collectors.toList())
            ));
        }

        return new AiWeeklyReportSnapshotV1(
                snapshot.schemaVersion(),
                snapshot.reportContext(),
                snapshot.metrics(),
                snapshot.comparison(),
                snapshot.workflow(),
                snapshot.members(),
                updatedTasks,
                snapshot.calendarConstraints(),
                finalCandidates
        );
    }

    private void addRawCandidate(List<RawRiskCandidate> list, String riskCode, Severity severity, String taskRef,
                                 List<SignalCode> evidence, List<DecisionOptionCode> options,
                                 List<ExecutionStepCode> steps, List<CompletionSignalCode> completions) {
        Optional<RawRiskCandidate> existing = list.stream().filter(c -> c.riskCode.equals(riskCode)).findFirst();
        if (existing.isPresent()) {
            RawRiskCandidate candidate = existing.get();
            if (!candidate.taskRefs.contains(taskRef)) {
                candidate.taskRefs.add(taskRef);
            }
        } else {
            List<String> tasks = new ArrayList<>();
            tasks.add(taskRef);
            list.add(new RawRiskCandidate(riskCode, severity, tasks, evidence, options, steps, completions));
        }
    }

    private static class RawRiskCandidate {
        final String riskCode;
        final Severity severity;
        final List<String> taskRefs;
        final List<SignalCode> evidenceCodes;
        final List<DecisionOptionCode> allowedOptionCodes;
        final List<ExecutionStepCode> allowedExecutionStepCodes;
        final List<CompletionSignalCode> allowedCompletionSignalCodes;

        RawRiskCandidate(String riskCode, Severity severity, List<String> taskRefs,
                         List<SignalCode> evidenceCodes, List<DecisionOptionCode> allowedOptionCodes,
                         List<ExecutionStepCode> allowedExecutionStepCodes,
                         List<CompletionSignalCode> allowedCompletionSignalCodes) {
            this.riskCode = riskCode;
            this.severity = severity;
            this.taskRefs = taskRefs;
            this.evidenceCodes = new ArrayList<>(evidenceCodes);
            this.allowedOptionCodes = new ArrayList<>(allowedOptionCodes);
            this.allowedExecutionStepCodes = new ArrayList<>(allowedExecutionStepCodes);
            this.allowedCompletionSignalCodes = new ArrayList<>(allowedCompletionSignalCodes);
        }
    }
}
