import { useEffect, useRef, type CSSProperties, type ReactNode } from 'react';
import { Icon } from '../../../components';
import type { CompletenessResult } from '../types';
import '../styles.css';

// Thành phần trình bày dùng chung cho các màn sinh viên theo bộ bàn giao UI/UX 10/11 (frame 477:*).
// Chỉ nhận dữ liệu đã có từ API; không tự dựng số liệu.

export const checkLabels: Record<string, string> = {
  MAJOR_VALID: 'Ngành học hợp lệ', STUDENT_TERM_PRESENT: 'Liên kết sinh viên với học kỳ', SKILLS_DECLARED: 'Đã xác nhận khai báo kỹ năng',
  SKILLS_VALID: 'Có kỹ năng thuộc danh mục kỹ năng chuẩn', AVAILABILITY_DECLARED: 'Đã xác nhận trạng thái rảnh', AVAILABILITY_VALID: 'Có khoảng lịch rảnh hợp lệ'
};

// Biểu tượng riêng cho các tab sinh viên (bộ Icon dùng chung chưa có): cùng khung 24 và nét 1.7 với Icon.
const studentIcons = {
  send: <path d="M21 3 10 14M21 3l-7 18-4-7-7-4 18-7Z" />,
  pencil: <path d="M4 20h4L19 9l-4-4L4 16v4ZM14 6l4 4" />,
  trash: <path d="M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6" />,
  link: <path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1" />,
  chart: <path d="M4 20h16M7 16v-5m5 5V7m5 9v-8" />,
  folder: <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7Z" />,
  award: <><circle cx="12" cy="9" r="6" /><path d="m8.5 14-1.5 7 5-3 5 3-1.5-7" /></>,
  file: <><path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z" /><path d="M14 3v5h5M9 13h6M9 17h4" /></>,
  paperclip: <path d="m21 11-8.6 8.6a5 5 0 0 1-7-7l8.6-8.6a3.3 3.3 0 0 1 4.7 4.7l-8.6 8.6a1.7 1.7 0 0 1-2.4-2.4L15.5 7" />,
  phone: <path d="M5 4h4l2 5-2.5 1.5a11 11 0 0 0 5 5L15 13l5 2v4a2 2 0 0 1-2 2A16 16 0 0 1 3 6a2 2 0 0 1 2-2Z" />,
  upload: <path d="M12 16V4m-5 5 5-5 5 5M4 16v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3" />,
  sun: <><circle cx="12" cy="12" r="4" /><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" /></>,
  moon: <path d="M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5Z" />
};
export function StudentIcon({ name, size = 16 }: { name: keyof typeof studentIcons; size?: number }) {
  return <svg className="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"
    strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{studentIcons[name]}</svg>;
}

// Logo chữ của doanh nghiệp: chữ cái đầu của từ đầu và từ cuối trong tên (chỉ để nhìn, không có ảnh logo thật).
export const orgInitials = (name: string) => { const words = name.split(/[^\p{L}\p{N}]+/u).filter(Boolean);
  return ((words[0]?.[0] ?? '') + (words.length > 1 ? words[words.length - 1][0] : '')).toUpperCase() || '?'; };

// Thẻ số liệu Tổng quan (frame Trang chủ 477:12990): ô biểu tượng chuyển màu theo tông + tên, số lớn, thanh tiến độ (nếu có), gợi ý.
export type StudentTone = 'green' | 'blue' | 'purple' | 'amber';
export function StudentStat({ tone, icon, value, label, hint, progress, children }: {
  tone: StudentTone; icon: string; value: string; label: string; hint: string; progress?: number; children?: ReactNode;
}) {
  return <div className={'student-home-stat tone-' + tone}>
    <span className="student-home-stat-icon"><Icon name={icon} size={20} /></span>
    <p className="student-home-stat-label">{label}</p>
    <p className={'student-home-stat-value' + (/^[\d/]+$/.test(value) ? '' : ' is-text')}>{value}</p>
    {progress !== undefined && <span className="student-home-stat-bar" aria-hidden="true" style={{ '--value': progress + '%' } as CSSProperties}><span /></span>}
    <p className="student-home-stat-hint">{hint}</p>
    {children}
  </div>;
}

// Tiến độ học kỳ theo ngày trên máy từ startDate/endDate (YYYY-MM-DD, BC21): % đã qua, tuần thứ mấy / tổng số tuần, số ngày còn lại.
// Chỉ là phép tính hiển thị từ ngày kỳ do API trả, không phải dữ liệu tiến độ thực tập.
const DAY = 86_400_000;
const dayOf = (value: string) => { const [year, month, day] = value.split('-').map(Number); return new Date(year, month - 1, day).getTime(); };
export function termProgress(startDate: string, endDate: string) {
  const now = new Date(), today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
  const start = dayOf(startDate), end = dayOf(endDate), total = Math.max(1, Math.round((end - start) / DAY)), elapsed = Math.round((today - start) / DAY);
  return { status: elapsed < 0 ? 'before' as const : today > end ? 'after' as const : 'during' as const,
    // Ngày cuối kỳ khi tổng số ngày chia hết cho 7 không được ra "Tuần 35/34".
    percent: Math.min(100, Math.max(0, Math.round(elapsed / total * 100))), week: Math.min(Math.ceil(total / 7), Math.floor(elapsed / 7) + 1), weeks: Math.ceil(total / 7),
    daysLeft: Math.round((end - today) / DAY), daysUntil: -elapsed };
}

// Thông báo thành công dạng thẻ nổi góc phải (Figma GH-STU-*-TOAST): dấu ✓, tiêu đề theo thao tác, nội dung thông báo, nút đóng.
// Không che màn và tự ẩn sau 6 giây; vùng role="status" luôn có sẵn để trình đọc màn hình đọc thông báo khi xuất hiện.
const toastTitle = (message: string) => message.startsWith('Đã thêm') ? 'Thêm thành công' : message.startsWith('Đã cập nhật') ? 'Cập nhật thành công'
  : message.startsWith('Đã xóa') ? 'Xóa thành công' : message.startsWith('Đã lưu') ? 'Lưu thành công' : message.startsWith('Đã gửi') ? 'Gửi thành công'
  : message.startsWith('Đã rút') ? 'Rút thành công' : message.startsWith('Đã tải') ? 'Tải lên thành công' : message.startsWith('Đã tạo') ? 'Tạo thành công' : 'Thành công';
export function StudentToast({ message, onClose }: { message: string; onClose: () => void }) {
  const close = useRef(onClose);
  close.current = onClose;
  useEffect(() => {
    if (!message) return;
    const timer = setTimeout(() => close.current(), 6000);
    return () => clearTimeout(timer);
  }, [message]);
  return <div className="student-toast-region" role="status" aria-live="polite">
    {message && <div className="student-toast">
      <span className="student-toast-icon" aria-hidden="true"><Icon name="check" size={16} /></span>
      <div className="student-toast-body"><p className="student-toast-title">{toastTitle(message)}</p><p className="student-toast-text">{message}</p></div>
      <button type="button" className="student-toast-close" aria-label="Đóng thông báo" onClick={() => close.current()}><Icon name="close" size={16} /></button>
    </div>}
  </div>;
}

export function StudentNote({ tone = 'info', children }: { tone?: 'info' | 'warning' | 'success'; children: ReactNode }) {
  return <div className={'student-note ' + tone}><p>{children}</p></div>;
}

// Frame 477:11203: độ hoàn thiện và các tiêu chí đọc từ kết quả chính sách của server, đặt trong thẻ hồ sơ đầu trang S02:
// vòng tròn số tiêu chí đạt + nhãn trạng thái, rồi mỗi tiêu chí là một chip (xanh = đạt, vàng = cần bổ sung).
export function CompletenessRing({ result }: { result: CompletenessResult }) {
  const passed = result.checks.filter(check => check.passed).length, total = result.checks.length, missing = total - passed;
  return <div className="student-ring-block">
    <div className="student-ring" role="progressbar" aria-label="Tiêu chí hồ sơ đã đạt" aria-valuemin={0} aria-valuemax={total} aria-valuenow={passed}
      style={{ '--value': total ? (passed / total) * 100 : 0 } as CSSProperties}><span><b>{passed}/{total}</b><small>tiêu chí</small></span></div>
    <div className="student-ring-text"><h3>Độ hoàn thiện hồ sơ</h3>
      <span className={'student-status ' + (result.complete ? 'tone-success' : 'tone-warning')}>
        {result.complete ? 'Hồ sơ đủ điều kiện' : missing ? 'Còn ' + missing + ' tiêu chí cần bổ sung' : 'Hồ sơ chưa đủ điều kiện'}</span></div>
  </div>;
}
export function CompletenessChecklist({ result }: { result: CompletenessResult }) {
  return <ul className="student-checklist" aria-label="Mức hoàn thiện hồ sơ">{result.checks.map(check => <li key={check.code} className={check.passed ? 'passed' : ''}>
    <span aria-hidden="true">{check.passed ? <Icon name="check" size={14} /> : '!'}</span><span className="student-sr-only">{check.passed ? 'Đạt: ' : 'Cần bổ sung: '}</span>{checkLabels[check.code] ?? check.code}</li>)}</ul>;
}
