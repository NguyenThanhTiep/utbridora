import { useEffect, useRef, useState } from 'react';
import { Icon, Notice } from '../../../components';
import { FeaturePending } from '../ui/FeaturePending';
import { readAll } from '../portal/data';
import { workflowError } from '../proposals/api';
import { canLeaveDemand } from '../navigation';
import type { VerificationInput, VerificationRecord } from '../WorkflowViews';
import { VerificationJourney } from './VerificationJourney';

export function VerificationEditor({ org, existing, busy, onCancel, save }: {
  org: string; existing: VerificationRecord | null; busy: boolean; onCancel: () => void;
  save: (data: VerificationInput) => Promise<unknown>;
}) {
  const [data, setData] = useState<VerificationInput>(existing?.data || {
    schoolOrgId: '', previousVerificationId: null, registrationCode: '', registeredName: '', address: '',
    contactName: '', contactEmail: '', contactPhone: '', evidenceFileIds: [],
  });
  const original = useRef(JSON.stringify(data));
  const [schools, setSchools] = useState<{ schoolOrgId: string; name: string }[]>([]);
  const [loading, setLoading] = useState(true), [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    void readAll<{ schoolOrgId: string; name: string }>(org, '/enterprise/demand-references/schools', controller.signal)
      .then(value => { if (!controller.signal.aborted) setSchools(value); })
      .catch(reason => { if (!controller.signal.aborted) setError(workflowError(reason)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [org]);
  useEffect(() => {
    const dirty = original.current !== JSON.stringify(data);
    const leave = (event: Event) => {
      if (!event.defaultPrevented && dirty && !window.confirm('Hồ sơ xác minh chưa được lưu. Bạn muốn bỏ các thay đổi?')) event.preventDefault();
    };
    const unload = (event: BeforeUnloadEvent) => { if (dirty) { event.preventDefault();event.returnValue = ''; } };
    window.addEventListener('demand-before-leave', leave);window.addEventListener('beforeunload', unload);
    return () => { window.removeEventListener('demand-before-leave', leave);window.removeEventListener('beforeunload', unload); };
  }, [data]);
  const fields = [
    { key: 'registrationCode', label: 'Mã đăng ký doanh nghiệp', max: 64 },
    { key: 'registeredName', label: 'Tên đăng ký', max: 200 },
    { key: 'address', label: 'Địa chỉ', max: 500 },
    { key: 'contactName', label: 'Người liên hệ', max: 150 },
    { key: 'contactEmail', label: 'Email liên hệ', max: 254 },
    { key: 'contactPhone', label: 'Điện thoại liên hệ', max: 20 },
  ] as const;
  return <form className="workflow-review-form" onSubmit={event => { event.preventDefault();void save(data); }}>
    <h3>{existing ? 'Bổ sung thông tin xác minh' : 'Tạo hồ sơ xác minh'}</h3>
    <VerificationJourney state={existing?.state} />
    <Notice tone="error" message={error} />
    <fieldset disabled={busy}>
      <label>Trường nhận hồ sơ<select required value={data.schoolOrgId} disabled={!!existing || loading || !schools.length}
        onChange={event => setData(old => ({ ...old, schoolOrgId: event.target.value }))}>
        <option value="">{loading ? 'Đang tải trường…' : 'Chọn trường'}</option>
        {data.schoolOrgId && !schools.some(school => school.schoolOrgId === data.schoolOrgId)
          && <option value={data.schoolOrgId}>Trường trong hồ sơ · {data.schoolOrgId}</option>}
        {schools.map(school => <option key={school.schoolOrgId} value={school.schoolOrgId}>{school.name}</option>)}
      </select></label>
      <div className="cooperation-form-grid">{fields.map(field => <label key={field.key}>{field.label}
        <input required maxLength={field.max} minLength={field.key === 'contactPhone' ? 8 : undefined}
          type={field.key === 'contactEmail' ? 'email' : field.key === 'contactPhone' ? 'tel' : 'text'}
          value={data[field.key]} onChange={event => setData(old => ({ ...old, [field.key]: event.target.value }))} />
      </label>)}</div>
      <section className="portal-evidence"><div className="portal-evidence-heading"><Icon name="layers" /><h4>Tài liệu minh chứng</h4></div>
        {data.evidenceFileIds.length > 0 && <ul>{data.evidenceFileIds.map((file, index) => <li key={file}>Tài liệu đã liên kết {index + 1} <code>{file.slice(-8)}</code></li>)}</ul>}
        <div className="portal-upload-placeholder"><Icon name="plus" size={24} /><span>Giấy phép kinh doanh và tài liệu liên quan</span>
          <button type="button" className="secondary" disabled>Chọn tài liệu</button></div>
        <FeaturePending>Tải tài liệu chưa sẵn sàng. Bạn có thể lưu nháp và bổ sung minh chứng trước khi gửi xét duyệt.</FeaturePending>
      </section>
      <div className="button-row"><button>Lưu hồ sơ xác minh</button>
        <button type="button" className="secondary" onClick={() => { if (canLeaveDemand()) onCancel(); }}>Hủy</button></div>
    </fieldset>
  </form>;
}
