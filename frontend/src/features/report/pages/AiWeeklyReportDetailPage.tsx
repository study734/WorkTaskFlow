import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Link,
  Navigate,
  useNavigate,
  useParams,
  useSearchParams,
} from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { groupApi, GroupResponse } from '../../../api/groupApi';
import { AppNavigation } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { AiWeeklyReportPanel } from '../components/AiWeeklyReportPanel';
import type { ReportDensity } from '../components/AiReportContent';
import {
  readReportProjectionState,
  ReportProjectionState,
  writeReportProjectionState,
} from '../reportProjection';

export function AiWeeklyReportDetailPage() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const groupId = Number(useParams().groupId);
  const reportId = Number(useParams().reportId);
  const [group, setGroup] = useState<GroupResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const projectionState = useMemo(
    () => readReportProjectionState(searchParams),
    [searchParams],
  );

  function selectDensity(value: ReportDensity) {
    updateProjectionState({ ...projectionState, density: value });
  }

  const updateProjectionState = useCallback((value: ReportProjectionState) => {
    const next = writeReportProjectionState(searchParams, value);
    if (next.toString() !== searchParams.toString()) {
      setSearchParams(next, { replace: true });
    }
  }, [searchParams, setSearchParams]);

  useEffect(() => {
    updateProjectionState(projectionState);
  }, [projectionState, updateProjectionState]);

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
  const densityOptions: [ReportDensity, string, string][] = [
    ['STANDARD', t('표준', 'Standard'), t(
      '팀장이 과정·결과·진행 상황과 다음 행동을 한눈에 봅니다.',
      'Leader overview of progress, outcomes, risks, and next actions.',
    )],
    ['SUMMARY', t('요약', 'Summary'), t(
      '회의에 필요한 핵심 판단·결정 안건·실행 항목만 봅니다.',
      'Meeting brief with key judgement, decisions, and follow-up actions.',
    )],
    ['DETAILED', t('상세', 'Detailed'), t(
      '일별·팀원별·업무별 흐름과 근거까지 검토합니다.',
      'Review daily, member, task, and evidence-level detail.',
    )],
  ];

  return <>
    <AppNavigation />
    <main className="app-page ai-report-detail-page">
      <header className="ai-report-reader-header">
        <Link to={`/groups/${groupId}/dashboard`}>
          ← {t('그룹 대시보드로', 'Back to group dashboard')}
        </Link>
        {group && <span>{group.name}</span>}
      </header>
      <div className="ai-report-view-toolbar">
        <span>{t('보기 방식', 'View')}</span>
        <div className="report-density-controls" role="group"
          aria-label={t('리포트 표시 밀도', 'Report display density')}>
          {densityOptions.map(([value, label, description]) =>
            <button type="button" key={value}
              aria-label={label}
              aria-pressed={projectionState.density === value}
              title={description}
              onClick={() => selectDensity(value)}>
              <strong>{label}</strong>
              <small>{description}</small>
            </button>)}
        </div>
      </div>
      {error && <p className="error">{error}</p>}
      {group && <AiWeeklyReportPanel
        groupId={groupId}
        group={group}
        projectionState={projectionState}
        onProjectionStateChange={updateProjectionState}
        initialReportId={reportId}
        onReportIdChange={(nextReportId) => {
          if (nextReportId !== reportId) {
            const query = searchParams.toString();
            navigate(`/groups/${groupId}/reports/ai-weekly/${nextReportId}${query ? `?${query}` : ''}`, {
              replace: true,
            });
          }
        }}
      />}
    </main>
  </>;
}
