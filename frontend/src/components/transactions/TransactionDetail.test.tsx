import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import { attachmentService } from '../../services/attachmentService';
import { reportService } from '../../services/reportService';
import { materialService } from '../../services/materialService';
import { TransactionDetail } from './TransactionDetail';

let perms: string[] = [];
vi.mock('../../auth/usePermission', () => ({ usePermission: () => ({ can: (p: string) => perms.includes(p) }) }));
vi.mock('../../services/materialService');
vi.mock('../../services/attachmentService');
vi.mock('../../services/reportService');

const base = {
  id: 5, transactionNumber: 'MOUT-2026-00005', transactionDate: '2026-11-08', status: 'POSTED', partyName: 'Acme', remarks: 'Handle with care',
  totalAmount: '100.00', items: [{ id: 1, productCode: 'CEM', productName: 'Cement', unit: 'TON', quantity: '2', rate: '50', amount: '100.00' }],
};
const att = { id: 11, ownerType: 'MATERIAL_TRANSACTION', ownerId: 5, fileName: 'photo.png', contentType: 'image/png', fileSize: 2048, sha256: 'x', uploadedBy: 1, createdAt: '2026-11-08T10:00:00Z' };

function setup(status = 'POSTED') {
  vi.mocked(materialService.get).mockResolvedValue({ ...base, status } as never);
  vi.mocked(attachmentService.list).mockResolvedValue([att] as never);
  vi.mocked(attachmentService.content).mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
  const onEdit = vi.fn(); const onClose = vi.fn();
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={qc}><TransactionDetail type="OUT" id={5} onClose={onClose} canEdit canCancel onEdit={onEdit} onCancel={vi.fn()} /></QueryClientProvider>);
  return { onEdit, onClose };
}

beforeEach(() => {
  vi.resetAllMocks();
  perms = ['MATERIAL_OUT.CREATE', 'MATERIAL_OUT.UPDATE'];
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:thumb');
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
});

describe('TransactionDetail dialog', () => {
  it('offers the challan column picker next to the PDF button', async () => {
    perms = ['REPORT.EXPORT', 'MATERIAL_OUT.EXPORT'];
    vi.mocked(reportService.exportColumns).mockResolvedValue(['#', 'Material']);
    setup();
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByRole('button', { name: /Columns/ })).toBeInTheDocument();
    expect(reportService.exportColumns).toHaveBeenCalledWith('challan');
  });

  it('is a centered modal dialog (not a drawer) with image thumbnail, Edit/Cancel in the footer and Remarks last', async () => {
    const { onEdit } = setup();
    const dialog = await screen.findByRole('dialog');
    expect(dialog.closest('.ant-drawer')).toBeNull();
    expect(document.querySelector('.ant-modal-centered')).not.toBeNull();
    expect(await within(dialog).findByRole('img', { name: 'photo.png' }, { timeout: 4000 })).toHaveAttribute('src', 'blob:thumb');
    const footer = dialog.querySelector('.ant-modal-footer') as HTMLElement;
    await userEvent.click(within(footer).getByRole('button', { name: 'Edit' }));
    expect(onEdit).toHaveBeenCalledWith(5);
    expect(within(footer).getByRole('button', { name: 'Cancel' })).toBeInTheDocument();

    const headings = within(dialog).getAllByRole('heading', { level: 5 }).map((h) => h.textContent);
    expect(headings).toEqual(['Items', 'Challan / photos', 'Remarks']);
    expect(within(dialog).getByText('Handle with care')).toBeInTheDocument();
  });

  it('cancelled entries show the banner, no footer actions, read-only attachments', async () => {
    setup('CANCELLED');
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText('Cancelled')).toBeInTheDocument();
    expect(dialog.querySelector('.ant-modal-footer')).toBeNull();
    expect(within(dialog).getByRole('button', { name: /Choose file/ })).toBeDisabled();
  });

  it('upload is hidden without MATERIAL_OUT CREATE/UPDATE and delete needs UPDATE', async () => {
    perms = ['MATERIAL_OUT.CREATE'];
    setup();
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByRole('img', { name: 'photo.png' }, { timeout: 4000 });
    expect(within(dialog).getByRole('button', { name: /Choose file/ })).toBeEnabled();
    expect(within(dialog).queryByRole('button', { name: 'Delete photo.png' })).toBeNull();
  });
});
