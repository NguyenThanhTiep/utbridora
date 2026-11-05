# Dev11 + Dev12 — tuần 1, demo local và bàn giao tích hợp

Ngày cập nhật: 05/11/2026. Không thay thế contract Candidate hoặc nghiệm thu QA/Leader.

**Đính chính sau đối chiếu T1-01:** Contract v1.0 đã được người dùng chốt ngày 05/11 theo README contract; không chờ ký riêng các team. Các chỗ “chốt contract” bên dưới cần hiểu là rà implementation theo baseline đã chốt, không phải xin phê duyệt lại. v1.1 chưa tự được phê duyệt. Xem [bảng đối chiếu Dev11–T1-01](../reviews/DEV11_T1_01_COMPATIBILITY_REVIEW.md) về lỗi filter, audit metadata, BC31 và gate còn cần sửa.

## Đã triển khai

- Dev11: API nhu cầu nháp, snapshot/history, khóa phiên bản, idempotency, transaction/audit; bổ sung kiểm tra expiry replay, pagination/filter, soft-delete và adapter configuration.
- Dev12: form doanh nghiệp; cảnh báo dữ liệu chưa lưu khi đóng/chuyển màn hình/đổi tổ chức/đăng xuất và khi đóng tab; chặn điều hướng trong lúc lưu hoặc chưa xác nhận kết quả; lỗi cạnh trường và focus lỗi đầu tiên.
- Trang tài khoản giải thích thiếu quyền doanh nghiệp mà không khẳng định sai rằng org chắc chắn chưa xác minh.
- Khung nhà trường theo grant SCHOOL_STAFF: xác minh và hợp tác ghi rõ đang chờ tích hợp; chưa có thao tác duyệt nghiệp vụ.
- Browser test nối backend và MySQL test thật cho create/reload/edit/reload. Danh mục trong test vẫn là demo.

Form chỉ giữ dữ liệu trong bộ nhớ khi trang còn mở. Cảnh báo rời trang không phải lưu nháp tự động: nếu người dùng xác nhận rời/đóng tab hoặc phiên bị thu hồi, không hứa khôi phục dữ liệu. Không đưa thông tin form vào localStorage dùng chung tài khoản.

## Chạy demo local

`DefaultDemandReferenceAdapter` là danh sách mẫu, không xác minh tính tồn tại/hiệu lực của school, term, academicCatalogVersion hoặc taxonomyVersion. Đã tắt mặc định. Không dùng nó làm bằng chứng tích hợp T4.

Trong terminal khởi động backend local, đặt:

```powershell
$env:T5_DEMAND_DEMO_REFERENCES='true'
$env:T5_DEMAND_CONTRACT_APPROVED='true'
.\mvnw.cmd spring-boot:run
```

Giữ cấu hình DB/mail hiện có. Hai biến trên chỉ dùng để diễn thử riêng theo Candidate; việc bật cờ local không có nghĩa các team đã ký contract. Không thay đổi file `.env`/`.env.local` của người dùng trong lần triển khai này. Nếu chạy bằng Docker, phải truyền biến vào environment của service backend; chỉ thêm biến ở host không tự truyền vào container.

Tài khoản phải có membership ENTERPRISE hoạt động và org VERIFIED. Trường phải là UUID tổ chức có thật để thỏa FK database. Kỳ dùng UUID fixture do nhóm thống nhất; demo adapter chưa kiểm kỳ. Điền đủ form như yêu cầu đã chọn. Có thể dùng major `IT`, skill `JAVA` hoặc `REACT`, phiên bản mẫu `v1`; đây không phải danh mục production. Danh sách mã demo đầy đủ nằm trong adapter.

Test tự tạo trường/doanh nghiệp/user riêng nên không cần sửa database local:

```powershell
.\mvnw.cmd '-Dit.test=DemandIT' '-Ddemand.e2e=true' verify
cd web-demo
npx.cmd playwright test tests/demand.spec.ts
npm.cmd run build
```

Docker Linux engine, JDK, Node dependencies và Playwright Chromium phải sẵn sàng. Không chạy hai bộ Playwright cùng lúc vì cùng dùng cổng 5178. `demand-live.spec.ts` chạy qua harness DemandIT; chạy đơn lẻ không có fixture env sẽ skip.

## Chờ ai và nối ở đâu

| Phụ thuộc | Việc cần bàn giao | Điểm nối |
|---|---|---|
| T1/owner-consumer | CP-T5UI1/BC31, org header, mã lỗi, version/hash, slot migration | Controller/DTO, DemandJson, migration V8 |
| T4-01 | Mã kỹ năng canonical, taxonomy version, quy tắc stale/unknown/unavailable và giao diện tra cứu/validation đã chốt | DemandReferencePort và form chọn kỹ năng |
| Nguồn học vụ theo phân công T2/T4 | Mã ngành thuộc trường và academic catalog version | DemandReferencePort, form chọn ngành |
| Dev10/T5 | School/term hợp lệ và gate verification theo trường | DemandReferencePort; không đồng nhất với organizations.status T1 |
| QA05/Leader | Review scope tuần 1, evidence và nghiệm thu | Báo cáo test, PR sau review |

Adapter thật phải kiểm school/term/catalog/skill/version theo dữ liệu authoritative; không chỉ giữ allowlist hoặc luôn trả thành công. Lỗi nghiệp vụ cần mã ổn định và `fieldErrors`; outage trả DEPENDENCY_UNAVAILABLE để UI giữ nội dung. Chuẩn hóa code phải thống nhất giữa dữ liệu lưu và dữ liệu được kiểm.

Khi ghép: tắt T5_DEMAND_DEMO_REFERENCES, cung cấp đúng một bean DemandReferencePort thật, chốt contract, chạy consumer tests với case hợp lệ/unknown/stale/cross-school/outage, sau đó chạy DemandIT và browser thật với fixture nguồn mới. Không đổi chữ ký port theo phỏng đoán khi T4 chưa bàn giao.

## Chưa tuyên bố hoàn thành

Danh mục thật, chọn tên doanh nghiệp/trường/kỳ qua API được phân quyền, school-scoped matching, verification/cooperation/placement chính thức và nghiệm thu không nằm trong kết quả PASS local này. Không tự thêm quyền admin để vượt gate nghiệp vụ.
