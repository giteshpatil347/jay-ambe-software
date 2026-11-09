import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { materialService } from '../../services/materialService';
import TransactionListPage from './TransactionListPage';

let perms: string[] = [];
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: (p: string) => perms.includes(p) }) }));
vi.mock('../../pages/payments/PartySelect', () => ({ PartySelect: () => <div data-testid="party-filter" /> }));
vi.mock('./ProductSelect', () => ({ ProductSelect: () => <div data-testid="product-filter" /> }));
vi.mock('./TransactionForm', () => ({ TransactionForm: () => null }));
vi.mock('./TransactionDetail', () => ({ TransactionDetail: () => null }));
vi.mock('../../services/materialService');

function renderList() {
  vi.mocked(materialService.list).mockResolvedValue({ content: [], totalElements: 0, totalAmount: '0' } as never);
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><MemoryRouter><TransactionListPage type="IN" /></MemoryRouter></QueryClientProvider>);
}

describe('TransactionListPage filters', () => {
  it('hides party and product filters for a viewer (no PARTY.VIEW / PRODUCT.VIEW)', () => {
    perms = ['MATERIAL_IN.VIEW'];
    renderList();
    expect(screen.queryByTestId('party-filter')).not.toBeInTheDocument();
    expect(screen.queryByTestId('product-filter')).not.toBeInTheDocument();
    expect(screen.getAllByLabelText('Status filter')[0]).toBeInTheDocument();
  });
  it('shows each filter only with its own permission', () => {
    perms = ['MATERIAL_IN.VIEW', 'PRODUCT.VIEW'];
    renderList();
    expect(screen.queryByTestId('party-filter')).not.toBeInTheDocument();
    expect(screen.getByTestId('product-filter')).toBeInTheDocument();
  });
  it('shows both for a full user', () => {
    perms = ['MATERIAL_IN.VIEW', 'PARTY.VIEW', 'PRODUCT.VIEW'];
    renderList();
    expect(screen.getByTestId('party-filter')).toBeInTheDocument();
    expect(screen.getByTestId('product-filter')).toBeInTheDocument();
  });
});

describe('TransactionListPage quantity', () => {
  it('shows each entry\'s quantity per unit (desktop table)', async () => {
    perms = ['MATERIAL_IN.VIEW'];
    const realMatch = window.matchMedia;
    window.matchMedia = ((q: string) => ({ ...realMatch(q), matches: true })) as typeof window.matchMedia;
    vi.mocked(materialService.list).mockResolvedValue({
      content: [{ id: 1, transactionNumber: 'MIN-1', transactionDate: '2026-11-09', partyId: 1, partyName: 'Shree', itemCount: 2, productNames: ['Steel', 'Wire'],
        quantities: [{ unit: 'KG', quantity: '40.000' }, { unit: 'TON', quantity: '12.500' }], totalAmount: '100.00', status: 'POSTED', createdByName: 'A' }],
      page: 0, size: 20, totalElements: 1, totalAmount: '100.00',
    } as never);
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(<QueryClientProvider client={qc}><MemoryRouter><TransactionListPage type="IN" /></MemoryRouter></QueryClientProvider>);
    expect(await screen.findByText('40 KG + 12.5 TON')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: 'Quantity' })).toBeInTheDocument();
    window.matchMedia = realMatch;
  });
});
