package com.teamproject.report.application;

import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.*;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * v7-2 AI 주간 리포트의 서버 결정론적 Fallback 분석 생성기 (M4).
 * OpenAI API 호출 실패·비활성화 또는 결과 검증 실패 시 호출된다.
 * 원본 사용자 제목·설명·댓글·실명·새 날짜·새 숫자를 작성하지 않으며 ref 및 code 중심 결과를 만든다.
 * 생성된 결과는 Analysis JSON Schema와 Validator를 100% 통과한다.
 */
@Component
public class AiWeeklyReportFallbackFactory {

    public AiWeeklyReportAnalysisV1 create(AiWeeklyReportSnapshotV1 snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Snapshot must not be null");
        }

        // 문서와 같은 언어로 써야 한다. Snapshot이 이미 요청 언어를 담고 있다.
        boolean ko = isKorean(snapshot);
        List<RiskCandidate> riskCandidates = snapshot.riskCandidates() != null ? snapshot.riskCandidates() : List.of();
        AnalysisStatus status = riskCandidates.isEmpty() ? AnalysisStatus.NO_ACTION_REQUIRED : AnalysisStatus.NORMAL;

        // Executive Judgment
        List<MetricRef> metricRefs = new ArrayList<>();
        metricRefs.add(MetricRef.PERIOD_TASK_COUNT);
        metricRefs.add(MetricRef.COMPLETION_RATE);
        metricRefs.add(MetricRef.DELAYED_COUNT);
        if (snapshot.comparison() != null && snapshot.comparison().status() == ComparisonStatus.AVAILABLE) {
            metricRefs.add(MetricRef.COMPLETION_RATE_DELTA);
        }

        List<String> ejEvidenceTasks = new ArrayList<>();
        for (RiskCandidate candidate : riskCandidates) {
            for (String tRef : candidate.taskRefs()) {
                if (!ejEvidenceTasks.contains(tRef) && ejEvidenceTasks.size() < 5) {
                    ejEvidenceTasks.add(tRef);
                }
            }
        }
        if (ejEvidenceTasks.isEmpty() && snapshot.tasks() != null && !snapshot.tasks().isEmpty()) {
            ejEvidenceTasks.add(snapshot.tasks().get(0).taskRef());
        }

        String headline = headline(snapshot, ko);
        String interpretation = interpretation(snapshot, riskCandidates.isEmpty(), ko);

        ExecutiveJudgment executiveJudgment = new ExecutiveJudgment(
                headline,
                interpretation,
                metricRefs,
                ejEvidenceTasks,
                Confidence.HIGH,
                List.of()
        );

        // 계약은 성과 하나에 근거 업무 5건까지 허용한다(evidenceTaskRefs maxItems 5).
        // 완료 5건인 기간에 1건만 실으면 실제보다 빈약해 보인다. 위험 후보가 없는 기간에는
        // 성과가 문서의 주된 내용이라 더 그렇다.
        Achievement achievement;
        List<String> completedRefs = snapshot.tasks() == null ? List.of()
                : snapshot.tasks().stream()
                        .filter(t -> t.status() == TaskStatus.COMPLETED)
                        .map(SnapshotTask::taskRef)
                        .limit(ACHIEVEMENT_EVIDENCE_MAX)
                        .toList();

        if (!completedRefs.isEmpty()) {
            achievement = new Achievement(
                    AchievementStatus.AVAILABLE,
                    ko ? "기간 내 업무 완료" : "Tasks completed in this period",
                    achievementSummary(completedRefs.size(), completedCount(snapshot), ko),
                    completedRefs
            );
        } else {
            achievement = Achievement.none();
        }

        // Issues (Max 3, priorities P1, P2, P3)
        List<AnalysisIssue> issues = new ArrayList<>();
        IssuePriority[] priorities = IssuePriority.values();

        int limit = Math.min(riskCandidates.size(), 3);
        for (int i = 0; i < limit; i++) {
            RiskCandidate candidate = riskCandidates.get(i);
            IssuePriority priority = priorities[i];

            List<String> issueTaskRefs = candidate.taskRefs().stream()
                    .limit(5)
                    .toList();

            DecisionOptionCode recOption = candidate.allowedOptionCodes().isEmpty()
                    ? DecisionOptionCode.KEEP_CURRENT_PLAN
                    : candidate.allowedOptionCodes().get(0);

            // candidateRef를 문장에 넣지 않는다. projection이 ref를 표시 문구로 바꾸므로
            // "위험 대응 조치 (해당 위험 후보)"처럼 같은 말이 겹친다. 구조화 필드로 이미 나간다.
            IssueDecision decision = new IssueDecision(
                    ko ? "위험 대응 조치" : "Risk response",
                    ko ? "제시된 대응 조치 옵션을 승인 및 이행하시겠습니까?"
                            : "Approve and carry out the proposed response option?",
                    recOption,
                    ko ? "서버 정책 엔진이 승인한 대응 절차에 따라 진행하십시오."
                            : "Proceed with the response steps allowed by the server policy engine.",
                    "LEADER",
                    "CURRENT_ASSIGNEE",
                    new IssueDeadline("LEADER_DECISION_REQUIRED", null),
                    candidate.allowedExecutionStepCodes(),
                    candidate.allowedCompletionSignalCodes()
            );

            AnalysisIssue issue = new AnalysisIssue(
                    priority,
                    candidate.candidateRef(),
                    candidate.severity(),
                    riskCodeLabel(candidate.riskCode(), ko),
                    ko ? "지정된 신호 및 마감 상태에 따른 서버 기본 검토 항목입니다."
                            : "A server baseline review item from the recorded signals and due state.",
                    Confidence.HIGH,
                    issueTaskRefs,
                    candidate.evidenceCodes(),
                    List.of(),
                    ko ? "확정 데이터 기준 기본 정책에 따라 대응이 필요합니다."
                            : "The baseline policy requires a response based on confirmed data.",
                    ko ? "제시된 조치 옵션 선택 및 실행 주체 지정"
                            : "Choose a response option and name the action owner",
                    decision
            );
            issues.add(issue);
        }

        return new AiWeeklyReportAnalysisV1(
                AiWeeklyReportAnalysisDtos.ANALYSIS_SCHEMA_VERSION,
                status,
                executiveJudgment,
                achievement,
                issues,
                List.of()
        );
    }

    /**
     * policy engine의 riskCode 12개를 사람이 읽는 제목으로 옮긴다.
     * 이 문자열은 이슈 제목으로 그대로 사용자 문서에 나가므로 영문 상수를 남기지 않는다.
     */
    private String riskCodeLabel(String riskCode, boolean ko) {
        return switch (riskCode) {
            case "APPROVED_UNASSIGNED_OVERDUE" -> ko ? "담당자 없이 마감이 지난 업무" : "Overdue task with no owner";
            case "APPROVED_UNASSIGNED" -> ko ? "담당자가 지정되지 않은 업무" : "Task with no owner";
            case "OVERDUE_ACTIVE" -> ko ? "마감이 지난 진행 업무" : "Active task past its due date";
            case "WORKLOAD_CONCENTRATION" -> ko ? "한 사람에게 몰린 업무" : "Work concentrated on one member";
            case "COMPLETION_RATE_DROP" -> ko ? "완료율 하락" : "Completion rate drop";
            case "SCHEDULE_CONFLICT" -> ko ? "일정과 겹치는 업무" : "Task conflicting with a scheduled event";
            case "APPROVAL_PENDING" -> ko ? "승인 대기 중인 업무" : "Task awaiting approval";
            case "CHECKLIST_NOT_STARTED" -> ko ? "체크리스트가 시작되지 않은 업무" : "Task with an untouched checklist";
            case "BACKLOG_GROWTH" -> ko ? "쌓이는 미착수 업무" : "Growing backlog";
            case "UNRESOLVED_MENTION" -> ko ? "응답이 없는 멘션" : "Unanswered mention";
            case "ON_HOLD_LONG" -> ko ? "오래 보류된 업무" : "Task on hold for a long time";
            case "RESOURCE_MISSING" -> ko ? "관련 자료가 없는 업무" : "Task with no linked resource";
            default -> ko ? "서버 기본 검토 항목" : "Server baseline review item";
        };
    }

    /** 계약의 achievement.evidenceTaskRefs maxItems와 같은 값이다. */
    private static final int ACHIEVEMENT_EVIDENCE_MAX = 5;

    private int completedCount(AiWeeklyReportSnapshotV1 snapshot) {
        if (snapshot.workflow() != null) return snapshot.workflow().completed();
        return snapshot.tasks() == null ? 0
                : (int) snapshot.tasks().stream().filter(t -> t.status() == TaskStatus.COMPLETED).count();
    }

    /**
     * 근거로 실은 건수와 실제 완료 건수가 다르면 그 사실을 밝힌다. 몇 건 중 몇 건인지
     * 적지 않으면 5건만 완료된 것처럼 읽힌다. 숫자는 Snapshot에 이미 있는 값만 쓴다.
     */
    private String achievementSummary(int shown, int total, boolean ko) {
        if (total > shown) {
            return ko ? "해당 기간에 완료 처리가 확인된 업무 " + total + "건 중 " + shown + "건입니다."
                    : "Showing " + shown + " of " + total + " tasks confirmed as completed in this period.";
        }
        return ko ? "해당 기간 중 완료 처리가 확인된 업무 " + total + "건입니다."
                : total + " task(s) confirmed as completed within this period.";
    }

    private static final int HEADLINE_MAX = 160;
    private static final int INTERPRETATION_MAX = 360;

    /**
     * Snapshot에 이미 확정된 숫자만 문장으로 옮긴다. 새 숫자는 만들지 않는다.
     * 위험 후보가 하나도 없더라도 KPI와 workflow 현황은 그대로 전달한다.
     */
    private String headline(AiWeeklyReportSnapshotV1 snapshot, boolean ko) {
        SnapshotMetrics metrics = snapshot.metrics();
        SnapshotWorkflow workflow = snapshot.workflow();
        int total = metrics != null ? metrics.periodTaskCount() : 0;
        if (total == 0 || workflow == null) {
            return ko ? "이번 기간에 집계된 확정 업무가 없습니다."
                    : "No confirmed tasks were recorded in this period.";
        }
        return trim(ko
                ? String.format("이번 기간 %d개 업무 중 %d개를 완료했고, %d개가 진행 중이며 %d개가 보류 상태입니다.",
                        total, workflow.completed(), workflow.inProgress(), workflow.onHold())
                : String.format("Of %d tasks this period, %d completed, %d in progress, and %d on hold.",
                        total, workflow.completed(), workflow.inProgress(), workflow.onHold()),
                HEADLINE_MAX);
    }

    private String interpretation(AiWeeklyReportSnapshotV1 snapshot, boolean noRiskCandidates, boolean ko) {
        SnapshotMetrics metrics = snapshot.metrics();
        SnapshotWorkflow workflow = snapshot.workflow();
        int total = metrics != null ? metrics.periodTaskCount() : 0;
        if (total == 0 || workflow == null) {
            return ko ? "해당 기간에 확정된 업무 데이터가 없어 서버 기본 분석은 현황만 보고합니다."
                    : "No confirmed task data in this period, so the server baseline analysis reports status only.";
        }

        List<String> sentences = new ArrayList<>();
        sentences.add(rateSentence(metrics, ko));

        String attention = attentionSentence(metrics, workflow, ko);
        if (attention != null) {
            sentences.add(attention);
        }

        String comparison = comparisonSentence(snapshot.comparison(), ko);
        if (comparison != null) {
            sentences.add(comparison);
        }

        if (noRiskCandidates) {
            sentences.add(ko ? "확정 수치 기준으로 추가 조치가 필요한 위험 후보는 선정되지 않았습니다."
                    : "Confirmed metrics selected no risk candidate needing further action.");
        }

        StringBuilder text = new StringBuilder();
        for (String sentence : sentences) {
            if (text.length() + sentence.length() + 1 > INTERPRETATION_MAX) break;
            if (!text.isEmpty()) text.append(' ');
            text.append(sentence);
        }
        return text.toString();
    }

    private String rateSentence(SnapshotMetrics metrics, boolean ko) {
        String rate = metrics.completionRatePercent() == null
                ? (ko ? "집계할 수 없음" : "not available")
                : metrics.completionRatePercent() + "%";
        if (ko) {
            return metrics.onTimeRatePercent() != null
                    ? "완료율은 " + rate + "이고, 완료 업무의 정시 완료율은 " + metrics.onTimeRatePercent() + "%입니다."
                    : "완료율은 " + rate + "이고, 완료 업무가 없어 정시 완료율은 집계되지 않았습니다.";
        }
        return metrics.onTimeRatePercent() != null
                ? "Completion rate is " + rate + ", and on-time delivery among completed tasks is "
                        + metrics.onTimeRatePercent() + "%."
                : "Completion rate is " + rate + ", and on-time delivery is not measured because no task was completed.";
    }

    /** 확인이 필요한 항목은 0이 아닌 것만 적는다. 없으면 없다고 분명히 적는다. */
    private String attentionSentence(SnapshotMetrics metrics, SnapshotWorkflow workflow, boolean ko) {
        List<String> items = new ArrayList<>();
        if (metrics.delayedCount() > 0) {
            items.add(ko ? "지연 업무 " + metrics.delayedCount() + "건"
                    : metrics.delayedCount() + " overdue");
        }
        if (workflow.requested() > 0) {
            items.add(ko ? "승인 대기 업무 " + workflow.requested() + "건"
                    : workflow.requested() + " awaiting approval");
        }
        if (workflow.acceptedUnassigned() > 0) {
            items.add(ko ? "담당자 미지정 업무 " + workflow.acceptedUnassigned() + "건"
                    : workflow.acceptedUnassigned() + " unassigned");
        }
        if (workflow.assignedNotStarted() > 0) {
            items.add(ko ? "착수 전 업무 " + workflow.assignedNotStarted() + "건"
                    : workflow.assignedNotStarted() + " not started");
        }
        if (items.isEmpty()) {
            return ko ? "지연·승인 대기·담당자 미지정 업무는 없습니다."
                    : "No overdue, pending-approval, or unassigned tasks.";
        }
        return ko
                ? String.join("과 ", items) + "을 우선 확인해야 합니다."
                : "Review " + String.join(", ", items) + " task(s) first.";
    }

    private String comparisonSentence(SnapshotComparison comparison, boolean ko) {
        if (comparison == null || comparison.status() != ComparisonStatus.AVAILABLE) {
            return null;
        }
        List<String> deltas = new ArrayList<>();
        if (comparison.periodTaskCountDelta() != null) {
            deltas.add(ko ? "업무 수 " + signed(comparison.periodTaskCountDelta()) + "개"
                    : "task count " + signed(comparison.periodTaskCountDelta()));
        }
        if (comparison.completionRatePointDelta() != null) {
            deltas.add(ko ? "완료율 " + signed(comparison.completionRatePointDelta()) + "%p"
                    : "completion rate " + signed(comparison.completionRatePointDelta()) + "%p");
        }
        if (comparison.delayedCountDelta() != null) {
            deltas.add(ko ? "지연 업무 " + signed(comparison.delayedCountDelta()) + "건"
                    : "overdue " + signed(comparison.delayedCountDelta()));
        }
        if (deltas.isEmpty()) return null;
        return ko
                ? "지난 기간 대비 " + String.join(", ", deltas) + " 변화입니다."
                : "Versus the previous period: " + String.join(", ", deltas) + ".";
    }

    /** Snapshot이 담고 있는 요청 언어를 그대로 따른다. 없으면 한국어로 본다. */
    private boolean isKorean(AiWeeklyReportSnapshotV1 snapshot) {
        return snapshot.reportContext() == null
                || snapshot.reportContext().language() != Language.EN;
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
