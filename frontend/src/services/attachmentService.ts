import { api } from '../api/client';
import type { Attachment, AttachmentOwnerType } from '../types/attachment';

export const attachmentService = {
  list: (ownerType: AttachmentOwnerType, ownerId: number) =>
    api.get<Attachment[]>('/attachments', { params: { ownerType, ownerId } }).then((r) => r.data),
  /** Multipart through the authenticated client; Content-Type (with boundary) is set by the browser/axios. */
  upload: (file: File, ownerType: AttachmentOwnerType, ownerId: number, onProgress?: (percent: number) => void) => {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('ownerType', ownerType);
    fd.append('ownerId', String(ownerId));
    return api.post<Attachment>('/attachments', fd, {
      onUploadProgress: (e) => { if (e.total) onProgress?.(Math.round((e.loaded / e.total) * 100)); },
    }).then((r) => r.data);
  },
  /** Authenticated byte fetch (no URL token): the caller turns the Blob into an object URL and must revoke it. */
  content: (id: number) => api.get<Blob>(`/attachments/${id}/content`, { responseType: 'blob' }).then((r) => r.data),
  remove: (id: number) => api.delete(`/attachments/${id}`).then(() => undefined),
};
