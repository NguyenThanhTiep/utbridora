import { useEffect, useRef, useState } from 'react';
import { Icon } from '../../../components';
import { formatDateDMY } from '../../../ui/dateFormat';
import type { Demand } from '../types';
import { useSchoolNames } from '../portal/useSchoolNames';

export function CooperationComposer({ org, demands, busy, close, save, submit }: {
  org: string; demands: Demand[]; busy: boolean; close: () => void; save: (demand: Demand) => void; submit: (demand: Demand) => void;
}) {
  const [selected, setSelected] = useState('');
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    heading.current?.closest('section')?.scrollIntoView({ block: 'start',
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  }, []);
  const schools = useSchoolNames(org);
  const rows = demands.filter(demand => !demand.cooperationId && demand.state === 'DRAFT' && !demand.softDeletedAt);
  const demand = rows.find(row => row.demandId === selected) || rows[0];
  return <section className="workflow-review-form portal-cooperation-composer" aria-label="Soạn yêu cầu hợp tác">
    <header className="enterprise-coop-composer-header"><span className="enterprise-coop-icon" aria-hidden="true"><Icon name="briefcase" size={21} /></span>
      <div><h3 ref={heading} tabIndex={-1}>Soạn yêu cầu hợp tác</h3><p>Chọn nhu cầu đã lưu để gửi đến nhà trường.</p></div>
    </header>
    <fieldset disabled={busy}><div className="cooperation-form-grid">
      <label>Nhu cầu gửi hợp tác<select value={demand?.demandId || ''} onChange={event => setSelected(event.target.value)} disabled={!rows.length}>
        {!rows.length && <option value="">Chưa có nhu cầu nháp sẵn sàng</option>}
        {rows.map(row => <option key={row.demandId} value={row.demandId}>{row.data.title}</option>)}
      </select></label>
      <label>Trường tiếp nhận<input readOnly value={demand
        ? schools[demand.data.schoolOrgId] || 'Trường đã chọn trong nhu cầu'
        : ''} placeholder="Chọn nhu cầu trước" /></label>
    </div></fieldset>
    {demand && <section className="enterprise-coop-preview" aria-label="Nhu cầu đã chọn"><h4>{demand.data.title}</h4>
      <dl><div><dt>Chỉ tiêu tuyển</dt><dd>{demand.data.quantity} vị trí</dd></div>
        <div><dt>Thời gian thực tập</dt><dd>{formatDateDMY(demand.data.required.dateRange.startDate)} – {formatDateDMY(demand.data.required.dateRange.endDate)}</dd></div>
        <div><dt>Tiêu chí kỹ năng</dt><dd>{demand.data.required.skills.length} bắt buộc · {demand.data.preferred.length} ưu tiên</dd></div></dl>
    </section>}
    <div className="t5-rule-note" role="note"><Icon name="shield" size={18} /><div><strong>Điều kiện gửi hợp tác</strong><p>Doanh nghiệp cần được xác minh trên hệ thống và được chính trường tiếp nhận phê duyệt hồ sơ xác minh.</p></div></div>
    <div className="button-row"><button className="secondary" disabled={busy} onClick={close}>Đóng soạn hợp tác</button>
      <button className="secondary" disabled={busy || !demand} onClick={() => { if (demand) save(demand); }}>Lưu nháp</button>
      <button className="t5-btn-teal" disabled={busy || !demand} onClick={() => { if (demand) submit(demand); }}>Kiểm tra và gửi</button>
    </div>
  </section>;
}
