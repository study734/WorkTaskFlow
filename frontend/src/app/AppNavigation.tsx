import { useEffect, useId, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useLanguage } from './LanguageContext';
import { notificationApi } from '../api/notificationApi';
import { groupApi, type GroupResponse } from '../api/groupApi';
import { accessToken, sessionMode } from '../api/client';
import { authApi } from '../api/authApi';
import { BrandMark } from './BrandMark';

const items = [
  { to: '/app', label: '홈', icon: '⌂' },
  { to: '/groups', label: '그룹', icon: '♧' },
  { to: '/calendar', label: '캘린더', icon: '□' },
  { to: '/notifications', label: '알림', icon: '♢' },
  { to: '/profile', label: '프로필', icon: '○' },
];

export function AppNavigation({ unreadCount }: { unreadCount?: number }) {
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const { language, setLanguage } = useLanguage();
  const [liveUnreadCount, setLiveUnreadCount] = useState(unreadCount ?? 0);
  const [groups, setGroups] = useState<GroupResponse[]>([]);
  useEffect(() => {
    if (!accessToken.get()) return;
    const refresh = () => notificationApi.list(1).then((page) => setLiveUnreadCount(page.unreadCount)).catch(() => undefined);
    const refreshGroups = () => groupApi.list().then(setGroups).catch(() => undefined);
    refresh();
    refreshGroups();
    const interval = window.setInterval(refresh, 60_000);
    const refreshOnResume = () => { if (document.visibilityState === 'visible') refresh(); };
    document.addEventListener('visibilitychange', refreshOnResume);
    window.addEventListener('notifications:refresh', refresh);
    window.addEventListener('groups:refresh', refreshGroups);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener('visibilitychange', refreshOnResume);
      window.removeEventListener('notifications:refresh', refresh);
      window.removeEventListener('groups:refresh', refreshGroups);
    };
  }, []);
  useEffect(() => { if (unreadCount !== undefined) setLiveUnreadCount(unreadCount); }, [unreadCount]);
  const pathGroupId = pathname.match(/^\/groups\/(\d+)/)?.[1];
  const selectedGroupId = pathGroupId ?? new URLSearchParams(search).get('groupId') ?? '';
  const labels = language === 'ko' ? ['홈', '그룹', '캘린더', '알림', '프로필'] : ['Home', 'Groups', 'Calendar', 'Alerts', 'Profile'];
  const teamGroups = groups.filter((group) => group.type === 'TEAM');
  const demo = sessionMode.isDemo();
  async function exitDemo() {
    await authApi.logout().catch(() => undefined);
    accessToken.clear();
    sessionMode.clear();
    navigate('/login', { replace: true });
  }
  return <nav className="app-navigation" aria-label={language === 'ko' ? '주요 메뉴' : 'Main navigation'}>
    <Link className="app-navigation-brand" to="/app" aria-label={language === 'ko' ? '퇴사 앱 홈' : 'toesa app home'}><span><BrandMark /></span><strong>{language === 'ko' ? '퇴사' : 'toesa'}</strong></Link>
    {demo && <div className="demo-session-notice"><span>{language === 'ko' ? '읽기 전용 데모' : 'Read-only demo'}</span><button type="button" onClick={exitDemo}>{language === 'ko' ? '데모 종료·로그인' : 'Exit demo & log in'}</button></div>}
    {groups.length > 0 && <label className="group-switcher"><span>{language === 'ko' ? '공간 이동' : 'Switch workspace'}</span><select aria-label={language === 'ko' ? '이동할 공간 선택' : 'Choose a workspace'} value={selectedGroupId} onChange={(event) => {
      const group = groups.find((value) => value.id === Number(event.target.value));
      if (!group) { navigate('/groups'); return; }
      navigate(group.type === 'PERSONAL' ? `/calendar?groupId=${group.id}` : `/groups/${group.id}/dashboard`);
    }}><option value="">{language === 'ko' ? '전체 그룹 보기' : 'View all groups'}</option><optgroup label={language === 'ko' ? '개인 일정' : 'Personal schedule'}>{groups.filter((group) => group.type === 'PERSONAL').map((group) => <option value={group.id} key={group.id}>● {group.name}</option>)}</optgroup><optgroup label={language === 'ko' ? '팀 그룹' : 'Teams'}>{teamGroups.map((group) => <option value={group.id} key={group.id}>◆ {group.name}</option>)}</optgroup></select><small>{language === 'ko' ? '목록을 열어 다른 그룹으로 바로 이동하세요.' : 'Open the list to move directly to another group.'}</small></label>}
    <div className="app-navigation-items">{items.map((item, index) => {
      const active = item.to === '/app' ? pathname === '/app' : pathname.startsWith(item.to);
      return <Link className={active ? 'active' : ''} to={item.to} key={item.to} aria-current={active ? 'page' : undefined}>
        <span className="app-navigation-icon" aria-hidden="true">{item.icon}</span>
        <span>{labels[index]}</span>
        {item.to === '/notifications' && liveUnreadCount > 0 && <b>{liveUnreadCount > 99 ? '99+' : liveUnreadCount}</b>}
      </Link>;
    })}</div>
    <div className="language-toggle" role="group" aria-label="Language"><button type="button" aria-pressed={language === 'ko'} className={language === 'ko' ? 'active' : ''} onClick={() => setLanguage('ko')}>한글</button><button type="button" aria-pressed={language === 'en'} className={language === 'en' ? 'active' : ''} onClick={() => setLanguage('en')}>EN</button></div>
  </nav>;
}

export function Modal({ title, description, onClose, children }: {
  title: string; description?: string; onClose: () => void; children: ReactNode;
}) {
  const { t } = useLanguage();
  const backdropRef = useRef<HTMLDivElement>(null);
  const dialogRef = useRef<HTMLElement>(null);
  const onCloseRef = useRef(onClose);
  const titleId = useId();
  const [viewportStyle, setViewportStyle] = useState<CSSProperties>();
  useEffect(() => { onCloseRef.current = onClose; }, [onClose]);
  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
    const overflow = document.body.style.overflow;
    const focusableSelector = 'a[href], button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const focusDialog = () => {
      const dialog = dialogRef.current;
      if (!dialog) return;
      const preferred = dialog.querySelector<HTMLElement>('[autofocus]');
      const first = dialog.querySelector<HTMLElement>(focusableSelector);
      (preferred ?? first ?? dialog).focus();
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onCloseRef.current();
        return;
      }
      if (event.key !== 'Tab' || !dialogRef.current) return;
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(focusableSelector))
        .filter((element) => !element.hasAttribute('disabled') && element.offsetParent !== null);
      if (focusable.length === 0) {
        event.preventDefault();
        dialogRef.current.focus();
        return;
      }
      const first = focusable[0];
      const last = focusable[focusable.length - 1];
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus();
      }
    };
    document.body.style.overflow = 'hidden';
    document.addEventListener('keydown', handleKeyDown);
    const frame = window.requestAnimationFrame(focusDialog);
    return () => {
      window.cancelAnimationFrame(frame);
      document.body.style.overflow = overflow;
      document.removeEventListener('keydown', handleKeyDown);
      previousFocus?.focus();
    };
  }, []);
  useEffect(() => {
    const viewport = window.visualViewport;
    if (!viewport) return;
    let focusTimer: number | undefined;
    const syncViewport = () => {
      setViewportStyle({ height: viewport.height, top: viewport.offsetTop, bottom: 'auto' });
      const active = document.activeElement;
      if (!(active instanceof HTMLElement) || !backdropRef.current?.contains(active)) return;
      window.clearTimeout(focusTimer);
      focusTimer = window.setTimeout(() => active.scrollIntoView({ block: 'center' }), 80);
    };
    const keepInputVisible = (event: FocusEvent) => {
      if (!(event.target instanceof HTMLElement) || !event.target.matches('input, textarea, select')) return;
      window.clearTimeout(focusTimer);
      focusTimer = window.setTimeout(() => event.target instanceof HTMLElement
        && event.target.scrollIntoView({ block: 'center', behavior: 'smooth' }), 280);
    };
    syncViewport();
    viewport.addEventListener('resize', syncViewport);
    viewport.addEventListener('scroll', syncViewport);
    document.addEventListener('focusin', keepInputVisible);
    return () => {
      window.clearTimeout(focusTimer);
      viewport.removeEventListener('resize', syncViewport);
      viewport.removeEventListener('scroll', syncViewport);
      document.removeEventListener('focusin', keepInputVisible);
    };
  }, []);
  return <div ref={backdropRef} className="modal-backdrop" style={viewportStyle} role="presentation" onMouseDown={(event) => {
    if (event.target === event.currentTarget) onClose();
  }}>
    <section ref={dialogRef} className="app-modal" role="dialog" aria-modal="true" aria-labelledby={titleId} tabIndex={-1}>
      <header><div><h2 id={titleId}>{title}</h2>{description && <p>{description}</p>}</div><button type="button" className="modal-close" onClick={onClose} aria-label={t('닫기', 'Close')}>×</button></header>
      {children}
    </section>
  </div>;
}
