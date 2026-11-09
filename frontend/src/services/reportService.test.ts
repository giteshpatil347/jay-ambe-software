import { vi } from 'vitest';
import { reportService } from './reportService';
import * as download from '../utils/download';

describe('reportService export column params', () => {
  beforeEach(() => { vi.spyOn(download, 'downloadFile').mockResolvedValue('x'); });
  afterEach(() => vi.restoreAllMocks());

  it('passes a subset as the columns array (serialised as repeated params)', async () => {
    await reportService.exportReport('stock', 'csv', { productId: 3 }, ['Code', 'Status']);
    expect(download.downloadFile).toHaveBeenCalledWith('/reports/stock/export', { productId: 3, format: 'csv', columns: ['Code', 'Status'] }, 'stock.csv');
    await reportService.ledgerExport(9, 'pdf', '2026-01-01', undefined, ['Date']);
    expect(download.downloadFile).toHaveBeenLastCalledWith('/reports/parties/9/ledger/export', { format: 'pdf', from: '2026-01-01', columns: ['Date'] }, 'ledger.pdf');
    await reportService.challanPdf(4, ['#', 'Amount']);
    expect(download.downloadFile).toHaveBeenLastCalledWith('/reports/transactions/4/pdf', { columns: ['#', 'Amount'] }, 'challan-4.pdf');
  });

  it('sends no columns param when none/empty are given', async () => {
    await reportService.exportReport('expenses', 'pdf', {});
    expect(download.downloadFile).toHaveBeenLastCalledWith('/reports/expenses/export', { format: 'pdf' }, 'expenses.pdf');
    await reportService.challanPdf(4, []);
    expect(download.downloadFile).toHaveBeenLastCalledWith('/reports/transactions/4/pdf', {}, 'challan-4.pdf');
  });
});
