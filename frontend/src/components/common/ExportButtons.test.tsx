import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import { reportService } from '../../services/reportService';
import { ExportButtons } from './ExportButtons';

vi.mock('../../services/reportService');
const LABELS = ['Number', 'Date', 'Amount'];

const setup = (onExport = vi.fn().mockResolvedValue('x'), kind: 'material-in' | null = 'material-in') => {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <ExportButtons columnsKind={kind ?? undefined} onExport={onExport} /></QueryClientProvider>);
  return onExport;
};
const openPicker = async () => { await userEvent.click(await screen.findByRole('button', { name: /Columns/ })); };

beforeEach(() => {
  vi.resetAllMocks();
  localStorage.clear();
  vi.mocked(reportService.exportColumns).mockResolvedValue(LABELS);
});

describe('ExportButtons column picker', () => {
  it('lists the labels from the endpoint for the given kind, all ticked by default', async () => {
    setup();
    await openPicker();
    expect(reportService.exportColumns).toHaveBeenCalledWith('material-in');
    for (const l of LABELS) expect(await screen.findByRole('checkbox', { name: l })).toBeChecked();
    expect(screen.getByRole('button', { name: /Columns \(3\/3\)/ })).toBeInTheDocument();
  });

  it('all selected sends no columns; a subset sends exactly the chosen labels in order', async () => {
    const onExport = setup();
    await userEvent.click(await screen.findByRole('button', { name: /CSV/ }));
    expect(onExport).toHaveBeenLastCalledWith('csv');
    await openPicker();
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Date' }));
    await userEvent.click(screen.getByRole('button', { name: /PDF/ }));
    await waitFor(() => expect(onExport).toHaveBeenLastCalledWith('pdf', ['Number', 'Amount']));
  });

  it('persists the choice per kind and blocks export when nothing is ticked', async () => {
    const onExport = setup();
    await openPicker();
    await userEvent.click(await screen.findByRole('button', { name: 'Select none' }));
    expect(screen.getByRole('button', { name: /PDF/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: /CSV/ })).toBeDisabled();
    expect(screen.getByText(/at least one column/i)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('checkbox', { name: 'Amount' }));
    expect(screen.getByRole('button', { name: /PDF/ })).toBeEnabled();
    expect(JSON.parse(localStorage.getItem('mb.exportColumns.material-in') as string)).toEqual(['Amount']);
    expect(localStorage.getItem('mb.exportColumns.expenses')).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: /CSV/ }));
    expect(onExport).toHaveBeenCalledWith('csv', ['Amount']);
  });

  it('restores the stored choice', async () => {
    localStorage.setItem('mb.exportColumns.material-in', JSON.stringify(['Date']));
    setup();
    expect(await screen.findByRole('button', { name: /Columns \(1\/3\)/ })).toBeInTheDocument();
  });

  it('works without storage and without a picker kind', async () => {
    const spy = vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => { throw new Error('blocked'); });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked'); });
    const onExport = setup();
    await openPicker();
    await userEvent.click(await screen.findByRole('checkbox', { name: 'Date' }));
    await userEvent.click(screen.getByRole('button', { name: /PDF/ }));
    expect(onExport).toHaveBeenCalledWith('pdf', ['Number', 'Amount']);
    spy.mockRestore();
    vi.restoreAllMocks();
  });

  it('without columnsKind there is no picker and nothing is requested', async () => {
    const onExport = setup(undefined, null);
    await userEvent.click(screen.getByRole('button', { name: /PDF/ }));
    expect(onExport).toHaveBeenCalledWith('pdf');
    expect(screen.queryByRole('button', { name: /Columns/ })).toBeNull();
    expect(reportService.exportColumns).not.toHaveBeenCalled();
  });
});
