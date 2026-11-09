import type { ProductUnit } from './masterData';

export type TxType = 'IN' | 'OUT';
export type TxStatus = 'POSTED' | 'CANCELLED';

export interface TxListRow {
  id: number; transactionNumber: string; transactionDate: string; partyId: number | null; partyName: string | null;
  itemCount: number; productNames: string[]; quantities?: { unit: string; quantity: string }[]; totalAmount: string; status: TxStatus; createdByName: string | null;
}
export interface TxPage { content: TxListRow[]; page: number; size: number; totalElements: number; totalAmount: string }
export interface TxFilters {
  from?: string; to?: string; partyId?: number; productId?: number; status?: string; q?: string; page: number; size: number;
}
export interface TxItem {
  id: number; productId: number; productCode: string; productName: string; unit: ProductUnit;
  quantity: string; rate: string; amount: string; remarks?: string | null;
}
export interface TxDetail {
  id: number; transactionNumber: string; transactionType: TxType; transactionDate: string;
  partyId: number | null; partyName: string | null; challanNumber?: string | null; vehicleNumber?: string | null;
  driverName?: string | null; remarks?: string | null; totalAmount: string; status: TxStatus; version: number;
  createdByName?: string | null; createdAt: string; updatedAt?: string | null;
  cancelledByName?: string | null; cancelledAt?: string | null; cancellationReason?: string | null; items: TxItem[];
}
export interface TxBody {
  transactionDate: string; partyId?: number; challanNumber?: string; vehicleNumber?: string; driverName?: string;
  remarks?: string; items: { productId: number; quantity: string; unit?: ProductUnit; rate?: string }[]; version?: number;
}

export interface StockRow {
  productId: number; code: string; name: string; categoryName: string | null; unit: ProductUnit;
  quantity: string; minimumStock: string | null; lowStock: boolean;
}
export interface StockPage { content: StockRow[]; page: number; size: number; totalElements: number }
export interface LedgerRow {
  date: string; movementType: string; delta: string; balance: string;
  transactionId: number | null; transactionNumber: string | null; createdBy: string | null;
}
export interface LedgerPage { openingBalance: string; content: LedgerRow[]; page: number; size: number; totalElements: number }
export interface OpeningStockBody { date?: string; items: { productId: number; quantity: string }[] }
