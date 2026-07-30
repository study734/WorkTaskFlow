import { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { activatePwaUpdate, isPwaInstallAvailable, isPwaUpdateAvailable, promptPwaInstall } from './pwa';
import { useLanguage } from './LanguageContext';
import { sessionMode } from '../api/client';

export function PwaStatus() {
  const { t } = useLanguage();
  const { pathname } = useLocation();
  const [online, setOnline] = useState(navigator.onLine);
  const [installable, setInstallable] = useState(isPwaInstallAvailable());
  const [updateAvailable, setUpdateAvailable] = useState(isPwaUpdateAvailable());

  useEffect(() => {
    const onlineHandler = () => setOnline(true);
    const offlineHandler = () => setOnline(false);
    const installHandler = () => setInstallable(isPwaInstallAvailable());
    const installedHandler = () => setInstallable(false);
    const updateHandler = () => setUpdateAvailable(true);
    window.addEventListener('online', onlineHandler);
    window.addEventListener('offline', offlineHandler);
    window.addEventListener('pwa-install-available', installHandler);
    window.addEventListener('pwa-installed', installedHandler);
    window.addEventListener('pwa-update-available', updateHandler);
    return () => {
      window.removeEventListener('online', onlineHandler);
      window.removeEventListener('offline', offlineHandler);
      window.removeEventListener('pwa-install-available', installHandler);
      window.removeEventListener('pwa-installed', installedHandler);
      window.removeEventListener('pwa-update-available', updateHandler);
    };
  }, []);

  const corePath = pathname === '/app' || pathname.startsWith('/groups')
    || pathname.startsWith('/tasks') || pathname === '/calendar'
    || pathname === '/notifications' || pathname === '/profile'
    || pathname === '/account' || pathname === '/payments';
  if (!corePath || sessionMode.isDemo() || (online && !installable && !updateAvailable)) return null;
  return <aside className={`pwa-status ${online ? '' : 'offline'}`} role="status" aria-live="polite">
    <span>{!online
      ? t('오프라인입니다. 저장된 화면만 볼 수 있으며 조회·변경은 연결 후 가능합니다.', 'You are offline. Reconnect to view or update current data.')
      : updateAvailable ? t('새 버전이 준비되었습니다.', 'A new version is ready.') : t('이 기기에 앱으로 설치할 수 있습니다.', 'You can install this app on this device.')}</span>
    {online && updateAvailable && <button type="button" onClick={activatePwaUpdate}>{t('업데이트', 'Update')}</button>}
    {online && !updateAvailable && installable && <button type="button" onClick={promptPwaInstall}>{t('설치', 'Install')}</button>}
  </aside>;
}
