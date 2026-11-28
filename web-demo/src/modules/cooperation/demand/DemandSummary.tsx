import { useEffect, useRef } from 'react';
import { Icon } from '../../../components';
import { formatDateDMY } from '../../../ui/dateFormat';
import type { Demand } from '../types';
import { CooperationDescription } from '../workflow/CooperationDescription';
import { CooperationSkillCriteria } from '../workflow/CooperationSkillCriteria';
import { DemandStatus } from './DemandStatus';
import '../workflow/cooperation-review.css';

/** Saved content only; editing and submission remain separate commands. */
export function DemandSummary({ id, demand, schoolName, onClose }: {
  id: string; demand: Demand; schoolName?: string; onClose: () => void;
}) {
  const heading = useRef<HTMLHeadingElement>(null);
  const data = demand.data;
  useEffect(() => {
    heading.current?.focus({ preventScroll: true });
    if (heading.current?.closest('dialog')) return;
    heading.current?.closest('section')?.scrollIntoView({ block: 'nearest',
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' });
  }, []);
  return <section id={id} className="panel demand-preview" aria-labelledby={id + '-title'}>
    <header className="demand-preview-header">
      <span className="demand-position-icon" aria-hidden="true"><Icon name="briefcase" size={23} /></span>
      <div><p className="demand-kicker">Chi tiết nhu cầu thực tập</p>
        <h3 id={id + '-title'} tabIndex={-1} ref={heading}>{data.title}</h3>
      </div>
      <div className="button-row"><DemandStatus demand={demand} />
        <button type="button" className="secondary" onClick={onClose}><Icon name="close" size={16} />Đóng chi tiết nhu cầu</button>
      </div>
    </header>
    <div className="demand-preview-body"><dl className="demand-preview-meta">
      <div><dt>Trường tiếp nhận</dt><dd>{schoolName || 'Trường đối tác'}</dd></div>
      <div><dt>Thời gian thực tập</dt><dd>{formatDateDMY(data.required.dateRange.startDate)} – {formatDateDMY(data.required.dateRange.endDate)}</dd></div>
      <div><dt>Chỉ tiêu tuyển</dt><dd>{data.quantity} vị trí</dd></div>
      <div><dt>Hạn tuyển</dt><dd>{new Date(data.deadline).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}</dd></div>
      <div><dt>Ngành</dt><dd>{data.required.majorCodes.join(', ') || 'Không giới hạn ngành'}</dd></div>
      {data.location && <div><dt>Địa điểm</dt><dd>{data.location}</dd></div>}
    </dl>
    <div className="demand-preview-copy"><CooperationDescription text={data.description} />
    {data.benefits && <div className="demand-benefits"><h4>Quyền lợi</h4><p>{data.benefits}</p></div>}
    </div></div>
    <div className="demand-preview-skills">
      <CooperationSkillCriteria label="Kỹ năng bắt buộc" skills={data.required.skills} />
      {data.preferred.length > 0 && <CooperationSkillCriteria label="Kỹ năng ưu tiên" skills={data.preferred} />}
    </div>
    <details className="demand-catalog-note"><summary>Thông tin danh mục</summary><dl>
      <div><dt>Phiên bản học vụ</dt><dd>{data.academicCatalogVersion}</dd></div>
      <div><dt>Phiên bản kỹ năng</dt><dd>{data.taxonomyVersion}</dd></div>
    </dl></details>
  </section>;
}
