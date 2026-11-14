import type { Me } from '../../../api';
import { TaskBoard } from '../../internship/pages/TaskBoard';
import { MyInternship } from '../components/MyInternship';
import { StudentNote } from '../components/StudentUI';
import '../styles.css';

// S07 (SRS 14.1) "Nhiệm vụ & sản phẩm": khối F22 của T2 phía trên bảng việc T3 dùng nguyên
// (F23 xem/lọc/lịch, F24 bắt đầu làm, F25 nộp URL v1/v2, lịch sử và nhận xét; đều gọi API T3 thật).
// Báo cáo tuần thuộc S08 nên không mở trong bảng việc này.
// Bố cục theo frame 477:12406: khối kỳ thực tập, ghi chú điều kiện, rồi bảng việc (T3, chỉ đổi trình bày qua CSS trong .student-page).
export function InternshipTasksPage({ me }: { me: Me }) {
  return <div className="student-page student-tasks-page">
    <MyInternship userId={me.userId} />
    <StudentNote>Bạn chỉ có thể bắt đầu nhiệm vụ và nộp bài khi kỳ thực tập chính thức bắt đầu.</StudentNote>
    <TaskBoard student showReports={false} />
  </div>;
}
