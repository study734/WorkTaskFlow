package com.teamproject.report.application;

import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AnalysisIssue;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.Confidence;
import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.ExecutiveJudgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 검증 전에 돌리는 보수적 교정.
 *
 * <p>규칙 하나를 어겼다고 응답 전체를 버리면, 값을 치른 분석이 통째로 템플릿 문서로 바뀐다.
 * 실제로 "HIGH인데 missingEvidence가 있다"는 이유로 이슈 두 건이 걸려 리포트 한 장이
 * SERVER_FALLBACK이 됐다. 프롬프트에 이미 있는 규칙이고 모델이 가끔 어긴다.
 *
 * <p>여기서 하는 일은 <b>주장을 약하게 만드는 것</b>뿐이다. 없던 내용을 지어내거나 근거를
 * 만들어 붙이지 않는다. 강도를 올리는 교정도 하지 않는다 — 그건 모델이 하지 않은 주장을
 * 서버가 대신 하는 것이다. 참조 무결성·허용 코드·상태 같은 안전 규칙은 손대지 않고
 * validator가 그대로 거부한다.
 */
@Component
public class AiWeeklyReportAnalysisRepair {

    public record Result(AiWeeklyReportAnalysisV1 analysis, List<String> repairs) {
        public boolean repaired() {
            return !repairs.isEmpty();
        }
    }

    public Result repair(AiWeeklyReportAnalysisV1 analysis) {
        if (analysis == null) return new Result(null, List.of());
        List<String> repairs = new ArrayList<>();

        ExecutiveJudgment judgment = analysis.executiveJudgment();
        if (overconfident(judgment == null ? null : judgment.confidence(),
                judgment == null ? null : judgment.missingEvidence())) {
            judgment = new ExecutiveJudgment(judgment.headline(), judgment.interpretation(),
                    judgment.metricRefs(), judgment.evidenceTaskRefs(), Confidence.MEDIUM,
                    judgment.missingEvidence());
            repairs.add("executiveJudgment confidence HIGH -> MEDIUM");
        }

        List<AnalysisIssue> issues = new ArrayList<>();
        List<AnalysisIssue> source = analysis.issues() == null ? List.of() : analysis.issues();
        for (int i = 0; i < source.size(); i++) {
            AnalysisIssue issue = source.get(i);
            if (overconfident(issue.confidence(), issue.missingEvidence())) {
                issue = new AnalysisIssue(issue.priority(), issue.candidateRef(), issue.severity(),
                        issue.title(), issue.impact(), Confidence.MEDIUM, issue.taskRefs(),
                        issue.evidenceCodes(), issue.missingEvidence(), issue.integratedJudgment(),
                        issue.requiredDecision(), issue.decision());
                repairs.add("issue[" + i + "] confidence HIGH -> MEDIUM");
            }
            issues.add(issue);
        }

        if (repairs.isEmpty()) return new Result(analysis, List.of());

        return new Result(new AiWeeklyReportAnalysisV1(analysis.schemaVersion(),
                analysis.analysisStatus(), judgment, analysis.achievement(), issues,
                analysis.globalMissingEvidence()), repairs);
    }

    /** 근거가 부족하다고 적어 놓고 확신은 최고로 둔 상태. 확신 쪽을 낮춘다. */
    private boolean overconfident(Confidence confidence, List<String> missingEvidence) {
        return confidence == Confidence.HIGH && missingEvidence != null && !missingEvidence.isEmpty();
    }
}
