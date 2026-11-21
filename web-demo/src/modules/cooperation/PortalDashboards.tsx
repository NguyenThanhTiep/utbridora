import { EmptyState, Icon, Notice } from '../../components';
import type { CooperationRecord, VerificationRecord } from './WorkflowViews';
import type { Demand } from './types';
import type { Resource } from './portal/data';
import type { PortalPage, PortalSide } from './portal/navigation';
import type { NotificationStore } from './notifications/useNotifications';
import type { NotificationTarget } from './notifications/notificationService';
import { useCooperationTitles } from './workflow/useCooperationTitles';
import { SchoolOverviewCards } from './portal/SchoolOverviewCards';

export function PortalDashboard({ side, organizationId, revision, demands, cooperations, verifications, navigate, openTask, notifications, termId = '' }: {
  organizationId: string; revision: number; termId?: string;
  side: PortalSide; demands: Resource<Demand>; cooperations: Resource<CooperationRecord>;
  verifications: Resource<VerificationRecord>; navigate: (page: PortalPage) => void; openTask: (target: NotificationTarget) => void; notifications?: NotificationStore;
}) {
  const school = side === 'school';
  const demandTitleById = new Map(demands.items.map(item => [item.demandId, item.data.title]));
  const pendingCooperations = cooperations.items.filter(row => row.state === (school ? 'SUBMITTED' : 'NEEDS_CHANGES'));
  const titles = useCooperationTitles(organizationId, school, pendingCooperations);
  const enterpriseNames = new Map(verifications.items.filter(row => row.state !== 'DRAFT' && row.enterpriseOrgId && row.data.registeredName)
    .map(row => [row.enterpriseOrgId, row.data.registeredName]));
  const cards = [
    { title: school ? 'Hồ sơ chờ xác minh' : 'Xác minh cần bổ sung', icon: 'shield', page: 'verifications' as const,
      resource: verifications, value: verifications.items.filter(row => row.state === (school ? 'SUBMITTED' : 'NEEDS_CHANGES')).length, tone: 'mint',
      hint: 'Hồ sơ cần bổ sung hoặc cập nhật' },
    { title: school ? 'Hợp tác chờ xét duyệt' : 'Bản nháp nhu cầu', icon: 'briefcase', page: school ? 'cooperations' as const : 'demands' as const,
      resource: school ? cooperations : demands, value: school ? cooperations.items.filter(row => row.state === 'SUBMITTED').length : demands.items.filter(row => row.state === 'DRAFT' && !row.softDeletedAt).length, tone: 'blue',
      hint: 'Nhu cầu đang soạn thảo theo kỳ' },
    { title: 'Hợp tác đã duyệt', icon: 'users', page: 'proposals' as const, resource: cooperations,
      value: cooperations.items.filter(row => row.state === 'APPROVED').length, tone: 'violet',
      hint: 'Sẵn sàng xem hồ sơ ứng viên' },
  ];
  const tasks = [
    ...verifications.items.filter(row => row.state === (school ? 'SUBMITTED' : 'NEEDS_CHANGES')).map(row => ({
      id: row.verificationId, title: row.data.registeredName || 'Hồ sơ xác minh doanh nghiệp',
      description: 'Hồ sơ xác minh doanh nghiệp', status: school ? 'Chờ xác minh' : 'Cần bổ sung hồ sơ', page: 'verifications' as const,
      target: { page: 'verifications' as const, recordId: row.verificationId },
    })),
    ...pendingCooperations.map(row => {
      const demandTitle = titles.names[row.cooperationId] || demandTitleById.get(row.demandId);
      const enterpriseName = row.enterpriseOrgId ? enterpriseNames.get(row.enterpriseOrgId) : undefined;
      return {
        id: row.cooperationId,
        title: demandTitle || 'Đề nghị hợp tác',
        description: [enterpriseName, !demandTitle && titles.loading ? 'Đang tải tên nhu cầu…' : 'Đề nghị hợp tác', 'Lần gửi ' + row.submissionNo].filter(Boolean).join(' · '),
        status: school ? 'Chờ xét duyệt' : 'Cần bổ sung',
        page: 'cooperations' as const,
        target: { page: 'cooperations' as const, recordId: row.cooperationId, cooperationId: row.cooperationId },
      };
    }),
  ];
  const failed = [verifications.error, cooperations.error, ...(!school ? [demands.error] : [])].filter(Boolean);
  const loading = verifications.loading || cooperations.loading || (!school && demands.loading);
  const taskSection = <section className="panel portal-dashboard-tasks">
    <div className="school-section-head">
      <div className="school-section-title">
        <span className="school-section-icon is-tasks" aria-hidden="true"><Icon name="clock" size={18} /></span>
        <h2>Công việc cần xử lý</h2>
      </div>
      {!loading && tasks.length > 0 && <span className="school-task-counter">{tasks.length} mục chờ</span>}
    </div>
    {loading ? <p role="status">Đang tải công việc…</p> : tasks.length ? <div className="proposal-table"><table>
      <thead><tr><th>Nội dung</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{tasks.map(task => <tr key={task.page + ':' + task.id} className={!school ? 'enterprise-task' : undefined}>
        <td>
          <div className="school-task-item">
            <span className={'school-task-type-icon is-' + task.page} aria-hidden="true">
              <Icon name={task.page === 'verifications' ? 'shield' : 'briefcase'} size={17} />
            </span>
            <div className="school-task-item-text">
              {school ? <strong className="portal-task-title">{task.title}</strong>
                : <h3 className="portal-task-title">{task.title}</h3>}
              <span className="portal-task-description">{task.description}</span>
            </div>
          </div>
        </td>
        <td><span className="t5-badge t5-badge-warning">{task.status}</span></td>
        <td><button type="button" className="secondary school-task-action-btn" title={'Xem hồ sơ: ' + task.title} onClick={() => openTask(task.target)}>Xem hồ sơ<Icon name="arrow-right" size={14} /></button></td>
      </tr>)}</tbody>
    </table></div> : !failed.length ? <EmptyState icon="shield" title="Không có hồ sơ cần xử lý" description={school ? 'Các hồ sơ mới sẽ xuất hiện tại đây khi được gửi lên hệ thống.' : 'Hồ sơ cần bổ sung và phản hồi mới từ nhà trường sẽ hiển thị tại đây.'} /> : null}
  </section>;
  const shortcutSection = <section className="panel" aria-label="Truy cập nhanh">
    <div className="school-section-head">
      <div className="school-section-title">
        <span className="school-section-icon is-shortcuts" aria-hidden="true"><Icon name="layers" size={18} /></span>
        <h2>Truy cập nhanh</h2>
      </div>
    </div>
    <div className="portal-shortcuts">
    {school ? <>
      <button className="secondary school-shortcut portal-shortcut is-tone-teal" aria-label="Duyệt hợp tác doanh nghiệp" onClick={() => navigate('cooperations')}><span className="school-shortcut-icon portal-shortcut-icon"><Icon name="briefcase" size={19} /></span><span><b>Duyệt hợp tác</b><small aria-hidden="true">Xem xét đề nghị hợp tác</small></span><Icon name="chevron-right" size={16} /></button>
      <button className="secondary school-shortcut portal-shortcut is-tone-blue" aria-label="Đề xuất sinh viên" onClick={() => navigate('proposals')}><span className="school-shortcut-icon portal-shortcut-icon"><Icon name="users" size={19} /></span><span><b>Đề xuất sinh viên</b><small aria-hidden="true">Theo dõi đề xuất và chỉ tiêu</small></span><Icon name="chevron-right" size={16} /></button>
      <button className="secondary school-shortcut portal-shortcut is-tone-violet" aria-label="Theo dõi tiếp nhận thực tập" onClick={() => navigate('placements')}><span className="school-shortcut-icon portal-shortcut-icon"><Icon name="graduation" size={19} /></span><span><b>Tiếp nhận thực tập</b><small aria-hidden="true">Kiểm tra tiến độ tiếp nhận</small></span><Icon name="chevron-right" size={16} /></button>
    </> : <>
      <button className="secondary portal-shortcut is-tone-teal" aria-label="Gửi yêu cầu hợp tác" onClick={() => navigate('cooperations')}>
        <span className="portal-shortcut-icon"><Icon name="briefcase" size={19} /></span><span><b>Gửi yêu cầu hợp tác</b><small aria-hidden="true">Theo dõi đề nghị và phản hồi từ trường</small></span><Icon name="chevron-right" size={16} /></button>
      <button className="secondary portal-shortcut is-tone-blue" aria-label="Xem ứng viên được đề xuất" onClick={() => navigate('proposals')}>
        <span className="portal-shortcut-icon"><Icon name="users" size={19} /></span><span><b>Xem ứng viên được đề xuất</b><small aria-hidden="true">Hồ sơ được chia sẻ và các lô đề xuất</small></span><Icon name="chevron-right" size={16} /></button>
      <button className="secondary portal-shortcut is-tone-violet" aria-label="Thực tập & bàn giao" onClick={() => navigate('placements')}>
        <span className="portal-shortcut-icon"><Icon name="graduation" size={19} /></span><span><b>Thực tập & bàn giao</b><small aria-hidden="true">Kết quả tiếp nhận và không gian thực tập</small></span><Icon name="chevron-right" size={16} /></button>
    </>}
  </div></section>;
  return <>
    {school && <section className="portal-welcome">
      <div><p className="section-kicker">CỔNG NHÀ TRƯỜNG</p>
        <h2>Tổng quan Nhà trường</h2>
        <p>Theo dõi hồ sơ, nhu cầu và các bước hợp tác với nhà trường.</p></div>
      <span className="portal-welcome-icon" aria-hidden="true"><Icon name="graduation" size={38} /></span>
    </section>}
    {!school && notifications && !notifications.loading && !notifications.errors.length && notifications.unread > 0 && <section className="notification-banner">
      <span className="portal-banner-icon" aria-hidden="true"><Icon name="bell" size={20} /></span>
      <div><strong>Bạn có {notifications.unread} thông báo chưa đọc</strong>
        <p>Hồ sơ xác minh, hợp tác và ứng viên có cập nhật mới.</p></div>
      <button className="t5-btn-teal" onClick={() => navigate('notifications')}>Xem thông báo</button>
    </section>}
    {school ? <SchoolOverviewCards organizationId={organizationId} revision={revision} cooperations={cooperations} verifications={verifications} navigate={navigate} termId={termId} />
      : <div className="portal-stat-grid">{cards.map(card => <button key={card.title} className={'portal-stat tone-' + card.tone}
      onClick={() => navigate(card.page)}>
      <span className="portal-stat-title"><span>{card.title}</span><span className="portal-stat-icon" aria-hidden="true"><Icon name={card.icon} size={18} /></span></span>
      <strong>{card.resource.loading || card.resource.error ? '—' : card.value}</strong>
      <small className="portal-stat-sub">{card.hint}</small>
      <span className="portal-stat-action">Xem chi tiết <Icon name="arrow-right" size={14} /></span>
    </button>)}</div>}
    {failed.map((error, index) => <Notice key={index} tone="error" message={error} />)}
    {school && termId && failed.length > 0 && (
      <p className="muted" role="note">
        Lưu ý: Nếu kỳ đang chọn ở trạng thái Đã đóng/Nháp hoặc tài khoản chưa được ủy quyền trong riêng kỳ đó, dữ liệu của kỳ sẽ bị ẩn theo phạm vi ủy quyền. Hãy chọn «Tất cả kỳ» hoặc một kỳ đang hoạt động.
      </p>
    )}
    {school ? <div className="school-dashboard-grid">{taskSection}{shortcutSection}</div> : <div className="enterprise-dashboard-grid">{taskSection}{shortcutSection}</div>}
  </>;
}
