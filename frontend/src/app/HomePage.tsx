import { useEffect, useState } from 'react';
import { Link, Navigate } from 'react-router-dom';
import { authApi, MeResponse } from '../api/authApi';
import { accessToken, type ApiError, errorMessage } from '../api/client';
import { notificationApi, NotificationResponse } from '../api/notificationApi';
import { dashboardApi, DashboardTask, PersonalDashboard } from '../api/dashboardApi';
import { groupApi, GroupResponse } from '../api/groupApi';
import { AppNavigation } from './AppNavigation';
import { useLanguage } from './LanguageContext';
import { AuthenticatedImage } from './AuthenticatedImage';

export function HomePage() {
  const { t, language } = useLanguage();
  const [me, setMe] = useState<MeResponse>();
  const [loading, setLoading] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);
  const [dashboard, setDashboard] = useState<PersonalDashboard>();
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  const [loadError, setLoadError] = useState('');
  useEffect(() => {
    async function load() {
      try {
        if (!accessToken.get()) {
          const token = await authApi.refresh();
          accessToken.set(token.accessToken, token.expiresIn);
        }
        setMe(await authApi.me());
        const [dashboardValue, groupValues] = await Promise.all([
          dashboardApi.personal(),
          groupApi.list().catch(() => [] as GroupResponse[]),
        ]);
        setDashboard(dashboardValue);
        setGroups(groupValues);
        notificationApi.list(1).then((page) => setUnreadCount(page.unreadCount)).catch(() => undefined);
      } catch (error) {
        const code = (error as ApiError)?.code;
        if (code === 'OFFLINE' || code === 'NETWORK_ERROR' || code === 'REQUEST_TIMEOUT') {
          setLoadError(errorMessage(error));
        } else {
          accessToken.clear();
        }
      }
      finally { setLoading(false); }
    }
    load();
  }, []);
  if (loading) return <main className="center-page">{t('인증 상태 확인 중...', 'Checking your session...')}</main>;
  if (!me && loadError) return <main className="center-page session-unavailable" role="alert"><div><h1>{t('서비스에 연결할 수 없습니다.', 'The service is unavailable.')}</h1><p>{loadError}</p><button type="button" onClick={() => window.location.reload()}>{t('다시 시도', 'Try again')}</button></div></main>;
  if (!me) return <Navigate to="/login" replace />;
  return <><AppNavigation unreadCount={unreadCount} /><main className="personal-dashboard-page app-page"><header className="personal-dashboard-header"><div><div><span className="page-eyebrow">TODAY</span><h1>{t(`${me.name}님, 오늘도 반가워요!`, `Welcome back, ${me.name}!`)}</h1><p>{t('중요한 일부터 하나씩 가볍게 시작해 볼까요?', 'Let’s start with what matters most today.')}</p></div></div></header>
    {dashboard && <><section className="dashboard-panel home-group-panel"><div className="dashboard-panel-title inline"><div><span className="page-eyebrow">SHORTCUTS</span><h2>{t('바로가기', 'Shortcuts')}</h2><p>{t('참여 중인 그룹으로 바로 이동할 수 있어요.', 'Jump to one of your team groups.')}</p></div><Link to="/groups">{t('전체 보기', 'View all')}</Link></div>
        {groups.filter((group) => group.type === 'TEAM').length === 0 ? <div className="home-start-guide"><div><span>1</span><strong>{t('그룹 만들기 또는 참여', 'Create or join a group')}</strong><small>{t('팀장이 그룹을 만들거나 받은 그룹 키로 참여하세요.', 'Create a team or join with a group key.')}</small></div><div><span>2</span><strong>{t('팀원과 업무 시작', 'Start working together')}</strong><small>{t('업무를 만들고 담당자를 정하면 알림이 전달됩니다.', 'Create tasks and assign teammates to notify them.')}</small></div><div><span>3</span><strong>{t('대시보드에서 확인', 'Track it on the dashboard')}</strong><small>{t('진행률, 일정과 팀원별 현황을 한눈에 볼 수 있습니다.', 'See progress, schedules, and workload at a glance.')}</small></div><Link className="primary" to="/groups">{t('첫 그룹 시작하기', 'Start your first group')} →</Link></div> : <div className="home-group-list">{groups.filter((group) => group.type === 'TEAM').map((group, index) => <Link to={`/groups/${group.id}/dashboard`} key={group.id}><span className={`home-group-avatar home-group-avatar-${index % 4}`}>{group.imageUrl ? <AuthenticatedImage src={group.imageUrl} alt="" /> : group.name.trim().charAt(0).toUpperCase() || 'G'}</span><span><strong>{group.name}</strong><small>{t(group.role === 'LEADER' ? '팀장' : '팀원', group.role === 'LEADER' ? 'Leader' : 'Member')}</small></span></Link>)}</div>}
      </section>
      <section className="dashboard-two-columns"><section className="dashboard-panel"><h2>{t('내 우선 업무', 'Priority tasks')}</h2>{dashboard.priorityTasks.length === 0 ? <p className="empty-state">{t('진행할 담당 업무가 없습니다.', 'No assigned tasks to work on.')}</p> : <div className="dashboard-task-list">{dashboard.priorityTasks.map((task) => <PersonalTask task={task} key={task.id} />)}</div>}</section><section className="dashboard-panel"><h2>{t('다가오는 일정', 'Upcoming events')}</h2>{dashboard.upcomingItems.length === 0 ? <p className="empty-state">{t('7일 안에 예정된 일정이 없습니다.', 'No events scheduled in the next 7 days.')}</p> : <div className="upcoming-list">{dashboard.upcomingItems.map((item) => <Link to={item.sourceTaskId ? `/tasks/${item.sourceTaskId}` : '/calendar'} key={`${item.source}-${item.eventId ?? item.sourceTaskId}`}><strong>{item.title}</strong><span>{item.startAt.slice(0, 16)} · {item.groupName}</span></Link>)}</div>}</section></section>
      <section className="dashboard-two-columns"><section className="dashboard-panel"><h2>{t('그룹별 내 업무', 'Tasks by group')}</h2>{dashboard.groups.length === 0 ? <p className="empty-state">{t('그룹별 담당 업무가 없습니다.', 'No group assignments yet.')}</p> : <div className="personal-group-metrics">{dashboard.groups.map((group) => <Link to={`/groups/${group.groupId}/dashboard`} key={group.groupId}><strong>{group.groupName}</strong><span>{group.assignedCount === 0 ? t('담당 업무가 없습니다.', 'No assigned tasks.') : t(`담당 ${group.assignedCount} · 진행 ${group.activeCount} · 완료 ${group.completedCount} · 지연 ${group.delayedCount}`, `Assigned ${group.assignedCount} · Active ${group.activeCount} · Done ${group.completedCount} · Overdue ${group.delayedCount}`)}</span></Link>)}</div>}</section><section className="dashboard-panel"><div className="dashboard-panel-title inline"><h2>{t('미확인 알림', 'Unread alerts')}</h2><Link to="/notifications">{t('전체 보기 →', 'View all →')}</Link></div>{dashboard.unreadNotifications.length === 0 ? <p className="empty-state">{t('확인하지 않은 알림이 없습니다.', 'You are all caught up.')}</p> : <div className="recent-dashboard-notifications">{dashboard.unreadNotifications.map((item) => <Link to="/notifications" key={item.id}><strong>{notificationTitle(item, language)}</strong><span>{notificationMessage(item, language)}</span></Link>)}</div>}</section></section>
    </>}
  </main></>;
}

function PersonalTask({ task }: { task: DashboardTask }) { const { t } = useLanguage(); return <Link to={`/tasks/${task.id}`}><div><strong>{task.title}</strong>{task.delayed && <span>{t('지연', 'Overdue')}</span>}</div><small>{task.groupName} · {task.startAt ? t(`시작 ${task.startAt.slice(0, 16)} · `, `Started ${task.startAt.slice(0, 16)} · `) : ''}{task.dueAt ? t(`마감 ${task.dueAt.slice(0, 16)}`, `Due ${task.dueAt.slice(0, 16)}`) : t('마감 없음', 'No due date')}</small></Link>; }
function notificationTitle(item: NotificationResponse, language: 'ko' | 'en') { if (language === 'ko') return item.title; return ({ TASK_REQUESTED: 'New task request', TASK_ASSIGNED: 'Task assigned', TASK_STATUS_CHANGED: 'Task status changed', TASK_DUE_SOON: 'Important deadline approaching', COMMENT_CREATED: 'New comment', COMMENT_MENTIONED: 'You were mentioned', SECURITY_NEW_DEVICE: 'New device login', SECURITY_SESSION_REUSED: 'Suspicious session blocked', SUBSCRIPTION_ROLLOUT_NOTICE: 'Subscription rollout notice' } as Record<string, string>)[item.type] ?? item.title; }
function notificationMessage(item: NotificationResponse, language: 'ko' | 'en') { if (language === 'ko') return item.message; const title = item.message.match(/^'(.+?)'/)?.[1] ?? 'A task'; return ({ TASK_REQUESTED: `'${title}' is waiting for approval.`, TASK_ASSIGNED: `You were assigned to '${title}'.`, TASK_STATUS_CHANGED: `The status of '${title}' changed.`, TASK_DUE_SOON: `The deadline for '${title}' is approaching.`, COMMENT_CREATED: `A new comment was added to '${title}'.`, COMMENT_MENTIONED: `You were mentioned in a comment on '${title}'.`, SECURITY_NEW_DEVICE: 'A new sign-in was detected. Review your signed-in devices.', SECURITY_SESSION_REUSED: 'An old session token was reused and the device session was blocked.', SUBSCRIPTION_ROLLOUT_NOTICE: 'The paid subscription rollout schedule and your keep-free or continue-paid options are ready.' } as Record<string, string>)[item.type] ?? item.message; }
