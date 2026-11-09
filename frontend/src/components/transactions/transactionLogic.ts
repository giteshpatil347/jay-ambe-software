import type { Dayjs } from 'dayjs';
import type { ProductUnit } from '../../types/masterData';
import type { TxBody, TxDetail, TxType } from '../../types/material';
import { toIsoDate } from '../../utils/date';
import { addDec, cmpDec, fmtDec, mulDec, parseDec, roundDec, ZERO, type Dec } from '../../utils/decimalMath';
import { formatQuantity } from '../../utils/quantity';

/** `unit` = the unit the quantity is typed in; undefined means the product's own unit (the server's null semantics). */
export interface ItemRow { key: string; productId?: number; quantity: string; rate: string; unit?: ProductUnit }
export interface ProductInfo { id: number; code: string; name: string; unit: ProductUnit }
export interface HeaderValues {
  date: Dayjs; partyId?: number; challanNumber: string; vehicleNumber: string; driverName: string; remarks: string;
}

let seq = 0;
export type Conversion = { value: string } | { error: string };

/** Units a user may type a quantity in for a product: TON/KG products allow TON and KG, PIECE products only PIECE. */
export const entryUnits = (product: ProductUnit): ProductUnit[] => (product === 'PIECE' ? ['PIECE'] : ['TON', 'KG']);
/** Default entry unit for a NEW line: TON (PIECE for PIECE products). */
export const defaultEntryUnit = (product: ProductUnit): ProductUnit => (product === 'PIECE' ? 'PIECE' : 'TON');

/**
 * Server parity (UnitConverter): 1 TON = 1000 KG, scale 3, HALF_UP, string-safe. Null while the quantity is blank/zero/invalid.
 * A result of 0.000 or an impossible mix (PIECE <-> weight) is an error.
 */
export function convertQuantity(qty: string, entered: ProductUnit | undefined, product: ProductUnit): Conversion | null {
  const q = pos(qty);
  if (!q) return null;
  const from = entered ?? product;
  let r: Dec;
  if (from === product) r = roundDec(q, 3);
  else if (from === 'TON' && product === 'KG') r = roundDec(mulDec(q, { n: 1000n, s: 0 }), 3);
  else if (from === 'KG' && product === 'TON') r = roundDec({ n: q.n, s: q.s + 3 }, 3);
  else return { error: `Cannot convert ${from} to ${product}` };
  if (r.n <= 0n) return { error: `Too small: converts to 0 ${product}` };
  const value = fmtDec(r, 3);
  if (value.split('.')[0].length > 11) return { error: `Too large for ${product}` };
  return { value };
}

/** Rows with `quantity` replaced by the product-unit quantity ('' when incomplete/invalid), `unit` cleared. */
export function storedRows(rows: ItemRow[], products: Record<number, ProductInfo>): ItemRow[] {
  return rows.map((r) => {
    const p = r.productId ? products[r.productId] : undefined;
    if (!p) return r;
    const c = convertQuantity(r.quantity, r.unit, p.unit);
    return { ...r, unit: undefined, quantity: c && 'value' in c ? c.value : '' };
  });
}

export const newRow = (p?: Partial<ItemRow>): ItemRow => ({ key: `r${++seq}`, quantity: '', rate: '', ...p });

/** Qty up to 3dp, rate up to 2dp: only these shapes are accepted while typing. */
export const QTY_INPUT = /^\d{0,10}(\.\d{0,3})?$/;
export const RATE_INPUT = /^\d{0,10}(\.\d{0,2})?$/;

const pos = (v: string): Dec | null => { const d = parseDec(v); return d && d.n > 0n ? d : null; };

/** qty x rate rounded half-up to 2dp (server parity); null while incomplete. Blank rate counts as 0 (server default). */
export function lineAmount(qty: string, rate: string): string | null {
  const q = parseDec(qty);
  const r = rate.trim() === '' ? ZERO : parseDec(rate);
  if (!q || !r) return null;
  return fmtDec(roundDec(mulDec(q, r), 2), 2);
}

export function grandTotal(rows: ItemRow[]): string {
  const sum = rows.reduce((acc, r) => { const a = parseDec(lineAmount(r.quantity, r.rate)); return a ? addDec(acc, a) : acc; }, ZERO);
  return fmtDec(roundDec(sum, 2), 2);
}

/** Per-unit quantity totals; units are never mixed (TON + KG). */
export function unitTotals(rows: ItemRow[], products: Record<number, ProductInfo>): { unit: ProductUnit; quantity: string }[] {
  const acc = new Map<ProductUnit, Dec>();
  for (const r of rows) {
    const p = r.productId ? products[r.productId] : undefined;
    const q = parseDec(r.quantity);
    if (p && q) acc.set(p.unit, addDec(acc.get(p.unit) ?? ZERO, q));
  }
  return [...acc].map(([unit, d]) => ({ unit, quantity: formatQuantity(fmtDec(d)) }));
}

/** Summed quantity per product across duplicate rows. */
export function requiredByProduct(rows: ItemRow[]): Map<number, Dec> {
  const m = new Map<number, Dec>();
  for (const r of rows) {
    const q = parseDec(r.quantity);
    if (r.productId && q) m.set(r.productId, addDec(m.get(r.productId) ?? ZERO, q));
  }
  return m;
}

export interface Shortfall { productId: number; required: string; available: string }

/**
 * OUT only. available = fresh stock + (edit) the quantity of that product already booked on this transaction,
 * because the server reverses the original lines before re-posting. Unknown stock (not loaded yet) is not flagged.
 */
export function stockShortfalls(
  rows: ItemRow[], stock: Record<number, string | undefined>, originals: Record<number, string> = {},
): Shortfall[] {
  const out: Shortfall[] = [];
  for (const [productId, req] of requiredByProduct(rows)) {
    const s = parseDec(stock[productId]);
    if (!s) continue;
    const avail = addDec(s, parseDec(originals[productId]) ?? ZERO);
    if (cmpDec(req, avail) > 0) out.push({ productId, required: fmtDec(req), available: fmtDec(avail) });
  }
  return out;
}

/** Original quantity per product of the transaction being edited (empty when creating). */
export function originalQuantities(tx?: TxDetail | null): Record<number, string> {
  const m = new Map<number, Dec>();
  for (const i of tx?.items ?? []) { const q = parseDec(i.quantity); if (q) m.set(i.productId, addDec(m.get(i.productId) ?? ZERO, q)); }
  return Object.fromEntries([...m].map(([k, v]) => [k, fmtDec(v)]));
}

export function validateForm(
  type: TxType, h: HeaderValues, rows: ItemRow[], products: Record<number, ProductInfo> = {},
): { header: Record<string, string>; rows: Record<string, string> } {
  const header: Record<string, string> = {};
  const rowErr: Record<string, string> = {};
  if (!h.date) header.date = 'Date is required';
  if (type === 'IN' && !h.partyId) header.partyId = 'Supplier is required';
  rows.forEach((r) => {
    if (!r.productId) rowErr[r.key] = 'Select a material';
    else if (!pos(r.quantity)) rowErr[r.key] = 'Enter a quantity greater than 0';
    else if (products[r.productId] && 'error' in (convertQuantity(r.quantity, r.unit, products[r.productId].unit) ?? {})) {
      rowErr[r.key] = (convertQuantity(r.quantity, r.unit, products[r.productId].unit) as { error: string }).error;
    } else if (type === 'IN' && r.rate.trim() === '') rowErr[r.key] = 'Rate is required';
    else if (r.rate.trim() !== '' && !parseDec(r.rate)) rowErr[r.key] = 'Enter a valid rate';
  });
  return { header, rows: rowErr };
}

const blank = (s: string) => (s.trim() ? s.trim() : undefined);
const num = (s: string) => s.trim().replace(/\.$/, '');

/** Strings stay strings (quantity exactly as typed, `unit` = the entry unit); `version` is only sent when editing. */
export function buildPayload(h: HeaderValues, rows: ItemRow[], version?: number): TxBody {
  const body: TxBody = {
    transactionDate: toIsoDate(h.date), partyId: h.partyId, challanNumber: blank(h.challanNumber),
    vehicleNumber: blank(h.vehicleNumber)?.toUpperCase(), driverName: blank(h.driverName), remarks: blank(h.remarks),
    items: rows.map((r) => ({ productId: r.productId as number, quantity: num(r.quantity), unit: r.unit, rate: r.rate.trim() ? num(r.rate) : undefined })),
  };
  if (version !== undefined) body.version = version;
  return body;
}

/** Maps server 409 `fieldErrors` (field = product code, or `items[i]...`) onto row keys. Unmapped messages are returned too. */
export function mapStockErrors(
  fieldErrors: { field: string; message: string }[], rows: ItemRow[], products: Record<number, ProductInfo>,
): { byRow: Record<string, string>; other: string[] } {
  const byRow: Record<string, string> = {};
  const other: string[] = [];
  for (const e of fieldErrors) {
    const idx = /^items\[(\d+)\]/.exec(e.field);
    const hit = idx ? [rows[Number(idx[1])]] : rows.filter((r) => r.productId && products[r.productId]?.code === e.field);
    const keys = hit.filter(Boolean).map((r) => r.key);
    if (keys.length === 0) other.push(e.message);
    keys.forEach((k) => { byRow[k] = e.message; });
  }
  return { byRow, other };
}

export const rowsFromDetail = (tx: TxDetail): ItemRow[] => tx.items.map((i) => newRow({ productId: i.productId, quantity: i.quantity, rate: i.rate, unit: i.unit }));
export const productsFromDetail = (tx: TxDetail): Record<number, ProductInfo> =>
  Object.fromEntries(tx.items.map((i) => [i.productId, { id: i.productId, code: i.productCode, name: i.productName, unit: i.unit }]));

