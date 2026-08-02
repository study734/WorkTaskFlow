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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

        RefResolver refs = new RefResolver(taskTitleByRef, memberNameByRef, eventTitleByRef, isKorean(snapshot));

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
                        // 담당자가 없으면 null을 그대로 넘겨 화면이 "미지정"으로 그리게 둔다.
                        t.assigneeRef() == null ? null : refs.memberName(t.assigneeRef()),
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
                        refs.memberName(m.memberRef()),
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
            List<String> evidenceTitles = evidenceRefs.stream().map(refs::taskTitle).toList();

            ejView = new ExecutiveJudgmentView(
                    refs.resolve(analysis.executiveJudgment().headline()),
                    refs.resolve(analysis.executiveJudgment().interpretation()),
                    analysis.executiveJudgment().metricRefs() != null ? analysis.executiveJudgment().metricRefs().stream().map(Enum::name).toList() : List.of(),
                    evidenceRefs,
                    evidenceTitles,
                    analysis.executiveJudgment().confidence() != null ? analysis.executiveJudgment().confidence().name() : "MEDIUM",
                    refs.resolveAll(analysis.executiveJudgment().missingEvidence())
            );
        }

        AchievementView achView = null;
        if (analysis.achievement() != null) {
            List<String> evidenceRefs = analysis.achievement().evidenceTaskRefs() != null
                    ? analysis.achievement().evidenceTaskRefs() : List.of();
            List<String> evidenceTitles = evidenceRefs.stream().map(refs::taskTitle).toList();

            achView = new AchievementView(
                    analysis.achievement().status() != null ? analysis.achievement().status().name() : "NONE",
                    refs.resolve(analysis.achievement().headline()),
                    refs.resolve(analysis.achievement().summary()),
                    evidenceRefs,
                    evidenceTitles
            );
        }

        List<IssueView> issueViews = new ArrayList<>();
        if (analysis.issues() != null) {
            for (AnalysisIssue issue : analysis.issues()) {
                List<String> tRefs = issue.taskRefs() != null ? issue.taskRefs() : List.of();
                List<String> tTitles = tRefs.stream().map(refs::taskTitle).toList();

                String issueTitle = refs.resolve(issue.title());
                String realTitle = tTitles.isEmpty() ? issueTitle : tTitles.get(0);

                DecisionView dView = null;
                if (issue.decision() != null) {
                    IssueDecision d = issue.decision();
                    DeadlineView dlView = null;
                    if (d.deadline() != null) {
                        String ref = d.deadline().referenceRef();
                        dlView = new DeadlineView(d.deadline().source(), ref, refs.referenceTitle(ref));
                    }

                    dView = new DecisionView(
                            refs.resolve(d.title()),
                            refs.resolve(d.question()),
                            d.recommendedOptionCode() != null ? d.recommendedOptionCode().name() : null,
                            refs.resolve(d.recommendation()),
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
                        issueTitle,
                        realTitle,
                        refs.resolve(issue.impact()),
                        issue.confidence() != null ? issue.confidence().name() : "MEDIUM",
                        tRefs,
                        tTitles,
                        issue.evidenceCodes() != null ? issue.evidenceCodes().stream().map(Enum::name).toList() : List.of(),
                        refs.resolveAll(issue.missingEvidence()),
                        refs.resolve(issue.integratedJudgment()),
                        refs.resolve(issue.requiredDecision()),
                        dView
                ));
            }
        }

        // 생성 응답과 같은 경로를 알려 준다. 산출물은 인쇄용 HTML이다.
        String downloadUrl = String.format("/api/v1/groups/%d/reports/ai-weekly/%d/download", groupId, revision.getId());

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
                refs.resolveAll(analysis.globalMissingEvidence()),
                metricsView,
                comparisonView,
                workflowView,
                taskViews,
                memberViews,
                calendarViews,
                riskChecks(snapshot, isKorean(snapshot))
        );
    }

    /**
     * 서버가 검사한 위험 항목 전체와 각 항목의 후보 수. 후보가 0인 항목도 남긴다.
     *
     * <p>후보가 하나도 없으면 문서에 "없습니다" 한 줄만 남아 아무 일도 안 한 것으로 읽힌다.
     * policy engine은 실제로 12개 항목을 다 검사한다. 그 사실을 문서가 보여 줄 수 있게
     * 결과를 함께 넘긴다.
     */
    private List<RiskCheckView> riskChecks(AiWeeklyReportSnapshotV1 snapshot, boolean ko) {
        List<RiskCandidate> candidates = snapshot.riskCandidates() != null
                ? snapshot.riskCandidates() : List.of();
        Map<String, Long> countByCode = candidates.stream()
                .collect(Collectors.groupingBy(RiskCandidate::riskCode, Collectors.counting()));

        return AiWeeklyReportPolicyEngine.RISK_CODES.stream()
                .map(code -> new RiskCheckView(code, riskCodeLabel(code, ko),
                        Math.toIntExact(countByCode.getOrDefault(code, 0L))))
                .toList();
    }

    private String riskCodeLabel(String code, boolean ko) {
        return switch (code) {
            case "APPROVED_UNASSIGNED_OVERDUE" -> ko ? "담당자 없이 마감 초과" : "Overdue with no owner";
            case "APPROVED_UNASSIGNED" -> ko ? "담당자 미지정" : "No owner";
            case "OVERDUE_ACTIVE" -> ko ? "마감 초과" : "Past due";
            case "WORKLOAD_CONCENTRATION" -> ko ? "업무 편중" : "Workload concentration";
            case "COMPLETION_RATE_DROP" -> ko ? "완료율 하락" : "Completion rate drop";
            case "SCHEDULE_CONFLICT" -> ko ? "일정 충돌" : "Schedule conflict";
            case "APPROVAL_PENDING" -> ko ? "승인 대기" : "Awaiting approval";
            case "CHECKLIST_NOT_STARTED" -> ko ? "체크리스트 미착수" : "Checklist not started";
            case "BACKLOG_GROWTH" -> ko ? "미착수 누적" : "Growing backlog";
            case "UNRESOLVED_MENTION" -> ko ? "미응답 멘션" : "Unanswered mention";
            case "ON_HOLD_LONG" -> ko ? "장기 보류" : "Long on hold";
            case "RESOURCE_MISSING" -> ko ? "관련 자료 없음" : "No linked resource";
            default -> ko ? "기타 항목" : "Other check";
        };
    }

    /** Snapshot이 담고 있는 요청 언어를 그대로 따른다. 없으면 한국어로 본다. */
    private boolean isKorean(AiWeeklyReportSnapshotV1 snapshot) {
        return snapshot.reportContext() == null || snapshot.reportContext().language() != Language.EN;
    }

    /**
     * ref를 표시 이름으로 되돌린다. 명세 §8.1 "재결합은 서버에서 수행한다"의 실제 구현이다.
     *
     * <p>구조화된 ref 필드뿐 아니라 모델이 문장 안에 직접 써 넣은 ref도 바꾼다. 그러지 않으면
     * "TASK-6은 URGENT 우선순위이며"처럼 내부 식별자가 사용자 문서에 그대로 찍힌다.
     * 매칭에 실패한 ref도 원시 식별자를 남기지 않고 비식별 라벨로 바꾼다.
     */
    private static final class RefResolver {
        /**
         * Snapshot ref 표기는 {@code TASK-12} 형태로 고정이다. 접두사가 붙은 단어는 건드리지 않는다.
         *
         * <p>{@code RISK-001}(candidateRef)도 포함한다. 모델이 "RISK-001의 OVERDUE 근거는"처럼
         * 문장에 써 넣는데, 이것도 사용자에게는 의미 없는 내부 식별자다.
         */
        private static final Pattern REF = Pattern.compile("\\b(TASK|MEMBER|EVENT|RISK)-(\\d+)\\b");

        /** 치환 뒤 곧바로 오는 조사. 앞말의 받침이 바뀌므로 다시 골라야 한다. */
        private static final Pattern JOSA = Pattern.compile("^(은|는|이|가|을|를|과|와|으로|로)(?![가-힣])");

        private final Map<String, String> taskTitleByRef;
        private final Map<String, String> memberNameByRef;
        private final Map<String, String> eventTitleByRef;
        private final boolean ko;

        private RefResolver(Map<String, String> taskTitleByRef, Map<String, String> memberNameByRef,
                Map<String, String> eventTitleByRef, boolean ko) {
            this.taskTitleByRef = taskTitleByRef;
            this.memberNameByRef = memberNameByRef;
            this.eventTitleByRef = eventTitleByRef;
            this.ko = ko;
        }

        String taskTitle(String ref) {
            return taskTitleByRef.getOrDefault(ref, ko ? "확인할 수 없는 업무" : "Unidentified task");
        }

        String memberName(String ref) {
            return memberNameByRef.getOrDefault(ref, ko ? "확인할 수 없는 팀원" : "Unidentified member");
        }

        String eventTitle(String ref) {
            return eventTitleByRef.getOrDefault(ref, ko ? "확인할 수 없는 일정" : "Unidentified event");
        }

        /** deadline의 referenceRef는 업무일 수도 일정일 수도 있어 둘 다 찾아본다. */
        String referenceTitle(String ref) {
            if (ref == null) return null;
            String title = taskTitleByRef.get(ref);
            if (title != null) return title;
            title = eventTitleByRef.get(ref);
            return title != null ? title : eventTitle(ref);
        }

        /** missingEvidence처럼 사용자에게 문장으로 보이는 문자열 목록도 같이 치환한다. */
        List<String> resolveAll(List<String> texts) {
            return texts == null ? List.of() : texts.stream().map(this::resolve).toList();
        }

        String resolve(String text) {
            if (text == null || text.isEmpty()) return text;
            Matcher matcher = REF.matcher(text);
            StringBuilder out = new StringBuilder();
            int tail = 0;
            while (matcher.find()) {
                String replacement = displayNameOf(matcher.group());
                out.append(text, tail, matcher.start()).append(replacement);
                tail = matcher.end();

                // 모델은 ref 발음에 맞춰 조사를 붙였다("TASK-5은"). 제목으로 바꾸면 받침이 달라진다.
                Matcher josa = JOSA.matcher(text.substring(tail));
                if (josa.find()) {
                    String corrected = correctJosa(josa.group(), replacement);
                    if (corrected == null) {
                        out.append(josa.group());
                    } else {
                        out.append(corrected);
                    }
                    tail += josa.group().length();
                }
            }
            out.append(text, tail, text.length());
            return out.toString();
        }

        private String displayNameOf(String ref) {
            if (ref.startsWith("TASK-")) return taskTitle(ref);
            if (ref.startsWith("MEMBER-")) return memberName(ref);
            if (ref.startsWith("RISK-")) return ko ? "해당 위험 후보" : "the risk candidate";
            return eventTitle(ref);
        }

        /**
         * 앞말의 받침에 맞는 조사를 고른다. 마지막 글자가 한글 음절이 아니면(영문 제목 등)
         * 규칙을 세울 수 없으므로 원문을 그대로 둔다는 뜻으로 null을 준다.
         */
        private String correctJosa(String josa, String precedingWord) {
            if (precedingWord.isEmpty()) return null;
            char last = precedingWord.charAt(precedingWord.length() - 1);
            if (last < 0xAC00 || last > 0xD7A3) return null;
            int jongseong = (last - 0xAC00) % 28;
            boolean batchim = jongseong != 0;
            boolean rieul = jongseong == 8;
            return switch (josa) {
                case "은", "는" -> batchim ? "은" : "는";
                case "이", "가" -> batchim ? "이" : "가";
                case "을", "를" -> batchim ? "을" : "를";
                case "과", "와" -> batchim ? "과" : "와";
                // 받침 ㄹ은 예외다. "정리로"가 맞고 "정리으로"는 틀리다.
                case "으로", "로" -> batchim && !rieul ? "으로" : "로";
                default -> null;
            };
        }
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
