import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../../../api/client';
import { GroupResponse } from '../../../api/groupApi';
import { reportApi } from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';
import {
  isCompletedWeek as weekIsCompleted,
  lastCompletedWeekStart,
  mondayOf,
  weekEndInclusive,
  weekToExclusive,
} from '../../../app/week';

type Props = {
  groupId: number;
  group?: GroupResponse;
  selection: {
    scope: 'MY' | 'GROUP';
    period: 'WEEKLY' | 'MONTHLY' | 'YEARLY';
    from: string;
  };
};

export function AiWeeklyReportAction({ groupId, group, selection }: Props) {
  const { language, t } = useLanguage();
  const navigate = useNavigate();
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);

  const canManage = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const supportedSelection = selection.scope === 'GROUP' && selection.period === 'WEEKLY';

  // 화면의 주차 선택은 월요일 시작이 아닐 수 있다. 고른 날이 속한 주의 월요일만 잡고,
  // 어느 기간으로 생성되는지 버튼 아래에 그대로 적는다. 다른 주로 몰래 옮기지 않는다.
  const fromDate = mondayOf(selection.from);
  const toExclusive = weekToExclusive(fromDate);
  const targetPeriodText = `${fromDate} ~ ${weekEndInclusive(fromDate)}`;

  const isCompletedWeek = weekIsCompleted(fromDate, group?.timezone);

  const recentCompletedStart = lastCompletedWeekStart(group?.timezone);
  const recentCompletedText = `${recentCompletedStart} ~ ${weekEndInclusive(recentCompletedStart)}`;

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

      {canManage && supportedSelection && (
        <small style={{ color: '#4b5563' }}>
          {t(`AI 리포트 기간: ${targetPeriodText}`, `AI report period: ${targetPeriodText}`)}
        </small>
      )}

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
