import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { usePermission } from '../../auth/usePermission';
import { FileQueueSection } from '../upload/FileQueueSection';
import { useFileQueue } from '../upload/useFileQueue';
import { Alert, Button, Card, DatePicker, Drawer, Form, Grid, Input, Modal, Select, Space, Spin, Tag, Typography, message } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { PartySelect } from '../../pages/payments/PartySelect';
import { attachmentService } from '../../services/attachmentService';
import { materialService } from '../../services/materialService';
import { productService } from '../../services/productService';
import type { TxDetail, TxType } from '../../types/material';
import { formatCurrency } from '../../utils/currency';
import { problemOf, statusOf } from '../../utils/formErrors';
import { formatQuantity } from '../../utils/quantity';
import { ProductSelect } from './ProductSelect';
import {
  QTY_INPUT, RATE_INPUT, buildPayload, convertQuantity, defaultEntryUnit, entryUnits, storedRows, grandTotal, lineAmount, mapStockErrors, newRow, originalQuantities, productsFromDetail,
  rowsFromDetail, stockShortfalls, unitTotals, validateForm, type HeaderValues, type ItemRow, type ProductInfo,
} from './transactionLogic';

const HEADER_FIELDS = ['transactionDate', 'partyId', 'challanNumber', 'vehicleNumber', 'driverName', 'remarks'];
const HEADER_KEY: Record<string, string> = { transactionDate: 'date' };

interface Props {
  type: TxType; open: boolean; /** Edit when set. */ transactionId?: number | null; onClose: () => void;
  /** Reserved for the later attachments UI (T10); intentionally unused. */
  attachmentsSlot?: ReactNode;
}

const LABEL = { IN: { party: 'Supplier', noun: 'Material In' }, OUT: { party: 'Client / Recipient', noun: 'Material Out' } } as const;

/** Reusable Material IN/OUT entry form; configured only by `type`. */
export function TransactionForm({ type, open, transactionId, onClose }: Props) {
  const editing = !!transactionId;
  const existing = useQuery({
    queryKey: ['material-tx', type, transactionId], queryFn: () => materialService.get(type, transactionId as number),
    enabled: open && editing, gcTime: 0,
  });
  const screens = Grid.useBreakpoint();
  const mobile = !screens.md;
  const title = `${editing ? 'Edit' : 'New'} ${LABEL[type].noun}`;

  if (!open) return null;
  const body = editing && existing.isLoading
    ? { content: <div style={{ textAlign: 'center', padding: 48 }}><Spin /></div>, footer: null }
    : editing && !existing.data
      ? { content: <Alert type="error" showIcon message="Could not load this entry" />, footer: null }
      : null;
  if (body) {
    return mobile
      ? <Drawer open title={title} width="100%" onClose={onClose} destroyOnHidden>{body.content}</Drawer>
      : <Modal open title={title} width={960} onCancel={onClose} footer={null} destroyOnHidden>{body.content}</Modal>;
  }
  return (
    <FormBody
      key={`${existing.data?.id ?? 'new'}-${existing.dataUpdatedAt}`} type={type} initial={existing.data} mobile={mobile} title={title}
      onClose={onClose} onReload={() => { void existing.refetch(); }}
    />
  );
}

interface BodyProps { type: TxType; initial?: TxDetail; mobile: boolean; title: string; onClose: () => void; onReload: () => void }

function FormBody({ type, initial, mobile, title, onClose, onReload }: BodyProps) {
  const qc = useQueryClient();
  const out = type === 'OUT';
  const [header, setHeader] = useState<HeaderValues>(() => ({
    date: initial ? dayjs(initial.transactionDate) : dayjs(), partyId: initial?.partyId ?? undefined,
    challanNumber: initial?.challanNumber ?? '', vehicleNumber: initial?.vehicleNumber ?? '', driverName: initial?.driverName ?? '',
    remarks: initial?.remarks ?? '',
  }));
  const [rows, setRows] = useState<ItemRow[]>(() => (initial ? rowsFromDetail(initial) : [newRow()]));
  const [products, setProducts] = useState<Record<number, ProductInfo>>(() => (initial ? productsFromDetail(initial) : {}));
  const [stock, setStock] = useState<Record<number, string | undefined>>({});
  const [headerErr, setHeaderErr] = useState<Record<string, string>>({});
  const [rowErr, setRowErr] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [conflict, setConflict] = useState<string | null>(null);
  const submitting = useRef(false);
  const { can } = usePermission();
  const perm = out ? 'MATERIAL_OUT' : 'MATERIAL_IN';
  const canAttach = can(`${perm}.${initial ? 'UPDATE' : 'CREATE'}`);
  const existingFiles = useQuery({
    queryKey: ['attachments', 'MATERIAL_TRANSACTION', initial?.id], enabled: !!initial && canAttach,
    queryFn: () => attachmentService.list('MATERIAL_TRANSACTION', initial?.id as number),
  });
  const queue = useFileQueue('MATERIAL_TRANSACTION', existingFiles.data?.length ?? 0);
  /** Set once the entry is saved: from then on only uploads (never another create/update) can happen. */
  const [saved, setSaved] = useState<{ id: number; number: string } | null>(null);
  const [failedCount, setFailedCount] = useState(0);
  const originals = useMemo(() => originalQuantities(initial), [initial]);

  const loadStock = (id: number) => {
    void productService.get(id).then((p) => setStock((s) => ({ ...s, [id]: p.availableStock ?? '0' }))).catch(() => undefined);
  };
  useEffect(() => { if (out) Object.keys(originals).forEach((id) => loadStock(Number(id))); }, [out, originals]);

  /** Rows with quantities converted to each product's own unit: totals, amounts and the stock check all use these. */
  const stored = storedRows(rows, products);
  const shortfalls = out ? stockShortfalls(stored, stock, originals) : [];
  const short = Object.fromEntries(shortfalls.map((s) => [s.productId, s]));
  const total = grandTotal(stored);
  const units = unitTotals(stored, products);

  const patchRow = (key: string, p: Partial<ItemRow>) => {
    setRows((rs) => rs.map((r) => (r.key === key ? { ...r, ...p } : r)));
    setRowErr((e) => { const { [key]: _drop, ...rest } = e; void _drop; return rest; });
  };
  const pick = (key: string, p: ProductInfo) => {
    setProducts((m) => ({ ...m, [p.id]: p }));
    patchRow(key, { productId: p.id, unit: defaultEntryUnit(p.unit) });
    if (out) loadStock(p.id);
  };
  const setH = <K extends keyof HeaderValues>(k: K, v: HeaderValues[K]) => {
    setHeader((h) => ({ ...h, [k]: v }));
    setHeaderErr((e) => { const { [k]: _drop, ...rest } = e; void _drop; return rest; });
  };

  const save = useMutation({
    mutationFn: () => {
      const payload = buildPayload(header, rows, initial?.version);
      return initial ? materialService.update(type, initial.id, payload) : materialService.create(type, payload);
    },
    onSuccess: (tx) => {
      message.success(`${initial ? 'Updated' : 'Saved'} ${tx.transactionNumber}`);
      for (const k of ['material-in', 'material-out', 'inventory', 'products']) void qc.invalidateQueries({ queryKey: [k] });
      if (canAttach && queue.items.length > 0) {
        setSaved({ id: tx.id, number: tx.transactionNumber });
        void runUploads(tx.id);
      } else onClose();
    },
    onSettled: () => { submitting.current = false; },
    onError: (e) => {
      const p = problemOf(e);
      const fe = p?.fieldErrors ?? [];
      const st = statusOf(e);
      if (st === 409 && fe.length > 0) {
        const m = mapStockErrors(fe, rows, products);
        setRowErr(m.byRow);
        setFormError(m.other.length ? m.other.join(' ') : (p?.message ?? 'Not enough stock'));
        if (out) Array.from(new Set(rows.map((r) => r.productId))).forEach((id) => { if (id) loadStock(id); });
      } else if (st === 409) {
        if (initial) setConflict(p?.message ?? 'This entry was modified by someone else.');
        else setFormError(p?.message ?? 'Conflict');
      } else if (st === 422 && fe.length > 0) {
        const hdr = fe.filter((x) => HEADER_FIELDS.includes(x.field));
        setHeaderErr(Object.fromEntries(hdr.map((x) => [HEADER_KEY[x.field] ?? x.field, x.message])));
        const m = mapStockErrors(fe.filter((x) => !HEADER_FIELDS.includes(x.field)), rows, products);
        setRowErr(m.byRow);
        setFormError(m.other.length ? m.other.join(' ') : null);
      } else setFormError(p?.message ?? 'Could not save. Your entries are kept; please retry.');
    },
  });

  const runUploads = async (id: number) => {
    const failed = await queue.uploadAll(id);
    setFailedCount(failed);
    void qc.invalidateQueries({ queryKey: ['attachments', 'MATERIAL_TRANSACTION', id] });
    if (failed === 0) onClose();
  };

  const submit = () => {
    if (saved || submitting.current || save.isPending || shortfalls.length > 0) return;
    setFormError(null); setConflict(null);
    const v = validateForm(type, header, rows, products);
    setHeaderErr(v.header); setRowErr(v.rows);
    if (Object.keys(v.header).length || Object.keys(v.rows).length) return;
    submitting.current = true;
    save.mutate();
  };

  const footer = (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap' }} data-testid="form-footer">
      <div>
        <div style={{ fontWeight: 600, fontSize: 16 }} data-testid="grand-total">{formatCurrency(total)}</div>
        <div style={{ fontSize: 12 }} data-testid="unit-totals">
          {units.length === 0 ? <Typography.Text type="secondary">No quantity yet</Typography.Text>
            : units.map((u) => <Tag key={u.unit} style={{ marginInlineEnd: 4 }}>{u.quantity} {u.unit}</Tag>)}
        </div>
      </div>
      {saved ? (
        <Space>
          <Button onClick={onClose} disabled={queue.uploading}>Close</Button>
          {failedCount > 0 && <Button type="primary" onClick={() => void runUploads(saved.id)} loading={queue.uploading}>Retry failed files</Button>}
        </Space>
      ) : (
        <Space>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" onClick={submit} loading={save.isPending} disabled={shortfalls.length > 0}>Save</Button>
        </Space>
      )}
    </div>
  );

  const content = (
    <div>
      {conflict && (
        <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={conflict}
          description="Reload to see the latest version, then re-apply your changes."
          action={<Button size="small" onClick={onReload}>Reload</Button>} />
      )}
      {formError && <Alert type="error" showIcon style={{ marginBottom: 12 }} message={formError} />}
      {saved && (
        failedCount > 0 ? (
          <Alert type="warning" showIcon style={{ marginBottom: 12 }} message={`Entry ${saved.number} saved, but ${failedCount} file(s) failed to upload`}
            description="Your entry is saved. Retry the failed files, or close and add them later from the entry details." />
        ) : <Alert type="info" showIcon style={{ marginBottom: 12 }} message={`Entry ${saved.number} saved. Uploading files…`} />
      )}
      <div style={saved ? { pointerEvents: 'none', opacity: 0.6 } : undefined} aria-disabled={!!saved}>
      <Form layout="vertical" requiredMark component="div">
        <div style={{ display: 'grid', gridTemplateColumns: mobile ? 'repeat(2, 1fr)' : 'repeat(3, 1fr)', columnGap: 16 }}>
          {initial && (
            <div style={{ marginBottom: 24, gridColumn: mobile ? 'span 2' : undefined }}>
              <div style={{ marginBottom: 8 }}>Entry Number</div>
              <Input value={initial.transactionNumber} disabled aria-label="Entry Number" />
            </div>
          )}
          <Form.Item label="Date" required validateStatus={headerErr.date ? 'error' : undefined} help={headerErr.date}>
            <DatePicker value={header.date} onChange={(d) => d && setH('date', d)} allowClear={false} format="DD MMM YYYY" style={{ width: '100%' }} inputReadOnly={mobile} aria-label="Date" />
          </Form.Item>
          <Form.Item label={LABEL[type].party} required={type === 'IN'} style={mobile ? { gridColumn: 'span 2' } : undefined} validateStatus={headerErr.partyId ? 'error' : undefined} help={headerErr.partyId}>
            <PartySelect
              value={header.partyId} onChange={(v) => setH('partyId', v)} allowClear={out} onlyActive
              type={out ? 'CLIENT' : 'SUPPLIER'} placeholder={`Select ${LABEL[type].party.toLowerCase()}`}
              initial={initial?.partyId ? { id: initial.partyId, name: initial.partyName ?? '' } : undefined}
            />
          </Form.Item>
          <Form.Item label="Challan no." validateStatus={headerErr.challanNumber ? 'error' : undefined} help={headerErr.challanNumber}>
            <Input value={header.challanNumber} maxLength={50} onChange={(e) => setH('challanNumber', e.target.value)} placeholder="Optional" aria-label="Challan no." />
          </Form.Item>
          <Form.Item label="Vehicle no." validateStatus={headerErr.vehicleNumber ? 'error' : undefined} help={headerErr.vehicleNumber}>
            <Input value={header.vehicleNumber} maxLength={20} autoCapitalize="characters" onChange={(e) => setH('vehicleNumber', e.target.value.toUpperCase())} placeholder="Optional" aria-label="Vehicle no." />
          </Form.Item>
          <Form.Item label="Driver" style={mobile ? { gridColumn: 'span 2' } : undefined} validateStatus={headerErr.driverName ? 'error' : undefined} help={headerErr.driverName}>
            <Input value={header.driverName} maxLength={100} onChange={(e) => setH('driverName', e.target.value)} placeholder="Optional" aria-label="Driver" />
          </Form.Item>
        </div>
      </Form>

      <Typography.Title level={5} style={{ marginTop: 4 }}>Items</Typography.Title>
      {!mobile && (
        <div style={{ ...gridCols(), fontWeight: 500, padding: '0 0 4px' }}>
          <span>Material</span><span>Quantity</span><span>Rate (₹ per unit)</span><span style={{ textAlign: 'right' }}>Amount</span><span />
        </div>
      )}
      {rows.map((r, i) => {
        const p = r.productId ? products[r.productId] : undefined;
        const sf = r.productId ? short[r.productId] : undefined;
        const err = rowErr[r.key];
        const amount = lineAmount(stored[i].quantity, r.rate);
        const conv = p ? convertQuantity(r.quantity, r.unit, p.unit) : null;
        const converted = p && conv && r.unit && r.unit !== p.unit ? conv : null;
        const avail = r.productId ? stock[r.productId] : undefined;
        const fields = (
          <>
            <div>
              <ProductSelect value={r.productId} selected={p} onSelect={(x) => pick(r.key, x)} invalid={!!err || !!sf} />
              {out && p && (
                <div style={{ fontSize: 12, marginTop: 2 }} data-testid={`avail-${i}`}>
                  <Typography.Text type="secondary">{avail === undefined ? 'Checking stock…' : `Available: ${formatQuantity(avail)} ${p.unit}`}</Typography.Text>
                </div>
              )}
            </div>
            <div>
              <Space.Compact style={{ width: '100%' }}>
                <Input
                  inputMode="decimal" value={r.quantity} placeholder="0.000" aria-label={`Quantity ${i + 1}`} status={err || sf || (converted && 'error' in converted) ? 'error' : undefined}
                  onChange={(e) => QTY_INPUT.test(e.target.value) && patchRow(r.key, { quantity: e.target.value })}
                />
                <Select
                  value={r.unit ?? p?.unit} disabled={!p || entryUnits(p.unit).length === 1} aria-label={`Unit ${i + 1}`} style={{ width: 96, flex: 'none' }}
                  options={(p ? entryUnits(p.unit) : []).map((u) => ({ value: u, label: u }))} onChange={(u) => patchRow(r.key, { unit: u })}
                />
              </Space.Compact>
              {converted && (
                <div style={{ fontSize: 12, marginTop: 2, overflowWrap: 'anywhere' }} data-testid={`convert-${i}`}
                  role={'error' in converted ? 'alert' : undefined}>
                  {'value' in converted
                    ? <Typography.Text type="secondary">= {converted.value} {p?.unit} (stock unit)</Typography.Text>
                    : <Typography.Text type="danger">{converted.error}</Typography.Text>}
                </div>
              )}
            </div>
            <div>
              {p && mobile && <div style={{ fontSize: 12 }} data-testid={`rate-label-${i}`}><Typography.Text type="secondary">Rate (₹ per {p.unit})</Typography.Text></div>}
              <Input
                inputMode="decimal" value={r.rate} placeholder={out ? 'Optional' : '0.00'} aria-label={`Rate ${i + 1}`} addonAfter={p && !mobile ? `/ ${p.unit}` : undefined}
                onChange={(e) => RATE_INPUT.test(e.target.value) && patchRow(r.key, { rate: e.target.value })}
              />
            </div>
            <div style={{ textAlign: mobile ? 'left' : 'right', paddingTop: mobile ? 0 : 5 }} data-testid={`amount-${i}`}>
              {mobile && <Typography.Text type="secondary">Amount: </Typography.Text>}{amount === null ? '—' : formatCurrency(amount)}
            </div>
            <Button
              type="text" danger icon={<DeleteOutlined />} aria-label={`Remove item ${i + 1}`} disabled={rows.length === 1}
              onClick={() => setRows((rs) => rs.filter((x) => x.key !== r.key))}
            />
          </>
        );
        const msgs = (
          <>
            {sf && <div role="alert" style={{ color: 'var(--ant-color-error, #ff4d4f)', fontSize: 12 }}>Only {formatQuantity(sf.available)} {p?.unit} available, {formatQuantity(sf.required)} entered</div>}
            {err && <div role="alert" style={{ color: 'var(--ant-color-error, #ff4d4f)', fontSize: 12 }}>{err}</div>}
          </>
        );
        return mobile ? (
          <Card key={r.key} size="small" style={{ marginBottom: 8 }} data-testid={`row-${i}`}>
            <div style={{ display: 'grid', gap: 8 }}>{fields}</div>{msgs}
          </Card>
        ) : (
          <div key={r.key} style={{ marginBottom: 8 }} data-testid={`row-${i}`}>
            <div style={gridCols()}>{fields}</div>{msgs}
          </div>
        );
      })}
      <Button icon={<PlusOutlined />} onClick={() => setRows((rs) => [...rs, newRow()])} block={mobile}>Add item</Button>
      </div>
      {canAttach && (
        <FileQueueSection queue={queue} mobile={mobile} ownerType="MATERIAL_TRANSACTION" ownerId={initial?.id} canDelete={!!initial && can(`${perm}.UPDATE`)} disabled={!!saved && queue.uploading} />
      )}
      <Form layout="vertical" requiredMark component="div" style={{ marginTop: 16 }}>
        <Form.Item label="Remarks" validateStatus={headerErr.remarks ? 'error' : undefined} help={headerErr.remarks}>
          <Input.TextArea autoSize={{ minRows: 1, maxRows: 4 }} value={header.remarks} maxLength={500} onChange={(e) => setH('remarks', e.target.value)} placeholder="Optional" aria-label="Remarks" />
        </Form.Item>
      </Form>
    </div>
  );

  return mobile ? (
    <Drawer open title={title} width="100%" onClose={onClose} destroyOnHidden maskClosable={false} footer={footer}>{content}</Drawer>
  ) : (
    <Modal open title={title} width={960} onCancel={onClose} footer={footer} destroyOnHidden maskClosable={false}>{content}</Modal>
  );
}

const gridCols = () => ({ display: 'grid', alignItems: 'start', gridTemplateColumns: 'minmax(220px, 2.4fr) 1.2fr 1fr 1fr 40px', columnGap: 8 }) as const;
