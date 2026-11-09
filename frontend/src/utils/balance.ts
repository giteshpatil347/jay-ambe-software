import { formatCurrency } from './currency';

const isNegative = (v: string) => v.trim().startsWith('-');
const absOf = (v: string) => v.trim().replace(/^-/, '');
export const isZeroMoney = (v?: string | null) => !v || /^-?0*\.?0*$/.test(v.trim());

export type BalanceSide = 'RECEIVABLE' | 'PAYABLE' | 'ZERO';
/** Positive balance = party owes us (receivable); negative = we owe them (payable). */
export const balanceSide = (v?: string | null): BalanceSide =>
  isZeroMoney(v) ? 'ZERO' : isNegative(v as string) ? 'PAYABLE' : 'RECEIVABLE';

/** "Receivable ₹x" / "Payable ₹x" — never a bare minus sign. */
export function formatBalance(v?: string | null): string {
  const side = balanceSide(v);
  if (side === 'ZERO') return formatCurrency('0');
  const amount = formatCurrency(absOf(v as string));
  return `${side === 'RECEIVABLE' ? 'Receivable' : 'Payable'} ${amount}`;
}
export const balanceColor = (v?: string | null) => {
  const s = balanceSide(v);
  return s === 'RECEIVABLE' ? '#389e0d' : s === 'PAYABLE' ? '#cf1322' : undefined;
};
