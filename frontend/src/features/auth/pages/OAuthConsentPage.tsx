import { FormEvent, useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi, ConsentRequest, OAuthSignupStatus } from '../../../api/authApi';
import { accessToken, errorMessage, sessionMode } from '../../../api/client';
import { useLanguage } from '../../../app/LanguageContext';
import { AuthLayout, SubmitButton } from '../components/AuthComponents';
import { Consent } from './SignupPage';

const emptyConsents: ConsentRequest = {
  termsAgreed: false,
  privacyAgreed: false,
  ageConfirmed: false,
  notificationAgreed: false,
  marketingAgreed: false,
};

export function OAuthConsentPage() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [profile, setProfile] = useState<OAuthSignupStatus>();
  const [consents, setConsents] = useState(emptyConsents);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    authApi.oauthSignupStatus()
      .then(setProfile)
      .catch((caught) => setError(errorMessage(caught)));
  }, []);

  const setConsent = (key: keyof ConsentRequest, value: boolean) =>
    setConsents(current => ({ ...current, [key]: value }));
  const setAll = (value: boolean) => setConsents({
    termsAgreed: value, privacyAgreed: value, ageConfirmed: value,
    notificationAgreed: value, marketingAgreed: value,
  });
  const allAgreed = Object.values(consents).every(Boolean);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!consents.termsAgreed || !consents.privacyAgreed || !consents.ageConfirmed) {
      setError(t('필수 항목에 동의해 주세요.', 'Please accept the required items.'));
      return;
    }
    setPending(true); setError('');
    try {
      const tokens = await authApi.completeOAuthSignup(consents);
      accessToken.set(tokens.accessToken, tokens.expiresIn);
      sessionMode.clear();
      navigate('/app', { replace: true });
    } catch (caught) {
      setError(errorMessage(caught));
    } finally {
      setPending(false);
    }
  }

  async function cancel() {
    await authApi.cancelOAuthSignup().catch(() => undefined);
    navigate('/login', { replace: true });
  }

  return <AuthLayout title={t('Google 가입 동의', 'Complete Google sign-up')} description={t(
    'Google에서 확인한 정보를 검토하고 퇴사 가입 동의를 선택해 주세요.',
    'Review the Google account information and choose your toesa consents.',
  )}>
    {profile ? <form className="form" onSubmit={submit}>
      <section className="oauth-profile" aria-label={t('Google 제공 정보', 'Information from Google')}>
        <span>Google</span><strong>{profile.name}</strong><small>{profile.email}</small>
      </section>
      <p className="oauth-data-note">{t(
        '퇴사는 Google 비밀번호와 Google 액세스·리프레시 토큰을 저장하지 않습니다. 인증에 필요한 Google 계정 식별자, 이메일, 이름만 전달받습니다.',
        'toesa does not store your Google password or Google access/refresh tokens. It receives only the Google account identifier, email, and name needed for authentication.',
      )}</p>
      <fieldset className="signup-consents"><legend>{t('약관 및 수신 설정', 'Terms and preferences')}</legend>
        <label className="consent-all"><input type="checkbox" checked={allAgreed} onChange={event => setAll(event.target.checked)} /><span><strong>{t('전체 동의', 'Agree to all')}</strong><small>{t('선택 항목은 거부해도 가입할 수 있습니다.', 'Optional choices are not required.')}</small></span></label>
        <div className="consent-divider" />
        <Consent checked={consents.termsAgreed} onChange={value => setConsent('termsAgreed', value)} label={t('[필수] 서비스 이용약관 동의', '[Required] Terms of service')} href="/terms">
          {t('퇴사의 업무·그룹·알림 기능 이용 조건, 계정 관리, 금지 행위 및 서비스 책임 범위를 확인하고 동의합니다.', 'I accept the conditions for toesa task, group, and notification features, account management, prohibited conduct, and service responsibilities.')}
        </Consent>
        <Consent checked={consents.privacyAgreed} onChange={value => setConsent('privacyAgreed', value)} label={t('[필수] Google 가입 개인정보 수집·이용 동의', '[Required] Google sign-up data collection and use')} href="/privacy">
          {t('항목: Google 계정 식별자, 이메일, 이름 · 목적: 본인 식별, Google 로그인, 계정 생성 및 서비스 제공 · 기간: 회원 탈퇴 시까지(법령상 보존 예외) · 동의를 거부할 수 있으나 필수 정보이므로 Google 가입을 완료할 수 없습니다.', 'Items: Google account identifier, email, and name · Purpose: identity, Google sign-in, account creation, and service delivery · Retention: until account deletion, except where legally required · You may refuse, but Google sign-up cannot be completed without this required data.')}
        </Consent>
        <Consent checked={consents.ageConfirmed} onChange={value => setConsent('ageConfirmed', value)} label={t('[필수] 만 14세 이상 확인', '[Required] I am at least 14 years old')}>
          {t('만 14세 미만 아동의 개인정보는 현재 가입 절차에서 처리하지 않습니다.', 'This sign-up flow is not available to children under 14.')}
        </Consent>
        <Consent checked={consents.notificationAgreed} onChange={value => setConsent('notificationAgreed', value)} label={t('[선택] 업무 알림 메시지 수신', '[Optional] Work notification messages')}>
          {t('이메일·기기 알림 주소를 마감 임박, 멘션 등 업무 알림에 동의 철회 또는 탈퇴 시까지 사용합니다. 거부해도 가입할 수 있으며 필수 서비스 안내는 별도로 전달될 수 있습니다.', 'Email and device notification addresses are used for deadlines and mentions until consent withdrawal or account deletion. You may decline; essential service notices may still be delivered separately.')}
        </Consent>
        <Consent checked={consents.marketingAgreed} onChange={value => setConsent('marketingAgreed', value)} label={t('[선택] 기능 소식·혜택 정보 수신', '[Optional] Product news and offers')}>
          {t('이메일·기기 알림 주소를 새 기능, 활용 팁과 프로모션 전달에 동의 철회 또는 탈퇴 시까지 사용합니다. 거부해도 가입할 수 있고 언제든 철회할 수 있습니다.', 'Email and device notification addresses are used for product updates, tips, and promotions until consent withdrawal or account deletion. You may decline and withdraw anytime.')}
        </Consent>
      </fieldset>
      {error && <p className="error">{error}</p>}
      <SubmitButton pending={pending} disabled={!consents.termsAgreed || !consents.privacyAgreed || !consents.ageConfirmed}>{t('동의하고 가입 완료', 'Accept and create account')}</SubmitButton>
      <button type="button" className="oauth-cancel" onClick={cancel}>{t('취소하고 로그인으로', 'Cancel and return to login')}</button>
    </form> : <section className="oauth-consent-loading">{error
      ? <><p className="error">{error}</p><Link to="/login">{t('로그인부터 다시 시작', 'Start again from login')}</Link></>
      : t('Google 계정 정보를 확인하고 있습니다…', 'Checking your Google account…')}</section>}
  </AuthLayout>;
}
