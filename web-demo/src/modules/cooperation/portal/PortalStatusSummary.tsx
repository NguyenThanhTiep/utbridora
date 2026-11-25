import type { Resource } from './data';

/** A visual distribution of records already loaded by the portal, with no extra reads. */
export function PortalStatusSummary({ cooperations, verifications }: {
  cooperations: Resource<{ state: string }>; verifications: Resource<{ state: string }>;
}) {
  const loading = cooperations.loading || verifications.loading;
  const unavailable = Boolean(cooperations.error || verifications.error);
  const records = [...cooperations.items, ...verifications.items];
  const groups = [
    { label: 'Chờ xét duyệt', state: 'SUBMITTED', tone: 'info' },
    { label: 'Cần bổ sung', state: 'NEEDS_CHANGES', tone: 'warning' },
    { label: 'Đã duyệt', state: 'APPROVED', tone: 'success' },
    { label: 'Từ chối', state: 'REJECTED', tone: 'danger' },
    { label: 'Bản nháp', state: 'DRAFT', tone: 'neutral' },
  ].map(group => ({ ...group, count: records.filter(record => record.state === group.state).length }));
  const total = groups.reduce((sum, group) => sum + group.count, 0);
  return <section className="panel portal-status-summary is-summary-strip" aria-label="Tình hình hợp tác và xác minh">
    <div className="section-heading"><h3>Tình hình hồ sơ</h3><span className="muted">Hợp tác & xác minh</span></div>
    {loading ? <p className="rc-loading-state" role="status">Đang tải tình hình hồ sơ…</p> : unavailable
      ? <p className="muted">Chưa tải đủ dữ liệu để tổng hợp. Hãy tải lại dữ liệu.</p>
      : <>
        <div className="portal-status-total"><strong>{total}</strong><span>hồ sơ trong phạm vi đang xem</span></div>
        {total > 0 && <div className="portal-status-bar" role="img" aria-label={groups.map(group => `${group.label}: ${group.count}`).join(', ')}>
          {groups.filter(group => group.count > 0).map(group => <span key={group.state} className={'is-' + group.tone} style={{ width: `${group.count / total * 100}%` }} />)}
        </div>}
        <dl className="portal-status-legend">{groups.filter(group => group.count > 0).map(group => <div key={group.state}>
          <dt><span className={'portal-status-dot is-' + group.tone} />{group.label}</dt><dd>{group.count}</dd>
        </div>)}</dl>
        {total === 0 && <p className="muted">Chưa có hồ sơ trong phạm vi đang xem.</p>}
      </>}
  </section>;
}
