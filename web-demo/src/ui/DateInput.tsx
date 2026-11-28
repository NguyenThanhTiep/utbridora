import {
  forwardRef,
  useCallback,
  useRef,
  useState,
  type ChangeEvent,
  type InputHTMLAttributes,
  type KeyboardEvent,
  type MouseEvent,
} from 'react';
import { formatDateInputDMY } from './dateFormat';
import { CalendarPopover } from './CalendarPopover';
import './DateInput.css';

export type DateInputRangeConfig = {
  startValue: string;
  endValue: string;
  activeEndpoint: 'start' | 'end';
  startLabel?: string;
  endLabel?: string;
  onApplyRange: (startDate: string, endDate: string) => void;
};

export type DateInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  type?: 'date' | 'datetime-local' | 'month';
  value: string;
  wrapperClassName?: string;
  dateRange?: DateInputRangeConfig;
  pickerLabel?: string;
};

/**
 * Localizes native date, datetime and month fields (`DD/MM/YYYY` or `Tháng M/YYYY`)
 * while enhancing clicks with the Lucian #030 animated calendar/range popover
 * and keeping the underlying HTMLInputElement synchronized for typing and automation.
 */
export const DateInput = forwardRef<HTMLInputElement, DateInputProps>(function DateInput(
  {
    type = 'date',
    value,
    className,
    wrapperClassName,
    onClick,
    onKeyDown,
    onChange,
    dateRange,
    pickerLabel,
    min,
    max,
    step,
    required,
    ...props
  },
  ref,
) {
  const inputRef = useRef<HTMLInputElement | null>(null);
  const wrapperRef = useRef<HTMLSpanElement>(null);
  const [open, setOpen] = useState(false);

  const setMergedRef = useCallback(
    (node: HTMLInputElement | null) => {
      inputRef.current = node;
      if (typeof ref === 'function') ref(node);
      else if (ref) ref.current = node;
    },
    [ref],
  );

  const formatted = formatDateInputDMY(value, type);
  const placeholder = type === 'month' ? 'Chọn tháng/năm' : type === 'date' ? 'dd/mm/yyyy' : 'dd/mm/yyyy · --:--';
  const controlClass = [
    'vn-date-control',
    open ? 'is-open' : '',
    props.disabled ? 'is-disabled' : '',
    props.readOnly ? 'is-readonly' : '',
    wrapperClassName ?? '',
  ]
    .filter(Boolean)
    .join(' ');
  const inputClass = ['vn-date-input', className ?? ''].filter(Boolean).join(' ');

  const emitValueChange = useCallback(
    (nextValue: string) => {
      const input = inputRef.current;
      if (!input) {
        onChange?.({
          target: { value: nextValue },
          currentTarget: { value: nextValue },
        } as ChangeEvent<HTMLInputElement>);
        return;
      }
      const descriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value');
      descriptor?.set?.call(input, nextValue);
      onChange?.({ target: input, currentTarget: input } as ChangeEvent<HTMLInputElement>);
    },
    [onChange],
  );

  const closeAndFocus = useCallback((restoreFocus = false) => {
    setOpen(false);
    if (restoreFocus) {
      requestAnimationFrame(() => inputRef.current?.focus({ preventScroll: true }));
    }
  }, []);

  function handleClick(event: MouseEvent<HTMLInputElement>) {
    onClick?.(event);
    if (event.defaultPrevented || props.disabled || props.readOnly) return;
    event.preventDefault();
    setOpen(prev => !prev);
  }

  function handleKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    onKeyDown?.(event);
    if (event.defaultPrevented || props.disabled || props.readOnly) return;
    if ((event.altKey && event.key === 'ArrowDown') || event.key === ' ') {
      event.preventDefault();
      setOpen(true);
    }
  }

  const mode = dateRange && type === 'date' ? 'range' : type;
  const includeSeconds =
    type === 'datetime-local' &&
    step !== undefined &&
    Number(step) > 0 &&
    Number(step) < 60;

  return (
    <span ref={wrapperRef} className={controlClass}>
      <input
        ref={setMergedRef}
        type={type}
        value={value}
        min={min}
        max={max}
        step={step}
        required={required}
        className={inputClass}
        aria-expanded={!props.disabled && !props.readOnly ? open : undefined}
        onChange={onChange}
        onClick={handleClick}
        onKeyDown={handleKeyDown}
        {...props}
      />
      <span
        aria-hidden="true"
        className={'vn-date-display' + (formatted ? '' : ' is-placeholder')}
      >
        {formatted || placeholder}
      </span>
      <button
        type="button"
        className="vn-date-icon"
        tabIndex={-1}
        aria-hidden="true"
        disabled={props.disabled}
        onMouseDown={event => event.preventDefault()}
        onClick={() => {
          if (!props.disabled && !props.readOnly) setOpen(prev => !prev);
        }}
      >
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
          <rect x="3" y="5" width="18" height="16" rx="2" />
          <path d="M16 3v4M8 3v4M3 11h18" />
        </svg>
      </button>
      <CalendarPopover
        open={open && !props.disabled && !props.readOnly}
        mode={mode}
        anchorRef={wrapperRef}
        value={value}
        startValue={dateRange?.startValue}
        endValue={dateRange?.endValue}
        activeEndpoint={dateRange?.activeEndpoint}
        startLabel={dateRange?.startLabel}
        endLabel={dateRange?.endLabel}
        min={typeof min === 'string' ? min : undefined}
        max={typeof max === 'string' ? max : undefined}
        includeSeconds={includeSeconds}
        required={required}
        ariaLabel={pickerLabel || props['aria-label']}
        onSelectDate={next => emitValueChange(next)}
        onSelectMonth={next => emitValueChange(next)}
        onApplyDateTime={next => emitValueChange(next)}
        onApplyRange={(startDate, endDate) => dateRange?.onApplyRange(startDate, endDate)}
        onClose={() => closeAndFocus(true)}
      />
    </span>
  );
});
