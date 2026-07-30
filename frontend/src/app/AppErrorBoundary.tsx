import { Component, type ErrorInfo, type ReactNode } from 'react';

export class AppErrorBoundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Application render failure', error, info.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    const english = localStorage.getItem('language') === 'en';
    return <main className="center-page app-failure" role="alert">
      <div>
        <span aria-hidden="true">!</span>
        <h1>{english ? 'This screen could not be displayed.' : '화면을 표시하지 못했습니다.'}</h1>
        <p>{english ? 'Reload the app. Your saved server data is not affected.' : '앱을 다시 불러와 주세요. 서버에 저장된 데이터에는 영향이 없습니다.'}</p>
        <div>
          <button type="button" onClick={() => window.location.reload()}>{english ? 'Reload' : '다시 불러오기'}</button>
          <a href="/">{english ? 'Go to home' : '홈으로 이동'}</a>
        </div>
      </div>
    </main>;
  }
}
