const DATE_PREFIX_RE = /^(\d{4})-(\d{2})-(\d{2})(?:$|[T\s])/;
const DATE_ONLY_RE = /^(\d{4})-(\d{2})-(\d{2})$/;
const DATETIME_LOCAL_RE = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?/;
const HAS_TZ_RE = /(?:Z|[+-]\d{2}:?\d{2})$/i;

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

/**
 * Formats an ISO date (`YYYY-MM-DD`) or timestamp to day-first `DD/MM/YYYY`.
 * Pure date strings avoid timezone conversion so dates never shift by UTC offset.
 */
export function formatDateDMY(value: string | null | undefined, fallback = '—'): string {
  const trimmed = value?.trim();
  if (!trimmed) return fallback;

  if (!HAS_TZ_RE.test(trimmed)) {
    const match = DATE_PREFIX_RE.exec(trimmed);
    if (match) return `${match[3]}/${match[2]}/${match[1]}`;
  }

  const parsed = new Date(trimmed);
  if (!Number.isNaN(parsed.getTime())) {
    return `${pad2(parsed.getDate())}/${pad2(parsed.getMonth() + 1)}/${parsed.getFullYear()}`;
  }

  const fallbackMatch = DATE_PREFIX_RE.exec(trimmed);
  return fallbackMatch ? `${fallbackMatch[3]}/${fallbackMatch[2]}/${fallbackMatch[1]}` : trimmed;
}

/**
 * Formats native `<input type="date">` / `<input type="datetime-local">` values
 * (`YYYY-MM-DD` / `YYYY-MM-DDTHH:mm[:ss]`) into day-first display labels.
 */
export function formatDateInputDMY(value: string, type: 'date' | 'datetime-local'): string {
  const trimmed = value.trim();
  if (!trimmed) return '';

  if (type === 'date') {
    const match = DATE_ONLY_RE.exec(trimmed) ?? DATE_PREFIX_RE.exec(trimmed);
    return match ? `${match[3]}/${match[2]}/${match[1]}` : '';
  }

  const match = DATETIME_LOCAL_RE.exec(trimmed);
  if (!match) return '';
  const base = `${match[3]}/${match[2]}/${match[1]} · ${match[4]}:${match[5]}`;
  return match[6] ? `${base}:${match[6]}` : base;
}
