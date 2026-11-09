import { api } from '../api/client';
import type { Page, Party, PartyInput, PartyType, Status } from '../types/masterData';

export interface PartyQuery { q?: string; type?: PartyType; status?: Status; page: number; size: number }

export const partyService = {
  list: (p: PartyQuery) => api.get<Page<Party>>('/parties', { params: p }).then((r) => r.data),
  create: (b: PartyInput) => api.post<Party>('/parties', b).then((r) => r.data),
  update: (id: number, b: PartyInput) => api.put<Party>(`/parties/${id}`, b).then((r) => r.data),
  setActive: (id: number, active: boolean) =>
    api.post<Party>(`/parties/${id}/${active ? 'activate' : 'deactivate'}`).then((r) => r.data),
};
