import { request } from './client';

export type PaymentConfig = { configured: boolean; testMode: boolean; clientKey?: string; customerKey?: string };
export type PaymentMethod = {
  id: number; provider: string; issuerCode?: string; maskedNumber?: string;
  status: 'ACTIVE' | 'INACTIVE' | 'DELETED'; createdAt: string;
};
export type PaymentAttempt = {
  id: number; paymentMethodId?: number; operationType: 'BILLING_KEY_ISSUE' | 'TEST_CHARGE' | 'SUBSCRIPTION_CHARGE';
  orderId?: string; amount?: number; status: 'PENDING' | 'SUCCESS' | 'FAILED';
  httpStatus?: number; providerCode?: string; providerMessage?: string; retryCount: number; createdAt: string;
};

export const paymentApi = {
  config: () => request<PaymentConfig>('/payments/config', {}, true),
  methods: () => request<PaymentMethod[]>('/payments/methods', {}, true),
  issue: (authKey: string, customerKey: string) => request<PaymentMethod>('/payments/methods', {
    method: 'POST', body: JSON.stringify({ authKey, customerKey }),
  }, true),
  attempts: () => request<PaymentAttempt[]>('/payments/attempts', {}, true),
  testCharge: (methodId: number, amount: number) => request<PaymentAttempt>(`/payments/methods/${methodId}/test-charge`, {
    method: 'POST', body: JSON.stringify({ amount }),
  }, true),
  retry: (attemptId: number) => request<PaymentAttempt>(`/payments/attempts/${attemptId}/retry`, {
    method: 'POST',
  }, true),
};
