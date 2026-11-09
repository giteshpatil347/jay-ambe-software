/** String-safe decimal arithmetic on BigInt (never floats). Non-negative inputs only; invalid input -> null. */
export interface Dec { n: bigint; s: number }

const RE = /^(\d+)(?:\.(\d*))?$|^\.(\d+)$/;

export function parseDec(v: string | null | undefined): Dec | null {
  const m = RE.exec((v ?? '').trim());
  if (!m) return null;
  const frac = m[2] ?? m[3] ?? '';
  return { n: BigInt((m[1] ?? '0') + frac), s: frac.length };
}

const pow10 = (k: number) => 10n ** BigInt(k);

/** Rounds half-up to `scale` decimals (same as BigDecimal.setScale(scale, HALF_UP) for non-negatives). */
export function roundDec(d: Dec, scale: number): Dec {
  if (d.s <= scale) return { n: d.n * pow10(scale - d.s), s: scale };
  const div = pow10(d.s - scale);
  const q = d.n / div;
  return { n: (d.n % div) * 2n >= div ? q + 1n : q, s: scale };
}

export const mulDec = (a: Dec, b: Dec): Dec => ({ n: a.n * b.n, s: a.s + b.s });

export function addDec(a: Dec, b: Dec): Dec {
  const s = Math.max(a.s, b.s);
  return { n: a.n * pow10(s - a.s) + b.n * pow10(s - b.s), s };
}

export function subDec(a: Dec, b: Dec): Dec {
  const s = Math.max(a.s, b.s);
  return { n: a.n * pow10(s - a.s) - b.n * pow10(s - b.s), s };
}

/** Text with exactly `scale` decimals (rounded half-up by magnitude), e.g. "187500.00". Negative allowed. */
export function fmtDec(d: Dec, scale = d.s): string {
  const r = roundDec(d.n < 0n ? { n: -d.n, s: d.s } : d, scale);
  const neg = d.n < 0n && r.n !== 0n;
  const str = r.n.toString().padStart(scale + 1, '0');
  const body = scale === 0 ? str : `${str.slice(0, -scale)}.${str.slice(-scale)}`;
  return neg ? `-${body}` : body;
}

export const ZERO: Dec = { n: 0n, s: 0 };
export const cmpDec = (a: Dec, b: Dec): number => {
  const d = subDec(a, b).n;
  return d < 0n ? -1 : d > 0n ? 1 : 0;
};
