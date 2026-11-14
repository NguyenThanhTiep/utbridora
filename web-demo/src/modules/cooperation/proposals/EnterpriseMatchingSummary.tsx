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
  }, () => {setRelease(null);setError('Chưa truy cập được thống kê đối chiếu. Kiểm tra quyền hoặc chờ dịch vụ thống kê sẵn sàng.');}, {guardNavigation: false});
  const requiredSkills = demand.data.required.skills.map(s => s.skillCode).filter(Boolean).join(' · ') || 'Chưa cấu hình';
  const preferredSkills = demand.data.preferred.map(s => s.skillCode).filter(Boolean).join(' · ') || 'Không có';
  return <section className="proposal-workspace p30-agg-section" aria-label="Thống kê đối chiếu tổng hợp">
    <div className="p30-info-banner">
      <strong>Protected aggregate · kết quả đã phát hành</strong>
      <p>Không có PII hoặc khả năng mở danh sách sinh viên từ thống kê.</p>
    </div>
    <h3>Thống kê đối chiếu tổng hợp</h3>
    <p>Kết quả được công bố theo khoảng số lượng; không hiển thị danh tính sinh viên.</p>
    <Notice message={error || command.message} tone="error" />
    <div className="p30-agg-grid">
      <div className="p30-agg-main-card" aria-busy={command.busy}>
        <h4>Kết quả tổng hợp được bảo vệ</h4>
        <div className="p30-agg-band-value" aria-label={release ? `Khoảng kết quả: ${bands[release.eligibleBand]}` : 'Chưa có kết quả'}>
          {release ? release.eligibleBand : '—'}
        </div>
        {release && <p role="status">Số ứng viên phù hợp: <strong>{bands[release.eligibleBand]}</strong></p>}
        <p role="status" aria-live="polite" aria-atomic="true">{command.busy ? 'Đang tải thống kê được bảo vệ…' : ''}</p>
        <p className="p30-agg-caption">Khoảng số lượng phù hợp với toàn bộ kỹ năng REQUIRED.</p>
        <p className="p30-agg-caption">Một lần phát hành theo doanh nghiệp – trường – kỳ.</p>
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
        <h4>Quyền riêng tư</h4>
        <span className="p30-k-badge">k = 5</span>
        <p className="p30-agg-caption">Không có danh sách sinh viên, tổng, tỷ lệ hay breakdown kỹ năng.</p>
      </div>
    </div>
    <div className="p30-agg-demand-card">
      <h4>Nhu cầu đang áp dụng</h4>
      <dl className="p30-kv-list">
        <div><dt>Vị trí</dt><dd>{demand.data.title}</dd></div>
        <div><dt>Kỹ năng REQUIRED</dt><dd>{requiredSkills} — phải đạt tất cả</dd></div>
        <div><dt>Kỹ năng PREFERRED</dt><dd>{preferredSkills} — chỉ dùng xếp hạng</dd></div>
      </dl>
    </div>
  </section>;
}
