import { FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { authApi } from '../../../api/authApi';
import { errorMessage } from '../../../api/client';
import { AuthLayout, Field, SubmitButton } from '../components/AuthComponents';
import { useLanguage } from '../../../app/LanguageContext';

export function SignupPage() {
  const { t } = useLanguage();
  const navigate = useNavigate();
  const [form, setForm] = useState({ username: '', email: '', name: '', password: '', passwordConfirm: '', verificationCode: '' });
  const [consents, setConsents] = useState({ termsAgreed: false, privacyAgreed: false, ageConfirmed: false, notificationAgreed: false, marketingAgreed: false });
  const [sent, setSent] = useState(false); const [verified, setVerified] = useState(false); const [pending, setPending] = useState(false); const [error, setError] = useState('');
  const update = (key: keyof typeof form, value: string) => { setForm(current => ({ ...current, [key]: value })); if (key === 'email') { setSent(false); setVerified(false); } };
  async function sendCode() { setPending(true); setError(''); try { await authApi.sendVerification(form.email); setSent(true); } catch (e) { setError(errorMessage(e)); } finally { setPending(false); } }
  async function verifyCode() { setPending(true); setError(''); try { await authApi.confirmVerification(form.email, form.verificationCode); setVerified(true); } catch (e) { setError(errorMessage(e)); } finally { setPending(false); } }
  async function submit(event: FormEvent) { event.preventDefault(); if (form.password !== form.passwordConfirm) return setError(t('비밀번호가 서로 다릅니다.', 'Passwords do not match.')); if (!verified) return setError(t('이메일 인증을 완료해 주세요.', 'Please verify your email.')); if (!consents.termsAgreed || !consents.privacyAgreed || !consents.ageConfirmed) return setError(t('필수 약관에 동의해 주세요.', 'Please accept the required terms.')); setPending(true); setError(''); try { await authApi.signup({ username: form.username, email: form.email, name: form.name, password: form.password, verificationCode: form.verificationCode, ...consents }); navigate('/login?signup=success'); } catch (e) { setError(errorMessage(e)); } finally { setPending(false); } }
  const allAgreed = Object.values(consents).every(Boolean);
  const setAll = (value: boolean) => setConsents({ termsAgreed: value, privacyAgreed: value, ageConfirmed: value, notificationAgreed: value, marketingAgreed: value });
  const setConsent = (key: keyof typeof consents, value: boolean) => setConsents((current) => ({ ...current, [key]: value }));
  return <AuthLayout title={t('회원가입', 'Sign up')} description={t('기본 정보를 입력하고 이메일을 인증해 주세요.', 'Enter your details and verify your email.')}><form className="form" onSubmit={submit}>
    <Field label={t('이름', 'Name')} value={form.name} onChange={e => update('name', e.target.value)} minLength={2} required /><Field label={t('아이디', 'Username')} value={form.username} onChange={e => update('username', e.target.value)} pattern="[A-Za-z0-9_]{4,20}" placeholder={t('영문, 숫자, 밑줄 4~20자', '4–20 letters, numbers, or underscores')} required />
    <div className="inline-field"><Field label={t('이메일', 'Email')} type="email" value={form.email} onChange={e => update('email', e.target.value)} disabled={verified} required /><button type="button" onClick={sendCode} disabled={pending || !form.email || verified}>{sent ? t('재발송', 'Resend') : t('인증번호 받기', 'Send code')}</button></div>
    {sent && <div className="inline-field"><Field label={t('인증번호', 'Verification code')} inputMode="numeric" maxLength={6} value={form.verificationCode} onChange={e => update('verificationCode', e.target.value.replace(/\D/g, ''))} disabled={verified} required /><button type="button" onClick={verifyCode} disabled={pending || form.verificationCode.length !== 6 || verified}>{verified ? t('인증 완료', 'Verified') : t('확인', 'Verify')}</button></div>}
    <Field label={t('비밀번호', 'Password')} type="password" minLength={8} value={form.password} onChange={e => update('password', e.target.value)} placeholder={t('8자 이상', 'At least 8 characters')} required /><Field label={t('비밀번호 확인', 'Confirm password')} type="password" value={form.passwordConfirm} onChange={e => update('passwordConfirm', e.target.value)} required />
    <fieldset className="signup-consents"><legend>{t('약관 및 수신 설정', 'Terms and preferences')}</legend>
      <label className="consent-all"><input type="checkbox" checked={allAgreed} onChange={(event) => setAll(event.target.checked)} /><span><strong>{t('전체 동의', 'Agree to all')}</strong><small>{t('선택 항목은 동의하지 않아도 가입할 수 있습니다.', 'Optional choices are not required to sign up.')}</small></span></label>
      <div className="consent-divider" />
      <Consent checked={consents.termsAgreed} onChange={(value) => setConsent('termsAgreed', value)} label={t('[필수] 서비스 이용약관 동의', '[Required] Terms of service')} href="/terms">
        {t('퇴사의 업무·그룹·알림 기능 이용 조건, 계정 관리, 금지 행위 및 서비스 책임 범위를 확인하고 동의합니다.', 'I accept the conditions for toesa task, group, and notification features, account management, prohibited conduct, and service responsibilities.')}
      </Consent>
      <Consent checked={consents.privacyAgreed} onChange={(value) => setConsent('privacyAgreed', value)} label={t('[필수] 개인정보 수집·이용 동의', '[Required] Personal information collection and use')} href="/privacy">
        {t('항목: 이름, 아이디, 이메일, 비밀번호 해시 · 목적: 회원가입, 인증, 서비스 제공 · 기간: 탈퇴 시까지(법령상 보존 예외) · 동의를 거부할 수 있으나 필수 정보이므로 가입할 수 없습니다.', 'Items: name, username, email, password hash · Purpose: signup, authentication, service · Retention: until withdrawal, except where legally required · You may refuse, but an account cannot be created without this required information.')}
      </Consent>
      <Consent checked={consents.ageConfirmed} onChange={(value) => setConsent('ageConfirmed', value)} label={t('[필수] 만 14세 이상 확인', '[Required] I am at least 14 years old')}>
        {t('만 14세 미만 아동의 개인정보는 현재 가입 절차에서 처리하지 않습니다.', 'This sign-up flow is not available to children under 14.')}
      </Consent>
      <Consent checked={consents.notificationAgreed} onChange={(value) => setConsent('notificationAgreed', value)} label={t('[선택] 업무 알림 메시지 수신', '[Optional] Work notification messages')}>
        {t('이메일·기기 알림 주소를 마감 임박, 멘션 등 업무 알림 전달에 동의 철회 또는 탈퇴 시까지 사용합니다. 동의하지 않아도 가입할 수 있으며 앱 안의 필수 상태 알림은 표시됩니다.', 'Your email and device notification address are used for deadlines and mentions until withdrawal of consent or account deletion. You may decline and still sign up; essential in-app status notices remain available.')}
      </Consent>
      <Consent checked={consents.marketingAgreed} onChange={(value) => setConsent('marketingAgreed', value)} label={t('[선택] 기능 소식·혜택 정보 수신', '[Optional] Product news and offers')}>
        {t('이메일·기기 알림 주소를 새 기능, 활용 팁과 프로모션 전달에 동의 철회 또는 탈퇴 시까지 사용합니다. 동의하지 않아도 가입할 수 있고 언제든 철회할 수 있습니다.', 'Your email and device notification address are used for product updates, tips, and promotions until withdrawal of consent or account deletion. You may decline and can withdraw anytime.')}
      </Consent>
    </fieldset>
    {error && <p className="error">{error}</p>}<SubmitButton pending={pending} disabled={!verified || !consents.termsAgreed || !consents.privacyAgreed || !consents.ageConfirmed}>{t('가입하기', 'Create account')}</SubmitButton></form><p className="bottom-link">{t('이미 계정이 있나요?', 'Already have an account?')} <Link to="/login">{t('로그인', 'Log in')}</Link></p></AuthLayout>;
}

export function Consent({ checked, onChange, label, href, children }: {
  checked: boolean; onChange: (value: boolean) => void; label: string; href?: string; children?: string;
}) {
  const { t } = useLanguage();
  return <div className="consent-item"><label><input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)} /><span>{label}</span>{href && <Link to={href} target="_blank" rel="noreferrer">{t('보기', 'View')}</Link>}</label>{children && <details><summary>{t('내용 보기', 'View details')}</summary><p>{children}</p></details>}</div>;
}
