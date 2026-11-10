import type { Demand } from '../types';

const date = (value: string) => value.split('-').reverse().join('/');

/** Read-only saved data, distinct from the editable draft state. */
export function DemandSummary({ demand }: { demand: Demand }) {
  const data = demand.data;
  const skills = (items: typeof data.preferred) => items.length ? items.map(skill =>
    `${skill.skillCode}${skill.minLevel == null ? '' : ` · cấp ${skill.minLevel}+`}${skill.minYearsPractice == null ? '' : ` · ${skill.minYearsPractice} năm+`}`
  ).join('; ') : 'Không yêu cầu';
  return <details className="cooperation-summary">
    <summary>Xem thông tin đã lưu</summary>
    <dl>
      <dt>Trường / kỳ</dt><dd>{data.schoolOrgId} / {data.termId}</dd>
      <dt>Ngành</dt><dd>{data.required.majorCodes.join(', ') || 'Không giới hạn ngành'}</dd>
      <dt>Thời gian thực tập</dt><dd>{date(data.required.dateRange.startDate)} – {date(data.required.dateRange.endDate)}</dd>
      <dt>Hạn tuyển</dt><dd>{new Date(data.deadline).toLocaleString('vi-VN')}</dd>
      <dt>Địa điểm</dt><dd>{data.location || 'Chưa cung cấp'}</dd>
      <dt>Mô tả</dt><dd>{data.description}</dd>
      <dt>Quyền lợi</dt><dd>{data.benefits || 'Chưa cung cấp'}</dd>
      <dt>Kỹ năng bắt buộc</dt><dd>{skills(data.required.skills)}</dd>
      <dt>Kỹ năng ưu tiên</dt><dd>{skills(data.preferred)}</dd>
      <dt>Phiên bản danh mục</dt><dd>Học vụ: {data.academicCatalogVersion} · Kỹ năng: {data.taxonomyVersion}</dd>
    </dl>
  </details>;
}
