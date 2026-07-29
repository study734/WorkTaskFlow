import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { errorMessage } from '../../../api/client';
import type { ApiError } from '../../../api/client';
import { GroupResponse } from '../../../api/groupApi';
import { reportApi } from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';

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
  const [reportId, setReportId] = useState<number>();
  const [reportStatus, setReportStatus] = useState<string>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const canManage = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const supportedSelection = selection.scope === 'GROUP' && selection.period === 'WEEKLY';

  useEffect(() => {
    setReportId(undefined);
    setReportStatus(undefined);
    setMessage('');
    if (!canManage || !supportedSelection) return;
    let active = true;
    reportApi.findWeeklyAi(groupId, selection.from, language)
      .then((report) => {
        if (!active) return;
        setReportId(report.reportId);
        setReportStatus(report.status);
      })
      .catch((caught: ApiError) => {
        if (!active || caught.code === 'AI_REPORT_NOT_FOUND') return;
        setMessage(errorMessage(caught));
      });
    return () => {
      active = false;
    };
  }, [canManage, groupId, language, selection.from, supportedSelection]);

  async function openAiReport() {
    if (reportId && reportStatus !== 'GENERATING') {
      navigate(`/groups/${groupId}/reports/ai-weekly/${reportId}`);
      return;
    }
    setPending(true);
    setMessage('');
    try {
      const report = await reportApi.generateWeeklyAi(groupId, selection.from, language);
      navigate(`/groups/${groupId}/reports/ai-weekly/${report.reportId}`);
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
      : '';
  const availabilityDescriptionId = `ai-report-availability-${groupId}`;

  return <div className="ai-report-action">
    <button className="report-download ai-report-button" type="button"
      disabled={pending || Boolean(unavailableReason)}
      title={unavailableReason || undefined}
      aria-describedby={unavailableReason ? availabilityDescriptionId : undefined}
      onClick={() => void openAiReport()}>
      {pending ? t('AI 리포트 준비 중...', 'Preparing AI report...') : t('AI 리포트', 'AI report')}
    </button>
    {unavailableReason &&
      <span className="sr-only" id={availabilityDescriptionId}>{unavailableReason}</span>}
    {message && <small className="error">{message}</small>}
  </div>;
}
