import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import { ApiError, type ApiFieldError } from '../../../api';
import { Field, Notice } from '../../../components';
import { prepareSave, saveDemand, type SaveAttempt } from '../api';
import type { Demand } from '../types';
import { demandInput, initialForm, skillRow, type FormData } from './form';
import { failureMessage } from './errors';
import { SkillFields } from './SkillFields';
import { SkillCatalogPicker } from './SkillCatalogPicker';
import { DemandReferenceFields } from './DemandReferenceFields';

export function DemandEditor({ organizationId, existing, onBusy, onUnconfirmed, onClose, onSaved }: {
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
        && ['FEATURE_DISABLED', 'CONTRACT_PENDING_SIGNOFF', 'CONTRACT_NOT_APPROVED', 'CONTRACT_NOT_SIGNED_OFF', 'CONTRACT_NOT_ACCEPTED', 'DEPENDENCY_UNAVAILABLE'].includes(failure.errorCode || '');
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
  return <section className="panel cooperation-editor" aria-labelledby={prefix + '-title'}>
    <div className="workflow-section-header">
      <div>
        <h2 id={prefix + '-title'} tabIndex={-1} ref={titleRef}>{existing ? 'Sửa bản nháp nhu cầu' : 'Tạo bản nháp nhu cầu'}</h2>
      </div>
    </div>
    <form ref={formRef} onSubmit={event => void submit(event)} aria-busy={busy}>
      <div className="bm01-two-col-grid">
        <div className="bm01-col-left">
          <fieldset disabled={locked} className="cooperation-fields"><legend>Thông tin nhu cầu</legend>
            <DemandReferenceFields org={organizationId} form={form} existing={!!existing} fieldError={fieldError}
              change={values => setForm(previous => ({ ...previous, ...values }))} />
            <div className="cooperation-form-grid">
              <Field error={fieldError('title')} label="Tên nhu cầu" value={form.title} onChange={value => set('title', value)} maxLength={200} />
              <Field error={fieldError('quantity')} label="Số lượng vị trí" value={form.quantity} onChange={value => set('quantity', value)} type="number" min={1} max={5000} step={1} />
              <Field error={fieldError('deadline')} label="Hạn tuyển" value={form.deadline} onChange={value => set('deadline', value)} type="datetime-local" step={1} />
              <Field error={fieldError('location')} label="Địa điểm (không bắt buộc)" value={form.location} onChange={value => set('location', value)} required={false} maxLength={500} />
            </div>
            <label htmlFor={prefix + '-description'}>Mô tả công việc</label>
            <textarea aria-invalid={!!fieldError('description')} aria-describedby={prefix + '-description-error'} id={prefix + '-description'} value={form.description} onChange={event => set('description', event.target.value)} required maxLength={10000} rows={4} />
            <p id={prefix + '-description-error'} className="field-error">{fieldError('description')}</p>
            <label htmlFor={prefix + '-benefits'}>Quyền lợi (không bắt buộc)</label>
            <textarea aria-invalid={!!fieldError('benefits')} aria-describedby={prefix + '-benefits-error'} id={prefix + '-benefits'} value={form.benefits} onChange={event => set('benefits', event.target.value)} maxLength={2000} rows={3} />
            <p id={prefix + '-benefits-error'} className="field-error">{fieldError('benefits')}</p>
          </fieldset>
        </div>
        <div className="bm01-col-right">
          <div className="bm01-rule-note" role="note">
            Kỹ năng bắt buộc cần đạt đầy đủ. Kỹ năng ưu tiên giúp xếp hạng ứng viên. Mỗi kỹ năng chỉ chọn trong một nhóm.
          </div>
          <fieldset disabled={locked} className="cooperation-fields"><legend>Tiêu chí bắt buộc</legend>
            <SkillCatalogPicker selected={[...form.required, ...form.preferred].map(row => row.skillCode.trim())}
              onVersion={version => set('taxonomyVersion', version)} onPick={(skill, group, version) => setForm(previous => {
                if ([...previous.required, ...previous.preferred].some(row => row.skillCode.trim() === skill.skillCode)) return previous;
                return { ...previous, taxonomyVersion: version,
                  [group]: [...previous[group], skillRow({ skillCode: skill.skillCode, minLevel: null, minYearsPractice: null })] };
              })} />
            <Field error={fieldError('required.majorCodes')} label="Mã ngành chính thức (không bắt buộc)" value={form.majorCodes} onChange={value => set('majorCodes', value)} required={false}
              hint="Phân cách bằng dấu phẩy. Để trống nghĩa là không giới hạn ngành; không nhập tên ngành thay cho mã." />
            <div className="cooperation-form-grid">
              <Field error={fieldError('required.dateRange')} label="Ngày bắt đầu thực tập" value={form.startDate} onChange={value => set('startDate', value)} type="date" />
              <Field error={fieldError('required.dateRange.endDate')} label="Ngày kết thúc thực tập" value={form.endDate} onChange={value => set('endDate', value)} type="date" min={form.startDate || undefined} />
            </div>
            <SkillFields error={fieldError('required.skills')} label="Bắt buộc" rows={form.required} change={rows => set('required', rows)} />
          </fieldset>
          <fieldset disabled={locked} className="cooperation-fields"><legend>Tiêu chí ưu tiên</legend>
            <SkillFields error={fieldError('preferred')} label="Ưu tiên" rows={form.preferred} change={rows => set('preferred', rows)} />
          </fieldset>
        </div>
      </div>
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
