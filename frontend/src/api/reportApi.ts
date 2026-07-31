import { request, requestBlob, saveBlob } from './client';

export type ReportSchedule = {
  id: number; groupId: number; recipientEmail: string; weeklyEnabled: boolean; weeklyDay?: string;
  monthlyEnabled: boolean; monthlyDay?: number; language: 'KO' | 'EN' | 'BOTH'; active: boolean;
  weeklyEligible: boolean; monthlyEligible: boolean; weeklyMinimumDays: number; monthlyMinimumDays: number;
};

export type GenerateReportRequest = {
  from: string;
  toExclusive: string;
  language: 'KO' | 'EN';
  regenerate: boolean;
};

export type GenerateReportResponse = {
  reportId: number;
  groupId: number;
  from: string;
  toExclusive: string;
  revision: number;
  status: 'FINALIZED';
  analysisMode: 'OPENAI' | 'SERVER_FALLBACK';
  generatedAt: string;
  downloadUrl: string;
};

export type ExecutiveJudgmentView = {
  headline: string;
  interpretation: string;
  metricRefs?: string[];
  evidenceTaskRefs?: string[];
  evidenceTaskTitles?: string[];
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  missingEvidence?: string[];
};

export type AchievementView = {
  status: string;
  headline: string;
  summary: string;
  evidenceTaskRefs?: string[];
  evidenceTaskTitles?: string[];
};

export type DeadlineView = {
  source: string;
  referenceRef?: string;
  referenceTitle?: string;
};

export type DecisionView = {
  title: string;
  question: string;
  recommendedOptionCode?: string;
  recommendation: string;
  decisionMakerRole?: string;
  actionOwnerRole?: string;
  deadline?: DeadlineView;
  executionStepCodes?: string[];
  completionSignalCodes?: string[];
};

export type IssueView = {
  priority: 'P1' | 'P2' | 'P3';
  candidateRef: string;
  severity: 'HIGH' | 'MEDIUM' | 'LOW';
  title: string;
  realTaskTitle: string;
  impact: string;
  confidence: 'HIGH' | 'MEDIUM' | 'LOW';
  taskRefs?: string[];
  taskTitles?: string[];
  evidenceCodes?: string[];
  missingEvidence?: string[];
  integratedJudgment?: string;
  requiredDecision?: string;
  decision?: DecisionView;
};

export type SnapshotMetricsView = {
  periodTaskCount: number;
  completionRatePercent?: number;
  onTimeRatePercent?: number;
  delayedCount: number;
  averageLeadTimeHours?: number;
};

export type SnapshotComparisonView = {
  status: 'AVAILABLE' | 'NO_BASELINE';
  previousPeriodFrom?: string;
  previousPeriodToExclusive?: string;
  taskCountDiff?: number;
  completionRateDiffPercent?: number;
  onTimeRateDiffPercent?: number;
  delayedCountDiff?: number;
};

export type SnapshotWorkflowView = {
  requestedCount: number;
  todoUnassignedCount: number;
  todoAssignedCount: number;
  inProgressCount: number;
  onHoldCount: number;
  completedCount: number;
};

export type SnapshotTaskView = {
  taskRef: string;
  realTitle: string;
  safeLabel: string;
  status: string;
  priority?: string;
  assigneeRef?: string;
  assigneeName?: string;
  createdAt?: string;
  dueAt?: string;
  completedAt?: string;
  dueState?: string;
  calendarEventRefs?: string[];
};

export type SnapshotMemberView = {
  memberRef: string;
  realName: string;
  role: string;
  periodTaskCount: number;
  activeTaskCount: number;
  completedTaskCount: number;
  delayedTaskCount: number;
  onTimeRatePercent?: number;
  upcomingEventCount: number;
};

export type CalendarConstraintView = {
  eventRef: string;
  realTitle: string;
  eventType: string;
  safeLabel: string;
  startAt: string;
  endAt: string;
  relatedTaskRefs?: string[];
};

export type AiWeeklyReportView = {
  reportId: number;
  groupId: number;
  from: string;
  toExclusive: string;
  revision: number;
  status: 'FINALIZED';
  analysisMode: 'OPENAI' | 'SERVER_FALLBACK';
  generatedAt: string;
  downloadUrl: string;
  executiveJudgment?: ExecutiveJudgmentView;
  achievement?: AchievementView;
  issues?: IssueView[];
  globalMissingEvidence?: string[];
  metrics?: SnapshotMetricsView;
  comparison?: SnapshotComparisonView;
  workflow?: SnapshotWorkflowView;
  tasks?: SnapshotTaskView[];
  members?: SnapshotMemberView[];
  calendarConstraints?: CalendarConstraintView[];
};

export const reportApi = {
  schedule: (groupId: number) => request<ReportSchedule>(`/groups/${groupId}/reports/schedule`, {}, true),
  updateSchedule: (groupId: number, body: {
    recipientEmail: string; weeklyEnabled: boolean; weeklyDay?: string;
    monthlyEnabled: boolean; monthlyDay?: number; language: 'KO' | 'EN' | 'BOTH';
  }) => request<ReportSchedule>(`/groups/${groupId}/reports/schedule`, {
    method: 'PUT', body: JSON.stringify(body),
  }, true),
  download: async (groupId: number, from: string, to: string, language: 'KO' | 'EN') => {
    const result = await requestBlob(`/groups/${groupId}/reports/download?from=${from}&to=${to}&language=${language}`,
      `toesa-report-${language.toLowerCase()}.html`);
    saveBlob(result.blob, result.filename);
  },
  generateAiWeekly: (groupId: number, body: GenerateReportRequest) =>
    request<GenerateReportResponse>(`/groups/${groupId}/reports/ai-weekly`, {
      method: 'POST',
      body: JSON.stringify(body),
    }, true),
  findAiWeeklyById: (groupId: number, reportId: number) =>
    request<AiWeeklyReportView>(`/groups/${groupId}/reports/ai-weekly/${reportId}`, {}, true),
  downloadAiWeeklyPdf: async (groupId: number, reportId: number, from: string, revision: number) => {
    const result = await requestBlob(`/groups/${groupId}/reports/ai-weekly/${reportId}/pdf`, `ai-weekly-report-${from}-r${revision}.pdf`);
    saveBlob(result.blob, result.filename);
  },
};
