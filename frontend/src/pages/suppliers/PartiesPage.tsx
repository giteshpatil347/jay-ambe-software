import { useState } from 'react';
import { Button, Flex, Input, Popconfirm, Select, Space, Tabs, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { usePermission } from '../../auth/usePermission';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { partyService } from '../../services/partyService';
import { applyApiError } from '../../utils/apiError';
import { formatCurrency } from '../../utils/currency';
import type { Party, PartyType, Status } from '../../types/masterData';
import { PartyForm } from './PartyForm';
import { PartyBalancesTab } from './PartyBalancesTab';
import { useNavigate } from 'react-router-dom';

const PAGE_SIZE = 20;
const TYPE_LABEL: Record<PartyType, string> = { SUPPLIER: 'Supplier', CLIENT: 'Client', BOTH: 'Both' };

export function PartiesPage() {
  const { can } = usePermission();
  const nav = useNavigate();
  const [view, setView] = useState<'parties' | 'balances'>('parties');
  const qc = useQueryClient();
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const [type, setType] = useState<PartyType | 'ALL'>('ALL');
  const [status, setStatus] = useState<Status>();
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<Party | 'new' | null>(null);

  const list = useQuery({
    queryKey: ['parties', { q, type, status, page }],
    queryFn: () => partyService.list({ q: q || undefined, type: type === 'ALL' ? undefined : type, status, page, size: PAGE_SIZE }),
  });
  const toggle = useMutation({
    mutationFn: (p: Party) => partyService.setActive(p.id, p.status !== 'ACTIVE'),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['parties'] }),
    onError: (e) => applyApiError(e),
  });

  const actions = (p: Party) => (
    <Space wrap onClick={(e) => e.stopPropagation()}>
      <Button onClick={() => nav(`/suppliers/${p.id}/ledger`)}>Ledger</Button>
      {can('PARTY.UPDATE') && <Button onClick={() => setEditing(p)}>Edit</Button>}
      {can('PARTY.UPDATE') && (
        <Popconfirm title={p.status === 'ACTIVE' ? 'Deactivate this party?' : 'Activate this party?'} onConfirm={() => toggle.mutate(p)}>
          <Button danger={p.status === 'ACTIVE'}>{p.status === 'ACTIVE' ? 'Deactivate' : 'Activate'}</Button>
        </Popconfirm>
      )}
    </Space>
  );
  const statusTag = (s: Status) => <Tag color={s === 'ACTIVE' ? 'green' : 'default'}>{s}</Tag>;

  return (
    <Flex vertical gap={8}>
      <Flex justify="space-between" align="center" wrap gap={8}>
        <Typography.Title level={3} style={{ margin: 0 }}>Suppliers &amp; Clients</Typography.Title>
        {can('PARTY.CREATE') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setEditing('new')}>New party</Button>}
      </Flex>
      <Tabs activeKey={view} onChange={(k) => setView(k as 'parties' | 'balances')}
        items={[{ key: 'parties', label: 'Parties' }, { key: 'balances', label: 'Balances' }]} />
      {view === 'balances' ? <PartyBalancesTab /> : <>
      <Tabs activeKey={type} onChange={(k) => { setType(k as PartyType | 'ALL'); setPage(0); }}
        items={[{ key: 'ALL', label: 'All' }, { key: 'SUPPLIER', label: 'Suppliers' }, { key: 'CLIENT', label: 'Clients' }]} />
      <Flex gap={8} wrap style={{ marginBottom: 8 }}>
        <Input.Search allowClear placeholder="Search name, code or mobile" value={search} style={{ flex: '1 1 220px', maxWidth: 360 }}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }} />
        <Select allowClear placeholder="Status" style={{ minWidth: 130, flex: '1 1 120px' }} value={status}
          onChange={(v) => { setStatus(v); setPage(0); }}
          options={[{ value: 'ACTIVE', label: 'Active' }, { value: 'INACTIVE', label: 'Inactive' }]} />
      </Flex>
      <ResponsiveList<Party>
        data={list.data?.content ?? []} rowKey={(p) => p.id} loading={list.isLoading}
        pagination={{ current: (list.data?.page ?? page) + 1, pageSize: PAGE_SIZE, total: list.data?.totalElements ?? 0,
          showSizeChanger: false, onChange: (p) => setPage(p - 1) }}
        columns={[
          { title: 'Code', dataIndex: 'code' },
          { title: 'Name', dataIndex: 'name' },
          { title: 'Type', dataIndex: 'type', render: (t: PartyType) => TYPE_LABEL[t] },
          { title: 'Mobile', dataIndex: 'mobile', render: (v: string | null) => v ?? '—' },
          { title: 'GSTIN', dataIndex: 'gstNumber', render: (v: string | null) => v ?? '—' },
          { title: 'Opening balance', dataIndex: 'openingBalance', align: 'right', render: (v: string | null) => formatCurrency(v) || '—' },
          { title: 'Status', dataIndex: 'status', render: statusTag },
          { title: '', key: 'actions', render: (_: unknown, p) => actions(p) },
        ]}
        renderCard={(p) => (
          <Flex vertical gap={6}>
            <Flex justify="space-between" gap={8}>
              <Typography.Text strong>{p.name}</Typography.Text>{statusTag(p.status)}
            </Flex>
            <Typography.Text type="secondary">{p.code} · {TYPE_LABEL[p.type]}</Typography.Text>
            {p.mobile && <a href={`tel:${p.mobile}`} onClick={(e) => e.stopPropagation()}>{p.mobile}</a>}
            {actions(p)}
          </Flex>
        )}
      />
      <PartyForm key={editing === 'new' ? 'new' : editing?.id ?? 'none'} open={editing !== null}
        party={editing === 'new' ? null : editing} onClose={() => setEditing(null)} />
      </>}
    </Flex>
  );
}
