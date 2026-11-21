import { useId, useState } from 'react';
import { Icon, Notice } from '../../../components';
import { WorkflowTabs } from '../ui/WorkflowTabs';
import type { PortalNotification } from './notificationService';
import type { NotificationStore } from './useNotifications';
import { NotificationList } from './NotificationList';
import './notification-center.css';

const tabs = [{ value: 'all', label: 'Tất cả' }, { value: 'unread', label: 'Chưa đọc' }, { value: 'read', label: 'Đã đọc' }] as const;
export function NotificationCenter({ store, open, refresh }: { store: NotificationStore; open: (notification: PortalNotification) => void; refresh?: () => void }) {
  const id = useId();
  const [tab, setTab] = useState<typeof tabs[number]['value']>('all');
  const [search, setSearch] = useState(''), [category, setCategory] = useState('');
  const rows = store.items.filter(row => (tab === 'all' || (tab === 'read') === store.read.has(row.id))
    && (!category || row.category === category)
    && (!search.trim() || `${row.title} ${row.description} ${row.related}`.toLocaleLowerCase('vi').includes(search.trim().toLocaleLowerCase('vi'))));
  const complete = !store.loading && !store.errors.length;
  return <section className="panel notification-center" aria-label="Danh sách thông báo">
    <header className="notification-inbox-header"><div className="notification-inbox-title"><span className="notification-inbox-icon" aria-hidden="true"><Icon name="bell" size={23} /></span>
      <div><h3>Thông báo của bạn</h3><p>{store.loading ? 'Đang kiểm tra cập nhật hồ sơ…' : store.errors.length ? 'Một số thông báo chưa tải được.'
        : store.unread ? <><strong>{store.unread}</strong> thông báo chưa đọc</> : store.items.length ? 'Bạn đã đọc tất cả thông báo.' : 'Theo dõi cập nhật hồ sơ trong phạm vi của bạn.'}</p></div>
    </div><button type="button" className="secondary notification-mark-all" disabled={!complete || !store.unread} onClick={store.markAllRead}><Icon name="check-circle" size={17} />Đánh dấu tất cả đã đọc</button></header>
    <WorkflowTabs label="Trạng thái thông báo" value={tab} options={tabs} change={setTab}>
      <div className="portal-list-toolbar notification-filters"><div className="portal-search"><label htmlFor={id + '-search'}>Tìm thông báo</label>
        <div className="notification-search-input"><Icon name="search" size={18} /><input id={id + '-search'} type="search" placeholder="Tìm theo nội dung hoặc hồ sơ liên quan…" value={search} onChange={event => setSearch(event.target.value)} /></div></div>
        <div><label htmlFor={id + '-category'}>Loại thông báo</label><select id={id + '-category'} value={category} onChange={event => setCategory(event.target.value)}>
          <option value="">Tất cả loại</option><option value="verification">Xác minh</option><option value="cooperation">Hợp tác</option>
          <option value="proposal">Ứng viên</option><option value="placement">Tiếp nhận thực tập</option>
        </select></div>
        {refresh && <button type="button" className="secondary" disabled={store.loading} onClick={refresh}><Icon name="refresh" size={16} />Làm mới</button>}
      </div>
      {store.errors.map(error => <Notice key={error} tone="error" message={error} />)}
      {store.loading && <p className="notification-loading" role="status"><span className="spinner" />Đang tải thông báo…</p>}
      <div className="notification-results-toolbar">
        {complete && <p className="notification-filter-summary" aria-live="polite">{rows.length} thông báo đang hiển thị · {store.unread} chưa đọc</p>}
        {(search || category) && <button type="button" className="text-button" onClick={() => { setSearch(''); setCategory(''); }}><Icon name="close" size={15} />Xóa bộ lọc</button>}
      </div>
      <NotificationList items={rows} read={store.read} loading={store.loading} hasErrors={!!store.errors.length} hasItems={!!store.items.length} open={open} markRead={store.markRead} />
      <p className="notification-storage-note"><Icon name="info" size={16} /><span>Trạng thái đã đọc được lưu riêng trên trình duyệt này. Danh sách phản ánh trạng thái hồ sơ hiện tại.</span></p>
    </WorkflowTabs>
  </section>;
}
