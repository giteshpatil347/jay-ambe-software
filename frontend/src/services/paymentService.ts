import { api } from '../api/client';
import type { Payment, PaymentBody, PaymentFilters, PaymentPage } from '../types/payment';

const clean = <T extends object>(p: T) =>
  Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== ''));

/** `page` is 0-based on the wire. */
export const paymentService = {
  list: (f: PaymentFilters) => api.get<PaymentPage>('/payments', { params: clean(f) }).then((r) => r.data),
  create: (b: PaymentBody) => api.post<Payment>('/payments', b).then((r) => r.data),
  cancel: (id: number, reason: string) => api.post<Payment>(`/payments/${id}/cancel`, { reason }).then((r) => r.data),
};
