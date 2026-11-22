import { describe, expect, it } from 'vitest';
import { formatCents, MINUS } from './money';

// covers AC12, T13
describe('formatCents', () => {
  it('formats whole and fractional dollars with thousands separators', () => {
    expect(formatCents(120_000)).toBe('$1,200.00');
    expect(formatCents(1_400)).toBe('$14.00');
    expect(formatCents(5)).toBe('$0.05');
    expect(formatCents(0)).toBe('$0.00');
    expect(formatCents(123_456_789)).toBe('$1,234,567.89');
  });

  it('uses a true minus sign for negative amounts by default', () => {
    expect(MINUS).toBe('−');
    expect(formatCents(-64_200)).toBe('−$642.00');
  });

  it('adds +$ for charges and −$ for credits when signed', () => {
    expect(formatCents(120_000, { signed: true })).toBe('+$1,200.00');
    expect(formatCents(-90_082, { signed: true })).toBe('−$900.82');
    expect(formatCents(0, { signed: true })).toBe('$0.00');
  });

  it('can drop the sign to show a magnitude', () => {
    expect(formatCents(-143_482, { abs: true })).toBe('$1,434.82');
  });

  it('refuses non-integer cents rather than rounding silently', () => {
    expect(() => formatCents(10.5)).toThrow(/integer/);
  });
});
