import { useId, useState } from 'react';
import { EmptyState, Icon, Notice } from '../../../components';
import { formatDateDMY } from '../../../ui/dateFormat';
import type { Demand } from '../types';
import type { Resource } from '../portal/data';
import type { PortalPage } from '../portal/navigation';
import { EnterpriseMatchingSummary } from '../proposals/EnterpriseMatchingSummary';
import { CooperationSkillCriteria } from '../workflow/CooperationSkillCriteria';
import '../workflow/cooperation-review.css';

// Reuse T4 protected-release API; backend gate and ledger remain authoritative.

export function ProtectedStatistics({ org, demands, navigate }: { org: string; demands: Resource<Demand>; navigate: (page: PortalPage) => void }) {
  const id = useId();
  const [selected, setSelected] = useState('');
  const rows = demands.items.filter(demand => !demand.softDeletedAt);
  const demand = rows.find(row => row.demandId === selected) || rows[0];
  const demandContext = <section className="panel enterprise-applied-demand"><div className="statistics-demand-heading"><h3>Nhu cầu đang áp dụng</h3>
      {demand && <button type="button" className="secondary" onClick={() => navigate('demands')}>Xem nhu cầu<Icon name="arrow-right" size={15} /></button>}
    </div>
      {demands.loading ? <p className="rc-loading-state" role="status">Đang tải nhu cầu…</p> : demand ? <>
        <div className="statistics-demand-context"><span className="statistics-demand-icon" aria-hidden="true"><Icon name="briefcase" size={22} /></span><div>
          <h4>{demand.data.title}</h4><p><Icon name="calendar" size={15} />{formatDateDMY(demand.data.required.dateRange.startDate)} – {formatDateDMY(demand.data.required.dateRange.endDate)}<span aria-hidden="true">·</span>{demand.data.quantity} vị trí</p>
        </div></div>
        <div className="statistics-demand-criteria"><CooperationSkillCriteria key={demand.demandId + ':required'} label="Kỹ năng bắt buộc" skills={demand.data.required.skills} majorCodes={demand.data.required.majorCodes} />
          {demand.data.preferred.length > 0 && <CooperationSkillCriteria key={demand.demandId + ':preferred'} label="Kỹ năng ưu tiên" skills={demand.data.preferred} />}
        </div>
        <div className="button-row statistics-demand-actions"><button className="secondary" onClick={() => navigate('cooperations')}>{demand.cooperationId ? 'Theo dõi hợp tác' : 'Gửi yêu cầu hợp tác'}<Icon name="arrow-right" size={15} /></button></div>
      </> : !demands.error && <>
        <EmptyState icon="briefcase" title="Chưa có nhu cầu để đối chiếu" description="Tạo nhu cầu thực tập để xác định tiêu chí tuyển dụng." />
        <div className="button-row"><button type="button" className="t5-btn-teal" onClick={() => navigate('demands')}>Tạo nhu cầu thực tập</button></div>
      </>}
    </section>;
  return <>
    <section className="panel portal-record-picker"><label htmlFor={id}>Nhu cầu xem thống kê</label>
      <select id={id} value={demand?.demandId || ''} disabled={demands.loading || !rows.length} onChange={event => setSelected(event.target.value)}>
        {!rows.length && <option value="">{demands.loading ? 'Đang tải nhu cầu…' : 'Chưa có nhu cầu'}</option>}
        {rows.map(row => <option key={row.demandId} value={row.demandId}>{row.data.title}</option>)}
      </select><Notice tone="error" message={demands.error} />
    </section>
    {demand ? <EnterpriseMatchingSummary key={[org, demand.demandId, demand.version, demand.criteriaVersion].join(':')}
      org={org} demand={demand} showDemandContext={false} asideContent={demandContext} /> : demandContext}
  </>;
}
