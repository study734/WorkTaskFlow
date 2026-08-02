package com.teamproject.report.application.port;

import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;

/**
 * v7-2 AI 주간 리포트 분석 포트 인터페이스 (M6).
 * Application 계층은 OpenAI SDK 타입을 알지 못하며 이 인터페이스만 호출한다.
 */
public interface AiWeeklyReportGateway {

    /**
     * 분석 결과와 함께 토큰 사용량을 돌려준다. 사용량은 provider가 주지 않을 수 있으므로 null을
     * 허용한다. revision에 기록해 두지 않으면 이 기능이 얼마를 쓰는지 나중에도 알 수 없다.
     */
    record Analysis(AiWeeklyReportAnalysisV1 analysis, Integer inputTokens, Integer outputTokens) {
        public static Analysis of(AiWeeklyReportAnalysisV1 analysis) {
            return new Analysis(analysis, null, null);
        }
    }

    Analysis analyze(AiWeeklyReportSnapshotV1 snapshot);
}
