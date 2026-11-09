# Runtime và demo nội bộ tuần 1

Ngày: 09/11/2026. Phạm vi theo Plan tuần 1 và SRS v1.2.4. Đây là ghi nhận triển khai; không thay chữ ký QA/Lead hoặc tự ban hành contract mới.

## Các điểm nối đã hoàn thiện

- Hồ sơ dùng T2 `AcademicCatalogService`: danh mục có phiên bản theo đúng trường/kỳ, route BC12 `/api/v1/students/me/catalogs`. Chọn ngành trên form, lưu và tải lại qua BC11. T5 Demand dùng cùng provider `DemandMajorCatalogPort`; T2 dùng `TaxonomyReferencePort` của T4 để xác minh mã chuẩn. Database production không được nạp ngành mẫu mặc định.
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
cd D:\HKDN-TTTV-UTH\.work\integration-20261108\repo
.\scripts\start-week1-demo.ps1 -LanIp 192.168.1.241
```

Stack `utbridora-week1-demo`, database `utbridora_week1_demo`, HTTPS `https://192.168.1.241:8443`. Không dùng volume/database của stack `utbridora-local` trên 8088/3307. MySQL và backend không mở cổng host. Hộp thư chỉ ở máy chủ: `http://localhost:8027`.

Mật khẩu ngẫu nhiên nằm ở `DEMO_PASSWORD` trong `.env.week1-demo` (file bị Git/Docker build context loại trừ). Tài khoản tổng hợp: `admin@example.test`, `school@example.test`, `student@example.test`, `hr@example.test`, `mentor@example.test`; coordinator và intern là tiền điều kiện fixture. Không dùng mật khẩu demo cho tài khoản thật.

Profile `demo` và cờ `app.demo.week1.enabled=true` mới cho phép import. Import từ chối database khác tên hoặc database có dữ liệu nhưng chưa có receipt. Khởi động lại giữ nguyên chỉnh sửa. Verification APPROVED, Placement ACTIVE, delegation và invitation accepted trong database này là **tiền điều kiện tổng hợp**, không phải bằng chứng đã xây xong workflow phê duyệt/đăng ký/lời mời của tuần sau. Không tạo metadata CLEAN thay cho scan tệp.

HTTPS dùng CA nội bộ theo [tài liệu Caddy](https://caddyserver.com/docs/automatic-https). Chỉ chia sẻ `root.crt` công khai và kiểm fingerprint trước khi cài vào Trusted Root của máy thử nghiệm; khóa riêng nằm trong volume Caddy, không xuất. Có thể xuất certificate bằng:

```powershell
docker compose --env-file .env.week1-demo -f compose.demo.yaml cp caddy:/data/caddy/pki/authorities/local/root.crt demo-root.crt
```

Nếu Windows chặn kết nối LAN, PowerShell **Administrator** chạy `.\scripts\enable-week1-demo-lan.ps1`. Lệnh chỉ mở TCP 8443 cho LocalSubnet trên Wi-Fi 2, không đổi mạng Public sang Private. Máy thử phải cùng mạng và máy chủ cần bật Docker, không Sleep; địa chỉ LAN có thể đổi theo DHCP. Chưa có phép thử từ máy thứ hai thì không ghi nhận cả 5 team đã truy cập.

```powershell
.\scripts\start-week1-demo.ps1 -Action status
.\scripts\start-week1-demo.ps1 -Action logs
.\scripts\start-week1-demo.ps1 -Action stop
```

Stop giữ dữ liệu. Không dùng `down -v` nếu muốn giữ demo. Kiểm lại HTTPS/session/scope bằng `scripts/check-week1-demo.py --base-url ... --ca-file ...`; cờ `--exercise-profile` chỉ tạo hồ sơ nếu chưa có, dùng trên database demo riêng.

## Đồng bộ phiên bản bằng Docker cho các dev

Mỗi dev clone repository, cài Git và Docker Desktop đang chạy Linux containers. Trong checkout sạch của `main`, cập nhật rồi dựng lại:

```powershell
git pull --ff-only
.\scripts\start-week1-demo.ps1 -LanIp 127.0.0.1
```

Lần đầu script sinh `.env.week1-demo` riêng; sau đó giữ nguyên mật khẩu và các volume khi build lại. Truy cập `https://127.0.0.1:8443`, xuất CA theo hướng dẫn trên để trình duyệt tin cậy HTTPS. Nếu đã có cấu hình, script dùng `DEMO_HOST` đang lưu; tham số `-LanIp` chỉ dùng lúc khởi tạo. Muốn chia sẻ LAN, đặt địa chỉ máy chủ trong `DEMO_HOST` rồi chạy lại script và mở firewall có giới hạn như trên. Không chép mật khẩu/volume của máy chủ vào Git.

Để tái lập chính xác một bản đã kiểm thử, dùng cùng commit đã có CI xanh; image backend/frontend ghi SHA ở label `org.opencontainers.image.revision`. Checkout có thay đổi sẽ được ghi hậu tố `-dirty`. Nếu `git pull --ff-only` báo xung đột/lịch sử phân kỳ, giữ thay đổi và xử lý Git trước khi dựng; không reset để ép đồng bộ.

Docker đồng bộ ứng dụng/cấu hình dựng và fixture ban đầu, **không tự đồng bộ dữ liệu MySQL giữa các máy**. Mỗi máy giữ database riêng; nhóm cần cùng xem/sửa một dữ liệu thì dùng URL máy chủ `https://192.168.1.241:8443` trong cùng LAN. Cần máy chủ bật Docker và không Sleep. Chưa cấu hình truy cập từ Internet.

## Demo nghiệp vụ

1. `student@example.test`: Sinh viên → Thông tin chung → Tạo hồ sơ nếu chưa có → chọn SE, sửa điện thoại/thời gian → Lưu → tải lại. Trường VERIFIED, kỳ ACTIVE và liên kết sinh viên–trường–kỳ đã có; MSSV `00123` là chuỗi, schoolApproved vẫn false. Completeness đầy đủ thuộc tuần 2, không dùng kết quả này làm phê duyệt Placement.
2. `hr@example.test` / `mentor@example.test`: Không gian thực tập → chọn phân công → DEMO-W1 → Xem nhóm DEMO-G1. Đây là read API và assignment thật trên dữ liệu tổng hợp. Các bảng công việc/lịch/đánh giá chưa xây của tuần sau vẫn ghi rõ trạng thái.
3. Doanh nghiệp → Nhu cầu: tạo/sửa bản nháp. Dùng school `...0011`, term `...0021` theo UUID đầy đủ trong fixture; ngành `SE`/`AI`, catalog `school-demo-v1`, taxonomy `skills-v1`, kỹ năng `JAVA`. Snapshot nhu cầu giữ version cũ khi sửa. Form hiện nhận UUID/version; danh mục lựa chọn nâng cao thuộc phần UI tiếp theo.
4. Nhà trường: khung tuần 1 và TermReference đã có. Giao diện phê duyệt verification đầy đủ là task tuần 2; không giới thiệu shell hiện tại như workflow hoàn chỉnh.

Xem [ma trận nghiệm thu](../testing/WEEK1_ACCEPTANCE.md) để biết bằng chứng đã chạy và ca còn NOT RUN.
