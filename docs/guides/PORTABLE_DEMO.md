# Bộ demo đầy đủ cho Docker local

Bộ tổng hợp hai đợt 14/11 và 20/11/2026 gồm **406 tài khoản demo, 3 trường, 6 doanh nghiệp**, dữ liệu tuyển chọn, thực tập, bài nộp, nhận xét, báo cáo tuần, đánh giá, công bố điểm và kết thúc Case. Đây là dữ liệu DEMO/SYNTHETIC, không phải hồ sơ hoặc quyết định của trường thật.

## Dev mới: pull code rồi chạy

Trên Windows chỉ cần Docker Desktop ở chế độ Linux containers:

```powershell
.\run-local.cmd start
```

Launcher tạo `.env.local` với mật khẩu riêng trên máy, build Docker rồi chạy http://localhost:8088. Flyway tạo schema trước; ứng dụng tự nạp bộ demo một lần vào database trống. Không cần chép SQL, Java, Maven hoặc Node từ máy người tạo dữ liệu. Gói nén khoảng 1,2 MB nằm trong source và được Dockerfile đưa vào JAR.

Trên máy dùng Docker Compose trực tiếp, cấu hình các biến tương đương trong file env riêng: `DB_PASSWORD`, `DB_ROOT_PASSWORD`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD`, `DEMO_PASSWORD` (mật khẩu đủ mạnh, tối thiểu theo PasswordPolicy). Sau đó chạy:

```sh
docker compose --env-file .env.local -f compose.local.yaml up -d --build --wait
```

`LOCAL_PORTABLE_DEMO_ENABLED` mặc định `true` trong Compose local; đặt `false` để chạy local không nạp fixture. Importer chỉ tồn tại ở profile `local`, không bật ở staging/production. Luồng `seed-demo` cũ chỉ dành cho bộ nhỏ cũ; bộ đầy đủ dùng `start`/`reset-demo`.

## Dev đã có database: thay dữ liệu bằng bộ này

```powershell
.\run-local.cmd reset-demo
```

Lệnh này **ghi đè toàn bộ dữ liệu trong schema local `utbridora` của Compose project đã chọn**, không chỉ các tài khoản demo. Script build xong trước, dừng frontend/backend, sao lưu SQL và SHA-256 vào `.local-demo-backups/`, rồi tạo lại schema và khởi động ứng dụng. Backup thất bại thì không reset. Nếu nhập thất bại, script trả lỗi; backup vẫn còn và có thể chạy `start` để thử lại khi schema trống. Không dùng lệnh này trên môi trường triển khai thật.

Các mật khẩu database/cấu hình `.env.local` được giữ nguyên; kho file local không bị xóa. Bộ demo không có CV/file đính kèm, không gán CLEAN giả. File của dữ liệu dev cũ vẫn nằm trong volume để có thể khôi phục cùng backup SQL. Không chạy `docker compose down -v` để nạp bộ này.

`start` bình thường không ghi đè dữ liệu dev cũ, không reset mật khẩu hoặc sửa lại thao tác demo sau mỗi lần restart. Receipt `portable_demo_receipts` ngăn nạp lại. Một database cũ chưa có receipt được giữ nguyên và log hướng dẫn `reset-demo`.

## Tài khoản và mật khẩu

- **406 tài khoản trong fixture**, kể cả `admin@demo.utbridora.test`, dùng `DEMO_PASSWORD` trong `.env.local` của **máy dev đó**. Không dùng mật khẩu trên máy tạo bộ dữ liệu.
- `admin@example.test` do bootstrap tạo thêm (hoặc email đã cấu hình), dùng `BOOTSTRAP_ADMIN_PASSWORD` riêng. Với cấu hình mặc định sẽ có **407 tài khoản, 2 ADMIN**. Tài khoản admin cũ trên máy tạo snapshot không nằm trong gói.
- 403 tài khoản fixture ACTIVE; `status1@demo.utbridora.test` PENDING_ACTIVATION, `status2/status3` DEACTIVATED. Ba tài khoản này dùng xem quản lý trạng thái, không đăng nhập bình thường.
- Danh sách đủ từng tài khoản và chức năng demo: [full-cycle-accounts.csv](../demo/full-cycle-accounts.csv). Không commit file env, mật khẩu thật, bản backup hoặc Excel/HTML PRIVATE từ máy tạo snapshot.

| Tài khoản mẫu | Nội dung demo |
|---|---|
| `admin@demo.utbridora.test` | Quản trị tài khoản, tổ chức và phân quyền theo quyền được cấp |
| `school1@demo.utbridora.test` | Giáo vụ đợt đầu: hồ sơ, matching, đề xuất và chia sẻ theo phân công |
| `cycle.school1@demo.utbridora.test` | Chu kỳ đầy đủ trường 1: theo dõi thực tập, công bố điểm, đóng Case |
| `cycle.e1.coordinator@demo.utbridora.test` | Điều phối DN 1, nhóm và phân công |
| `cycle.e1.hr@demo.utbridora.test` | HR DN 1, đánh giá, quyền hiển thị điểm, nhu cầu tuyển sau thực tập |
| `cycle.e1.mentor@demo.utbridora.test` | Mentor: nhiệm vụ, bài nộp v1/v2, nhận xét và báo cáo tuần |
| `cycle.e1.student01@demo.utbridora.test` | Đã hoàn tất thực tập, có điểm công bố 87/100 |
| `cycle.e1.student06@demo.utbridora.test` | Đã hoàn tất, có điểm công bố 43/100; không tự suy ra đạt/rớt |
| `cycle.e1.student07@demo.utbridora.test` | Kết thúc riêng: Case TERMINATED, Placement CANCELLED |
| `cycle.e1.student08@demo.utbridora.test` | Điểm đã công bố, Case còn chờ đóng |
| `cycle.e1.student09@demo.utbridora.test` | Đánh giá SUBMITTED chờ công bố; sinh viên chưa thấy điểm |
| `cycle.e1.student10@demo.utbridora.test` | Đánh giá DRAFT; sinh viên chưa thấy điểm |

Mẫu trên lặp lại cho DN1–DN6; mỗi trường phụ trách hai DN. Trong 60 sinh viên chu kỳ có36 đã hoàn tất,6 kết thúc riêng,18 còn hoạt động;48 kết quả được công bố. Bộ 300 sinh viên đợt đầu giữ các giai đoạn tuyển chọn/đang thực tập.

## Phạm vi snapshot và tính toàn vẹn

Snapshot chụp ngày20/11/2026; giữ mốc thời gian lịch sử, không đổi đồng hồ hệ thống hoặc bỏ guard thời hạn. Các lời mời, giữ chỗ, consent và phân công vẫn có hạn thật; dữ liệu có thể thay đổi theo thời gian và thao tác. Khi một phân công hết hạn, cấp lại bằng luồng nghiệp vụ hoặc chuẩn bị snapshot mới, không coi bộ tĩnh này là fixture có thời hạn vô hạn.

Chỉ đóng gói dữ liệu synthetic của hai đợt và danh mục kỹ thuật cần thiết. Loại session, token, mật khẩu/hash thật, mail queue, operation cache, audit riêng, file và tài khoản ngoài demo. Giữ tham chiếu Mentor đã có nhưng bỏ liên kết token. Không chép Flyway history/schema của máy tạo snapshot; schema mới chạy migration nguyên bản của source, gồm hội tụ các lineage.

Manifest và SHA-256 ở `src/main/resources/demo/portable/manifest.json`. Import theo thứ tự khóa ngoại, tạm để trống đúng các back-reference nullable rồi khôi phục trong cùng transaction; không tắt FK/CHECK. Lỗi nạp làm rollback dữ liệu fixture; receipt DONE chỉ ghi khi đã đủ số dòng. Các quyền đọc/ghi vẫn qua policy hiện hành; không có quyền ADMIN bỏ qua owner scope.

Kiểm chứng DEV của gói không thay QA/Lead/nhà trường nghiệm thu và không chứng nhận tải/RPO/RTO.
