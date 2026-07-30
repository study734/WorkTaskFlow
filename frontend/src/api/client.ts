const API_BASE = String(import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/$/, '');

export type ApiError = { code?: string; message?: string; fieldErrors?: Record<string, string> };
type TokenResponse = { accessToken: string; tokenType: string; expiresIn: number };

let refreshInFlight: Promise<TokenResponse> | undefined;
let currentAccessToken: string | null = null;
let currentAccessExpiresAt = 0;
const SESSION_HINT = 'hasRefreshSession';
const REFRESH_LOCK = 'totaskflowRefreshLock';
const REQUEST_TIMEOUT_MS = 30_000;
const DOWNLOAD_TIMEOUT_MS = 90_000;

if (localStorage.getItem('accessToken')) {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('accessTokenExpiresAt');
  localStorage.setItem(SESSION_HINT, 'true');
}

export async function request<T>(path: string, init: RequestInit = {}, authenticated = false): Promise<T> {
  return performRequest<T>(path, init, authenticated, true);
}

export async function requestBlob(path: string, filenameFallback: string) {
  async function run(allowRefresh: boolean): Promise<{ blob: Blob; filename: string }> {
    const headers = new Headers();
    const token = accessToken.get();
    if (token) headers.set('Authorization', `Bearer ${token}`);
    const response = await fetchWithTimeout(`${API_BASE}${path}`, { headers, credentials: 'include' }, DOWNLOAD_TIMEOUT_MS);
    if (response.status === 401 && allowRefresh) {
      await refreshAccessToken();
      return run(false);
    }
    if (!response.ok) throw await response.json().catch(() => ({ message: localeText('다운로드하지 못했습니다.', 'Download failed.') }));
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
    const plain = disposition.match(/filename=\"?([^\";]+)\"?/i)?.[1];
    return { blob: await response.blob(), filename: encoded ? decodeURIComponent(encoded) : plain ?? filenameFallback };
  }
  return run(true);
}

export function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url; anchor.download = filename; document.body.appendChild(anchor); anchor.click(); anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

async function performRequest<T>(path: string, init: RequestInit, authenticated: boolean, allowRefresh: boolean): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (authenticated) {
    const token = accessToken.get();
    if (token) headers.set('Authorization', `Bearer ${token}`);
  }
  let response: Response;
  try {
    const timeout = init.body instanceof FormData ? DOWNLOAD_TIMEOUT_MS : REQUEST_TIMEOUT_MS;
    response = await fetchWithTimeout(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' }, timeout);
  } catch (error) {
    const apiError = error as ApiError;
    if (apiError?.code === 'REQUEST_TIMEOUT' || apiError?.code === 'REQUEST_CANCELLED') throw apiError;
    const offline = typeof navigator !== 'undefined' && !navigator.onLine;
    throw {
      code: offline ? 'OFFLINE' : 'NETWORK_ERROR',
      message: offline
        ? localeText('오프라인에서는 조회하거나 변경할 수 없습니다. 연결 후 다시 시도해 주세요.', 'You are offline. Reconnect and try again.')
        : localeText('서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.', 'Could not connect to the server. Try again shortly.'),
    } satisfies ApiError;
  }
  if (response.status === 401 && authenticated && allowRefresh) {
    try {
      await refreshAccessToken();
      return performRequest<T>(path, init, true, false);
    } catch {
      accessToken.clear();
    }
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: localeText('요청 처리 중 오류가 발생했습니다.', 'An error occurred while processing the request.') }));
    throw error as ApiError;
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

async function fetchWithTimeout(input: RequestInfo | URL, init: RequestInit, timeoutMs: number) {
  const controller = new AbortController();
  let timedOut = false;
  const externalSignal = init.signal;
  const cancel = () => controller.abort(externalSignal?.reason);
  if (externalSignal?.aborted) cancel();
  else externalSignal?.addEventListener('abort', cancel, { once: true });
  const timer = window.setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  try {
    return await fetch(input, { ...init, signal: controller.signal });
  } catch (error) {
    if (timedOut) {
      throw {
        code: 'REQUEST_TIMEOUT',
        message: localeText('응답 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요.', 'The request timed out. Try again shortly.'),
      } satisfies ApiError;
    }
    if (externalSignal?.aborted) {
      throw { code: 'REQUEST_CANCELLED', message: localeText('요청이 취소되었습니다.', 'The request was cancelled.') } satisfies ApiError;
    }
    throw error;
  } finally {
    window.clearTimeout(timer);
    externalSignal?.removeEventListener('abort', cancel);
  }
}

export function refreshAccessToken() {
  if (!refreshInFlight) {
    refreshInFlight = withRefreshLock(() => performRequest<TokenResponse>('/auth/refresh', {
      method: 'POST', headers: sessionClientHeaders(),
    }, false, false))
      .then((token) => {
        accessToken.set(token.accessToken, token.expiresIn);
        return token;
      })
      .finally(() => { refreshInFlight = undefined; });
  }
  return refreshInFlight;
}

export function serviceUrl(path: string) {
  return `${API_BASE.replace(/\/api\/v1$/, '')}${path}`;
}

export const accessToken = {
  get: () => currentAccessToken,
  set: (value: string, expiresIn = 3600) => {
    currentAccessToken = value;
    currentAccessExpiresAt = Date.now() + expiresIn * 1000;
    localStorage.setItem(SESSION_HINT, 'true');
    window.dispatchEvent(new Event('access-token-updated'));
  },
  expiresAt: () => currentAccessExpiresAt,
  clear: () => {
    currentAccessToken = null;
    currentAccessExpiresAt = 0;
    localStorage.removeItem(SESSION_HINT);
  },
};

export async function bootstrapAuthSession() {
  if (accessToken.get() || localStorage.getItem(SESSION_HINT) !== 'true') return;
  try {
    await refreshAccessToken();
  } catch (error) {
    const code = (error as ApiError)?.code;
    if (code !== 'OFFLINE' && code !== 'NETWORK_ERROR') accessToken.clear();
    throw error;
  }
}

export function sessionClientHeaders() {
  const headers: Record<string, string> = {
    'X-Device-Id': deviceId(),
    'X-Device-Name': navigator.platform || '웹 브라우저',
  };
  const standalone = window.matchMedia('(display-mode: standalone)').matches
    || ('standalone' in navigator && Boolean((navigator as Navigator & { standalone?: boolean }).standalone));
  if (standalone) headers['X-Client-Mode'] = 'PWA';
  return headers;
}

function deviceId() {
  const existing = localStorage.getItem('deviceId');
  if (existing) return existing;
  const value = typeof crypto.randomUUID === 'function'
    ? crypto.randomUUID()
    : Array.from(crypto.getRandomValues(new Uint8Array(16)), byte => byte.toString(16).padStart(2, '0')).join('');
  localStorage.setItem('deviceId', value);
  return value;
}

async function withRefreshLock<T>(work: () => Promise<T>): Promise<T> {
  if (navigator.locks) {
    return navigator.locks.request('totaskflow-refresh-token', { mode: 'exclusive' }, work);
  }
  const owner = typeof crypto.randomUUID === 'function' ? crypto.randomUUID() : `${Date.now()}-${Math.random()}`;
  const started = Date.now();
  while (Date.now() - started < 10_000) {
    const now = Date.now();
    const current = JSON.parse(localStorage.getItem(REFRESH_LOCK) ?? 'null') as { owner: string; expiresAt: number } | null;
    if (!current || current.expiresAt <= now) {
      localStorage.setItem(REFRESH_LOCK, JSON.stringify({ owner, expiresAt: now + 10_000 }));
      const acquired = JSON.parse(localStorage.getItem(REFRESH_LOCK) ?? 'null') as { owner?: string } | null;
      if (acquired?.owner === owner) {
        try { return await work(); }
        finally {
          const latest = JSON.parse(localStorage.getItem(REFRESH_LOCK) ?? 'null') as { owner?: string } | null;
          if (latest?.owner === owner) localStorage.removeItem(REFRESH_LOCK);
        }
      }
    }
    await new Promise(resolve => window.setTimeout(resolve, 100));
  }
  return work();
}

export const sessionMode = {
  isDemo: () => localStorage.getItem('sessionMode') === 'demo',
  setDemo: () => localStorage.setItem('sessionMode', 'demo'),
  clear: () => localStorage.removeItem('sessionMode'),
};

export function errorMessage(error: unknown) {
  const value = error as ApiError;
  if (localStorage.getItem('language') === 'en' && value?.message && /[가-힣]/.test(value.message)) {
    return 'The request could not be completed. Check your input or try again.';
  }
  return value?.message ?? localeText('요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.', 'The request could not be completed. Try again shortly.');
}

function localeText(ko: string, en: string) { return localStorage.getItem('language') === 'en' ? en : ko; }
