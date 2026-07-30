import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './app/App';
import { registerPwa } from './app/pwa';
import './styles.css';
import { bootstrapAuthSession } from './api/client';
import { AppErrorBoundary } from './app/AppErrorBoundary';

function trackVisualViewport() {
  const viewport = window.visualViewport;
  if (!viewport) return;
  const sync = () => {
    document.documentElement.style.setProperty('--visual-viewport-height', `${viewport.height}px`);
    document.documentElement.classList.toggle('keyboard-open', window.innerHeight - viewport.height > 150);
  };
  sync();
  viewport.addEventListener('resize', sync);
  viewport.addEventListener('scroll', sync);
  window.addEventListener('resize', sync);
}

trackVisualViewport();
registerPwa();
const root = ReactDOM.createRoot(document.getElementById('root')!);
root.render(<main className="center-page">인증 상태 확인 중...</main>);
bootstrapAuthSession()
  .catch(() => undefined)
  .finally(() => root.render(<React.StrictMode><AppErrorBoundary><App /></AppErrorBoundary></React.StrictMode>));
