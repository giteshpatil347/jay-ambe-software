import { api } from '../api/client';
import type { AuditFilters, AuditPage } from '../types/payment';

const clean = <T extends object>(p: T) =>
  Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== ''));

export const auditService = {
  list: (f: AuditFilters) => api.get<AuditPage>('/audit-logs', { params: clean(f) }).then((r) => r.data),
};
