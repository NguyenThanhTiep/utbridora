import { useState } from 'react';
import { EmptyState } from '../../../components';
import { EnterpriseProposals } from '../proposals/EnterpriseProposals';
import { EnterpriseBatches } from './EnterpriseBatches';
import { WorkflowTabs } from '../ui/WorkflowTabs';
import { canLeaveDemand } from '../navigation';

const tabs = [{ value: 'candidates', label: 'Ứng viên được chia sẻ' }, { value: 'batches', label: 'Lô đề xuất' }] as const;

export function EnterpriseCandidateWorkspace({ org, cooperationId, focusProposalId }: { org: string; cooperationId?: string; focusProposalId?: string }) {
  const [tab, setTab] = useState<typeof tabs[number]['value']>('candidates');
  return <WorkflowTabs label="Danh sách đề xuất" value={tab} options={tabs} change={value => { if (canLeaveDemand()) setTab(value); }}>
    {tab === 'candidates' ? cooperationId ? <EnterpriseProposals org={org} cooperationId={cooperationId} focusProposalId={focusProposalId} />
      : <EmptyState icon="users" title="Chưa có hợp tác được duyệt" description="Ứng viên được chia sẻ sẽ xuất hiện theo từng hợp tác đã được nhà trường duyệt." /> : <div>
      {cooperationId ? <EnterpriseBatches key={org + ':' + cooperationId} org={org} cooperationId={cooperationId} openCandidates={() => setTab('candidates')} />
        : <EmptyState icon="users" title="Chưa có dữ liệu lô đề xuất" description="Danh sách lô sẽ được mở theo hợp tác đã được nhà trường duyệt." />}
      <button type="button" className="secondary" onClick={() => {if (canLeaveDemand()) setTab('candidates');}}>Xem ứng viên được chia sẻ</button>
    </div>}
  </WorkflowTabs>;
}
