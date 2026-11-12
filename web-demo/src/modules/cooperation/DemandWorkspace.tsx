import { useId, useState } from 'react';
import type { Grant } from '../../api';
import { EmptyState } from '../../components';
import { ScopedDemands } from './demand/ScopedDemands';
import { canLeaveDemand } from './navigation';
import './styles.css';
import { CooperationFlows, VerificationFlows } from './WorkflowViews';
import { EnterpriseMatchingPanel } from '../matching/EnterpriseMatchingPanel';

export const canAuthorDemands = (grant: Grant) => grant.role === 'ENTERPRISE'
  && !!grant.organizationId && grant.permissions.includes('cooperation:enterprise:create');

export function DemandWorkspace({ grants }: { grants: Grant[] }) {
  const organizations = [...new Set(grants.filter(canAuthorDemands).map(grant => grant.organizationId!))];
  const [selected, setSelected] = useState(organizations[0] || '');
  const scope = organizations.includes(selected) ? selected : organizations[0] || '';
  const selectId = useId();
  if (!scope) return <section className="panel"><EmptyState icon="lock" title="Chưa có quyền quản lý nhu cầu"
    description="Bạn cần vai trò doanh nghiệp và quyền tạo nhu cầu trong tổ chức được cấp." /></section>;
  return <div className="cooperation-workspace stack">
    <section className="panel cooperation-scope">
      <label htmlFor={selectId}>Doanh nghiệp đang làm việc</label>
      <select id={selectId} value={scope} onChange={event => { if (canLeaveDemand()) setSelected(event.target.value); }}>
        {organizations.map(id => <option key={id} value={id}>{id}</option>)}
      </select>
      <p className="field-hint">Hiển thị mã tổ chức được cấp quyền. Đổi doanh nghiệp sẽ đóng bản nhập và tải lại danh sách.</p>
    </section>
    {/* A fresh component per organization discards its editor, list and pending request results. */}
    <ScopedDemands key={scope} organizationId={scope} />
    {/* liên quan đến việc thêm lọc svien theo gpa là MVP: Đưa form chọn Demand/GPA/kỹ năng và aggregate matching vào workspace doanh nghiệp. */}
    <EnterpriseMatchingPanel key={scope + ':matching'} organizationId={scope} />
    <VerificationFlows key={scope + ':v'} org={scope} school={false} />
    <CooperationFlows key={scope + ':c'} org={scope} school={false} />
  </div>;
}
