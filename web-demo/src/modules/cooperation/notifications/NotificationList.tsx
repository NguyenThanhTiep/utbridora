import { EmptyState, Icon } from '../../../components';
import type { PortalNotification } from './notificationService';
import { recordRow } from '../ui/recordRow';

const categories: Record<PortalNotification['category'], { label: string; icon: string }> = {
  verification: { label: 'Xác minh', icon: 'shield' },
  cooperation: { label: 'Hợp tác', icon: 'briefcase' },
  proposal: { label: 'Ứng viên', icon: 'users' },
  placement: { label: 'Tiếp nhận thực tập', icon: 'graduation' },
};

export function NotificationList({ items, read, loading, hasErrors, hasItems, open, markRead }: {
  items: PortalNotification[]; read: Set<string>; loading: boolean; hasErrors: boolean; hasItems: boolean;
  open: (item: PortalNotification) => void; markRead: (ids: string[]) => void;
}) {
  return <div className="proposal-table notification-table"><table>
    <caption className="notification-sr-only">Danh sách thông báo trong phạm vi đang xem</caption>
    <thead><tr><th>Thông báo</th><th>Nội dung liên quan</th><th>Thời gian</th><th>Trạng thái / Hành động</th></tr></thead>
    <tbody>{items.map(item => {
      const isRead = read.has(item.id), category = categories[item.category];
      return <tr key={item.id} className={isRead ? 'notification-read' : 'notification-unread'} {...recordRow(() => open(item), loading)}>
        <th scope="row" data-label="Thông báo"><div className="notification-message">
          <span className={'notification-message-icon tone-' + item.tone} aria-hidden="true"><Icon name={category.icon} size={20} /></span>
          <div className="notification-message-content"><div className="notification-message-meta"><span>{category.label}</span>
            {!isRead && <span className="notification-unread-dot" aria-hidden="true" />}</div>
            <span className="notification-kind">{item.title}</span><p className="notification-description">{item.description}</p>
          </div>
        </div></th>
        <td data-label="Nội dung liên quan"><span className="notification-related">{item.related}</span></td>
        <td data-label="Thời gian">{item.occurredAt ? <time dateTime={item.occurredAt}>
          <span>{new Date(item.occurredAt).toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })}</span>
          <span>{new Date(item.occurredAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}</span>
        </time> : <span className="notification-time-unknown" title="Chưa có thời gian phát sinh trong hồ sơ">Chưa ghi nhận</span>}</td>
        <td data-label="Trạng thái / Hành động"><div className="notification-row-actions">
          <span className={'notification-state ' + (isRead ? 'is-read' : 'is-unread')}><span aria-hidden="true" className="notification-state-dot" />{isRead ? 'Đã đọc' : 'Chưa đọc'}</span>
          <div className="button-row"><button type="button" className="secondary notification-open" aria-label={'Mở hồ sơ ' + item.title} onClick={() => open(item)}>Mở hồ sơ<Icon name="arrow-right" size={15} /></button>
            {!isRead && <button type="button" className="text-button notification-mark-read" aria-label={'Đánh dấu đã đọc ' + item.title} onClick={() => markRead([item.id])}><Icon name="check" size={15} />Đánh dấu đã đọc</button>}
          </div>
        </div></td>
      </tr>;
    })}{!loading && !items.length && !hasErrors && <tr className="notification-empty"><td colSpan={4}><EmptyState icon="bell"
      title={hasItems ? 'Không có thông báo phù hợp' : 'Chưa có thông báo nào.'}
      description={hasItems ? 'Thử thay đổi bộ lọc hoặc từ khóa.' : 'Khi có hồ sơ cần xử lý, thông báo sẽ hiển thị tại đây.'} /></td></tr>}</tbody>
  </table></div>;
}
