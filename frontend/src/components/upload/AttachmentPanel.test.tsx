import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import { AttachmentPanel } from './AttachmentPanel';
import { attachmentService } from '../../services/attachmentService';
import { validateFile } from './attachmentRules';

vi.mock('../../services/attachmentService');
const svc = vi.mocked(attachmentService);
const att = { id: 7, ownerType: 'EXPENSE', ownerId: 3, fileName: 'challan.pdf', contentType: 'application/pdf', fileSize: 2048, sha256: 'x', uploadedBy: 1, createdAt: '2026-11-08T10:00:00Z' } as const;

function setup(props: Partial<React.ComponentProps<typeof AttachmentPanel>> = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}><AttachmentPanel ownerType="EXPENSE" ownerId={3} canUpload canDelete {...props} /></QueryClientProvider>);
}
beforeEach(() => {
  vi.restoreAllMocks(); vi.resetAllMocks();
  let u = 0;
  vi.spyOn(URL, 'createObjectURL').mockImplementation(() => `blob:mock-${++u}`);
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
  svc.list.mockResolvedValue([att]); });

describe('validateFile', () => {
  it('rejects oversize, wrong type, and >10 files', () => {
    expect(validateFile({ name: 'a.pdf', size: 11 * 1024 * 1024, type: 'application/pdf' }, 0)).toMatch(/10 MB/);
    expect(validateFile({ name: 'a.exe', size: 10, type: 'application/x-msdownload' }, 0)).toMatch(/not allowed/);
    expect(validateFile({ name: 'a.png', size: 10, type: 'image/png' }, 10)).toMatch(/Maximum 10/);
    expect(validateFile({ name: 'a.png', size: 10, type: 'image/png' }, 0)).toBeNull();
  });
});

describe('AttachmentPanel', () => {
  it('lists files with human size', async () => {
    setup();
    expect(await screen.findByText('challan.pdf')).toBeInTheDocument();
    expect(screen.getByText(/2\.0 KB/)).toBeInTheDocument();
  });

  it('blocks wrong type client-side without calling the API', async () => {
    const user = userEvent.setup({ applyAccept: false });
    const { container } = setup();
    await screen.findByText('challan.pdf');
    await user.upload(container.querySelector('input[type=file]') as HTMLInputElement, new File(['x'], 'run.exe', { type: 'application/x-msdownload' }));
    expect(await screen.findByText(/not allowed/)).toBeInTheDocument();
    expect(svc.upload).not.toHaveBeenCalled();
  });

  it('uploads multipart with owner fields after confirming selection', async () => {
    svc.upload.mockResolvedValue(att);
    const user = userEvent.setup();
    const { container } = setup();
    await screen.findByText('challan.pdf');
    const file = new File(['%PDF'], 'bill.pdf', { type: 'application/pdf' });
    await user.upload(container.querySelector('input[type=file]') as HTMLInputElement, file);
    expect(svc.upload).not.toHaveBeenCalled(); // not auto-upload
    await user.click(await screen.findByRole('button', { name: /Upload 1 file/ }));
    await waitFor(() => expect(svc.upload).toHaveBeenCalledWith(file, 'EXPENSE', 3, expect.any(Function)));
  });

  it('shows server 422 message on upload failure', async () => {
    svc.upload.mockRejectedValue({ response: { status: 422, data: { message: 'File content does not match type' } } });
    const user = userEvent.setup();
    const { container } = setup();
    await screen.findByText('challan.pdf');
    await user.upload(container.querySelector('input[type=file]') as HTMLInputElement, new File(['x'], 'b.png', { type: 'image/png' }));
    await user.click(await screen.findByRole('button', { name: /Upload 1 file/ }));
    expect(await screen.findByText('File content does not match type')).toBeInTheDocument();
  });

  it('delete asks for confirmation first', async () => {
    svc.remove.mockResolvedValue(undefined);
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole('button', { name: 'Delete challan.pdf' }));
    expect(svc.remove).not.toHaveBeenCalled();
    await user.click(await screen.findByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(svc.remove).toHaveBeenCalledWith(7));
  });

  it('is disabled with an explanation when the owner is cancelled', async () => {
    setup({ disabled: true });
    expect(await screen.findByText(/cancelled; attachments are read-only/)).toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Delete challan.pdf' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /Choose file/ })).toBeDisabled();
  });

  it('PDF shows a file card whose Open button fetches the bytes and opens a blob URL in a tab (opener cleared)', async () => {
    svc.content.mockResolvedValue(new Blob(['%PDF'], { type: 'application/pdf' }));
    const wins: { opener: unknown; location: { href: string }; close: ReturnType<typeof vi.fn> }[] = [];
    const open = vi.spyOn(window, 'open').mockImplementation(() => {
      const w = { opener: 'x', location: { href: '' }, close: vi.fn() }; wins.push(w); return w as unknown as Window;
    });
    const user = userEvent.setup();
    setup();
    expect(await screen.findByTestId('attachment-file')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Open challan.pdf' }));
    expect(open).toHaveBeenCalledWith('', '_blank'); // before the fetch resolves
    await waitFor(() => expect(wins[0].location.href).toBe('blob:mock-1'));
    expect(wins[0].opener).toBeNull();
    expect(svc.content).toHaveBeenCalledWith(7);
  });

  it('closes the blank tab and shows the error when the fetch fails', async () => {
    svc.content.mockRejectedValue({ response: { status: 404, data: { message: 'Attachment not found' } } });
    const w = { opener: 'x', location: { href: '' }, close: vi.fn() };
    vi.spyOn(window, 'open').mockReturnValue(w as unknown as Window);
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole('button', { name: 'Open challan.pdf' }));
    await waitFor(() => expect(w.close).toHaveBeenCalled());
    expect(await screen.findByText('Attachment not found')).toBeInTheDocument();
    expect(w.location.href).toBe('');
  });
});

describe('AttachmentPanel image previews', () => {
  const img = { ...att, id: 8, fileName: 'site.png', contentType: 'image/png', fileSize: 5000 };
  const img2 = { ...att, id: 9, fileName: 'second.png', contentType: 'image/png', fileSize: 6000 };
  const created: string[] = [];
  const revoked: string[] = [];
  beforeEach(() => {
    created.length = 0; revoked.length = 0;
    let n = 0;
    vi.spyOn(URL, 'createObjectURL').mockImplementation(() => { const u = `blob:img-${++n}`; created.push(u); return u; });
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation((u) => { revoked.push(u); });
    svc.content.mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
  });

  it('renders thumbnails from blob URLs with name and size, no PDF card for images', async () => {
    svc.list.mockResolvedValue([img, img2]);
    setup();
    const thumb = await screen.findByRole('img', { name: 'site.png' });
    expect(thumb).toHaveAttribute('src', 'blob:img-1');
    expect(screen.getByText('site.png')).toBeInTheDocument();
    expect(screen.getByText('4.9 KB')).toBeInTheDocument();
    expect(await screen.findByRole('img', { name: 'second.png' })).toHaveAttribute('src', 'blob:img-2');
    expect(screen.queryByTestId('attachment-file')).not.toBeInTheDocument();
  });

  it('clicking a thumbnail opens the full-size preview', async () => {
    svc.list.mockResolvedValue([img]);
    const user = userEvent.setup();
    setup();
    await user.click(await screen.findByRole('img', { name: 'site.png' }));
    expect(await screen.findByRole('dialog')).toBeInTheDocument();
    expect(document.querySelector('.ant-image-preview-img')).toHaveAttribute('src', 'blob:img-1');
  });

  it('revokes object URLs on unmount', async () => {
    svc.list.mockResolvedValue([img]);
    const { unmount } = setup();
    await screen.findByRole('img', { name: 'site.png' });
    expect(revoked).toEqual([]);
    unmount();
    expect(revoked).toEqual(['blob:img-1']);
  });

  it('shows a graceful fallback when the bytes cannot be loaded', async () => {
    svc.list.mockResolvedValue([img]);
    svc.content.mockRejectedValue(new Error('boom'));
    setup();
    expect(await screen.findByRole('img', { name: 'Preview unavailable' })).toBeInTheDocument();
    expect(screen.getByText('site.png')).toBeInTheDocument();
  });

  it('keeps delete gating on image thumbnails', async () => {
    svc.list.mockResolvedValue([img]);
    setup({ canDelete: false });
    await screen.findByRole('img', { name: 'site.png' });
    expect(screen.queryByRole('button', { name: 'Delete site.png' })).not.toBeInTheDocument();
  });
});
