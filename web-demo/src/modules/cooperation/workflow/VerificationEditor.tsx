import { useEffect, useRef, useState } from 'react';
import { Notice } from '../../../components';
import { readAll } from '../portal/data';
import { workflowError } from '../proposals/api';
import { canLeaveDemand } from '../navigation';
import { newIdempotencyKey } from '../../student/api';
import type { VerificationInput, VerificationRecord } from '../WorkflowViews';
import { VerificationJourney } from './VerificationJourney';
import { TaxLookupField } from './TaxLookupField';
import { states } from './types';
import { VerificationEvidence, type QueuedEvidenceFile, type VerificationEvidenceSeed } from './VerificationEvidence';

export function VerificationEditor({ org, existing, busy, onCancel, save, submitError = '', openExisting, schoolScope = '', evidenceSeed, initialFileIds = [] }: {
  org: string; existing: VerificationRecord | null; busy: boolean; onCancel: () => void;
  save: (data: VerificationInput, queuedFiles?: QueuedEvidenceFile[]) => Promise<unknown>;
  submitError?: string; openExisting?: (record: VerificationRecord) => void;
  schoolScope?: string;
  evidenceSeed?: VerificationEvidenceSeed;
  initialFileIds?: string[];
}) {
  const [data, setData] = useState<VerificationInput>(() => {
    const base = existing?.data || {
      schoolOrgId: schoolScope, previousVerificationId: null, registrationCode: '', registeredName: '', address: '',
      contactName: '', contactEmail: '', contactPhone: '', evidenceFileIds: [],
    };
    return initialFileIds.length
      ? { ...base, evidenceFileIds: [...new Set([...base.evidenceFileIds, ...initialFileIds])] }
      : base;
  });
  const [queuedFiles, setQueuedFiles] = useState<QueuedEvidenceFile[]>([]);
  const original = useRef(JSON.stringify(existing?.data || {
    schoolOrgId: schoolScope, previousVerificationId: null, registrationCode: '', registeredName: '', address: '',
    contactName: '', contactEmail: '', contactPhone: '', evidenceFileIds: [],
  }));
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    heading.current?.closest('form')?.scrollIntoView({ block: 'start',
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  }, []);
  const [schools, setSchools] = useState<{ schoolOrgId: string; name: string }[]>([]);
  const [records, setRecords] = useState<VerificationRecord[]>([]);
  const [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [evidenceBusy, setEvidenceBusy] = useState(false);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setSchools([]); setRecords([]);
    void Promise.all([
      readAll<{ schoolOrgId: string; name: string }>(org, '/enterprise/demand-references/schools', controller.signal),
      existing ? Promise.resolve([] as VerificationRecord[]) : readAll<VerificationRecord>(org, '/enterprise/verifications', controller.signal),
    ])
      .then(([value, current]) => { if (!controller.signal.aborted) { setSchools(value); setRecords(current); } })
      .catch(reason => { if (!controller.signal.aborted) setError(workflowError(reason)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [org, existing?.verificationId]);
  const occupied = records.filter(record => ['DRAFT', 'SUBMITTED', 'NEEDS_CHANGES', 'APPROVED'].includes(record.state));
  const duplicate = existing ? undefined : occupied.find(record => record.data.schoolOrgId === data.schoolOrgId);
  useEffect(() => {
    const dirty = evidenceBusy || queuedFiles.length > 0 || original.current !== JSON.stringify(data);
    const leave = (event: Event) => {
      if (!event.defaultPrevented && dirty && !window.confirm('Hồ sơ xác minh chưa được lưu. Bạn muốn bỏ các thay đổi?')) event.preventDefault();
    };
    const unload = (event: BeforeUnloadEvent) => { if (dirty) { event.preventDefault();event.returnValue = ''; } };
    window.addEventListener('demand-before-leave', leave);window.addEventListener('beforeunload', unload);
    return () => { window.removeEventListener('demand-before-leave', leave);window.removeEventListener('beforeunload', unload); };
  }, [data, evidenceBusy, queuedFiles.length]);
  const fields = [
    { key: 'registeredName', label: 'Tên đăng ký', max: 200 },
    { key: 'address', label: 'Địa chỉ', max: 500 },
    { key: 'contactName', label: 'Người liên hệ', max: 150 },
    { key: 'contactEmail', label: 'Email liên hệ', max: 254 },
    { key: 'contactPhone', label: 'Điện thoại liên hệ', max: 20 },
  ] as const;
  return <form className="workflow-review-form enterprise-verification-editor" onSubmit={event => {
    event.preventDefault(); if (!busy && !loading && !error && !duplicate && !evidenceBusy) void save(data, queuedFiles);
  }}>
    <h3 ref={heading} tabIndex={-1}>{existing ? 'Bổ sung thông tin xác minh' : 'Tạo hồ sơ xác minh'}</h3>
    <VerificationJourney state={existing?.state} />
    <Notice tone="error" message={error} />
    <Notice tone="error" message={submitError} />
    <fieldset disabled={busy}>
      <label>Trường nhận hồ sơ<select aria-label="Trường nhận hồ sơ" required value={data.schoolOrgId} disabled={!!existing || !!schoolScope || loading || !schools.length}
        onChange={event => setData(old => ({ ...old, schoolOrgId: event.target.value }))}>
        <option value="">{loading ? 'Đang tải trường…' : 'Chọn trường'}</option>
        {data.schoolOrgId && !schools.some(school => school.schoolOrgId === data.schoolOrgId)
          && <option value={data.schoolOrgId}>Trường trong hồ sơ</option>}
        {schools.map(school => {
          const record = occupied.find(item => item.data.schoolOrgId === school.schoolOrgId);
          return <option key={school.schoolOrgId} value={school.schoolOrgId}>{school.name}{record ? ` · ${states[record.state] || record.state}` : ''}</option>;
        })}
      </select></label>
      {!existing && schoolScope && <p className="form-description">Trường nhận hồ sơ theo phạm vi đang xem. Chọn trường khác trên thanh phía trên nếu muốn đổi.</p>}
      {duplicate && <div className="tax-verdict tax-tone-warning" role="status">
        <strong>{duplicate.state === 'APPROVED' ? 'Doanh nghiệp đã được xác minh tại trường này.' : 'Doanh nghiệp đã có hồ sơ tại trường này.'}</strong>
        <p>Trạng thái hồ sơ hiện có: {states[duplicate.state] || duplicate.state}. Chọn trường khác để tạo hồ sơ mới.</p>
        {openExisting && <button type="button" className="secondary" onClick={() => { if (canLeaveDemand()) openExisting(duplicate); }}>Mở hồ sơ hiện có</button>}
      </div>}
      <TaxLookupField org={org} data={data} busy={busy} update={setData}>{fields.map(field => <label key={field.key}>{field.label}
        <input required maxLength={field.max} minLength={field.key === 'contactPhone' ? 8 : undefined}
          type={field.key === 'contactEmail' ? 'email' : field.key === 'contactPhone' ? 'tel' : 'text'}
          value={data[field.key]} onChange={event => setData(old => ({ ...old, [field.key]: event.target.value }))} />
      </label>)}</TaxLookupField>
      <VerificationEvidence key={org + ':' + (existing?.verificationId || 'new')} org={org} recordId={existing?.verificationId}
        data={data} disabled={busy} update={setData} onBusyChange={setEvidenceBusy}
        queuedFiles={existing ? [] : queuedFiles}
        onQueueAdd={file => setQueuedFiles(current => current.length >= 20 ? current : [...current, { id: newIdempotencyKey(), file, key: newIdempotencyKey() }])}
        onQueueRemove={index => setQueuedFiles(current => current.filter((_, itemIndex) => itemIndex !== index))}
        seed={evidenceSeed} />
      <div className="button-row"><button className="t5-btn-teal" disabled={busy || loading || !!error || !!duplicate || evidenceBusy}>Lưu hồ sơ xác minh</button>
        <button type="button" className="secondary" onClick={() => { if (canLeaveDemand()) onCancel(); }}>Hủy</button></div>
    </fieldset>
  </form>;
}
