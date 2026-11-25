import { canLeaveDemand } from '../navigation';
import type { SchoolEvaluation } from './schoolEvaluation';

/** Navigation carries a Case reference only. T3 revalidates school/term, authority and current version. */
export function SchoolFollowUpPanels({ evaluations, placementState, caseId }: { evaluations: SchoolEvaluation[] | null; placementState?: string; caseId: string }) {
  const published=evaluations?.some(row=>row.state==='PUBLISHED');
  const link=(label:string,section:string)=><a className="button secondary" href={'/workspace/cases/'+caseId+'?followup='+section} onClick={e=>{if(!canLeaveDemand())e.preventDefault();}}>{label}</a>;
  return <>
    <section className="t5-detail-card" aria-label="Chính sách hiển thị điểm nhà trường"><h5>Chính sách hiển thị điểm nhà trường</h5>
      <p>Sinh viên chỉ xem điểm khi đánh giá đã công bố, chính sách trường và quyền hiển thị của HR cùng cho phép. Chính sách trường áp dụng cho toàn kỳ.</p>
      {link('Mở công bố điểm và chính sách T3','policy')}
    </section>
    <section className="t5-detail-card" aria-label="Đánh giá doanh nghiệp & vấn đề phối hợp"><h5>Đánh giá doanh nghiệp & vấn đề phối hợp</h5>
      <p>Rubric chất lượng doanh nghiệp và yêu cầu phối hợp được quản lý tại T3. Quyền ghi nhận được kiểm tra riêng; đánh giá chất lượng không tham gia điểm sinh viên.</p>
      {link('Mở chất lượng doanh nghiệp và phối hợp T3','quality')}
    </section>
    <section className="t5-detail-card" aria-label="Đóng hồ sơ và lịch sử nghiệp vụ"><h5>Đóng hồ sơ / chấm dứt thực tập sớm</h5>
      {placementState && <p>Trạng thái tiếp nhận hiện tại: {placementState}.</p>}<p>{published?'Đã đọc được đánh giá công bố từ T3.':'Chưa xác nhận đánh giá đã công bố.'} Máy chủ kiểm tra ngày kết thúc, nhiệm vụ, báo cáo, đánh giá và quyết định trước khi đóng Case.</p>
      {link('Mở tổng kết và đóng Case T3','close')}
      <p>Kết quả tiếp nhận được đồng bộ qua sự kiện sau quyết định đóng Case. Tải lại màn tiếp nhận để xem kết quả đồng bộ.</p>
      <h5>Lịch sử audit theo trường</h5><p>Chưa có API audit dành cho trường. Lịch sử quyết định hợp tác xem tại màn Duyệt hợp tác.</p>
    </section>
  </>;
}
