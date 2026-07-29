const API_BASE = String(import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/$/, '');

export type ApiError = { code?: string; message?: string; fieldErrors?: Record<string, string> };

export async function request<T>(path: string, init: RequestInit = {}, authenticated = false): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (authenticated) {
    const token = accessToken.get();
    if (token) headers.set('Authorization', `Bearer ${token}`);
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
  } catch {
    const offline = typeof navigator !== 'undefined' && !navigator.onLine;
    throw {
      code: offline ? 'OFFLINE' : 'NETWORK_ERROR',
      message: offline
        ? localeText('오프라인에서는 조회하거나 변경할 수 없습니다. 연결 후 다시 시도해 주세요.', 'You are offline. Reconnect and try again.')
        : localeText('서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.', 'Could not connect to the server. Try again shortly.'),
    } satisfies ApiError;
  }
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: localeText('요청 처리 중 오류가 발생했습니다.', 'An error occurred while processing the request.') }));
    throw error as ApiError;
  }
  return response.status === 204 ? (undefined as T) : response.json();
}

export type DownloadedFile = { blob: Blob; filename: string };

export async function requestFile(
  path: string,
  init: RequestInit = {},
  authenticated = false,
): Promise<DownloadedFile> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (authenticated) {
    const token = accessToken.get();
    if (token) headers.set('Authorization', `Bearer ${token}`);
  }
  let response: Response;
  try {
    response = await fetch(`${API_BASE}${path}`, { ...init, headers, credentials: 'include' });
  } catch {
    throw {
      code: 'NETWORK_ERROR',
      message: localeText(
        '서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.',
        'Could not connect to the server. Try again shortly.',
      ),
    } satisfies ApiError;
  }
  if (!response.ok) {
    throw await response.json().catch(() => ({
      message: localeText(
        '파일을 다운로드하지 못했습니다.',
        'The file could not be downloaded.',
      ),
    })) as ApiError;
  }
  return {
    blob: await response.blob(),
    filename: responseFilename(response.headers.get('Content-Disposition')),
  };
}

export function saveDownloadedFile(file: DownloadedFile) {
  const url = URL.createObjectURL(file.blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = file.filename;
  anchor.hidden = true;
  document.body.append(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 0);
}

function responseFilename(disposition: string | null) {
  const encoded = disposition?.match(/filename\*=UTF-8''([^;]+)/i)?.[1];
  if (encoded) {
    try { return decodeURIComponent(encoded); } catch { /* use the plain fallback */ }
  }
  return disposition?.match(/filename="?([^";]+)"?/i)?.[1] ?? 'report.pdf';
}

export function serviceUrl(path: string) {
  return `${API_BASE.replace(/\/api\/v1$/, '')}${path}`;
}

export const accessToken = {
  get: () => localStorage.getItem('accessToken'),
  set: (value: string) => localStorage.setItem('accessToken', value),
  clear: () => localStorage.removeItem('accessToken'),
};

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
