import { useState } from 'react';
import type { Grant } from '../../api';
import { canLeaveDemand } from './navigation';
import { PortalWorkspace } from './portal/PortalWorkspace';
import type { PortalPage } from './portal/navigation';
import './styles.css';

export function SchoolWorkspace({ grants, page, onNavigate, viewerId = 'session' }: {
  grants: Grant[]; page?: PortalPage; onNavigate?: (page: PortalPage) => void; viewerId?: string;
}) {
  const [localPage, setLocalPage] = useState<PortalPage>('cooperations');
  const organizations = [...new Set(grants.filter(grant => grant.role === 'SCHOOL_STAFF' && grant.organizationId).map(grant => grant.organizationId!))];
  const navigate = (next: PortalPage) => { if (onNavigate) onNavigate(next); else if (canLeaveDemand()) setLocalPage(next); };
  return <PortalWorkspace organizations={organizations} side="school" page={page || localPage} navigate={navigate} viewerId={viewerId} grants={grants} />;
}
