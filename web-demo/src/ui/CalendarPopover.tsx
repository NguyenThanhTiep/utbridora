import { useEffect, useLayoutEffect, useMemo, useRef, useState, type KeyboardEvent, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import './calendar-030.css';

export type CalendarMode = 'date' | 'datetime-local' | 'month' | 'range';

export type CalendarPopoverProps = {
  open: boolean;
  mode: CalendarMode;
  anchorRef: RefObject<HTMLElement | null>;
  value: string;
  startValue?: string;
  endValue?: string;
  activeEndpoint?: 'start' | 'end';
  startLabel?: string;
  endLabel?: string;
  min?: string;
  max?: string;
  includeSeconds?: boolean;
  required?: boolean;
  ariaLabel?: string;
  onSelectDate?: (isoDate: string) => void;
  onSelectMonth?: (isoMonth: string) => void;
  onApplyDateTime?: (isoDateTime: string) => void;
  onApplyRange?: (startDate: string, endDate: string) => void;
  onClose: () => void;
};

const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7'];
const WEEKDAYS_FULL = ['CN', 'Th 2', 'Th 3', 'Th 4', 'Th 5', 'Th 6', 'Th 7'];
const YMD_RE = /^(\d{4})-(\d{2})-(\d{2})/;
const YM_RE = /^(\d{4})-(\d{2})/;
const TIME_RE = /T(\d{2}):(\d{2})(?::(\d{2}))?/;

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}

function keyFromParts(y: number, mZero: number, d: number): number {
  return y * 10000 + (mZero + 1) * 100 + d;
}

function partsFromKey(k: number): { y: number; m: number; d: number } {
  const y = Math.floor(k / 10000);
  const m = (Math.floor(k / 100) % 100) - 1;
  const d = k % 100;
  return { y, m, d };
}

function dateFromKey(k: number): Date {
  const { y, m, d } = partsFromKey(k);
  return new Date(y, m, d, 12, 0, 0);
}

function keyToIso(k: number): string {
  const { y, m, d } = partsFromKey(k);
  return `${y}-${pad2(m + 1)}-${pad2(d)}`;
}

function parseKey(raw?: string): number | null {
  if (!raw) return null;
  const match = YMD_RE.exec(raw.trim());
  if (!match) return null;
  const y = Number(match[1]);
  const m = Number(match[2]) - 1;
  const d = Number(match[3]);
  const dt = new Date(y, m, d, 12, 0, 0);
  if (dt.getFullYear() !== y || dt.getMonth() !== m || dt.getDate() !== d) return null;
  return keyFromParts(y, m, d);
}

function parseYm(raw?: string): { y: number; m: number } | null {
  if (!raw) return null;
  const match = YM_RE.exec(raw.trim());
  if (!match) return null;
  const y = Number(match[1]);
  const m = Number(match[2]) - 1;
  if (m < 0 || m > 11) return null;
  return { y, m };
}

function formatPillDate(k: number | null, placeholder: string): string {
  if (!k) return placeholder;
  const { y, m, d } = partsFromKey(k);
  const dt = new Date(y, m, d, 12, 0, 0);
  return `${WEEKDAYS_FULL[dt.getDay()]}, ${pad2(d)}/${pad2(m + 1)}/${y}`;
}

function formatShortDm(k: number): string {
  const { m, d } = partsFromKey(k);
  return `${pad2(d)}/${pad2(m + 1)}`;
}

function daysBetweenInclusive(a: number, b: number): number {
  const ms = dateFromKey(Math.max(a, b)).getTime() - dateFromKey(Math.min(a, b)).getTime();
  return Math.round(ms / 86400000) + 1;
}

function parseTimeParts(raw: string): { hour: string; minute: string; second: string } {
  const match = TIME_RE.exec(raw.trim());
  if (!match) return { hour: '09', minute: '00', second: '00' };
  return { hour: match[1], minute: match[2], second: match[3] || '00' };
}

export function CalendarPopover({
  open,
  mode,
  anchorRef,
  value,
  startValue,
  endValue,
  activeEndpoint = 'start',
  startLabel = 'Từ ngày',
  endLabel = 'Đến ngày',
  min,
  max,
  includeSeconds = false,
  required = false,
  ariaLabel,
  onSelectDate,
  onSelectMonth,
  onApplyDateTime,
  onApplyRange,
  onClose,
}: CalendarPopoverProps) {
  const popRef = useRef<HTMLDivElement>(null);
  const timerRef = useRef<number>(0);

  const now = new Date();
  const todayKey = keyFromParts(now.getFullYear(), now.getMonth(), now.getDate());
  const minKey = useMemo(() => (mode === 'range' ? null : parseKey(min)), [mode, min]);
  const maxKey = useMemo(() => parseKey(max), [max]);
  const minYm = useMemo(() => parseYm(min), [min]);
  const maxYm = useMemo(() => parseYm(max), [max]);

  const [pos, setPos] = useState<{ top: number; left: number; width: number }>({ top: 0, left: 0, width: 344 });
  const [cur, setCur] = useState<{ y: number; m: number }>({ y: now.getFullYear(), m: now.getMonth() });
  const [anim, setAnim] = useState<{
    leaving: { y: number; m: number } | null;
    dir: -1 | 0 | 1;
    seq: number;
  }>({ leaving: null, dir: 0, seq: 0 });

  const [draftStart, setDraftStart] = useState<number | null>(null);
  const [draftEnd, setDraftEnd] = useState<number | null>(null);
  const [hoverKey, setHoverKey] = useState<number | null>(null);
  const [focusKey, setFocusKey] = useState<number | null>(null);
  const [pickingEnd, setPickingEnd] = useState(false);

  const [draftDate, setDraftDate] = useState<number | null>(null);
  const [hour, setHour] = useState('09');
  const [minute, setMinute] = useState('00');
  const [second, setSecond] = useState('00');

  useEffect(() => {
    if (!open) return;
    if (mode === 'range') {
      const s = parseKey(startValue);
      const e = parseKey(endValue);
      setDraftStart(s);
      setDraftEnd(e);
      setHoverKey(null);
      setPickingEnd(activeEndpoint === 'end' && !!s && !e);
      const seed = (activeEndpoint === 'end' ? e || s : s || e) || todayKey;
      const p = partsFromKey(seed);
      setCur({ y: p.y, m: p.m });
      setFocusKey(seed);
    } else if (mode === 'month') {
      const ym = parseYm(value) || { y: now.getFullYear(), m: now.getMonth() };
      setCur(ym);
    } else {
      const picked = parseKey(value);
      setDraftDate(picked);
      const tp = parseTimeParts(value);
      setHour(tp.hour);
      setMinute(tp.minute);
      setSecond(tp.second);
      const seed = picked || (minKey && minKey > todayKey ? minKey : todayKey);
      const p = partsFromKey(seed);
      setCur({ y: p.y, m: p.m });
      setFocusKey(seed);
    }
    setAnim({ leaving: null, dir: 0, seq: 0 });
  }, [open, mode, value, startValue, endValue, activeEndpoint, minKey, todayKey]);

  const updatePosition = () => {
    const anchor = anchorRef.current;
    if (!anchor) return;
    const rect = anchor.getBoundingClientRect();
    const width = Math.min(350, window.innerWidth - 16);
    const measuredHeight = popRef.current?.getBoundingClientRect().height || 0;
    const estHeight = measuredHeight > 0 ? measuredHeight : mode === 'month' ? 290 : mode === 'datetime-local' ? 500 : 470;
    const spaceBelow = window.innerHeight - rect.bottom - 8;
    const spaceAbove = rect.top - 8;

    let top: number;
    if (spaceBelow >= estHeight || spaceBelow >= spaceAbove) {
      top = Math.max(8, Math.min(rect.bottom + 6, window.innerHeight - estHeight - 8));
    } else {
      top = Math.max(8, Math.min(rect.top - estHeight - 6, window.innerHeight - estHeight - 8));
    }
    const left = Math.max(8, Math.min(rect.left, window.innerWidth - width - 8));
    setPos({ top, left, width });
  };

  useLayoutEffect(() => {
    if (!open) return;
    updatePosition();
    const rafPos = requestAnimationFrame(() => updatePosition());
    const frame = requestAnimationFrame(() => {
      const target = popRef.current?.querySelector<HTMLButtonElement>(
        'button.u030-d[tabindex="0"], button.u030-mo.is-selected, button.u030-mo:not(:disabled)',
      );
      target?.focus({ preventScroll: true });
    });
    return () => {
      cancelAnimationFrame(rafPos);
      cancelAnimationFrame(frame);
    };
  }, [open, mode]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDown = (event: MouseEvent) => {
      const target = event.target as Node;
      if (popRef.current?.contains(target) || anchorRef.current?.contains(target)) return;
      onClose();
    };
    const handleEsc = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        event.stopPropagation();
        onClose();
      }
    };
    const handleReposition = () => updatePosition();

    document.addEventListener('mousedown', handlePointerDown, true);
    document.addEventListener('keydown', handleEsc, true);
    window.addEventListener('resize', handleReposition);
    window.addEventListener('scroll', handleReposition, true);
    const main = anchorRef.current?.closest('.workspace-main');
    const resizeObs = main ? new ResizeObserver(handleReposition) : null;
    if (main && resizeObs) resizeObs.observe(main);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown, true);
      document.removeEventListener('keydown', handleEsc, true);
      window.removeEventListener('resize', handleReposition);
      window.removeEventListener('scroll', handleReposition, true);
      resizeObs?.disconnect();
    };
  }, [open, anchorRef, onClose]);

  useEffect(() => () => window.clearTimeout(timerRef.current), []);

  if (!open) return null;

  const reducedMotion =
    typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const isDisabledDay = (k: number): boolean => {
    if (minKey !== null && k < minKey) return true;
    if (maxKey !== null && k > maxKey) return true;
    return false;
  };

  const stepMonth = (dir: -1 | 1) => {
    const nextDate = new Date(cur.y, cur.m + dir, 1, 12, 0, 0);
    const nextYm = { y: nextDate.getFullYear(), m: nextDate.getMonth() };
    if (reducedMotion) {
      setCur(nextYm);
      setAnim({ leaving: null, dir: 0, seq: anim.seq + 1 });
      return;
    }
    const prev = cur;
    setCur(nextYm);
    setAnim({ leaving: prev, dir, seq: anim.seq + 1 });
    window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      setAnim(current => ({ ...current, leaving: null }));
    }, 390);
  };

  const stepYear = (dir: -1 | 1) => {
    const nextY = cur.y + dir;
    if (nextY < 1900 || nextY > 2199) return;
    if (reducedMotion) {
      setCur({ y: nextY, m: cur.m });
      setAnim({ leaving: null, dir: 0, seq: anim.seq + 1 });
      return;
    }
    const prev = cur;
    setCur({ y: nextY, m: cur.m });
    setAnim({ leaving: prev, dir, seq: anim.seq + 1 });
    window.clearTimeout(timerRef.current);
    timerRef.current = window.setTimeout(() => {
      setAnim(current => ({ ...current, leaving: null }));
    }, 390);
  };

  const handleDayPick = (k: number) => {
    if (isDisabledDay(k)) return;
    setFocusKey(k);
    if (mode === 'range') {
      if (draftStart === null || (draftEnd !== null && !pickingEnd)) {
        setDraftStart(k);
        setDraftEnd(null);
        setPickingEnd(true);
      } else if (draftStart !== null && draftEnd !== null && pickingEnd) {
        if (k < draftStart) {
          setDraftStart(k);
          setDraftEnd(null);
          setPickingEnd(true);
        } else {
          setDraftEnd(k);
          setPickingEnd(false);
        }
      } else if (draftStart !== null) {
        setDraftStart(Math.min(draftStart, k));
        setDraftEnd(Math.max(draftStart, k));
        setPickingEnd(false);
      }
      setHoverKey(null);
      return;
    }

    if (mode === 'date') {
      setDraftDate(k);
      onSelectDate?.(keyToIso(k));
      onClose();
      return;
    }

    setDraftDate(k);
  };

  const commitDateTime = () => {
    if (!draftDate) return;
    const h = pad2(Math.min(23, Math.max(0, Number(hour) || 0)));
    const m = pad2(Math.min(59, Math.max(0, Number(minute) || 0)));
    const s = pad2(Math.min(59, Math.max(0, Number(second) || 0)));
    const suffix = includeSeconds ? `:${s}` : '';
    onApplyDateTime?.(`${keyToIso(draftDate)}T${h}:${m}${suffix}`);
    onClose();
  };

  const commitRange = () => {
    if (!draftStart || !draftEnd) return;
    onApplyRange?.(keyToIso(draftStart), keyToIso(draftEnd));
    onClose();
  };

  const handleGridKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const target = event.target as HTMLElement;
    const dayAttr = target.getAttribute('data-day-key');
    if (!dayAttr) return;

    const deltaMap: Record<string, number> = {
      ArrowLeft: -1,
      ArrowRight: 1,
      ArrowUp: -7,
      ArrowDown: 7,
    };
    const delta = deltaMap[event.key];
    if (!delta) return;

    event.preventDefault();
    const dt = dateFromKey(Number(dayAttr));
    dt.setDate(dt.getDate() + delta);
    const nextK = keyFromParts(dt.getFullYear(), dt.getMonth(), dt.getDate());
    if (isDisabledDay(nextK)) return;

    setFocusKey(nextK);
    if (mode === 'range' && draftStart !== null && draftEnd === null) {
      setHoverKey(nextK);
    }
    if (dt.getFullYear() !== cur.y || dt.getMonth() !== cur.m) {
      const dir: -1 | 1 = delta > 0 ? 1 : -1;
      setCur({ y: dt.getFullYear(), m: dt.getMonth() });
      if (!reducedMotion) {
        setAnim(prev => ({ leaving: cur, dir, seq: prev.seq + 1 }));
        window.clearTimeout(timerRef.current);
        timerRef.current = window.setTimeout(() => {
          setAnim(current => ({ ...current, leaving: null }));
        }, 390);
      }
    }
    requestAnimationFrame(() => {
      popRef.current
        ?.querySelector<HTMLButtonElement>(`button.u030-d[data-day-key="${nextK}"]`)
        ?.focus({ preventScroll: true });
    });
  };

  const renderGrid = (ym: { y: number; m: number }, layerClass: string) => {
    const first = new Date(ym.y, ym.m, 1, 12, 0, 0).getDay();
    const totalDays = new Date(ym.y, ym.m + 1, 0, 12, 0, 0).getDate();

    let lo: number | null = null;
    let hi: number | null = null;
    if (mode === 'range') {
      lo = draftStart;
      hi = draftEnd !== null ? draftEnd : draftStart !== null && hoverKey !== null ? hoverKey : null;
      if (lo !== null && hi !== null && hi < lo) {
        [lo, hi] = [hi, lo];
      }
    }

    const firstEnabledDay = Array.from({ length: totalDays }, (_, idx) =>
      keyFromParts(ym.y, ym.m, idx + 1),
    ).find(k => !isDisabledDay(k));
    const preferredRoving =
      (focusKey && partsFromKey(focusKey).y === ym.y && partsFromKey(focusKey).m === ym.m && !isDisabledDay(focusKey)
        ? focusKey
        : null)
      || (mode === 'range'
        ? draftStart && partsFromKey(draftStart).y === ym.y && partsFromKey(draftStart).m === ym.m
          ? draftStart
          : firstEnabledDay
        : draftDate && partsFromKey(draftDate).y === ym.y && partsFromKey(draftDate).m === ym.m
          ? draftDate
          : firstEnabledDay);

    return (
      <div
        className={`u030-grid ${layerClass}`.trim()}
        role="grid"
        aria-label={`Tháng ${ym.m + 1} năm ${ym.y}`}
        onKeyDown={layerClass.includes('leaving') ? undefined : handleGridKeyDown}
        onPointerLeave={() => {
          if (hoverKey !== null) setHoverKey(null);
        }}
      >
        {Array.from({ length: 42 }, (_, idx) => {
          const dayNum = idx - first + 1;
          if (dayNum < 1 || dayNum > totalDays) {
            return <div className="u030-cell" role="gridcell" key={idx} />;
          }
          const k = keyFromParts(ym.y, ym.m, dayNum);
          const disabled = isDisabledDay(k);
          const isToday = k === todayKey;

          const isSingleSelected = mode !== 'range' && draftDate === k;
          const isRangeStart = mode === 'range' && k === (hi === null ? draftStart : lo);
          const isRangeEnd = mode === 'range' && hi !== null && k === hi;
          const isInRange = mode === 'range' && hi !== null && lo !== null && k >= lo && k <= hi;
          const isPicked =
            mode === 'range' ? k === draftStart || k === draftEnd : draftDate === k;

          const cellClass = [
            'u030-cell',
            isRangeStart ? 's' : '',
            isRangeEnd ? 'e' : '',
            isInRange ? 'in' : '',
          ]
            .filter(Boolean)
            .join(' ');
          const btnClass = [
            'u030-d',
            disabled ? 'past' : '',
            isToday ? 'today' : '',
            isSingleSelected ? 'is-selected' : '',
          ]
            .filter(Boolean)
            .join(' ');

          return (
            <div className={cellClass} role="gridcell" key={idx}>
              <button
                type="button"
                className={btnClass}
                data-day-key={k}
                disabled={disabled}
                tabIndex={k === preferredRoving && !layerClass.includes('leaving') ? 0 : -1}
                aria-selected={isPicked}
                aria-label={formatPillDate(k, '')}
                onClick={() => handleDayPick(k)}
                onPointerEnter={() => {
                  if (mode === 'range' && !disabled && draftStart !== null && draftEnd === null) {
                    setHoverKey(k);
                  }
                }}
              >
                {dayNum}
              </button>
            </div>
          );
        })}
      </div>
    );
  };

  const monthHeading =
    mode === 'month' ? `Năm ${cur.y}` : `Tháng ${cur.m + 1}, ${cur.y}`;
  const leavingHeading = anim.leaving
    ? mode === 'month'
      ? `Năm ${anim.leaving.y}`
      : `Tháng ${anim.leaving.m + 1}, ${anim.leaving.y}`
    : '';

  const rangeReady = mode === 'range' && draftStart !== null && draftEnd !== null;
  const rangeSummaryText =
    mode === 'range' && draftStart && draftEnd
      ? `${daysBetweenInclusive(draftStart, draftEnd)} ngày`
      : mode === 'range' && draftStart
        ? 'Chọn ngày kết thúc'
        : 'Chọn khoảng ngày';

  return createPortal(
    <div
      ref={popRef}
      className="u030-pop"
      role="dialog"
      aria-modal="false"
      aria-label={ariaLabel || (mode === 'range' ? 'Chọn khoảng thời gian' : mode === 'month' ? 'Chọn tháng' : 'Chọn ngày')}
      style={{ top: pos.top, left: pos.left, width: pos.width }}
    >
      {mode === 'range' ? (
        <div className="u030-pills">
          <button
            type="button"
            className={'u030-pill' + (!draftStart || (!!draftEnd && !pickingEnd) ? ' on' : '')}
            onClick={() => {
              setPickingEnd(false);
              if (draftStart) {
                const p = partsFromKey(draftStart);
                setCur({ y: p.y, m: p.m });
              }
            }}
          >
            <small>{startLabel}</small>
            <b className={draftStart ? '' : 'ph'}>{formatPillDate(draftStart, 'Chọn ngày')}</b>
          </button>
          <button
            type="button"
            className={'u030-pill' + ((!!draftStart && !draftEnd) || pickingEnd ? ' on' : '')}
            onClick={() => {
              if (draftStart) setPickingEnd(true);
              if (draftEnd) {
                const p = partsFromKey(draftEnd);
                setCur({ y: p.y, m: p.m });
              }
            }}
          >
            <small>{endLabel}</small>
            <b className={draftEnd ? '' : 'ph'}>{formatPillDate(draftEnd, 'Chọn ngày')}</b>
          </button>
        </div>
      ) : mode !== 'month' ? (
        <div className="u030-pills is-single">
          <div className="u030-pill on">
            <small>{mode === 'datetime-local' ? 'Ngày đã chọn' : 'Ngày đang chọn'}</small>
            <b className={draftDate ? '' : 'ph'}>{formatPillDate(draftDate, 'Chọn ngày trên lịch')}</b>
          </div>
        </div>
      ) : null}

      <div className="u030-top">
        <div className="u030-m" aria-live="polite">
          {anim.leaving && (
            <span
              key={`out-${anim.seq}`}
              className={`u030-m-label ${anim.dir > 0 ? 'is-leaving-next' : 'is-leaving-prev'}`}
            >
              {leavingHeading}
            </span>
          )}
          <span
            key={`in-${anim.seq}`}
            className={
              'u030-m-label'
              + (anim.dir > 0 ? ' is-entering-next' : anim.dir < 0 ? ' is-entering-prev' : '')
            }
          >
            {monthHeading}
          </span>
        </div>
        <div className="u030-nav">
          <button
            type="button"
            aria-label={mode === 'month' ? 'Năm trước' : 'Tháng trước'}
            onClick={() => (mode === 'month' ? stepYear(-1) : stepMonth(-1))}
          >
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <path d="M10 3.5L5.5 8l4.5 4.5" />
            </svg>
          </button>
          <button
            type="button"
            aria-label={mode === 'month' ? 'Năm sau' : 'Tháng sau'}
            onClick={() => (mode === 'month' ? stepYear(1) : stepMonth(1))}
          >
            <svg viewBox="0 0 16 16" aria-hidden="true">
              <path d="M6 3.5L10.5 8 6 12.5" />
            </svg>
          </button>
        </div>
      </div>

      {mode === 'month' ? (
        <div className="u030-months" role="grid" aria-label={`Các tháng năm ${cur.y}`}>
          {Array.from({ length: 12 }, (_, idx) => {
            const iso = `${cur.y}-${pad2(idx + 1)}`;
            const selected = value.trim() === iso;
            const beforeMin = minYm ? cur.y < minYm.y || (cur.y === minYm.y && idx < minYm.m) : false;
            const afterMax = maxYm ? cur.y > maxYm.y || (cur.y === maxYm.y && idx > maxYm.m) : false;
            const disabled = beforeMin || afterMax;
            return (
              <button
                key={iso}
                type="button"
                disabled={disabled}
                className={'u030-mo' + (selected ? ' is-selected' : '')}
                aria-selected={selected}
                onClick={() => {
                  onSelectMonth?.(iso);
                  onClose();
                }}
              >
                Tháng {idx + 1}
              </button>
            );
          })}
        </div>
      ) : (
        <>
          <div className="u030-wk" aria-hidden="true">
            {WEEKDAYS.map(label => (
              <span key={label}>{label}</span>
            ))}
          </div>
          <div className="u030-view">
            {anim.leaving &&
              renderGrid(
                anim.leaving,
                anim.dir > 0 ? 'is-leaving-next' : 'is-leaving-prev',
              )}
            {renderGrid(
              cur,
              anim.dir > 0 ? 'is-entering-next' : anim.dir < 0 ? 'is-entering-prev' : '',
            )}
          </div>
        </>
      )}

      {mode === 'datetime-local' && (
        <div className="u030-time">
          <span className="u030-time-label">Giờ : Phút{includeSeconds ? ' : Giây' : ''}</span>
          <div className="u030-time-inputs">
            <select
              aria-label="Giờ"
              value={hour}
              onChange={event => setHour(event.target.value)}
            >
              {Array.from({ length: 24 }, (_, n) => pad2(n)).map(h => (
                <option key={h} value={h}>{h}</option>
              ))}
            </select>
            <span>:</span>
            <select
              aria-label="Phút"
              value={minute}
              onChange={event => setMinute(event.target.value)}
            >
              {Array.from({ length: 60 }, (_, n) => pad2(n)).map(m => (
                <option key={m} value={m}>{m}</option>
              ))}
            </select>
            {includeSeconds && (
              <>
                <span>:</span>
                <select
                  aria-label="Giây"
                  value={second}
                  onChange={event => setSecond(event.target.value)}
                >
                  {Array.from({ length: 60 }, (_, n) => pad2(n)).map(s => (
                    <option key={s} value={s}>{s}</option>
                  ))}
                </select>
              </>
            )}
          </div>
        </div>
      )}

      <div className="u030-foot">
        <span className="u030-sum" aria-live="polite">
          {mode === 'range' ? (
            draftStart && draftEnd ? (
              <>
                <b>{rangeSummaryText}</b> · {formatShortDm(draftStart)} – {formatShortDm(draftEnd)}
              </>
            ) : (
              rangeSummaryText
            )
          ) : mode === 'month' ? (
            value ? `Đã chọn Tháng ${parseYm(value) ? parseYm(value)!.m + 1 : ''}/${parseYm(value)?.y || ''}` : 'Chọn tháng'
          ) : draftDate ? (
            <><b>Hôm nay:</b> {formatShortDm(todayKey)}</>
          ) : (
            'Chọn một ngày'
          )}
        </span>
        <div className="u030-foot-actions">
          {mode === 'date' && !isDisabledDay(todayKey) && (
            <button
              type="button"
              className="u030-ghost"
              onClick={() => handleDayPick(todayKey)}
            >
              Hôm nay
            </button>
          )}
          {mode === 'date' && !required && draftDate && (
            <button
              type="button"
              className="u030-ghost"
              onClick={() => {
                onSelectDate?.('');
                onClose();
              }}
            >
              Xóa
            </button>
          )}
          {mode === 'range' && (
            <button
              type="button"
              className="u030-ok"
              aria-disabled={!rangeReady}
              onClick={commitRange}
            >
              Áp dụng
            </button>
          )}
          {mode === 'datetime-local' && (
            <button
              type="button"
              className="u030-ok"
              aria-disabled={!draftDate}
              onClick={commitDateTime}
            >
              Áp dụng
            </button>
          )}
        </div>
      </div>
    </div>,
    document.body,
  );
}
