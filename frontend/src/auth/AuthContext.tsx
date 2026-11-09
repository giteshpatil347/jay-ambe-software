import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { api, loginRequest, logoutRequest, refreshSession, setAccessToken, setSessionLostHandler } from '../api/client';
import type { AuthResponse, AuthUser } from '../api/types';

export interface AuthState {
  user: AuthUser | null;
  /** true until the initial silent refresh has settled */
  loading: boolean;
  login: (mobile: string, password: string) => Promise<AuthUser>;
  logout: () => Promise<void>;
  /** Backend returns 204 and revokes every refresh token, so this ends the session. */
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  /** One-shot notice shown on /login (e.g. after a password change). */
  flash: string | null;
  clearFlash: () => void;
}

export const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [loading, setLoading] = useState(true);
  const [flash, setFlash] = useState<string | null>(null);
  const qc = useQueryClient();

  const clear = useCallback(() => { setAccessToken(null); setUser(null); qc.clear(); }, [qc]);

  useEffect(() => {
    setSessionLostHandler(clear);
    let live = true;
    refreshSession()
      .then((r) => live && setUser(r.user))
      .catch(() => undefined)
      .finally(() => live && setLoading(false));
    return () => { live = false; };
  }, [clear]);

  const login = useCallback(async (mobile: string, password: string) => {
    const r: AuthResponse = await loginRequest(mobile, password);
    setAccessToken(r.accessToken);
    setUser(r.user);
    return r.user;
  }, []);

  const logout = useCallback(async () => { await logoutRequest(); clear(); }, [clear]);

  const changePassword = useCallback(async (currentPassword: string, newPassword: string) => {
    await api.post('/auth/change-password', { currentPassword, newPassword });
    setFlash('Password changed. Please sign in with your new password.');
    clear();
  }, [clear]);
  const clearFlash = useCallback(() => setFlash(null), []);

  const value = useMemo(
    () => ({ user, loading, login, logout, changePassword, flash, clearFlash }),
    [user, loading, login, logout, changePassword, flash, clearFlash],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
