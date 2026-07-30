import { FormEvent, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate } from 'react-router-dom';
import { accessToken, errorMessage, sessionMode } from '../../../api/client';
import { userApi, UserProfile } from '../../../api/userApi';
import { AppNavigation } from '../../../app/AppNavigation';
import { authApi } from '../../../api/authApi';
import { useLanguage } from '../../../app/LanguageContext';
import { AuthenticatedImage } from '../../../app/AuthenticatedImage';

export function ProfilePage() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<UserProfile>();
  const [nickname, setNickname] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [profileImageUrl, setProfileImageUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    userApi.profile().then(value => {
      setProfile(value);
      setNickname(value.nickname);
      setPhoneNumber(value.phoneNumber ?? '');
      setProfileImageUrl(value.profileImageUrl ?? '');
    }).catch(value => setError(errorMessage(value))).finally(() => setLoading(false));
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault(); setSaving(true); setError(''); setSaved(false);
    try {
      const updated = await userApi.updateProfile({ nickname, phoneNumber, profileImageUrl });
      setProfile(updated); setSaved(true);
    } catch (value) { setError(errorMessage(value)); }
    finally { setSaving(false); }
  }

  async function logout() {
    await authApi.logout().catch(() => undefined);
    accessToken.clear();
    sessionMode.clear();
    navigate('/login');
  }

  async function uploadImage(file?: File) {
    if (!file) return;
    setUploading(true); setError(''); setSaved(false);
    try {
      const updated = await userApi.uploadProfileImage(file);
      setProfile(updated);
      setProfileImageUrl(updated.profileImageUrl ?? '');
      setSaved(true);
    } catch (value) { setError(errorMessage(value)); }
    finally { setUploading(false); }
  }

  if (!accessToken.get()) return <Navigate to="/login" replace />;
  if (loading) return <main className="center-page">{t('프로필을 불러오는 중...', 'Loading profile...')}</main>;
  return <><AppNavigation /><main className="profile-page app-page"><header className="profile-page-header"><span className="page-eyebrow">MY PROFILE</span><h1>{t('프로필', 'Profile')}</h1><p>{t('나를 표현하는 정보를 편하게 관리하세요.', 'Manage how you appear to your teammates.')}</p></header><section className="profile-card-new">
    <div className="profile-hero"><div className="profile-avatar">{profileImageUrl ? <AuthenticatedImage src={profileImageUrl} alt={t('프로필', 'Profile')} /> : nickname.slice(0, 1)}</div><div><h2>{nickname}</h2>{profile && <p>{profile.username} · {profile.email}</p>}</div></div>
    <form className="form" onSubmit={submit}>
      <label className="field"><span>{t('닉네임', 'Nickname')}</span><input value={nickname} onChange={event => setNickname(event.target.value)} minLength={1} maxLength={30} required /></label>
      <label className="field"><span>{t('전화번호', 'Phone number')}</span><input value={phoneNumber} onChange={event => setPhoneNumber(event.target.value)} placeholder="010-1234-5678" maxLength={20} /></label>
      <div className="field"><span>{t('프로필 이미지', 'Profile image')}</span><label className={`profile-image-picker ${uploading ? 'pending' : ''}`}><span className="profile-image-picker-preview">{profileImageUrl ? <AuthenticatedImage src={profileImageUrl} alt="" /> : nickname.slice(0, 1)}</span><span><strong>{uploading ? t('프로필 이미지를 업로드하고 있어요', 'Uploading your profile image') : t('새 프로필 이미지 선택', 'Choose a new profile image')}</strong><small>{t('JPG, PNG, GIF · 최대 5MB', 'JPG, PNG, GIF · up to 5MB')}</small></span><i aria-hidden="true">＋</i><input className="sr-only" type="file" accept="image/jpeg,image/png,image/gif" disabled={uploading} onChange={event => uploadImage(event.target.files?.[0])} /></label></div>
      {error && <p className="error">{error}</p>}{saved && <p className="success-message">{t('프로필을 저장했습니다.', 'Profile saved.')}</p>}
      <button className="primary" disabled={saving}>{saving ? t('저장 중...', 'Saving...') : t('저장', 'Save')}</button>
    </form>
    <div className="profile-secondary-actions"><Link className="account-link" to="/account">{t('계정 및 보안 설정', 'Account & security')} →</Link><button className="profile-logout" type="button" onClick={logout}>{t('로그아웃', 'Log out')}</button></div>
  </section></main></>;
}
