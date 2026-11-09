import type { DashboardDay } from '../../types/report';

/** Quantity series (tons) - one shared axis. Colours are palette slots 1-3. */
export const SERIES = [
  { key: 'inTons', name: 'Material IN', color: 'var(--s1)' },
  { key: 'outTons', name: 'Material OUT', color: 'var(--s2)' },
  { key: 'balanceTons', name: 'Stock balance', color: 'var(--s3)', dashed: true },
] as const;

/** Series that exist in the payload (a series the caller may not see is omitted entirely). */
export const presentSeries = (days: DashboardDay[]) => SERIES.filter((s) => days.some((d) => d[s.key] !== undefined));

/** Expenses are rupees: they never share an axis with tons, so they get their own chart. */
export const hasExpenses = (days: DashboardDay[]) => days.some((d) => d.expenses !== undefined);

/** Tons of a stock line for bar lengths (display only): KG /1000, TON as is. Pieces have no tons value. */
export const lineTons = (unit: string, quantity: string): number | null =>
  unit === 'TON' ? Number(quantity) : unit === 'KG' ? Number(quantity) / 1000 : null;
