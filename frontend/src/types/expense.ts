export type CategoryType = 'ELECTRICITY' | 'SALARY' | 'GENERAL';
export type PaymentMode = 'CASH' | 'UPI' | 'BANK' | 'CHEQUE' | 'OTHER';
export type SalaryPaymentType = 'MONTHLY' | 'ADHOC' | 'ADVANCE' | 'SETTLEMENT';

export interface ExpenseCategory {
  id: number; categoryCode: string; categoryName: string; categoryType: CategoryType; fixed: boolean; status: 'ACTIVE' | 'INACTIVE';
}
export type UsageType = 'HOURLY' | 'DAILY';
export interface SalaryDetail { employeeId: number; paymentType: SalaryPaymentType; grossSalary: string; advanceAdjustment?: string | null }
export interface ElectricityDetail { connectionNumber: '1_NO' | '2_NO'; usageType: UsageType; usageQuantity: string }
export interface Expense {
  id: number; expenseNumber: string; expenseDate: string; categoryId: number; categoryName: string; categoryType: CategoryType;
  amount: string; paidTo?: string | null; paymentMode?: PaymentMode | null; remarks?: string | null;
  status: 'POSTED' | 'CANCELLED'; version: number; cancelledAt?: string | null; cancellationReason?: string | null; createdAt: string;
  /** Hours (HOURLY) or days (DAILY) of an ELECTRICITY expense, also in list rows; null for other categories. */
  usageType?: UsageType | null; usageQuantity?: string | null; connectionNumber?: string | null;
  /** SALARY rows in lists: who was paid and how (Monthly / Adhoc / Advance / Settlement). */
  employeeName?: string | null; paymentType?: SalaryPaymentType | null;
  salary?: SalaryDetail | null; electricity?: ElectricityDetail | null;
}
export interface ExpensePage { content: Expense[]; page: number; size: number; totalElements: number; totalAmount: string }
export interface ExpenseFilters { from?: string; to?: string; categoryId?: number; q?: string; page: number; size: number }
export interface ExpenseBody {
  expenseDate: string; categoryId: number; amount?: string; paidTo?: string; paymentMode?: PaymentMode; remarks?: string;
  salary?: SalaryDetail; electricity?: ElectricityDetail; version?: number;
}
export interface Employee {
  id: number; employeeCode: string; employeeName: string; mobile?: string | null; monthlySalary: string; joiningDate?: string | null;
  status: 'ACTIVE' | 'INACTIVE'; createdAt: string;
}
export interface EmployeeBody {
  employeeCode?: string; employeeName: string; mobile?: string; monthlySalary?: string; joiningDate?: string; status?: string;
  /** With monthlySalary on update: date the new salary applies from (default today) and its remarks. */
  effectiveFrom?: string; salaryRemarks?: string;
}
export interface SalaryRevision { id: number; effectiveFrom: string; year: number; monthlySalary: string; remarks?: string | null; createdByName?: string | null }
export interface SalaryHistoryItem {
  expenseId: number; expenseNumber: string; expenseDate: string; paymentType: SalaryPaymentType;
  grossSalary: string; advanceAdjustment?: string | null; amount: string; status: 'POSTED' | 'CANCELLED';
}
export interface PageEnvelope<T> { content: T[]; page: number; size: number; totalElements: number }
