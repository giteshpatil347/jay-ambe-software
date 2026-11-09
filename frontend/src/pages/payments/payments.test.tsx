import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';
import { vi } from 'vitest';
import { buildPaymentBody } from './paymentForm';
import { CancelPaymentModal } from './CancelPaymentModal';
import { formatBalance, balanceSide } from '../../utils/balance';
import { BalanceText, amountCell } from '../suppliers/ledgerView';
import { diffValues } from '../audit-logs/diff';
import { sumSide } from '../suppliers/PartyBalancesTab';

describe('payment payload', () => {
  it('maps form values, keeps amount a string, drops blanks', () => {
    expect(buildPaymentBody({ partyId: 3, paymentDate: dayjs('2026-11-08'), direction: 'RECEIVED', amount: ' 1500.50 ', paymentMode: 'UPI', referenceNumber: ' ', remarks: undefined }))
      .toEqual({ partyId: 3, paymentDate: '2026-11-08', direction: 'RECEIVED', amount: '1500.50', paymentMode: 'UPI', referenceNumber: undefined, remarks: undefined });
  });
});

describe('cancel payment', () => {
  it('requires a reason', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(<CancelPaymentModal open paymentNumber="PAY-2026-00001" onCancel={() => {}} onConfirm={onConfirm} />);
    await user.click(screen.getByRole('button', { name: 'Cancel payment' }));
    expect(await screen.findByText('Reason is required')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Reason'), '  wrong party ');
    await user.click(screen.getByRole('button', { name: 'Cancel payment' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('wrong party'));
  });
});

describe('ledger labels', () => {
  it('formats receivable/payable, never a bare minus', () => {
    expect(balanceSide('100.00')).toBe('RECEIVABLE');
    expect(formatBalance('1500.00')).toMatch(/^Receivable\s+₹1,500\.00$/);
    expect(formatBalance('-2500.50')).toMatch(/^Payable\s+₹2,500\.50$/);
    expect(formatBalance('-2500.50')).not.toContain('-');
    expect(formatBalance('0.00')).toMatch(/₹0\.00$/);
  });
  it('renders balance text and dash for zero debit/credit', () => {
    render(<BalanceText value="-10.00" />);
    expect(screen.getByText(/Payable/)).toBeInTheDocument();
    expect(amountCell('0.00')).toBe('—');
    expect(amountCell('500.00')).toMatch(/₹500\.00/);
  });
  it('sums sides in string math', () => {
    const rows = [{ balance: '10.10' }, { balance: '-3.05' }, { balance: '0.20' }, { balance: '-1.00' }] as never[];
    expect(sumSide(rows, 'RECEIVABLE')).toBe('10.30');
    expect(sumSide(rows, 'PAYABLE')).toBe('4.05');
  });
});

describe('audit diff', () => {
  it('lists only changed keys, flattens nested, shows redacted as-is', () => {
    expect(diffValues({ a: 1, b: { c: 'x' }, password: '***' }, { a: 2, b: { c: 'x' }, password: '***', d: 'new' })).toEqual([
      { key: 'a', oldValue: '1', newValue: '2' },
      { key: 'd', oldValue: null, newValue: 'new' },
    ]);
  });
  it('handles create (null old) and delete (null new)', () => {
    expect(diffValues(null, { k: 'v' })).toEqual([{ key: 'k', oldValue: null, newValue: 'v' }]);
    expect(diffValues({ k: 'v' }, null)).toEqual([{ key: 'k', oldValue: 'v', newValue: null }]);
  });
});
