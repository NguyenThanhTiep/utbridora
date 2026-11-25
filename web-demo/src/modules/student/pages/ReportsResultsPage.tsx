import { useCallback, useEffect, useState, type ReactNode } from 'react';
import type { Me } from '../../../api';
import { Icon } from '../../../components';
import { WeeklyReports } from '../../internship/pages/WeeklyReports';
import { EvaluationResults } from '../components/EvaluationResults';
import { StudentTabs } from '../components/StudentTabs';
import { StudentIcon } from '../components/StudentUI';

// S08 (SRS 14.1) "Báo cáo & đánh giá" gồm 2 tab: báo cáo tuần dùng nguyên màn T3 (F26, API T3 thật);
// kết quả đánh giá chỉ đọc StudentResultDto CP-WS4 (F27), không tự tính hay dựng điểm.
export type StudentReportTab = 'reports' | 'results';
// Thanh tab phân đoạn như màn Năng lực / Lời mời; dòng phụ chỉ mô tả mục (không phải số liệu).
const tabs: { id: StudentReportTab; label: string; icon: ReactNode; meta: string }[] = [
  { id: 'reports', label: 'Báo cáo tuần', icon: <Icon name="calendar" size={19} />, meta: 'Ghi lại công việc mỗi tuần' },
  { id: 'results', label: 'Kết quả đánh giá', icon: <StudentIcon name="chart" size={19} />, meta: 'Kết quả đã được công bố' }];

export function ReportsResultsPage({ me }: { me: Me }) {
  const [tab, setTab] = useState<StudentReportTab>('reports');
  return <StudentTabs label="Báo cáo và đánh giá" idPrefix="student-report" tabs={tabs} tab={tab} setTab={setTab} variant="segmented"
    render={id => id === 'reports' ? <ScopedReports /> : <EvaluationResults userId={me.userId} />} />;
}

// Như bảng việc T3: mất phiên/quyền thì đóng dữ liệu báo cáo, chỉ mở lại khi người dùng xác nhận.
// Lớp .student-reports chỉ để CSS trình bày khối WeeklyReports (T3) của riêng màn này, không đổi DOM bên trong.
function ScopedReports() {
  const [denied, setDenied] = useState(false), [generation, setGeneration] = useState(0);
  const deny = useCallback(() => setDenied(true), []);
  useEffect(() => { window.addEventListener('session-expired', deny); return () => window.removeEventListener('session-expired', deny); }, [deny]);
  return denied ? <section className="panel"><p role="alert">Phiên hoặc quyền truy cập đã thay đổi. Dữ liệu báo cáo đã được đóng.</p>
    <button type="button" onClick={() => { setDenied(false); setGeneration(previous => previous + 1); }}>Xác nhận lại quyền truy cập</button></section>
    : <div className="student-reports"><ReportFlow /><WeeklyReports key={generation} student mentor={false} denied={deny} /></div>;
}

// Thanh quy trình báo cáo tuần, bám đúng các bước WeeklyReports của T3 cho sinh viên: tạo bản nháp (DRAFT) → nộp (SUBMITTED)
// → mentor nhận xét (REVIEWED) → tạo bản sửa bằng mã lý do khi cần. Chú thích mã trạng thái tiếng Anh mà danh sách của T3 đang hiện.
const flow: { code: string; label: string; title: string; text: string; tone: string; icon: ReactNode }[] = [
  { code: 'DRAFT', label: 'Nháp', title: 'Viết bản nháp', tone: 'blue', icon: <StudentIcon name="pencil" size={18} />,
    text: 'Chọn Thứ Hai đầu tuần, ghi việc đã làm, khó khăn và kế hoạch; lưu nháp và thêm minh chứng.' },
  { code: 'SUBMITTED', label: 'Đã nộp', title: 'Nộp báo cáo', tone: 'amber', icon: <StudentIcon name="send" size={18} />,
    text: 'Lưu nội dung vừa sửa rồi bấm Nộp báo cáo tuần; bản đã nộp chỉ còn xem.' },
  { code: 'REVIEWED', label: 'Đã nhận xét', title: 'Mentor nhận xét', tone: 'green', icon: <Icon name="check-circle" size={18} />,
    text: 'Nhận xét của mentor hiện trong phần chi tiết của báo cáo.' },
  { code: 'Khi cần', label: 'Bản sửa', title: 'Tạo bản sửa', tone: 'purple', icon: <Icon name="refresh" size={18} />,
    text: 'Cần chỉnh nội dung đã nộp: nhập mã lý do (ví dụ UPDATE_PROGRESS) để tạo bản sửa.' }];
function ReportFlow() {
  return <section className="panel student-report-flow" aria-label="Quy trình báo cáo tuần">
    <div className="student-report-flow-head"><h2>Quy trình báo cáo tuần</h2>
      <p>Mã trạng thái trong danh sách: <b>DRAFT</b> nháp · <b>SUBMITTED</b> đã nộp · <b>REVIEWED</b> đã nhận xét.</p></div>
    <ol className="student-flow">{flow.map((step, index) => <li key={step.title} className={'tone-' + step.tone}>
      <span className="student-flow-icon" aria-hidden="true">{step.icon}<b>{index + 1}</b></span>
      <div><h3>{step.title}</h3><span className="student-flow-code">{step.code} · {step.label}</span><p>{step.text}</p></div>
    </li>)}</ol>
  </section>;
}
