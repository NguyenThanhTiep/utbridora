# Trạng thái triển khai Core Platform

Ngày khảo sát: 29/10/2026. Đã đọc toàn bộ SRS v1.0 (Draft for Review), báo cáo bàn giao và tài liệu trong `docs`. Đây là dự án kế thừa, không tạo project thay thế. Gói workspace ban đầu chưa có thư mục `.git`. Cập nhật 30/10/2026: đã khởi tạo Git, chia source theo nhóm chức năng và cấu hình origin tới repo private [NguyenThanhTiep/utbridora](https://github.com/NguyenThanhTiep/utbridora). Lượt kiểm thử trước push kết thúc 11:07:57 (UTC+7): 28 unit, 49 integration và 14 Chromium PASS; bằng chứng tại `PREPUSH_TEST_EVIDENCE.json`. Kết quả này là thực thi local, không phải kết quả GitHub Actions.

## Baseline trước thay đổi

- Java 21.0.11, Node 24.16.0, npm 11.13.0. Maven 3.9.15 có trong `.m2/wrapper`, chưa có trên PATH.
- `mvn -o test`: FAIL vì parent chưa có trong cache.
- `mvn test -B` sau khi cấp truy cập mạng: FAIL compile `AuthController.java:59`, thiếu import `org.springframework.security.core.AuthenticationException`. Chưa có test nào được thực thi.
- Giữ Spring Boot 4.1.2: Maven đã resolve được phiên bản này. Không nâng/hạ framework.
- `npm run build`: FAIL vì chưa cài dependencies (`tsc` không tồn tại).
- Docker baseline: FAIL vì chưa có pipe Docker daemon. Đang thử khởi động Docker Desktop; chưa có bằng chứng MySQL chạy.
- V1–V6 được chụp SHA-256 trước thay đổi; chỉ thêm migration mới.

## Đối chiếu source ban đầu và thứ tự triển khai

| US | Code ban đầu | Thiếu/lỗi phát hiện | Test ban đầu | Ưu tiên và phụ thuộc |
|---|---|---|---|---|
| US-T1-01 | AuthController, AccountService, SecurityConfig | Lỗi compile; strategy login thủ công; race giữa xác thực và revoke | Chưa có HTTP test | P0, database/session |
| US-T1-02 | Logout handler | Chưa chứng minh session JDBC bị hủy, CSRF | Không | P0 |
| US-T1-03 | me, permissions | Thiếu test serialize an toàn, membership | Không | P0 |
| US-T1-04 | AccountTokenService | Email lỗi có thể lộ sự tồn tại account; thiếu concurrency reset; chưa có UI | TokenCodecTest | P0; SMTP, T2 quyết định provision PENDING |
| US-T1-05 | MembershipService | Race admin cuối; timestamp khi khôi phục | Không | P0 |
| US-T1-06 | AuthorizationPolicy | Backend guard có sẵn; thiếu coverage service/HTTP | 4 unit test | P0; owner/business guard T2–T5 |
| US-T1-07 | OrganizationAccess, demo scope | Chưa test IDOR URL/body, đa org | 1 unit test liên quan | P0; tài nguyên thật T2–T5 |
| US-T1-08 | ProfileVisibilityPort, deny-all | Chưa có adapter chia sẻ thật (đúng ranh giới) | Mock policy | P0 contract; T2/T5 consent, field/time/purpose |
| US-T1-09 | Flyway V1–V6, entities | Chưa chứng minh MySQL/JPA validate; mapping enum/Instant cần kiểm tra | Không | P0 |
| US-T1-10 | LifecycleService, history | Race admin cuối; thiếu API danh sách/history | 2 policy test | P0 |
| US-T1-11 | AuditService, paged API | Password change audit ngoài transaction; thiếu filter thời gian và registry | Không | P0; events nghiệp vụ do T2–T5 |
| US-T1-12 | React login/me, Compose MySQL | Chưa có UI admin/recovery; thiếu staging, CI, OpenAPI | Không | P1 sau nền tảng |

## Kế hoạch theo phụ thuộc

1. Sửa lỗi build, thiết lập test database dùng một lần, giữ nguyên V1–V6; migration mới cho mutex quản trị.
2. Chuẩn hóa session strategy, transaction credential/audit, khóa ghi và test authentication/token/revocation.
3. Serial hóa quản trị admin cuối và recheck actor; kiểm thử membership/org/profile deny-by-default.
4. Bổ sung read API quản trị có phân quyền, audit filter/registry và kiểm thử rollback.
5. Hoàn thiện React bằng API hiện có và API bổ sung; build, kiểm thử các luồng quan trọng.
6. Chuẩn bị staging/CI/backup và cập nhật tài liệu theo bằng chứng kiểm thử thực tế.

Kết quả sau thay đổi và ma trận nghiệm thu cuối phiên được cập nhật tại `TEST_REPORT.md`; không đánh dấu toàn bộ Must hoàn thành khi còn dependency nghiệp vụ hoặc test chưa chạy.

## Kết quả sau triển khai

Lượt nền tảng trước nâng cấp UI ngày29/10/2026 kết thúc 17:47:08 (UTC+7): **BUILD SUCCESS**, 28 unit +49 Failsafe, không failure/error/skip; test điều phối7 kịch bản Chromium PASS. Lượt toàn bộ suite trước push ngày30/10/2026 kết thúc 11:07:57: **28 unit +49 Failsafe +14 Chromium PASS**, frontend production build PASS. Hai image staging đã được build trong lượt trước; không build lại image staging trong lượt trước push. V1–V6 giữ nguyên SHA-256. Chưa deploy server.

| Giai đoạn | File/nhóm thay đổi | Kết quả và kiểm thử | Vấn đề còn lại / team phụ thuộc |
|---|---|---|---|
| Khảo sát/baseline | IMPLEMENTATION_STATUS, README, Maven Wrapper, pom.xml | Baseline compile lỗi được tái hiện; giữ Boot4.1.2; wrapper3.9.15 | Gói ban đầu chưa có Git; đã khởi tạo và cấu hình remote ngày 30/10/2026 |
| Kiểm tra build tool Windows | mvnw.cmd | Sửa null Target trên PowerShell; -version và28 unit PASS với exit0 | Sandbox cần quyền đọc dependency cache đã tải |
| Database | V7, enum JDBC VARCHAR ở entity, repository | MySQL8.4 schema sạch/upgradeV2, FK/unique/JPA/session PASS | Clone DB chung/backup-restore chưa chạy |
| Authentication | SecurityConfig, AuthController, AccountService, AccountTokenService, TokenRepository, EmailService, config | Session strategy chuẩn; fixation/CSRF/logout/me/4roles/lockout/reset concurrent PASS; hash không serialize | SMTP outbox/retry, timing enumeration, IP throttling; PENDING activation riêng chờ quyết định |
| Authorization | AuthorizationPolicy, AdministrationGuard, MembershipService, TestAuthorizationEndpoints | Backend deny; org URL/body, multiple membership, revoked principal/admin business denied PASS | T2–T5 record/Placement/share thật |
| Lifecycle | AccountLifecycleService, history repo, AdminReadController | 16 cặp unit/DB, version/history, concurrency admin-last PASS | Chưa mở flow DEACTIVATED recovery hoặc PENDING import |
| Audit | AuditAction, AuditService, RequestIdFilter, AdminController | Registry/correlation/filter; rollback khi DB audit fail; paged read/denied PASS | Event nghiệp vụ T2–T5; append-only/retention/alerts |
| Frontend | api.ts, App.tsx, components.tsx, management.tsx, style.css, package-lock, Playwright config/tests | Login/dashboard/account/admin/membership/status/audit/forgot/reset/invite; build và7 E2E PASS; desktop/mobile đã xem ảnh | UI nghiệp vụ T2–T5 chưa tích hợp |
| Staging | Dockerfiles, nginx.conf, compose.staging.yaml, backup script, CI workflow | Image build, Compose config, nginx syntax PASS; script backup chỉ syntax PASS | Server HTTPS/CI thực, restore, monitoring/limiter NOT RUN |
| Bàn giao | 11 tài liệu yêu cầu, README, openapi.json, TEST_EVIDENCE | Ma trận code/test/nghiệm thu đủ12 US ở TEST_REPORT | PM/Leader ký nhận và chốt quyết định OPEN |

## Ưu tiên tiếp theo

Bản nâng cấp UI toàn diện: App/components/management được chuẩn hóa cùng design tokens; sidebar/topbar mới, màn xác thực, dashboard tài khoản, bảng/forms, mobile drawer, keyboard focus và password visibility. Không đổi backend/API/migrations. Lượt UI kết thúc18:56:43 ngày29/10/2026: build PASS,28 unit+1 Failsafe điều phối14/14 Chromium PASS, gồm7 core cũ và7 UX mới(3UI mock). Xem UI_UPGRADE.md và UI_TEST_EVIDENCE.json; không nhầm với việc chạy lại toàn bộ49 backend integration test.

Bổ sung cách chạy đơn giản: `run-local.cmd`, `scripts/run-local.ps1`, `compose.local.yaml`; chỉ cần Docker Desktop. Script tự sinh secret trong `.env.local`, không sửa `.env` cũ, dùng volume riêng và chờ health check. Đã chạy stack thật và smoke test frontend/login/session/CSRF/logout/admin API/Mailpit PASS. Cấu hình staging HTTPS không thay đổi.

1. P0 trước truy cập công khai: mail delivery tin cậy, timing/abuse recovery, gateway throttling, HTTPS xuyên proxy và review bảo mật độc lập.
2. P0 tích hợp: T2/T5 adapter share/consent/field và T3/T4/T5 business guards; giữ deny khi thiếu dữ liệu.
3. P1 vận hành: tách runtime/migration credentials, audit append-only/retention, backup/restore drill, CI remote và load test.

Không tuyên bố toàn bộ dự án hoàn thành. Phần T1 đã kiểm thử local sẵn sàng review; phần nghiệm thu còn thiếu được tách rõ trong TEST_REPORT/HANDOFF_CHECKLIST.
