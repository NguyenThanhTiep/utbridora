import { Icon } from '../../../components';
import { states, type Cooperation } from './types';
import { recordRow } from '../ui/recordRow';

export function EnterpriseCooperationList({ items, titles, schools, schoolIds, selectedId, openingId, busy, idPrefix, open }: {
  items: { item: Cooperation; seq: number }[]; titles: Record<string, string>; schools: Record<string, string>;
  schoolIds: Record<string, string>; selectedId?: string; openingId: string; busy: boolean; idPrefix: string;
  open: (item: Cooperation) => void;
}) {
  return <div className="proposal-table enterprise-cooperations-table enterprise-mobile-list"><table>
    <caption className="cooperation-sr-only">Đề nghị hợp tác của doanh nghiệp</caption>
    <thead><tr><th>Đề nghị hợp tác</th><th>Trường tiếp nhận</th><th>Trạng thái</th><th>Thao tác</th></tr></thead>
    <tbody>{items.map(({ item, seq }) => {
      const selected = selectedId === item.cooperationId;
      const opening = openingId === item.cooperationId;
      return <tr key={item.cooperationId} className={selected ? 'table-row-selected' : undefined}
        {...recordRow(() => open(item), busy || opening)}>
        <th scope="row" data-label="Đề nghị hợp tác"><div className="enterprise-coop-name">
          <span className="enterprise-coop-icon" aria-hidden="true"><Icon name="briefcase" size={20} /></span>
          <div><b>{titles[item.cooperationId] || `Đề nghị ${seq}`}</b><small>{item.submissionNo > 0 ? `Lần gửi ${item.submissionNo}` : 'Chưa gửi hồ sơ'}</small></div>
        </div></th>
        <td data-label="Trường tiếp nhận"><span className="enterprise-coop-school">{schools[schoolIds[item.cooperationId]] || 'Trường đối tác'}</span></td>
        <td data-label="Trạng thái"><span className={'status-pill pill-' + item.state.toLowerCase()}><span className="pill-dot" />{states[item.state] || item.state}</span></td>
        <td data-label="Thao tác"><button type="button" className="secondary" id={idPrefix + item.cooperationId}
          aria-label={`Xem nội dung đề nghị ${seq}`} aria-expanded={selected} aria-busy={opening} disabled={busy || opening}
          onClick={() => open(item)}>{opening ? <><span className="spinner" />Đang tải…</> : selected ? 'Đang xem' : 'Xem chi tiết'}<Icon name="arrow-right" size={15} /></button></td>
      </tr>;
    })}</tbody>
  </table></div>;
}
