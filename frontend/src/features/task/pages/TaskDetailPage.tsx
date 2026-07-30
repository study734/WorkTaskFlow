import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { commentApi, CommentResponse } from '../../../api/commentApi';
import { groupApi, GroupResponse, MemberResponse } from '../../../api/groupApi';
import { BlockerNextActionType, BlockerType, ChecklistItemResponse, ChecklistResponse, taskApi, TaskAction, TaskHistoryResponse, TaskResponse, TransitionOptions } from '../../../api/taskApi';
import { AppNavigation, Modal } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { ResourcePanel } from '../../resource/ResourcePanel';

const statusLabels: Record<TaskResponse['status'], [string, string]> = {
  REQUESTED: ['승인 대기', 'Pending approval'], TODO: ['할 일', 'To do'], IN_PROGRESS: ['진행 중', 'In progress'], ON_HOLD: ['보류', 'On hold'],
  COMPLETED: ['완료', 'Completed'], REJECTED: ['반려', 'Rejected'], CANCELLED: ['취소', 'Cancelled'],
};
const priorityLabels: Record<TaskResponse['priority'], [string, string]> = {
  LOW: ['낮음', 'Low'], NORMAL: ['보통', 'Normal'], HIGH: ['높음', 'High'], URGENT: ['긴급', 'Urgent'],
};
const blockerTypeLabels: Record<BlockerType, [string, string]> = {
  DEPENDENCY: ['선행 업무 대기', 'Waiting on another task'], DECISION: ['의사결정 대기', 'Waiting on a decision'], ACCESS: ['권한·접근 부족', 'Missing access'],
  RESOURCE: ['자원·인력 부족', 'Not enough resources'], TECHNICAL: ['기술 문제', 'Technical issue'], EXTERNAL: ['외부 요인', 'External party'], OTHER: ['기타', 'Other'],
};
const blockerNextActionLabels: Record<BlockerNextActionType, [string, string]> = {
  FOLLOW_UP: ['후속 확인', 'Follow up'], ESCALATE: ['상위 보고', 'Escalate'], DECIDE: ['결정 요청', 'Request a decision'], UNBLOCK_ACCESS: ['권한 확보', 'Get access'],
  REPLAN: ['계획 재수립', 'Replan'], WAIT_EXTERNAL: ['외부 응답 대기', 'Wait for external reply'], OTHER: ['기타', 'Other'],
};
const actionLabels: Record<TaskAction, [string, string]> = {
  ACCEPT: ['요청 승인', 'Approve request'], REJECT: ['요청 반려', 'Reject request'], START: ['업무 시작', 'Start task'], HOLD: ['업무 보류', 'Put on hold'],
  RESUME: ['업무 재개', 'Resume task'], COMPLETE: ['업무 완료', 'Complete task'], REOPEN: ['완료 업무 재개', 'Reopen completed task'], CANCEL: ['업무 취소', 'Cancel task'],
};

export function TaskDetailPage() {
  const { t, language } = useLanguage();
  const label = (value: [string, string]) => value[language === 'ko' ? 0 : 1];
  const taskId = Number(useParams().taskId);
  const [task, setTask] = useState<TaskResponse>();
  const [group, setGroup] = useState<GroupResponse>();
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [histories, setHistories] = useState<TaskHistoryResponse[]>([]);
  const [checklist, setChecklist] = useState<ChecklistResponse>();
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [newCommentContent, setNewCommentContent] = useState('');
  const [newCommentMentionIds, setNewCommentMentionIds] = useState<number[]>([]);
  const [editingCommentId, setEditingCommentId] = useState<number>();
  const [editCommentContent, setEditCommentContent] = useState('');
  const [editCommentMentionIds, setEditCommentMentionIds] = useState<number[]>([]);
  const [newChecklistContent, setNewChecklistContent] = useState('');
  const [assigneeMemberId, setAssigneeMemberId] = useState('');
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState('');
  const [editDescription, setEditDescription] = useState('');
  const [editPriority, setEditPriority] = useState<TaskResponse['priority']>('NORMAL');
  const [editDueAt, setEditDueAt] = useState('');
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [reasonAction, setReasonAction] = useState<TaskAction>();
  const [actionReason, setActionReason] = useState('');
  const [blockerType, setBlockerType] = useState<BlockerType | ''>('');
  const [blockerNextActionType, setBlockerNextActionType] = useState<BlockerNextActionType | ''>('');
  const [blockerReviewDate, setBlockerReviewDate] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    if (!Number.isInteger(taskId) || taskId < 1) {
      setError(t('올바르지 않은 업무 주소입니다.', 'This task address is invalid.'));
      setLoading(false);
      return;
    }
    taskApi.get(taskId).then(async (taskValue) => {
      const [groupValue, memberValues, historyValues, checklistValue, commentValues] = await Promise.all([
        groupApi.get(taskValue.groupId), groupApi.members(taskValue.groupId), taskApi.histories(taskId),
        taskApi.checklist(taskId), commentApi.list(taskId),
      ]);
      setTask(taskValue);
      setGroup(groupValue);
      setMembers(memberValues);
      setHistories(historyValues);
      setChecklist(checklistValue);
      setComments(commentValues);
      setAssigneeMemberId(taskValue.assigneeMemberId?.toString() ?? '');
      syncEditFields(taskValue);
    }).catch((caught) => setError(errorMessage(caught))).finally(() => setLoading(false));
  }, [taskId]);

  async function transition(action: TaskAction) {
    if (!task) return;
    if (action === 'REJECT' || action === 'HOLD' || action === 'REOPEN' || action === 'CANCEL') {
      setReasonAction(action); setActionReason(''); clearBlockerFields(); setError(''); return;
    }
    await performTransition(action);
  }

  async function performTransition(action: TaskAction, reason?: string) {
    if (!task) return;
    if (reasonAction && !reason?.trim()) { setError(t('상태 변경 사유를 입력해 주세요.', 'Enter a reason for this status change.')); return; }
    const options: TransitionOptions = { reason: reason?.trim() };
    if (action === 'HOLD') {
      if (!blockerType || !blockerNextActionType || !blockerReviewDate) {
        setError(t('보류 유형, 다음 조치, 확인 날짜를 모두 선택해 주세요.', 'Select a blocker type, next action, and review date.'));
        return;
      }
      if (blockerReviewDate < groupToday(group?.timezone)) {
        setError(t('보류 확인일은 오늘 이후여야 합니다.', 'The review date must be today or later.'));
        return;
      }
      options.blockerType = blockerType;
      options.blockerNextActionType = blockerNextActionType;
      options.blockerReviewDate = blockerReviewDate;
    }
    setPending(true);
    setError('');
    try {
      const updated = await taskApi.transition(task.id, action, task.version, options);
      setTask(updated);
      setHistories(await taskApi.histories(task.id));
      setReasonAction(undefined); setActionReason(''); clearBlockerFields();
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function assign() {
    if (!task || !assigneeMemberId) return;
    setPending(true);
    setError('');
    try {
      const updated = await taskApi.assign(task.id, Number(assigneeMemberId), task.version);
      setTask(updated);
      window.dispatchEvent(new Event('notifications:refresh'));
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function update(event: FormEvent) {
    event.preventDefault();
    if (!task) return;
    setPending(true);
    setError('');
    try {
      const updated = await taskApi.update(task.id, {
        title: editTitle,
        description: editDescription,
        priority: editPriority,
        dueAt: editDueAt || undefined,
        clearDueAt: !editDueAt,
        expectedVersion: task.version,
      });
      setTask(updated);
      syncEditFields(updated);
      setEditing(false);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function refreshChecklist() {
    if (task) setChecklist(await taskApi.checklist(task.id));
  }

  async function addChecklistItem(event: FormEvent) {
    event.preventDefault();
    if (!task || !newChecklistContent.trim()) return;
    setPending(true);
    setError('');
    try {
      await taskApi.createChecklistItem(task.id, newChecklistContent.trim());
      setNewChecklistContent('');
      await refreshChecklist();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function toggleChecklistItem(item: ChecklistItemResponse) {
    setPending(true);
    setError('');
    try {
      await taskApi.updateChecklistItem(item.id, { completed: !item.completed, expectedVersion: item.version });
      await refreshChecklist();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function editChecklistItem(item: ChecklistItemResponse) {
    const content = window.prompt(t('체크리스트 내용을 수정해 주세요.', 'Edit the checklist item.'), item.content);
    if (content === null || content.trim() === item.content) return;
    if (!content.trim()) { setError(t('체크리스트 내용을 입력해 주세요.', 'Enter checklist item text.')); return; }
    setPending(true);
    setError('');
    try {
      await taskApi.updateChecklistItem(item.id, { content: content.trim(), expectedVersion: item.version });
      await refreshChecklist();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function deleteChecklistItem(item: ChecklistItemResponse) {
    if (!window.confirm(t(`‘${item.content}’ 항목을 삭제할까요?`, `Delete “${item.content}”?`))) return;
    setPending(true);
    setError('');
    try {
      await taskApi.deleteChecklistItem(item.id, item.version);
      await refreshChecklist();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function refreshComments() {
    if (task) setComments(await commentApi.list(task.id));
  }

  async function addComment(event: FormEvent) {
    event.preventDefault();
    if (!task || !newCommentContent.trim()) return;
    setPending(true);
    setError('');
    try {
      await commentApi.create(task.id, newCommentContent.trim(), newCommentMentionIds);
      setNewCommentContent('');
      setNewCommentMentionIds([]);
      await refreshComments();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  function startEditComment(comment: CommentResponse) {
    setEditingCommentId(comment.id);
    setEditCommentContent(comment.content);
    setEditCommentMentionIds(comment.mentions.map((mention) => mention.memberId));
  }

  async function editComment(event: FormEvent, comment: CommentResponse) {
    event.preventDefault();
    if (!editCommentContent.trim()) { setError(t('댓글 내용을 입력해 주세요.', 'Enter a comment.')); return; }
    setPending(true);
    setError('');
    try {
      await commentApi.update(comment.id, editCommentContent.trim(), editCommentMentionIds, comment.version);
      await refreshComments();
      setEditingCommentId(undefined);
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function deleteComment(comment: CommentResponse) {
    if (!window.confirm(t('댓글을 삭제할까요? 삭제된 댓글의 원문은 화면에 표시되지 않습니다.', 'Delete this comment? Its original text will no longer be shown.'))) return;
    setPending(true);
    setError('');
    try {
      await commentApi.delete(comment.id, comment.version);
      await refreshComments();
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  function clearBlockerFields() {
    setBlockerType(''); setBlockerNextActionType(''); setBlockerReviewDate('');
  }

  function syncEditFields(value: TaskResponse) {
    setEditTitle(value.title);
    setEditDescription(value.description ?? '');
    setEditPriority(value.priority);
    setEditDueAt(value.dueAt?.slice(0, 16) ?? '');
  }

  if (!accessToken.get()) return <Navigate to={`/login?next=${encodeURIComponent(`/tasks/${taskId}`)}`} replace />;
  if (loading) return <main className="center-page">{t('업무를 불러오는 중...', 'Loading task...')}</main>;
  return <><AppNavigation /><main className="task-detail-page app-page"><section className="auth-card profile-card task-detail-card">
    <Link to={task ? `/groups/${task.groupId}/tasks` : '/groups'}>← {t('업무 목록으로', 'Back to tasks')}</Link>
    {error && <p className="error task-detail-error">{error}</p>}
    {task && <>
      <div className="task-detail-heading"><div className="task-item-top"><span className={`task-status status-${task.status.toLowerCase()}`}>{label(statusLabels[task.status])}</span><span className={`task-priority priority-${task.priority.toLowerCase()}`}>{label(priorityLabels[task.priority])}</span>{task.delayed && <span className="task-delayed">{t('지연', 'Overdue')}</span>}</div><h1>{task.title}</h1></div>
      <TaskWorkflow status={task.status} />
      {isTerminal(task.status) && <aside className="task-record-lock"><strong>{t('지난 기록이 잠겨 있습니다.', 'Past records are locked.')}</strong><p>{t('업무 내용, 담당자와 체크리스트는 수정하거나 삭제할 수 없습니다. 완료 업무를 다시 진행해야 한다면 팀장이 사유를 남기고 재개할 수 있습니다.', 'Task details, assignees, and checklists can no longer be edited or deleted. A leader can reopen a completed task with a reason.')}</p></aside>}
      <p className="task-description">{task.description || t('등록된 설명이 없습니다.', 'No description provided.')}</p>
      {canEdit(task, group) && <section className="task-edit-section">{editing ? <form className="form task-edit-form" onSubmit={update}>
        <label className="field"><span>{t('제목', 'Title')}</span><input required maxLength={120} value={editTitle} onChange={(event) => setEditTitle(event.target.value)} /></label>
        <label className="field"><span>{t('설명', 'Description')}</span><textarea maxLength={5000} value={editDescription} onChange={(event) => setEditDescription(event.target.value)} /></label>
        <div className="task-edit-row"><label className="field"><span>{t('우선순위', 'Priority')}</span><select value={editPriority} onChange={(event) => setEditPriority(event.target.value as TaskResponse['priority'])}>{Object.entries(priorityLabels).map(([value, valueLabel]) => <option value={value} key={value}>{label(valueLabel)}</option>)}</select></label><label className="field"><span>{t('마감일', 'Due date')}</span><input type="datetime-local" value={editDueAt} onChange={(event) => setEditDueAt(event.target.value)} /></label></div>
        <div className="task-edit-actions"><button className="primary" disabled={pending}>{t('수정 저장', 'Save changes')}</button><button className="secondary" type="button" disabled={pending} onClick={() => { syncEditFields(task); setEditing(false); }}>{t('취소', 'Cancel')}</button></div>
      </form> : <button className="secondary" type="button" disabled={pending} onClick={() => setEditing(true)}>{t('업무 내용 수정', 'Edit task')}</button>}</section>}
      <dl className="task-metadata">
        <div><dt>{t('요청자', 'Requester')}</dt><dd>{memberName(members, task.requesterMemberId, language)}</dd></div>
        <div><dt>{t('담당자', 'Assignee')}</dt><dd>{task.assigneeMemberId ? memberName(members, task.assigneeMemberId, language) : t('미지정', 'Unassigned')}</dd></div>
        <div><dt>{t('승인자', 'Approver')}</dt><dd>{task.approverMemberId ? memberName(members, task.approverMemberId, language) : t('미지정', 'Not assigned')}</dd></div>
        <div><dt>{t('마감일', 'Due date')}</dt><dd>{task.dueAt ? formatDate(task.dueAt, language) : t('없음', 'None')}</dd></div>
        <div><dt>{t('등록일', 'Created')}</dt><dd>{formatDate(task.createdAt, language)}</dd></div>
        {task.startAt && <div><dt>{t('시작일', 'Started')}</dt><dd>{formatDate(task.startAt, language)}</dd></div>}
        {task.completedAt && <div><dt>{t('완료일', 'Completed')}</dt><dd>{formatDate(task.completedAt, language)}</dd></div>}
        {task.holdReason && <div><dt>{t('보류 사유', 'Hold reason')}</dt><dd>{task.holdReason}</dd></div>}
        {task.stopReason && <div><dt>{t('종료 사유', 'Closure reason')}</dt><dd>{task.stopReason}</dd></div>}
      </dl>
      <div className="task-next-actions"><TaskActions task={task} group={group} pending={pending} onAction={transition} />
        {group?.role === 'LEADER' && task.status !== 'REQUESTED' && !isTerminal(task.status) && <section className="task-action-section"><h2>{t('다음 단계 · 담당자 지정', 'Next step · Assign owner')}</h2><div className="task-assignee-form"><select value={assigneeMemberId} onChange={(event) => setAssigneeMemberId(event.target.value)}><option value="">{t('담당자 선택', 'Select assignee')}</option>{members.map((member) => <option value={member.id} key={member.id}>{member.nickname} · {member.role === 'LEADER' ? t('팀장', 'Leader') : t('팀원', 'Member')}</option>)}</select><button className="secondary" type="button" disabled={pending || !assigneeMemberId} onClick={assign}>{t('담당자 저장', 'Save assignee')}</button></div></section>}
      </div>
      <ChecklistSection
        checklist={checklist}
        writable={canWriteChecklist(task, group)}
        pending={pending}
        newContent={newChecklistContent}
        onNewContent={setNewChecklistContent}
        onAdd={addChecklistItem}
        onToggle={toggleChecklistItem}
        onEdit={editChecklistItem}
        onDelete={deleteChecklistItem}
      />
      <CommentSection
        comments={comments}
        members={members}
        currentMemberId={group?.memberId}
        pending={pending}
        newContent={newCommentContent}
        newMentionIds={newCommentMentionIds}
        editingCommentId={editingCommentId}
        editContent={editCommentContent}
        editMentionIds={editCommentMentionIds}
        onNewContent={setNewCommentContent}
        onNewMentionIds={setNewCommentMentionIds}
        onAdd={addComment}
        onStartEdit={startEditComment}
        onEdit={editComment}
        onEditContent={setEditCommentContent}
        onEditMentionIds={setEditCommentMentionIds}
        onCancelEdit={() => setEditingCommentId(undefined)}
        onDelete={deleteComment}
        recordLocked={isTerminal(task.status)}
      />
      <ResourcePanel groupId={task.groupId} taskId={task.id} />
      <section className="task-action-section"><h2>{t('상태 이력', 'Status history')}</h2><div className="task-history-list">{histories.map((history) => <div className="task-history-item" key={history.id}><span className="task-history-dot" /><div><strong>{history.fromStatus ? `${label(statusLabels[history.fromStatus])} → ` : ''}{label(statusLabels[history.toStatus])}</strong><small>{t(`멤버 #${history.changedByMemberId}`, `Member #${history.changedByMemberId}`)} · {formatDate(history.createdAt, language)}</small>{history.reason && <p>{history.reason}</p>}</div></div>)}</div></section>
    </>}
  </section>{reasonAction && <Modal
    title={label(actionLabels[reasonAction])}
    description={reasonAction === 'HOLD'
      ? t('업무 이력에 남을 사유와 보류 상황을 입력해 주세요.', 'Describe the hold and record its blocker details for the task history.')
      : t('업무 이력에 남을 사유를 입력해 주세요.', 'Enter a reason to keep in the task history.')}
    onClose={() => { setReasonAction(undefined); setActionReason(''); clearBlockerFields(); setError(''); }}
  ><form className="form modal-form" onSubmit={(event) => { event.preventDefault(); void performTransition(reasonAction, actionReason); }}>
    <label className="field"><span>{t('사유', 'Reason')}</span><textarea autoFocus required maxLength={500} value={actionReason} onChange={(event) => setActionReason(event.target.value)} placeholder={t('팀원이 이해할 수 있도록 간단히 적어주세요.', 'Add a short explanation for the team.')} /></label>
    {reasonAction === 'HOLD' && <>
      <div className="form-row">
        <label className="field"><span>{t('보류 유형', 'Blocker type')}</span><select required value={blockerType} onChange={(event) => setBlockerType(event.target.value as BlockerType)}><option value="">{t('선택해 주세요', 'Select one')}</option>{Object.entries(blockerTypeLabels).map(([value, valueLabel]) => <option value={value} key={value}>{label(valueLabel)}</option>)}</select></label>
        <label className="field"><span>{t('다음 조치', 'Next action')}</span><select required value={blockerNextActionType} onChange={(event) => setBlockerNextActionType(event.target.value as BlockerNextActionType)}><option value="">{t('선택해 주세요', 'Select one')}</option>{Object.entries(blockerNextActionLabels).map(([value, valueLabel]) => <option value={value} key={value}>{label(valueLabel)}</option>)}</select></label>
      </div>
      <label className="field"><span>{t('확인 날짜', 'Review date')}</span><input type="date" required min={groupToday(group?.timezone)} value={blockerReviewDate} onChange={(event) => setBlockerReviewDate(event.target.value)} /></label>
      <p className="field-help">{t('보류 유형·다음 조치·확인 날짜는 AI 주간 리포트의 근거로 사용됩니다. 자유 입력 사유는 팀에게만 표시되고 AI에 전송되지 않습니다.', 'The blocker type, next action, and review date are used as evidence in the AI weekly report. The free-text reason stays with your team and is never sent to the AI.')}</p>
    </>}
    {error && <p className="error">{error}</p>}
    <div className="modal-actions"><button className="secondary" type="button" onClick={() => { setReasonAction(undefined); setActionReason(''); clearBlockerFields(); setError(''); }}>{t('돌아가기', 'Back')}</button><button className="danger" disabled={pending || !actionReason.trim() || (reasonAction === 'HOLD' && (!blockerType || !blockerNextActionType || !blockerReviewDate))}>{pending ? t('처리 중...', 'Processing...') : label(actionLabels[reasonAction])}</button></div>
  </form></Modal>}</main></>;
}

function TaskWorkflow({ status }: { status: TaskResponse['status'] }) {
  const { t, language } = useLanguage();
  const label = (value: [string, string]) => value[language === 'ko' ? 0 : 1];
  const steps: TaskResponse['status'][] = ['REQUESTED', 'TODO', 'IN_PROGRESS', 'COMPLETED'];
  const effective = status === 'ON_HOLD' ? 'IN_PROGRESS' : status;
  const activeIndex = steps.indexOf(effective);
  if (status === 'REJECTED' || status === 'CANCELLED') return <div className="task-workflow stopped"><span>{t('요청', 'Request')}</span><span>{t(`업무가 ${label(statusLabels[status])}되었습니다`, `Task ${label(statusLabels[status]).toLowerCase()}`)}</span></div>;
  return <div className="task-workflow">{steps.map((step, index) => <div className={index < activeIndex ? 'done' : index === activeIndex ? 'active' : ''} key={step}><i>{index < activeIndex ? '✓' : index + 1}</i><span>{step === 'REQUESTED' ? t('승인 대기', 'Pending approval') : status === 'ON_HOLD' && step === 'IN_PROGRESS' ? t('보류 중', 'On hold') : label(statusLabels[step])}</span></div>)}</div>;
}

function memberName(members: MemberResponse[], memberId: number, language: 'ko' | 'en') { return members.find((member) => member.id === memberId)?.nickname ?? (language === 'ko' ? `멤버 #${memberId}` : `Member #${memberId}`); }

function CommentSection({ comments, members, currentMemberId, pending, newContent, newMentionIds, recordLocked,
  editingCommentId, editContent, editMentionIds, onNewContent, onNewMentionIds, onAdd,
  onStartEdit, onEdit, onEditContent, onEditMentionIds, onCancelEdit, onDelete }: {
  comments: CommentResponse[];
  members: MemberResponse[];
  currentMemberId?: number;
  recordLocked: boolean;
  pending: boolean;
  newContent: string;
  newMentionIds: number[];
  editingCommentId?: number;
  editContent: string;
  editMentionIds: number[];
  onNewContent: (value: string) => void;
  onNewMentionIds: (value: number[]) => void;
  onAdd: (event: FormEvent) => void;
  onStartEdit: (comment: CommentResponse) => void;
  onEdit: (event: FormEvent, comment: CommentResponse) => void;
  onEditContent: (value: string) => void;
  onEditMentionIds: (value: number[]) => void;
  onCancelEdit: () => void;
  onDelete: (comment: CommentResponse) => void;
}) {
  const { t, language } = useLanguage();
  return <section className="task-action-section task-comments-section">
    <div className="task-section-heading"><h2>{t('댓글', 'Comments')}</h2><strong>{comments.length}</strong></div>
    {comments.length === 0 ? <p className="task-checklist-empty">{t('아직 댓글이 없습니다.', 'No comments yet.')}</p> : <div className="task-comment-list">
      {comments.map((comment) => <article className={`task-comment${comment.deleted ? ' deleted' : ''}`} key={comment.id}>
        <header><div><strong>{comment.authorNickname}</strong><small>{t(`멤버 #${comment.authorMemberId}`, `Member #${comment.authorMemberId}`)}</small></div><time dateTime={comment.createdAt}>{formatDate(comment.createdAt, language)}</time></header>
        {editingCommentId === comment.id && !comment.recordLocked ? <form className="task-comment-edit-form" onSubmit={(event) => onEdit(event, comment)}>
          <textarea required maxLength={2000} value={editContent} onChange={(event) => onEditContent(event.target.value)} />
          <MentionPicker members={members} currentMemberId={currentMemberId} selectedIds={editMentionIds} onChange={onEditMentionIds} />
          <div><button className="primary" disabled={pending || !editContent.trim()}>{t('수정 저장', 'Save changes')}</button><button className="secondary" type="button" disabled={pending} onClick={onCancelEdit}>{t('취소', 'Cancel')}</button></div>
        </form> : <>
          <p>{comment.content}</p>
          {!comment.deleted && comment.mentions.length > 0 && <div className="task-comment-mentions">{comment.mentions.map((mention) => <span key={mention.id}>@{mention.nickname}</span>)}</div>}
          <footer>{comment.updatedAt && !comment.deleted && <small>{t('수정됨', 'Edited')}</small>}{comment.recordLocked && !comment.deleted && <small>{t('기록 잠김', 'Record locked')}</small>}{comment.authorMemberId === currentMemberId && !comment.deleted && !comment.recordLocked && !recordLocked && withinCommentEditWindow(comment.createdAt) && <div><button type="button" disabled={pending} onClick={() => onStartEdit(comment)}>{t('수정', 'Edit')}</button><button className="danger" type="button" disabled={pending} onClick={() => onDelete(comment)}>{t('삭제', 'Delete')}</button></div>}</footer>
        </>}
      </article>)}
    </div>}
    {recordLocked && <p className="task-record-note">{t('기존 기록은 잠겨 있습니다. 아래에는 수정할 수 없는 후속 기록으로 추가됩니다.', 'Existing records are locked. New comments below are added as immutable follow-up records.')}</p>}
    <form className="task-comment-form" onSubmit={onAdd}><textarea aria-label={t('새 댓글 내용', 'New comment')} required maxLength={2000} placeholder={recordLocked ? t('완료 후 회고나 정정 내용을 새 기록으로 남겨 주세요.', 'Add a retrospective or correction as a new record.') : t('@닉네임을 입력하거나 아래에서 멤버를 선택해 주세요.', 'Type @nickname or select a member below.')} value={newContent} onChange={(event) => onNewContent(event.target.value)} /><MentionPicker members={members} currentMemberId={currentMemberId} selectedIds={newMentionIds} onChange={onNewMentionIds} /><button className="primary" disabled={pending || !newContent.trim()}>{recordLocked ? t('후속 기록 추가', 'Add follow-up record') : t('댓글 등록', 'Post comment')}</button></form>
  </section>;
}

function MentionPicker({ members, currentMemberId, selectedIds, onChange }: {
  members: MemberResponse[];
  currentMemberId?: number;
  selectedIds: number[];
  onChange: (value: number[]) => void;
}) {
  const { t } = useLanguage();
  const mentionableMembers = members.filter((member) => member.id !== currentMemberId);
  return <fieldset className="task-mention-picker"><legend>{t('멘션할 멤버', 'Mention members')} <small>{t('@닉네임 입력도 자동 인식됩니다', '@nickname is detected automatically')}</small></legend><div>{mentionableMembers.map((member) => <label key={member.id}><input type="checkbox" checked={selectedIds.includes(member.id)} onChange={() => onChange(selectedIds.includes(member.id) ? selectedIds.filter((id) => id !== member.id) : [...selectedIds, member.id])} /><span>@{member.nickname}</span></label>)}</div></fieldset>;
}

function ChecklistSection({ checklist, writable, pending, newContent, onNewContent, onAdd, onToggle, onEdit, onDelete }: {
  checklist?: ChecklistResponse;
  writable: boolean;
  pending: boolean;
  newContent: string;
  onNewContent: (value: string) => void;
  onAdd: (event: FormEvent) => void;
  onToggle: (item: ChecklistItemResponse) => void;
  onEdit: (item: ChecklistItemResponse) => void;
  onDelete: (item: ChecklistItemResponse) => void;
}) {
  const { t } = useLanguage();
  return <section className="task-action-section task-checklist-section">
    <div className="task-section-heading"><h2>{t('체크리스트', 'Checklist')}</h2>{checklist && checklist.totalCount > 0 && <strong>{checklist.completedCount}/{checklist.totalCount} · {checklist.progressPercent}%</strong>}</div>
    {!checklist || checklist.totalCount === 0 ? <p className="task-checklist-empty">{t('체크리스트 없음', 'No checklist items')}</p> : <>
      <div className="task-progress" aria-label={t(`체크리스트 진행률 ${checklist.progressPercent}%`, `Checklist progress ${checklist.progressPercent}%`)}><span style={{ width: `${checklist.progressPercent}%` }} /></div>
      <div className="task-checklist-list">{checklist.items.map((item) => <div className={`task-checklist-item${item.completed ? ' completed' : ''}`} key={item.id}>
        <label><input type="checkbox" checked={item.completed} disabled={!writable || pending} onChange={() => onToggle(item)} /><span>{item.content}</span></label>
        {writable && <div className="task-checklist-actions"><button type="button" disabled={pending} onClick={() => onEdit(item)}>{t('수정', 'Edit')}</button><button className="danger" type="button" disabled={pending} onClick={() => onDelete(item)}>{t('삭제', 'Delete')}</button></div>}
      </div>)}</div>
    </>}
    {writable ? <form className="task-checklist-form" onSubmit={onAdd}><input aria-label={t('새 체크리스트 내용', 'New checklist item')} maxLength={300} placeholder={t('새 체크리스트 항목', 'New checklist item')} value={newContent} onChange={(event) => onNewContent(event.target.value)} /><button className="secondary" disabled={pending || !newContent.trim()}>{t('추가', 'Add')}</button></form> : <small className="task-checklist-readonly">{t('담당자 또는 팀장만 변경할 수 있습니다.', 'Only the assignee or a leader can make changes.')}</small>}
  </section>;
}

function TaskActions({ task, group, pending, onAction }: {
  task: TaskResponse; group?: GroupResponse; pending: boolean; onAction: (action: TaskAction) => void;
}) {
  const { t, language } = useLanguage();
  const label = (value: [string, string]) => value[language === 'ko' ? 0 : 1];
  if (!group) return null;
  if (task.status === 'COMPLETED') {
    const canReopen = group.type === 'PERSONAL' || group.role === 'LEADER';
    return canReopen ? <section className="task-action-section"><h2>{t('완료 이후', 'After completion')}</h2><div className="task-action-buttons"><button className="secondary" type="button" disabled={pending} onClick={() => onAction('REOPEN')}>{t('완료 업무 재개', 'Reopen completed task')}</button></div></section> : null;
  }
  if (isTerminal(task.status)) return null;
  const leader = group.role === 'LEADER';
  const assignee = task.assigneeMemberId === group.memberId;
  const requester = task.requesterMemberId === group.memberId;
  const actions: TaskAction[] = [];
  if (task.status === 'REQUESTED' && leader) actions.push('ACCEPT', 'REJECT');
  if (task.status === 'TODO' && assignee) actions.push('START');
  if (task.status === 'IN_PROGRESS' && assignee) actions.push('HOLD', 'COMPLETE');
  if (task.status === 'ON_HOLD' && assignee) actions.push('RESUME');
  if (leader || (requester && task.status === 'REQUESTED')) actions.push('CANCEL');
  if (actions.length === 0) return null;
  return <section className="task-action-section"><h2>{t('상태 변경', 'Change status')}</h2><div className="task-action-buttons">{actions.map((action) => <button className={action === 'REJECT' || action === 'CANCEL' ? 'task-danger-action' : 'secondary'} type="button" disabled={pending} onClick={() => onAction(action)} key={action}>{label(actionLabels[action])}</button>)}</div></section>;
}

function isTerminal(status: TaskResponse['status']) {
  return status === 'COMPLETED' || status === 'REJECTED' || status === 'CANCELLED';
}

function canEdit(task: TaskResponse, group?: GroupResponse) {
  if (!group || isTerminal(task.status)) return false;
  if (group.type === 'PERSONAL') return true;
  return task.status === 'REQUESTED'
    && (group.role === 'LEADER' || task.requesterMemberId === group.memberId);
}

function canWriteChecklist(task: TaskResponse, group?: GroupResponse) {
  return Boolean(group && !isTerminal(task.status)
    && (group.role === 'LEADER' || task.assigneeMemberId === group.memberId));
}

function withinCommentEditWindow(createdAt: string) {
  return Date.now() - new Date(createdAt).getTime() <= 15 * 60 * 1000;
}

function groupToday(timezone?: string) {
  const options: Intl.DateTimeFormatOptions = { year: 'numeric', month: '2-digit', day: '2-digit' };
  try {
    return new Intl.DateTimeFormat('en-CA', { ...options, timeZone: timezone }).format(new Date());
  } catch {
    return new Intl.DateTimeFormat('en-CA', options).format(new Date());
  }
}

function formatDate(value: string, language: 'ko' | 'en') {
  return new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
