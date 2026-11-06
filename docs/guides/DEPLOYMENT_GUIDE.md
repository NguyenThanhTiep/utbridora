# Hướng dẫn local và staging

Hiện chỉ chuẩn bị cấu hình và kiểm tra tại máy. Không có server, DNS, certificate hoặc quyền deploy staging được cung cấp. Không triển khai production, không sửa dữ liệu production.

## Local trên Windows

MySQL của `compose.local.yaml` được mở riêng trên loopback `127.0.0.1:3307` để dùng Workbench, không trùng cổng 3306 của MySQL cài trên máy. Sau khi `run-local.cmd start` hoàn tất, tạo kết nối TCP/IP với hostname `127.0.0.1`, port `3307`, username `bridora`, mật khẩu là `DB_PASSWORD` trong `.env.local`, default schema `utbridora`. Ứng dụng tại cổng 8088 dùng cùng database này; không cần export/import để xem dữ liệu. `BOOTSTRAP_ADMIN_PASSWORD` dành cho đăng nhập ứng dụng, không phải mật khẩu kết nối database.

Nếu sửa cổng trong Compose, chạy lại `run-local.cmd start` để áp dụng và giữ volume cũ. Dừng container vẫn giữ dữ liệu; xóa volume sẽ xóa database. MySQL cài trên máy và MySQL Docker vẫn là hai hệ thống độc lập. Kết nối từ IDE tới MySQL Docker dùng `DB_URL=jdbc:mysql://127.0.0.1:3307/utbridora?connectionTimeZone=UTC`, `DB_USER=bridora`, `DB_PASSWORD` theo `.env.local`. Backend bên trong Docker tiếp tục dùng địa chỉ nội bộ `mysql:3306`.

`application.yml` hiện mặc định cổng 3307, user `bridora` và import tùy chọn `.env.local` như file Java Properties khi thư mục làm việc là thư mục chứa `pom.xml`. Vì vậy có thể chạy `UtBridoraApplication` trong IDE hoặc `./mvnw.cmd spring-boot:run` mà không sao chép mật khẩu vào YAML. Nếu IDE/terminal còn các biến `DB_URL`, `DB_USER`, `DB_PASSWORD` của MySQL trên máy, hãy bỏ các giá trị cũ hoặc đặt lại theo MySQL Docker; biến môi trường được ưu tiên hơn file import. `.env.local` vẫn nằm ngoài Git. Import này dành cho định dạng `KEY=value` đơn giản do launcher tạo, không thực thi cú pháp shell/dotenv. Chế độ thủ công cổng 3306 dùng `DB_URL` tường minh trong `.env.example`.

Cách đơn giản nhất: mở Docker Desktop rồi chạy `./run-local.cmd start` ở thư mục project. Script `scripts/run-local.ps1` tự sinh `.env.local`, khởi động stack riêng từ `compose.local.yaml`, chờ health check, dùng http://localhost:8088 và Mailpit http://localhost:8026. Không cần cài Java/Maven/Node trên host. Tài khoản admin và mật khẩu nằm trong `.env.local`; không ghi secret vào log. `./run-local.cmd stop` giữ nguyên volume; `./run-local.cmd logs` xem log. Không xóa `.env.local` khi database còn tồn tại. Script tách biến môi trường khỏi cấu hình đã nạp cho staging/manual và không sửa `.env` cũ.

Các bước bên dưới dành cho cách chạy backend/frontend thủ công.

Docker Desktop phải chạy Linux engine. JDK21/Maven3.9+ trên PATH. Tạo .env từ .env.example và nhập secret riêng; không dùng giá trị ví dụ làm mật khẩu. Compose tự đọc .env; Spring Boot qua Maven thì không.

Để nạp file key=value đơn giản vào process PowerShell:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
        [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
    }
}
docker compose up -d
mvn spring-boot:run
```

File này dùng giá trị không bọc quote, không nội suy biến. Không in các biến secret. Local DB bind127.0.0.1:3306, Mailpit SMTP1025/UI8025. Backend port8080 và Vite5173. Nếu đã có MySQL trên3306, thay published port ở bản Compose local và DB_URL; không tái sử dụng database thật.

## Staging chuẩn bị

File compose.staging.yaml gồm MySQL8.4, backend và frontend/Nginx. Backend/database không publish cổng; frontend chỉ bind127.0.0.1:8088, đặt sau reverse proxy HTTPS đã quản lý certificate. Cookie Secure=true cố định trong staging; truy cập HTTP trực tiếp sẽ không dùng được phiên trên browser thông thường.

Tạo .env.staging ngoài Git với DB_PASSWORD, DB_ROOT_PASSWORD, APP_PUBLIC_URL=https://..., MAIL_HOST/PORT, MAIL_FROM (địa chỉ gửi đã xác thực), MAIL_USERNAME/PASSWORD, MAIL_AUTH=true, MAIL_STARTTLS=true. Khi bật STARTTLS, backend yêu cầu TLS thành công. Chỉ bootstrap admin bằng secret tự nhập lần đầu; xóa biến bootstrap và restart sau khi thành công. Không có mật khẩu admin seed cố định.

Các lệnh dưới đây dành cho người vận hành trên môi trường staging đã chuẩn bị:

```powershell
docker compose --env-file .env.staging -f compose.staging.yaml config --quiet
docker compose --env-file .env.staging -f compose.staging.yaml build
docker compose --env-file .env.staging -f compose.staging.yaml up -d
docker compose --env-file .env.staging -f compose.staging.yaml ps
```

Không chạy config không kèm --quiet trong log công khai vì có thể in secret. Tài liệu không khẳng định các lệnh up đã được thực thi lên server.

## Health và logging

Backend GET /actuator/health, /liveness, /readiness trả trạng thái tối thiểu (path đầy đủ /actuator/health/liveness và /actuator/health/readiness). Readiness gồm DB; SMTP không làm identity health DOWN. Frontend /healthz nội bộ. Dockerfile backend có curl healthcheck; frontend dùng wget. Container backend không chạy root, filesystem read-only, /tmp tmpfs, không đặc quyền.

Nginx CSP, X-Content-Type-Options, frame denial và no-referrer; API access log tắt để hạn chế URL/query nhạy cảm. Backend có requestId trong log, không bật DEBUG/TRACE web trên staging. Correlation ID sinh tại backend. Có log rotation10MB×3, chưa có log collector/alert.

Proxy TLS ngoài cùng phải chặn client tự giả header forwarded, thêm HSTS khi HTTPS đã kiểm chứng, và cấu hình limiter cho login/forgot theo IP. Không public backend để bypass limiter. Cần kiểm thử cookie Secure/SameSite/CSRF qua HTTPS thật; cấu hình local không chứng minh phần này.

## Migration, backup và khôi phục

Flyway chạy lúc backend startup, JPA validate sau migration; thất bại thì ứng dụng dừng. Backup trước thay đổi schema; giữ V1–V6 nguyên vẹn. Không sửa flyway_schema_history, không chạy clean/repair để che lỗi. V7 chỉ thêm lock/index; rollback schema bằng restore bản sao được kiểm chứng, không xóa migration.

`scripts/backup-staging.ps1 -OutputDirectory <thu-muc-bao-mat>` dùng mysqldump --single-transaction bên trong container và docker cp, tránh lỗi encoding redirect của PowerShell. File SQL chứa credential hash/PII thử nghiệm, phải lưu ngoài Git, phân quyền và mã hóa ở kho backup. Script dùng cấu hình Compose hiện có; cần nạp env đúng trước gọi.

Khôi phục **chỉ vào database dùng một lần/bản sao**: tạo MySQL8.4 riêng, docker cp file dump vào container đó rồi chạy mysql bằng env trong container. Không restore đè database hiện hành. Sau restore kiểm tra Flyway validate, số lượng FK/bản ghi và login/authorization; chưa có bài diễn tập restore chính thức trong phiên này.

## Việc vận hành chưa nghiệm thu

- SMTP outbox/retry và monitoring mail; hiện delivery đồng bộ, lỗi forgot trả response chung nhưng email có thể không được gửi.
- Tách credential chạy Flyway và runtime với quyền tối thiểu; audit append-only ở DB/storage chưa được áp dụng.
- RPO/RTO, retention, lịch backup, restore drill và cảnh báo.
- Pin image digest/scan dependency và container trước khi phát hành.
- Gateway throttling, MFA privileged account, load test và HTTPS E2E.
