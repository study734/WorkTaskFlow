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

        String headline = riskCandidates.isEmpty()
                ? "확정 수치 기준 추가 조치가 필요한 특이사항이 없습니다."
                : "확정 업무 수치와 지정된 위험 후보 기준의 서버 기본 분석입니다.";
        String interpretation = "서버 정책 엔진이 도출한 결정론적 항목에 따른 기본 요약 결과입니다.";

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
}
