import { api } from '../api/client';
import type { TxBody, TxDetail, TxFilters, TxPage, TxType } from '../types/material';

const base = (t: TxType) => (t === 'IN' ? '/material-in' : '/material-out');
const clean = <T extends object>(p: T) =>
  Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== ''));

export const materialService = {
  list: (t: TxType, f: TxFilters) => api.get<TxPage>(base(t), { params: clean({ ...f, page: f.page - 1 }) }).then((r) => r.data),
  get: (t: TxType, id: number) => api.get<TxDetail>(`${base(t)}/${id}`).then((r) => r.data),
  create: (t: TxType, b: TxBody) => api.post<TxDetail>(base(t), b).then((r) => r.data),
  update: (t: TxType, id: number, b: TxBody) => api.put<TxDetail>(`${base(t)}/${id}`, b).then((r) => r.data),
  cancel: (t: TxType, id: number, reason: string) => api.post<TxDetail>(`${base(t)}/${id}/cancel`, { reason }).then((r) => r.data),
};
