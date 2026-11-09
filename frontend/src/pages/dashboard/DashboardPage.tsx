import { Suspense, lazy, useState } from 'react';
import dayjs, { type Dayjs } from 'dayjs';
import { Alert, Button, Card, Col, ConfigProvider, DatePicker, Empty, Grid, List, Row, Segmented, Skeleton, Typography } from 'antd';
import { BankOutlined, DollarOutlined, DownloadOutlined, InboxOutlined, PlusOutlined, UploadOutlined, WalletOutlined, WarningOutlined } from '@ant-design/icons';
import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { usePermission } from '../../auth/usePermission';
import { dashboardService } from '../../services/dashboardService';
import type { Dashboard } from '../../types/report';
import { formatCurrency } from '../../utils/currency';
import { formatDate } from '../../utils/date';
import { problemOf } from '../../utils/formErrors';
import { formatQuantity } from '../../utils/quantity';
import { hasExpenses, presentSeries } from './seriesUtil';
import { PERIOD_OPTIONS, labelOf, rangeOf, type PeriodKey } from './period';

const DashboardChart = lazy(() => import('./DashboardChart'));

/** Icon chip per KPI; colours match the charts (IN blue, OUT orange, stock green). */
const CHIP: Record<string, { icon: ReactNode; fg: string; bg: string }> = {
  stock: { icon: <InboxOutlined />, fg: '#119a68', bg: '#e5f6ef' },
  in: { icon: <DownloadOutlined />, fg: '#2a78d6', bg: '#e8f1fc' },
  out: { icon: <UploadOutlined />, fg: '#e0561f', bg: '#fdeee7' },
  low: { icon: <WarningOutlined />, fg: '#d9363e', bg: '#fdecec' },
  exp: { icon: <DollarOutlined />, fg: '#6b4fd8', bg: '#efeafd' },
  rec: { icon: <BankOutlined />, fg: '#0d8f9c', bg: '#e2f5f7' },
  pay: { icon: <WalletOutlined />, fg: '#b97a00', bg: '#fff4dc' },
};

/** Compact tile: icon chip + label / value / caption. Same height for every tile (the caption slot is reserved) so the grid stays aligned. */
function Stat({ id, label, value, sub, testId }: { id: string; label: string; value: string | number; sub?: string; testId?: string }) {
  const chip = CHIP[id];
  return (
    <div className="mb-stat" data-testid={testId}>
      {chip && <span className="mb-stat__icon" style={{ color: chip.fg, background: chip.bg }} aria-hidden>{chip.icon}</span>}
      <div style={{ minWidth: 0 }}>
        <div className="mb-stat__label">{label}</div>
        <div className="mb-stat__value">{value}</div>
        <div className="mb-stat__sub">{sub ?? '\u00A0'}</div>
      </div>
    </div>
  );
}

// Light, flat, compact dashboard styling (scoped to .mb-dash). KPI grids: 2 columns on phones, 3 on tablets, 4 on desktop -
// the SAME tile width in every section (fewer tiles leave the last slots empty rather than stretching).
const GRID_CSS = `
.mb-dash{display:flex;flex-direction:column;gap:10px;min-width:0}
.mb-dash .ant-card{border-radius:10px;border:1px solid #e9edf2;box-shadow:none;background:#fff}
.mb-dash .ant-card-head{min-height:34px;padding:0 12px;border-bottom:1px solid #f0f2f5}
.mb-dash .ant-card-head-title{padding:8px 0;font-size:13px;font-weight:600;color:#1f2937}
.mb-dash .ant-card-extra{padding:8px 0;font-size:12px}
.mb-dash .ant-card-body{padding:8px 10px}
.mb-dash .ant-list-item{padding:6px 0}
.mb-dash .ant-empty{margin:8px 0}
.mb-section{display:flex;flex-direction:column;gap:6px}
.mb-section__title{font-size:11px;font-weight:600;letter-spacing:.5px;text-transform:uppercase;color:#8a94a6}
.mb-kpis{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));grid-auto-rows:1fr;gap:8px}
@media (min-width:768px){.mb-kpis{grid-template-columns:repeat(3,minmax(0,1fr))}}
@media (min-width:1200px){.mb-kpis{grid-template-columns:repeat(4,minmax(0,1fr))}}
.mb-stat{display:flex;align-items:center;gap:10px;padding:9px 12px;min-height:66px;background:#fff;border:1px solid #e9edf2;border-radius:10px}
.mb-stat__icon{flex:0 0 34px;width:34px;height:34px;border-radius:9px;display:flex;align-items:center;justify-content:center;font-size:16px}
.mb-stat__label{font-size:12px;line-height:1.25;color:#6b7280}
.mb-stat__value{font-size:19px;font-weight:700;line-height:1.25;color:#111827;overflow-wrap:break-word}
@media (max-width:767px){.mb-stat__icon{display:none}.mb-stat__value{font-size:17px}}
.mb-stat__sub{font-size:11px;line-height:1.25;min-height:14px;color:#9aa3b2}
.mb-quick{display:flex;gap:6px;flex:1 1 100%}
.mb-quick .ant-btn{flex:1 1 0;min-width:0;padding-inline:6px;font-size:13px}
@media (min-width:768px){.mb-quick{flex:0 0 auto}.mb-quick .ant-btn{flex:0 0 auto;padding-inline:12px}}
`;

type Card = { key: string; label: string; value: string | number; sub?: string; group: 'ops' | 'fin' };

const tons = (v: string) => `${formatQuantity(v)} TON`;

/** Cards are rendered only for fields the server returned; nothing is defaulted to a zero placeholder. Material is TONS, money only for financial users. */
export function statCards(d: Dashboard, periodLabel = 'Selected period'): Card[] {
  const cards: Card[] = [];
  const t = d.periodTotals;
  /** Material IN/OUT and expenses are totals of the SELECTED period; stock, receivable, payable and low stock are current. */
  const pair = (key: string, label: string, f: keyof NonNullable<Dashboard['periodTotals']>, fmt: (v: string) => string, group: Card['group']) => {
    const v = t?.[f];
    if (v === undefined) return;
    cards.push({ key, label, value: fmt(v), sub: periodLabel, group });
  };
  if (d.stockTons !== undefined) {
    cards.push({
      key: 'stock', label: 'Stock on hand', value: tons(d.stockTons),
      sub: d.pieceProducts ? `+ ${d.pieceProducts} in pieces` : undefined, group: 'ops',
    });
  }
  pair('in', 'Material IN', 'inTons', tons, 'ops');
  pair('out', 'Material OUT', 'outTons', tons, 'ops');
  pair('exp', 'Expenses', 'expenses', formatCurrency, 'fin');
  if (d.receivable !== undefined) cards.push({ key: 'rec', label: 'Receivable', value: formatCurrency(d.receivable), group: 'fin' });
  if (d.payable !== undefined) cards.push({ key: 'pay', label: 'Payable', value: formatCurrency(d.payable), group: 'fin' });
  if (d.lowStockCount !== undefined) cards.push({ key: 'low', label: 'Low-stock materials', value: d.lowStockCount, group: 'ops' });
  return cards;
}

/** Items counted in pieces cannot be drawn on a tons axis, so they are listed here in pieces. */
function PieceList({ lines }: { lines: NonNullable<Dashboard['stockByProduct']> }) {
  if (lines.length === 0) return null;
  return (
    <Typography.Paragraph type="secondary" style={{ margin: '8px 4px 0', fontSize: 12 }}>
      Counted in pieces: {lines.map((l) => `${l.name} ${formatQuantity(l.quantity)} ${l.unit}`).join(' · ')}
    </Typography.Paragraph>
  );
}

/** A titled KPI group; the title gives the page its hierarchy (operational first, money second). */
function KpiSection({ title, cards, testId }: { title: string; cards: Card[]; testId: string }) {
  if (cards.length === 0) return null;
  return (
    <section data-testid={testId} aria-label={title} className="mb-section">
      <div className="mb-section__title">{title}</div>
      <div className="mb-kpis">
        {cards.map((c) => <Stat key={c.key} id={c.key} testId={`stat-${c.key}`} label={c.label} value={c.value} sub={c.sub} />)}
      </div>
    </section>
  );
}

/** Each button opens the create form of its page (`?new=1`), shown only when the user may create that record. */
function QuickActions() {
  const { can } = usePermission();
  const navigate = useNavigate();
  const actions = [
    { label: 'Material IN', to: '/material-in', perm: 'MATERIAL_IN.CREATE' },
    { label: 'Material OUT', to: '/material-out', perm: 'MATERIAL_OUT.CREATE' },
    { label: 'Expense', to: '/expenses', perm: 'EXPENSE.CREATE' },
  ].filter((a) => can(a.perm));
  if (actions.length === 0) return null;
  return (
    <div className="mb-quick" role="group" aria-label="Quick actions">
      {actions.map((a) => (
        <Button key={a.to} type="primary" icon={<PlusOutlined />} onClick={() => navigate(`${a.to}?new=1`)}>{a.label}</Button>
      ))}
    </div>
  );
}

export default function DashboardPage() {
  const screens = Grid.useBreakpoint();
  const [period, setPeriod] = useState<PeriodKey>('1M');
  const [custom, setCustom] = useState<[Dayjs, Dayjs] | null>(null);
  const range = rangeOf(period, custom);
  const periodLabel = labelOf(period, range);
  const q = useQuery({
    queryKey: ['dashboard', range.from, range.to], queryFn: () => dashboardService.get(range),
    retry: false, refetchOnWindowFocus: true, placeholderData: (p) => p,
  });
  const d = q.data;
  const cards = d ? statCards(d, periodLabel) : [];
  const pick = (p: PeriodKey) => {
    if (p === 'CUSTOM' && !custom) setCustom([dayjs().subtract(6, 'day'), dayjs()]);
    setPeriod(p);
  };
  const recent = d?.recentTransactions ?? d?.recent;
  const showLow = d?.lowStock !== undefined || d?.lowStockCount !== undefined;
  const weightLines = (d?.stockByProduct ?? []).filter((l) => l.unit !== 'PIECE');
  const pieceLines = (d?.stockByProduct ?? []).filter((l) => l.unit === 'PIECE');
  const showStock = d?.stockByProduct !== undefined;
  const hasSeries = !!d?.series?.length && presentSeries(d.series).length > 0;
  const showExpenses = !!d?.series?.length && hasExpenses(d.series);
  return (
    <ConfigProvider theme={{ token: { controlHeight: screens.md ? 30 : 40, fontSize: 13 } }}>
    <div className="mb-dash">
      <style>{GRID_CSS}</style>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', justifyContent: 'space-between', gap: 6 }}>
        <Typography.Title level={4} style={{ margin: 0, fontSize: 18, lineHeight: 1.3 }}>Dashboard</Typography.Title>
        <QuickActions />
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 6 }} data-testid="period-bar">
        <Segmented<PeriodKey> options={PERIOD_OPTIONS} value={period} onChange={pick} block={!screens.md} style={screens.md ? undefined : { flex: '1 1 100%' }} aria-label="Period" />
        {period === 'CUSTOM' && (
          <DatePicker.RangePicker
            allowClear={false} value={custom} style={screens.md ? undefined : { flex: '1 1 100%' }} aria-label="Custom period"
            disabledDate={(c) => c.isAfter(dayjs(), 'day')}
            onChange={(v) => { if (v?.[0] && v?.[1]) setCustom([v[0], v[1]]); }}
          />
        )}
      </div>
      {q.isError && <Alert type="error" showIcon message={problemOf(q.error)?.message ?? 'Could not load the dashboard'} />}
      {q.isLoading && <Skeleton active />}
      {d && (
        <>
          <KpiSection title="Operational KPIs" cards={cards.filter((c) => c.group === 'ops')} testId="kpi-ops" />
          <KpiSection title="Financial KPIs" cards={cards.filter((c) => c.group === 'fin')} testId="kpi-fin" />
          {(hasSeries || showStock) && (
            <Row gutter={[8, 8]} data-testid="chart-row">
              {hasSeries && d.series && <Col xs={24} lg={showStock ? 12 : 24}>
                <Card size="small" title={periodLabel} styles={{ body: { padding: 8 } }} style={{ height: '100%' }}>
                  <Suspense fallback={<Skeleton active />}><DashboardChart days={d.series} kind="tons" /></Suspense>
                </Card>
              </Col>}
              {showStock && d.stockByProduct && <Col xs={24} lg={hasSeries ? 12 : 24}>
                <Card size="small" title="Stock by material" extra={<Link to="/inventory">Inventory</Link>} styles={{ body: { padding: 8 } }} style={{ height: '100%' }}>
                  {weightLines.length > 0
                    ? <Suspense fallback={<Skeleton active />}><DashboardChart lines={weightLines} kind="stock" /></Suspense>
                    : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No stock on hand" />}
                  <PieceList lines={pieceLines} />
                </Card>
              </Col>}
            </Row>
          )}
          <Row gutter={[8, 8]}>
            {showLow && <Col xs={24} lg={showExpenses ? 12 : 24}>
              <Card size="small" title="Low stock" extra={<Link to="/inventory">Inventory</Link>} style={{ height: '100%' }}>
                <List dataSource={d.lowStock ?? []} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Nothing low on stock" /> }}
                  renderItem={(p) => (
                    <List.Item style={{ paddingInline: 0 }}>
                      <Link to="/inventory" style={{ minHeight: 32 }}>{p.name} <Typography.Text type="secondary">({p.code})</Typography.Text></Link>
                      <Typography.Text type="danger">{formatQuantity(p.quantity)} {p.unit}{p.minimumStock ? ` / min ${formatQuantity(p.minimumStock)}` : ''}</Typography.Text>
                    </List.Item>
                  )} />
              </Card>
            </Col>}
            {showExpenses && d.series && <Col xs={24} lg={showLow ? 12 : 24}>
              <Card size="small" title={`Expenses, ${periodLabel.toLowerCase()} (₹)`} styles={{ body: { padding: 8 } }} style={{ height: '100%' }}>
                <Suspense fallback={<Skeleton active />}><DashboardChart days={d.series} kind="expenses" /></Suspense>
              </Card>
            </Col>}
            {recent !== undefined && <Col xs={24}>
              <Card size="small" title="Recent transactions">
                <List dataSource={recent} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No recent transactions" /> }}
                  renderItem={(r) => (
                    <List.Item style={{ paddingInline: 0 }}>
                      <div style={{ minWidth: 0 }}>
                        <Link to={r.type === 'IN' ? '/material-in' : '/material-out'}>{r.partyName ?? (r.type === 'IN' ? 'Material IN' : 'Material OUT')}</Link>
                        <div><Typography.Text type="secondary">{r.type === 'IN' ? 'Material IN' : 'Material OUT'} · {formatDate(r.transactionDate)}</Typography.Text></div>
                      </div>
                      <strong>{formatCurrency(r.totalAmount)}</strong>
                    </List.Item>
                  )} />
              </Card>
            </Col>}
          </Row>
        </>
      )}
    </div>
    </ConfigProvider>
  );
}
