import { useState } from 'react';
import type { Grant } from '../../api';
import { canLeaveDemand } from './navigation';
import { PortalWorkspace } from './portal/PortalWorkspace';
import type { PortalPage } from './portal/navigation';
import './styles.css';

export const canAuthorDemands = (grant: Grant) => grant.role === 'ENTERPRISE'
  && !!grant.organizationId && grant.permissions.includes('cooperation:enterprise:create');

export function DemandWorkspace({ grants, page, onNavigate, viewerId = 'session' }: {
  grants: Grant[]; page?: PortalPage; onNavigate?: (page: PortalPage) => void; viewerId?: string;
}) {
  const [localPage, setLocalPage] = useState<PortalPage>('demands');
  const organizations = [...new Set(grants.filter(canAuthorDemands).map(grant => grant.organizationId!))];
  const navigate = (next: PortalPage) => { if (onNavigate) onNavigate(next); else if (canLeaveDemand()) setLocalPage(next); };
  return <PortalWorkspace organizations={organizations} side="enterprise" page={page || localPage} navigate={navigate} viewerId={viewerId} />;
}
