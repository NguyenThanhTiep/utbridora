import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { Icon } from '../../../components';
import { fetchSchoolTaxCheck, type VerificationTaxCheck } from './taxApi';
import type { Verification } from './types';
import { TaxSourceMeta } from './TaxSourceMeta';
import './tax-verification.css';

const verdicts = {
  MATCHED_ACTIVE: ['success', 'Thông tin phù hợp — doanh nghiệp đang hoạt động'],
  INFO_MISMATCH: ['warning', 'Thông tin khai báo có sai lệch cần đối chiếu'],
  INACTIVE_OR_INVALID: ['danger', 'MST không hợp lệ hoặc doanh nghiệp không hoạt động'],
  UNVERIFIED_OFFLINE: ['neutral', 'Chưa xác minh được — kiểm tra minh chứng thủ công'],
} as const;
export function TaxAssessmentPanel({ org, record, busy, requestInfo }: { org: string; record: Verification; busy: boolean; requestInfo: (fields: string[]) => void }) {
  const identity = `${org}:${record.verificationId}:${record.submissionNo}:${record.version}`;
  const [slot, setSlot] = useState<{ key: string; check: VerificationTaxCheck } | null>(null);
  const check = slot?.key === identity ? slot.check : null;
  const pending = check?.state === 'PENDING' || check?.state === 'PROCESSING';
  const data = pending ? null : check?.assessment;
  const [loading, setLoading] = useState(true), [refresh, setRefresh] = useState(0);
  const [command, setCommand] = useState<{ key: string; value: number } | null>(null);
  const [denied, setDenied] = useState(false);
  useEffect(() => {
    const controller = new AbortController(); let timer: ReturnType<typeof setTimeout> | undefined;
    setSlot(null); setLoading(true); setDenied(false);
    const recheck = command?.key === identity && command.value === refresh;
    async function load(restart = false) {
      try {
        const result = await fetchSchoolTaxCheck(org, record.verificationId, controller.signal, restart);
        if (controller.signal.aborted) return;
        if (result.verificationId !== record.verificationId || record.submissionNo != null && result.submissionNo !== record.submissionNo) throw new Error('Stale assessment');
        setSlot({ key: identity, check: result });
        if (result.state === 'PENDING' || result.state === 'PROCESSING') {
          const delay = result.nextAttemptAt ? Math.min(30000, Math.max(3000, Date.parse(result.nextAttemptAt) - Date.now())) : 3000;
          timer = setTimeout(() => void load(), delay);
        }
      } catch (error) {
        if (!controller.signal.aborted) { setSlot(null); setDenied(error instanceof ApiError && [401, 403, 404].includes(error.status)); }
      } finally { if (!controller.signal.aborted) setLoading(false); }
    }
    void load(recheck);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [org, record.verificationId, record.submissionNo, record.version, identity, refresh, command]);
  const [tone, summary] = data ? verdicts[data.verdict] : ['neutral', denied ? 'Không còn quyền đọc kết quả đối chiếu trong phạm vi này.' : 'Chưa tải được kết quả đối chiếu. Chọn Kiểm tra lại MST để thử lại.'];
  return <section className="tax-assessment" aria-label="Trợ lý đối chiếu Mã số thuế">
    <div className="tax-assessment-heading"><div><h4>Trợ lý đối chiếu Mã số thuế</h4><p className="tax-section-hint">So sánh thông tin doanh nghiệp trước khi xét duyệt hồ sơ.</p></div><button type="button" className="secondary" disabled={loading || busy || pending} onClick={() => {
      setCommand({ key: identity, value: refresh + 1 }); setRefresh(n => n + 1);
    }}><Icon name="refresh" size={16} />Kiểm tra lại MST</button></div>
    {loading ? <p role="status">Đang tải kết quả đối chiếu…</p> : pending ? <div role="status" className="tax-verdict tax-tone-neutral">
      <strong>{check.attemptCount > 0 && check.state === 'PENDING' ? 'Nguồn tra cứu tạm thời chưa sẵn sàng — hệ thống sẽ thử lại.' : 'Hệ thống đang tự động kiểm tra thông tin doanh nghiệp.'}</strong>
      <p>Kết quả sẽ tự cập nhật tại đây. Hồ sơ vẫn chờ cán bộ nhà trường xét duyệt.</p>
    </div> : <>
      {check && <p className="tax-check-meta">Kết quả lưu cho lần gửi {check.submissionNo}{check.completedAt && ` · Hoàn tất ${new Date(check.completedAt).toLocaleString('vi-VN')}`}</p>}
      <div role="status" className={'tax-verdict tax-tone-' + tone}><div className="tax-result-heading">
        <span className="tax-result-icon" aria-hidden="true"><Icon name={tone === 'success' ? 'check' : tone === 'danger' || tone === 'warning' ? 'alert' : 'info'} size={19} /></span><strong>{summary}</strong>
        </div>
        {data?.taxpayerStatusLabel && <p>{data.taxpayerStatusLabel}</p>}
        {data?.taxDepartment && <p>Cơ quan thuế: {data.taxDepartment}</p>}
        {data && <TaxSourceMeta provider={data.provider} checkedAt={data.checkedAt} />}
        {!!data?.reasons.length && <details className="tax-result-details"><summary>Chi tiết đối chiếu</summary><ul>{data.reasons.map(reason => <li key={reason}>{reason}</li>)}</ul></details>}
      </div>
      {data?.lookupStatus === 'FOUND' && <div className="tax-comparison" role="table" aria-label="So sánh thông tin thuế">
        <div role="row" className="tax-comparison-head"><span role="columnheader">Thông tin</span><span role="columnheader">Doanh nghiệp khai</span><span role="columnheader">Dữ liệu tra cứu MST</span></div>
        {([['registeredName', 'Tên doanh nghiệp', record.data.registeredName, data.officialName], ['address', 'Địa chỉ trụ sở', record.data.address, data.officialAddress]] as const).map(([key, label, declared, official]) =>
          <div role="row" key={key} className={data.mismatchedFields.includes(key) ? 'is-mismatch' : ''}>
            <div role="rowheader"><b>{label}</b><small className={'tax-match-label' + (data.mismatchedFields.includes(key) ? ' is-warning' : '')}>{!official ? 'Chưa đủ dữ liệu' : data.mismatchedFields.includes(key) ? 'Cần đối chiếu' : 'Thông tin phù hợp'}</small></div>
            <span role="cell" data-label="Doanh nghiệp khai">{declared}</span><span role="cell" data-label="Dữ liệu tra cứu MST">{official || '—'}</span></div>)}
      </div>}
      {!busy && record.state === 'SUBMITTED' && data && ['INFO_MISMATCH', 'INACTIVE_OR_INVALID'].includes(data.verdict) && <button type="button" className="secondary"
        onClick={() => requestInfo(data.verdict === 'INACTIVE_OR_INVALID' ? ['registrationCode'] : data.mismatchedFields.filter(field => ['registeredName', 'address'].includes(field)))}>Chọn mục sai lệch để yêu cầu bổ sung</button>}
    </>}
    <p className="tax-review-note">Kết quả tra cứu hỗ trợ đối chiếu. Quyết định xác minh hồ sơ do nhà trường thực hiện.</p>
  </section>;
}
