import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Icon } from '../../../components';
import { lookupEnterpriseTaxCode, type TaxAssessment } from './taxApi';
import { validateTaxCodeInput } from './taxCode';
import type { VerificationInput } from './types';
import { TaxSourceMeta } from './TaxSourceMeta';
import './tax-verification.css';

export function TaxLookupField({ org, data, busy, update, children }: { org: string; data: VerificationInput; busy: boolean;
  update: (apply: (current: VerificationInput) => VerificationInput) => void; children?: ReactNode }) {
  const validation = validateTaxCodeInput(data.registrationCode);
  const inputId = useId();
  const [result, setResult] = useState<TaxAssessment | null>(null), [loading, setLoading] = useState(false), [error, setError] = useState('');
  const [applied, setApplied] = useState(false);
  const pending = useRef<AbortController | null>(null);
  useEffect(() => {
    pending.current?.abort(); setResult(null); setError(''); setLoading(false); setApplied(false);
    return () => pending.current?.abort();
  }, [org, data.registrationCode]);
  async function lookup() {
    pending.current?.abort(); const controller = new AbortController(); pending.current = controller;
    setLoading(true); setError(''); setResult(null); setApplied(false);
    try {
      const found = await lookupEnterpriseTaxCode(org, data.registrationCode, controller.signal);
      if (controller.signal.aborted) return;
      if (found.normalizedTaxCode !== validation.normalizedTaxCode) throw new Error('Mismatched tax code');
      setResult(found);
      if (found.lookupStatus === 'FOUND' && found.taxpayerStatus === 'ACTIVE') update(current => current.registrationCode !== data.registrationCode ? current : ({ ...current,
        registeredName: current.registeredName.trim() || (found.officialName?.length || 0) > 200 ? current.registeredName : found.officialName || '',
        address: current.address.trim() || (found.officialAddress?.length || 0) > 500 ? current.address : found.officialAddress || '' }));
    } catch { if (!controller.signal.aborted) setError('Chưa tra cứu được MST. Bạn vẫn có thể lưu hồ sơ và đối chiếu minh chứng thủ công.'); }
    finally { if (!controller.signal.aborted) setLoading(false); }
  }
  const canApply = result?.lookupStatus === 'FOUND' && result.taxpayerStatus === 'ACTIVE'
    && (result.officialName?.length || 0) <= 200 && (result.officialAddress?.length || 0) <= 500
    && (data.registeredName !== result.officialName || data.address !== result.officialAddress);
  const validationText = validation.validEnterprise ? 'Định dạng MST hợp lệ. Tra cứu để kiểm tra thông tin doanh nghiệp.'
    : validation.codeKind === 'PERSONAL_ID_12' ? 'Mã 12 số cần đối chiếu hồ sơ. Tra cứu này dành cho MST doanh nghiệp.'
    : validation.checksumValid === false ? 'Mã số thuế chưa hợp lệ. Vui lòng kiểm tra lại các chữ số.'
    : 'Chưa nhận diện được MST. Bạn vẫn có thể lưu hồ sơ để đối chiếu thủ công.';
  const found = result?.lookupStatus === 'FOUND';
  const tone = found && result.taxpayerStatus === 'ACTIVE' ? 'success' : found && ['SUSPENDED', 'DISSOLVED'].includes(result.taxpayerStatus) ? 'danger' : 'neutral';
  return <div className="tax-lookup-section">
    <div className="cooperation-form-grid"><div className="tax-lookup-field" aria-busy={loading}>
    <label htmlFor={inputId}>Mã đăng ký doanh nghiệp</label>
    <div className="tax-lookup-control"><input id={inputId} required maxLength={64} value={data.registrationCode}
      placeholder="Nhập mã số thuế doanh nghiệp" autoComplete="off" aria-describedby={inputId + '-help'}
      onChange={event => update(current => ({ ...current, registrationCode: event.target.value }))} />
    <button type="button" className="secondary" disabled={busy || loading || !validation.validEnterprise} onClick={() => void lookup()}>
      <Icon name="search" size={16} />{loading ? 'Đang tra cứu MST…' : 'Tra cứu MST'}</button></div>
    <p id={inputId + '-help'} className={'tax-validation' + (validation.checksumValid === false ? ' is-warning' : '')} role="status">
      {data.registrationCode ? validationText : 'Tra cứu MST để lấy tên và địa chỉ doanh nghiệp.'}</p>
    {error && <p className="tax-validation is-warning" role="status">{error}</p>}
    </div>{children}</div>
    {result && <div className={'tax-lookup-preview tax-tone-' + tone} role="status">
      <div className="tax-result-heading"><span className="tax-result-icon" aria-hidden="true"><Icon name={tone === 'success' ? 'check' : tone === 'danger' ? 'alert' : 'info'} size={19} /></span>
        <strong>{found ? 'Đã tìm thấy thông tin doanh nghiệp' : result.lookupStatus === 'NOT_FOUND' ? 'Chưa tìm thấy mã số thuế' : 'Chưa tra cứu được thông tin'}</strong>
        {found && <span className="tax-result-badge">{result.taxpayerStatus === 'ACTIVE' ? 'Đang hoạt động' : result.taxpayerStatus === 'SUSPENDED' ? 'Tạm ngừng' : result.taxpayerStatus === 'DISSOLVED' ? 'Đã giải thể' : 'Chưa rõ trạng thái'}</span>}
      </div>
      {result.provider === 'mock' && <strong>Dữ liệu minh họa ngoại tuyến — chưa xác minh từ cơ quan thuế</strong>}
      {(result.officialName || result.officialAddress) && <dl className="tax-result-fields">
        {result.officialName && <div><dt>Tên doanh nghiệp</dt><dd>{result.officialName}</dd></div>}
        {result.officialAddress && <div><dt>Địa chỉ đăng ký</dt><dd>{result.officialAddress}</dd></div>}
      </dl>}
      <p>{result.taxpayerStatusLabel || (result.lookupStatus === 'NOT_FOUND' ? 'Chưa tìm thấy MST trong nguồn tra cứu. Kiểm tra minh chứng thủ công.' : 'Nguồn tra cứu chưa sẵn sàng. Kiểm tra minh chứng thủ công.')}</p>
      {['SUSPENDED', 'DISSOLVED'].includes(result.taxpayerStatus) && <p>Cảnh báo: doanh nghiệp không ở trạng thái hoạt động; cần kiểm tra lại hồ sơ.</p>}
      {((result.officialName?.length || 0) > 200 || (result.officialAddress?.length || 0) > 500) && <p>Thông tin tra cứu vượt độ dài trường nhập. Vui lòng đối chiếu và nhập nội dung phù hợp; hệ thống không tự cắt thông tin pháp lý.</p>}
      {found && result.taxpayerStatus === 'ACTIVE' && <div className="tax-apply-row">
        <p>{applied && !canApply ? 'Đã áp dụng thông tin tra cứu. Kiểm tra lại và lưu hồ sơ để ghi nhận thay đổi.'
          : canApply ? 'Thông tin đã nhập được giữ nguyên. Chọn áp dụng nếu bạn muốn dùng tên và địa chỉ từ kết quả tra cứu.'
          : 'Hệ thống chỉ tự điền ô còn trống. Kiểm tra thông tin trước khi lưu hồ sơ.'}</p>
        {canApply && <button type="button" className="secondary" disabled={busy} onClick={() => {
          update(current => ({ ...current, registeredName: result.officialName || '', address: result.officialAddress || '' })); setApplied(true);
        }}>Áp dụng thông tin thuế</button>}
      </div>}
      <TaxSourceMeta provider={result.provider} checkedAt={result.checkedAt} />
      <p className="tax-review-note">Kết quả tra cứu hỗ trợ chuẩn bị hồ sơ. Nhà trường sẽ kiểm tra minh chứng và quyết định xác minh.</p>
    </div>}
  </div>;
}
