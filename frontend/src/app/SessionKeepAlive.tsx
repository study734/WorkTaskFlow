import { useEffect } from 'react';
import { accessToken, refreshAccessToken } from '../api/client';
import { isRunningStandalone } from './pwa';

const REFRESH_EARLY_MS = 5 * 60 * 1000;
const MIN_CHECK_MS = 30 * 1000;

export function SessionKeepAlive() {
  useEffect(() => {
    if (!isRunningStandalone()) return;
    let timer: number | undefined;

    const schedule = () => {
      if (timer) window.clearTimeout(timer);
      if (!accessToken.get()) return;
      const delay = Math.max(MIN_CHECK_MS, accessToken.expiresAt() - Date.now() - REFRESH_EARLY_MS);
      timer = window.setTimeout(check, delay);
    };
    const check = () => {
      if (document.visibilityState !== 'visible' || !navigator.onLine || !accessToken.get()) {
        schedule();
        return;
      }
      if (accessToken.expiresAt() - Date.now() <= REFRESH_EARLY_MS) {
        refreshAccessToken().catch(() => undefined).finally(schedule);
      } else {
        schedule();
      }
    };
    const onResume = () => { if (document.visibilityState === 'visible') check(); };

    schedule();
    document.addEventListener('visibilitychange', onResume);
    window.addEventListener('online', check);
    window.addEventListener('access-token-updated', schedule);
    return () => {
      if (timer) window.clearTimeout(timer);
      document.removeEventListener('visibilitychange', onResume);
      window.removeEventListener('online', check);
      window.removeEventListener('access-token-updated', schedule);
    };
  }, []);
  return null;
}
