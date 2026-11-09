import { useMemo, useState } from 'react';
import { Button, Checkbox, Flex, Popover, Typography, message } from 'antd';
import { FileExcelOutlined, FilePdfOutlined, TableOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import { reportService } from '../../services/reportService';
import type { ExportColumnsKind } from '../../types/report';
import { downloadErrorMessage } from '../../utils/download';

interface Props {
  /** Performs the download; resolves with the saved filename. `columns` is set only for a strict subset of the columns. */
  onExport: (format: 'pdf' | 'csv', columns?: string[]) => Promise<unknown>;
  /** Hide pdf or csv where only one is offered. */
  formats?: ('pdf' | 'csv')[];
  size?: 'small' | 'middle';
  /** Shows the 'Columns' picker for this export. Without it every column is exported. */
  columnsKind?: ExportColumnsKind;
}

const storageKey = (kind: string) => `mb.exportColumns.${kind}`;

function readStored(kind: string): string[] | null {
  try {
    const raw = localStorage.getItem(storageKey(kind));
    const parsed: unknown = raw ? JSON.parse(raw) : null;
    return Array.isArray(parsed) && parsed.every((x) => typeof x === 'string') ? parsed : null;
  } catch { return null; }
}

function writeStored(kind: string, value: string[]) {
  try { localStorage.setItem(storageKey(kind), JSON.stringify(value)); } catch { /* storage unavailable: choice lasts for this view only */ }
}

/**
 * PDF / CSV buttons with a per-button loading state; failures surface the server message. With `columnsKind` a 'Columns' popover lets
 * the user choose which columns are exported (remembered per kind in localStorage). Caller handles permission gating.
 */
export function ExportButtons({ onExport, formats = ['pdf', 'csv'], size, columnsKind }: Props) {
  const [busy, setBusy] = useState<'pdf' | 'csv' | null>(null);
  const [stored, setStored] = useState<string[] | null>(() => (columnsKind ? readStored(columnsKind) : null));
  const labelsQ = useQuery({
    queryKey: ['export-columns', columnsKind], queryFn: () => reportService.exportColumns(columnsKind as ExportColumnsKind),
    enabled: !!columnsKind, staleTime: Infinity, retry: false,
  });
  const labels = useMemo(() => (Array.isArray(labelsQ.data) ? labelsQ.data : []), [labelsQ.data]);
  const selected = useMemo(() => {
    if (stored === null) return labels;
    const kept = labels.filter((l) => stored.includes(l));
    if (stored.length === 0) return []; // user ticked nothing this session (never persisted)
    return kept.length === 0 ? labels : kept; // stored labels no longer exist: fall back to all
  }, [labels, stored]);
  const showPicker = !!columnsKind && labels.length > 0;
  const none = showPicker && selected.length === 0;

  const choose = (next: string[]) => {
    const ordered = labels.filter((l) => next.includes(l));
    setStored(ordered);
    if (columnsKind && ordered.length > 0) writeStored(columnsKind, ordered);
  };
  const run = async (f: 'pdf' | 'csv') => {
    setBusy(f);
    try {
      await (showPicker && selected.length < labels.length ? onExport(f, selected) : onExport(f));
    } catch (e) { void message.error(await downloadErrorMessage(e)); } finally { setBusy(null); }
  };

  const picker = (
    <Flex vertical gap={4} style={{ width: 'min(280px, calc(100vw - 48px))' }}>
      <Flex gap={8}>
        <Button type="link" style={{ minHeight: 44, paddingInline: 4 }} onClick={() => choose(labels)}>Select all</Button>
        <Button type="link" style={{ minHeight: 44, paddingInline: 4 }} onClick={() => choose([])}>Select none</Button>
      </Flex>
      <Checkbox.Group value={selected} onChange={(v) => choose(v as string[])} style={{ display: 'flex', flexDirection: 'column', flexWrap: 'nowrap', maxHeight: '50vh', overflowY: 'auto' }}>
        {labels.map((l) => (
          <Checkbox key={l} value={l} style={{ minHeight: 44, alignItems: 'center', marginInlineStart: 0 }}>{l}</Checkbox>
        ))}
      </Checkbox.Group>
      {none && <Typography.Text type="danger">Keep at least one column ticked to export.</Typography.Text>}
    </Flex>
  );

  return (
    <Flex gap={8} wrap>
      {showPicker && (
        <Popover content={picker} trigger="click" placement="bottomRight" title="Columns to export">
          <Button size={size} style={{ minHeight: 44 }} icon={<TableOutlined />}>
            Columns ({selected.length}/{labels.length})
          </Button>
        </Popover>
      )}
      {formats.includes('pdf') && <Button size={size} style={{ minHeight: 44 }} icon={<FilePdfOutlined />} loading={busy === 'pdf'} disabled={busy !== null || none} onClick={() => void run('pdf')}>PDF</Button>}
      {formats.includes('csv') && <Button size={size} style={{ minHeight: 44 }} icon={<FileExcelOutlined />} loading={busy === 'csv'} disabled={busy !== null || none} onClick={() => void run('csv')}>CSV</Button>}
    </Flex>
  );
}
