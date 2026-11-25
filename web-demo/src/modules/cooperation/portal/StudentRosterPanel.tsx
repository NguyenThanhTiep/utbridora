import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { api, ApiError } from '../../../api';
import { Icon, Notice } from '../../../components';
import { askConfirm } from '../../../confirm';
import { formatDateDMY } from '../../../ui/dateFormat';
import { readAll } from './data';
import { workflowError } from '../proposals/api';

type Term = { termId: string; termCode: string; schoolOrgId: string; status: string };
type MajorItem = { code: string; name: string; status: string };
type ExistingRosterRow = {
  id: string;
  schoolOrgId: string;
  studentCode: string | null;
  userId: string;
  fullName: string | null;
  email: string | null;
  majorCode: string | null;
  termCode: string;
  createdAt: string;
};
type Job = {
  importJobId: string;
  jobVersion: number;
  jobState: string;
  fileHash: string;
  termId: string;
  previewExpiresAt: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  previewNeedsReviewRows: number;
  selectedRows: number;
  processedRows: number;
  successRows: number;
  needsReviewRows: number;
  permanentFailedRows: number;
  transientFailedRows: number;
  startedAt?: string | null;
  finishedAt?: string | null;
  observedAt?: string | null;
};
type JobPage = { content: Job[]; page: number; totalElements: number; totalPages: number };
type Row = {
  rowId: string;
  rowNo: number;
  rowVersion: number;
  sourceData: { studentCode: string | null; fullName: string | null; email: string | null };
  validationState: string;
  selected: boolean;
  rowState: string;
  errorCodes: string[];
  activationState: string;
  invitationVersion: number | null;
  mailState: string;
};
type Rows = { content: Row[]; page: number; totalElements: number; totalPages: number };
type Preview = { job: Job; rows: Rows };

const base = '/school/student-imports';
const rosterBase = '/school/student-rosters';

const labels: Record<string, string> = {
  PREVIEW_READY: 'Chờ xác nhận',
  EXPIRED: 'Bản xem trước hết hạn',
  PROCESSING: 'Đang xử lý',
  COMPLETED: 'Nhập danh sách hoàn tất',
  COMPLETED_WITH_ERRORS: 'Hoàn tất, có dòng cần xử lý',
  FAILED: 'Thất bại',
  VALID: 'Hợp lệ',
  INVALID: 'Không hợp lệ',
  NEEDS_REVIEW: 'Cần xác minh',
  NOT_SELECTED: 'Chưa chọn',
  QUEUED: 'Đang chờ',
  PROVISIONED: 'Đã chuẩn bị kích hoạt',
  ALREADY_LINKED: 'Đã liên kết',
  PERMANENT_FAILED: 'Lỗi dữ liệu',
  TRANSIENT_FAILED: 'Chờ thử lại',
  NOT_REQUIRED: 'Không cần',
  NOT_READY: 'Chưa sẵn sàng',
  SENDING: 'Đang gửi',
  RETRY_WAIT: 'Chờ gửi lại',
  SENT: 'Máy chủ thư đã nhận',
  SUPERSEDED: 'Đã thay thế',
  CANCELLED: 'Đã hủy',
  PENDING: 'Chờ kích hoạt',
  ACTIVE: 'Đã kích hoạt',
  BLOCKED: 'Bị chặn',
};

const errors: Record<string, string> = {
  STUDENT_CODE_REQUIRED: 'Thiếu MSSV',
  STUDENT_CODE_MUST_BE_TEXT: 'Ô MSSV phải là văn bản',
  STUDENT_CODE_INVALID: 'MSSV không hợp lệ',
  EMAIL_REQUIRED: 'Thiếu email',
  EMAIL_INVALID: 'Email không hợp lệ',
  FULL_NAME_REQUIRED: 'Thiếu họ tên',
  FULL_NAME_INVALID: 'Họ tên không hợp lệ',
  MAJOR_INVALID: 'Mã ngành không thuộc danh mục đang dùng',
  TERM_CODE_INVALID: 'Mã kỳ không khớp kỳ đã chọn',
  DUPLICATE_IN_FILE: 'Trùng MSSV hoặc email trong tệp',
  IDENTITY_CONFLICT: 'Danh tính cần xác minh',
  ACCOUNT_BLOCKED: 'Tài khoản bị chặn',
  MEMBERSHIP_REVOKED: 'Quyền sinh viên đã bị thu hồi',
  ROSTER_METADATA_CONFLICT: 'Thông tin không khớp danh sách hiện có',
  TRANSIENT_DEPENDENCY: 'Dịch vụ tạm thời gián đoạn',
  AUTHORITY_REVOKED: 'Quyền thực hiện đã bị thu hồi',
  REFERENCE_DATA_CHANGED: 'Danh mục hoặc kỳ đã thay đổi',
  PERMANENT_VALIDATION: 'Dữ liệu không hợp lệ',
};

const label = (value: string) => labels[value] || value;

function randomUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
    crypto.getRandomValues(bytes);
  } else {
    for (let i = 0; i < bytes.length; i++) bytes[i] = Math.floor(Math.random() * 256);
  }
  bytes[6] = (bytes[6] & 0x0f) | 0x40;
  bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = [...bytes].map(b => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

function badgeTone(value: string): string {
  switch (value) {
    case 'VALID':
    case 'PROVISIONED':
    case 'ALREADY_LINKED':
    case 'SENT':
    case 'ACTIVE':
    case 'COMPLETED':
      return 't5-badge t5-badge-success';
    case 'NEEDS_REVIEW':
    case 'PENDING':
    case 'PREVIEW_READY':
    case 'PROCESSING':
    case 'QUEUED':
    case 'SENDING':
    case 'RETRY_WAIT':
    case 'TRANSIENT_FAILED':
    case 'COMPLETED_WITH_ERRORS':
      return 't5-badge t5-badge-warning';
    case 'INVALID':
    case 'PERMANENT_FAILED':
    case 'FAILED':
    case 'BLOCKED':
    case 'EXPIRED':
      return 't5-badge t5-badge-danger';
    default:
      return 't5-badge t5-badge-neutral';
  }
}

function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function downloadSampleCsv(termCode: string, majorCode: string) {
  const safeTerm = termCode || 'HK_HIEN_HANH';
  const safeMajor = majorCode || 'SE';
  const csv = [
    'studentCode,email,fullName,majorCode,termCode',
    `22520001,sinhvien01@student.edu.vn,Nguyễn Văn An,${safeMajor},${safeTerm}`,
    `22520002,sinhvien02@student.edu.vn,Trần Thị Bình,${safeMajor},${safeTerm}`,
  ].join('\r\n');
  const blob = new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = `mau-danh-sach-sinh-vien-${safeTerm}.csv`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  window.setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function StudentRosterPanel({ org, viewerId, embedded = false }: { org: string; viewerId: string; embedded?: boolean }) {
  const id = useId();
  const [terms, setTerms] = useState<Term[]>([]);
  const [term, setTerm] = useState('');
  const [error, setError] = useState('');
  const [step, setStep] = useState(1);
  const [activeMajor, setActiveMajor] = useState('SE');

  useEffect(() => {
    const controller = new AbortController();
    setTerms([]);
    setTerm('');
    setError('');
    setStep(1);
    void readAll<Term>(org, '/terms?' + new URLSearchParams({ schoolOrgId: org }), controller.signal)
      .then(items => {
        if (!controller.signal.aborted) setTerms(items.filter(row => row.schoolOrgId === org && row.status === 'ACTIVE'));
      })
      .catch(reason => {
        if (!controller.signal.aborted) setError(workflowError(reason));
      });
    return () => controller.abort();
  }, [org, viewerId]);

  const selectedTerm = terms.find(row => row.termId === term);
  const currentStep = !term ? 1 : step;

  const steps = [
    { no: 1, title: 'Chọn kỳ & tệp' },
    { no: 2, title: 'Kiểm tra & xem trước' },
    { no: 3, title: 'Xác nhận nhập' },
    { no: 4, title: 'Gửi thư & kích hoạt' },
  ];

  return (
    <section
      className={'panel school-roster-panel' + (embedded ? ' is-embedded' : '')}
      aria-label="Nhập danh sách & Kích hoạt sinh viên"
    >
      <div className="roster-header-row">
        <div className="t5-section-heading">
          <span className="portal-card-icon"><Icon name="users" /></span>
          <div>
            <p className="portal-eyebrow">QUẢN LÝ SINH VIÊN</p>
            <h2>Nhập danh sách & Kích hoạt sinh viên</h2>
            <p>Chuẩn bị danh sách theo trường và kỳ trước khi cấp tài khoản.</p>
          </div>
        </div>
        <div className="roster-header-actions">
          <span className="roster-spec-chip">CSV UTF-8 · XLSX</span>
          <span className="roster-spec-chip">≤ 5 MB · 5.000 dòng</span>
          <button
            type="button"
            className="secondary"
            onClick={() => downloadSampleCsv(selectedTerm?.termCode || '', activeMajor)}
          >
            <Icon name="download" size={16} />
            Tải file CSV mẫu
          </button>
        </div>
      </div>

      <ol className="t5-process-steps" aria-label="Quy trình nhập danh sách sinh viên">
        {steps.map(item => (
          <li key={item.no} aria-current={currentStep === item.no ? 'step' : undefined}>
            <span>{item.no}</span>
            {item.title}
          </li>
        ))}
      </ol>

      <div className="t5-detail-grid roster-guide-grid">
        <div className="t5-detail-card">
          <div className="t5-card-heading">
            <h3>Cấu trúc tệp &amp; cột dữ liệu</h3>
            <span className="t5-badge t5-badge-neutral">UTF-8 / Text</span>
          </div>
          <p>
            Nhập CSV UTF-8 hoặc XLSX một trang tính, tối đa 5 MB và 5.000 dòng. MSSV trong Excel phải là văn bản để giữ số 0 đầu và chữ hoa, chữ thường.
          </p>
          <dl className="t5-roster-columns">
            <div>
              <dt>Cột bắt buộc</dt>
              <dd><code>studentCode,email,fullName,majorCode,termCode</code></dd>
            </div>
            <div>
              <dt>Cột tùy chọn</dt>
              <dd><code>cohortCode,classCode,schoolOrgId</code></dd>
            </div>
          </dl>
        </div>

        <div className="t5-detail-card roster-term-card">
          <div className="t5-card-heading">
            <h3>Phạm vi kỳ nhập danh sách</h3>
            <span className="t5-badge t5-badge-neutral">{terms.length} kỳ đang mở</span>
          </div>
          <div className="t5-rule-note">
            <strong>Quy định ủy quyền</strong>
            <p>Cần ủy quyền nhập danh sách trong trường và kỳ hiện hành. Dữ liệu nhập không tự xác nhận điều kiện thực tập.</p>
          </div>
          <div className="roster-term-field">
            <label htmlFor={id}>Kỳ nhập danh sách</label>
            <select
              id={id}
              value={term}
              onChange={event => {
                setTerm(event.target.value);
                setStep(1);
                setActiveMajor('SE');
              }}
            >
              <option value="">Chọn kỳ đang hoạt động</option>
              {terms.map(row => <option key={row.termId} value={row.termId}>{row.termCode}</option>)}
            </select>
          </div>
          {error && <Notice message={error} tone="error" />}
        </div>
      </div>

      {term && (
        <ImportSession
          key={viewerId + ':' + org + ':' + term}
          org={org}
          term={term}
          termCode={selectedTerm?.termCode || ''}
          onStepChange={setStep}
          onPrimaryMajor={setActiveMajor}
        />
      )}
    </section>
  );
}

function ImportSession({
  org,
  term,
  termCode,
  onStepChange,
  onPrimaryMajor,
}: {
  org: string;
  term: string;
  termCode: string;
  onStepChange: (step: number) => void;
  onPrimaryMajor: (code: string) => void;
}) {
  const id = useId();
  const lifetime = useRef<AbortController | null>(null);
  const attempts = useRef(new Map<string, string>());
  const [file, setFile] = useState<File | null>(null);
  const [previewKey, setPreviewKey] = useState(randomUuid);
  const [job, setJob] = useState<Job | null>(null);
  const [rows, setRows] = useState<Rows | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState('');
  const [searchText, setSearchText] = useState('');
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [trackingId, setTrackingId] = useState('');
  const [expired, setExpired] = useState(false);
  const [revision, setRevision] = useState(0);
  const [recentJobs, setRecentJobs] = useState<Job[]>([]);
  const [majors, setMajors] = useState<MajorItem[]>([]);
  const [existingRoster, setExistingRoster] = useState<ExistingRosterRow[]>([]);
  const [rosterSearch, setRosterSearch] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    lifetime.current = controller;
    const expire = () => {
      controller.abort();
      setExpired(true);
      setJob(null);
      setRows(null);
      setSelected(new Set());
      setFile(null);
      setRecentJobs([]);
      setMajors([]);
      setExistingRoster([]);
      attempts.current.clear();
    };
    window.addEventListener('session-expired', expire);
    return () => {
      controller.abort();
      window.removeEventListener('session-expired', expire);
    };
  }, []);

  useEffect(() => {
    if (!job) {
      onStepChange(1);
    } else if (job.jobState === 'PREVIEW_READY') {
      onStepChange(2);
    } else if (job.jobState === 'PROCESSING' || job.jobState === 'FAILED') {
      onStepChange(3);
    } else {
      onStepChange(4);
    }
  }, [job, onStepChange]);

  // Optional enrichments after a term is explicitly chosen; 403/404 never break import workflow.
  useEffect(() => {
    if (expired) return;
    const controller = new AbortController();
    setExistingRoster([]);
    void api<JobPage>(`${base}?` + new URLSearchParams({ termId: term, page: '0', size: '6' }), { signal: controller.signal })
      .then(data => {
        if (!controller.signal.aborted && Array.isArray(data?.content)) setRecentJobs(data.content);
      })
      .catch(() => { /* History list is optional when endpoint or permission is unavailable. */ });
    void api<MajorItem[]>(`${base}/majors?` + new URLSearchParams({ termId: term }), { signal: controller.signal })
      .then(items => {
        if (controller.signal.aborted || !Array.isArray(items)) return;
        setMajors(items);
        if (items[0]?.code) onPrimaryMajor(items[0].code);
      })
      .catch(() => { /* Catalog hints are optional across scoped environments. */ });
    const rosterQuery = new URLSearchParams({ schoolOrgId: org, ...(termCode ? { termCode } : {}) });
    void api<ExistingRosterRow[]>(`${rosterBase}?${rosterQuery}`, { signal: controller.signal })
      .then(items => {
        if (!controller.signal.aborted && Array.isArray(items)) setExistingRoster(items);
      })
      .catch(() => { if (!controller.signal.aborted) setExistingRoster([]); });
    return () => controller.abort();
  }, [org, term, termCode, revision, expired, onPrimaryMajor]);

  const jobId = job?.importJobId;
  useEffect(() => {
    if (!jobId || expired) return;
    const controller = new AbortController();
    let running = false;
    const refresh = async () => {
      if (running || document.hidden) return;
      running = true;
      try {
        const [next, values] = await Promise.all([
          api<Job>(base + '/' + jobId, { signal: controller.signal }),
          api<Rows>(
            base + '/' + jobId + '/rows?' + new URLSearchParams({
              page: String(page),
              size: '25',
              ...(filter ? { [filter.startsWith('mail:') ? 'mailState' : 'validationState']: filter.replace(/^mail:/, '') } : {}),
            }),
            { signal: controller.signal },
          ),
        ]);
        if (!controller.signal.aborted) {
          if (next.termId !== term) throw new Error('Đợt nhập không thuộc kỳ đang chọn.');
          setJob(next);
          setRows(values);
        }
      } catch (reason) {
        if (!controller.signal.aborted) {
          setError(workflowError(reason));
          if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) {
            setJob(null);
            setRows(null);
            setSelected(new Set());
          }
        }
      } finally {
        running = false;
      }
    };
    void refresh();
    const timer = window.setInterval(() => { void refresh(); }, 5000);
    const visible = () => { void refresh(); };
    window.addEventListener('focus', visible);
    document.addEventListener('visibilitychange', visible);
    return () => {
      controller.abort();
      window.clearInterval(timer);
      window.removeEventListener('focus', visible);
      document.removeEventListener('visibilitychange', visible);
    };
  }, [jobId, page, filter, revision, term, expired]);

  function key(path: string, body: object) {
    const fingerprint = path + ':' + JSON.stringify(body);
    let value = attempts.current.get(fingerprint);
    if (!value) {
      value = randomUuid();
      attempts.current.set(fingerprint, value);
    }
    return value;
  }

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError('');
    setNotice('');
    try {
      await action();
    } catch (reason) {
      if (!lifetime.current?.signal.aborted) {
        const local = reason instanceof Error && !(reason instanceof ApiError) ? reason.message : workflowError(reason);
        setError(
          local
          + (reason instanceof ApiError ? ' ' + reason.fieldErrors.map(field => field.message).join(' ') : '')
          + (reason instanceof ApiError && reason.retryAfterSeconds !== null ? ' Có thể thử lại sau ' + reason.retryAfterSeconds + ' giây.' : ''),
        );
        if (reason instanceof ApiError && reason.status === 409) setRevision(old => old + 1);
        if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) {
          setJob(null);
          setRows(null);
          setSelected(new Set());
        }
      }
    } finally {
      if (!lifetime.current?.signal.aborted) setBusy(false);
    }
  }

  async function command(path: string, body: object) {
    return api(path, {
      method: 'POST',
      body: JSON.stringify(body),
      signal: lifetime.current?.signal,
      headers: { 'Idempotency-Key': key(path, body) },
    });
  }

  async function loadJobById(targetId: string) {
    const value = await api<Job>(base + '/' + encodeURIComponent(targetId.trim()), { signal: lifetime.current?.signal });
    if (value.termId !== term) throw new Error('Đợt nhập không thuộc kỳ đang chọn.');
    if (!lifetime.current?.signal.aborted) {
      setJob(value);
      setTrackingId(value.importJobId);
      setSelected(new Set());
      setPage(0);
      setFilter('');
      setRevision(old => old + 1);
    }
  }

  const filteredRows = useMemo(() => {
    const list = rows?.content || [];
    const q = searchText.trim().toLocaleLowerCase('vi');
    if (!q) return list;
    return list.filter(row =>
      [row.sourceData.studentCode, row.sourceData.fullName, row.sourceData.email]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('vi')
        .includes(q),
    );
  }, [rows, searchText]);

  const filteredExistingRoster = useMemo(() => {
    const q = rosterSearch.trim().toLocaleLowerCase('vi');
    if (!q) return existingRoster;
    return existingRoster.filter(item =>
      [item.studentCode, item.fullName, item.email, item.majorCode]
        .filter(Boolean)
        .join(' ')
        .toLocaleLowerCase('vi')
        .includes(q),
    );
  }, [existingRoster, rosterSearch]);

  const selectable = job?.jobState === 'PREVIEW_READY' && Date.now() < Date.parse(job.previewExpiresAt);
  const progressPercent = job && job.selectedRows > 0
    ? Math.min(100, Math.round((job.processedRows / job.selectedRows) * 100))
    : 0;

  return (
    <div className="roster-session stack" aria-busy={busy}>
      {expired ? (
        <Notice message="Phiên đã hết hạn. Đăng nhập lại để tiếp tục." tone="error" />
      ) : (
        <>
          {majors.length > 0 && (
            <div className="roster-majors-bar" aria-label="Danh mục ngành hợp lệ trong kỳ">
              <span className="roster-majors-label">Mã ngành hợp lệ ({termCode}):</span>
              <div className="roster-major-chips">
                {majors.map(item => (
                  <span key={item.code} className="roster-major-chip" title={item.name}>
                    <code>{item.code}</code>
                    <span>{item.name}</span>
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="t5-detail-grid roster-actions-grid">
            <div className="t5-detail-card">
              <div className="t5-card-heading">
                <h3>Tải tệp danh sách lên hệ thống</h3>
                {file && <span className="t5-badge t5-badge-neutral">{formatFileSize(file.size)}</span>}
              </div>
              <div className="roster-upload-dropzone">
                <p>Chọn tệp bảng tính chứa thông tin sinh viên thuộc kỳ <strong>{termCode || 'đã chọn'}</strong>.</p>
                <label htmlFor={id + '-file'}>Tệp danh sách sinh viên</label>
                <input
                  id={id + '-file'}
                  type="file"
                  accept=".csv,.xlsx"
                  disabled={busy}
                  onChange={event => {
                    setFile(event.target.files?.[0] || null);
                    setPreviewKey(randomUuid());
                  }}
                />
                {file && (
                  <p className={'roster-file-meta' + (file.size > 5 * 1024 * 1024 ? ' is-oversize' : '')}>
                    Đã chọn: <strong>{file.name}</strong> ({formatFileSize(file.size)})
                    {file.size > 5 * 1024 * 1024 ? ' — vượt quá giới hạn 5 MB.' : ''}
                  </p>
                )}
              </div>
              <div className="button-row roster-upload-actions">
                <button
                  disabled={!file || busy}
                  onClick={() => {
                    void run(async () => {
                      if (!file || file.size > 5 * 1024 * 1024) throw new Error('Chọn tệp không quá 5 MB.');
                      const form = new FormData();
                      form.set('file', file);
                      form.set('termId', term);
                      const value = await api<Preview>(base + '/preview', {
                        method: 'POST',
                        body: form,
                        signal: lifetime.current?.signal,
                        headers: { 'Idempotency-Key': previewKey, 'X-Organization-Id': org },
                      });
                      if (lifetime.current?.signal.aborted) return;
                      setJob(value.job);
                      setRows(value.rows);
                      setSelected(new Set());
                      setPage(0);
                      setFilter('');
                      setTrackingId(value.job.importJobId);
                    });
                  }}
                >
                  Kiểm tra và xem trước
                </button>
              </div>
            </div>

            <div className="t5-detail-card">
              <div className="t5-card-heading">
                <h3>Theo dõi tiến độ &amp; đợt nhập gần đây</h3>
                {recentJobs.length > 0 && <span className="t5-badge t5-badge-neutral">{recentJobs.length} đợt gần nhất</span>}
              </div>
              <div className="roster-track-form">
                <div>
                  <label htmlFor={id + '-job'}>Mã đợt nhập cần theo dõi</label>
                  <input
                    id={id + '-job'}
                    value={trackingId}
                    onChange={event => setTrackingId(event.target.value)}
                    placeholder="Nhập mã UUID đợt nhập…"
                    disabled={busy}
                  />
                </div>
                <button
                  className="secondary"
                  disabled={busy || !trackingId}
                  onClick={() => {
                    void run(async () => {
                      await loadJobById(trackingId);
                    });
                  }}
                >
                  Theo dõi đợt nhập
                </button>
              </div>

              {recentJobs.length > 0 ? (
                <ul className="roster-recent-list" aria-label="Các đợt nhập gần đây trong kỳ">
                  {recentJobs.map(item => (
                    <li key={item.importJobId}>
                      <button
                        type="button"
                        className={'roster-recent-item' + (job?.importJobId === item.importJobId ? ' is-active' : '')}
                        disabled={busy}
                        onClick={() => {
                          void run(async () => {
                            await loadJobById(item.importJobId);
                          });
                        }}
                      >
                        <div>
                          <span className={badgeTone(item.jobState)}>{label(item.jobState)}</span>
                          <code>…{item.importJobId.slice(-8)}</code>
                        </div>
                        <small>
                          {item.validRows}/{item.totalRows} hợp lệ · {item.successRows} đã xử lý
                        </small>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="roster-recent-empty">
                  Nhập mã đợt hoặc chọn tệp mới để bắt đầu kiểm tra và theo dõi tiến độ kích hoạt sinh viên.
                </p>
              )}
            </div>
          </div>

          {error && <Notice message={error} tone="error" />}
          {notice && <Notice message={notice} tone="success" />}

          {job && (
            <div className="t5-detail-card roster-job-shell">
              <div className="roster-job-header">
                <div>
                  <div className="roster-job-title-row">
                    <span className={badgeTone(job.jobState)}>{label(job.jobState)}</span>
                    <h3>{label(job.jobState)}</h3>
                  </div>
                  <p className="roster-job-meta">
                    Mã đợt: <code>{job.importJobId}</code>
                    {job.jobState === 'PREVIEW_READY' && (
                      <> · Hết hạn xác nhận: <strong>{new Date(job.previewExpiresAt).toLocaleString('vi-VN')}</strong></>
                    )}
                  </p>
                </div>
                <button className="secondary" disabled={busy} onClick={() => setRevision(old => old + 1)}>
                  <Icon name="refresh" size={16} />
                  Tải lại tiến độ
                </button>
              </div>

              <div className="roster-kpi-grid" aria-label="Thống kê kết quả kiểm tra và xử lý">
                <div className="roster-kpi"><span>Tổng số dòng</span><strong>{job.totalRows}</strong></div>
                <div className="roster-kpi tone-mint"><span>Hợp lệ</span><strong>{job.validRows}</strong></div>
                <div className="roster-kpi tone-danger"><span>Không hợp lệ</span><strong>{job.invalidRows}</strong></div>
                <div className="roster-kpi tone-warning"><span>Cần xác minh</span><strong>{job.jobState === 'PREVIEW_READY' ? job.previewNeedsReviewRows : job.needsReviewRows}</strong></div>
                <div className="roster-kpi tone-teal"><span>Thành công</span><strong>{job.successRows}</strong></div>
              </div>

              {job.selectedRows > 0 && (
                <div className="roster-progress-wrap">
                  <div className="roster-progress-label">
                    <span>Tiến độ xử lý đợt nhập</span>
                    <strong>{job.processedRows}/{job.selectedRows} dòng ({progressPercent}%)</strong>
                  </div>
                  <progress max={job.selectedRows} value={job.processedRows} />
                </div>
              )}

              <div className="roster-job-notes">
                <p>Tệp có {job.totalRows} dòng: {job.validRows} hợp lệ, {job.invalidRows} không hợp lệ, {job.previewNeedsReviewRows} cần xác minh.</p>
                <p>Đã chọn {job.selectedRows}; đã xử lý {job.processedRows}; thành công {job.successRows}; cần xác minh {job.needsReviewRows}; lỗi dữ liệu {job.permanentFailedRows}; chờ thử lại {job.transientFailedRows}.</p>
                <p className="muted">Nhập danh sách hoàn tất chưa có nghĩa email đã gửi hoặc sinh viên đã kích hoạt. Theo dõi từng cột bên dưới.</p>
              </div>

              {selectable && (
                <div className="button-row roster-selection-bar">
                  <button
                    className="secondary"
                    disabled={busy}
                    onClick={() => {
                      void run(async () => {
                        const ids = new Set<string>();
                        for (let current = 0; ; current++) {
                          const values = await api<Rows>(
                            base + '/' + job.importJobId + '/rows?' + new URLSearchParams({
                              page: String(current),
                              size: '100',
                              validationState: 'VALID',
                            }),
                            { signal: lifetime.current?.signal },
                          );
                          values.content.forEach(row => ids.add(row.rowId));
                          if (current + 1 >= values.totalPages) break;
                        }
                        if (!lifetime.current?.signal.aborted) setSelected(ids);
                      });
                    }}
                  >
                    Chọn tất cả dòng hợp lệ
                  </button>
                  <button className="secondary" onClick={() => setSelected(new Set())} disabled={busy || selected.size === 0}>
                    Bỏ chọn
                  </button>
                  <button
                    disabled={busy || !selected.size}
                    onClick={async () => {
                      if (!await askConfirm({ title: 'Nhập danh sách sinh viên?', message: 'Xác nhận nhập ' + selected.size + ' dòng? Tài khoản mới chỉ được kích hoạt khi sinh viên chấp nhận lời mời.', confirmLabel: 'Nhập ' + selected.size + ' dòng' })) return;
                      void run(async () => {
                        await command(base + '/' + job.importJobId + '/confirm', {
                          expectedJobVersion: job.jobVersion,
                          fileHash: job.fileHash,
                          selectedRowIds: [...selected].sort(),
                        });
                        setSelected(new Set());
                        setRevision(old => old + 1);
                      });
                    }}
                  >
                    Xác nhận nhập {selected.size} dòng
                  </button>
                </div>
              )}

              {job.jobState === 'FAILED' && (
                <div className="button-row">
                  <button
                    disabled={busy || !job.selectedRows}
                    onClick={() => {
                      void run(async () => {
                        await command(base + '/' + job.importJobId + '/resume', { expectedJobVersion: job.jobVersion });
                        setRevision(old => old + 1);
                      });
                    }}
                  >
                    Phục hồi các dòng chưa hoàn tất
                  </button>
                </div>
              )}

              <div className="portal-list-toolbar roster-filter-toolbar">
                <div>
                  <label htmlFor={id + '-filter'}>
                    <span>Lọc dòng</span>
                    <select id={id + '-filter'} value={filter} onChange={event => { setFilter(event.target.value); setPage(0); }}>
                      <option value="">Tất cả</option>
                      {['VALID', 'INVALID', 'NEEDS_REVIEW'].map(value => <option key={value} value={value}>{label(value)}</option>)}
                      <option value="mail:FAILED">Email gửi thất bại</option>
                      <option value="mail:SENT">Email đã được máy chủ nhận</option>
                    </select>
                  </label>
                </div>
                <div className="portal-search">
                  <label htmlFor={id + '-search'}>
                    <span>Tìm trong trang</span>
                    <input
                      id={id + '-search'}
                      type="search"
                      value={searchText}
                      onChange={event => setSearchText(event.target.value)}
                      placeholder="Tìm theo MSSV, họ tên hoặc email…"
                    />
                  </label>
                </div>
              </div>

              <div className="proposal-table roster-table">
                <table>
                  <thead>
                    <tr>
                      <th>Chọn</th>
                      <th>Dòng / MSSV</th>
                      <th>Họ tên / email</th>
                      <th>Kiểm tra</th>
                      <th>Nhập danh sách</th>
                      <th>Email</th>
                      <th>Kích hoạt</th>
                      <th>Thao tác</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredRows.map(row => (
                      <tr key={row.rowId}>
                        <td className="roster-check-cell">
                          <input
                            type="checkbox"
                            aria-label={'Chọn dòng ' + row.rowNo}
                            disabled={!selectable || busy || row.validationState !== 'VALID'}
                            checked={selectable ? selected.has(row.rowId) : row.selected}
                            onChange={event => setSelected(old => {
                              const next = new Set(old);
                              if (event.target.checked) next.add(row.rowId);
                              else next.delete(row.rowId);
                              return next;
                            })}
                          />
                        </td>
                        <td>{row.rowNo} / {row.sourceData.studentCode || '—'}</td>
                        <td>{row.sourceData.fullName || '—'}<br />{row.sourceData.email || '—'}</td>
                        <td>
                          <span className={badgeTone(row.validationState)}>{label(row.validationState)}</span>
                          {row.errorCodes.map(code => (
                            <div key={code} className="roster-row-error">{errors[code] || code}</div>
                          ))}
                        </td>
                        <td><span className={badgeTone(row.rowState)}>{label(row.rowState)}</span></td>
                        <td><span className={badgeTone(row.mailState)}>{label(row.mailState)}</span></td>
                        <td><span className={badgeTone(row.activationState)}>{label(row.activationState)}</span></td>
                        <td>
                          {row.rowState === 'PROVISIONED' && row.activationState === 'PENDING' && row.invitationVersion && (
                            <button
                              className="secondary"
                              disabled={busy || row.mailState === 'SENDING'}
                              onClick={() => {
                                void run(async () => {
                                  await command(base + '/' + job.importJobId + '/rows/' + row.rowId + '/resend', {
                                    expectedRowVersion: row.rowVersion,
                                    expectedInvitationVersion: row.invitationVersion,
                                  });
                                  setNotice('Đã xếp hàng thư kích hoạt mới.');
                                  setRevision(old => old + 1);
                                });
                              }}
                            >
                              Gửi lại thư kích hoạt
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="button-row roster-pagination">
                <button className="secondary" disabled={page === 0 || busy} onClick={() => setPage(old => old - 1)}>Trang trước</button>
                <span>Trang {page + 1} / {Math.max(1, rows?.totalPages || 0)} · {rows?.totalElements || 0} dòng</span>
                <button className="secondary" disabled={busy || page + 1 >= (rows?.totalPages || 0)} onClick={() => setPage(old => old + 1)}>Trang sau</button>
              </div>
            </div>
          )}

          {existingRoster.length > 0 && (
            <div className="t5-detail-card roster-existing-card">
              <div className="t5-card-heading">
                <h3>Danh sách sinh viên đã ghi nhận trong kỳ {termCode}</h3>
                <span className="t5-badge t5-badge-success">{existingRoster.length} sinh viên</span>
              </div>
              <div className="portal-list-toolbar">
                <div className="portal-search">
                  <label htmlFor={id + '-roster-search'}>
                    <span>Tìm trong danh sách kỳ</span>
                    <input
                      id={id + '-roster-search'}
                      type="search"
                      value={rosterSearch}
                      onChange={event => setRosterSearch(event.target.value)}
                      placeholder="Nhập MSSV, họ tên, email hoặc ngành…"
                    />
                  </label>
                </div>
              </div>
              <div className="proposal-table roster-table">
                <table>
                  <thead>
                    <tr>
                      <th>MSSV</th>
                      <th>Họ tên / Email</th>
                      <th>Ngành</th>
                      <th>Kỳ học</th>
                      <th>Ngày ghi nhận</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredExistingRoster.slice(0, 50).map(item => (
                      <tr key={item.id + ':' + item.termCode}>
                        <td><code>{item.studentCode || 'Không được cấp quyền xem'}</code></td>
                        <td>{item.fullName || 'Không được cấp quyền xem'}<br /><small>{item.email || 'Không được cấp quyền xem'}</small></td>
                        <td><span className="t5-badge t5-badge-neutral">{item.majorCode}</span></td>
                        <td>{item.termCode}</td>
                        <td>{formatDateDMY(item.createdAt)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {filteredExistingRoster.length > 50 && (
                <p className="muted">Đang hiển thị 50/{filteredExistingRoster.length} sinh viên phù hợp. Dùng ô tìm kiếm để thu hẹp kết quả.</p>
              )}
            </div>
          )}
        </>
      )}
    </div>
  );
}
