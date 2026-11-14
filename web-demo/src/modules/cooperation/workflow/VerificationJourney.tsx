import { states } from './types';

export function VerificationJourney({ state }: { state?: string }) {
  const current = state === 'DRAFT' || !state ? 0 : state === 'SUBMITTED' ? 1 : 2;
  return <section className="verification-journey" aria-label="Tiến trình xác minh theo trường">
    <p>Bước hiện tại: <span className={'status-pill pill-' + (state || 'draft').toLowerCase()}>{state ? states[state] || state : 'Chuẩn bị hồ sơ'}</span></p>
    <ol className="t5-process-steps">{['Chuẩn bị hồ sơ', 'Gửi xét duyệt', 'Duyệt / Bổ sung / Từ chối'].map((label, index) => <li key={label} aria-current={current === index ? 'step' : undefined}><span aria-hidden="true">{index + 1}</span>{label}</li>)}</ol>
    <p className="field-hint">Xác minh chỉ có hiệu lực tại trường nhận hồ sơ. Kết quả phê duyệt của một trường không áp dụng cho trường khác.</p>
  </section>;
}
