import { useEffect, useRef, useState } from 'react';
import { Navigate } from 'react-router-dom';
import { accessToken, errorMessage } from '../../../api/client';
import { PaymentAttempt, PaymentConfig, PaymentMethod, paymentApi } from '../../../api/paymentApi';
import { AppNavigation } from '../../../app/AppNavigation';
import { useLanguage } from '../../../app/LanguageContext';

declare global {
  interface Window {
    TossPayments?: (clientKey: string) => {
      payment: (options: { customerKey: string }) => {
        requestBillingAuth: (options: {
          method: 'CARD'; successUrl: string; failUrl: string;
        }) => Promise<void>;
      };
    };
  }
}

export function PaymentsPage() {
  const { t, language } = useLanguage();
  const [config, setConfig] = useState<PaymentConfig>();
  const [methods, setMethods] = useState<PaymentMethod[]>([]);
  const [attempts, setAttempts] = useState<PaymentAttempt[]>([]);
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const redirectHandled = useRef(false);

  useEffect(() => {
    if (!accessToken.get()) return;
    const query = new URLSearchParams(window.location.search);
    const authKey = query.get('authKey');
    const customerKey = query.get('customerKey');
    const failureCode = query.get('code');
    if (authKey || customerKey || failureCode) window.history.replaceState({}, '', '/payments');
    Promise.all([paymentApi.config(), paymentApi.methods(), paymentApi.attempts()])
      .then(async ([configValue, methodValues, attemptValues]) => {
        setConfig(configValue); setMethods(methodValues); setAttempts(attemptValues);
        if (redirectHandled.current) return;
        redirectHandled.current = true;
        if (failureCode) throw { message: t('결제수단 인증이 취소되었거나 실패했습니다.', 'Payment method authorization was cancelled or failed.') };
        if (authKey && customerKey) {
          setPending(true);
          try {
            const method = await paymentApi.issue(authKey, customerKey);
            setMethods((current) => [method, ...current]);
            setMessage(t('결제수단을 안전하게 등록했습니다.', 'Payment method added securely.'));
          } finally {
            setPending(false);
          }
        }
      })
      .catch((value) => setError(errorMessage(value)))
      .finally(() => setLoading(false));
  }, []);

  async function addMethod() {
    if (!config?.configured || !config.clientKey || !config.customerKey) return;
    setPending(true); setError(''); setMessage('');
    try {
      await loadTossSdk();
      const payment = window.TossPayments!(config.clientKey).payment({ customerKey: config.customerKey });
      await payment.requestBillingAuth({
        method: 'CARD',
        successUrl: `${window.location.origin}/payments`,
        failUrl: `${window.location.origin}/payments`,
      });
    } catch (value) {
      setError(errorMessage(value));
      setPending(false);
    }
  }

  async function testCharge(methodId: number) {
    if (!window.confirm(t('테스트 환경에서 100원 결제 호출을 실행할까요?', 'Run a KRW 100 charge in the test environment?'))) return;
    setPending(true); setError(''); setMessage('');
    try {
      const attempt = await paymentApi.testCharge(methodId, 100);
      setAttempts((current) => [attempt, ...current]);
      setMessage(t('테스트 호출에 성공했습니다.', 'Test call succeeded.'));
    } catch (value) {
      setError(errorMessage(value));
      setAttempts(await paymentApi.attempts().catch(() => attempts));
    } finally { setPending(false); }
  }

  async function retry(attemptId: number) {
    setPending(true); setError(''); setMessage('');
    try {
      const updated = await paymentApi.retry(attemptId);
      setAttempts((current) => current.map((value) => value.id === updated.id ? updated : value));
      setMessage(t('같은 멱등키로 안전하게 재전송했습니다.', 'Safely resent with the same idempotency key.'));
    } catch (value) {
      setError(errorMessage(value));
      setAttempts(await paymentApi.attempts().catch(() => attempts));
    } finally { setPending(false); }
  }

  if (!accessToken.get()) return <Navigate to="/login?next=%2Fpayments" replace />;
  const activeMethods = methods.filter((method) => method.status === 'ACTIVE');
  return <><AppNavigation /><main className="payments-page app-page">
    <header><span className="page-eyebrow">PAYMENTS</span><h1>{t('결제수단 및 테스트', 'Payment methods & tests')}</h1><p>{t('카드 정보는 토스페이먼츠가 처리하며 이 서비스에는 저장하지 않습니다.', 'Card details are handled by Toss Payments and are never stored in this service.')}</p></header>
    {loading && <p className="payment-notice" role="status">{t('결제 정보를 불러오는 중...', 'Loading payment information...')}</p>}
    {!loading && !config?.configured && <p className="payment-notice">{t('서버 결제 환경변수를 설정하면 기능이 활성화됩니다.', 'Configure the server payment environment variables to enable this feature.')}</p>}
    {error && <p className="error">{error}</p>}{message && <p className="success-message">{message}</p>}
    <section className="payment-panel"><div className="payment-heading"><div><h2>{t('등록된 결제수단', 'Payment methods')}</h2><small>{t('민감한 카드번호와 빌링키는 화면과 로그에 표시하지 않습니다.', 'Sensitive card numbers and billing keys are never shown or logged.')}</small></div><button className="primary" type="button" disabled={pending || !config?.configured} onClick={addMethod}>{t('결제수단 추가', 'Add payment method')}</button></div>
      {!loading && activeMethods.length === 0 ? <p className="empty-state">{t('등록된 결제수단이 없습니다.', 'No payment methods have been added.')}</p> : <div className="payment-method-list">{activeMethods.map((method) => <div className="payment-method-row" key={method.id}><div><strong>{method.maskedNumber || t('등록된 카드', 'Saved card')}</strong><small>{method.issuerCode || 'Toss Payments'} · {formatDate(method.createdAt, language)}</small></div>{config?.testMode && <button type="button" disabled={pending} onClick={() => testCharge(method.id)}>{t('100원 테스트', 'Test KRW 100')}</button>}</div>)}</div>}
    </section>
    <section className="payment-panel"><h2>{t('API 호출 로그', 'API call log')}</h2>{!loading && attempts.length === 0 ? <p className="empty-state">{t('아직 결제 API 호출 기록이 없습니다.', 'No payment API calls have been recorded yet.')}</p> : <div className="payment-log-list">{attempts.map((attempt) => <div className="payment-log-row" key={attempt.id}><div><strong>{attempt.operationType === 'TEST_CHARGE' ? t('테스트 결제', 'Test charge') : attempt.operationType === 'SUBSCRIPTION_CHARGE' ? t('구독 결제', 'Subscription charge') : t('결제수단 등록', 'Payment method registration')}</strong><small>{formatDate(attempt.createdAt, language)} · {attempt.status}{attempt.providerCode ? ` · ${attempt.providerCode}` : ''}</small></div>{attempt.status === 'FAILED' && attempt.operationType === 'TEST_CHARGE' && attempt.retryCount < 3 && <button type="button" disabled={pending} onClick={() => retry(attempt.id)}>{t('재전송', 'Retry')}</button>}</div>)}</div>}</section>
  </main></>;
}

function loadTossSdk() {
  if (window.TossPayments) return Promise.resolve();
  return new Promise<void>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = 'https://js.tosspayments.com/v2/standard';
    script.onload = () => resolve();
    script.onerror = () => reject({ message: '토스페이먼츠 SDK를 불러오지 못했습니다.' });
    document.head.appendChild(script);
  });
}

function formatDate(value: string, language: 'ko' | 'en') {
  return new Intl.DateTimeFormat(language === 'ko' ? 'ko-KR' : 'en-US', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));
}
