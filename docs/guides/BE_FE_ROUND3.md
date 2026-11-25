# BE–FE lượt 3: lịch sử hợp tác và thống kê Matching

Base lượt 2: `2575e8acaf41b178da36f3c638c118ace66f4005`, kế thừa lượt 1 tại `855be104112014107f8cdbedb90143842421e53f`. Không thay backend, SQL, SRS/Contract Pack/extensions đóng băng, ranking hoặc cấu hình runtime AI/scanner/worker. Chỉ nối các API đã có và thêm kiểm chứng; main chưa được merge hoặc deploy trong lượt này.

## Lịch sử các lần gửi hợp tác

Nhà trường mở **Duyệt hợp tác → Xem nội dung đề nghị → Tải lịch sử các lần gửi**. Component nằm cạnh nội dung xét duyệt hiện hành và chỉ tải khi người dùng yêu cầu. GET `/school/cooperations/{id}/submission-history` dùng `X-Organization-Id`, page/size=25 và quyền `cooperation:school:review` theo ủy nhiệm trường–kỳ hiện hành. Không mở thêm quyền cho Enterprise hoặc ADMIN global; không suy ra quyền từ một ID hay từ menu.

API cung cấp submissionNo/submittedAt, demandVersion/criteriaVersion/quantitySnapshot và quyết định mới nhất của từng lần gửi cùng thời gian, mã lý do, căn cứ. UI giữ đúng các giá trị snapshot này, hiển thị rõ lần chưa có quyết định và không dùng số liệu lịch sử thay nhu cầu hoặc expectedVersion đang xét duyệt. API hiện không trả toàn bộ tiêu chí của snapshot cũ, nên màn này không tự dựng chi tiết tiêu chí đã lưu trước đây. Đổi trường/hồ sơ hoặc sau lệnh làm thay đổi hợp tác sẽ reset component, hủy đọc cũ và xóa dữ liệu. Tải lại/mất quyền không giữ bảng lịch sử cũ. Lịch sử chỉ đọc, không phát sinh quyết định, proposal hoặc Placement.

## Thống kê thực thi Matching dành cho Admin

ADMIN global có `audit:global:read` mở **Thống kê Matching**, nhập tùy chọn demandId UUID và thời gian theo múi giờ máy người dùng. GET `/admin/matching-statistics` có from bao gồm đầu khoảng, until không bao gồm cuối khoảng; UI chuyển datetime-local thành ISO UTC. Cần from<until nếu nhập cả hai. Có thể bỏ một hoặc cả hai mốc và demandId. Không tự tải toàn hệ thống khi mới mở màn.

Kết quả là số dòng audit có action `MATCH_RUN_COMPLETED`, số actor_id khác nhau và thời điểm đầu/cuối trong bộ lọc. Không đồng nhất số dòng audit với số sinh viên, số nhu cầu riêng biệt, số model inference, chất lượng model hoặc thống kê bảo mật dành cho doanh nghiệp. Danh tính actor, sinh viên và hồ sơ không được hiển thị từ aggregate này. Số liệu 0 có thông báo riêng; lỗi tải hoặc 403 không hiển thị 0 thay lỗi, không giữ kết quả trước đó. Đổi bộ lọc hủy yêu cầu cũ và xóa kết quả; phản hồi muộn không ghi đè bộ lọc mới. Không có export mới hoặc thay đổi audit.

## Kiểm chứng và giới hạn bàn giao

`CooperationReportingIT` dùng MySQL 8.4 riêng để kiểm 26 lần gửi/phân trang/metadata snapshot/decision, quyền trường–kỳ/thu hồi/khác trường/Admin/Enterprise, bộ lọc demand/time inclusive-exclusive của statistics, sai input và empty result. Test browser thật đi qua menu trường và Admin, xem trang lịch sử thứ hai, lọc số liệu, ngăn khoảng thời gian sai và kiểm tra 390px; backend harness truyền API_PROXY_TARGET đến server test riêng, không mock HTTP. Các browser fixture bổ sung kiểm tra scope reset, 404 xóa dữ liệu, abort phản hồi cũ, zero/403 và không lộ menu cho School; chúng tách khỏi bằng chứng MySQL thật.

Giữ unit/integration/browser/contract/build cùng kiểm chứng scanner/operations của CI hiện hữu. Chốt bằng exact commit, inventory test và 5 raw artifact ZIP/API SHA256/XML; không chạy integration hoặc container trên Docker demo local. Kết quả là DEV verification, không phải QA/Lead/nhà trường nghiệm thu, R31 load hoặc VPS acceptance.

Lượt 1–3 nối các nhóm BE–FE đã chốt: phân công, preference/invitation, roster và T5–T3 follow-up, lịch sử hợp tác và Admin Matching statistics. School-scoped audit vẫn chưa có API tương ứng; không dùng Admin audit thay quyền trường. Actual AI/model quality, pilot và các cổng vận hành/nghiệm thu vẫn riêng. Merge main cần tác vụ merge được yêu cầu; sau merge/push main phải đồng bộ source gốc an toàn, giữ Plan/config/stash. Không tự restart/rebuild Docker demo.
