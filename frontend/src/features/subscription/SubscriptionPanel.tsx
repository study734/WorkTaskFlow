import { FormEvent, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { errorMessage } from '../../api/client';
import { PaymentMethod, paymentApi } from '../../api/paymentApi';
import { reportApi, ReportSchedule } from '../../api/reportApi';
import { Subscription, subscriptionApi } from '../../api/subscriptionApi';
import { useLanguage } from '../../app/LanguageContext';

export function SubscriptionPanel({ groupId }: { groupId: number }) {
  const { t, language } = useLanguage();
  const [subscription, setSubscription] = useState<Subscription>();
  const [schedule, setSchedule] = useState<ReportSchedule>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [paymentMethodId, setPaymentMethodId] = useState<number>();
  const [billingConsent, setBillingConsent] = useState(false);
  const [policyConsent, setPolicyConsent] = useState(false);
  const [recipientEmail, setRecipientEmail] = useState('');
  const [editingRecipient, setEditingRecipient] = useState(false);
  useEffect(() => {
    Promise.all([subscriptionApi.get(groupId), reportApi.schedule(groupId), paymentApi.methods()])
      .then(([value, report, paymentMethods]) => {
        setSubscription(value); setSchedule(report); setRecipientEmail(report.recipientEmail);
        const activeMethods = paymentMethods.filter((method) => method.status === 'ACTIVE');
        setMethods(activeMethods); setPaymentMethodId(activeMethods[0]?.id);
      })
      .catch((value) => setError(errorMessage(value)));
  }, [groupId]);
  async function trial() {
    setPending(true); setError('');
    try { setSubscription(await subscriptionApi.trial(groupId)); setMessage(t('30일 무료 체험을 시작했습니다.', '30-day trial started.')); }
    catch (value) { setError(errorMessage(value)); } finally { setPending(false); }
  }
  async function choose(choice: 'KEEP_FREE' | 'CONTINUE_PAID') {
    setPending(true); setError('');
    try { setSubscription(await subscriptionApi.choose(groupId, choice)); setMessage(t('전환 선택을 저장했습니다.', 'Conversion choice saved.')); }
    catch (value) { setError(errorMessage(value)); } finally { setPending(false); }
  }
  async function cancel() {
    if (!window.confirm(t('현재 이용 기간 종료 후 구독을 해지할까요?', 'Cancel at the end of the current period?'))) return;
    setPending(true);
    try { setSubscription(await subscriptionApi.cancel(groupId)); }
    catch (value) { setError(errorMessage(value)); } finally { setPending(false); }
  }
  async function activate() {
    if (!paymentMethodId || !billingConsent || !policyConsent) return;
    if (!window.confirm(t(`월 ₩${subscription?.amount.toLocaleString()} 자동결제를 시작할까요?`, `Start recurring billing at KRW ${subscription?.amount.toLocaleString()} per month?`))) return;
    setPending(true); setError(''); setMessage('');
    try {
      setSubscription(await subscriptionApi.activate(groupId, paymentMethodId));
      setMessage(t('유료 구독이 활성화됐습니다.', 'Paid subscription activated.'));
    } catch (value) { setError(errorMessage(value)); } finally { setPending(false); }
  }
  async function saveSchedule(event: FormEvent) {
    event.preventDefault(); if (!schedule) return;
    const nextRecipientEmail = recipientEmail.trim();
    if (nextRecipientEmail !== schedule.recipientEmail
        && !window.confirm(t(`앞으로 리포트를 ${nextRecipientEmail}(으)로 받을까요?`, `Send future reports to ${nextRecipientEmail}?`))) return;
    setPending(true); setError('');
    try {
      const updated = await reportApi.updateSchedule(groupId, {
        recipientEmail: nextRecipientEmail, weeklyEnabled: schedule.weeklyEnabled,
        weeklyDay: schedule.weeklyDay, monthlyEnabled: schedule.monthlyEnabled,
        monthlyDay: schedule.monthlyDay, language: schedule.language,
      });
      setSchedule(updated); setRecipientEmail(updated.recipientEmail); setEditingRecipient(false);
      setMessage(t('리포트 메일 일정을 저장했습니다.', 'Report email schedule saved.'));
    } catch (value) { setError(errorMessage(value)); } finally { setPending(false); }
  }
  if (!subscription || !schedule) return <section className="subscription-panel"><p>{t('구독 정보를 불러오는 중...', 'Loading subscription...')}</p>{error && <p className="error">{error}</p>}</section>;
  return <section className="subscription-panel group-subsection"><header className="group-section-heading"><div><span className="page-eyebrow">SUBSCRIPTION</span><h2>{t('구독과 자동 리포트', 'Subscription & scheduled reports')}</h2><p>{t('무료 체험과 운영 전환 선택, 주간·월간 리포트 메일을 관리합니다.', 'Manage trials, conversion, and weekly/monthly report emails.')}</p></div><span className={`membership-badge ${subscription.status.toLowerCase()}`}>{subscriptionStatus(subscription.status, language)}</span></header>
    {subscription.rolloutNoticeAt && <aside className="policy-notice"><strong>{t('유료 전환 사전 안내', 'Paid rollout notice')}</strong><p>{t(`${subscription.decisionDeadline?.slice(0, 10)}까지 무료 유지 또는 유료 전환을 선택해 주세요. 선택하지 않으면 무료 상태가 유지되며 데이터는 삭제되지 않습니다.`, `Choose free or paid by ${subscription.decisionDeadline?.slice(0, 10)}. No choice keeps the group free and does not delete data.`)}</p><div><button type="button" className="secondary" disabled={pending} onClick={() => choose('KEEP_FREE')}>{t('무료 유지', 'Keep free')}</button><button type="button" className="primary" disabled={pending} onClick={() => choose('CONTINUE_PAID')}>{t('유료 전환 희망', 'Continue paid')}</button></div></aside>}
    <div className="subscription-summary"><dl><div><dt>{t('월 구독 예정가', 'Planned monthly price')}</dt><dd>₩{subscription.amount.toLocaleString()}</dd></div><div><dt>{t('현재 기간 종료', 'Current period ends')}</dt><dd>{subscription.currentPeriodEnd?.slice(0, 10) ?? '-'}</dd></div><div><dt>{t('다음 결제', 'Next billing')}</dt><dd>{subscription.nextBillingAt?.slice(0, 10) ?? '-'}</dd></div></dl><div>
      {subscription.canStartTrial && <button className="primary" type="button" disabled={pending} onClick={trial}>{t('30일 무료 체험 시작', 'Start 30-day trial')}</button>}
      {subscription.status === 'ACTIVE' && <button className="secondary" type="button" disabled={pending} onClick={cancel}>{t('기간 종료 시 해지', 'Cancel at period end')}</button>}
      <Link className="secondary" to="/payments">{t('결제수단 관리', 'Payment methods')}</Link>
      {!subscription.liveBillingEnabled && <small>{t('실결제는 사업자·통신판매업 및 PG 운영 승인이 끝날 때까지 잠겨 있습니다.', 'Live billing stays locked until business and PG approval.')}</small>}
    </div></div>
    {subscription.liveBillingEnabled && !['ACTIVE', 'CANCEL_AT_PERIOD_END'].includes(subscription.status) && <div className="subscription-activation"><h3>{t('유료 구독 시작', 'Start paid subscription')}</h3>
      {methods.length === 0 ? <p>{t('먼저 결제수단을 등록해 주세요.', 'Add a payment method first.')} <Link to="/payments">{t('결제수단 등록', 'Add payment method')} →</Link></p> : <>
        <label>{t('결제수단', 'Payment method')}<select value={paymentMethodId ?? ''} onChange={(event) => setPaymentMethodId(Number(event.target.value))}>{methods.map((method) => <option value={method.id} key={method.id}>{method.maskedNumber || t('등록된 카드', 'Saved card')}</option>)}</select></label>
        <label><input type="checkbox" checked={billingConsent} onChange={(event) => setBillingConsent(event.target.checked)} /> {t(`월 ₩${subscription.amount.toLocaleString()} 정기결제와 자동 갱신에 동의합니다.`, `I agree to recurring monthly billing of KRW ${subscription.amount.toLocaleString()} and automatic renewal.`)}</label>
        <label><input type="checkbox" checked={policyConsent} onChange={(event) => setPolicyConsent(event.target.checked)} /> <Link to="/paid-terms" target="_blank">{t('유료서비스 약관', 'Paid terms')}</Link> · <Link to="/refund-policy" target="_blank">{t('환불 정책', 'Refund policy')}</Link>{t('에 동의합니다.', ' accepted.')}</label>
        <button className="primary" type="button" disabled={pending || !paymentMethodId || !billingConsent || !policyConsent || subscription.conversionChoice !== 'CONTINUE_PAID'} onClick={activate}>{t('유료 구독 시작', 'Start paid subscription')}</button>
        {subscription.conversionChoice !== 'CONTINUE_PAID' && <small>{t('위의 “유료 전환 희망”을 먼저 선택해 주세요.', 'Choose “Continue paid” above first.')}</small>}
      </>}
    </div>}
    <form className="report-schedule-form" onSubmit={saveSchedule}><header><div><span className="page-eyebrow">EMAIL REPORT</span><h3>{t('메일 리포트 일정', 'Email report schedule')}</h3><p>{t('원하는 주기와 언어로 팀 업무 요약을 받아보세요.', 'Receive team summaries on your preferred schedule and language.')}</p></div><span className="report-schedule-state">{schedule.weeklyEnabled || schedule.monthlyEnabled ? t('발송 설정됨', 'Scheduled') : t('발송 꺼짐', 'Disabled')}</span></header>
      <label className="report-cycle-toggle"><span><input type="checkbox" checked={schedule.weeklyEnabled} disabled={!schedule.weeklyEligible} onChange={(event) => setSchedule({ ...schedule, weeklyEnabled: event.target.checked })} /><i /></span><strong>{t('주간 리포트', 'Weekly report')}</strong><small>{t('선택한 요일 오전에 발송', 'Delivered in the morning on your chosen day')}</small></label>
      <label className="report-select-field"><span>{t('발송 요일', 'Delivery day')}</span><select value={schedule.weeklyDay ?? 'MONDAY'} disabled={!schedule.weeklyEnabled} onChange={(event) => setSchedule({ ...schedule, weeklyDay: event.target.value })}>{weekdays.map(([value, ko, en]) => <option value={value} key={value}>{t(ko, en)}</option>)}</select></label>
      {!schedule.weeklyEligible && <small>{t(`${schedule.weeklyMinimumDays}일 이상 사용 후 설정할 수 있습니다.`, `Available after ${schedule.weeklyMinimumDays} days of use.`)}</small>}
      <label className="report-cycle-toggle"><span><input type="checkbox" checked={schedule.monthlyEnabled} disabled={!schedule.monthlyEligible} onChange={(event) => setSchedule({ ...schedule, monthlyEnabled: event.target.checked })} /><i /></span><strong>{t('월간 리포트', 'Monthly report')}</strong><small>{t('매월 선택한 날짜에 발송', 'Delivered monthly on your chosen date')}</small></label>
      <label className="report-select-field"><span>{t('발송일', 'Delivery date')}</span><select value={schedule.monthlyDay ?? 1} disabled={!schedule.monthlyEnabled} onChange={(event) => setSchedule({ ...schedule, monthlyDay: Number(event.target.value) })}>{Array.from({ length: 28 }, (_, index) => index + 1).map((day) => <option value={day} key={day}>{t(`${day}일`, `Day ${day}`)}</option>)}</select></label>
      {!schedule.monthlyEligible && <small>{t(`${schedule.monthlyMinimumDays}일 이상 사용 후 설정할 수 있습니다.`, `Available after ${schedule.monthlyMinimumDays} days of use.`)}</small>}
      <label className="report-select-field report-language-field"><span>{t('리포트 언어', 'Report language')}</span><select value={schedule.language} onChange={(event) => setSchedule({ ...schedule, language: event.target.value as ReportSchedule['language'] })}><option value="KO">한국어</option><option value="EN">English</option><option value="BOTH">{t('한글 + 영문', 'Korean + English')}</option></select><small>{t('두 언어를 선택하면 각각 한 부씩 발송합니다.', 'Both sends one copy in each language.')}</small></label>
      <div className={`report-email-field ${editingRecipient ? 'editing' : ''}`}><div><span>{t('수신 이메일', 'Recipient email')}</span>{editingRecipient ? <button type="button" onClick={() => { setRecipientEmail(schedule.recipientEmail); setEditingRecipient(false); }}>{t('취소', 'Cancel')}</button> : <button type="button" onClick={() => setEditingRecipient(true)}>{t('수정', 'Edit')}</button>}</div><label><span aria-hidden="true">@</span><input type="email" value={recipientEmail} required maxLength={255} readOnly={!editingRecipient} onChange={(event) => setRecipientEmail(event.target.value)} /></label><small>{editingRecipient ? t('새 수신 주소를 입력한 뒤 아래 저장 버튼을 눌러주세요.', 'Enter a new recipient and save the schedule below.') : t('실수로 바뀌지 않도록 잠겨 있습니다.', 'Locked to prevent accidental changes.')}</small></div>
      <footer><small>{t('일정 변경은 저장 후 다음 발송부터 적용됩니다.', 'Changes apply from the next delivery after saving.')}</small><button className="primary" disabled={pending}>{pending ? t('저장 중...', 'Saving...') : t('메일 일정 저장', 'Save schedule')}</button></footer>
    </form>{message && <p className="success-message">{message}</p>}{error && <p className="error">{error}</p>}
  </section>;
}
const weekdays = [['MONDAY', '월요일', 'Monday'], ['TUESDAY', '화요일', 'Tuesday'], ['WEDNESDAY', '수요일', 'Wednesday'], ['THURSDAY', '목요일', 'Thursday'], ['FRIDAY', '금요일', 'Friday'], ['SATURDAY', '토요일', 'Saturday'], ['SUNDAY', '일요일', 'Sunday']] as const;
function subscriptionStatus(status: string, language: 'ko' | 'en') {
  const labels: Record<string, [string, string]> = {
    FREE: ['무료', 'Free'], TRIALING: ['무료 체험', 'Trial'], ACTIVE: ['구독 중', 'Active'],
    CANCEL_AT_PERIOD_END: ['해지 예정', 'Cancels at period end'], CANCELED: ['해지됨', 'Canceled'],
    PAST_DUE: ['결제 확인 필요', 'Past due'],
  };
  return labels[status]?.[language === 'ko' ? 0 : 1] ?? status;
}
