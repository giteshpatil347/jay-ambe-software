import { addDec, cmpDec, fmtDec, mulDec, parseDec, roundDec, subDec, type Dec } from './decimalMath';

const p = (s: string) => parseDec(s) as Dec;
const line = (q: string, r: string) => fmtDec(roundDec(mulDec(p(q), p(r)), 2), 2);

describe('decimal', () => {
  it('multiplies and rounds half-up to 2dp', () => {
    expect(line('12.500', '15000')).toBe('187500.00');
    expect(line('0.333', '100.10')).toBe('33.33');
    expect(line('0.005', '1')).toBe('0.01');
    expect(line('0.004', '1')).toBe('0.00');
    expect(line('1.005', '1.00')).toBe('1.01'); // would be 1.00 with floats
  });
  it('parses edge inputs', () => {
    expect(parseDec('')).toBeNull();
    expect(parseDec('.')).toBeNull();
    expect(parseDec('1.')).toEqual({ n: 1n, s: 0 });
    expect(parseDec('-1')).toBeNull();
    expect(parseDec('.5')).toEqual({ n: 5n, s: 1 });
  });
  it('adds, subtracts, compares', () => {
    expect(fmtDec(addDec(p('0.1'), p('0.2')), 1)).toBe('0.3');
    expect(fmtDec(subDec(p('1'), p('2.5')))).toBe('-1.5');
    expect(cmpDec(p('10.000'), p('10'))).toBe(0);
    expect(cmpDec(p('10.001'), p('10'))).toBe(1);
  });
});
