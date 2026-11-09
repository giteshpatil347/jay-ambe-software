import { api } from '../api/client';
import type { Dashboard } from '../types/report';

/** `range` = inclusive from/to (ISO dates); without it the server uses the last 30 days. */
export const dashboardService = {
  get: (range?: { from: string; to: string }) => api.get<Dashboard>('/reports/dashboard', { params: range }).then((r) => r.data),
};
