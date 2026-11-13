import { useEffect, useRef, useState } from 'react';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { listDemands } from '../api';
import type { Demand, DemandPage } from '../types';
import { canLeaveDemand } from '../navigation';
import { DemandEditor } from './DemandEditor';
import { failureMessage } from './errors';
import { DemandSummary } from './DemandSummary';
import { api } from '../../../api';
import { newIdempotencyKey } from '../../student/api';

export function ScopedDemands({ organizationId, onLoaded }: { organizationId: string; onLoaded?: (items: Demand[]) => void }) {
  const [pageIndex, setPageIndex] = useState(0), [refresh, setRefresh] = useState(0);
  const [page, setPage] = useState<DemandPage | null>(null), [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(''), [success, setSuccess] = useState('');
  const [editor, setEditor] = useState<{ record: Demand | null; key: number } | null>(null);
  const [saving, setSaving] = useState(false), [unconfirmed, setUnconfirmed] = useState(false);
  const nextEditor = useRef(0);
  const loadedRef = useRef(onLoaded);
  loadedRef.current = onLoaded;
  const submitKeys = useRef(new Map<string, string>());
  const submitCooperation = async (record: Demand) => {
    const ref = record.demandId + ':' + record.version;
    if (!submitKeys.current.has(ref)) submitKeys.current.set(ref, newIdempotencyKey()); setSaving(true); setListError('');
    try { await api('/enterprise/demands/submit-with-cooperation', { method: 'POST', headers: { 'X-Organization-Id': organizationId, 'Idempotency-Key': submitKeys.current.get(ref)! }, body: JSON.stringify({ demandId: record.demandId, expectedDemandVersion: record.version }) }); submitKeys.current.delete(ref); setSuccess('Đã gửi đề nghị hợp tác.'); setRefresh(n => n + 1); window.dispatchEvent(new Event('cooperation-updated')); }
    catch (error) { setListError(failureMessage(error)); } finally { setSaving(false); }
  };
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setLoading(true); setListError(''); setPage(null);
    void listDemands(organizationId, pageIndex, controller.signal).then(result => {
      if (active) {
        setPage(result);
        loadedRef.current?.(result.content);
      }
    }).catch(error => {
      if (active) setListError(failureMessage(error));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [organizationId, pageIndex, refresh]);

  const open = (record: Demand | null) => {
    if (!canLeaveDemand()) return;
    setSuccess(''); setUnconfirmed(false);
    setEditor({ record, key: ++nextEditor.current });
  };
  const blocked = saving || unconfirmed;
  return <>
    <section className="panel">
      <div className="section-heading cooperation-heading"><div>
        <h2>Danh sách nhu cầu</h2>
        </div>
        <div className="button-row">
          <button className="secondary" disabled={loading || blocked} onClick={() => setRefresh(value => value + 1)}><Icon name="refresh" size={16} />Tải lại</button>
          <button disabled={blocked} onClick={() => open(null)}><Icon name="plus" size={16} />Tạo nhu cầu</button>
        </div>
      </div>
      <Notice message={success} tone="success" />
      <Notice message={listError} tone="error" />
      {loading ? <LoadingState /> : page && page.content.length === 0 ? <EmptyState icon="briefcase" title="Chưa có nhu cầu"
        description="Tạo bản nháp đầu tiên cho doanh nghiệp này." /> : page && <>
        <div className="proposal-table portal-demands-table">
          <table><thead><tr><th>Vị trí / nhu cầu</th><th>Trạng thái</th><th>Thời gian thực tập</th><th>Chỉ tiêu</th><th>Thao tác</th></tr></thead>
            <tbody>{page.content.map(record => <tr key={record.demandId}>
              <th scope="row"><h3>{record.data.title}</h3><DemandSummary demand={record} /></th>
              <td><span className={'cooperation-state ' + (record.state === 'DRAFT' ? 'state-draft' : 'state-ready')}>
                {record.state === 'DRAFT' ? 'Bản nháp' : record.cooperationId ? 'Đã gửi hợp tác' : 'Đã sẵn sàng'}</span>
                {record.validationState !== 'VALID' && <p className="field-hint">Tiêu chí cần kiểm tra</p>}</td>
              <td>{record.data.required.dateRange.startDate} – {record.data.required.dateRange.endDate}</td>
              <td>{record.data.quantity} vị trí</td>
              <td><div className="button-row">
                <button className="secondary" disabled={blocked || record.state !== 'DRAFT' || !!record.softDeletedAt}
                  onClick={() => open(record)}>Sửa bản nháp<span className="cooperation-sr-only"> {record.data.title}</span></button>
                {!record.cooperationId && record.state === 'DRAFT' && <button disabled={blocked}
                  onClick={() => void submitCooperation(record)}>Gửi yêu cầu hợp tác<span className="cooperation-sr-only"> {record.data.title}</span></button>}
              </div></td>
            </tr>)}</tbody>
          </table>
        </div>
        <div className="pagination"><span className="pagination-summary">{page.totalElements} kết quả · Trang {page.page + 1}/{Math.max(1, page.totalPages)}</span>
          <div className="pagination-buttons">
            <button className="secondary" disabled={blocked || page.page === 0} onClick={() => setPageIndex(page.page - 1)}>Trước</button>
            <button className="secondary" disabled={blocked || page.page + 1 >= page.totalPages} onClick={() => setPageIndex(page.page + 1)}>Sau</button>
          </div>
        </div>
      </>}
    </section>
    {editor && <DemandEditor key={editor.key} organizationId={organizationId} existing={editor.record}
      onBusy={setSaving} onUnconfirmed={setUnconfirmed} onClose={() => { if (canLeaveDemand()) setEditor(null); }} onSaved={record => {
        setEditor(null); setUnconfirmed(false); setSuccess(`Đã lưu bản nháp “${record.data.title}”, phiên bản ${record.version}.`);
        setPageIndex(0); setRefresh(value => value + 1);
        window.dispatchEvent(new Event('cooperation-data-updated'));
      }} />}
  </>;
}
