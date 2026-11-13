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
  return <section className="proposal-workspace" aria-label="Thống kê đối chiếu tổng hợp"><h3>Thống kê đối chiếu tổng hợp</h3>
    <p>Kết quả được công bố theo khoảng số lượng; không hiển thị danh tính sinh viên.</p>
    <Notice message={error || command.message} tone="error" />
    <button className="secondary" disabled={command.blocked} onClick={() => {
      setRelease(null);setError('');void command.submit('/matching/evaluate', 'POST', {
        demandId: demand.demandId, expectedDemandVersion: demand.version, expectedCriteriaVersion: demand.criteriaVersion,
      });
    }}>Xem thống kê đối chiếu</button>
    {command.retryable && <button disabled={command.busy} onClick={() => void command.retry()}>Thử lại yêu cầu thống kê đã gửi</button>}
    {release && <p role="status">Số ứng viên phù hợp: <strong>{bands[release.eligibleBand]}</strong></p>}
  </section>;
}
