import { useEffect, useId, useRef, useState } from 'react';
import { api, ApiError } from '../../../api';
import { Notice } from '../../../components';
import { askConfirm } from '../../../confirm';
import { readAll } from './data';
import { workflowError } from '../proposals/api';

type Term = { termId: string; termCode: string; schoolOrgId: string; status: string };
type Job = {
  importJobId: string; jobVersion: number; jobState: string; fileHash: string; termId: string; previewExpiresAt: string;
  totalRows: number; validRows: number; invalidRows: number; previewNeedsReviewRows: number; selectedRows: number;
  processedRows: number; successRows: number; needsReviewRows: number; permanentFailedRows: number; transientFailedRows: number;
};
type Row = {
  rowId: string; rowNo: number; rowVersion: number; sourceData: { studentCode: string | null; fullName: string | null; email: string | null };
  validationState: string; selected: boolean; rowState: string; errorCodes: string[]; activationState: string;
  invitationVersion: number | null; mailState: string;
};
type Rows = { content: Row[]; page: number; totalElements: number; totalPages: number };
type Preview = { job: Job; rows: Rows };
const base = '/school/student-imports';
const labels: Record<string, string> = {
  PREVIEW_READY: 'Chờ xác nhận', EXPIRED: 'Bản xem trước hết hạn', PROCESSING: 'Đang xử lý', COMPLETED: 'Nhập danh sách hoàn tất',
  COMPLETED_WITH_ERRORS: 'Hoàn tất, có dòng cần xử lý', FAILED: 'Thất bại', VALID: 'Hợp lệ', INVALID: 'Không hợp lệ', NEEDS_REVIEW: 'Cần xác minh',
  NOT_SELECTED: 'Chưa chọn', QUEUED: 'Đang chờ', PROVISIONED: 'Đã chuẩn bị kích hoạt', ALREADY_LINKED: 'Đã liên kết', PERMANENT_FAILED: 'Lỗi dữ liệu',
  TRANSIENT_FAILED: 'Chờ thử lại', NOT_REQUIRED: 'Không cần', NOT_READY: 'Chưa sẵn sàng', SENDING: 'Đang gửi', RETRY_WAIT: 'Chờ gửi lại',
  SENT: 'Máy chủ thư đã nhận', SUPERSEDED: 'Đã thay thế', CANCELLED: 'Đã hủy', PENDING: 'Chờ kích hoạt', ACTIVE: 'Đã kích hoạt', BLOCKED: 'Bị chặn',
};
const errors: Record<string, string> = {
  STUDENT_CODE_REQUIRED: 'Thiếu MSSV', STUDENT_CODE_MUST_BE_TEXT: 'Ô MSSV phải là văn bản', STUDENT_CODE_INVALID: 'MSSV không hợp lệ',
  EMAIL_REQUIRED: 'Thiếu email', EMAIL_INVALID: 'Email không hợp lệ', FULL_NAME_REQUIRED: 'Thiếu họ tên', FULL_NAME_INVALID: 'Họ tên không hợp lệ',
  MAJOR_INVALID: 'Mã ngành không thuộc danh mục đang dùng', TERM_CODE_INVALID: 'Mã kỳ không khớp kỳ đã chọn', DUPLICATE_IN_FILE: 'Trùng MSSV hoặc email trong tệp',
  IDENTITY_CONFLICT: 'Danh tính cần xác minh', ACCOUNT_BLOCKED: 'Tài khoản bị chặn', MEMBERSHIP_REVOKED: 'Quyền sinh viên đã bị thu hồi',
  ROSTER_METADATA_CONFLICT: 'Thông tin không khớp danh sách hiện có', TRANSIENT_DEPENDENCY: 'Dịch vụ tạm thời gián đoạn',
  AUTHORITY_REVOKED: 'Quyền thực hiện đã bị thu hồi', REFERENCE_DATA_CHANGED: 'Danh mục hoặc kỳ đã thay đổi', PERMANENT_VALIDATION: 'Dữ liệu không hợp lệ',
};
const label = (value: string) => labels[value] || value;

export function StudentRosterPanel({ org, viewerId }: { org: string; viewerId: string }) {
  const id = useId();
  const [terms, setTerms] = useState<Term[]>([]), [term, setTerm] = useState(''), [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController(); setTerms([]); setTerm(''); setError('');
    void readAll<Term>(org, '/terms?' + new URLSearchParams({ schoolOrgId: org }), controller.signal)
      .then(items => { if (!controller.signal.aborted) setTerms(items.filter(row => row.schoolOrgId === org && row.status === 'ACTIVE')); })
      .catch(reason => { if (!controller.signal.aborted) setError(workflowError(reason)); });
    return () => controller.abort();
  }, [org, viewerId]);
  return <section className="panel school-roster-panel" aria-label="Nhập danh sách & Kích hoạt sinh viên">
    <h2>Nhập danh sách & Kích hoạt sinh viên</h2>
    <p>Nhập CSV UTF-8 hoặc XLSX một trang tính, tối đa 5 MB và 5.000 dòng. MSSV trong Excel phải là văn bản để giữ số 0 đầu và chữ hoa, chữ thường.</p>
    <p>Cột bắt buộc: <code>studentCode,email,fullName,majorCode,termCode</code>. Cột tùy chọn: <code>cohortCode,classCode,schoolOrgId</code>.</p>
    <p>Cần ủy quyền nhập danh sách trong trường và kỳ hiện hành. Dữ liệu nhập không tự xác nhận điều kiện thực tập.</p>
    <label htmlFor={id}>Kỳ nhập danh sách</label><select id={id} value={term} onChange={event => setTerm(event.target.value)}>
      <option value="">Chọn kỳ đang hoạt động</option>{terms.map(row => <option key={row.termId} value={row.termId}>{row.termCode}</option>)}
    </select>
    {error && <Notice message={error} tone="error" />}
    {term && <ImportSession key={viewerId + ':' + org + ':' + term} org={org} term={term} />}
  </section>;
}

function ImportSession({ org, term }: { org: string; term: string }) {
  const id = useId(), lifetime = useRef<AbortController | null>(null), attempts = useRef(new Map<string, string>());
  const [file, setFile] = useState<File | null>(null), [previewKey, setPreviewKey] = useState(() => crypto.randomUUID());
  const [job, setJob] = useState<Job | null>(null), [rows, setRows] = useState<Rows | null>(null), [selected, setSelected] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(0), [filter, setFilter] = useState(''), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false), [trackingId, setTrackingId] = useState(''), [expired, setExpired] = useState(false), [revision, setRevision] = useState(0);
  useEffect(() => {
    const controller = new AbortController(); lifetime.current = controller;
    const expire = () => { controller.abort(); setExpired(true); setJob(null); setRows(null); setSelected(new Set()); setFile(null); attempts.current.clear(); };
    window.addEventListener('session-expired', expire);
    return () => { controller.abort(); window.removeEventListener('session-expired', expire); };
  }, []);
  const jobId = job?.importJobId;
  useEffect(() => {
    if (!jobId || expired) return;
    const controller = new AbortController(); let running = false;
    const refresh = async () => {
      if (running || document.hidden) return; running = true;
      try {
        const [next, values] = await Promise.all([
          api<Job>(base + '/' + jobId, { signal: controller.signal }),
          api<Rows>(base + '/' + jobId + '/rows?' + new URLSearchParams({ page: String(page), size: '25', ...(filter ? { [filter.startsWith('mail:') ? 'mailState' : 'validationState']: filter.replace(/^mail:/, '') } : {}) }), { signal: controller.signal }),
        ]);
        if (!controller.signal.aborted) {
          if (next.termId !== term) throw new Error('Đợt nhập không thuộc kỳ đang chọn.');
          setJob(next); setRows(values);
        }
      } catch (reason) { if (!controller.signal.aborted) { setError(workflowError(reason)); if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) { setJob(null); setRows(null); setSelected(new Set()); } } }
      finally { running = false; }
    };
    void refresh(); const timer = window.setInterval(() => { void refresh(); }, 5000);
    const visible = () => { void refresh(); }; window.addEventListener('focus', visible); document.addEventListener('visibilitychange', visible);
    return () => { controller.abort(); window.clearInterval(timer); window.removeEventListener('focus', visible); document.removeEventListener('visibilitychange', visible); };
  }, [jobId, page, filter, revision, term, expired]);
  function key(path: string, body: object) {
    const fingerprint = path + ':' + JSON.stringify(body); let value = attempts.current.get(fingerprint);
    if (!value) { value = crypto.randomUUID(); attempts.current.set(fingerprint, value); } return value;
  }
  async function run(action: () => Promise<void>) {
    setBusy(true); setError(''); setNotice('');
    try { await action(); }
    catch (reason) { if (!lifetime.current?.signal.aborted) {
      const local = reason instanceof Error && !(reason instanceof ApiError) ? reason.message : workflowError(reason);
      setError(local + (reason instanceof ApiError ? ' ' + reason.fieldErrors.map(field => field.message).join(' ') : '') + (reason instanceof ApiError && reason.retryAfterSeconds !== null ? ' Có thể thử lại sau ' + reason.retryAfterSeconds + ' giây.' : ''));
      if (reason instanceof ApiError && reason.status === 409) setRevision(old => old + 1);
      if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) { setJob(null); setRows(null); setSelected(new Set()); }
    } }
    finally { if (!lifetime.current?.signal.aborted) setBusy(false); }
  }
  async function command(path: string, body: object) {
    return api(path, { method: 'POST', body: JSON.stringify(body), signal: lifetime.current?.signal, headers: { 'Idempotency-Key': key(path, body) } });
  }
  const selectable = job?.jobState === 'PREVIEW_READY' && Date.now() < Date.parse(job.previewExpiresAt);
  return <div aria-busy={busy}>
    {expired ? <Notice message="Phiên đã hết hạn. Đăng nhập lại để tiếp tục." tone="error" /> : <>
      <label htmlFor={id + '-file'}>Tệp danh sách sinh viên</label><input id={id + '-file'} type="file" accept=".csv,.xlsx" disabled={busy}
        onChange={event => { setFile(event.target.files?.[0] || null); setPreviewKey(crypto.randomUUID()); }} />
      <button disabled={!file || busy} onClick={() => { void run(async () => {
        if (!file || file.size > 5 * 1024 * 1024) throw new Error('Chọn tệp không quá 5 MB.');
        const form = new FormData(); form.set('file', file); form.set('termId', term);
        const value = await api<Preview>(base + '/preview', { method: 'POST', body: form, signal: lifetime.current?.signal, headers: { 'Idempotency-Key': previewKey, 'X-Organization-Id': org } });
        if (lifetime.current?.signal.aborted) return;
        setJob(value.job); setRows(value.rows); setSelected(new Set()); setPage(0); setFilter(''); setTrackingId(value.job.importJobId);
      }); }}>Kiểm tra và xem trước</button>
      <div className="button-row"><label htmlFor={id + '-job'}>Mã đợt nhập cần theo dõi</label><input id={id + '-job'} value={trackingId} onChange={event => setTrackingId(event.target.value)} disabled={busy} />
        <button className="secondary" disabled={busy || !trackingId} onClick={() => { void run(async () => {
          const value = await api<Job>(base + '/' + encodeURIComponent(trackingId.trim()), { signal: lifetime.current?.signal });
          if (value.termId !== term) throw new Error('Đợt nhập không thuộc kỳ đang chọn.');
          if (!lifetime.current?.signal.aborted) { setJob(value); setSelected(new Set()); setPage(0); setFilter(''); setRevision(old => old + 1); }
        }); }}>Theo dõi đợt nhập</button></div>
      {error && <Notice message={error} tone="error" />}{notice && <Notice message={notice} tone="success" />}
      {job && <>
        <h3>{label(job.jobState)}</h3><p>Mã đợt: <code>{job.importJobId}</code></p>
        <p>Tệp có {job.totalRows} dòng: {job.validRows} hợp lệ, {job.invalidRows} không hợp lệ, {job.previewNeedsReviewRows} cần xác minh.</p>
        <p>Đã chọn {job.selectedRows}; đã xử lý {job.processedRows}; thành công {job.successRows}; cần xác minh {job.needsReviewRows}; lỗi dữ liệu {job.permanentFailedRows}; chờ thử lại {job.transientFailedRows}.</p>
        <p>Nhập danh sách hoàn tất chưa có nghĩa email đã gửi hoặc sinh viên đã kích hoạt. Theo dõi từng cột bên dưới.</p>
        <button className="secondary" disabled={busy} onClick={() => setRevision(old => old + 1)}>Tải lại tiến độ</button>
        {selectable && <div className="button-row"><button className="secondary" disabled={busy} onClick={() => { void run(async () => {
          const ids = new Set<string>();
          for (let current = 0; ; current++) {
            const values = await api<Rows>(base + '/' + job.importJobId + '/rows?' + new URLSearchParams({ page: String(current), size: '100', validationState: 'VALID' }), { signal: lifetime.current?.signal });
            values.content.forEach(row => ids.add(row.rowId)); if (current + 1 >= values.totalPages) break;
          }
          if (!lifetime.current?.signal.aborted) setSelected(ids);
        }); }}>Chọn tất cả dòng hợp lệ</button><button className="secondary" onClick={() => setSelected(new Set())} disabled={busy}>Bỏ chọn</button>
          <button disabled={busy || !selected.size} onClick={async () => {
            if (!await askConfirm({ title: 'Nhập danh sách sinh viên?', message: 'Xác nhận nhập ' + selected.size + ' dòng? Tài khoản mới chỉ được kích hoạt khi sinh viên chấp nhận lời mời.',
              confirmLabel: 'Nhập ' + selected.size + ' dòng' })) return;
            void run(async () => { await command(base + '/' + job.importJobId + '/confirm', { expectedJobVersion: job.jobVersion, fileHash: job.fileHash, selectedRowIds: [...selected].sort() }); setSelected(new Set()); setRevision(old => old + 1); });
          }}>Xác nhận nhập {selected.size} dòng</button></div>}
        {job.jobState === 'FAILED' && <button disabled={busy || !job.selectedRows} onClick={() => { void run(async () => {
          await command(base + '/' + job.importJobId + '/resume', { expectedJobVersion: job.jobVersion }); setRevision(old => old + 1);
        }); }}>Phục hồi các dòng chưa hoàn tất</button>}
        <label htmlFor={id + '-filter'}>Lọc dòng</label><select id={id + '-filter'} value={filter} onChange={event => { setFilter(event.target.value); setPage(0); }}>
          <option value="">Tất cả</option>{['VALID', 'INVALID', 'NEEDS_REVIEW'].map(value => <option key={value} value={value}>{label(value)}</option>)}
          <option value="mail:FAILED">Email gửi thất bại</option><option value="mail:SENT">Email đã được máy chủ nhận</option>
        </select>
        <div className="proposal-table"><table><thead><tr><th>Chọn</th><th>Dòng / MSSV</th><th>Họ tên / email</th><th>Kiểm tra</th><th>Nhập danh sách</th><th>Email</th><th>Kích hoạt</th><th>Thao tác</th></tr></thead>
          <tbody>{rows?.content.map(row => <tr key={row.rowId}><td><input type="checkbox" aria-label={'Chọn dòng ' + row.rowNo} disabled={!selectable || busy || row.validationState !== 'VALID'} checked={selectable ? selected.has(row.rowId) : row.selected}
            onChange={event => setSelected(old => { const next = new Set(old); if (event.target.checked) next.add(row.rowId); else next.delete(row.rowId); return next; })} /></td>
            <td>{row.rowNo} / {row.sourceData.studentCode || '—'}</td><td>{row.sourceData.fullName || '—'}<br />{row.sourceData.email || '—'}</td>
            <td>{label(row.validationState)}{row.errorCodes.map(code => <div key={code}>{errors[code] || code}</div>)}</td><td>{label(row.rowState)}</td><td>{label(row.mailState)}</td><td>{label(row.activationState)}</td>
            <td>{row.rowState === 'PROVISIONED' && row.activationState === 'PENDING' && row.invitationVersion && <button className="secondary" disabled={busy || row.mailState === 'SENDING'} onClick={() => { void run(async () => {
              await command(base + '/' + job.importJobId + '/rows/' + row.rowId + '/resend', { expectedRowVersion: row.rowVersion, expectedInvitationVersion: row.invitationVersion }); setNotice('Đã xếp hàng thư kích hoạt mới.'); setRevision(old => old + 1);
            }); }}>Gửi lại thư kích hoạt</button>}</td></tr>)}</tbody></table></div>
        <div className="button-row"><button className="secondary" disabled={page === 0 || busy} onClick={() => setPage(old => old - 1)}>Trang trước</button>
          <span>Trang {page + 1} / {Math.max(1, rows?.totalPages || 0)} · {rows?.totalElements || 0} dòng</span><button className="secondary" disabled={busy || page + 1 >= (rows?.totalPages || 0)} onClick={() => setPage(old => old + 1)}>Trang sau</button></div>
      </>}
    </>}
  </div>;
}
