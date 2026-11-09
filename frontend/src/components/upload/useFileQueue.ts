import { useCallback, useEffect, useRef, useState } from 'react';
import { attachmentService } from '../../services/attachmentService';
import type { AttachmentOwnerType } from '../../types/attachment';
import { problemOf } from '../../utils/formErrors';
import { MAX_FILES, validateFile } from './attachmentRules';
import { downscaleImage } from './imageDownscale';

export type QueueStatus = 'queued' | 'uploading' | 'done' | 'failed';
export interface QueuedFile { id: number; file: File; previewUrl?: string; status: QueueStatus; progress: number; error?: string }

/** Camera originals may exceed 10 MB; images are downscaled before upload, so only a sanity cap applies to them. */
const MAX_RAW_IMAGE_BYTES = 40 * 1024 * 1024;

/** Client-side check for a file about to be queued; null when OK. */
export function validateQueued(file: File, existingCount: number): string | null {
  const isImage = file.type.startsWith('image/');
  if (isImage && file.size > MAX_RAW_IMAGE_BYTES) return `"${file.name}" is too large.`;
  // images are shrunk before upload, so their raw size is not held to the 10 MB limit here (re-checked after shrinking)
  return validateFile({ name: file.name, type: file.type, size: isImage ? (file.size ? 1 : 0) : file.size }, existingCount);
}

/** Client-side upload queue shared by the entry forms: validation, previews, sequential upload with per-file status. */
export function useFileQueue(ownerType: AttachmentOwnerType, existingCount = 0) {
  const [items, setItems] = useState<QueuedFile[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const ref = useRef<QueuedFile[]>([]);
  const seq = useRef(0);
  const set = (next: QueuedFile[]) => { ref.current = next; setItems(next); };
  const patch = (id: number, p: Partial<QueuedFile>) => set(ref.current.map((x) => (x.id === id ? { ...x, ...p } : x)));

  const add = useCallback((files: File[]) => {
    const accepted: QueuedFile[] = [];
    let problem: string | null = null;
    for (const f of files) {
      const p = validateQueued(f, existingCount + ref.current.length + accepted.length);
      if (p) { problem = p; continue; }
      const previewUrl = f.type.startsWith('image/') && typeof URL.createObjectURL === 'function' ? URL.createObjectURL(f) : undefined;
      accepted.push({ id: ++seq.current, file: f, previewUrl, status: 'queued', progress: 0 });
    }
    setError(problem);
    if (accepted.length) set([...ref.current, ...accepted]);
    return problem;
  }, [existingCount]);

  const remove = (id: number) => {
    const it = ref.current.find((x) => x.id === id);
    if (it?.previewUrl) URL.revokeObjectURL?.(it.previewUrl);
    set(ref.current.filter((x) => x.id !== id));
  };
  useEffect(() => () => { ref.current.forEach((x) => x.previewUrl && URL.revokeObjectURL?.(x.previewUrl)); }, []);

  /** Uploads queued + failed files one by one. Resolves with the number of files that failed. Never throws. */
  const uploadAll = async (ownerId: number): Promise<number> => {
    setUploading(true);
    const todo = ref.current.filter((x) => x.status === 'queued' || x.status === 'failed');
    let failed = 0;
    for (const it of todo) {
      patch(it.id, { status: 'uploading', progress: 0, error: undefined });
      try {
        const prepared = await downscaleImage(it.file);
        if (prepared.size > 10 * 1024 * 1024) throw new Error(`"${it.file.name}" is still larger than 10 MB.`);
        await attachmentService.upload(prepared, ownerType, ownerId, (p) => patch(it.id, { progress: p }));
        patch(it.id, { status: 'done', progress: 100 });
      } catch (e) {
        failed++;
        patch(it.id, { status: 'failed', error: problemOf(e)?.message ?? (e instanceof Error ? e.message : 'Upload failed') });
      }
    }
    setUploading(false);
    return failed;
  };

  return { items, error, setError, uploading, add, remove, uploadAll, pendingCount: items.filter((x) => x.status !== 'done').length, max: MAX_FILES };
}
