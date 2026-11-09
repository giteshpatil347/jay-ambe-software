import { Bar, BarChart, CartesianGrid, LabelList, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import type { DashboardDay, DashboardStockLine } from '../../types/report';
import { formatCurrency } from '../../utils/currency';
import { formatQuantity } from '../../utils/quantity';
import dayjs from 'dayjs';
import { SERIES, lineTons, presentSeries } from './seriesUtil';

type Day = DashboardDay;

// Categorical slots 1-3 of the validated dataviz palette (blue, orange, aqua). Light theme only.
const CSS = `
.mb-chart{--s1:#2a78d6;--s2:#eb6834;--s3:#1baf7a;--grid:#eceff3;--ink:#6b7280;--surf:#ffffff;width:100%;min-width:0}
`;
const compact = new Intl.NumberFormat('en-IN', { notation: 'compact', maximumFractionDigits: 1 });
const axis = { fill: 'var(--ink)', fontSize: 11 } as const;
const tip = { background: 'var(--surf)', border: '1px solid var(--grid)', color: 'var(--ink)' } as const;

/**
 * kind 'tons'    : daily Material IN / OUT and the closing Stock balance, all in tons (one axis).
 * kind 'expenses': daily expenses in rupees (own chart - rupees and tons never share an axis).
 * kind 'stock'   : current stock per product as horizontal bars in tons (KG /1000); the label keeps the product's own unit.
 *                  Items counted in pieces have no tons value and are not drawn (the page lists them).
 * Display-only: numeric conversion for plotting; tooltips format the original strings.
 */
export default function DashboardChart({ days = [], lines = [], kind = 'tons' }: { days?: Day[]; lines?: DashboardStockLine[]; kind?: 'tons' | 'expenses' | 'stock' }) {
  if (kind === 'stock') return <StockChart lines={lines} />;
  const data = days.map((d) => ({
    ...d, label: dayjs(d.date).format('D MMM'),
    inN: Number(d.inTons ?? 0), outN: Number(d.outTons ?? 0), balN: Number(d.balanceTons ?? 0), exN: Number(d.expenses ?? 0),
  }));
  const tons = kind === 'tons';
  const series = presentSeries(days);
  const num = { inTons: 'inN', outTons: 'outN', balanceTons: 'balN' } as const;
  return (
    <div className="mb-chart" role="img"
      aria-label={tons ? `Daily ${series.map((s) => s.name.toLowerCase()).join(', ')} in tons for the last 30 days` : 'Daily expenses in rupees for the last 30 days'}>
      <style>{CSS}</style>
      <ResponsiveContainer width="100%" height={tons ? 230 : 180}>
        <LineChart data={data} margin={{ top: 6, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="var(--grid)" vertical={false} />
          <XAxis dataKey="label" tick={axis} interval="preserveStartEnd" minTickGap={24} stroke="var(--grid)" />
          <YAxis tick={axis} tickFormatter={(v: number) => compact.format(v)} width={38} stroke="var(--grid)" />
          <Tooltip
            formatter={(_v, name, item) => {
              const p = item.payload as Day;
              if (!tons) return [formatCurrency(p.expenses ?? '0'), name];
              const s = SERIES.find((x) => x.name === name);
              return [`${formatQuantity(p[s?.key ?? 'inTons'] ?? '0')} TON`, name];
            }}
            labelFormatter={(l) => String(l)} contentStyle={tip} />
          <Legend verticalAlign="bottom" align="center" wrapperStyle={{ paddingTop: 2, fontSize: 11 }} />
          {tons ? series.map((s) => (
            <Line key={s.key} type="monotone" dataKey={num[s.key]} name={s.name} stroke={s.color} strokeWidth={2}
              strokeDasharray={'dashed' in s && s.dashed ? '5 4' : undefined} dot={days.length <= 8} isAnimationActive={false}
              activeDot={{ r: 4, stroke: 'var(--surf)', strokeWidth: 2 }} />
          )) : (
            <Line type="monotone" dataKey="exN" name="Expenses" stroke="var(--s3)" strokeWidth={2} dot={days.length <= 8} isAnimationActive={false}
              activeDot={{ r: 4, stroke: 'var(--surf)', strokeWidth: 2 }} />
          )}
        </LineChart>
      </ResponsiveContainer>
    </div>
  );
}

/** Horizontal bars, biggest first; the value label shows the stored quantity in the product's own unit. */
function StockChart({ lines }: { lines: DashboardStockLine[] }) {
  const rows = lines.flatMap((l) => {
    const t = lineTons(l.unit, l.quantity);
    return t === null ? [] : [{ name: l.name, tons: t, label: `${formatQuantity(l.quantity)} ${l.unit}` }];
  });
  return (
    <div className="mb-chart" role="img" aria-label="Current stock per material in tons">
      <style>{CSS}</style>
      <ResponsiveContainer width="100%" height={230}>
        <BarChart data={rows} layout="vertical" margin={{ top: 4, right: 64, bottom: 0, left: 0 }}>
          <CartesianGrid stroke="var(--grid)" horizontal={false} />
          <XAxis type="number" tick={axis} tickFormatter={(v: number) => compact.format(v)} stroke="var(--grid)" />
          <YAxis type="category" dataKey="name" tick={axis} width={96} stroke="var(--grid)" interval={0} />
          <Tooltip formatter={(_v, _n, item) => [(item.payload as { label: string }).label, 'Stock']} contentStyle={tip} cursor={{ fill: 'var(--grid)', opacity: 0.4 }} />
          <Bar dataKey="tons" fill="var(--s3)" radius={[0, 4, 4, 0]} maxBarSize={18} isAnimationActive={false}>
            <LabelList dataKey="label" position="right" style={{ fill: 'var(--ink)', fontSize: 11 }} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
