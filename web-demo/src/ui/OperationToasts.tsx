import { useEffect, useRef, useState, useSyncExternalStore, type FocusEvent } from 'react';
import { createPortal } from 'react-dom';
import { Icon } from '../components';
import { dismissOperationFeedback, operationFeedbackStore, type OperationFeedback } from './operationFeedback';
import './operation-toasts.css';

function OperationToast({ item }: { item: OperationFeedback }) {
  const [hovered, setHovered] = useState(false), [focused, setFocused] = useState(false);
  const [hidden, setHidden] = useState(document.hidden);
  const remaining = useRef(0);
  useEffect(() => {
    const changed = () => setHidden(document.hidden);
    document.addEventListener('visibilitychange', changed);
    return () => document.removeEventListener('visibilitychange', changed);
  }, []);
  useEffect(() => { remaining.current = item.tone === 'success' ? 4500 : 7000; }, [item.revision, item.tone]);
  useEffect(() => {
    if (hovered || focused || hidden) return;
    const started = performance.now();
    const timer = window.setTimeout(() => dismissOperationFeedback(item.id), remaining.current);
    return () => { window.clearTimeout(timer); remaining.current = Math.max(0, remaining.current - (performance.now() - started)); };
  }, [item.id, item.revision, hovered, focused, hidden]);
  const blur = (event: FocusEvent) => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) setFocused(false); };
  return <div className={'operation-toast is-' + item.tone} role={item.tone === 'error' ? 'alert' : undefined} aria-live={item.tone === 'error' ? 'assertive' : 'polite'} aria-atomic="true"
    onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocus={() => setFocused(true)} onBlur={blur}>
    <span className="operation-toast-icon"><Icon name={item.tone === 'success' ? 'check' : item.tone === 'error' ? 'alert' : 'info'} size={19} /></span>
    <div className="operation-toast-copy"><strong>{item.title}</strong><p>{item.description}</p></div>
    <button type="button" className="operation-toast-close" aria-label="Đóng thông báo thao tác" onClick={() => dismissOperationFeedback(item.id)}><Icon name="close" size={17} /></button>
  </div>;
}

export function OperationToasts() {
  const items = useSyncExternalStore(operationFeedbackStore.subscribe, operationFeedbackStore.getSnapshot);
  return createPortal(<div className="operation-toasts" aria-label="Thông báo thao tác">{items.map(item => <OperationToast key={item.id} item={item} />)}</div>, document.body);
}
