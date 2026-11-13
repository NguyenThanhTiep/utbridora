import { useId, useState } from 'react';
import { EmptyState, Icon, Notice } from '../../../components';
import { WorkflowTabs } from '../ui/WorkflowTabs';
import type { PortalNotification } from './notificationService';
import type { NotificationStore } from './useNotifications';

const tabs = [{ value: 'all', label: 'Tất cả' }, { value: 'unread', label: 'Chưa đọc' }, { value: 'read', label: 'Đã đọc' }] as const;
export function NotificationCenter({ store, open }: { store: NotificationStore; open: (notification: PortalNotification) => void }) {
  const id = useId();
  const [tab, setTab] = useState<typeof tabs[number]['value']>('all');
  const [search, setSearch] = useState(''), [category, setCategory] = useState('');
  const rows = store.items.filter(row => (tab === 'all' || (tab === 'read') === store.read.has(row.id))
    && (!category || row.category === category)
    && (!search.trim() || `${row.title} ${row.description} ${row.related}`.toLocaleLowerCase('vi').includes(search.trim().toLocaleLowerCase('vi'))));
  return <section className="panel notification-center">
    <WorkflowTabs label="Trạng thái thông báo" value={tab} options={tabs} change={setTab}>
      <div className="portal-list-toolbar"><div className="portal-search"><label htmlFor={id + '-search'}>Tìm thông báo</label>
        <input id={id + '-search'} type="search" placeholder="Tìm theo nội dung…" value={search} onChange={event => setSearch(event.target.value)} /></div>
        <div><label htmlFor={id + '-category'}>Loại thông báo</label><select id={id + '-category'} value={category} onChange={event => setCategory(event.target.value)}>
          <option value="">Tất cả loại</option><option value="verification">Xác minh</option><option value="cooperation">Hợp tác</option>
          <option value="proposal">Ứng viên</option><option value="placement">Tiếp nhận thực tập</option>
        </select></div>
        <button className="secondary" disabled={store.loading || !!store.errors.length || !store.unread} onClick={store.markAllRead}><Icon name="check-circle" size={16} />Đánh dấu tất cả đã đọc</button>
      </div>
      {store.errors.map(error => <Notice key={error} tone="error" message={error} />)}
      {store.loading && <p role="status">Đang tải thông báo…</p>}
      <div className="proposal-table"><table><thead><tr><th>Thông báo</th><th>Nội dung liên quan</th><th>Thời gian</th><th>Trạng thái / Hành động</th></tr></thead>
        <tbody>{rows.map(row => <tr key={row.id} className={store.read.has(row.id) ? 'notification-read' : 'notification-unread'}>
          <th scope="row"><span className={'notification-kind tone-' + row.tone}>{row.title}</span><p className="notification-description">{row.description}</p></th>
          <td>{row.related}</td><td>{row.occurredAt ? <time dateTime={row.occurredAt}>{new Date(row.occurredAt).toLocaleString('vi-VN')}</time> : <span title="Chưa có thời gian phát sinh trong hồ sơ">—</span>}</td>
          <td><span className={'notification-state ' + (store.read.has(row.id) ? 'is-read' : 'is-unread')}>{store.read.has(row.id) ? 'Đã đọc' : 'Chưa đọc'}</span>
            <div className="button-row"><button className="secondary" aria-label={'Mở hồ sơ ' + row.title} onClick={() => open(row)}>Mở hồ sơ</button>
              {!store.read.has(row.id) && <button className="text-button" aria-label={'Đánh dấu đã đọc ' + row.title} onClick={() => store.markRead([row.id])}>Đánh dấu đã đọc</button>}</div>
          </td>
        </tr>)}{!store.loading && !rows.length && !store.errors.length && <tr><td colSpan={4}><EmptyState icon="bell"
          title={store.items.length ? 'Không có thông báo phù hợp' : 'Chưa có thông báo nào.'}
          description={store.items.length ? 'Thử thay đổi bộ lọc hoặc từ khóa.' : 'Khi có hồ sơ cần xử lý, thông báo sẽ hiển thị tại đây.'} /></td></tr>}</tbody>
      </table></div><p className="field-hint">Trạng thái đã đọc được lưu riêng trên trình duyệt này. Danh sách phản ánh trạng thái hồ sơ hiện tại.</p>
    </WorkflowTabs>
  </section>;
}
