package com.teamproject.report.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamproject.calendar.domain.CalendarEvent;
import com.teamproject.calendar.domain.CalendarEventRepository;
import com.teamproject.group.domain.GroupMember;
import com.teamproject.group.domain.GroupMemberRepository;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.*;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import com.teamproject.report.domain.AiWeeklyReportRevision;
import com.teamproject.report.presentation.dto.AiWeeklyReportApiDtos.*;
import com.teamproject.task.domain.Task;
import com.teamproject.task.domain.TaskRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Snapshot/Analysis ref와 실제 DB 엔티티를 재결합하여 v7-2 API/PDF 화면 projection 뷰를 생성한다 (M8/M9).
 */
@Component
@Transactional(readOnly = true)
public class AiWeeklyReportViewProjector {

    private final ObjectMapper objectMapper;
    private final TaskRepository taskRepository;
    private final GroupMemberRepository memberRepository;
    private final CalendarEventRepository calendarEventRepository;

    public AiWeeklyReportViewProjector(
            ObjectMapper objectMapper,
            TaskRepository taskRepository,
            GroupMemberRepository memberRepository,
            CalendarEventRepository calendarEventRepository
    ) {
        this.objectMapper = objectMapper;
        this.taskRepository = taskRepository;
        this.memberRepository = memberRepository;
        this.calendarEventRepository = calendarEventRepository;
    }

    public AiWeeklyReportView project(AiWeeklyReportRevision revision) {
        AiWeeklyReportSnapshotV1 snapshot = parseSnapshot(revision.getSnapshotJson());
        AiWeeklyReportAnalysisV1 analysis = parseAnalysis(revision.getAnalysisJson());

        Long groupId = revision.getGroupId();

        // 계약상 배열은 비어 있을 수 있고, 과거 revision에는 아예 없을 수도 있다.
        List<SnapshotTask> snapshotTasks = snapshot.tasks() != null ? snapshot.tasks() : List.of();
        List<SnapshotMember> snapshotMembers = snapshot.members() != null ? snapshot.members() : List.of();
        List<CalendarConstraint> snapshotConstraints = snapshot.calendarConstraints() != null
                ? snapshot.calendarConstraints() : List.of();

        Map<String, String> taskTitleByRef = buildTaskTitleMap(groupId, snapshotTasks);
        Map<String, String> memberNameByRef = buildMemberNameMap(groupId, snapshotMembers);
        Map<String, String> eventTitleByRef = buildEventTitleMap(groupId, snapshotConstraints);

        SnapshotMetricsView metricsView = projectMetrics(snapshot.metrics());
        SnapshotComparisonView comparisonView = projectComparison(snapshot.comparison());
        SnapshotWorkflowView workflowView = projectWorkflow(snapshot.workflow());

        List<SnapshotTaskView> taskViews = snapshotTasks.stream()
                .map(t -> new SnapshotTaskView(
                        t.taskRef(),
                        taskTitleByRef.getOrDefault(t.taskRef(), t.safeLabel()),
                        t.safeLabel(),
                        t.status() != null ? t.status().name() : null,
                        t.priority(),
                        t.assigneeRef(),
                        memberNameByRef.getOrDefault(t.assigneeRef(), t.assigneeRef()),
                        t.createdAt(),
                        t.dueAt(),
                        t.completedAt(),
                        t.dueState() != null ? t.dueState().name() : null,
                        t.checklist() != null ? new TaskChecklistView(t.checklist().completed(), t.checklist().total()) : null,
                        t.collaboration() != null ? new TaskCollaborationView(t.collaboration().commentCount(), t.collaboration().unresolvedMentionCount(), t.collaboration().resourceLinkCount()) : null,
                        t.history() != null ? new TaskHistoryView(t.history().lastTransitionCode(), t.history().holdReasonCategory() != null ? t.history().holdReasonCategory().name() : null, t.history().reopenedCount()) : null,
                        t.calendarEventRefs() != null ? t.calendarEventRefs() : List.of()
                ))
                .toList();

        List<SnapshotMemberView> memberViews = snapshotMembers.stream()
                .map(m -> new SnapshotMemberView(
                        m.memberRef(),
                        memberNameByRef.getOrDefault(m.memberRef(), m.memberRef()),
                        m.role(),
                        m.assignedCount(),
                        m.activeCount(),
                        m.completedCount(),
                        m.delayedCount(),
                        m.onTimeRatePercent(),
                        m.upcomingCalendarCount()
                ))
                .toList();

        List<CalendarConstraintView> calendarViews = snapshotConstraints.stream()
                .map(c -> new CalendarConstraintView(
                        c.eventRef(),
                        eventTitleByRef.getOrDefault(c.eventRef(), c.safeLabel()),
                        c.type(),
                        c.safeLabel(),
                        c.startAt(),
                        c.endAt(),
                        c.relatedTaskRefs() != null ? c.relatedTaskRefs() : List.of()
                ))
                .toList();

        ExecutiveJudgmentView ejView = null;
        if (analysis.executiveJudgment() != null) {
            List<String> evidenceRefs = analysis.executiveJudgment().evidenceTaskRefs() != null
                    ? analysis.executiveJudgment().evidenceTaskRefs() : List.of();
            List<String> evidenceTitles = evidenceRefs.stream()
                    .map(ref -> taskTitleByRef.getOrDefault(ref, "확인할 수 없는 업무 (" + ref + ")"))
                    .toList();

            ejView = new ExecutiveJudgmentView(
                    analysis.executiveJudgment().headline(),
                    analysis.executiveJudgment().interpretation(),
                    analysis.executiveJudgment().metricRefs() != null ? analysis.executiveJudgment().metricRefs().stream().map(Enum::name).toList() : List.of(),
                    evidenceRefs,
                    evidenceTitles,
                    analysis.executiveJudgment().confidence() != null ? analysis.executiveJudgment().confidence().name() : "MEDIUM",
                    analysis.executiveJudgment().missingEvidence() != null ? analysis.executiveJudgment().missingEvidence() : List.of()
            );
        }

        AchievementView achView = null;
        if (analysis.achievement() != null) {
            List<String> evidenceRefs = analysis.achievement().evidenceTaskRefs() != null
                    ? analysis.achievement().evidenceTaskRefs() : List.of();
            List<String> evidenceTitles = evidenceRefs.stream()
                    .map(ref -> taskTitleByRef.getOrDefault(ref, "확인할 수 없는 업무 (" + ref + ")"))
                    .toList();

            achView = new AchievementView(
                    analysis.achievement().status() != null ? analysis.achievement().status().name() : "NONE",
                    analysis.achievement().headline(),
                    analysis.achievement().summary(),
                    evidenceRefs,
                    evidenceTitles
            );
        }

        List<IssueView> issueViews = new ArrayList<>();
        if (analysis.issues() != null) {
            for (AnalysisIssue issue : analysis.issues()) {
                List<String> tRefs = issue.taskRefs() != null ? issue.taskRefs() : List.of();
                List<String> tTitles = tRefs.stream()
                        .map(ref -> taskTitleByRef.getOrDefault(ref, "확인할 수 없는 업무 (" + ref + ")"))
                        .toList();

                String realTitle = tTitles.isEmpty() ? issue.title() : tTitles.get(0);

                DecisionView dView = null;
                if (issue.decision() != null) {
                    IssueDecision d = issue.decision();
                    DeadlineView dlView = null;
                    if (d.deadline() != null) {
                        String ref = d.deadline().referenceRef();
                        String refTitle = ref != null ? taskTitleByRef.getOrDefault(ref, eventTitleByRef.getOrDefault(ref, ref)) : null;
                        dlView = new DeadlineView(d.deadline().source(), ref, refTitle);
                    }

                    dView = new DecisionView(
                            d.title(),
                            d.question(),
                            d.recommendedOptionCode() != null ? d.recommendedOptionCode().name() : null,
                            d.recommendation(),
                            d.decisionMakerRole(),
                            d.actionOwnerRole(),
                            dlView,
                            d.executionStepCodes() != null ? d.executionStepCodes().stream().map(Enum::name).toList() : List.of(),
                            d.completionSignalCodes() != null ? d.completionSignalCodes().stream().map(Enum::name).toList() : List.of()
                    );
                }

                issueViews.add(new IssueView(
                        issue.priority() != null ? issue.priority().name() : "P1",
                        issue.candidateRef(),
                        issue.severity() != null ? issue.severity().name() : "MEDIUM",
                        issue.title(),
                        realTitle,
                        issue.impact(),
                        issue.confidence() != null ? issue.confidence().name() : "MEDIUM",
                        tRefs,
                        tTitles,
                        issue.evidenceCodes() != null ? issue.evidenceCodes().stream().map(Enum::name).toList() : List.of(),
                        issue.missingEvidence() != null ? issue.missingEvidence() : List.of(),
                        issue.integratedJudgment(),
                        issue.requiredDecision(),
                        dView
                ));
            }
        }

        String downloadUrl = String.format("/api/v1/groups/%d/reports/ai-weekly/%d/pdf", groupId, revision.getId());

        return new AiWeeklyReportView(
                revision.getId(),
                groupId,
                revision.getPeriodFrom(),
                revision.getPeriodToExclusive(),
                revision.getRevision(),
                revision.getStatus(),
                revision.getAnalysisMode(),
                revision.getGeneratedAt(),
                downloadUrl,
                ejView,
                achView,
                issueViews,
                analysis.globalMissingEvidence() != null ? analysis.globalMissingEvidence() : List.of(),
                metricsView,
                comparisonView,
                workflowView,
                taskViews,
                memberViews,
                calendarViews
        );
    }

    private Map<String, String> buildTaskTitleMap(Long groupId, List<SnapshotTask> snapshotTasks) {
        if (snapshotTasks == null || snapshotTasks.isEmpty()) {
            // 이후 조회는 null ref도 그대로 넘긴다. Map.of()는 null 키를 거부한다.
            return new HashMap<>();
        }
        List<Task> dbTasks = taskRepository.findAllByGroupIdOrderByCreatedAtDesc(groupId);
        Map<Long, Task> dbTaskById = new HashMap<>();
        for (Task task : dbTasks) {
            dbTaskById.putIfAbsent(task.getId(), task);
        }

        Map<String, String> map = new HashMap<>();
        for (SnapshotTask st : snapshotTasks) {
            Long id = parseRefId(st.taskRef(), "TASK");
            Task dbTask = id == null ? null : dbTaskById.get(id);
            if (dbTask != null && sameTask(dbTask, st)) {
                map.put(st.taskRef(), dbTask.getTitle());
            } else if (st.safeLabel() != null) {
                map.put(st.taskRef(), st.safeLabel());
            }
        }
        return map;
    }

    /**
     * ref가 가리키는 행이 정말 그 Snapshot 항목인지 생성 시각으로 확인한다.
     *
     * <p>ref 규칙이 바뀌기 전에 저장된 revision은 순번 ref를 담고 있어, 그대로 믿으면 같은
     * 그룹의 전혀 다른 업무 제목을 보여 준다. 대조에 실패하면 제목 대신 비식별 라벨로 되돌린다.
     */
    private boolean sameTask(Task dbTask, SnapshotTask snapshotTask) {
        String snapshotCreatedAt = snapshotTask.createdAt();
        if (snapshotCreatedAt == null || dbTask.getCreatedAt() == null) {
            return true;
        }
        return snapshotCreatedAt.equals(dbTask.getCreatedAt().toInstant(ZoneOffset.UTC).toString());
    }

    private Map<String, String> buildMemberNameMap(Long groupId, List<SnapshotMember> snapshotMembers) {
        if (snapshotMembers == null || snapshotMembers.isEmpty()) {
            // 이후 조회는 null ref도 그대로 넘긴다. Map.of()는 null 키를 거부한다.
            return new HashMap<>();
        }
        List<GroupMember> dbMembers = memberRepository.findAllByGroupIdAndStatusOrderByRoleAscJoinedAtAsc(groupId, GroupMember.Status.ACTIVE);
        Map<Long, String> dbMemberNameById = new HashMap<>();
        for (GroupMember gm : dbMembers) {
            String name = gm.getUser() != null ? gm.getUser().getName() : "멤버 " + gm.getId();
            dbMemberNameById.put(gm.getId(), name);
        }

        Map<String, String> map = new HashMap<>();
        for (SnapshotMember sm : snapshotMembers) {
            Long id = parseRefId(sm.memberRef(), "MEMBER");
            if (id != null && dbMemberNameById.containsKey(id)) {
                map.put(sm.memberRef(), dbMemberNameById.get(id));
            }
        }
        return map;
    }

    private Map<String, String> buildEventTitleMap(Long groupId, List<CalendarConstraint> constraints) {
        if (constraints == null || constraints.isEmpty()) {
            // 이후 조회는 null ref도 그대로 넘긴다. Map.of()는 null 키를 거부한다.
            return new HashMap<>();
        }
        LocalDateTime farPast = LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime farFuture = LocalDateTime.of(2100, 1, 1, 0, 0);
        List<CalendarEvent> dbEvents = calendarEventRepository.findAllByGroupIdAndStartAtUtcLessThanAndEndAtUtcGreaterThanOrderByStartAtUtcAscIdAsc(groupId, farFuture, farPast);
        Map<Long, String> dbEventTitleById = dbEvents.stream()
                .collect(Collectors.toMap(CalendarEvent::getId, CalendarEvent::getTitle, (a, b) -> a));

        Map<String, String> map = new HashMap<>();
        for (CalendarConstraint cc : constraints) {
            Long id = parseRefId(cc.eventRef(), "EVENT");
            if (id != null && dbEventTitleById.containsKey(id)) {
                map.put(cc.eventRef(), dbEventTitleById.get(id));
            } else if (cc.safeLabel() != null) {
                map.put(cc.eventRef(), cc.safeLabel());
            }
        }
        return map;
    }

    private Long parseRefId(String ref, String prefix) {
        if (ref == null || !ref.startsWith(prefix + "-")) {
            return null;
        }
        try {
            return Long.parseLong(ref.substring(prefix.length() + 1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private SnapshotMetricsView projectMetrics(SnapshotMetrics m) {
        if (m == null) return new SnapshotMetricsView(0, 0, 0, 0, null);
        return new SnapshotMetricsView(m.periodTaskCount(), m.completionRatePercent(), m.onTimeRatePercent(), m.delayedCount(), m.averageCompletionHours());
    }

    private SnapshotComparisonView projectComparison(SnapshotComparison c) {
        if (c == null) return new SnapshotComparisonView("NO_BASELINE", null, null, null, null, null, null);
        return new SnapshotComparisonView(
                c.status() != null ? c.status().name() : "NO_BASELINE",
                c.previousFrom(),
                c.previousToExclusive(),
                c.periodTaskCountDelta(),
                c.completionRatePointDelta(),
                c.onTimeRatePointDelta(),
                c.delayedCountDelta()
        );
    }

    private SnapshotWorkflowView projectWorkflow(SnapshotWorkflow w) {
        if (w == null) return new SnapshotWorkflowView(0, 0, 0, 0, 0, 0);
        return new SnapshotWorkflowView(w.requested(), w.acceptedUnassigned(), w.assignedNotStarted(), w.inProgress(), w.onHold(), w.completed());
    }

    private AiWeeklyReportSnapshotV1 parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json, AiWeeklyReportSnapshotV1.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored snapshot JSON", e);
        }
    }

    private AiWeeklyReportAnalysisV1 parseAnalysis(String json) {
        try {
            return objectMapper.readValue(json, AiWeeklyReportAnalysisV1.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to parse stored analysis JSON", e);
        }
    }
}
