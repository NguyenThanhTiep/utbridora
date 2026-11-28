export type PagerSlot = number | null;

/** Computes up to 7 compact page slots (using `null` for ellipsis) matching Lucian #449 windowing. */
export function pagerSlots(current1Based: number, totalPages: number): PagerSlot[] {
  const total = Math.max(1, Math.trunc(totalPages));
  const cur = Math.min(Math.max(1, Math.trunc(current1Based)), total);
  if (total <= 7) {
    return Array.from({ length: total }, (_, idx) => idx + 1);
  }
  if (cur <= 4) {
    return [1, 2, 3, 4, 5, null, total];
  }
  if (cur >= total - 3) {
    return [1, null, total - 4, total - 3, total - 2, total - 1, total];
  }
  return [1, null, cur - 1, cur, cur + 1, null, total];
}
