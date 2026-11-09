/** String-safe quantity helpers: quantities/money are never parsed to float. */
function parts(s: string): [bigint, number] {
  const [i, f = ''] = s.trim().split('.');
  return [BigInt(i + f), f.length];
}

/** Compares two plain decimal strings (e.g. "12.500" vs "10"); returns -1, 0, 1. */
export function compareDecimal(a: string, b: string): number {
  const [x, sx] = parts(a);
  const [y, sy] = parts(b);
  const s = Math.max(sx, sy);
  const nx = x * 10n ** BigInt(s - sx);
  const ny = y * 10n ** BigInt(s - sy);
  return nx < ny ? -1 : nx > ny ? 1 : 0;
}

/** "12500.500" -> "12,500.5", "10.000" -> "10". */
export function formatQuantity(s: string | null | undefined): string {
  if (s === null || s === undefined || s === '') return '';
  const [i, f = ''] = s.split('.');
  const frac = f.replace(/0+$/, '');
  const neg = i.startsWith('-');
  const grouped = (neg ? i.slice(1) : i).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
  return `${neg ? '-' : ''}${grouped}${frac ? `.${frac}` : ''}`;
}

/** "12.5 TON + 40 KG": the quantity per unit of an entry (units are never added together); a dash when there is none. */
export function formatQuantities(list?: { unit: string; quantity: string }[] | null): string {
  return list && list.length > 0 ? list.map((q) => `${formatQuantity(q.quantity)} ${q.unit}`).join(' + ') : '—';
}
