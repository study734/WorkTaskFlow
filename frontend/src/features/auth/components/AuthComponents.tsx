import { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { BrandMark } from '../../../app/BrandMark';
import { useLanguage } from '../../../app/LanguageContext';

export function AuthLayout({ title, description, children }: {
  title: string; description: string; children: ReactNode;
}) {
  const { t } = useLanguage();
  return <main className="auth-page"><section className="brand"><span className="brand-mark"><BrandMark /></span><p>{t('퇴사', 'TOESA')}</p><h1>{t('업무는 남기고,', 'Keep the work,')}<br />{t('야근은 남기지 마세요.', 'not the overtime.')}</h1><span>{t('요청부터 완료와 리포트까지. 퇴근을 사수하는 팀 업무관리.', 'From requests to reports—a team workspace built to help work finish on time.')}</span></section><section className="auth-card"><header><Link to="/" className="mobile-logo"><BrandMark />{t('퇴사', 'toesa')}</Link><h2>{title}</h2><p>{description}</p></header>{children}</section></main>;
}

export function Field({ label, ...props }: React.InputHTMLAttributes<HTMLInputElement> & { label: string }) {
  return <label className="field"><span>{label}</span><input {...props} /></label>;
}

export function SubmitButton({ children, pending, disabled }: {
  children: ReactNode; pending?: boolean; disabled?: boolean;
}) {
  const { t } = useLanguage();
  return <button className="primary" type="submit" disabled={pending || disabled}>
    {pending ? t('처리 중...', 'Processing...') : children}
  </button>;
}
