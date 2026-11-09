import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthContext, type AuthState } from '../../auth/AuthContext';
import { productService } from '../../services/productService';
import type { Product } from '../../types/masterData';
import { ProductsPage, isLowStock } from './ProductsPage';

const product = { id: 1, code: 'PRD-1', name: 'Cement', categoryId: null, categoryName: null, unit: 'KG', description: null,
  minimumStock: '100.000', availableStock: '99.500', status: 'ACTIVE' } as Product;

const auth = (perms: string[]): AuthState => ({
  user: { id: 1, fullName: 'T', permissions: perms, passwordChangeRequired: false },
  loading: false, login: async () => { throw new Error('x'); }, logout: async () => {},
  changePassword: async () => {}, flash: null, clearFlash: () => {},
});
const renderPage = (perms: string[]) => {
  vi.spyOn(productService, 'list').mockResolvedValue({ content: [product], page: 0, size: 20, totalElements: 1 });
  vi.spyOn(productService, 'categories').mockResolvedValue([]);
  return render(
    <AuthContext.Provider value={auth(perms)}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter><ProductsPage /></MemoryRouter>
      </QueryClientProvider>
    </AuthContext.Provider>);
};

describe('ProductsPage permission gating', () => {
  it('hides write buttons for view-only users', async () => {
    renderPage(['PRODUCT.VIEW']);
    expect(await screen.findByText('Cement')).toBeInTheDocument();
    expect(screen.queryByText('New material')).toBeNull();
    expect(screen.queryByText('Edit')).toBeNull();
    expect(screen.queryByText('Deactivate')).toBeNull();
  });

  it('shows write buttons when permitted', async () => {
    renderPage(['PRODUCT.VIEW', 'PRODUCT.CREATE', 'PRODUCT.UPDATE', 'PRODUCT.DELETE']);
    expect(await screen.findByText('New material')).toBeInTheDocument();
    expect(await screen.findByText('Edit')).toBeInTheDocument();
    expect(screen.getByText('Deactivate')).toBeInTheDocument();
  });

  it('flags low stock with string-safe compare', () => {
    expect(isLowStock(product)).toBe(true);
    expect(isLowStock({ ...product, availableStock: '100.000' })).toBe(false);
  });
});
