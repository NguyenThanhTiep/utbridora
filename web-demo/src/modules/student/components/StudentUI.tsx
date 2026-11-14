import type { ReactNode } from 'react';
import type { CompletenessResult } from '../types';
import '../styles.css';

// Thành phần trình bày dùng chung cho các màn sinh viên theo bộ bàn giao UI/UX 10/11 (frame 477:*).
// Chỉ nhận dữ liệu đã có từ API; không tự dựng số liệu.

export const checkLabels: Record<string, string> = {
  MAJOR_VALID: 'Ngành học hợp lệ', STUDENT_TERM_PRESENT: 'Liên kết sinh viên với học kỳ', SKILLS_DECLARED: 'Đã xác nhận khai báo kỹ năng',
  SKILLS_VALID: 'Có kỹ năng thuộc danh mục kỹ năng chuẩn', AVAILABILITY_DECLARED: 'Đã xác nhận trạng thái rảnh', AVAILABILITY_VALID: 'Có khoảng lịch rảnh hợp lệ'
};
export function StudentKpi({ label, value, children }: { label: string; value: ReactNode; children?: ReactNode }) {
  return <div className="student-kpi"><p className="student-kpi-label">{label}</p><p className="student-kpi-value">{value}</p>{children}</div>;
}

export function StudentNote({ tone = 'info', children }: { tone?: 'info' | 'warning' | 'success'; children: ReactNode }) {
  return <div className={'student-note ' + tone}><p>{children}</p></div>;
}

// Frame 477:11203: thanh tiến độ và danh sách 6 tiêu chí đọc từ kết quả chính sách của server.
export function CompletenessCards({ result }: { result: CompletenessResult }) {
  const passed = result.checks.filter(check => check.passed).length, total = result.checks.length;
  return <>
    <section className="panel student-completeness" aria-label="Độ hoàn thiện hồ sơ">
      <h2>Độ hoàn thiện hồ sơ · {passed}/{total} tiêu chí</h2>
      <div className="student-progress" role="progressbar" aria-label="Tiêu chí hồ sơ đã đạt" aria-valuemin={0} aria-valuemax={total} aria-valuenow={passed}>
        <span style={{ width: total ? (passed / total) * 100 + '%' : 0 }} /></div>
      <p className="student-card-note">{result.complete ? 'Hồ sơ đã đủ các điều kiện theo chính sách hồ sơ.' : 'Để hoàn thiện hồ sơ, bạn cần đáp ứng đủ ' + total + ' điều kiện theo quy định.'}</p>
    </section>
    <section className="panel">
      <ul className="student-checklist" aria-label="Mức hoàn thiện hồ sơ">{result.checks.map(check => <li key={check.code} className={check.passed ? 'passed' : ''}>
        <span aria-hidden="true">{check.passed ? '✓' : '○'}</span><span className="student-sr-only">{check.passed ? 'Đạt: ' : 'Cần bổ sung: '}</span>{checkLabels[check.code] ?? check.code}</li>)}</ul>
    </section>
  </>;
}
