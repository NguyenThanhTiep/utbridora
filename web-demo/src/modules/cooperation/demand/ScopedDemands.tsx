import { useEffect, useRef, useState } from 'react';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { listDemands } from '../api';
import type { Demand, DemandPage } from '../types';
import { canLeaveDemand } from '../navigation';
import { DemandEditor } from './DemandEditor';
import { failureMessage } from './errors';
import { DemandSummary } from './DemandSummary';

export function ScopedDemands({ organizationId }: { organizationId: string }) {
  const [pageIndex, setPageIndex] = useState(0), [refresh, setRefresh] = useState(0);
  const [page, setPage] = useState<DemandPage | null>(null), [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(''), [success, setSuccess] = useState('');
  const [editor, setEditor] = useState<{ record: Demand | null; key: number } | null>(null);
  const [saving, setSaving] = useState(false), [unconfirmed, setUnconfirmed] = useState(false);
  const nextEditor = useRef(0);
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setLoading(true); setListError(''); setPage(null);
    void listDemands(organizationId, pageIndex, controller.signal).then(result => {
      if (active) setPage(result);
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
      <div className="section-heading cooperation-heading"><div><h2>Danh sách nhu cầu</h2>
        <p className="section-description">Lưu bản nháp và tiêu chí tuyển thực tập. Bản nháp chưa được gửi hợp tác.</p></div>
        <div className="button-row">
          <button className="secondary" disabled={loading || blocked} onClick={() => setRefresh(value => value + 1)}><Icon name="refresh" size={16} />Tải lại</button>
          <button disabled={blocked} onClick={() => open(null)}><Icon name="plus" size={16} />Tạo nhu cầu</button>
        </div>
      </div>
      <Notice message={success} tone="success" />
      <Notice message={listError} tone="error" />
      {loading ? <LoadingState /> : page && page.content.length === 0 ? <EmptyState icon="briefcase" title="Chưa có nhu cầu"
        description="Tạo bản nháp đầu tiên cho doanh nghiệp này." /> : page && <>
        <ul className="cooperation-demand-list" aria-label="Các nhu cầu của doanh nghiệp">
          {page.content.map(record => <li key={record.demandId}>
            <div className="cooperation-demand-title"><h3>{record.data.title}</h3>
              <span className="cooperation-state">{record.state === 'DRAFT' ? 'Bản nháp' : 'Đã sẵn sàng'}</span></div>
            <p className="section-description">{record.data.quantity} vị trí · Phiên bản {record.version} · Tiêu chí v{record.criteriaVersion}</p>
            <p className="field-hint">{record.validationState === 'VALID' ? 'Tiêu chí hợp lệ' : 'Tiêu chí cần kiểm tra'} · Mã nhu cầu: {record.demandId}</p>
            <DemandSummary demand={record} />
            <button className="secondary" disabled={blocked || record.state !== 'DRAFT' || !!record.softDeletedAt || !!record.cooperationId}
              onClick={() => open(record)}>Sửa bản nháp<span className="cooperation-sr-only"> {record.data.title}</span></button>
          </li>)}
        </ul>
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
      }} />}
  </>;
}
