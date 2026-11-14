import { FeaturePending } from '../ui/FeaturePending';
import type { SchoolEvaluation } from './schoolEvaluation';

export function SchoolFollowUpPanels({ evaluations, placementState }: { evaluations: SchoolEvaluation[] | null; placementState?: string }) {
  const published = evaluations?.find(row => row.state === 'PUBLISHED');
  const current = published || evaluations?.[0], mode = current?.visibilityMode;
  return <>
    <section className="t5-detail-card" aria-label="Chính sách hiển thị điểm nhà trường"><div className="t5-card-heading"><h5>Chính sách hiển thị điểm nhà trường</h5>
      <span className={'t5-badge ' + (mode === 'SUMMARY_ONLY' ? 't5-badge-suppressed' : mode === 'DETAILED' ? 't5-badge-success' : 't5-badge-info')}>
        {mode === 'SUMMARY_ONLY' ? 'Chỉ xem điểm tổng kết' : mode === 'DETAILED' ? 'Xem chi tiết tiêu chí & nhận xét' : 'Chưa có chính sách từ API'}</span></div>
      <dl className="portal-key-values"><div><dt>Chỉ xem điểm tổng kết</dt><dd>Sinh viên xem kết quả tổng kết được cho phép.</dd></div>
        <div><dt>Xem chi tiết</dt><dd>Sinh viên xem thêm tiêu chí và nhận xét khi được cấp quyền.</dd></div></dl>
      <p>Công bố đánh giá chưa tự mở quyền xem cho sinh viên. Chính sách của trường và quyền hiển thị từ doanh nghiệp được kiểm tra độc lập.</p>
      <FeaturePending>Chờ kết nối API cập nhật chính sách công bố điểm. Chế độ hiển thị chỉ được ghi nhận khi dữ liệu từ máy chủ có cung cấp.</FeaturePending>
    </section>
    <section className="t5-detail-card" aria-label="Đánh giá doanh nghiệp & vấn đề phối hợp"><div className="t5-card-heading"><h5>Đánh giá doanh nghiệp & vấn đề phối hợp</h5><span className="t5-badge t5-badge-info">Chưa kết nối</span></div>
      <dl className="portal-key-values">{['Mức độ hỗ trợ mentor', 'Đúng cam kết đề cương', 'Tiếp nhận sinh viên'].map(label => <div key={label}><dt>{label}</dt><dd>Chưa có dữ liệu đánh giá</dd></div>)}</dl>
      <p>Đánh giá chất lượng phối hợp của doanh nghiệp được theo dõi riêng với điểm thực tập của sinh viên.</p>
      <FeaturePending>Chờ kết nối API hậu kiểm đối tác để ghi nhận đánh giá và vấn đề phối hợp.</FeaturePending>
    </section>
    <section className="t5-detail-card" aria-label="Đóng hồ sơ và lịch sử nghiệp vụ"><div className="t5-card-heading"><h5>Đóng hồ sơ / chấm dứt thực tập sớm</h5><span className="t5-badge t5-badge-info">Chờ đối chiếu điều kiện</span></div>
      {placementState && <p>Trạng thái tiếp nhận từ máy chủ: {({ ACTIVE: 'Đang thực tập', APPROVED: 'Đã duyệt, chờ bắt đầu', COMPLETED: 'Đã hoàn thành', CANCELLED: 'Đã hủy', PENDING_APPROVAL: 'Chờ trường phê duyệt' } as Record<string, string>)[placementState] || placementState}.</p>}
      <ul className="t5-checklist"><li><span>Đánh giá đã công bố</span><span className={'t5-badge ' + (published ? 't5-badge-success' : 't5-badge-warning')}>{published ? 'Đã có từ T3' : 'Chưa xác nhận'}</span></li>
        {['Đã đến ngày kết thúc thực tập', 'Nhiệm vụ hoàn thành và báo cáo được duyệt', 'Quyết định của nhà trường'].map(label => <li key={label}><span>{label}</span><span className="t5-badge t5-badge-info">Chờ đối chiếu</span></li>)}</ul>
      <div className="t5-rule-note"><strong>Hai hình thức kết thúc</strong><p>Hoàn thành thực tập cần đủ điều kiện nghiệp vụ. Chấm dứt sớm cần quyết định và lý do của nhà trường. Máy chủ sở hữu hồ sơ kiểm tra các điều kiện trước khi ghi nhận.</p></div>
      <FeaturePending>Chờ tích hợp API quyết định kết thúc hồ sơ. Giao diện hiện chỉ theo dõi và không thực hiện đóng hồ sơ.</FeaturePending>
      <h5>Lịch sử audit theo trường</h5><p>Đang chờ T1 cung cấp API và quyền tra cứu audit theo phạm vi trường. Lịch sử quyết định hợp tác vẫn xem tại màn Duyệt hợp tác.</p>
    </section>
  </>;
}
