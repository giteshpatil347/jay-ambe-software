import { vi } from 'vitest';
import { api } from '../api/client';
import { downloadErrorMessage, downloadFile, filenameFromDisposition } from './download';

describe('filenameFromDisposition', () => {
  it('parses quoted, unquoted, RFC5987 and falls back', () => {
    expect(filenameFromDisposition('attachment; filename="material-in-20261108.pdf"', 'x')).toBe('material-in-20261108.pdf');
    expect(filenameFromDisposition('attachment; filename=a.csv', 'x')).toBe('a.csv');
    expect(filenameFromDisposition("attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf", 'x')).toBe('résumé.pdf');
    expect(filenameFromDisposition('attachment; filename="../evil.pdf"', 'x')).toBe('.._evil.pdf');
    expect(filenameFromDisposition(undefined, 'fallback.pdf')).toBe('fallback.pdf');
  });
});

describe('downloadFile', () => {
  afterEach(() => vi.restoreAllMocks());
  it('requests a blob via the api client, saves with the header filename and revokes the URL', async () => {
    vi.useFakeTimers();
    const get = vi.spyOn(api, 'get').mockResolvedValue({ data: new Blob(['x']), headers: { 'content-disposition': 'attachment; filename="r.csv"' } });
    const create = vi.fn(() => 'blob:abc'); const revoke = vi.fn();
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke });
    let saved = '';
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) { saved = `${this.download}|${this.href}`; });
    const name = await downloadFile('/reports/stock/export', { format: 'csv' }, 'f.csv');
    expect(get).toHaveBeenCalledWith('/reports/stock/export', { params: { format: 'csv' }, responseType: 'blob', paramsSerializer: { indexes: null } });
    expect(name).toBe('r.csv');
    expect(saved).toBe('r.csv|blob:abc');
    vi.runAllTimers();
    expect(revoke).toHaveBeenCalledWith('blob:abc');
    vi.useRealTimers();
  });
});

describe('downloadErrorMessage', () => {
  it('reads problem messages out of blob bodies and maps statuses', async () => {
    const blob = new Blob([JSON.stringify({ message: 'Too many rows; narrow the filters' })]);
    expect(await downloadErrorMessage({ response: { status: 422, data: blob } })).toMatch(/narrow the filters/);
    expect(await downloadErrorMessage({ response: { status: 403, data: new Blob(['']) } })).toMatch(/permission/);
    expect(await downloadErrorMessage({ response: { status: 422, data: new Blob(['not json']) } })).toMatch(/Narrow the filters/);
    expect(await downloadErrorMessage(new Error('net'))).toMatch(/failed/i);
  });
});
