import { useEffect, useState } from 'react';
import { ApiError, type Grant, type Me } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { cohortInScope, groupInScope, usableAssignments } from '../access';
import { liveWorkspaceGateway, WorkspacePendingError, WorkspaceScopeError } from '../api';
import type { Cohort, ContextAssignment, InternshipGroup, WorkspaceAssignment, WorkspaceGateway } from '../types';
import '../styles.css';
import { TaskBoard } from './TaskBoard';
import { MentorInvitationForm } from './MentorInvitations';
import { HrCohortManagement, HrCreateCohort } from './HrCohortActions';
import { HrGroupManagement } from './HrGroupManagement';
import { HrCaseTracker } from './HrCaseTracker';

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

type CaseTarget = { caseId: string; schoolOrgId: string; enterpriseOrgId: string; termId: string; contextAssignmentId?: string };
export type HrSection = 'overview' | 'cohorts' | 'groups' | 'mentors' | 'cases';
export function InternshipWorkspace({ me, grants, gateway = liveWorkspaceGateway, initialCase, section, onKindChange, onSectionChange }: {
  me: Me; grants: Grant[]; gateway?: WorkspaceGateway; initialCase?: CaseTarget; section?: HrSection;
  onKindChange?: (kind: 'HR' | 'MENTOR' | null) => void;
  onSectionChange?: (section: HrSection) => void;
}) {
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<ReadState<ContextAssignment[]>>({ status: 'loading' });
  const [selectedId, setSelectedId] = useState('');
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' }); setSelectedId('');
    void gateway.readAssignments(controller.signal).then(value => {
      if (!controller.signal.aborted) {
        setNow(Date.now()); setState({ status: 'ready', value });
        const available = usableAssignments(value, me, grants, Date.now());
        if (!initialCase && available.length === 1) setSelectedId(available[0].id);
        if (initialCase) setSelectedId(usableAssignments(value, me, grants, Date.now()).find(a =>
          (!initialCase.contextAssignmentId || a.id === initialCase.contextAssignmentId)
          && a.scope.schoolOrgId === initialCase.schoolOrgId && a.scope.enterpriseOrgId === initialCase.enterpriseOrgId
          && a.scope.termId === initialCase.termId)?.id || '');
      }
    }).catch(error => { if (!controller.signal.aborted) setState({ status: 'error', error }); });
    return () => controller.abort();
  }, [gateway, me.userId, attempt, initialCase, grants]);
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
  useEffect(() => { onKindChange?.(selected?.kind || null); }, [selected?.kind]);
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
              {item.kind === 'HR' ? 'HR' : 'Mentor'} · DN {item.scope.enterpriseOrgId ? '…' + item.scope.enterpriseOrgId.slice(-8) : 'chưa xác định'} · Trường …{item.scope.schoolOrgId.slice(-8)} · Kỳ …{item.scope.termId.slice(-8)}
              {item.scope.groupId ? ' · Nhóm …' + item.scope.groupId.slice(-8) : ''}
            </option>)}
          </select>
          {selected && <details className="internship-scope-details"><summary>Xem phạm vi phân công</summary>
            <dl className="internship-scope" aria-label="Phạm vi phân công đã chọn">
            <div><dt>Mã doanh nghiệp</dt><dd>{selected.scope.enterpriseOrgId}</dd></div>
            <div><dt>Mã trường</dt><dd>{selected.scope.schoolOrgId}</dd></div>
            <div><dt>Mã kỳ thực tập</dt><dd>{selected.scope.termId}</dd></div>
            {selected.scope.groupId && <div><dt>Mã nhóm được giao</dt><dd>{selected.scope.groupId}</dd></div>}
          </dl></details>}
          {!selected && <p className="muted">Chưa chọn phạm vi. Dữ liệu nhóm sẽ xuất hiện sau khi bạn chọn phân công.</p>}
        </div>}
    </section>
    {selected && <AssignmentWorkspace key={selected.id + ':' + selected.version + ':' + attempt}
      assignment={selected} gateway={gateway} reset={refresh} initialCaseId={initialCase?.caseId} onSectionChange={onSectionChange}
      section={selected.kind === 'HR' ? section || 'all' : 'all'} />}
  </div>;
}

function AssignmentWorkspace({ assignment, gateway, reset, initialCaseId, section, onSectionChange }: {
  assignment: WorkspaceAssignment; gateway: WorkspaceGateway; reset: () => void; initialCaseId?: string;
  section: HrSection | 'all'; onSectionChange?: (section: HrSection) => void;
}) {
  const [state, setState] = useState<ReadState<Cohort[]>>({ status: 'loading' });
  const [cohortId, setCohortId] = useState('');
  const [reload, setReload] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });
    void gateway.readCohorts(assignment, controller.signal).then(async value => {
      if (controller.signal.aborted) return;
      if (value.some(cohort => !cohortInScope(cohort, assignment))) throw new WorkspaceScopeError();
      setState({ status: 'ready', value });
      setCohortId(current => value.some(cohort => cohort.cohortId === current) ? current : '');
      if (initialCaseId) {
        for (const cohort of value) {
          const groups = await gateway.readGroups(assignment, cohort, controller.signal);
          if (controller.signal.aborted) return;
          if (groups.some(group => groupInScope(group, cohort, assignment) && group.caseIds.includes(initialCaseId))) {setCohortId(cohort.cohortId);break;}
        }
      }
    }).catch(error => { if (!controller.signal.aborted) setState({ status: 'error', error }); });
    return () => controller.abort();
  }, [assignment, gateway, initialCaseId, reload]);
  const cohort = state.status === 'ready' ? state.value.find(item => item.cohortId === cohortId) : undefined;
  return <div className="hr-workspace">
    <section className="panel hr-workspace-context">
    <div className="section-heading"><div><p className="section-kicker">{assignment.kind === 'HR' ? 'KHÔNG GIAN HR' : 'KHÔNG GIAN MENTOR'}</p>
      <h2>{section === 'cases' ? 'Chọn đợt để theo dõi hồ sơ' : section === 'overview' ? 'Tổng quan thực tập' : section === 'cohorts' ? 'Quản lý đợt thực tập'
        : section === 'groups' ? 'Phân nhóm thực tập' : section === 'mentors' ? 'Quản lý người hướng dẫn'
        : 'Đợt và nhóm thực tập'}</h2></div>
      <span className="internship-role"><Icon name="briefcase" size={16} />{assignment.kind === 'HR' ? 'HR' : 'Mentor'}</span></div>
    {state.status === 'loading' ? <LoadingState label="Đang tải đợt thực tập…" />
      : state.status === 'error' ? <Failure error={state.error} retry={reset} />
      : state.value.length === 0 ? <EmptyState icon="calendar" title="Chưa có đợt thực tập" description="Chưa có đợt thực tập trong phạm vi phân công này." />
      : <div className="internship-context"><label htmlFor="internship-cohort">Đợt thực tập</label>
        <select id="internship-cohort" value={cohortId} onChange={event => setCohortId(event.target.value)}>
          <option value="">Chọn đợt thực tập…</option>
          {state.value.map(item => <option key={item.cohortId} value={item.cohortId}>{item.code} — {item.name}</option>)}
        </select></div>}
    </section>
    {assignment.kind === 'HR' && section === 'cohorts' && state.status === 'ready' && <section className="panel stack hr-cohort-directory">
      <div className="section-heading"><div><h3>Danh sách đợt thực tập</h3>
        <p className="muted">Chọn đợt để nhận Placement từ T5 và theo dõi hồ sơ.</p></div>
        {gateway === liveWorkspaceGateway && <button type="button" onClick={() => setCreateOpen(value => !value)}
          aria-expanded={createOpen || state.value.length === 0} aria-controls="hr-create-cohort">
          {createOpen ? 'Đóng form tạo đợt' : '+ Tạo đợt thực tập'}</button>}</div>
      {state.value.length > 0 && <div className="hr-cohort-grid">{state.value.map(item =>
        <button key={item.cohortId} type="button" className={'hr-cohort-choice' + (cohortId === item.cohortId ? ' selected' : '')}
          aria-pressed={cohortId === item.cohortId} onClick={() => setCohortId(item.cohortId)}>
          <strong>{item.name}</strong><span>{item.code}</span><small>{cohortId === item.cohortId ? 'Đang xem' : 'Xem đợt →'}</small>
        </button>)}</div>}
      {state.value.length === 0 && <p className="muted">Chưa có đợt trong phạm vi này. Tạo đợt đầu tiên để bắt đầu.</p>}
    </section>}
    {assignment.kind === 'HR' && section === 'overview' && state.status === 'ready' && <section className="hr-overview">
      <div className="hr-overview-intro"><p className="section-kicker">TỔNG QUAN HR</p>
        <h3>Theo dõi thực tập trong phạm vi được phân công</h3>
        <p className="muted">Chọn đợt thực tập để xem số nhóm, hồ sơ đã xếp nhóm và Mentor từ dữ liệu hiện hành.</p></div>
      <div className="hr-kpi-grid"><div className="hr-kpi-card"><span>Đợt thực tập</span>
        <strong>{state.value.length}</strong><small>Trong phạm vi HR</small></div></div>
      {onSectionChange && <nav className="hr-overview-actions" aria-label="Đi tới công việc thực tập">
        <p className="section-kicker">TIẾP TỤC CÔNG VIỆC</p>
        <div className="button-row">
          <button type="button" className="secondary" onClick={() => onSectionChange('cohorts')}><Icon name="calendar" size={16} />Quản lý đợt thực tập</button>
          <button type="button" className="secondary" onClick={() => onSectionChange('groups')}><Icon name="users" size={16} />Xem nhóm thực tập</button>
          <button type="button" className="secondary" onClick={() => onSectionChange('mentors')}><Icon name="briefcase" size={16} />Người hướng dẫn</button>
        </div>
      </nav>}
    </section>}
    {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && (section === 'all'
      || section === 'cohorts' && (createOpen || state.status === 'ready' && state.value.length === 0)) &&
      <div id="hr-create-cohort"><HrCreateCohort assignment={assignment} created={value => {
      setState(previous => previous.status === 'ready' ? { status: 'ready', value: [...previous.value, value] } : previous);
      setCohortId(value.cohortId); setCreateOpen(false);
    }} /></div>}
    {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && cohort && (section === 'cohorts' || section === 'groups' || section === 'all') &&
      <HrCohortManagement key={cohort.cohortId} assignment={assignment} cohort={cohort}
        section={section === 'all' ? 'all' : section}
        changed={() => setReload(value => value + 1)}
        versionChanged={version => setState(previous => previous.status === 'ready'
          ? { status: 'ready', value: previous.value.map(item => item.cohortId === cohort.cohortId ? { ...item, version } : item) }
          : previous)} />}
    {cohort && section !== 'cohorts' && <GroupWorkspace key={cohort.cohortId + ':' + cohort.version}
      assignment={assignment} cohort={cohort} gateway={gateway} reset={reset} initialCaseId={initialCaseId} section={section} />}
    {!cohort && state.status === 'ready' && state.value.length > 0 && section !== 'cohorts' &&
      <p className="hr-step-hint">Chọn một đợt thực tập ở trên để tiếp tục.</p>}
  </div>;
}

function GroupWorkspace({ assignment, cohort, gateway, reset, initialCaseId, section }: {
  assignment: WorkspaceAssignment; cohort: Cohort; gateway: WorkspaceGateway; reset: () => void;
  initialCaseId?: string; section: HrSection | 'all';
}) {
  const [state, setState] = useState<ReadState<InternshipGroup[]>>({ status: 'loading' });
  const [groupId, setGroupId] = useState('');
  const [reload, setReload] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });
    void gateway.readGroups(assignment, cohort, controller.signal).then(value => {
      if (controller.signal.aborted) return;
      if (value.some(group => !groupInScope(group, cohort, assignment))) throw new WorkspaceScopeError();
      setState({ status: 'ready', value });
      setGroupId(current => value.some(group => group.groupId === current) ? current
        : initialCaseId ? value.find(group => group.caseIds.includes(initialCaseId))?.groupId || '' : '');
    }).catch(error => { if (!controller.signal.aborted) setState({ status: 'error', error }); });
    return () => controller.abort();
  }, [assignment, cohort, gateway, initialCaseId, reload]);
  const selected = state.status === 'ready' ? state.value.find(item => item.groupId === groupId) : undefined;
  if (section === 'cases') {
    if (state.status === 'loading') return <LoadingState label="Đang tải nhóm được phân công…" />;
    if (state.status === 'error') return <Failure error={state.error} retry={reset} />;
    return <HrCaseTracker assignment={assignment} groups={state.value} />;
  }
  return <div className="internship-groups">
    {state.status === 'ready' && assignment.kind === 'HR' && (section === 'overview' || section === 'all') && <section className="panel stack hr-overview-metrics">
      <h3>Tổng quan đợt {cohort.code}</h3>
      <dl className="internship-facts">
        <div><dt>Nhóm hiện có</dt><dd>{state.value.length}</dd></div>
        <div><dt>Hồ sơ đã xếp nhóm</dt><dd>{new Set(state.value.flatMap(group => group.caseIds)).size}</dd></div>
        <div><dt>Mentor đang được phân công</dt><dd>{new Set(state.value.flatMap(group => group.mentorUserIds)).size}</dd></div>
      </dl>
      <p className="muted">Số hồ sơ đã xếp nhóm lấy từ các nhóm trong phạm vi; chưa tính hồ sơ đang chờ chia nhóm.</p>
    </section>}
    {section === 'overview' ? null : state.status === 'loading' ? <LoadingState label="Đang tải nhóm được phân công…" />
      : state.status === 'error' ? <Failure error={state.error} retry={reset} />
      : state.value.length === 0 ? <EmptyState icon="users" title="Chưa có nhóm được phân công" description="Nhóm sẽ xuất hiện sau khi người phụ trách hoàn tất phân công." />
      : selected ? <div className="internship-group-detail">
        <button className="text-button" onClick={() => setGroupId('')}><Icon name="arrow-left" size={16} />Về danh sách nhóm</button>
        <h3>Nhóm {selected.code}</h3><p className="muted">{cohort.code} — {cohort.name}</p>
        <dl className="internship-facts"><div><dt>Hồ sơ thực tập</dt><dd>{selected.caseIds.length}</dd></div>
          <div><dt>Mentor được phân công</dt><dd>{new Set(selected.mentorUserIds).size}</dd></div></dl>
        {section !== 'mentors' && <div className="panel stack"><h4>Hồ sơ trong nhóm</h4>
          {selected.caseIds.length ? <ul>{selected.caseIds.map(id => <li key={id}><code>{id}</code></li>)}</ul>
            : <p className="muted">Nhóm chưa có hồ sơ.</p>}
        </div>}
        {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && section !== 'mentors' &&
          <HrGroupManagement key={selected.groupId} assignment={assignment} group={selected}
            groups={state.value} changed={() => setReload(value => value + 1)} />}
        {gateway === liveWorkspaceGateway && (section === 'all' || section === 'groups') && <TaskBoard initialView={assignment.kind === 'MENTOR' ? 'overview' : 'board'} assignment={assignment} group={selected} />}
        {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && (section === 'mentors' || section === 'all') &&
          <MentorInvitationForm key={selected.groupId} assignment={assignment} group={selected} groups={state.value} />}
      </div>
      : <><p className="internship-group-count">{state.value.length} nhóm trong phạm vi đã chọn</p>
        <div className="internship-group-grid">{state.value.map(group => <article className="internship-group-card" key={group.groupId}>
          <span className="internship-group-icon"><Icon name="users" size={23} /></span><h3>Nhóm {group.code}</h3>
          <p>{group.caseIds.length} hồ sơ thực tập · {new Set(group.mentorUserIds).size} mentor</p>
          <button className="secondary" onClick={() => setGroupId(group.groupId)} aria-label={'Xem nhóm ' + group.code}>Xem nhóm<Icon name="arrow-right" size={16} /></button>
        </article>)}</div></>}
  </div>;
}
