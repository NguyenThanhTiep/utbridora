import { useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { pagerSlots, type PagerSlot } from './pagerWindow';
import './pager-nav.css';

type SlotRoll = { id: number; from: PagerSlot; to: PagerSlot; back: boolean };

export function PagerNav({
  page,
  totalPages,
  onChange,
  busy = false,
  totalElements,
  unitLabel = 'kết quả',
  summary,
  prevLabel = 'Trước',
  nextLabel = 'Sau',
  ariaLabel = 'Phân trang',
  extra,
  inline = false,
}: {
  page: number;
  totalPages: number;
  onChange: (nextPage0Based: number) => void;
  busy?: boolean;
  totalElements?: number;
  unitLabel?: string;
  summary?: ReactNode;
  prevLabel?: string;
  nextLabel?: string;
  ariaLabel?: string;
  extra?: ReactNode;
  inline?: boolean;
}) {
  const safeTotal = Math.max(1, totalPages);
  const cur = Math.min(Math.max(1, page + 1), safeTotal);
  const slots = pagerSlots(cur, safeTotal);
  const activeIndex = Math.max(0, slots.indexOf(cur));

  const prevState = useRef<{ cur: number; slots: PagerSlot[] }>({ cur, slots });
  const pillRef = useRef<HTMLSpanElement>(null);
  const seqRef = useRef(0);
  const [rolls, setRolls] = useState<Record<number, SlotRoll>>({});

  useEffect(() => {
    const previous = prevState.current;
    if (previous.cur === cur && previous.slots.length === slots.length && previous.slots.every((v, idx) => v === slots[idx])) {
      return;
    }
    const back = cur < previous.cur;
    if (previous.cur !== cur && pillRef.current) {
      pillRef.current.classList.remove('is-sq');
      void pillRef.current.offsetWidth;
      pillRef.current.classList.add('is-sq');
    }
    const nextRolls: Record<number, SlotRoll> = {};
    slots.forEach((value, idx) => {
      const oldValue = previous.slots[idx];
      if (oldValue !== undefined && oldValue !== value) {
        nextRolls[idx] = { id: ++seqRef.current, from: oldValue, to: value, back };
      }
    });
    prevState.current = { cur, slots };
    if (Object.keys(nextRolls).length === 0) return;
    setRolls(nextRolls);
    const timer = window.setTimeout(() => setRolls({}), 340);
    return () => window.clearTimeout(timer);
  }, [cur, safeTotal, slots.join(',')]);

  const summaryContent = summary !== undefined ? summary
    : totalElements !== undefined
      ? <><strong>{totalElements}</strong> {unitLabel}<span className="u-pager-divider">·</span>Trang {cur}/{safeTotal}</>
      : null;

  const wrapperClass = [
    'u-pager',
    summaryContent ? '' : 'is-standalone',
    inline ? 'is-inline' : '',
  ].filter(Boolean).join(' ');

  return (
    <div className={wrapperClass}>
      {summaryContent && <span className="u-pager-summary">{summaryContent}</span>}
      <div className="u-pager-bar">
        <nav className="u-pager-nav" aria-label={ariaLabel}>
          <button
            type="button"
            className="u-pager-ar u-pager-pv"
            aria-label={prevLabel}
            title={prevLabel}
            disabled={busy || cur <= 1}
            onClick={() => onChange(cur - 2)}
          >
            <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M10 3.5 5.5 8l4.5 4.5" /></svg>
          </button>
          <div
            className="u-pager-sl"
            style={{ '--pg-cols': slots.length, '--pg-index': activeIndex } as CSSProperties}
          >
            <span ref={pillRef} className="u-pager-pill" aria-hidden="true" />
            {slots.map((slot, idx) => {
              const roll = rolls[idx];
              const isRolling = Boolean(roll && roll.to === slot);
              const isDots = slot === null;
              const isCurrent = slot === cur;
              return (
                <button
                  key={idx}
                  type="button"
                  className={'u-pager-n' + (isDots ? ' is-dots' : '') + (isRolling && roll.back ? ' is-back' : '')}
                  tabIndex={isDots ? -1 : 0}
                  aria-hidden={isDots ? 'true' : undefined}
                  aria-label={isDots ? undefined : `Trang ${slot}`}
                  aria-current={isCurrent ? 'page' : undefined}
                  disabled={!isDots && busy && !isCurrent}
                  onClick={() => { if (slot !== null && slot !== cur && !busy) onChange(slot - 1); }}
                >
                  {isRolling ? (
                    <>
                      <span key={`out-${roll.id}`} className="is-out" aria-hidden="true">
                        {roll.from === null ? '…' : roll.from}
                      </span>
                      <span key={`in-${roll.id}`} className="is-in">
                        {slot === null ? '…' : slot}
                      </span>
                    </>
                  ) : (
                    <span key={`st-${idx}`}>{slot === null ? '…' : slot}</span>
                  )}
                </button>
              );
            })}
          </div>
          <button
            type="button"
            className="u-pager-ar u-pager-nx"
            aria-label={nextLabel}
            title={nextLabel}
            disabled={busy || cur >= safeTotal}
            onClick={() => onChange(cur)}
          >
            <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M6 3.5 10.5 8 6 12.5" /></svg>
          </button>
        </nav>
        {extra}
      </div>
      <p className="u-pager-live" aria-live="polite">{`Trang ${cur}/${safeTotal}`}</p>
    </div>
  );
}
