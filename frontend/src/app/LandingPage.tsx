import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../api/authApi';
import { accessToken, errorMessage, sessionMode } from '../api/client';
import { BrandMark } from './BrandMark';
import { useLanguage } from './LanguageContext';
import { isPwaInstallAvailable, isRunningStandalone, promptPwaInstall } from './pwa';

export function LandingPage() {
  const { t, language, setLanguage } = useLanguage();
  const navigate = useNavigate();
  const [demoPending, setDemoPending] = useState(false);
  const [demoError, setDemoError] = useState('');
  const [installOpen, setInstallOpen] = useState(false);
  const [installable, setInstallable] = useState(isPwaInstallAvailable());
  const installed = isRunningStandalone();

  useEffect(() => {
    const sync = () => setInstallable(isPwaInstallAvailable());
    window.addEventListener('pwa-install-available', sync);
    window.addEventListener('pwa-installed', sync);
    return () => {
      window.removeEventListener('pwa-install-available', sync);
      window.removeEventListener('pwa-installed', sync);
    };
  }, []);

  async function startDemo() {
    setDemoPending(true);
    setDemoError('');
    try {
      const token = await authApi.demo();
      accessToken.set(token.accessToken);
      sessionMode.setDemo();
      navigate('/app');
    } catch (error) {
      setDemoError(errorMessage(error));
    } finally {
      setDemoPending(false);
    }
  }

  async function install() {
    if (installable) {
      await promptPwaInstall();
      setInstallOpen(false);
      return;
    }
    setInstallOpen(true);
  }

  return <main className="landing-page">
    <header className="landing-nav">
      <Link to="/" className="landing-brand"><BrandMark /><strong>ToTaskFlow</strong></Link>
      <nav aria-label={t('랜딩 페이지 메뉴', 'Landing navigation')}>
        <Link to="/product">{t('제품', 'Product')}</Link>
        <Link to="/b2b">{t('B2B 솔루션', 'B2B solutions')}</Link>
        <Link to="/pricing">{t('가격', 'Pricing')}</Link>
        <Link to="/contact">{t('문의', 'Contact')}</Link>
        <button type="button" className="landing-language" onClick={() => setLanguage(language === 'ko' ? 'en' : 'ko')}>{language === 'ko' ? 'EN' : '한글'}</button>
        <Link to="/login">{t('로그인', 'Log in')}</Link>
        <Link className="landing-nav-cta" to="/login">{t('무료로 시작', 'Get started')}</Link>
      </nav>
    </header>

    <section className="landing-hero">
      <div className="landing-eyebrow"><span />{t('팀의 일을 한 흐름으로', 'One flow for your team')}</div>
      <h1>{t('흩어진 업무와 일정을', 'Bring tasks and schedules')}<br /><em>{t('하나의 작업 공간으로.', 'into one calm workspace.')}</em></h1>
      <p>{t('요청, 승인, 담당자 선택, 진행 상황과 캘린더까지. 팀이 지금 무엇을 해야 하는지 누구나 같은 화면에서 확인하세요.', 'Requests, approvals, ownership, progress, and calendar—give everyone one clear view of what comes next.')}</p>
      <div className="landing-hero-actions">
        <Link className="landing-primary" to="/login">{t('무료로 시작하기', 'Start for free')} <span>→</span></Link>
        <button className="landing-demo" type="button" disabled={demoPending} onClick={startDemo}>{demoPending ? t('데모 준비 중...', 'Opening demo...') : t('김팀장의 데모 둘러보기', 'Explore the manager demo')} <span>↗</span></button>
      </div>
      <small>{t('데모는 공용 읽기 전용 환경입니다. 실제 개인정보를 입력하지 마세요.', 'The demo is shared and read-only. Do not enter real personal information.')}</small>
      {demoError && <p className="landing-error">{demoError}</p>}
    </section>

    <section className="landing-product" aria-label={t('제품 화면 미리보기', 'Product preview')}>
      <div className="landing-window-bar"><i /><i /><i /><span>worktaskflow.app/app</span></div>
      <div className="landing-product-body">
        <aside><div className="mock-brand"><BrandMark />ToTaskFlow</div><span className="active">⌂ {t('홈', 'Home')}</span><span>♧ {t('그룹', 'Groups')}</span><span>□ {t('캘린더', 'Calendar')}</span><span>♢ {t('알림', 'Alerts')}</span><b>{t('로컬 알파 시연팀', 'Alpha demo team')}</b></aside>
        <div className="mock-dashboard"><header><div><small>TODAY</small><h2>{t('김팀장님, 오늘도 반가워요!', 'Welcome back, Team Lead Kim!')}</h2><p>{t('중요한 일부터 하나씩 시작해 볼까요?', 'Start with what matters most today.')}</p></div><span className="mock-avatar">김</span></header>
          <div className="mock-metrics"><article><small>{t('진행 중', 'In progress')}</small><strong>8</strong><span>↗ 12%</span></article><article><small>{t('완료', 'Completed')}</small><strong>24</strong><span>75%</span></article><article><small>{t('마감 임박', 'Due soon')}</small><strong>3</strong><span className="warning">{t('확인 필요', 'Review')}</span></article></div>
          <div className="mock-columns"><article><h3>{t('내 우선 업무', 'Priority tasks')}</h3><MockTask color="violet" title={t('모바일 화면 최종 점검', 'Final mobile review')} meta={t('오늘 18:00 마감', 'Due today 18:00')} /><MockTask color="blue" title={t('발표 자료 초안 작성', 'Draft presentation')} meta={t('내일 마감', 'Due tomorrow')} /><MockTask color="orange" title={t('외부 피드백 반영', 'Apply external feedback')} meta={t('보류 중', 'On hold')} /></article><article><h3>{t('다가오는 일정', 'Upcoming')}</h3><div className="mock-event"><b>26</b><span><strong>{t('주간 진행 공유', 'Weekly sync')}</strong><small>10:00 · {t('회의실 A', 'Room A')}</small></span></div><div className="mock-event"><b>28</b><span><strong>{t('발표 리허설', 'Presentation rehearsal')}</strong><small>14:00 · Online</small></span></div></article></div>
        </div>
      </div>
    </section>

    <section id="features" className="landing-section">
      <span className="landing-section-label">{t('한곳에서 끝내기', 'Everything connected')}</span>
      <h2>{t('팀의 속도를 늦추는 빈틈을 없애세요.', 'Remove the gaps that slow work down.')}</h2>
      <div className="landing-feature-grid">
        <article><b>01</b><h3>{t('요청부터 담당까지', 'Request to ownership')}</h3><p>{t('팀원이 업무를 제안하고 팀장이 승인하면, 적합한 팀원이 직접 담당 업무를 선택합니다.', 'Members propose work, leads approve it, and the right teammate can claim ownership.')}</p></article>
        <article><b>02</b><h3>{t('상태가 보이는 협업', 'Visible progress')}</h3><p>{t('체크리스트, 댓글, 멘션과 상태 이력이 한 업무 안에 쌓여 맥락을 잃지 않습니다.', 'Checklists, comments, mentions, and history stay attached to the work itself.')}</p></article>
        <article><b>03</b><h3>{t('업무와 일정 연결', 'Tasks meet calendar')}</h3><p>{t('마감 업무와 팀 일정을 같은 캘린더에서 확인하고 중요한 알림을 놓치지 않습니다.', 'See deadlines and team events together, with alerts for what needs attention.')}</p></article>
      </div>
    </section>

    <section className="landing-ai-sample" aria-labelledby="ai-sample-title">
      <div className="landing-ai-sample-heading">
        <span className="landing-section-label">{t('유료 플랜 미리보기 · 예시 데이터', 'Paid plan preview · Sample data')}</span>
        <h2 id="ai-sample-title">{t('숫자를 나열하는 리포트에서, 다음 행동이 보이는 리포트로.', 'From a list of numbers to a report with clear next actions.')}</h2>
        <p>{t('아래 내용은 고정된 예시이며 공개 OpenAI API를 호출하지 않습니다.', 'This is a fixed example and does not call the OpenAI API.')}</p>
      </div>
      <div className="landing-ai-compare">
        <article>
          <small>{t('기본 리포트 · AI 미사용', 'Basic report · No AI')}</small>
          <h3>{t('주간 업무 현황', 'Weekly task metrics')}</h3>
          <dl><div><dt>{t('업무', 'Tasks')}</dt><dd>18</dd></div><div><dt>{t('완료율', 'Completion')}</dt><dd>72%</dd></div><div><dt>{t('지연', 'Overdue')}</dt><dd>3</dd></div></dl>
          <p>{t('기간별 확정 통계와 업무 목록을 PDF로 저장합니다.', 'Save finalized period metrics and task lists as PDF.')}</p>
        </article>
        <article className="paid-preview">
          <small>{t('AI 주간 리포트 · 유료', 'AI weekly report · Paid')}</small>
          <h3>{t('완료 흐름은 안정적이지만 지연 업무 확인이 필요합니다.', 'Completion is steady, but overdue work needs attention.')}</h3>
          <ul>
            <li>{t('핵심 흐름: 완료율과 기한 준수율을 함께 설명', 'Highlight: explains completion and on-time trends together')}</li>
            <li>{t('위험: 서버가 판정한 지연 근거만 인용', 'Risk: cites only server-determined overdue evidence')}</li>
            <li>{t('다음 행동: 다음 주 우선 확인 항목 제안', 'Next action: suggests what to check first next week')}</li>
          </ul>
          <p>{t('업무 제목·댓글·닉네임 없이 비식별 확정 통계만 사용합니다.', 'Uses only de-identified finalized metrics, without titles, comments, or nicknames.')}</p>
        </article>
      </div>
    </section>

    <section id="workflow" className="landing-workflow">
      <div><span className="landing-section-label">{t('가볍게 시작하기', 'Start lightly')}</span><h2>{t('설치 없이 시작하고, 필요할 때 앱으로.', 'Start in the browser. Make it an app when ready.')}</h2><p>{t('회원가입 후 바로 브라우저에서 사용할 수 있습니다. 자주 사용한다면 별도 앱스토어 없이 현재 기기에 PWA 앱을 만들 수 있습니다.', 'Use it immediately after signing up. If it becomes part of your routine, add the PWA to your device without an app store.')}</p><button type="button" onClick={() => setInstallOpen(true)} disabled={installed}>{installed ? t('이미 앱으로 사용 중', 'Already installed') : t('이 기기에 APP 만들기', 'Make this an app')}</button></div>
      <ol><li><b>1</b><span><strong>{t('그룹 만들기', 'Create a group')}</strong><small>{t('멤버를 초대하고 역할을 정합니다.', 'Invite members and set roles.')}</small></span></li><li><b>2</b><span><strong>{t('업무 흐름 연결', 'Connect the workflow')}</strong><small>{t('요청·승인·담당·완료를 기록합니다.', 'Track request, approval, ownership, and completion.')}</small></span></li><li><b>3</b><span><strong>{t('한눈에 확인', 'Stay aligned')}</strong><small>{t('대시보드와 캘린더로 지금을 봅니다.', 'See the present in dashboards and calendars.')}</small></span></li></ol>
    </section>

    <section className="landing-final-cta"><BrandMark /><h2>{t('오늘의 업무를 더 선명하게.', 'Make today’s work clearer.')}</h2><p>{t('팀의 다음 행동이 보이는 작업 공간을 지금 시작하세요.', 'Start a workspace where the next action is always visible.')}</p><div><Link to="/login">{t('무료로 시작하기', 'Start for free')} →</Link><button type="button" onClick={startDemo}>{t('데모 보기', 'View demo')}</button></div></section>
    <footer><Link to="/" className="landing-brand"><BrandMark /><strong>ToTaskFlow</strong></Link><nav><Link to="/privacy">{t('개인정보 처리방침', 'Privacy')}</Link><Link to="/terms">{t('이용약관', 'Terms')}</Link><Link to="/site-map">{t('사이트맵', 'Site map')}</Link></nav><small>© 2026 ToTaskFlow</small></footer>

    {installOpen && <div className="landing-install-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setInstallOpen(false)}><section role="dialog" aria-modal="true" aria-labelledby="install-title"><button className="landing-install-close" type="button" onClick={() => setInstallOpen(false)} aria-label={t('닫기', 'Close')}>×</button><span className="landing-install-icon"><BrandMark /></span><h2 id="install-title">{t('ToTaskFlow를 앱으로 만들까요?', 'Make ToTaskFlow an app?')}</h2><p>{t('앱스토어 다운로드 없이 홈 화면과 앱 목록에 아이콘을 추가합니다.', 'Add an icon to your home screen and app list without an app store download.')}</p><ul><li>{t('독립된 앱 화면으로 빠르게 실행됩니다.', 'Opens quickly in its own app window.')}</li><li>{t('기본 화면 일부를 저장하지만, 최신 조회와 변경에는 인터넷이 필요합니다.', 'Keeps part of the shell offline; current data and changes still need internet.')}</li><li>{t('알림·카메라 같은 권한은 자동으로 허용되지 않습니다.', 'Notification and camera permissions are not granted automatically.')}</li><li>{t('기기 설정에서 언제든 제거할 수 있습니다.', 'You can remove it anytime in device settings.')}</li></ul>{installable ? <div className="landing-install-actions"><button type="button" onClick={() => setInstallOpen(false)}>{t('나중에', 'Not now')}</button><button className="confirm" type="button" onClick={install}>{t('APP 만들기', 'Install app')}</button></div> : <div className="landing-install-manual"><strong>{t('브라우저 메뉴에서 직접 추가해 주세요.', 'Add it from your browser menu.')}</strong><p>{t('iPhone/iPad: Safari 공유 버튼 → 홈 화면에 추가\nAndroid/PC: 브라우저 메뉴 → 앱 설치 또는 홈 화면에 추가', 'iPhone/iPad: Safari Share → Add to Home Screen\nAndroid/Desktop: Browser menu → Install app or Add to Home Screen')}</p></div>}</section></div>}
  </main>;
}

function MockTask({ color, title, meta }: { color: string; title: string; meta: string }) {
  return <div className="mock-task"><i className={color} /><span><strong>{title}</strong><small>{meta}</small></span><b>•••</b></div>;
}
