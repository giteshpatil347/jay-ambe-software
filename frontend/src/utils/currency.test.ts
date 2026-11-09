import { formatCurrency } from './currency';

it('formats INR with Indian grouping', () => {
  expect(formatCurrency('187500.00')).toBe('₹1,87,500.00');
  expect(formatCurrency('1234567.5')).toBe('₹12,34,567.50');
});
it('handles empty/invalid', () => {
  expect(formatCurrency(null)).toBe('');
  expect(formatCurrency('abc')).toBe('abc');
});
