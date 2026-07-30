import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';
import { useLanguage } from './LanguageContext';

const publicPaths = new Set(['/', '/demo', '/product', '/b2b', '/pricing', '/contact', '/privacy', '/terms', '/paid-terms', '/refund-policy', '/site-map']);
const allowIndexing = String(import.meta.env.VITE_ALLOW_INDEXING ?? 'false') === 'true';

export function PageMeta() {
  const { pathname } = useLocation();
  const { language } = useLanguage();
  useEffect(() => {
    const title = pageTitle(pathname, language);
    const description = language === 'ko'
      ? '요청, 승인, 담당자, 업무 진행과 캘린더를 한곳에서 관리하는 팀 협업 도구'
      : 'A team workspace for requests, approvals, ownership, progress, and calendars.';
    document.title = `${title} | ${language === 'ko' ? '퇴사' : 'toesa'}`;
    setMeta('description', description);
    setMeta('robots', allowIndexing && publicPaths.has(pathname) ? 'index,follow' : 'noindex,nofollow');
    const base = String(import.meta.env.VITE_PUBLIC_SITE_URL ?? window.location.origin).replace(/\/$/, '');
    setCanonical(`${base}${pathname}`);
  }, [language, pathname]);
  return null;
}

function setMeta(name: string, content: string) {
  let element = document.head.querySelector<HTMLMetaElement>(`meta[name="${name}"]`);
  if (!element) {
    element = document.createElement('meta');
    element.name = name;
    document.head.appendChild(element);
  }
  element.content = content;
}

function setCanonical(href: string) {
  let element = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]');
  if (!element) {
    element = document.createElement('link');
    element.rel = 'canonical';
    document.head.appendChild(element);
  }
  element.href = href;
}

function pageTitle(pathname: string, language: 'ko' | 'en') {
  if (pathname === '/') return language === 'ko' ? '팀 업무와 일정 관리' : 'Team work and schedule management';
  if (pathname === '/demo') return language === 'ko' ? '읽기 전용 제품 데모' : 'Read-only product demo';
  if (pathname === '/privacy') return language === 'ko' ? '개인정보 처리방침' : 'Privacy policy';
  if (pathname === '/terms') return language === 'ko' ? '서비스 이용약관' : 'Terms of service';
  if (pathname === '/paid-terms') return language === 'ko' ? '유료서비스 이용약관' : 'Paid service terms';
  if (pathname === '/refund-policy') return language === 'ko' ? '환불 정책' : 'Refund policy';
  if (pathname === '/site-map') return language === 'ko' ? '사이트맵' : 'Site map';
  if (pathname === '/product') return language === 'ko' ? '제품' : 'Product';
  if (pathname === '/b2b') return language === 'ko' ? 'B2B 솔루션' : 'B2B solutions';
  if (pathname === '/pricing') return language === 'ko' ? '가격' : 'Pricing';
  if (pathname === '/contact') return language === 'ko' ? '문의' : 'Contact';
  if (pathname === '/app') return language === 'ko' ? '내 대시보드' : 'Dashboard';
  return language === 'ko' ? '서비스' : 'Service';
}
