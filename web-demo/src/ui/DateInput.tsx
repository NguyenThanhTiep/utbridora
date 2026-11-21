import { forwardRef, type InputHTMLAttributes, type MouseEvent } from 'react';
import { formatDateInputDMY } from './dateFormat';
import './DateInput.css';

export type DateInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> & {
  type?: 'date' | 'datetime-local';
  value: string;
  wrapperClassName?: string;
};

type PickerInputElement = HTMLInputElement & { showPicker?: () => void };

/**
 * Wraps native `<input type="date" | "datetime-local">` so day always precedes month
 * (`DD/MM/YYYY`) visually regardless of OS/browser locale while keeping ISO `.value`.
 */
export const DateInput = forwardRef<HTMLInputElement, DateInputProps>(function DateInput(
  { type = 'date', value, className, wrapperClassName, onClick, ...props },
  ref,
) {
  const formatted = formatDateInputDMY(value, type);
  const placeholder = type === 'date' ? 'dd/mm/yyyy' : 'dd/mm/yyyy · --:--';
  const controlClass = [
    'vn-date-control',
    props.disabled ? 'is-disabled' : '',
    wrapperClassName ?? '',
  ].filter(Boolean).join(' ');
  const inputClass = ['vn-date-input', className ?? ''].filter(Boolean).join(' ');

  function handleClick(event: MouseEvent<HTMLInputElement>) {
    onClick?.(event);
    if (event.defaultPrevented || props.disabled || props.readOnly) return;
    const rect = event.currentTarget.getBoundingClientRect();
    // Only invoke showPicker when clicking the formatted overlay area so clicking the native icon doesn't double-toggle.
    if (event.clientX > 0 && event.clientX < rect.right - 34) {
      try {
        (event.currentTarget as PickerInputElement).showPicker?.();
      } catch {
        // Ignore SecurityError / NotAllowedError when triggered outside a user gesture or inside test harnesses.
      }
    }
  }

  return (
    <span className={controlClass}>
      <input
        ref={ref}
        type={type}
        value={value}
        className={inputClass}
        onClick={handleClick}
        {...props}
      />
      <span
        aria-hidden="true"
        className={'vn-date-display' + (formatted ? '' : ' is-placeholder')}
      >
        {formatted || placeholder}
      </span>
    </span>
  );
});
