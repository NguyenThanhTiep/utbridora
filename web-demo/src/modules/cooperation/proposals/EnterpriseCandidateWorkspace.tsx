import { useState } from 'react';
import { EmptyState } from '../../../components';
import { EnterpriseProposals } from '../proposals/EnterpriseProposals';
import { FeaturePending } from '../ui/FeaturePending';
import { WorkflowTabs } from '../ui/WorkflowTabs';
import { canLeaveDemand } from '../navigation';

const tabs = [{ value: 'candidates', label: 'Ứng viên được chia sẻ' }, { value: 'batches', label: 'Lô đề xuất' }] as const;

export function EnterpriseCandidateWorkspace({ org, cooperationId, focusProposalId }: { org: string; cooperationId?: string; focusProposalId?: string }) {
  const [tab, setTab] = useState<typeof tabs[number]['value']>('candidates');
  return <WorkflowTabs label="Danh sách đề xuất" value={tab} options={tabs} change={value => { if (canLeaveDemand()) setTab(value); }}>
    {tab === 'candidates' ? cooperationId ? <EnterpriseProposals org={org} cooperationId={cooperationId} focusProposalId={focusProposalId} />
      : <EmptyState icon="users" title="Chưa có hợp tác được duyệt" description="Ứng viên được chia sẻ sẽ xuất hiện theo từng hợp tác đã được nhà trường duyệt." /> : <div className="portal-batches">
      <FeaturePending>Danh sách theo lô chưa sẵn sàng. Bạn vẫn có thể xem và phản hồi những hồ sơ đã được chia sẻ.</FeaturePending>
      <div className="proposal-table"><table><thead><tr><th>Lô đề xuất</th><th>Phạm vi chia sẻ</th><th>Cập nhật</th><th>Thao tác</th></tr></thead>
        <tbody><tr><td colSpan={4}><EmptyState icon="users" title="Chưa có dữ liệu lô đề xuất"
          description="Các lô sẽ xuất hiện sau khi chức năng được kết nối." /></td></tr></tbody>
      </table></div><button className="secondary" onClick={() => setTab('candidates')}>Xem ứng viên được chia sẻ</button>
    </div>}
  </WorkflowTabs>;
}
