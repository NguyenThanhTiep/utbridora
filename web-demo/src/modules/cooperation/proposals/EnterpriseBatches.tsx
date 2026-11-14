import { useEffect, useState } from 'react';
import { EmptyState, Notice } from '../../../components';
import type { ContractPage } from '../../student/types';
import { canLeaveDemand } from '../navigation';
import { scopedRead, workflowError } from './api';
import type { EnterpriseProposalBatch } from './types';
import './styles.css';

function validPage(value: ContractPage<EnterpriseProposalBatch>, cooperationId: string) {
  return !!value && Array.isArray(value.content)
    && [value.page, value.totalElements, value.totalPages].every(number => Number.isSafeInteger(number) && number >= 0)
    && Number.isSafeInteger(value.size) && value.size > 0 && value.size <= 100
    && value.content.length <= value.size && value.content.length <= value.totalElements
    && value.content.every(row => row && typeof row.batchId === 'string' && /^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(row.batchId)
      && row.cooperationId === cooperationId && Number.isSafeInteger(row.visibleProposalCount) && row.visibleProposalCount > 0
      && Object.keys(row).every(key => ['batchId', 'cooperationId', 'visibleProposalCount'].includes(key)));
}

export function EnterpriseBatches({ org, cooperationId, openCandidates }: { org: string; cooperationId: string; openCandidates: () => void }) {
  const [response, setResponse] = useState<{ org: string; cooperationId: string; data: ContractPage<EnterpriseProposalBatch> } | null>(null);
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const result = response?.org === org && response.cooperationId === cooperationId ? response.data : null;
  useEffect(() => {
    const refresh = () => {if (!document.hidden && canLeaveDemand()) setRevision(value => value + 1);};
    window.addEventListener('focus', refresh);document.addEventListener('visibilitychange', refresh);
    return () => {window.removeEventListener('focus', refresh);document.removeEventListener('visibilitychange', refresh);};
  }, []);
  useEffect(() => {
    const controller = new AbortController();setLoading(true);setError('');setResponse(null);
    const query = new URLSearchParams({ page: String(page), size: '10' });
    void scopedRead<ContractPage<EnterpriseProposalBatch>>(org, `/enterprise/cooperations/${cooperationId}/proposal-batches?${query}`, controller.signal)
      .then(value => {
        if (controller.signal.aborted) return;
        if (!validPage(value, cooperationId)) {setError('Chưa nhận được danh sách lô hợp lệ trong phạm vi hợp tác đang xem. Hãy tải lại.');return;}
        if (page > 0 && page >= value.totalPages) {setPage(Math.max(0, value.totalPages - 1));return;}
        setResponse({ org, cooperationId, data: value });
      }).catch(reason => {if (!controller.signal.aborted) setError(workflowError(reason));})
      .finally(() => {if (!controller.signal.aborted) setLoading(false);});
    return () => controller.abort();
  }, [org, cooperationId, page, revision]);
  return <section className="portal-batches" aria-label="Lô đề xuất được chia sẻ">
    <div className="placement-header-bar"><h3>Lô đề xuất</h3><button type="button" className="secondary" disabled={loading}
      onClick={() => {if (canLeaveDemand()) setRevision(value => value + 1);}}>Tải lại lô đề xuất</button></div>
    <p>Chỉ hiển thị lô có hồ sơ đang được nhà trường chia sẻ hợp lệ cho doanh nghiệp của bạn.</p>
    <Notice tone="error" message={error} />{loading && <p role="status">Đang tải lô đề xuất…</p>}
    {result && (result.totalElements === 0 ? <EmptyState icon="users" title="Chưa có dữ liệu lô đề xuất"
      description="Lô sẽ xuất hiện khi nhà trường chia sẻ ít nhất một hồ sơ hợp lệ cho hợp tác này." />
      : <div className="proposal-table"><table><caption>Lô đề xuất của hợp tác đang xem</caption>
        <thead><tr><th>Lô đề xuất</th><th>Hồ sơ được phép xem</th><th>Thao tác</th></tr></thead>
        <tbody>{result.content.map((batch, index) => <tr key={batch.batchId}><th scope="row">Lô đề xuất {page * 10 + index + 1}
          <small className="proposal-batch-reference">{batch.batchId}</small></th><td><strong>{batch.visibleProposalCount}</strong> hồ sơ được chia sẻ</td>
          <td><button type="button" className="secondary" disabled={loading} onClick={() => {if (canLeaveDemand()) openCandidates();}}>Xem ứng viên trong lô</button></td></tr>)}</tbody>
      </table></div>)}
    {result && result.totalPages > 1 && <div className="button-row"><button type="button" className="secondary" disabled={loading || page === 0}
      onClick={() => {if (canLeaveDemand()) setPage(value => value - 1);}}>Trang lô trước</button><span>Trang lô {page + 1}/{result.totalPages}</span>
      <button type="button" className="secondary" disabled={loading || page + 1 >= result.totalPages}
        onClick={() => {if (canLeaveDemand()) setPage(value => value + 1);}}>Trang lô sau</button></div>}
  </section>;
}
