import dayjs, { type Dayjs } from 'dayjs';

export type PeriodKey = '1D' | '1W' | '1M' | 'CUSTOM';
export interface Range { from: string; to: string }

export const PERIOD_OPTIONS: { label: string; value: PeriodKey }[] = [
  { label: '1 day', value: '1D' }, { label: '1 week', value: '1W' }, { label: '1 month', value: '1M' }, { label: 'Custom', value: 'CUSTOM' },
];

const iso = (d: Dayjs) => d.format('YYYY-MM-DD');
const DAYS: Record<Exclude<PeriodKey, 'CUSTOM'>, number> = { '1D': 1, '1W': 7, '1M': 30 };

/** The from/to (inclusive) the dashboard asks the server for: the last 1 / 7 / 30 days ending today, or the chosen custom range. */
export function rangeOf(period: PeriodKey, custom: [Dayjs, Dayjs] | null, today: Dayjs = dayjs()): Range {
  if (period === 'CUSTOM' && custom) return { from: iso(custom[0]), to: iso(custom[1]) };
  const days = DAYS[period === 'CUSTOM' ? '1M' : period];
  return { from: iso(today.subtract(days - 1, 'day')), to: iso(today) };
}

/** Human label used in card captions and chart titles. */
export function labelOf(period: PeriodKey, range: Range): string {
  if (period === '1D') return 'Today';
  if (period === '1W') return 'Last 7 days';
  if (period === '1M') return 'Last 30 days';
  const a = dayjs(range.from); const b = dayjs(range.to);
  return a.isSame(b, 'day') ? b.format('D MMM YYYY') : `${a.format('D MMM')} – ${b.format('D MMM YYYY')}`;
}
