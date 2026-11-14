import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, type Grant, type Me } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { httpsArtifact } from '../../internship/workflow';
import { getMyProfile, newIdempotencyKey, saveMyProfile, toProfileInput } from '../api';
import { StudentTabs } from '../components/StudentTabs';
import { downloadProfileFile, fileProblem, getFileMetadata, scanLabels, uploadProfileFile, type ProfileFile, type ProfileFilePurpose } from '../files';
import { useStudentTerm } from '../preferences';
import type { Certificate, ProfileInput, Project, SelfProfileView } from '../types';
import '../styles.css';

// S04 (SRS 14.1, /ho-so/nang-luc), US-T2-05, T2-F10..F13: dự án, chứng chỉ, CV và minh chứng trong một màn có tab.
// Mọi thay đổi là PUT hồ sơ đầy đủ (BC11) với expectedProfileVersion; kỹ năng, lịch rảnh và học vụ được chép nguyên.
export type CapabilityTab = 'projects' | 'certificates' | 'cv' | 'evidence';
const tabs: { id: CapabilityTab; label: string }[] = [
  { id: 'projects', label: 'Dự án' }, { id: 'certificates', label: 'Chứng chỉ' }, { id: 'cv', label: 'CV' }, { id: 'evidence', label: 'Minh chứng' }];
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

export function CapabilityPage({ me, schools }: { me: Me; schools: Grant[] }) {
  const { terms, failed, termId, setTermId, retry } = useStudentTerm(schools);
  const term = terms?.find(item => item.termId === termId);
  return <div className="student-page">
    <section className="panel student-term-panel">
      <div className="section-heading"><div><p className="section-kicker">HỒ SƠ NĂNG LỰC</p><h2>Dự án, chứng chỉ, CV và minh chứng</h2>
        <p className="section-description">{term ? 'Học kỳ ' + term.termCode + '. ' : ''}Tệp được lưu riêng tư và chỉ tải xuống khi đã được xác nhận an toàn.</p></div>
        {terms && terms.length > 1 && <label className="student-term-select">Học kỳ<select value={termId} onChange={event => setTermId(event.target.value)}>
          <option value="" disabled>Chọn học kỳ</option>{terms.map(item => <option key={item.termId} value={item.termId}>{item.termCode}</option>)}</select></label>}</div>
      {terms === null ? <LoadingState label="Đang tải học kỳ…" />
        : terms.length === 0 ? <EmptyState icon="calendar" title="Chưa xác định học kỳ"
          description={(failed ? 'Chưa tải được danh sách học kỳ của trường. ' : 'Trường chưa có học kỳ đang mở. ') + 'Hồ sơ được đọc theo học kỳ nên chưa thể mở năng lực.'}>
          <button type="button" className="secondary" onClick={retry}><Icon name="refresh" size={16} />Thử lại</button></EmptyState>
        : !termId && <EmptyState icon="calendar" title="Chọn học kỳ" description="Trường có nhiều học kỳ đang mở. Chọn học kỳ để mở hồ sơ năng lực." />}
    </section>
    {termId && <Capability key={termId} me={me} termId={termId} />}
  </div>;
}

function Capability({ me, termId }: { me: Me; termId: string }) {
  const [view, setView] = useState<SelfProfileView | null>(null), [loadError, setLoadError] = useState(''), [missing, setMissing] = useState(false);
  const [attempt, setAttempt] = useState(0), [tab, setTab] = useState<CapabilityTab>('projects');
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

  if (missing) return <section className="panel"><EmptyState icon="user" title="Chưa có hồ sơ trong học kỳ này"
    description="Mở Thông tin chung để tạo và lưu hồ sơ trước, sau đó quay lại khai dự án, chứng chỉ, CV và minh chứng." /></section>;
  if (loadError) return <section className="panel"><EmptyState icon="alert" title="Không tải được hồ sơ" description={loadError}>
    <button type="button" className="secondary" onClick={reload}><Icon name="refresh" size={16} />Thử lại</button></EmptyState></section>;
  if (!view || !profile) return <section className="panel"><LoadingState label="Đang tải hồ sơ…" /></section>;
  const locked = busy || conflict;
  return <>
    <Notice message={error} tone="error" /><Notice message={notice} tone="success" />
    <div className="button-row">
      <button type="button" className="secondary" disabled={busy} onClick={reload}><Icon name="refresh" size={16} />Tải lại dữ liệu mới nhất</button>
      <button type="button" className="secondary" disabled={busy} onClick={() => setFileAttempt(previous => previous + 1)}>Kiểm tra trạng thái tệp</button>
    </div>
    <StudentTabs label="Hồ sơ năng lực" idPrefix="student-capability" tabs={tabs} tab={tab} setTab={setTab} render={id =>
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
    <span className="student-file-name">{name}</span>
    {typeof file === 'object' && <span className={'student-status ' + file.scanStatus.toLowerCase()}>{scanLabels[file.scanStatus]}</span>}
    {typeof file === 'object' && <button type="button" className="text-button" aria-label={'Tải xuống ' + file.originalName} disabled={busy || file.scanStatus !== 'CLEAN'}
      onClick={() => { setBusy(true); setError(''); downloadProfileFile(file).catch(() => setError('Chưa tải được tệp.')).finally(() => setBusy(false)); }}>Tải xuống</button>}
    {error && <span className="student-field-error">{error}</span>}
  </span>;
}
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
  return <section className="panel">
    <div className="section-heading"><div><p className="section-kicker">DỰ ÁN</p><h2>Dự án đã thực hiện</h2></div>
      <button type="button" disabled={busy || editing !== null} onClick={() => open('new')}><Icon name="plus" size={16} />Thêm dự án</button></div>
    {projects.length === 0 ? <EmptyState icon="briefcase" title="Chưa có dự án" description="Thêm dự án để nhà trường và doanh nghiệp hiểu năng lực thực tế của bạn." />
      : <ul className="student-list">{projects.map((item, index) => <li className="student-list-item" key={item.projectId ?? index}>
        <h3>{item.title}</h3>
        {item.role && <p className="student-list-meta">Vai trò: {item.role}</p>}
        {item.description && <p className="student-list-meta student-pre-line">{item.description}</p>}
        {item.url && <p className="student-list-meta"><a href={item.url} target="_blank" rel="noopener noreferrer">{item.url}</a></p>}
        {item.skillCodes.length > 0 && <p className="student-list-meta">Kỹ năng: {item.skillCodes.join(', ')}</p>}
        <FileList ids={item.evidenceFileIds} files={files} />
        <div className="button-row">
          <button type="button" className="secondary" aria-label={'Sửa dự án ' + item.title} disabled={busy || editing !== null} onClick={() => open(index)}>Sửa</button>
          <button type="button" className="secondary" aria-label={'Xóa dự án ' + item.title} disabled={busy || editing !== null} onClick={() => {
            if (window.confirm('Xóa dự án này khỏi hồ sơ?')) void save(input => ({ ...input, projects: input.projects.filter((_, position) => position !== index) }), 'Đã xóa dự án.');
          }}>Xóa</button></div>
      </li>)}</ul>}
    {editing !== null && <form className="student-profile-form" noValidate onSubmit={submit}><fieldset className="student-fieldset" disabled={busy}>
      <legend>{editing === 'new' ? 'Thêm dự án' : 'Sửa dự án'}</legend>
      <label>Tên dự án<input value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value })} /></label>
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
  return <section className="panel">
    <div className="section-heading"><div><p className="section-kicker">CHỨNG CHỈ</p><h2>Chứng chỉ đã đạt</h2></div>
      <button type="button" disabled={busy || editing !== null} onClick={() => open('new')}><Icon name="plus" size={16} />Thêm chứng chỉ</button></div>
    {certificates.length === 0 ? <EmptyState icon="check-circle" title="Chưa có chứng chỉ" description="Thêm chứng chỉ kèm minh chứng để hồ sơ đáng tin cậy hơn." />
      : <ul className="student-list">{certificates.map((item, index) => <li className="student-list-item" key={item.certificateId ?? index}>
        <h3>{item.title}</h3><p className="student-list-meta">{item.issuer} · Cấp ngày {item.issuedOn}{item.expiresOn ? ' · Hết hạn ' + item.expiresOn : ''}</p>
        <FileList ids={item.fileId ? [item.fileId] : []} files={files} />
        <div className="button-row">
          <button type="button" className="secondary" aria-label={'Sửa chứng chỉ ' + item.title} disabled={busy || editing !== null} onClick={() => open(index)}>Sửa</button>
          <button type="button" className="secondary" aria-label={'Xóa chứng chỉ ' + item.title} disabled={busy || editing !== null} onClick={() => {
            if (window.confirm('Xóa chứng chỉ này khỏi hồ sơ?')) void save(input => ({ ...input, certificates: input.certificates.filter((_, position) => position !== index) }), 'Đã xóa chứng chỉ.');
          }}>Xóa</button></div>
      </li>)}</ul>}
    {editing !== null && <form className="student-profile-form" noValidate onSubmit={submit}><fieldset className="student-fieldset" disabled={busy}>
      <legend>{editing === 'new' ? 'Thêm chứng chỉ' : 'Sửa chứng chỉ'}</legend>
      <label>Tên chứng chỉ<input value={draft.title} onChange={event => setDraft({ ...draft, title: event.target.value })} /></label>
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
  return <section className="panel">
    <div className="section-heading"><div><p className="section-kicker">CV</p><h2>CV trong hồ sơ</h2>
      <p className="section-description">Doanh nghiệp chỉ xem được CV khi bạn đồng ý chia sẻ và tệp đã được xác nhận an toàn.</p></div></div>
    {current ? <><FileList ids={[current]} files={files} />
      <div className="button-row"><button type="button" className="secondary" disabled={busy} onClick={() => {
        if (window.confirm('Gỡ CV khỏi hồ sơ?')) void save(input => ({ ...input, cvFileId: null }), 'Đã gỡ CV khỏi hồ sơ.');
      }}>Gỡ CV khỏi hồ sơ</button></div></>
      : <p className="student-list-meta">Chưa có CV trong hồ sơ.</p>}
    <UploadForm label="Chọn tệp CV" action="Tải CV lên" busy={busy} onUpload={(file, key) => upload('CV', file, key)} />
    {fresh.length > 0 && <><h3>CV vừa tải lên</h3><ul className="student-file-list">{fresh.map(id => <li key={id}><FileRow id={id} files={files} />
      <button type="button" disabled={busy || !isClean(files, id)} onClick={() => void save(input => ({ ...input, cvFileId: id }), 'Đã gắn CV vào hồ sơ.')}>Dùng làm CV</button></li>)}</ul>
      <p className="field-hint">Bấm "Kiểm tra trạng thái tệp" sau ít phút; chỉ dùng làm CV khi tệp đã được xác nhận an toàn.</p></>}
  </section>;
}

function EvidenceTab({ view, evidence, files, busy, upload }: { view: SelfProfileView; evidence: string[]; files: Files; busy: boolean; upload: Upload }) {
  const usage = (id: string) => [...view.profile.projects.filter(item => item.evidenceFileIds.includes(id)).map(item => 'Dự án: ' + item.title),
    ...view.profile.certificates.filter(item => item.fileId === id).map(item => 'Chứng chỉ: ' + item.title)];
  return <section className="panel">
    <div className="section-heading"><div><p className="section-kicker">MINH CHỨNG</p><h2>Tệp minh chứng</h2>
      <p className="section-description">Minh chứng chỉ được lưu trong hồ sơ khi gắn vào một dự án hoặc chứng chỉ; tệp chưa gắn sẽ không còn hiện sau khi tải lại trang.</p></div></div>
    <UploadForm label="Chọn tệp minh chứng" action="Tải minh chứng lên" busy={busy} onUpload={(file, key) => upload('EVIDENCE', file, key)} />
    {evidence.length === 0 ? <p className="student-list-meta">Chưa có minh chứng.</p>
      : <ul className="student-file-list">{evidence.map(id => { const used = usage(id);
        return <li key={id}><FileRow id={id} files={files} /><span className="student-list-meta">{used.length ? used.join(' · ') : 'Chưa gắn vào dự án hoặc chứng chỉ'}</span></li>; })}</ul>}
  </section>;
}
