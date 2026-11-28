import { expect, test } from '@playwright/test';
import { pagerSlots } from '../../src/ui/pagerWindow';

test('pagerSlots builds full lists up to 7 pages without duplicates', () => {
  expect(pagerSlots(1, 1)).toEqual([1]);
  expect(pagerSlots(1, 2)).toEqual([1, 2]);
  expect(pagerSlots(3, 5)).toEqual([1, 2, 3, 4, 5]);
  expect(pagerSlots(4, 7)).toEqual([1, 2, 3, 4, 5, 6, 7]);
});

test('pagerSlots applies Lucian 7-slot ellipsis windowing cleanly for 8+ pages', () => {
  expect(pagerSlots(1, 20)).toEqual([1, 2, 3, 4, 5, null, 20]);
  expect(pagerSlots(4, 20)).toEqual([1, 2, 3, 4, 5, null, 20]);
  expect(pagerSlots(5, 20)).toEqual([1, null, 4, 5, 6, null, 20]);
  expect(pagerSlots(10, 20)).toEqual([1, null, 9, 10, 11, null, 20]);
  expect(pagerSlots(16, 20)).toEqual([1, null, 15, 16, 17, null, 20]);
  expect(pagerSlots(17, 20)).toEqual([1, null, 16, 17, 18, 19, 20]);
  expect(pagerSlots(20, 20)).toEqual([1, null, 16, 17, 18, 19, 20]);
  for (let total = 1; total <= 22; total++) {
    for (let cur = 1; cur <= total; cur++) {
      const slots = pagerSlots(cur, total);
      expect(slots.includes(cur), `cur=${cur}, total=${total}`).toBe(true);
      const numbers = slots.filter((v): v is number => v !== null);
      expect(new Set(numbers).size).toBe(numbers.length);
    }
  }
});
