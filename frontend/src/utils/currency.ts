const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' });

/** Formats a server money string (e.g. "187500.00") as INR. Never parses for arithmetic. */
export function formatCurrency(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === '') return '';
  const n = Number(value);
  return Number.isFinite(n) ? inr.format(n) : String(value);
}
