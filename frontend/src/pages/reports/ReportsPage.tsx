import { useState } from 'react';
import { Alert, Flex, Select, Tabs, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import { usePermission } from '../../auth/usePermission';
import { ExportButtons } from '../../components/common/ExportButtons';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { PartySelect } from '../payments/PartySelect';
import { ProductSelect } from '../../components/transactions/ProductSelect';
import { reportService } from '../../services/reportService';
import { expenseService } from '../../services/expenseService';
import { productService } from '../../services/productService';
import type {
  ExpenseReportRow, GroupBy, MaterialGroupRow, MaterialRow, MaterialSummary, ReportFilters, ReportKind, ReportPage, StockRow,
} from '../../types/report';
import { formatCurrency } from '../../utils/currency';
import { formatDate } from '../../utils/date';
import { problemOf } from '../../utils/formErrors';
import { formatQuantity, formatQuantities } from '../../utils/quantity';
import { AsOfFilter, DateRangeFilter, LowStockSwitch, StatusFilter } from './ReportFiltersBar';
import { StatCard, UnitQuantities } from './reportView';

const SIZE = 50;
const EXPORT_PERM: Record<ReportKind, string> = {
  'material-in': 'MATERIAL_IN.EXPORT', 'material-out': 'MATERIAL_OUT.EXPORT', expenses: 'EXPENSE.EXPORT', stock: 'INVENTORY.EXPORT',
};
const TITLES: Record<ReportKind, string> = { 'material-in': 'Material IN', 'material-out': 'Material OUT', expenses: 'Expenses', stock: 'Stock' };

/** Both REPORT.EXPORT and the module's EXPORT permission are required. */
export function useCanExport(kind: ReportKind) {
  const { can } = usePermission();
  return can('REPORT.EXPORT') && can(EXPORT_PERM[kind]);
}

function useFilters(initial: ReportFilters = {}) {
  const [f, setF] = useState<ReportFilters>(initial);
  const [page, setPage] = useState(0);
  const patch = (p: Partial<ReportFilters>) => { setF((x) => ({ ...x, ...p })); setPage(0); };
  return { f, page, setPage, patch };
}

function Header({ kind, f, children }: { kind: ReportKind; f: ReportFilters; children: React.ReactNode }) {
  const canExport = useCanExport(kind);
  return (
    <Flex gap={8} wrap align="center" justify="space-between">
      <Flex gap={8} wrap align="center" style={{ minWidth: 0 }}>{children}</Flex>
      {canExport && <ExportButtons columnsKind={kind} onExport={(fmt, columns) => reportService.exportReport(kind, fmt, f, columns)} />}
    </Flex>
  );
}

const err = (e: unknown) => <Alert type="error" showIcon message={problemOf(e)?.message ?? 'Could not load the report'} />;
const pager = (page: number, total: number, set: (p: number) => void) =>
  ({ current: page + 1, pageSize: SIZE, total, showSizeChanger: false, onChange: (p: number) => set(p - 1), hideOnSinglePage: true });

function MaterialTab({ kind }: { kind: 'material-in' | 'material-out' }) {
  const { f, page, setPage, patch } = useFilters({ status: undefined, groupBy: undefined });
  const grouped = !!f.groupBy;
  const q = useQuery({
    queryKey: ['report', kind, f, page],
    queryFn: (): Promise<ReportPage<MaterialRow | MaterialGroupRow, MaterialSummary>> =>
      (grouped ? reportService.materialGrouped(kind, { ...f, page, size: SIZE }) : reportService.material(kind, { ...f, page, size: SIZE })),
    placeholderData: (p) => p, retry: false,
  });
  const s = q.data?.summary;
  const partyLabel = kind === 'material-in' ? 'Supplier' : 'Client';
  return (
    <Flex vertical gap={12}>
      <Header kind={kind} f={f}>
        <DateRangeFilter f={f} onChange={patch} />
        <PartySelect allowClear placeholder={partyLabel} value={f.partyId} onChange={(v) => patch({ partyId: v })} style={{ minWidth: 180 }} />
        <div style={{ minWidth: 200 }}>
          <ProductSelect value={f.productId} onSelect={(p) => patch({ productId: p.id })} onClear={() => patch({ productId: undefined })} />
        </div>
        {!grouped && <StatusFilter f={f} onChange={patch} />}
        <Select allowClear placeholder="Group by" aria-label="Group by" style={{ minWidth: 130 }} value={f.groupBy}
          options={[{ value: 'party', label: partyLabel }, { value: 'product', label: 'Material' }, { value: 'day', label: 'Day' }]}
          onChange={(v?: GroupBy) => patch({ groupBy: v })} />
      </Header>
      {q.isError && err(q.error)}
      <Flex gap={8} wrap>
        <StatCard label="Entries (posted)">{s?.transactionCount ?? 0}</StatCard>
        <StatCard label="Total amount (posted)">{formatCurrency(s?.totalAmount ?? '0')}</StatCard>
        <StatCard label="Quantity by unit (posted)"><UnitQuantities items={s?.quantityByUnit} /></StatCard>
      </Flex>
      {grouped ? (
        <ResponsiveList<MaterialGroupRow>
          data={(q.data?.content ?? []) as unknown as MaterialGroupRow[]} loading={q.isFetching} rowKey={(r) => r.key || '__none'}
          pagination={pager(page, q.data?.totalElements ?? 0, setPage)}
          columns={[
            { title: f.groupBy === 'day' ? 'Day' : f.groupBy === 'product' ? 'Material' : partyLabel, dataIndex: 'label', render: (v: string) => (f.groupBy === 'day' ? formatDate(v) : v || '(No party)') },
            { title: 'Entries', dataIndex: 'transactionCount', align: 'right' },
            { title: 'Quantity', dataIndex: 'quantities', render: (v) => <UnitQuantities items={v} /> },
            { title: 'Amount', dataIndex: 'amount', align: 'right', render: formatCurrency },
          ]}
          renderCard={(r) => (
            <Flex justify="space-between" gap={8}>
              <Flex vertical><Typography.Text strong>{f.groupBy === 'day' ? formatDate(r.label) : r.label || '(No party)'}</Typography.Text>
                <Typography.Text type="secondary">{r.transactionCount} entries</Typography.Text><UnitQuantities items={r.quantities} /></Flex>
              <strong>{formatCurrency(r.amount)}</strong>
            </Flex>
          )}
        />
      ) : (
        <ResponsiveList<MaterialRow>
          data={(q.data?.content ?? []) as unknown as MaterialRow[]} loading={q.isFetching} rowKey={(r) => r.id}
          pagination={pager(page, q.data?.totalElements ?? 0, setPage)}
          columns={[
            { title: 'Number', dataIndex: 'transactionNumber' },
            { title: 'Date', dataIndex: 'transactionDate', render: formatDate },
            { title: partyLabel, dataIndex: 'partyName', render: (v) => v ?? '—' },
            { title: 'Challan', dataIndex: 'challanNumber', render: (v) => v || '—' },
            { title: 'Items', dataIndex: 'itemCount', align: 'right' },
            { title: 'Quantity', dataIndex: 'quantities', render: (v) => <UnitQuantities items={v} /> },
            { title: 'Status', dataIndex: 'status' },
            { title: 'Amount', dataIndex: 'amount', align: 'right', render: formatCurrency },
          ]}
          renderCard={(r) => (
            <Flex justify="space-between" gap={8}>
              <Flex vertical><Typography.Text strong>{r.transactionNumber}</Typography.Text>
                <Typography.Text type="secondary">{formatDate(r.transactionDate)} · {r.partyName ?? '—'}</Typography.Text>
                <Typography.Text>{formatQuantities(r.quantities)}</Typography.Text>
                {r.status === 'CANCELLED' && <Typography.Text type="danger">Cancelled</Typography.Text>}</Flex>
              <strong>{formatCurrency(r.amount)}</strong>
            </Flex>
          )}
        />
      )}
    </Flex>
  );
}

function ExpensesTab() {
  const { f, page, setPage, patch } = useFilters();
  const cats = useQuery({ queryKey: ['expense-categories', 'all'], queryFn: () => expenseService.categories() });
  const q = useQuery({ queryKey: ['report', 'expenses', f, page], queryFn: () => reportService.expenses({ ...f, page, size: SIZE }), placeholderData: (p) => p, retry: false });
  const s = q.data?.summary;
  return (
    <Flex vertical gap={12}>
      <Header kind="expenses" f={f}>
        <DateRangeFilter f={f} onChange={patch} />
        <Select allowClear showSearch optionFilterProp="label" placeholder="Category" aria-label="Category filter" style={{ minWidth: 180 }} value={f.categoryId}
          options={(cats.data ?? []).map((c) => ({ value: c.id, label: c.categoryName }))} onChange={(v) => patch({ categoryId: v })} />
        <StatusFilter f={f} onChange={patch} />
      </Header>
      {q.isError && err(q.error)}
      <Flex gap={8} wrap>
        <StatCard label="Expenses (posted)">{s?.count ?? 0}</StatCard>
        <StatCard label="Total amount (posted)">{formatCurrency(s?.totalAmount ?? '0')}</StatCard>
      </Flex>
      {!!s?.byCategory?.length && (
        <Flex gap={8} wrap>
          {s.byCategory.map((c) => <StatCard key={c.categoryId} label={`${c.categoryName} (${c.count})`}>{formatCurrency(c.amount)}</StatCard>)}
        </Flex>
      )}
      <ResponsiveList<ExpenseReportRow>
        data={q.data?.content ?? []} loading={q.isFetching} rowKey={(r) => r.id} pagination={pager(page, q.data?.totalElements ?? 0, setPage)}
        columns={[
          { title: 'Number', dataIndex: 'expenseNumber' },
          { title: 'Date', dataIndex: 'expenseDate', render: formatDate },
          { title: 'Category', dataIndex: 'categoryName' },
          { title: 'Paid to', dataIndex: 'paidTo', render: (v) => v || '—' },
          { title: 'Mode', dataIndex: 'paymentMode', render: (v) => v || '—' },
          { title: 'Status', dataIndex: 'status' },
          { title: 'Amount', dataIndex: 'amount', align: 'right', render: formatCurrency },
        ]}
        renderCard={(r) => (
          <Flex justify="space-between" gap={8}>
            <Flex vertical><Typography.Text strong>{r.categoryName}</Typography.Text>
              <Typography.Text type="secondary">{r.expenseNumber} · {formatDate(r.expenseDate)}</Typography.Text>
              {r.status === 'CANCELLED' && <Typography.Text type="danger">Cancelled</Typography.Text>}</Flex>
            <strong>{formatCurrency(r.amount)}</strong>
          </Flex>
        )}
      />
    </Flex>
  );
}

function StockTab() {
  const { f, page, setPage, patch } = useFilters();
  const cats = useQuery({ queryKey: ['product-categories'], queryFn: () => productService.categories() });
  const q = useQuery({ queryKey: ['report', 'stock', f, page], queryFn: () => reportService.stock({ ...f, page, size: SIZE }), placeholderData: (p) => p, retry: false });
  const s = q.data?.summary;
  return (
    <Flex vertical gap={12}>
      <Header kind="stock" f={f}>
        <AsOfFilter f={f} onChange={patch} />
        <Select allowClear placeholder="Category" aria-label="Category filter" style={{ minWidth: 180 }} value={f.categoryId}
          options={(cats.data ?? []).map((c) => ({ value: c.id, label: c.name }))} onChange={(v) => patch({ categoryId: v })} />
        <LowStockSwitch f={f} onChange={patch} />
      </Header>
      {q.isError && err(q.error)}
      <Flex gap={8} wrap>
        <StatCard label="Materials">{s?.productCount ?? 0}</StatCard>
        <StatCard label="Low stock">{s?.lowStockCount ?? 0}</StatCard>
      </Flex>
      <ResponsiveList<StockRow>
        data={q.data?.content ?? []} loading={q.isFetching} rowKey={(r) => r.productId} pagination={pager(page, q.data?.totalElements ?? 0, setPage)}
        columns={[
          { title: 'Code', dataIndex: 'code' },
          { title: 'Material', dataIndex: 'name' },
          { title: 'Category', dataIndex: 'categoryName', render: (v) => v ?? '—' },
          { title: 'Quantity', dataIndex: 'quantity', align: 'right', render: (v: string, r) => `${formatQuantity(v)} ${r.unit}` },
          { title: 'Minimum', dataIndex: 'minimumStock', align: 'right', render: (v: string | null, r) => (v ? `${formatQuantity(v)} ${r.unit}` : '—') },
          { title: 'Low?', dataIndex: 'lowStock', render: (v: boolean) => (v ? <Typography.Text type="danger">Low</Typography.Text> : '') },
        ]}
        renderCard={(r) => (
          <Flex justify="space-between" gap={8}>
            <Flex vertical><Typography.Text strong>{r.name}</Typography.Text><Typography.Text type="secondary">{r.code}{r.categoryName ? ` · ${r.categoryName}` : ''}</Typography.Text>
              {r.lowStock && <Typography.Text type="danger">Low stock</Typography.Text>}</Flex>
            <strong>{formatQuantity(r.quantity)} {r.unit}</strong>
          </Flex>
        )}
      />
    </Flex>
  );
}

export default function ReportsPage() {
  const kinds = Object.keys(TITLES) as ReportKind[]; // the route is gated by REPORT.VIEW; the server is authoritative
  const body = (k: ReportKind) => (k === 'expenses' ? <ExpensesTab /> : k === 'stock' ? <StockTab /> : <MaterialTab kind={k} />);
  return (
    <Flex vertical gap={8}>
      <Typography.Title level={4} style={{ margin: 0 }}>Reports</Typography.Title>
      <Tabs destroyOnHidden items={kinds.map((k) => ({ key: k, label: TITLES[k], children: body(k) }))} />
    </Flex>
  );
}
