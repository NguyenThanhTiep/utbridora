# Trạng thái triển khai Core Platform

## T1-07 tuần 4 — 14/11/2026

Đã bổ sung source scanner ClamD thật, retry/lease fencing/audit atomic, validators
ZIP/XLSX/CSV và regression quyền tải sau CLEAN/revoke; V38 áp dụng mọi lineage.
Runtime worker opt-in, không deploy môi trường 8088. CI/test mới phải xem đúng SHA
nhánh T1-07; chưa thay QA01/Lead nghiệm thu. Xem [hướng dẫn T1-07](../guides/T1_07_FILE_SCANNING.md).
Các ghi nhận scanner chưa có bên dưới là checkpoint lịch sử trước T1-07.

## Tích hợp tuần 3 — 13/11/2026

Bản tích hợp T1-06 và DEV T2/T3/T4/T5 giữ lịch sử nhánh nguồn, bổ sung các điểm nối thật Opportunity/Preference, shared file và Placement/Case, sửa privacy/scope/replay và hội tụ Flyway theo từng lịch sử DB. Xem [ma trận task và giới hạn](../testing/WEEK3_INTEGRATION_ACCEPTANCE.md), [evidence đúng source](../testing/evidence/WEEK3_INTEGRATION_VERIFICATION.json), [runtime/nâng cấp DB](../guides/WEEK3_INTEGRATION_RUNTIME.md) và [bàn giao tuần 4](../README.md#bàn-giao-tuần-3--tuần-4). Các ghi nhận bên dưới là checkpoint lịch sử. Plan, QA/Lead và nghiệm thu SRS không được tự đánh dấu hoàn thành; không deploy stack 8088 và scanner T1-07 vẫn chưa có.

## T3-08 — DEV06 — 12/11/2026

Đã triển khai Comment, Weekly Report draft/submit/revision/history, Mentor review và đọc
nhận xét theo revision; V31 bổ sung schema Report và quyền Comment. API dùng transaction,
audit/ledger, Case/Placement guard và FileService thật. Bổ sung CP-WS3 v1.1 giữ nguyên
ReportDto v1.0. Xem [bằng chứng và giới hạn T3-08](../reviews/T3_08_COMPLETION_EVIDENCE.md).
Dependency task theo Plan là T3-05; không chờ T3-07. Không tự xác nhận QA/Lead, UI T3-09
hoặc scanner T1-07. Thay đổi hiện local, chưa commit/push/deploy.

## T1-06 tuần 3 — 10/11/2026

Nhánh bàn giao `feature/t1-06-private-files`, base tuần 2 `f1ef2176041c5b0892d5bb107f01d601616755c2`: đã bổ sung private File Service tối thiểu với upload/metadata/content, owner policies cho StudentProfile/TaskAssignment/EnterpriseVerification, storage ngoài MySQL, immutable binding, ledger/audit và migration V28. Code, tests và tài liệu được đóng gói cùng bản DEV T1-06; chưa deploy. File mới PENDING; scanner T1-07, shared authorizer, các resource provider còn thiếu và workflow attachment của team khác chưa hoàn thành. Xem [hướng dẫn tích hợp](../guides/T1_06_PRIVATE_FILES.md) và [kiểm chứng DEV/giới hạn](../testing/T1_06_ACCEPTANCE.md). Evidence giữ snapshot trước commit; trạng thái phát hành hiện hành xem theo Git. Các ghi nhận “file chỉ có metadata/chưa có runtime” phía dưới là lịch sử, không phủ định phần mới này.

## Tuần 2 — 09/11/2026

Nhánh triển khai `feature/week2-implementation`, base `6687a94`: đã bổ sung luồng delegation/audit, skills/completeness/snapshot, strict matching, Cooperation/proposal/invitation/consent và Mentor/Task/calendar. Code, tests và bốn file bàn giao được đóng gói trong bản phát hành tuần 2 trên main. Xem [đối chiếu 18 task và kiểm chứng DEV](../testing/WEEK2_ACCEPTANCE.md), [hướng dẫn runtime](../guides/WEEK2_RUNTIME.md), [bàn giao T2–T5](../README.md#bàn-giao-tuần-2--tuần-3). Docker dùng chung chưa triển khai lại; không sửa trạng thái QA/Lead trong Plan. Evidence giữ nguyên thời điểm kiểm chứng trước commit, CI sau push xem theo đúng commit trong GitHub Actions. Các mục bên dưới là lịch sử tại ngày được ghi, không phủ định implementation mới và không chứng minh kiểm thử của source tuần 2.

## Rà soát kỹ thuật T1-01 — 05/11/2026

Đã xuất [Contract v1.1 Candidate](../contracts/UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md), Data Dictionary bổ sung, 374 schema, Input/Value cho 62 port/consumer và CSV quyền/audit. **868/868 kiểm tra DEV PASS**: 69 ví dụ, 795 fixture và 4 kiểm tra tĩnh; không phải integration test của module chưa triển khai. Diff v1.1 chưa tự được phê duyệt theo quyết định v1.0.

Core runtime đã chạy thật trong cùng phiên trước phần rà contract: 28 unit, 49 Failsafe, 14 Chromium PASS (3 UI mock), frontend build PASS; bản kiểm tra contract không chạy lại suite đó. Xem [review/phạm vi còn thiếu](../contracts/T1_01_TECHNICAL_REVIEW.md), [evidence contract](../testing/evidence/T1_01_CONTRACT_REVIEW.json) và [evidence core](../testing/evidence/T1_01_CORE_REGRESSION.json).

Workbook chỉ cập nhật ô DEV/bằng chứng/ghi chú; Tester, Leader và nghiệm thu SRS giữ nguyên. T1-01 tổng vẫn Đang làm, CONTRACT-01 hoàn thành phần DEV chuẩn bị typed contract. Nghiệp vụ mới/adapter/migration, R33 và staging vẫn có task riêng. Job CI contracts đã thêm nhưng chưa có kết quả GitHub cho thay đổi mới. Không sửa mã ứng dụng BE/FE, migration, SRS hoặc nội dung Contract v1.0.

## Quyết định chốt Contract — 05/11/2026

Người dùng đã chốt Contract Pack B01–B06 v1.0 làm căn cứ kỹ thuật và không chờ vòng ký riêng T2–T5. Xem [quyết định và hash phiên bản](../contracts/README.md#quyết-định-chốt-contract--05112026). Đây là xác nhận của người dùng, không ghi thay chữ ký/review của các team hoặc kết quả QA.

Phần rà kỹ thuật được cập nhật ở mục đầu; không đánh dấu toàn bộ task hoàn thành chỉ từ quyết định chốt. Bản SRS và nội dung Candidate v1.0 được giữ nguyên.

## Snapshot kiểm tra tĩnh — 04/11/2026

Đây là cập nhật tài liệu và kiểm tra tĩnh, **không phải lượt build/test/migration mới**. Các số PASS và lỗi baseline ở những mục lịch sử bên dưới giữ nguyên thời điểm 29–30/10/2026; không dùng để khẳng định runtime Docker/MySQL, GitHub Actions hoặc source của team khác vừa được kiểm chứng.

| Nội dung | Trạng thái được đối chiếu |
|---|---|
| Yêu cầu hiện có | [SRS tổng thể v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx); đọc phân biệt baseline §25.8 với đề xuất hợp nhất §25.9 |
| Giao tiếp liên team | [Contract Pack B01–B06 Candidate](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) còn cần signoff; không coi API/permission đề xuất là đã triển khai |
| Source nền tảng | Core T1 hiện có, migration V1–V7; file service mới có metadata. Chưa thấy module nghiệp vụ T2–T5 trong checkout hiện tại |
| Phần chưa triển khai | Hồ sơ/roster sinh viên, import job/claim/mail outbox mới, Placement/workspace, Matching và adapter share/consent thật |
| Quy tắc đã đặc tả | T5 sở hữu Placement, T3 sở hữu InternshipCase; baseline privacy k=5. Đây là yêu cầu cần chuyển thành code/test, không phải kết quả nghiệm thu |
| Môi trường và remote | Chưa kiểm chứng lại Docker/MySQL/SMTP, visibility/quyền GitHub, CI remote hoặc staging trong lượt sắp xếp tài liệu |

Hướng dẫn đang dùng: [phát triển và tích hợp BE/FE](../guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md), [Git/GitHub](../guides/HUONG_DAN_LAM_VIEC_VOI_GITHUB.md), [triển khai](../guides/DEPLOYMENT_GUIDE.md). Báo cáo UI cũ ở [archive](../archive/UI_UPGRADE.md); kết quả kiểm thử có ngày cụ thể ở [TEST_REPORT](../testing/TEST_REPORT.md).

## Snapshot lịch sử 29–30/10/2026

Ngày khảo sát: 29/10/2026. Đã đọc toàn bộ SRS v1.0 (Draft for Review), báo cáo bàn giao và tài liệu trong `docs`. Đây là dự án kế thừa, không tạo project thay thế. Gói workspace ban đầu chưa có thư mục `.git`. Cập nhật 30/10/2026: đã khởi tạo Git, chia source theo nhóm chức năng và cấu hình origin tới repo private [NguyenThanhTiep/utbridora](https://github.com/NguyenThanhTiep/utbridora). Lượt kiểm thử trước push kết thúc 11:07:57 (UTC+7): 28 unit, 49 integration và 14 Chromium PASS; bằng chứng tại `../testing/evidence/PREPUSH_TEST_EVIDENCE.json`. Kết quả này là thực thi local, không phải kết quả GitHub Actions.

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

Kết quả sau thay đổi và ma trận nghiệm thu cuối phiên được cập nhật tại `../testing/TEST_REPORT.md`; không đánh dấu toàn bộ Must hoàn thành khi còn dependency nghiệp vụ hoặc test chưa chạy.

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

## Bổ sung UI và vận hành đã ghi nhận trong lịch sử

Bản nâng cấp UI toàn diện: App/components/management được chuẩn hóa cùng design tokens; sidebar/topbar mới, màn xác thực, dashboard tài khoản, bảng/forms, mobile drawer, keyboard focus và password visibility. Không đổi backend/API/migrations. Lượt UI kết thúc18:56:43 ngày29/10/2026: build PASS,28 unit+1 Failsafe điều phối14/14 Chromium PASS, gồm7 core cũ và7 UX mới(3UI mock). Xem [báo cáo UI được lưu trữ](../archive/UI_UPGRADE.md) và [UI_TEST_EVIDENCE.json](../testing/evidence/UI_TEST_EVIDENCE.json); không nhầm với việc chạy lại toàn bộ49 backend integration test.

Bổ sung cách chạy đơn giản: `run-local.cmd`, `scripts/run-local.ps1`, `compose.local.yaml`; chỉ cần Docker Desktop. Script tự sinh secret trong `.env.local`, không sửa `.env` cũ, dùng volume riêng và chờ health check. Đã chạy stack thật và smoke test frontend/login/session/CSRF/logout/admin API/Mailpit PASS. Cấu hình staging HTTPS không thay đổi.

## Ưu tiên tiếp theo — cập nhật ngày 05/11/2026

Quyết định chốt bản v1.0 đã được người dùng xác nhận tại mục đầu. Tiếp tục hoàn tất rà soát kỹ thuật DTO/Data Dictionary, schema/ví dụ và mapping permission/audit với core trước khi tích hợp phần liên quan. Nếu cần sửa contract, phát hành phiên bản mới với diff và quyết định tương ứng; giữ phạm vi công việc được giao, tái sử dụng core hiện có.

1. P0 trước truy cập công khai: mail delivery tin cậy, timing/abuse recovery, gateway throttling, HTTPS xuyên proxy và review bảo mật độc lập.
2. P0 tích hợp: T2/T5 adapter share/consent/field và T3/T4/T5 business guards; giữ deny khi thiếu dữ liệu.
3. P1 vận hành: tách runtime/migration credentials, audit append-only/retention, backup/restore drill, CI remote và load test.

Không tuyên bố toàn bộ dự án hoàn thành. Phần T1 đã kiểm thử local sẵn sàng review; phần nghiệm thu còn thiếu được tách rõ trong TEST_REPORT/HANDOFF_CHECKLIST.
