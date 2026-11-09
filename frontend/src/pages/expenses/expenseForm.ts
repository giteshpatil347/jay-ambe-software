import dayjs, { type Dayjs } from 'dayjs';
import type { CategoryType, Expense, ExpenseBody, PaymentMode } from '../../types/expense';

export interface ExpenseFormValues {
  expenseDate: Dayjs; categoryId: number; amount: string; paidTo?: string; paymentMode?: PaymentMode; remarks?: string;
  salary?: { employeeId?: number; paymentType?: string; grossSalary?: string; advanceAdjustment?: string };
  electricity?: { connectionNumber?: string; usageType?: string; usageQuantity?: string };
}

/** Starting values of a NEW expense: today, paid in CASH (the user can change it). */
export const newExpenseDefaults = (): Partial<ExpenseFormValues> => ({ expenseDate: dayjs(), paymentMode: 'CASH' });

const blank = (v?: string) => (v && v.trim() !== '' ? v.trim() : undefined);

/**
 * Builds the API body. Money/qty stay strings; only the block matching the category type is sent, and only the fields
 * that category's form offers (salary: no payment mode; electricity: no paid-to / payment mode).
 */
export function buildExpenseBody(v: ExpenseFormValues, type: CategoryType | undefined, version?: number): ExpenseBody {
  const body: ExpenseBody = {
    expenseDate: v.expenseDate.format('YYYY-MM-DD'), categoryId: v.categoryId, amount: blank(v.amount), remarks: blank(v.remarks),
  };
  if (type === 'GENERAL') Object.assign(body, { paidTo: blank(v.paidTo), paymentMode: v.paymentMode });
  if (type === 'SALARY' && v.salary) {
    const s = v.salary;
    body.paidTo = blank(v.paidTo);
    body.salary = {
      employeeId: s.employeeId as number, paymentType: s.paymentType as never,
      grossSalary: (s.grossSalary ?? '').trim(), advanceAdjustment: blank(s.advanceAdjustment),
    };
  }
  if (type === 'ELECTRICITY' && v.electricity) {
    const e = v.electricity;
    body.electricity = { connectionNumber: e.connectionNumber as never, usageType: e.usageType as never, usageQuantity: (e.usageQuantity ?? '').trim() };
  }
  if (version !== undefined) body.version = version;
  return body;
}

export function expenseToFormValues(x: Expense): Partial<ExpenseFormValues> {
  return {
    expenseDate: dayjs(x.expenseDate), categoryId: x.categoryId, amount: x.amount, paidTo: x.paidTo ?? undefined,
    paymentMode: x.paymentMode ?? undefined, remarks: x.remarks ?? undefined,
    salary: x.salary ? {
      employeeId: x.salary.employeeId, paymentType: x.salary.paymentType,
      grossSalary: x.salary.grossSalary, advanceAdjustment: x.salary.advanceAdjustment ?? undefined,
    } : undefined,
    electricity: x.electricity ? {
      connectionNumber: x.electricity.connectionNumber, usageType: x.electricity.usageType, usageQuantity: x.electricity.usageQuantity ?? undefined,
    } : undefined,
  };
}
