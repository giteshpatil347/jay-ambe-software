import { api } from '../api/client';
import type { Page, Product, ProductCategory, ProductInput, Status } from '../types/masterData';

export interface ProductQuery { q?: string; categoryId?: number; status?: Status; page: number; size: number }

export const productService = {
  list: (p: ProductQuery) => api.get<Page<Product>>('/products', { params: p }).then((r) => r.data),
  get: (id: number) => api.get<Product>(`/products/${id}`).then((r) => r.data),
  create: (b: ProductInput) => api.post<Product>('/products', b).then((r) => r.data),
  update: (id: number, b: Omit<ProductInput, 'productCode'>) => api.put<Product>(`/products/${id}`, b).then((r) => r.data),
  setActive: (id: number, active: boolean) =>
    api.post<Product>(`/products/${id}/${active ? 'activate' : 'deactivate'}`).then((r) => r.data),
  uploadImage: (id: number, file: File) => {
    const fd = new FormData();
    fd.append('file', file);
    return api.put<{ imageKey: string }>(`/products/${id}/image`, fd).then((r) => r.data);
  },
  /** Authenticated byte fetch (no URL token); 404 when the material has no image. The caller revokes the object URL. */
  imageBlob: (id: number) => api.get<Blob>(`/products/${id}/image`, { responseType: 'blob' }).then((r) => r.data),
  categories: () => api.get<ProductCategory[]>('/product-categories').then((r) => r.data),
  createCategory: (name: string) => api.post<ProductCategory>('/product-categories', { name }).then((r) => r.data),
};
