import { notification, type FormInstance } from 'antd';
import { isAxiosError } from 'axios';
import type { ProblemDetail } from '../api/types';

/** Maps a 422 `fieldErrors[]` onto form fields; everything else (403/409/...) becomes a notification. */
export function applyApiError(error: unknown, form?: FormInstance): void {
  if (!isAxiosError<ProblemDetail>(error) || !error.response) {
    notification.error({ message: 'Network error', description: 'Could not reach the server.' });
    return;
  }
  const { status, data } = error.response;
  const fieldErrors = data?.fieldErrors ?? [];
  if (status === 422 && fieldErrors.length > 0 && form) {
    form.setFields(fieldErrors.map((e) => ({ name: e.field, errors: [e.message] })));
    return;
  }
  const message = data?.message ?? 'Request failed';
  if (status === 409) notification.warning({ message: 'Conflict', description: message });
  else if (status === 403) notification.error({ message: 'Not allowed', description: message });
  else notification.error({ message: 'Error', description: message });
}
