import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Flex, Grid, Image, Popconfirm, Progress, Skeleton, Typography, Upload, message } from 'antd';
import { DeleteOutlined, FileOutlined, FilePdfOutlined, PictureOutlined, UploadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { attachmentService } from '../../services/attachmentService';
import type { Attachment, AttachmentOwnerType } from '../../types/attachment';
import { formatDate } from '../../utils/date';
import { problemOf } from '../../utils/formErrors';
import { MAX_FILES, humanSize, validateFile } from './attachmentRules';

export interface AttachmentPanelProps {
  ownerType: AttachmentOwnerType;
  ownerId: number;
  canUpload: boolean;
  canDelete: boolean;
  /** Read-only (e.g. owner CANCELLED): upload/delete are disabled and the reason is shown. */
  disabled?: boolean;
  disabledReason?: string;
}

const errMsg = (e: unknown, fallback: string) => problemOf(e)?.message ?? fallback;
const isImage = (a: Attachment) => a.contentType.startsWith('image/');

/** Fetches the bytes with the authenticated client once the element is near the viewport; revokes the object URL on unmount/replace. */
function useBlobUrl(id: number) {
  const [state, setState] = useState<{ url: string | null; failed: boolean }>({ url: null, failed: false });
  const [visible, setVisible] = useState(typeof IntersectionObserver === 'undefined');
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (visible || !ref.current) return;
    const io = new IntersectionObserver((entries) => { if (entries.some((e) => e.isIntersecting)) setVisible(true); }, { rootMargin: '200px' });
    io.observe(ref.current);
    return () => io.disconnect();
  }, [visible]);
  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    let created: string | null = null;
    attachmentService.content(id).then((blob) => {
      if (cancelled) return;
      created = URL.createObjectURL(blob);
      setState({ url: created, failed: false });
    }).catch(() => { if (!cancelled) setState({ url: null, failed: true }); });
    return () => { cancelled = true; if (created) URL.revokeObjectURL(created); };
  }, [id, visible]);
  return { ref, ...state };
}

interface ItemProps { a: Attachment; size: number; readOnly: boolean; canDelete: boolean; onDelete: (id: number) => void }

function DeleteButton({ a, readOnly, onDelete }: Pick<ItemProps, 'a' | 'readOnly' | 'onDelete'>) {
  return (
    <Popconfirm title="Delete this attachment?" okText="Delete" okButtonProps={{ danger: true }} disabled={readOnly} onConfirm={() => onDelete(a.id)}>
      <Button danger size="small" style={{ minHeight: 32, minWidth: 32 }} icon={<DeleteOutlined />} disabled={readOnly} aria-label={`Delete ${a.fileName}`} />
    </Popconfirm>
  );
}

function Caption({ a, canDelete, readOnly, onDelete }: Pick<ItemProps, 'a' | 'canDelete' | 'readOnly' | 'onDelete'>) {
  return (
    <Flex align="center" gap={4} style={{ marginTop: 4, minWidth: 0 }}>
      <div style={{ flex: 1, minWidth: 0 }}>
        <Typography.Text ellipsis={{ tooltip: a.fileName }} style={{ display: 'block', fontSize: 12 }}>{a.fileName}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 11 }}>{humanSize(a.fileSize)}</Typography.Text>
      </div>
      {canDelete && <DeleteButton a={a} readOnly={readOnly} onDelete={onDelete} />}
    </Flex>
  );
}

function ImageThumb(props: ItemProps) {
  const { a, size } = props;
  const { ref, url, failed } = useBlobUrl(a.id);
  const box = { width: size, height: size, borderRadius: 6, overflow: 'hidden' } as const;
  return (
    <div style={{ width: size }} data-testid="attachment-image">
      <div ref={ref} style={box}>
        {url ? (
          <Image src={url} alt={a.fileName} width={size} height={size} style={{ objectFit: 'cover', display: 'block' }} preview={{ mask: 'Preview' }} />
        ) : failed ? (
          <Flex vertical align="center" justify="center" gap={4} style={{ ...box, background: 'rgba(128,128,128,0.12)', textAlign: 'center', fontSize: 11 }} role="img" aria-label="Preview unavailable">
            <PictureOutlined style={{ fontSize: 22 }} />Preview unavailable
          </Flex>
        ) : <Skeleton.Node active style={{ width: size, height: size }}><PictureOutlined /></Skeleton.Node>}
      </div>
      <Caption {...props} />
    </div>
  );
}

function FileCard({ a, canDelete, readOnly, onDelete, onOpen, opening }: ItemProps & { onOpen: (a: Attachment) => void; opening: boolean }) {
  return (
    <Flex align="center" gap={10} style={{ border: '1px solid rgba(128,128,128,0.3)', borderRadius: 6, padding: 8, minWidth: 0 }} data-testid="attachment-file">
      <span style={{ fontSize: 28 }}>{a.contentType === 'application/pdf' ? <FilePdfOutlined style={{ color: '#cf1322' }} /> : <FileOutlined />}</span>
      <div style={{ flex: 1, minWidth: 0 }}>
        <Typography.Text ellipsis={{ tooltip: a.fileName }} style={{ display: 'block' }}>{a.fileName}</Typography.Text>
        <Typography.Text type="secondary" style={{ fontSize: 12 }}>{humanSize(a.fileSize)} · {formatDate(a.createdAt)}</Typography.Text>
      </div>
      <Button style={{ minHeight: 40 }} loading={opening} onClick={() => onOpen(a)} aria-label={`Open ${a.fileName}`}>Open</Button>
      {canDelete && <DeleteButton a={a} readOnly={readOnly} onDelete={onDelete} />}
    </Flex>
  );
}

export function AttachmentPanel({ ownerType, ownerId, canUpload, canDelete, disabled, disabledReason }: AttachmentPanelProps) {
  const qc = useQueryClient();
  const key = ['attachments', ownerType, ownerId];
  const list = useQuery({ queryKey: key, queryFn: () => attachmentService.list(ownerType, ownerId) });
  const [pending, setPending] = useState<File[]>([]);
  const [progress, setProgress] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [opening, setOpening] = useState<number | null>(null);
  const pendingRef = useRef<File[]>([]);
  const screens = Grid.useBreakpoint();
  const thumb = screens.md ? 112 : 96;
  const all = list.data ?? [];
  const images = all.filter(isImage);
  const files = all.filter((a) => !isImage(a));
  const existing = all.length;

  const uploadAll = useMutation({
    mutationFn: async (files: File[]) => {
      for (let i = 0; i < files.length; i++) {
        await attachmentService.upload(files[i], ownerType, ownerId, (p) => setProgress(Math.round(((i + p / 100) / files.length) * 100)));
      }
    },
    onSuccess: () => { pendingRef.current = []; setPending([]); void message.success('Uploaded'); },
    onError: (e) => { setError(errMsg(e, 'Upload failed')); },
    onSettled: () => { setProgress(null); void qc.invalidateQueries({ queryKey: key }); },
  });
  const remove = useMutation({
    mutationFn: (id: number) => attachmentService.remove(id),
    onSuccess: () => { void message.success('Attachment deleted'); void qc.invalidateQueries({ queryKey: key }); },
    onError: (e) => { void message.error(errMsg(e, 'Could not delete the attachment')); },
  });

  const open = async (a: Attachment) => {
    setOpening(a.id);
    // Open the tab synchronously inside the click so mobile popup blockers allow it; navigate once the bytes arrive.
    const w = window.open('', '_blank');
    try {
      const url = URL.createObjectURL(await attachmentService.content(a.id));
      if (w) { w.opener = null; w.location.href = url; } else window.open(url, '_blank', 'noopener,noreferrer');
      window.setTimeout(() => URL.revokeObjectURL(url), 5 * 60 * 1000);
    } catch (e) {
      w?.close();
      void message.error(errMsg(e, 'Could not open the file'));
    } finally { setOpening(null); }
  };

  const beforeUpload = (file: File) => {
    const problem = validateFile(file, existing + pendingRef.current.length);
    if (problem) { setError(problem); return Upload.LIST_IGNORE; }
    setError(null);
    pendingRef.current = [...pendingRef.current, file];
    setPending(pendingRef.current);
    return false; // not auto-uploaded
  };
  const dropPending = (f: File) => { pendingRef.current = pendingRef.current.filter((x) => x !== f); setPending(pendingRef.current); };

  const readOnly = !!disabled;
  const uploading = uploadAll.isPending;

  return (
    <div data-testid="attachment-panel">
      <Typography.Text strong>Attachments ({existing}/{MAX_FILES})</Typography.Text>
      {readOnly && <Alert type="info" showIcon style={{ margin: '8px 0' }} message={disabledReason ?? 'This record is cancelled; attachments are read-only.'} />}
      {error && <Alert type="error" showIcon closable onClose={() => setError(null)} style={{ margin: '8px 0' }} message={error} />}
      {list.isError && <Alert type="error" showIcon style={{ margin: '8px 0' }} message={errMsg(list.error, 'Could not load attachments')} />}

      {list.isLoading && <Skeleton active paragraph={{ rows: 1 }} />}
      {!list.isLoading && existing === 0 && !list.isError && <div style={{ margin: '8px 0' }}><Typography.Text type="secondary">No attachments</Typography.Text></div>}
      {images.length > 0 && (
        <Image.PreviewGroup>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, margin: '8px 0' }} data-testid="attachment-grid">
            {images.map((a) => <ImageThumb key={a.id} a={a} size={thumb} readOnly={readOnly} canDelete={canDelete} onDelete={remove.mutate} />)}
          </div>
        </Image.PreviewGroup>
      )}
      {files.length > 0 && (
        <Flex vertical gap={8} style={{ margin: '8px 0' }}>
          {files.map((a) => <FileCard key={a.id} a={a} size={thumb} readOnly={readOnly} canDelete={canDelete} onDelete={remove.mutate} onOpen={(x) => void open(x)} opening={opening === a.id} />)}
        </Flex>
      )}

      {canUpload && (
        <div style={{ marginTop: 8 }}>
          <Upload
            multiple accept=".jpg,.jpeg,.png,.pdf,image/jpeg,image/png,application/pdf" beforeUpload={beforeUpload}
            fileList={pending.map((f, i) => ({ uid: `${i}-${f.name}`, name: f.name, size: f.size, status: 'done' as const }))}
            onRemove={(f) => { const m = pending.find((p, i) => `${i}-${p.name}` === f.uid); if (m) dropPending(m); }}
            disabled={readOnly || uploading}
            // single-column layout; list items wrap on 360px
          >
            <Button icon={<UploadOutlined />} disabled={readOnly || uploading} style={{ minHeight: 48, width: '100%' }}>
              Choose file or take photo
            </Button>
          </Upload>
          {progress !== null && <Progress percent={progress} size="small" />}
          {pending.length > 0 && (
            <Button type="primary" style={{ minHeight: 48, marginTop: 8, width: '100%' }} loading={uploading} disabled={readOnly}
              onClick={() => { setError(null); uploadAll.mutate(pending); }}>
              Upload {pending.length} file{pending.length > 1 ? 's' : ''}
            </Button>
          )}
          <Typography.Paragraph type="secondary" style={{ marginTop: 4, fontSize: 12 }}>JPG, PNG or PDF, up to 10 MB each, max {MAX_FILES} files.</Typography.Paragraph>
        </div>
      )}
    </div>
  );
}
