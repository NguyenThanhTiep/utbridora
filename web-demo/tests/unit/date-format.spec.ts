import { expect, test } from '@playwright/test';
import { formatDateDMY, formatDateInputDMY } from '../../src/ui/dateFormat';

test('formats ISO dates and local timestamps as DD/MM/YYYY without timezone shift', () => {
  expect(formatDateDMY('2026-11-28')).toBe('28/11/2026');
  expect(formatDateDMY('2026-02-05')).toBe('05/02/2026');
  expect(formatDateDMY('2026-11-28T09:30')).toBe('28/11/2026');
  expect(formatDateDMY('')).toBe('—');
  expect(formatDateDMY(null)).toBe('—');
  expect(formatDateDMY(undefined, 'Chưa rõ')).toBe('Chưa rõ');
});

test('formats native date and datetime-local input values day-first', () => {
  expect(formatDateInputDMY('', 'date')).toBe('');
  expect(formatDateInputDMY('2026-11-28', 'date')).toBe('28/11/2026');
  expect(formatDateInputDMY('', 'datetime-local')).toBe('');
  expect(formatDateInputDMY('2026-11-28T09:30', 'datetime-local')).toBe('28/11/2026 · 09:30');
  expect(formatDateInputDMY('2026-11-28T09:30:45', 'datetime-local')).toBe('28/11/2026 · 09:30:45');
});
