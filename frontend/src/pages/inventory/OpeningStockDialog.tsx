import { useState } from 'react';
import { Alert, Button, DatePicker, Input, Space, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FormDialog } from '../../components/common/FormDialog';
import { ProductSelect } from '../../components/transactions/ProductSelect';
import { QTY_INPUT, newRow, type ItemRow, type ProductInfo } from '../../components/transactions/transactionLogic';
import { inventoryService } from '../../services/inventoryService';
import { toIsoDate } from '../../utils/date';
import { parseDec } from '../../utils/decimalMath';
import { problemOf } from '../../utils/formErrors';

/** One-time opening balances; the server rejects products that already have movements. */
export function OpeningStockDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const qc = useQueryClient();
  const [date, setDate] = useState(dayjs());
  const [rows, setRows] = useState<ItemRow[]>([newRow()]);
  const [products, setProducts] = useState<Record<string, ProductInfo>>({});
  const [error, setError] = useState<string | null>(null);

  const save = useMutation({
    mutationFn: () => inventoryService.openingStock({
      date: toIsoDate(date), items: rows.map((r) => ({ productId: r.productId as number, quantity: r.quantity.trim().replace(/\.$/, '') })),
    }),
    onSuccess: () => { message.success('Opening stock saved'); void qc.invalidateQueries({ queryKey: ['inventory'] }); void qc.invalidateQueries({ queryKey: ['products'] }); onClose(); },
    onError: (e) => setError(problemOf(e)?.message ?? 'Could not save opening stock'),
  });
  const submit = () => {
    if (save.isPending) return;
    const bad = rows.some((r) => !r.productId || !(parseDec(r.quantity)?.n ?? 0n));
    if (bad) { setError('Pick a material and enter a quantity greater than 0 on every row.'); return; }
    const ids = rows.map((r) => r.productId);
    if (new Set(ids).size !== ids.length) { setError('Each material can appear only once.'); return; }
    setError(null); save.mutate();
  };
  const patch = (key: string, p: Partial<ItemRow>) => setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...p } : r)));

  return (
    <FormDialog open={open} title="Opening stock" onClose={onClose} onSubmit={submit} submitting={save.isPending}>
      <Alert type="info" showIcon style={{ marginBottom: 12 }}
        message="Works only for materials that have no stock movements yet. Use Material In / Out afterwards." />
      {error && <Alert type="error" showIcon style={{ marginBottom: 12 }} message={error} />}
      <Typography.Text>As-of date</Typography.Text>
      <DatePicker value={date} onChange={(d) => d && setDate(d)} allowClear={false} style={{ width: '100%', margin: '4px 0 12px' }} aria-label="As-of date" />
      {rows.map((r, i) => (
        <Space.Compact key={r.key} style={{ width: '100%', marginBottom: 8 }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            <ProductSelect value={r.productId} selected={r.productId ? products[r.key] : undefined}
              onSelect={(p) => { setProducts((m) => ({ ...m, [r.key]: p })); patch(r.key, { productId: p.id }); }} />
          </div>
          <Input style={{ width: 130 }} inputMode="decimal" placeholder="Quantity" value={r.quantity} addonAfter={products[r.key]?.unit}
            aria-label={`Opening quantity ${i + 1}`} onChange={(e) => QTY_INPUT.test(e.target.value) && patch(r.key, { quantity: e.target.value })} />
          <Button icon={<DeleteOutlined />} aria-label={`Remove row ${i + 1}`} disabled={rows.length === 1} onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))} />
        </Space.Compact>
      ))}
      <Button icon={<PlusOutlined />} onClick={() => setRows((rs) => [...rs, newRow()])}>Add material</Button>
    </FormDialog>
  );
}
