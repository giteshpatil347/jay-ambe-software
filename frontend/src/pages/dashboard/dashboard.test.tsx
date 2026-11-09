import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import dayjs from 'dayjs';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { dashboardService } from '../../services/dashboardService';
import DashboardPage, { statCards } from './DashboardPage';
import { presentSeries } from './seriesUtil';
import type { Dashboard } from '../../types/report';

vi.mock('../../services/dashboardService');
let perms: string[] = ['MATERIAL_IN.CREATE', 'MATERIAL_OUT.CREATE', 'EXPENSE.CREATE'];
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: (p: string) => perms.includes(p) }) }));
const navigateMock = vi.fn();
vi.mock('react-router-dom', async (orig) => ({ ...(await orig<typeof import('react-router-dom')>()), useNavigate: () => navigateMock }));
vi.mock('./DashboardChart', () => ({
  default: ({ days = [], kind = 'tons' }: { days?: Parameters<typeof presentSeries>[0]; kind?: 'tons' | 'expenses' | 'stock' }) => (
    <div data-testid={`chart-${kind}`}>{kind === 'tons' ? presentSeries(days).map((s) => s.name).join('|') : kind}</div>
  ),
}));

const totals = { inTons: '1.500', outTons: '0.500', expenses: '10.00' };
const day = { date: '2026-11-01', inTons: '1', outTons: '2', balanceTons: '3', expenses: '3' };
const byProduct = [{ productId: 1, code: 'C', name: 'Cement', unit: 'TON', quantity: '12.500' }, { productId: 2, code: 'B', name: 'Bolts', unit: 'PIECE', quantity: '40.000' }];
const recent = [{ id: 1, type: 'IN' as const, transactionNumber: 'MIN-1', transactionDate: '2026-11-01', partyName: 'A', totalAmount: '5' }];
const low = [{ productId: 1, code: 'C', name: 'Cement', unit: 'TON', quantity: '1', minimumStock: '5' }];
const FULL: Dashboard = {
  today: '2026-11-08', periodTotals: totals, receivable: '9.00', payable: '8.00', stockTons: '12.500', pieceProducts: 1,
  lowStockCount: 1, lowStock: low, stockByProduct: byProduct, series: [day], recent,
};
const VIEWER: Dashboard = {
  today: '2026-11-08', periodTotals: { inTons: '1.500', outTons: '0.500' }, stockTons: '12.500',
  lowStockCount: 1, lowStock: low, stockByProduct: byProduct, series: [{ date: '2026-11-01', inTons: '1', outTons: '2', balanceTons: '3' }],
};

function renderPage(data: Dashboard) {
  vi.mocked(dashboardService.get).mockResolvedValue(data);
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><MemoryRouter><DashboardPage /></MemoryRouter></QueryClientProvider>);
}

describe('statCards', () => {
  it('only builds cards for present figures', () => {
    expect(statCards(FULL).map((c) => c.key)).toEqual(['stock', 'in', 'out', 'exp', 'rec', 'pay', 'low']);
    expect(statCards(VIEWER).map((c) => c.key)).toEqual(['stock', 'in', 'out', 'low']);
    // quantities are tons; only expenses/receivable/payable are rupees
    expect(statCards(FULL).find((c) => c.key === 'in')?.value).toBe('1.5 TON');
    // IN / OUT / expenses are period totals and say which period; stock, receivable, payable and low stock are current figures
    expect(statCards(FULL, 'Last 7 days').filter((c) => c.sub === 'Last 7 days').map((c) => c.key)).toEqual(['in', 'out', 'exp']);
    expect(statCards(FULL).find((c) => c.key === 'stock')?.sub).toBe('+ 1 in pieces');
    expect(statCards({})).toEqual([]);
  });
});

describe('DashboardPage', () => {
  beforeEach(() => { perms = ['MATERIAL_IN.CREATE', 'MATERIAL_OUT.CREATE', 'EXPENSE.CREATE']; navigateMock.mockClear(); });
  it('period selector: defaults to 1 month and asks the server for 1 day / 1 week ranges', async () => {
    renderPage(FULL);
    await screen.findByTestId('kpi-ops');
    const iso = (d: dayjs.Dayjs) => d.format('YYYY-MM-DD');
    const today = dayjs();
    expect(dashboardService.get).toHaveBeenLastCalledWith({ from: iso(today.subtract(29, 'day')), to: iso(today) });
    fireEvent.click(screen.getByText('1 week'));
    await waitFor(() => expect(dashboardService.get).toHaveBeenLastCalledWith({ from: iso(today.subtract(6, 'day')), to: iso(today) }));
    fireEvent.click(screen.getByText('1 day'));
    await waitFor(() => expect(dashboardService.get).toHaveBeenLastCalledWith({ from: iso(today), to: iso(today) }));
    expect(screen.getAllByText('Today').length).toBeGreaterThan(0); // card captions follow the period
  });
  it('custom period shows a date-range picker (and only then)', async () => {
    renderPage(FULL);
    await screen.findByTestId('kpi-ops');
    expect(document.querySelector('.ant-picker-range')).toBeNull();
    fireEvent.click(screen.getByText('Custom'));
    await waitFor(() => expect(document.querySelector('.ant-picker-range')).not.toBeNull());
    const today = dayjs();
    await waitFor(() => expect(dashboardService.get).toHaveBeenLastCalledWith({ from: today.subtract(6, 'day').format('YYYY-MM-DD'), to: today.format('YYYY-MM-DD') }));
  });
  it('groups cards into Operational and Financial KPIs', async () => {
    renderPage(FULL);
    const ops = await screen.findByTestId('kpi-ops'); const fin = screen.getByTestId('kpi-fin');
    expect(within(ops).getByText('Operational KPIs')).toBeInTheDocument();
    expect(['stat-stock', 'stat-in', 'stat-out', 'stat-low'].every((id) => within(ops).queryByTestId(id))).toBe(true);
    expect(within(fin).getByText('Financial KPIs')).toBeInTheDocument();
    expect(['stat-exp', 'stat-rec', 'stat-pay'].every((id) => within(fin).queryByTestId(id))).toBe(true);
  });
  it('viewer payload has no Financial KPIs section', async () => {
    renderPage(VIEWER);
    expect(await screen.findByTestId('kpi-ops')).toBeInTheDocument();
    expect(screen.queryByTestId('kpi-fin')).not.toBeInTheDocument();
    expect(screen.queryByText('Financial KPIs')).not.toBeInTheDocument();
  });
  it('quick actions open the create form of each page', async () => {
    renderPage(FULL);
    fireEvent.click(await screen.findByRole('button', { name: /material in/i }));
    fireEvent.click(screen.getByRole('button', { name: /material out/i }));
    fireEvent.click(screen.getByRole('button', { name: /expense/i }));
    expect(navigateMock.mock.calls.map((c) => c[0])).toEqual(['/material-in?new=1', '/material-out?new=1', '/expenses?new=1']);
  });
  it('quick actions are shown only for records the user may create', async () => {
    perms = ['MATERIAL_OUT.CREATE'];
    renderPage(FULL);
    await screen.findByTestId('kpi-ops');
    expect(screen.getByRole('button', { name: /material out/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /material in/i })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /expense/i })).not.toBeInTheDocument();
    perms = [];
  });
  it('full payload: all cards, all three series, low stock and recent transactions', async () => {
    renderPage(FULL);
    expect(await screen.findByTestId('stat-exp')).toBeInTheDocument();
    expect(screen.getByTestId('stat-rec')).toBeInTheDocument();
    expect(screen.getByTestId('stat-pay')).toBeInTheDocument();
    expect(await screen.findByTestId('chart-tons')).toHaveTextContent('Material IN|Material OUT|Stock balance');
    expect(screen.getByTestId('chart-expenses')).toBeInTheDocument();
    expect(screen.getByTestId('chart-stock')).toBeInTheDocument();
    expect(screen.getByText('Counted in pieces: Bolts 40 PIECE')).toBeInTheDocument();
    expect(screen.getByText('Stock by material')).toBeInTheDocument();
    expect(screen.getByText('Low stock')).toBeInTheDocument();
    expect(screen.getByText('Recent transactions')).toBeInTheDocument();
  });
  it('viewer payload: no expenses/receivable/payable/recent, no empty series or ₹0.00 placeholders', async () => {
    renderPage(VIEWER);
    expect(await screen.findByTestId('stat-in')).toBeInTheDocument();
    expect(screen.queryByTestId('stat-exp')).not.toBeInTheDocument();
    expect(screen.queryByTestId('stat-rec')).not.toBeInTheDocument();
    expect(screen.queryByTestId('stat-pay')).not.toBeInTheDocument();
    expect(await screen.findByTestId('chart-tons')).toHaveTextContent(/^Material IN\|Material OUT\|Stock balance$/);
    expect(screen.queryByTestId('chart-expenses')).not.toBeInTheDocument();
    expect(screen.getByTestId('chart-stock')).toBeInTheDocument();
    expect(screen.getByText('Stock by material')).toBeInTheDocument();
    expect(screen.getByText('Low stock')).toBeInTheDocument();
    expect(screen.queryByText('Recent transactions')).not.toBeInTheDocument();
    expect(screen.queryByText(/₹0\.00/)).not.toBeInTheDocument();
  });
  it('minimal payload renders no chart and no empty sections', async () => {
    renderPage({ today: '2026-11-08' });
    await screen.findByText('Dashboard');
    expect(screen.queryByTestId('chart-tons')).not.toBeInTheDocument();
    expect(screen.queryByText('Low stock')).not.toBeInTheDocument();
    expect(screen.queryByTestId('stat-grid')).not.toBeInTheDocument();
  });
});
