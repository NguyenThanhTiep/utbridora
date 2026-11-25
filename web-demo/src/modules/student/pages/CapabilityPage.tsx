import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useViewRouting } from '../../../routing/BrowserRouting';
import { ApiError, type Grant, type Me } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { askConfirm } from '../../../confirm';
import { httpsArtifact } from '../../internship/workflow';
import { getMyProfile, newIdempotencyKey, saveMyProfile, toProfileInput } from '../api';
import { StudentTabs } from '../components/StudentTabs';
import { StudentIcon, StudentToast } from '../components/StudentUI';
import { downloadProfileFile, fileProblem, getFileMetadata, scanLabels, uploadProfileFile, type ProfileFile, type ProfileFilePurpose } from '../files';
import { useStudentTerm } from '../preferences';
import type { Certificate, ProfileInput, Project, SelfProfileView } from '../types';
import '../styles.css';

// S04 (SRS 14.1, /ho-so/nang-luc), US-T2-05, T2-F10..F13: dự án, chứng chỉ, CV và minh chứng trong một màn có tab.
// Mọi thay đổi là PUT hồ sơ đầy đủ (BC11) với expectedProfileVersion; kỹ năng, lịch rảnh và học vụ được chép nguyên.
export type CapabilityTab = 'projects' | 'certificates' | 'cv' | 'evidence';
const tabIcons = { projects: 'folder', certificates: 'award', cv: 'file', evidence: 'paperclip' } as const;
const tabs: { id: CapabilityTab; label: string; icon: ReactNode }[] = (['projects', 'certificates', 'cv', 'evidence'] as const).map(id => ({
  id, label: { projects: 'Dự án', certificates: 'Chứng chỉ', cv: 'CV', evidence: 'Minh chứng' }[id], icon: <StudentIcon name={tabIcons[id]} /> }));
type Files = Record<string, ProfileFile | 'error'>;
type Save = (change: (input: ProfileInput) => ProfileInput, success: string) => Promise<boolean>;
type Upload = (purpose: ProfileFilePurpose, file: File, key: string) => Promise<boolean>;
const isClean = (files: Files, id: string) => { const file = files[id]; return typeof file === 'object' && file.scanStatus === 'CLEAN'; };
const fileLabel = (files: Files, id: string) => { const file = files[id]; return typeof file === 'object' ? file.originalName + ' · ' + scanLabels[file.scanStatus] : 'Tệp …' + id.slice(-6); };
const length = (text: string) => [...text.trim()].length;
const withRequest = (failure: ApiError) => failure.requestId ? ' Mã yêu cầu: ' + failure.requestId + '.' : '';
// Không hiện nguyên văn message server; FILE_* theo CP-FILE và BC11 dòng 778.
function profileError(failure: unknown) {
  if (!(failure instanceof ApiError)) return failure instanceof Error ? failure.message : 'Chưa lưu được hồ sơ.';
  if (failure.errorCode === 'PROFILE_STALE') return 'Hồ sơ vừa thay đổi ở nơi khác. Tải lại dữ liệu mới nhất rồi thao tác lại.';
  if (failure.errorCode === 'FILE_NOT_CLEAN') return 'Tệp chưa được xác nhận an toàn. Chờ trạng thái "Đã xác nhận an toàn" rồi gắn lại.';
  if (failure.errorCode === 'FILE_BINDING_INVALID') return 'Tệp không thuộc hồ sơ của bạn hoặc sai loại (CV/minh chứng).';
  if (failure.errorCode === 'REQUEST_IN_PROGRESS') return 'Yêu cầu trước đang được xử lý. Đợi giây lát rồi thử lại.';
  if (failure.status === 400 || failure.status === 422) return 'Một số thông tin chưa hợp lệ. Kiểm tra lại rồi lưu.' + withRequest(failure);
  return failure.message + withRequest(failure);
}
const uploadError = (failure: unknown) => !(failure instanceof ApiError) ? (failure instanceof Error ? failure.message : 'Chưa tải được tệp.')
  : failure.status === 403 || failure.status === 404 ? 'Chưa thể tải tệp cho hồ sơ này. Lưu Thông tin chung trước rồi thử lại.'
  : failure.status === 413 || failure.status === 415 || failure.status === 422 ? 'Tệp không hợp lệ: chỉ nhận PDF, PNG hoặc JPEG tối đa 10 MiB.'
  : failure.message + withRequest(failure);

// Tiêu đề từng tab, dùng cả khi chưa có học kỳ để 4 mục vẫn tách riêng như khi đã có dữ liệu.
const tabHeadings: Record<CapabilityTab, { kicker: string; title: string }> = {
  projects: { kicker: 'DỰ ÁN', title: 'Dự án đã thực hiện' }, certificates: { kicker: 'CHỨNG CHỈ', title: 'Chứng chỉ đã đạt' },
  cv: { kicker: 'CV', title: 'CV trong hồ sơ' }, evidence: { kicker: 'MINH CHỨNG', title: 'Tệp minh chứng' }
};
// Đầu thẻ của từng tab: biểu tượng trong ô mint, nhãn nhỏ + tiêu đề, mô tả (nếu có).
function TabHeading({ id, children }: { id: CapabilityTab; children?: ReactNode }) {
  return <div className="section-heading"><div className="student-cap-title">
    <span className="student-cap-title-icon" aria-hidden="true"><StudentIcon name={tabIcons[id]} size={19} /></span>
    <div><p className="section-kicker">{tabHeadings[id].kicker}</p><h2>{tabHeadings[id].title}</h2>{children}</div></div></div>;
}
// Ô "Thêm" nằm cuối lưới dự án/chứng chỉ (kiểu thư viện thẻ): tên nút đúng bằng nhãn, dòng gợi ý chỉ để nhìn.
function AddTile({ label, hint, busy, onClick }: { label: string; hint: string; busy: boolean; onClick: () => void }) {
  return <li className="student-add-tile"><button type="button" aria-label={label} disabled={busy} onClick={onClick}>
    <span className="student-add-tile-icon" aria-hidden="true"><Icon name="plus" size={22} /></span>
    <span aria-hidden="true"><b>{label}</b><small>{hint}</small></span></button></li>;
}

export function CapabilityPage({ me, schools }: { me: Me; schools: Grant[] }) {
  const { terms, failed, termId, setTermId, retry } = useStudentTerm(schools);
  const routing=useViewRouting('student-capability');
  const [localTab, setLocalTab] = useState<CapabilityTab>('projects');
  const tab=routing?.route.capabilityTab || localTab;
  const setTab=(next:CapabilityTab)=>{if(routing)routing.go({...routing.route,capabilityTab:next});else setLocalTab(next);};
  const term = terms?.find(item => item.termId === termId);
  const termSelect = terms && terms.length > 1 && <label className="student-term-select">Học kỳ<select value={termId} onChange={event => setTermId(event.target.value)}>
    <option value="" disabled>Chọn học kỳ</option>{terms.map(item => <option key={item.termId} value={item.termId}>{item.termCode}</option>)}</select></label>;
  const termState = terms === null ? <LoadingState label="Đang tải học kỳ…" />
    : terms.length === 0 ? <EmptyState icon="calendar" title="Chưa xác định học kỳ"
      description={(failed ? 'Chưa tải được danh sách học kỳ của trường. ' : 'Trường chưa có học kỳ đang mở. ') + 'Hồ sơ được đọc theo học kỳ nên chưa thể mở năng lực.'}>
      <button type="button" className="secondary" onClick={retry}><Icon name="refresh" size={16} />Thử lại</button></EmptyState>
    : <EmptyState icon="calendar" title="Chọn học kỳ" description="Trường có nhiều học kỳ đang mở. Chọn học kỳ để mở hồ sơ năng lực." />;
  // Thanh nhỏ đầu trang: nhãn học kỳ / chọn học kỳ bên trái, nút tải lại và kiểm tra trạng thái tệp bên phải. Không dùng banner
  // riêng để màn không lặp kiểu thẻ đầu trang của Tổng quan và Thông tin chung; số lượng từng mục nằm ngay trên thanh tab.
  // Chưa có học kỳ thì vẫn giữ 4 tab, mỗi tab báo trạng thái học kỳ trong thẻ của mục đó.
  const toolbar: Toolbar = actions => <div className="student-cap-toolbar">
    {term && <span className="student-status tone-brand"><Icon name="calendar" size={14} />Học kỳ {term.termCode}</span>}
    {termSelect}{actions && <div className="student-cap-actions">{actions}</div>}</div>;
  return <div className="student-page">
    {termId ? <Capability key={termId} me={me} termId={termId} tab={tab} setTab={setTab} toolbar={toolbar} />
      : <>{toolbar()}<StudentTabs label="Hồ sơ năng lực" idPrefix="student-capability" tabs={segmentTabs()} tab={tab} setTab={setTab} variant="segmented" render={id =>
        <section className="panel student-term-panel"><TabHeading id={id} />{termState}</section>} /></>}
  </div>;
}

type Toolbar = (actions?: ReactNode) => ReactNode;
// Thanh tab phân đoạn liền khối: biểu tượng, tên mục và dòng phụ số lượng đọc từ hồ sơ (chưa đọc được hồ sơ thì không có dòng phụ).
const segmentTabs = (meta?: Record<CapabilityTab, string>) => tabs.map(item => ({ ...item, icon: <StudentIcon name={tabIcons[item.id]} size={19} />, meta: meta?.[item.id] }));
function Capability({ me, termId, tab, setTab, toolbar }: {
  me: Me; termId: string; tab: CapabilityTab; setTab: (tab: CapabilityTab) => void; toolbar: Toolbar;
}) {
  const [view, setView] = useState<SelfProfileView | null>(null), [loadError, setLoadError] = useState(''), [missing, setMissing] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [files, setFiles] = useState<Files>({}), [fileAttempt, setFileAttempt] = useState(0);
  // Tệp vừa tải lên trong phiên này; chưa gắn vào hồ sơ thì không được lưu lại sau khi tải lại trang.
  const [uploaded, setUploaded] = useState<Record<ProfileFilePurpose, string[]>>({ CV: [], EVIDENCE: [] });
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState(''), [conflict, setConflict] = useState(false);
  const pending = useRef<{ body: string; key: string } | null>(null);
  useEffect(() => {
    let active = true;
    setLoadError(''); setMissing(false); setConflict(false); pending.current = null;
    getMyProfile(termId).then(value => { if (active) setView(value); }, (failure: unknown) => {
      if (!active) return;
      if (failure instanceof ApiError && failure.status === 404) setMissing(true);
      else setLoadError(failure instanceof Error ? failure.message : 'Không thể tải hồ sơ. Vui lòng thử lại.');
    });
    return () => { active = false; };
  }, [termId, attempt]);
  const profile = view?.profile;
  const evidence = profile ? [...new Set([...profile.projects.flatMap(item => item.evidenceFileIds),
    ...profile.certificates.flatMap(item => item.fileId ? [item.fileId] : []), ...uploaded.EVIDENCE])] : [];
  const ids = [...new Set([...(profile?.cvFileId ? [profile.cvFileId] : []), ...uploaded.CV, ...evidence])].join(',');
  // T1 trả trạng thái quét hiện hành; đọc lại khi danh sách tệp đổi hoặc người dùng yêu cầu kiểm tra.
  useEffect(() => {
    let active = true;
    for (const id of ids.split(',').filter(Boolean)) getFileMetadata(id).then(file => { if (active) setFiles(previous => ({ ...previous, [id]: file })); },
      () => { if (active) setFiles(previous => ({ ...previous, [id]: 'error' })); });
    return () => { active = false; };
  }, [ids, fileAttempt]);

  // Cùng nội dung dùng lại Idempotency-Key để thử lại an toàn; nội dung khác dùng key mới (C.0).
  const save: Save = async (change, success) => {
    if (!view || busy || conflict) return false;
    const input = change(toProfileInput(view)), body = JSON.stringify(input);
    if (pending.current?.body !== body) pending.current = { body, key: newIdempotencyKey() };
    const key = pending.current.key;
    setBusy(true); setError(''); setNotice('');
    try {
      const saved = await saveMyProfile(input, key);
      pending.current = null; setView(saved); setNotice(success); return true;
    } catch (failure) {
      if (failure instanceof ApiError && failure.errorCode === 'PROFILE_STALE') setConflict(true);
      setError(profileError(failure)); return false;
    } finally { setBusy(false); }
  };
  const upload: Upload = async (purpose, file, key) => {
    setBusy(true); setError(''); setNotice('');
    try {
      const dto = await uploadProfileFile(me.userId, purpose, file, key);
      if (dto.resource.resourceType !== 'STUDENT_PROFILE' || dto.resource.resourceId !== me.userId || dto.purpose !== purpose)
        throw new Error('Máy chủ trả về tệp không đúng hồ sơ; tệp không được dùng.');
      setFiles(previous => ({ ...previous, [dto.fileId]: dto }));
      setUploaded(previous => ({ ...previous, [purpose]: [...previous[purpose], dto.fileId] }));
      setNotice('Đã tải tệp lên. Tệp đang chờ quét; chỉ gắn vào hồ sơ được khi đã xác nhận an toàn.'); return true;
    } catch (failure) { setError(uploadError(failure)); return false; } finally { setBusy(false); }
  };
  const reload = () => { setView(null); setFiles({}); setAttempt(previous => previous + 1); };

  if (missing) return <>{toolbar()}<section className="panel"><EmptyState icon="user" title="Chưa có hồ sơ trong học kỳ này"
    description="Mở Thông tin chung để tạo và lưu hồ sơ trước, sau đó quay lại khai dự án, chứng chỉ, CV và minh chứng." /></section></>;
  if (loadError) return <>{toolbar()}<section className="panel"><EmptyState icon="alert" title="Không tải được hồ sơ" description={loadError}>
    <button type="button" className="secondary" onClick={reload}><Icon name="refresh" size={16} />Thử lại</button></EmptyState></section></>;
  if (!view || !profile) return <>{toolbar()}<section className="panel"><LoadingState label="Đang tải hồ sơ…" /></section></>;
  const locked = busy || conflict;
  // Số lượng trên thẻ tab đọc từ hồ sơ đã lưu; minh chứng gồm cả tệp vừa tải trong phiên.
  const meta: Record<CapabilityTab, string> = {
    projects: profile.projects.length + ' dự án', certificates: profile.certificates.length + ' chứng chỉ',
    cv: profile.cvFileId ? 'Đã có CV' : 'Chưa có CV', evidence: evidence.length + ' tệp'
  };
  return <>
    <Notice message={error} tone="error" /><StudentToast message={notice} onClose={() => setNotice('')} />
    {toolbar(<>
      <button type="button" className="secondary" disabled={busy} onClick={reload}><Icon name="refresh" size={15} />Tải lại dữ liệu mới nhất</button>
      <button type="button" className="secondary" disabled={busy} onClick={() => setFileAttempt(previous => previous + 1)}><Icon name="shield" size={15} />Kiểm tra trạng thái tệp</button>
    </>)}
    <StudentTabs label="Hồ sơ năng lực" idPrefix="student-capability" tabs={segmentTabs(meta)} tab={tab} setTab={setTab} variant="segmented" render={id =>
      id === 'projects' ? <ProjectsTab view={view} evidence={evidence} files={files} busy={locked} save={save} />
      : id === 'certificates' ? <CertificatesTab view={view} evidence={evidence} files={files} busy={locked} save={save} />
      : id === 'cv' ? <CvTab view={view} candidates={uploaded.CV} files={files} busy={locked} save={save} upload={upload} />
      : <EvidenceTab view={view} evidence={evidence} files={files} busy={locked} upload={upload} />} />
  </>;
}

function FileRow({ id, files }: { id: string; files: Files }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const file = files[id];
  const name = file === undefined ? 'Đang tải thông tin tệp…' : file === 'error' ? 'Tệp …' + id.slice(-6) + ' (chưa đọc được thông tin)' : file.originalName;
  return <span className="student-file">
    <span className="student-file-icon" aria-hidden="true"><StudentIcon name="file" size={16} /></span>
    <span className="student-file-name">{name}</span>
    {typeof file === 'object' && <span className={'student-status ' + file.scanStatus.toLowerCase()}>{scanLabels[file.scanStatus]}</span>}
    {typeof file === 'object' && <button type="button" className="text-button" aria-label={'Tải xuống ' + file.originalName} disabled={busy || file.scanStatus !== 'CLEAN'}
      onClick={() => { setBusy(true); setError(''); downloadProfileFile(file).catch(() => setError('Chưa tải được tệp.')).finally(() => setBusy(false)); }}>Tải xuống</button>}
    {error && <span className="student-field-error">{error}</span>}
  </span>;
}
// Thẻ dự án chỉ hiện tên miền của đường dẫn (đủ đường dẫn khi rê chuột) và chữ cái đầu của tên làm biểu tượng.
const host = (url: string) => { try { return new URL(url).hostname.replace(/^www\./, ''); } catch { return url; } };
const initial = (title: string) => [...title.trim()][0]?.toUpperCase() ?? '?';
const day = (value: string) => value.split('-').reverse().join('/');
function FileList({ ids, files }: { ids: string[]; files: Files }) {
  return ids.length === 0 ? null : <ul className="student-file-list">{ids.map(id => <li key={id}><FileRow id={id} files={files} /></li>)}</ul>;
}
function UploadForm({ label, action, busy, onUpload }: { label: string; action: string; busy: boolean; onUpload: (file: File, key: string) => Promise<boolean> }) {
  const [file, setFile] = useState<File | null>(null), [reset, setReset] = useState(0), keys = useRef(new Map<File, string>());
  const problem = file ? fileProblem(file) : '';
  return <form className="student-upload" onSubmit={event => {
    event.preventDefault(); if (!file || problem) return;
    const key = keys.current.get(file) ?? newIdempotencyKey(); keys.current.set(file, key);
    void onUpload(file, key).then(ok => { if (ok) { keys.current.delete(file); setFile(null); setReset(previous => previous + 1); } });
  }}>
    <span className="student-upload-icon" aria-hidden="true"><StudentIcon name="upload" size={20} /></span>
    <label>{label}<input key={reset} type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={event => setFile(event.target.files?.[0] ?? null)} /></label>
    {problem && <p className="student-field-error">{problem}</p>}
    <p className="field-hint">PDF, PNG hoặc JPEG; tối đa 10 MiB. Tệp mới luôn chờ quét trước khi gắn vào hồ sơ.</p>
    <button type="submit" disabled={busy || !file || !!problem}>{action}</button>
  </form>;
}
// Tệp đã gắn trong bản ghi đang sửa được giữ; tệp mới chỉ chọn được khi đã CLEAN (server vẫn kiểm lại).
function EvidencePicker({ evidence, kept, selected, files, toggle }: { evidence: string[]; kept: string[]; selected: string[]; files: Files; toggle: (id: string) => void }) {
  return <fieldset className="student-fieldset"><legend>Minh chứng đính kèm</legend>
    {evidence.length === 0 ? <p className="field-hint">Chưa có minh chứng. Tải tệp ở tab Minh chứng trước.</p>
      : evidence.map(id => <label className="student-checkbox" key={id}><input type="checkbox" checked={selected.includes(id)}
        disabled={!selected.includes(id) && !kept.includes(id) && !isClean(files, id)} onChange={() => toggle(id)} />{fileLabel(files, id)}</label>)}
  </fieldset>;
}

type ProjectDraft = { title: string; role: string; description: string; url: string; skillCodes: string[]; evidenceFileIds: string[] };
const emptyProject: ProjectDraft = { title: '', role: '', description: '', url: '', skillCodes: [], evidenceFileIds: [] };
const projectProblem = (draft: ProjectDraft) => length(draft.title) < 1 || length(draft.title) > 200 ? 'Tên dự án cần từ 1 đến 200 ký tự.'
  : length(draft.role) > 200 ? 'Vai trò tối đa 200 ký tự.' : length(draft.description) > 5000 ? 'Mô tả tối đa 5.000 ký tự.'
  : draft.url.trim() && (!httpsArtifact(draft.url.trim()) || draft.url.trim().length > 2048) ? 'Đường dẫn phải là URL https:// hợp lệ, không chứa tài khoản hoặc mật khẩu.' : '';
function ProjectsTab({ view, evidence, files, busy, save }: { view: SelfProfileView; evidence: string[]; files: Files; busy: boolean; save: Save }) {
  const projects = view.profile.projects;
  const [editing, setEditing] = useState<number | 'new' | null>(null), [draft, setDraft] = useState(emptyProject), [touched, setTouched] = useState(false);
  const skills = [...new Set([...view.profile.skills.map(item => item.skillCode), ...draft.skillCodes])];
  const problem = projectProblem(draft);
  const open = (index: number | 'new') => {
    const item = index === 'new' ? null : projects[index];
    setEditing(index); setTouched(false);
    setDraft(item ? { title: item.title, role: item.role, description: item.description, url: item.url ?? '', skillCodes: [...item.skillCodes], evidenceFileIds: [...item.evidenceFileIds] } : emptyProject);
  };
  const submit = (event: FormEvent) => {
    event.preventDefault(); setTouched(true);
    if (editing === null || problem) return;
    const next: Project = { projectId: editing === 'new' ? null : projects[editing].projectId, title: draft.title.trim(), description: draft.description.trim(),
      role: draft.role.trim(), skillCodes: draft.skillCodes, url: draft.url.trim() || null, evidenceFileIds: draft.evidenceFileIds };
    void save(input => ({ ...input, projects: editing === 'new' ? [...input.projects, next] : input.projects.map((item, index) => index === editing ? next : item) }),
      editing === 'new' ? 'Đã thêm dự án.' : 'Đã cập nhật dự án.').then(ok => { if (ok) setEditing(null); });
  };
  const toggle = (key: 'skillCodes' | 'evidenceFileIds', value: string) =>
    setDraft(previous => ({ ...previous, [key]: previous[key].includes(value) ? previous[key].filter(item => item !== value) : [...previous[key], value] }));
  // Frame 477:11368 "Dự án cá nhân" dựng thành thư viện thẻ (portfolio): mỗi dự án có dải bìa xanh với chữ lồng (chữ cái đầu tên),
  // nút Sửa/Xóa nổi trên bìa, tên, vai trò, kỹ năng, mô tả và chân thẻ (tên miền đường dẫn, số minh chứng); ô "Thêm dự án" ở cuối lưới
  // (ẩn khi đang sửa). Chưa có dự án thì chính ô thêm là trạng thái trống.
  return <section className="panel student-cap-section">
    <TabHeading id="projects" />
    {(projects.length > 0 || editing === null) && <ul className="student-gallery">{projects.map((item, index) => <li className="student-project-card" key={item.projectId ?? index}>
      <div className="student-project-cover" aria-hidden="true"><span className="student-project-monogram">{initial(item.title)}</span></div>
      <div className="student-item-actions student-card-tools">
        <button type="button" className="secondary student-icon-button" aria-label={'Sửa dự án ' + item.title} title="Sửa" disabled={busy || editing !== null} onClick={() => open(index)}><StudentIcon name="pencil" size={15} /></button>
        <button type="button" className="secondary student-icon-button student-danger" aria-label={'Xóa dự án ' + item.title} title="Xóa" disabled={busy || editing !== null} onClick={async () => {
          if (await askConfirm({ title: 'Xóa dự án?', message: 'Xóa dự án này khỏi hồ sơ?', confirmLabel: 'Xóa dự án', tone: 'danger' })) void save(input => ({ ...input, projects: input.projects.filter((_, position) => position !== index) }), 'Đã xóa dự án.');
        }}><StudentIcon name="trash" size={15} /></button></div>
      <div className="student-project-body">
        <h3>{item.title}</h3>
        {item.role && <p className="student-project-role"><Icon name="user" size={14} />Vai trò: {item.role}</p>}
        {item.skillCodes.length > 0 && <p className="student-item-tags">{item.skillCodes.map(code => <span key={code} className="tone-skill">{code}</span>)}</p>}
        {item.description && <p className="student-list-text student-pre-line student-item-text">{item.description}</p>}
        {(item.url || item.evidenceFileIds.length > 0) && <div className="student-item-foot">
          {item.url && <a className="student-item-link" href={item.url} target="_blank" rel="noopener noreferrer" title={item.url}><StudentIcon name="link" size={14} /><span>{host(item.url)}</span></a>}
          {item.evidenceFileIds.length > 0 && <span className="student-item-count"><StudentIcon name="paperclip" size={13} />{item.evidenceFileIds.length} minh chứng</span>}
        </div>}
        <FileList ids={item.evidenceFileIds} files={files} />
      </div>
    </li>)}
      {editing === null && <AddTile label="Thêm dự án" busy={busy} onClick={() => open('new')}
        hint={projects.length ? 'Ghi lại một dự án bạn đã làm' : 'Thêm dự án để nhà trường và doanh nghiệp hiểu năng lực thực tế của bạn'} />}
    </ul>}
    {editing !== null && <form className="student-profile-form" noValidate onSubmit={submit}><fieldset className="student-fieldset" disabled={busy}>
      <legend>{editing === 'new' ? 'Thêm dự án' : 'Sửa dự án'}</legend>
      <label>Tên dự án<input autoFocus value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value })} /></label>
      <label>Vai trò của bạn<input value={draft.role} onChange={event => setDraft({ ...draft, role: event.target.value })} /></label>
      <label>Mô tả<textarea value={draft.description} onChange={event => setDraft({ ...draft, description: event.target.value })} /></label>
      <label>Đường dẫn dự án (https)<input type="url" value={draft.url} onChange={event => setDraft({ ...draft, url: event.target.value })} /></label>
      <fieldset className="student-fieldset"><legend>Kỹ năng sử dụng</legend>
        {skills.length === 0 ? <p className="field-hint">Chưa có kỹ năng chuẩn trong hồ sơ. Khai kỹ năng ở Thông tin chung trước.</p>
          : skills.map(code => <label className="student-checkbox" key={code}><input type="checkbox" checked={draft.skillCodes.includes(code)} onChange={() => toggle('skillCodes', code)} />{code}</label>)}
      </fieldset>
      <EvidencePicker evidence={evidence} kept={editing === 'new' ? [] : projects[editing].evidenceFileIds} selected={draft.evidenceFileIds} files={files}
        toggle={id => toggle('evidenceFileIds', id)} />
      {touched && problem && <p className="student-field-error">{problem}</p>}
      <div className="button-row"><button type="submit">Lưu dự án</button><button type="button" className="secondary" onClick={() => setEditing(null)}>Hủy</button></div>
    </fieldset></form>}
  </section>;
}

type CertificateDraft = { title: string; issuer: string; issuedOn: string; expiresOn: string; fileId: string };
const emptyCertificate: CertificateDraft = { title: '', issuer: '', issuedOn: '', expiresOn: '', fileId: '' };
const certificateProblem = (draft: CertificateDraft) => length(draft.title) < 1 || length(draft.title) > 200 ? 'Tên chứng chỉ cần từ 1 đến 200 ký tự.'
  : length(draft.issuer) < 1 || length(draft.issuer) > 200 ? 'Đơn vị cấp cần từ 1 đến 200 ký tự.' : !draft.issuedOn ? 'Chọn ngày cấp.'
  : draft.expiresOn && draft.expiresOn < draft.issuedOn ? 'Ngày hết hạn phải sau hoặc trùng ngày cấp.' : '';
function CertificatesTab({ view, evidence, files, busy, save }: { view: SelfProfileView; evidence: string[]; files: Files; busy: boolean; save: Save }) {
  const certificates = view.profile.certificates;
  const [editing, setEditing] = useState<number | 'new' | null>(null), [draft, setDraft] = useState(emptyCertificate), [touched, setTouched] = useState(false);
  const problem = certificateProblem(draft);
  const kept = editing === null || editing === 'new' ? null : certificates[editing].fileId;
  const open = (index: number | 'new') => {
    const item = index === 'new' ? null : certificates[index];
    setEditing(index); setTouched(false);
    setDraft(item ? { title: item.title, issuer: item.issuer, issuedOn: item.issuedOn, expiresOn: item.expiresOn ?? '', fileId: item.fileId ?? '' } : emptyCertificate);
  };
  const submit = (event: FormEvent) => {
    event.preventDefault(); setTouched(true);
    if (editing === null || problem) return;
    const next: Certificate = { certificateId: editing === 'new' ? null : certificates[editing].certificateId, title: draft.title.trim(), issuer: draft.issuer.trim(),
      issuedOn: draft.issuedOn, expiresOn: draft.expiresOn || null, fileId: draft.fileId || null };
    void save(input => ({ ...input, certificates: editing === 'new' ? [...input.certificates, next] : input.certificates.map((item, index) => index === editing ? next : item) }),
      editing === 'new' ? 'Đã thêm chứng chỉ.' : 'Đã cập nhật chứng chỉ.').then(ok => { if (ok) setEditing(null); });
  };
  // Thẻ chứng chỉ kiểu huy chương: cột huy chương xanh bên trái, tên, đơn vị cấp, ô ngày cấp / hết hạn và tệp minh chứng;
  // nút Sửa/Xóa ở góc; ô "Thêm chứng chỉ" ở cuối lưới (chưa có chứng chỉ thì chính ô thêm là trạng thái trống).
  return <section className="panel student-cap-section">
    <TabHeading id="certificates" />
    {(certificates.length > 0 || editing === null) && <ul className="student-gallery">{certificates.map((item, index) => <li className="student-cert-card" key={item.certificateId ?? index}>
      <div className="student-cert-medal" aria-hidden="true"><StudentIcon name="award" size={26} /></div>
      <div className="student-item-actions student-card-tools">
        <button type="button" className="secondary student-icon-button" aria-label={'Sửa chứng chỉ ' + item.title} title="Sửa" disabled={busy || editing !== null} onClick={() => open(index)}><StudentIcon name="pencil" size={15} /></button>
        <button type="button" className="secondary student-icon-button student-danger" aria-label={'Xóa chứng chỉ ' + item.title} title="Xóa" disabled={busy || editing !== null} onClick={async () => {
          if (await askConfirm({ title: 'Xóa chứng chỉ?', message: 'Xóa chứng chỉ này khỏi hồ sơ?', confirmLabel: 'Xóa chứng chỉ', tone: 'danger' })) void save(input => ({ ...input, certificates: input.certificates.filter((_, position) => position !== index) }), 'Đã xóa chứng chỉ.');
        }}><StudentIcon name="trash" size={15} /></button></div>
      <div className="student-cert-body">
        <h3>{item.title}</h3>
        <p className="student-company">{item.issuer}</p>
        <dl className="student-cert-dates"><div><dt>Ngày cấp</dt><dd>{day(item.issuedOn)}</dd></div><div><dt>Hết hạn</dt><dd>{item.expiresOn ? day(item.expiresOn) : 'Không có'}</dd></div></dl>
        <FileList ids={item.fileId ? [item.fileId] : []} files={files} />
      </div>
    </li>)}
      {editing === null && <AddTile label="Thêm chứng chỉ" busy={busy} onClick={() => open('new')}
        hint={certificates.length ? 'Thêm chứng chỉ bạn vừa đạt' : 'Thêm chứng chỉ kèm minh chứng để hồ sơ đáng tin cậy hơn'} />}
    </ul>}
    {editing !== null && <form className="student-profile-form" noValidate onSubmit={submit}><fieldset className="student-fieldset" disabled={busy}>
      <legend>{editing === 'new' ? 'Thêm chứng chỉ' : 'Sửa chứng chỉ'}</legend>
      <label>Tên chứng chỉ<input autoFocus value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value })} /></label>
      <label>Đơn vị cấp<input value={draft.issuer} onChange={event => setDraft({ ...draft, issuer: event.target.value })} /></label>
      <label>Ngày cấp<input type="date" value={draft.issuedOn} onChange={event => setDraft({ ...draft, issuedOn: event.target.value })} /></label>
      <label>Ngày hết hạn (nếu có)<input type="date" value={draft.expiresOn} onChange={event => setDraft({ ...draft, expiresOn: event.target.value })} /></label>
      <label>Tệp minh chứng<select value={draft.fileId} onChange={event => setDraft({ ...draft, fileId: event.target.value })}>
        <option value="">Không đính kèm</option>
        {evidence.filter(id => id === kept || id === draft.fileId || isClean(files, id)).map(id => <option key={id} value={id}>{fileLabel(files, id)}</option>)}</select></label>
      <p className="field-hint">Chỉ chọn được minh chứng đã xác nhận an toàn. Tải tệp mới ở tab Minh chứng.</p>
      {touched && problem && <p className="student-field-error">{problem}</p>}
      <div className="button-row"><button type="submit">Lưu chứng chỉ</button><button type="button" className="secondary" onClick={() => setEditing(null)}>Hủy</button></div>
    </fieldset></form>}
  </section>;
}

function CvTab({ view, candidates, files, busy, save, upload }: { view: SelfProfileView; candidates: string[]; files: Files; busy: boolean; save: Save; upload: Upload }) {
  const current = view.profile.cvFileId;
  const fresh = candidates.filter(id => id !== current);
  // Bên trái: thẻ "CV đang dùng" có hình trang giấy minh họa (chỉ để nhìn), tên tệp, trạng thái quét, tải xuống, gỡ CV;
  // bên phải: khối tải CV mới. Hẹp thì xếp dọc.
  return <section className="panel student-cap-section">
    <TabHeading id="cv"><p className="section-description">Doanh nghiệp chỉ xem được CV khi bạn đồng ý chia sẻ và tệp đã được xác nhận an toàn.</p></TabHeading>
    <div className="student-cv-layout">
      <div className={'student-cv-current' + (current ? '' : ' empty')}>
        <div className="student-cv-paper" aria-hidden="true"><b>CV</b><span /><span /><span /><span /><span /></div>
        <div className="student-cv-info"><p className="student-cv-label">CV đang dùng</p>
          {current ? <><FileList ids={[current]} files={files} />
            <div className="button-row"><button type="button" className="secondary" disabled={busy} onClick={async () => {
              if (await askConfirm({ title: 'Gỡ CV khỏi hồ sơ?', message: 'Gỡ CV khỏi hồ sơ? CV sẽ không còn gắn với hồ sơ của bạn.', confirmLabel: 'Gỡ CV', tone: 'danger' })) void save(input => ({ ...input, cvFileId: null }), 'Đã gỡ CV khỏi hồ sơ.');
            }}>Gỡ CV khỏi hồ sơ</button></div></>
            : <p className="student-list-meta">Chưa có CV trong hồ sơ. Tải tệp CV ở khung bên cạnh, chờ quét an toàn rồi bấm "Dùng làm CV".</p>}
        </div>
      </div>
      <UploadForm label="Chọn tệp CV" action="Tải CV lên" busy={busy} onUpload={(file, key) => upload('CV', file, key)} />
    </div>
    {fresh.length > 0 && <><h3>CV vừa tải lên</h3><ul className="student-file-list">{fresh.map(id => <li key={id}><FileRow id={id} files={files} />
      <button type="button" disabled={busy || !isClean(files, id)} onClick={() => void save(input => ({ ...input, cvFileId: id }), 'Đã gắn CV vào hồ sơ.')}>Dùng làm CV</button></li>)}</ul>
      <p className="field-hint">Bấm "Kiểm tra trạng thái tệp" sau ít phút; chỉ dùng làm CV khi tệp đã được xác nhận an toàn.</p></>}
  </section>;
}

function EvidenceTab({ view, evidence, files, busy, upload }: { view: SelfProfileView; evidence: string[]; files: Files; busy: boolean; upload: Upload }) {
  const usage = (id: string) => [...view.profile.projects.filter(item => item.evidenceFileIds.includes(id)).map(item => 'Dự án: ' + item.title),
    ...view.profile.certificates.filter(item => item.fileId === id).map(item => 'Chứng chỉ: ' + item.title)];
  // Khối tải lên ở trên; mỗi minh chứng là một ô trong lưới tệp: tên, trạng thái quét, tải xuống, nơi đang gắn (chip).
  return <section className="panel student-cap-section">
    <TabHeading id="evidence"><p className="section-description">Minh chứng chỉ được lưu trong hồ sơ khi gắn vào một dự án hoặc chứng chỉ; tệp chưa gắn sẽ không còn hiện sau khi tải lại trang.</p></TabHeading>
    <UploadForm label="Chọn tệp minh chứng" action="Tải minh chứng lên" busy={busy} onUpload={(file, key) => upload('EVIDENCE', file, key)} />
    {evidence.length === 0 ? <p className="student-list-meta">Chưa có minh chứng.</p>
      : <ul className="student-file-list student-file-grid">{evidence.map(id => { const used = usage(id);
        return <li key={id}><FileRow id={id} files={files} />
          <p className="student-item-tags">{used.length ? used.map(text => <span key={text} className="tone-skill">{text}</span>) : <span>Chưa gắn vào dự án hoặc chứng chỉ</span>}</p></li>; })}</ul>}
  </section>;
}
