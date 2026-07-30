import { request } from './client';

export type Subscription = {
  id: number; groupId: number; planCode: string; status: 'FREE' | 'TRIALING' | 'ACTIVE' | 'PAST_DUE' | 'CANCEL_AT_PERIOD_END' | 'CANCELLED';
  amount: number; currency: string; conversionChoice: 'UNDECIDED' | 'KEEP_FREE' | 'CONTINUE_PAID';
  rolloutNoticeAt?: string; decisionDeadline?: string; currentPeriodStart?: string;
  currentPeriodEnd?: string; nextBillingAt?: string; liveBillingEnabled: boolean; canStartTrial: boolean;
};
export const subscriptionApi = {
  get: (groupId: number) => request<Subscription>(`/groups/${groupId}/subscription`, {}, true),
  trial: (groupId: number) => request<Subscription>(`/groups/${groupId}/subscription/trial`, { method: 'POST' }, true),
  choose: (groupId: number, choice: 'KEEP_FREE' | 'CONTINUE_PAID') =>
    request<Subscription>(`/groups/${groupId}/subscription/conversion-choice`, {
      method: 'PUT', body: JSON.stringify({ choice }),
    }, true),
  activate: (groupId: number, paymentMethodId: number) =>
    request<Subscription>(`/groups/${groupId}/subscription/activate`, {
      method: 'POST', body: JSON.stringify({
        paymentMethodId, recurringBillingConsent: true, policyConsent: true,
        termsVersion: '2026-07-27-v3', refundPolicyVersion: '2026-07-27-v3',
      }),
    }, true),
  cancel: (groupId: number) => request<Subscription>(`/groups/${groupId}/subscription/cancel`, { method: 'POST' }, true),
};
