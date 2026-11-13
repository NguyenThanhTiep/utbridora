import { EmptyState, Icon, Notice } from '../../components';
import type { CooperationRecord, VerificationRecord } from './WorkflowViews';
import type { Demand } from './types';
import type { Resource } from './portal/data';
import type { PortalPage, PortalSide } from './portal/navigation';
import type { NotificationStore } from './notifications/useNotifications';

export function PortalDashboard({ side, demands, cooperations, verifications, navigate, notifications }: {
  side: PortalSide; demands: Resource<Demand>; cooperations: Resource<CooperationRecord>;
  verifications: Resource<VerificationRecord>; navigate: (page: PortalPage) => void; notifications?: NotificationStore;
}) {
  const school = side === 'school';
  const cards = [
    { title: school ? 'Hồ sơ chờ xác minh' : 'Xác minh cần bổ sung', icon: 'shield', page: 'verifications' as const,
      resource: verifications, value: verifications.items.filter(row => row.state === (school ? 'SUBMITTED' : 'NEEDS_CHANGES')).length, tone: 'mint' },
    { title: school ? 'Hợp tác chờ xét duyệt' : 'Bản nháp nhu cầu', icon: 'briefcase', page: school ? 'cooperations' as const : 'demands' as const,
      resource: school ? cooperations : demands, value: school ? cooperations.items.filter(row => row.state === 'SUBMITTED').length : demands.items.filter(row => row.state === 'DRAFT').length, tone: 'blue' },
    { title: 'Hợp tác đã duyệt', icon: 'users', page: 'proposals' as const, resource: cooperations,
      value: cooperations.items.filter(row => row.state === 'APPROVED').length, tone: 'violet' },
  ];
  const tasks = [
    ...verifications.items.filter(row => row.state === (school ? 'SUBMITTED' : 'NEEDS_CHANGES')).map(row => ({
      id: row.verificationId, title: row.data.registeredName, status: school ? 'Chờ xác minh' : 'Cần bổ sung hồ sơ', page: 'verifications' as const,
    })),
    ...cooperations.items.filter(row => row.state === (school ? 'SUBMITTED' : 'NEEDS_CHANGES')).map(row => ({
      id: row.cooperationId, title: 'Đề nghị hợp tác · ' + row.cooperationId.slice(0, 8), status: school ? 'Chờ xét duyệt' : 'Cần bổ sung', page: 'cooperations' as const,
    })),
  ];
  const failed = [verifications.error, cooperations.error, ...(!school ? [demands.error] : [])].filter(Boolean);
  const loading = verifications.loading || cooperations.loading || (!school && demands.loading);
  return <>
    <section className="portal-welcome">
      <div><h2>{school ? 'Tổng quan Nhà trường' : 'Tổng quan doanh nghiệp'}</h2>
        <p>Theo dõi hồ sơ, nhu cầu và hợp tác thực tập.</p></div>
      <Icon name={school ? 'graduation' : 'building'} size={68} />
    </section>
    {notifications && !notifications.loading && !notifications.errors.length && notifications.unread > 0 && <section className="notification-banner">
      <Icon name="bell" size={24} /><div><strong>Bạn có {notifications.unread} thông báo chưa đọc</strong>
        <p>Hồ sơ xác minh, hợp tác và ứng viên có cập nhật.</p></div>
      <button onClick={() => navigate('notifications')}>Xem thông báo</button>
    </section>}
    <div className="portal-stat-grid">{cards.map(card => <button key={card.title} className={'portal-stat tone-' + card.tone}
      onClick={() => navigate(card.page)}>
      <span className="portal-stat-title"><span>{card.title}</span><Icon name={card.icon} /></span>
      <strong>{card.resource.loading || card.resource.error ? '—' : card.value}</strong><span>Xem chi tiết <Icon name="arrow-right" size={14} /></span>
    </button>)}</div>
    {failed.map((error, index) => <Notice key={index} tone="error" message={error} />)}
    <section className="panel"><h2>Công việc cần xử lý</h2>
      {loading ? <p role="status">Đang tải công việc…</p> : tasks.length ? <div className="proposal-table"><table>
        <thead><tr><th>Nội dung</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
        <tbody>{tasks.map(task => <tr key={task.id}><td>{task.title}</td><td><span className="t5-badge t5-badge-warning">{task.status}</span></td>
          <td><button className="secondary" onClick={() => navigate(task.page)}>Xem hồ sơ</button></td></tr>)}</tbody>
      </table></div> : !failed.length ? <EmptyState icon="shield" title="Không có hồ sơ cần xử lý" description="Các hồ sơ mới sẽ xuất hiện tại đây khi được gửi lên hệ thống." /> : null}
    </section>
    {school && <section className="panel"><h2>Truy cập nhanh</h2><div className="portal-shortcuts">
      <button className="secondary" onClick={() => navigate('cooperations')}><Icon name="building" />{school ? 'Duyệt hợp tác doanh nghiệp' : 'Gửi yêu cầu hợp tác'}</button>
      <button className="secondary" onClick={() => navigate('proposals')}><Icon name="users" />{school ? 'Đề xuất sinh viên' : 'Xem ứng viên được đề xuất'}</button>
      <button className="secondary" onClick={() => navigate('placements')}><Icon name="graduation" />Theo dõi tiếp nhận thực tập</button>
    </div></section>}
  </>;
}
