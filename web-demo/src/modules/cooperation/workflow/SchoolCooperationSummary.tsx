import { Icon } from '../../../components';
import { fieldLabels, states, type Detail } from './types';
import { CooperationDescription } from './CooperationDescription';
import { CooperationSkillCriteria, SKILL_PREVIEW_COUNT } from './CooperationSkillCriteria';
import './cooperation-review.css';

const date = (value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) ? value.split('-').reverse().join('/') : value;

export function SchoolCooperationSummary({ detail, termName, close, busy = false }: { detail: Detail; termName?: string; close: () => void; busy?: boolean }) {
  const { demand, cooperation: c, feedback } = detail;
  const data = demand.data;
  const status = states[c.state] || c.state;
  const wideCriteria = data.required.skills.length > SKILL_PREVIEW_COUNT || data.required.majorCodes.length > SKILL_PREVIEW_COUNT || data.preferred.length > 0;
  const requiredCard = <CooperationSkillCriteria key="required" label="Tiêu chí bắt buộc" skills={data.required.skills} majorCodes={data.required.majorCodes} wide={wideCriteria} />;
  const timeCard = <section key="dates" className="school-coop-fact"><span className="school-coop-fact-icon" aria-hidden="true"><Icon name="calendar" size={18} /></span><h4>Thời gian thực tập</h4>
    <p className="school-coop-period"><time dateTime={data.required.dateRange.startDate}>{date(data.required.dateRange.startDate)}</time><span aria-hidden="true">→</span><time dateTime={data.required.dateRange.endDate}>{date(data.required.dateRange.endDate)}</time></p></section>;
  const messages: Record<string, string> = {
    SUBMITTED: 'Đề nghị đang chờ xét duyệt. Kiểm tra thông tin trước khi ra quyết định.',
    NEEDS_CHANGES: 'Đã yêu cầu bổ sung. Nội dung bên dưới là bản doanh nghiệp đã gửi gần nhất.',
    REJECTED: 'Đề nghị đã bị từ chối. Doanh nghiệp có thể điều chỉnh và gửi lại theo quy trình.',
  };
  return <div className={'school-coop-summary review-' + c.state.toLowerCase()}>
    <header className="school-coop-summary-header">
      <span className="school-coop-summary-icon" aria-hidden="true"><Icon name="briefcase" size={24} /></span>
      <div className="school-coop-summary-heading"><p className="school-coop-eyebrow">BM-02 · Đề nghị hợp tác</p><h3>{data.title}</h3>
        <p className="school-coop-summary-context">{termName && <><span>{termName}</span><span aria-hidden="true">·</span></>}<span>Lần gửi {c.submissionNo}</span></p></div>
      <div className="school-coop-summary-actions"><span className={`status-pill pill-${c.state.toLowerCase()}`}><span className="pill-dot" />{status}</span>
        <button type="button" className="secondary school-coop-close" disabled={busy} onClick={close}><Icon name="close" size={15} />Đóng chi tiết</button></div>
    </header>
    {c.state !== 'APPROVED' && <p className="school-coop-status-note" role="note"><Icon name="shield" size={17} /><span>{messages[c.state] || status}</span></p>}
    <CooperationDescription text={data.description} />
    <div className={'school-coop-facts' + (wideCriteria ? ' has-wide-criteria' : '')}>
      <section className="school-coop-fact"><span className="school-coop-fact-icon" aria-hidden="true"><Icon name="users" size={18} /></span><h4>Quy mô & Địa điểm</h4>
        <p className="school-coop-quantity">{data.quantity} vị trí</p><p>{data.location || 'Chưa ghi nhận địa điểm'}</p></section>
      {wideCriteria ? timeCard : requiredCard}
      {wideCriteria ? requiredCard : timeCard}
      {data.preferred.length > 0 && <CooperationSkillCriteria label="Tiêu chí ưu tiên" skills={data.preferred} wide />}
    </div>
    {feedback && <section className="school-coop-feedback" aria-label="Kết quả xét duyệt">
      <div className="school-coop-feedback-heading"><Icon name="shield" size={18} /><h4>Kết quả xét duyệt</h4></div>
      <dl><div><dt>Phản hồi</dt><dd>{feedback.reasonCode || status}</dd></div>
        <div><dt>Cần bổ sung</dt><dd>{feedback.missingFields.map(field => fieldLabels[field] || field).join(', ') || 'Không có'}</dd></div>
        <div><dt>Căn cứ xét duyệt</dt><dd>{feedback.decisionRef || '—'}</dd></div></dl>
    </section>}
  </div>;
}
