import { useId, useState } from 'react';
import { EmptyState, Notice } from '../../../components';
import type { Demand } from '../types';
import type { Resource } from '../portal/data';
import { EnterpriseMatchingSummary } from '../proposals/EnterpriseMatchingSummary';

// Reuse T4 protected-release API; backend gate and ledger remain authoritative.

export function ProtectedStatistics({ org, demands }: { org: string; demands: Resource<Demand> }) {
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
    {demand && <EnterpriseMatchingSummary key={[org, demand.demandId, demand.version, demand.criteriaVersion].join(':')} org={org} demand={demand} />}
    <section className="panel portal-statistics-scope"><h3>Phạm vi đang xem</h3>
      <p className="portal-statistics-scope__note">Thống kê chỉ gắn với nhu cầu được chọn. Băng kết quả không bao gồm thông tin ứng viên hoặc phân tích theo từng tiêu chí.</p>
      {demands.loading ? <p role="status">Đang tải nhu cầu…</p> : demand ? <dl className="portal-key-values">
        <div><dt>Nhu cầu</dt><dd>{demand.data.title}</dd></div>
        <div><dt>Thời gian thực tập</dt><dd>{demand.data.required.dateRange.startDate} – {demand.data.required.dateRange.endDate}</dd></div>
      </dl> : !demands.error && <EmptyState icon="briefcase" title="Chưa có nhu cầu để đối chiếu" description="Tạo nhu cầu thực tập để xác định tiêu chí tuyển dụng." />}
    </section>
  </>;
}
