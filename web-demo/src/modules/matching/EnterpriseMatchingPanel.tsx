import { useEffect, useState } from 'react';
import { ApiError, api } from '../../api';
import { EmptyState, Notice } from '../../components';
import { newIdempotencyKey } from '../student/api';
import { listDemands } from '../cooperation/api';
import type { Demand } from '../cooperation/types';

type Evaluation = { audience: string; release: { eligibleBand: string } | null };

// liên quan đến việc thêm lọc svien theo gpa là MVP: Doanh nghiệp chạy Demand đã lưu với tiêu chí GPA/kỹ năng và chỉ nhận aggregate được bảo vệ.
export function EnterpriseMatchingPanel({ organizationId }: { organizationId: string }) {
  const [demands, setDemands] = useState<Demand[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [result, setResult] = useState<Evaluation | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true); setError(''); setResult(null);
    void (async () => {
      try {
        const rows: Demand[] = [];
        let page = 0, pages = 1;
        while (page < pages) {
          const response = await listDemands(organizationId, page, controller.signal);
          rows.push(...response.content); pages = response.totalPages; page++;
        }
        if (!controller.signal.aborted) {
          const usable = rows.filter(row => !row.softDeletedAt && row.validationState === 'VALID');
          setDemands(usable);
          setSelectedId(previous => usable.some(row => row.demandId === previous) ? previous : usable[0]?.demandId || '');
        }
      } catch (failure) {
        if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : 'Không tải được nhu cầu.');
      } finally { if (!controller.signal.aborted) setLoading(false); }
    })();
    return () => controller.abort();
  }, [organizationId, refresh]);

  const selected = demands.find(row => row.demandId === selectedId) || null;
  const evaluate = async () => {
    if (!selected) return;
    setBusy(true); setError(''); setResult(null);
    try {
      const response = await api<Evaluation>('/matching/evaluate', {
        method: 'POST', headers: { 'X-Organization-Id': organizationId, 'Idempotency-Key': newIdempotencyKey() },
        body: JSON.stringify({ demandId: selected.demandId, expectedDemandVersion: selected.version,
          expectedCriteriaVersion: selected.criteriaVersion }),
      });
      if (response.audience !== 'ENTERPRISE' || !response.release) throw new Error('Phản hồi không đúng phạm vi doanh nghiệp.');
      setResult(response);
    } catch (failure) {
      setError(failure instanceof ApiError && failure.errorCode === 'PRIVACY_RELEASE_LOCKED'
        ? 'Tiêu chí đã được phát hành trong đợt dữ liệu này. Không thể đổi GPA/kỹ năng để tạo lượt xem mới.'
        : failure instanceof Error ? failure.message : 'Không chạy được Matching.');
    } finally { setBusy(false); }
  };

  return <section className="panel matching-enterprise-panel" aria-labelledby="enterprise-matching-title">
    <div className="section-heading"><div><h2 id="enterprise-matching-title">Phân tích nguồn lực sinh viên</h2>
      <p className="section-description">Chọn nhu cầu đã lưu. Tiêu chí GPA tối thiểu và kỹ năng được chỉnh trong bản nháp nhu cầu.</p></div>
      <button className="secondary" disabled={loading || busy} onClick={() => setRefresh(value => value + 1)}>Tải lại</button></div>
    <Notice message={error} tone="error" />
    {loading ? <p role="status">Đang tải nhu cầu…</p> : demands.length === 0
      ? <EmptyState title="Chưa có nhu cầu hợp lệ" description="Tạo và lưu nhu cầu cùng tiêu chí kỹ năng/GPA trước khi chạy matching." />
      : <>
        <label htmlFor="enterprise-matching-demand">Nhu cầu tuyển dụng</label>
        <select id="enterprise-matching-demand" value={selectedId} disabled={busy}
          onChange={event => { setSelectedId(event.target.value); setResult(null); }}>
          {demands.map(demand => <option key={demand.demandId} value={demand.demandId}>{demand.data.title}</option>)}
        </select>
        {selected && <div className="matching-enterprise-criteria">
          {/* liên quan đến việc thêm lọc svien theo gpa là MVP: Tóm tắt tiêu chí nhà tuyển dụng đã chọn để xác nhận trước khi chạy. */}
          <p>GPA tối thiểu: <strong>{selected.data.required.minGpa?.toFixed(1) ?? 'Không lọc'}</strong></p>
          <p>Kỹ năng bắt buộc: <strong>{selected.data.required.skills.map(skill => skill.skillCode).join(', ') || 'Không giới hạn'}</strong></p>
          <p>Kỹ năng ưu tiên: <strong>{selected.data.preferred.map(skill => skill.skillCode).join(', ') || 'Không có'}</strong></p>
          <button disabled={busy || selected.state !== 'DRAFT'} onClick={() => void evaluate()}>
            {busy ? 'Đang đối chiếu…' : 'Chạy Matching'}
          </button>
        </div>}
        {result?.release && <div className="matching-enterprise-result" role="status">
          <h3>Kết quả đã bảo vệ</h3>
          <p>Sinh viên đạt đủ tiêu chí: <strong>{result.release.eligibleBand}</strong></p>
          <p className="field-hint">Doanh nghiệp chỉ nhận aggregate đã áp dụng suppression. Danh sách và thông tin từng sinh viên chỉ hiển thị cho nhà trường đúng phạm vi.</p>
        </div>}
      </>}
  </section>;
}
