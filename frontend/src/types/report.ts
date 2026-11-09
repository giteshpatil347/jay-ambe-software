/** All money/quantity values are decimal STRINGS from the API. */
export interface QtyByUnit { unit: string; quantity: string }
export interface ReportPage<R, S> { content: R[]; page: number; size: number; totalElements: number; summary: S }

export type ReportStatus = 'POSTED' | 'CANCELLED';
export type GroupBy = 'party' | 'product' | 'day';

export interface MaterialRow {
  id: number; transactionNumber: string; transactionDate: string; partyId: number | null; partyName: string | null;
  challanNumber: string | null; vehicleNumber: string | null; status: ReportStatus; itemCount: number; amount: string; quantities?: QtyByUnit[];
}
export interface MaterialSummary { transactionCount: number; totalAmount: string; quantityByUnit: QtyByUnit[] }
export interface MaterialGroupRow { key: string; label: string; transactionCount: number; amount: string; quantities: QtyByUnit[] }

export interface ExpenseReportRow {
  id: number; expenseNumber: string; expenseDate: string; categoryId: number; categoryName: string; amount: string;
  paidTo: string | null; paymentMode: string | null; status: ReportStatus;
}
export interface ExpenseSummary {
  count: number; totalAmount: string; byCategory: { categoryId: number; categoryName: string; count: number; amount: string }[];
}

export interface StockRow {
  productId: number; code: string; name: string; categoryName: string | null; unit: string; quantity: string;
  minimumStock: string | null; lowStock: boolean;
}
export interface StockSummary { productCount: number; lowStockCount: number }

export interface ReportFilters {
  from?: string; to?: string; partyId?: number; productId?: number; categoryId?: number; status?: ReportStatus;
  groupBy?: GroupBy; asOf?: string; lowStockOnly?: boolean;
}
/** Export kinds that have a selectable column list (GET /reports/export-columns). */
export type ExportColumnsKind = 'material-in' | 'material-out' | 'expenses' | 'stock' | 'ledger' | 'challan';
export type ReportKind = 'material-in' | 'material-out' | 'expenses' | 'stock';

/** Every figure is optional: the server omits what the caller may not see. IN/OUT/balance are TONS; expenses are rupees. */
export interface DashboardTotals { inTons?: string; outTons?: string; expenses?: string }
export interface DashboardDay { date: string; inTons?: string; outTons?: string; balanceTons?: string; expenses?: string }
export interface DashboardStockLine { productId: number; code: string; name: string; unit: string; quantity: string }
export interface DashboardRecent { id: number; type: 'IN' | 'OUT'; transactionNumber: string; transactionDate: string; partyName: string | null; totalAmount: string }
export interface Dashboard {
  today?: string; from?: string; to?: string; periodTotals?: DashboardTotals; receivable?: string; payable?: string;
  stockTons?: string; pieceProducts?: number; stockByProduct?: DashboardStockLine[];
  lowStockCount?: number;
  lowStock?: { productId: number; code: string; name: string; unit: string; quantity: string; minimumStock: string | null }[];
  /** One point per day of the selected period. */
  series?: DashboardDay[];
  recent?: DashboardRecent[];
  recentTransactions?: DashboardRecent[];
}
