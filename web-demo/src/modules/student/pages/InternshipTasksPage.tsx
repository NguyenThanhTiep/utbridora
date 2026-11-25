import type { Me } from '../../../api';
import { TaskBoard } from '../../internship/pages/TaskBoard';
import { MyInternship } from '../components/MyInternship';
import '../styles.css';

// S07 (SRS 14.1) "Nhiệm vụ & sản phẩm": khối F22 của T2 phía trên bảng việc T3 dùng nguyên
// (F23 xem/lọc/lịch, F24 bắt đầu làm, F25 nộp URL v1/v2, lịch sử và nhận xét; đều gọi API T3 thật).
// Báo cáo tuần thuộc S08 nên không mở trong bảng việc này.
// Bố cục theo frame 477:12406: thẻ kỳ thực tập (ghi chú điều kiện ở chân thẻ), rồi bảng việc (T3, chỉ đổi trình bày qua CSS trong .student-page).
export function InternshipTasksPage({ me }: { me: Me }) {
  return <div className="student-page student-tasks-page">
    <MyInternship userId={me.userId}>Bạn chỉ có thể bắt đầu nhiệm vụ và nộp bài khi kỳ thực tập chính thức bắt đầu.</MyInternship>
    <TodayInCalendar />
    <TaskBoard student showReports={false} />
  </div>;
}

// Lịch của TaskBoard (T3) không đánh dấu ngày hôm nay và không thể sửa DOM của nó, nên khoanh ngày hôm nay bằng một quy tắc CSS
// sinh theo ngày giờ máy: chỉ áp khi ô chọn tháng đang là tháng hiện tại; ô ngày là các <div role="gridcell"> duy nhất trong lưới
// nên div:nth-of-type(n) là ngày n. Màu lấy từ biến trong styles.css.
function TodayInCalendar() {
  const now = new Date(), month = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');
  const cell = `.student-tasks-page section.panel:has(> label > input[type="month"][value="${month}"]) [role="grid"] > div[role="gridcell"]:nth-of-type(${now.getDate()})`;
  return <style>{`${cell} { background: var(--student-today-bg); }
${cell} > strong { color: #fff; background: var(--brand); box-shadow: 0 4px 10px rgba(9, 123, 105, .3); }`}</style>;
}
