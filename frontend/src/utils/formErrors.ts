import type { FormInstance } from 'antd';
import type { AxiosError } from 'axios';
import type { ProblemDetail } from '../api/types';

export const problemOf = (e: unknown) => (e as AxiosError<ProblemDetail>)?.response?.data;
export const statusOf = (e: unknown) => (e as AxiosError)?.response?.status;

/** Maps 422 fieldErrors onto form fields (dotted paths -> nested names; aliases for range pickers). Returns true if handled. */
export function applyFieldErrors(form: FormInstance, e: unknown, alias: Record<string, string> = {}): boolean {
  const fe = statusOf(e) === 422 ? problemOf(e)?.fieldErrors : undefined;
  if (!fe?.length) return false;
  form.setFields(fe.map((x) => ({ name: (alias[x.field] ?? x.field).split('.'), errors: [x.message] })));
  return true;
}
