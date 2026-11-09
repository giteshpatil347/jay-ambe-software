import { useEffect, useState } from 'react';
import { useOpenCreateParam } from '../../hooks/useOpenCreateParam';
import { Alert, Button, DatePicker, Input, Select, Space, Typography, message } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { usePermission } from '../../auth/usePermission';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { expenseService } from '../../services/expenseService';
import type { Expense } from '../../types/expense';
import { formatDate, toIsoDate } from '../../utils/date';
import { problemOf } from '../../utils/formErrors';
import { CancelExpenseModal } from './CancelExpenseModal';
import { amountOf, detailsOf, headlineOf } from './expenseDisplay';
import { ExpenseFormModal } from './ExpenseFormModal';

const muted = { opacity: 0.5, textDecoration: 'line-through' } as const;

function useDebounced<T>(value: T, ms = 400) {
  const [v, setV] = useState(value);
  useEffect(() => { const t = setTimeout(() => setV(value), ms); return () => clearTimeout(t); }, [value, ms]);
  return v;
}

export default function ExpensesPage() {
  const { can } = usePermission();
  const qc = useQueryClient();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [search, setSearch] = useState('');
  const q = useDebounced(search);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [form, setForm] = useState<{ open: boolean; id?: number | null }>({ open: false });
  const [cancelling, setCancelling] = useState<Expense | null>(null);
  useOpenCreateParam(can('EXPENSE.CREATE'), () => setForm({ open: true, id: null }));

  const filters = {
    from: range?.[0] ? toIsoDate(range[0]) : undefined, to: range?.[1] ? toIsoDate(range[1]) : undefined,
    categoryId, q: q || undefined, page, size,
  };
  const list = useQuery({ queryKey: ['expenses', filters], queryFn: () => expenseService.list(filters), placeholderData: (p) => p });
  const cats = useQuery({ queryKey: ['expense-categories', 'all'], queryFn: () => expenseService.categories() });

  const cancel = useMutation({
    mutationFn: (reason: string) => expenseService.cancel((cancelling as Expense).id, reason),
    onSuccess: () => { message.success('Expense cancelled'); setCancelling(null); void qc.invalidateQueries({ queryKey: ['expenses'] }); },
    onError: (e) => { message.error(problemOf(e)?.message ?? 'Could not cancel the expense'); },
  });

  const reset = () => setPage(1);
  const actions = (r: Expense) => r.status === 'POSTED' && (
    <Space size="small" onClick={(e) => e.stopPropagation()}>
      {can('EXPENSE.UPDATE') && <Button size="small" onClick={() => setForm({ open: true, id: r.id })}>Edit</Button>}
      {can('EXPENSE.CANCEL') && <Button size="small" danger onClick={() => setCancelling(r)}>Cancel</Button>}
    </Space>
  );
  const rowStyle = (r: Expense) => (r.status === 'CANCELLED' ? muted : undefined);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Expenses</Typography.Title>
        {can('EXPENSE.CREATE') && <Button type="primary" icon={<PlusOutlined />} onClick={() => setForm({ open: true, id: null })}>New expense</Button>}
      </div>

      <Space wrap style={{ marginBottom: 12, width: '100%' }}>
        <DatePicker.RangePicker onChange={(v) => { setRange(v); reset(); }} />
        <Select
          allowClear placeholder="Category" style={{ minWidth: 160 }} aria-label="Category filter"
          options={(cats.data ?? []).map((c) => ({ value: c.id, label: c.categoryName }))}
          onChange={(v) => { setCategoryId(v); reset(); }}
        />
        <Input.Search placeholder="Search paid to" allowClear value={search} onChange={(e) => { setSearch(e.target.value); reset(); }} style={{ minWidth: 240 }} />
      </Space>

      {list.isError && <Alert type="error" showIcon message="Could not load expenses" style={{ marginBottom: 12 }} />}

      <ResponsiveList<Expense>
        data={list.data?.content ?? []} loading={list.isFetching} rowKey={(r) => r.id}
        pagination={{ current: page, pageSize: size, total: list.data?.totalElements ?? 0, showSizeChanger: false, onChange: (p, s) => { setPage(p); setSize(s); } }}
        columns={[
          { title: 'Date', dataIndex: 'expenseDate', render: (v: string, r) => <span style={rowStyle(r)}>{formatDate(v)}</span> },
          { title: 'Category', dataIndex: 'categoryName', render: (v: string, r) => <span style={rowStyle(r)}>{v}</span> },
          { title: 'Details', key: 'details', render: (_: unknown, r) => <span style={rowStyle(r)}>{detailsOf(r)}</span> },
          { title: 'Amount', key: 'amount', align: 'right', render: (_: unknown, r) => <span style={rowStyle(r)} title={r.cancellationReason ?? undefined}>{amountOf(r)}</span> },
          { title: '', key: 'a', render: (_: unknown, r) => actions(r) },
        ]}
        renderCard={(r) => (
          <div style={r.status === 'CANCELLED' ? { opacity: 0.55 } : undefined}>
            <strong style={r.status === 'CANCELLED' ? { textDecoration: 'line-through' } : undefined}>{headlineOf(r)}</strong>
            <div>{r.categoryName}{detailsOf(r) ? ` · ${detailsOf(r)}` : ''}</div>
            <Typography.Text type="secondary">{formatDate(r.expenseDate)}</Typography.Text>
            {r.cancellationReason && <div><Typography.Text type="secondary">Reason: {r.cancellationReason}</Typography.Text></div>}
            <div style={{ marginTop: 6 }}>{actions(r)}</div>
          </div>
        )}
      />

      {form.open && <ExpenseFormModal open expenseId={form.id} onClose={() => setForm({ open: false })} />}
      <CancelExpenseModal
        open={!!cancelling} expenseNumber={cancelling?.expenseNumber} loading={cancel.isPending}
        onCancel={() => setCancelling(null)} onConfirm={(reason) => cancel.mutate(reason)}
      />
    </div>
  );
}
