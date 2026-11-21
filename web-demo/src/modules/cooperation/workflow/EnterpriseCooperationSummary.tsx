import { useEffect, useRef } from 'react';
import { Icon } from '../../../components';
import { formatDateDMY } from '../../../ui/dateFormat';
import { states, fieldLabels, type Detail } from './types';
import { CooperationDescription } from './CooperationDescription';
import { CooperationSkillCriteria } from './CooperationSkillCriteria';
import './cooperation-review.css';

const messages: Record<string, string> = {
  DRAFT: 'Đề nghị đang ở bản nháp. Kiểm tra nội dung trước khi gửi đến nhà trường.',
  SUBMITTED: 'Đề nghị đã được gửi và đang chờ nhà trường xét duyệt.',
  NEEDS_CHANGES: 'Nhà trường yêu cầu bổ sung. Kiểm tra phản hồi và bổ sung theo yêu cầu trước khi gửi lại đề nghị.',
  APPROVED: 'Đề nghị đã được phê duyệt. Bạn có thể tiếp tục xem ứng viên và theo dõi tiếp nhận thực tập.',
  REJECTED: 'Đề nghị đã bị từ chối. Bạn có thể mở lại bản nháp để điều chỉnh theo phản hồi.',
};
const reasons: Record<string, string> = {
  ADDITIONAL_INFO_REQUIRED: 'Yêu cầu bổ sung thông tin', REVIEW_REJECTED: 'Nhà trường từ chối đề nghị',
};

export function EnterpriseCooperationSummary({ detail, schoolName, busy, close }: {
  detail: Detail; schoolName?: string; busy: boolean; close: () => void;
}) {
  const { demand, cooperation, feedback } = detail;
  const data = demand.data;
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    heading.current?.closest('article')?.scrollIntoView({ block: 'start',
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  }, []);
  return <div className={'enterprise-coop-summary review-' + cooperation.state.toLowerCase()}>
    <header className="enterprise-coop-detail-header"><span className="enterprise-coop-icon" aria-hidden="true"><Icon name="briefcase" size={24} /></span>
      <div className="enterprise-coop-detail-title"><p className="enterprise-coop-eyebrow">Đề nghị hợp tác</p><h3 ref={heading} tabIndex={-1}>{data.title}</h3>
        <p className="enterprise-coop-detail-context">{schoolName || 'Trường đối tác'}<span aria-hidden="true">·</span>{cooperation.submissionNo > 0 ? `Lần gửi ${cooperation.submissionNo}` : 'Chưa gửi hồ sơ'}</p>
      </div><div className="enterprise-coop-detail-actions"><span className={'status-pill pill-' + cooperation.state.toLowerCase()}><span className="pill-dot" />{states[cooperation.state] || cooperation.state}</span>
        <button type="button" className="secondary" disabled={busy} onClick={close}><Icon name="close" size={16} />Đóng chi tiết</button>
      </div>
    </header>
    <p className="enterprise-coop-status-note" role="note"><Icon name={cooperation.state === 'APPROVED' ? 'check-circle' : 'info'} size={18} /><span>{messages[cooperation.state] || states[cooperation.state] || cooperation.state}</span></p>
    {feedback && <section className="enterprise-coop-feedback" aria-label="Phản hồi nhà trường"><h4><Icon name="shield" size={18} />Phản hồi nhà trường</h4>
      <dl><div><dt>Kết quả</dt><dd>{feedback.reasonCode ? reasons[feedback.reasonCode] || feedback.reasonCode : states[cooperation.state] || cooperation.state}</dd></div>
        <div><dt>Căn cứ xét duyệt</dt><dd>{feedback.decisionRef || 'Chưa có căn cứ xét duyệt'}</dd></div>
        <div className="enterprise-coop-feedback-fields"><dt>Cần bổ sung</dt><dd>{feedback.missingFields.length ? <ul>{feedback.missingFields.map(field => <li key={field}>{fieldLabels[field] || field}</li>)}</ul> : 'Không có'}</dd></div>
      </dl>
    </section>}
    <dl className="enterprise-coop-meta"><div><dt><Icon name="users" size={16} />Chỉ tiêu tuyển</dt><dd>{data.quantity} vị trí</dd></div>
      <div><dt><Icon name="calendar" size={16} />Thời gian thực tập</dt><dd>{formatDateDMY(data.required.dateRange.startDate)} – {formatDateDMY(data.required.dateRange.endDate)}</dd></div>
      <div><dt><Icon name="building" size={16} />Địa điểm</dt><dd>{data.location || 'Chưa ghi nhận địa điểm'}</dd></div>
    </dl>
    <CooperationDescription text={data.description} />
    <div className="enterprise-coop-criteria"><CooperationSkillCriteria label="Tiêu chí bắt buộc" skills={data.required.skills} majorCodes={data.required.majorCodes} />
      {data.preferred.length > 0 && <CooperationSkillCriteria label="Tiêu chí ưu tiên" skills={data.preferred} />}
    </div>
    {data.benefits && <section className="enterprise-coop-benefits"><h4>Quyền lợi</h4><p>{data.benefits}</p></section>}
  </div>;
}
