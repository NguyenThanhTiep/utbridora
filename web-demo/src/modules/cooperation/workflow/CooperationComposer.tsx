import { useState } from 'react';
import type { Demand } from '../types';
import { useSchoolNames } from '../portal/useSchoolNames';
import { FeaturePending } from '../ui/FeaturePending';

export function CooperationComposer({ org, demands, busy, close, save, submit }: {
  org: string; demands: Demand[]; busy: boolean; close: () => void; save: (demand: Demand) => void; submit: (demand: Demand) => void;
}) {
  const [selected, setSelected] = useState('');
  const schools = useSchoolNames(org);
  const rows = demands.filter(demand => !demand.cooperationId && demand.state === 'DRAFT' && !demand.softDeletedAt);
  const demand = rows.find(row => row.demandId === selected) || rows[0];
  return <section className="workflow-review-form portal-cooperation-composer" aria-label="Soạn yêu cầu hợp tác">
    <h3>Soạn yêu cầu hợp tác</h3>
    <fieldset disabled={busy}><div className="cooperation-form-grid">
      <label>Nhu cầu gửi hợp tác<select value={demand?.demandId || ''} onChange={event => setSelected(event.target.value)} disabled={!rows.length}>
        {!rows.length && <option value="">Chưa có nhu cầu nháp sẵn sàng</option>}
        {rows.map(row => <option key={row.demandId} value={row.demandId}>{row.data.title}</option>)}
      </select></label>
      <label>Trường tiếp nhận<input readOnly value={demand
        ? schools[demand.data.schoolOrgId] || 'Trường đã chọn trong nhu cầu'
        : ''} placeholder="Chọn nhu cầu trước" /></label>
    </div></fieldset>
    <div className="portal-upcoming-fields">
      <FeaturePending>Nội dung riêng và đầu mối phụ trách sẽ được bổ sung sau. Hiện hồ sơ được gửi theo nội dung nhu cầu đã chọn.</FeaturePending>
      <fieldset disabled><div className="cooperation-form-grid"><label>Nội dung hợp tác<textarea rows={2} placeholder="Chưa sẵn sàng" /></label>
        <label>Đầu mối phụ trách<input placeholder="Chưa sẵn sàng" /></label></div></fieldset>
    </div>
    {demand && <p className="field-hint">{demand.data.quantity} vị trí · {demand.data.required.dateRange.startDate} – {demand.data.required.dateRange.endDate}</p>}
    <div className="button-row"><button className="secondary" disabled={busy} onClick={close}>Đóng soạn hợp tác</button>
      <button className="secondary" disabled={busy || !demand} onClick={() => { if (demand) save(demand); }}>Lưu nháp</button>
      <button disabled={busy || !demand} onClick={() => { if (demand) submit(demand); }}>Kiểm tra và gửi</button>
    </div>
  </section>;
}
