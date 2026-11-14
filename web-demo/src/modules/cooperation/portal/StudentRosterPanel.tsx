import { useId } from 'react';
import { Icon } from '../../../components';
import { FeaturePending } from '../ui/FeaturePending';

const fields = [
  ['studentCode', 'Mã sinh viên'], ['fullName', 'Họ và tên'], ['schoolEmail', 'Email nhà trường'],
  ['cohort', 'Khóa học'], ['majorCode', 'Mã ngành'],
];

export function StudentRosterPanel() {
  const fileId = useId();
  return <section className="panel school-roster-panel" aria-label="Nhập danh sách & Kích hoạt sinh viên">
    <div className="t5-section-heading"><span className="portal-card-icon"><Icon name="users" /></span>
      <div><h2>Nhập danh sách & Kích hoạt sinh viên</h2><p>Chuẩn bị danh sách theo trường và kỳ trước khi cấp tài khoản.</p></div></div>
    <FeaturePending>Chờ kết nối API nhập danh sách sinh viên và theo dõi kích hoạt từ Team 1. Chưa có tệp hay tài khoản nào được xử lý tại đây.</FeaturePending>
    <ol className="t5-process-steps" aria-label="Các bước nhập danh sách sinh viên">
      {['Tải tệp lên', 'Xem trước & kiểm tra', 'Chọn dòng hợp lệ', 'Xác nhận nhập'].map((step, index) => <li key={step}><span aria-hidden="true">{index + 1}</span>{step}</li>)}
    </ol>
    <div className="t5-detail-grid"><section className="t5-detail-card" aria-label="Chuẩn bị tệp danh sách">
      <h3>Tệp CSV hoặc XLSX</h3><p>Các cột cần chuẩn bị:</p>
      <dl className="t5-roster-columns">{fields.map(([code, label]) => <div key={code}><dt><code>{code}</code></dt><dd>{label}</dd></div>)}</dl>
      <p className="field-hint">Mã sinh viên không được trùng trong tệp hoặc danh sách hiện có. Email nhà trường phải đúng định dạng; mã ngành phải thuộc danh mục chính thức.</p>
      <fieldset disabled><label htmlFor={fileId}>Chọn tệp danh sách sinh viên</label>
        <input className="cooperation-sr-only" id={fileId} type="file" accept=".csv,.xlsx" />
        <div className="button-row"><button type="button" className="secondary">Chọn tệp CSV/XLSX</button><button type="button">Xem trước danh sách</button></div></fieldset>
    </section><section className="t5-detail-card" aria-label="Theo dõi xử lý và kích hoạt">
      <h3>Theo dõi xử lý và kích hoạt</h3><p>Chú giải trạng thái xử lý sau khi dịch vụ được kết nối:</p>
      <ul className="t5-status-legend"><li><span className="t5-badge t5-badge-info">Đang chờ xử lý</span><span>Đã xếp hàng, chưa bắt đầu nhập.</span></li>
        <li><span className="t5-badge t5-badge-info">Đang xử lý</span><span>Dịch vụ đang tạo hoặc cập nhật tài khoản.</span></li>
        <li><span className="t5-badge t5-badge-success">Đã hoàn thành</span><span>Đã xử lý việc nhập tài khoản.</span></li>
        <li><span className="t5-badge t5-badge-danger">Thất bại</span><span>Cần xem lỗi và điều kiện thử lại.</span></li></ul>
      <div className="t5-rule-note"><strong>Ba trạng thái được theo dõi riêng</strong>
        <p>Nhập danh sách hoàn thành chưa có nghĩa email đã gửi. Gửi email thành công chưa có nghĩa sinh viên đã kích hoạt tài khoản.</p>
        <p>Nhập tài khoản không tự xác nhận sinh viên đủ điều kiện thực tập.</p></div>
    </section></div>
  </section>;
}
