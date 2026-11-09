import { api } from '../api/client';

export interface Branding { name: string; hasIcon: boolean; iconVersion: number }

export const DEFAULT_BRAND_NAME = 'MaterialBook';
export const ICON_URL = '/api/v1/settings/branding/icon';

export const brandingService = {
  get: () => api.get<Branding>('/settings/branding').then((r) => r.data),
  /** multipart/form-data: `name` + optional `icon` file (the browser sets the boundary). */
  update: (name: string, icon?: File) => {
    const body = new FormData();
    body.append('name', name);
    if (icon) body.append('icon', icon);
    return api.put<Branding>('/settings/branding', body, { headers: { 'Content-Type': undefined } }).then((r) => r.data);
  },
  removeIcon: () => api.delete('/settings/branding/icon').then(() => undefined),
};
