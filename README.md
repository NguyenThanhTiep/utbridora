# UT.Bridora – Team 1 Core Platform

Nền tảng dùng chung cho T2–T5: xác thực, phân quyền theo tổ chức, quản lý tài khoản, audit log và giao diện quản trị tiếng Việt.

Repository: [NguyenThanhTiep/utbridora](https://github.com/NguyenThanhTiep/utbridora) — private; thành viên cần được cấp quyền truy cập.

**Công nghệ:** Java 21 · Spring Boot 4.1.2 · MySQL 8.4 · Flyway · React/TypeScript. Kiến trúc Modular Monolith, Spring Security, session JDBC, cookie HttpOnly và CSRF.

## Chạy nhanh trên Windows

Mở Docker Desktop ở chế độ **Linux containers**, chạy tại thư mục chứa `pom.xml`:

```powershell
.\run-local.cmd start
```

Script tự tạo `.env.local` với mật khẩu ngẫu nhiên và chạy toàn bộ hệ thống; không cần cài riêng Java/Maven/Node. Có thể nhấp đúp `run-local.cmd`.

| Nội dung | Thông tin mặc định |
|---|---|
| Ứng dụng | http://localhost:8088 |
| Hộp thư demo (Mailpit) | http://localhost:8026 |
| MySQL Docker | `127.0.0.1:3307`, database `utbridora`, user `bridora` |
| Admin | `admin@example.test` |
| Mật khẩu | `BOOTSTRAP_ADMIN_PASSWORD` trong `.env.local`; nếu đã đổi trên giao diện, dùng mật khẩu mới |

Dừng và giữ dữ liệu: `.\run-local.cmd stop`. Xem log: `.\run-local.cmd logs`. Nếu cổng bị chiếm, đổi `LOCAL_HTTP_PORT` hoặc `MAILPIT_HTTP_PORT` trong `.env.local`.

**Không commit/chia sẻ `.env.local` hoặc xóa file khi còn database.** Chế độ này chỉ dành cho demo local và dùng volume riêng với cách chạy thủ công.

Trong Workbench, tạo kết nối TCP/IP tới `127.0.0.1:3307`, user `bridora`, mật khẩu lấy từ `DB_PASSWORD` trong `.env.local`, schema `utbridora`. Giao diện ở cổng `8088` và kết nối Workbench này dùng cùng MySQL Docker. Backend chạy trong IDE vẫn dùng database theo `DB_URL` riêng; hai MySQL không tự đồng bộ. Không xóa Docker volume nếu muốn giữ dữ liệu.

## Chạy thủ công

Cần **JDK 21, Node 24 và Docker Desktop**.

Để chạy backend trong IDE với MySQL Docker, khởi động stack local rồi chạy từ thư mục chứa `pom.xml`. Backend mặc định kết nối `127.0.0.1:3307/utbridora`, user `bridora`, tự đọc mật khẩu trong `.env.local`. Biến môi trường `DB_URL`, `DB_USER`, `DB_PASSWORD` được ưu tiên nếu đã đặt. Cách chạy bằng `.env.example` bên dưới dùng MySQL tại cổng 3306.

1. Sao chép `.env.example` thành `.env`, đặt `DB_PASSWORD` và `DB_ROOT_PASSWORD` khác nhau. Nếu cần admin ban đầu, cấu hình hai biến `BOOTSTRAP_ADMIN_*` theo [hướng dẫn triển khai](docs/DEPLOYMENT_GUIDE.md), rồi bỏ chúng sau khi khởi tạo.
2. Chạy `docker compose up -d` và nạp biến từ `.env` vào terminal theo hướng dẫn trên; Maven không tự đọc file này.
3. Chạy backend và frontend ở hai terminal, bắt đầu từ thư mục gốc:

```powershell
# Terminal 1
.\mvnw.cmd spring-boot:run

# Terminal 2
cd web-demo
npm ci
npm run dev
```

Frontend: http://localhost:5173 · Backend: http://localhost:8080 · Mailpit: http://localhost:8025. Lấy liên kết kích hoạt/reset từ Mailpit; token nằm sau dấu `#`. Linux/macOS dùng `./mvnw` thay `.\mvnw.cmd`.

## Kiểm thử

```powershell
.\mvnw.cmd test                         # Unit
.\mvnw.cmd verify                       # Integration, cần Docker
cd web-demo
npm ci
npm run build
npx playwright install chromium
cd ..
.\mvnw.cmd '-Dfrontend.e2e=true' verify  # Thêm E2E Chromium
```

Integration/E2E dùng MySQL Testcontainers và dữ liệu thử `@example.test`, không dùng database thật. Báo cáo: `target/surefire-reports`, `target/failsafe-reports`, `target/frontend-e2e.log`, `web-demo/test-results`.

## Trạng thái và giới hạn

- Build frontend và 14 kịch bản Chromium đã PASS trong lượt ghi nhận tại [báo cáo kiểm thử](docs/TEST_REPORT.md); trạng thái 12 User Story Must nằm trong [IMPLEMENTATION_STATUS](docs/IMPLEMENTATION_STATUS.md).
- Chưa triển khai server staging/production. Local HTTP dùng `COOKIE_SECURE=false`; staging yêu cầu HTTPS và `COOKIE_SECURE=true`. Không dùng dữ liệu sinh viên thật hoặc bật `ALLOW_DEMO_VERIFICATION` ngoài demo local.
- Kích hoạt hiện qua invitation; chưa có workflow cấp tài khoản PENDING riêng. T5 phụ trách xác thực tổ chức thật; ma trận quyền còn chờ PM/Leader phê duyệt.

## Tài liệu

- [Kiến trúc](docs/ARCHITECTURE.md) · [Database](docs/DATABASE.md)
- [API](docs/API_DOCUMENTATION.md) · [OpenAPI](docs/openapi.json) · [Tích hợp T2–T5](docs/INTEGRATION_GUIDE.md)
- [Phân quyền](docs/AUTHORIZATION.md) · [Vòng đời tài khoản](docs/ACCOUNT_LIFECYCLE.md) · [Audit log](docs/AUDIT_LOG.md)
- [Giao diện](docs/UI_UPGRADE.md) · [Triển khai và backup](docs/DEPLOYMENT_GUIDE.md) · [Checklist bàn giao](docs/HANDOFF_CHECKLIST.md)

Tài liệu đánh số 00–10 giữ làm thiết kế ban đầu; khi khác biệt, ưu tiên bộ tài liệu bàn giao và kết quả kiểm thử hiện tại.
