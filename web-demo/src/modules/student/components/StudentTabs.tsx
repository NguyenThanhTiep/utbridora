import { useRef, type KeyboardEvent, type ReactNode } from 'react';
import '../styles.css';

// Tablist theo mẫu WAI-ARIA: mũi tên trái/phải chuyển tab; chỉ tab đang chọn nằm trong thứ tự Tab.
// Chỉ dựng nội dung tab đang mở để mỗi lần chuyển tab đều đọc lại dữ liệu mới nhất từ server.
export function StudentTabs<T extends string>({ label, idPrefix, tabs, tab, setTab, render }: {
  label: string; idPrefix: string; tabs: { id: T; label: string }[]; tab: T; setTab: (tab: T) => void; render: (tab: T) => ReactNode;
}) {
  const buttons = useRef<Partial<Record<T, HTMLButtonElement | null>>>({});
  const keydown = (event: KeyboardEvent) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const next = tabs[(tabs.findIndex(item => item.id === tab) + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length].id;
    setTab(next); buttons.current[next]?.focus();
  };
  return <div className="student-page">
    <div className="student-tabs" role="tablist" aria-label={label} onKeyDown={keydown}>
      {tabs.map(item => <button key={item.id} type="button" role="tab" id={idPrefix + '-tab-' + item.id} aria-controls={idPrefix + '-tabpanel-' + item.id}
        aria-selected={tab === item.id} tabIndex={tab === item.id ? 0 : -1} ref={node => { buttons.current[item.id] = node; }}
        className={'student-tab' + (tab === item.id ? ' active' : '')} onClick={() => setTab(item.id)}>{item.label}</button>)}
    </div>
    {tabs.map(item => <div key={item.id} role="tabpanel" id={idPrefix + '-tabpanel-' + item.id} aria-labelledby={idPrefix + '-tab-' + item.id} hidden={tab !== item.id}>
      {tab === item.id && render(item.id)}
    </div>)}
  </div>;
}
