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
  /** false면 서버가 저장된 revision을 그대로 돌려준 것이다. OpenAI를 부르지 않았다. */
  createdNew: boolean;
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
  // OpenAI 왕복이 기본 30초를 넘긴다(업무 15건 기준 31.8초 관측). 넉넉히 잡지 않으면
  // 서버가 성공해 저장까지 마친 뒤에 프런트만 실패로 끊긴다.
  generateAiWeekly: (groupId: number, body: GenerateReportRequest) =>
    request<GenerateReportResponse>(`/groups/${groupId}/reports/ai-weekly`, {
      method: 'POST',
      body: JSON.stringify(body),
    }, true, 120_000),
  // 기본 리포트와 같다. 서버가 완성된 HTML을 주고 PDF 저장은 브라우저 인쇄로 한다.
  downloadAiWeeklyDocument: async (groupId: number, reportId: number, from: string, revision: number) => {
    const result = await requestBlob(`/groups/${groupId}/reports/ai-weekly/${reportId}/download`,
      `toesa-ai-weekly-${groupId}-${from}-r${revision}.html`);
    saveBlob(result.blob, result.filename);
  },
};
