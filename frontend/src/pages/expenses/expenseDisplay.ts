import type { Expense } from '../../types/expense';
import { formatCurrency } from '../../utils/currency';

/**
 * What an expense list row shows depends on its category type (each type captures different data):
 *  - ELECTRICITY: details = connection + hours/days; amount = the price if one was entered, else a dash.
 *  - SALARY:      details = employee + payment type (Monthly / Adhoc / Advance / Settlement); amount = the amount.
 *  - GENERAL:     details = the remarks (if any); amount = the amount.
 * Electricity rows from before usage was recorded fall back to their rupee amount.
 */
const PAY_TYPE: Record<string, string> = { MONTHLY: 'Monthly', ADHOC: 'Adhoc', ADVANCE: 'Advance', SETTLEMENT: 'Settlement' };

export const hasUsage = (r: Expense) => r.categoryType === 'ELECTRICITY' && !!r.usageQuantity;

/** "8 hrs" / "1 day" - the electricity usage. */
export function usageOf(r: Expense): string {
  if (!hasUsage(r)) return '';
  const n = Number(r.usageQuantity);
  const [one, many] = r.usageType === 'DAILY' ? ['day', 'days'] : ['hr', 'hrs'];
  return `${n} ${n === 1 ? one : many}`;
}

export function detailsOf(r: Expense): string {
  if (r.categoryType === 'ELECTRICITY') {
    const conn = r.connectionNumber ? `Connection ${r.connectionNumber.charAt(0)}` : '';
    return [conn, usageOf(r)].filter(Boolean).join(' · ');
  }
  if (r.categoryType === 'SALARY') return [r.employeeName, r.paymentType ? PAY_TYPE[r.paymentType] ?? r.paymentType : ''].filter(Boolean).join(' · ');
  return r.remarks?.trim() ?? '';
}

/** The money column: for electricity only a price that was actually entered; a dash otherwise. */
export function amountOf(r: Expense): string {
  if (!hasUsage(r)) return formatCurrency(r.amount);
  return Number(r.amount) > 0 ? formatCurrency(r.amount) : '—';
}

/** First line of a mobile card: the amount, or the hours when an electricity row has no price. */
export const headlineOf = (r: Expense): string => (hasUsage(r) && !(Number(r.amount) > 0) ? usageOf(r) : amountOf(r));
