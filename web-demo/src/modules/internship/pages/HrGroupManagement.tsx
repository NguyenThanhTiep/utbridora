import { useRef, useState, type FormEvent } from 'react';
import { api, ApiError } from '../../../api';
import { newIdempotencyKey } from '../../student/api';
import type { CaseRef } from '../cases';
import { caseStateLabel, formatEntityShort, formatGroupCode } from '../displayLabels';
import type { InternshipGroup, WorkspaceAssignment } from '../types';

const isUuid = (value: string) => /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
function failureMessage(error: unknown) {
  if (error instanceof ApiError) return error.status === 409
    ? 'Hồ sơ hoặc nhóm vừa thay đổi. Tải lại danh sách nhóm rồi thử lại.'
    : error.message + (error.requestId ? ` (mã hỗ trợ ${error.requestId})` : '');
  return 'Không kết nối được máy chủ. Giữ nội dung đã nhập và thử lại.';
}

export function HrGroupManagement({ assignment, group, groups, changed }: {
  assignment: WorkspaceAssignment; group: InternshipGroup; groups: InternshipGroup[]; changed: () => void;
}) {
  const [caseId, setCaseId] = useState(''), [moveId, setMoveId] = useState('');
  const [targetId, setTargetId] = useState(''), [reason, setReason] = useState('');
  const [inspectId, setInspectId] = useState(''), [detail, setDetail] = useState<CaseRef | null>(null);
  const [detailError, setDetailError] = useState('');
  const [busy, setBusy] = useState(false), [feedback, setFeedback] = useState('');
  const pending = useRef<{ action: string; body: string; key: string } | null>(null);
  const headers = { 'X-Context-Assignment-Id': assignment.id };
  async function readCase(id: string) {
    const value = await api<CaseRef>(`/t3/cases/${encodeURIComponent(id)}`, { headers });
    if (value.caseId !== id || value.schoolOrgId !== assignment.scope.schoolOrgId
      || value.enterpriseOrgId !== assignment.scope.enterpriseOrgId || value.termId !== assignment.scope.termId) {
      throw new Error('Phạm vi hồ sơ không khớp phân công HR.');
    }
    return value;
  }
  const [query, setQuery] = useState('');
  const members = group.caseIds.filter(id => id.toLowerCase().includes(query.trim().toLowerCase()));
  async function inspect(id = inspectId) {
    if (!id) return;
    setInspectId(id);
    setBusy(true); setDetail(null); setDetailError('');
    try { setDetail(await readCase(id)); }
    catch (error) { setDetailError(failureMessage(error)); }
    finally { setBusy(false); }
  }
  async function submit(action: 'add' | 'move', event: FormEvent) {
    event.preventDefault();
    const id = (action === 'add' ? caseId : moveId).trim();
    if (!isUuid(id) || action === 'move' && (!targetId || !/^[A-Z0-9_]{3,64}$/.test(reason.trim()))) {
      setFeedback('Kiểm tra Case ID, nhóm đích và mã lý do (3–64 ký tự A-Z, 0-9, _).'); return;
    }
    setBusy(true); setFeedback('');
    try {
      const path = `/t3/groups/${encodeURIComponent(group.groupId)}/members${action === 'move' ? ':move' : ''}`;
      if (!pending.current || pending.current.action !== path) {
        const current = await readCase(id);
        const target = groups.find(item => item.groupId === targetId);
        if (action === 'move' && (!group.caseIds.includes(id) || !target || target.groupId === group.groupId)) {
          setFeedback('Chọn hồ sơ thuộc nhóm hiện tại và một nhóm đích khác.'); return;
        }
        const body = action === 'add'
          ? { caseId: id, expectedCaseVersion: current.version, expectedGroupVersion: group.version }
          : { caseId: id, toGroupId: target!.groupId, expectedCaseVersion: current.version,
              expectedFromGroupVersion: group.version, expectedToGroupVersion: target!.version, reasonCode: reason.trim() };
        const encoded = JSON.stringify(body);
        pending.current = { action: path, body: encoded, key: newIdempotencyKey() };
      }
      await api(path, { method: 'POST', body: pending.current.body,
        headers: { ...headers, 'Idempotency-Key': pending.current.key } });
      pending.current = null;
      if (action === 'add') setCaseId('');
      else { setMoveId(''); setTargetId(''); setReason(''); }
      changed();
      setFeedback(action === 'add' ? 'Đã thêm hồ sơ vào nhóm.' : 'Đã chuyển hồ sơ sang nhóm đích.');
    } catch (error) {
      if (error instanceof ApiError && [400, 403, 404, 409, 422].includes(error.status)) pending.current = null;
      setFeedback(failureMessage(error));
    }
    finally { setBusy(false); }
  }
  return <section className="panel stack t3-group-management">
    <div className="t3-group-mgmt-head"><h4>Hồ sơ trong nhóm</h4>
      <p className="muted">Chọn hồ sơ để kiểm tra trạng thái hoặc chuyển sang nhóm khác trong đợt.</p></div>
    <div className={'t3-group-mgmt-grid' + (group.caseIds.length === 0 ? ' is-single' : '')}>
    {group.caseIds.length > 0 && <div className="stack t3-group-members-pane">
      <label>Lọc theo mã hồ sơ<input type="search" value={query} placeholder="Tìm theo mã hồ sơ..." onChange={e=>setQuery(e.target.value)} /></label>
      <div className="t3-table-scroll"><table><thead><tr><th>Hồ sơ</th><th>Nhóm</th><th>Thao tác</th></tr></thead><tbody>
        {members.map(id=><tr key={id}>
          <th scope="row" title={id}><span className="t3-code-pill">Hồ sơ {formatEntityShort(id)}</span></th><td title={group.code}>{formatGroupCode(group.code)}</td><td><button className="secondary" disabled={busy}
          aria-label={'Xem hồ sơ ' + id} onClick={()=>void inspect(id)}>Xem hồ sơ</button></td></tr>)}
      </tbody></table></div>
      {members.length === 0 && <p>Không tìm thấy hồ sơ phù hợp.</p>}
      <details><summary>Tra cứu theo mã Case</summary><label>Kiểm tra trạng thái Case<select disabled={busy} value={inspectId} onChange={event => {
        setInspectId(event.target.value); setDetail(null); setDetailError('');
      }}>
        <option value="">Chọn hồ sơ…</option>
        {group.caseIds.map(id => <option key={id} value={id}>Hồ sơ {formatEntityShort(id)}</option>)}
      </select></label>
      <button type="button" className="secondary" disabled={busy || !inspectId} onClick={() => void inspect()}>Xem hồ sơ</button></details>
      {detail && <dl className="internship-facts">
        <div><dt>Trạng thái</dt><dd>{caseStateLabel(detail.state)}</dd></div>
        <div><dt>Mã tiếp nhận</dt><dd><code title={detail.placementId}>{formatEntityShort(detail.placementId)}</code></dd></div>
        <div><dt>Cập nhật</dt><dd>#{detail.version}</dd></div>
      </dl>}
      {detailError && <p role="alert">{detailError}</p>}
    </div>}
    <div className="stack t3-group-forms-pane">
    <form className="stack" onSubmit={event => void submit('add', event)}>
      <h4>Thêm vào nhóm</h4>
      <label>Case ID chưa có nhóm<input value={caseId} onChange={event => { setCaseId(event.target.value); pending.current = null; }} placeholder="UUID hồ sơ trong đợt" required /></label>
      <button disabled={busy || !isUuid(caseId.trim())}>Thêm vào nhóm</button>
    </form>
    {group.caseIds.length > 0 && groups.length > 1 && <form className="stack" onSubmit={event => void submit('move', event)}>
      <h4>Chuyển hồ sơ sang nhóm khác</h4>
      <label>Hồ sơ trong nhóm<select value={moveId} onChange={event => { setMoveId(event.target.value); pending.current = null; }} required>
        <option value="">Chọn hồ sơ…</option>
        {group.caseIds.map(id => <option key={id} value={id}>Hồ sơ {formatEntityShort(id)}</option>)}
      </select></label>
      <label>Nhóm đích<select value={targetId} onChange={event => { setTargetId(event.target.value); pending.current = null; }} required>
        <option value="">Chọn nhóm…</option>
        {groups.filter(item => item.groupId !== group.groupId).map(item =>
          <option key={item.groupId} value={item.groupId}>{formatGroupCode(item.code)} · {item.caseIds.length} hồ sơ</option>)}
      </select></label>
      <label>Mã lý do<input value={reason} onChange={event => { setReason(event.target.value.toUpperCase()); pending.current = null; }}
        minLength={3} maxLength={64} pattern="[A-Z0-9_]{3,64}" placeholder="GROUP_REBALANCE" required /></label>
      <button disabled={busy || !moveId || !targetId || !/^[A-Z0-9_]{3,64}$/.test(reason.trim())}>Chuyển hồ sơ</button>
    </form>}
    </div>
    </div>
    {feedback && <p role="status">{feedback}</p>}
  </section>;
}
