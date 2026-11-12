import { useRef, type KeyboardEvent } from 'react';
import { InvitationConsentPage } from './InvitationConsentPage';
import { PreferencesPage } from './PreferencesPage';
import '../styles.css';

// S06 (SRS 14.1): một màn "Lời mời & nguyện vọng" gồm 2 tab. Chia sẻ hồ sơ nằm trong tab lời mời
// nhưng vẫn là thao tác riêng, tách khỏi nút nhận lời (luồng 06, PROP-16, C-04).
export type StudentResponseTab = 'invitations' | 'preferences';
const tabs: { id: StudentResponseTab; label: string }[] = [{ id: 'invitations', label: 'Lời mời' }, { id: 'preferences', label: 'Nguyện vọng' }];

export function InvitationsPreferencesPage({ tab, setTab }: { tab: StudentResponseTab; setTab: (tab: StudentResponseTab) => void }) {
  const buttons = useRef<Partial<Record<StudentResponseTab, HTMLButtonElement | null>>>({});
  // Mũi tên trái/phải chuyển tab theo mẫu tablist WAI-ARIA; chỉ tab đang chọn nằm trong thứ tự Tab.
  const keydown = (event: KeyboardEvent) => {
    if (event.key !== 'ArrowLeft' && event.key !== 'ArrowRight') return;
    event.preventDefault();
    const next = tabs[(tabs.findIndex(item => item.id === tab) + (event.key === 'ArrowRight' ? 1 : tabs.length - 1)) % tabs.length].id;
    setTab(next); buttons.current[next]?.focus();
  };
  // Chỉ dựng nội dung tab đang mở để mỗi lần chuyển tab đều đọc lại dữ liệu mới nhất từ server.
  return <div className="student-page">
    <div className="student-tabs" role="tablist" aria-label="Lời mời và nguyện vọng" onKeyDown={keydown}>
      {tabs.map(item => <button key={item.id} type="button" role="tab" id={'student-tab-' + item.id} aria-controls={'student-tabpanel-' + item.id}
        aria-selected={tab === item.id} tabIndex={tab === item.id ? 0 : -1} ref={node => { buttons.current[item.id] = node; }}
        className={'student-tab' + (tab === item.id ? ' active' : '')} onClick={() => setTab(item.id)}>{item.label}</button>)}
    </div>
    {tabs.map(item => <div key={item.id} role="tabpanel" id={'student-tabpanel-' + item.id} aria-labelledby={'student-tab-' + item.id} hidden={tab !== item.id}>
      {tab === item.id && (item.id === 'invitations' ? <InvitationConsentPage /> : <PreferencesPage />)}
    </div>)}
  </div>;
}
