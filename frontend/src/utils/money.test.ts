import { subtractMoney } from './money';

it('subtracts money strings without float error', () => {
  expect(subtractMoney('1000.10', '0.20')).toBe('999.90');
  expect(subtractMoney('5000', '1200.5')).toBe('3799.50');
  expect(subtractMoney('100', undefined)).toBe('100.00');
  expect(subtractMoney('10', '25')).toBe('-15.00');
  expect(subtractMoney('abc', '1')).toBeNull();
});
