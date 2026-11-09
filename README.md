# UT.Bridora – Team 1 Core Platform

Nền tảng dùng chung cho T2–T5: xác thực, phân quyền theo tổ chức, quản lý tài khoản, audit log và giao diện quản trị tiếng Việt.

Repository: [NguyenThanhTiep/utbridora](https://github.com/NguyenThanhTiep/utbridora) — private; thành viên cần được cấp quyền truy cập.

**Công nghệ:** Java 21 · Spring Boot 4.1.2 · MySQL 8.4 · Flyway · React/TypeScript. Kiến trúc Modular Monolith, Spring Security, session JDBC, cookie HttpOnly và CSRF.

## Chạy nhanh trên Windows

Mở Docker Desktop ở chế độ **Linux containers**, chạy tại thư mục chứa `pom.xml`:

```powershell
.\run-local.cmd start
```

Script tự tạo `.env.local` với mật khẩu ngẫu nhiên và chạy toàn bộ hệ thống; không cần cài riêng Java/Maven/Node. Với database mới, script bật seed local một lần để có tài khoản Student/HR/Mentor và dữ liệu mẫu. Database/cấu hình cũ được giữ nguyên. Có thể nhấp đúp `run-local.cmd`.

Xem **[hướng dẫn demo cho dev: tài khoản, hồ sơ, nhu cầu và HR/Mentor](docs/guides/LOCAL_DEMO.md)**. Mật khẩu sáu tài khoản nghiệp vụ mới ở `DEMO_PASSWORD` trong `.env.local`; mật khẩu Admin dùng `BOOTSTRAP_ADMIN_PASSWORD` riêng.

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

1. Sao chép `.env.example` thành `.env`, đặt `DB_PASSWORD` và `DB_ROOT_PASSWORD` khác nhau. Nếu cần admin ban đầu, cấu hình hai biến `BOOTSTRAP_ADMIN_*` theo [hướng dẫn triển khai](docs/guides/DEPLOYMENT_GUIDE.md), rồi bỏ chúng sau khi khởi tạo.
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

- Build frontend và 14 kịch bản Chromium đã PASS trong lượt ghi nhận tại [báo cáo kiểm thử](docs/testing/TEST_REPORT.md); trạng thái 12 User Story Must nằm trong [IMPLEMENTATION_STATUS](docs/status/IMPLEMENTATION_STATUS.md).
- Chưa triển khai server staging/production. Local HTTP dùng `COOKIE_SECURE=false`; staging yêu cầu HTTPS và `COOKIE_SECURE=true`. Không dùng dữ liệu sinh viên thật hoặc bật `ALLOW_DEMO_VERIFICATION` ngoài demo local.
- Kích hoạt hiện qua invitation; chưa có workflow cấp tài khoản PENDING riêng. T5 phụ trách xác thực tổ chức thật; ma trận quyền cần đối chiếu với registry của Contract Pack đã được người dùng chốt.

## Tài liệu

- [Mục lục toàn bộ tài liệu](docs/README.md): phân biệt yêu cầu, contract đề xuất, source đã triển khai và bằng chứng kiểm thử.
- [SRS tổng thể v1.2.4](docs/srs/UTBridora_SRS_Tong_The_v1.2.4.docx) · [Contract Pack B01–B06 v1.0 Candidate](docs/contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md): người dùng chốt bản v1.0 ngày 05/11/2026, xem [quyết định và hash](docs/contracts/README.md#quyết-định-chốt-contract--05112026). Rà soát kỹ thuật còn tiếp tục; chưa phải xác nhận chức năng mới đã triển khai.
- [Review kỹ thuật T1-01](docs/contracts/T1_01_TECHNICAL_REVIEW.md) · [v1.1 Candidate](docs/contracts/UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md) · [checker DEV](scripts/contracts/README.md): 868 kiểm tra schema/registry/oracle PASS; tách khỏi runtime và xác nhận Tester.
- [Kiến trúc](docs/core/ARCHITECTURE.md) · [Database](docs/core/DATABASE.md)
- [API](docs/core/API_DOCUMENTATION.md) · [OpenAPI](docs/openapi.json) · [Phát triển và tích hợp BE/FE T2–T5](docs/guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md)
- [Phân quyền](docs/core/AUTHORIZATION.md) · [Vòng đời tài khoản](docs/core/ACCOUNT_LIFECYCLE.md) · [Audit log](docs/core/AUDIT_LOG.md)
- [Lịch sử nâng cấp giao diện](docs/archive/UI_UPGRADE.md) · [Triển khai và backup](docs/guides/DEPLOYMENT_GUIDE.md) · [Checklist bàn giao](docs/status/HANDOFF_CHECKLIST.md)

Tài liệu thiết kế ban đầu được giữ trong [archive](docs/archive/t1-baseline/README.md). Trạng thái source xem `docs/status`, kết quả kiểm thử xem `docs/testing`; SRS và contract đề xuất không tự thay thế bằng chứng thực thi.
