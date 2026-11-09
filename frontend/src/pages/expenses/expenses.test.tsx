import { render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';
import { Form } from 'antd';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { employeeService } from '../../services/employeeService';
import { expenseService } from '../../services/expenseService';
import ExpensesPage from './ExpensesPage';
import { ExpenseFormFields } from './ExpenseFormFields';
import { CancelExpenseModal } from './CancelExpenseModal';
import { buildExpenseBody, newExpenseDefaults, type ExpenseFormValues } from './expenseForm';
import type { CategoryType, Employee, Expense, ExpenseCategory } from '../../types/expense';

vi.mock('../../services/employeeService');
vi.mock('../../services/expenseService');
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: () => true }) }));

const cats: ExpenseCategory[] = [
  { id: 1, categoryCode: 'SALARY', categoryName: 'Salary', categoryType: 'SALARY', fixed: true, status: 'ACTIVE' },
  { id: 2, categoryCode: 'ELEC', categoryName: 'Electricity', categoryType: 'ELECTRICITY', fixed: true, status: 'ACTIVE' },
  { id: 3, categoryCode: 'TEA', categoryName: 'Tea', categoryType: 'GENERAL', fixed: false, status: 'ACTIVE' },
];
const emps: Employee[] = [{ id: 9, employeeCode: 'EMP-0001', employeeName: 'Ravi', monthlySalary: '4500.00', status: 'ACTIVE', createdAt: '' }];

function Harness({ type }: { type?: CategoryType }) {
  const [form] = Form.useForm<ExpenseFormValues>();
  return <Form form={form} initialValues={{ expenseDate: dayjs('2026-11-08') }}><ExpenseFormFields form={form} categories={cats} employees={emps} categoryType={type} onCategoryChange={() => {}} /></Form>;
}

describe('expense form blocks', () => {
  it('shows salary block only for SALARY', () => {
    render(<Harness type="SALARY" />);
    expect(screen.getByTestId('salary-block')).toBeInTheDocument();
    expect(screen.queryByTestId('electricity-block')).toBeNull();
  });
  it('shows electricity block only for ELECTRICITY', () => {
    render(<Harness type="ELECTRICITY" />);
    expect(screen.getByTestId('electricity-block')).toBeInTheDocument();
    expect(screen.queryByTestId('salary-block')).toBeNull();
  });
  it('shows neither for GENERAL', () => {
    render(<Harness type="GENERAL" />);
    expect(screen.queryByTestId('salary-block')).toBeNull();
    expect(screen.queryByTestId('electricity-block')).toBeNull();
  });
  it('uses decimal keyboard on money fields', () => {
    render(<Harness type="SALARY" />);
    expect(screen.getByLabelText('Amount')).toHaveAttribute('inputmode', 'decimal');
    expect(screen.getByLabelText('Monthly salary')).toHaveAttribute('inputmode', 'decimal');
  });
  it('suggests amount = gross - adjustment, keeping it editable', async () => {
    const user = userEvent.setup();
    render(<Harness type="SALARY" />);
    await user.type(screen.getByLabelText('Monthly salary'), '4500.50');
    await user.type(screen.getByLabelText('Advance adjustment'), '500.25');
    await user.click(screen.getByRole('button', { name: /Set amount/ }));
    expect(screen.getByLabelText('Amount')).toHaveValue('4000.25');
    await user.clear(screen.getByLabelText('Amount'));
    await user.type(screen.getByLabelText('Amount'), '4000');
    expect(screen.getByLabelText('Amount')).toHaveValue('4000');
  });
});

describe('electricity form', () => {
  it('shows exactly the electricity fields, defaulting to HOURLY', () => {
    render(<Harness type="ELECTRICITY" />);
    for (const l of ['Date', 'Category', 'Connection', 'Usage type', 'No. of hours', 'Price (optional)', 'Remarks']) {
      expect(screen.getAllByLabelText(l).length).toBeGreaterThan(0);
    }
    for (const l of ['Usage period', 'Units consumed', 'Unit rate', 'Payment mode', 'Paid to', 'No. of days', 'Voucher number', 'Amount']) {
      expect(screen.queryByLabelText(l)).toBeNull();
    }
    expect(screen.getByText('Hourly')).toBeInTheDocument();
    expect(screen.getByLabelText('No. of hours')).toHaveAttribute('inputmode', 'decimal');
  });
  it('labels the quantity as days for DAILY usage', async () => {
    const user = userEvent.setup();
    render(<Harness type="ELECTRICITY" />);
    await user.click(screen.getByRole('combobox', { name: 'Usage type' }));
    await user.click(await screen.findByTitle('Daily'));
    expect(await screen.findByLabelText('No. of days')).toBeInTheDocument();
  });
  it('requires a positive quantity', async () => {
    const user = userEvent.setup();
    render(<Harness type="ELECTRICITY" />);
    await user.type(screen.getByLabelText('No. of hours'), '0');
    expect(await screen.findByText(/Enter a number above 0/)).toBeInTheDocument();
  });
});

describe('salary form', () => {
  it('has no period, payment mode or voucher number, and labels the salary monthly', () => {
    render(<Harness type="SALARY" />);
    for (const l of ['Period', 'Payment mode', 'Voucher number', 'Gross salary']) expect(screen.queryByLabelText(l)).toBeNull();
    for (const l of ['Employee', 'Payment type', 'Monthly salary', 'Advance adjustment', 'Amount', 'Paid to', 'Remarks']) {
      expect(screen.getAllByLabelText(l).length).toBeGreaterThan(0);
    }
    expect(screen.getByText('MONTHLY')).toBeInTheDocument();
  });
  it('prefills the monthly salary effective on the expense date when an employee is picked', async () => {
    vi.mocked(employeeService.salaryAsOf).mockResolvedValue('4750.00');
    const user = userEvent.setup();
    render(<Harness type="SALARY" />);
    await user.click(screen.getByRole('combobox', { name: 'Employee' }));
    await user.click(await screen.findByTitle('Ravi (EMP-0001)'));
    await waitFor(() => expect(screen.getByLabelText('Monthly salary')).toHaveValue('4750.00'));
    expect(employeeService.salaryAsOf).toHaveBeenCalledWith(9, '2026-11-08');
    await user.clear(screen.getByLabelText('Monthly salary'));
    await user.type(screen.getByLabelText('Monthly salary'), '5000');
    expect(screen.getByLabelText('Monthly salary')).toHaveValue('5000');
  });
});

describe('expense list', () => {
  const row = (o: Partial<Expense>): Expense => ({
    id: 1, expenseNumber: 'EXP-2026-00001', expenseDate: '2026-11-08', categoryId: 3, categoryName: 'Tea', categoryType: 'GENERAL',
    amount: '250.00', paidTo: 'Vendor A', remarks: 'Office tea', status: 'POSTED', version: 0, createdAt: '', ...o,
  });
  const rows: Expense[] = [
    row({}),
    row({ id: 2, expenseNumber: 'EXP-2026-00002', categoryName: 'Electricity', categoryType: 'ELECTRICITY', amount: '999.00', remarks: null, paidTo: 'Stale Payee', usageType: 'HOURLY', usageQuantity: '8.00', connectionNumber: '2_NO' }),
    row({ id: 3, expenseNumber: 'EXP-2026-00003', categoryName: 'Electricity', categoryType: 'ELECTRICITY', amount: '0.00', remarks: null, usageType: 'DAILY', usageQuantity: '3.00', connectionNumber: '1_NO' }),
    row({ id: 4, expenseNumber: 'EXP-2026-00004', categoryName: 'Staff Salary', categoryType: 'SALARY', amount: '4500.00', remarks: null, employeeName: 'Ravi', paymentType: 'ADVANCE' }),
    row({ id: 5, expenseNumber: 'EXP-2026-00005', categoryName: 'Staff Salary', categoryType: 'SALARY', status: 'CANCELLED', cancellationReason: 'dup', amount: '60.00', remarks: null, employeeName: 'Meena', paymentType: 'ADHOC' }),
  ];
  function renderList() {
    vi.mocked(expenseService.list).mockResolvedValue({ content: rows, page: 0, size: 20, totalElements: 5, totalAmount: '1000.00' });
    vi.mocked(expenseService.categories).mockResolvedValue(cats);
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(<QueryClientProvider client={qc}><MemoryRouter><ExpensesPage /></MemoryRouter></QueryClientProvider>);
  }
  afterEach(() => { window.matchMedia = realMatchMedia; });
  const realMatchMedia = window.matchMedia;
  const desktop = () => { window.matchMedia = ((q: string) => ({ ...realMatchMedia(q), matches: true })) as typeof window.matchMedia; };

  it('desktop: no No., Paid to, Status or total bar; a Details column describes each category', async () => {
    desktop();
    renderList();
    const electricity = (await screen.findByText('Connection 2 · 8 hrs')).closest('tr') as HTMLElement;
    for (const gone of ['No.', 'Status', 'Paid to']) expect(screen.queryByRole('columnheader', { name: gone })).toBeNull();
    expect(screen.getByRole('columnheader', { name: 'Details' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Status filter')).toBeNull();
    expect(screen.queryByText(/EXP-2026-/)).toBeNull(); // document numbers are not shown in the list at all
    expect(screen.queryByTestId('total-bar')).toBeNull();
    expect(screen.queryByText('Total (posted)')).toBeNull();
    // electricity: connection + hours, the price only when one was entered, never a payee
    expect(within(electricity).getByText(/999\.00/)).toBeInTheDocument();
    expect(within(electricity).queryByText('Stale Payee')).toBeNull();
    const noPrice = screen.getByText('Connection 1 · 3 days').closest('tr') as HTMLElement;
    expect(within(noPrice).getByText('—')).toBeInTheDocument();
    // salary: employee + type, and the amount
    const salary = screen.getByText('Ravi · Advance').closest('tr') as HTMLElement;
    expect(within(salary).getByText(/4,500\.00/)).toBeInTheDocument();
    // other categories: the amount (+ remarks as details)
    expect(screen.queryByText('Vendor A')).toBeNull();
    const general = screen.getByText('Office tea').closest('tr') as HTMLElement;
    expect(within(general).getByText(/250\.00/)).toBeInTheDocument();
    // a cancelled row stays visible but struck through and without actions
    const cancelled = screen.getByText('Meena · Adhoc').closest('tr') as HTMLElement;
    expect(within(cancelled).queryByRole('button', { name: 'Cancel' })).toBeNull();
  });
  it('mobile cards apply the same rules', async () => {
    renderList();
    await screen.findByText(/Connection 2 · 8 hrs/);
    expect(screen.getByText('3 days')).toBeInTheDocument(); // electricity without a price: hours become the headline
    expect(screen.getByText(/Ravi · Advance/)).toBeInTheDocument();
    expect(screen.queryByText(/EXP-2026-/)).toBeNull();
    expect(screen.queryByText(/Vendor A|Stale Payee/)).toBeNull();
    expect(screen.queryByText('POSTED')).toBeNull();
    expect(screen.queryByText('CANCELLED')).toBeNull();
  });
});

describe('buildExpenseBody', () => {
  const base: ExpenseFormValues = { expenseDate: dayjs('2026-11-08'), categoryId: 1, amount: '4000.25', paidTo: ' ', remarks: 'wk' };
  it('keeps money as strings, sends only the salary block and version on edit', () => {
    const b = buildExpenseBody({
      ...base, paymentMode: 'CASH',
      salary: { employeeId: 9, paymentType: 'MONTHLY', grossSalary: '4500.50', advanceAdjustment: '500.25' },
      electricity: { connectionNumber: '1_NO', usageType: 'DAILY', usageQuantity: '2' },
    }, 'SALARY', 3);
    expect(b.amount).toBe('4000.25');
    expect(b.salary).toEqual({ employeeId: 9, paymentType: 'MONTHLY', grossSalary: '4500.50', advanceAdjustment: '500.25' });
    expect(b.electricity).toBeUndefined();
    expect(b.paymentMode).toBeUndefined();
    expect(b.version).toBe(3);
    expect(b.paidTo).toBeUndefined();
    expect(b.expenseDate).toBe('2026-11-08');
  });
  it('sends usageQuantity (no old fields, payee or payment mode) for ELECTRICITY and no version on create', () => {
    const b = buildExpenseBody({
      ...base, categoryId: 2, paidTo: 'X', paymentMode: 'UPI', salary: { employeeId: 1 },
      electricity: { connectionNumber: '2_NO', usageType: 'HOURLY', usageQuantity: ' 8.5 ' },
    }, 'ELECTRICITY');
    expect(b.electricity).toEqual({ connectionNumber: '2_NO', usageType: 'HOURLY', usageQuantity: '8.5' });
    expect(b.paidTo).toBeUndefined();
    expect(b.paymentMode).toBeUndefined();
    expect(b.salary).toBeUndefined();
    expect('version' in b).toBe(false);
  });
  it('keeps paid to and payment mode for GENERAL, and never sends a voucher number', () => {
    const b = buildExpenseBody({ ...base, paidTo: ' Shop ', paymentMode: 'CASH' }, 'GENERAL');
    expect(b).toMatchObject({ paidTo: 'Shop', paymentMode: 'CASH' });
    expect('voucherNumber' in b).toBe(false);
  });
  it('omits a blank electricity price instead of sending an empty amount', () => {
    const b = buildExpenseBody({ ...base, amount: '  ', electricity: { connectionNumber: '1_NO', usageType: 'HOURLY', usageQuantity: '8' } }, 'ELECTRICITY');
    expect(b.amount).toBeUndefined();
    const priced = buildExpenseBody({ ...base, amount: '1200.50', electricity: { connectionNumber: '1_NO', usageType: 'HOURLY', usageQuantity: '8' } }, 'ELECTRICITY');
    expect(priced.amount).toBe('1200.50');
  });
  it('a new expense starts with payment mode CASH and today', () => {
    const d = newExpenseDefaults();
    expect(d.paymentMode).toBe('CASH');
    expect(d.expenseDate?.format('YYYY-MM-DD')).toBe(dayjs().format('YYYY-MM-DD'));
  });
});

describe('CancelExpenseModal', () => {
  it('requires a reason', async () => {
    const onConfirm = vi.fn();
    const user = userEvent.setup();
    render(<CancelExpenseModal open expenseNumber="EXP-1" onCancel={() => {}} onConfirm={onConfirm} />);
    await user.click(screen.getByRole('button', { name: 'Cancel expense' }));
    expect(await screen.findByText('Reason is required')).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
    await user.type(screen.getByRole('textbox'), '  duplicate entry ');
    await user.click(screen.getByRole('button', { name: 'Cancel expense' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('duplicate entry'));
  });
});
