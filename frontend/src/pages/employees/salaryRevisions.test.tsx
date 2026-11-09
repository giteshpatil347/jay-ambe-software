import { render, screen, waitFor, within } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import userEvent from '@testing-library/user-event';
import dayjs from 'dayjs';
import { vi } from 'vitest';
import { employeeService } from '../../services/employeeService';
import type { Employee, SalaryRevision } from '../../types/expense';
import { SalaryHistoryDialog } from './SalaryHistoryDialog';
import { buildIncrementBody, groupRevisionsByYear } from './salaryRevisions';

vi.mock('../../services/employeeService');

const rev = (id: number, effectiveFrom: string, monthlySalary: string, remarks: string | null = null): SalaryRevision =>
  ({ id, effectiveFrom, year: Number(effectiveFrom.slice(0, 4)), monthlySalary, remarks });
const revisions = [rev(3, '2026-04-01', '26000.00', 'Annual'), rev(2, '2025-10-01', '24000.50'), rev(1, '2025-04-01', '25000.00')];
const emp: Employee = { id: 7, employeeCode: 'EMP-0007', employeeName: 'Asha', mobile: '9876543210', monthlySalary: '26000.00', joiningDate: '2025-04-01', status: 'ACTIVE', createdAt: '' };

describe('groupRevisionsByYear', () => {
  it('groups newest year first with the change versus the previous revision', () => {
    const g = groupRevisionsByYear([revisions[2], revisions[0], revisions[1]]);
    expect(g.map((x) => x.year)).toEqual([2026, 2025]);
    expect(g[0].rows.map((r) => [r.effectiveFrom, r.change])).toEqual([['2026-04-01', '+1999.50']]);
    expect(g[1].rows.map((r) => [r.effectiveFrom, r.change])).toEqual([['2025-10-01', '-999.50'], ['2025-04-01', null]]);
  });
});

describe('buildIncrementBody', () => {
  it('keeps the employee fields and adds salary, effective date and remarks', () => {
    expect(buildIncrementBody(emp, { monthlySalary: ' 28000 ', effectiveFrom: '2026-11-01', remarks: ' Diwali ' })).toEqual({
      employeeName: 'Asha', mobile: '9876543210', joiningDate: '2025-04-01', status: 'ACTIVE',
      monthlySalary: '28000', effectiveFrom: '2026-11-01', salaryRemarks: 'Diwali',
    });
  });
});

describe('SalaryHistoryDialog', () => {
  function renderDialog(canRevise: boolean) {
    vi.mocked(employeeService.salaryRevisions).mockResolvedValue(revisions);
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(<QueryClientProvider client={qc}><SalaryHistoryDialog employee={emp} canRevise={canRevise} onClose={() => {}} /></QueryClientProvider>);
  }

  it('lists revisions under year headings', async () => {
    renderDialog(true);
    const y2026 = await screen.findByRole('region', { name: 'Year 2026' });
    expect(within(y2026).getByRole('heading', { name: '2026' })).toBeInTheDocument();
    expect(within(y2026).getByText('Annual')).toBeInTheDocument();
    expect(within(y2026).getByText('+₹1,999.50')).toBeInTheDocument();
    const y2025 = screen.getByRole('region', { name: 'Year 2025' });
    expect(within(y2025).getAllByTestId('revision-row')).toHaveLength(2);
    expect(within(y2025).getByText('Initial')).toBeInTheDocument();
  });

  it('hides the increment action without EMPLOYEE.UPDATE', async () => {
    renderDialog(false);
    await screen.findByRole('region', { name: 'Year 2026' });
    expect(screen.queryByRole('button', { name: /Add increment/ })).toBeNull();
  });

  it('submits the new salary with its effective date and remarks', async () => {
    vi.mocked(employeeService.update).mockResolvedValue(emp);
    const user = userEvent.setup();
    renderDialog(true);
    await user.click(await screen.findByRole('button', { name: /Add increment/ }));
    await user.type(await screen.findByLabelText('New monthly salary'), '28000.00');
    await user.type(screen.getByLabelText('Remarks'), 'Raise');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(employeeService.update).toHaveBeenCalledWith(7, expect.objectContaining({
      monthlySalary: '28000.00', effectiveFrom: dayjs().format('YYYY-MM-DD'), salaryRemarks: 'Raise', status: 'ACTIVE', employeeName: 'Asha',
    })));
  });
});
