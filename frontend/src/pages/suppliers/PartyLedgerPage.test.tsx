import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { vi } from 'vitest';
import { ledgerService } from '../../services/ledgerService';
import { reportService } from '../../services/reportService';
import PartyLedgerPage from './PartyLedgerPage';

vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: () => true }) }));
vi.mock('../../services/ledgerService');
vi.mock('../../services/reportService');
vi.mock('../payments/PaymentFormDialog', () => ({ PaymentFormDialog: () => null }));

it('ledger page asks for the ledger column list', async () => {
  vi.mocked(ledgerService.ledger).mockResolvedValue({
    party: { id: 1, name: 'Acme', code: 'P1', type: 'BOTH' }, openingBalance: '0', closingBalance: '0', entries: [], totals: { debit: '0', credit: '0' },
  } as never);
  vi.mocked(reportService.exportColumns).mockResolvedValue(['Date', 'Balance']);
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={['/suppliers/1/ledger']}><Routes><Route path="/suppliers/:id/ledger" element={<PartyLedgerPage />} /></Routes></MemoryRouter>
  </QueryClientProvider>);
  expect(await screen.findByRole('button', { name: /Columns/ })).toBeInTheDocument();
  expect(reportService.exportColumns).toHaveBeenCalledWith('ledger');
});
