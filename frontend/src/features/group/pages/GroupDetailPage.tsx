import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import { groupApi, GroupResponse, InvitationResponse, InviteLinkResponse } from '../../../api/groupApi';
import { accessToken, errorMessage } from '../../../api/client';
import { AppNavigation } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';
import { AuthenticatedImage } from '../../../app/AuthenticatedImage';
import { ResourcePanel } from '../../resource/ResourcePanel';
import { SubscriptionPanel } from '../../subscription/SubscriptionPanel';

export function GroupDetailPage() {
  const { t, language } = useLanguage();
  const { groupId: rawGroupId } = useParams();
  const groupId = Number(rawGroupId);
  const [searchParams, setSearchParams] = useSearchParams();
  const [group, setGroup] = useState<GroupResponse>();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [timezone, setTimezone] = useState('Asia/Seoul');
  const [visibility, setVisibility] = useState<'LEADER_ONLY' | 'MEMBERS'>('MEMBERS');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState('');
  const [invitationList, setInvitationList] = useState<InvitationResponse[]>([]);
  const [inviteEmail, setInviteEmail] = useState('');
  const [invitePending, setInvitePending] = useState(false);
  const [inviteMode, setInviteMode] = useState<'EMAIL' | 'LINK' | 'KEY'>('EMAIL');
  const [inviteLink, setInviteLink] = useState<InviteLinkResponse>();
  const [linkCopied, setLinkCopied] = useState(false);
  const [joinCodePending, setJoinCodePending] = useState(false);
  const [joinCodeCopied, setJoinCodeCopied] = useState(false);
  const [imagePending, setImagePending] = useState(false);
  const [planPending, setPlanPending] = useState(false);

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1) {
      setError(t('올바르지 않은 그룹 주소입니다.', 'This group address is invalid.'));
      setLoading(false);
      return;
    }
    groupApi.get(groupId).then((value) => {
      setGroup(value);
      setName(value.name);
      setDescription(value.description ?? '');
      setTimezone(value.timezone);
      setVisibility(value.dashboardVisibility);
      if (value.type === 'TEAM' && value.role === 'LEADER') {
        groupApi.invitations(groupId).then(setInvitationList).catch((caught) => setError(errorMessage(caught)));
        groupApi.inviteLinks(groupId).then((links) => setInviteLink(links[0])).catch((caught) => setError(errorMessage(caught)));
      }
    }).catch((value) => setError(errorMessage(value))).finally(() => setLoading(false));
  }, [groupId]);

  async function update(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setSaved(false);
    setError('');
    try {
      const updated = await groupApi.update(groupId, {
        name, description, timezone,
        dashboardVisibility: group?.type === 'PERSONAL' ? 'MEMBERS' : visibility,
      });
      setGroup(updated);
      setSaved(true);
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setSaving(false);
    }
  }

  async function uploadGroupImage(file?: File) {
    if (!file) return;
    setImagePending(true);
    setError('');
    try {
      setGroup(await groupApi.uploadImage(groupId, file));
      window.dispatchEvent(new Event('groups:refresh'));
      setSaved(true);
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setImagePending(false);
    }
  }

  async function switchTestPlan(plan: 'FREE' | 'PAID') {
    const message = plan === 'PAID'
      ? t('테스트 유료 그룹으로 전환할까요? 실제 결제나 청구는 발생하지 않습니다.', 'Switch to the paid test plan? No real payment or charge will occur.')
      : t('무료 그룹으로 되돌릴까요? 테스트 이용 기간 정보가 초기화됩니다.', 'Return to the free plan? Test-period information will be cleared.');
    if (!window.confirm(message)) return;
    setPlanPending(true);
    setError('');
    try {
      setGroup(await groupApi.switchTestPlan(groupId, plan));
      window.dispatchEvent(new Event('groups:refresh'));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setPlanPending(false);
    }
  }

  async function invite(event: FormEvent) {
    event.preventDefault();
    setInvitePending(true);
    setError('');
    try {
      const created = await groupApi.invite(groupId, inviteEmail);
      setInvitationList((current) => [created, ...current]);
      setInviteEmail('');
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setInvitePending(false);
    }
  }

  async function cancelInvitation(invitationId: number) {
    setError('');
    try {
      await groupApi.cancelInvitation(groupId, invitationId);
      setInvitationList((current) => current.map((value) =>
        value.id === invitationId ? { ...value, status: 'CANCELLED' } : value));
    } catch (value) {
      setError(errorMessage(value));
    }
  }

  async function createInviteLink() {
    setInvitePending(true);
    setLinkCopied(false);
    setError('');
    try {
      setInviteLink(await groupApi.createInviteLink(groupId));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setInvitePending(false);
    }
  }

  async function copyInviteLink() {
    if (!inviteLink?.url) return;
    try {
      await navigator.clipboard.writeText(inviteLink.url);
      setLinkCopied(true);
    } catch {
      setError(t('링크를 복사하지 못했습니다. 링크를 직접 선택해 복사해 주세요.', 'Could not copy the link. Select and copy it manually.'));
    }
  }

  async function revokeInviteLink() {
    if (!inviteLink) return;
    setInvitePending(true);
    setError('');
    try {
      await groupApi.revokeInviteLink(groupId, inviteLink.id);
      setInviteLink(undefined);
      setLinkCopied(false);
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setInvitePending(false);
    }
  }

  async function createJoinCode() {
    setJoinCodePending(true);
    setJoinCodeCopied(false);
    setError('');
    try {
      setGroup(await groupApi.createJoinCode(groupId));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setJoinCodePending(false);
    }
  }

  async function rotateJoinCode() {
    if (!window.confirm(t('그룹 키를 재발급할까요? 기존 키는 즉시 사용할 수 없게 됩니다.', 'Reissue the group key? The current key will stop working immediately.'))) return;
    setJoinCodePending(true);
    setJoinCodeCopied(false);
    setError('');
    try {
      setGroup(await groupApi.rotateJoinCode(groupId));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setJoinCodePending(false);
    }
  }

  async function revokeJoinCode() {
    if (!window.confirm(t('그룹 키를 삭제할까요? 새 키를 만들기 전까지 키로 참여할 수 없습니다.', 'Delete the group key? No one can join with a key until a new one is created.'))) return;
    setJoinCodePending(true);
    setError('');
    try {
      await groupApi.revokeJoinCode(groupId);
      if (group) setGroup({ ...group, joinCodeActive: false, joinCode: undefined });
      setJoinCodeCopied(false);
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setJoinCodePending(false);
    }
  }

  async function copyJoinCode() {
    if (!group?.joinCode) return;
    try {
      await navigator.clipboard.writeText(group.joinCode);
      setJoinCodeCopied(true);
    } catch {
      setError(t('그룹 키를 복사하지 못했습니다. 키를 직접 선택해 복사해 주세요.', 'Could not copy the group key. Select and copy it manually.'));
    }
  }

  if (!accessToken.get()) return <Navigate to="/login" replace />;
  if (loading) return <main className="center-page">{t('그룹을 불러오는 중...', 'Loading group...')}</main>;
  const requestedTab = searchParams.get('tab');
  const settingsTab = requestedTab === 'collaboration' || requestedTab === 'plan' ? requestedTab : 'general';
  return <><AppNavigation /><main className="group-detail-page app-page"><section className="auth-card profile-card group-detail-card">
    <Link to="/groups">← {t('그룹 목록으로', 'Back to groups')}</Link>
    <div className="group-detail-title">{group?.imageUrl ? <AuthenticatedImage className="group-image" src={group.imageUrl} alt="" /> : <span className={`group-type ${group?.type.toLowerCase()}`}>{group?.type === 'PERSONAL' ? t('개인 일정', 'Personal') : t('팀', 'Team')}</span>}<div><h1>{group?.type === 'PERSONAL' ? t('개인 캘린더 설정', 'Personal calendar settings') : t('그룹 설정', 'Group settings')}</h1>{group && <p>{group.type === 'PERSONAL' ? t('내 일정의 기본 정보를 관리합니다.', 'Manage your personal calendar details.') : `${group.name} · ${group.role === 'LEADER' ? t('팀장', 'Leader') : t('팀원', 'Member')}`}</p>}</div></div>
    {group && <nav className="group-primary-links" aria-label={t('그룹 바로가기', 'Group shortcuts')}>{group.type === 'PERSONAL' ? <Link className="secondary" to={`/calendar?groupId=${group.id}`}>{t('캘린더', 'Calendar')}</Link> : <><Link className="secondary" to={`/groups/${group.id}/dashboard`}>{t('대시보드', 'Dashboard')}</Link><Link className="secondary group-tasks-link" to={`/groups/${group.id}/tasks`}>{t('업무', 'Tasks')}</Link><Link className="secondary" to={`/calendar?groupId=${group.id}`}>{t('캘린더', 'Calendar')}</Link><Link className="secondary" to={`/groups/${group.id}/members`}>{t('팀원', 'Members')}</Link></>}</nav>}
    {group?.type === 'TEAM' && <nav className="settings-category-tabs" aria-label={t('설정 항목', 'Settings sections')}>
      <button className={settingsTab === 'general' ? 'active' : ''} type="button" onClick={() => setSearchParams({}, { replace: true })}><span aria-hidden="true">⌂</span><strong>{t('기본', 'General')}</strong><small>{t('이름·시간대', 'Name & time zone')}</small></button>
      <button className={settingsTab === 'collaboration' ? 'active' : ''} type="button" onClick={() => setSearchParams({ tab: 'collaboration' }, { replace: true })}><span aria-hidden="true">♧</span><strong>{t('협업', 'Collaboration')}</strong><small>{t('팀원·초대·자료', 'Members, invites & files')}</small></button>
      <button className={settingsTab === 'plan' ? 'active' : ''} type="button" onClick={() => setSearchParams({ tab: 'plan' }, { replace: true })}><span aria-hidden="true">◇</span><strong>{t('요금·리포트', 'Plan & reports')}</strong><small>{t('멤버십·메일 일정', 'Membership & email')}</small></button>
    </nav>}
    {(group?.type === 'PERSONAL' || settingsTab === 'general') && (group?.role === 'LEADER' ? <section className="group-settings-section"><header><h2>{t('기본 설정', 'Basic settings')}</h2><p>{t('이름, 설명과 그룹에서 사용할 기준 시간대를 관리합니다.', 'Manage the name, description, and default time zone.')}</p></header><form className="form" onSubmit={update}>
      <label className="field"><span>{group.type === 'PERSONAL' ? t('캘린더 이름', 'Calendar name') : t('그룹 이름', 'Group name')}</span><input required maxLength={80} value={name} onChange={(event) => setName(event.target.value)} /></label>
      <div className="field"><span>{t('그룹 아이콘', 'Group icon')}</span><label className={`group-image-picker ${imagePending ? 'pending' : ''}`}>
        <span className="group-image-picker-preview">{group.imageUrl ? <AuthenticatedImage src={group.imageUrl} alt="" /> : <b>{group.name.slice(0, 1)}</b>}</span>
        <span><strong>{imagePending ? t('이미지를 업로드하고 있어요', 'Uploading image') : t('새 그룹 아이콘 선택', 'Choose a new group icon')}</strong><small>{t('목록과 좌측 사이드바에 바로 반영됩니다. JPG, PNG, GIF · 최대 5MB', 'Updates the group list and sidebar. JPG, PNG, GIF · up to 5MB')}</small></span>
        <i aria-hidden="true">＋</i>
        <input className="sr-only" type="file" accept="image/jpeg,image/png,image/gif" disabled={imagePending} onChange={(event) => uploadGroupImage(event.target.files?.[0])} />
      </label></div>
      <label className="field"><span>{t('설명', 'Description')}</span><textarea maxLength={500} value={description} onChange={(event) => setDescription(event.target.value)} /></label>
      <label className="field"><span>{t('기준 시간대', 'Default time zone')}</span><select required value={timezone} onChange={(event) => setTimezone(event.target.value)}>{timezoneOptions.map((option) => <option value={option.value} key={option.value}>{t(option.label, option.en)}</option>)}</select><small className="field-help">{t('업무 마감과 캘린더 알림 계산에 사용됩니다.', 'Used to calculate task deadlines and calendar alerts.')}</small></label>
      {group.type === 'TEAM' && <label className="field"><span>{t('대시보드 공개 범위', 'Dashboard visibility')}</span><select value={visibility} onChange={(event) => setVisibility(event.target.value as 'LEADER_ONLY' | 'MEMBERS')}><option value="MEMBERS">{t('모든 팀원이 볼 수 있음', 'Visible to all members')}</option><option value="LEADER_ONLY">{t('팀장만 볼 수 있음', 'Leaders only')}</option></select></label>}
      {saved && <p className="success-message">{t('그룹 설정을 저장했습니다.', 'Group settings saved.')}</p>}
      <button className="primary" disabled={saving}>{saving ? t('저장 중...', 'Saving...') : t('설정 저장', 'Save settings')}</button>
    </form></section> : <section className="group-settings-section readonly-settings"><header><h2>{t('기본 정보', 'Basic information')}</h2><p>{t('설정 변경은 그룹 팀장만 할 수 있습니다.', 'Only group leaders can change settings.')}</p></header>
      <dl><div><dt>{t('설명', 'Description')}</dt><dd>{group?.description || t('설명 없음', 'No description')}</dd></div><div><dt>{t('시간대', 'Time zone')}</dt><dd>{group?.timezone}</dd></div><div><dt>{t('대시보드', 'Dashboard')}</dt><dd>{group?.dashboardVisibility === 'MEMBERS' ? t('모든 멤버', 'All members') : t('팀장만', 'Leaders only')}</dd></div></dl>
    </section>)}
    {group?.type === 'TEAM' && settingsTab === 'plan' && <section className={`settings-tab-panel group-subsection membership-section ${group.membershipPlan.toLowerCase()}`}><header className="group-section-heading"><div><span className="page-eyebrow">PLAN</span><h2>{t('그룹 멤버십', 'Group membership')}</h2><p>{t('기본 리포트는 모든 그룹에서 제한 없이 사용할 수 있습니다.', 'Core reports are unlimited for every group.')}</p></div><span className={`membership-badge ${group.membershipPlan.toLowerCase()}`}>{group.membershipPlan === 'PAID' ? t('유료 테스트', 'Paid test') : t('무료', 'Free')}</span></header>
      <div className="membership-plan-layout"><div className="membership-benefits"><strong>{group.membershipPlan === 'PAID' ? t('유료 그룹 테스트 이용 중', 'Paid group test active') : t('무료 그룹', 'Free group')}</strong><ul>
        <li>{t('주간·월간·연간, 내 업무·그룹 전체 PDF 리포트 제한 없음', 'Unlimited weekly, monthly, and yearly personal/group PDF reports')}</li>
        <li>{t('업무·캘린더·댓글·멘션·알림의 전체 협업 흐름', 'Complete tasks, calendar, comments, mentions, and alerts')}</li>
        <li>{group.membershipPlan === 'PAID' ? t('향후 OpenAI 분석·자동 메일 리포트 연결 대상', 'Ready for future OpenAI analysis and scheduled email reports') : t('AI 분석·자동 메일 리포트는 유료 기능으로 연결 예정', 'AI analysis and scheduled email reports are planned paid features')}</li>
      </ul><small>{t('현재 유료 전환은 결제 없는 QA용 상태 변경입니다. 카드 등록이나 실제 청구가 발생하지 않습니다.', 'The current paid switch is QA-only. It does not register a card or create a real charge.')}</small></div>
      <aside className="membership-period">{group.membershipPlan === 'PAID' ? <dl><div><dt>{t('테스트 시작일', 'Test started')}</dt><dd>{group.paidStartedAt ? formatDate(group.paidStartedAt, language) : '-'}</dd></div><div><dt>{t('이용 기간', 'Access period')}</dt><dd>{group.paidUntil ? t(`${formatDate(group.paidUntil, language)}까지`, `Until ${formatDate(group.paidUntil, language)}`) : '-'}</dd></div><div><dt>{t('다음 결제 예정일', 'Next billing date')}</dt><dd>{group.nextBillingAt ? formatDate(group.nextBillingAt, language) : '-'}</dd></div></dl> : <p>{t('필요할 때 테스트 유료 플랜으로 전환해 기간과 결제 예정일 표시를 검증할 수 있습니다.', 'Switch to the paid test plan to verify period and billing-date presentation.')}</p>}
        {group.role === 'LEADER' && group.testPlanSwitchEnabled && <button className={group.membershipPlan === 'PAID' ? 'secondary' : 'primary'} type="button" disabled={planPending} onClick={() => switchTestPlan(group.membershipPlan === 'PAID' ? 'FREE' : 'PAID')}>{planPending ? t('전환 중...', 'Switching...') : group.membershipPlan === 'PAID' ? t('무료로 되돌리기', 'Return to free') : t('결제 없이 유료 그룹 체험', 'Try paid group without payment')}</button>}
        {group.role === 'LEADER' && <Link className="membership-payment-link" to="/payments">{t('토스페이먼츠 결제 연동 테스트 확인', 'Open Toss Payments integration tests')} →</Link>}
        {!group.testPlanSwitchEnabled && group.role === 'LEADER' && <small>{t('실제 서비스 환경에서는 결제 승인 완료 후에만 유료로 전환됩니다.', 'In production, paid status changes only after payment approval.')}</small>}
      </aside></div>
    </section>}
    {group?.type === 'TEAM' && settingsTab === 'plan' && group.role === 'LEADER' && <SubscriptionPanel groupId={group.id} />}
    {group?.type === 'TEAM' && settingsTab === 'collaboration' && <section className="member-directory-link-card"><div><span className="page-eyebrow">TEAM</span><h2>{t('팀원 목록', 'Team member directory')}</h2><p>{t('함께하는 팀원과 역할을 별도 화면에서 편하게 확인하세요.', 'View teammates and roles in a focused member directory.')}</p></div><Link className="primary" to={`/groups/${group.id}/members`}>{t('팀원 목록 열기', 'Open member list')} →</Link></section>}
    {group?.type === 'TEAM' && settingsTab === 'collaboration' && <ResourcePanel groupId={group.id} />}
    {group && error && <p className="error group-global-error">{error}</p>}
    {!group && error && <p className="error">{error}</p>}
    {group?.type === 'TEAM' && settingsTab === 'collaboration' && group.role === 'LEADER' && <section className="group-subsection invitation-section"><header className="group-section-heading"><div><h2>{t('멤버 초대', 'Invite members')}</h2><p>{t('이메일, 링크 또는 그룹 키를 공유할 수 있습니다.', 'Invite by email, link, or group key.')}</p></div></header><div className="invite-method-tabs"><button className={inviteMode === 'EMAIL' ? 'active' : ''} type="button" onClick={() => setInviteMode('EMAIL')}>{t('이메일 초대', 'Email')}</button><button className={inviteMode === 'LINK' ? 'active' : ''} type="button" onClick={() => setInviteMode('LINK')}>{t('초대 링크', 'Invite link')}</button><button className={inviteMode === 'KEY' ? 'active' : ''} type="button" onClick={() => setInviteMode('KEY')}>{t('그룹 키', 'Group key')}</button></div>
      {inviteMode === 'EMAIL' ? <><form className="inline-invite" onSubmit={invite}><input type="email" required maxLength={255} value={inviteEmail} onChange={(event) => setInviteEmail(event.target.value)} placeholder={t('초대할 이메일', 'Email address to invite')} /><button className="secondary" disabled={invitePending}>{invitePending ? t('전송 중...', 'Sending...') : t('초대 메일 보내기', 'Send invitation')}</button></form><div className="invitation-list">{invitationList.map((invitation) => <div className="invitation-row" key={invitation.id}><div><strong>{invitation.email}</strong><small>{invitationStatus(invitation.status, language)} · {t(`${formatDate(invitation.expiresAt, language)}까지`, `until ${formatDate(invitation.expiresAt, language)}`)}</small></div>{invitation.status === 'PENDING' && <button type="button" onClick={() => cancelInvitation(invitation.id)}>{t('취소', 'Cancel')}</button>}</div>)}</div></> :
      inviteMode === 'LINK' ? <div className="invite-link-panel">{inviteLink ? <>{inviteLink.url ? <label><span>{t('공유할 초대 링크', 'Invitation link to share')}</span><div><input readOnly value={inviteLink.url} onFocus={(event) => event.currentTarget.select()} /><button className="primary" type="button" onClick={copyInviteLink}>{linkCopied ? t('복사됨', 'Copied') : t('링크 복사', 'Copy link')}</button></div></label> : <p>{t('현재 사용 중인 초대 링크가 있습니다. 보안을 위해 페이지를 벗어나면 링크 주소는 다시 표시되지 않습니다.', 'An invitation link is active. For security, its URL is not shown again after leaving this page.')}</p>}<p>{t(`${formatDate(inviteLink.expiresAt, language)}까지 여러 명이 사용할 수 있습니다.`, `Multiple people can use it until ${formatDate(inviteLink.expiresAt, language)}.`)}</p><div className="invite-link-actions">{!inviteLink.url && <button className="secondary" type="button" disabled={invitePending} onClick={createInviteLink}>{t('새 링크로 교체', 'Replace link')}</button>}<button className="invite-link-revoke" type="button" disabled={invitePending} onClick={revokeInviteLink}>{t('링크 사용 중지', 'Revoke link')}</button></div></> : <><p>{t('하나의 링크를 팀원들에게 공유하면 이메일을 하나씩 입력하지 않아도 됩니다. 링크는 72시간 동안 유효합니다.', 'Share one link with the team instead of entering each email. The link remains valid for 72 hours.')}</p><button className="primary" type="button" disabled={invitePending} onClick={createInviteLink}>{invitePending ? t('생성 중...', 'Creating...') : t('초대 링크 만들기', 'Create invitation link')}</button></>}</div> :
      <div className="group-key-panel">{group.joinCode ? <><p>{t('새 그룹 키가 생성되었습니다. 이 화면을 벗어나면 원문은 다시 표시되지 않습니다.', 'A new group key was created. It will not be shown again after you leave this page.')}</p><strong>{group.joinCode}</strong><small>{t('그룹 키는 비밀번호처럼 취급하고 초대 대상에게만 공유해 주세요.', 'Treat the group key like a password and share it only with invitees.')}</small><div className="group-key-actions"><button className="primary" type="button" disabled={joinCodePending} onClick={copyJoinCode}>{joinCodeCopied ? t('복사됨', 'Copied') : t('키 복사', 'Copy key')}</button><button className="secondary" type="button" disabled={joinCodePending} onClick={rotateJoinCode}>{t('재발급', 'Reissue')}</button><button className="group-key-delete" type="button" disabled={joinCodePending} onClick={revokeJoinCode}>{t('키 삭제', 'Delete key')}</button></div></> : group.joinCodeActive ? <><p>{t('현재 그룹 키가 활성화되어 있습니다.', 'A group key is currently active.')}</p><small>{t('보안을 위해 원문은 저장하지 않아 다시 표시할 수 없습니다. 키를 잊었거나 노출이 의심되면 재발급하세요.', 'The original key is not stored and cannot be shown again. Reissue it if it is lost or may have been exposed.')}</small><div className="group-key-actions"><button className="secondary" type="button" disabled={joinCodePending} onClick={rotateJoinCode}>{t('새 키 재발급', 'Reissue key')}</button><button className="group-key-delete" type="button" disabled={joinCodePending} onClick={revokeJoinCode}>{t('키 삭제', 'Delete key')}</button></div></> : <><p>{t('현재 활성화된 그룹 키가 없습니다. 필요한 동안만 키를 만들어 공유할 수 있습니다.', 'No group key is active. Create one only when needed.')}</p><small>{t('사용이 끝나면 삭제하고, 노출이 의심되면 즉시 재발급하세요.', 'Delete it when no longer needed and reissue it immediately if exposure is suspected.')}</small><button className="primary" type="button" disabled={joinCodePending} onClick={createJoinCode}>{joinCodePending ? t('생성 중...', 'Creating...') : t('그룹 키 생성', 'Create group key')}</button></>}</div>}
    </section>}
  </section></main></>;
}

function formatDate(value: string, language: 'ko' | 'en') {
  return new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
function invitationStatus(status: InvitationResponse['status'], language: 'ko' | 'en') {
  const labels = { PENDING: ['대기 중', 'Pending'], ACCEPTED: ['수락됨', 'Accepted'], CANCELLED: ['취소됨', 'Cancelled'], EXPIRED: ['만료됨', 'Expired'] } as const;
  return labels[status][language === 'ko' ? 0 : 1];
}

const timezoneOptions = [
  { value: 'Asia/Seoul', label: '서울 (UTC+09:00)', en: 'Seoul (UTC+09:00)' }, { value: 'Asia/Tokyo', label: '도쿄 (UTC+09:00)', en: 'Tokyo (UTC+09:00)' },
  { value: 'Asia/Shanghai', label: '상하이 (UTC+08:00)', en: 'Shanghai (UTC+08:00)' }, { value: 'Asia/Singapore', label: '싱가포르 (UTC+08:00)', en: 'Singapore (UTC+08:00)' },
  { value: 'America/Los_Angeles', label: '로스앤젤레스', en: 'Los Angeles' }, { value: 'America/New_York', label: '뉴욕', en: 'New York' },
  { value: 'Europe/London', label: '런던', en: 'London' }, { value: 'Europe/Paris', label: '파리', en: 'Paris' }, { value: 'UTC', label: 'UTC', en: 'UTC' },
];
