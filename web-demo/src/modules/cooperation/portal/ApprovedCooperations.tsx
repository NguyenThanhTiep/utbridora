import { useEffect, useId, useState } from 'react';
import { api } from '../../../api';
import { EmptyState, Notice } from '../../../components';
import type { Demand } from '../types';
import type { CooperationRecord } from '../WorkflowViews';
import { EnterpriseCandidateWorkspace } from '../proposals/EnterpriseCandidateWorkspace';
import { SchoolProposals } from '../proposals/SchoolProposals';
import { PlacementWorkspace } from '../proposals/PlacementWorkspace';
import type { SchoolProposal } from '../proposals/types';
import { workflowError } from '../proposals/api';
import { readAll, type Resource } from './data';
import type { PortalSide } from './navigation';
import { canLeaveDemand } from '../navigation';
import { PlacementOverview } from '../proposals/PlacementOverview';
import type { PlacementProgress } from '../proposals/placementProgress';

export function ApprovedCooperations({ org, side, placements, records, selected, select, focusProposalId }: {
  org: string; side: PortalSide; placements: boolean; records: Resource<CooperationRecord>;
  selected: string; select: (id: string) => void; focusProposalId?: string;
}) {
  const selectId = useId();
  const [progressChoice, setProgressChoice] = useState<PlacementProgress | null>(null);
  useEffect(() => {setProgressChoice(null);}, [selected]);
  const approved = records.items.filter(record => record.state === 'APPROVED');
  const current = progressChoice && progressChoice.cooperationState !== 'APPROVED' ? undefined : approved.find(record => record.cooperationId === selected) || (!selected ? approved[0] : undefined);
  const overview = placements && <PlacementOverview org={org} school={side === 'school'} select={row => {
    setProgressChoice(row);if (row.cooperationState === 'APPROVED' && row.cooperationId) select(row.cooperationId);
  }} />;
  if (records.loading) return <section className="panel" role="status">Đang tải danh sách hợp tác…</section>;
  if (records.error) return <section className="panel"><Notice tone="error" message={records.error} /></section>;
  if (focusProposalId && selected && !approved.some(record => record.cooperationId === selected)) return <section className="panel">
    <Notice tone="error" message="Hợp tác trong thông báo đã thay đổi hoặc không còn trong phạm vi truy cập. Hãy tải lại danh sách." />
  </section>;
  if (!current) return <>{overview}<section className="panel">{progressChoice ? <><h3>{progressChoice.title}</h3><p>{progressChoice.cooperationState === 'REJECTED'
    ? 'Yêu cầu hợp tác đã bị từ chối. Doanh nghiệp cần chỉnh sửa và gửi lại theo quy trình trước khi tiếp nhận.'
    : 'Nhu cầu chưa có hợp tác được duyệt. Chức năng tạo tiếp nhận chỉ mở sau khi nhà trường phê duyệt hợp tác.'}</p></> : side === 'enterprise' && !placements ? <EnterpriseCandidateWorkspace org={org} /> : <>
    <EmptyState icon="briefcase" title="Chưa có hợp tác được duyệt"
      description="Danh sách ứng viên và tiếp nhận thực tập sẽ được mở theo từng hợp tác đã được nhà trường duyệt." />
    {side === 'enterprise' && <div className="proposal-table"><table><thead><tr><th>Hồ sơ tiếp nhận</th><th>Trạng thái</th><th>Thời gian thực tập</th></tr></thead>
      <tbody><tr><td colSpan={3}>Chưa có dữ liệu tiếp nhận thực tập.</td></tr></tbody></table></div>}
  </>}</section></>;
  return <>
    {overview}
    <section className="panel portal-record-picker"><label htmlFor={selectId}>Hợp tác đang xem</label><select id={selectId} value={current.cooperationId}
      onChange={event => { if (canLeaveDemand()) select(event.target.value); }}>
      {approved.map(record => <option key={record.cooperationId} value={record.cooperationId}>
        {record.cooperationId} · Lần gửi {record.submissionNo}
      </option>)}
    </select></section>
    <ApprovedDetail key={org + current.cooperationId + placements + focusProposalId} org={org} side={side} id={current.cooperationId} placements={placements} focusProposalId={focusProposalId} />
  </>;
}

function ApprovedDetail({ org, side, id, placements, focusProposalId }: { org: string; side: PortalSide; id: string; placements: boolean; focusProposalId?: string }) {
  const [detail, setDetail] = useState<{ demand: Demand; cooperation: CooperationRecord } | null>(null);
  const [candidates, setCandidates] = useState<SchoolProposal[]>([]);
  const [error, setError] = useState('');
  const [refreshing, setRefreshing] = useState(true);
  const [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController();setError('');setRefreshing(true);
    void Promise.all([
      api<{ demand: Demand; cooperation: CooperationRecord }>('/' + side + '/cooperations/' + id,
        { signal: controller.signal, headers: { 'X-Organization-Id': org } }),
      side === 'school' && placements ? readAll<SchoolProposal>(org, '/school/cooperations/' + id + '/proposals?state=ENTERPRISE_ACCEPTED', controller.signal) : Promise.resolve([]),
    ]).then(([record, rows]) => {
      if (controller.signal.aborted) return;
      if (record.cooperation.cooperationId !== id || record.cooperation.state !== 'APPROVED'
        || record.demand.cooperationId !== id || (side === 'enterprise' && record.demand.enterpriseOrgId !== org)
        || (side === 'school' && record.demand.data.schoolOrgId !== org)) throw new Error('Phạm vi hợp tác đã thay đổi. Hãy tải lại.');
      setDetail(record);setCandidates(rows);
    }).catch(reason => { if (!controller.signal.aborted) {setDetail(null);setCandidates([]);setError(workflowError(reason));} })
      .finally(() => {if (!controller.signal.aborted) setRefreshing(false);});
    return () => controller.abort();
  }, [org, side, id, placements, revision]);
  return <section className="panel portal-detail">
    <Notice tone="error" message={error} />
    {!detail && !error && <p role="status">Đang tải nội dung hợp tác…</p>}
    {detail && <><div className="portal-detail-heading"><h2>{detail.demand.data.title}</h2>
      <p>{detail.demand.data.quantity} vị trí · {detail.demand.data.required.dateRange.startDate} – {detail.demand.data.required.dateRange.endDate}</p></div>
      {placements ? <PlacementWorkspace org={org} school={side === 'school'} cooperationId={id} demand={detail.demand}
        candidates={candidates} locked={refreshing} initialCandidateId={focusProposalId} changed={() => setRevision(value => value + 1)} />
        : side === 'school' ? <SchoolProposals org={org} cooperationId={id} demand={detail.demand} includePlacement={false} />
        : <EnterpriseCandidateWorkspace org={org} cooperationId={id} focusProposalId={focusProposalId} />}
    </>}
  </section>;
}
