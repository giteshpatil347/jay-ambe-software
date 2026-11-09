import { api } from '../api/client';
import type { CreateUserInput, ModuleGroup, Page, PermissionOverride, Status, User, UserInput } from '../types/masterData';

export interface UserQuery { search?: string; status?: Status; page: number; size: number }

export const userService = {
  list: (p: UserQuery) => api.get<Page<User>>('/users', { params: p }).then((r) => r.data),
  get: (id: number) => api.get<User>(`/users/${id}`).then((r) => r.data),
  create: (b: CreateUserInput) => api.post<User>('/users', b).then((r) => r.data),
  update: (id: number, b: UserInput) => api.put<User>(`/users/${id}`, b).then((r) => r.data),
  setActive: (id: number, active: boolean) =>
    api.post<User>(`/users/${id}/${active ? 'activate' : 'deactivate'}`).then((r) => r.data),
  resetPassword: (id: number, password: string) => api.post(`/users/${id}/reset-password`, { password }).then(() => undefined),
  assignRoles: (id: number, roleCodes: string[]) => api.put<User>(`/users/${id}/roles`, { roleCodes }).then((r) => r.data),
  /** Bare array body. */
  setOverrides: (id: number, overrides: PermissionOverride[]) =>
    api.put<User>(`/users/${id}/permissions`, overrides).then((r) => r.data),
  effectivePermissions: (id: number) =>
    api.get<{ userId: number; permissions: string[] }>(`/users/${id}/effective-permissions`).then((r) => r.data.permissions),
  permissionCatalogue: () => api.get<ModuleGroup[]>('/permissions').then((r) => r.data),
};
