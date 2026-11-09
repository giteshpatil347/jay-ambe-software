import { api } from '../api/client';
import type { Employee, EmployeeBody, PageEnvelope, SalaryHistoryItem, SalaryRevision } from '../types/expense';

export const employeeService = {
  /** Paged list (page is 1-based here). */
  list: (params: { status?: string; q?: string; page?: number; size?: number } = {}) =>
    api.get<PageEnvelope<Employee>>('/employees', { params: { ...params, page: (params.page ?? 1) - 1, size: params.size ?? 20 } }).then((r) => r.data),
  /** For selects: first 200 matching employees. */
  options: (status?: string) =>
    api.get<PageEnvelope<Employee>>('/employees', { params: { status, size: 200 } }).then((r) => r.data.content),
  create: (b: EmployeeBody) => api.post<Employee>('/employees', b).then((r) => r.data),
  update: (id: number, b: EmployeeBody) => api.put<Employee>(`/employees/${id}`, b).then((r) => r.data),
  /** Revisions newest first. */
  salaryRevisions: (id: number) => api.get<SalaryRevision[]>(`/employees/${id}/salary-revisions`).then((r) => r.data),
  /** Monthly salary in force on `date` (YYYY-MM-DD); null if none yet. */
  salaryAsOf: (id: number, date: string) =>
    api.get<{ monthlySalary: string | null }>(`/employees/${id}/salary-as-of`, { params: { date } }).then((r) => r.data.monthlySalary),
  /** Salary payments (expenses) made to the employee. */
  salaryHistory: (id: number, page: number, size: number) =>
    api.get<PageEnvelope<SalaryHistoryItem>>(`/employees/${id}/salary-history`, { params: { page: page - 1, size } })
      .then((r) => r.data),
};
