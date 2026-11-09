import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { useAuth } from './useAuth';
import { usePermission } from './usePermission';

/** Gate for authenticated routes; with `permission` also enforces it (else /403). */
export function RequirePermission({ permission, children }: { permission?: string | string[]; children: ReactNode }) {
  const { user, loading } = useAuth();
  const { can } = usePermission();
  const location = useLocation();

  if (loading) return <Spin fullscreen />;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (user.passwordChangeRequired && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }
  if (!can(permission)) return <Navigate to="/403" replace />;
  return <>{children}</>;
}
