import { useState } from 'react';
import { Alert, Button, DatePicker, Flex, Input, Modal, Select, Tag, Typography, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { usePermission } from '../../auth/usePermission';
import { AttachmentPanel } from '../../components/upload/AttachmentPanel';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { paymentService } from '../../services/paymentService';
import type { Payment, PaymentDirection, PaymentStatus } from '../../types/payment';
import { formatCurrency } from '../../utils/currency';
import { formatDate, toIsoDate } from '../../utils/date';
import { problemOf } from '../../utils/formErrors';
import { CancelPaymentModal } from './CancelPaymentModal';
import { PartySelect } from './PartySelect';
import { PaymentFormDialog } from './PaymentFormDialog';

const muted = { opacity: 0.5, textDecoration: 'line-through' } as const;
const DIR_LABEL: Record<PaymentDirection, string> = { PAID: 'We paid', RECEIVED: 'We received' };

export default function PaymentsPage() {
  const { can } = usePermission();
  const qc = useQueryClient();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [partyId, setPartyId] = useState<number>();
  const [direction, setDirection] = useState<PaymentDirection>();
  const [status, setStatus] = useState<PaymentStatus>();
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search.trim());
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [attachFor, setAttachFor] = useState<Payment | null>(null);
  const [cancelling, setCancelling] = useState<Payment | null>(null);
  const size = 20;

  const filters = {
    from: range?.[0] ? toIsoDate(range[0]) : undefined, to: range?.[1] ? toIsoDate(range[1]) : undefined,
    partyId, direction, status, q: q || undefined, page, size,
  };
  const list = useQuery({ queryKey: ['payments', filters], queryFn: () => paymentService.list(filters), placeholderData: (p) => p });
  const cancel = useMutation({
    mutationFn: (reason: string) => paymentService.cancel((cancelling as Payment).id, reason),
    onSuccess: () => {
      void message.success('Payment cancelled'); setCancelling(null);
      for (const k of ['payments', 'ledger', 'balances']) void qc.invalidateQueries({ queryKey: [k] });
    },
    onError: (e) => { void message.error(problemOf(e)?.message ?? 'Could not cancel the payment'); },
  });

  const reset = () => setPage(0);
  const dirTag = (d: PaymentDirection) => <Tag color={d === 'PAID' ? 'orange' : 'blue'}>{DIR_LABEL[d]}</Tag>;
  const statusTag = (r: Payment) => <Tag color={r.status === 'POSTED' ? 'green' : 'default'} title={r.cancellationReason ?? undefined}>{r.status}</Tag>;
  const actions = (r: Payment) => (
    <Flex gap={8} wrap>
      {can('PAYMENT.VIEW') && (
        <Button style={{ minHeight: 44 }} onClick={(e) => { e.stopPropagation(); setAttachFor(r); }}>Attachments</Button>
      )}
      {r.status === 'POSTED' && can('PAYMENT.CANCEL') && (
        <Button danger style={{ minHeight: 44 }} onClick={(e) => { e.stopPropagation(); setCancelling(r); }}>Cancel</Button>
      )}
    </Flex>
  );
  const st = (r: Payment) => (r.status === 'CANCELLED' ? muted : undefined);

  return (
    <div>
      <Flex justify="space-between" align="center" wrap gap={8} style={{ marginBottom: 12 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Payments</Typography.Title>
        {can('PAYMENT.CREATE') && (
          <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Record payment</Button>
        )}
      </Flex>

      <Flex gap={8} wrap style={{ marginBottom: 12 }}>
        <DatePicker.RangePicker onChange={(v) => { setRange(v); reset(); }} style={{ flex: '1 1 260px' }} />
        <PartySelect allowClear placeholder="Party" value={partyId} onChange={(v) => { setPartyId(v); reset(); }} style={{ flex: '1 1 200px', minWidth: 180 }} />
        <Select allowClear placeholder="Direction" aria-label="Direction filter" style={{ flex: '1 1 140px' }}
          options={[{ value: 'PAID', label: 'We paid' }, { value: 'RECEIVED', label: 'We received' }]}
          onChange={(v) => { setDirection(v); reset(); }} />
        <Select allowClear placeholder="Status" aria-label="Status filter" style={{ flex: '1 1 130px' }}
          options={[{ value: 'POSTED', label: 'Posted' }, { value: 'CANCELLED', label: 'Cancelled' }]}
          onChange={(v) => { setStatus(v); reset(); }} />
        <Input.Search allowClear placeholder="Search reference / remarks" value={search} style={{ flex: '1 1 240px' }}
          onChange={(e) => { setSearch(e.target.value); reset(); }} />
      </Flex>

      {list.isError && <Alert type="error" showIcon message="Could not load payments" style={{ marginBottom: 12 }} />}

      <ResponsiveList<Payment>
        data={list.data?.content ?? []} loading={list.isFetching} rowKey={(r) => r.id}
        pagination={{ current: page + 1, pageSize: size, total: list.data?.totalElements ?? 0, showSizeChanger: false, onChange: (p) => setPage(p - 1) }}
        columns={[
          { title: 'Date', dataIndex: 'paymentDate', render: (v: string, r) => <span style={st(r)}>{formatDate(v)}</span> },
          { title: 'Party', dataIndex: 'partyName', render: (v: string, r) => <span style={st(r)}>{v}</span> },
          { title: 'Direction', dataIndex: 'direction', render: dirTag },
          { title: 'Mode', dataIndex: 'paymentMode', render: (v: string | null, r) => <span style={st(r)}>{v ?? '—'}</span> },
          { title: 'Reference', dataIndex: 'referenceNumber', render: (v: string | null, r) => <span style={st(r)}>{v ?? '—'}</span> },
          { title: 'Amount', dataIndex: 'amount', align: 'right', render: (v: string, r) => <span style={st(r)}>{formatCurrency(v)}</span> },
          { title: 'Status', key: 'status', render: (_: unknown, r) => statusTag(r) },
          { title: '', key: 'a', render: (_: unknown, r) => actions(r) },
        ]}
        renderCard={(r) => (
          <div style={r.status === 'CANCELLED' ? { opacity: 0.55 } : undefined}>
            <Flex justify="space-between" gap={8}>
              <strong style={r.status === 'CANCELLED' ? { textDecoration: 'line-through' } : undefined}>{formatCurrency(r.amount)}</strong>
              {statusTag(r)}
            </Flex>
            <div>{r.partyName} {dirTag(r.direction)}</div>
            <Typography.Text type="secondary">{formatDate(r.paymentDate)}{r.paymentMode ? ` · ${r.paymentMode}` : ''}</Typography.Text>
            {r.cancellationReason && <div><Typography.Text type="secondary">Reason: {r.cancellationReason}</Typography.Text></div>}
            <div style={{ marginTop: 6 }}>{actions(r)}</div>
          </div>
        )}
      />

      <Modal open={!!attachFor} title={`Attachments - ${attachFor?.paymentNumber ?? ''}`} footer={null} onCancel={() => setAttachFor(null)} destroyOnHidden>
        {attachFor && (
          <AttachmentPanel ownerType="PARTY_PAYMENT" ownerId={attachFor.id}
            canUpload={can('PAYMENT.CREATE')} canDelete={can('PAYMENT.CREATE')} disabled={attachFor.status === 'CANCELLED'} />
        )}
      </Modal>
      {creating && <PaymentFormDialog open onClose={() => setCreating(false)} />}
      <CancelPaymentModal open={!!cancelling} paymentNumber={cancelling?.paymentNumber} loading={cancel.isPending}
        onCancel={() => setCancelling(null)} onConfirm={(reason) => cancel.mutate(reason)} />
    </div>
  );
}
