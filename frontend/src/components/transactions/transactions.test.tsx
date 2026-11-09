import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { vi } from 'vitest';
import { materialService } from '../../services/materialService';
import { productService } from '../../services/productService';
import type { TxDetail } from '../../types/material';
import { TransactionForm } from './TransactionForm';
import {
  buildPayload, convertQuantity, grandTotal, lineAmount, mapStockErrors, newRow, stockShortfalls, storedRows, unitTotals, validateForm,
  type ProductInfo,
} from './transactionLogic';

vi.mock('./ProductSelect', () => ({
  ProductSelect: ({ value, onSelect }: { value?: number; onSelect: (p: ProductInfo) => void }) => (
    <select aria-label="Material" value={value ?? ''} onChange={(e) => onSelect(PRODUCTS[Number(e.target.value)])}>
      <option value="">-</option>
      {Object.values(PRODUCTS).map((p) => <option key={p.id} value={p.id}>{p.code}</option>)}
    </select>
  ),
}));
vi.mock('../../pages/payments/PartySelect', () => ({
  PartySelect: ({ onChange }: { onChange?: (v: number) => void }) => <button type="button" onClick={() => onChange?.(7)}>pick party</button>,
}));
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: () => true }) }));
vi.mock('../../services/attachmentService');
vi.mock('../../services/materialService');
vi.mock('../../services/productService');

const PRODUCTS: Record<number, ProductInfo> = {
  1: { id: 1, code: 'CEM', name: 'Cement', unit: 'TON' },
  2: { id: 2, code: 'SND', name: 'Sand', unit: 'KG' },
  3: { id: 3, code: 'BRK', name: 'Bricks', unit: 'PIECE' },
};
const row = (productId: number, quantity: string, rate = '') => newRow({ productId, quantity, rate });

describe('line math', () => {
  it('computes string-safe amounts and totals', () => {
    expect(lineAmount('12.500', '15000')).toBe('187500.00');
    expect(lineAmount('0.333', '100.10')).toBe('33.33');
    expect(lineAmount('2', '')).toBe('0.00');
    expect(lineAmount('', '5')).toBeNull();
    expect(grandTotal([row(1, '12.500', '15000'), row(1, '0.333', '100.10'), row(2, '', '')])).toBe('187533.33');
  });
  it('keeps unit totals separate (no TON + KG)', () => {
    const t = unitTotals([row(1, '2.5'), row(2, '100'), row(1, '1.250')], PRODUCTS);
    expect(t).toEqual([{ unit: 'TON', quantity: '3.75' }, { unit: 'KG', quantity: '100' }]);
  });
});

describe('unit conversion (parity with the server UnitConverter)', () => {
  it.each([
    ['500', 'KG', 'TON', '0.500'], ['0.5', 'KG', 'TON', '0.001'], ['1499', 'KG', 'TON', '1.499'], ['2.5', 'KG', 'TON', '0.003'],
    ['0.5004', 'KG', 'TON', '0.001'], ['12345.678', 'KG', 'TON', '12.346'],
    ['2', 'TON', 'KG', '2000.000'], ['0.001', 'TON', 'KG', '1.000'], ['0.0005', 'TON', 'KG', '0.500'], ['12.5', 'TON', 'KG', '12500.000'],
    ['12.5', 'TON', 'TON', '12.500'], ['7', 'KG', 'KG', '7.000'], ['3', 'PIECE', 'PIECE', '3.000'],
  ] as const)('%s %s -> %s product = %s', (q, from, to, expected) => {
    expect(convertQuantity(q, from, to)).toEqual({ value: expected });
  });
  it('treats an undefined entry unit as the product unit', () => {
    expect(convertQuantity('4', undefined, 'KG')).toEqual({ value: '4.000' });
  });
  it('rejects too-small, impossible and oversized conversions', () => {
    expect(convertQuantity('0.4', 'KG', 'TON')).toEqual({ error: 'Too small: converts to 0 TON' });
    expect(convertQuantity('0.0004', 'KG', 'TON')).toHaveProperty('error');
    expect(convertQuantity('1', 'KG', 'PIECE')).toHaveProperty('error');
    expect(convertQuantity('1', 'PIECE', 'TON')).toHaveProperty('error');
    expect(convertQuantity('9999999999', 'TON', 'KG')).toHaveProperty('error');
    expect(convertQuantity('', 'KG', 'TON')).toBeNull();
    expect(convertQuantity('0', 'KG', 'TON')).toBeNull();
  });
  it('totals, amounts and unit totals use the converted quantity; rate is per product unit', () => {
    const rows = storedRows([newRow({ productId: 1, quantity: '500', rate: '15000', unit: 'KG' })], PRODUCTS);
    expect(rows[0].quantity).toBe('0.500');
    expect(grandTotal(rows)).toBe('7500.00');
    expect(unitTotals(rows, PRODUCTS)).toEqual([{ unit: 'TON', quantity: '0.5' }]);
  });
  it('validateForm flags a too-small conversion and payload carries unit with the typed quantity', () => {
    const h = { date: dayjs('2026-11-08'), partyId: 7, challanNumber: '', vehicleNumber: '', driverName: '', remarks: '' };
    const r = newRow({ productId: 1, quantity: '0.4', rate: '10', unit: 'KG' });
    expect(validateForm('IN', h, [r], PRODUCTS).rows[r.key]).toMatch(/Too small/);
    expect(buildPayload(h, [r]).items).toEqual([{ productId: 1, quantity: '0.4', unit: 'KG', rate: '10' }]);
  });
});

describe('stock check', () => {
  it('sums duplicate rows of the same product', () => {
    expect(stockShortfalls([row(1, '6'), row(1, '6')], { 1: '10.000' })).toEqual([{ productId: 1, required: '12', available: '10.000' }]);
    expect(stockShortfalls([row(1, '6'), row(2, '6')], { 1: '10', 2: '10' })).toEqual([]);
  });
  it('adds back the original quantity when editing', () => {
    expect(stockShortfalls([row(1, '9')], { 1: '2' }, { 1: '8' })).toEqual([]);
    expect(stockShortfalls([row(1, '10.001')], { 1: '2' }, { 1: '8' })).toHaveLength(1);
  });
  it('ignores products whose stock is not loaded yet', () => {
    expect(stockShortfalls([row(1, '99')], {})).toEqual([]);
  });
});

describe('payload and error mapping', () => {
  const h = { date: dayjs('2026-11-08'), partyId: 7, challanNumber: ' C1 ', vehicleNumber: 'mh12ab1234', driverName: '', remarks: '' };
  it('keeps strings, uppercases vehicle, sends version on edit only', () => {
    const create = buildPayload(h, [row(1, '12.500', '15000.50'), row(2, '3.', '')]);
    expect(create).toEqual({
      transactionDate: '2026-11-08', partyId: 7, challanNumber: 'C1', vehicleNumber: 'MH12AB1234', driverName: undefined, remarks: undefined,
      items: [{ productId: 1, quantity: '12.500', rate: '15000.50' }, { productId: 2, quantity: '3', rate: undefined }],
    });
    expect('version' in create).toBe(false);
    expect(buildPayload(h, [row(1, '1', '1')], 4).version).toBe(4);
  });
  it('maps fieldErrors (product code) onto every matching row', () => {
    const rows = [row(1, '6'), row(2, '1'), row(1, '6')];
    const m = mapStockErrors([{ field: 'CEM', message: 'Cement: available 10' }, { field: 'ZZZ', message: 'unknown' }], rows, PRODUCTS);
    expect(m.byRow).toEqual({ [rows[0].key]: 'Cement: available 10', [rows[2].key]: 'Cement: available 10' });
    expect(m.other).toEqual(['unknown']);
  });
});

function renderForm(props: Partial<Parameters<typeof TransactionForm>[0]> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><TransactionForm type="OUT" open onClose={() => {}} {...props} /></QueryClientProvider>);
}
const stockOf = (available: string) => vi.mocked(productService.get).mockResolvedValue({ id: 1, availableStock: available } as never);
const saveBtn = () => screen.getByRole('button', { name: 'Save' });

async function fillRow(user: ReturnType<typeof userEvent.setup>, i: number, qty: string, product = '1') {
  const r = screen.getByTestId(`row-${i}`);
  await user.selectOptions(within(r).getByLabelText('Material'), product);
  await user.type(within(r).getByLabelText(`Quantity ${i + 1}`), qty);
}

describe('TransactionForm OUT', () => {
  beforeEach(() => { vi.resetAllMocks(); });

  it('shows live stock and disables Save when duplicate rows exceed it', async () => {
    stockOf('10.000');
    const user = userEvent.setup();
    renderForm();
    await fillRow(user, 0, '6');
    await waitFor(() => expect(screen.getByTestId('avail-0')).toHaveTextContent('Available: 10 TON'));
    expect(saveBtn()).toBeEnabled();
    await user.click(screen.getByRole('button', { name: /Add item/ }));
    await fillRow(user, 1, '6');
    expect(await screen.findAllByText(/Only 10 TON available, 12 entered/)).toHaveLength(2);
    expect(saveBtn()).toBeDisabled();
    expect(productService.get).toHaveBeenCalledWith(1);
  });

  it('maps a server 409 fieldErrors onto the row and keeps entered data', async () => {
    stockOf('10');
    vi.mocked(materialService.create).mockRejectedValue({
      response: { status: 409, data: { code: 'INSUFFICIENT_STOCK', message: 'Not enough stock', fieldErrors: [{ field: 'CEM', message: 'Cement: available 4, required 6' }] } },
    });
    const user = userEvent.setup();
    renderForm();
    await fillRow(user, 0, '6');
    await user.click(saveBtn());
    expect(await within(screen.getByTestId('row-0')).findByText('Cement: available 4, required 6')).toBeInTheDocument();
    expect(within(screen.getByTestId('row-0')).getByLabelText('Quantity 1')).toHaveValue('6');
    expect(materialService.create).toHaveBeenCalledTimes(1);
    const payload = vi.mocked(materialService.create).mock.calls[0][1];
    expect(payload.items).toEqual([{ productId: 1, quantity: '6', unit: 'TON', rate: undefined }]);
    expect('version' in payload).toBe(false);
  });

  it('on edit adds back the original quantity, sends version', async () => {
    stockOf('2');
    const tx: TxDetail = {
      id: 5, transactionNumber: 'OUT-2026-00005', transactionType: 'OUT', transactionDate: '2026-11-01', partyId: 7, partyName: 'Acme',
      totalAmount: '0.00', status: 'POSTED', version: 3, createdAt: '2026-11-01T00:00:00Z',
      items: [{ id: 1, productId: 1, productCode: 'CEM', productName: 'Cement', unit: 'TON', quantity: '8.000', rate: '0.00', amount: '0.00' }],
    };
    vi.mocked(materialService.get).mockResolvedValue(tx);
    vi.mocked(materialService.update).mockResolvedValue(tx);
    const user = userEvent.setup();
    renderForm({ transactionId: 5 });
    expect(await screen.findByDisplayValue('OUT-2026-00005')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByTestId('avail-0')).toHaveTextContent('Available: 2 TON'));
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty); await user.type(qty, '10');
    expect(saveBtn()).toBeEnabled();
    await user.clear(qty); await user.type(qty, '10.5');
    expect(saveBtn()).toBeDisabled();
    await user.clear(qty); await user.type(qty, '9');
    await user.click(saveBtn());
    await waitFor(() => expect(materialService.update).toHaveBeenCalled());
    expect(vi.mocked(materialService.update).mock.calls[0][2].version).toBe(3);
  });
});

describe('TransactionForm units', () => {
  beforeEach(() => { vi.resetAllMocks(); });
  const unitOf = (i: number) => within(screen.getByTestId(`row-${i}`)).getByRole('combobox', { name: `Unit ${i + 1}` });

  it('new lines default to TON, show the hint, a per-product rate label and send the typed quantity + unit', async () => {
    vi.mocked(materialService.create).mockResolvedValue({ id: 1, transactionNumber: 'MIN-1' } as never);
    const user = userEvent.setup();
    renderForm({ type: 'IN' });
    await fillRow(user, 0, '1');
    expect(screen.getByTestId('row-0')).toHaveTextContent('TON');
    await user.click(unitOf(0));
    await user.click(await screen.findByTitle('KG'));
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty); await user.type(qty, '500');
    expect(screen.getByTestId('convert-0')).toHaveTextContent('= 0.500 TON (stock unit)');
    expect(screen.getByTestId('rate-label-0')).toHaveTextContent('Rate (₹ per TON)');
    await user.type(screen.getByLabelText('Rate 1'), '15000');
    expect(screen.getByTestId('amount-0')).toHaveTextContent('7,500.00');
    expect(screen.getByTestId('unit-totals')).toHaveTextContent('0.5 TON');
    await user.click(screen.getByRole('button', { name: 'pick party' }));
    await user.click(saveBtn());
    await waitFor(() => expect(materialService.create).toHaveBeenCalled());
    expect(vi.mocked(materialService.create).mock.calls[0][1].items).toEqual([{ productId: 1, quantity: '500', unit: 'KG', rate: '15000' }]);
  });

  it('shows the too-small error inline and blocks saving', async () => {
    const user = userEvent.setup();
    renderForm({ type: 'IN' });
    await fillRow(user, 0, '1');
    await user.click(unitOf(0));
    await user.click(await screen.findByTitle('KG'));
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty); await user.type(qty, '0.4');
    expect(screen.getByTestId('convert-0')).toHaveTextContent('Too small: converts to 0 TON');
    await user.type(screen.getByLabelText('Rate 1'), '1');
    await user.click(screen.getByRole('button', { name: 'pick party' }));
    await user.click(saveBtn());
    expect(await within(screen.getByTestId('row-0')).findAllByText(/Too small/)).not.toHaveLength(0);
    expect(materialService.create).not.toHaveBeenCalled();
  });

  it('PIECE products have a fixed, disabled PIECE unit and no hint', async () => {
    const user = userEvent.setup();
    renderForm({ type: 'IN' });
    await fillRow(user, 0, '5', '3');
    expect(unitOf(0).closest('.ant-select')).toHaveClass('ant-select-disabled');
    expect(screen.getByTestId('row-0')).toHaveTextContent('PIECE');
    expect(screen.queryByTestId('convert-0')).toBeNull();
    expect(screen.getByTestId('rate-label-0')).toHaveTextContent('Rate (₹ per PIECE)');
  });

  it('editing defaults existing lines to the product unit (no hint) and OUT uses the converted quantity for stock', async () => {
    stockOf('2');
    const tx: TxDetail = {
      id: 5, transactionNumber: 'OUT-2026-00005', transactionType: 'OUT', transactionDate: '2026-11-01', partyId: 7, partyName: 'Acme',
      totalAmount: '0.00', status: 'POSTED', version: 3, createdAt: '2026-11-01T00:00:00Z',
      items: [{ id: 1, productId: 1, productCode: 'CEM', productName: 'Cement', unit: 'TON', quantity: '1.000', rate: '0.00', amount: '0.00' }],
    };
    vi.mocked(materialService.get).mockResolvedValue(tx);
    const user = userEvent.setup();
    renderForm({ transactionId: 5 });
    await screen.findByDisplayValue('OUT-2026-00005');
    await waitFor(() => expect(screen.getByTestId('avail-0')).toHaveTextContent('Available: 2 TON'));
    expect(screen.getByTestId('row-0')).toHaveTextContent('TON');
    expect(screen.queryByTestId('convert-0')).toBeNull();
    await user.click(unitOf(0));
    await user.click(await screen.findByTitle('KG'));
    const qty = screen.getByLabelText('Quantity 1');
    await user.clear(qty); await user.type(qty, '3001');
    expect(screen.getByTestId('convert-0')).toHaveTextContent('= 3.001 TON (stock unit)');
    expect(saveBtn()).toBeDisabled();
    expect(screen.getByText(/Only 3 TON available, 3.001 entered/)).toBeInTheDocument();
    await user.clear(qty); await user.type(qty, '3000');
    expect(saveBtn()).toBeEnabled();
  });
});

describe('TransactionForm IN', () => {
  beforeEach(() => { vi.resetAllMocks(); });
  it('requires supplier and rate, shows decimal keyboard and totals', async () => {
    const user = userEvent.setup();
    renderForm({ type: 'IN' });
    expect(screen.getByLabelText('Quantity 1')).toHaveAttribute('inputmode', 'decimal');
    await fillRow(user, 0, '12.500');
    await user.type(screen.getByLabelText('Rate 1'), '15000');
    expect(screen.getByTestId('amount-0')).toHaveTextContent('1,87,500.00');
    expect(screen.getByTestId('grand-total')).toHaveTextContent('1,87,500.00');
    expect(screen.getByTestId('unit-totals')).toHaveTextContent('12.5 TON');
    await user.click(saveBtn());
    expect(await screen.findByText('Supplier is required')).toBeInTheDocument();
    expect(materialService.create).not.toHaveBeenCalled();
  });
});
