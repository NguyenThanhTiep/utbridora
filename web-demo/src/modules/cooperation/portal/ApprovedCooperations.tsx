import { useCallback, useEffect, useId, useRef, useState } from 'react';
import { api } from '../../../api';
import { EmptyState, Notice } from '../../../components';
import { formatDateDMY } from '../../../ui/dateFormat';
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
import { useCooperationTitles } from '../workflow/useCooperationTitles';
import { useViewRouting } from '../../../routing/BrowserRouting';
import { cooperationInScope, demandInScope } from './scope';

export function ApprovedCooperations({ org, side, placements, records, demands = [], selected, select, focusProposalId, termId, onTermChange, schoolOrgId, onSchoolChange }: {
  org: string; side: PortalSide; placements: boolean; records: Resource<CooperationRecord>;
  selected: string; select: (id: string) => void; focusProposalId?: string; demands?: Demand[]; termId?: string; onTermChange?: (termId: string) => void; schoolOrgId?: string; onSchoolChange?: (schoolOrgId: string) => void;
}) {
  const selectId = useId();
  const routing=useViewRouting(side==='school'?'school':'demands');
  const explicitSelection=!!routing?.route.cooperationId;
  const explicitFocus=!!routing?.route.recordId;
  const detailRef = useRef<HTMLElement>(null);
  const enterpriseCompactPlacements = side === 'enterprise' && placements;
  const enterpriseCandidates = side === 'enterprise' && !placements;
  const [resolvedTitle, setResolvedTitle] = useState<{ org: string; side: PortalSide; id: string; title: string } | null>(null);
  const rememberTitle = useCallback((record: { demand: Demand; cooperation: CooperationRecord }) => {
    setResolvedTitle({ org, side, id: record.cooperation.cooperationId, title: record.demand.data.title });
  }, [org, side]);
  const [progressChoice, setProgressChoice] = useState<PlacementProgress | null>(null);
  const [detailExpanded, setDetailExpanded] = useState(() => !enterpriseCompactPlacements || Boolean(focusProposalId) || explicitFocus);
  const [detailFocusTick, setDetailFocusTick] = useState(0);
  useEffect(() => {
    setDetailExpanded(!enterpriseCompactPlacements || Boolean(focusProposalId) || explicitFocus);
  }, [org, enterpriseCompactPlacements, focusProposalId, explicitFocus]);
  const collapse=()=>{
    if(!canLeaveDemand())return;
    if(routing && !routing.go({...routing.route,recordId:undefined,proposalId:undefined}))return;
    setDetailExpanded(false);
  };
  useEffect(() => {
    setProgressChoice(old => old && old.cooperationState === 'APPROVED' && old.cooperationId === selected ? old : null);
  }, [org, selected]);
  useEffect(() => {
    if (!detailFocusTick || !detailExpanded || !detailRef.current) return;
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    detailRef.current.scrollIntoView({ block: 'start', behavior: reduced ? 'auto' : 'smooth' });
    detailRef.current.focus({ preventScroll: true });
  }, [detailFocusTick, detailExpanded, selected, progressChoice?.demandId]);
  const approved = records.items.filter(record => record.state === 'APPROVED' && cooperationInScope(record, { schoolOrgId, termId }));
  const titles = useCooperationTitles(org, side === 'school', approved);
  const matchedSelected = approved.find(record => record.cooperationId === selected);
  const activeApproved = progressChoice?.cooperationState === 'APPROVED' && progressChoice.cooperationId
    ? approved.find(record => record.cooperationId === progressChoice.cooperationId)
    : matchedSelected || (!selected || !focusProposalId && !explicitSelection ? approved[0] : undefined);
  const current = progressChoice && progressChoice.cooperationState !== 'APPROVED'
    ? undefined
    : activeApproved || (progressChoice?.cooperationState === 'APPROVED' && progressChoice.cooperationId
      ? { cooperationId: progressChoice.cooperationId, demandId: progressChoice.demandId, termId: progressChoice.termId, state: 'APPROVED', version: 0, submissionNo: 0 }
      : undefined);
  const currentId = current?.cooperationId;
  const activeOverviewDemandId = detailExpanded ? (progressChoice?.demandId ?? activeApproved?.demandId) : undefined;
  const activeOverviewCooperationId = detailExpanded ? (progressChoice ? (progressChoice.cooperationId ?? undefined) : currentId) : undefined;
  const overview = placements && <PlacementOverview org={org} school={side === 'school'} termId={termId} onTermChange={onTermChange} schoolOrgId={schoolOrgId} onSchoolChange={onSchoolChange}
    selectedDemandId={activeOverviewDemandId} selectedCooperationId={activeOverviewCooperationId}
    select={row => {
      setProgressChoice(row);
      if (row.cooperationState === 'APPROVED' && row.cooperationId) select(row.cooperationId);
      setDetailExpanded(true);
      setDetailFocusTick(n => n + 1);
    }} />;
  if (enterpriseCompactPlacements && !detailExpanded) return <>{overview}</>;
  if (records.loading) return <>{overview}<section className="panel rc-loading-state" role="status">Đang tải danh sách hợp tác…</section></>;
  if (records.error) return <>{overview}<section className="panel"><Notice tone="error" message={records.error} /></section></>;
  if ((focusProposalId || explicitSelection) && selected && !approved.some(record => record.cooperationId === selected)) return <>{overview}<section className="panel">
    <Notice tone="error" message="Hợp tác trong thông báo đã thay đổi hoặc không còn trong phạm vi truy cập. Hãy tải lại danh sách." />
  </section></>;
  if (!currentId) return <>{overview}<section ref={detailRef} tabIndex={-1} className={'panel placement-detail-target' + (detailFocusTick ? ' is-focused-target' : '')}>{progressChoice ? <><h3>{progressChoice.title}</h3><p>{progressChoice.cooperationState === 'REJECTED'
    ? 'Yêu cầu hợp tác đã bị từ chối. Doanh nghiệp cần chỉnh sửa và gửi lại theo quy trình trước khi tiếp nhận.'
    : 'Nhu cầu chưa có hợp tác được duyệt. Chức năng tạo tiếp nhận chỉ mở sau khi nhà trường phê duyệt hợp tác.'}</p><div className="button-row">{approved.length > 0 && <button type="button" className="secondary" onClick={() => {
      setProgressChoice(null);
      if (!approved.some(record => record.cooperationId === selected)) select(approved[0].cooperationId);
    }}>Quay lại hợp tác đã duyệt</button>}{enterpriseCompactPlacements && <button type="button" className="secondary" onClick={collapse}>Đóng chi tiết</button>}</div></> : side === 'enterprise' && !placements ? <EnterpriseCandidateWorkspace org={org} /> : <>
    <EmptyState icon="briefcase" title="Chưa có hợp tác được duyệt"
      description="Danh sách ứng viên và tiếp nhận thực tập sẽ được mở theo từng hợp tác đã được nhà trường duyệt." />
    {side === 'enterprise' && <div className="proposal-table"><table><thead><tr><th>Hồ sơ tiếp nhận</th><th>Trạng thái</th><th>Thời gian thực tập</th></tr></thead>
      <tbody><tr><td colSpan={3}>Chưa có dữ liệu tiếp nhận thực tập.</td></tr></tbody></table></div>}
  </>}</section></>;
  const picker = <section ref={detailRef} tabIndex={-1} className={(enterpriseCandidates ? '' : 'panel ') + 'portal-record-picker placement-detail-target' + (detailFocusTick ? ' is-focused-target' : '') + (enterpriseCandidates ? ' enterprise-candidate-scope' : '')}><label htmlFor={selectId}>Hợp tác đang xem</label><div className="placement-picker-controls"><select id={selectId} value={current.cooperationId}
      onChange={event => { if (canLeaveDemand()) select(event.target.value); }}>
      {!approved.some(record => record.cooperationId === current.cooperationId) && progressChoice && <option value={current.cooperationId}>{progressChoice.title}</option>}
      {approved.map((record, index) => {
        const title = (resolvedTitle?.org === org && resolvedTitle.side === side && resolvedTitle.id === record.cooperationId ? resolvedTitle.title : '')
          || titles.names[record.cooperationId] || demands.find(demand => demand.demandId === record.demandId)?.data.title;
        return <option key={record.cooperationId} value={record.cooperationId}>
          {title ? `${title}${record.submissionNo > 1 ? ` · Đợt nộp ${record.submissionNo}` : ''}`
            : `Hợp tác #${index + 1}${record.submissionNo > 1 ? ` · Đợt nộp ${record.submissionNo}` : ''}`}
        </option>;
      })}
    </select>{enterpriseCompactPlacements && <button type="button" className="secondary placement-collapse-btn" onClick={collapse}>Đóng chi tiết</button>}</div></section>;
  const detail = <ApprovedDetail key={org + current.cooperationId + placements + focusProposalId} org={org} side={side} id={current.cooperationId} placements={placements} focusProposalId={focusProposalId} loaded={rememberTitle} schoolOrgId={schoolOrgId} termId={termId} />;
  return <>
    {overview}
    {enterpriseCandidates ? <div className="enterprise-candidate-review">{picker}{detail}</div> : <>{picker}{detail}</>}
  </>;
}

function ApprovedDetail({ org, side, id, placements, focusProposalId, loaded, schoolOrgId, termId }: { org: string; side: PortalSide; id: string; placements: boolean; focusProposalId?: string; schoolOrgId?: string; termId?: string;
  loaded: (record: { demand: Demand; cooperation: CooperationRecord }) => void;
}) {
  const enterpriseCandidates = side === 'enterprise' && !placements;
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
      side === 'school' && placements ? readAll<SchoolProposal>(org, '/school/cooperations/' + id + '/proposals', controller.signal) : Promise.resolve([]),
    ]).then(([record, rows]) => {
      if (controller.signal.aborted) return;
      if (record.cooperation.cooperationId !== id || record.cooperation.state !== 'APPROVED'
        || record.demand.cooperationId !== id || (side === 'enterprise' && record.demand.enterpriseOrgId !== org)
        || (side === 'school' && record.demand.data.schoolOrgId !== org)
        || !cooperationInScope(record.cooperation, { schoolOrgId, termId }) || !demandInScope(record.demand, { schoolOrgId, termId })) throw new Error('Phạm vi hợp tác đã thay đổi. Hãy tải lại.');
      setDetail(record);setCandidates(rows);loaded(record);
    }).catch(reason => { if (!controller.signal.aborted) {setDetail(null);setCandidates([]);setError(workflowError(reason));} })
      .finally(() => {if (!controller.signal.aborted) setRefreshing(false);});
    return () => controller.abort();
  }, [org, side, id, placements, revision, loaded, schoolOrgId, termId]);
  return <section className={(enterpriseCandidates ? '' : 'panel ') + 'portal-detail' + (enterpriseCandidates ? ' enterprise-candidate-detail' : '')}>
    <Notice tone="error" message={error} />
    {!detail && !error && <p className="rc-loading-state" role="status">Đang tải nội dung hợp tác…</p>}
    {detail && <>{!enterpriseCandidates && <div className="portal-detail-heading approved-coop-header">
      <div>{side === 'school'
        ? <h3 title={detail.demand.data.title}>{detail.demand.data.title}</h3>
        : <h2>{detail.demand.data.title}</h2>}</div>
      <div className="approved-coop-summary">
        <span className="approved-coop-chip">{detail.demand.data.quantity} vị trí</span>
        <span className="approved-coop-chip muted">{formatDateDMY(detail.demand.data.required.dateRange.startDate)} – {formatDateDMY(detail.demand.data.required.dateRange.endDate)}</span>
      </div>
    </div>}
      {placements ? <PlacementWorkspace org={org} school={side === 'school'} cooperationId={id} demand={detail.demand}
        candidates={candidates} locked={refreshing} initialCandidateId={focusProposalId} changed={() => setRevision(value => value + 1)} />
        : side === 'school' ? <SchoolProposals org={org} cooperationId={id} demand={detail.demand} includePlacement={false} />
        : <EnterpriseCandidateWorkspace org={org} cooperationId={id} focusProposalId={focusProposalId}
          context={<div className="candidate-cooperation-meta"><span className="approved-coop-chip">{detail.demand.data.quantity} vị trí</span>
            <span className="approved-coop-chip muted">{formatDateDMY(detail.demand.data.required.dateRange.startDate)} – {formatDateDMY(detail.demand.data.required.dateRange.endDate)}</span></div>} />}
    </>}
  </section>;
}
