import { useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { Notice } from '../../components';
import type { ContractPage } from '../student/types';
import { states, fieldLabels } from './workflow/types';
import type { Cooperation, Detail, Verification } from './workflow/types';
import { useCommand } from './workflow/useCommand';
import { ReviewDecision } from './workflow/ReviewDecision';
import { CooperationComposer } from './workflow/CooperationComposer';
import { VerificationEditor } from './workflow/VerificationEditor';
import { useSchoolNames } from './portal/useSchoolNames';
import type { Demand } from './types';
import { EnterpriseProposals } from './proposals/EnterpriseProposals';
import { SchoolProposals } from './proposals/SchoolProposals';
import { PlacementWorkspace } from './proposals/PlacementWorkspace';
import { canLeaveDemand } from './navigation';
import { useFocusedWorkflow } from './workflow/useFocusedWorkflow';
export type { CooperationRecord, VerificationInput, VerificationRecord } from './workflow/types';

export function CooperationFlows({ org, school, demands = [], onLoaded, includeProgress = true, focusId }: { org: string; school: boolean; demands?: Demand[]; onLoaded?: (items: Cooperation[]) => void; includeProgress?: boolean; focusId?: string }) {
  const [items, setItems] = useState<Cooperation[]>([]), [detail, setDetail] = useState<Detail | null>(null);
  const [refresh, setRefresh] = useState(0), [page, setPage] = useState(0), [total, setTotal] = useState(0), [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [stateFilter, setStateFilter] = useState(''), [searchText, setSearchText] = useState('');
  const [composing, setComposing] = useState(false);
  const loadedRef = useRef(onLoaded);
  loadedRef.current = onLoaded;
  const side = school ? 'school' : 'enterprise', changed = () => { setDetail(null); setComposing(false); setRefresh(n => n + 1); };
  const action = useCommand(org, changed);
  const focusedRef = useRef<HTMLElement>(null);
  useFocusedWorkflow<Detail>(org, '/' + side + '/cooperations', focusId, refresh, value => {
    if (value.cooperation.cooperationId !== focusId) throw new Error('Unexpected cooperation');
    setDetail(value);
  }, () => setError('Đề nghị đã thay đổi hoặc không còn trong phạm vi truy cập.'));
  useEffect(() => { if (focusId && detail) focusedRef.current?.focus(); }, [focusId, detail]);

  useEffect(() => {
    const update = () => changed();
    window.addEventListener('cooperation-updated', update);
    return () => window.removeEventListener('cooperation-updated', update);
  }, []);

  useEffect(() => {
    const c = new AbortController(); setError(''); setDetail(null); setItems([]); setLoading(true);
    void api<ContractPage<Cooperation>>('/' + side + '/cooperations?page=' + page + '&size=25', { signal: c.signal, headers: { 'X-Organization-Id': org } })
      .then(p => { if (!c.signal.aborted) { setItems(p.content); setTotal(p.totalPages); loadedRef.current?.(p.content); } })
      .catch(() => { if (!c.signal.aborted) setError('Chưa tải được đề nghị hợp tác.'); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [org, side, refresh, page]);

  const indexedItems = items.map((c, n) => ({ item: c, seq: page * 25 + n + 1 })).filter(({ item, seq }) => {
    if (stateFilter && item.state !== stateFilter) return false;
    if (searchText.trim()) {
      const q = searchText.trim().toLowerCase();
      return (
        `đề nghị ${seq}`.includes(q)
        || item.cooperationId.toLowerCase().includes(q)
        || item.demandId.toLowerCase().includes(q)
        || item.termId.toLowerCase().includes(q)
        || (states[item.state] || item.state).toLowerCase().includes(q)
      );
    }
    return true;
  });

  return <section className="panel workflow-surface">
    <div className="workflow-section-header">
      <div>
        <h2>Đề nghị hợp tác</h2>
      </div>
      <div className="button-row">
        {!school && <button type="button" className="t5-btn-teal" disabled={action.busy} onClick={() => setComposing(v => !v)}>Soạn từ nhu cầu</button>}
        <button className="secondary" disabled={action.busy} onClick={changed}>Tải lại đề nghị</button>
      </div>
    </div>

    <Notice message={action.message || error} tone="error" />
    {action.retryable && <button disabled={action.retrying} onClick={() => void action.retry()}>Thử lại yêu cầu đã gửi</button>}

    {!school && composing && <CooperationComposer org={org} demands={demands} busy={action.busy} close={() => setComposing(false)}
      save={demand => void action.command('/enterprise/cooperations', 'POST', { demandId: demand.demandId, expectedDemandVersion: demand.version })}
      submit={demand => void action.command('/enterprise/demands/submit-with-cooperation', 'POST', { demandId: demand.demandId, expectedDemandVersion: demand.version })} />}

    {items.length > 0 && <>
      <div className="placement-filter-bar">
        <label>Lọc trạng thái đề nghị
          <select value={stateFilter} onChange={e => setStateFilter(e.target.value)}>
            <option value="">Tất cả trạng thái</option>
            {Object.entries(states).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </label>
        <label>Tìm kiếm đề nghị hợp tác
          <input type="search" placeholder="Tìm trong trang theo mã đề nghị, mã kỳ…" value={searchText} onChange={e => setSearchText(e.target.value)} />
        </label>
        {(stateFilter || searchText) && <button type="button" className="secondary" onClick={() => { setStateFilter(''); setSearchText(''); }}>Xóa bộ lọc</button>}
      </div>
    </>}

    {loading && <p role="status">Đang tải đề nghị hợp tác…</p>}
    {!loading && !error && items.length === 0 && <p>Chưa có đề nghị trong phạm vi này.</p>}
    {items.length > 0 && indexedItems.length === 0 && <p>Không tìm thấy đề nghị hợp tác phù hợp bộ lọc hiện tại.</p>}

    {!school ? <div className="proposal-table"><table><thead><tr><th>Yêu cầu</th><th>Nhu cầu</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{indexedItems.map(({ item: c, seq }) => <tr key={c.cooperationId}>
        <th scope="row">Đề nghị {seq}<small>Lần gửi {c.submissionNo}</small></th>
        <td>{demands.find(demand => demand.demandId === c.demandId)?.data.title || 'Nhu cầu đã gửi'}</td>
        <td><span className={'status-pill pill-' + c.state.toLowerCase()}>{states[c.state] || c.state}</span></td>
        <td><button className="secondary" disabled={action.busy} onClick={() => void api<Detail>('/enterprise/cooperations/' + c.cooperationId,
          { headers: { 'X-Organization-Id': org } }).then(setDetail).catch(() => setError('Không đọc được đề nghị.'))}>Xem nội dung đề nghị {seq}</button></td>
      </tr>)}</tbody></table></div> : <div className="workflow-card-list">
      {indexedItems.map(({ item: c, seq }) => {
        const isSelected = detail?.cooperation.cooperationId === c.cooperationId;
        return <article key={c.cooperationId} className={`workflow-item-card state-border-${c.state.toLowerCase()} ${isSelected ? 'card-selected' : ''}`}>
          <div className="workflow-card-top">
            <div>
              <h3>Đề nghị {seq} · {states[c.state] || c.state}</h3>
              <p className="workflow-card-meta">Lần gửi {c.submissionNo} · Kỳ <code>{c.termId.slice(0, 8)}</code> · Phiên bản v{c.version}</p>
            </div>
            <div className="placement-title-group">
              <span className="placement-seq-badge">BM-02</span>
              <span className={`status-pill pill-${c.state.toLowerCase()}`}>
                <span className="pill-dot" />
                {states[c.state] || c.state}
              </span>
            </div>
          </div>
          <div className="button-row">
            <button className="secondary" onClick={() => void api<Detail>('/' + side + '/cooperations/' + c.cooperationId, { headers: { 'X-Organization-Id': org } }).then(setDetail).catch(() => setError('Không đọc được đề nghị.'))}>
              Xem nội dung đề nghị {seq}
            </button>
          </div>
        </article>;
      })}
    </div>}

    {total > 1 && <div className="button-row">
      <button className="secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Trước</button>
      <span>Trang {page + 1}/{Math.max(1, total)}</span>
      <button className="secondary" disabled={page + 1 >= total} onClick={() => setPage(n => n + 1)}>Sau</button>
    </div>}

    {detail && <article className="workflow-detail-drawer" ref={focusedRef} tabIndex={-1}>
      <div className="workflow-card-top">
        <div>
          <h3>{detail.demand.data.title}</h3>
        </div>
        <span className={`status-pill pill-${detail.cooperation.state.toLowerCase()}`}>
          <span className="pill-dot" />
          {states[detail.cooperation.state] || detail.cooperation.state}
        </span>
      </div>

      <div className={`placement-status-banner tone-${detail.cooperation.state.toLowerCase()}`} role="note">
        <p>
          {detail.cooperation.state === 'SUBMITTED' && (school
            ? 'Đề nghị hợp tác đang chờ Nhà trường xét duyệt. Kiểm tra chỉ tiêu, tiêu chí kỹ năng và khoảng thời gian trước khi ra quyết định.'
            : 'Đề nghị hợp tác đã được gửi tới Nhà trường và đang chờ phản hồi xét duyệt.')}
          {detail.cooperation.state === 'NEEDS_CHANGES' && 'Bổ sung theo phản hồi của nhà trường và gửi lại đề nghị.'}
          {detail.cooperation.state === 'APPROVED' && 'Đề nghị hợp tác đã được phê duyệt. Quy trình đề xuất ứng viên, chia sẻ hồ sơ và tiếp nhận thực tập đã sẵn sàng.'}
          {detail.cooperation.state === 'REJECTED' && 'Đề nghị hợp tác đã bị từ chối. Doanh nghiệp có thể mở lại bản nháp để điều chỉnh và gửi lại.'}
          {detail.cooperation.state === 'DRAFT' && 'Đề nghị hợp tác đang ở trạng thái bản nháp, sẵn sàng gửi xét duyệt.'}
        </p>
      </div>

      <p className="workflow-detail-desc">{detail.demand.data.description}</p>

      <div className="placement-meta-grid">
        <div className="placement-meta-item">
          <span className="placement-meta-label">Quy mô & Địa điểm</span>
          <span className="placement-meta-value">{detail.demand.data.quantity} vị trí · {detail.demand.data.location || 'Chưa ghi nhận địa điểm'}</span>
        </div>
        <div className="placement-meta-item">
          <span className="placement-meta-label">Tiêu chí bắt buộc</span>
          <span className="placement-meta-value">Tiêu chí bắt buộc: {detail.demand.data.required.skills.map(s => s.skillCode).join(', ') || 'Không yêu cầu kỹ năng cụ thể'}</span>
        </div>
        <div className="placement-meta-item">
          <span className="placement-meta-label">Khoảng thời gian thực tập</span>
          <span className="placement-meta-value">Thời gian: {detail.demand.data.required.dateRange.startDate} – {detail.demand.data.required.dateRange.endDate}</span>
        </div>
      </div>

      {detail.feedback && <div className="workflow-feedback-box">
        <p>Phản hồi: {detail.feedback.reasonCode || 'Đã duyệt'} · Cần bổ sung: {detail.feedback.missingFields.map(f => fieldLabels[f] || f).join(', ') || 'Không có'} · Căn cứ: {detail.feedback.decisionRef}</p>
      </div>}

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
    if (value.verificationId !== focusId) throw new Error('Unexpected verification');
    setFocusedRecord(value); setExpanded(value.verificationId);
  }, () => setError('Hồ sơ xác minh đã thay đổi hoặc không còn trong phạm vi truy cập.'));
  useEffect(() => { if (focusId && focusedRecord) focusedRef.current?.focus(); }, [focusId, focusedRecord]);

  useEffect(() => {
    const c = new AbortController(); setError(''); setItems([]); setLoading(true);
    void api<ContractPage<Verification>>('/' + side + '/verifications?page=' + page + '&size=25', { signal: c.signal, headers: { 'X-Organization-Id': org } })
      .then(p => { if (!c.signal.aborted) { setItems(p.content); setTotal(p.totalPages); loadedRef.current?.(p.content); } })
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
        || v.data.registrationCode.toLowerCase().includes(q)
        || v.data.contactName.toLowerCase().includes(q)
        || v.data.contactEmail.toLowerCase().includes(q)
      );
    }
    return true;
  });

  return <section className="panel workflow-surface">
    <div className="workflow-section-header">
      <div>
        <h2>Danh sách hồ sơ xác minh</h2>
      </div>
      <div className="button-row">
        {!school && <button className="t5-btn-teal" disabled={action.busy} onClick={() => { if (canLeaveDemand()) setEditing(null); }}>Tạo hồ sơ xác minh</button>}
        <button className="secondary" disabled={action.busy} onClick={changed}>Tải lại xác minh</button>
      </div>
    </div>

    <Notice message={error || action.message} tone="error" />
    {action.retryable && <button disabled={action.retrying} onClick={() => void action.retry()}>Thử lại yêu cầu đã gửi</button>}

    {items.length > 0 && <>
      <div className="placement-filter-bar">
        <label>Lọc trạng thái xác minh
          <select value={stateFilter} onChange={e => setStateFilter(e.target.value)}>
            <option value="">Tất cả trạng thái</option>
            {Object.entries(states).map(([k, label]) => <option key={k} value={k}>{label}</option>)}
          </select>
        </label>
        <label>Tìm kiếm hồ sơ xác minh
          <input type="search" placeholder="Tìm theo tên doanh nghiệp, mã ĐKKD, người liên hệ…" value={searchText} onChange={e => setSearchText(e.target.value)} />
        </label>
        {(stateFilter || searchText) && <button type="button" className="secondary" onClick={() => { setStateFilter(''); setSearchText(''); }}>Xóa bộ lọc</button>}
      </div>
    </>}

    {loading && <p role="status">Đang tải hồ sơ xác minh…</p>}
    {!loading && !error && items.length === 0 && <p>Chưa có hồ sơ xác minh trong phạm vi này.</p>}
    {items.length > 0 && filteredItems.length === 0 && <p>Không có hồ sơ xác minh khớp với bộ lọc hiện tại.</p>}

    {!school && <div className="proposal-table"><table><thead><tr><th>Trường tiếp nhận</th><th>Doanh nghiệp</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
      <tbody>{filteredItems.map(v => <tr key={v.verificationId}>
        <th scope="row">{schools[v.data.schoolOrgId] || 'Trường đối tác · ' + v.data.schoolOrgId.slice(0, 8)}</th>
        <td>{v.data.registeredName}</td><td><span className={'status-pill pill-' + v.state.toLowerCase()}>{states[v.state] || v.state}</span></td>
        <td><div className="button-row"><button className="secondary" aria-expanded={expanded === v.verificationId}
          disabled={action.busy} onClick={() => setExpanded(old => old === v.verificationId ? null : v.verificationId)}>Xem hồ sơ</button>
          {['DRAFT', 'NEEDS_CHANGES'].includes(v.state) && <>
            <button className="secondary" disabled={action.busy} onClick={() => { if (canLeaveDemand()) setEditing(v); }}>Bổ sung hồ sơ</button>
            <button disabled={action.busy || !v.data.evidenceFileIds.length} onClick={() => void action.command('/enterprise/verifications/' + v.verificationId + '/submit', 'PATCH', { expectedVersion: v.version })}>Gửi xác minh</button>
          </>}
        </div></td>
      </tr>)}</tbody></table></div>}
    <div className="workflow-card-list">
      {[...filteredItems.filter(v => (school || expanded === v.verificationId) && v.verificationId !== focusedRecord?.verificationId), ...(focusedRecord ? [focusedRecord] : [])]
        .map(v => <article key={v.verificationId} ref={v.verificationId === focusId ? focusedRef : undefined} tabIndex={-1} className={`workflow-item-card state-border-${v.state.toLowerCase()}`}>
        <div className="workflow-card-top">
          <div>
            <h3>{v.data.registeredName} · {states[v.state]}</h3>
            <p className="workflow-card-meta">Mã ĐKKD: <strong>{v.data.registrationCode}</strong> · Trường nhận: <code>{v.data.schoolOrgId.slice(0, 8)}</code> · {v.data.address}</p>
          </div>
          <div className="placement-title-group">
            <span className={`status-pill pill-${v.state.toLowerCase()}`}>
              <span className="pill-dot" />
              {states[v.state] || v.state}
            </span>
          </div>
        </div>

        <div className="placement-meta-grid">
          <div className="placement-meta-item">
            <span className="placement-meta-label">Thông tin liên hệ</span>
            <span className="placement-meta-value">Liên hệ: {v.data.contactName} · {v.data.contactEmail} · {v.data.contactPhone}</span>
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
      <button className="secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Trước</button>
      <span>Trang {page + 1}/{Math.max(1, total)}</span>
      <button className="secondary" disabled={page + 1 >= total} onClick={() => setPage(n => n + 1)}>Sau</button>
    </div>}
  </section>;
}
