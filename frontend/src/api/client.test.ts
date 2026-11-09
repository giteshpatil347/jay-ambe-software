import type { AxiosAdapter, InternalAxiosRequestConfig } from 'axios';
import axios from 'axios';
import { api, authApi, getAccessToken, setAccessToken, setSessionLostHandler } from './client';

const resp = (cfg: InternalAxiosRequestConfig, status: number, data: unknown = {}) =>
  status >= 400
    ? Promise.reject(Object.assign(new axios.AxiosError('err', undefined, cfg, null, { status, data, statusText: '', headers: {}, config: cfg }), {}))
    : Promise.resolve({ status, data, statusText: '', headers: {}, config: cfg });

describe('refresh interceptor', () => {
  afterEach(() => { vi.restoreAllMocks(); setAccessToken(null); });

  it('shares ONE refresh across concurrent 401s, then retries with new token', async () => {
    setAccessToken('old');
    const refresh = vi.fn(async (cfg: InternalAxiosRequestConfig) => {
      await new Promise((r) => setTimeout(r, 10));
      return resp(cfg, 200, { accessToken: 'new', expiresIn: 600, user: {} });
    });
    authApi.defaults.adapter = refresh as AxiosAdapter;
    api.defaults.adapter = ((cfg: InternalAxiosRequestConfig) =>
      cfg.headers.Authorization === 'Bearer new' ? resp(cfg, 200, { ok: 1 }) : resp(cfg, 401)) as AxiosAdapter;

    const rs = await Promise.all([api.get('/a'), api.get('/b'), api.get('/c')]);
    expect(rs.map((r) => r.data.ok)).toEqual([1, 1, 1]);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBe('new');
  });

  it('logs out when refresh fails', async () => {
    setAccessToken('old');
    authApi.defaults.adapter = ((cfg: InternalAxiosRequestConfig) => resp(cfg, 401)) as AxiosAdapter;
    const lost = vi.fn();
    setSessionLostHandler(lost);
    api.defaults.adapter = ((cfg: InternalAxiosRequestConfig) => resp(cfg, 401)) as AxiosAdapter;
    await expect(api.get('/a')).rejects.toBeTruthy();
    expect(lost).toHaveBeenCalledTimes(1);
    expect(getAccessToken()).toBeNull();
  });
});
