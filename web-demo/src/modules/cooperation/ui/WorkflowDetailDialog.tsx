import { useLayoutEffect, useRef, type ReactNode } from 'react';
import { canLeaveDemand } from '../navigation';
import './workflow-detail-dialog.css';

const openDialogs = new Set<HTMLDialogElement>();
let scrollStyles: { overflow: string; gutter: string } | undefined;

/** Shared T5 record viewer. Native modal focus/inert behavior also supports nested details. */
export function WorkflowDetailDialog({ label, labelledBy, onClose, blocked = false, compact = false, className = '', children }: {
  label?: string; labelledBy?: string; onClose: () => void; blocked?: boolean;
  compact?: boolean; className?: string; children: ReactNode;
}) {
  const ref = useRef<HTMLDialogElement>(null), backdropDown = useRef(false);
  const close = () => { if (!blocked && canLeaveDemand()) onClose(); };
  useLayoutEffect(() => {
    const node = ref.current;
    if (!node) return;
    const previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    if (!node.open) node.showModal();
    if (!openDialogs.size) {
      const style = document.documentElement.style;
      scrollStyles = { overflow: style.overflow, gutter: style.scrollbarGutter };
      style.overflow = 'hidden'; style.scrollbarGutter = 'stable';
    }
    openDialogs.add(node);
    return () => {
      if (node.open) node.close();
      openDialogs.delete(node);
      if (!openDialogs.size && scrollStyles) {
        document.documentElement.style.overflow = scrollStyles.overflow;
        document.documentElement.style.scrollbarGutter = scrollStyles.gutter;
        scrollStyles = undefined;
      }
      if (previous?.isConnected) previous.focus({ preventScroll: true });
    };
  }, []);
  const outside = (x: number, y: number) => {
    const box = ref.current?.getBoundingClientRect();
    return !!box && (x < box.left || x > box.right || y < box.top || y > box.bottom);
  };
  return <dialog ref={ref} className={'t5-detail-dialog' + (compact ? ' is-compact' : '') + ' ' + className}
    aria-label={label} aria-labelledby={labelledBy} aria-modal="true" tabIndex={-1}
    onKeyDown={event => {
      const node = ref.current;
      if (!node || event.key !== 'Tab' || [...openDialogs].at(-1) !== node) return;
      const controls = [...node.querySelectorAll<HTMLElement>('button, a[href], input, select, textarea, summary, [tabindex]')]
        .filter(control => control.tabIndex >= 0 && !control.matches(':disabled') && control.getClientRects().length > 0 && getComputedStyle(control).visibility !== 'hidden');
      const first = controls[0], last = controls.at(-1);
      if (!first || !last) { event.preventDefault(); node.focus({ preventScroll: true }); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); node.scrollTop = 0; first.focus({ preventScroll: true }); }
      else if (event.shiftKey && (document.activeElement === first || document.activeElement === node)) { event.preventDefault(); node.scrollTop = node.scrollHeight; last.focus({ preventScroll: true }); }
    }}
    onCancel={event => { event.preventDefault(); event.stopPropagation(); close(); }}
    onPointerDown={event => { backdropDown.current = event.target === event.currentTarget && outside(event.clientX, event.clientY); }}
    onClick={event => { if (backdropDown.current && event.target === event.currentTarget && outside(event.clientX, event.clientY)) close(); backdropDown.current = false; }}>
    {children}
  </dialog>;
}
