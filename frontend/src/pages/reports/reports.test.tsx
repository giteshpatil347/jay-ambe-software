import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import ReportsPage from './ReportsPage';
import DashboardPage, { statCards } from '../dashboard/DashboardPage';
import { reportService } from '../../services/reportService';
import { dashboardService } from '../../services/dashboardService';

let perms: string[] = [];
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: (p?: string | string[]) => !p || [p].flat().some((x) => perms.includes(x)) }) }));
vi.mock('../payments/PartySelect', () => ({ PartySelect: () => <span /> }));
vi.mock('../../components/transactions/ProductSelect', () => ({ ProductSelect: () => <span /> }));
vi.mock('../../services/reportService');
vi.mock('../../services/dashboardService');
vi.mock('../../services/expenseService');
vi.mock('../../services/productService');
vi.mock('../dashboard/DashboardChart', () => ({ default: () => <div data-testid="chart" /> }));

const svc = vi.mocked(reportService);
const wrap = (ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>);

beforeEach(() => {
  vi.resetAllMocks();
  svc.material.mockResolvedValue({
    content: [], page: 0, size: 50, totalElements: 0,
    summary: { transactionCount: 3, totalAmount: '1500.00', quantityByUnit: [{ unit: 'KG', quantity: '10.000' }, { unit: 'TON', quantity: '2.500' }] },
  });
});

describe('Reports export gating', () => {
  it('hides export without both REPORT.EXPORT and the module permission', async () => {
    perms = ['REPORT.VIEW', 'REPORT.EXPORT'];
    wrap(<ReportsPage />);
    await screen.findByText('Total amount (posted)');
    expect(screen.queryByRole('button', { name: /PDF/ })).toBeNull();
  });
  it('hides export with only the module permission', async () => {
    perms = ['REPORT.VIEW', 'MATERIAL_IN.EXPORT'];
    wrap(<ReportsPage />);
    await screen.findByText('Total amount (posted)');
    expect(screen.queryByRole('button', { name: /CSV/ })).toBeNull();
  });
  it('shows export and calls the service with both permissions', async () => {
    perms = ['REPORT.VIEW', 'REPORT.EXPORT', 'MATERIAL_IN.EXPORT'];
    svc.exportReport.mockResolvedValue('x.pdf');
    wrap(<ReportsPage />);
    await userEvent.click(await screen.findByRole('button', { name: /PDF/ }));
    await waitFor(() => expect(svc.exportReport).toHaveBeenCalledWith('material-in', 'pdf', expect.any(Object), undefined));
  });
});

describe('Reports export column kinds', () => {
  it('each report tab asks for its own column list', async () => {
    perms = ['REPORT.VIEW', 'REPORT.EXPORT', 'MATERIAL_IN.EXPORT', 'MATERIAL_OUT.EXPORT', 'EXPENSE.EXPORT', 'INVENTORY.EXPORT'];
    svc.exportColumns.mockResolvedValue(['Number', 'Amount']);
    svc.material.mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, summary: { transactionCount: 0, totalAmount: '0', quantityByUnit: [] } });
    wrap(<ReportsPage />);
    await screen.findByRole('button', { name: /Columns/ });
    expect(svc.exportColumns).toHaveBeenCalledWith('material-in');
    await userEvent.click(screen.getByRole('tab', { name: /Material OUT/ }));
    await waitFor(() => expect(svc.exportColumns).toHaveBeenCalledWith('material-out'));
  });
});

describe('quantity by unit', () => {
  it('lists each unit separately and never a combined total', async () => {
    perms = ['REPORT.VIEW'];
    wrap(<ReportsPage />);
    const el = await screen.findByTestId('qty-by-unit');
    expect(el).toHaveTextContent('10 KG');
    expect(el).toHaveTextContent('2.5 TON');
    expect(el).not.toHaveTextContent('12.5');
  });
});

describe('Dashboard', () => {
  it('renders with partial data (only some fields)', async () => {
    perms = ['DASHBOARD.VIEW'];
    vi.mocked(dashboardService.get).mockResolvedValue({ receivable: '500.00', lowStockCount: 0 });
    wrap(<DashboardPage />);
    expect(await screen.findByTestId('stat-rec')).toHaveTextContent('500');
    expect(screen.getByTestId('stat-low')).toHaveTextContent('0');
    expect(screen.queryByTestId('stat-in')).toBeNull();
    expect(screen.queryByTestId('chart')).toBeNull();
    expect(screen.getByText('Nothing low on stock')).toBeInTheDocument();
  });
  it('shows period cards, chart and recent list', async () => {
    perms = ['DASHBOARD.VIEW'];
    vi.mocked(dashboardService.get).mockResolvedValue({
      periodTotals: { inTons: '100.000', outTons: '0.000', expenses: '5.00' },
      series: [{ date: '2026-11-08', inTons: '10.000', outTons: '0.000', balanceTons: '10.000', expenses: '1.00' }],
      recent: [{ id: 1, type: 'IN', transactionNumber: 'IN-1', transactionDate: '2026-11-08', partyName: null, totalAmount: '10.00' }],
    });
    wrap(<DashboardPage />);
    expect(await screen.findByTestId('stat-in')).toHaveTextContent('Last 30 days');
    expect((await screen.findAllByTestId('chart')).length).toBeGreaterThan(0);
    expect(screen.getByText('Recent transactions')).toBeInTheDocument();
  });
  it('statCards is empty for an empty payload', () => { expect(statCards({})).toEqual([]); });
});
