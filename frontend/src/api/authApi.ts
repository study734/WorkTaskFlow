import { request, serviceUrl, sessionClientHeaders } from './client';

export type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number };
export type ProviderResponse = { google: boolean; kakao: boolean };
export type OAuthSignupStatus = { provider: string; email: string; name: string; expiresAt: string };
export type ConsentRequest = {
  termsAgreed: boolean; privacyAgreed: boolean; ageConfirmed: boolean;
  notificationAgreed: boolean; marketingAgreed: boolean;
};
export type MeResponse = { userId: number; username: string; email: string; name: string; role: string };
export type SignupRequest = {
  username: string; email: string; name: string; password: string; verificationCode: string;
} & ConsentRequest;
export type DeviceSessionResponse = {
  sessionId: string; deviceName: string; clientMode: 'WEB' | 'PWA'; ipAddress: string;
  createdAt: string; lastUsedAt: string; expiresAt: string; current: boolean;
};

export const authApi = {
  sendVerification: (email: string) =>
    request<void>('/auth/email-verifications', { method: 'POST', body: JSON.stringify({ email }) }),
  confirmVerification: (email: string, code: string) =>
    request<void>('/auth/email-verifications/confirm', { method: 'POST', body: JSON.stringify({ email, code }) }),
  signup: (body: SignupRequest) => request('/auth/signup', { method: 'POST', body: JSON.stringify(body) }),
  login: (username: string, password: string, mfaCode?: string) =>
    request<TokenResponse>('/auth/login', {
      method: 'POST',
      headers: sessionClientHeaders(),
      body: JSON.stringify({ username, password, mfaCode: mfaCode || undefined }),
    }),
  demo: () => request<TokenResponse>('/auth/demo-session', { method: 'POST' }),
  refresh: () => request<TokenResponse>('/auth/refresh', {
    method: 'POST', headers: sessionClientHeaders(),
  }),
  logout: () => request<void>('/auth/logout', { method: 'POST' }),
  logoutAll: () => request<void>('/auth/logout-all', { method: 'POST' }, true),
  remindUsername: (email: string) =>
    request<void>('/auth/username-reminders', { method: 'POST', body: JSON.stringify({ email }) }),
  requestPasswordReset: (email: string) =>
    request<void>('/auth/password-resets', { method: 'POST', body: JSON.stringify({ email }) }),
  resetPassword: (email: string, token: string, newPassword: string) =>
    request<void>('/auth/password-resets/confirm', {
      method: 'POST', body: JSON.stringify({ email, token, newPassword }),
    }),
  providers: () => request<ProviderResponse>('/auth/providers'),
  oauthSignupStatus: () => request<OAuthSignupStatus>('/auth/oauth-signup'),
  completeOAuthSignup: (body: ConsentRequest) =>
    request<TokenResponse>('/auth/oauth-signup/complete', {
      method: 'POST', headers: sessionClientHeaders(), body: JSON.stringify(body),
    }),
  cancelOAuthSignup: () => request<void>('/auth/oauth-signup', { method: 'DELETE' }),
  me: () => request<MeResponse>('/auth/me', {}, true),
  sessions: () => request<{ sessions: DeviceSessionResponse[] }>('/auth/sessions', {}, true),
  logoutSession: (sessionId: string) =>
    request<void>(`/auth/sessions/${encodeURIComponent(sessionId)}`, { method: 'DELETE' }, true),
  socialUrl: (provider: 'google' | 'kakao') =>
    serviceUrl(`/oauth2/authorization/${provider}${isStandalonePwa() ? '?client_mode=PWA' : ''}`),
};

function isStandalonePwa() {
  return window.matchMedia('(display-mode: standalone)').matches
    || ('standalone' in navigator && Boolean((navigator as Navigator & { standalone?: boolean }).standalone));
}
