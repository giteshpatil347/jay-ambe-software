import { api } from '../api/client';
import type { PartyType } from '../types/masterData';
import type { PartyBalancePage, PartyLedger } from '../types/payment';

export const ledgerService = {
  ledger: (id: number, from?: string, to?: string) =>
    api.get<PartyLedger>(`/parties/${id}/ledger`, { params: { from, to } }).then((r) => r.data),
  balances: (p: { type?: PartyType; onlyNonZero?: boolean; page: number; size: number }) =>
    api.get<PartyBalancePage>('/parties/balances', { params: p }).then((r) => r.data),
};
