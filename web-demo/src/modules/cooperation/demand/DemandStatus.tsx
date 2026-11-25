import type { Demand } from '../types';

export function demandStatus(demand: Demand) {
  return demand.state === 'DRAFT' ? 'DRAFT' : demand.cooperationId ? 'SENT' : 'READY';
}

const labels = { DRAFT: 'Bản nháp', SENT: 'Đã gửi hợp tác', READY: 'Đã sẵn sàng' };

export function DemandStatus({ demand }: { demand: Demand }) {
  if (demand.softDeletedAt) {
    return <span className="cooperation-state demand-status is-deleted">
      <span className="demand-status-dot" aria-hidden="true" />Đã xóa
    </span>;
  }
  const status = demandStatus(demand);
  return <span className={'cooperation-state demand-status is-' + status.toLowerCase()}>
    <span className="demand-status-dot" aria-hidden="true" />{labels[status]}
  </span>;
}
