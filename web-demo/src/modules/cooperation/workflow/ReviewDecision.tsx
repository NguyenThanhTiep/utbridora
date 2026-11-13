import { useState } from 'react';
import { fieldLabels } from './types';

export function ReviewDecision({ busy, fields, submit }: { busy: boolean; fields: string[]; submit: (data: { decision: string; reasonCode: string | null; decisionRef: string; missingFields: string[] }) => Promise<void> }) {
  const [decision, setDecision] = useState('APPROVE'), [reference, setReference] = useState(''), [missing, setMissing] = useState<string[]>([]);
  return <form className="workflow-review-form" onSubmit={e => {
    e.preventDefault();
    void submit({
      decision,
      reasonCode: decision === 'APPROVE' ? null : decision === 'REJECT' ? 'REVIEW_REJECTED' : 'ADDITIONAL_INFO_REQUIRED',
      decisionRef: reference,
      missingFields: decision === 'REQUEST_INFO' ? missing : [],
    });
  }}>
    <fieldset disabled={busy}>
      <legend>Ra quyết định xét duyệt</legend>
      <div className="cooperation-form-grid">
        <label>Kết quả xét duyệt
          <select value={decision} onChange={e => setDecision(e.target.value)}>
            <option value="APPROVE">Phê duyệt</option>
            <option value="REQUEST_INFO">Yêu cầu bổ sung</option>
            <option value="REJECT">Từ chối</option>
          </select>
        </label>
        <label>Căn cứ quyết định
          <input required maxLength={120} placeholder="VD: QD-HT-2026-01" value={reference} onChange={e => setReference(e.target.value)} />
        </label>
      </div>
      <div className="quick-ref-row">
        <span className="quick-ref-label">Gợi ý căn cứ:</span>
        <button type="button" className="quick-ref-chip" onClick={() => setReference('QD-PHEDUYET-2026')}>QD-PHEDUYET-2026</button>
        <button type="button" className="quick-ref-chip" onClick={() => setReference('TB-BOSUNG-2026')}>TB-BOSUNG-2026</button>
        <button type="button" className="quick-ref-chip" onClick={() => setReference('QD-TUCHOI-2026')}>QD-TUCHOI-2026</button>
      </div>
      {decision === 'REQUEST_INFO' && <fieldset className="workflow-missing-fields">
        <legend>Thông tin cần bổ sung</legend>
        <div className="workflow-checkbox-grid">
          {fields.map(f => <label key={f} className="workflow-checkbox-item">
            <input type="checkbox" checked={missing.includes(f)} onChange={e => setMissing(e.target.checked ? [...missing, f] : missing.filter(v => v !== f))} />
            {fieldLabels[f] || f}
          </label>)}
        </div>
      </fieldset>}
      <div className="button-row">
        <button disabled={decision === 'REQUEST_INFO' && missing.length === 0}>Lưu kết quả xét duyệt</button>
      </div>
    </fieldset>
  </form>;
}
