package com.teamproject.report.application.port;

import com.teamproject.report.application.dto.AiWeeklyReportAnalysisDtos.AiWeeklyReportAnalysisV1;
import com.teamproject.report.application.dto.AiWeeklyReportDtos.AiWeeklyReportSnapshotV1;

/**
 * v7-2 AI 주간 리포트 분석 포트 인터페이스 (M6).
 * Application 계층은 OpenAI SDK 타입을 알지 못하며 이 인터페이스만 호출한다.
 */
public interface AiWeeklyReportGateway {
    AiWeeklyReportAnalysisV1 analyze(AiWeeklyReportSnapshotV1 snapshot);
}
