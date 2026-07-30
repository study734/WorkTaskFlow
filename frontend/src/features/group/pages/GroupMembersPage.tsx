import { useEffect, useMemo, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { accessToken, errorMessage, sessionMode } from '../../../api/client';
import { groupApi, GroupResponse, MemberResponse } from '../../../api/groupApi';
import { AppNavigation } from '../../../app/AppNavigation';
import { AuthenticatedImage } from '../../../app/AuthenticatedImage';
import { useLanguage } from '../../../app/LanguageContext';

export function GroupMembersPage() {
  const { t, language } = useLanguage();
  const navigate = useNavigate();
  const groupId = Number(useParams().groupId);
  const [group, setGroup] = useState<GroupResponse>();
  const [members, setMembers] = useState<MemberResponse[]>([]);
  const [query, setQuery] = useState('');
  const [loading, setLoading] = useState(true);
  const [pendingMemberId, setPendingMemberId] = useState<number>();
  const [error, setError] = useState('');
  const demo = sessionMode.isDemo();

  useEffect(() => {
    if (!Number.isInteger(groupId) || groupId < 1) {
      setError(t('올바르지 않은 그룹 주소입니다.', 'This group address is invalid.'));
      setLoading(false);
      return;
    }
    Promise.all([groupApi.get(groupId), groupApi.members(groupId)])
      .then(([groupValue, memberValues]) => {
        setGroup(groupValue);
        setMembers(memberValues);
      })
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, [groupId]);

  const filteredMembers = useMemo(() => {
    const normalized = query.trim().toLocaleLowerCase();
    return [...members]
      .filter((member) => !normalized || member.nickname.toLocaleLowerCase().includes(normalized))
      .sort((left, right) => {
        if (left.role !== right.role) return left.role === 'LEADER' ? -1 : 1;
        return left.nickname.localeCompare(right.nickname, language === 'ko' ? 'ko' : 'en');
      });
  }, [language, members, query]);

  async function changeRole(member: MemberResponse, role: 'LEADER' | 'MEMBER') {
    if (member.role === role || demo) return;
    if (role === 'LEADER' && !window.confirm(t(
      `${member.nickname}님에게 팀장 권한을 부여할까요?\n\n팀장은 그룹 설정, 멤버 관리와 초대 권한을 갖게 됩니다.`,
      `Make ${member.nickname} a leader?\n\nLeaders can manage group settings, members, and invitations.`,
    ))) return;
    setPendingMemberId(member.id);
    setError('');
    try {
      const updated = await groupApi.changeMemberRole(groupId, member.id, role);
      setMembers((current) => current.map((value) => value.id === updated.id ? updated : value));
      if (group?.memberId === updated.id) setGroup({ ...group, role: updated.role });
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setPendingMemberId(undefined);
    }
  }

  async function removeMember(member: MemberResponse) {
    if (demo || !window.confirm(t(`${member.nickname}님을 그룹에서 내보낼까요?`, `Remove ${member.nickname} from this group?`))) return;
    setPendingMemberId(member.id);
    setError('');
    try {
      await groupApi.removeMember(groupId, member.id);
      setMembers((current) => current.filter((value) => value.id !== member.id));
    } catch (value) {
      setError(errorMessage(value));
    } finally {
      setPendingMemberId(undefined);
    }
  }

  async function leaveGroup() {
    if (demo || !window.confirm(t(
      '이 그룹에서 탈퇴할까요? 마지막 팀장은 먼저 다른 멤버에게 팀장 역할을 넘겨야 합니다.',
      'Leave this group? The last leader must transfer the leader role first.',
    ))) return;
    setPendingMemberId(group?.memberId);
    setError('');
    try {
      await groupApi.leave(groupId);
      navigate('/groups', { replace: true });
    } catch (value) {
      setError(errorMessage(value));
      setPendingMemberId(undefined);
    }
  }

  if (!accessToken.get()) return <Navigate to="/login" replace />;
  return <><AppNavigation /><main className="group-members-page app-page">
    <header className="group-members-header">
      <div><Link to={`/groups/${groupId}/dashboard`}>← {t('그룹 대시보드', 'Group dashboard')}</Link>
        <span className="page-eyebrow">TEAM MEMBERS</span>
        <h1>{t('팀원', 'Team members')}</h1>
        <p>{t(`${group?.name ?? '그룹'}에서 함께하는 사람과 역할을 확인하세요.`, `See everyone working in ${group?.name ?? 'this group'} and their roles.`)}</p>
      </div>
      <div className="group-members-header-actions">
        <Link className="secondary" to={`/groups/${groupId}/tasks`}>{t('업무 보기', 'View tasks')}</Link>
        {group?.role === 'LEADER' && <Link className="secondary" to={`/groups/${groupId}?tab=collaboration`}>{t('초대·설정', 'Invites & settings')}</Link>}
      </div>
    </header>

    {error && <p className="error group-members-error">{error}</p>}
    <section className="group-members-summary" aria-label={t('팀원 요약', 'Member summary')}>
      <div><span>{t('전체 팀원', 'All members')}</span><strong>{members.length}</strong></div>
      <div><span>{t('팀장', 'Leaders')}</span><strong>{members.filter((member) => member.role === 'LEADER').length}</strong></div>
      <div><span>{t('팀원', 'Members')}</span><strong>{members.filter((member) => member.role === 'MEMBER').length}</strong></div>
    </section>

    <section className="group-members-panel">
      <header><div><h2>{t('팀원 목록', 'Member list')}</h2><p>{t('이름과 역할을 빠르게 찾아볼 수 있습니다.', 'Quickly find a teammate and check their role.')}</p></div>
        <label className="member-search"><span className="sr-only">{t('팀원 검색', 'Search members')}</span><input type="search" value={query} onChange={(event) => setQuery(event.target.value)} placeholder={t('이름으로 검색', 'Search by name')} /></label>
      </header>
      {loading && <p className="muted">{t('팀원을 불러오는 중...', 'Loading team members...')}</p>}
      {!loading && filteredMembers.length === 0 && <p className="empty-state">{query ? t('검색 결과가 없습니다.', 'No matching members.') : t('표시할 팀원이 없습니다.', 'No members to show.')}</p>}
      <div className="member-directory">{filteredMembers.map((member) => <article key={member.id}>
        <span className="member-directory-avatar">{member.profileImageUrl ? <AuthenticatedImage src={member.profileImageUrl} alt="" /> : member.nickname.slice(0, 1)}</span>
        <div className="member-directory-info"><div><strong>{member.nickname}</strong>{member.id === group?.memberId && <span>{t('나', 'Me')}</span>}</div>
          <small>{t(`${formatJoinedAt(member.joinedAt, language)} 참여`, `Joined ${formatJoinedAt(member.joinedAt, language)}`)}</small>
        </div>
        <span className={`member-role-badge ${member.role.toLowerCase()}`}>{member.role === 'LEADER' ? t('팀장', 'Leader') : t('팀원', 'Member')}</span>
        {group?.role === 'LEADER' && !demo && <div className="member-directory-actions">
          <select aria-label={t(`${member.nickname} 역할`, `${member.nickname} role`)} value={member.role} disabled={pendingMemberId === member.id || member.id === group.memberId} onChange={(event) => changeRole(member, event.target.value as 'LEADER' | 'MEMBER')}>
            <option value="LEADER">{t('팀장', 'Leader')}</option><option value="MEMBER">{t('팀원', 'Member')}</option>
          </select>
          {member.id !== group.memberId && <button type="button" disabled={pendingMemberId === member.id} onClick={() => removeMember(member)}>{t('내보내기', 'Remove')}</button>}
        </div>}
      </article>)}</div>
      {demo && <p className="demo-readonly-hint">{t('데모에서는 팀원 정보만 볼 수 있으며 역할 변경과 내보내기는 할 수 없습니다.', 'The demo shows member information, but roles and membership cannot be changed.')}</p>}
    </section>
    {group?.type === 'TEAM' && !demo && <button className="leave-group-button member-directory-leave" type="button" disabled={pendingMemberId === group.memberId} onClick={leaveGroup}>{t('이 그룹에서 나가기', 'Leave this group')}</button>}
  </main></>;
}

function formatJoinedAt(value: string, language: 'ko' | 'en') {
  return new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', {
    year: 'numeric', month: 'short', day: 'numeric',
  }).format(new Date(value));
}
