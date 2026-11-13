import { Icon } from '../../../components';
import type { NotificationStore } from './useNotifications';
export function NotificationBell({ store, open }: { store: NotificationStore; open: () => void }) {
  return <button className="secondary portal-notification-shortcut" aria-label="Mở trung tâm thông báo" onClick={open}>
    <Icon name="bell" size={18} /><span>Thông báo</span>
    {store.loading ? <span className="notification-count is-loading" aria-label="Đang tải số thông báo">…</span>
      : store.errors.length ? <span className="notification-count is-loading" title="Chưa tải đầy đủ thông báo">—</span>
      : store.unread > 0 && <span className="notification-count" aria-label={`${store.unread} thông báo chưa đọc`}>{store.unread}</span>}
  </button>;
}
