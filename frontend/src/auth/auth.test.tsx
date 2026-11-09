import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { renderHook } from '@testing-library/react';
import type { ReactNode } from 'react';
import { AuthContext, type AuthState } from './AuthContext';
import { RequirePermission } from './RequirePermission';
import { usePermission } from './usePermission';

const state = (perms: string[] | null, extra = {}): AuthState => ({
  user: perms && { id: 1, fullName: 'T', permissions: perms, passwordChangeRequired: false, ...extra },
  loading: false, login: async () => { throw new Error('x'); }, logout: async () => {},
  changePassword: async () => {}, flash: null, clearFlash: () => {},
});
const wrap = (s: AuthState) => ({ children }: { children: ReactNode }) =>
  <AuthContext.Provider value={s}>{children}</AuthContext.Provider>;

describe('usePermission', () => {
  it('checks single, any-of and missing', () => {
    const { result } = renderHook(() => usePermission(), { wrapper: wrap(state(['A.VIEW'])) });
    expect(result.current.can('A.VIEW')).toBe(true);
    expect(result.current.can('B.VIEW')).toBe(false);
    expect(result.current.can(['B.VIEW', 'A.VIEW'])).toBe(true);
    expect(result.current.can()).toBe(true);
  });
});

describe('RequirePermission', () => {
  const ui = (s: AuthState, perm: string) => render(
    <AuthContext.Provider value={s}>
      <MemoryRouter initialEntries={['/p']}>
        <Routes>
          <Route path="/p" element={<RequirePermission permission={perm}>secret</RequirePermission>} />
          <Route path="/login" element={<>login</>} />
          <Route path="/403" element={<>forbidden</>} />
          <Route path="/change-password" element={<>change</>} />
        </Routes>
      </MemoryRouter>
    </AuthContext.Provider>);

  it('renders when permitted', () => { ui(state(['X.VIEW']), 'X.VIEW'); expect(screen.getByText('secret')).toBeInTheDocument(); });
  it('403 when not permitted', () => { ui(state(['Y.VIEW']), 'X.VIEW'); expect(screen.getByText('forbidden')).toBeInTheDocument(); });
  it('login when anonymous', () => { ui(state(null), 'X.VIEW'); expect(screen.getByText('login')).toBeInTheDocument(); });
  it('forces password change', () => { ui(state(['X.VIEW'], { passwordChangeRequired: true }), 'X.VIEW'); expect(screen.getByText('change')).toBeInTheDocument(); });
});
