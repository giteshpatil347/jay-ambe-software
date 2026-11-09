import type { PartyType } from './masterData';

export type PaymentDirection = 'PAID' | 'RECEIVED';
export type PaymentStatus = 'POSTED' | 'CANCELLED';

/** All money values are decimal STRINGS from the API. */
export interface Payment {
  id: number; paymentNumber: string; partyId: number; partyName: string; paymentDate: string;
  direction: PaymentDirection; amount: string; paymentMode: string | null; referenceNumber: string | null;
  remarks: string | null; status: PaymentStatus; createdAt: string; cancelledAt: string | null; cancellationReason: string | null;
}
export interface PaymentPage { content: Payment[]; page: number; size: number; totalElements: number; totalAmount: string }
export interface PaymentFilters {
  from?: string; to?: string; partyId?: number; direction?: PaymentDirection; status?: PaymentStatus; q?: string;
  page: number; size: number;
}
export interface PaymentBody {
  partyId: number; paymentDate: string; direction: PaymentDirection; amount: string;
  paymentMode?: string; referenceNumber?: string; remarks?: string;
}

export type LedgerKind = 'PURCHASE' | 'DISPATCH' | 'PAYMENT_PAID' | 'PAYMENT_RECEIVED';
export interface LedgerEntry { date: string; kind: LedgerKind; reference: string; debit: string; credit: string; balance: string }
export interface PartyLedger {
  party: { id: number; code: string; name: string; type: PartyType };
  openingBalance: string; entries: LedgerEntry[]; closingBalance: string; totals: { debit: string; credit: string };
}
export interface PartyBalance { id: number; code: string; name: string; type: PartyType; status: string; balance: string }
export interface PartyBalancePage { content: PartyBalance[]; page: number; size: number; totalElements: number }

export interface AuditLog {
  id: number; userId: number | null; userFullName: string | null; action: string; module: string; recordId: string | number | null;
  oldValues: unknown; newValues: unknown; ipAddress: string | null; createdAt: string;
}
export interface AuditPage { content: AuditLog[]; page: number; size: number; totalElements: number }
export interface AuditFilters {
  module?: string; recordId?: string; userId?: number; action?: string; from?: string; to?: string; page: number; size: number;
}
