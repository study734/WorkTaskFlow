import { AiWeeklyReportView } from '../../../api/reportApi';

function formatPeriodDisplay(from: string, toExclusive: string): string {
  if (!toExclusive) return from;
  const d = new Date(`${toExclusive}T00:00:00`);
  if (Number.isNaN(d.getTime())) return `${from} ~ ${toExclusive}`;
  d.setDate(d.getDate() - 1);
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const date = String(d.getDate()).padStart(2, '0');
  return `${from} ~ ${d.getFullYear()}-${month}-${date}`;
}

export function AiReportContent({ report, print = false }: {
  report: AiWeeklyReportView;
  print?: boolean;
}) {
  const isFallback = report.analysisMode === 'SERVER_FALLBACK';

  return (
    <div className={`ai-report-v72-container ${print ? 'print-mode' : ''}`}>
      {isFallback && (
        <div className="fallback-banner" style={{
          background: '#fff3cd', color: '#856404', padding: '12px 16px', borderRadius: '6px',
          marginBottom: '16px', fontWeight: 600, border: '1px solid #ffeeba'
        }}>
          ⚠️ 기본 분석으로 생성됨 · 확정 업무 데이터 기준
        </div>
      )}

      {/* PAGE 1: 확정 업무 현황 */}
      <section className="report-page page-1">
        <div className="page-header">
          <span className="eyebrow">WorkTaskFlow · AI WEEKLY REPORT v7-2 · R{report.revision}</span>
          <h1>1. 확정 업무 현황</h1>
          <p className="meta">
            기간: {formatPeriodDisplay(report.from, report.toExclusive)} · 상태: {report.status} ({report.analysisMode})
          </p>
        </div>

        {report.metrics && (
          <div className="metrics-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(130px, 1fr))', gap: '12px', margin: '16px 0' }}>
            <div className="metric-card" style={{ background: '#f8f9fa', padding: '12px', borderRadius: '8px', border: '1px solid #e9ecef' }}>
              <span className="label" style={{ fontSize: '12px', color: '#6c757d' }}>전체 업무</span>
              <strong className="value" style={{ display: 'block', fontSize: '20px', marginTop: '4px' }}>{report.metrics.periodTaskCount}</strong>
            </div>
            <div className="metric-card" style={{ background: '#f8f9fa', padding: '12px', borderRadius: '8px', border: '1px solid #e9ecef' }}>
              <span className="label" style={{ fontSize: '12px', color: '#6c757d' }}>완료율</span>
              <strong className="value" style={{ display: 'block', fontSize: '20px', marginTop: '4px' }}>{report.metrics.completionRatePercent != null ? `${report.metrics.completionRatePercent}%` : '-'}</strong>
            </div>
            <div className="metric-card" style={{ background: '#f8f9fa', padding: '12px', borderRadius: '8px', border: '1px solid #e9ecef' }}>
              <span className="label" style={{ fontSize: '12px', color: '#6c757d' }}>기한 준수율</span>
              <strong className="value" style={{ display: 'block', fontSize: '20px', marginTop: '4px' }}>{report.metrics.onTimeRatePercent != null ? `${report.metrics.onTimeRatePercent}%` : '-'}</strong>
            </div>
            <div className="metric-card" style={{ background: '#f8f9fa', padding: '12px', borderRadius: '8px', border: '1px solid #e9ecef' }}>
              <span className="label" style={{ fontSize: '12px', color: '#6c757d' }}>지연 업무</span>
              <strong className="value" style={{ display: 'block', fontSize: '20px', marginTop: '4px', color: report.metrics.delayedCount > 0 ? '#dc3545' : 'inherit' }}>{report.metrics.delayedCount}</strong>
            </div>
            <div className="metric-card" style={{ background: '#f8f9fa', padding: '12px', borderRadius: '8px', border: '1px solid #e9ecef' }}>
              <span className="label" style={{ fontSize: '12px', color: '#6c757d' }}>평균 소요</span>
              <strong className="value" style={{ display: 'block', fontSize: '20px', marginTop: '4px' }}>{report.metrics.averageLeadTimeHours != null ? `${report.metrics.averageLeadTimeHours}시간` : '-'}</strong>
            </div>
          </div>
        )}

        {report.workflow && (
          <div className="workflow-section" style={{ margin: '16px 0' }}>
            <h3>워크플로우 현황</h3>
            <div className="workflow-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: '8px', background: '#f1f3f5', padding: '12px', borderRadius: '8px' }}>
              <div><small>요청됨</small><div><strong>{report.workflow.requestedCount}</strong></div></div>
              <div><small>미지정</small><div><strong>{report.workflow.todoUnassignedCount}</strong></div></div>
              <div><small>담당지정</small><div><strong>{report.workflow.todoAssignedCount}</strong></div></div>
              <div><small>진행중</small><div><strong>{report.workflow.inProgressCount}</strong></div></div>
              <div><small>보류중</small><div><strong>{report.workflow.onHoldCount}</strong></div></div>
              <div><small>완료됨</small><div><strong>{report.workflow.completedCount}</strong></div></div>
            </div>
          </div>
        )}

        <div className="tasks-section" style={{ margin: '20px 0' }}>
          <h3>업무 상세 목록</h3>
          {!report.tasks || report.tasks.length === 0 ? (
            <p className="empty-text">해당 기간의 업무가 없습니다.</p>
          ) : (
            <table className="report-table" style={{ width: '100%', borderCollapse: 'collapse', marginTop: '8px' }}>
              <thead>
                <tr style={{ background: '#f8f9fa', borderBottom: '2px solid #dee2e6', textAlign: 'left' }}>
                  <th style={{ padding: '8px' }}>업무명</th>
                  <th style={{ padding: '8px' }}>상태</th>
                  <th style={{ padding: '8px' }}>우선순위</th>
                  <th style={{ padding: '8px' }}>담당자</th>
                  <th style={{ padding: '8px' }}>마감일</th>
                </tr>
              </thead>
              <tbody>
                {report.tasks.map((task) => (
                  <tr key={task.taskRef} style={{ borderBottom: '1px solid #e9ecef' }}>
                    <td style={{ padding: '8px', fontWeight: 600 }}>{task.realTitle}</td>
                    <td style={{ padding: '8px' }}>{task.status}</td>
                    <td style={{ padding: '8px' }}>{task.priority || '-'}</td>
                    <td style={{ padding: '8px' }}>{task.assigneeName || '-'}</td>
                    <td style={{ padding: '8px' }}>{task.dueAt || '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      </section>

      {/* PAGE 2: 이번 주 핵심 */}
      <section className="report-page page-2" style={{ marginTop: '32px', borderTop: '2px dashed #ccc', paddingTop: '24px' }}>
        <div className="page-header">
          <span className="eyebrow">WorkTaskFlow · AI WEEKLY REPORT v7-2 · R{report.revision}</span>
          <h1>2. 이번 주 핵심</h1>
        </div>

        {report.executiveJudgment && (
          <div className="executive-card" style={{ background: '#eef2ff', padding: '16px', borderRadius: '8px', borderLeft: '4px solid #4f46e5', margin: '16px 0' }}>
            <h2 style={{ margin: '0 0 8px 0', fontSize: '18px', color: '#1e1b4b' }}>{report.executiveJudgment.headline}</h2>
            <p style={{ margin: 0, color: '#312e81', lineHeight: 1.6 }}>{report.executiveJudgment.interpretation}</p>
            <div style={{ marginTop: '8px', fontSize: '12px', color: '#4338ca' }}>
              신뢰도: <strong>{report.executiveJudgment.confidence}</strong>
              {report.executiveJudgment.evidenceTaskTitles && report.executiveJudgment.evidenceTaskTitles.length > 0 && (
                <span> · 근거 업무: {report.executiveJudgment.evidenceTaskTitles.join(', ')}</span>
              )}
            </div>
          </div>
        )}

        <div className="comparison-section" style={{ margin: '16px 0' }}>
          <h3>주간 비교</h3>
          {report.comparison?.status === 'NO_BASELINE' ? (
            <div className="baseline-banner" style={{ background: '#f3f4f6', padding: '12px', borderRadius: '6px', border: '1px solid #e5e7eb', color: '#4b5563' }}>
              <b>BASELINE</b> 첫 리포트라 지난주 비교 기준이 아직 없습니다.
            </div>
          ) : (
            <div className="comparison-grid" style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '12px' }}>
              <div style={{ background: '#f9fafb', padding: '12px', borderRadius: '6px' }}>
                <small>업무 수 변화</small>
                <div><strong>{formatDelta(report.comparison?.taskCountDiff)}</strong></div>
              </div>
              <div style={{ background: '#f9fafb', padding: '12px', borderRadius: '6px' }}>
                <small>완료율 변화</small>
                <div><strong>{formatDeltaPercent(report.comparison?.completionRateDiffPercent)}</strong></div>
              </div>
              <div style={{ background: '#f9fafb', padding: '12px', borderRadius: '6px' }}>
                <small>기한준수율 변화</small>
                <div><strong>{formatDeltaPercent(report.comparison?.onTimeRateDiffPercent)}</strong></div>
              </div>
              <div style={{ background: '#f9fafb', padding: '12px', borderRadius: '6px' }}>
                <small>지연 수 변화</small>
                <div><strong>{formatDelta(report.comparison?.delayedCountDiff)}</strong></div>
              </div>
            </div>
          )}
        </div>

        {report.achievement && (
          <div className="achievement-section" style={{ margin: '16px 0' }}>
            <h3>주요 성과</h3>
            <div style={{ background: '#ecfdf5', padding: '14px', borderRadius: '8px', borderLeft: '4px solid #10b981' }}>
              <h4 style={{ margin: '0 0 6px 0', color: '#065f46' }}>{report.achievement.headline}</h4>
              <p style={{ margin: 0, color: '#047857' }}>{report.achievement.summary}</p>
            </div>
          </div>
        )}

        {report.issues && report.issues.length > 0 && (
          <div className="issues-summary-section" style={{ margin: '16px 0' }}>
            <h3>주요 위험 요약</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {report.issues.slice(0, 3).map((issue) => (
                <div key={issue.candidateRef} style={{ background: '#fff1f2', padding: '10px 14px', borderRadius: '6px', borderLeft: '4px solid #f43f5e' }}>
                  <span style={{ fontWeight: 700, color: '#be123c', marginRight: '8px' }}>[{issue.priority} / {issue.severity}]</span>
                  <span style={{ fontWeight: 600, color: '#881337' }}>{issue.title}</span>
                  <p style={{ margin: '4px 0 0 0', fontSize: '13px', color: '#9f1239' }}>{issue.impact}</p>
                </div>
              ))}
            </div>
          </div>
        )}

        {report.calendarConstraints && report.calendarConstraints.length > 0 && (
          <div className="calendar-section" style={{ margin: '16px 0' }}>
            <h3>다음 주 주요 일정</h3>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {report.calendarConstraints.slice(0, 3).map((cal) => (
                <div key={cal.eventRef} style={{ background: '#f8fafc', padding: '10px', borderRadius: '6px', border: '1px solid #e2e8f0' }}>
                  <strong>{cal.realTitle}</strong> ({cal.eventType})
                  <div style={{ fontSize: '12px', color: '#64748b', marginTop: '2px' }}>일시: {cal.startAt} ~ {cal.endAt}</div>
                </div>
              ))}
            </div>
          </div>
        )}
      </section>

      {/* PAGE 3: 조치가 필요한 업무 */}
      <section className="report-page page-3" style={{ marginTop: '32px', borderTop: '2px dashed #ccc', paddingTop: '24px' }}>
        <div className="page-header">
          <span className="eyebrow">WorkTaskFlow · AI WEEKLY REPORT v7-2 · R{report.revision}</span>
          <h1>3. 조치가 필요한 업무</h1>
        </div>

        {!report.issues || report.issues.length === 0 ? (
          <p className="empty-text">조치가 필요한 위험 업무가 없습니다.</p>
        ) : (
          <div className="risk-cards-list" style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '16px' }}>
            {report.issues.map((issue) => (
              <div key={issue.candidateRef} style={{ background: '#fff1f2', padding: '16px', borderRadius: '8px', borderLeft: '4px solid #e11d48' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <h3 style={{ margin: 0, color: '#9f1239', fontSize: '16px' }}>
                    [{issue.priority}] {issue.realTaskTitle}
                  </h3>
                  <span style={{ fontSize: '12px', background: '#ffe4e6', color: '#be123c', padding: '2px 8px', borderRadius: '4px', fontWeight: 600 }}>
                    {issue.severity} RISK
                  </span>
                </div>
                <p style={{ margin: '8px 0 4px 0', fontWeight: 600, color: '#881337' }}>원인/현상: {issue.title}</p>
                <p style={{ margin: '4px 0', color: '#4c0519' }}><b>영향:</b> {issue.impact}</p>
                {issue.integratedJudgment && (
                  <p style={{ margin: '4px 0', color: '#4c0519' }}><b>통합 판단:</b> {issue.integratedJudgment}</p>
                )}
                {issue.missingEvidence && issue.missingEvidence.length > 0 && (
                  <p style={{ margin: '8px 0 0 0', fontSize: '12px', color: '#be123c' }}>
                    <b>부족한 근거:</b> {issue.missingEvidence.join(', ')}
                  </p>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {/* PAGE 4: 결정과 실행 */}
      <section className="report-page page-4" style={{ marginTop: '32px', borderTop: '2px dashed #ccc', paddingTop: '24px' }}>
        <div className="page-header">
          <span className="eyebrow">WorkTaskFlow · AI WEEKLY REPORT v7-2 · R{report.revision}</span>
          <h1>4. 결정과 실행</h1>
        </div>

        {!report.issues || report.issues.length === 0 ? (
          <p className="empty-text">리더 결정 사항이 없습니다.</p>
        ) : (
          <div className="decisions-list" style={{ display: 'flex', flexDirection: 'column', gap: '16px', marginTop: '16px' }}>
            {report.issues.filter(i => i.decision != null).slice(0, 3).map((issue) => {
              const d = issue.decision!;
              return (
                <div key={issue.candidateRef} style={{ background: '#faf5ff', padding: '16px', borderRadius: '8px', borderLeft: '4px solid #9333ea' }}>
                  <h3 style={{ margin: '0 0 8px 0', color: '#581c87', fontSize: '16px' }}>
                    [{issue.priority}] {d.title}
                  </h3>
                  <p style={{ margin: '4px 0', color: '#3b0764' }}><b>결정 질문:</b> {d.question}</p>
                  <p style={{ margin: '4px 0', color: '#3b0764' }}><b>권고안:</b> {d.recommendation}</p>
                  <div style={{ display: 'flex', gap: '16px', margin: '8px 0', fontSize: '13px', color: '#6b21a8' }}>
                    <span>결정 주체: <strong>{d.decisionMakerRole || '-'}</strong></span>
                    <span>실행 담당: <strong>{d.actionOwnerRole || '-'}</strong></span>
                  </div>
                  {d.executionStepCodes && d.executionStepCodes.length > 0 && (
                    <p style={{ margin: '4px 0', fontSize: '13px', color: '#581c87' }}>
                      <b>실행 단계:</b> {d.executionStepCodes.join(' → ')}
                    </p>
                  )}
                  {d.completionSignalCodes && d.completionSignalCodes.length > 0 && (
                    <p style={{ margin: '4px 0', fontSize: '13px', color: '#581c87' }}>
                      <b>완료 신호:</b> {d.completionSignalCodes.join(', ')}
                    </p>
                  )}
                </div>
              );
            })}
          </div>
        )}

        <p className="footer-notice" style={{ marginTop: '32px', fontSize: '12px', color: '#9ca3af', textAlign: 'center' }}>
          이 문서는 WorkTaskFlow v7-2 엔진으로 생성된 4페이지 AI 주간 리포트입니다.
        </p>
      </section>
    </div>
  );
}

function formatDelta(val?: number): string {
  if (val == null) return '-';
  return val >= 0 ? `+${val}` : `${val}`;
}

function formatDeltaPercent(val?: number): string {
  if (val == null) return '-';
  return val >= 0 ? `+${val}%p` : `${val}%p`;
}
