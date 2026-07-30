import { Link } from 'react-router-dom';
import { BrandMark } from './BrandMark';
import { useLanguage } from './LanguageContext';

const policyVersion = '2026-07-27-v3';
const privacyContact = String(import.meta.env.VITE_PRIVACY_CONTACT ?? 'ghrud8835@gmail.com');

function PublicHeader() {
  const { t, language, setLanguage } = useLanguage();
  return <header className="public-header"><Link to="/" className="landing-brand"><BrandMark /><strong>{t('퇴사', 'toesa')}</strong></Link><nav>
    <Link to="/product">{t('제품', 'Product')}</Link><Link to="/b2b">{t('B2B 솔루션', 'B2B solutions')}</Link><Link to="/pricing">{t('가격', 'Pricing')}</Link><Link to="/contact">{t('문의', 'Contact')}</Link>
    <button type="button" className="landing-language" onClick={() => setLanguage(language === 'ko' ? 'en' : 'ko')}>{language === 'ko' ? 'EN' : '한글'}</button>
    <Link className="landing-nav-cta" to="/login">{t('무료로 시작', 'Get started')}</Link>
  </nav></header>;
}

function PublicLayout({ eyebrow, title, intro, policy = false, children }: { eyebrow: string; title: string; intro: string; policy?: boolean; children: React.ReactNode }) {
  const { t } = useLanguage();
  return <main className="public-page"><PublicHeader /><article><span>{eyebrow}</span><h1>{title}</h1><p className="public-intro">{intro}</p>{children}{policy && <p className="policy-version">{t('시행일·버전', 'Effective date · version')}: {policyVersion}</p>}</article><footer><Link to="/product">{t('제품', 'Product')}</Link><Link to="/pricing">{t('가격', 'Pricing')}</Link><Link to="/contact">{t('문의', 'Contact')}</Link><Link to="/privacy">{t('개인정보 처리방침', 'Privacy')}</Link><Link to="/terms">{t('이용약관', 'Terms')}</Link><Link to="/paid-terms">{t('유료서비스 약관', 'Paid terms')}</Link><Link to="/refund-policy">{t('환불 정책', 'Refunds')}</Link><Link to="/site-map">{t('사이트맵', 'Site map')}</Link></footer></main>;
}

export function ProductPage() {
  const { t } = useLanguage();
  return <PublicLayout eyebrow="PRODUCTS" title={t('우리가 만드는 제품을 한곳에서.', 'Everything we build, in one place.')} intro={t('현재는 퇴사에 집중하고 있습니다. 앞으로 업무 에이전트, 추가 기능과 이전 프로젝트도 제품 기준에 맞춰 이곳에 순차적으로 공개합니다.', 'We are focused on toesa today. Work agents, extensions, and selected past projects will be added here as they become ready.')}>
    <div className="product-catalog">
      <article className="featured"><span>{t('현재 이용 가능', 'Available now')}</span><h2>{t('퇴사', 'toesa')}</h2><p>{t('요청·승인·담당자 선택·진행·완료와 팀 일정을 한 흐름으로 관리하는 업무 협업 서비스입니다.', 'A work collaboration service connecting requests, approvals, ownership, progress, completion, and team schedules.')}</p><div><small>{t('업무 관리', 'Work management')}</small><small>{t('팀 협업', 'Team collaboration')}</small><small>PWA</small></div><Link to="/login">{t('무료로 시작', 'Get started')} →</Link></article>
      <article><span>{t('기획 중', 'Planned')}</span><h2>{t('업무 에이전트', 'Work agents')}</h2><p>{t('반복 업무 정리, 진행 요약과 다음 행동 제안을 돕는 에이전트 기능을 검토하고 있습니다.', 'We are exploring agents that organize recurring work, summarize progress, and suggest next actions.')}</p><small>{t('제공 시점 미정', 'Release date not set')}</small></article>
      <article><span>{t('준비 중', 'In preparation')}</span><h2>{t('프로젝트 아카이브', 'Project archive')}</h2><p>{t('이전에 만든 프로젝트와 실험 중 재사용 가능한 결과를 정리해 제품 또는 사례 형태로 공개할 예정입니다.', 'Selected past projects and reusable experiments will be organized as products or case studies.')}</p><small>{t('내용 정리 후 순차 공개', 'Coming as materials are prepared')}</small></article>
    </div>
    <h2 className="public-section-title">{t('퇴사가 제공하는 것', 'What toesa offers')}</h2>
    <InfoGrid cards={[
      [t('요청부터 담당까지', 'Request to ownership'), t('팀원이 업무를 제안하고 승인된 미담당 업무를 적합한 팀원이 직접 맡을 수 있습니다.', 'Members propose work, and eligible teammates can claim approved unassigned tasks.')],
      [t('업무 맥락과 일정', 'Context and schedule'), t('체크리스트, 댓글, 멘션, 상태 이력과 캘린더를 하나의 작업 공간에서 확인합니다.', 'Checklists, comments, mentions, status history, and calendars stay in one workspace.')],
    ]} />
    <PublicCta title={t('현재 제품을 직접 확인해 보세요.', 'Explore the product available today.')} text={t('일반 계정으로 시작하거나 별도의 읽기 전용 김팀장 데모를 살펴볼 수 있습니다.', 'Start with an account or explore the separate read-only manager demo.')} />
  </PublicLayout>;
}

export function B2BPage() {
  const { t } = useLanguage();
  return <PublicLayout eyebrow="B2B · PLANNED" title={t('사내 환경에 맞춘 독립 구축을 준비합니다.', 'Private deployment, designed for your organization.')} intro={t('현재 서비스는 개인과 일반 팀 사용 경험을 먼저 개발하고 있습니다. B2B는 이후 회사 내부 서버에서 독립적으로 운영할 수 있는 실행 패키지와 도입 가이드를 제공하는 모델로 준비할 예정입니다.', 'We are currently focused on the individual and general team experience. A future B2B model is planned around self-contained deployment packages and rollout guidance for private company infrastructure.')}>
    <aside className="b2b-status"><strong>{t('현재 제공하지 않는 기능입니다.', 'Not currently available.')}</strong><p>{t('설치 파일, 라이선스와 사내 구축 지원 범위는 아직 확정되지 않았습니다. 아래 내용은 계획 방향이며 계약 또는 제공 약속이 아닙니다.', 'Installers, licensing, and implementation support are not finalized. The following is a direction, not a current offering or commitment.')}</p></aside>
    <InfoGrid cards={[
      [t('독립 실행 패키지', 'Self-contained package'), t('사내 서버 또는 지정 클라우드에 구축할 수 있는 배포 단위와 버전별 업그레이드 절차를 목표로 합니다.', 'The goal is a deployable package for company servers or a chosen cloud, with versioned upgrade procedures.')],
      [t('조직 맞춤 설정', 'Organization fit'), t('조직 구조, 승인 방식, 권한, 보존 정책과 사내 인증 연동 요구사항을 도입 전에 정리합니다.', 'Organization structure, approvals, permissions, retention, and identity requirements would be scoped before rollout.')],
      [t('운영 가이드', 'Operations guide'), t('설치, 백업·복원, 모니터링, 보안 업데이트와 장애 대응 기준을 함께 제공하는 방향입니다.', 'The intended guide covers installation, backup and restore, monitoring, security updates, and incident response.')],
      [t('분리된 데이터', 'Data isolation'), t('공용 서비스와 분리된 DB·파일·비밀키 운영을 기본 전제로 검토합니다.', 'Separate databases, files, and secrets from the public service are a core design assumption.')],
    ]} />
    <h2 className="public-section-title">{t('예상 도입 순서', 'Expected rollout path')}</h2>
    <ol className="b2b-guide"><li><b>1</b><span><strong>{t('요구사항 확인', 'Discovery')}</strong><small>{t('인원, 업무 흐름, 권한과 데이터 보존 조건을 확인합니다.', 'Review team size, workflow, permissions, and retention needs.')}</small></span></li><li><b>2</b><span><strong>{t('환경 설계', 'Environment design')}</strong><small>{t('사내 서버·클라우드, 도메인, 메일과 인증 구성을 정합니다.', 'Define server or cloud, domain, mail, and identity configuration.')}</small></span></li><li><b>3</b><span><strong>{t('시험 구축', 'Pilot deployment')}</strong><small>{t('분리된 QA 환경에서 설치·보안·백업·복원을 검증합니다.', 'Validate installation, security, backups, and restoration in isolated QA.')}</small></span></li><li><b>4</b><span><strong>{t('운영 전환', 'Production handoff')}</strong><small>{t('관리 담당자, 업데이트와 장애 대응 절차를 확정합니다.', 'Finalize ownership, updates, and incident procedures.')}</small></span></li></ol>
    <PublicCta title={t('향후 B2B 소식을 먼저 받고 싶나요?', 'Interested in future B2B updates?')} text={t('현재 필요한 구축 방식과 보안 조건을 보내 주시면 향후 모델을 설계할 때 참고하겠습니다.', 'Share your deployment and security needs to help inform the future model.')} contact />
  </PublicLayout>;
}

export function PricingPage() {
  const { t } = useLanguage();
  return <PublicLayout eyebrow="PRICING" title={t('필요한 기능으로 바로 시작하세요.', 'Start with the features you need.')} intro={t('핵심 협업 기능은 무료로 제공하며, 유료 그룹 기능은 결제 운영 승인 후 별도로 안내합니다.', 'Core collaboration features are free. Paid group features will be announced separately after payment operations are approved.')}>
    <div className="pricing-grid"><article><span>FREE</span><h2>{t('무료', 'Free')}</h2><strong>₩0</strong><p>{t('업무 흐름, 댓글, 알림, 캘린더와 한국어·영어 기본 리포트를 제공합니다.', 'Task workflows, comments, alerts, calendar, and core Korean/English reports are included.')}</p><Link to="/login">{t('무료로 시작', 'Get started')}</Link></article><article><span>PAID PREVIEW</span><h2>{t('유료 그룹', 'Paid group')}</h2><strong>{t('월 9,900원 예정', 'Planned at ₩9,900/mo')}</strong><p>{t('30일 체험과 선택 요일의 주간·월간 메일 리포트를 준비했습니다. 결제 운영 승인 전에는 실제 과금하지 않으며 AI 분석은 비활성입니다.', 'A 30-day trial and weekly/monthly scheduled email reports are ready. No live charge occurs before payment operations are approved, and AI analysis remains disabled.')}</p><Link to="/paid-terms">{t('유료서비스 기준 보기', 'View paid terms')}</Link></article><article><span>B2B</span><h2>{t('팀 도입', 'Team rollout')}</h2><strong>{t('문의', 'Contact')}</strong><p>{t('인원·보안·운영 지원 범위를 확인한 뒤 도입 방식을 협의합니다.', 'We scope rollout options based on team size, security, and support needs.')}</p><Link to="/contact">{t('도입 문의', 'Contact sales')}</Link></article></div>
    <aside className="policy-notice">{t('유료 플랜은 아직 판매하지 않습니다. 판매 전 가격과 시행일을 다시 고지하며, 유료서비스 약관과 환불 정책을 확인할 수 있습니다.', 'Paid plans are not yet sold. Price and effective date will be announced again before launch; paid terms and refund policy are available now.')} <Link to="/refund-policy">{t('환불 정책', 'Refund policy')} →</Link></aside>
  </PublicLayout>;
}

export function ContactPage() {
  const { t } = useLanguage();
  const contact = String(import.meta.env.VITE_CONTACT_EMAIL ?? 'ghrud8835@gmail.com');
  return <PublicLayout eyebrow="CONTACT" title={t('궁금한 점을 편하게 남겨 주세요.', 'Let’s talk.')} intro={t('제품 사용, 기능 제안과 향후 B2B 구축에 대한 문의를 이메일로 받고 있습니다.', 'We welcome questions about the product, feature ideas, and future B2B deployment by email.')}>
    <InfoGrid cards={[
      [t('제품·기능', 'Product & features'), t('퇴사 사용 방법, 불편한 점과 필요한 기능을 알려 주세요.', 'Ask about toesa, report friction, or suggest a feature.')],
      [t('B2B 사전 문의', 'Future B2B'), t('예상 인원, 사내 구축 환경과 필요한 보안 조건을 알려 주세요. 현재 제공 중인 상품은 아닙니다.', 'Share team size, deployment environment, and security needs. This is not a currently available offering.')],
    ]} />
    <section className="contact-panel"><span>{t('이메일 문의', 'Email')}</span><a href={`mailto:${contact}`}>{contact}</a><p>{t('문의 목적과 회신 받을 내용을 적어 보내 주세요. 비밀번호, 결제키, 카드정보 같은 민감정보는 보내지 마세요.', 'Include the purpose of your message and what you need from us. Never send passwords, payment keys, or card information.')}</p></section>
  </PublicLayout>;
}

export function PrivacyPage() {
  const { t } = useLanguage();
  return <PublicLayout policy eyebrow="PRIVACY" title={t('개인정보 처리방침', 'Privacy policy')} intro={t('퇴사는 서비스 제공에 필요한 최소한의 개인정보를 처리하고, 사용자가 자신의 정보를 통제할 수 있도록 합니다.', 'toesa processes only the personal information needed to provide the service and helps users stay in control of it.')}>
    <PolicySection title={t('1. 처리하는 개인정보', '1. Information we process')}><p>{t('일반 회원가입 시 이름, 아이디, 이메일과 비밀번호의 단방향 해시를 처리합니다. Google 가입 시에는 Google 계정 식별자, 확인된 이메일과 이름을 처리하며 Google 비밀번호 또는 Google 액세스·리프레시 토큰은 저장하지 않습니다. 선택적으로 전화번호, 프로필 이미지, 업무 알림 및 마케팅 수신 동의를 처리합니다. 서비스 이용 과정에서 접속 기록, 보안 감사 기록, 그룹·업무·댓글·캘린더 데이터가 생성될 수 있습니다.', 'For regular signup we process your name, username, email, and a one-way password hash. For Google signup we process the Google account identifier, verified email, and name; we do not store your Google password or Google access/refresh tokens. Phone number, profile image, work-notification preference, and marketing preference are optional. Security logs and workspace content can be created as you use the service.')}</p></PolicySection>
    <PolicySection title={t('2. 처리 목적', '2. Purposes')}><p>{t('계정 생성과 인증, 협업 기능 제공, 알림 전달, 보안 사고 방지, 문의 대응 및 사용자가 별도로 동의한 소식 제공에 이용합니다.', 'We use this information for account authentication, collaboration features, requested notifications, security, support, and separately consented updates.')}</p></PolicySection>
    <PolicySection title={t('3. 보유 기간', '3. Retention')}><p>{t('계정 정보는 회원 탈퇴 시 익명화하거나 삭제합니다. 법령상 보존 의무 또는 분쟁 대응이 필요한 정보는 해당 목적과 기간 동안 분리 보관할 수 있습니다. 업무 상태 이력처럼 그룹의 협업 기록에 포함되는 정보는 서비스 기록 보존 정책에 따라 보존될 수 있습니다.', 'Account information is deleted or anonymized on withdrawal. Information required by law or for dispute handling may be retained separately for the applicable period. Collaboration records such as task history may remain under the service record-retention policy.')}</p></PolicySection>
    <PolicySection title={t('4. 외부 서비스와 처리 위탁', '4. External services')}><p>{t('Google OAuth 2.0은 사용자가 선택한 Google 로그인을 처리합니다. 퇴사는 인증 결과로 필요한 최소 프로필만 받아 계정을 생성하며, Google 계정 연결은 회원 탈퇴 시 삭제합니다. 이메일 발송과 인프라 운영을 위해 설정된 제공자를 이용할 수 있습니다. 결제수단을 등록하는 경우 카드 정보는 토스페이먼츠 화면에서 직접 처리되며 퇴사 서버에는 카드번호·CVC가 저장되지 않습니다. 실제 운영 제공자, 처리 위치와 국외 이전 사항은 운영 전 최종 방침에 명시해야 합니다.', 'Google OAuth 2.0 handles Google sign-in selected by the user. toesa receives only the minimum profile needed to create an account and deletes the Google account link when the account is deleted. Configured providers may support email and infrastructure. Card details are entered directly with Toss Payments and card number/CVC are not stored by toesa. Final production vendors, processing locations, and international transfers must be listed before launch.')}</p></PolicySection>
    <PolicySection title={t('5. 이용자의 권리', '5. Your rights')}><p>{t('프로필과 계정 설정에서 정보를 열람·수정하거나 회원 탈퇴를 요청할 수 있습니다. 동의 철회, 처리 정지 또는 문의는 아래 연락처로 요청할 수 있습니다.', 'You can review and change profile information or request account deletion. Contact us to withdraw consent, restrict processing, or ask a privacy question.')}</p><p><strong>{t('개인정보 문의', 'Privacy contact')}:</strong> {privacyContact}</p></PolicySection>
    <aside className="policy-notice">{t('이 문서는 현재 개발 환경에 맞춘 초안입니다. 사업자명, 주소, 개인정보 보호책임자, 실제 수탁사 및 보유기간은 운영 배포 전에 법률 검토와 함께 확정해야 합니다.', 'This is a development-stage draft. Legal entity details, address, privacy officer, production processors, and exact retention periods must be finalized before launch.')}</aside>
  </PublicLayout>;
}

export function TermsPage() {
  const { t } = useLanguage();
  return <PublicLayout policy eyebrow="TERMS" title={t('서비스 이용약관', 'Terms of service')} intro={t('퇴사를 안전하고 공정하게 이용하기 위한 기본 조건입니다.', 'These are the basic conditions for using toesa safely and fairly.')}>
    <PolicySection title={t('1. 서비스의 목적', '1. Purpose')}><p>{t('퇴사는 그룹 업무, 일정, 알림과 협업 기록을 관리하는 서비스를 제공합니다.', 'toesa provides group task, schedule, notification, and collaboration-record features.')}</p></PolicySection>
    <PolicySection title={t('2. 계정 관리', '2. Accounts')}><p>{t('사용자는 정확한 정보를 제공하고 인증정보를 안전하게 관리해야 합니다. 계정 공유, 타인 사칭, 무단 접근 시도는 허용되지 않습니다.', 'Users must provide accurate information and protect account credentials. Account sharing, impersonation, and unauthorized access attempts are prohibited.')}</p></PolicySection>
    <PolicySection title={t('3. 협업 데이터', '3. Workspace data')}><p>{t('그룹에 등록한 업무와 댓글은 다른 그룹 멤버에게 공개됩니다. 사용자는 필요한 권한이 있는 정보만 등록해야 하며 불법·침해 콘텐츠를 게시해서는 안 됩니다.', 'Tasks and comments are visible to relevant group members. Users must only upload authorized information and may not post unlawful or infringing content.')}</p></PolicySection>
    <PolicySection title={t('4. 데모와 시험 기능', '4. Demo and preview features')}><p>{t('제품 데모는 실제 API와 데이터베이스에서 분리된 읽기 전용 샘플 화면입니다. 시험 기능은 예고 없이 변경될 수 있습니다.', 'The product demo is a read-only sample isolated from live APIs and databases. Preview features may change without notice.')}</p></PolicySection>
    <PolicySection title={t('5. 변경과 중단', '5. Changes and availability')}><p>{t('보안, 유지보수 또는 불가피한 사유로 서비스 일부가 변경·중단될 수 있습니다. 중요한 변경은 합리적인 방법으로 사전에 안내합니다.', 'Features may change or become unavailable for security, maintenance, or unavoidable reasons. Material changes will be announced through reasonable channels.')}</p></PolicySection>
    <aside className="policy-notice">{t('유료 서비스, 환불, 준거법과 사업자 정보는 실제 판매 시작 전에 별도 약관과 법률 검토를 통해 보완해야 합니다.', 'Paid plans, refunds, governing law, and legal entity information must be finalized before commercial launch.')}</aside>
  </PublicLayout>;
}

export function PaidTermsPage() {
  const { t } = useLanguage();
  return <PublicLayout policy eyebrow="PAID TERMS" title={t('유료서비스 이용약관', 'Paid service terms')} intro={t('유료 전환 전 공개하는 운영 기준안입니다. 실제 판매는 사업자·통신판매업·결제 운영 준비와 법률 검토 후 시작합니다.', 'These are the operating terms published before paid rollout. Sales begin only after business, e-commerce, payment, and legal readiness.')}>
    <PolicySection title={t('1. 그룹 구독', '1. Group subscription')}><p>{t('그룹 리더가 요금제, 결제수단, 자동 갱신과 해지를 관리합니다. 첫 결제 전에 금액·주기·자동 갱신·환불 정책에 개별 동의를 받습니다.', 'The group leader manages the plan, payment method, renewal, and cancellation. Price, cycle, renewal, and refund terms require explicit consent before the first charge.')}</p></PolicySection>
    <PolicySection title={t('2. 무료 운영에서 유료 전환', '2. Free-to-paid rollout')}><p>{t('유료 전환일과 선택 기한을 사전 고지합니다. 무료 유지를 선택하거나 응답하지 않은 그룹에는 결제하지 않으며, 멤버십과 데이터를 일괄 삭제하지 않습니다.', 'We provide advance notice and a decision deadline. Groups that keep free or do not respond are not charged, and memberships and data are not bulk-deleted.')}</p></PolicySection>
    <PolicySection title={t('3. 갱신과 해지', '3. Renewal and cancellation')}><p>{t('구독은 표시된 주기로 선불 갱신됩니다. 언제든 다음 갱신을 중단할 수 있고, 현재 결제기간 말일까지 이용할 수 있습니다.', 'Subscriptions renew in advance on the displayed cycle. Leaders can stop the next renewal at any time and retain access through the paid period.')}</p></PolicySection>
    <PolicySection title={t('4. 리포트와 파일', '4. Reports and files')}><p>{t('기본 리포트는 확정 업무 지표로 생성합니다. AI 분석은 활성화되는 경우 보조 정보이며 중요한 판단 전 원자료를 확인해야 합니다. 업로드 자료는 이용자가 적법한 권한을 보유해야 합니다.', 'Core reports use confirmed work metrics. Any enabled AI analysis is supplementary and source data should be reviewed before important decisions. Uploaders must have rights to submitted files.')}</p></PolicySection>
    <PolicySection title={t('5. 변경과 책임', '5. Changes and responsibility')}><p>{t('중요한 가격·조건 변경은 원칙적으로 30일 전에 고지합니다. 회사의 고의·중과실 및 법령상 배제할 수 없는 책임은 제한하지 않습니다.', 'Material price and term changes are generally announced 30 days in advance. Liability for intent, gross negligence, and non-waivable statutory rights is not excluded.')}</p></PolicySection>
    <aside className="policy-notice">{t('사업자 정보와 최종 상품 한도는 운영 전에 입력해야 합니다. 상세 운영 원문은 고객센터에 요청할 수 있습니다.', 'Business identity and final plan limits must be inserted before launch. The complete operating text is available from support.')}</aside>
  </PublicLayout>;
}

export function RefundPolicyPage() {
  const { t } = useLanguage();
  return <PublicLayout policy eyebrow="REFUNDS" title={t('환불 및 구독 해지 정책', 'Refund and cancellation policy')} intro={t('간단히 해지하고, 사용하지 않은 기간은 합리적으로 돌려받는 기준입니다.', 'A straightforward cancellation policy with fair refunds for unused time.')}>
    <PolicySection title={t('1. 다음 결제 중단', '1. Stop renewal')}><p>{t('그룹 리더는 언제든 다음 자동결제를 중단할 수 있으며 현재 결제기간 말일까지 이용합니다.', 'The group leader may stop the next renewal at any time and retain access through the paid period.')}</p></PolicySection>
    <PolicySection title={t('2. 첫 결제 7일', '2. First seven days')}><p>{t('첫 유료 결제일부터 7일 이내 요청하면 전액 환불합니다. 중복·부정·오결제도 확인 후 전액 환불합니다.', 'The first paid charge is fully refundable within seven days. Verified duplicate, unauthorized, or incorrect charges are fully refunded.')}</p></PolicySection>
    <PolicySection title={t('3. 이후 중도 환불', '3. Later cancellation')}><p>{t('요청 다음 날부터 남은 완전한 일수를 결제기간 총 일수로 나누어 일할 환불하며 별도 위약금은 없습니다.', 'Afterward, we refund complete unused days pro rata from the day after the request, without a separate penalty.')}</p></PolicySection>
    <PolicySection title={t('4. 하자와 처리기간', '4. Defects and timing')}><p>{t('중대한 서비스 하자는 복구·기간 연장 또는 전액/비례 환불로 처리합니다. 승인 후 3영업일 이내 취소·환급 절차를 시작합니다.', 'Material service defects are remedied, extended, or refunded fully/proportionally. We initiate an approved refund within three business days.')}</p></PolicySection>
    <PolicySection title={t('5. 데이터 보호', '5. Data protection')}><p>{t('해지 후 무료 요금제로 전환하며 그룹 멤버십과 데이터를 즉시 삭제하지 않습니다. 관계 법령이 더 유리하면 법령을 우선합니다.', 'After cancellation the group moves to the free plan; memberships and data are not immediately deleted. More favorable statutory rights prevail.')}</p></PolicySection>
    <aside className="policy-notice">{t('환불액 = 실제 결제금액 × 남은 완전한 일수 ÷ 결제기간 총 일수. 원 미만은 이용자에게 불리하지 않게 처리합니다.', 'Refund = amount paid × complete unused days ÷ total days in the billing period, rounded without disadvantaging the customer.')}</aside>
  </PublicLayout>;
}

export function SiteMapPage() {
  const { t } = useLanguage();
  const sections = [
    { title: t('서비스 알아보기', 'Explore'), links: [['/', t('서비스 소개', 'Overview')], ['/demo', t('읽기 전용 데모', 'Read-only demo')], ['/product', t('제품', 'Product')], ['/b2b', t('B2B 솔루션', 'B2B solutions')], ['/pricing', t('가격', 'Pricing')], ['/contact', t('문의', 'Contact')]] },
    { title: t('시작', 'Start'), links: [['/login', t('로그인·무료 시작', 'Log in · get started')], ['/signup', t('회원가입', 'Sign up')]] },
    { title: t('내 작업 공간', 'My workspace'), links: [['/app', t('내 대시보드', 'Dashboard')], ['/groups', t('그룹', 'Groups')], ['/calendar', t('캘린더', 'Calendar')], ['/notifications', t('알림', 'Alerts')]] },
    { title: t('계정', 'Account'), links: [['/profile', t('프로필', 'Profile')], ['/account', t('계정 및 보안', 'Account & security')], ['/payments', t('결제수단 및 테스트', 'Payments & tests')]] },
    { title: t('정책과 안내', 'Policies'), links: [['/privacy', t('개인정보 처리방침', 'Privacy policy')], ['/terms', t('이용약관', 'Terms')], ['/paid-terms', t('유료서비스 약관', 'Paid terms')], ['/refund-policy', t('환불 정책', 'Refund policy')], ['/site-map', t('사이트맵', 'Site map')]] },
  ];
  return <PublicLayout eyebrow="SITE MAP" title={t('전체 사이트맵', 'Site map')} intro={t('퇴사의 주요 화면과 정보 구조를 한눈에 확인하세요.', 'Explore the main screens and information structure of toesa.')}>
    <div className="site-map-grid">{sections.map((section) => <section key={section.title}><h2>{section.title}</h2>{section.links.map(([to, label]) => <Link to={to} key={to}>{label}<span>→</span></Link>)}</section>)}</div>
    <aside className="policy-notice">{t('그룹 대시보드, 업무 상세 및 초대 수락 화면은 로그인과 접근 권한이 있을 때 해당 경로에서 열립니다.', 'Group dashboards, task details, and invitation acceptance pages appear only with the required login and access.')}</aside>
  </PublicLayout>;
}

function PolicySection({ title, children }: { title: string; children: React.ReactNode }) {
  return <section className="policy-section"><h2>{title}</h2>{children}</section>;
}

function InfoGrid({ cards }: { cards: string[][] }) {
  return <div className="public-info-grid">{cards.map(([title, body]) => <section key={title}><h2>{title}</h2><p>{body}</p></section>)}</div>;
}

function PublicCta({ title, text, contact = false }: { title: string; text: string; contact?: boolean }) {
  const { t } = useLanguage();
  return <section className="public-cta"><div><h2>{title}</h2><p>{text}</p></div><Link to={contact ? '/contact' : '/login'}>{contact ? t('문의하기', 'Contact us') : t('무료로 시작', 'Get started')} →</Link></section>;
}
