import { useState } from 'react';
import { useOpenCreateParam } from '../../hooks/useOpenCreateParam';
import { Alert, Button, DatePicker, Input, Select, Space, Tag, Typography } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useQuery } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { usePermission } from '../../auth/usePermission';
import { ResponsiveList } from '../common/ResponsiveList';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { PartySelect } from '../../pages/payments/PartySelect';
import { materialService } from '../../services/materialService';
import type { TxListRow, TxType } from '../../types/material';
import { formatCurrency } from '../../utils/currency';
import { formatDate, toIsoDate } from '../../utils/date';
import { formatQuantities } from '../../utils/quantity';
import { CancelTransactionModal } from './CancelTransactionModal';
import { ProductSelect } from './ProductSelect';
import { TransactionDetail } from './TransactionDetail';
import { TransactionForm } from './TransactionForm';
import type { ProductInfo } from './transactionLogic';

const muted = { opacity: 0.5, textDecoration: 'line-through' } as const;
const CFG = { IN: { title: 'Material In', perm: 'MATERIAL_IN', party: 'Supplier', add: 'New Material In' }, OUT: { title: 'Material Out', perm: 'MATERIAL_OUT', party: 'Client', add: 'New Material Out' } } as const;

/** List + filters + sticky totals + detail drawer + create/edit/cancel for one transaction type. */
export default function TransactionListPage({ type }: { type: TxType }) {
  const c = CFG[type];
  const { can } = usePermission();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [partyId, setPartyId] = useState<number | undefined>();
  const [product, setProduct] = useState<ProductInfo | undefined>();
  const [status, setStatus] = useState<string | undefined>();
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search);
  const [page, setPage] = useState(1);
  const [form, setForm] = useState<{ open: boolean; id?: number | null }>({ open: false });
  const [detailId, setDetailId] = useState<number | null>(null);
  useOpenCreateParam(can(`${c.perm}.CREATE`), () => setForm({ open: true, id: null }));
  const [cancelling, setCancelling] = useState<{ id: number; number: string } | null>(null);

  const filters = {
    from: range?.[0] ? toIsoDate(range[0]) : undefined, to: range?.[1] ? toIsoDate(range[1]) : undefined,
    partyId, productId: product?.id, status, q: q || undefined, page, size: 20,
  };
  const list = useQuery({ queryKey: [type === 'IN' ? 'material-in' : 'material-out', filters], queryFn: () => materialService.list(type, filters), placeholderData: (p) => p });
  const reset = () => setPage(1);
  const canEdit = can(`${c.perm}.UPDATE`);
  const canCancel = can(`${c.perm}.CANCEL`);
  const st = (r: TxListRow) => (r.status === 'CANCELLED' ? muted : undefined);
  const items = (r: TxListRow) => `${r.productNames.join(', ')}${r.itemCount > r.productNames.length ? ` +${r.itemCount - r.productNames.length}` : ''}`;
  const actions = (r: TxListRow) => r.status === 'POSTED' && (canEdit || canCancel) && (
    <Space size="small" onClick={(e) => e.stopPropagation()}>
      {canEdit && <Button size="small" onClick={() => setForm({ open: true, id: r.id })}>Edit</Button>}
      {canCancel && <Button size="small" danger onClick={() => setCancelling({ id: r.id, number: r.transactionNumber })}>Cancel</Button>}
    </Space>
  );

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>{c.title}</Typography.Title>
        {can(`${c.perm}.CREATE`) && <Button type="primary" icon={<PlusOutlined />} onClick={() => setForm({ open: true, id: null })}>{c.add}</Button>}
      </div>

      <Space wrap style={{ marginBottom: 12, width: '100%' }}>
        <DatePicker.RangePicker onChange={(v) => { setRange(v); reset(); }} />
        {can('PARTY.VIEW') && (
          <PartySelect allowClear placeholder={c.party} type={type === 'IN' ? 'SUPPLIER' : 'CLIENT'} style={{ minWidth: 180 }} value={partyId} onChange={(v) => { setPartyId(v); reset(); }} />
        )}
        {can('PRODUCT.VIEW') && (
          <div style={{ minWidth: 220 }}>
            <ProductSelect value={product?.id} selected={product} onSelect={(p) => { setProduct(p); reset(); }} onClear={() => { setProduct(undefined); reset(); }} />
          </div>
        )}
        <Select
          allowClear placeholder="Status" style={{ minWidth: 130 }} aria-label="Status filter"
          options={[{ value: 'POSTED', label: 'Posted' }, { value: 'CANCELLED', label: 'Cancelled' }]}
          onChange={(v) => { setStatus(v); reset(); }}
        />
        <Input.Search placeholder="Search challan / vehicle" allowClear value={search} onChange={(e) => { setSearch(e.target.value); reset(); }} style={{ minWidth: 240 }} />
      </Space>

      {list.isError && <Alert type="error" showIcon message={`Could not load ${c.title.toLowerCase()}`} style={{ marginBottom: 12 }} />}

      <ResponsiveList<TxListRow>
        data={list.data?.content ?? []} loading={list.isFetching} rowKey={(r) => r.id} onRowClick={(r) => setDetailId(r.id)}
        pagination={{ current: page, pageSize: 20, total: list.data?.totalElements ?? 0, showSizeChanger: false, onChange: setPage }}
        columns={[
          { title: 'Date', dataIndex: 'transactionDate', render: (v: string, r) => <span style={st(r)}>{formatDate(v)}</span> },
          { title: c.party, dataIndex: 'partyName', render: (v: string | null, r) => <span style={st(r)}>{v ?? '—'}</span> },
          { title: 'Items', key: 'items', render: (_: unknown, r) => <span style={st(r)}>{items(r)}</span> },
          { title: 'Quantity', key: 'qty', align: 'right', render: (_: unknown, r) => <span style={st(r)}>{formatQuantities(r.quantities)}</span> },
          { title: 'Amount', dataIndex: 'totalAmount', align: 'right', render: (v: string, r) => <span style={st(r)}>{formatCurrency(v)}</span> },
          { title: 'Status', dataIndex: 'status', render: (v: string) => <Tag color={v === 'POSTED' ? 'green' : 'default'}>{v}</Tag> },
          { title: '', key: 'a', render: (_: unknown, r) => actions(r) },
        ]}
        renderCard={(r) => (
          <div style={r.status === 'CANCELLED' ? { opacity: 0.55 } : undefined}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <strong style={r.status === 'CANCELLED' ? { textDecoration: 'line-through' } : undefined}>{formatCurrency(r.totalAmount)}</strong>
              <Tag color={r.status === 'POSTED' ? 'green' : 'default'}>{r.status}</Tag>
            </div>
            <div>{r.partyName ?? '—'}</div>
            <div><Typography.Text type="secondary">{items(r)}</Typography.Text></div>
            <div><strong>{formatQuantities(r.quantities)}</strong></div>
            <Typography.Text type="secondary">{formatDate(r.transactionDate)}</Typography.Text>
            <div style={{ marginTop: 6 }}>{actions(r)}</div>
          </div>
        )}
      />

      <TransactionDetail
        type={type} id={detailId} onClose={() => setDetailId(null)} canEdit={canEdit} canCancel={canCancel}
        onEdit={(id) => { setDetailId(null); setForm({ open: true, id }); }}
        onCancel={(id, number) => setCancelling({ id, number })}
      />
      <TransactionForm type={type} open={form.open} transactionId={form.id} onClose={() => setForm({ open: false })} />
      <CancelTransactionModal
        type={type} open={!!cancelling} id={cancelling?.id} number={cancelling?.number} onClose={() => setCancelling(null)}
        onDone={() => { setCancelling(null); setDetailId(null); }}
      />
    </div>
  );
}
