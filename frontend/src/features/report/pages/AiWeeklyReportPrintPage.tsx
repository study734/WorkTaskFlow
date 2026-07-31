import { useEffect, useState } from 'react';
import { Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { AiWeeklyReportView, reportApi } from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';
import { AiReportContent } from '../components/AiReportContent';

export function AiWeeklyReportPrintPage() {
  const { t } = useLanguage();
  const groupId = Number(useParams().groupId);
  const reportId = Number(useParams().reportId);
  const [report, setReport] = useState<AiWeeklyReportView>();
  const [error, setError] = useState('');

  useEffect(() => {
    if (!Number.isInteger(groupId) || !Number.isInteger(reportId)) {
      setError(t('올바르지 않은 리포트 주소입니다.', 'This report address is invalid.'));
      return;
    }
    reportApi.findAiWeeklyById(groupId, reportId)
      .then((value) => setReport(value))
      .catch((caught) => setError(errorMessage(caught)));
  }, [groupId, reportId, t]);

  if (!accessToken.get()) {
    const next = `/groups/${groupId}/reports/ai-weekly/${reportId}/print`;
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />;
  }

  return <main className="ai-report-print-page">
    {!report && !error && <p>{t('리포트를 불러오는 중...', 'Loading report...')}</p>}
    {error && <p className="error">{error}</p>}
    {report && <>
      <div className="print-toolbar" style={{ marginBottom: '16px' }}>
        <button className="primary" type="button" onClick={() => window.print()}>
          {t('인쇄·PDF 저장', 'Print / save PDF')}
        </button>
      </div>
      <AiReportContent report={report} print />
    </>}
  </main>;
}
