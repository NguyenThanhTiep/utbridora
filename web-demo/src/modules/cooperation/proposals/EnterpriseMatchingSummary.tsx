import { useState } from 'react';
import { Notice } from '../../../components';
import type { Demand } from '../types';
import { useWorkflowCommand } from './useWorkflowCommand';

type Release = { releaseId: string; matchRunId: string; eligibleBand: 'SUPPRESSED' | '5-9' | '10-19' | '20+' };
type Evaluation = { audience: string; release?: Release };
const bands = { SUPPRESSED: 'Chưa đủ ngưỡng để công bố', '5-9': '5–9 ứng viên', '10-19': '10–19 ứng viên', '20+': 'Từ 20 ứng viên' };

/** T4 owns release, threshold and ledger. This consumer never calculates counts from school results. */
export function EnterpriseMatchingSummary({ org, demand }: { org: string; demand: Demand }) {
  const [release, setRelease] = useState<Release | null>(null), [error, setError] = useState('');
  const command = useWorkflowCommand<Evaluation>(org, result => {
    const r = result?.release;
    if (!result || result.audience !== 'ENTERPRISE' || !r || !r.releaseId || !r.matchRunId || !Object.hasOwn(bands, r.eligibleBand)
        || Object.keys(result).some(k => !['audience', 'release'].includes(k))
        || Object.keys(r).some(k => !['releaseId', 'matchRunId', 'eligibleBand'].includes(k))) {
      setRelease(null);setError('Chưa nhận được bản thống kê được bảo vệ hợp lệ.');return;
    }
    setRelease(r);setError('');
  }, () => {setRelease(null);setError('');}, {guardNavigation: false});
  return <section className="proposal-workspace p30-agg-section" aria-label="Thống kê đối chiếu tổng hợp">
    <div className="p30-info-banner" role="note">
      <span className="p30-info-icon" aria-hidden="true">i</span>
      <div><strong>Thống kê được bảo vệ</strong>
        <p>Chỉ hiển thị băng số lượng do máy chủ phát hành. Không có danh tính sinh viên hoặc dữ liệu chi tiết.</p></div>
    </div>
    <h3>Thống kê bảo vệ dành cho doanh nghiệp</h3>
    <p>Kết quả dùng ngưỡng bảo vệ tối thiểu k = 5; số lượng dưới ngưỡng được ẩn.</p>
    <Notice message={error || command.message} tone="error" />
    <div className="p30-agg-grid">
      <div className="p30-agg-main-card" aria-busy={command.busy}>
        <h4>Băng ứng viên đủ điều kiện</h4>
        <div className="p30-agg-band-value" aria-label={release ? `Khoảng kết quả: ${bands[release.eligibleBand]}` : 'Chưa có kết quả'}>
          {release ? release.eligibleBand : '—'}
        </div>
        {release && <p className="p30-agg-band-label" role="status">{bands[release.eligibleBand]}</p>}
        <p role="status" aria-live="polite" aria-atomic="true">{command.busy ? 'Đang tải thống kê được bảo vệ…' : ''}</p>
        <p className="p30-agg-caption">Băng được tính khi ứng viên đáp ứng toàn bộ kỹ năng bắt buộc.</p>
        <p className="p30-agg-caption">Mỗi doanh nghiệp, trường, kỳ và batch chỉ có một bản phát hành.</p>
        <div className="button-row">
          <button className="t5-btn-teal" disabled={command.blocked} aria-busy={command.busy} onClick={() => {
            setRelease(null);setError('');void command.submit('/matching/evaluate', 'POST', {
              demandId: demand.demandId, expectedDemandVersion: demand.version, expectedCriteriaVersion: demand.criteriaVersion,
            });
          }}>{command.busy ? 'Đang tải thống kê…' : 'Xem thống kê đối chiếu'}</button>
          {command.retryable && <button disabled={command.busy} aria-busy={command.busy} onClick={() => void command.retry()}>
            {command.busy ? 'Đang thử lại…' : 'Thử lại yêu cầu thống kê đã gửi'}
          </button>}
        </div>
      </div>
      <div className="p30-agg-privacy-card">
        <h4>Phạm vi bảo vệ</h4>
        <span className="p30-k-badge">k = 5</span>
        <p className="p30-agg-caption">Băng: SUPPRESSED, 5–9, 10–19 hoặc 20+.</p>
        <p className="p30-agg-caption">Không có số đếm chính xác, tỷ lệ, phân tích theo kỹ năng, danh sách hay thông tin nhận diện.</p>
        <p className="p30-agg-caption">Thay nhu cầu hoặc tiêu chí không mở khóa batch đã phát hành. Batch do vận hành quản lý.</p>
      </div>
    </div>
  </section>;
}
