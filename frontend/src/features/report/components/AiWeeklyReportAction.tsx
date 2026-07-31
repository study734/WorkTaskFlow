import { useState } from 'react';
import { errorMessage } from '../../../api/client';
import type { ApiError } from '../../../api/client';
import { GroupResponse } from '../../../api/groupApi';
import { reportApi } from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';
import {
  aiReportMessage,
  openReportWindow,
  resolveWeeklyAiReport,
  writeGeneratingWindow,
  writeReportWindow,
} from './aiReportWindow';

type Props = {
  groupId: number;
  group?: GroupResponse;
  selection: {
    scope: 'MY' | 'GROUP';
    period: 'WEEKLY' | 'MONTHLY' | 'YEARLY';
    from: string;
  };
};

function completedWeekStart(from: string) {
  const base = new Date(`${from}T00:00:00`);
  if (Number.isNaN(base.getTime())) return from;
  const monday = new Date(base);
  monday.setDate(base.getDate() - ((base.getDay() + 6) % 7));
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const DAY = 86_400_000;
  while (monday.getTime() + 6 * DAY >= today.getTime()) {
    monday.setDate(monday.getDate() - 7);
  }
  const month = String(monday.getMonth() + 1).padStart(2, '0');
  const date = String(monday.getDate()).padStart(2, '0');
  return `${monday.getFullYear()}-${month}-${date}`;
}

export function AiWeeklyReportAction({ groupId, group, selection }: Props) {
  const { language, t } = useLanguage();
  const weekStart = completedWeekStart(selection.from);
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);
  const canManage = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const supportedSelection = selection.scope === 'GROUP' && selection.period === 'WEEKLY';

  async function openAiReport() {
    setMessage('');
    const reportWindow = openReportWindow();
    if (!reportWindow) {
      setMessage(t(
        '팝업이 차단되어 리포트를 열지 못했습니다. 이 사이트의 팝업을 허용해 주세요.',
        'The report could not open because pop-ups are blocked. Allow pop-ups for this site.',
      ));
      return;
    }
    const langCode = language === 'en' ? 'EN' : 'KO';
    writeGeneratingWindow(reportWindow, t, langCode);
    setPending(true);
    try {
      const reportView = await resolveWeeklyAiReport(groupId, weekStart, langCode);
      if (reportWindow.closed) return;

      writeReportWindow(reportWindow, reportView, {
        t,
        language: langCode,
        onDownload: async () => {
          await reportApi.downloadAiWeeklyPdf(groupId, reportView.reportId, reportView.from, reportView.revision);
        },
      });
      setMessage('');
    } catch (caught) {
      reportWindow.close();
      setMessage(aiReportMessage(caught as ApiError, t));
    } finally {
      setPending(false);
    }
  }

  const unavailableReason = !canManage
    ? t('AI 리포트는 유료 그룹 팀장만 사용할 수 있습니다.', 'AI reports require a paid-group leader.')
    : !supportedSelection
      ? t(
        'AI 리포트는 그룹 전체·주간 기본 리포트를 사용합니다.',
        'AI reports use whole-group weekly basic reports.',
      )
      : '';

  return (
    <div className="ai-report-action">
      <button
        className="report-download ai-report-button"
        type="button"
        disabled={Boolean(unavailableReason) || pending}
        title={unavailableReason || undefined}
        onClick={() => void openAiReport()}
      >
        {pending ? t('생성 중...', 'Generating...') : t('AI 리포트', 'AI report')}
      </button>
      {message && <small className="error">{message}</small>}
    </div>
  );
}
