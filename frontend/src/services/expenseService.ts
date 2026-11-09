import { api } from '../api/client';
import type { Expense, ExpenseBody, ExpenseCategory, ExpenseFilters, ExpensePage } from '../types/expense';

const clean = <T extends object>(p: T) =>
  Object.fromEntries(Object.entries(p).filter(([, v]) => v !== undefined && v !== null && v !== ''));

export const expenseService = {
  list: (f: ExpenseFilters) => api.get<ExpensePage>('/expenses', { params: clean({ ...f, page: f.page - 1 }) }).then((r) => r.data),
  get: (id: number) => api.get<Expense>(`/expenses/${id}`).then((r) => r.data),
  create: (b: ExpenseBody) => api.post<Expense>('/expenses', b).then((r) => r.data),
  update: (id: number, b: ExpenseBody) => api.put<Expense>(`/expenses/${id}`, b).then((r) => r.data),
  cancel: (id: number, reason: string) => api.post<Expense>(`/expenses/${id}/cancel`, { reason }).then((r) => r.data),
  categories: (status?: string) => api.get<ExpenseCategory[]>('/expense-categories', { params: clean({ status }) }).then((r) => r.data),
  createCategory: (b: { categoryCode: string; categoryName: string; categoryType: string }) =>
    api.post<ExpenseCategory>('/expense-categories', b).then((r) => r.data),
  updateCategory: (id: number, b: { categoryName: string; status: string }) =>
    api.put<ExpenseCategory>(`/expense-categories/${id}`, b).then((r) => r.data),
};
