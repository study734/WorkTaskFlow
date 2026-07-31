import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../../../api/client';
import { GroupResponse } from '../../../api/groupApi';
import { reportApi } from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';
import { lastCompletedWeekStart } from '../../../app/week';

type Props = {
  groupId: number;
  group?: GroupResponse;
  selection: {
    scope: 'MY' | 'GROUP';
    period: 'WEEKLY' | 'MONTHLY' | 'YEARLY';
    from: string;
  };
};

function getToExclusive(fromStr: string): string {
  const d = new Date(`${fromStr}T00:00:00`);
  if (Number.isNaN(d.getTime())) return fromStr;
  d.setDate(d.getDate() + 7);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const date = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${date}`;
}

function formatInclusiveEnd(fromStr: string): string {
  const d = new Date(`${fromStr}T00:00:00`);
  if (Number.isNaN(d.getTime())) return fromStr;
  d.setDate(d.getDate() + 6);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const date = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${month}-${date}`;
}

function getZonedTodayString(timeZone?: string): string {
  const now = new Date();
  if (!timeZone) {
    const month = String(now.getMonth() + 1).padStart(2, '0');
    const date = String(now.getDate()).padStart(2, '0');
    return `${now.getFullYear()}-${month}-${date}`;
  }
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
  }).formatToParts(now);
  const number = (type: Intl.DateTimeFormatPartTypes) =>
    Number(parts.find((part) => part.type === type)?.value);
  const year = number('year');
  const month = String(number('month')).padStart(2, '0');
  const day = String(number('day')).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

export function AiWeeklyReportAction({ groupId, group, selection }: Props) {
  const { language, t } = useLanguage();
  const navigate = useNavigate();
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);

  const canManage = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const supportedSelection = selection.scope === 'GROUP' && selection.period === 'WEEKLY';

  const fromDate = selection.from;
  const toExclusive = getToExclusive(fromDate);
  const todayStr = getZonedTodayString(group?.timezone);

  // Check if week is completed: toExclusive must be <= today
  const isCompletedWeek = toExclusive <= todayStr;

  const recentCompletedStart = lastCompletedWeekStart(group?.timezone);
  const recentCompletedEnd = formatInclusiveEnd(recentCompletedStart);
  const recentCompletedText = `${recentCompletedStart} ~ ${recentCompletedEnd}`;

  async function handleGenerate() {
    if (!isCompletedWeek) {
      setMessage(t(
        'AI 주간 리포트는 완료된 주간만 생성할 수 있습니다.',
        'AI weekly reports can only be generated for completed weeks.'
      ));
      return;
    }
    setMessage('');
    setPending(true);
    const langCode = language === 'en' ? 'EN' : 'KO';
    try {
      const res = await reportApi.generateAiWeekly(groupId, {
        from: fromDate,
        toExclusive,
        language: langCode,
        regenerate: false,
      });

      navigate(`/groups/${groupId}/reports/ai-weekly/${res.reportId}`);
    } catch (caught) {
      setMessage(errorMessage(caught));
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
      : !isCompletedWeek
        ? t('AI 주간 리포트는 완료된 주간만 생성할 수 있습니다.', 'AI weekly reports can only be generated for completed weeks.')
        : '';

  return (
    <div className="ai-report-action" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
      <button
        className="report-download ai-report-button"
        type="button"
        disabled={Boolean(unavailableReason) || pending}
        title={unavailableReason || undefined}
        onClick={() => void handleGenerate()}
      >
        {pending ? t('생성 중...', 'Generating...') : t('AI 리포트', 'AI report')}
      </button>

      {!isCompletedWeek && canManage && supportedSelection && (
        <div className="uncompleted-week-notice" style={{ fontSize: '12px', color: '#d97706', marginTop: '4px' }}>
          <span>{t('AI 주간 리포트는 완료된 주간만 생성할 수 있습니다.', 'AI weekly reports can only be generated for completed weeks.')}</span>
          <br />
          <small style={{ color: '#4b5563' }}>
            {t(`최근 완료 주간: ${recentCompletedText}`, `Recent completed week: ${recentCompletedText}`)}
          </small>
        </div>
      )}

      {message && <small className="error">{message}</small>}
    </div>
  );
}
