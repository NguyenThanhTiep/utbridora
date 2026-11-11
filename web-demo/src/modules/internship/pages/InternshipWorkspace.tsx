import { useEffect, useState } from 'react';
import { ApiError, type Grant, type Me } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { cohortInScope, groupInScope, usableAssignments } from '../access';
import { liveWorkspaceGateway, WorkspacePendingError, WorkspaceScopeError } from '../api';
import type { Cohort, ContextAssignment, InternshipGroup, WorkspaceAssignment, WorkspaceGateway } from '../types';
import '../styles.css';
import { TaskBoard } from './TaskBoard';
import { MentorInvitationForm } from './MentorInvitations';

type ReadState<T> = { status: 'loading' } | { status: 'ready'; value: T } | { status: 'error'; error: unknown };

function Failure({ error, retry }: { error: unknown; retry: () => void }) {
  const pending = error instanceof WorkspacePendingError;
  const denied = error instanceof ApiError && [403, 404].includes(error.status);
  const message = pending ? error.message : denied
    ? 'Bạn không còn quyền trong phạm vi này. Hãy tải lại phân công hoặc liên hệ người phụ trách.'
    : error instanceof WorkspaceScopeError ? error.message
    : 'Không thể tải dữ liệu thực tập. Vui lòng thử lại.';
  return <div className="internship-feedback">
    <EmptyState icon={pending ? 'clock' : denied ? 'lock' : 'alert'}
      title={pending ? 'Chờ tích hợp dữ liệu' : denied ? 'Không có quyền truy cập' : 'Chưa tải được dữ liệu'} description={message}>
      <button className="secondary" onClick={retry}><Icon name="refresh" size={16} />Thử lại</button>
    </EmptyState>
    {error instanceof ApiError && error.requestId && <p className="muted">Mã hỗ trợ: <code>{error.requestId}</code></p>}
  </div>;
}

export function InternshipWorkspace({ me, grants, gateway = liveWorkspaceGateway }: {
  me: Me; grants: Grant[]; gateway?: WorkspaceGateway;
}) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ReadState<ContextAssignment[]>>({ status: 'loading' });
  const [selectedId, setSelectedId] = useState('');
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' }); setSelectedId('');
    void gateway.readAssignments(controller.signal).then(value => {
      if (!controller.signal.aborted) { setNow(Date.now()); setState({ status: 'ready', value }); }
    }).catch(error => { if (!controller.signal.aborted) setState({ status: 'error', error }); });
    return () => controller.abort();
  }, [gateway, me.userId, attempt]);
  // Expiry invalidates the displayed group without waiting for another click.
  useEffect(() => {
    if (state.status !== 'ready') return;
    const next = state.value.flatMap(item => [Date.parse(item.validFrom), Date.parse(item.validUntil)])
      .filter(time => Number.isFinite(time) && time > now).sort((a, b) => a - b)[0];
    if (!next) return;
    const timer = window.setTimeout(() => setNow(Date.now()), Math.min(next - Date.now() + 1, 2_147_483_647));
    return () => window.clearTimeout(timer);
  }, [state, now]);
  const assignments = state.status === 'ready' ? usableAssignments(state.value, me, grants, now) : [];
  const selected = assignments.find(item => item.id === selectedId);
  const refresh = () => { setSelectedId(''); setState({ status: 'loading' }); setAttempt(value => value + 1); };

  return <div className="internship-workspace stack">
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">HR / MENTOR</p><h2>Nhóm thực tập được phân công</h2>
        <p className="section-description">Chọn phân công để xem đúng doanh nghiệp, trường và kỳ thực tập.</p></div>
        <button className="secondary" disabled={state.status === 'loading'} onClick={refresh}><Icon name="refresh" size={16} />Tải lại phân công</button>
      </div>
      {state.status === 'loading' ? <LoadingState label="Đang xác nhận phân công…" />
        : state.status === 'error' ? <Failure error={state.error} retry={refresh} />
        : assignments.length === 0 ? <EmptyState icon="lock" title="Chưa có phân công phù hợp"
          description="Bạn cần phân công HR hoặc Mentor còn hiệu lực và quyền xem trong đúng phạm vi. Liên hệ người phụ trách để kiểm tra." />
        : <div className="internship-context">
          <label htmlFor="internship-assignment">Phân công đang làm việc</label>
          <select id="internship-assignment" value={selected?.id || ''} onChange={event => setSelectedId(event.target.value)}>
            <option value="">Chọn phân công…</option>
            {assignments.map(item => <option key={item.id} value={item.id}>
              {item.kind === 'HR' ? 'HR' : 'Mentor'} · DN {item.scope.enterpriseOrgId} · Trường {item.scope.schoolOrgId} · Kỳ {item.scope.termId}
              {item.scope.groupId ? ' · Nhóm ' + item.scope.groupId : ''}
            </option>)}
          </select>
          {selected && <dl className="internship-scope" aria-label="Phạm vi phân công đã chọn">
            <div><dt>Mã doanh nghiệp</dt><dd>{selected.scope.enterpriseOrgId}</dd></div>
            <div><dt>Mã trường</dt><dd>{selected.scope.schoolOrgId}</dd></div>
            <div><dt>Mã kỳ thực tập</dt><dd>{selected.scope.termId}</dd></div>
            {selected.scope.groupId && <div><dt>Mã nhóm được giao</dt><dd>{selected.scope.groupId}</dd></div>}
          </dl>}
          {!selected && <p className="muted">Chưa chọn phạm vi. Dữ liệu nhóm sẽ xuất hiện sau khi bạn chọn phân công.</p>}
        </div>}
    </section>
    {selected && <AssignmentWorkspace key={selected.id + ':' + selected.version + ':' + attempt}
      assignment={selected} gateway={gateway} reset={refresh} />}
  </div>;
}

function AssignmentWorkspace({ assignment, gateway, reset }: {
  assignment: WorkspaceAssignment; gateway: WorkspaceGateway; reset: () => void;
}) {
  const [state, setState] = useState<ReadState<Cohort[]>>({ status: 'loading' });
  const [cohortId, setCohortId] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    void gateway.readCohorts(assignment, controller.signal).then(value => {
      if (controller.signal.aborted) return;
      if (value.some(cohort => !cohortInScope(cohort, assignment))) throw new WorkspaceScopeError();
      setState({ status: 'ready', value });
    }).catch(error => { if (!controller.signal.aborted) setState({ status: 'error', error }); });
    return () => controller.abort();
  }, [assignment, gateway]);
  const cohort = state.status === 'ready' ? state.value.find(item => item.cohortId === cohortId) : undefined;
  return <section className="panel">
    <div className="section-heading"><div><p className="section-kicker">{assignment.kind === 'HR' ? 'KHÔNG GIAN HR' : 'KHÔNG GIAN MENTOR'}</p>
      <h2>Đợt và nhóm thực tập</h2></div><span className="internship-role"><Icon name="briefcase" size={16} />{assignment.kind === 'HR' ? 'HR' : 'Mentor'}</span></div>
    {state.status === 'loading' ? <LoadingState label="Đang tải đợt thực tập…" />
      : state.status === 'error' ? <Failure error={state.error} retry={reset} />
      : state.value.length === 0 ? <EmptyState icon="calendar" title="Chưa có đợt thực tập" description="Chưa có đợt thực tập trong phạm vi phân công này." />
      : <div className="internship-context"><label htmlFor="internship-cohort">Đợt thực tập</label>
        <select id="internship-cohort" value={cohortId} onChange={event => setCohortId(event.target.value)}>
          <option value="">Chọn đợt thực tập…</option>
          {state.value.map(item => <option key={item.cohortId} value={item.cohortId}>{item.code} — {item.name}</option>)}
        </select></div>}
    {cohort && <GroupWorkspace key={cohort.cohortId + ':' + cohort.version} assignment={assignment} cohort={cohort} gateway={gateway} reset={reset} />}
  </section>;
}

function GroupWorkspace({ assignment, cohort, gateway, reset }: {
  assignment: WorkspaceAssignment; cohort: Cohort; gateway: WorkspaceGateway; reset: () => void;
}) {
  const [state, setState] = useState<ReadState<InternshipGroup[]>>({ status: 'loading' });
  const [groupId, setGroupId] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    void gateway.readGroups(assignment, cohort, controller.signal).then(value => {
      if (controller.signal.aborted) return;
      if (value.some(group => !groupInScope(group, cohort, assignment))) throw new WorkspaceScopeError();
      setState({ status: 'ready', value });
    }).catch(error => { if (!controller.signal.aborted) setState({ status: 'error', error }); });
    return () => controller.abort();
  }, [assignment, cohort, gateway]);
  const selected = state.status === 'ready' ? state.value.find(item => item.groupId === groupId) : undefined;
  return <div className="internship-groups">
    {state.status === 'loading' ? <LoadingState label="Đang tải nhóm được phân công…" />
      : state.status === 'error' ? <Failure error={state.error} retry={reset} />
      : state.value.length === 0 ? <EmptyState icon="users" title="Chưa có nhóm được phân công" description="Nhóm sẽ xuất hiện sau khi người phụ trách hoàn tất phân công." />
      : selected ? <div className="internship-group-detail">
        <button className="text-button" onClick={() => setGroupId('')}><Icon name="arrow-left" size={16} />Về danh sách nhóm</button>
        <h3>Nhóm {selected.code}</h3><p className="muted">{cohort.code} — {cohort.name}</p>
        <dl className="internship-facts"><div><dt>Hồ sơ thực tập</dt><dd>{selected.caseIds.length}</dd></div>
          <div><dt>Mentor được phân công</dt><dd>{new Set(selected.mentorUserIds).size}</dd></div></dl>
        {gateway === liveWorkspaceGateway && <TaskBoard assignment={assignment} group={selected} />}
        {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && <MentorInvitationForm assignment={assignment} group={selected} />}
      </div>
      : <><p className="internship-group-count">{state.value.length} nhóm trong phạm vi đã chọn</p>
        <div className="internship-group-grid">{state.value.map(group => <article className="internship-group-card" key={group.groupId}>
          <span className="internship-group-icon"><Icon name="users" size={23} /></span><h3>Nhóm {group.code}</h3>
          <p>{group.caseIds.length} hồ sơ thực tập · {new Set(group.mentorUserIds).size} mentor</p>
          <button className="secondary" onClick={() => setGroupId(group.groupId)} aria-label={'Xem nhóm ' + group.code}>Xem nhóm<Icon name="arrow-right" size={16} /></button>
        </article>)}</div></>}
  </div>;
}
