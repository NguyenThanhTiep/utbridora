# Runtime và demo nội bộ tuần 1

Ngày: 09/11/2026. Phạm vi theo Plan tuần 1 và SRS v1.2.4. Đây là ghi nhận triển khai; không thay chữ ký QA/Lead hoặc tự ban hành contract mới.

## Các điểm nối đã hoàn thiện

- Hồ sơ dùng T2 `AcademicCatalogService`: danh mục có phiên bản theo đúng trường/kỳ, route BC12 `/api/v1/students/me/catalogs`. Chọn ngành trên form, lưu và tải lại qua BC11. T5 Demand dùng cùng provider `DemandMajorCatalogPort`; T2 dùng `TaxonomyReferencePort` của T4 để xác minh mã chuẩn. Guard đọc hiện hành có khóa cả version và mã, tránh snapshot MySQL cũ chấp nhận mã vừa ngừng hoạt động. Database production không được nạp ngành mẫu mặc định.
- T3 đăng ký `WorkspaceService` qua `WorkspaceConfiguration`; adapter JDBC lưu cohort/nhóm/task, CAS Case qua port owner, dùng ledger chung và audit cùng transaction. Tiếp nhận ID Placement, preview/confirm nhóm, thêm/chuyển thành viên, tạo/đọc Task có guard hiện hành. Chưa triển khai artifact/submission/review của tuần sau.
- T5 `PlacementReadPort` đọc nguồn owner với khóa trong transaction. `PlacementLifecyclePublisher` là hook nội bộ cho transaction T5, không phải API tự đổi trạng thái. Outbox giao ít nhất một lần; T3 kiểm provenance bằng payload/delivery ID của outbox, đọc nguồn hiện hành và commit inbox + Case + lịch sử projection nguyên tử. Có dedup, stale, retry 1/5/30/300/1800 giây rồi DLQ. Không mở REST callback từ trình duyệt. Luồng phê duyệt/tạo Placement đầy đủ thuộc tuần 3.
- Trước ACTIVE/effectiveAt, Task bị từ chối. T5 hủy Placement dù event chưa tới T3 vẫn chặn Task mới. Case terminal không được mở lại bằng Activated mới.

## Binding để giao diện chọn phân công

Contract nền đã có ContextAssignment/Scope và yêu cầu dùng context được chọn. Hai bản contract chưa chỉ định transport của thao tác chọn. Bản triển khai này công bố rõ binding đọc sau, không ghi thành quyết định đã được Lead phê duyệt:

- `GET /api/v1/t3/context-assignments/me`: danh sách ContextAssignment còn hiệu lực của principal. Không suy phân công từ membership.
- `X-Context-Assignment-Id`: selector khi đọc/ghi workspace. Backend kiểm lại chủ thể, membership, tổ chức VERIFIED, quyền cụ thể, phạm vi, thời hạn, lời mời Mentor đã chấp nhận, chuỗi HR/coordinator, delegation trường và xác minh trường–doanh nghiệp từ T5. Header không cấp quyền.
- Không dùng membership đầu tiên, không thêm global role HR/Mentor và không cho ADMIN quyền nghiệp vụ mặc định. Giao diện thật dùng `liveWorkspaceGateway`; harness mock được giữ để kiểm UX riêng.

## Khởi động trên máy hiện tại

```powershell
cd D:\HKDN-TTTV-UTH\UTBridora_SourceCode\utbridora-t1-full-foundation
.\run-local.cmd start
```

Ứng dụng: **http://localhost:8088**. Hộp thư: **http://localhost:8026**. Stack `utbridora-local` dùng database `utbridora`, MySQL tại `127.0.0.1:3307`; backend không mở cổng host. Các cổng local chỉ bind loopback, không phải URL dùng chung từ máy khác.

`run-local.cmd` đọc `.env.local`; lần đầu tạo cấu hình và mật khẩu ngẫu nhiên nếu chưa có cấu hình/volume cũ, đồng thời bật `LOCAL_DEMO_ENABLED=true`. Seed local tạo tài khoản/dữ liệu mẫu một lần. Mật khẩu admin khởi tạo nằm ở `BOOTSTRAP_ADMIN_PASSWORD`, sáu tài khoản nghiệp vụ dùng `DEMO_PASSWORD` riêng trong file đó. Tài khoản đã đổi mật khẩu tiếp tục dùng mật khẩu hiện hành; script không reset mật khẩu khi khởi động lại. Xem [hướng dẫn demo cho dev](LOCAL_DEMO.md).

Trên máy demo đã chuẩn bị ngày 09/11/2026, dữ liệu tổng hợp đã được nhập bổ sung vào database chung sau khi thử migration/import trên bản sao và đối chiếu dữ liệu cũ. Sáu tài khoản `school`, `coordinator`, `hr`, `mentor`, `student`, `intern` tại miền `example.test` dùng mật khẩu trong `DEMO_PASSWORD` của `.env.local-demo`. Admin và tài khoản cũ giữ mật khẩu riêng. Hai file `.env.local`/`.env.local-demo` bị Git và Docker build context loại trừ; không chia sẻ hoặc in secret ra log.

Checkout mới dùng `LocalDemoBootstrap` và `demo/local.sql` trên database local mới; seed đòi profile `local` và cờ bật, từ chối database cũ chưa có receipt. File cấu hình local cũ không tự được bật cờ. Bộ triển khai HTTPS 8443 riêng đã được gỡ. Verification APPROVED, Placement ACTIVE, delegation và invitation accepted trong dữ liệu demo là **tiền điều kiện tổng hợp**, không phải bằng chứng hoàn thành workflow phê duyệt/đăng ký/lời mời của tuần sau. Không tạo metadata CLEAN thay cho scan tệp.

```powershell
.\run-local.cmd logs
.\run-local.cmd stop
```

Stop giữ database và cấu hình. Không dùng `down -v` hoặc restore database demo đè lên database chung. Việc gỡ file triển khai 8443 không tự dừng/xóa container, volume, chứng chỉ hoặc firewall đã có trên máy.

## Cập nhật phiên bản bằng Docker cho các dev

Mỗi dev clone repository, cài Git và Docker Desktop đang chạy Linux containers. Trong checkout sạch có thể cập nhật rồi dựng lại:

```powershell
git pull --ff-only
.\run-local.cmd start
```

Giữ `.env.local` và volume cũ để giữ mật khẩu/dữ liệu. Nếu checkout có thay đổi hoặc lịch sử phân kỳ, xử lý Git trước khi cập nhật; không reset để ép đồng bộ. Để tái lập bản đã kiểm thử, dùng đúng commit có CI xanh. CI dựng frontend, chạy unit/integration và browser theo cấu hình `.github/workflows/verify.yml`.

Docker dựng cùng ứng dụng/cấu hình, **không tự đồng bộ dữ liệu MySQL giữa các máy**. Chia sẻ môi trường qua LAN/Internet cần cấu hình triển khai và URL riêng; có thể dùng `compose.staging.yaml` làm nền tảng. Kiểm chứng HTTPS, session và truy cập từ máy khác riêng; kết quả localhost không chứng minh các điều kiện đó.

## Demo nghiệp vụ

Các bước sau áp dụng cho máy đã có dữ liệu demo trong database chung:

1. `student@example.test`: Sinh viên → Thông tin chung → sửa ngành, điện thoại/thời gian → Lưu thông tin chung → tải lại. Hồ sơ đã có ngành SE; trường VERIFIED, kỳ ACTIVE và liên kết sinh viên–trường–kỳ đã có; MSSV `00123` là chuỗi, schoolApproved vẫn false. Completeness đầy đủ thuộc tuần 2, không dùng kết quả này làm phê duyệt Placement.
2. `hr@example.test` / `mentor@example.test`: Không gian thực tập → chọn phân công → DEMO-W1 → Xem nhóm DEMO-G1. Đây là read API và assignment thật trên dữ liệu tổng hợp. Các bảng công việc/lịch/đánh giá chưa xây của tuần sau vẫn ghi rõ trạng thái.
3. `hr@example.test`: Nhu cầu thực tập → bản nháp **DEMO - Java Internship** → sửa/lưu/tải lại hoặc tạo bản nháp mới. School `00000000-0000-4000-8000-000000000011`, term `00000000-0000-4000-8000-000000000021`; ngành `SE`/`AI`, catalog `school-demo-v1`, taxonomy `skills-v1`, kỹ năng `JAVA`. Snapshot nhu cầu giữ version cũ khi sửa. Form hiện nhận UUID/version; danh mục lựa chọn nâng cao thuộc phần UI tiếp theo.
4. Nhà trường: khung tuần 1 và TermReference đã có. Giao diện phê duyệt verification đầy đủ là task tuần 2; không giới thiệu shell hiện tại như workflow hoàn chỉnh.

Xem [ma trận nghiệm thu](../testing/WEEK1_ACCEPTANCE.md) để biết bằng chứng đã chạy và ca còn NOT RUN.
