# Chạy và trình diễn dữ liệu demo trên Docker local

## 1. Chuẩn bị và khởi động

Cài Git, Docker Desktop và bật **Linux containers**. Không cần cài riêng Java, Maven, Node hoặc MySQL. Cần quyền truy cập repository private và kết nối Internet khi tải image/dependency lần đầu.

```powershell
git clone https://github.com/NguyenThanhTiep/utbridora.git
cd utbridora
# Checkout nhánh/commit được bàn giao nếu thay đổi chưa được tích hợp vào main.
.\run-local.cmd start
```

Với checkout mới, script sinh `.env.local` với mật khẩu ngẫu nhiên, bật `LOCAL_DEMO_ENABLED=true` và dựng database local mới. Sau Flyway, seed tạo dữ liệu tổng hợp một lần; chờ script báo khởi động thành công rồi mở:

| Thành phần | Địa chỉ mặc định |
|---|---|
| Giao diện và API | http://localhost:8088 |
| Hộp thư Mailpit | http://localhost:8026 |
| MySQL/Workbench | `127.0.0.1:3307`, database `utbridora`, user `bridora`; mật khẩu `DB_PASSWORD` trong `.env.local` |

Nếu cổng bị chiếm, đổi `LOCAL_HTTP_PORT`, `MAILPIT_HTTP_PORT`, `LOCAL_MYSQL_PORT` trong `.env.local`, rồi chạy lại. Ba cổng phải khác nhau. Cấu hình này chỉ bind loopback; không dùng URL LAN 8443 hoặc cài chứng chỉ.

## 2. Tài khoản

Mật khẩu **khác nhau giữa các máy**, không được commit/chia sẻ `.env.local` hoặc đưa giá trị vào log, ảnh chụp hay tài liệu.

| Email mặc định | Mật khẩu trong `.env.local` | Nội dung sử dụng |
|---|---|---|
| `admin@example.test` | `BOOTSTRAP_ADMIN_PASSWORD` | Quản lý tài khoản/tổ chức, xem audit theo quyền Admin |
| `student@example.test` | `DEMO_PASSWORD` | Xem, sửa và lưu hồ sơ sinh viên |
| `hr@example.test` | `DEMO_PASSWORD` | Nhu cầu thực tập và nhóm được phân công HR |
| `mentor@example.test` | `DEMO_PASSWORD` | Nhóm được phân công Mentor |
| `school@example.test` | `DEMO_PASSWORD` | Khung nhà trường, phạm vi trường và TermReference |
| `coordinator@example.test`, `intern@example.test` | `DEMO_PASSWORD` | Tiền điều kiện chuỗi phân công và hồ sơ thực tập |

HR/Mentor là phân công có phạm vi của tài khoản ENTERPRISE, không phải global role hoặc Admin. `student@example.test` là tài khoản demo hồ sơ; `intern@example.test` là sinh viên gắn với Case của nhóm thực tập.

Nếu tự cấu hình email bootstrap khác trước lần chạy đầu, tài khoản Admin dùng email đó. Sau khi đổi mật khẩu trên giao diện, dùng mật khẩu mới; seed và restart không đặt lại mật khẩu.

## 3. Kịch bản demo

**Hồ sơ sinh viên** — đăng nhập `student@example.test`:

1. Menu **SINH VIÊN → Thông tin chung**; nếu cửa sổ hẹp, mở menu ☰.
2. Xem ngành `SE`, điện thoại `0901234567` và khoảng thời gian tham gia đã chuẩn bị.
3. Sửa điện thoại hoặc khoảng thời gian → **Lưu thông tin chung**.
4. Nhấn F5, mở lại **Thông tin chung**, kiểm tra dữ liệu đã lưu. Có thể chọn ngành `AI` rồi lưu theo quy tắc form.

Trang **Tài khoản của tôi** là trang mặc định sau đăng nhập; danh sách quyền trên trang này không phải nút mở hồ sơ.

**Nhu cầu doanh nghiệp** — đăng nhập `hr@example.test`:

1. Mở **Nhu cầu thực tập**, chọn bản nháp **DEMO - Java Internship**.
2. Chọn **Sửa bản nháp**, sửa mô tả hoặc chỉ tiêu, lưu và tải lại để kiểm tra.
3. Khi tạo bản nháp khác, dùng các mã bên dưới. Giữ trường/kỳ của bản nháp hiện có; các lần sửa tạo revision theo cơ chế của backend.

| Tham chiếu | Giá trị |
|---|---|
| Trường | `00000000-0000-4000-8000-000000000011` |
| Doanh nghiệp | `00000000-0000-4000-8000-000000000012` |
| Kỳ | `00000000-0000-4000-8000-000000000021` |
| Ngành | `SE`, `AI` |
| Danh mục học vụ | `school-demo-v1` |
| Taxonomy / kỹ năng | `skills-v1` / `JAVA` |

**Nhóm thực tập HR/Mentor** — lần lượt đăng nhập `hr@example.test`, `mentor@example.test`:

1. Mở **Không gian thực tập**.
2. Chọn **Phân công đang làm việc**, chọn đợt **DEMO-W1**.
3. Bấm **Xem nhóm DEMO-G1**, kiểm tra số hồ sơ thực tập và Mentor.
4. Phạm vi được kiểm lại ở backend; đăng nhập hoặc có role ENTERPRISE không tự cấp quyền với mọi nhóm.

**Nhà trường/Admin**: tài khoản trường có khung nghiệp vụ và quyền theo phạm vi; Admin có các màn quản trị. Không giới thiệu khung nhà trường như giao diện xét duyệt hoàn chỉnh. Màn giao việc → nộp bài → nhận xét/chấm điểm chưa hoàn thiện trên UI hiện tại.

## 4. Giữ dữ liệu và cập nhật code

```powershell
.\run-local.cmd stop   # Dừng container, giữ volume và mật khẩu
.\run-local.cmd start  # Dựng lại code, giữ chỉnh sửa đã lưu
.\run-local.cmd logs   # Xem log; Ctrl+C để thoát xem log
```

Nếu checkout sạch và nhánh cho phép cập nhật fast-forward, dùng `git pull --ff-only` trước khi chạy lại. Giữ các sửa đổi local khi Git báo xung đột; không reset để ép cập nhật.

Seed có receipt `LOCAL_SYNTHETIC_V1` trong `local_demo_seed_receipts`. Khi `DONE`, restart không nạp lại hồ sơ, đổi mật khẩu, phân công hay thời hạn. Nếu ứng dụng dừng sau khi nguồn Placement đã commit, restart tiếp tục delivery và gắn nhóm mà không tạo lại dữ liệu nguồn. Thời hạn mẫu tính từ lần seed đầu; sau khi hết hạn cần chuẩn bị lại dữ liệu bằng quy trình phù hợp, restart không gia hạn quyền.

**Database đã có từ trước:** file `.env.local` cũ thiếu `LOCAL_DEMO_ENABLED` được hiểu là `false`; script không tự thêm seed vào database đó. Không xóa `.env.local` khi còn volume. Nếu bật seed trên database có dữ liệu nhưng chưa có receipt, ứng dụng từ chối seed; đặt `LOCAL_DEMO_ENABLED=false` để tiếp tục dùng dữ liệu cũ. Tạo môi trường demo mới trong dự án/volume riêng khi cần; không dùng `down -v` để dọn database đang cần giữ.

Riêng máy đã nhập demo vào database chung ngày 09/11/2026: giữ `.env.local` và dữ liệu hiện tại, mật khẩu sáu tài khoản tổng hợp ở `DEMO_PASSWORD` trong `.env.local-demo`. Không bật seed mới để nhập lại.

## 5. Giới hạn

Mỗi dev có **cùng cấu trúc dữ liệu mẫu ban đầu**, sau đó database và mật khẩu độc lập. `git pull`, Docker build hay restart không đồng bộ chỉnh sửa MySQL giữa các máy. Muốn cùng xem/sửa một dữ liệu phải dùng một môi trường/server chung được cấu hình riêng.

Hai tổ chức VERIFIED, xác minh APPROVED, Placement ACTIVE và các phân công trong seed là tiền điều kiện **synthetic** để demo; không phải phê duyệt thật hoặc chứng minh workflow tuần 2/3 đã hoàn tất. Không có dữ liệu sinh viên thật, file scan CLEAN giả hay chữ ký QA. Completeness và school approval giữ đúng giới hạn hiện hành.

Seed chỉ được kích hoạt khi có đồng thời Spring profile `local` và `app.demo.local.enabled=true`, đồng thời kiểm database và dữ liệu trước khi ghi. Cấu hình staging/production không bật seed này.
