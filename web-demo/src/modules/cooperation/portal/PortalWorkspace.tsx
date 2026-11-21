import { useEffect, useId, useState, type ReactNode } from 'react';
import { EmptyState, Icon, Notice } from '../../../components';
import { ScopedDemands } from '../demand/ScopedDemands';
import { canLeaveDemand } from '../navigation';
import { CooperationFlows, VerificationFlows } from '../WorkflowViews';
import { PortalDashboard } from '../PortalDashboards';
import { StudentRosterPanel } from './StudentRosterPanel';
import { usePortalData } from './data';
import { ApprovedCooperations } from './ApprovedCooperations';
import { ProtectedStatistics } from './ProtectedStatistics';
import { NotificationCenter } from '../notifications/NotificationCenter';
import { NotificationBell } from '../notifications/NotificationBell';
import { NotificationScopeFields } from '../notifications/NotificationScopeFields';
import { useNotifications } from '../notifications/useNotifications';
import type { PortalNotification, NotificationTarget } from '../notifications/notificationService';
import { pagesFor, type PortalPage, type PortalSide } from './navigation';
import { usePortalOrganizations } from './usePortalOrganizations';
import type { Grant } from '../../../api';
import { MatchingWorkspace } from '../../matching/MatchingWorkspace';
import { useSurfaceSpotlight } from '../../../ui/useSurfaceSpotlight';
import { portalSpotlightTargets } from '../../../ui/workspaceSurfaces';
import { EnterprisePageHeader } from './EnterprisePageHeader';
import './portal.css';
import './features.css';
import '../notifications/notifications.css';
import './context-bar.css';
import './motion.css';
import './school-layout.css';
import './enterprise-layout.css';
import './enterprise-polish.css';
import './typography.css';

const schoolPageMeta: Partial<Record<PortalPage, { kicker: string; subtitle: string }>> = {
  students: { kicker: 'QUẢN LÝ SINH VIÊN', subtitle: 'Nhập danh sách theo kỳ, đối soát dữ liệu hợp lệ và theo dõi kích hoạt tài khoản.' },
  verifications: { kicker: 'QUẢN LÝ NHÀ TRƯỜNG', subtitle: 'Kiểm tra hồ sơ xác minh doanh nghiệp và xử lý yêu cầu' },
  cooperations: { kicker: 'QUẢN LÝ NHÀ TRƯỜNG', subtitle: 'Xét duyệt đề nghị hợp tác thực tập.' },
  matching: { kicker: 'SÀNG LỌC & CHẤM ĐIỂM', subtitle: 'Phân tích mức độ khớp tiêu chí & kỹ năng của sinh viên trước khi đề xuất.' },
  proposals: { kicker: 'ĐIỀU PHỐI ỨNG VIÊN', subtitle: 'Xét điều kiện thực tập, lập danh sách đề xuất, gửi lời mời và chia sẻ hồ sơ.' },
  placements: { kicker: 'QUẢN LÝ NHÀ TRƯỜNG', subtitle: 'Phê duyệt tiếp nhận và theo dõi thực tập.' },
  notifications: { kicker: 'QUẢN LÝ NHÀ TRƯỜNG', subtitle: 'Theo dõi cập nhật và hồ sơ cần xử lý.' },
};

const enterprisePageMeta: Partial<Record<PortalPage, { kicker: string; subtitle: string }>> = {
  verifications: { kicker: 'CỔNG DOANH NGHIỆP', subtitle: 'Quản lý hồ sơ xác minh độc lập tại từng trường tiếp nhận.' },
  demands: { kicker: 'CỔNG DOANH NGHIỆP', subtitle: 'Tạo, chỉnh sửa bản nháp nhu cầu tuyển dụng và gửi đề nghị hợp tác.' },
  statistics: { kicker: 'THỐNG KÊ ĐƯỢC BẢO VỆ', subtitle: 'Theo dõi số lượng ứng viên phù hợp, bảo đảm quyền riêng tư của sinh viên.' },
  cooperations: { kicker: 'CỔNG DOANH NGHIỆP', subtitle: 'Theo dõi đề nghị hợp tác và phản hồi từ nhà trường.' },
  proposals: { kicker: 'ĐIỀU PHỐI ỨNG VIÊN', subtitle: 'Xem hồ sơ ứng viên được chia sẻ theo từng hợp tác đã duyệt và phản hồi tiếp nhận.' },
  placements: { kicker: 'THỰC TẬP & BÀN GIAO', subtitle: 'Theo dõi sinh viên được tiếp nhận và tiến độ thực tập.' },
  notifications: { kicker: 'TRUNG TÂM THÔNG BÁO', subtitle: 'Theo dõi cập nhật hồ sơ xác minh, hợp tác, ứng viên và tiếp nhận thực tập.' },
};

export function PortalWorkspace({ organizations, side, page, navigate, viewerId, grants = [] }: {
  organizations: string[]; side: PortalSide; page: PortalPage; navigate: (page: PortalPage) => void; viewerId: string; grants?: Grant[];
  user?: { displayName: string; email?: string };
}) {
  const [selected, setSelected] = useState(organizations[0] || '');
  const scope = organizations.includes(selected) ? selected : organizations[0];
  const surface = useSurfaceSpotlight<HTMLDivElement>(!!scope, portalSpotlightTargets, 'portal');
  const id = useId();
  const organizationNames = usePortalOrganizations(viewerId, side, organizations);
  if (!scope) return <section className="panel"><EmptyState icon="lock" title="Chưa có quyền truy cập"
    description="Bạn cần vai trò và phạm vi tổ chức được cấp để sử dụng cổng này." /></section>;
  return <div className={'cooperation-workspace portal-workspace has-portal-motion is-' + side} ref={surface}>
    <ScopedPortal key={scope + side} org={scope} side={side} page={page} navigate={navigate} viewerId={viewerId} grants={grants}
      organizationPicker={<div className="portal-organization-field"><span className="portal-organization-icon"><Icon name={side === 'school' ? 'graduation' : 'building'} size={22} /></span>
        <div className="portal-context-select"><label htmlFor={id}>{side === 'school' ? 'Nhà trường' : 'Doanh nghiệp'} đang làm việc</label>
          <select id={id} value={scope} title={organizationNames.names[scope] || (side === 'school' ? 'Nhà trường đang làm việc' : 'Doanh nghiệp đang làm việc')} aria-busy={organizationNames.loading}
            onChange={event => { if (canLeaveDemand()) setSelected(event.target.value); }}>
            {organizations.map((org, index) => <option key={org} value={org}>
              {organizationNames.names[org] || (organizationNames.loading ? 'Đang tải tên tổ chức…' : `${side === 'school' ? 'Nhà trường' : 'Doanh nghiệp'} ${index + 1}`)}
            </option>)}
          </select></div>
      </div>} />
  </div>;
}

function ScopedPortal({ org, side, page, navigate, viewerId, organizationPicker, grants }: {
  org: string; side: PortalSide; page: PortalPage; navigate: (page: PortalPage) => void; viewerId: string; organizationPicker: ReactNode; grants: Grant[];
}) {
  const school = side === 'school';
  const [scope, setScope] = useState({ schoolOrgId: side === 'school' ? org : '', termId: '' });
  const termId = school ? scope.termId : '';
  const data = usePortalData(org, side, termId);
  const [revision, setRevision] = useState(0), [cooperationId, setCooperationId] = useState('');
  const [focus, setFocus] = useState<{ key: string; target: NotificationTarget } | null>(null);
  const notifications = useNotifications({ org, side, viewerId, ...scope }, data);
  const meta = pagesFor(side).find(item => item.id === page) || pagesFor(side)[0];
  const pageMeta = page !== 'overview' ? (school ? schoolPageMeta : enterprisePageMeta)[page] : undefined;
  const changeScope = (schoolOrgId: string, selectedTermId: string) => {
    setScope({ schoolOrgId, termId: selectedTermId });
    if (school) {setCooperationId('');setFocus(null);}
  };
  useEffect(() => { if (focus && focus.target.page !== page) setFocus(null); }, [page, focus]);
  const openWorkflow = (target: NotificationTarget, key: string) => {
    setFocus({ key, target });
    if (target.cooperationId) setCooperationId(target.cooperationId);
    navigate(target.page);
  };
  const openNotification = (notification: PortalNotification) => {
    if (!canLeaveDemand()) return;
    notifications.markRead([notification.id]);
    openWorkflow(notification.target, notification.id);
  };
  const openTask = (target: NotificationTarget) => {
    if (canLeaveDemand()) openWorkflow(target, 'task:' + target.page + ':' + target.recordId);
  };
  const refresh = () => { if (canLeaveDemand()) { data.reload();setRevision(value => value + 1); } };
  const pageHeader = school ? <header className={'portal-page-header' + (page === 'overview' ? ' is-overview' : '')}><div><p className="portal-breadcrumb">{pageMeta ? pageMeta.kicker : `Cổng nhà trường / ${meta.title}`}</p>
      {page !== 'overview' && <h2>{page === 'notifications' ? 'Trung tâm thông báo' : meta.title}</h2>}
      {pageMeta && <p className="portal-page-subtitle">{pageMeta.subtitle}</p>}</div>
      {['overview', 'statistics', 'notifications'].includes(page) && (
        <div className="button-row">
          <button className="secondary" onClick={refresh}><Icon name="refresh" size={16} />Tải lại dữ liệu</button>
        </div>
      )}
      {page !== 'overview' && page !== 'notifications' && (
        <span className="portal-welcome-icon" aria-hidden="true"><Icon name={meta.icon} size={32} /></span>
      )}
    </header> : <EnterprisePageHeader
      title={page === 'overview' ? 'Tổng quan doanh nghiệp' : page === 'notifications' ? 'Trung tâm thông báo' : meta.title}
      breadcrumb={meta.title} kicker={pageMeta?.kicker || 'CỔNG DOANH NGHIỆP'}
      description={pageMeta?.subtitle || 'Theo dõi nhu cầu, hợp tác và tiến độ tiếp nhận sinh viên.'}
      icon={page === 'overview' ? 'building' : meta.icon}
      refresh={['overview', 'statistics', 'notifications'].includes(page) ? refresh : undefined} />;
  const pageContent = <>
    {page === 'overview' && <PortalDashboard side={side} organizationId={org} revision={data.revision} demands={data.demands} cooperations={data.cooperations} verifications={data.verifications} navigate={navigate} openTask={openTask} notifications={notifications} termId={termId} />}
    {page === 'students' && school && <StudentRosterPanel org={org} viewerId={viewerId} embedded />}
    {page === 'demands' && !school && <ScopedDemands key={'d:' + revision} organizationId={org} cooperations={data.cooperations} />}
    {page === 'statistics' && !school && <ProtectedStatistics org={org} demands={data.demands} navigate={navigate} />}
    {page === 'matching' && school && <MatchingWorkspace key={termId} organizationId={org} grants={grants} termId={termId} onOpenProposals={coopId => { setCooperationId(coopId); navigate('proposals'); }} />}
    {page === 'notifications' && <NotificationCenter store={notifications} open={openNotification} refresh={refresh} />}
    {page === 'verifications' && <>
      {!school && <div className="enterprise-info-banner" role="note"><Icon name="shield" size={20} /><div>
        <strong>Xác minh độc lập tại từng trường</strong><p>Kết quả xác minh chỉ áp dụng tại trường đã xét duyệt hồ sơ.</p>
      </div></div>}
      <VerificationFlows key={'v:' + revision + (focus?.key || '')} org={org} school={school} focusId={focus?.target.recordId} />
    </>}
    {page === 'cooperations' && <>
      {!school && <Notice tone="error" message={data.demands.error} />}
      <CooperationFlows key={'c:' + revision + ':' + termId + (focus?.key || '')} org={org} school={school} demands={data.demands.items} includeProgress={false} focusId={focus?.target.recordId} termId={termId} />
    </>}
    {(page === 'proposals' || page === 'placements') && <>
      {!school && page === 'placements' && <div className="enterprise-info-banner" role="note"><Icon name="graduation" size={20} /><div>
        <strong>Nhà trường quản lý việc tạo tiếp nhận</strong><p>Doanh nghiệp theo dõi kết quả tiếp nhận và bàn giao sau khi nhà trường phê duyệt.</p>
      </div></div>}
      <ApprovedCooperations key={page + revision + ':' + termId} org={org} side={side} termId={school ? termId : undefined} onTermChange={school ? value => changeScope(org, value) : undefined}
        placements={page === 'placements'} records={data.cooperations} demands={data.demands.items} selected={cooperationId} select={setCooperationId} focusProposalId={focus?.target.proposalId} />
    </>}
  </>;
  return <>
    <div className={'portal-context-bar is-' + side}><div className="portal-context-group">{organizationPicker}
      <NotificationScopeFields org={org} side={side} {...scope} change={(schoolOrgId, value) => { if (canLeaveDemand()) changeScope(schoolOrgId, value); }} />
    </div><div className="portal-context-actions"><NotificationBell store={notifications} open={() => navigate('notifications')} />
    </div></div>
    {school && page !== 'overview' ? <div key={page} className={'portal-subpage-sheet school-page-' + page}>{pageHeader}{pageContent}</div>
      : !school && page !== 'overview' ? <div key={page} className="portal-page-stage">{pageHeader}<div className={'portal-subpage-sheet enterprise-page enterprise-page-' + page}>{pageContent}</div></div>
      : <div key="overview" className="portal-page-stage is-overview">{pageHeader}{pageContent}</div>}
  </>;
}
