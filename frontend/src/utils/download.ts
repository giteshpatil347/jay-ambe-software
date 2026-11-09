import type { AxiosError } from 'axios';
import { api } from '../api/client';

/** Extracts the filename from a Content-Disposition header (RFC 5987 filename* first, then filename=). */
export function filenameFromDisposition(h: string | null | undefined, fallback: string): string {
  if (!h) return fallback;
  const star = /filename\*\s*=\s*(?:UTF-8|utf-8)''([^;]+)/i.exec(h);
  if (star) { try { return decodeURIComponent(star[1].trim()); } catch { /* fall through */ } }
  const plain = /filename\s*=\s*(?:"([^"]*)"|([^;]+))/i.exec(h);
  const name = (plain?.[1] ?? plain?.[2] ?? '').trim().replace(/[\\/]/g, '_');
  return name || fallback;
}

/** Blob error bodies arrive as Blob; parse the problem+json message out of them. */
export async function downloadErrorMessage(e: unknown): Promise<string> {
  const res = (e as AxiosError)?.response;
  let data: unknown = res?.data;
  if (typeof Blob !== 'undefined' && data instanceof Blob) {
    try { data = JSON.parse(await data.text()); } catch { data = undefined; }
  }
  const msg = (data as { message?: string; detail?: string } | undefined);
  const m = msg?.message ?? msg?.detail;
  if (m) return m;
  if (res?.status === 403) return 'You do not have permission to export this.';
  if (res?.status === 422) return 'The export was rejected. Narrow the filters and try again.';
  return 'Download failed. Please try again.';
}

/** Authenticated download: bearer token via the axios client, saved through a temporary object URL. Returns the filename. */
export async function downloadFile(url: string, params: Record<string, unknown>, fallbackName: string): Promise<string> {
  const res = await api.get<Blob>(url, { params, responseType: 'blob', paramsSerializer: { indexes: null } });
  const name = filenameFromDisposition(res.headers['content-disposition'] as string | undefined, fallbackName);
  const objectUrl = URL.createObjectURL(res.data);
  try {
    const a = document.createElement('a');
    a.href = objectUrl; a.download = name; a.rel = 'noopener'; a.style.display = 'none';
    document.body.appendChild(a); a.click(); a.remove();
  } finally {
    // revoke after the browser has started the save
    setTimeout(() => URL.revokeObjectURL(objectUrl), 0);
  }
  return name;
}
