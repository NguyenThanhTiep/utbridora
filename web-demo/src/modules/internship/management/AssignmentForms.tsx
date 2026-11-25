import { useEffect, useState } from 'react';
import type { ContextAssignment, WorkspacePage } from '../types';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { caseStateLabel } from '../displayLabels';
import { ManagementPager, localDateTime, managementBase, shortId, useManagementRead, type CaseOption, type GroupOption, type Member, type ManagementScope } from './read';

export type ManagementCommand = ReturnType<typeof useWorkflowCommand>;
type FormProps = { scope: ManagementScope; enterprise: string; query: string; command: ManagementCommand; changed: (message: string) => void };
export function GrantAssignment({ scope, enterprise, query, command, changed }: FormProps) {
  const [search, setSearch] = useState(''), [searchValue, setSearchValue] = useState(''), [page, setPage] = useState(0);
  const [target, setTarget] = useState<Member | null>(null), [decision, setDecision] = useState('');
  const [from, setFrom] = useState(() => localDateTime(new Date(Math.max(Math.floor(Date.now() / 60_000) * 60_000, Math.ceil(Date.parse(scope.validFrom) / 60_000) * 60_000)).toISOString()));
  const [until, setUntil] = useState(() => localDateTime(scope.validUntil));
  const [limited, setLimited] = useState(false), [groupId, setGroupId] = useState(''), [caseId, setCaseId] = useState('');
  const [groupPage, setGroupPage] = useState(0), [casePage, setCasePage] = useState(0);
  const context = scope.contextId || undefined;
  const members = useManagementRead<WorkspacePage<Member>>(managementBase + '/candidates?' + query + '&page=' + page + '&search=' + encodeURIComponent(searchValue), context);
  const groups = useManagementRead<WorkspacePage<GroupOption>>(limited ? managementBase + '/groups?' + query + '&page=' + groupPage : null, context);
  const cases = useManagementRead<WorkspacePage<CaseOption>>(limited && groupId ? managementBase + '/cases?' + query + '&groupId=' + groupId + '&page=' + casePage : null, context);
  useEffect(() => { const t = window.setTimeout(() => { setSearchValue(search.trim()); setPage(0); }, 300); return () => window.clearTimeout(t); }, [search]);
  const coordinator = scope.mode === 'SCHOOL', kind = coordinator ? 'Coordinator' : 'HR';
  const busy = command.busy || command.pending;
  const start = Date.parse(from), end = Date.parse(until);
  const periodValid = Number.isFinite(start) && Number.isFinite(end) && start < end && start >= Date.parse(scope.validFrom) && end <= Date.parse(scope.validUntil);
  const valid = !!target && !!decision.trim() && periodValid && !members.loading && !members.error
    && !!members.data?.content.some(m => m.userId === target.userId)
    && (!limited || !!groups.data?.content.some(g => g.groupId === groupId) && !groups.loading && !groups.error
      && (!caseId || !!cases.data?.content.some(c => c.caseId === caseId) && !cases.loading && !cases.error));
  return <section className="panel stack"><h2>{coordinator ? 'Xác nhận Coordinator' : 'Cấp phân công HR'}</h2>
    <p>{coordinator ? 'Người nhận phải là thành viên doanh nghiệp đã được xác minh và được trường chấp thuận.' : 'Người nhận và thời hạn phải thuộc phạm vi Coordinator đang được xác nhận.'}</p>
    <form className="stack" onSubmit={e => {
      e.preventDefault(); if (!valid || !target) return;
      const payload = coordinator ? { userId: target.userId, enterpriseOrgId: enterprise, termId: scope.termId, validFrom: new Date(start).toISOString(), validUntil: new Date(end).toISOString(), decisionRef: decision.trim() }
        : { userId: target.userId, scope: { schoolOrgId: scope.schoolOrgId, enterpriseOrgId: enterprise, termId: scope.termId, groupId: limited ? groupId : null, caseId: limited && caseId ? caseId : null }, validFrom: new Date(start).toISOString(), validUntil: new Date(end).toISOString(), decisionRef: decision.trim() };
      command.run<ContextAssignment>(coordinator ? '/t3/coordinators/confirm' : '/t3/hr-assignments', payload, () => {
        setTarget(null); setDecision(''); changed('Đã ' + (coordinator ? 'xác nhận Coordinator' : 'cấp phân công HR') + '. Người nhận cần đăng nhập lại để cập nhật quyền.');
      });
    }}>
      <fieldset disabled={busy}><div className="assignment-form-grid">
        <label>Tìm người nhận theo tên<input value={search} maxLength={120} onChange={e => { setSearch(e.target.value); setTarget(null); }} /></label>
        <label>Người nhận {kind}<select value={target?.userId || ''} disabled={members.loading || !!members.error} onChange={e => setTarget(members.data?.content.find(m => m.userId === e.target.value) || null)}>
          <option value="">Chọn thành viên doanh nghiệp…</option>{members.data?.content.map(m => <option key={m.userId} value={m.userId}>{m.displayName} · {shortId(m.userId)}</option>)}
        </select></label>
      </div><ManagementPager data={members.data} disabled={busy || members.loading} change={p => { setTarget(null); setPage(p); }} />
      {members.loading && <p role="status">Đang tải thành viên…</p>}{members.error && <p role="alert">{members.error}</p>}
      {members.data?.totalElements === 0 && <p>Chưa có thành viên phù hợp; cần tài khoản ACTIVE và membership doanh nghiệp hợp lệ.</p>}
      {!coordinator && <div className="stack"><label className="assignment-check"><input type="checkbox" checked={limited} onChange={e => { setLimited(e.target.checked); setGroupId(''); setCaseId(''); setGroupPage(0); setCasePage(0); }} />Giới hạn HR theo nhóm/Case cho nghiệp vụ điểm</label>
        <p className="muted">HR giới hạn nhóm/Case không có quyền quản lý toàn bộ đợt hoặc mời Mentor.</p>
        {limited && <><label>Nhóm được phân công<select value={groupId} onChange={e => { setGroupId(e.target.value); setCaseId(''); setCasePage(0); }}><option value="">Chọn nhóm…</option>{groups.data?.content.map(g => <option key={g.groupId} value={g.groupId}>{g.cohortCode} · {g.code}</option>)}</select></label>
          {groups.error && <p role="alert">{groups.error}</p>}<ManagementPager data={groups.data} disabled={busy || groups.loading} change={p => { setGroupId(''); setCaseId(''); setGroupPage(p); }} />
          {groupId && <><label>Giới hạn thêm một hồ sơ<select value={caseId} onChange={e => setCaseId(e.target.value)}><option value="">Tất cả hồ sơ trong nhóm</option>{cases.data?.content.map(c => <option key={c.caseId} value={c.caseId}>Hồ sơ {shortId(c.caseId)} · {caseStateLabel(c.state)}</option>)}</select></label>
            {cases.error && <p role="alert">{cases.error}</p>}<ManagementPager data={cases.data} disabled={busy || cases.loading} change={p => { setCaseId(''); setCasePage(p); }} /></>}
        </>}
      </div>}
      <div className="assignment-form-grid"><label>Hiệu lực từ<input type="datetime-local" required value={from} min={localDateTime(scope.validFrom)} max={localDateTime(scope.validUntil)} onChange={e => setFrom(e.target.value)} /></label>
        <label>Hiệu lực đến<input type="datetime-local" required value={until} min={from} max={localDateTime(scope.validUntil)} onChange={e => setUntil(e.target.value)} /></label></div>
      {!periodValid && <p role="alert">Thời hạn phải nằm trong phân công cấp quyền hiện hành và ngày kết thúc phải sau ngày bắt đầu.</p>}
      <label>Căn cứ quyết định<input required maxLength={120} value={decision} onChange={e => setDecision(e.target.value)} /></label>
      <button disabled={!valid}>{coordinator ? 'Xác nhận Coordinator' : 'Cấp phân công HR'}</button></fieldset>
    </form>
  </section>;
}

export function MentorHandover({ scope, query, command, changed }: Omit<FormProps, 'enterprise'>) {
  const [casePage, setCasePage] = useState(0), [mentorPage, setMentorPage] = useState(0);
  const [selected, setSelected] = useState<CaseOption | null>(null), [mentor, setMentor] = useState(''), [reason, setReason] = useState('');
  const [revision, setRevision] = useState(0);
  const context = scope.contextId || undefined;
  const cases = useManagementRead<WorkspacePage<CaseOption>>(managementBase + '/cases?' + query + '&page=' + casePage, context, revision);
  const mentors = useManagementRead<WorkspacePage<Member>>(selected ? managementBase + '/cases/' + selected.caseId + '/mentors?' + query + '&page=' + mentorPage : null, context, revision);
  const busy = command.busy || command.pending;
  const validMentor = !!mentor && !mentors.loading && !mentors.error && !!mentors.data?.content.some(m => m.userId === mentor);
  return <section className="panel stack"><h2>Bàn giao Mentor</h2><p>Mentor mới phải đã chấp thuận lời mời và còn phân công đúng nhóm/Case. Bàn giao cho phép tiếp nhận quyền hướng dẫn các nhiệm vụ thuộc nhóm cũ; không cấp quyền sửa sản phẩm cũ.</p>
    <form onSubmit={e => { e.preventDefault(); if (!selected || !mentor || !/^[A-Z0-9_]{3,64}$/.test(reason)) return;
      command.run('/t3/cases/' + selected.caseId + '/mentor-handover', { caseId: selected.caseId, newMentorUserId: mentor, expectedCaseVersion: selected.version, reasonCode: reason }, () => {
        setSelected(null); setMentor(''); setReason(''); setRevision(n => n + 1); changed('Đã ghi nhận bàn giao Mentor. Mentor cần đăng nhập lại để cập nhật quyền.');
      });
    }} className="stack"><fieldset disabled={busy}>
      <label>Hồ sơ cần bàn giao<select value={selected?.caseId || ''} onChange={e => { setSelected(cases.data?.content.find(c => c.caseId === e.target.value) || null); setMentor(''); setMentorPage(0); }}><option value="">Chọn hồ sơ đang thực tập…</option>{cases.data?.content.map(c => <option key={c.caseId} value={c.caseId}>Hồ sơ {shortId(c.caseId)} · {c.groupCode} · {caseStateLabel(c.state)}</option>)}</select></label>
      {cases.error && <p role="alert">{cases.error}</p>}<ManagementPager data={cases.data} disabled={busy || cases.loading} change={p => { setSelected(null); setMentor(''); setCasePage(p); }} />
      {selected && <><label>Mentor tiếp nhận<select value={mentor} onChange={e => setMentor(e.target.value)}><option value="">Chọn Mentor đủ điều kiện…</option>{mentors.data?.content.map(m => <option key={m.userId} value={m.userId}>{m.displayName} · {shortId(m.userId)}</option>)}</select></label>
        {mentors.error && <p role="alert">{mentors.error}</p>}{mentors.data?.totalElements === 0 && <p>Chưa có Mentor đủ điều kiện. Cần hoàn tất luồng mời và tự chấp thuận trước.</p>}
        <ManagementPager data={mentors.data} disabled={busy || mentors.loading} change={p => { setMentor(''); setMentorPage(p); }} />
        <p className="muted">Phiên bản hồ sơ: {selected.version}. Nếu có thay đổi, tải lại danh sách rồi chọn lại.</p></>}
      <label>Mã lý do bàn giao<input value={reason} minLength={3} maxLength={64} pattern="[A-Z0-9_]{3,64}" onChange={e => setReason(e.target.value.toUpperCase())} required /></label>
      <button disabled={!selected || !validMentor || !/^[A-Z0-9_]{3,64}$/.test(reason)}>Xác nhận bàn giao Mentor</button>
      <button className="secondary" type="button" onClick={() => { setSelected(null); setMentor(''); setRevision(n => n + 1); }}>Tải lại hồ sơ và Mentor</button>
    </fieldset></form>
  </section>;
}
export { CommandFeedback };
