import { request, requestBlob, saveBlob } from './client';

export type ReportSchedule = {
  id: number; groupId: number; recipientEmail: string; weeklyEnabled: boolean; weeklyDay?: string;
  monthlyEnabled: boolean; monthlyDay?: number; language: 'KO' | 'EN' | 'BOTH'; active: boolean;
  weeklyEligible: boolean; monthlyEligible: boolean; weeklyMinimumDays: number; monthlyMinimumDays: number;
};

export type LocalReference = {
  ref: string;
  type: 'TASK' | 'OBJECTIVE' | 'MEMBER';
  label: string;
  url?: string;
  secondaryLabel?: string;
};

export type EvidenceValue = {
  key: string;
  label: string;
  value: string;
  kind: string;
};

export type NarrativeItemView = {
  text: string;
  evidenceKeys: string[];
  taskRefs: LocalReference[];
  objectiveRefs: LocalReference[];
};

export type RiskNarrativeItemView = NarrativeItemView & {
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
};

export type ActionNarrativeItemView = {
  priority: number;
  action: string;
  reason: string;
  owner?: LocalReference;
  evidenceKeys: string[];
  taskRefs: LocalReference[];
  objectiveRefs: LocalReference[];
};

export type DecisionNarrativeItemView = {
  question: string;
  impact: string;
  evidenceKeys: string[];
  taskRefs: LocalReference[];
  objectiveRefs: LocalReference[];
};

export type ReportAnalysis = {
  headline: string;
  summary: NarrativeItemView;
  changes: NarrativeItemView[];
  achievements: NarrativeItemView[];
  risks: RiskNarrativeItemView[];
  topActions: ActionNarrativeItemView[];
  leaderDecisions: DecisionNarrativeItemView[];
  limitations: NarrativeItemView[];
};

export type NarrativeDraftItem = {
  textTemplate: string;
  evidenceKeys: string[];
  taskRefs: string[];
  objectiveRefs: string[];
};

export type RiskNarrativeDraftItem = NarrativeDraftItem & {
  severity: 'LOW' | 'MEDIUM' | 'HIGH';
};

export type ActionNarrativeDraftItem = {
  priority: number;
  actionTemplate: string;
  reasonTemplate: string;
  ownerRef: string;
  evidenceKeys: string[];
  taskRefs: string[];
  objectiveRefs: string[];
};

export type DecisionNarrativeDraftItem = {
  questionTemplate: string;
  impactTemplate: string;
  evidenceKeys: string[];
  taskRefs: string[];
  objectiveRefs: string[];
};

export type NarrativeDraft = {
  headlineTemplate: string;
  summary: NarrativeDraftItem;
  changes: NarrativeDraftItem[];
  achievements: NarrativeDraftItem[];
  risks: RiskNarrativeDraftItem[];
  topActions: ActionNarrativeDraftItem[];
  leaderDecisions: DecisionNarrativeDraftItem[];
  limitations: NarrativeDraftItem[];
};

// 구버전 저장본에는 뒤쪽 두 항목이 없어 null로 역직렬화된다.
export type ComparisonMetrics = {
  available: boolean;
  totalTasksDelta?: number;
  completedTasksDelta?: number;
  delayedTasksDelta?: number;
  onHoldTasksDelta?: number;
  completionRateDeltaPercent?: number;
  checklistCompletionRateDeltaPercent?: number;
  onTimeRateDeltaPercent?: number;
  averageCompletionHoursDelta?: number;
};

// grade/score/rank는 서버가 동결 지표에서 결정적으로 계산한다. 화면은 계산하지 않는다.
// 근거가 없는 팀원은 grade가 없고(NOT_RATED) 순위에서 제외된다.
export type MemberWorkView = {
  member: LocalReference;
  assigned: number;
  active: number;
  completed: number;
  delayed: number;
  onTimeRatePercent?: number;
  grade?: string;
  score?: number;
  rank?: string;
};

export type TaskWorkView = {
  task: LocalReference;
  assignee?: LocalReference;
  objective?: LocalReference;
  status: string;
  priority: string;
  dueState: string;
  checklistTotal: number;
  checklistCompleted: number;
  blockerType?: string;
  blockerNextActionType?: string;
  blockerReviewWindow?: string;
  changes: string[];
};

export type OperationalView = {
  groupName: string;
  healthStatus: 'ON_TRACK' | 'NEEDS_ATTENTION' | 'AT_RISK';
  confidenceLevel: 'LOW' | 'MEDIUM' | 'HIGH';
  memberCount: number;
  activeMemberCount: number;
  members: MemberWorkView[];
  tasks: TaskWorkView[];
};

export type WeeklyAiReport = {
  reportId: number;
  status: 'PENDING' | 'GENERATING' | 'COMPLETED' | 'FAILED';
  publicationStatus: 'LEGACY' | 'DRAFT' | 'FINALIZED' | 'SUPERSEDED';
  periodStart: string;
  periodEnd: string;
  language: 'ko' | 'en';
  generatedAt?: string;
  finalizedAt?: string;
  revision: number;
  editorVersion: number;
  cached: boolean;
  metrics: {
    totalTasks: number;
    completionRatePercent?: number;
    onTimeRatePercent?: number;
    averageCompletionHours?: number;
    statuses: {
      requested: number;
      todo: number;
      inProgress: number;
      onHold: number;
      completed: number;
      rejected: number;
      cancelled: number;
      delayed: number;
    };
    historyCoverage: {
      status: 'COMPLETE' | 'PARTIAL';
      trackingStartedAt?: string;
    };
    checklist: {
      total: number;
      completed: number;
      completionRatePercent?: number;
    };
    daily: {
      date: string;
      created: number;
      completed: number;
    }[];
    members: {
      memberLabel: string;
      assigned: number;
      active: number;
      completed: number;
      delayed: number;
      onTimeRatePercent?: number;
    }[];
    riskSignals: {
      code: string;
      severity: 'LOW' | 'MEDIUM' | 'HIGH';
      evidenceKeys: string[];
    }[];
    evidence: Record<string, number>;
  };
  comparison: ComparisonMetrics;
  evidence: Record<string, EvidenceValue>;
  operations: OperationalView;
  analysis: ReportAnalysis | null;
  draft: NarrativeDraft | null;
  // evidence에 members.ratedCount가 있을 때만 서버가 내려주는 등급 산식 설명.
  gradeRule?: string;
};

export type CompletedWeeklyAiReport = WeeklyAiReport & {
  status: 'COMPLETED';
  analysis: ReportAnalysis;
};

export type RevisionSummary = {
  reportId: number;
  revision: number;
  status: WeeklyAiReport['status'];
  publicationStatus: WeeklyAiReport['publicationStatus'];
  generatedAt?: string;
  finalizedAt?: string;
};

function weeklyReportRequest<T extends WeeklyAiReport = WeeklyAiReport>(
  path: string,
  init: RequestInit = {},
): Promise<T> {
  return request<unknown>(path, init, true).then((value) => requireWeeklyReport(value) as T);
}

function requireWeeklyReport(value: unknown): WeeklyAiReport {
  // 저장된 구버전 JSON도 들어오므로 TypeScript 타입만 믿지 않고 화면 경계에서 fail-closed 검증한다.
  if (!isRecord(value)
    || typeof value.status !== 'string'
    || !isRecord(value.metrics)
    || !isRecord(value.metrics.historyCoverage)
    || !isRecord(value.operations)
    || typeof value.operations.groupName !== 'string'
    || typeof value.operations.healthStatus !== 'string'
    || typeof value.operations.confidenceLevel !== 'string'
    || !Array.isArray(value.operations.members)
    || !Array.isArray(value.operations.tasks)
    || (value.status === 'COMPLETED' && !isRecord(value.analysis))) {
    throw {
      code: 'AI_REPORT_CONTRACT_MISMATCH',
      message: '저장된 AI 리포트 형식이 현재 화면과 호환되지 않습니다. 다시 생성하거나 관리자에게 문의하세요.',
    };
  }
  return value as WeeklyAiReport;
}

function isRecord(value: unknown): value is Record<string, any> {
  return typeof value === 'object' && value !== null;
}

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
  findWeeklyAi: (
    groupId: number,
    weekStart: string,
    language: 'ko' | 'en',
  ) =>
    weeklyReportRequest(
      `/groups/${groupId}/reports/ai-weekly?weekStart=${encodeURIComponent(weekStart)}&language=${language}`,
      {},
    ),
  findWeeklyAiById: (groupId: number, reportId: number) =>
    weeklyReportRequest(
      `/groups/${groupId}/reports/ai-weekly/${reportId}`,
      {},
    ),
  revisions: (
    groupId: number,
    weekStart: string,
    language: 'ko' | 'en',
  ) =>
    request<RevisionSummary[]>(
      `/groups/${groupId}/reports/ai-weekly/revisions?weekStart=${encodeURIComponent(weekStart)}&language=${language}`,
      {},
      true,
    ),
  generateWeeklyAi: (
    groupId: number,
    weekStart: string,
    language: 'ko' | 'en',
  ) =>
    weeklyReportRequest<CompletedWeeklyAiReport>(
      `/groups/${groupId}/reports/ai-weekly`,
      {
        method: 'POST',
        body: JSON.stringify({ weekStart, language }),
      },
    ),
  editDraft: (
    groupId: number,
    reportId: number,
    expectedEditorVersion: number,
    content: NarrativeDraft,
  ) =>
    weeklyReportRequest<CompletedWeeklyAiReport>(
      `/groups/${groupId}/reports/ai-weekly/${reportId}/draft`,
      {
        method: 'PATCH',
        body: JSON.stringify({ expectedEditorVersion, content }),
      },
    ),
  regenerate: (
    groupId: number,
    reportId: number,
    expectedEditorVersion: number,
  ) =>
    weeklyReportRequest<CompletedWeeklyAiReport>(
      `/groups/${groupId}/reports/ai-weekly/${reportId}/regenerations`,
      {
        method: 'POST',
        body: JSON.stringify({ expectedEditorVersion }),
      },
    ),
  finalize: (
    groupId: number,
    reportId: number,
    expectedEditorVersion: number,
  ) =>
    weeklyReportRequest<CompletedWeeklyAiReport>(
      `/groups/${groupId}/reports/ai-weekly/${reportId}/finalization`,
      {
        method: 'POST',
        body: JSON.stringify({ expectedEditorVersion }),
      },
    ),
  downloadWeeklyAiPdf: (groupId: number, reportId: number) =>
    requestBlob(`/groups/${groupId}/reports/ai-weekly/${reportId}/pdf`, `toesa-ai-report-${reportId}.pdf`),
};
