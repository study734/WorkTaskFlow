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

        String headline = headline(snapshot);
        String interpretation = interpretation(snapshot, riskCandidates.isEmpty());

        ExecutiveJudgment executiveJudgment = new ExecutiveJudgment(
                headline,
                interpretation,
                metricRefs,
                ejEvidenceTasks,
                Confidence.HIGH,
                List.of()
        );

        // Achievement (Max 1 completed task)
        Achievement achievement;
        SnapshotTask completedTask = null;
        if (snapshot.tasks() != null) {
            completedTask = snapshot.tasks().stream()
                    .filter(t -> t.status() == TaskStatus.COMPLETED)
                    .findFirst()
                    .orElse(null);
        }

        if (completedTask != null) {
            achievement = new Achievement(
                    AchievementStatus.AVAILABLE,
                    "주간 업무 완료 성과",
                    "해당 기간 중 성공적으로 완료 처리된 업무입니다.",
                    List.of(completedTask.taskRef())
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

            IssueDecision decision = new IssueDecision(
                    "위험 대응 조치 (" + candidate.candidateRef() + ")",
                    "제시된 대응 조치 옵션을 승인 및 이행하시겠습니까?",
                    recOption,
                    "서버 정책 엔진이 승인한 대응 절차에 따라 진행하십시오.",
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
                    "위험 후보 " + candidate.candidateRef() + " (" + candidate.riskCode() + ")",
                    "지정된 신호 및 마감 상태에 따른 서버 기본 검토 항목입니다.",
                    Confidence.HIGH,
                    issueTaskRefs,
                    candidate.evidenceCodes(),
                    List.of(),
                    "확정 데이터 기준 기본 정책에 따라 대응이 필요합니다.",
                    "제시된 조치 옵션 선택 및 실행 주체 지정",
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

    private static final int HEADLINE_MAX = 160;
    private static final int INTERPRETATION_MAX = 360;

    /**
     * Snapshot에 이미 확정된 숫자만 문장으로 옮긴다. 새 숫자는 만들지 않는다.
     * 위험 후보가 하나도 없더라도 KPI와 workflow 현황은 그대로 전달한다.
     */
    private String headline(AiWeeklyReportSnapshotV1 snapshot) {
        SnapshotMetrics metrics = snapshot.metrics();
        SnapshotWorkflow workflow = snapshot.workflow();
        int total = metrics != null ? metrics.periodTaskCount() : 0;
        if (total == 0 || workflow == null) {
            return "이번 주 기간에 집계된 확정 업무가 없습니다.";
        }
        return trim(String.format(
                "이번 주 %d개 업무 중 %d개를 완료했고, %d개가 진행 중이며 %d개가 보류 상태입니다.",
                total, workflow.completed(), workflow.inProgress(), workflow.onHold()),
                HEADLINE_MAX);
    }

    private String interpretation(AiWeeklyReportSnapshotV1 snapshot, boolean noRiskCandidates) {
        SnapshotMetrics metrics = snapshot.metrics();
        SnapshotWorkflow workflow = snapshot.workflow();
        int total = metrics != null ? metrics.periodTaskCount() : 0;
        if (total == 0 || workflow == null) {
            return "해당 기간에 확정된 업무 데이터가 없어 서버 기본 분석은 현황만 보고합니다.";
        }

        List<String> sentences = new ArrayList<>();
        sentences.add(rateSentence(metrics));

        String attention = attentionSentence(metrics, workflow);
        if (attention != null) {
            sentences.add(attention);
        }

        String comparison = comparisonSentence(snapshot.comparison());
        if (comparison != null) {
            sentences.add(comparison);
        }

        if (noRiskCandidates) {
            sentences.add("확정 수치 기준으로 추가 조치가 필요한 위험 후보는 선정되지 않았습니다.");
        }

        StringBuilder text = new StringBuilder();
        for (String sentence : sentences) {
            if (text.length() + sentence.length() + 1 > INTERPRETATION_MAX) break;
            if (!text.isEmpty()) text.append(' ');
            text.append(sentence);
        }
        return text.toString();
    }

    private String rateSentence(SnapshotMetrics metrics) {
        StringBuilder text = new StringBuilder();
        text.append("완료율은 ")
                .append(metrics.completionRatePercent() == null ? "집계할 수 없음"
                        : metrics.completionRatePercent() + "%")
                .append("이고");
        if (metrics.onTimeRatePercent() != null) {
            text.append(", 완료 업무의 정시 완료율은 ")
                    .append(metrics.onTimeRatePercent()).append("%입니다.");
        } else {
            text.append(", 완료 업무가 없어 정시 완료율은 집계되지 않았습니다.");
        }
        return text.toString();
    }

    /** 확인이 필요한 항목은 0이 아닌 것만 적는다. 없으면 없다고 분명히 적는다. */
    private String attentionSentence(SnapshotMetrics metrics, SnapshotWorkflow workflow) {
        List<String> items = new ArrayList<>();
        if (metrics.delayedCount() > 0) items.add("지연 업무 " + metrics.delayedCount() + "건");
        if (workflow.requested() > 0) items.add("승인 대기 업무 " + workflow.requested() + "건");
        if (workflow.acceptedUnassigned() > 0) {
            items.add("담당자 미지정 업무 " + workflow.acceptedUnassigned() + "건");
        }
        if (workflow.assignedNotStarted() > 0) {
            items.add("착수 전 업무 " + workflow.assignedNotStarted() + "건");
        }
        if (items.isEmpty()) {
            return "지연·승인 대기·담당자 미지정 업무는 없습니다.";
        }
        return String.join("과 ", items) + "을 우선 확인해야 합니다.";
    }

    private String comparisonSentence(SnapshotComparison comparison) {
        if (comparison == null || comparison.status() != ComparisonStatus.AVAILABLE) {
            return null;
        }
        List<String> deltas = new ArrayList<>();
        if (comparison.periodTaskCountDelta() != null) {
            deltas.add("업무 수 " + signed(comparison.periodTaskCountDelta()) + "개");
        }
        if (comparison.completionRatePointDelta() != null) {
            deltas.add("완료율 " + signed(comparison.completionRatePointDelta()) + "%p");
        }
        if (comparison.delayedCountDelta() != null) {
            deltas.add("지연 업무 " + signed(comparison.delayedCountDelta()) + "건");
        }
        if (deltas.isEmpty()) return null;
        return "지난 주 대비 " + String.join(", ", deltas) + " 변화입니다.";
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private String trim(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
