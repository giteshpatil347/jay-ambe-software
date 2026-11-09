import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { AuthResponse } from './types';

export const api = axios.create({ baseURL: '/api/v1' });
// Bare instance (no interceptors) for the auth endpoints that must not recurse.
export const authApi = axios.create({ baseURL: '/api/v1', headers: { 'X-Requested-With': 'MaterialBook' } });

// Access token lives in memory only — never localStorage/sessionStorage.
let accessToken: string | null = null;
export const getAccessToken = () => accessToken;
export const setAccessToken = (t: string | null) => { accessToken = t; };

let onSessionLost: () => void = () => {};
export const setSessionLostHandler = (fn: () => void) => { onSessionLost = fn; };

let inFlight: Promise<AuthResponse> | null = null;

/** One shared in-flight refresh; concurrent callers await the same promise. */
export function refreshSession(): Promise<AuthResponse> {
  inFlight ??= authApi
    .post<AuthResponse>('/auth/refresh')
    .then((r) => { accessToken = r.data.accessToken; return r.data; })
    .finally(() => { inFlight = null; });
  return inFlight;
}

export const loginRequest = (mobile: string, password: string) =>
  authApi.post<AuthResponse>('/auth/login', { mobile, password }).then((r) => r.data);
export const logoutRequest = () => authApi.post('/auth/logout').catch(() => undefined);

api.interceptors.request.use((cfg) => {
  if (accessToken) cfg.headers.Authorization = `Bearer ${accessToken}`;
  return cfg;
});

type Retriable = InternalAxiosRequestConfig & { _retried?: boolean };

api.interceptors.response.use(undefined, async (error: AxiosError) => {
  const cfg = error.config as Retriable | undefined;
  if (error.response?.status !== 401 || !cfg || cfg._retried) throw error;
  cfg._retried = true;
  try {
    await refreshSession();
  } catch {
    accessToken = null;
    onSessionLost();
    throw error;
  }
  return api(cfg);
});
