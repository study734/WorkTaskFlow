import {
  ActionNarrativeItemView,
  CompletedWeeklyAiReport,
  DecisionNarrativeItemView,
  EvidenceValue,
  LocalReference,
  MemberWorkView,
  NarrativeItemView,
  RiskNarrativeItemView,
  TaskWorkView,
} from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';
import type { ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import {
  normalizeReportProjectionState,
  projectReportRisks,
  readReportProjectionState,
  type ProjectedAiRisk,
  type ProjectedMemberException,
  type ProjectedMemberRiskWork,
  type ProjectedServerRiskTask,
} from '../reportProjection';

export type ReportDensity = 'SUMMARY' | 'STANDARD' | 'DETAILED';

type TeamKpi =
  'TOTAL_TASKS'
  | 'COMPLETION_RATE'
  | 'ON_TIME_RATE'
  | 'DELAYED'
  | 'AVERAGE_COMPLETION_HOURS'
  | 'ON_HOLD'
  | 'CHECKLIST';

const SUMMARY_TEAM_KPIS: readonly TeamKpi[] = [
  'TOTAL_TASKS',
  'COMPLETION_RATE',
  'ON_TIME_RATE',
  'DELAYED',
];
const STANDARD_TEAM_KPIS: readonly TeamKpi[] = [
  ...SUMMARY_TEAM_KPIS,
  'AVERAGE_COMPLETION_HOURS',
  'ON_HOLD',
  'CHECKLIST',
];
const FINALIZED_PDF_TEAM_KPIS: readonly TeamKpi[] = [
  'TOTAL_TASKS',
  'COMPLETION_RATE',
  'ON_TIME_RATE',
  'AVERAGE_COMPLETION_HOURS',
  'DELAYED',
  'ON_HOLD',
  'CHECKLIST',
];
const AI_RISK_SEVERITY_RANK: Record<RiskNarrativeItemView['severity'], number> = {
  HIGH: 0,
  MEDIUM: 1,
  LOW: 2,
};
// 결정 코어가 상단 4개를 이미 보여주므로 상세 지표 행은 나머지만 이어서 표시한다.
const EXTRA_TEAM_KPIS: readonly TeamKpi[] = STANDARD_TEAM_KPIS
  .filter((key) => !SUMMARY_TEAM_KPIS.includes(key));

export function AiReportContent({ report, print = false, density = 'DETAILED' }: {
  report: CompletedWeeklyAiReport;
  print?: boolean;
  density?: ReportDensity;
}) {
  const { t } = useLanguage();
  const location = useLocation();
  const summary = density === 'SUMMARY';
  const detailed = density === 'DETAILED';
  const projectionState = normalizeReportProjectionState(
    readReportProjectionState(new URLSearchParams(location.search)),
    report.operations.members,
  );
  const groupScope = projectionState.scope === 'GROUP';
  const comparisonScope = projectionState.scope === 'MEMBER_COMPARISON';
  const individualScope = projectionState.scope === 'INDIVIDUAL_MEMBER';
  const showMemberExceptions = !print
    && summary
    && projectionState.scope !== 'INDIVIDUAL_MEMBER';
  // print는 확정 PDF 계약대로 7개를 한 행에 유지하고, 웹은 결정 코어 4개 + 상세 3개로 나눈다.
  const coreKpiOrder = print ? [] : SUMMARY_TEAM_KPIS;
  const teamKpiOrder = print
    ? FINALIZED_PDF_TEAM_KPIS
    : summary ? [] : EXTRA_TEAM_KPIS;
  const teamKpis: Record<TeamKpi, ReactNode> = {
    TOTAL_TASKS: <Metric key="TOTAL_TASKS"
      label={t('전체 업무', 'Tasks')} value={String(report.metrics.totalTasks)} />,
    COMPLETION_RATE: <Metric key="COMPLETION_RATE" label={t('완료율', 'Completion')}
      value={percent(report.metrics.completionRatePercent)} />,
    ON_TIME_RATE: <Metric key="ON_TIME_RATE" label={t('기한 준수율', 'On-time rate')}
      value={percent(report.metrics.onTimeRatePercent)} />,
    DELAYED: <Metric key="DELAYED" label={t('지연', 'Overdue')}
      value={String(report.metrics.statuses.delayed)} />,
    AVERAGE_COMPLETION_HOURS: <Metric key="AVERAGE_COMPLETION_HOURS"
      label={t('등록 후 완료', 'Created-to-completed')}
      value={hours(report.metrics.averageCompletionHours, t)} />,
    ON_HOLD: <Metric key="ON_HOLD" label={t('보류', 'On hold')}
      value={String(report.metrics.statuses.onHold)} />,
    CHECKLIST: <Metric key="CHECKLIST" label={t('체크리스트', 'Checklist')}
      value={`${report.metrics.checklist.completed}/${report.metrics.checklist.total}`} />,
  };
  const riskProjection = projectReportRisks(report);
  const selectedMember = individualScope
    ? riskProjection.memberRiskWork.find(({ member }) =>
      member.member.ref === projectionState.memberRef)
    : undefined;
  const visibleServerRisks = print
    ? report.metrics.riskSignals.map((signal, frozenIndex) => ({
      signal,
      frozenIndex,
      known: false,
      tasks: [] as readonly TaskWorkView[],
    }))
    : riskProjection.serverRisks;
  const visibleAiRisks = projectAiRisks(
    print
      ? report.analysis.risks.map((item, frozenIndex) => ({
        item,
        frozenIndex,
        tasks: [] as readonly TaskWorkView[],
        taskRefMismatch: false,
      }))
      : riskProjection.aiRisks,
    density,
  );
  const visibleActions = summary
    ? report.analysis.topActions.slice(0, 3)
    : report.analysis.topActions;
  // 결정 코어의 대표 항목. 위험 대표는 스펙의 severity → frozen index 규칙을 그대로 재사용한다.
  const topRisk = projectAiRisks(riskProjection.aiRisks, 'SUMMARY')[0];
  const topServerRisk = riskProjection.serverRisks[0];
  const topAction = report.analysis.topActions.find((item) => item.priority === 1)
    ?? report.analysis.topActions[0];
  const topDecision = report.analysis.leaderDecisions[0];
  const visibleChanges = summary
    ? report.analysis.changes.slice(0, 2)
    : report.analysis.changes;
  const operationalSignal = report.comparison.available
    ? t(
      `완료율 변화 ${delta(report.comparison.completionRateDeltaPercent, '%p')} · 지연 업무 변화 ${delta(report.comparison.delayedTasksDelta)}건`,
      `Completion change ${delta(report.comparison.completionRateDeltaPercent, 'pp')} · overdue change ${delta(report.comparison.delayedTasksDelta)}`,
    )
    : t(
      `완료율 ${percent(report.metrics.completionRatePercent)} · 지연 ${report.metrics.statuses.delayed}건 · 보류 ${report.metrics.statuses.onHold}건`,
      `Completion ${percent(report.metrics.completionRatePercent)} · overdue ${report.metrics.statuses.delayed} · on hold ${report.metrics.statuses.onHold}`,
    );
  return <article className={`ai-report-document ${density.toLowerCase()}${print ? ' print' : ''}`}>
    <header className="ai-report-document-header">
      <div>
        <span className="page-eyebrow">
          {report.operations.groupName} · AI WEEKLY DECISION BRIEF · R{report.revision}
        </span>
        <h1>{report.analysis.headline}</h1>
        <p>{report.periodStart} — {report.periodEnd}
          {' · '}{confidenceLabel(report.operations.confidenceLevel, t)}
          {' '}{t('확신', 'confidence')}</p>
      </div>
      <span className={`publication-badge ${report.publicationStatus.toLowerCase()}`}>
        {publicationLabel(report.publicationStatus, t)}
      </span>
    </header>

    <section className="ai-report-basis">
      <div>
        <span>{t('종합 상태', 'Health')}</span>
        <b className={`report-health ${report.operations.healthStatus.toLowerCase()}`}>
          {healthLabel(report.operations.healthStatus, t)}
        </b>
      </div>
      <div><span>{t('생성 시각', 'Generated')}</span>
        <b>{report.generatedAt ? new Date(report.generatedAt).toLocaleString() : '-'}</b></div>
      <div><span>{t('분석 확신', 'Confidence')}</span>
        <b>{confidenceLabel(report.operations.confidenceLevel, t)}</b></div>
      <div><span>{t('데이터 상태', 'Data status')}</span>
        <b>{report.metrics.historyCoverage.status === 'COMPLETE'
          ? t('정상', 'Complete') : t('일부 누락', 'Partial')}</b></div>
    </section>

    {report.metrics.historyCoverage.status === 'PARTIAL' &&
      <p className="ai-report-coverage-warning">
        {t(
          '활동 이력 수집 전 데이터가 포함되어 일부 판단에는 제한이 있습니다.',
          'Some conclusions are limited because the report includes data from before activity tracking.',
        )}
      </p>}

    <section className="ai-report-decision-core">
      <div className="ai-report-executive-brief">
        <span>{t('30초 리더 브리프', '30-second leadership brief')}</span>
        <strong>{operationalSignal}</strong>
        <p>{report.analysis.summary.text}</p>
        <EvidenceDetails item={report.analysis.summary} evidence={report.evidence} />
      </div>

      {coreKpiOrder.length > 0 && <div className="ai-report-core-kpis"
        aria-label={t('핵심 지표', 'Key metrics')}>
        {coreKpiOrder.map((key) => teamKpis[key])}
      </div>}

      <div className="ai-report-core-calls">
        <CoreCall kind="risk" label={t('가장 큰 위험', 'Top risk')}
          text={topRisk?.item.text
            ?? (topServerRisk ? riskSignalLabel(topServerRisk.signal.code, t) : undefined)}
          tag={topRisk?.item.severity ?? topServerRisk?.signal.severity}
          empty={t('확인된 위험이 없습니다.', 'No risk identified.')} />
        <CoreCall kind="action" label={t('가장 먼저 할 일', 'First action')}
          text={topAction?.action} tag={topAction ? `P${topAction.priority}` : undefined}
          owner={topAction?.owner?.label}
          empty={t('제안된 행동이 없습니다.', 'No action suggested.')} />
        <CoreCall kind="decision" label={t('결정할 사항', 'Decision needed')}
          text={topDecision?.question}
          empty={t('결정할 안건이 없습니다.', 'No decision required.')} />
      </div>
    </section>

    {report.comparison.available ? <section className="ai-report-comparison">
      <strong>{t('지난주 대비', 'Compared with last week')}</strong>
      <div className="ai-report-delta-row">
        <Delta label={t('완료 업무', 'Completed')}
          value={report.comparison.completedTasksDelta} />
        <Delta label={t('지연 업무', 'Overdue')}
          value={report.comparison.delayedTasksDelta} invert />
        <Delta label={t('완료율', 'Completion')}
          value={report.comparison.completionRateDeltaPercent} suffix="%p" />
      </div>
    </section> : <section className="ai-report-baseline">
      <strong>BASELINE</strong>
      <span>{t(
        '첫 리포트라 지난주 비교 기준이 아직 없습니다.',
        'This is the first report, so no previous-week baseline is available yet.',
      )}</span>
    </section>}

    <ReportSection title={t('이번 주 핵심 변화', 'Material changes this week')}
      empty={t('의사결정에 영향을 주는 변화가 확인되지 않았습니다.', 'No decision-relevant change was identified.')}>
      {visibleChanges.length > 0 && <div className="ai-report-change-grid">
        {visibleChanges.map((item, index) =>
          <NarrativeCard key={index} item={item} evidence={report.evidence} compact />)}
      </div>}
    </ReportSection>

    <ReportSection title={t('서버 확인 위험 신호', 'Server-confirmed risk signals')}
      note={t(
        '저장된 업무 수치와 상태 규칙으로 확인한 사실입니다.',
        'These are confirmed from stored task metrics and server rules.',
      )}
      empty={t('서버 규칙으로 확인된 위험 신호가 없습니다.', 'No risk signal was confirmed by server rules.')}>
      {visibleServerRisks.map((risk) =>
        <div className="ai-report-item server-risk"
          data-risk-code={risk.signal.code}
          key={`${risk.signal.code}-${risk.frozenIndex}`}>
          <span className={`risk-level ${risk.signal.severity.toLowerCase()}`}>
            {risk.signal.severity}
          </span>
          <div>
            <strong>{riskSignalLabel(risk.signal.code, t)}</strong>
          </div>
          {!print && <LinkedTaskList tasks={risk.tasks} />}
          <EvidenceDetails item={{
            evidenceKeys: risk.signal.evidenceKeys,
            taskRefs: print ? [] : risk.tasks.map(({ task }) => task),
            objectiveRefs: [],
          }} evidence={report.evidence} />
        </div>)}
    </ReportSection>

    {showMemberExceptions && <MemberExceptions items={riskProjection.memberExceptions} />}

    <ReportSection title={t('AI 위험 후보', 'AI risk candidates')}
      note={t(
        '아래 내용은 서버 근거를 바탕으로 AI가 작성한 해석이며, 원인을 단정하지 않습니다.',
        'The items below are AI-written interpretations of server evidence and do not assert causes.',
      )}
      empty={t('AI가 제안한 위험 후보가 없습니다.', 'AI suggested no risk candidate.')}>
      {visibleAiRisks.map((risk) =>
        <RiskCard key={risk.frozenIndex} projection={risk}
          evidence={report.evidence} preservePrintRiskMarkup={print} />)}
    </ReportSection>

    {!summary && (print || groupScope) &&
      <MemberPerformance members={report.operations.members} gradeRule={report.gradeRule}
        memberCount={report.operations.memberCount}
        activeMemberCount={report.operations.activeMemberCount} />}

    <ReportSection title={summary
      ? t('회의 후 실행 항목', 'Post-meeting actions')
      : t('다음 주 우선 행동·목표', 'Next-week actions and goals')}
      empty={t('제안된 행동이 없습니다.', 'No action was suggested.')}>
      {visibleActions.length > 0 && <div className="ai-report-priority-grid">
        {visibleActions.map((item) =>
          <ActionCard key={item.priority} item={item} evidence={report.evidence} />)}
      </div>}
    </ReportSection>

    <ReportSection title={summary
      ? t('회의 결정 안건', 'Meeting decisions')
      : t('팀장 결정 사항', 'Leader decisions')}
      empty={t('현재 결정할 안건이 없습니다.', 'No decision is currently required.')}>
      {report.analysis.leaderDecisions.length > 0 && <div className="ai-report-decision-grid">
        {report.analysis.leaderDecisions.slice(0, 3).map((item, index) =>
          <DecisionCard key={index} item={item} evidence={report.evidence} />)}
      </div>}
    </ReportSection>

    {teamKpiOrder.length > 0 && <section className="ai-report-metrics"
      aria-label={t('상세 운영 지표', 'Detailed operating metrics')}>
      {teamKpiOrder.map((key) => teamKpis[key])}
    </section>}

    {detailed && <ReportSection title={t('일별 업무 흐름', 'Daily work flow')}
      empty={t('표시할 일별 활동이 없습니다.', 'No daily activity is available.')}>
      {report.metrics.daily.length > 0 && <div className="ai-report-daily-flow">
        {report.metrics.daily.map((item) => <div key={item.date}>
          <span>{item.date.slice(5)}</span>
          <b>{t('생성', 'Created')} {item.created}</b>
          <b>{t('완료', 'Done')} {item.completed}</b>
        </div>)}
      </div>}
    </ReportSection>}

    {!print && comparisonScope && !summary &&
      <MemberComparison items={riskProjection.memberRiskWork} detailed={detailed} />}

    {!print && individualScope && selectedMember &&
      <IndividualMemberDetail item={selectedMember} density={density} />}

    {detailed && (print || groupScope) &&
      <ReportSection title={t('이번 주 핵심 업무', 'Key work this week')}
      empty={t('이번 주 활동 업무가 없습니다.', 'No work activity was recorded this week.')}>
      {report.operations.tasks.map((item) =>
        <FrozenTaskCard item={item} key={item.task.ref} />)}
    </ReportSection>}

    {!summary && <ReportSection title={t('이번 주 성과', 'Achievements')}
      empty={t('확인된 성과 항목이 없습니다.', 'No achievement was identified.')}>
      {report.analysis.achievements.map((item, index) =>
        <NarrativeCard key={index} item={item} evidence={report.evidence} />)}
    </ReportSection>}

    {detailed && <ReportSection title={t('AI 분석 원문', 'Full AI interpretation')}>
      <div className="ai-report-analysis-grid">
        <div>
          <strong>{t('가능한 해석', 'Possible interpretation')}</strong>
          <NarrativeCard item={report.analysis.summary} evidence={report.evidence} compact />
        </div>
      </div>
    </ReportSection>}

    {report.analysis.limitations.length > 0 && <ReportSection
      title={t('데이터 제한', 'Data limitations')}>
      {report.analysis.limitations.map((item, index) =>
        <NarrativeCard key={index} item={item} evidence={report.evidence} compact />)}
    </ReportSection>}
  </article>;
}

function projectAiRisks(
  risks: readonly ProjectedAiRisk[],
  density: ReportDensity,
): readonly ProjectedAiRisk[] {
  if (density !== 'SUMMARY') return risks;
  let representative: ProjectedAiRisk | undefined;
  for (const candidate of risks) {
    if (!representative
      || AI_RISK_SEVERITY_RANK[candidate.item.severity]
        < AI_RISK_SEVERITY_RANK[representative.item.severity]) {
      representative = candidate;
    }
  }
  return representative ? [representative] : [];
}

function healthLabel(
  value: CompletedWeeklyAiReport['operations']['healthStatus'],
  t: (ko: string, en: string) => string,
) {
  return {
    ON_TRACK: t('정상', 'On track'),
    NEEDS_ATTENTION: t('주의 필요', 'Needs attention'),
    AT_RISK: t('위험', 'At risk'),
  }[value];
}

function confidenceLabel(
  value: CompletedWeeklyAiReport['operations']['confidenceLevel'],
  t: (ko: string, en: string) => string,
) {
  return { LOW: t('낮음', 'Low'), MEDIUM: t('중간', 'Medium'), HIGH: t('높음', 'High') }[value];
}

function taskStatusLabel(value: string, t: (ko: string, en: string) => string) {
  return ({
    REQUESTED: t('요청', 'Requested'), TODO: t('할 일', 'Todo'),
    IN_PROGRESS: t('진행 중', 'In progress'), ON_HOLD: t('보류', 'On hold'),
    COMPLETED: t('완료', 'Completed'), REJECTED: t('반려', 'Rejected'),
    CANCELLED: t('취소', 'Cancelled'),
  } as Record<string, string>)[value] ?? value;
}

function priorityLabel(value: string, t: (ko: string, en: string) => string) {
  return ({
    LOW: t('낮음', 'Low'), NORMAL: t('보통', 'Normal'), HIGH: t('높음', 'High'),
    URGENT: t('긴급', 'Urgent'),
  } as Record<string, string>)[value] ?? value;
}

function dueLabel(value: string, t: (ko: string, en: string) => string) {
  return ({
    NONE: t('없음', 'None'), COMPLETED_LATE: t('기한 후 완료', 'Completed late'),
    COMPLETED_ON_TIME: t('기한 내 완료', 'Completed on time'),
    OVERDUE: t('지연', 'Overdue'), DUE_WITHIN_WEEK: t('주간 내 마감', 'Due this week'),
    LATER: t('이후', 'Later'),
  } as Record<string, string>)[value] ?? value;
}

function changeLabel(value: string, t: (ko: string, en: string) => string) {
  return ({
    CREATED: t('신규', 'Created'), DETAILS_CHANGED: t('내용 변경', 'Details changed'),
    ASSIGNEE_CHANGED: t('담당 변경', 'Owner changed'),
    CHECKLIST_PROGRESS: t('체크리스트 변경', 'Checklist changed'),
    BLOCKER_CHANGED: t('차단 변경', 'Blocker changed'),
    OBJECTIVE_CHANGED: t('목표 변경', 'Goal changed'), BLOCKED: t('보류 전환', 'Blocked'),
    COMPLETED: t('완료 전환', 'Completed'), RESUMED: t('재개', 'Resumed'),
    REOPENED: t('다시 열림', 'Reopened'),
  } as Record<string, string>)[value] ?? value;
}

function CoreCall({ kind, label, text, tag, owner, empty }: {
  kind: 'risk' | 'action' | 'decision';
  label: string;
  text?: string;
  tag?: string;
  owner?: string;
  empty: string;
}) {
  return <div className={`ai-report-core-call ${kind}`}>
    <span className="ai-report-core-call-label">{label}</span>
    {text
      ? <>
        {tag && <b className={`ai-report-core-call-tag ${kind}`}>{tag}</b>}
        <p>{text}</p>
        {owner && <small>{owner}</small>}
      </>
      : <p className="ai-report-core-call-empty">{empty}</p>}
  </div>;
}

// 등급·점수·순위는 서버가 동결 지표에서 계산해 내려준 값만 그대로 표시한다.
// 근거가 없어 grade가 비어 있으면 낮은 등급이 아니라 평가 대상 아님이다.
function MemberPerformance({ members, gradeRule, memberCount, activeMemberCount }: {
  members: readonly MemberWorkView[];
  gradeRule?: string;
  memberCount: number;
  activeMemberCount: number;
}) {
  const { t } = useLanguage();
  return <ReportSection title={t('팀원 성과와 업무 부담', 'Member performance and workload')}
    note={t(
      '등급·점수·순위는 서버가 동결된 지표로 계산합니다. AI는 등급을 계산하거나 바꾸지 않습니다.',
      'Grade, score, and rank are computed by the server from frozen metrics. AI neither computes nor changes them.',
    )}
    empty={t('활성 팀원이 없습니다.', 'No active members are available.')}>
    {members.length > 0 && <div className="ai-report-member-table performance">
      <div className="report-member-row performance report-member-head">
        <span>{t('팀원', 'Member')}</span><span>{t('등급', 'Grade')}</span>
        <span>{t('점수', 'Score')}</span><span>{t('순위', 'Rank')}</span>
        <span>{t('담당', 'Assigned')}</span><span>{t('진행', 'Active')}</span>
        <span>{t('완료', 'Done')}</span><span>{t('지연', 'Overdue')}</span>
      </div>
      {members.map((item) => {
        const rated = Boolean(item.grade);
        return <div className="report-member-row performance" key={item.member.ref}
          data-member-ref={item.member.ref}>
          <strong>{item.member.label}</strong>
          {rated
            ? <span className={`member-grade ${item.grade?.toLowerCase()}`}>{item.grade}</span>
            : <span className="member-grade not-rated">{t('평가 대상 아님', 'Not rated')}</span>}
          <span>{rated && item.score !== undefined ? item.score : '-'}</span>
          <span>{rated && item.rank ? item.rank : '-'}</span>
          <span>{item.assigned}</span><span>{item.active}</span><span>{item.completed}</span>
          <span className={item.delayed > 0 ? 'negative' : ''}>{item.delayed}</span>
        </div>;
      })}
      <p className="ai-report-table-note">
        {t(
          `활성 팀원 ${memberCount}명 중 이번 주 업무 활동 ${activeMemberCount}명`,
          `${activeMemberCount} of ${memberCount} active members had assigned work`,
        )}
      </p>
      {gradeRule && <p className="ai-report-table-note">{gradeRule}</p>}
    </div>}
  </ReportSection>;
}

function ReportSection({ title, children, empty, note }: {
  title: string;
  children: ReactNode;
  empty?: string;
  /** 항목마다 같은 문장을 반복하지 않도록 섹션에 한 번만 붙이는 안내다. */
  note?: string;
}) {
  const hasChildren = Array.isArray(children) ? children.length > 0 : Boolean(children);
  return <section className="ai-report-section">
    <h4>{title}</h4>
    {note && <p className="ai-report-section-note">{note}</p>}
    {hasChildren ? <div className="ai-report-section-list">{children}</div>
      : empty && <p className="ai-report-empty">{empty}</p>}
  </section>;
}

function MemberComparison({ items, detailed }: {
  items: readonly ProjectedMemberRiskWork[];
  detailed: boolean;
}) {
  const { t } = useLanguage();
  return <ReportSection title={t('팀원 비교', 'Member comparison')}
    empty={t('비교할 frozen 팀원이 없습니다.', 'No frozen member is available to compare.')}>
    {items.length > 0 && <div className="ai-report-member-comparison">
      <div className="report-member-row comparison report-member-head">
        <span>{t('팀원', 'Member')}</span><span>{t('담당', 'Assigned')}</span>
        <span>{t('진행', 'Active')}</span><span>{t('완료', 'Done')}</span>
        <span>{t('지연', 'Overdue')}</span><span>{t('기한 준수율', 'On-time rate')}</span>
      </div>
      {items.map((item) => <div className="ai-report-member-comparison-entry"
        data-member-ref={item.member.member.ref} key={item.member.member.ref}>
        <div className="report-member-row comparison">
          <strong>{item.member.member.label}</strong><span>{item.member.assigned}</span>
          <span>{item.member.active}</span><span>{item.member.completed}</span>
          <span className={item.member.delayed > 0 ? 'negative' : ''}>
            {item.member.delayed}
          </span>
          <span>{percent(item.member.onTimeRatePercent)}</span>
        </div>
        {detailed && <MatchedServerRiskTasks items={item.matchedRiskTasks} />}
      </div>)}
    </div>}
  </ReportSection>;
}

function IndividualMemberDetail({ item, density }: {
  item: ProjectedMemberRiskWork;
  density: ReportDensity;
}) {
  const { t } = useLanguage();
  const summary = density === 'SUMMARY';
  const detailed = density === 'DETAILED';
  return <ReportSection title={t('선택 팀원 운영 상세', 'Selected member operations')}>
    <div className="ai-report-individual-member" data-member-ref={item.member.member.ref}>
      <strong className="ai-report-individual-member-name">{item.member.member.label}</strong>
      <div className="ai-report-individual-kpis">
        <Metric label={t('담당', 'Assigned')} value={String(item.member.assigned)} />
        <Metric label={t('진행', 'Active')} value={String(item.member.active)} />
        <Metric label={t('완료', 'Done')} value={String(item.member.completed)} />
        <Metric label={t('지연', 'Overdue')} value={String(item.member.delayed)} />
        <Metric label={t('기한 준수율', 'On-time rate')}
          value={percent(item.member.onTimeRatePercent)} />
      </div>
      {summary && item.representativeRisk &&
        <div className="ai-report-individual-risk"
          data-risk-code={item.representativeRisk.signal.code}>
          <span className={`risk-level ${item.representativeRisk.signal.severity.toLowerCase()}`}>
            {item.representativeRisk.signal.severity}
          </span>
          <strong>{riskSignalLabel(item.representativeRisk.signal.code, t)}</strong>
        </div>}
      {item.assignedTasks.length === 0
        ? <p className="ai-report-empty">
          {t('선택된 기간에 담당 업무 없음', 'No assigned work in the selected period')}
        </p>
        : !summary && !detailed
          ? <MatchedServerRiskTasks items={item.matchedRiskTasks} />
          : detailed && <div className="ai-report-individual-tasks">
            {item.assignedTasks.map((task) =>
              <FrozenTaskCard item={task} key={task.task.ref} />)}
          </div>}
    </div>
  </ReportSection>;
}

function MatchedServerRiskTasks({ items }: {
  items: readonly ProjectedServerRiskTask[];
}) {
  const { t } = useLanguage();
  if (items.length === 0) {
    return <p className="ai-report-linked-empty">
      {t('연결된 서버 위험 업무 없음', 'No matched server-risk task')}
    </p>;
  }
  return <div className="ai-report-member-risk-tasks">
    {items.map((item) => <div className="ai-report-member-risk-task"
      data-task-ref={item.task.task.ref} key={item.task.task.ref}>
      {item.task.task.url
        ? <a href={item.task.task.url}>{item.task.task.label}</a>
        : <strong>{item.task.task.label}</strong>}
      <span className={`risk-level ${item.representativeRisk.signal.severity.toLowerCase()}`}>
        {item.representativeRisk.signal.severity}
      </span>
      <span>{riskSignalLabel(item.representativeRisk.signal.code, t)}</span>
    </div>)}
  </div>;
}

function FrozenTaskCard({ item }: { item: TaskWorkView }) {
  const { t } = useLanguage();
  return <div className="ai-report-task-card" data-task-ref={item.task.ref}>
    <div>
      {item.task.url
        ? <a href={item.task.url}>{item.task.label}</a>
        : <strong>{item.task.label}</strong>}
      <span className={`task-state ${item.status.toLowerCase()}`}>
        {taskStatusLabel(item.status, t)}
      </span>
    </div>
    <p>
      {t('담당', 'Owner')}: {item.assignee?.label ?? t('미할당', 'Unassigned')}
      {' · '}{t('우선순위', 'Priority')}: {priorityLabel(item.priority, t)}
      {' · '}{t('마감', 'Due')}: {dueLabel(item.dueState, t)}
    </p>
    <p>
      {t('체크리스트', 'Checklist')}: {item.checklistCompleted}/{item.checklistTotal}
      {item.objective && ` · ${t('목표', 'Goal')}: ${item.objective.label}`}
      {item.blockerType && ` · ${t('차단', 'Blocker')}: ${item.blockerType}`}
    </p>
    {item.changes.length > 0 && <div className="task-change-list">
      {item.changes.map((change) => <span key={change}>{changeLabel(change, t)}</span>)}
    </div>}
  </div>;
}

function NarrativeCard({ item, evidence, compact = false }: {
  item: NarrativeItemView;
  evidence: Record<string, EvidenceValue>;
  compact?: boolean;
}) {
  return <div className={`ai-report-item${compact ? ' compact' : ''}`}>
    <p>{item.text}</p>
    <EvidenceDetails item={item} evidence={evidence} />
  </div>;
}

function RiskCard({ projection, evidence, preservePrintRiskMarkup = false }: {
  projection: ProjectedAiRisk;
  evidence: Record<string, EvidenceValue>;
  preservePrintRiskMarkup?: boolean;
}) {
  const { t } = useLanguage();
  const { item, tasks, taskRefMismatch } = projection;
  return <div className="ai-report-item risk">
    <span className={`risk-level ${item.severity.toLowerCase()}`}>{item.severity}</span>
    <strong className="ai-report-item-label">
      {t('AI 해석 · 예상 영향', 'AI interpretation · expected impact')}
    </strong>
    <p>{item.text}</p>
    {!preservePrintRiskMarkup && item.taskRefs.length > 0 && <>
      <LinkedTaskList tasks={tasks} />
      {taskRefMismatch && <p className="ai-report-contract-warning">{t(
        '업무 참조가 frozen 리포트와 일치하지 않아 연결하지 않았습니다.',
        'Task references did not match the frozen report and were not linked.',
      )}</p>}
    </>}
    <EvidenceDetails item={preservePrintRiskMarkup ? item : {
      ...item,
      taskRefs: tasks.map(({ task }) => task),
    }} evidence={evidence} />
  </div>;
}

function LinkedTaskList({ tasks }: { tasks: readonly TaskWorkView[] }) {
  const { t } = useLanguage();
  if (tasks.length === 0) {
    return <p className="ai-report-linked-empty">
      {t('연결된 frozen 업무 없음', 'No linked frozen task')}
    </p>;
  }
  return <div className="ai-report-linked-tasks">
    <strong>{t('연결 업무', 'Linked tasks')}</strong>
    {tasks.map((item) => <div className="ai-report-linked-task"
      data-task-ref={item.task.ref} key={item.task.ref}>
      {item.task.url
        ? <a href={item.task.url}>{item.task.label}</a>
        : <strong>{item.task.label}</strong>}
      <span>
        {t('담당', 'Owner')}: {item.assignee?.label ?? t('미할당', 'Unassigned')}
        {' · '}{t('상태', 'Status')}: {taskStatusLabel(item.status, t)}
        {' · '}{t('우선순위', 'Priority')}: {priorityLabel(item.priority, t)}
      </span>
    </div>)}
  </div>;
}

function MemberExceptions({ items }: {
  items: readonly ProjectedMemberException[];
}) {
  const { t } = useLanguage();
  return <ReportSection title={t('팀원 예외', 'Member exceptions')}>
    {items.length > 0 ? items.map((item) =>
      <div className="ai-report-member-exception"
        data-member-ref={item.member.member.ref}
        key={item.member.member.ref}>
        <div>
          <strong>{item.member.member.label}</strong>
          <span className={`risk-level ${item.representativeRisk.signal.severity.toLowerCase()}`}>
            {item.representativeRisk.signal.severity}
          </span>
        </div>
        <p>{riskSignalLabel(item.representativeRisk.signal.code, t)}</p>
        <span>{t('연결 업무', 'Linked tasks')} {item.tasks.length}
          {' · '}{t('지연', 'Overdue')} {item.member.delayed}</span>
      </div>)
      : <p className="ai-report-empty">
        {t('확인된 팀원 예외 없음', 'No confirmed member exception')}
      </p>}
  </ReportSection>;
}

function ActionCard({ item, evidence }: {
  item: ActionNarrativeItemView;
  evidence: Record<string, EvidenceValue>;
}) {
  const { t } = useLanguage();

  return <div className="ai-report-item action">
    <span className="action-priority">P{item.priority}</span>
    <div>
      <strong>{item.action}</strong>
      {item.owner && <span className="action-owner">
        {t('담당', 'Owner')}: {item.owner.label}
      </span>}
      <p><b>{t('왜 지금', 'Why now')}</b>{item.reason}</p>
    </div>
    <EvidenceDetails item={item} evidence={evidence} />
  </div>;
}

function DecisionCard({ item, evidence }: {
  item: DecisionNarrativeItemView;
  evidence: Record<string, EvidenceValue>;
}) {
  const { t } = useLanguage();
  return <div className="ai-report-item decision">
    <span className="ai-report-item-label">{t('결정 필요', 'Decision required')}</span>
    <strong>{item.question}</strong>
    <p><b>{t('결정 영향', 'Decision impact')}</b>{item.impact}</p>
    <EvidenceDetails item={item} evidence={evidence} />
  </div>;
}

function EvidenceDetails({ item, evidence }: {
  item: {
    evidenceKeys: string[];
    taskRefs: LocalReference[];
    objectiveRefs: LocalReference[];
  };
  evidence: Record<string, EvidenceValue>;
}) {
  const { t } = useLanguage();
  const references = [...item.taskRefs, ...item.objectiveRefs];
  if (item.evidenceKeys.length === 0 && references.length === 0) return null;
  return <details className="ai-report-evidence">
    <summary>{t('근거 보기', 'View evidence')}</summary>
    <div>
      {item.evidenceKeys.map((key) => {
        const value = evidence[key];
        return value
          ? <span className="evidence-chip" key={key}>{value.label} <b>{value.value}</b></span>
          : <span className="evidence-chip" key={key}>{key}</span>;
      })}
      {references.map((reference) => reference.url
        ? <a className="reference-chip" href={reference.url} key={reference.ref}>
          {reference.label}{reference.secondaryLabel ? ` · ${reference.secondaryLabel}` : ''}
        </a>
        : <span className="reference-chip" key={reference.ref}>{reference.label}</span>)}
    </div>
  </details>;
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div><span>{label}</span><b>{value}</b></div>;
}

/**
 * 부호가 아니라 방향을 읽는다. 완료 업무 감소와 지연 업무 증가는 부호가 반대지만 둘 다 악화이므로
 * 후자처럼 증가가 나쁜 지표는 `invert`로 표시한다. 색만으로 뜻을 전달하지 않도록 부호와
 * 개선·악화 문구를 항상 함께 둔다.
 */
function Delta({ label, value, suffix = '', invert = false }: {
  label: string;
  value?: number;
  suffix?: string;
  invert?: boolean;
}) {
  const { t } = useLanguage();
  const tone = value == null || value === 0
    ? 'flat'
    : (value > 0) !== invert ? 'good' : 'bad';
  return <div className={`ai-report-delta ${tone}`}>
    <span>{label}</span>
    <b>{delta(value, suffix)}</b>
    <small>{tone === 'flat'
      ? t('변화 없음', 'no change')
      : tone === 'good' ? t('개선', 'improved') : t('악화', 'worsened')}</small>
  </div>;
}

function percent(value?: number) {
  return value == null ? '-' : `${value}%`;
}

function hours(value: number | undefined, t: (ko: string, en: string) => string) {
  return value == null ? '-' : `${value}${t('시간', 'h')}`;
}

function riskSignalLabel(code: string, t: (ko: string, en: string) => string) {
  return ({
    OVERDUE_PRESENT: t('지연 업무가 있습니다.', 'Overdue tasks are present.'),
    ON_HOLD_PRESENT: t('보류 업무가 있습니다.', 'On-hold tasks are present.'),
    HIGH_PRIORITY_PRESENT: t('높은 우선순위 업무가 있습니다.', 'High-priority tasks are present.'),
    DELAYED_TASKS: t('지연 업무가 있습니다.', 'Overdue tasks are present.'),
    ON_HOLD_TASKS: t('보류 업무가 있습니다.', 'On-hold tasks are present.'),
    HIGH_PRIORITY_TASKS: t('높은 우선순위 업무가 있습니다.', 'High-priority tasks are present.'),
  } as Record<string, string>)[code] ?? code;
}
function delta(value?: number, suffix = '') {
  if (value == null) return '-';
  return `${value > 0 ? '+' : ''}${value}${suffix}`;
}

function publicationLabel(
  status: CompletedWeeklyAiReport['publicationStatus'],
  t: (ko: string, en: string) => string,
) {
  const labels = {
    LEGACY: t('기존 리포트', 'Legacy'),
    DRAFT: t('검토 초안', 'Draft'),
    FINALIZED: t('확정됨', 'Finalized'),
    SUPERSEDED: t('이전 리비전', 'Superseded'),
  };
  return labels[status];
}
