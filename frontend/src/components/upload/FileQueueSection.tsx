import { useRef } from 'react';
import { Alert, Button, Flex, Popconfirm, Progress, Typography, message } from 'antd';
import { CameraOutlined, CloseOutlined, DeleteOutlined, FilePdfOutlined, FolderOpenOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { attachmentService } from '../../services/attachmentService';
import type { AttachmentOwnerType } from '../../types/attachment';
import { problemOf } from '../../utils/formErrors';
import { MAX_FILES, humanSize } from './attachmentRules';
import type { QueuedFile, useFileQueue } from './useFileQueue';

interface Props {
  queue: ReturnType<typeof useFileQueue>;
  mobile: boolean;
  ownerType: AttachmentOwnerType;
  /** Set when editing: already attached files are listed (read-only, deletable with `canDelete`). */
  ownerId?: number;
  canDelete?: boolean;
  disabled?: boolean;
}

const btn = (mobile: boolean) => ({ minHeight: 48, width: mobile ? '100%' : undefined, flex: mobile ? undefined : '0 0 auto' }) as const;
const STATUS_TEXT: Record<QueuedFile['status'], string> = { queued: 'Ready', uploading: 'Uploading…', done: 'Uploaded', failed: 'Failed' };

/** "Challan / photos": camera + file-picker buttons, queued thumbnails with per-file status, existing attachments on edit. */
export function FileQueueSection({ queue, mobile, ownerType, ownerId, canDelete, disabled }: Props) {
  const qc = useQueryClient();
  const camera = useRef<HTMLInputElement>(null);
  const picker = useRef<HTMLInputElement>(null);
  const key = ['attachments', ownerType, ownerId];
  const list = useQuery({ queryKey: key, queryFn: () => attachmentService.list(ownerType, ownerId as number), enabled: ownerId !== undefined });
  const remove = useMutation({
    mutationFn: (id: number) => attachmentService.remove(id),
    onSuccess: () => { void message.success('Attachment deleted'); void qc.invalidateQueries({ queryKey: key }); },
    onError: (e) => { void message.error(problemOf(e)?.message ?? 'Could not delete the attachment'); },
  });
  const existing = list.data ?? [];
  const locked = !!disabled || queue.uploading;
  const onPick = (e: React.ChangeEvent<HTMLInputElement>) => {
    queue.add(Array.from(e.target.files ?? []));
    e.target.value = ''; // allow picking the same file again
  };
  const hidden = { display: 'none' } as const;

  return (
    <div data-testid="file-queue-section" style={{ marginTop: 16, minWidth: 0 }}>
      <Typography.Title level={5} style={{ marginBottom: 4 }}>Challan / photos</Typography.Title>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        JPG, PNG or PDF, up to 10 MB each, max {MAX_FILES} per entry. Uploaded when you save.
      </Typography.Text>
      {queue.error && <Alert type="error" showIcon closable onClose={() => queue.setError(null)} style={{ margin: '8px 0' }} message={queue.error} />}

      <div style={{ display: 'flex', flexDirection: mobile ? 'column' : 'row', gap: 8, margin: '8px 0' }}>
        <Button icon={<CameraOutlined />} style={btn(mobile)} disabled={locked} onClick={() => camera.current?.click()}>Take photo</Button>
        <Button icon={<FolderOpenOutlined />} style={btn(mobile)} disabled={locked} onClick={() => picker.current?.click()}>Choose file</Button>
        <input ref={camera} type="file" accept="image/*" capture="environment" style={hidden} onChange={onPick} data-testid="camera-input" aria-label="Take photo" tabIndex={-1} />
        <input ref={picker} type="file" accept="image/jpeg,image/png,application/pdf" multiple style={hidden} onChange={onPick} data-testid="file-input" aria-label="Choose file" tabIndex={-1} />
      </div>

      {existing.map((a) => (
        <Flex key={a.id} gap={8} align="center" style={{ padding: '6px 0', minWidth: 0 }} data-testid="existing-file">
          <span style={{ fontSize: 22 }}><FilePdfOutlined /></span>
          <div style={{ flex: 1, minWidth: 0 }}>
            <Typography.Text ellipsis style={{ display: 'block' }}>{a.fileName}</Typography.Text>
            <Typography.Text type="secondary" style={{ fontSize: 12 }}>{humanSize(a.fileSize)} · attached</Typography.Text>
          </div>
          {canDelete && (
            <Popconfirm title="Delete this attachment?" okText="Delete" okButtonProps={{ danger: true }} onConfirm={() => remove.mutate(a.id)}>
              <Button danger icon={<DeleteOutlined />} style={{ minHeight: 44, minWidth: 44 }} disabled={locked} aria-label={`Delete ${a.fileName}`} />
            </Popconfirm>
          )}
        </Flex>
      ))}

      {queue.items.map((q) => (
        <Flex key={q.id} gap={8} align="center" style={{ padding: '6px 0', minWidth: 0 }} data-testid="queued-file">
          {q.previewUrl
            ? <img src={q.previewUrl} alt="" style={{ width: 44, height: 44, objectFit: 'cover', borderRadius: 4, flex: '0 0 auto' }} />
            : <span style={{ fontSize: 32, color: '#cf1322', flex: '0 0 auto' }}><FilePdfOutlined /></span>}
          <div style={{ flex: 1, minWidth: 0 }}>
            <Typography.Text ellipsis style={{ display: 'block' }}>{q.file.name}</Typography.Text>
            <Typography.Text type={q.status === 'failed' ? 'danger' : 'secondary'} style={{ fontSize: 12 }}>
              {humanSize(q.file.size)} · {STATUS_TEXT[q.status]}{q.error ? ` — ${q.error}` : ''}
            </Typography.Text>
            {q.status === 'uploading' && <Progress percent={q.progress} size="small" showInfo={false} />}
          </div>
          {q.status !== 'done' && q.status !== 'uploading' && (
            <Button type="text" icon={<CloseOutlined />} style={{ minHeight: 44, minWidth: 44 }} disabled={locked && !!disabled} aria-label={`Remove ${q.file.name}`} onClick={() => queue.remove(q.id)} />
          )}
        </Flex>
      ))}
    </div>
  );
}
