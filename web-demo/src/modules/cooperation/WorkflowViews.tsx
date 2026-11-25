import { useViewRouting } from '../../routing/BrowserRouting';
﻿import { useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { EmptyState, Icon, Notice } from '../../components';
import { formatDateDMY } from '../../ui/dateFormat';
import type { ContractPage } from '../student/types';
import { states, fieldLabels } from './workflow/types';
import type { Cooperation, Detail, Verification } from './workflow/types';
import { useCommand } from './workflow/useCommand';
import { ReviewDecision } from './workflow/ReviewDecision';
import { CooperationComposer } from './workflow/CooperationComposer';
import { VerificationEditor } from './workflow/VerificationEditor';
import { VerificationJourney } from './workflow/VerificationJourney';
import { useSchoolNames } from './portal/useSchoolNames';
import type { Demand } from './types';
import { EnterpriseProposals } from './proposals/EnterpriseProposals';
import { SchoolProposals } from './proposals/SchoolProposals';
import { PlacementWorkspace } from './proposals/PlacementWorkspace';
import { canLeaveDemand } from './navigation';
import { useFocusedWorkflow, useWorkflowFocus } from './workflow/useFocusedWorkflow';
import { SchoolVerificationView } from './workflow/SchoolVerificationView';
import { SchoolCooperationList } from './workflow/SchoolCooperationList';
import { CooperationSubmissionHistory } from './workflow/CooperationSubmissionHistory';
import { SchoolCooperationSummary } from './workflow/SchoolCooperationSummary';
import { useSchoolTermNames } from './workflow/useSchoolTermNames';
import { useCooperationTitles } from './workflow/useCooperationTitles';
import { EnterpriseCooperationList } from './workflow/EnterpriseCooperationList';
import { EnterpriseCooperationSummary } from './workflow/EnterpriseCooperationSummary';
import './workflow/enterprise-cooperations.css';
export type { CooperationRecord, VerificationInput, VerificationRecord } from './workflow/types';

export function CooperationFlows({ org, school, demands = [], onLoaded, includeProgress = true, focusId, termId = '' }: { org: string; school: boolean; demands?: Demand[]; onLoaded?: (items: Cooperation[]) => void; includeProgress?: boolean; focusId?: string; termId?: string }) {
  const [items, setItems] = useState<Cooperation[]>([]), [detail, setDetail] = useState<Detail | null>(null);
  const routeContext=useViewRouting(school?'school':'demands');
  const routing=routeContext?.route.portalPage==='cooperations'?routeContext:null;
  const closeRoute=(force=false)=>!routing || routing.go({...routing.route,recordId:undefined},{replace:force,guard:!force});
  const [refresh, setRefresh] = useState(0), [page, setPage] = useState(0), [total, setTotal] = useState(0), [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [stateFilter, setStateFilter] = useState(''), [searchText, setSearchText] = useState('');
  const [composing, setComposing] = useState(false);
  const [openingId, setOpeningId] = useState(''), [detailError, setDetailError] = useState('');
  const detailRequest = useRef<AbortController | null>(null);
  const loadedRef = useRef(onLoaded);
  loadedRef.current = onLoaded;
  const side = school ? 'school' : 'enterprise', changed = () => {
    detailRequest.current?.abort(); setOpeningId(''); setDetailError('');
    closeRoute(true);setDetail(null); setComposing(false); setRefresh(n => n + 1);
  };
  const termNames = useSchoolTermNames(org, school);
  const schoolNames = useSchoolNames(org, !school);
  const action = useCommand(org, changed);
  const focusedRef = useRef<HTMLElement>(null);
  useFocusedWorkflow<Detail>(org, '/' + side + '/cooperations', focusId, refresh, value => {
    if (value.cooperation.cooperationId !== focusId || value.cooperation.demandId!==value.demand.demandId
      || (school?value.demand.data.schoolOrgId!==org:value.demand.enterpriseOrgId!==org)
      || termId && value.cooperation.termId!==termId) throw new Error('Unexpected cooperation');
    setDetail(value);
  }, () => setDetailError('Đề nghị đã thay đổi hoặc không còn trong phạm vi truy cập.'));
  useWorkflowFocus(focusedRef, !!focusId && !loading, detail);

  useEffect(() => {
    const update = () => {if(canLeaveDemand())changed();};
    window.addEventListener('cooperation-updated', update);
    return () => window.removeEventListener('cooperation-updated', update);
  }, []);

  useEffect(() => {
    detailRequest.current?.abort(); setOpeningId(''); setDetailError('');
    const c = new AbortController(); setError(''); setDetail(null); setItems([]); setLoading(true);
    void api<ContractPage<Cooperation>>('/' + side + '/cooperations?page=' + page + '&size=25' + (termId ? '&' + new URLSearchParams({ termId }) : ''), { signal: c.signal, headers: { 'X-Organization-Id': org } })
      .then(p => { if (!c.signal.aborted) {
        const visible = p.content.filter(item => (!school || item.state !== 'DRAFT') && (!termId || item.termId === termId));
        setItems(visible); setTotal(p.totalPages); loadedRef.current?.(visible);
      } })
      .catch(() => { if (!c.signal.aborted) setError('Chưa tải được đề nghị hợp tác.'); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => { c.abort(); detailRequest.current?.abort(); };
  }, [org, side, termId, refresh, page]);

  const titles = useCooperationTitles(org, school, items);
  const demandTitles = { ...titles.names };
  const cooperationSchools: Record<string, string> = {};
  if (!school) items.forEach(item => {
    const demand = demands.find(record => record.demandId === item.demandId);
    if (demand) demandTitles[item.cooperationId] = demand.data.title;
    cooperationSchools[item.cooperationId] = item.schoolOrgId || demand?.data.schoolOrgId || '';
  });
  if (!school && detail?.demand.enterpriseOrgId === org) demandTitles[detail.cooperation.cooperationId] = detail.demand.data.title;
  if (school && detail?.demand.data.schoolOrgId === org) demandTitles[detail.cooperation.cooperationId] = detail.demand.data.title;
  const indexedItems = items.map((c, n) => ({ item: c, seq: page * 25 + n + 1 })).filter(({ item, seq }) => {
    if (stateFilter && item.state !== stateFilter) return false;
    if (searchText.trim()) {
      const q = searchText.trim().toLowerCase();
      return (
        `đề nghị ${seq}`.includes(q)
        || item.cooperationId.toLowerCase().includes(q)
        || item.demandId.toLowerCase().includes(q)
        || item.termId.toLowerCase().includes(q)
        || (demandTitles[item.cooperationId] || '').toLowerCase().includes(q)
        || (!school && (schoolNames[cooperationSchools[item.cooperationId]] || '').toLowerCase().includes(q))
        || (states[item.state] || item.state).toLowerCase().includes(q)
      );
    }
    return true;
  });

  const summaryCards = school
    ? [
        { state: 'SUBMITTED', label: 'Chờ xét duyệt', count: items.filter(c => c.state === 'SUBMITTED').length, tone: 'kpi-pending' },
        { state: 'APPROVED', label: 'Đã duyệt', count: items.filter(c => c.state === 'APPROVED').length, tone: 'kpi-approved' },
        { state: 'REJECTED', label: 'Từ chối', count: items.filter(c => c.state === 'REJECTED').length, tone: 'kpi-rejected' },
      ]
    : [
        { state: 'SUBMITTED', label: 'Chờ xét duyệt', count: items.filter(c => c.state === 'SUBMITTED').length, tone: 'kpi-pending' },
        { state: 'NEEDS_CHANGES', label: 'Cần bổ sung', count: items.filter(c => c.state === 'NEEDS_CHANGES').length, tone: 'kpi-warning' },
        { state: 'APPROVED', label: 'Đã duyệt', count: items.filter(c => c.state === 'APPROVED').length, tone: 'kpi-approved' },
      ];

  const openEnterpriseCooperation = (item: Cooperation) => {
    if (action.busy || !canLeaveDemand()) return;
    if(routing){routing.go({...routing.route,orgId:org,recordId:routing.route.recordId===item.cooperationId?undefined:item.cooperationId});return;}
    detailRequest.current?.abort();
    setOpeningId(''); setDetailError(''); setComposing(false);
    if (detail?.cooperation.cooperationId === item.cooperationId) { setDetail(null); return; }
    const controller = new AbortController(); detailRequest.current = controller;
    setDetail(null); setOpeningId(item.cooperationId);
    void api<Detail>('/enterprise/cooperations/' + encodeURIComponent(item.cooperationId),
      { signal: controller.signal, headers: { 'X-Organization-Id': org } })
      .then(value => {
        if (controller.signal.aborted) return;
        if (value.cooperation.cooperationId !== item.cooperationId || value.cooperation.demandId !== item.demandId
          || value.demand.demandId !== item.demandId || value.demand.enterpriseOrgId !== org) throw new Error('Unexpected cooperation');
        setDetail(value);
      })
      .catch(() => { if (!controller.signal.aborted) setDetailError('Không đọc được đề nghị. Vui lòng chọn lại đề nghị để thử lại.'); })
      .finally(() => { if (!controller.signal.aborted) setOpeningId(''); });
  };

  return <section className={'panel workflow-surface' + (!school ? ' enterprise-cooperation-workspace' : '')}>
    <div className="workflow-kpi-grid school-summary-strip" aria-label="Thống kê đề nghị hợp tác">
      {summaryCards.map(card => <button key={card.state} type="button"
        className={`kpi-card ${card.tone}${stateFilter === card.state ? ' is-active' : ''}`}
        disabled={!school && (loading || Boolean(error) || action.busy)}
        aria-pressed={stateFilter === card.state} title={stateFilter === card.state ? 'Bỏ lọc trạng thái' : `Lọc: ${card.label}`}
        onClick={() => setStateFilter(current => current === card.state ? '' : card.state)}>
        <span className="kpi-card-label">{card.label}</span>
        <span className="kpi-card-bottom">
          <strong className="kpi-card-value">{!school && (loading || error) ? '—' : card.count}</strong>
          <span className="kpi-card-hint" aria-hidden="true">{stateFilter === card.state ? 'Đang lọc · Bỏ lọc' : school ? 'Nhấn để lọc →' : 'Trong trang hiện tại'}</span>
        </span>
      </button>)}
    </div>

    <div className={school ? 'school-section-card' : 'enterprise-section-stack'}>
      <div className="workflow-section-header">
        <div className="workflow-section-heading">
          {school && <span className="school-section-icon is-cooperations" aria-hidden="true"><Icon name="briefcase" size={18} /></span>}
          <h3 className="workflow-subtoolbar-title">Đề nghị hợp tác</h3>
          {items.length > 0 && <span className="workflow-count-pill">{indexedItems.length}/{items.length} đề nghị</span>}
        </div>
        <div className="button-row">
          {!school && <button type="button" className="t5-btn-teal" id={'enterprise-cooperation-compose-' + org} disabled={action.busy} aria-expanded={composing} onClick={() => {
            detailRequest.current?.abort(); setOpeningId(''); setDetailError(''); setDetail(null); setComposing(v => !v);
          }}><Icon name="plus" size={17} />Soạn từ nhu cầu</button>}
          <button className="secondary" disabled={action.busy || (!school && loading)} onClick={changed}><Icon name="refresh" size={17} />Tải lại đề nghị</button>
        </div>
      </div>

      <Notice message={action.message || error || detailError} tone="error" />
      {action.retryable && <button disabled={action.retrying} onClick={() => void action.retry()}>Thử lại yêu cầu đã gửi</button>}

      {!school && composing && <CooperationComposer org={org} demands={demands} busy={action.busy} close={() => {
        document.getElementById('enterprise-cooperation-compose-' + org)?.focus(); setComposing(false);
      }}
        save={demand => void action.command('/enterprise/cooperations', 'POST', { demandId: demand.demandId, expectedDemandVersion: demand.version })}
        submit={demand => void action.command('/enterprise/demands/submit-with-cooperation', 'POST', { demandId: demand.demandId, expectedDemandVersion: demand.version })} />}

      {(school || items.length > 0) && <>
        <fieldset className="placement-filter-bar" disabled={!school && action.busy}>
          <label>Lọc trạng thái đề nghị
            <select value={stateFilter} onChange={e => setStateFilter(e.target.value)}>
              <option value="">Tất cả trạng thái</option>
              {Object.entries(states).filter(([k]) => !school || k !== 'DRAFT').map(([k, label]) => <option key={k} value={k}>{label}</option>)}
            </select>
          </label>
          <label>Tìm kiếm đề nghị hợp tác
            <input type="search" placeholder={school ? 'Tìm trong trang theo tên nhu cầu, mã đề nghị…' : 'Tìm trong trang theo tên nhu cầu hoặc trường…'} value={searchText} onChange={e => setSearchText(e.target.value)} />
          </label>
          {(stateFilter || searchText) && <button type="button" className="secondary" onClick={() => { setStateFilter(''); setSearchText(''); }}>Xóa bộ lọc</button>}
        </fieldset>
      </>}

      {loading && <p className="rc-loading-state" role="status">Đang tải đề nghị hợp tác…</p>}
      {!school && !loading && !error && items.length === 0 && <EmptyState icon="building" title="Chưa có đề nghị trong phạm vi này." description="Chọn một nhu cầu để soạn đề nghị hợp tác với nhà trường." />}
      {!school && items.length > 0 && indexedItems.length === 0 && <EmptyState icon="search" title="Không tìm thấy đề nghị hợp tác phù hợp bộ lọc hiện tại." description="Thử từ khóa khác hoặc xóa bộ lọc để xem lại các đề nghị trong trang." />}

      {!school ? indexedItems.length > 0 && <EnterpriseCooperationList items={indexedItems} titles={demandTitles} schools={schoolNames} schoolIds={cooperationSchools}
        selectedId={detail?.cooperation.cooperationId} openingId={openingId} busy={action.busy} idPrefix="enterprise-cooperation-open-" open={openEnterpriseCooperation} />
      : <SchoolCooperationList items={indexedItems} selectedId={detail?.cooperation.cooperationId} termNames={termNames} demandTitles={demandTitles} titlesLoading={titles.loading}
        loading={loading || Boolean(error)} busy={action.busy}
        emptyMessage={items.length === 0 ? 'Chưa có đề nghị trong phạm vi này.' : 'Không tìm thấy đề nghị hợp tác phù hợp bộ lọc hiện tại.'}
        open={c => {if(routing)routing.go({...routing.route,orgId:org,recordId:c.cooperationId});else void api<Detail>('/school/cooperations/' + c.cooperationId, { headers: { 'X-Organization-Id': org } }).then(setDetail).catch(() => setError('Không đọc được đề nghị.'));}} />}

      {total > 1 && <div className="button-row cooperation-pagination">
        <button className="secondary" disabled={page === 0 || (!school && (action.busy || loading))} onClick={() => setPage(n => n - 1)}>Trước</button>
        <span>Trang {page + 1}/{Math.max(1, total)}</span>
        <button className="secondary" disabled={page + 1 >= total || (!school && (action.busy || loading))} onClick={() => setPage(n => n + 1)}>Sau</button>
      </div>}
    </div>

    {detail && <article className={'workflow-detail-drawer' + (school ? ' school-cooperation-detail' : ' enterprise-cooperation-detail')}
      id={school ? 'school-cooperation-detail-' + detail.cooperation.cooperationId : undefined} ref={focusedRef} tabIndex={-1}>
      {school ? <SchoolCooperationSummary key={detail.cooperation.cooperationId + ':' + detail.demand.version + ':' + detail.cooperation.version} detail={detail} termName={termNames[detail.demand.data.termId]} close={() => {
        document.getElementById('school-cooperation-open-' + detail.cooperation.cooperationId)?.focus();
        if(closeRoute())setDetail(null);
      }} /> : <EnterpriseCooperationSummary key={detail.cooperation.cooperationId + ':' + detail.demand.version + ':' + detail.cooperation.version}
        detail={detail} schoolName={schoolNames[detail.demand.data.schoolOrgId]} busy={action.busy} close={() => {
          document.getElementById('enterprise-cooperation-open-' + detail.cooperation.cooperationId)?.focus(); if(closeRoute())setDetail(null);
        }} />}

      {school && <CooperationSubmissionHistory key={org+':'+detail.cooperation.cooperationId+':'+detail.cooperation.version} org={org} cooperationId={detail.cooperation.cooperationId} disabled={action.busy} />}

      {school && detail.cooperation.state === 'SUBMITTED' && (
        <ReviewDecision
          busy={action.busy}
          fields={['title', 'description', 'quantity', 'required', 'preferred', 'deadline']}
          submit={data => action.command('/school/cooperations/' + detail.cooperation.cooperationId + '/decision', 'PATCH', { expectedVersion: detail.cooperation.version, ...data })}
        />
      )}

      {!school && ['DRAFT', 'NEEDS_CHANGES'].includes(detail.cooperation.state) && (
        <div className="button-row">
          <button disabled={action.busy} onClick={() => void action.command('/enterprise/cooperations/' + detail.cooperation.cooperationId + '/submit', 'PATCH', { expectedVersion: detail.cooperation.version, expectedDemandVersion: detail.demand.version })}>
            Gửi lại đề nghị hợp tác
          </button>
        </div>
      )}

      {!school && detail.cooperation.state === 'REJECTED' && (
        <div className="button-row">
          <button disabled={action.busy} onClick={() => void action.command('/enterprise/cooperations/' + detail.cooperation.cooperationId + '/reopen', 'PATCH', { expectedVersion: detail.cooperation.version, reasonCode: 'ENTERPRISE_REOPENED' })}>
            Mở lại bản nháp để bổ sung
          </button>
        </div>
      )}

      {includeProgress && detail.cooperation.state === 'APPROVED' && (school
        ? <SchoolProposals key={org + detail.cooperation.cooperationId} org={org} cooperationId={detail.cooperation.cooperationId} demand={detail.demand} />
        : <div key={org + detail.cooperation.cooperationId}>
            <EnterpriseProposals org={org} cooperationId={detail.cooperation.cooperationId} />
            <PlacementWorkspace org={org} school={false} cooperationId={detail.cooperation.cooperationId} demand={detail.demand} />
          </div>)}
    </article>}
  </section>;
}

export function VerificationFlows({ org, school, onLoaded, focusId }: { org: string; school: boolean; onLoaded?: (items: Verification[]) => void; focusId?: string }) {
  const [loading, setLoading] = useState(true);
  const [items, setItems] = useState<Verification[]>([]), [refresh, setRefresh] = useState(0), [page, setPage] = useState(0), [total, setTotal] = useState(0);
  const [editing, setEditing] = useState<Verification | null | undefined>(undefined), [error, setError] = useState('');
  const [stateFilter, setStateFilter] = useState(''), [searchText, setSearchText] = useState('');
  const [expanded, setExpanded] = useState<string | null>(null);
  const [focusedRecord, setFocusedRecord] = useState<Verification | null>(null);
  const focusedRef = useRef<HTMLElement>(null);
  const schools = useSchoolNames(org, !school);
  const loadedRef = useRef(onLoaded);
  loadedRef.current = onLoaded;
  const changed = () => { setEditing(undefined); setRefresh(n => n + 1); }, action = useCommand(org, changed), side = school ? 'school' : 'enterprise';
  useFocusedWorkflow<Verification>(org, '/' + side + '/verifications', focusId, refresh, value => {
    if (value.verificationId !== focusId || (school && value.state === 'DRAFT')) throw new Error('Unexpected verification');
    setFocusedRecord(value); setExpanded(value.verificationId);
  }, () => setError('Hồ sơ xác minh đã thay đổi hoặc không còn trong phạm vi truy cập.'));
  useWorkflowFocus(focusedRef, !!focusId && !loading, focusedRecord);

  useEffect(() => {
    const c = new AbortController(); setError(''); setItems([]); setLoading(true);
    void api<ContractPage<Verification>>('/' + side + '/verifications?page=' + page + '&size=25', { signal: c.signal, headers: { 'X-Organization-Id': org } })
      .then(p => { if (!c.signal.aborted) {
        const visible = school ? p.content.filter(item => item.state !== 'DRAFT') : p.content;
        setItems(visible); setTotal(p.totalPages); loadedRef.current?.(visible);
      } })
      .catch(() => { if (!c.signal.aborted) setError('Chưa tải được hồ sơ xác minh.'); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [org, side, page, refresh]);

  const filteredItems = items.filter(v => {
    if (stateFilter && v.state !== stateFilter) return false;
    if (searchText.trim()) {
      const q = searchText.trim().toLowerCase();
      return (
        v.data.registeredName.toLowerCase().includes(q)
        || (schools[v.data.schoolOrgId] || '').toLowerCase().includes(q)
        || v.data.registrationCode.toLowerCase().includes(q)
        || v.data.contactName.toLowerCase().includes(q)
        || v.data.contactEmail.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const schoolSummaryCards = [
    { state: 'SUBMITTED', label: 'Chờ duyệt', count: items.filter(v => v.state === 'SUBMITTED').length, tone: 'kpi-pending' },
    { state: 'NEEDS_CHANGES', label: 'Cần bổ sung', count: items.filter(v => v.state === 'NEEDS_CHANGES').length, tone: 'kpi-warning' },
    { state: 'APPROVED', label: 'Đã duyệt', count: items.filter(v => v.state === 'APPROVED').length, tone: 'kpi-approved' },
  ];
  const selectedSchoolRecord = school
    ? (focusedRecord
      || (expanded === '__none__' ? undefined : filteredItems.find(v => v.verificationId === expanded) || (expanded === null ? (filteredItems.find(v => v.state === 'SUBMITTED') || filteredItems[0]) : undefined)))
    : undefined;
  const visibleDetails = school
    ? (selectedSchoolRecord ? [selectedSchoolRecord] : [])
    : [...filteredItems.filter(v => expanded === v.verificationId && v.verificationId !== focusedRecord?.verificationId), ...(focusedRecord ? [focusedRecord] : [])];

  if (school) {
    return (
      <SchoolVerificationView
        items={items}
        loading={loading}
        error={error}
        actionMessage={action.message}
        retryable={action.retryable}
        retrying={action.retrying}
        busy={action.busy}
        onRetry={() => void action.retry()}
        onReload={changed}
        onDecide={(v, data) => action.command('/school/verifications/' + v.verificationId + '/decision', 'PATCH', { expectedVersion: v.version, ...data })}
        focusId={focusId}
        focusedRecord={focusedRecord}
        focusedRef={focusedRef}
        page={page}
        total={total}
        onPageChange={setPage}
      />
    );
  }

  return <section className="panel workflow-surface enterprise-verifications-workspace">
    <div className="workflow-kpi-grid school-summary-strip" aria-label="Thống kê hồ sơ xác minh">
      {schoolSummaryCards.map(card => <button key={card.state} type="button"
        className={`kpi-card ${card.tone}${stateFilter === card.state ? ' is-active' : ''}`}
        disabled={loading || Boolean(error) || action.busy}
        aria-pressed={stateFilter === card.state} title={stateFilter === card.state ? 'Bỏ lọc trạng thái' : `Lọc: ${card.label}`}
        onClick={() => setStateFilter(current => current === card.state ? '' : card.state)}>
        <span className="kpi-card-label">{card.label}</span>
        <span className="kpi-card-bottom">
          <strong className="kpi-card-value">{loading || error ? '—' : card.count}</strong>
          <span className="kpi-card-hint" aria-hidden="true">{stateFilter === card.state ? 'Đang lọc · Bỏ lọc' : 'Trong trang hiện tại'}</span>
        </span>
      </button>)}
    </div>

    <div className="workflow-section-header">
      <div>
        <div className="workflow-section-heading">
          <h3 className="workflow-subtoolbar-title">Hồ sơ xác minh theo trường</h3>
          {items.length > 0 && <span className="workflow-count-pill">{filteredItems.length}/{items.length} hồ sơ</span>}
        </div>
      </div>
      <div className="button-row">
        {!school && <button className="t5-btn-teal" disabled={action.busy} onClick={() => {
          if (canLeaveDemand()) { setExpanded(null); setFocusedRecord(null); setEditing(null); }
        }}><Icon name="plus" size={17} />Tạo hồ sơ xác minh</button>}
        <button className="secondary" disabled={action.busy || loading} onClick={changed}><Icon name="refresh" size={17} />Tải lại xác minh</button>
      </div>
    </div>

    <Notice message={error || action.message} tone="error" />
    {action.retryable && <button disabled={action.retrying} onClick={() => void action.retry()}>Thử lại yêu cầu đã gửi</button>}

    {(school || items.length > 0) && <>
      <div className="placement-filter-bar">
        <label>Lọc trạng thái xác minh
          <select value={stateFilter} onChange={e => setStateFilter(e.target.value)}>
            <option value="">Tất cả trạng thái</option>
            {Object.entries(states).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </label>
        <label>Tìm kiếm hồ sơ xác minh
          <input type="search" placeholder="Tìm trong trang theo trường, doanh nghiệp, mã ĐKKD…" value={searchText} onChange={e => setSearchText(e.target.value)} />
        </label>
        {(stateFilter || searchText) && <button type="button" className="secondary" onClick={() => { setStateFilter(''); setSearchText(''); }}>Xóa bộ lọc</button>}
      </div>
    </>}

    {loading && <p className="rc-loading-state" role="status">Đang tải hồ sơ xác minh…</p>}
    {!school && !loading && !error && items.length === 0 && <EmptyState icon="shield" title="Chưa có hồ sơ xác minh trong phạm vi này." description="Tạo hồ sơ để gửi xác minh đến trường tiếp nhận sinh viên." />}
    {!school && items.length > 0 && filteredItems.length === 0 && <p>Không có hồ sơ xác minh khớp với bộ lọc hiện tại.</p>}

    {!school && filteredItems.length > 0 && <div className="proposal-table enterprise-mobile-list"><table><thead><tr><th>Trường tiếp nhận</th><th>Doanh nghiệp</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{filteredItems.map(v => <tr key={v.verificationId} className={expanded === v.verificationId ? 'table-row-selected' : undefined}>
        <th scope="row" data-label="Trường tiếp nhận">{schools[v.data.schoolOrgId] || 'Trường đối tác'}<small>{v.data.registrationCode}</small></th>
        <td data-label="Doanh nghiệp">{v.data.registeredName}</td><td data-label="Trạng thái"><span className={'status-pill pill-' + v.state.toLowerCase()}><span className="pill-dot" />{states[v.state] || v.state}</span></td>
        <td data-label="Thao tác"><div className="button-row"><button className="secondary" aria-expanded={expanded === v.verificationId}
          disabled={action.busy} onClick={() => {
            if (canLeaveDemand()) { setEditing(undefined); setFocusedRecord(null); setExpanded(old => old === v.verificationId ? null : v.verificationId); }
          }}>Xem hồ sơ</button>
          {['DRAFT', 'NEEDS_CHANGES'].includes(v.state) && <>
            <button className="secondary" disabled={action.busy} onClick={() => {
              if (canLeaveDemand()) { setExpanded(null); setFocusedRecord(null); setEditing(v); }
            }}>Bổ sung hồ sơ</button>
            <button className="t5-btn-teal" disabled={action.busy || !v.data.evidenceFileIds.length} onClick={() => void action.command('/enterprise/verifications/' + v.verificationId + '/submit', 'PATCH', { expectedVersion: v.version })}>Gửi xác minh</button>
          </>}
        </div></td>
      </tr>)}</tbody></table></div>}
    {school && <div className="proposal-table"><table><thead><tr><th>Doanh nghiệp</th><th>Mã ĐKKD</th><th>Liên hệ</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{filteredItems.map(v => {
        const isSelected = selectedSchoolRecord?.verificationId === v.verificationId;
        return <tr key={v.verificationId} className={isSelected ? 'table-row-selected' : undefined}>
          <td><h3 className="workflow-table-title" title={v.data.registeredName}>{v.data.registeredName}</h3><small title={v.data.address}>{v.data.address}</small></td>
          <td><code>{v.data.registrationCode}</code></td>
          <td>{v.data.contactName}<small title={v.data.contactEmail}>{v.data.contactEmail}</small></td>
          <td><span className={`status-pill pill-${v.state.toLowerCase()}`}><span className="pill-dot" />{states[v.state] || v.state}</span></td>
          <td><button type="button" className="secondary" aria-expanded={isSelected} disabled={action.busy}
            onClick={() => setExpanded(isSelected ? '__none__' : v.verificationId)}>{isSelected ? 'Ẩn chi tiết' : 'Xem hồ sơ'}</button></td>
        </tr>;
      })}{!loading && !error && filteredItems.length === 0 && <tr><td colSpan={5}>{items.length === 0 ? 'Chưa có hồ sơ xác minh trong phạm vi này.' : 'Không có hồ sơ xác minh khớp với bộ lọc hiện tại.'}</td></tr>}</tbody></table></div>}
    <div className="workflow-card-list">
      {visibleDetails.map(v => <article key={v.verificationId} ref={v.verificationId === focusId ? focusedRef : undefined} tabIndex={-1} className={`${school ? 'workflow-detail-drawer' : 'workflow-item-card'} state-border-${v.state.toLowerCase()}`}>
        <div className="workflow-card-top">
          <div>
            <h3>{v.data.registeredName} · {states[v.state]}</h3>
            <p className="workflow-card-meta">Mã ĐKKD: <strong>{v.data.registrationCode}</strong> · Trường nhận: <strong>{schools[v.data.schoolOrgId] || 'Trường tiếp nhận'}</strong> · {v.data.address}</p>
          </div>
          <div className="placement-title-group">
            <span className={`status-pill pill-${v.state.toLowerCase()}`}>
              <span className="pill-dot" />
              {states[v.state] || v.state}
            </span>
            {!school && <button type="button" className="secondary" onClick={() => { setExpanded(null); setFocusedRecord(null); }}>Đóng chi tiết</button>}
          </div>
        </div>

        <VerificationJourney state={v.state} />
        <div className="placement-meta-grid">
          <div className="placement-meta-item">
            <span className="placement-meta-label">Thông tin liên hệ</span>
            <span className="placement-meta-value verification-contact"><span>{v.data.contactName}</span><a href={'mailto:' + v.data.contactEmail}>{v.data.contactEmail}</a><a href={'tel:' + v.data.contactPhone}>{v.data.contactPhone}</a></span>
          </div>
          <div className="placement-meta-item">
            <span className="placement-meta-label">Hồ sơ đính kèm</span>
            <span className="placement-meta-value">{v.data.evidenceFileIds.length} minh chứng đã liên kết</span>
          </div>
          {v.decisionRef && <div className="placement-meta-item">
            <span className="placement-meta-label">Căn cứ quyết định</span>
            <span className="placement-meta-value"><code>{v.decisionRef}</code></span>
          </div>}
        </div>

        {v.missingFields.length > 0 && <div className="workflow-feedback-box">
          <p>Cần bổ sung: {v.missingFields.map(f => fieldLabels[f] || f).join(', ')}</p>
        </div>}

        {school && v.state === 'SUBMITTED' && (
          <ReviewDecision
            busy={action.busy}
            fields={['registrationCode', 'registeredName', 'address', 'contactName', 'contactEmail', 'contactPhone', 'evidenceFileIds']}
            submit={data => action.command('/school/verifications/' + v.verificationId + '/decision', 'PATCH', { expectedVersion: v.version, ...data })}
          />
        )}

      </article>)}
    </div>

    {editing !== undefined && (
      <VerificationEditor
        org={org}
        key={editing?.verificationId || 'new'}
        existing={editing}
        busy={action.busy}
        onCancel={() => setEditing(undefined)}
        save={data => action.command('/enterprise/verifications' + (editing ? '/' + editing.verificationId : ''), editing ? 'PATCH' : 'POST', editing ? { expectedVersion: editing.version, data } : data)}
      />
    )}

    {total > 1 && <div className="button-row">
      <button className="secondary" disabled={page === 0 || action.busy || loading} onClick={() => setPage(n => n - 1)}>Trước</button>
      <span>Trang {page + 1}/{Math.max(1, total)}</span>
      <button className="secondary" disabled={page + 1 >= total || action.busy || loading} onClick={() => setPage(n => n + 1)}>Sau</button>
    </div>}
  </section>;
}
