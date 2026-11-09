export interface DiffRow { key: string; oldValue: string | null; newValue: string | null }

/** Flattens nested objects into dotted keys; arrays and scalars are leaves. */
function flatten(v: unknown, prefix = '', out: Record<string, unknown> = {}): Record<string, unknown> {
  if (v && typeof v === 'object' && !Array.isArray(v)) {
    for (const [k, val] of Object.entries(v as Record<string, unknown>)) flatten(val, prefix ? `${prefix}.${k}` : k, out);
  } else if (prefix) out[prefix] = v;
  return out;
}
const show = (v: unknown): string | null =>
  v === undefined ? null : typeof v === 'string' ? v : JSON.stringify(v);

/** Changed keys between old and new snapshots; redacted values ("***") are shown as-is. Missing side = null. */
export function diffValues(oldValues: unknown, newValues: unknown): DiffRow[] {
  const o = flatten(oldValues), n = flatten(newValues);
  const keys = [...new Set([...Object.keys(o), ...Object.keys(n)])].sort();
  return keys
    .filter((k) => JSON.stringify(o[k]) !== JSON.stringify(n[k]))
    .map((k) => ({ key: k, oldValue: show(o[k]), newValue: show(n[k]) }));
}
