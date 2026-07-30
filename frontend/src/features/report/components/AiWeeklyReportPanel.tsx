import { useEffect, useState } from 'react';
import { ApiError, errorMessage, saveBlob } from '../../../api/client';
import { GroupResponse } from '../../../api/groupApi';
import {
  CompletedWeeklyAiReport,
  NarrativeDraft,
  reportApi,
  RevisionSummary,
  WeeklyAiReport,
} from '../../../api/reportApi';
import { useLanguage } from '../../../app/LanguageContext';
import { lastCompletedWeekStart } from '../../../app/week';
import {
  DEFAULT_REPORT_PROJECTION_STATE,
  normalizeReportProjectionState,
  ReportProjectionState,
  ReportScope,
  sameReportProjectionState,
} from '../reportProjection';
import { AiReportContent } from './AiReportContent';
import { AiReportDraftEditor } from './AiReportDraftEditor';
import { aiReportPrintPath } from './reportPrintRenderer';

type Props = {
  groupId: number;
  group?: GroupResponse;
  projectionState?: ReportProjectionState;
  onProjectionStateChange?: (state: ReportProjectionState) => void;
  initialReportId: number;
  onReportIdChange?: (reportId: number) => void;
};
type ViewState =
  'idle' | 'loading' | 'generating' | 'saved' | 'failed' | 'insufficient' | 'unconfigured';

export function AiWeeklyReportPanel({
  groupId,
  group,
  projectionState = DEFAULT_REPORT_PROJECTION_STATE,
  onProjectionStateChange,
  initialReportId,
  onReportIdChange,
}: Props) {
  const { language, t } = useLanguage();
  const [weekStart, setWeekStart] = useState(lastCompletedWeekStart);
  const [report, setReport] = useState<CompletedWeeklyAiReport>();
  const [failedReport, setFailedReport] = useState<WeeklyAiReport>();
  const [revisions, setRevisions] = useState<RevisionSummary[]>([]);
  const [draft, setDraft] = useState<NarrativeDraft>();
  const [editing, setEditing] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [state, setState] = useState<ViewState>('idle');
  const [message, setMessage] = useState('');
  const canManageAi = group?.membershipPlan === 'PAID' && group.role === 'LEADER';
  const canViewAi = group?.membershipPlan === 'PAID';

  useEffect(() => {
    if (group?.timezone) setWeekStart(lastCompletedWeekStart(group.timezone));
  }, [group?.timezone]);

  useEffect(() => {
    if (!canViewAi) {
      setReport(undefined);
      setFailedReport(undefined);
      setRevisions([]);
      setState('idle');
      setMessage('');
      return;
    }
    let active = true;
    setState('loading');
    setMessage('');
    setEditing(false);
    const request = reportApi.findWeeklyAiById(groupId, initialReportId)
      .then(async (value) => {
        const revisionValues = await reportApi.revisions(
          groupId,
          value.periodStart,
          value.language,
        ).catch(() => []);
        return [value, revisionValues] as const;
      });
    request.then(([value, revisionValues]) => {
      if (!active) return;
      if (value.periodStart !== weekStart) setWeekStart(value.periodStart);
      setRevisions(revisionValues);
      acceptReport(value);
    }).catch((value: ApiError) => {
      if (!active) return;
      setReport(undefined);
      setFailedReport(undefined);
      setRevisions([]);
      if (value.code === 'AI_REPORT_NOT_FOUND') setState('idle');
      else {
        setState(errorState(value));
        setMessage(errorMessage(value));
      }
    });
    return () => { active = false; };
  }, [
    canViewAi,
    groupId,
    initialReportId,
    weekStart,
  ]);

  useEffect(() => {
    if (!report || !onProjectionStateChange) return;
    const normalized = normalizeReportProjectionState(
      projectionState,
      report.operations.members,
    );
    if (!sameReportProjectionState(normalized, projectionState)) {
      onProjectionStateChange(normalized);
    }
  }, [
    onProjectionStateChange,
    projectionState.density,
    projectionState.memberRef,
    projectionState.scope,
    report,
  ]);

  function acceptReport(value: WeeklyAiReport) {
    if (isCompleted(value)) {
      setReport(value);
      setFailedReport(undefined);
      setDraft(value.draft ? cloneDraft(value.draft) : undefined);
      setState('saved');
    } else if (value.status === 'FAILED') {
      setReport(undefined);
      setFailedReport(value);
      setState('failed');
      setMessage(t(
        '이전 생성이 실패했습니다. 다시 생성할 수 있습니다.',
        'The previous generation failed. You can retry it.',
      ));
    } else {
      setReport(undefined);
      setFailedReport(undefined);
      setState('generating');
    }
  }

  async function refreshRevisions() {
    setRevisions(await reportApi.revisions(groupId, weekStart, language));
  }

  async function retryFailed() {
    if (!failedReport) return;
    setSubmitting(true);
    setState('generating');
    setMessage('');
    try {
      const created = await reportApi.regenerate(
        groupId, failedReport.reportId, failedReport.editorVersion,
      );
      acceptReport(created);
      onReportIdChange?.(created.reportId);
      await refreshRevisions();
    } catch (value) {
      setState(errorState(value as ApiError));
      setMessage(errorMessage(value));
    } finally {
      setSubmitting(false);
    }
  }

  async function openRevision(reportId: number) {
    setState('loading');
    setMessage('');
    try {
      const value = await reportApi.findWeeklyAiById(groupId, reportId);
      acceptReport(value);
      onReportIdChange?.(value.reportId);
    } catch (value) {
      setState(errorState(value as ApiError));
      setMessage(errorMessage(value));
    }
  }

  async function saveDraft() {
    if (!report || !draft) return;
    setState('loading');
    setMessage('');
    try {
      const updated = await reportApi.editDraft(
        groupId, report.reportId, report.editorVersion, draft,
      );
      acceptReport(updated);
      setEditing(false);
      await refreshRevisions();
    } catch (value) {
      setState(errorState(value as ApiError));
      setMessage(errorMessage(value));
    }
  }

  async function regenerate() {
    if (!report || !window.confirm(t(
      '현재 근거를 그대로 사용해 새 리비전을 생성할까요?',
      'Generate a new revision from the same frozen evidence?',
    ))) return;
    setState('generating');
    setMessage('');
    try {
      const created = await reportApi.regenerate(
        groupId, report.reportId, report.editorVersion,
      );
      acceptReport(created);
      onReportIdChange?.(created.reportId);
      setEditing(false);
      await refreshRevisions();
    } catch (value) {
      setState(errorState(value as ApiError));
      setMessage(errorMessage(value));
    }
  }

  async function finalizeReport() {
    if (!report || !window.confirm(t(
      '이 초안을 확정할까요? 확정한 내용은 수정할 수 없습니다.',
      'Finalize this draft? Finalized content cannot be edited.',
    ))) return;
    setState('loading');
    setMessage('');
    try {
      const finalized = await reportApi.finalize(
        groupId, report.reportId, report.editorVersion,
      );
      acceptReport(finalized);
      setEditing(false);
      await refreshRevisions();
    } catch (value) {
      setState(errorState(value as ApiError));
      setMessage(errorMessage(value));
    }
  }

  async function downloadPdf() {
    if (!report) return;
    setSubmitting(true);
    setMessage('');
    try {
      const file = await reportApi.downloadWeeklyAiPdf(groupId, report.reportId);
      saveBlob(file.blob, file.filename);
    } catch (value) {
      setMessage(errorMessage(value));
    } finally {
      setSubmitting(false);
    }
  }

  function selectScope(scope: ReportScope) {
    if (!report || !onProjectionStateChange) return;
    const memberRef = scope === 'INDIVIDUAL_MEMBER'
      ? projectionState.memberRef ?? report.operations.members[0]?.member.ref
      : undefined;
    onProjectionStateChange(normalizeReportProjectionState(
      { ...projectionState, scope, memberRef },
      report.operations.members,
    ));
  }

  function selectMember(memberRef: string) {
    if (!report || !onProjectionStateChange) return;
    onProjectionStateChange(normalizeReportProjectionState(
      { ...projectionState, scope: 'INDIVIDUAL_MEMBER', memberRef },
      report.operations.members,
    ));
  }

  return <section className="ai-report-reader">
    {!group || group.membershipPlan === 'FREE' ? <div className="ai-report-lock">
      <strong>🔒 {t('유료 플랜 기능', 'Paid plan feature')}</strong>
      <p>{t(
        '기본 리포트는 현황을 보여주고, AI 리포트는 비식별 업무 문맥을 분석해 위험·행동·결정사항을 제안합니다.',
        'Basic reports show status. AI reports analyze de-identified context to suggest risks, actions, and decisions.',
      )}</p>
    </div> : <>
      <div className="ai-report-reader-toolbar">
        <div className="report-controls ai-report-controls">
          {canManageAi && revisions.length > 0 && <label><span>{t('리비전', 'Revision')}</span>
              <select name="ai-report-revision" value={report?.reportId ?? failedReport?.reportId ?? ''}
                onChange={(event) => void openRevision(Number(event.target.value))}>
            {revisions.map((revision) => <option value={revision.reportId}
              key={revision.reportId}>
              R{revision.revision} · {revision.publicationStatus}
            </option>)}
          </select>
          </label>}
          {report && onProjectionStateChange && <>
          <label><span>{t('리포트 범위', 'Report scope')}</span>
            <select name="ai-report-scope" value={projectionState.scope}
              onChange={(event) => selectScope(event.target.value as ReportScope)}>
              <option value="GROUP">{t('그룹 전체', 'Whole group')}</option>
              <option value="MEMBER_COMPARISON">
                {t('팀원 비교', 'Member comparison')}
              </option>
              <option value="INDIVIDUAL_MEMBER">
                {t('개별 팀원', 'Individual member')}
              </option>
            </select>
          </label>
          {projectionState.scope === 'INDIVIDUAL_MEMBER'
            && <label><span>{t('팀원 선택', 'Select member')}</span>
              <select name="ai-report-member" value={projectionState.memberRef}
                onChange={(event) => selectMember(event.target.value)}>
                {report.operations.members.map(({ member }) =>
                  <option value={member.ref} key={member.ref}>{member.label}</option>)}
              </select>
            </label>}
          </>}
          {failedReport && <button className="report-download" type="button"
            disabled={submitting} onClick={() => void retryFailed()}>
            {submitting
              ? t('생성 중...', 'Generating...')
              : t('실패한 리비전 다시 시도', 'Retry failed revision')}
          </button>}
        </div>
        <span className={`report-state ${state}`}>{stateLabel(state, t)}</span>
      </div>

      {report && !editing && <>
        <AiReportContent report={report} density={projectionState.density} />
        <div className="ai-report-actions">
          {report.publicationStatus === 'FINALIZED'
            ? <button className="secondary" type="button" disabled={submitting}
              onClick={() => void downloadPdf()}>
              {submitting ? t('다운로드 중...', 'Downloading...')
                : t('PDF 다운로드', 'Download PDF')}
            </button>
            : <a className="secondary button-link"
              href={aiReportPrintPath(groupId, report.reportId)} target="_blank" rel="noreferrer">
              {t('인쇄 미리보기', 'Print preview')}
            </a>}
          {canManageAi && report.publicationStatus === 'DRAFT' && <>
            <button className="secondary" type="button" onClick={() => {
              setDraft(report.draft ? cloneDraft(report.draft) : undefined);
              setEditing(true);
            }}>{t('초안 편집', 'Edit draft')}</button>
            <button className="secondary" type="button"
              disabled={state === 'generating'} onClick={regenerate}>
              {t('새 리비전 생성', 'Regenerate')}
            </button>
            <button className="primary" type="button"
              disabled={state === 'loading'} onClick={finalizeReport}>
              {t('리포트 확정', 'Finalize report')}
            </button>
          </>}
        </div>
      </>}

      {report && editing && draft && <AiReportDraftEditor
        value={draft}
        members={report.operations.members.map(({ member }) => member)}
        disabled={state === 'loading'}
        onChange={setDraft}
        onSave={() => void saveDraft()}
        onCancel={() => {
          setDraft(report.draft ? cloneDraft(report.draft) : undefined);
          setEditing(false);
        }}
      />}

      {message && <p className="error">{message}</p>}
      <p className="report-policy">{t(
        '업무 제목·설명·댓글·이름·자유 입력 보류 사유는 OpenAI에 보내지 않습니다. 수치와 날짜는 서버 근거로만 표시합니다.',
        'Task titles, descriptions, comments, names, and free-text blocker reasons are not sent to OpenAI. Numbers and dates come only from server evidence.',
      )}</p>
    </>}
  </section>;
}

function isCompleted(value: WeeklyAiReport): value is CompletedWeeklyAiReport {
  return value.status === 'COMPLETED' && value.analysis != null;
}

function cloneDraft(value: NarrativeDraft) {
  return JSON.parse(JSON.stringify(value)) as NarrativeDraft;
}

function stateLabel(state: ViewState, t: (ko: string, en: string) => string) {
  const labels: Record<ViewState, [string, string]> = {
    idle: ['생성 가능', 'Ready'],
    loading: ['확인 중', 'Checking'],
    generating: ['생성 중', 'Generating'],
    saved: ['검토 가능', 'Ready to review'],
    failed: ['확인 필요', 'Needs attention'],
    insufficient: ['데이터 부족', 'Insufficient data'],
    unconfigured: ['AI 미설정', 'AI not configured'],
  };
  const [ko, en] = labels[state];
  return t(ko, en);
}

function errorState(error: ApiError): ViewState {
  if (error.code === 'AI_REPORT_INSUFFICIENT_DATA') return 'insufficient';
  if (error.code === 'AI_REPORT_NOT_CONFIGURED') return 'unconfigured';
  if (error.code === 'AI_REPORT_GENERATING') return 'generating';
  return 'failed';
}
