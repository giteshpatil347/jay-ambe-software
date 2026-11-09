/** Minimal string-based money subtraction (2dp). No float math. Returns null for invalid input. */
const toCents = (v: string): bigint | null => {
  const m = /^(-?)(\d*)(?:\.(\d*))?$/.exec(v.trim());
  if (!m || (m[2] === '' && !m[3])) return null;
  const frac = ((m[3] ?? '') + '00').slice(0, 2);
  const n = BigInt((m[2] || '0') + frac);
  return m[1] ? -n : n;
};
export function subtractMoney(a?: string | null, b?: string | null): string | null {
  const x = toCents(a ?? '');
  if (x === null) return null;
  const y = b ? toCents(b) : 0n;
  if (y === null) return null;
  const r = x - y;
  const neg = r < 0n;
  const s = (neg ? -r : r).toString().padStart(3, '0');
  return `${neg ? '-' : ''}${s.slice(0, -2)}.${s.slice(-2)}`;
}
/** Allow only decimal-looking text for money/qty inputs. */
export const decimalRule = { pattern: /^\d+(\.\d{1,4})?$/, message: 'Enter a valid number' };
