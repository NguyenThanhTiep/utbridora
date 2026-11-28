import type { KeyboardEvent, MouseEvent } from 'react';
import './record-row.css';

const controls = 'button, a, input, select, textarea, label, summary, dialog, [role="dialog"], [role="button"], [role="link"], [contenteditable]:not([contenteditable="false"]), [data-no-row-open]';

function recordActivation<T extends HTMLElement>(open: (element: T) => void, disabled: boolean) {
  const activate = (element: T) => { element.focus({ preventScroll: true });open(element); };
  return {
    tabIndex: disabled ? -1 : 0,
    'aria-disabled': disabled || undefined,
    onClick: (event: MouseEvent<T>) => {
      if (disabled || event.defaultPrevented || event.button !== 0 || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey) return;
      if (event.target instanceof Element && event.target.closest(controls)) return;
      const selection = window.getSelection();
      if (selection && !selection.isCollapsed && event.currentTarget.contains(selection.anchorNode)) return;
      activate(event.currentTarget);
    },
    onKeyDown: (event: KeyboardEvent<T>) => {
      if (disabled || event.defaultPrevented || event.repeat || event.target !== event.currentTarget
        || event.ctrlKey || event.metaKey || event.altKey || event.shiftKey || !['Enter', ' '].includes(event.key)) return;
      event.preventDefault();activate(event.currentTarget);
    },
  };
}

/** A record row opens its existing viewer; embedded controls retain their own actions. */
export function recordRow(open: (row: HTMLTableRowElement) => void, disabled = false) {
  return { 'data-record-row': disabled ? 'disabled' : 'ready', ...recordActivation(open, disabled) };
}

/** Cards use the same opening behavior without giving the article button semantics. */
export function recordCard(open: () => void, disabled = false) {
  const activation = recordActivation<HTMLElement>(open, disabled);
  // Disabling the card opener must not disable the dialog and its controls inside it.
  return { 'data-record-card': disabled ? 'disabled' : 'ready', tabIndex: activation.tabIndex,
    onClick: activation.onClick, onKeyDown: activation.onKeyDown };
}
