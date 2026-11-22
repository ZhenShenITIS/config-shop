import { useSyncExternalStore } from 'react';

type SessionStatus = 'loading' | 'authenticated' | 'anonymous' | 'unavailable';

let accessToken: string | undefined;
let sessionStatus: SessionStatus = 'loading';
let refreshPromise: Promise<string | undefined> | undefined;
const listeners = new Set<() => void>();

export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string) {
    super(message);
  }
}

export type ErrorType<T> = ApiError & { readonly payload?: T };

export function useSession() {
  return useSyncExternalStore(subscribe, () => sessionStatus);
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  return () => { listeners.delete(listener); };
}

function setStatus(status: SessionStatus) {
  sessionStatus = status;
  listeners.forEach((listener) => listener());
}

export function setSession(token?: string) {
  accessToken = token;
  setStatus(token ? 'authenticated' : 'anonymous');
}

export async function withSessionLock<T>(action: () => Promise<T>): Promise<T> {
  // Refresh tokens rotate on use. Serializing requests also protects other tabs.
  return navigator.locks ? await navigator.locks.request('config-shop-session', action) : action();
}

async function request<T>(url: string, options: RequestInit, token?: string): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Accept', 'application/json');
  if (token) headers.set('Authorization', 'Bearer ' + token);
  const response = await fetch(url, { ...options, headers, credentials: 'same-origin' });

  if (!response.ok) {
    const payload = await response.json().catch(() => undefined);
    throw new ApiError(response.status, payload?.error ?? 'request_failed',
      payload?.message ?? 'Не удалось выполнить запрос. Попробуйте ещё раз.');
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}

export function restoreSession(): Promise<string | undefined> {
  if (!refreshPromise) {
    refreshPromise = withSessionLock(async () => {
      try {
        const session = await request<{ accessToken: string }>('/api/auth/refresh', {
          method: 'POST', headers: { 'X-CSRF-Protection': '1' },
        });
        setSession(session.accessToken);
        return session.accessToken;
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          setSession();
          return undefined;
        }
        if (!accessToken) setStatus('unavailable');
        throw error;
      }
    }).finally(() => { refreshPromise = undefined; });
  }
  return refreshPromise;
}

export async function apiFetch<T>(url: string, options: RequestInit = {}): Promise<T> {
  const authenticated = url.startsWith('/api/me/') || url === '/api/auth/link-telegram';
  if (!authenticated) return request<T>(url, options);

  const previous = accessToken;
  try {
    return await request<T>(url, options, previous);
  } catch (error) {
    if (!(error instanceof ApiError) || error.status !== 401) throw error;
    options.signal?.throwIfAborted();
    const current = accessToken && accessToken !== previous ? accessToken : await restoreSession();
    if (!current) throw error;
    try {
      return await request<T>(url, options, current);
    } catch (retryError) {
      if (retryError instanceof ApiError && retryError.status === 401 && current === accessToken) setSession();
      throw retryError;
    }
  }
}
