import { useEffect, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { api, ApiError, type Grant, type Me } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { cohortInScope, groupInScope, usableAssignments } from '../access';
import { liveWorkspaceGateway, WorkspacePendingError, WorkspaceScopeError } from '../api';
import { formatEntityShort, formatGroupCode, formatUserLabel } from '../displayLabels';
import type { Cohort, ContextAssignment, InternshipGroup, WorkspaceAssignment, WorkspaceGateway } from '../types';
import '../styles.css';
import { TaskBoard } from './TaskBoard';
import { MentorInvitationForm } from './MentorInvitations';
import { HrCohortManagement, HrCreateCohort } from './HrCohortActions';
import { HrGroupManagement } from './HrGroupManagement';
import { HrCaseTracker } from './HrCaseTracker';
import { CohortManagement } from './CohortManagement';
import { readAll } from '../../cooperation/portal/data';
import '../../cooperation/portal/context-bar.css';

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
export function InternshipWorkspace({ me, grants, gateway = liveWorkspaceGateway, initialCase, section, onKindChange, onSectionChange, topbarTarget, accountMenu }: {
  me: Me; grants: Grant[]; gateway?: WorkspaceGateway; initialCase?: CaseTarget; section?: HrSection;
  onKindChange?: (kind: 'HR' | 'MENTOR' | null) => void;
  onSectionChange?: (section: HrSection) => void;
  topbarTarget?: HTMLElement | null;
  accountMenu?: ReactNode;
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
  const organizationIds = [...new Set(assignments.flatMap(item =>
    [item.scope.enterpriseOrgId, item.scope.schoolOrgId].filter((id): id is string => !!id)))].sort();
  const organizationKey = `${me.userId}:${organizationIds.join(',')}`;
  const [organizationState, setOrganizationState] = useState<{ key: string; names: Record<string, string> }>({ key: '', names: {} });
  useEffect(() => {
    if (gateway !== liveWorkspaceGateway || organizationIds.length === 0) return;
    const controller = new AbortController();
    void api<{ id: string; name: string }[]>('/cooperation/organizations', { signal: controller.signal })
      .then(rows => {
        if (!controller.signal.aborted) setOrganizationState({ key: organizationKey, names: Object.fromEntries(
          rows.filter(row => organizationIds.includes(row.id) && row.name?.trim()).map(row => [row.id, row.name])) });
      }).catch(() => {
        if (!controller.signal.aborted) setOrganizationState({ key: organizationKey, names: {} });
      });
    return () => controller.abort();
  }, [gateway, organizationKey]);
  const organizationNames = organizationState.key === organizationKey ? organizationState.names : {};
  const scopeKey = selected ? `${selected.scope.enterpriseOrgId}:${selected.scope.schoolOrgId}:${selected.scope.termId}` : '';
  const [scopeState, setScopeState] = useState<{ key: string; school: string; term: string }>({ key: '', school: '', term: '' });
  useEffect(() => {
    if (gateway !== liveWorkspaceGateway || !selected?.scope.enterpriseOrgId) return;
    const controller = new AbortController();
    const enterpriseId = selected.scope.enterpriseOrgId;
    const schoolId = selected.scope.schoolOrgId;
    const termId = selected.scope.termId;
    void Promise.allSettled([
      readAll<{ schoolOrgId: string; name: string }>(enterpriseId, '/enterprise/demand-references/schools', controller.signal),
      readAll<{ termId: string; termCode: string }>(enterpriseId,
        '/enterprise/demand-references/terms?' + new URLSearchParams({ schoolOrgId: schoolId }), controller.signal)
    ]).then(([schoolsResult, termsResult]) => {
      if (controller.signal.aborted) return;
      const school = schoolsResult.status === 'fulfilled'
        ? schoolsResult.value.find(item => item.schoolOrgId === schoolId)?.name || '' : '';
      const term = termsResult.status === 'fulfilled'
        ? termsResult.value.find(item => item.termId === termId)?.termCode || '' : '';
      setScopeState({ key: scopeKey, school, term });
    });
    return () => controller.abort();
  }, [gateway, scopeKey]);
  const scopeLabels = scopeState.key === scopeKey ? scopeState : { school: '', term: '' };
  useEffect(() => { onKindChange?.(selected?.kind || null); }, [selected?.kind]);
  const refresh = () => { setSelectedId(''); setState({ status: 'loading' }); setAttempt(value => value + 1); };

  const contextBar = <section className="portal-context-bar t3-context-bar" aria-label="Phạm vi thực tập">
      <div className="portal-organization-field"><span className="portal-organization-icon"><Icon name="briefcase" size={22} /></span>
        <div className="portal-context-select"><label htmlFor="internship-assignment">Phân công đang làm việc</label>
          <select id="internship-assignment" value={selected?.id || ''} disabled={state.status !== 'ready' || !assignments.length}
            onChange={event => setSelectedId(event.target.value)}>
            <option value="">{state.status === 'loading' ? 'Đang tải phân công…' : 'Chọn phân công…'}</option>
            {assignments.map(item => <option key={item.id} value={item.id}>
              {item.kind === 'HR' ? 'HR' : 'Mentor'} · {item.scope.enterpriseOrgId
                ? organizationNames[item.scope.enterpriseOrgId] || 'DN ' + formatEntityShort(item.scope.enterpriseOrgId)
                : 'Chưa xác định DN'} · {(item.id === selected?.id && scopeLabels.school ? scopeLabels.school : organizationNames[item.scope.schoolOrgId]) || 'Trường ' + formatEntityShort(item.scope.schoolOrgId)} · {item.id === selected?.id && scopeLabels.term ? scopeLabels.term : 'Kỳ ' + formatEntityShort(item.scope.termId)}
              {item.scope.groupId ? ' · Nhóm ' + formatEntityShort(item.scope.groupId) : ''}
            </option>)}
          </select></div></div>
      <div className="notification-scope t3-context-scope"><div className="notification-scope-fields">
        <div><span className="t3-context-label">Trường theo dõi</span><span className="t3-context-value" title={selected?.scope.schoolOrgId}>
          {selected ? scopeLabels.school || organizationNames[selected.scope.schoolOrgId] || 'Trường …' + selected.scope.schoolOrgId.slice(-8) : 'Chọn phân công'}</span></div>
        <div><span className="t3-context-label">Kỳ theo dõi</span><span className="t3-context-value" title={selected?.scope.termId}>
          {selected ? scopeLabels.term || 'Kỳ …' + selected.scope.termId.slice(-8) : 'Chọn phân công'}</span></div>
      </div></div>
      <div className="portal-context-actions t3-context-actions">
        <button type="button" className="secondary" disabled={state.status === 'loading'} onClick={refresh}>
          <Icon name="refresh" size={16} />Tải lại phân công</button>
        {accountMenu || <span className="portal-role"><span>{selected?.kind === 'HR' ? 'HR' : selected?.kind === 'MENTOR' ? 'MT' : 'T3'}</span>
          {selected?.kind === 'HR' ? 'Nhân sự' : selected?.kind === 'MENTOR' ? 'Người hướng dẫn' : 'Thực tập'}</span>}
      </div>
      {selected && <details className="internship-scope-details t3-context-details"><summary>Xem phạm vi phân công</summary>
        <dl className="internship-scope" aria-label="Phạm vi phân công đã chọn">
          <div><dt>Mã doanh nghiệp</dt><dd>{selected.scope.enterpriseOrgId}</dd></div>
          <div><dt>Mã trường</dt><dd>{selected.scope.schoolOrgId}</dd></div>
          <div><dt>Mã kỳ thực tập</dt><dd>{selected.scope.termId}</dd></div>
          {selected.scope.groupId && <div><dt>Mã nhóm được giao</dt><dd>{selected.scope.groupId}</dd></div>}
        </dl></details>}
      {state.status === 'loading' ? <div className="t3-context-feedback"><LoadingState label="Đang xác nhận phân công…" /></div>
        : state.status === 'error' ? <div className="t3-context-feedback"><Failure error={state.error} retry={refresh} /></div>
        : assignments.length === 0 ? <div className="t3-context-feedback"><EmptyState icon="lock" title="Chưa có phân công phù hợp"
          description="Bạn cần phân công HR hoặc Mentor còn hiệu lực và quyền xem trong đúng phạm vi. Liên hệ người phụ trách để kiểm tra." /></div>
        : !selected ? <p className="t3-context-feedback muted">Chọn phân công để xem dữ liệu thực tập trong đúng phạm vi.</p> : null}
    </section>;
  return <div className="internship-workspace stack">
    {topbarTarget === undefined ? contextBar : topbarTarget ? createPortal(contextBar, topbarTarget) : null}
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
  const [createOpen, setCreateOpen] = useState<boolean | null>(null);
  const [cohortQuery, setCohortQuery] = useState('');
  const [intake, setIntake] = useState<'file' | 'manual'>('file');
  useEffect(() => {
    const controller = new AbortController();
    setState({ status: 'loading' });
    void gateway.readCohorts(assignment, controller.signal).then(async value => {
      if (controller.signal.aborted) return;
      if (value.some(cohort => !cohortInScope(cohort, assignment))) throw new WorkspaceScopeError();
      setState({ status: 'ready', value });
      setCohortId(current => value.some(cohort => cohort.cohortId === current) ? current
        : !initialCaseId && section !== 'cohorts' && value.length > 0 ? value[0].cohortId : '');
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
  const visibleCohorts = state.status === 'ready' ? state.value.filter(item =>
    (item.name + ' ' + item.code).toLocaleLowerCase('vi').includes(cohortQuery.trim().toLocaleLowerCase('vi'))) : [];
  const showCreate = createOpen ?? (state.status === 'ready' && state.value.length === 0);
  function selectCohort(id: string) { setCohortId(id); setIntake('file'); }

  const cohortActions = cohort && gateway === liveWorkspaceGateway && assignment.kind === 'HR' &&
    (section === 'all' || section === 'cohorts' || section === 'groups') && <HrCohortManagement
      key={cohort.cohortId} assignment={assignment} cohort={cohort} section={section}
      changed={() => setReload(value => value + 1)}
      versionChanged={version => setState(previous => previous.status === 'ready'
        ? { ...previous, value: previous.value.map(item => item.cohortId === cohort.cohortId ? { ...item, version } : item) }
        : previous)} />;
  const scopeFields = <>
      <strong className="internship-role">{assignment.kind === 'HR' ? 'KHÔNG GIAN HR' : 'KHÔNG GIAN MENTOR'}</strong>
    {state.status === 'loading' ? <LoadingState label="Đang tải đợt thực tập…" />
      : state.status === 'error' ? <Failure error={state.error} retry={reset} />
      : state.value.length === 0 ? <EmptyState icon="calendar" title="Chưa có đợt thực tập" description="Chưa có đợt thực tập trong phạm vi phân công này." />
      : <div className="internship-context"><label htmlFor="internship-cohort">Đợt thực tập</label>
        <select id="internship-cohort" value={cohortId} onChange={event => selectCohort(event.target.value)}>
          <option value="">Chọn đợt thực tập…</option>
          {state.value.map(item => <option key={item.cohortId} value={item.cohortId}>{item.code} — {item.name}</option>)}
        </select></div>}
      {section !== 'cohorts' && state.status === 'ready' && state.value.length > 0 && !cohort && <span className="t3-scope-summary">
        {`${state.value.length} đợt khả dụng`}
      </span>}
      {section === 'cohorts' && state.status === 'ready' && <>
        <label>Tìm đợt thực tập<input type="search" placeholder="Tên hoặc mã đợt" value={cohortQuery} onChange={e => setCohortQuery(e.target.value)} /></label>
        {gateway === liveWorkspaceGateway && <button type="button" aria-expanded={showCreate} aria-controls="hr-create-cohort"
          onClick={() => setCreateOpen(true)}>+ Tạo đợt thực tập</button>}
      </>}
  </>;
  const groupToolbar = cohort && (section === 'groups' || section === 'mentors');
  return <div className="hr-workspace">
    {!groupToolbar && <section className="hr-workspace-context t3-scope-toolbar" aria-label="Phạm vi đợt thực tập">{scopeFields}</section>}
    {assignment.kind === 'HR' && section === 'cohorts' && state.status === 'ready' && <section className="panel stack hr-cohort-directory">
      <div className="t3-card-heading"><h3>Danh sách đợt thực tập</h3><span className="muted">{state.value.length} đợt thực tập</span></div>
      {state.value.length > 0 && <div className="t3-table-scroll"><table><thead><tr>
        <th>Tên đợt</th><th>Mã đợt</th><th>Trạng thái</th><th>Thao tác</th>
      </tr></thead><tbody>{visibleCohorts.map(item => <tr key={item.cohortId} className={item.cohortId === cohortId ? 'is-active-row' : undefined}>
        <th scope="row">{item.name}</th><td><span className="t3-code-pill">{item.code}</span></td>
        <td><span className="t3-status-pill" data-tone={item.cohortId === cohortId ? 'teal' : 'neutral'}>{item.cohortId === cohortId ? 'Đang xem' : 'Chưa chọn'}</span></td>
        <td><button className="secondary" aria-pressed={item.cohortId === cohortId} onClick={() => selectCohort(item.cohortId)}>Xem chi tiết →</button></td>
      </tr>)}</tbody></table>{visibleCohorts.length === 0 && <p>Không tìm thấy đợt phù hợp.</p>}</div>}
      {state.value.length === 0 && <EmptyState icon="calendar" title="Chưa có đợt trong phạm vi này." description="Tạo đợt đầu tiên để bắt đầu." />}
    </section>}
    {assignment.kind === 'HR' && section === 'overview' && state.status === 'ready' && <section className="hr-overview">
      {cohort ? <GroupWorkspace key={cohort.cohortId + ':' + cohort.version} assignment={assignment} cohort={cohort}
        gateway={gateway} reset={reset} section="overview" cohortCount={state.value.length} navigate={onSectionChange} />
        : <HrOverviewMetrics cohortCount={state.value.length} navigate={onSectionChange} />}
    </section>}
    {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && (section === 'all'
      || section === 'cohorts' && showCreate) &&
      <div id="hr-create-cohort"><HrCreateCohort close={section === 'cohorts' ? () => setCreateOpen(false) : undefined} assignment={assignment} created={value => {
      setState(previous => previous.status === 'ready' ? { status: 'ready', value: [...previous.value, value] } : previous);
      setCohortId(value.cohortId);
      setIntake(createOpen === null && state.status === 'ready' && !state.value.length ? 'manual' : 'file');
      setCreateOpen(false);
    }} /></div>}
    {section === 'all' && cohortActions}
    {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && cohort && section === 'cohorts' &&
      <section className="panel stack t3-intake-methods">
        <h3>Tiếp nhận Placement từ danh sách</h3>
        <div className="button-row" aria-label="Cách tiếp nhận">
          <button className="secondary" aria-pressed={intake === 'file'} onClick={() => setIntake('file')}>Tải tệp CSV/XLSX</button>
          <button className="secondary" aria-pressed={intake === 'manual'} onClick={() => setIntake('manual')}>Dán danh sách UUID</button>
        </div>
        <div hidden={intake !== 'file'}><CohortManagement key={cohort.cohortId + ':' + cohort.version} assignment={assignment} cohort={cohort}
          changed={() => setReload(value => value + 1)} denied={reset} fileImportOnly /></div>
        <div hidden={intake !== 'manual'}>{cohortActions}</div>
      </section>}
    {cohort && section !== 'cohorts' && section !== 'overview' && <GroupWorkspace key={cohort.cohortId + ':' + cohort.version}
      assignment={assignment} cohort={cohort} gateway={gateway} reset={reset} initialCaseId={initialCaseId} section={section} scopeFields={groupToolbar ? scopeFields : null} groupActions={section === 'groups' ? cohortActions : null} />}
    {!cohort && state.status === 'ready' && state.value.length > 0 && section !== 'cohorts' &&
      <p className="hr-step-hint">Chọn một đợt thực tập ở trên để tiếp tục.</p>}
  </div>;
}

function HrOverviewMetrics({ cohortCount, groups, navigate }: {
  cohortCount: number; groups?: InternshipGroup[]; navigate?: (section: HrSection) => void;
}) {
  const metrics = [
    { label: 'Đợt thực tập', count: cohortCount, sub: 'Trong phạm vi phân công', target: 'cohorts', link: 'Quản lý đợt' },
    { label: 'Nhóm hiện có', count: groups?.length, sub: 'Đang theo dõi trong đợt', target: 'groups', link: 'Xem theo nhóm' },
    { label: 'Hồ sơ đã xếp nhóm', count: groups && new Set(groups.flatMap(g => g.caseIds)).size, sub: 'Đã phân công vào nhóm', target: 'groups', link: 'Chi tiết nhóm' },
    { label: 'Mentor phân công', count: groups && new Set(groups.flatMap(g => g.mentorUserIds)).size, sub: 'Người hướng dẫn phụ trách', target: 'mentors', link: 'Người hướng dẫn' },
  ] as const;
  const unassigned = groups?.filter(g => !g.mentorUserIds.length).length;
  const priorities: [string, string, HrSection][] = [
    [cohortCount ? 'Chọn đợt để quản lý tiếp nhận' : 'Tạo đợt thực tập đầu tiên', cohortCount ? 'Theo dõi danh sách đợt và hạn tiếp nhận' : 'Khởi tạo đợt thực tập theo trường và kỳ', 'cohorts'],
    ...(cohortCount ? [['Đối chiếu Placement từ T5', 'Nhập danh sách CSV/XLSX hoặc đối chiếu mã từ T5', 'cohorts'] as [string, string, HrSection]] : []),
    ...(groups ? [['Xem hồ sơ và phương án chia nhóm', `${groups.length} nhóm trong đợt đang chọn`, 'groups'] as [string, string, HrSection]] : []),
    ...(unassigned ? [[unassigned + ' nhóm cần người hướng dẫn', 'Mời Mentor phụ trách cho các nhóm chưa phân công', 'mentors'] as [string, string, HrSection]] : []),
  ];
  return <><dl className="hr-kpi-grid">{metrics.map(metric => <div key={metric.label} className="hr-kpi-card">
    <dt><span>{metric.label}</span></dt>
    <dd className="t3-kpi-value">{metric.count ?? '—'}</dd>
    <div className="t3-kpi-footer"><small>{metric.sub}</small>{navigate && <button className="text-button" onClick={() => navigate(metric.target)}>{metric.link} →</button>}</div>
  </div>)}</dl>
  {navigate && <div className="t3-overview-columns"><section className="panel stack">
    <div className="t3-card-heading"><h3>Ưu tiên hôm nay</h3><span className="t3-status-pill" data-tone="amber">{priorities.length} mục</span></div>
    <ol className="t3-priorities">{priorities.map(([title, sub, target], i) => <li key={title}><small>{String(i + 1).padStart(2, '0')}</small>
      <div><strong>{title}</strong><span>{sub}</span></div>
      <button className="text-button" aria-label={'Xem: ' + title} onClick={() => navigate(target)}>Xem<Icon name="chevron-right" size={14} /></button></li>)}</ol>
  </section><nav className="panel stack t3-quick-actions" aria-label="Thao tác nhanh"><h3>Thao tác nhanh</h3>
    <button className="t3-quick-action is-primary" onClick={() => navigate('cohorts')}><span>+ Quản lý đợt thực tập</span><Icon name="chevron-right" size={16} /></button>
    <button className="secondary t3-quick-action" onClick={() => navigate('groups')}><span>Phân nhóm thực tập</span><Icon name="chevron-right" size={16} /></button>
    <button className="secondary t3-quick-action" onClick={() => navigate('mentors')}><span>Mời người hướng dẫn</span><Icon name="chevron-right" size={16} /></button>
    <button className="secondary t3-quick-action" onClick={() => navigate('cases')}><span>Theo dõi hồ sơ Case</span><Icon name="chevron-right" size={16} /></button>
  </nav></div>}</>;
}

function GroupWorkspace({ assignment, cohort, gateway, reset, initialCaseId, section, cohortCount, navigate, groupActions, scopeFields }: {
  assignment: WorkspaceAssignment; cohort: Cohort; gateway: WorkspaceGateway; reset: () => void;
  initialCaseId?: string; section: HrSection | 'all'; cohortCount?: number; navigate?: (section: HrSection) => void; groupActions?: ReactNode; scopeFields?: ReactNode;
}) {
  const [query, setQuery] = useState('');
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
  if (section === 'overview' && cohortCount !== undefined) {
    return <><HrOverviewMetrics navigate={navigate} cohortCount={cohortCount} groups={state.status === 'ready' ? state.value : undefined} />
      {state.status === 'loading' && <LoadingState label="Đang tải nhóm được phân công…" />}
      {state.status === 'error' && <Failure error={state.error} retry={reset} />}</>;
  }
  if (section === 'cases') {
    if (state.status === 'loading') return <LoadingState label="Đang tải nhóm được phân công…" />;
    if (state.status === 'error') return <Failure error={state.error} retry={reset} />;
    return <HrCaseTracker assignment={assignment} groups={state.value} />;
  }
  const visibleGroups = state.status === 'ready' ? state.value.filter(g => g.code.toLocaleLowerCase('vi').includes(query.trim().toLocaleLowerCase('vi'))) : [];
  return <div className="internship-groups">
    {(scopeFields || !selected && state.status === 'ready' && state.value.length > 0) &&
      <section className="hr-workspace-context t3-scope-toolbar t3-group-toolbar" aria-label="Phạm vi nhóm thực tập">
        {scopeFields}
        {!selected && state.status === 'ready' && state.value.length > 0 && <label>Tìm nhóm thực tập<input type="search" value={query}
          onChange={e => setQuery(e.target.value)} placeholder="Mã nhóm" /></label>}
        {!selected && groupActions}
      </section>}
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
        <header className="t3-group-banner"><button className="text-button" onClick={() => setGroupId('')}><Icon name="arrow-left" size={16} />Về danh sách nhóm</button>
        <div><h3 title={selected.code}>Nhóm {formatGroupCode(selected.code)}</h3><small>{cohort.code} — {cohort.name}</small></div>
        <dl className="internship-facts"><div><dt><Icon name="briefcase" size={16} />Hồ sơ thực tập</dt><dd>{selected.caseIds.length}</dd></div>
          <div><dt><Icon name="users" size={16} />Mentor được phân công</dt><dd>{new Set(selected.mentorUserIds).size}</dd></div></dl>
        {section === 'mentors' && selected.mentorUserIds.length > 0 && <div className="t3-group-banner-mentors">
          <span className="t3-group-mentors-label">Mentor phụ trách</span>
          <ul className="t3-case-chips" aria-label="Mentor trong nhóm">
            {[...new Set(selected.mentorUserIds)].map(id => <li key={id} title={id}><Icon name="user" size={14} /><span>{formatUserLabel(id, assignment.userId, 'Mentor')}</span></li>)}
          </ul>
        </div>}</header>
        {section !== 'mentors' && !(gateway === liveWorkspaceGateway && assignment.kind === 'HR') &&
          (gateway === liveWorkspaceGateway && assignment.kind === 'MENTOR'
            ? <details className="t3-member-disclosure"><summary>Hồ sơ trong nhóm ({selected.caseIds.length})</summary><GroupMembers group={selected} /></details>
            : <section className="panel stack"><h4>Hồ sơ trong nhóm</h4><GroupMembers group={selected} /></section>)}
        {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && section !== 'mentors' &&
          <HrGroupManagement key={selected.groupId} assignment={assignment} group={selected}
            groups={state.value} changed={() => setReload(value => value + 1)} />}
        {gateway === liveWorkspaceGateway && (section === 'all' || section === 'groups') && <TaskBoard initialView={assignment.kind === 'MENTOR' ? 'overview' : 'board'} assignment={assignment} group={selected} />}
        {gateway === liveWorkspaceGateway && assignment.kind === 'HR' && (section === 'mentors' || section === 'all') &&
          <MentorInvitationForm key={selected.groupId} assignment={assignment} group={selected} groups={state.value} />}
      </div>
      : <><p className="internship-group-count">{state.value.length} nhóm trong phạm vi đã chọn</p>
        {visibleGroups.length === 0 && <p>Không tìm thấy nhóm phù hợp.</p>}
        {section === 'mentors' ? <div className="t3-table-scroll"><table><thead><tr>
          <th>Nhóm</th><th>Hồ sơ</th><th>Mentor phụ trách</th><th>Trạng thái</th><th>Thao tác</th>
        </tr></thead><tbody>{visibleGroups.map(group => <tr key={group.groupId}>
          <th scope="row" title={group.code}>{formatGroupCode(group.code)}</th><td>{group.caseIds.length}</td>
          <td>{[...new Set(group.mentorUserIds)].map(id => <span className="t3-code-pill" key={id} title={id}>{formatEntityShort(id, 'Mentor #')}</span>)}{!group.mentorUserIds.length && <span className="muted">Chưa gán</span>}</td>
          <td><span className="t3-status-pill" data-tone={group.mentorUserIds.length ? 'teal' : 'amber'}>{group.mentorUserIds.length ? 'Đã phân công' : 'Chờ phân công'}</span></td>
          <td><button className="secondary" aria-label={'Xem nhóm ' + group.code} onClick={() => setGroupId(group.groupId)}>Xem nhóm →</button></td>
        </tr>)}</tbody></table></div> : <div className="internship-group-grid">{visibleGroups.map(group => <article className="internship-group-card" key={group.groupId}>
          <div className="t3-group-card-head"><span className="internship-group-icon"><Icon name="users" size={20} /></span><div><h3 title={group.code}>Nhóm {formatGroupCode(group.code)}</h3><small className="muted">{cohort.code} — {cohort.name}</small></div></div>
          <p className="t3-group-counts"><span className="t3-status-pill" data-tone="teal">{group.caseIds.length} hồ sơ thực tập</span><span className="t3-counts-sep" aria-hidden="true"> · </span><span className="t3-status-pill" data-tone={group.mentorUserIds.length ? 'blue' : 'amber'}>{new Set(group.mentorUserIds).size} mentor</span></p>
          <div className="t3-group-card-foot"><button className="secondary" onClick={() => setGroupId(group.groupId)} aria-label={'Xem nhóm ' + group.code}>Xem nhóm<Icon name="arrow-right" size={16} /></button></div>
        </article>)}</div>}</>}
  </div>;
}

function GroupMembers({ group }: { group: InternshipGroup }) {
  return group.caseIds.length
    ? <ul className="t3-case-chips">{group.caseIds.map(id => <li key={id}><Icon name="user" size={14}/><code>{id}</code></li>)}</ul>
    : <p className="muted">Nhóm chưa có hồ sơ.</p>;
}
