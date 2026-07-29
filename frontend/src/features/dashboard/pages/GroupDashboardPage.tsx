import { FormEvent, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage, saveDownloadedFile } from '../../../api/client';
import { dashboardApi, DashboardTask, GroupDashboard } from '../../../api/dashboardApi';
import { taskApi, TaskPriority } from '../../../api/taskApi';
import { AppNavigation, Modal } from '../../../app/AppNavigation';
import { groupApi, GroupResponse } from '../../../api/groupApi';
import { useLanguage } from '../../../app/LanguageContext';
import { lastCompletedWeekStart } from '../../../app/week';
import { AiWeeklyReportAction } from '../../report/components/AiWeeklyReportAction';

const statusLabels: Record<string, [string, string]> = { requested: ['승인 대기', 'Pending approval'], todo: ['할 일', 'To do'], inProgress: ['진행 중', 'In progress'], onHold: ['보류', 'On hold'], completed: ['완료', 'Completed'], rejected: ['반려', 'Rejected'], cancelled: ['취소', 'Cancelled'], delayed: ['지연', 'Overdue'] };
const taskStatusLabels: Record<string, [string, string]> = { REQUESTED: ['승인 대기', 'Pending approval'], TODO: ['할 일', 'To do'], IN_PROGRESS: ['진행 중', 'In progress'], ON_HOLD: ['보류', 'On hold'], COMPLETED: ['완료', 'Completed'], REJECTED: ['반려', 'Rejected'], CANCELLED: ['취소', 'Cancelled'] };
const priorityLabels: Record<TaskPriority, [string, string]> = { LOW: ['낮음', 'Low'], NORMAL: ['보통', 'Normal'], HIGH: ['높음', 'High'], URGENT: ['긴급', 'Urgent'] };
type ReportScope = 'GROUP' | 'MY';
type ReportPeriod = 'WEEKLY' | 'MONTHLY' | 'YEARLY';

export function GroupDashboardPage() {
  const { t, language } = useLanguage();
  const label = (value: [string, string]) => value[language === 'ko' ? 0 : 1];
  const groupId = Number(useParams().groupId);
  const today = new Date();
  const [year, setYear] = useState(today.getFullYear());
  const [month, setMonth] = useState(today.getMonth() + 1);
  const [week, setWeek] = useState(0);
  const [dashboard, setDashboard] = useState<GroupDashboard>();
  const [group, setGroup] = useState<GroupResponse>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TaskPriority>('NORMAL');
  const [dueAt, setDueAt] = useState('');
  const [saving, setSaving] = useState(false);
  const [createError, setCreateError] = useState('');
  const [reportScope, setReportScope] = useState<ReportScope>('MY');
  const [reportPeriod, setReportPeriod] = useState<ReportPeriod>('WEEKLY');
  const [reportPending, setReportPending] = useState(false);
  const [reportMessage, setReportMessage] = useState('');
  const range = useMemo(() => periodRange(year, month, week), [year, month, week]);
  const periodLabel = language === 'ko' ? `${year}년 ${month}월${week ? ` ${week}주차` : ''}` : `${monthName(month)} ${year}${week ? ` · Week ${week}` : ''}`;

  useEffect(() => {
    groupApi.get(groupId).then((value) => {
      setGroup(value);
      setReportScope(value.role === 'LEADER' ? 'GROUP' : 'MY');
    }).catch((value) => setError(errorMessage(value)));
  }, [groupId]);
  useEffect(() => { load(); }, [groupId, range.from, range.to]); // eslint-disable-line react-hooks/exhaustive-deps
  async function load() {
    setLoading(true); setError('');
    try { setDashboard(await dashboardApi.group(groupId, range.from, range.to)); }
    catch (value) { setError(errorMessage(value)); }
    finally { setLoading(false); }
  }
  async function createTask(event: FormEvent) {
    event.preventDefault();
    setSaving(true); setCreateError('');
    try {
      await taskApi.create(groupId, { title: title.trim(), description: description.trim() || undefined, priority, dueAt: dueAt || undefined });
      setTitle(''); setDescription(''); setPriority('NORMAL'); setDueAt(''); setShowCreate(false);
      window.dispatchEvent(new Event('notifications:refresh'));
      const current = new Date();
      const periodChanged = year !== current.getFullYear() || month !== current.getMonth() + 1 || week !== 0;
      setYear(current.getFullYear()); setMonth(current.getMonth() + 1); setWeek(0);
      if (!periodChanged) await load();
    } catch (value) { setCreateError(errorMessage(value)); }
    finally { setSaving(false); }
  }
  async function downloadReport(scope: ReportScope = reportScope, period: ReportPeriod = reportPeriod) {
    setReportPending(true);
    setReportMessage('');
    try {
      const reportRangeValue = reportRange(year, month, week, period, language);
      const file = await groupApi.downloadBasicReport(groupId, {
        scope,
        periodType: period,
        from: reportRangeValue.from,
        to: reportRangeValue.to,
        language,
      });
      saveDownloadedFile(file);
    } catch (value) {
      setReportMessage(errorMessage(value));
    } finally {
      setReportPending(false);
    }
  }
  if (!accessToken.get()) return <Navigate to="/login" replace />;
  const years = Array.from({ length: 5 }, (_, index) => today.getFullYear() - 3 + index);
  return <><AppNavigation /><main className="group-dashboard-page app-page">
    <header className="dashboard-header"><div><Link to="/groups">← {t('내 그룹', 'My groups')}</Link><h1>{dashboard?.groupName ?? t('그룹', 'Group')} {t('대시보드', 'Dashboard')}</h1><p>{t(`${periodLabel}의 업무와 일정을 모아봤어요.`, `Tasks and events for ${periodLabel}.`)}</p></div><div className="group-dashboard-actions"><button className="primary create-action" type="button" onClick={() => setShowCreate(true)}><span aria-hidden="true">＋</span> {t('새 업무', 'New task')}</button><Link className="secondary" to={`/groups/${groupId}/tasks`}>{t('업무 목록', 'Task list')}</Link><Link className="settings-icon-button" to={`/groups/${groupId}`} aria-label={t('그룹 설정', 'Group settings')}>⚙</Link></div></header>
    <section className="dashboard-period dashboard-period-selectors"><div><label><span>{t('연도', 'Year')}</span><select value={year} onChange={(event) => setYear(Number(event.target.value))}>{years.map((value) => <option value={value} key={value}>{language === 'ko' ? `${value}년` : value}</option>)}</select></label><label><span>{t('월', 'Month')}</span><select value={month} onChange={(event) => { setMonth(Number(event.target.value)); setWeek(0); }}>{Array.from({ length: 12 }, (_, index) => index + 1).map((value) => <option value={value} key={value}>{language === 'ko' ? `${value}월` : monthName(value)}</option>)}</select></label><label><span>{t('주차', 'Week')}</span><select value={week} onChange={(event) => setWeek(Number(event.target.value))}><option value={0}>{t('월 전체', 'Full month')}</option>{availableWeeks(year, month).map((value) => <option value={value} key={value}>{t(`${value}주차`, `Week ${value}`)}</option>)}</select></label></div><div><button className="secondary" type="button" onClick={() => shiftMonth(year, month, -1, setYear, setMonth, setWeek)}>‹ {t('이전 달', 'Previous month')}</button><button className="secondary" type="button" onClick={() => { setYear(today.getFullYear()); setMonth(today.getMonth() + 1); setWeek(0); }}>{t('이번 달', 'This month')}</button></div></section>
    {error && <p className="error">{error}</p>}{loading && <p className="muted">{t('대시보드를 불러오는 중...', 'Loading dashboard...')}</p>}
    {!loading && !dashboard && group && <section className="dashboard-panel weekly-report-preview"><div className="dashboard-panel-title"><div><span className="page-eyebrow">MY REPORT</span><h2>{t('내 업무 리포트', 'My task report')}</h2><p>{t('그룹 전체 대시보드 공개 여부와 관계없이 본인 담당 업무를 확인할 수 있습니다.', 'View your assigned work regardless of group dashboard visibility.')}</p></div></div><div className="report-controls"><label><span>{t('기간', 'Period')}</span><select value={reportPeriod} onChange={(event) => setReportPeriod(event.target.value as ReportPeriod)}><option value="WEEKLY">{t('주간', 'Weekly')}</option><option value="MONTHLY">{t('월간', 'Monthly')}</option><option value="YEARLY">{t('연간', 'Yearly')}</option></select></label><button className="report-download" type="button" disabled={reportPending} onClick={() => downloadReport('MY', reportPeriod)}>{reportPending ? t('생성 중...', 'Generating...') : t('내 PDF 리포트 생성', 'Generate my PDF report')}</button></div>{reportMessage && <p className="error">{reportMessage}</p>}</section>}
    {dashboard && <>
      <section className="dashboard-overview">
        <section className="dashboard-panel dashboard-status-panel"><div className="dashboard-panel-title"><div><span className="page-eyebrow">WORKFLOW</span><h2>{periodLabel} {t('업무 상태', 'Task status')}</h2><p>{t('승인 대기는 전체 미처리 건, 나머지는 선택 기간 기준입니다.', 'Pending approval includes all open requests; other figures use the selected period.')}</p></div></div><div className="status-metric-grid">{Object.entries(dashboard.statuses).map(([key, value]) => <div className={key === 'delayed' ? 'risk' : ''} key={key}><span>{label(statusLabels[key])}</span><strong>{value}</strong></div>)}</div></section>
        <section className="dashboard-stat-grid dashboard-kpi-grid" aria-label={t('기간 핵심 지표', 'Key period metrics')}><Stat label={t('기간 업무', 'Period tasks')} value={dashboard.periodTasks.length} /><Stat label={t('흐름 진행률', 'Workflow progress')} value={rate(dashboard.workflowProgressPercent)} /><Stat label={t('기간 완료율', 'Completion rate')} value={rate(dashboard.periodCompletionRatePercent)} detail={t(`${dashboard.periodCompletedCount}/${dashboard.periodCreatedCount}건`, `${dashboard.periodCompletedCount}/${dashboard.periodCreatedCount} tasks`)} /><Stat label={t('기한 준수율', 'On-time rate')} value={rate(dashboard.onTimeRatePercent)} detail={t(`${dashboard.onTimeCompletedCount}/${dashboard.completedWithDueDateCount}건`, `${dashboard.onTimeCompletedCount}/${dashboard.completedWithDueDateCount} tasks`)} /><Stat label={t('평균 완료 시간', 'Average completion time')} value={dashboard.averageCompletionHours == null ? '-' : t(`${dashboard.averageCompletionHours}시간`, `${dashboard.averageCompletionHours} hours`)} /></section>
      </section>
      <section className="dashboard-two-columns"><section className="dashboard-panel"><h2>{t('기간 업무', 'Period tasks')}</h2>{dashboard.periodTasks.length === 0 ? <p className="empty-state">{t('이 기간에 연결된 업무가 없습니다.', 'No tasks are connected to this period.')}</p> : <div className="dashboard-task-list period-task-list">{dashboard.periodTasks.map((task) => <TaskLink task={task} key={task.id} />)}</div>}</section>
        <section className="dashboard-panel"><div className="dashboard-panel-title inline"><div><h2>{t('그룹 전체 일정', 'Group schedule')}</h2><p>{t('내 담당 여부와 관계없이 그룹 일정을 보여줍니다.', 'Shows group events regardless of assignment.')}</p></div><Link to={`/calendar?groupId=${groupId}`}>{t('캘린더 열기', 'Open calendar')} →</Link></div>{dashboard.calendarItems.length === 0 ? <p className="empty-state">{t('이 기간에 등록된 일정이 없습니다.', 'No events in this period.')}</p> : <div className="group-calendar-preview">{dashboard.calendarItems.slice(0, 8).map((item) => <Link to={item.sourceTaskId ? `/tasks/${item.sourceTaskId}` : `/calendar?groupId=${groupId}`} key={`${item.source}-${item.eventId ?? item.sourceTaskId}`}><time>{item.startAt.slice(5, 10)}<small>{item.allDay ? t('종일', 'All day') : item.startAt.slice(11, 16)}</small></time><span><strong>{item.title}</strong><small>{item.ownerNickname ?? item.groupName}</small></span></Link>)}</div>}</section></section>
      <section className="dashboard-panel team-workload-panel"><div className="dashboard-panel-title inline"><div><span className="page-eyebrow">TEAM WORKLOAD</span><h2>{t('팀원별 담당 현황', 'Workload by member')}</h2><p>{t('팀원이 늘어나도 한눈에 비교할 수 있도록 전체 너비로 정리했습니다.', 'A full-width view that remains easy to compare as the team grows.')}</p></div><span className="member-count">{t(`${dashboard.members.length}명`, `${dashboard.members.length} members`)}</span></div><div className="member-metrics">{dashboard.members.map((member) => <article key={member.memberId}><div className="member-metric-heading"><span className="member-metric-avatar" aria-hidden="true">{member.nickname.slice(0, 1)}</span><span><strong>{member.nickname}</strong><small>{member.role === 'LEADER' ? t('팀장', 'Leader') : t('팀원', 'Member')}</small></span></div><dl><div><dt>{t('담당', 'Assigned')}</dt><dd>{member.assignedCount}</dd></div><div><dt>{t('진행', 'Active')}</dt><dd>{member.activeCount}</dd></div><div><dt>{t('완료', 'Done')}</dt><dd>{member.completedCount}</dd></div><div><dt>{t('지연', 'Overdue')}</dt><dd>{member.delayedCount}</dd></div><div><dt>{t('기한 준수', 'On time')}</dt><dd>{rate(member.onTimeRatePercent)}</dd></div></dl></article>)}</div></section>
      <section className="dashboard-panel risk-task-panel"><div className="dashboard-panel-title"><div><h2>{t('위험·우선 확인 업무', 'At-risk and priority tasks')}</h2><p>{t('지연되었거나 먼저 확인해야 하는 업무입니다.', 'Tasks that are overdue or need attention first.')}</p></div></div>{dashboard.riskTasks.length === 0 ? <p className="empty-state">{t('선택 기간에 위험 업무가 없습니다.', 'No at-risk tasks in this period.')}</p> : <div className="dashboard-task-list risk-task-grid">{dashboard.riskTasks.map((task) => <TaskLink task={task} key={task.id} />)}</div>}</section>
      <section className="dashboard-panel weekly-report-preview"><div className="dashboard-panel-title inline"><div><span className="page-eyebrow">REPORTS</span><h2>{t('업무 리포트', 'Task reports')}</h2><p>{t('기본 리포트의 수치와 업무 흐름으로 PDF를 만들거나 AI 분석을 엽니다.', 'Create a PDF or open AI analysis from the basic-report metrics and work flow.')}</p></div><span className={`membership-badge ${group?.membershipPlan.toLowerCase() ?? 'free'}`}>{group?.membershipPlan === 'PAID' ? t('유료 그룹', 'Paid group') : t('무료 그룹', 'Free group')}</span></div><div className="report-controls"><label><span>{t('범위', 'Scope')}</span><select value={reportScope} onChange={(event) => setReportScope(event.target.value as ReportScope)}>{group?.role === 'LEADER' && <option value="GROUP">{t('그룹 전체', 'Whole group')}</option>}<option value="MY">{t('내 업무', 'My tasks')}</option></select></label><label><span>{t('기간', 'Period')}</span><select value={reportPeriod} onChange={(event) => setReportPeriod(event.target.value as ReportPeriod)}><option value="WEEKLY">{t('주간', 'Weekly')}</option><option value="MONTHLY">{t('월간', 'Monthly')}</option><option value="YEARLY">{t('연간', 'Yearly')}</option></select></label><button className="report-download" type="button" disabled={reportPending || !group} onClick={() => downloadReport()}>{reportPending ? t('생성 중...', 'Generating...') : t('PDF 리포트 생성', 'Generate PDF report')}</button><AiWeeklyReportAction groupId={groupId} group={group} selection={{ scope: reportScope, period: reportPeriod, from: reportRange(year, month, week, reportPeriod, language).from }} /></div>{group?.membershipPlan === 'FREE' && reportScope === 'GROUP' && <p className="report-policy">{t('무료 그룹의 전체 리포트는 주 2회까지 생성할 수 있습니다. 내 업무 리포트는 제한 없이 제공됩니다.', 'Free groups can generate two group reports per week. Personal reports are unlimited.')}</p>}{reportMessage && <p className={reportMessage.includes('더 생성') || reportMessage.includes('more group reports') ? 'success-message' : 'error'}>{reportMessage}</p>}<ReportSummary dashboard={dashboard} /></section>
  </>}
    {showCreate && <Modal title={t('새 업무 만들기', 'Create a task')} description={t(`${dashboard?.groupName ?? '그룹'} 대시보드에서 바로 업무를 추가합니다.`, `Add a task directly from the ${dashboard?.groupName ?? 'group'} dashboard.`)} onClose={() => !saving && setShowCreate(false)}><form className="form modal-form" onSubmit={createTask}>
      <label className="field"><span>{t('제목', 'Title')}</span><input autoFocus required maxLength={120} value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t('예: 발표 자료 초안 작성', 'e.g. Draft presentation slides')} /></label>
      <label className="field"><span>{t('설명 (선택)', 'Description (optional)')}</span><textarea maxLength={5000} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <label className="field"><span>{t('우선순위', 'Priority')}</span><select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>{Object.entries(priorityLabels).map(([value, valueLabel]) => <option value={value} key={value}>{label(valueLabel)}</option>)}</select></label>
      <label className="field"><span>{t('마감 날짜·시간 (선택)', 'Due date and time (optional)')}</span><input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} /><small className="field-help">{t('시간이 필요한 업무는 시각까지 지정할 수 있습니다.', 'Add a specific time when needed.')}</small></label>
      {createError && <p className="error">{createError}</p>}
      <div className="modal-actions"><button className="secondary" type="button" disabled={saving} onClick={() => setShowCreate(false)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={saving || !title.trim()}>{saving ? t('등록 중...', 'Creating...') : t('업무 만들기', 'Create task')}</button></div>
    </form></Modal>}
  </main></>;
}

function Stat({ label, value, detail }: { label: string; value: string | number; detail?: string }) { return <article><span>{label}</span><strong>{value}</strong>{detail && <small>{detail}</small>}</article>; }
function TaskLink({ task }: { task: DashboardTask }) { const { t, language } = useLanguage(); const value = taskStatusLabels[task.status]; return <Link to={`/tasks/${task.id}`}><div><strong>{task.title}</strong>{task.delayed && <span>{t('지연', 'Overdue')}</span>}</div><small>{value ? value[language === 'ko' ? 0 : 1] : task.status} · {task.assigneeNickname ?? t('담당자 미지정', 'Unassigned')} · {task.dueAt?.slice(0, 16) ?? t('마감 없음', 'No due date')}</small></Link>; }
function ReportSummary({ dashboard }: { dashboard: GroupDashboard }) { const { t } = useLanguage(); return <div className="report-summary-grid"><div><span>{t('완료한 업무', 'Completed tasks')}</span><strong>{t(`${dashboard.statuses.completed}건`, `${dashboard.statuses.completed}`)}</strong></div><div><span>{t('진행·보류', 'Active · On hold')}</span><strong>{t(`${dashboard.statuses.inProgress + dashboard.statuses.onHold}건`, `${dashboard.statuses.inProgress + dashboard.statuses.onHold}`)}</strong></div><div><span>{t('새로 등록', 'Newly created')}</span><strong>{t(`${dashboard.periodCreatedCount}건`, `${dashboard.periodCreatedCount}`)}</strong></div><div><span>{t('지연 업무', 'Overdue tasks')}</span><strong>{t(`${dashboard.statuses.delayed}건`, `${dashboard.statuses.delayed}`)}</strong></div></div>; }
function rate(value?: number) { return value == null ? '-' : `${value}%`; }
function dateText(value: Date) { return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}-${String(value.getDate()).padStart(2, '0')}`; }
function availableWeeks(year: number, month: number) { return Array.from({ length: Math.ceil(new Date(year, month, 0).getDate() / 7) }, (_, index) => index + 1); }
function periodRange(year: number, month: number, week: number) { const startDay = week ? (week - 1) * 7 + 1 : 1; const endDay = week ? Math.min(week * 7 + 1, new Date(year, month, 0).getDate() + 1) : 1; const from = new Date(year, month - 1, startDay); const to = week ? new Date(year, month - 1, endDay) : new Date(year, month, 1); return { from: dateText(from), to: dateText(to) }; }
function shiftMonth(year: number, month: number, amount: number, setYear: (value: number) => void, setMonth: (value: number) => void, setWeek: (value: number) => void) { const value = new Date(year, month - 1 + amount, 1); setYear(value.getFullYear()); setMonth(value.getMonth() + 1); setWeek(0); }
function monthName(month: number) { return new Intl.DateTimeFormat('en-US', { month: 'long' }).format(new Date(2020, month - 1, 1)); }
function reportRange(year: number, month: number, week: number, period: ReportPeriod, language: 'ko' | 'en') {
  if (period === 'YEARLY') return { from: `${year}-01-01`, to: `${year + 1}-01-01`, label: language === 'ko' ? `${year}년 연간` : `${year} Yearly` };
  if (period === 'MONTHLY') return { ...periodRange(year, month, 0), label: language === 'ko' ? `${year}년 ${month}월` : `${monthName(month)} ${year}` };
  const from = week
    ? new Date(year, month - 1, (week - 1) * 7 + 1)
    : new Date(`${lastCompletedWeekStart()}T00:00:00`);
  if (week) from.setDate(from.getDate() - ((from.getDay() + 6) % 7));
  const to = new Date(from);
  to.setDate(to.getDate() + 7);
  const dateRangeLabel = `${dateText(from)} ~ ${dateText(new Date(to.getFullYear(), to.getMonth(), to.getDate() - 1))}`;
  return { from: dateText(from), to: dateText(to), label: dateRangeLabel };
}
