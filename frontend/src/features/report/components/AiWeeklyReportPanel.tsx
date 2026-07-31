import { useEffect, useState } from 'react';
import { ApiError, errorMessage } from '../../../api/client';
import { GroupResponse } from '../../../api/groupApi';
import {
  AiWeeklyReportView,
  reportApi,
} from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';
import { lastCompletedWeekStart } from '../../../app/week';
import { AiReportContent } from './AiReportContent';

type Props = {
  groupId: number;
  group?: GroupResponse;
  initialReportId?: number;
  onReportIdChange?: (reportId: number) => void;
};

export function AiWeeklyReportPanel({
  groupId,
  group,
  initialReportId,
  onReportIdChange,
}: Props) {
  const { t } = useLanguage();
  const [weekFrom, setWeekFrom] = useState(lastCompletedWeekStart);
  const [language, setLanguage] = useState<'KO' | 'EN'>('KO');
  const [reportView, setReportView] = useState<AiWeeklyReportView>();
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');

  const canManageAi = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const canViewAi = group?.membershipPlan === 'PAID';

  useEffect(() => {
    if (group?.timezone) {
      setWeekFrom(lastCompletedWeekStart(group.timezone));
    }
  }, [group?.timezone]);

  useEffect(() => {
    if (!canViewAi || !initialReportId || initialReportId < 1) {
      return;
    }
    let active = true;
    setLoading(true);
    setMessage('');
    reportApi.findAiWeeklyById(groupId, initialReportId)
      .then((res) => {
        if (active) {
          setReportView(res);
          setWeekFrom(res.from);
        }
      })
      .catch((err: ApiError) => {
        if (active) {
          if (err.code === 'AI_REPORT_NOT_FOUND') {
            setReportView(undefined);
          } else {
            setMessage(errorMessage(err));
          }
        }
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => { active = false; };
  }, [canViewAi, groupId, initialReportId]);

  function getToExclusive(fromStr: string): string {
    const d = new Date(fromStr);
    d.setDate(d.getDate() + 7);
    return d.toISOString().split('T')[0];
  }

  async function handleGenerate(regenerate: boolean) {
    if (!canManageAi) return;
    const toExclusive = getToExclusive(weekFrom);
    const toExclusiveDate = new Date(`${toExclusive}T00:00:00`);
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (toExclusiveDate.getTime() > today.getTime()) {
      setMessage(t('AI 주간 리포트는 완료된 주간만 생성할 수 있습니다.', 'AI weekly reports can only be generated for completed weeks.'));
      return;
    }
    setSubmitting(true);
    setMessage('');
    try {
      const res = await reportApi.generateAiWeekly(groupId, {
        from: weekFrom,
        toExclusive,
        language,
        regenerate,
      });

      // Load full view
      const fullView = await reportApi.findAiWeeklyById(groupId, res.reportId);
      setReportView(fullView);
      onReportIdChange?.(fullView.reportId);
    } catch (err) {
      setMessage(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDownloadPdf() {
    if (!reportView) return;
    setSubmitting(true);
    try {
      await reportApi.downloadAiWeeklyPdf(
        groupId,
        reportView.reportId,
        reportView.from,
        reportView.revision
      );
    } catch (err) {
      setMessage(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="ai-report-reader">
      {!group || group.membershipPlan === 'FREE' ? (
        <div className="ai-report-lock">
          <strong>🔒 {t('유료 플랜 기능', 'Paid plan feature')}</strong>
          <p>
            {t(
              '유료 팀 플랜에서만 AI 주간 리포트를 사용할 수 있습니다.',
              'AI Weekly Report is only available on paid team plans.'
            )}
          </p>
        </div>
      ) : (
        <>
          <div className="ai-report-reader-toolbar" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', background: '#f8fafc', padding: '12px', borderRadius: '8px' }}>
            <div className="report-controls" style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
              <label style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span>{t('주간 선택 (월요일)', 'Week (Monday)')}</span>
                <input
                  type="date"
                  value={weekFrom}
                  onChange={(e) => setWeekFrom(e.target.value)}
                  style={{ padding: '6px 10px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                />
              </label>

              <label style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span>{t('언어', 'Language')}</span>
                <select
                  value={language}
                  onChange={(e) => setLanguage(e.target.value as 'KO' | 'EN')}
                  style={{ padding: '6px 10px', borderRadius: '4px', border: '1px solid #cbd5e1' }}
                >
                  <option value="KO">한국어 (KO)</option>
                  <option value="EN">English (EN)</option>
                </select>
              </label>

              {canManageAi && (
                <>
                  <button
                    className="primary"
                    type="button"
                    disabled={submitting || loading}
                    onClick={() => void handleGenerate(false)}
                    style={{ background: '#2563eb', color: '#fff', padding: '8px 16px', borderRadius: '6px', border: 'none', cursor: 'pointer', fontWeight: 600 }}
                  >
                    {submitting ? t('생성 중...', 'Generating...') : t('리포트 생성', 'Generate')}
                  </button>

                  {reportView && (
                    <button
                      className="secondary"
                      type="button"
                      disabled={submitting || loading}
                      onClick={() => void handleGenerate(true)}
                      style={{ background: '#475569', color: '#fff', padding: '8px 16px', borderRadius: '6px', border: 'none', cursor: 'pointer', fontWeight: 600 }}
                    >
                      {t('새 리비전 재생성', 'Regenerate')}
                    </button>
                  )}
                </>
              )}
            </div>

            {reportView && (
              <button
                className="secondary"
                type="button"
                disabled={submitting}
                onClick={() => void handleDownloadPdf()}
                style={{ background: '#059669', color: '#fff', padding: '8px 16px', borderRadius: '6px', border: 'none', cursor: 'pointer', fontWeight: 600 }}
              >
                📥 {t('PDF 다운로드', 'Download PDF')}
              </button>
            )}
          </div>

          {loading && <p>{t('리포트를 불러오는 중...', 'Loading report...')}</p>}
          {message && <p className="error" style={{ color: '#dc2626', fontWeight: 600 }}>{message}</p>}

          {reportView && !loading && (
            <AiReportContent report={reportView} />
          )}

          {!reportView && !loading && (
            <div style={{ textAlign: 'center', padding: '40px 20px', background: '#f8fafc', borderRadius: '8px', border: '1px dashed #cbd5e1' }}>
              <p style={{ color: '#64748b' }}>
                {t('선택한 주간의 리포트가 없습니다. 생성 버튼을 눌러 리포트를 생성하세요.', 'No report found for selected week. Click Generate to create one.')}
              </p>
            </div>
          )}
        </>
      )}
    </section>
  );
}
