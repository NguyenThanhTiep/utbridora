import { useId, useState } from 'react';
import { EmptyState, Icon, Notice } from '../../../components';
import type { Demand } from '../types';
import type { Resource } from '../portal/data';
import type { PortalPage } from '../portal/navigation';
import { FeaturePending } from '../ui/FeaturePending';

// T4's current evaluate endpoint is school-only. Await the enterprise release API; never derive counts from school matching.

export function ProtectedStatistics({ demands, navigate }: { demands: Resource<Demand>; navigate: (page: PortalPage) => void }) {
  const id = useId();
  const [selected, setSelected] = useState('');
  const rows = demands.items.filter(demand => !demand.softDeletedAt);
  const demand = rows.find(row => row.demandId === selected) || rows[0];
  return <>
    <section className="panel portal-record-picker"><label htmlFor={id}>Nhu cầu xem thống kê</label>
      <select id={id} value={demand?.demandId || ''} disabled={demands.loading || !rows.length} onChange={event => setSelected(event.target.value)}>
        {!rows.length && <option value="">{demands.loading ? 'Đang tải nhu cầu…' : 'Chưa có nhu cầu'}</option>}
        {rows.map(row => <option key={row.demandId} value={row.demandId}>{row.data.title}</option>)}
      </select><Notice tone="error" message={demands.error} />
    </section>
    <div className="portal-statistics-grid">
      <section className="panel portal-protected-result"><span className="portal-card-icon"><Icon name="activity" /></span>
        <h3>Kết quả tổng hợp được bảo vệ</h3><strong className="portal-result-placeholder">—</strong>
        <FeaturePending>Thống kê ứng viên phù hợp chưa sẵn sàng. Kết quả sẽ hiển thị theo khoảng số lượng khi được công bố.</FeaturePending>
        <button disabled>Xem kết quả thống kê</button>
      </section>
      <section className="panel portal-privacy-card"><Icon name="shield" size={28} /><h3>Quyền riêng tư</h3>
        <p>Thống kê chỉ hiển thị kết quả tổng hợp, không kèm danh tính hay danh sách sinh viên.</p>
      </section>
    </div>
    <section className="panel"><h3>Nhu cầu đang áp dụng</h3>
      {demands.loading ? <p role="status">Đang tải nhu cầu…</p> : demand ? <>
        <dl className="portal-key-values"><div><dt>Vị trí tuyển dụng</dt><dd>{demand.data.title}</dd></div>
          <div><dt>Kỹ năng bắt buộc</dt><dd>{demand.data.required.skills.map(skill => skill.skillCode).join(' · ') || 'Không yêu cầu'}</dd></div>
          <div><dt>Kỹ năng ưu tiên</dt><dd>{demand.data.preferred.map(skill => skill.skillCode).join(' · ') || 'Không yêu cầu'}</dd></div>
          <div><dt>Thời gian thực tập</dt><dd>{demand.data.required.dateRange.startDate} – {demand.data.required.dateRange.endDate}</dd></div>
        </dl>
        <div className="button-row"><button className="secondary" onClick={() => navigate('demands')}>Xem nhu cầu</button>
          <button onClick={() => navigate('cooperations')}>Gửi yêu cầu hợp tác</button></div>
      </> : !demands.error && <EmptyState icon="briefcase" title="Chưa có nhu cầu để đối chiếu" description="Tạo nhu cầu thực tập để xác định tiêu chí tuyển dụng." />}
    </section>
  </>;
}
