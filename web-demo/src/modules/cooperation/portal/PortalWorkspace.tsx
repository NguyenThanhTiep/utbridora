import { useEffect, useId, useState, type ReactNode } from 'react';
import { EmptyState, Icon, Notice } from '../../../components';
import { ScopedDemands } from '../demand/ScopedDemands';
import { canLeaveDemand } from '../navigation';
import { CooperationFlows, VerificationFlows } from '../WorkflowViews';
import { PortalDashboard } from '../PortalDashboards';
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
import './portal.css';
import './features.css';
import '../notifications/notifications.css';
import './context-bar.css';

export function PortalWorkspace({ organizations, side, page, navigate, viewerId, grants = [] }: {
  organizations: string[]; side: PortalSide; page: PortalPage; navigate: (page: PortalPage) => void; viewerId: string; grants?: Grant[];
}) {
  const [selected, setSelected] = useState(organizations[0] || '');
  const scope = organizations.includes(selected) ? selected : organizations[0];
  const id = useId();
  const organizationNames = usePortalOrganizations(viewerId, side, organizations);
  if (!scope) return <section className="panel"><EmptyState icon="lock" title="Chưa có quyền truy cập"
    description="Bạn cần vai trò và phạm vi tổ chức được cấp để sử dụng cổng này." /></section>;
  return <div className="cooperation-workspace portal-workspace">
    <ScopedPortal key={scope + side} org={scope} side={side} page={page} navigate={navigate} viewerId={viewerId} grants={grants}
      organizationPicker={<div className="portal-organization-field"><span className="portal-organization-icon"><Icon name={side === 'school' ? 'graduation' : 'building'} size={22} /></span>
        <div className="portal-context-select"><label htmlFor={id}>{side === 'school' ? 'Nhà trường' : 'Doanh nghiệp'} đang làm việc</label>
          <select id={id} value={scope} title={organizationNames.names[scope] || scope} aria-busy={organizationNames.loading}
            onChange={event => { if (canLeaveDemand()) setSelected(event.target.value); }}>
            {organizations.map(org => <option key={org} value={org}>
              {organizationNames.names[org] || (organizationNames.loading ? 'Đang tải tên tổ chức…' : `${side === 'school' ? 'Nhà trường' : 'Doanh nghiệp'} · ${org.slice(-8)}`)}
            </option>)}
          </select></div>
      </div>} />
  </div>;
}

function ScopedPortal({ org, side, page, navigate, viewerId, organizationPicker, grants }: {
  org: string; side: PortalSide; page: PortalPage; navigate: (page: PortalPage) => void; viewerId: string; organizationPicker: ReactNode; grants: Grant[];
}) {
  const data = usePortalData(org, side);
  const [revision, setRevision] = useState(0), [cooperationId, setCooperationId] = useState('');
  const [scope, setScope] = useState({ schoolOrgId: side === 'school' ? org : '', termId: '' });
  const [focus, setFocus] = useState<{ key: string; target: NotificationTarget } | null>(null);
  const notifications = useNotifications({ org, side, viewerId, ...scope }, data);
  const meta = pagesFor(side).find(item => item.id === page) || pagesFor(side)[0];
  const school = side === 'school';
  useEffect(() => { if (focus && focus.target.page !== page) setFocus(null); }, [page, focus]);
  const openNotification = (notification: PortalNotification) => {
    if (!canLeaveDemand()) return;
    notifications.markRead([notification.id]); setFocus({ key: notification.id, target: notification.target });
    if (notification.target.cooperationId) setCooperationId(notification.target.cooperationId);
    navigate(notification.target.page);
  };
  const refresh = () => { if (canLeaveDemand()) { data.reload();setRevision(value => value + 1); } };
  return <>
    <div className={'portal-context-bar is-' + side}><div className="portal-context-group">{organizationPicker}
      <NotificationScopeFields org={org} side={side} {...scope} change={(schoolOrgId, termId) => setScope({ schoolOrgId, termId })} />
    </div><div className="portal-context-actions"><NotificationBell store={notifications} open={() => navigate('notifications')} />
      <span className="portal-role"><span>{school ? 'NT' : 'DN'}</span>{school ? 'Nhà trường' : 'Doanh nghiệp'}</span>
    </div></div>
    <header className="portal-page-header"><div><p className="portal-breadcrumb">{school ? 'Cổng nhà trường' : 'Cổng doanh nghiệp'} / {meta.title}</p>
      {page !== 'overview' && <h2>{page === 'notifications' ? 'Trung tâm thông báo' : meta.title}</h2>}</div>
      <div className="button-row">{['overview', 'statistics', 'notifications'].includes(page) && <button className="secondary" onClick={refresh}><Icon name="refresh" size={16} />Tải lại dữ liệu</button>}
        {page !== 'overview' && <button className="secondary" onClick={() => navigate('overview')}><Icon name="grid" size={16} />Tổng quan</button>}</div>
    </header>
    {page === 'overview' && <PortalDashboard side={side} demands={data.demands} cooperations={data.cooperations} verifications={data.verifications} navigate={navigate} notifications={notifications} />}
    {page === 'demands' && !school && <ScopedDemands key={'d:' + revision} organizationId={org} />}
    {page === 'statistics' && !school && <ProtectedStatistics org={org} demands={data.demands} navigate={navigate} />}
    {page === 'matching' && school && <MatchingWorkspace organizationId={org} grants={grants} />}
    {page === 'notifications' && <NotificationCenter store={notifications} open={openNotification} refresh={refresh} />}
    {page === 'verifications' && <VerificationFlows key={'v:' + revision + (focus?.key || '')} org={org} school={school} focusId={focus?.target.recordId} />}
    {page === 'cooperations' && <>
      {!school && <Notice tone="error" message={data.demands.error} />}
      <CooperationFlows key={'c:' + revision + (focus?.key || '')} org={org} school={school} demands={data.demands.items} includeProgress={false} focusId={focus?.target.recordId} />
    </>}
    {(page === 'proposals' || page === 'placements') && <ApprovedCooperations key={page + revision} org={org} side={side}
      placements={page === 'placements'} records={data.cooperations} selected={cooperationId} select={setCooperationId} focusProposalId={focus?.target.proposalId} />}
  </>;
}
