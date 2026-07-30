import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { accessToken, errorMessage, sessionMode } from '../../../api/client';
import { userApi } from '../../../api/userApi';
import { authApi, DeviceSessionResponse } from '../../../api/authApi';
import { useLanguage } from '../../../app/LanguageContext';

export function AccountPage() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [withdrawPassword, setWithdrawPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [deviceSessions, setDeviceSessions] = useState<DeviceSessionResponse[]>([]);

  useEffect(() => {
    authApi.sessions().then(value => setDeviceSessions(value.sessions)).catch(() => undefined);
  }, []);

  async function changePassword(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try {
      await userApi.changePassword(currentPassword, newPassword);
      accessToken.clear();
      sessionMode.clear();
      navigate('/login', { replace: true });
    } catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  async function withdraw() {
    if (!window.confirm(t('탈퇴하면 개인정보가 익명화되고 다시 로그인할 수 없습니다. 계속할까요?', 'Your personal data will be anonymized and you will no longer be able to log in. Continue?'))) return;
    setBusy(true); setError('');
    try {
      await userApi.withdraw(withdrawPassword);
      accessToken.clear();
      sessionMode.clear();
      navigate('/login', { replace: true });
    } catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  async function logoutAll() {
    if (!window.confirm(t('이 기기를 포함한 모든 기기에서 로그아웃할까요?', 'Log out on every device, including this one?'))) return;
    setBusy(true); setError('');
    try {
      await authApi.logoutAll();
      accessToken.clear();
      sessionMode.clear();
      navigate('/login', { replace: true });
    } catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  async function logoutSession(session: DeviceSessionResponse) {
    if (!window.confirm(t(`${session.deviceName}의 로그인을 해제할까요?`, `Log out ${session.deviceName}?`))) return;
    setBusy(true); setError('');
    try {
      await authApi.logoutSession(session.sessionId);
      if (session.current) {
        accessToken.clear();
        sessionMode.clear();
        navigate('/login', { replace: true });
      } else {
        setDeviceSessions(current => current.filter(value => value.sessionId !== session.sessionId));
      }
    } catch (value) { setError(errorMessage(value)); }
    finally { setBusy(false); }
  }

  if (!accessToken.get()) return <Navigate to="/login" replace />;
  return <main className="center-page"><section className="auth-card profile-card">
    <Link to="/profile">← {t('프로필로', 'Back to profile')}</Link><h1>{t('계정 설정', 'Account settings')}</h1>
    <p className="muted">{t('비밀번호 변경 후에는 모든 기기에서 다시 로그인해야 합니다.', 'After changing your password, you must log in again on every device.')}</p>
    <Link className="account-link" to="/payments">{t('결제수단 및 테스트 관리', 'Manage payment methods and tests')} →</Link>
    <section className="danger-zone">
      <h2>{t('로그인된 기기', 'Signed-in devices')}</h2>
      <p>{t('기기를 분실했거나 세션이 의심되면 모든 기기의 로그인 세션을 즉시 종료하세요.', 'If a device is lost or a session looks suspicious, end every signed-in session immediately.')}</p>
      <div className="device-session-list">
        {deviceSessions.map(session => <article key={session.sessionId}>
          <div><strong>{session.deviceName}{session.current ? t(' · 현재 기기', ' · This device') : ''}</strong>
            <small>{session.clientMode} · {session.ipAddress} · {t('최근 사용', 'Last used')} {new Date(session.lastUsedAt).toLocaleString()}</small></div>
          <button className="secondary" type="button" disabled={busy} onClick={() => logoutSession(session)}>{t('로그아웃', 'Log out')}</button>
        </article>)}
        {deviceSessions.length === 0 && <p className="muted">{t('활성 로그인 정보를 불러오지 못했습니다.', 'No active sessions were found.')}</p>}
      </div>
      <button className="danger-button" type="button" disabled={busy} onClick={logoutAll}>{t('모든 기기에서 로그아웃', 'Log out on all devices')}</button>
    </section>
    <form className="form" onSubmit={changePassword}>
      <label className="field"><span>{t('현재 비밀번호', 'Current password')}</span><input type="password" value={currentPassword} onChange={event => setCurrentPassword(event.target.value)} required /></label>
      <label className="field"><span>{t('새 비밀번호', 'New password')}</span><input type="password" value={newPassword} onChange={event => setNewPassword(event.target.value)} minLength={8} maxLength={72} required /></label>
      <button className="primary" disabled={busy}>{t('비밀번호 변경', 'Change password')}</button>
    </form>
    <div className="danger-zone">
      <h2>{t('회원 탈퇴', 'Delete account')}</h2><p>{t('일반 계정은 현재 비밀번호가 필요합니다. 소셜 계정은 최근 5분 이내 재로그인이 필요합니다.', 'Password accounts require the current password. Social accounts require a login within the last five minutes.')}</p>
      <label className="field"><span>{t('현재 비밀번호(소셜 계정은 비워 둠)', 'Current password (leave blank for social accounts)')}</span><input type="password" value={withdrawPassword} onChange={event => setWithdrawPassword(event.target.value)} /></label>
      <button className="danger-button" type="button" disabled={busy} onClick={withdraw}>{t('회원 탈퇴', 'Delete account')}</button>
    </div>
    {error && <p className="error account-error">{error}</p>}
  </section></main>;
}
