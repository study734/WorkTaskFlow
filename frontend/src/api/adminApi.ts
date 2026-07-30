import { request } from './client';

export type AdminOverview = { users: number; activeUsers: number; suspendedUsers: number; groups: number; teamGroups: number; subscriptions: number; paymentAttempts: number; failedPayments: number; reportDownloads: number; reportDeliveries: number; failedReportDeliveries: number };
export type AdminUser = { id: number; username: string; maskedEmail: string; nickname: string; role: string; status: string; createdAt: string; lastLoginAt?: string };
export type AdminGroup = { id: number; name: string; type: string; membershipPlan: string; activeMembers: number; subscriptionStatus: string; reportScheduleActive: boolean; paidUntil?: string; createdAt: string };
export type AdminSubscription = { id: number; groupId: number; groupName: string; status: string; conversionChoice: string; amount: number; currentPeriodEnd?: string; decisionDeadline?: string };
export type AdminPayment = { id: number; userId: number; operation: string; orderId?: string; amount?: number; status: string; httpStatus?: number; providerCode?: string; providerMessage?: string; retryCount: number; createdAt: string; updatedAt: string };
export type AdminReportDownload = { id: number; groupId: number; groupName: string; requestedByUserId: number; scope: string; periodType: string; createdAt: string };
export type AdminReportDelivery = { id: number; groupId: number; groupName: string; periodType: string; language: string; status: string; retryCount: number; errorCode?: string; lastAttemptAt?: string; nextRetryAt?: string; sentAt?: string; createdAt: string };
export type AdminMfaStatus = { enabled: boolean; sessionVerified: boolean; encryptionConfigured: boolean; enabledAt?: string };
export type AdminMfaSetup = { secret: string; otpauthUri: string };
export type AdminAudit = { id: number; actorUserId?: number; method: string; path: string; status: number; outcome: string; ipAddress?: string; requestId?: string; occurredAt: string };
type Page<T> = { items: T[]; page: number; size: number; totalElements: number; totalPages: number };
export const adminApi = {
  overview: () => request<AdminOverview>('/admin/overview', {}, true),
  users: () => request<Page<AdminUser>>('/admin/users?size=50', {}, true),
  userStatus: (id: number, status: 'ACTIVE' | 'SUSPENDED') => request<AdminUser>(`/admin/users/${id}/status`, { method: 'PATCH', body: JSON.stringify({ status }) }, true),
  groups: () => request<Page<AdminGroup>>('/admin/groups?size=50', {}, true),
  subscriptions: () => request<AdminSubscription[]>('/admin/subscriptions', {}, true),
  payments: () => request<Page<AdminPayment>>('/admin/payments?size=50', {}, true),
  reportDownloads: () => request<Page<AdminReportDownload>>('/admin/report-downloads?size=50', {}, true),
  reportDeliveries: () => request<Page<AdminReportDelivery>>('/admin/report-deliveries?size=50', {}, true),
  announce: (decisionDeadline: string) => request<{ notifiedGroups: number; decisionDeadline: string }>('/admin/subscriptions/rollout-notice', { method: 'POST', body: JSON.stringify({ decisionDeadline }) }, true),
  mfaStatus: () => request<AdminMfaStatus>('/admin/mfa/status', {}, true),
  mfaSetup: () => request<AdminMfaSetup>('/admin/mfa/setup', { method: 'POST' }, true),
  mfaConfirm: (code: string) => request<{ recoveryCodes: string[] }>('/admin/mfa/confirm', {
    method: 'POST', body: JSON.stringify({ code }),
  }, true),
  auditLogs: () => request<Page<AdminAudit>>('/admin/audit-logs?size=50', {}, true),
};
