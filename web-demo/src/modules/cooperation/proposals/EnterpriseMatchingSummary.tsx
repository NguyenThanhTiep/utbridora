import { useState } from 'react';
import { ApiError } from '../../../api';
import { Icon, Notice } from '../../../components';
import type { Demand } from '../types';
import { workflowError } from './api';
import { useWorkflowCommand } from './useWorkflowCommand';
import './protected-statistics.css';

type Release = { releaseId: string; matchRunId: string; eligibleBand: 'SUPPRESSED' | '5-9' | '10-19' | '20+' };
type Evaluation = { audience: string; release?: Release };
const bands = { SUPPRESSED: 'Chưa đủ ngưỡng để công bố', '5-9': '5–9 ứng viên', '10-19': '10–19 ứng viên', '20+': 'Từ 20 ứng viên' };
const scaleSteps: { code: Release['eligibleBand']; short: string; label: string }[] = [
  { code: 'SUPPRESSED', short: '< 5', label: 'Ẩn số lượng (k < 5)' },
  { code: '5-9', short: '5–9', label: '5–9 ứng viên' },
  { code: '10-19', short: '10–19', label: '10–19 ứng viên' },
  { code: '20+', short: '20+', label: 'Từ 20 ứng viên' },
];
const exposedErrors = new Set([
  'PRIVACY_RELEASE_LOCKED', 'PRIVACY_RELEASE_UNAVAILABLE', 'CRITERIA_STALE',
  'DEMAND_LOCKED', 'CATALOG_CHANGED', 'FEATURE_DISABLED',
]);

/** T4 owns release, threshold and ledger. This consumer never calculates counts from school results. */
export function EnterpriseMatchingSummary({ org, demand, showDemandContext = true }: { org: string; demand: Demand; showDemandContext?: boolean }) {
  const [release, setRelease] = useState<Release | null>(null), [error, setError] = useState('');
  const command = useWorkflowCommand<Evaluation>(org, result => {
    const r = result?.release;
    if (!result || result.audience !== 'ENTERPRISE' || !r || !r.releaseId || !r.matchRunId || !Object.hasOwn(bands, r.eligibleBand)
        || Object.keys(result).some(k => !['audience', 'release'].includes(k))
        || Object.keys(r).some(k => !['releaseId', 'matchRunId', 'eligibleBand'].includes(k))) {
      setRelease(null);setError('Chưa nhận được bản thống kê được bảo vệ hợp lệ.');return;
    }
    setRelease(r);setError('');
  }, failure => {
    setRelease(null);
    setError(failure instanceof ApiError && exposedErrors.has(failure.errorCode || '')
      ? workflowError(failure)
      : 'Chưa truy cập được thống kê đối chiếu. Kiểm tra quyền hoặc chờ dịch vụ thống kê sẵn sàng.');
  }, {guardNavigation: false});
  const requiredSkills = demand.data.required.skills.map(s => s.skillCode).filter(Boolean).join(' · ') || 'Chưa cấu hình';
  const preferredSkills = demand.data.preferred.map(s => s.skillCode).filter(Boolean).join(' · ') || 'Không có';
  return <section className="proposal-workspace p30-agg-section" aria-label="Thống kê đối chiếu tổng hợp">
    <div className="p30-info-banner"><Icon name="shield" size={21} /><div>
      <strong>Thống kê theo khoảng số lượng</strong>
      <p>Kết quả đối chiếu được công bố theo phạm vi bảo vệ danh tính sinh viên.</p>
    </div>
    </div>
    <h3>Thống kê đối chiếu tổng hợp</h3>
    <Notice message={error || command.message} tone="error" />
    <div className="p30-agg-grid">
      <div className="p30-agg-main-card" aria-busy={command.busy}>
        <div className="statistics-result-heading"><h4>Kết quả tổng hợp được bảo vệ</h4><Icon name="users" size={21} /></div>
        <div className={'p30-agg-band-value' + (release?.eligibleBand === 'SUPPRESSED' ? ' is-suppressed' : '')} aria-label={release ? `Khoảng kết quả: ${bands[release.eligibleBand]}` : 'Chưa có kết quả'}>
          {release ? release.eligibleBand === 'SUPPRESSED' ? 'Được bảo vệ' : release.eligibleBand.replace('-', '–') : '—'}
        </div>
        {release ? <p role="status">Số ứng viên phù hợp: <strong>{bands[release.eligibleBand]}</strong></p>
          : !command.busy && <p className="p30-agg-hint">Chưa có kết quả. Bấm “Xem thống kê đối chiếu” để kiểm tra nhu cầu đang chọn.</p>}
        <ul className="p30-band-scale" aria-label="Thang khoảng công bố theo ngưỡng k = 5">
          {scaleSteps.map(step => <li key={step.code} className={'p30-band-step' + (release?.eligibleBand === step.code ? ' is-active' : '') + (step.code === 'SUPPRESSED' ? ' is-suppressed' : '')} title={step.label}>
            <strong>{step.short}</strong><span>{step.code === 'SUPPRESSED' ? 'Ẩn' : 'ứng viên'}</span>
          </li>)}
        </ul>
        <p role="status" aria-live="polite" aria-atomic="true">{command.busy ? 'Đang tải thống kê được bảo vệ…' : ''}</p>
        <p className="p30-agg-caption">Ứng viên được đối chiếu với toàn bộ tiêu chí bắt buộc của nhu cầu.</p>
        <div className="button-row">
          <button className="t5-btn-teal" disabled={command.blocked} aria-busy={command.busy} onClick={() => {
            setRelease(null);setError('');void command.submit('/matching/evaluate', 'POST', {
              demandId: demand.demandId, expectedDemandVersion: demand.version, expectedCriteriaVersion: demand.criteriaVersion,
            });
          }}>{command.busy ? 'Đang tải thống kê…' : 'Xem thống kê đối chiếu'}</button>
          {release && <a className="button secondary p30-csv-link" href={`/api/v1/matching/runs/${encodeURIComponent(release.matchRunId)}/summary.csv`} download="matching-summary.csv">
            Tải CSV tóm tắt
          </a>}
          {command.retryable && <button disabled={command.busy} aria-busy={command.busy} onClick={() => void command.retry()}>
            {command.busy ? 'Đang thử lại…' : 'Thử lại yêu cầu thống kê đã gửi'}
          </button>}
        </div>
      </div>
      <div className="p30-agg-privacy-card"><span className="statistics-privacy-icon" aria-hidden="true"><Icon name="lock" size={23} /></span>
        <h4>Quyền riêng tư</h4>
        <span className="p30-k-badge">Ngưỡng công bố: 5 ứng viên</span>
        <p>Danh tính và hồ sơ sinh viên không xuất hiện trong thống kê.</p>
        <p className="p30-agg-caption">Mỗi doanh nghiệp chỉ nhận một bản công bố cho cùng trường, kỳ và đợt dữ liệu.</p>
      </div>
    </div>
    {showDemandContext && <div className="p30-agg-demand-card">
      <h4>Nhu cầu đang áp dụng</h4>
      <dl className="p30-kv-list">
        <div><dt>Vị trí</dt><dd>{demand.data.title}</dd></div>
        <div><dt>Kỹ năng bắt buộc</dt><dd>{requiredSkills} — phải đạt tất cả</dd></div>
        <div><dt>Kỹ năng ưu tiên</dt><dd>{preferredSkills} — chỉ dùng xếp hạng</dd></div>
      </dl>
    </div>}
  </section>;
}
