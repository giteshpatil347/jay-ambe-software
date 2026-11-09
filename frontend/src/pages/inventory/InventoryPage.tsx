import { useState } from 'react';
import { Alert, Button, DatePicker, Input, Select, Space, Switch, Tabs, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import type { Dayjs } from 'dayjs';
import { usePermission } from '../../auth/usePermission';
import { ResponsiveList } from '../../components/common/ResponsiveList';
import { ProductSelect } from '../../components/transactions/ProductSelect';
import type { ProductInfo } from '../../components/transactions/transactionLogic';
import { useDebouncedValue } from '../../hooks/useDebouncedValue';
import { inventoryService } from '../../services/inventoryService';
import { productService } from '../../services/productService';
import type { LedgerRow, StockRow } from '../../types/material';
import { formatDate, toIsoDate } from '../../utils/date';
import { formatQuantity } from '../../utils/quantity';
import { OpeningStockDialog } from './OpeningStockDialog';

const MOVEMENT_COLOR: Record<string, string> = { IN: 'green', OUT: 'orange' };

function StockTab() {
  const [search, setSearch] = useState('');
  const q = useDebouncedValue(search);
  const [categoryId, setCategoryId] = useState<number | undefined>();
  const [lowStockOnly, setLow] = useState(false);
  const [page, setPage] = useState(1);
  const filters = { q: q || undefined, categoryId, lowStockOnly: lowStockOnly || undefined, page, size: 20 };
  const stock = useQuery({ queryKey: ['inventory', 'stock', filters], queryFn: () => inventoryService.stock(filters), placeholderData: (p) => p });
  const cats = useQuery({ queryKey: ['product-categories'], queryFn: () => productService.categories() });
  const qty = (r: StockRow) => <span style={r.lowStock ? { color: 'var(--ant-color-error, #cf1322)', fontWeight: 600 } : undefined}>{formatQuantity(r.quantity)} {r.unit}</span>;
  const lowTag = (r: StockRow) => r.lowStock && <Tag color="red">Low stock</Tag>;

  return (
    <div>
      <Space wrap style={{ marginBottom: 12 }}>
        <Input.Search placeholder="Search code / name" allowClear value={search} onChange={(e) => { setSearch(e.target.value); setPage(1); }} style={{ minWidth: 220 }} />
        <Select allowClear placeholder="Category" style={{ minWidth: 160 }} aria-label="Category filter"
          options={(cats.data ?? []).map((c) => ({ value: c.id, label: c.name }))} onChange={(v) => { setCategoryId(v); setPage(1); }} />
        <label><Switch checked={lowStockOnly} onChange={(v) => { setLow(v); setPage(1); }} aria-label="Low stock only" /> Low stock only</label>
      </Space>
      {stock.isError && <Alert type="error" showIcon message="Could not load stock" style={{ marginBottom: 12 }} />}
      <ResponsiveList<StockRow>
        data={stock.data?.content ?? []} loading={stock.isFetching} rowKey={(r) => r.productId}
        pagination={{ current: page, pageSize: 20, total: stock.data?.totalElements ?? 0, showSizeChanger: false, onChange: setPage }}
        columns={[
          { title: 'Code', dataIndex: 'code' },
          { title: 'Material', dataIndex: 'name' },
          { title: 'Category', dataIndex: 'categoryName', render: (v: string | null) => v ?? '—' },
          { title: 'In stock', align: 'right', render: (_: unknown, r) => qty(r) },
          { title: 'Minimum', align: 'right', render: (_: unknown, r) => (r.minimumStock ? `${formatQuantity(r.minimumStock)} ${r.unit}` : '—') },
          { title: '', key: 'low', render: (_: unknown, r) => lowTag(r) },
        ]}
        renderCard={(r) => (
          <div style={r.lowStock ? { borderLeft: '4px solid var(--ant-color-error, #cf1322)', paddingLeft: 8 } : undefined}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}><strong>{r.name}</strong>{lowTag(r)}</div>
            <Typography.Text type="secondary">{r.code}{r.categoryName ? ` · ${r.categoryName}` : ''}</Typography.Text>
            <div style={{ fontSize: 18 }}>{qty(r)}</div>
            {r.minimumStock && <Typography.Text type="secondary">Minimum: {formatQuantity(r.minimumStock)} {r.unit}</Typography.Text>}
          </div>
        )}
      />
    </div>
  );
}

function LedgerTab() {
  const [product, setProduct] = useState<ProductInfo | undefined>();
  const [range, setRange] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [newestFirst, setNewest] = useState(false);
  const [page, setPage] = useState(1);
  const filters = {
    productId: product?.id as number, from: range?.[0] ? toIsoDate(range[0]) : undefined, to: range?.[1] ? toIsoDate(range[1]) : undefined,
    newestFirst: newestFirst || undefined, page, size: 50,
  };
  const ledger = useQuery({ queryKey: ['inventory', 'ledger', filters], queryFn: () => inventoryService.ledger(filters), enabled: !!product, placeholderData: (p) => p });
  const delta = (v: string) => <span style={{ color: v.startsWith('-') ? 'var(--ant-color-error, #cf1322)' : 'var(--ant-color-success, #389e0d)' }}>{v.startsWith('-') ? '' : '+'}{formatQuantity(v)}</span>;

  return (
    <div>
      <Space wrap style={{ marginBottom: 12 }}>
        <div style={{ minWidth: 260 }}><ProductSelect value={product?.id} selected={product} onSelect={(p) => { setProduct(p); setPage(1); }} /></div>
        <DatePicker.RangePicker onChange={(v) => { setRange(v); setPage(1); }} />
        <label><Switch checked={newestFirst} onChange={(v) => { setNewest(v); setPage(1); }} aria-label="Newest first" /> Newest first</label>
      </Space>
      {!product && <Alert type="info" showIcon message="Select a material to see its stock ledger." />}
      {ledger.isError && <Alert type="error" showIcon message="Could not load the ledger" style={{ marginBottom: 12 }} />}
      {product && ledger.data && (
        <div data-testid="opening-balance" style={{ padding: '8px 12px', border: '1px solid #f0f0f0', borderRadius: 8, marginBottom: 12, display: 'flex', justifyContent: 'space-between' }}>
          <span>Opening balance{range?.[0] ? ` (before ${formatDate(toIsoDate(range[0]))})` : ''}</span>
          <strong>{formatQuantity(ledger.data.openingBalance) || '0'} {product.unit}</strong>
        </div>
      )}
      {product && (
        <ResponsiveList<LedgerRow>
          data={ledger.data?.content ?? []} loading={ledger.isFetching} rowKey={(r) => `${r.date}-${r.transactionId}-${r.balance}-${r.delta}`}
          pagination={{ current: page, pageSize: 50, total: ledger.data?.totalElements ?? 0, showSizeChanger: false, onChange: setPage }}
          columns={[
            { title: 'Date', dataIndex: 'date', render: (v: string) => formatDate(v) },
            { title: 'Type', dataIndex: 'movementType', render: (v: string) => <Tag color={MOVEMENT_COLOR[v]}>{v}</Tag> },
            { title: 'Reference', dataIndex: 'transactionNumber', render: (v: string | null) => v ?? '—' },
            { title: 'Change', dataIndex: 'delta', align: 'right', render: (v: string) => delta(v) },
            { title: 'Balance', dataIndex: 'balance', align: 'right', render: (v: string) => `${formatQuantity(v)} ${product.unit}` },
            { title: 'By', dataIndex: 'createdBy', render: (v: string | null) => v ?? '—' },
          ]}
          renderCard={(r) => (
            <div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span>{formatDate(r.date)} <Tag color={MOVEMENT_COLOR[r.movementType]}>{r.movementType}</Tag></span>
                <strong>{delta(r.delta)}</strong>
              </div>
              <Typography.Text type="secondary">{r.transactionNumber ?? '—'}</Typography.Text>
              <div>Balance: {formatQuantity(r.balance)} {product.unit}</div>
            </div>
          )}
        />
      )}
    </div>
  );
}

export default function InventoryPage() {
  const { can } = usePermission();
  const [opening, setOpening] = useState(false);
  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
        <Typography.Title level={4} style={{ margin: 0 }}>Inventory</Typography.Title>
        {can('INVENTORY.ADJUST') && <Button onClick={() => setOpening(true)}>Opening stock</Button>}
      </div>
      <Tabs items={[
        { key: 'stock', label: 'Stock', children: <StockTab /> },
        { key: 'ledger', label: 'Ledger', children: <LedgerTab /> },
      ]} />
      {opening && <OpeningStockDialog open onClose={() => setOpening(false)} />}
    </div>
  );
}
