import { expect, it } from 'vitest';
import { money } from '../api';
it('formats rupees with Indian grouping and paise', () => {
  expect(money(6999)).toBe('₹6,999.00');
  expect(money(123456.78)).toBe('₹1,23,456.78');
  expect(money(0)).toBe('₹0.00');
});
