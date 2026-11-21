import { useState, type ReactNode } from 'react';
import { EmptyState, Icon } from '../../../components';
import { EnterpriseProposals } from '../proposals/EnterpriseProposals';
import { EnterpriseBatches } from './EnterpriseBatches';
import { WorkflowTabs } from '../ui/WorkflowTabs';
import { canLeaveDemand } from '../navigation';
import './enterprise-candidates.css';

const tabs = [{ value: 'candidates', label: 'Ứng viên được chia sẻ' }, { value: 'batches', label: 'Lô đề xuất' }] as const;

export function EnterpriseCandidateWorkspace({ org, cooperationId, focusProposalId, context }: { org: string; cooperationId?: string; focusProposalId?: string; context?: ReactNode }) {
  const [tab, setTab] = useState<typeof tabs[number]['value']>('candidates');
  return <div className="enterprise-candidates"><div className="candidate-context-summary"><div className="candidate-sharing-note" role="note"><Icon name="shield" size={19} />
    <p>Chỉ hiển thị hồ sơ sinh viên đã đồng ý chia sẻ.</p>
  </div>{context}</div><WorkflowTabs label="Danh sách đề xuất" value={tab} options={tabs} change={value => { if (canLeaveDemand()) setTab(value); }}>
    {tab === 'candidates' ? cooperationId ? <EnterpriseProposals org={org} cooperationId={cooperationId} focusProposalId={focusProposalId} />
      : <EmptyState icon="users" title="Chưa có hợp tác được duyệt" description="Ứng viên được chia sẻ sẽ xuất hiện theo từng hợp tác đã được nhà trường duyệt." /> : <div>
      {cooperationId ? <EnterpriseBatches key={org + ':' + cooperationId} org={org} cooperationId={cooperationId} openCandidates={() => setTab('candidates')} />
        : <EmptyState icon="users" title="Chưa có dữ liệu lô đề xuất" description="Danh sách lô sẽ được mở theo hợp tác đã được nhà trường duyệt." />}
      <button type="button" className="secondary" onClick={() => {if (canLeaveDemand()) setTab('candidates');}}>Xem ứng viên được chia sẻ</button>
    </div>}
  </WorkflowTabs></div>;
}
