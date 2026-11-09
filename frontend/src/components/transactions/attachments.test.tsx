import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import { attachmentService } from '../../services/attachmentService';
import { materialService } from '../../services/materialService';
import { TransactionForm } from './TransactionForm';
import type { ProductInfo } from './transactionLogic';

let perms = ['MATERIAL_IN.CREATE', 'MATERIAL_OUT.CREATE', 'MATERIAL_IN.UPDATE'];
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: (p: string) => perms.includes(p) }) }));
vi.mock('./ProductSelect', () => ({
  ProductSelect: ({ onSelect }: { onSelect: (p: ProductInfo) => void }) => (
    <button type="button" onClick={() => onSelect({ id: 1, code: 'CEM', name: 'Cement', unit: 'TON' })}>pick product</button>
  ),
}));
vi.mock('../../pages/payments/PartySelect', () => ({
  PartySelect: ({ onChange }: { onChange?: (v: number) => void }) => <button type="button" onClick={() => onChange?.(7)}>pick party</button>,
}));
vi.mock('../../services/materialService');
vi.mock('../../services/productService');
vi.mock('../../services/attachmentService');
vi.mock('../upload/imageDownscale', () => ({ downscaleImage: (f: File) => Promise.resolve(f) }));

const svc = vi.mocked(attachmentService);
const tx = { id: 42, transactionNumber: 'MIN-2026-00042' } as never;
const pdf = (n: string) => new File(['x'], n, { type: 'application/pdf' });
const png = (n: string) => new File(['x'], n, { type: 'image/png' });

function renderForm(props: Partial<Parameters<typeof TransactionForm>[0]> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><TransactionForm type="IN" open onClose={props.onClose ?? (() => {})} {...props} /></QueryClientProvider>);
}
async function fillAndPick(user: ReturnType<typeof userEvent.setup>, files: File[]) {
  await user.click(screen.getByText('pick party'));
  await user.click(screen.getByText('pick product'));
  await user.type(screen.getByLabelText('Quantity 1'), '2');
  await user.type(screen.getByLabelText('Rate 1'), '10');
  await user.upload(screen.getByTestId('file-input'), files);
}

beforeEach(() => {
  vi.resetAllMocks();
  perms = ['MATERIAL_IN.CREATE', 'MATERIAL_OUT.CREATE', 'MATERIAL_IN.UPDATE'];
  svc.list.mockResolvedValue([]);
  vi.mocked(materialService.create).mockResolvedValue(tx);
});

describe('Challan / photos section', () => {
  it('has a camera input (capture=environment) and a separate multi file input', () => {
    renderForm();
    const cam = screen.getByTestId('camera-input');
    expect(cam).toHaveAttribute('accept', 'image/*');
    expect(cam).toHaveAttribute('capture', 'environment');
    expect(cam).not.toHaveAttribute('multiple');
    const pick = screen.getByTestId('file-input');
    expect(pick).toHaveAttribute('accept', 'image/jpeg,image/png,application/pdf');
    expect(pick).toHaveAttribute('multiple');
    expect(pick).not.toHaveAttribute('capture');
    expect(screen.getByRole('button', { name: /Take photo/ })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Choose file/ })).toBeInTheDocument();
  });
  it('is hidden without CREATE permission', () => {
    perms = [];
    renderForm();
    expect(screen.queryByTestId('file-queue-section')).not.toBeInTheDocument();
  });
  it('queues files, rejects bad ones, and removes', async () => {
    const user = userEvent.setup({ applyAccept: false });
    renderForm();
    await user.upload(screen.getByTestId('file-input'), [pdf('a.pdf'), new File(['x'], 'v.webp', { type: 'image/webp' })]);
    expect(await screen.findByText(/not allowed/)).toBeInTheDocument();
    expect(screen.getAllByTestId('queued-file')).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Remove a.pdf' }));
    expect(screen.queryAllByTestId('queued-file')).toHaveLength(0);
  });
});

describe('save then upload', () => {
  it('creates the entry first, then uploads each file sequentially to the new id, and closes', async () => {
    const order: string[] = [];
    vi.mocked(materialService.create).mockImplementation(() => { order.push('create'); return Promise.resolve(tx); });
    svc.upload.mockImplementation((f) => { order.push(`upload:${f.name}`); return Promise.resolve({} as never); });
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderForm({ onClose });
    await fillAndPick(user, [pdf('a.pdf'), png('b.png')]);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(order).toEqual(['create', 'upload:a.pdf', 'upload:b.png']);
    expect(svc.upload.mock.calls.map((c) => [c[1], c[2]])).toEqual([['MATERIAL_TRANSACTION', 42], ['MATERIAL_TRANSACTION', 42]]);
  });

  it('keeps the form on upload failure, never re-creates the entry, retries only failed files', async () => {
    svc.upload
      .mockResolvedValueOnce({} as never) // a.pdf ok
      .mockRejectedValueOnce({ response: { status: 500, data: { message: 'S3 down' } } }); // b.png fails
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderForm({ onClose });
    await fillAndPick(user, [pdf('a.pdf'), png('b.png')]);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Entry MIN-2026-00042 saved, but 1 file(s) failed to upload')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();

    svc.upload.mockResolvedValueOnce({} as never);
    await user.click(screen.getByRole('button', { name: 'Retry failed files' }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(materialService.create).toHaveBeenCalledTimes(1);
    expect(svc.upload).toHaveBeenCalledTimes(3);
    expect(svc.upload.mock.calls.map((c) => c[0].name)).toEqual(['a.pdf', 'b.png', 'b.png']);
  });

  it('can be closed after a failure and does not call create again', async () => {
    svc.upload.mockRejectedValue({ response: { status: 500, data: { message: 'x' } } });
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderForm({ onClose });
    await fillAndPick(user, [pdf('a.pdf')]);
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await screen.findByText(/saved, but 1 file\(s\) failed/);
    await user.click(screen.getAllByRole('button', { name: 'Close' })[0]);
    expect(onClose).toHaveBeenCalled();
    expect(materialService.create).toHaveBeenCalledTimes(1);
  });

  it('closes immediately (no uploads) when nothing was queued', async () => {
    const onClose = vi.fn();
    const user = userEvent.setup();
    renderForm({ onClose });
    await user.click(screen.getByText('pick party'));
    await user.click(screen.getByText('pick product'));
    await user.type(screen.getByLabelText('Quantity 1'), '2');
    await user.type(screen.getByLabelText('Rate 1'), '10');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
    expect(svc.upload).not.toHaveBeenCalled();
  });
});

describe('edit mode', () => {
  it('lists already attached files with delete (UPDATE) and the add controls', async () => {
    vi.mocked(materialService.get).mockResolvedValue({
      id: 5, transactionNumber: 'MIN-1', transactionType: 'IN', transactionDate: '2026-11-01', partyId: 7, partyName: 'A', totalAmount: '0', status: 'POSTED', version: 1, createdAt: '',
      items: [{ id: 1, productId: 1, productCode: 'CEM', productName: 'Cement', unit: 'TON', quantity: '1', rate: '1', amount: '1' }],
    } as never);
    svc.list.mockResolvedValue([{ id: 9, ownerType: 'MATERIAL_TRANSACTION', ownerId: 5, fileName: 'old.pdf', contentType: 'application/pdf', fileSize: 2048, sha256: '', uploadedBy: 1, createdAt: '' }]);
    renderForm({ transactionId: 5 });
    const row = await screen.findByTestId('existing-file');
    expect(within(row).getByText('old.pdf')).toBeInTheDocument();
    expect(within(row).getByRole('button', { name: 'Delete old.pdf' })).toBeInTheDocument();
    expect(svc.list).toHaveBeenCalledWith('MATERIAL_TRANSACTION', 5);
    expect(screen.getByTestId('camera-input')).toBeInTheDocument();
  });
});

describe('form field order', () => {
  it('Remarks is the last field, after Items and Challan / photos', async () => {
    renderForm();
    const remarks = screen.getByLabelText('Remarks');
    const items = screen.getByRole('heading', { name: 'Items' });
    const photos = screen.getByTestId('file-queue-section');
    expect(items.compareDocumentPosition(remarks) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(photos.compareDocumentPosition(remarks) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const fields = Array.from(document.querySelectorAll('input, textarea')).filter((e) => (e as HTMLInputElement).type !== 'file' && (e as HTMLInputElement).type !== 'hidden');
    expect(fields[fields.length - 1]).toBe(remarks);
  });
});
