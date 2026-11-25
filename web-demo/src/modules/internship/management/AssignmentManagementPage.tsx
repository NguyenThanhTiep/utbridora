import { useEffect, useState } from 'react';
import { EmptyState, LoadingState, Notice } from '../../../components';
import { useOrganizationNames } from '../../useOrganizationNames';
import type { WorkspacePage } from '../types';
import { useWorkflowCommand } from '../useWorkflowCommand';
import { CommandFeedback, GrantAssignment, MentorHandover } from './AssignmentForms';
import { dateLabel, ManagementPager, managementBase, shortId, useManagementRead, type ManagedAssignment, type ManagementScope } from './read';
import './assignment-management.css';

const modeNames = { SCHOOL: 'Nhà trường xác nhận', COORDINATOR: 'Coordinator quản lý HR', HR: 'HR quản lý Mentor' };
export function AssignmentManagementPage() {
  const [page, setPage] = useState(0), [id, setId] = useState(''), [revision, setRevision] = useState(0), [locked, setLocked] = useState(false);
  const scopes = useManagementRead<WorkspacePage<ManagementScope>>(managementBase + '/scopes?page=' + page, undefined, revision);
  const scope = scopes.data?.content.find(s => s.id === id) || (!id ? scopes.data?.content[0] : undefined);
  const names = useOrganizationNames();
  useEffect(() => {
    if (!scope || locked) return;
    const wait = Date.parse(scope.validUntil) - Date.now();
    const timer = window.setTimeout(() => { setId(''); setRevision(n => n + 1); }, Math.max(0, Math.min(wait + 10, 2_147_483_647)));
    return () => window.clearTimeout(timer);
  }, [scope?.id, scope?.validUntil, locked]);
  return <div className="stack assignment-management"><section className="panel stack"><h2>Quản lý phân công thực tập</h2>
    <p>Xác nhận Coordinator, phân công HR, bàn giao Mentor và thu hồi quyền trong phạm vi đang được giao.</p>
    <Notice message={scopes.error} tone="error" />{scopes.loading && <LoadingState />}
    <label>Phạm vi cấp quyền<select disabled={locked || scopes.loading} value={scope?.id || ''} onChange={e => setId(e.target.value)}>
      <option value="">Chọn phạm vi…</option>{scopes.data?.content.map(s => <option key={s.id} value={s.id}>{modeNames[s.mode]} · {names[s.schoolOrgId] || 'Trường ' + shortId(s.schoolOrgId)} · {s.termCode}{s.enterpriseOrgId ? ' · ' + (names[s.enterpriseOrgId] || 'DN ' + shortId(s.enterpriseOrgId)) : ''}</option>)}
    </select></label><ManagementPager data={scopes.data} disabled={locked || scopes.loading} change={p => { setId(''); setPage(p); }} />
    <button type="button" className="secondary" disabled={locked} onClick={() => { setId(''); setRevision(n => n + 1); }}>Tải lại phạm vi cấp quyền</button>
    {scopes.data?.totalElements === 0 && <EmptyState title="Chưa có phạm vi cấp quyền phù hợp" description="Cần ủy quyền xác nhận Coordinator, hoặc phân công Coordinator/HR còn hiệu lực. Vai trò doanh nghiệp hoặc Admin riêng lẻ chưa đủ." />}
  </section>
    {scope && <EnterpriseScope key={scope.id + ':' + revision} scope={scope} locked={locked} lock={setLocked} reset={() => { setId(''); setRevision(n => n + 1); }} />}
  </div>;
}
function EnterpriseScope({ scope, locked, lock, reset }: { scope: ManagementScope; locked: boolean; lock: (value: boolean) => void; reset: () => void }) {
  const [page, setPage] = useState(0), [enterprise, setEnterprise] = useState('');
  const options = useManagementRead<WorkspacePage<{ enterpriseOrgId: string; name: string; canConfirm: boolean }>>(scope.mode === 'SCHOOL'
    ? managementBase + '/enterprises?' + new URLSearchParams({ schoolOrgId: scope.schoolOrgId, termId: scope.termId, page: String(page) }) : null);
  const selected = scope.enterpriseOrgId || (options.data?.content.some(o => o.enterpriseOrgId === enterprise) ? enterprise : '');
  return <>
    {scope.mode === 'SCHOOL' && <section className="panel stack"><h2>Doanh nghiệp được xác nhận</h2><Notice message={options.error} tone="error" />
      <label>Doanh nghiệp<select disabled={locked || options.loading} value={selected} onChange={e => setEnterprise(e.target.value)}><option value="">Chọn doanh nghiệp…</option>{options.data?.content.map(o => <option key={o.enterpriseOrgId} value={o.enterpriseOrgId}>{o.name}{o.canConfirm ? '' : ' · Chỉ tra cứu/thu hồi'}</option>)}</select></label>
      {options.data?.totalElements === 0 && <p>Chưa có doanh nghiệp VERIFIED và có hồ sơ xác minh được trường chấp thuận.</p>}
      <ManagementPager data={options.data} disabled={locked || options.loading} change={p => { setEnterprise(''); setPage(p); }} />
    </section>}
    {selected && <ScopeManagement key={scope.id + ':' + selected} scope={scope} enterprise={selected} canGrant={scope.mode !== 'SCHOOL' || !!options.data?.content.find(o => o.enterpriseOrgId === selected)?.canConfirm} lock={lock} reset={reset} />}
  </>;
}
function ScopeManagement({ scope, enterprise, canGrant, lock, reset }: { scope: ManagementScope; enterprise: string; canGrant: boolean; lock: (value: boolean) => void; reset: () => void }) {
  const [page, setPage] = useState(0), [inactive, setInactive] = useState(false), [revision, setRevision] = useState(0), [message, setMessage] = useState('');
  const [selected, setSelected] = useState(''), [reason, setReason] = useState(''), [confirm, setConfirm] = useState(false);
  const context = scope.contextId || undefined;
  const query = new URLSearchParams({ schoolOrgId: scope.schoolOrgId, enterpriseOrgId: enterprise, termId: scope.termId }).toString();
  const command = useWorkflowCommand(context, reset);
  const busy = command.busy || command.pending;
  useEffect(() => { lock(busy); return () => lock(false); }, [busy, lock]);
  const rows = useManagementRead<WorkspacePage<ManagedAssignment>>(managementBase + '/assignments?' + query + '&includeInactive=' + inactive + '&page=' + page, context, revision);
  const detail = useManagementRead<ManagedAssignment>(selected ? managementBase + '/assignments/' + selected + '?' + query : null, context, revision);
  const changed = (text: string) => { setMessage(text); setSelected(''); setConfirm(false); setReason(''); setRevision(n => n + 1); };
  const child = scope.mode === 'SCHOOL' ? 'Coordinator' : scope.mode === 'COORDINATOR' ? 'HR' : 'Mentor';
  return <>
    <section className="panel assignment-authority"><p><strong>{modeNames[scope.mode]}</strong> · Kỳ {scope.termCode}</p><p>Thời hạn cấp quyền: {dateLabel(scope.validFrom)} → {dateLabel(scope.validUntil)}</p>
      <p className="muted">Chỉ hiển thị phân công do bạn cấp trong phạm vi này. Thu hồi có thể làm mất hiệu lực quyền phụ thuộc và yêu cầu người nhận đăng nhập lại.</p>
      <Notice message={message} /><CommandFeedback command={command} />
    </section>
    {scope.mode !== 'HR' && canGrant && <GrantAssignment scope={scope} enterprise={enterprise} query={query} command={command} changed={changed} />}
    {scope.mode === 'SCHOOL' && !canGrant && <p className="panel">Doanh nghiệp chưa đủ điều kiện xác nhận mới. Bạn vẫn có thể tra cứu và thu hồi các phân công đã cấp.</p>}
    <section className="panel stack"><h2>Phân công {child} đã cấp</h2>
      <label className="assignment-check"><input type="checkbox" checked={inactive} disabled={busy} onChange={e => { setInactive(e.target.checked); setPage(0); setSelected(''); }} />Bao gồm phân công đã thu hồi</label>
      {rows.loading && <LoadingState />}<Notice message={rows.error} tone="error" />
      <div className="assignment-table"><table><thead><tr><th>Người nhận</th><th>Phạm vi</th><th>Hiệu lực</th><th>Thao tác</th></tr></thead><tbody>
        {rows.data?.content.map(row => <tr key={row.assignment.id}><td>{row.displayName}<small>{child} · {shortId(row.assignment.id)}</small></td>
          <td>{row.assignment.scope.groupId ? 'Nhóm ' + shortId(row.assignment.scope.groupId) : 'Toàn phạm vi trường–doanh nghiệp–kỳ'}{row.assignment.scope.caseId && <small>Case {shortId(row.assignment.scope.caseId)}</small>}</td>
          <td>{!row.assignment.active ? 'Đã thu hồi' : row.effective ? 'Đang có hiệu lực' : 'Chưa/không còn hiệu lực'}<small>{dateLabel(row.assignment.validUntil)}</small></td>
          <td><button className="secondary" disabled={busy} onClick={() => { setSelected(row.assignment.id); setConfirm(false); setReason(''); }}>Chi tiết phân công</button></td></tr>)}
      </tbody></table></div>
      {rows.data?.totalElements === 0 && <p>Chưa có phân công {child} do bạn cấp trong phạm vi này.</p>}
      <ManagementPager data={rows.data} disabled={busy || rows.loading} change={p => { setPage(p); setSelected(''); }} />
      <button className="secondary" disabled={busy} onClick={() => { setConfirm(false); setRevision(n => n + 1); }}>Tải phiên bản hiện tại</button>
      {selected && <section className="assignment-detail stack" aria-label="Chi tiết phân công"><Notice message={detail.error} tone="error" />
        {detail.data && <><h3>{detail.data.displayName}</h3><dl><dt>Mã phân công</dt><dd>{detail.data.assignment.id}</dd><dt>Căn cứ quyết định</dt><dd>{detail.data.assignment.decisionRef}</dd><dt>Thời hạn</dt><dd>{dateLabel(detail.data.assignment.validFrom)} → {dateLabel(detail.data.assignment.validUntil)}</dd><dt>Phiên bản</dt><dd>{detail.data.assignment.version}</dd></dl>
          {detail.data.canRevoke && <form className="stack" onSubmit={e => { e.preventDefault(); if (!detail.data || !confirm || !/^[A-Z0-9_]{3,64}$/.test(reason)) return;
            command.run('/t3/context-assignments/' + detail.data.assignment.id, { expectedVersion: detail.data.assignment.version, reasonCode: reason }, () => changed('Đã thu hồi phân công. Quyền phụ thuộc được kiểm tra lại; người bị ảnh hưởng cần đăng nhập lại.'), 'DELETE');
          }}><fieldset disabled={busy}><label>Mã lý do thu hồi<input value={reason} onChange={e => setReason(e.target.value.toUpperCase())} minLength={3} maxLength={64} pattern="[A-Z0-9_]{3,64}" required /></label>
            <label className="assignment-check"><input type="checkbox" checked={confirm} onChange={e => setConfirm(e.target.checked)} />Tôi xác nhận thu hồi và hiểu ảnh hưởng tới quyền phụ thuộc.</label>
            <button disabled={!confirm || !/^[A-Z0-9_]{3,64}$/.test(reason)}>Thu hồi phân công</button></fieldset></form>}
        </>}
      </section>}
    </section>
    {scope.mode === 'HR' && <MentorHandover scope={scope} query={query} command={command} changed={changed} />}
  </>;
}
