import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { groupApi, GroupResponse } from '../../../api/groupApi';
import { taskApi, TaskPriority, TaskResponse } from '../../../api/taskApi';
import { AppNavigation, Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { ChecklistDraftField, cleanChecklistDraft } from '../components/ChecklistDraftField';
import { useTaskPasteImport } from '../components/useTaskPasteImport';

const statusLabels: Record<TaskResponse['status'], [string, string]> = {
  REQUESTED: ['승인 대기', 'Pending approval'], TODO: ['할 일', 'To do'], IN_PROGRESS: ['진행 중', 'In progress'], ON_HOLD: ['보류', 'On hold'],
  COMPLETED: ['완료', 'Completed'], REJECTED: ['반려', 'Rejected'], CANCELLED: ['취소', 'Cancelled'],
};
const priorityLabels: Record<TaskPriority, [string, string]> = {
  LOW: ['낮음', 'Low'], NORMAL: ['보통', 'Normal'], HIGH: ['높음', 'High'], URGENT: ['긴급', 'Urgent'],
};

export function TasksPage() {
  const { t, language } = useLanguage();
  const label = (value: [string, string]) => value[language === 'ko' ? 0 : 1];
  const groupId = Number(useParams().groupId);
  const [group, setGroup] = useState<GroupResponse>();
  const [tasks, setTasks] = useState<TaskResponse[]>([]);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [priority, setPriority] = useState<TaskPriority>('NORMAL');
  const [dueAt, setDueAt] = useState('');
  const [checklistItems, setChecklistItems] = useState<string[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [claimingId, setClaimingId] = useState<number>();
  const pasteNotice = useTaskPasteImport({
    active: showCreate, title, setTitle, checklistItems, setChecklistItems, disabled: saving,
  });

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1) {
      setError(t('올바르지 않은 그룹 주소입니다.', 'This group address is invalid.'));
      setLoading(false);
      return;
    }
    Promise.all([groupApi.get(groupId), taskApi.list(groupId)])
      .then(([groupValue, taskValues]) => { setGroup(groupValue); setTasks(taskValues); })
      .catch((caught) => setError(errorMessage(caught)))
      .finally(() => setLoading(false));
  }, [groupId]);

  async function create(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError('');
    try {
      const checklist = cleanChecklistDraft(checklistItems);
      const created = await taskApi.create(groupId, {
        title, description: description || undefined, priority, dueAt: dueAt || undefined,
        checklistItems: checklist.length > 0 ? checklist : undefined,
      });
      setTasks((current) => [created, ...current]);
      setTitle('');
      setDescription('');
      setPriority('NORMAL');
      setDueAt('');
      setChecklistItems([]);
      setShowCreate(false);
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setSaving(false);
    }
  }

  async function claim(event: React.MouseEvent, task: TaskResponse) {
    event.preventDefault();
    event.stopPropagation();
    setClaimingId(task.id);
    setError('');
    try {
      const updated = await taskApi.claim(task.id, task.version);
      setTasks((current) => current.map((value) => value.id === updated.id ? updated : value));
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setClaimingId(undefined);
    }
  }

  if (!accessToken.get()) return <Navigate to={`/login?next=${encodeURIComponent(`/groups/${groupId}/tasks`)}`} replace />;
  if (loading) return <main className="center-page">{t('업무를 불러오는 중...', 'Loading tasks...')}</main>;
  const sortedTasks = [...tasks].sort((left, right) => {
    if (left.dueAt && right.dueAt) return left.dueAt.localeCompare(right.dueAt);
    if (left.dueAt) return -1; if (right.dueAt) return 1;
    return right.createdAt.localeCompare(left.createdAt);
  });
  return <><AppNavigation /><main className="tasks-page app-page">
    <header className="tasks-header">
      <div><Link to={`/groups/${groupId}`}>← {t('그룹으로', 'Back to group')}</Link><h1>{group?.name ?? t('그룹', 'Group')} {t('업무', 'Tasks')}</h1><p>{group?.type === 'PERSONAL' ? t('등록 즉시 할 일로 시작합니다.', 'New tasks start in To do immediately.') : t('새 업무는 승인 대기 상태로 시작합니다.', 'New tasks start pending approval.')}</p></div>
      <button className="primary create-action" type="button" onClick={() => setShowCreate(true)}><span aria-hidden="true">＋</span> {t('업무 만들기', 'Create task')}</button>
    </header>
    {error && <p className="error tasks-error">{error}</p>}
    <section className="tasks-layout tasks-layout-single">
      <section className="task-list-card">
        <h2>{t('업무 목록', 'Task list')} <small>{tasks.length}</small></h2>
        {tasks.length === 0 && <p className="empty-state">{t('첫 업무를 등록해 보세요.', 'Create your first task.')}</p>}
        <div className="task-list">{sortedTasks.map((task) => <article className="task-item" key={task.id}>
          <Link className="task-item-main" to={`/tasks/${task.id}`}>
            <div className="task-item-top"><span className={`task-status status-${task.status.toLowerCase()}`}>{label(statusLabels[task.status])}</span><span className={`task-priority priority-${task.priority.toLowerCase()}`}>{label(priorityLabels[task.priority])}</span>{task.delayed && <span className="task-delayed">{t('지연', 'Overdue')}</span>}</div>
            <strong>{task.title}</strong>
            <p>{task.description || t('설명 없음', 'No description')}</p>
            <div className="task-date-row"><span><b>{t('등록', 'Created')}</b>{formatDate(task.createdAt, language)}</span><span className={task.delayed ? 'overdue' : ''}><b>{t('마감', 'Due')}</b>{task.dueAt ? formatDate(task.dueAt, language) : t('미정', 'Not set')}</span></div>
          </Link>
          <div className="task-item-actions"><Link to={`/tasks/${task.id}`}>{t('업무 상세 보기', 'View task details')} →</Link>
            {task.status === 'TODO' && !task.assigneeMemberId && group?.type === 'TEAM' &&
              <button type="button" className="task-claim-button" disabled={claimingId === task.id}
                onClick={(event) => claim(event, task)}>
                {claimingId === task.id ? t('선택 중...', 'Claiming...') : t('내가 담당하기', 'Assign to me')}
              </button>}
          </div>
        </article>)}</div>
      </section>
      {showCreate && <Modal title={t('새 업무 만들기', 'Create a task')} description={t(`${group?.name ?? '그룹'}에 새로운 업무를 추가합니다.`, `Add a new task to ${group?.name ?? 'this group'}.`)} onClose={() => setShowCreate(false)}><form className="form modal-form" onSubmit={create}>
        <label className="field"><span>{t('제목', 'Title')}</span><input required maxLength={120} data-task-paste="title" value={title} onChange={(event) => setTitle(event.target.value)} placeholder={t('예: 발표 자료 초안 작성', 'e.g. Draft presentation slides')} /><small className="field-help">{t('여러 줄을 붙여넣으면 첫 줄은 제목, 나머지 줄은 체크리스트로 들어갑니다.', 'Paste multiple lines to fill the title from the first line and the checklist from the rest.')}</small></label>
        <label className="field"><span>{t('설명 (선택)', 'Description (optional)')}</span><textarea maxLength={5000} data-task-paste="description" value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        <label className="field"><span>{t('우선순위', 'Priority')}</span><select value={priority} onChange={(event) => setPriority(event.target.value as TaskPriority)}>{Object.entries(priorityLabels).map(([value, valueLabel]) => <option value={value} key={value}>{label(valueLabel)}</option>)}</select></label>
        <label className="field"><span>{t('마감 날짜·시간 (선택)', 'Due date and time (optional)')}</span><input type="datetime-local" value={dueAt} onChange={(event) => setDueAt(event.target.value)} /><small className="field-help">{t('시간이 필요한 업무는 시각까지 지정할 수 있습니다.', 'Add a specific time when the task requires one.')}</small></label>
        <ChecklistDraftField items={checklistItems} onChange={setChecklistItems} disabled={saving} />
        {pasteNotice && <p className="success-message" role="status">{pasteNotice}</p>}
        <div className="modal-actions"><button className="secondary" type="button" onClick={() => setShowCreate(false)}>{t('취소', 'Cancel')}</button><button className="primary" disabled={saving}>{saving ? t('등록 중...', 'Creating...') : t('업무 만들기', 'Create task')}</button></div>
      </form></Modal>}
    </section>
  </main></>;
}

function formatDate(value: string, language: 'ko' | 'en') {
  return new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
