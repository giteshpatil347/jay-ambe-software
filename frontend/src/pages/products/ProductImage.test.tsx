import { render, screen, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { productService } from '../../services/productService';
import { ProductImage } from './ProductImage';

const renderImage = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <ProductImage productId={4} name="Cement" canUpdate />
  </QueryClientProvider>);

describe('ProductImage', () => {
  beforeEach(() => { vi.restoreAllMocks(); });

  it('shows the authenticated image as a blob URL and revokes it on unmount', async () => {
    vi.spyOn(productService, 'imageBlob').mockResolvedValue(new Blob(['x'], { type: 'image/png' }));
    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:img-1');
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => {});
    const { unmount } = renderImage();
    expect(await screen.findByRole('img', { name: 'Image of Cement' })).toHaveAttribute('src', 'blob:img-1');
    expect(create).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: /replace image/i })).toBeInTheDocument();
    unmount();
    expect(revoke).toHaveBeenCalledWith('blob:img-1');
  });

  it('shows "No image" on 404', async () => {
    vi.spyOn(productService, 'imageBlob').mockRejectedValue({ response: { status: 404, data: {} } });
    renderImage();
    await waitFor(() => expect(screen.getByText('No image')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /upload image/i })).toBeInTheDocument();
  });
});
