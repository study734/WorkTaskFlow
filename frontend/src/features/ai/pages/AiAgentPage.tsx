import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { aiApi, AgentHealth, PendingApproval } from '../../../api/aiApi';
import { errorMessage } from '../../../api/client';
import { groupApi, GroupResponse } from '../../../api/groupApi';
import { AppNavigation } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';

type Turn =
  | { role: 'user'; text: string }
  | { role: 'agent'; text: string }
  | { role: 'notice'; text: string };

export function AiAgentPage() {
  const { t } = useLanguage();
  const groupId = Number(useParams().groupId);
  const [group, setGroup] = useState<GroupResponse>();
  const [health, setHealth] = useState<AgentHealth>();
  const [turns, setTurns] = useState<Turn[]>([]);
  const [message, setMessage] = useState('');
  const [threadId, setThreadId] = useState<string>();
  const [pending, setPending] = useState<PendingApproval>();
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [indexMessage, setIndexMessage] = useState('');
  const transcript = useRef<HTMLDivElement>(null);

  useEffect(() => {
    groupApi.get(groupId).then(setGroup).catch((value) => setError(errorMessage(value)));
    aiApi.health().then(setHealth).catch(() => setHealth({ status: 'disabled', enabled: false, missing: [] }));
  }, [groupId]);
  // 새 답변은 아래에 쌓이므로 매번 끝으로 내린다.
  useEffect(() => { transcript.current?.scrollTo({ top: transcript.current.scrollHeight }); }, [turns, pending]);

  function apply(result: Awaited<ReturnType<typeof aiApi.chat>>) {
    setThreadId(result.threadId);
    if (result.status === 'awaiting_approval' && result.pending) {
      setPending(result.pending);
      setNote('');
      return;
    }
    setPending(undefined);
    setTurns((current) => [...current, { role: 'agent', text: result.reply || t('답변이 비어 있습니다.', 'The reply was empty.') }]);
  }

  // submit과 Enter 키 양쪽에서 부른다. 둘의 이벤트 타입이 다르므로 필요한 것만 받는다.
  async function send(event: { preventDefault: () => void }) {
    event.preventDefault();
    const text = message.trim();
    if (!text || busy || pending) return;
    setBusy(true); setError(''); setMessage('');
    setTurns((current) => [...current, { role: 'user', text }]);
    try { apply(await aiApi.chat(groupId, text, threadId)); }
    catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  async function decide(approved: boolean) {
    if (!pending || !threadId || busy) return;
    setBusy(true); setError('');
    setTurns((current) => [...current, {
      role: 'notice',
      text: approved ? t('승인했습니다.', 'Approved.') : t('거절했습니다.', 'Rejected.') + (note ? ` (${note})` : ''),
    }]);
    try { apply(await aiApi.resume(groupId, threadId, approved, note)); }
    catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  async function reindex() {
    if (busy) return;
    setBusy(true); setError(''); setIndexMessage('');
    try {
      const result = await aiApi.reindex(groupId);
      setIndexMessage(t(
        `색인 완료: ${result.indexed}건 반영, ${result.skipped}건 변경 없음, ${result.removed}건 삭제, ${result.unsupported}건 미지원.`,
        `Indexed ${result.indexed}, unchanged ${result.skipped}, removed ${result.removed}, unsupported ${result.unsupported}.`));
      if (result.failures.length > 0) setError(result.failures.join('\n'));
    } catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  function reset() {
    setTurns([]); setThreadId(undefined); setPending(undefined); setNote(''); setError('');
  }

  const disabled = health !== undefined && !health.enabled;

  return <><AppNavigation /><main className="ai-agent-page app-page">
    <header className="ai-agent-header">
      <div>
        <span className="page-eyebrow">AI AGENT</span>
        <h1>{t('업무 비서', 'Work assistant')}</h1>
        <p>{t('업무를 자연어로 묻고 바꿉니다. 바꾸는 작업은 실행 전에 승인을 받습니다.',
          'Ask about work in plain language. Any change asks for your approval before it runs.')}</p>
      </div>
      <div className="ai-agent-header-actions">
        {group && <Link className="secondary" to={`/groups/${groupId}/dashboard`}>{t('대시보드', 'Dashboard')}</Link>}
        {group?.role === 'LEADER' && <button type="button" className="secondary" disabled={busy || disabled} onClick={reindex}>
          {t('자료 다시 색인', 'Reindex documents')}
        </button>}
        <button type="button" className="secondary" disabled={busy || turns.length === 0} onClick={reset}>
          {t('대화 새로 시작', 'New conversation')}
        </button>
      </div>
    </header>

    {disabled && <p className="ai-agent-disabled">{t(
      `AI 기능이 꺼져 있습니다.${health?.missing.length ? ` 필요한 설정: ${health.missing.join(', ')}` : ''}`,
      `The AI feature is off.${health?.missing.length ? ` Missing settings: ${health.missing.join(', ')}` : ''}`)}</p>}
    {indexMessage && <p className="success-message">{indexMessage}</p>}

    <section className="ai-agent-transcript" ref={transcript} aria-live="polite" aria-label={t('대화 내용', 'Conversation')}>
      {turns.length === 0 && !pending && <p className="empty-state">{t(
        '예: "이번 주에 내가 맡은 업무 알려줘", "배포 절차서에서 롤백 방법 찾아줘", "12번 업무 진행 중으로 바꿔줘"',
        'Try: "What am I assigned this week?", "Find the rollback steps in the deployment guide", "Move task 12 to in progress"')}</p>}
      {turns.map((turn, index) => <article className={`ai-turn ai-turn-${turn.role}`} key={index}>
        <span className="ai-turn-role">{turn.role === 'user' ? t('나', 'You') : turn.role === 'agent' ? 'AI' : t('알림', 'Note')}</span>
        <p>{turn.text}</p>
      </article>)}
      {busy && <p className="ai-turn-busy">{t('생각하는 중...', 'Thinking...')}</p>}
    </section>

    {pending && <section className="ai-approval" role="alertdialog" aria-label={t('승인 요청', 'Approval request')}>
      <h2>{t('승인이 필요합니다', 'Approval needed')}</h2>
      <p className="ai-approval-summary">{pending.summary}</p>
      <dl className="ai-approval-details">
        <div><dt>{t('작업', 'Action')}</dt><dd>{pending.action}</dd></div>
        {Object.entries(pending.details ?? {}).filter(([, value]) => value !== null && value !== undefined && value !== '')
          .map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{String(value)}</dd></div>)}
      </dl>
      <label className="ai-approval-note">
        <span>{t('메모 (선택)', 'Note (optional)')}</span>
        <input value={note} maxLength={500} disabled={busy} onChange={(event) => setNote(event.target.value)}
          placeholder={t('거절하는 이유를 남길 수 있습니다.', 'You can leave a reason when rejecting.')} />
      </label>
      <div className="ai-approval-actions">
        <button type="button" className="primary" disabled={busy} onClick={() => decide(true)}>{t('승인하고 실행', 'Approve and run')}</button>
        <button type="button" className="secondary" disabled={busy} onClick={() => decide(false)}>{t('거절', 'Reject')}</button>
      </div>
    </section>}

    {error && <p className="error">{error}</p>}

    <form className="ai-agent-composer" onSubmit={send}>
      <label className="sr-only" htmlFor="ai-agent-message">{t('보낼 내용', 'Message')}</label>
      <textarea id="ai-agent-message" value={message} maxLength={2000} rows={2} disabled={busy || disabled || Boolean(pending)}
        placeholder={pending ? t('먼저 위 요청을 승인하거나 거절해 주세요.', 'Approve or reject the request above first.') : t('무엇을 도와드릴까요?', 'How can I help?')}
        onChange={(event) => setMessage(event.target.value)}
        onKeyDown={(event) => { if (event.key === 'Enter' && !event.shiftKey) { event.preventDefault(); send(event); } }} />
      <button type="submit" className="primary" disabled={busy || disabled || Boolean(pending) || message.trim() === ''}>
        {busy ? t('보내는 중...', 'Sending...') : t('보내기', 'Send')}
      </button>
    </form>
  </main></>;
}
