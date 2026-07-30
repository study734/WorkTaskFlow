import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { OAuthCallbackPage } from '../features/auth/pages/OAuthCallbackPage';
import { OAuthConsentPage } from '../features/auth/pages/OAuthConsentPage';
import { LoginPage } from '../features/auth/pages/LoginPage';
import { FindUsernamePage, ForgotPasswordPage, ResetPasswordPage } from '../features/auth/pages/RecoveryPages';
import { SignupPage } from '../features/auth/pages/SignupPage';
import { GroupsPage } from '../features/group/pages/GroupsPage';
import { GroupDetailPage } from '../features/group/pages/GroupDetailPage';
import { GroupMembersPage } from '../features/group/pages/GroupMembersPage';
import { InvitationAcceptPage } from '../features/group/pages/InvitationAcceptPage';
import { AccountPage } from '../features/user/pages/AccountPage';
import { ProfilePage } from '../features/user/pages/ProfilePage';
import { TasksPage } from '../features/task/pages/TasksPage';
import { TaskDetailPage } from '../features/task/pages/TaskDetailPage';
import { HomePage } from './HomePage';
import { NotificationsPage } from '../features/notification/pages/NotificationsPage';
import { CalendarPage } from '../features/calendar/pages/CalendarPage';
import { GroupDashboardPage } from '../features/dashboard/pages/GroupDashboardPage';
import { PaymentsPage } from '../features/payment/pages/PaymentsPage';
import { PwaStatus } from './PwaStatus';
import { LanguageProvider } from './LanguageContext';
import { useLanguage } from './LanguageContext';
import { LandingPage } from './LandingPage';
import { B2BPage, ContactPage, PaidTermsPage, PricingPage, PrivacyPage, ProductPage, RefundPolicyPage, SiteMapPage, TermsPage } from './PublicPages';
import { PageMeta } from './PageMeta';
import { isRunningStandalone } from './pwa';
import { SessionKeepAlive } from './SessionKeepAlive';
import { AdminPage } from '../features/admin/AdminPage';
import { DemoPage } from './DemoPage';
import { AiWeeklyReportDetailPage } from '../features/report/pages/AiWeeklyReportDetailPage';
import { AiWeeklyReportPrintPage } from '../features/report/pages/AiWeeklyReportPrintPage';

export default function App() {
  return <LanguageProvider><BrowserRouter>
    <SkipLink />
    <RouteAnnouncer />
    <StandalonePublicGuard />
    <PageMeta />
    <SessionKeepAlive />
    <div id="main-content" tabIndex={-1}><Routes>
    <Route path="/" element={<LandingPage />} />
    <Route path="/demo" element={<DemoPage />} />
    <Route path="/app" element={<HomePage />} />
    <Route path="/privacy" element={<PrivacyPage />} />
    <Route path="/terms" element={<TermsPage />} />
    <Route path="/paid-terms" element={<PaidTermsPage />} />
    <Route path="/refund-policy" element={<RefundPolicyPage />} />
    <Route path="/site-map" element={<SiteMapPage />} />
    <Route path="/product" element={<ProductPage />} />
    <Route path="/b2b" element={<B2BPage />} />
    <Route path="/pricing" element={<PricingPage />} />
    <Route path="/contact" element={<ContactPage />} />
    <Route path="/profile" element={<ProfilePage />} />
    <Route path="/account" element={<AccountPage />} />
    <Route path="/payments" element={<PaymentsPage />} />
    <Route path="/admin" element={<AdminPage />} />
    <Route path="/groups" element={<GroupsPage />} />
    <Route path="/groups/:groupId" element={<GroupDetailPage />} />
    <Route path="/groups/:groupId/members" element={<GroupMembersPage />} />
    <Route path="/groups/:groupId/tasks" element={<TasksPage />} />
    <Route path="/tasks/:taskId" element={<TaskDetailPage />} />
    <Route path="/notifications" element={<NotificationsPage />} />
    <Route path="/calendar" element={<CalendarPage />} />
    <Route path="/groups/:groupId/dashboard" element={<GroupDashboardPage />} />
    <Route path="/groups/:groupId/reports/ai-weekly/:reportId" element={<AiWeeklyReportDetailPage />} />
    <Route path="/groups/:groupId/reports/ai-weekly/:reportId/print" element={<AiWeeklyReportPrintPage />} />
    <Route path="/group-invitations/accept" element={<InvitationAcceptPage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/signup" element={<SignupPage />} />
    <Route path="/find-username" element={<FindUsernamePage />} />
    <Route path="/forgot-password" element={<ForgotPasswordPage />} />
    <Route path="/reset-password" element={<ResetPasswordPage />} />
    <Route path="/oauth/callback" element={<OAuthCallbackPage />} />
    <Route path="/oauth/consent" element={<OAuthConsentPage />} />
    <Route path="*" element={<Navigate to="/" replace />} />
    </Routes></div>
    <PwaStatus />
  </BrowserRouter></LanguageProvider>;
}

function StandalonePublicGuard() {
  const location = useLocation();
  const navigate = useNavigate();
  useEffect(() => {
    if (!isRunningStandalone()) return;
    const publicOnly = new Set(['/', '/product', '/b2b', '/pricing', '/contact', '/site-map']);
    if (publicOnly.has(location.pathname)) {
      navigate('/app', { replace: true });
    }
  }, [location.pathname, navigate]);
  return null;
}

function SkipLink() {
  const { t } = useLanguage();
  return <a className="skip-link" href="#main-content">{t('본문으로 건너뛰기', 'Skip to main content')}</a>;
}

function RouteAnnouncer() {
  const { language } = useLanguage();
  const location = useLocation();
  const label = pageLabel(location.pathname, language);
  useEffect(() => {
    window.requestAnimationFrame(() => document.getElementById('main-content')?.focus());
  }, [label, location.pathname]);
  return <span className="sr-only" role="status" aria-live="polite">{language === 'ko' ? `${label} 페이지` : `${label} page`}</span>;
}

function pageLabel(pathname: string, language: 'ko' | 'en') {
  if (language === 'en') {
    if (pathname === '/') return 'toesa'; if (pathname === '/app') return 'Dashboard'; if (pathname === '/calendar') return 'Calendar'; if (pathname === '/notifications') return 'Alerts';
    if (pathname === '/demo') return 'Product demo';
    if (pathname === '/groups') return 'Groups'; if (pathname === '/profile') return 'Profile'; if (pathname === '/account') return 'Account settings';
    if (pathname === '/payments') return 'Payments'; if (pathname === '/admin') return 'Admin';
    if (pathname === '/product') return 'Product'; if (pathname === '/b2b') return 'B2B solutions'; if (pathname === '/pricing') return 'Pricing'; if (pathname === '/contact') return 'Contact';
    if (pathname === '/paid-terms') return 'Paid service terms'; if (pathname === '/refund-policy') return 'Refund policy';
    if (/\/dashboard$/.test(pathname)) return 'Group dashboard'; if (/\/members$/.test(pathname)) return 'Team members'; if (/\/tasks$/.test(pathname)) return 'Tasks'; if (/^\/tasks\//.test(pathname)) return 'Task details';
    if (/^\/groups\/\d+$/.test(pathname)) return 'Group settings'; if (pathname === '/signup') return 'Sign up'; if (pathname === '/login') return 'Log in';
    if (pathname === '/find-username') return 'Find username'; if (pathname === '/forgot-password' || pathname === '/reset-password') return 'Reset password';
    if (pathname === '/oauth/consent') return 'Google sign-up consent';
  }
  if (pathname === '/') return 'toesa';
  if (pathname === '/demo') return '제품 데모';
  if (pathname === '/app') return '내 대시보드';
  if (pathname === '/calendar') return '캘린더';
  if (pathname === '/notifications') return '알림';
  if (/^\/groups\/\d+\/dashboard$/.test(pathname)) return '그룹 대시보드';
  if (/^\/groups\/\d+\/members$/.test(pathname)) return '팀원 목록';
  if (/^\/groups\/\d+\/tasks$/.test(pathname)) return '업무 목록';
  if (/^\/tasks\/\d+$/.test(pathname)) return '업무 상세';
  if (pathname === '/groups') return '그룹 목록';
  if (/^\/groups\/\d+$/.test(pathname)) return '그룹 상세';
  if (pathname === '/profile') return '프로필';
  if (pathname === '/account') return '계정 설정';
  if (pathname === '/payments') return '결제 관리';
  if (pathname === '/admin') return '운영자';
  if (pathname === '/product') return '제품'; if (pathname === '/b2b') return 'B2B 솔루션'; if (pathname === '/pricing') return '가격'; if (pathname === '/contact') return '문의';
  if (pathname === '/paid-terms') return '유료서비스 이용약관'; if (pathname === '/refund-policy') return '환불 정책';
  if (pathname === '/signup') return '회원가입';
  if (pathname === '/login') return '로그인';
  if (pathname === '/oauth/consent') return 'Google 가입 동의';
  return 'toesa';
}
