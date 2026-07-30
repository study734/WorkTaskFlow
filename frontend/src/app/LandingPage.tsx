import { useEffect, useId, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { BrandMark } from './BrandMark';
import { useLanguage } from './LanguageContext';
import { isPwaInstallAvailable, isRunningStandalone, promptPwaInstall } from './pwa';

export function LandingPage() {
  const { t, language, setLanguage } = useLanguage();
  const [installOpen, setInstallOpen] = useState(false);
  const [installable, setInstallable] = useState(isPwaInstallAvailable());
  const installDialogRef = useRef<HTMLElement>(null);
  const installTitleId = useId();
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
  useEffect(() => {
    if (!installOpen) return;
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
    const overflow = document.body.style.overflow;
    const focusableSelector = 'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])';
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault(); setInstallOpen(false); return;
      }
      if (event.key !== 'Tab' || !installDialogRef.current) return;
      const focusable = Array.from(installDialogRef.current.querySelectorAll<HTMLElement>(focusableSelector));
      if (focusable.length === 0) return;
      const first = focusable[0]; const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus();
      }
    };
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', handleKeyDown);
    const frame = window.requestAnimationFrame(() => installDialogRef.current?.querySelector<HTMLElement>('button')?.focus());
    return () => {
      window.cancelAnimationFrame(frame);
      document.body.style.overflow = overflow;
      document.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, [installOpen]);

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
      <Link to="/" className="landing-brand"><BrandMark /><strong>{t('퇴사', 'toesa')}</strong></Link>
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
      <div className="landing-eyebrow"><span />{t('퇴근을 사수하는 업무관리', 'Protect your time after work')}</div>
      <h1>{t('보고 때문에 남아 있다면,', 'Leave the busywork behind.')}<br /><em>{t('이제 퇴사하세요.', 'Keep the work that matters.')}</em></h1>
      <p>{t('퇴사를 권하는 앱이 아닙니다. 업무는 남기고, 야근은 남기지 않도록 요청부터 완료·리포트까지 한 흐름으로 정리하는 팀 작업 공간입니다.', 'Not an app for quitting—a workspace for finishing. Keep requests, ownership, progress, and reports in one clear flow so work does not follow you home.')}</p>
      <div className="landing-hero-actions">
        <Link className="landing-primary" to="/login">{t('퇴사 시작하기', 'Start with toesa')} <span>→</span></Link>
        <Link className="landing-demo" to="/demo">{t('퇴사 미리 보기', 'Preview toesa')} <span>↗</span></Link>
      </div>
      <button className="landing-pwa-hero" type="button" onClick={() => setInstallOpen(true)} disabled={installed}><span aria-hidden="true">↓</span>{installed ? t('퇴사 앱으로 실행 중', 'Running as the toesa app') : t('앱스토어 없이, 이 기기에 퇴사 추가', 'Add toesa to this device—no app store')}</button>
      <small>{t('데모는 실제 시스템과 분리된 읽기 전용 화면입니다.', 'The demo is a read-only experience isolated from the live system.')}</small>
    </section>

    <section className="landing-product" aria-label={t('제품 화면 미리보기', 'Product preview')}>
      <div className="landing-window-bar"><i /><i /><i /><span>totaskflow.com/app</span></div>
      <div className="landing-product-body">
        <aside><div className="mock-brand"><BrandMark />{t('퇴사', 'toesa')}</div><span className="active">⌂ {t('홈', 'Home')}</span><span>♧ {t('그룹', 'Groups')}</span><span>□ {t('캘린더', 'Calendar')}</span><span>♢ {t('알림', 'Alerts')}</span><b>{t('로컬 알파 시연팀', 'Alpha demo team')}</b></aside>
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

    <section className="landing-name-story" aria-label={t('퇴사 브랜드 이야기', 'The toesa name')}>
      <span>toesa / 퇴사</span>
      <h2>{t('퇴사를 권하는 앱이 아닙니다.', 'It is not about quitting.')}<br /><em>{t('퇴근을 사수하는 앱입니다.', 'It is about finishing well.')}</em></h2>
      <p>{t('할 일을 더 만드는 대신, 이미 한 일이 보고서가 되게. 팀의 업무 흐름은 남기고 불필요한 야근은 남기지 않습니다.', 'Instead of creating more busywork, turn the work already done into the report. Keep the team context—not the unnecessary overtime.')}</p>
    </section>

    <section className="landing-proof" aria-label={t('퇴사 핵심 가치', 'toesa key value')}>
      <header><span className="landing-section-label">{t('실제 업무가 움직이는 구조', 'Built for work that moves')}</span><h2>{t('기록만 쌓는 도구가 아니라, 다음 행동을 분명하게.', 'More than stored records—make the next action clear.')}</h2><p>{t('누가 요청했고, 누가 승인하며, 누가 맡아야 하는지 한 흐름 안에서 확인합니다. 일정과 대화도 같은 업무에 연결됩니다.', 'See who requested, who approves, and who owns the next step. Schedules and conversations stay connected to the same work.')}</p></header>
      <div><article><strong>{t('요청과 승인', 'Request & approve')}</strong><p>{t('팀원의 제안을 팀장이 확인하고 반려 사유까지 기록합니다.', 'Leads review proposals and preserve rejection context.')}</p></article><article><strong>{t('직접 담당 선택', 'Claim ownership')}</strong><p>{t('승인된 업무를 진행 가능한 팀원이 직접 맡아 병목을 줄입니다.', 'Available teammates claim approved work and reduce bottlenecks.')}</p></article><article><strong>{t('업무 중심 협업', 'Work-centered context')}</strong><p>{t('체크리스트, 댓글, 멘션과 변경 이력을 업무별로 모읍니다.', 'Keep checklists, comments, mentions, and history per task.')}</p></article><article><strong>{t('일정과 리포트', 'Schedule & reports')}</strong><p>{t('마감과 팀 일정을 함께 보고 기간별 PDF 리포트를 만듭니다.', 'View deadlines with team events and create period-based PDF reports.')}</p></article></div>
    </section>

    <section id="workflow" className="landing-workflow">
      <div><span className="landing-section-label">{t('가볍게 시작하기', 'Start lightly')}</span><h2>{t('설치 없이 시작하고, 필요할 때 앱으로.', 'Start in the browser. Make it an app when ready.')}</h2><p>{t('회원가입 후 바로 브라우저에서 사용할 수 있습니다. 자주 사용한다면 별도 앱스토어 없이 현재 기기에 PWA 앱을 만들 수 있습니다.', 'Use it immediately after signing up. If it becomes part of your routine, add the PWA to your device without an app store.')}</p><button type="button" onClick={() => setInstallOpen(true)} disabled={installed}>{installed ? t('이미 앱으로 사용 중', 'Already installed') : t('이 기기에 APP 만들기', 'Make this an app')}</button></div>
      <ol><li><b>1</b><span><strong>{t('그룹 만들기', 'Create a group')}</strong><small>{t('멤버를 초대하고 역할을 정합니다.', 'Invite members and set roles.')}</small></span></li><li><b>2</b><span><strong>{t('업무 흐름 연결', 'Connect the workflow')}</strong><small>{t('요청·승인·담당·완료를 기록합니다.', 'Track request, approval, ownership, and completion.')}</small></span></li><li><b>3</b><span><strong>{t('한눈에 확인', 'Stay aligned')}</strong><small>{t('대시보드와 캘린더로 지금을 봅니다.', 'See the present in dashboards and calendars.')}</small></span></li></ol>
    </section>

    <section className="landing-confidence">
      <div><span className="landing-section-label">{t('안심하고 시험하기', 'Try it with confidence')}</span><h2>{t('브라우저에서 시작하고, 팀에 맞으면 그대로 이어가세요.', 'Start in the browser and keep going when it fits your team.')}</h2></div>
      <div className="landing-confidence-grid"><article><b>01</b><strong>{t('읽기 전용 데모', 'Read-only demo')}</strong><p>{t('김팀장과 팀원 더미 데이터로 실제 화면과 흐름을 먼저 확인합니다.', 'Explore real screens and flows with manager and teammate sample data.')}</p></article><article><b>02</b><strong>{t('설치 선택권', 'Optional installation')}</strong><p>{t('다운로드 없이 사용하고, 자주 쓰는 기기에만 PWA로 추가합니다.', 'Use it without a download, then add the PWA only on devices you choose.')}</p></article><article><b>03</b><strong>{t('데이터 분리 원칙', 'Data boundaries')}</strong><p>{t('데모 데이터는 브라우저 샘플로만 제공되어 실제 계정·업무·결제 데이터와 섞이지 않습니다.', 'Demo data stays in the browser sample and never mixes with real accounts, work, or payments.')}</p></article></div>
    </section>

    <section className="landing-final-cta"><BrandMark /><h2>{t('오늘도 보고서 때문에 남아 있나요?', 'Still staying late for the report?')}</h2><p>{t('업무는 남기고, 야근은 남기지 마세요. 이제 퇴사하세요.', 'Keep the work, not the overtime. Start finishing with toesa.')}</p><div><Link to="/login">{t('퇴사 시작하기', 'Start with toesa')} →</Link><Link to="/demo">{t('데모 보기', 'View demo')}</Link></div></section>
    <footer><Link to="/" className="landing-brand"><BrandMark /><strong>{t('퇴사', 'toesa')}</strong></Link><nav><Link to="/privacy">{t('개인정보 처리방침', 'Privacy')}</Link><Link to="/terms">{t('이용약관', 'Terms')}</Link><Link to="/paid-terms">{t('유료서비스 약관', 'Paid terms')}</Link><Link to="/refund-policy">{t('환불 정책', 'Refunds')}</Link><Link to="/site-map">{t('사이트맵', 'Site map')}</Link></nav><small>© 2026 toesa</small></footer>

    {installOpen && <div className="landing-install-backdrop" role="presentation" onMouseDown={(event) => event.target === event.currentTarget && setInstallOpen(false)}><section ref={installDialogRef} role="dialog" aria-modal="true" aria-labelledby={installTitleId} tabIndex={-1}><button className="landing-install-close" type="button" onClick={() => setInstallOpen(false)} aria-label={t('닫기', 'Close')}>×</button><span className="landing-install-icon"><BrandMark /></span><h2 id={installTitleId}>{t('퇴사를 앱으로 만들까요?', 'Make toesa an app?')}</h2><p>{t('앱스토어 다운로드 없이 홈 화면과 앱 목록에 아이콘을 추가합니다.', 'Add an icon to your home screen and app list without an app store download.')}</p><ul><li>{t('독립된 앱 화면으로 빠르게 실행됩니다.', 'Opens quickly in its own app window.')}</li><li>{t('기본 화면 일부를 저장하지만, 최신 조회와 변경에는 인터넷이 필요합니다.', 'Keeps part of the shell offline; current data and changes still need internet.')}</li><li>{t('알림·카메라 같은 권한은 자동으로 허용되지 않습니다.', 'Notification and camera permissions are not granted automatically.')}</li><li>{t('기기 설정에서 언제든 제거할 수 있습니다.', 'You can remove it anytime in device settings.')}</li></ul>{installable ? <div className="landing-install-actions"><button type="button" onClick={() => setInstallOpen(false)}>{t('나중에', 'Not now')}</button><button className="confirm" type="button" onClick={install}>{t('APP 만들기', 'Install app')}</button></div> : <div className="landing-install-manual"><strong>{t('브라우저 메뉴에서 직접 추가해 주세요.', 'Add it from your browser menu.')}</strong><p>{t('iPhone/iPad: Safari 공유 버튼 → 홈 화면에 추가\nAndroid/PC: 브라우저 메뉴 → 앱 설치 또는 홈 화면에 추가', 'iPhone/iPad: Safari Share → Add to Home Screen\nAndroid/Desktop: Browser menu → Install app or Add to Home Screen')}</p></div>}</section></div>}
  </main>;
}

function MockTask({ color, title, meta }: { color: string; title: string; meta: string }) {
  return <div className="mock-task"><i className={color} /><span><strong>{title}</strong><small>{meta}</small></span><b>•••</b></div>;
}
