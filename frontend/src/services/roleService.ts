import { api } from '../api/client';
import type { Role, RoleInput } from '../types/masterData';

export const roleService = {
  list: () => api.get<Role[]>('/roles').then((r) => r.data),
  create: (b: RoleInput) => api.post<Role>('/roles', b).then((r) => r.data),
  update: (id: number, b: Omit<RoleInput, 'roleCode'>) => api.put<Role>(`/roles/${id}`, b).then((r) => r.data),
};
