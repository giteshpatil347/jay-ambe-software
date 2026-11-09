import { api } from '../api/client';
import type { LedgerPage, OpeningStockBody, StockPage } from '../types/material';

const clean = <T extends object>(p: T) =>
  Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== ''));

export interface StockQuery { q?: string; categoryId?: number; lowStockOnly?: boolean; page: number; size: number }
export interface LedgerQuery { productId: number; from?: string; to?: string; newestFirst?: boolean; page: number; size: number }

/** `page` is 1-based here, converted to the API's 0-based index. */
export const inventoryService = {
  stock: (f: StockQuery) => api.get<StockPage>('/inventory/stock', { params: clean({ ...f, page: f.page - 1 }) }).then((r) => r.data),
  ledger: (f: LedgerQuery) => api.get<LedgerPage>('/inventory/ledger', { params: clean({ ...f, page: f.page - 1 }) }).then((r) => r.data),
  openingStock: (b: OpeningStockBody) => api.post<void>('/inventory/opening-stock', b).then((r) => r.data),
};
