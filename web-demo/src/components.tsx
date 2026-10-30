import { useCallback, useEffect, useId, useRef, useState, type InputHTMLAttributes, type ReactNode } from 'react';
import type { Page } from './api';

export function Icon({ name, size = 20, className = '' }: { name: string; size?: number; className?: string }) {
  const paths: Record<string, ReactNode> = {
    grid: <><rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" /><rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" /></>,
    users: <><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75" /><circle cx="9" cy="7" r="4" /></>,
    user: <><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v2" /></>,
    building: <><rect x="5" y="3" width="14" height="18" rx="2" /><path d="M9 7h1m4 0h1M9 11h1m4 0h1M9 15h1m4 0h1M10 21v-3h4v3" /></>,
    activity: <><path d="M9 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-4M13 3h8v8M21 3l-9 9" /><path d="M7 14v3m4-2v2m4-4v4" /></>,
    shield: <><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" /><path d="m8.5 12 2.5 2.5 4.5-5" /></>,
    lock: <><rect x="5" y="10" width="14" height="11" rx="2" /><path d="M8 10V7a4 4 0 0 1 8 0v3M12 14v3" /></>,
    key: <><circle cx="8" cy="9" r="5" /><path d="m12 13 8 8m-2-2 3-3m-6 0 3-3" /></>,
    mail: <><rect x="3" y="5" width="18" height="14" rx="2" /><path d="m3 7 9 6 9-6" /></>,
    search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 5 5" /></>,
    filter: <><path d="M4 7h16M7 12h10M10 17h4" /><circle cx="8" cy="7" r="1" fill="currentColor" /><circle cx="15" cy="12" r="1" fill="currentColor" /></>,
    plus: <path d="M12 5v14M5 12h14" />,
    close: <path d="m6 6 12 12M18 6 6 18" />,
    menu: <path d="M4 6h16M4 12h16M4 18h16" />,
    'arrow-right': <path d="M4 12h16m-6-6 6 6-6 6" />,
    'arrow-left': <path d="M20 12H4m6-6-6 6 6 6" />,
    'chevron-right': <path d="m9 5 7 7-7 7" />,
    'chevron-left': <path d="m15 5-7 7 7 7" />,
    'chevron-down': <path d="m6 9 6 6 6-6" />,
    logout: <><path d="M9 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4M14 8l4 4-4 4M8 12h13" /></>,
    check: <path d="m5 12 4 4L19 6" />,
    'check-circle': <><circle cx="12" cy="12" r="9" /><path d="m8 12 3 3 5-6" /></>,
    info: <><circle cx="12" cy="12" r="9" /><path d="M12 11v6M12 7h.01" /></>,
    alert: <><path d="m10.3 4-8 14a2 2 0 0 0 1.7 3h16a2 2 0 0 0 1.7-3l-8-14a2 2 0 0 0-3.4 0Z" /><path d="M12 9v4M12 17h.01" /></>,
    eye: <><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z" /><circle cx="12" cy="12" r="3" /></>,
    'eye-off': <><path d="m3 3 18 18M10.6 5.1A12 12 0 0 1 12 5c6.5 0 10 7 10 7a18 18 0 0 1-3 3.8M6.4 6.4A18 18 0 0 0 2 12s3.5 7 10 7a11 11 0 0 0 5.6-1.6M10 10a3 3 0 0 0 4 4" /></>,
    clock: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 2" /></>,
    calendar: <><rect x="3" y="5" width="18" height="16" rx="2" /><path d="M16 3v4M8 3v4M3 11h18M8 15h2m4 0h2" /></>,
    refresh: <><path d="M20 7v5h-5M4 17v-5h5" /><path d="M6.1 7a7 7 0 0 1 11.6-2L20 8M4 16l2.3 3A7 7 0 0 0 18 17" /></>,
    graduation: <><path d="m2 9 10-6 10 6-10 6L2 9ZM6 12v5c3 3 9 3 12 0v-5M22 9v7" /></>,
    briefcase: <><rect x="3" y="7" width="18" height="14" rx="2" /><path d="M8 7V4h8v3M3 12a25 25 0 0 0 18 0M10 13h4" /></>,
    layers: <><path d="m12 3 10 5-10 5L2 8l10-5ZM2 12l10 5 10-5M2 16l10 5 10-5" /></>,
  };
  return <svg className={'icon ' + className} width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{paths[name] || paths.grid}</svg>;
}

export function Brand({ inverse = false }: { inverse?: boolean }) {
  return <span className={'brand-lockup' + (inverse ? ' inverse' : '')}>
    <svg className="brand-symbol" width="38" height="38" viewBox="0 0 40 40" fill="none" aria-hidden="true">
      <rect width="40" height="40" rx="12" fill="currentColor" />
      <path d="M11 25V15m18 10V15M11 19c6.2 0 6.2 6 9 6s2.8-6 9-6M11 29h18" stroke="white" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round" />
      <circle cx="20" cy="12" r="2" fill="white" />
    </svg>
    <span className="brand-wordmark">UT<span className="brand-dot">.</span>Bridora<span className="brand-caption">KẾT NỐI ĐỂ PHÁT TRIỂN</span></span>
  </span>;
}

export function Notice({ message, tone = 'info' }: { message: string; tone?: 'info' | 'success' | 'error' }) {
  return message ? <div className={'notice notice-' + tone} role="status" aria-live="polite">
    <Icon name={tone === 'error' ? 'alert' : tone === 'success' ? 'check-circle' : 'info'} size={19} /><p>{message}</p>
  </div> : null;
}

type FieldProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'onChange'> & {
  label: string; value: string; onChange: (value: string) => void; hint?: string; icon?: string;
};
export function Field({ label, value, onChange, type = 'text', required = true, hint, icon, ...props }: FieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const password = type === 'password';
  return <div className="field">
    <label htmlFor={id}>{label}</label>
    <div className={'input-control' + (icon ? ' with-icon' : '') + (password ? ' with-toggle' : '')}>
      {icon && <Icon name={icon} size={18} className="input-leading-icon" />}
      <input id={id} value={value} onChange={event => onChange(event.target.value)}
        type={password && visible ? 'text' : type} required={required}
        aria-describedby={hint ? id + '-hint' : undefined} {...props} />
      {password && <button className="password-toggle" type="button" disabled={props.disabled} aria-label={visible ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={visible}
        onClick={() => setVisible(previous => !previous)}><Icon name={visible ? 'eye-off' : 'eye'} size={19} /></button>}
    </div>
    {hint && <p className="field-hint" id={id + '-hint'}>{hint}</p>}
  </div>;
}

export function useAction() {
  const [busy, setBusy] = useState(false), [message, setMessageState] = useState(''), [error, setError] = useState(false);
  const mounted = useRef(true), pending = useRef(0);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const setMessage = useCallback((value: string) => { if (mounted.current) { setError(false); setMessageState(value); } }, []);
  const run = useCallback(async (work: () => Promise<void>) => {
    pending.current += 1;
    if (mounted.current) { setBusy(true); setMessageState(''); setError(false); }
    try { await work(); }
    catch (failure) {
      if (mounted.current) { setMessageState(failure instanceof Error ? failure.message : 'Thao tác thất bại. Vui lòng thử lại.'); setError(true); }
    } finally {
      pending.current -= 1;
      if (mounted.current) setBusy(pending.current > 0);
    }
  }, []);
  return { busy, message, error, setMessage, run };
}

export function EmptyState({ icon = 'search', title, description, children }: {
  icon?: string; title: string; description?: string; children?: ReactNode;
}) {
  return <div className="empty-state"><span className="empty-state-icon"><Icon name={icon} size={26} /></span>
    <h3>{title}</h3>{description && <p>{description}</p>}{children && <div className="empty-state-actions">{children}</div>}</div>;
}
export function LoadingState({ label = 'Đang tải dữ liệu…' }: { label?: string }) {
  return <div className="loading-state" role="status"><span className="spinner" aria-hidden="true" />{label}</div>;
}
export function StatusBadge({ status }: { status: string }) {
  const labels: Record<string, string> = { ...statusNames, SUCCESS: 'Thành công', FAILURE: 'Thất bại', DENIED: 'Bị từ chối' };
  return <span className={'status-badge badge ' + status.toLowerCase().replace(/[^a-z0-9_-]/g, '')}><span className="status-dot" />{labels[status] || status}</span>;
}
export function Pagination({ page, change, busy }: { page: Page<unknown> | null; change: (page: number) => void; busy: boolean }) {
  if (!page) return null;
  return <div className="pagination"><span className="pagination-summary"><strong>{page.totalElements}</strong> kết quả <span className="pagination-divider">·</span> Trang {page.number + 1}/{Math.max(1, page.totalPages)}</span>
    <div className="pagination-buttons"><button className="secondary" disabled={busy || page.number === 0} onClick={() => change(page.number - 1)}><Icon name="chevron-left" size={15} />Trước</button>
      <button className="secondary" disabled={busy || page.number + 1 >= page.totalPages} onClick={() => change(page.number + 1)}>Sau<Icon name="chevron-right" size={15} /></button></div></div>;
}
export const roleNames: Record<string, string> = { STUDENT: 'Sinh viên', ENTERPRISE: 'Đại diện doanh nghiệp', SCHOOL_STAFF: 'Cán bộ trường', ADMIN: 'Quản trị viên' };
export const statusNames: Record<string, string> = { ACTIVE: 'Đang hoạt động', SUSPENDED: 'Tạm khóa', DEACTIVATED: 'Đã vô hiệu hóa', PENDING_ACTIVATION: 'Chờ kích hoạt', PENDING: 'Chờ xác thực', VERIFIED: 'Đã xác thực' };
export const permissionNames: Record<string, string> = {
  'account:self:read': 'Xem tài khoản của tôi', 'account:self:password_change': 'Đổi mật khẩu của tôi',
  'account:admin:status': 'Quản lý trạng thái tài khoản', 'account:admin:revoke': 'Thu hồi phiên đăng nhập',
  'membership:admin:write': 'Quản lý vai trò theo tổ chức', 'organization:admin:create': 'Tạo tổ chức',
  'audit:global:read': 'Tra cứu nhật ký hệ thống', 'student_profile:self:read': 'Xem hồ sơ của tôi',
  'student_profile:self:write': 'Cập nhật hồ sơ của tôi', 'student_profile:shared:read': 'Xem hồ sơ đã được chia sẻ hợp lệ',
  'student_profile:school:read': 'Xem hồ sơ trong phạm vi trường được giao', 'cooperation:enterprise:create': 'Tạo nhu cầu hợp tác tại doanh nghiệp',
  'cooperation:school:review': 'Xét duyệt hợp tác được phân công', 'placement:school:write': 'Quản lý tiếp nhận theo thẩm quyền',
  'internship:enterprise:task_write': 'Giao nhiệm vụ cho kỳ thực tập được giao', 'internship:student:submission_write': 'Nộp sản phẩm thực tập của tôi',
  'internship:enterprise:evaluate': 'Đánh giá kỳ thực tập được phân công', 'matching:enterprise:aggregate_read': 'Xem thống kê tổng hợp được phép'
};
