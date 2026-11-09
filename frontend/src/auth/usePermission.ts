import { useAuth } from './useAuth';
import { hasPermission } from './permissions';

/** UX-only checks; the backend is authoritative. */
export function usePermission() {
  const { user } = useAuth();
  return { can: (required?: string | string[]) => hasPermission(user?.permissions, required) };
}
