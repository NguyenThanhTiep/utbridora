import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { ApiError, type Grant, type ApiFieldError } from '../../api';
import { EmptyState, Field, Icon, LoadingState, Notice } from '../../components';
import { listDemands, prepareSave, saveDemand, type SaveAttempt } from './api';
import type { Demand, DemandInput, DemandPage, SkillCriterion } from './types';
import './styles.css';
import { canLeaveDemand } from './navigation';

export const canAuthorDemands = (grant: Grant) => grant.role === 'ENTERPRISE'
  && !!grant.organizationId && grant.permissions.includes('cooperation:enterprise:create');

function failureMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : 'Không thể xử lý yêu cầu.';
  const codes: Record<string, string> = {
    CONTRACT_PENDING_SIGNOFF: 'Chức năng nhu cầu chưa được bật trong môi trường này. Bạn có thể chuẩn bị nội dung; liên hệ người phụ trách để bật chức năng.',
    CONTRACT_NOT_APPROVED: 'Chức năng nhu cầu chưa được bật. Vui lòng liên hệ người phụ trách để hoàn tất cấu hình.',
    CONTRACT_NOT_SIGNED_OFF: 'Chức năng nhu cầu chưa được bật. Vui lòng liên hệ người phụ trách để hoàn tất cấu hình.',
    DEPENDENCY_UNAVAILABLE: 'Chưa thể xác minh danh mục hoặc kỳ thực tập. Dữ liệu nhập vẫn được giữ; vui lòng thử lại sau.',
    INVALID_CATALOG_REFERENCE: 'Mã ngành, kỹ năng hoặc phiên bản danh mục không hợp lệ. Hãy dùng mã chính thức do người phụ trách cung cấp.',
    DEMAND_LOCKED: 'Nhu cầu đã khóa và không còn được sửa bản nháp. Tải lại danh sách để xem trạng thái mới.',
    REQUEST_IN_PROGRESS: 'Yêu cầu trước đang được xử lý. Chờ một chút rồi thử lại để nhận đúng kết quả.',
    VERSION_CONFLICT: 'Nhu cầu đã thay đổi. Tải lại danh sách rồi mở lại bản nháp trước khi sửa.'
  };
  const fallback = error.status === 503 ? 'Chức năng hoặc dịch vụ danh mục chưa sẵn sàng. Dữ liệu nhập vẫn được giữ để thử lại.'
    : error.status === 422 ? 'Thông tin hoặc tiêu chí chưa hợp lệ. Kiểm tra mã danh mục, kỳ, ngày và các ngưỡng kỹ năng.'
    : error.status === 409 ? 'Dữ liệu đã thay đổi hoặc nhu cầu đang khóa. Tải lại danh sách trước khi tiếp tục.' : error.message;
  return (codes[error.errorCode || ''] || fallback) + (error.requestId ? ` Mã hỗ trợ: ${error.requestId}.` : '');
}

export function DemandWorkspace({ grants }: { grants: Grant[] }) {
  const organizations = [...new Set(grants.filter(canAuthorDemands).map(grant => grant.organizationId!))];
  const [selected, setSelected] = useState(organizations[0] || '');
  const scope = organizations.includes(selected) ? selected : organizations[0] || '';
  const selectId = useId();
  if (!scope) return <section className="panel"><EmptyState icon="lock" title="Chưa có quyền quản lý nhu cầu"
    description="Bạn cần vai trò doanh nghiệp và quyền tạo nhu cầu trong tổ chức được cấp." /></section>;
  return <div className="cooperation-workspace stack">
    <section className="panel cooperation-scope">
      <label htmlFor={selectId}>Doanh nghiệp đang làm việc</label>
      <select id={selectId} value={scope} onChange={event => { if (canLeaveDemand()) setSelected(event.target.value); }}>
        {organizations.map(id => <option key={id} value={id}>{id}</option>)}
      </select>
      <p className="field-hint">Hiển thị mã tổ chức được cấp quyền. Đổi doanh nghiệp sẽ đóng bản nhập và tải lại danh sách.</p>
    </section>
    {/* A fresh component per organization discards its editor, list and pending request results. */}
    <ScopedDemands key={scope} organizationId={scope} />
  </div>;
}

function ScopedDemands({ organizationId }: { organizationId: string }) {
  const [pageIndex, setPageIndex] = useState(0), [refresh, setRefresh] = useState(0);
  const [page, setPage] = useState<DemandPage | null>(null), [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(''), [success, setSuccess] = useState('');
  const [editor, setEditor] = useState<{ record: Demand | null; key: number } | null>(null);
  const [saving, setSaving] = useState(false), [unconfirmed, setUnconfirmed] = useState(false);
  const nextEditor = useRef(0);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setLoading(true); setListError(''); setPage(null);
    void listDemands(organizationId, pageIndex, controller.signal).then(result => {
      if (active) setPage(result);
    }).catch(error => {
      if (active) setListError(failureMessage(error));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [organizationId, pageIndex, refresh]);

  const open = (record: Demand | null) => {
    if (!canLeaveDemand()) return;
    setSuccess(''); setUnconfirmed(false);
    setEditor({ record, key: ++nextEditor.current });
  };
  const blocked = saving || unconfirmed;
  return <>
    <section className="panel">
      <div className="section-heading cooperation-heading"><div><h2>Danh sách nhu cầu</h2>
        <p className="section-description">Lưu bản nháp và tiêu chí tuyển thực tập. Bản nháp chưa được gửi hợp tác.</p></div>
        <div className="button-row">
          <button className="secondary" disabled={loading || blocked} onClick={() => setRefresh(value => value + 1)}><Icon name="refresh" size={16} />Tải lại</button>
          <button disabled={blocked} onClick={() => open(null)}><Icon name="plus" size={16} />Tạo nhu cầu</button>
        </div>
      </div>
      <Notice message={success} tone="success" />
      <Notice message={listError} tone="error" />
      {loading ? <LoadingState /> : page && page.content.length === 0 ? <EmptyState icon="briefcase" title="Chưa có nhu cầu"
        description="Tạo bản nháp đầu tiên cho doanh nghiệp này." /> : page && <>
        <ul className="cooperation-demand-list" aria-label="Các nhu cầu của doanh nghiệp">
          {page.content.map(record => <li key={record.demandId}>
            <div className="cooperation-demand-title"><h3>{record.data.title}</h3>
              <span className="cooperation-state">{record.state === 'DRAFT' ? 'Bản nháp' : 'Đã sẵn sàng'}</span></div>
            <p className="section-description">{record.data.quantity} vị trí · Phiên bản {record.version} · Tiêu chí v{record.criteriaVersion}</p>
            <p className="field-hint">{record.validationState === 'VALID' ? 'Tiêu chí hợp lệ' : 'Tiêu chí cần kiểm tra'} · Mã nhu cầu: {record.demandId}</p>
            <button className="secondary" disabled={blocked || record.state !== 'DRAFT' || !!record.softDeletedAt || !!record.cooperationId}
              onClick={() => open(record)}>Sửa bản nháp<span className="cooperation-sr-only"> {record.data.title}</span></button>
          </li>)}
        </ul>
        <div className="pagination"><span className="pagination-summary">{page.totalElements} kết quả · Trang {page.page + 1}/{Math.max(1, page.totalPages)}</span>
          <div className="pagination-buttons">
            <button className="secondary" disabled={blocked || page.page === 0} onClick={() => setPageIndex(page.page - 1)}>Trước</button>
            <button className="secondary" disabled={blocked || page.page + 1 >= page.totalPages} onClick={() => setPageIndex(page.page + 1)}>Sau</button>
          </div>
        </div>
      </>}
    </section>
    {editor && <DemandEditor key={editor.key} organizationId={organizationId} existing={editor.record}
      onBusy={setSaving} onUnconfirmed={setUnconfirmed} onClose={() => { if (canLeaveDemand()) setEditor(null); }} onSaved={record => {
        setEditor(null); setUnconfirmed(false); setSuccess(`Đã lưu bản nháp “${record.data.title}”, phiên bản ${record.version}.`);
        setPageIndex(0); setRefresh(value => value + 1);
      }} />}
  </>;
}

type SkillRow = { id: string; skillCode: string; minLevel: string; minYearsPractice: string };
type FormData = {
  schoolOrgId: string; termId: string; title: string; quantity: string; description: string;
  location: string; benefits: string; deadline: string; majorCodes: string; startDate: string; endDate: string;
  academicCatalogVersion: string; taxonomyVersion: string; required: SkillRow[]; preferred: SkillRow[];
};
const skillRow = (skill?: SkillCriterion): SkillRow => ({ id: crypto.randomUUID(), skillCode: skill?.skillCode || '',
  minLevel: skill?.minLevel?.toString() || '', minYearsPractice: skill?.minYearsPractice?.toString() || '' });
function localDateTime(value: string) {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
function initialForm(existing: Demand | null): FormData {
  const data = existing?.data;
  return { schoolOrgId: data?.schoolOrgId || '', termId: data?.termId || '', title: data?.title || '', quantity: data?.quantity.toString() || '1',
    description: data?.description || '', location: data?.location || '', benefits: data?.benefits || '',
    deadline: data ? localDateTime(data.deadline) : '', majorCodes: data?.required.majorCodes.join(', ') || '',
    startDate: data?.required.dateRange.startDate || '', endDate: data?.required.dateRange.endDate || '',
    academicCatalogVersion: data?.academicCatalogVersion || '', taxonomyVersion: data?.taxonomyVersion || '',
    required: data?.required.skills.map(skillRow) || [], preferred: data?.preferred.map(skillRow) || [] };
}
function demandInput(form: FormData, existing: Demand | null): DemandInput {
  const toSkills = (rows: SkillRow[]) => rows.map(row => ({ skillCode: row.skillCode.trim(),
    minLevel: row.minLevel === '' ? null : Number(row.minLevel),
    minYearsPractice: row.minYearsPractice === '' ? null : Number(row.minYearsPractice) }));
  const required = toSkills(form.required), preferred = toSkills(form.preferred);
  const codes = [...required, ...preferred].map(skill => skill.skillCode);
  if (codes.some(code => !code)) throw new Error('Mã kỹ năng không được để trống hoặc chỉ có khoảng trắng.');
  if (new Set(codes).size !== codes.length) throw new Error('Mỗi mã kỹ năng chỉ xuất hiện một lần trong cả phần Bắt buộc và Ưu tiên.');
  if (form.endDate < form.startDate) throw new Error('Ngày kết thúc thực tập phải bằng hoặc sau ngày bắt đầu.');
  if (!form.title.trim() || !form.description.trim()) throw new Error('Tên nhu cầu và mô tả không được chỉ có khoảng trắng.');
  if (!form.academicCatalogVersion.trim() || !form.taxonomyVersion.trim()) throw new Error('Cần phiên bản danh mục học vụ và kỹ năng chính thức.');
  return { schoolOrgId: form.schoolOrgId.trim(), termId: form.termId.trim(), title: form.title.trim(), quantity: Number(form.quantity),
    description: form.description.trim(), location: form.location.trim() || null, benefits: form.benefits.trim() || null,
    deadline: existing && form.deadline === localDateTime(existing.data.deadline) ? existing.data.deadline : new Date(form.deadline).toISOString(),
    required: { majorCodes: form.majorCodes.split(/[,;]+/).map(code => code.trim()).filter(Boolean), skills: required,
      dateRange: { startDate: form.startDate, endDate: form.endDate } }, preferred,
    academicCatalogVersion: form.academicCatalogVersion.trim(), taxonomyVersion: form.taxonomyVersion.trim() };
}

function DemandEditor({ organizationId, existing, onBusy, onUnconfirmed, onClose, onSaved }: {
  organizationId: string; existing: Demand | null; onBusy: (value: boolean) => void;
  onUnconfirmed: (value: boolean) => void; onClose: () => void; onSaved: (record: Demand) => void;
}) {
  const [form, setForm] = useState(() => initialForm(existing));
  const [busy, setBusy] = useState(false), [retry, setRetry] = useState(false), [error, setError] = useState('');
  const [fieldErrors, setFieldErrors] = useState<string[]>([]);
  const [details, setDetails] = useState<ApiFieldError[]>([]);
  const formRef = useRef<HTMLFormElement>(null);
  const fieldError = (name: string) => details.filter(item => item.field === name || item.field.startsWith(name + '.')).map(item => item.message).join(' ');
  useEffect(() => {
    if (details.length) formRef.current?.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [details]);
  const attempt = useRef<SaveAttempt | null>(null), submitting = useRef(false), mounted = useRef(true);
  const titleRef = useRef<HTMLHeadingElement>(null);
  const prefix = useId();
  const initial = useRef(JSON.stringify(form));
  const guard = useRef({ dirty: false, blocked: false });
  guard.current = { dirty: JSON.stringify(form) !== initial.current, blocked: busy || retry };
  useEffect(() => {
    const leave = (event: Event) => {
      if (guard.current.blocked) {
        event.preventDefault();
        setError('Đang lưu hoặc chưa xác nhận được kết quả. Hãy hoàn tất lần lưu trước khi rời bản nhập.');
      } else if (guard.current.dirty && !window.confirm('Bạn có thay đổi chưa lưu. Rời bản nhập và bỏ các thay đổi?')) event.preventDefault();
    };
    const unload = (event: BeforeUnloadEvent) => {
      if (guard.current.dirty || guard.current.blocked) { event.preventDefault(); event.returnValue = ''; }
    };
    window.addEventListener('demand-before-leave', leave);
    window.addEventListener('beforeunload', unload);
    return () => { window.removeEventListener('demand-before-leave', leave); window.removeEventListener('beforeunload', unload); };
  }, []);
  useEffect(() => {
    mounted.current = true;
    titleRef.current?.focus({ preventScroll: true });
    return () => { mounted.current = false; };
  }, []);
  const set = <K extends keyof FormData>(key: K, value: FormData[K]) => setForm(previous => ({ ...previous, [key]: value }));
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting.current) return;
    setError(''); setFieldErrors([]); setDetails([]);
    try {
      if (!retry) {
        const next = prepareSave(demandInput(form, existing), existing);
        // A known dependency rejection may be retried unchanged, or corrected with a new key.
        if (!attempt.current || attempt.current.body !== next.body) attempt.current = next;
      }
    }
    catch (failure) { setError(failureMessage(failure)); return; }
    submitting.current = true; setBusy(true); onBusy(true);
    try {
      const record = await saveDemand(organizationId, attempt.current!);
      if (!mounted.current) return;
      attempt.current = null; setRetry(false); onUnconfirmed(false); onSaved(record);
    } catch (failure) {
      if (!mounted.current) return;
      const rejectedDependency = failure instanceof ApiError && failure.status === 503
        && ['CONTRACT_PENDING_SIGNOFF', 'CONTRACT_NOT_APPROVED', 'CONTRACT_NOT_SIGNED_OFF', 'CONTRACT_NOT_ACCEPTED', 'DEPENDENCY_UNAVAILABLE'].includes(failure.errorCode || '');
      const uncertain = !(failure instanceof ApiError)
        || (!rejectedDependency && failure.status >= 500) || failure.errorCode === 'REQUEST_IN_PROGRESS';
      if (!uncertain && !rejectedDependency) attempt.current = null;
      setRetry(uncertain); onUnconfirmed(uncertain); setError(failureMessage(failure));
      if (failure instanceof ApiError) {
        setDetails(failure.fieldErrors);
        setFieldErrors(failure.fieldErrors.map(item => `${item.field}: ${item.message}`));
      }
    } finally {
      submitting.current = false;
      if (mounted.current) { setBusy(false); onBusy(false); }
    }
  };
  const locked = busy || retry;
  const uuidPattern = '[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}';
  return <section className="panel cooperation-editor" aria-labelledby={prefix + '-title'}>
    <h2 id={prefix + '-title'} tabIndex={-1} ref={titleRef}>{existing ? 'Sửa bản nháp nhu cầu' : 'Tạo bản nháp nhu cầu'}</h2>
    <p className="section-description">Các mục bắt buộc cần được điền đầy đủ trước khi lưu. Mã trường, kỳ và danh mục phải do người phụ trách cung cấp.</p>
    <Notice message="Tra cứu danh mục chưa được tích hợp. Nhập mã chính thức và phiên bản được cung cấp; hệ thống sẽ kiểm tra trước khi lưu. Nếu dịch vụ danh mục chưa sẵn sàng, bản nháp chưa thể lưu." />
    <form ref={formRef} onSubmit={event => void submit(event)} aria-busy={busy}>
      <fieldset disabled={locked} className="cooperation-fields"><legend>Thông tin nhu cầu</legend>
        <div className="cooperation-form-grid">
          <Field error={fieldError('schoolOrgId')} label="Mã trường (UUID)" value={form.schoolOrgId} onChange={value => set('schoolOrgId', value)} pattern={uuidPattern} disabled={!!existing} />
          <Field error={fieldError('termId')} label="Mã kỳ thực tập (UUID)" value={form.termId} onChange={value => set('termId', value)} pattern={uuidPattern} disabled={!!existing} />
          <Field error={fieldError('title')} label="Tên nhu cầu" value={form.title} onChange={value => set('title', value)} maxLength={200} />
          <Field error={fieldError('quantity')} label="Số lượng vị trí" value={form.quantity} onChange={value => set('quantity', value)} type="number" min={1} max={5000} step={1} />
          <Field error={fieldError('deadline')} label="Hạn tuyển" value={form.deadline} onChange={value => set('deadline', value)} type="datetime-local" step={1} hint="Theo múi giờ trên thiết bị; được lưu thành thời điểm UTC." />
          <Field error={fieldError('location')} label="Địa điểm (không bắt buộc)" value={form.location} onChange={value => set('location', value)} required={false} maxLength={500} />
        </div>
        <label htmlFor={prefix + '-description'}>Mô tả công việc</label>
        <textarea aria-invalid={!!fieldError('description')} aria-describedby={prefix + '-description-error'} id={prefix + '-description'} value={form.description} onChange={event => set('description', event.target.value)} required maxLength={10000} rows={4} />
        <p id={prefix + '-description-error'} className="field-error">{fieldError('description')}</p>
        <label htmlFor={prefix + '-benefits'}>Quyền lợi (không bắt buộc)</label>
        <textarea aria-invalid={!!fieldError('benefits')} aria-describedby={prefix + '-benefits-error'} id={prefix + '-benefits'} value={form.benefits} onChange={event => set('benefits', event.target.value)} maxLength={2000} rows={3} />
        <p id={prefix + '-benefits-error'} className="field-error">{fieldError('benefits')}</p>
      </fieldset>
      <fieldset disabled={locked} className="cooperation-fields"><legend>Tiêu chí bắt buộc</legend>
        <Field error={fieldError('required.majorCodes')} label="Mã ngành chính thức (không bắt buộc)" value={form.majorCodes} onChange={value => set('majorCodes', value)} required={false}
          hint="Phân cách bằng dấu phẩy. Để trống nghĩa là không giới hạn ngành; không nhập tên ngành thay cho mã." />
        <div className="cooperation-form-grid">
          <Field error={fieldError('required.dateRange')} label="Ngày bắt đầu thực tập" value={form.startDate} onChange={value => set('startDate', value)} type="date" />
          <Field label="Ngày kết thúc thực tập" value={form.endDate} onChange={value => set('endDate', value)} type="date" min={form.startDate || undefined} />
        </div>
        <SkillFields error={fieldError('required.skills')} label="Bắt buộc" rows={form.required} change={rows => set('required', rows)} />
      </fieldset>
      <fieldset disabled={locked} className="cooperation-fields"><legend>Tiêu chí ưu tiên</legend>
        <SkillFields error={fieldError('preferred')} label="Ưu tiên" rows={form.preferred} change={rows => set('preferred', rows)} />
      </fieldset>
      <fieldset disabled={locked} className="cooperation-fields"><legend>Phiên bản danh mục</legend>
        <div className="cooperation-form-grid">
          <Field error={fieldError('academicCatalogVersion')} label="Phiên bản danh mục học vụ" value={form.academicCatalogVersion} onChange={value => set('academicCatalogVersion', value)} maxLength={64} />
          <Field error={fieldError('taxonomyVersion')} label="Phiên bản danh mục kỹ năng" value={form.taxonomyVersion} onChange={value => set('taxonomyVersion', value)} maxLength={64} />
        </div>
      </fieldset>
      <Notice message={error} tone="error" />
      {fieldErrors.length > 0 && <ul className="cooperation-field-errors" aria-label="Chi tiết thông tin chưa hợp lệ">{fieldErrors.map((message, index) => <li key={index}>{message}</li>)}</ul>}
      {retry && <Notice message="Chưa xác nhận được kết quả lưu. Thử lưu lại với cùng nội dung để tránh tạo trùng; nội dung tạm khóa trong lúc chờ xác nhận." />}
      <div className="button-row cooperation-actions">
        <button disabled={busy}>{busy ? <><span className="spinner spinner-light" />Đang lưu…</> : retry ? 'Thử lưu lại' : 'Lưu bản nháp'}</button>
        <button className="secondary" type="button" disabled={locked} onClick={onClose}>Đóng bản nhập</button>
      </div>
    </form>
  </section>;
}

function SkillFields({ label, rows, change, error }: { label: string; rows: SkillRow[]; change: (rows: SkillRow[]) => void; error?: string }) {
  const update = (id: string, key: keyof SkillRow, value: string) => change(rows.map(row => row.id === id ? { ...row, [key]: value } : row));
  return <div className="cooperation-skills">
    {error && <p className="field-error" tabIndex={-1} aria-invalid="true">{error}</p>}
    <p className="field-hint">{label === 'Bắt buộc' ? 'Tất cả kỹ năng bên dưới đều bắt buộc. Để trống danh sách nếu không yêu cầu kỹ năng.' : 'Kỹ năng ưu tiên hỗ trợ xếp hạng, không thay thế tiêu chí bắt buộc.'} Để trống ngưỡng nếu chỉ cần có kỹ năng.</p>
    {rows.map((row, index) => <div className="cooperation-skill-row" key={row.id}>
      <Field label={`${label}: mã kỹ năng ${index + 1}`} value={row.skillCode} onChange={value => update(row.id, 'skillCode', value)} maxLength={64} />
      <Field label={`${label}: cấp độ tối thiểu ${index + 1}`} value={row.minLevel} onChange={value => update(row.id, 'minLevel', value)} type="number" min={1} max={5} step={1} required={false} />
      <Field label={`${label}: số năm tối thiểu ${index + 1}`} value={row.minYearsPractice} onChange={value => update(row.id, 'minYearsPractice', value)} type="number" min={0} step="any" required={false} />
      <button className="secondary" type="button" aria-label={`Xóa kỹ năng ${label.toLowerCase()} ${index + 1}`} onClick={() => change(rows.filter(item => item.id !== row.id))}><Icon name="close" size={16} /></button>
    </div>)}
    <button type="button" className="secondary" onClick={() => change([...rows, skillRow()])}><Icon name="plus" size={16} />Thêm kỹ năng {label.toLowerCase()}</button>
  </div>;
}
