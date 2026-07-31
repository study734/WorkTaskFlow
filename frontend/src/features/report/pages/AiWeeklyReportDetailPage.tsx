import { useEffect, useState } from 'react';
import {
  Link,
  Navigate,
  useNavigate,
  useParams,
} from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { groupApi, GroupResponse } from '../../../api/groupApi';
import { AppNavigation } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { AiWeeklyReportPanel } from '../components/AiWeeklyReportPanel';

export function AiWeeklyReportDetailPage() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const groupId = Number(useParams().groupId);
  const reportId = Number(useParams().reportId);
  const [group, setGroup] = useState<GroupResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1
      || !Number.isInteger(reportId) || reportId < 1) {
      setError(t('올바르지 않은 리포트 주소입니다.', 'This report address is invalid.'));
      setLoading(false);
      return;
    }
    groupApi.get(groupId)
      .then(setGroup)
      .catch((caught) => setError(errorMessage(caught)))
      .finally(() => setLoading(false));
  }, [groupId, reportId, t]);

  if (!accessToken.get()) {
    const next = `/groups/${groupId}/reports/ai-weekly/${reportId}`;
    return <Navigate to={`/login?next=${encodeURIComponent(next)}`} replace />;
  }
  if (loading) {
    return <main className="center-page">
      {t('리포트를 불러오는 중...', 'Loading report...')}
    </main>;
  }

  return <>
    <AppNavigation />
    <main className="app-page ai-report-detail-page">
      <header className="ai-report-reader-header">
        <Link to={`/groups/${groupId}/dashboard`}>
          ← {t('그룹 대시보드로', 'Back to group dashboard')}
        </Link>
        {group && <span style={{ marginLeft: '12px', fontWeight: 600 }}>{group.name}</span>}
      </header>
      {error && <p className="error">{error}</p>}
      {group && <AiWeeklyReportPanel
        groupId={groupId}
        group={group}
        initialReportId={reportId}
        onReportIdChange={(nextReportId) => {
          if (nextReportId !== reportId) {
            navigate(`/groups/${groupId}/reports/ai-weekly/${nextReportId}`, {
              replace: true,
            });
          }
        }}
      />}
    </main>
  </>;
}
