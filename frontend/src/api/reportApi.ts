import { request, requestBlob, saveBlob } from './client';

export type ReportSchedule = {
  id: number; groupId: number; recipientEmail: string; weeklyEnabled: boolean; weeklyDay?: string;
  monthlyEnabled: boolean; monthlyDay?: number; language: 'KO' | 'EN' | 'BOTH'; active: boolean;
  weeklyEligible: boolean; monthlyEligible: boolean; weeklyMinimumDays: number; monthlyMinimumDays: number;
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
};
