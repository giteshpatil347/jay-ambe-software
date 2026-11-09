import { useState } from 'react';
import { Alert, Card, DatePicker, Flex, Grid, Input, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { auditService } from '../../services/auditService';
import type { AuditLog } from '../../types/payment';
import { formatDateTime, toIsoDate } from '../../utils/date';
import { AuditDiff } from './AuditDiff';

const SIZE = 25;
const who = (l: AuditLog) => l.userFullName ?? (l.userId ? `User #${l.userId}` : 'System');

export default function AuditLogsPage() {
  const [module, setModule] = useState('');
  const [action, setAction] = useState('');
  const [userId, setUserId] = useState('');
  const [recordId, setRecordId] = useState('');
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [page, setPage] = useState(0);
  const [openId, setOpenId] = useState<number | null>(null);
  const dModule = useDebouncedValue(module.trim().toUpperCase());
  const dAction = useDebouncedValue(action.trim().toUpperCase());
  const dRecord = useDebouncedValue(recordId.trim());
  const uid = /^\d+$/.test(userId.trim()) ? Number(userId.trim()) : undefined;

  const filters = {
    module: dModule || undefined, action: dAction || undefined, recordId: dRecord || undefined, userId: uid,
    from: range?.[0] ? toIsoDate(range[0]) : undefined, to: range?.[1] ? toIsoDate(range[1]) : undefined, page, size: SIZE,
  };
  const q = useQuery({ queryKey: ['audit-logs', filters], queryFn: () => auditService.list(filters), placeholderData: (p) => p });
  const f = (set: (v: string) => void) => (e: React.ChangeEvent<HTMLInputElement>) => { set(e.target.value); setPage(0); };
  const toggle = (id: number) => setOpenId((c) => (c === id ? null : id));

  return (
    <Flex vertical gap={12}>
      <Typography.Title level={4} style={{ margin: 0 }}>Audit logs</Typography.Title>
      <Flex gap={8} wrap>
        <Input allowClear placeholder="Module (e.g. PAYMENT)" value={module} onChange={f(setModule)} style={{ flex: '1 1 160px' }} aria-label="Module filter" />
        <Input allowClear placeholder="Action (e.g. LOGIN)" value={action} onChange={f(setAction)} style={{ flex: '1 1 160px' }} aria-label="Action filter" />
        <Input allowClear inputMode="numeric" placeholder="User ID" value={userId} onChange={f(setUserId)} style={{ flex: '1 1 120px' }} aria-label="User filter" />
        <Input allowClear placeholder="Record ID" value={recordId} onChange={f(setRecordId)} style={{ flex: '1 1 120px' }} aria-label="Record filter" />
        <DatePicker.RangePicker onChange={(v) => { setRange(v); setPage(0); }} style={{ flex: '1 1 260px' }} />
      </Flex>
      {q.isError && <Alert type="error" showIcon message="Could not load audit logs" />}
      <ResponsiveList<AuditLog>
        data={q.data?.content ?? []} loading={q.isFetching} rowKey={(r) => r.id}
        pagination={{ current: page + 1, pageSize: SIZE, total: q.data?.totalElements ?? 0, showSizeChanger: false, onChange: (p) => setPage(p - 1) }}
        columns={[
          { title: 'When', dataIndex: 'createdAt', render: formatDateTime },
          { title: 'User', key: 'user', render: (_: unknown, r) => who(r) },
          { title: 'Module', dataIndex: 'module', render: (v: string) => <Tag>{v}</Tag> },
          { title: 'Action', dataIndex: 'action' },
          { title: 'Record', dataIndex: 'recordId', render: (v: unknown) => (v === null || v === undefined ? '—' : String(v)) },
          { title: 'IP', dataIndex: 'ipAddress', render: (v: string | null) => v ?? '—' },
        ]}
        onRowClick={(r) => toggle(r.id)}
        renderCard={(r) => (
          <Flex vertical gap={4}>
            <Flex justify="space-between" gap={8}><Typography.Text strong>{r.action}</Typography.Text><Tag>{r.module}</Tag></Flex>
            <Typography.Text type="secondary">{who(r)} · {formatDateTime(r.createdAt)}</Typography.Text>
            {r.recordId !== null && r.recordId !== undefined && <Typography.Text type="secondary">Record {String(r.recordId)}</Typography.Text>}
            {openId === r.id && <div onClick={(e) => e.stopPropagation()}><AuditDiff oldValues={r.oldValues} newValues={r.newValues} /></div>}
          </Flex>
        )}
      />
      <ExpandedDesktop id={openId} logs={q.data?.content ?? []} />
    </Flex>
  );
}

/** Desktop detail panel under the table for the selected row (cards expand inline on mobile). */
function ExpandedDesktop({ id, logs }: { id: number | null; logs: AuditLog[] }) {
  const row = logs.find((l) => l.id === id);
  const screens = Grid.useBreakpoint();
  if (!row || !screens.md) return null;
  return (
    <Card size="small" title={`${row.action} · ${row.module} · ${formatDateTime(row.createdAt)}`}>
      <AuditDiff oldValues={row.oldValues} newValues={row.newValues} />
    </Card>
  );
}
