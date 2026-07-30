import { useEffect, useState } from 'react';
import { errorMessage, saveBlob } from '../../../api/client';
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

// 기본 리포트의 주간 범위는 '월의 N번째 7일' 버킷이라 월요일 시작도 아니고 끝난 주도 아니다.
// AI 생성 계약은 완료된 월~일 주차만 받으므로, 선택 범위가 속한 주에서 마지막으로 끝난 주로 맞춘다.
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
  const [reportId, setReportId] = useState<number>();
  const [reportStatus, setReportStatus] = useState<string>();
  const [message, setMessage] = useState('');
  const [pending, setPending] = useState(false);
  const canManage = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const supportedSelection = selection.scope === 'GROUP' && selection.period === 'WEEKLY';

  useEffect(() => {
    setReportId(undefined);
    setReportStatus(undefined);
    setMessage('');
    if (!canManage || !supportedSelection) return;
    let active = true;
    reportApi.findWeeklyAi(groupId, weekStart, language)
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
  }, [canManage, groupId, language, weekStart, supportedSelection]);

  // 기본 리포트의 '한국어 다운로드'와 같은 방식이다. 현재 페이지는 이동하지 않고 별도 창을
  // 띄운 뒤 그 안에서 생성 상태를 보여주고, PDF는 blob 다운로드로 저장한다.
  // window.open은 클릭 핸들러에서 동기로 호출해야 팝업 차단을 통과한다.
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
    writeGeneratingWindow(reportWindow, t, language);
    setPending(true);
    try {
      const report = await resolveWeeklyAiReport(groupId, weekStart, language);
      if (reportWindow.closed) return;
      setReportId(report.reportId);
      setReportStatus(report.status);
      if (report.status !== 'COMPLETED') {
        reportWindow.close();
        setMessage(t(
          '리포트 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.',
          'The report could not be generated. Please try again shortly.',
        ));
        return;
      }
      // 팀장에게는 검토 대기 상태를 두지 않는다. 새 revision은 DRAFT로 생성되므로 바로 확정해
      // 확정본만 화면과 PDF에 나가게 한다. 확정은 같은 주차의 이전 확정본을 SUPERSEDED로 바꾸고
      // 팀원에게 공개된다.
      const finalized = report.publicationStatus === 'FINALIZED'
        ? report
        : await reportApi.finalize(groupId, report.reportId, report.editorVersion);
      if (reportWindow.closed) return;
      writeReportWindow(reportWindow, finalized, {
        t,
        language,
        onDownload: async () => {
          const file = await reportApi.downloadWeeklyAiPdf(groupId, report.reportId);
          saveBlob(file.blob, file.filename);
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
  const availabilityDescriptionId = `ai-report-availability-${groupId}`;
  const hasSavedReport = Boolean(reportId) && reportStatus === 'COMPLETED';

  return <div className="ai-report-action">
    <button className="report-download ai-report-button" type="button"
      disabled={Boolean(unavailableReason) || pending}
      title={unavailableReason || undefined}
      aria-describedby={unavailableReason ? availabilityDescriptionId : undefined}
      onClick={() => void openAiReport()}>
      {pending
        ? t('생성 중...', 'Generating...')
        : hasSavedReport ? t('AI 리포트 열기', 'Open AI report') : t('AI 리포트', 'AI report')}
    </button>
    {unavailableReason &&
      <span className="sr-only" id={availabilityDescriptionId}>{unavailableReason}</span>}
    {message && <small className="error">{message}</small>}
  </div>;
}
