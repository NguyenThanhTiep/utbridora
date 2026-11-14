import type { Me } from '../../../api';
import { TaskBoard } from '../../internship/pages/TaskBoard';
import { MyInternship } from '../components/MyInternship';
import '../styles.css';

// S07 (SRS 14.1) "Nhiệm vụ & sản phẩm": khối F22 của T2 phía trên bảng việc T3 dùng nguyên
// (F23 xem/lọc/lịch, F24 bắt đầu làm, F25 nộp URL v1/v2, lịch sử và nhận xét; đều gọi API T3 thật).
// Báo cáo tuần thuộc S08 nên không mở trong bảng việc này.
export function InternshipTasksPage({ me }: { me: Me }) {
  return <div className="student-page">
    <MyInternship userId={me.userId} />
    <TaskBoard student showReports={false} />
  </div>;
}
