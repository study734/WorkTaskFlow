import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { BrandMark } from './BrandMark';
import { useLanguage } from './LanguageContext';

type DemoView = 'home' | 'groups' | 'dashboard' | 'tasks' | 'members' | 'calendar' | 'notifications' | 'profile';
type DemoStatus = 'REQUESTED' | 'TODO' | 'IN_PROGRESS' | 'ON_HOLD' | 'COMPLETED';
type DemoTask = {
  id: number;
  ko: string;
  en: string;
  status: DemoStatus;
  priority: 'NORMAL' | 'HIGH' | 'URGENT';
  owner: string;
  due: [string, string];
  description: [string, string];
};

const tasks: DemoTask[] = [
  { id: 1, ko: 'OAuth2 로그인 회귀 테스트', en: 'OAuth2 login regression test', status: 'IN_PROGRESS', priority: 'URGENT', owner: '이개발', due: ['오늘 18:00', 'Today 18:00'], description: ['Google 로그인과 신규 가입 동의 흐름을 점검합니다.', 'Verify Google login and the new-user consent flow.'] },
  { id: 2, ko: '결제 정보 마스킹 검증', en: 'Payment data masking review', status: 'IN_PROGRESS', priority: 'URGENT', owner: '정품질', due: ['오늘 17:00', 'Today 17:00'], description: ['카드번호와 빌링키가 응답 및 로그에 노출되지 않는지 확인합니다.', 'Confirm card data and billing keys never appear in responses or logs.'] },
  { id: 3, ko: '대시보드 디자인 시스템 적용', en: 'Apply dashboard design system', status: 'IN_PROGRESS', priority: 'HIGH', owner: '한디자인', due: ['내일', 'Tomorrow'], description: ['카드, 상태 배지와 반응형 간격을 일관되게 적용합니다.', 'Apply consistent cards, badges, and responsive spacing.'] },
  { id: 4, ko: '서비스 이용약관 최종 검토', en: 'Final terms review', status: 'ON_HOLD', priority: 'HIGH', owner: '최기획', due: ['8월 4일', 'Aug 4'], description: ['출시 버전 약관과 개인정보 처리 안내를 검토합니다.', 'Review launch terms and the privacy notice.'] },
  { id: 5, ko: '운영 배포 체크리스트', en: 'Production deployment checklist', status: 'COMPLETED', priority: 'URGENT', owner: '이개발', due: ['완료', 'Completed'], description: ['백업, 헬스체크와 롤백 절차를 실제 배포 기준으로 확인했습니다.', 'Verified backups, health checks, and rollback procedures.'] },
  { id: 6, ko: '모바일 브라우저 호환성 점검', en: 'Mobile browser compatibility', status: 'TODO', priority: 'HIGH', owner: '정품질', due: ['8월 2일', 'Aug 2'], description: ['Chrome과 Safari의 핵심 사용자 흐름을 점검합니다.', 'Test core user flows in Chrome and Safari.'] },
  { id: 7, ko: '출시 공지 초안', en: 'Draft launch announcement', status: 'IN_PROGRESS', priority: 'NORMAL', owner: '최기획', due: ['8월 3일', 'Aug 3'], description: ['주요 기능과 사용 방법을 고객 관점에서 정리합니다.', 'Explain key features and usage from the customer perspective.'] },
  { id: 8, ko: '이메일 템플릿 개선 제안', en: 'Improve email templates', status: 'REQUESTED', priority: 'NORMAL', owner: '미지정', due: ['8월 7일', 'Aug 7'], description: ['가입과 비밀번호 재설정 메일의 완성도를 높입니다.', 'Improve signup and password-reset email quality.'] },
  { id: 9, ko: '알림 읽음 처리 API', en: 'Notification read-state API', status: 'TODO', priority: 'NORMAL', owner: '박백엔드', due: ['8월 5일', 'Aug 5'], description: ['알림 목록과 읽음 상태 동기화를 마무리합니다.', 'Finish notification list and read-state synchronization.'] },
  { id: 10, ko: '온보딩 와이어프레임', en: 'Onboarding wireframes', status: 'COMPLETED', priority: 'HIGH', owner: '한디자인', due: ['완료', 'Completed'], description: ['첫 로그인부터 팀 생성까지의 화면 흐름입니다.', 'Covers first login through team creation.'] },
  { id: 11, ko: '부하 테스트 결과 정리', en: 'Document load-test results', status: 'TODO', priority: 'NORMAL', owner: '정품질', due: ['8월 6일', 'Aug 6'], description: ['단일 서버 기준 병목과 안전 운영 범위를 정리합니다.', 'Document bottlenecks and safe operating limits for one server.'] },
  { id: 12, ko: '고객 문의 FAQ 작성', en: 'Write customer FAQ', status: 'IN_PROGRESS', priority: 'NORMAL', owner: '박지원', due: ['어제', 'Yesterday'], description: ['로그인, 초대와 결제 관련 질문을 정리합니다.', 'Cover common login, invitation, and payment questions.'] },
];

const statusText: Record<DemoStatus, [string, string]> = {
  REQUESTED: ['승인 대기', 'Pending'],
  TODO: ['할 일', 'To do'],
  IN_PROGRESS: ['진행 중', 'In progress'],
  ON_HOLD: ['보류', 'On hold'],
  COMPLETED: ['완료', 'Completed'],
};

export function DemoPage() {
  const { t, language, setLanguage } = useLanguage();
  const [view, setView] = useState<DemoView>('home');
  const [status, setStatus] = useState<'ALL' | DemoStatus>('ALL');
  const [selectedTask, setSelectedTask] = useState<DemoTask>(tasks[0]);
  const filtered = status === 'ALL' ? tasks : tasks.filter((task) => task.status === status);

  return <div className="demo-page">
    <aside className="demo-nav">
      <Link className="demo-brand" to="/"><span><BrandMark /></span><strong>{t('퇴사', 'toesa')}</strong></Link>
      <div className="demo-readonly"><b>● {t('읽기 전용 데모', 'Read-only demo')}</b><small>{t('API와 데이터베이스에 연결되지 않습니다.', 'No API or database connection.')}</small></div>
      <label className="demo-team-switcher"><span>{t('공간 이동', 'Switch workspace')}</span><select value={['dashboard', 'tasks', 'members'].includes(view) ? 'team' : ''} onChange={(event) => setView(event.target.value === 'team' ? 'dashboard' : 'groups')}><option value="">{t('전체 그룹 보기', 'View all groups')}</option><option value="team">◆ {t('퇴사 런칭 준비팀', 'toesa Launch Team')}</option></select><small>{t('실제 서비스와 같은 방식으로 공간을 이동합니다.', 'Switch spaces just as you would in the live service.')}</small></label>
      <nav aria-label={t('데모 메뉴', 'Demo navigation')}>
        <DemoNavButton active={view === 'home'} icon="⌂" onClick={() => setView('home')}>{t('홈', 'Home')}</DemoNavButton>
        <DemoNavButton active={['groups', 'dashboard', 'tasks', 'members'].includes(view)} icon="♧" onClick={() => setView('groups')}>{t('그룹', 'Groups')}</DemoNavButton>
        <DemoNavButton active={view === 'calendar'} icon="□" onClick={() => setView('calendar')}>{t('캘린더', 'Calendar')}</DemoNavButton>
        <DemoNavButton active={view === 'notifications'} icon="♢" onClick={() => setView('notifications')}>{t('알림', 'Notifications')}<i>5</i></DemoNavButton>
        <DemoNavButton active={view === 'profile'} icon="○" onClick={() => setView('profile')}>{t('프로필', 'Profile')}</DemoNavButton>
      </nav>
      <div className="demo-profile"><span>김</span><div><strong>{t('김서준 팀장', 'Seo-jun Kim')}</strong><small>demo@totaskflow.local</small></div></div>
    </aside>

    <main className="demo-main">
      <header className="demo-topbar">
        <div><b>{t('제품 체험', 'Product tour')}</b><span>{t('화면을 자유롭게 둘러보세요. 저장되는 내용은 없습니다.', 'Explore freely. Nothing is saved.')}</span></div>
        <div><button type="button" onClick={() => setLanguage(language === 'ko' ? 'en' : 'ko')}>{language === 'ko' ? 'EN' : '한글'}</button><Link to="/">{t('랜딩으로', 'Back')}</Link><Link className="demo-start" to="/login">{t('내 계정으로 시작', 'Start with my account')} →</Link></div>
      </header>

      {view === 'home' && <DemoHome onOpenGroup={() => setView('dashboard')} onOpenTasks={() => setView('tasks')} onOpenAlerts={() => setView('notifications')} />}
      {view === 'groups' && <DemoGroups onOpenGroup={() => setView('dashboard')} onOpenMembers={() => setView('members')} />}
      {view === 'dashboard' && <DemoDashboard onOpenTasks={() => setView('tasks')} onOpenMembers={() => setView('members')} onSelect={(task) => { setSelectedTask(task); setView('tasks'); }} />}
      {view === 'tasks' && <DemoTasks filtered={filtered} selected={selectedTask} status={status} onStatus={setStatus} onSelect={setSelectedTask} />}
      {view === 'members' && <DemoMembers onBack={() => setView('dashboard')} onOpenTasks={() => setView('tasks')} />}
      {view === 'calendar' && <DemoCalendar />}
      {view === 'notifications' && <DemoNotifications onOpenTask={(task) => { setSelectedTask(task); setView('tasks'); }} />}
      {view === 'profile' && <DemoProfile />}
    </main>
  </div>;
}

function DemoNavButton({ active, icon, onClick, children }: { active: boolean; icon: string; onClick: () => void; children: ReactNode }) {
  return <button type="button" className={active ? 'active' : ''} onClick={onClick}><span aria-hidden="true">{icon}</span>{children}</button>;
}

function DemoHome({ onOpenGroup, onOpenTasks, onOpenAlerts }: { onOpenGroup: () => void; onOpenTasks: () => void; onOpenAlerts: () => void }) {
  const { t, language } = useLanguage();
  return <section className="demo-view">
    <header className="demo-view-heading"><div><span className="page-eyebrow">HOME</span><h1>{t('김서준님, 오늘도 반가워요.', 'Welcome back, Seo-jun.')}</h1><p>{t('실제 로그인 후에는 먼저 개인 홈에서 내 업무와 참여 중인 그룹을 확인합니다.', 'After signing in, your personal home shows your work and the groups you belong to.')}</p></div></header>
    <div className="demo-kpis demo-home-kpis"><article><span>{t('내 담당 업무', 'My assigned tasks')}</span><strong>6</strong><small>{t('진행 중 4건', '4 active')}</small></article><article><span>{t('마감 임박', 'Due soon')}</span><strong>2</strong><small>{t('오늘 확인', 'Review today')}</small></article><article><span>{t('참여 그룹', 'Groups')}</span><strong>1</strong><small>{t('팀장으로 참여', 'Joined as leader')}</small></article><article><span>{t('미확인 알림', 'Unread alerts')}</span><strong>5</strong><small>{t('새 소식', 'New updates')}</small></article></div>
    <div className="demo-home-grid">
      <article className="demo-panel demo-home-group"><header><div><span>SHORTCUTS</span><h2>{t('바로가기', 'Shortcuts')}</h2></div></header><button type="button" onClick={onOpenGroup}><span>퇴</span><div><strong>{t('퇴사 런칭 준비팀', 'toesa Launch Team')}</strong><small>{t('팀 대시보드 열기', 'Open group dashboard')}</small></div><b>›</b></button></article>
      <article className="demo-panel demo-priority-panel"><header><div><span>MY PRIORITY</span><h2>{t('내 우선 업무', 'Priority tasks')}</h2></div><button type="button" onClick={onOpenTasks}>{t('전체 보기', 'View all')} →</button></header>{tasks.slice(0, 4).map((task) => <button className="demo-task-row" type="button" onClick={onOpenTasks} key={task.id}><span className={`demo-status ${task.status.toLowerCase()}`}>{statusText[task.status][language === 'ko' ? 0 : 1]}</span><strong>{language === 'ko' ? task.ko : task.en}</strong><small>{task.owner}</small><time>{task.due[language === 'ko' ? 0 : 1]}</time></button>)}</article>
      <article className="demo-panel demo-upcoming"><header><div><span>SCHEDULE</span><h2>{t('다가오는 일정', 'Upcoming events')}</h2></div></header><div><span><b>31</b><small>JUL</small></span><p><strong>{t('주간 진행 공유', 'Weekly progress sync')}</strong><small>10:00 · {t('퇴사 런칭 준비팀', 'toesa Launch Team')}</small></p></div><div><span><b>01</b><small>AUG</small></span><p><strong>{t('출시 전 보안 점검', 'Pre-launch security review')}</strong><small>14:00 · Online</small></p></div></article>
      <article className="demo-panel demo-home-alerts"><header><div><span>ALERTS</span><h2>{t('미확인 알림', 'Unread alerts')}</h2></div><button type="button" onClick={onOpenAlerts}>{t('전체 보기', 'View all')} →</button></header><button type="button" onClick={onOpenAlerts}><strong>{t('업무 마감 임박', 'Task due soon')}</strong><small>{t('결제 정보 마스킹 검증이 오늘 마감됩니다.', 'Payment data masking review is due today.')}</small></button><button type="button" onClick={onOpenAlerts}><strong>{t('새 업무 승인 요청', 'New approval request')}</strong><small>{t('이메일 템플릿 개선 제안이 승인을 기다립니다.', 'The email template proposal is waiting for approval.')}</small></button></article>
    </div>
  </section>;
}

function DemoGroups({ onOpenGroup, onOpenMembers }: { onOpenGroup: () => void; onOpenMembers: () => void }) {
  const { t } = useLanguage();
  return <section className="demo-view">
    <header className="demo-view-heading"><div><span className="page-eyebrow">GROUPS</span><h1>{t('그룹', 'Groups')}</h1><p>{t('실제 서비스처럼 참여 중인 팀을 선택해 대시보드와 팀원 목록으로 이동합니다.', 'Choose a team to open its dashboard or member directory, just like in the live service.')}</p></div><div className="demo-heading-actions"><button type="button" disabled>{t('그룹 키로 참여', 'Join with key')}</button><button type="button" disabled>＋ {t('새 그룹', 'New group')}</button></div></header>
    <section className="demo-groups-card"><header><div><h2>{t('참여 중인 그룹', 'Your groups')}</h2><p>{t('1개의 그룹', '1 group')}</p></div></header><article><button className="demo-group-main" type="button" onClick={onOpenGroup}><span>퇴</span><div><small>{t('무료 그룹 · 팀장', 'Free group · Leader')}</small><strong>{t('퇴사 런칭 준비팀', 'toesa Launch Team')}</strong><p>{t('출시 준비와 운영 점검을 함께 관리하는 팀입니다.', 'A team managing launch preparation and operations review.')}</p></div></button><footer><button type="button" onClick={onOpenMembers}>{t('팀원 보기', 'Members')}</button><button type="button" disabled>{t('설정', 'Settings')}</button></footer></article></section>
  </section>;
}

const demoMembers = [
  ['김서준', 'LEADER', '2026. 7. 1.'], ['이개발', 'MEMBER', '2026. 7. 2.'], ['한디자인', 'MEMBER', '2026. 7. 2.'],
  ['최기획', 'MEMBER', '2026. 7. 3.'], ['정품질', 'MEMBER', '2026. 7. 3.'], ['박지원', 'MEMBER', '2026. 7. 4.'],
] as const;

function DemoMembers({ onBack, onOpenTasks }: { onBack: () => void; onOpenTasks: () => void }) {
  const { t } = useLanguage();
  return <section className="demo-view">
    <header className="demo-view-heading"><div><button className="demo-back-link" type="button" onClick={onBack}>← {t('그룹 대시보드', 'Group dashboard')}</button><span className="page-eyebrow">TEAM MEMBERS</span><h1>{t('팀원', 'Team members')}</h1><p>{t('퇴사 런칭 준비팀에서 함께하는 사람과 역할을 확인하세요.', 'See everyone working in the toesa Launch Team and their roles.')}</p></div><div className="demo-heading-actions"><button type="button" onClick={onOpenTasks}>{t('업무 보기', 'View tasks')}</button><button type="button" disabled>{t('초대·설정', 'Invites & settings')}</button></div></header>
    <div className="demo-member-summary"><article><span>{t('전체 팀원', 'All members')}</span><strong>6</strong></article><article><span>{t('팀장', 'Leaders')}</span><strong>1</strong></article><article><span>{t('팀원', 'Members')}</span><strong>5</strong></article></div>
    <section className="demo-members-panel"><header><div><h2>{t('팀원 목록', 'Member list')}</h2><p>{t('데모에서는 정보만 볼 수 있습니다.', 'The demo only displays member information.')}</p></div><input type="search" readOnly placeholder={t('이름으로 검색', 'Search by name')} /></header><div>{demoMembers.map(([name, role, joined], index) => <article key={name}><span>{name.slice(0, 1)}</span><div><strong>{name}</strong><small>{t(`${joined} 참여`, `Joined ${joined}`)}</small></div><b className={role.toLowerCase()}>{role === 'LEADER' ? t('팀장', 'Leader') : t('팀원', 'Member')}</b>{index === 0 && <i>{t('나', 'Me')}</i>}</article>)}</div><p className="demo-readonly-hint">{t('역할 변경, 초대와 내보내기는 읽기 전용 데모에서 비활성화됩니다.', 'Role changes, invitations, and removals are disabled in the read-only demo.')}</p></section>
  </section>;
}

function DemoProfile() {
  const { t } = useLanguage();
  return <section className="demo-view"><header className="demo-view-heading"><div><span className="page-eyebrow">PROFILE</span><h1>{t('프로필', 'Profile')}</h1><p>{t('실제 서비스에서 사용하는 계정 정보 화면을 읽기 전용으로 보여줍니다.', 'This is a read-only preview of the account profile used in the live service.')}</p></div></header><section className="demo-profile-card"><div className="demo-profile-avatar">김</div><div><span>{t('이름', 'Name')}</span><strong>{t('김서준', 'Seo-jun Kim')}</strong></div><div><span>{t('아이디', 'Username')}</span><strong>demo_manager</strong></div><div><span>{t('이메일', 'Email')}</span><strong>demo@totaskflow.local</strong></div><div><span>{t('언어', 'Language')}</span><strong>{t('한국어', 'English')}</strong></div><button type="button" disabled>{t('프로필 수정', 'Edit profile')}</button></section></section>;
}

function DemoDashboard({ onOpenTasks, onOpenMembers, onSelect }: { onOpenTasks: () => void; onOpenMembers: () => void; onSelect: (task: DemoTask) => void }) {
  const { t, language } = useLanguage();
  const label = (pair: [string, string]) => pair[language === 'ko' ? 0 : 1];
  return <section className="demo-view">
    <header className="demo-view-heading"><div><span className="page-eyebrow">GROUP DASHBOARD</span><h1>{t('퇴사 런칭 준비팀', 'toesa Launch Team')}</h1><p>{t('실제 그룹 대시보드처럼 기간 업무, 팀원별 담당 현황과 리포트를 확인합니다.', 'Review period work, member workload, and reports as you would on a live group dashboard.')}</p></div><div className="demo-heading-actions"><button type="button" onClick={onOpenTasks}>{t('업무', 'Tasks')}</button><button type="button" onClick={onOpenMembers}>{t('팀원', 'Members')}</button><button type="button" disabled>⚙</button></div></header>
    <div className="demo-period"><label><span>{t('연도', 'Year')}</span><select defaultValue="2026"><option>2026</option></select></label><label><span>{t('월', 'Month')}</span><select defaultValue="7"><option value="7">{t('7월', 'July')}</option></select></label><label><span>{t('주차', 'Week')}</span><select defaultValue="0"><option value="0">{t('월 전체', 'Full month')}</option></select></label><button type="button">{t('이번 달', 'This month')}</button></div>
    <div className="demo-kpis">
      <article><span>{t('전체 업무', 'Total tasks')}</span><strong>32</strong><small>+6 {t('이번 주', 'this week')}</small></article>
      <article><span>{t('진행 중', 'In progress')}</span><strong>8</strong><small>25%</small></article>
      <article><span>{t('완료', 'Completed')}</span><strong>19</strong><small>↗ 12%</small></article>
      <article className="risk"><span>{t('마감 임박', 'Due soon')}</span><strong>3</strong><small>{t('확인 필요', 'Needs review')}</small></article>
      <article><span>{t('완료율', 'Completion')}</span><strong>76%</strong><small>{t('지난주 71%', '71% last week')}</small></article>
    </div>
    <div className="demo-dashboard-grid">
      <article className="demo-panel demo-priority-panel"><header><div><span>MY PRIORITY</span><h2>{t('먼저 볼 업무', 'Priority work')}</h2></div><button type="button" onClick={onOpenTasks}>{t('전체 보기', 'View all')} →</button></header>{tasks.slice(0, 5).map((task) => <button className="demo-task-row" type="button" onClick={() => onSelect(task)} key={task.id}><span className={`demo-status ${task.status.toLowerCase()}`}>{label(statusText[task.status])}</span><strong>{language === 'ko' ? task.ko : task.en}</strong><small>{task.owner}</small><time>{label(task.due)}</time></button>)}</article>
      <article className="demo-panel demo-flow-panel"><header><div><span>WORKFLOW</span><h2>{t('업무 상태', 'Task status')}</h2></div><b>32</b></header><div className="demo-donut"><strong>76<small>%</small></strong></div><ul><li><i className="requested" />{t('승인 대기', 'Pending')}<b>2</b></li><li><i className="todo" />{t('할 일', 'To do')}<b>7</b></li><li><i className="progress" />{t('진행 중', 'In progress')}<b>8</b></li><li><i className="hold" />{t('보류', 'On hold')}<b>2</b></li><li><i className="done" />{t('완료', 'Completed')}<b>13</b></li></ul></article>
      <article className="demo-panel demo-workload"><header><div><span>TEAM</span><h2>{t('팀 업무량', 'Team workload')}</h2></div><small>{t('최근 30일', 'Last 30 days')}</small></header><div>{[['김서준', 82, 6], ['이개발', 68, 5], ['한디자인', 54, 4], ['최기획', 46, 3], ['정품질', 75, 5], ['박지원', 39, 2]].map(([name, width, count]) => <div className="demo-member-load" key={String(name)}><span>{String(name).slice(0, 1)}</span><strong>{name}</strong><i><b style={{ width: `${width}%` }} /></i><small>{count}{t('개', '')}</small></div>)}</div></article>
      <article className="demo-panel demo-upcoming"><header><div><span>SCHEDULE</span><h2>{t('다가오는 일정', 'Upcoming')}</h2></div></header><div><span><b>31</b><small>JUL</small></span><p><strong>{t('주간 진행 공유', 'Weekly progress sync')}</strong><small>10:00 · {t('회의실 A', 'Room A')}</small></p></div><div><span><b>01</b><small>AUG</small></span><p><strong>{t('출시 전 보안 점검', 'Pre-launch security review')}</strong><small>14:00 · Online</small></p></div><div><span><b>04</b><small>AUG</small></span><p><strong>{t('고객 피드백 리뷰', 'Customer feedback review')}</strong><small>11:00 · {t('회의실 B', 'Room B')}</small></p></div></article>
    </div>
    <article className="demo-panel demo-report-panel"><header><div><span>REPORTS</span><h2>{t('업무 리포트', 'Task reports')}</h2><p>{t('실제 서비스에서는 저장된 업무 데이터로 한글·영문 리포트를 만들고, 메일 발송 일정을 관리합니다.', 'The live service creates Korean and English reports from stored task data and manages scheduled email delivery.')}</p></div><b>{t('무료 그룹', 'Free group')}</b></header><div><label><span>{t('범위', 'Scope')}</span><select defaultValue="MY"><option value="MY">{t('내 업무', 'My tasks')}</option><option value="GROUP">{t('그룹 전체', 'Whole group')}</option></select></label><label><span>{t('기간', 'Period')}</span><select defaultValue="WEEKLY"><option value="WEEKLY">{t('주간', 'Weekly')}</option><option value="MONTHLY">{t('월간', 'Monthly')}</option><option value="YEARLY">{t('연간', 'Yearly')}</option></select></label><button type="button" disabled>{t('리포트 생성', 'Generate report')}</button></div><small>🔒 {t('읽기 전용 데모에서는 파일 생성과 메일 발송이 실행되지 않습니다.', 'File generation and email delivery do not run in the read-only demo.')}</small></article>
  </section>;
}

function DemoTasks({ filtered, selected, status, onStatus, onSelect }: { filtered: DemoTask[]; selected: DemoTask; status: 'ALL' | DemoStatus; onStatus: (status: 'ALL' | DemoStatus) => void; onSelect: (task: DemoTask) => void }) {
  const { t, language } = useLanguage();
  const label = (pair: [string, string]) => pair[language === 'ko' ? 0 : 1];
  const filters: Array<'ALL' | DemoStatus> = ['ALL', 'REQUESTED', 'TODO', 'IN_PROGRESS', 'ON_HOLD', 'COMPLETED'];
  return <section className="demo-view">
    <header className="demo-view-heading"><div><span className="page-eyebrow">TASKS</span><h1>{t('팀 업무', 'Team tasks')}</h1><p>{t('업무를 선택하면 체크리스트와 대화 맥락까지 확인할 수 있습니다.', 'Select a task to inspect its checklist and conversation context.')}</p></div><button type="button" disabled>＋ {t('업무 요청', 'Request task')}</button></header>
    <div className="demo-filter">{filters.map((value) => <button className={status === value ? 'active' : ''} type="button" onClick={() => onStatus(value)} key={value}>{value === 'ALL' ? t('전체', 'All') : label(statusText[value])}<small>{value === 'ALL' ? tasks.length : tasks.filter((task) => task.status === value).length}</small></button>)}</div>
    <div className="demo-task-layout"><div className="demo-task-list">{filtered.map((task) => <button type="button" className={selected.id === task.id ? 'selected' : ''} onClick={() => onSelect(task)} key={task.id}><div><span className={`demo-status ${task.status.toLowerCase()}`}>{label(statusText[task.status])}</span><span className={`demo-priority ${task.priority.toLowerCase()}`}>{task.priority}</span></div><strong>{language === 'ko' ? task.ko : task.en}</strong><p>{label(task.description)}</p><footer><span>○ {task.owner}</span><time>◷ {label(task.due)}</time></footer></button>)}</div><aside className="demo-task-detail"><div className="demo-detail-lock">🔒 <span><b>{t('읽기 전용', 'Read only')}</b><small>{t('모든 변경 기능이 비활성화되어 있습니다.', 'All mutation controls are disabled.')}</small></span></div><span className={`demo-status ${selected.status.toLowerCase()}`}>{label(statusText[selected.status])}</span><h2>{language === 'ko' ? selected.ko : selected.en}</h2><p>{label(selected.description)}</p><dl><div><dt>{t('담당자', 'Owner')}</dt><dd>{selected.owner}</dd></div><div><dt>{t('마감', 'Due')}</dt><dd>{label(selected.due)}</dd></div><div><dt>{t('우선순위', 'Priority')}</dt><dd>{selected.priority}</dd></div></dl><section><h3>{t('체크리스트', 'Checklist')} <small>2/3</small></h3><label><input type="checkbox" checked readOnly />{t('요청·응답 필드 확인', 'Review request/response fields')}</label><label><input type="checkbox" checked readOnly />{t('운영 로그 마스킹 확인', 'Verify production log masking')}</label><label><input type="checkbox" readOnly />{t('최종 결과 공유', 'Share final result')}</label></section><section><h3>{t('최근 댓글', 'Recent comments')}</h3><blockquote><b>김서준</b><p>{t('민감 정보가 화면과 로그에 남지 않는지 마지막으로 확인해 주세요.', 'Please make one final check that sensitive data never remains on screen or in logs.')}</p><small>{t('35분 전', '35 min ago')}</small></blockquote><blockquote><b>{selected.owner}</b><p>{t('확인 중입니다. 완료 후 결과를 공유하겠습니다.', 'Reviewing now. I will share the result when complete.')}</p><small>{t('18분 전', '18 min ago')}</small></blockquote></section></aside></div>
  </section>;
}

function DemoCalendar() {
  const { t, language } = useLanguage();
  const [monthOffset, setMonthOffset] = useState(0);
  const days = useMemo(() => {
    const today = new Date();
    const month = new Date(today.getFullYear(), today.getMonth() + monthOffset, 1, 12);
    const start = new Date(month);
    start.setDate(1 - start.getDay());
    return Array.from({ length: 42 }, (_, index) => {
      const date = new Date(start);
      date.setDate(start.getDate() + index);
      return date;
    });
  }, [monthOffset]);
  const visibleMonth = new Date(new Date().getFullYear(), new Date().getMonth() + monthOffset, 1);
  const eventForDay = (day: Date) => {
    if (day.getMonth() !== visibleMonth.getMonth()) return [];
    const values = [
      [4, '10:00', t('주간 진행 공유', 'Weekly sync'), 'meeting'],
      [8, '14:00', t('출시 전 보안 점검', 'Security review'), 'schedule'],
      [12, '18:00', t('OAuth2 회귀 테스트 마감', 'OAuth2 test due'), 'task'],
      [18, '11:00', t('고객 피드백 리뷰', 'Customer feedback review'), 'meeting'],
      [23, '15:30', t('결제 보안 검토', 'Payment security review'), 'task'],
      [27, '14:00', t('발표 리허설', 'Presentation rehearsal'), 'schedule'],
    ] as const;
    return values.filter(([date]) => date === day.getDate());
  };
  return <section className="demo-view"><header className="demo-view-heading"><div><span className="page-eyebrow">CALENDAR</span><h1>{t('캘린더', 'Calendar')}</h1><p>{t('실제 서비스와 같은 월간 화면에서 팀 일정과 업무 마감을 함께 확인합니다.', 'View team events and task deadlines together in the same monthly layout as the live service.')}</p></div><button type="button" disabled>＋ {t('일정 추가', 'Add event')}</button></header><div className="demo-calendar-toolbar"><button type="button" onClick={() => setMonthOffset((value) => value - 1)} aria-label={t('이전 달', 'Previous month')}>‹</button><strong>{visibleMonth.toLocaleDateString(language === 'ko' ? 'ko-KR' : 'en-US', { month: 'long', year: 'numeric' })}</strong><button type="button" onClick={() => setMonthOffset((value) => value + 1)} aria-label={t('다음 달', 'Next month')}>›</button><button className="demo-calendar-today" type="button" onClick={() => setMonthOffset(0)}>{t('오늘', 'Today')}</button><span><i className="meeting" />{t('회의', 'Meeting')}<i className="schedule" />{t('일정', 'Event')}<i className="task" />{t('업무 마감', 'Task due')}</span></div><div className="demo-calendar month-view">{days.map((day) => <article className={`${day.toDateString() === new Date().toDateString() ? 'today ' : ''}${day.getMonth() !== visibleMonth.getMonth() ? 'outside' : ''}`} key={day.toISOString()}><header><span>{day.toLocaleDateString(language === 'ko' ? 'ko-KR' : 'en-US', { weekday: 'short' })}</span><b>{day.getDate()}</b></header>{eventForDay(day).map(([, time, title, type]) => <div className={type} key={`${time}-${title}`}><time>{time}</time><strong>{title}</strong><small>{type === 'meeting' ? t('팀 일정', 'Team event') : type === 'task' ? t('업무 마감', 'Task due') : t('공유 일정', 'Shared event')}</small></div>)}</article>)}</div></section>;
}

function DemoNotifications({ onOpenTask }: { onOpenTask: (task: DemoTask) => void }) {
  const { t } = useLanguage();
  const items = [
    ['urgent', t('업무 마감 임박', 'Task due soon'), t('결제 정보 마스킹 검증 업무가 오늘 마감됩니다.', 'Payment data masking review is due today.'), '12분 전', tasks[1]],
    ['mention', t('댓글에서 회원님을 언급했습니다.', 'You were mentioned in a comment.'), t('OAuth2 로그인 회귀 테스트에서 새 댓글을 확인하세요.', 'Review a new comment in the OAuth2 regression test.'), '35분 전', tasks[0]],
    ['status', t('업무가 진행 중으로 변경됐습니다.', 'Task moved to in progress.'), t('한디자인님이 대시보드 디자인 시스템 적용을 시작했습니다.', 'Han Design started applying the dashboard design system.'), '1시간 전', tasks[2]],
    ['request', t('새 업무 승인 요청', 'New approval request'), t('이메일 템플릿 개선 제안이 승인을 기다리고 있습니다.', 'The email template proposal is waiting for approval.'), '2시간 전', tasks[7]],
    ['hold', t('업무가 보류됐습니다.', 'Task placed on hold.'), t('서비스 이용약관 최종 검토가 법무 회신을 기다립니다.', 'Final terms review is waiting for legal feedback.'), '4시간 전', tasks[3]],
    ['done', t('업무가 완료됐습니다.', 'Task completed.'), t('운영 배포 체크리스트가 완료 처리됐습니다.', 'The production deployment checklist was completed.'), '어제', tasks[4]],
  ] as const;
  return <section className="demo-view"><header className="demo-view-heading"><div><span className="page-eyebrow">NOTIFICATIONS</span><h1>{t('알림', 'Notifications')}</h1><p>{t('승인, 상태 변경, 댓글과 마감 알림을 한곳에서 확인합니다.', 'Review approvals, status changes, comments, and due-date alerts.')}</p></div><button type="button" disabled>{t('모두 읽음', 'Mark all read')}</button></header><div className="demo-notification-list">{items.map(([type, title, message, time, task], index) => <button type="button" className={index < 4 ? 'unread' : ''} onClick={() => onOpenTask(task)} key={title}><span className={type}>{type === 'urgent' ? '!' : type === 'mention' ? '@' : type === 'done' ? '✓' : '↗'}</span><div><strong>{title}</strong><p>{message}</p><small>{t(time, time === '어제' ? 'Yesterday' : time.replace('분 전', ' min ago').replace('시간 전', ' hr ago'))}</small></div><i>›</i></button>)}</div></section>;
}
