import { Icon } from '../../../components';
import { states, type Cooperation } from './types';
import { recordRow } from '../ui/recordRow';
import './cooperation-review.css';

export function SchoolCooperationList({ items, selectedId, termNames, demandTitles, titlesLoading, loading, emptyMessage, busy, open }: {
  items: { item: Cooperation; seq: number }[]; selectedId?: string; termNames: Record<string, string>; demandTitles: Record<string, string>; titlesLoading: boolean;
  loading: boolean; emptyMessage: string; busy: boolean; open: (cooperation: Cooperation) => void;
}) {
  return <div className="school-cooperation-table">
    <table role="table" aria-label="Danh sách đề nghị hợp tác">
      <thead><tr><th scope="col">Đề nghị</th><th scope="col">Kỳ thực tập</th><th scope="col">Trạng thái</th><th scope="col">Thao tác</th></tr></thead>
      <tbody>{items.map(({ item: c, seq }) => {
        const selected = selectedId === c.cooperationId;
        const status = states[c.state] || c.state;
        const title = demandTitles[c.cooperationId] || (titlesLoading ? 'Đang tải tên nhu cầu…' : 'Chưa tải được tên nhu cầu');
        return <tr key={c.cooperationId} className={selected ? 'is-selected' : undefined} {...recordRow(() => open(c), busy)}>
          <td className="school-coop-identity"><div className="school-coop-person"><span className="school-coop-document" aria-hidden="true"><Icon name="briefcase" size={20} /></span>
            <div><h3 id={'school-cooperation-title-' + c.cooperationId} className="workflow-table-title" title={title}>{title}</h3>
              <small>Lần gửi {c.submissionNo}</small></div></div></td>
          <td className="school-coop-term"><span className="school-coop-cell-label" aria-hidden="true">Kỳ thực tập</span>
            <span>{termNames[c.termId] || '—'}</span></td>
          <td className="school-coop-state"><span className={`status-pill pill-${c.state.toLowerCase()}`}><span className="pill-dot" />{status}</span></td>
          <td className="school-coop-action"><button id={'school-cooperation-open-' + c.cooperationId} type="button" className="secondary school-coop-open" disabled={busy}
            aria-label={`Xem nội dung đề nghị ${seq}`} aria-describedby={'school-cooperation-title-' + c.cooperationId} aria-expanded={selected}
            aria-controls={selected ? 'school-cooperation-detail-' + c.cooperationId : undefined} onClick={() => open(c)}>
            {selected ? 'Đang xem' : 'Xem chi tiết'}<Icon name="arrow-right" size={15} /></button></td>
        </tr>;
      })}
        {!loading && items.length === 0 && <tr><td colSpan={4} className="school-coop-empty"><Icon name="briefcase" size={26} /><p>{emptyMessage}</p></td></tr>}
      </tbody>
    </table>
  </div>;
}
