# Hướng dẫn phát triển backend và frontend UT.Bridora cho các team và AI

Ngày đối chiếu: **04/11/2026**. Áp dụng cho repository `NguyenThanhTiep/utbridora`, thư mục chứa `pom.xml` và `web-demo/`.

Tài liệu giúp thành viên T1–T5 hoặc AI tiếp nhận một chức năng, đặt code đúng chỗ, dùng lại nền tảng và kiểm thử trước khi tích hợp. Đây là hướng dẫn làm việc trên dự án đang có, không phải hướng dẫn tạo năm project riêng.

**Trạng thái cần hiểu trước:** core T1 đã có source; module nghiệp vụ T2–T5 chưa có trong checkout được kiểm tra. Các cây `modules/`, lớp nghiệp vụ và ví dụ T2 bên dưới là cấu trúc đề xuất để triển khai. Cập nhật 05/11/2026: người dùng đã chốt Contract v1.0, không chờ vòng ký riêng T2–T5; [v1.1 Candidate](../contracts/UTBridora_Contract_Pack_B01_B06_v1.1_Candidate.md) là diff kỹ thuật với schema/checker DEV, chưa tự thay bản đã chốt. Xem [review T1-01](../contracts/T1_01_TECHNICAL_REVIEW.md). Ví dụ trong hướng dẫn không chứng minh API đã triển khai.

## Mục lục

1. [Tài liệu và nguồn quyết định](#1-tài-liệu-và-nguồn-quyết-định)
2. [Kiến trúc và trách nhiệm từng team](#2-kiến-trúc-và-trách-nhiệm-từng-team)
3. [Bản đồ source hiện tại](#3-bản-đồ-source-hiện-tại)
4. [Chuẩn bị môi trường và chọn cách chạy](#4-chuẩn-bị-môi-trường-và-chọn-cách-chạy)
5. [Database và migration dùng chung](#5-database-và-migration-dùng-chung)
6. [Cách đặt và tổ chức code backend](#6-cách-đặt-và-tổ-chức-code-backend)
7. [Dùng lại xác thực và phân quyền](#7-dùng-lại-xác-thực-và-phân-quyền)
8. [Transaction, API và audit](#8-transaction-api-và-audit)
9. [Cách tổ chức và ghép frontend](#9-cách-tổ-chức-và-ghép-frontend)
10. [Gọi API, session và CSRF từ frontend](#10-gọi-api-session-và-csrf-từ-frontend)
11. [Ví dụ triển khai một chức năng T2](#11-ví-dụ-triển-khai-một-chức-năng-t2)
12. [Hợp đồng và phụ thuộc giữa các team](#12-hợp-đồng-và-phụ-thuộc-giữa-các-team)
13. [File, dữ liệu demo và dữ liệu giữa các máy](#13-file-dữ-liệu-demo-và-dữ-liệu-giữa-các-máy)
14. [Kiểm thử và điều kiện hoàn thành](#14-kiểm-thử-và-điều-kiện-hoàn-thành)
15. [Git, pull request và bàn giao](#15-git-pull-request-và-bàn-giao)
16. [Lỗi thường gặp](#16-lỗi-thường-gặp)
17. [Mẫu giao việc cho AI](#17-mẫu-giao-việc-cho-ai)
18. [Checklist bắt đầu và tích hợp](#18-checklist-bắt-đầu-và-tích-hợp)
19. [Căn cứ đối chiếu và giới hạn xác minh](#19-căn-cứ-đối-chiếu-và-giới-hạn-xác-minh)

## 1. Tài liệu và nguồn quyết định

Đọc theo thứ tự, với đường dẫn tính từ gốc repository:

| Tài liệu | Dùng để quyết định điều gì? |
|---|---|
| [README](../../README.md), [mục lục docs](../README.md) | Cách chạy và vị trí tài liệu |
| [SRS tổng thể v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx) | Yêu cầu nghiệp vụ; chú ý §20, §23, §25.8 và §25.9 |
| [Trạng thái SRS](../srs/README.md), [trạng thái contract](../contracts/README.md) | Phần nào đã baseline, phần nào còn đề xuất/signoff |
| [Contract Pack B01–B06 Candidate](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) | DTO, port, permission, audit, import và tương thích dự kiến |
| [API core](../core/API_DOCUMENTATION.md), [OpenAPI](../openapi.json) | Endpoint core đã công bố; cần đối chiếu controller/test |
| [Phân quyền](../core/AUTHORIZATION.md), [Database](../core/DATABASE.md) | Guard, scope và nền tảng dữ liệu |
| [Hướng dẫn Git/GitHub](HUONG_DAN_LAM_VIEC_VOI_GITHUB.md) | Nhánh, commit theo chức năng, PR, CI và review liên team |
| [Trạng thái triển khai](../status/IMPLEMENTATION_STATUS.md), [kiểm thử](../testing/TEST_REPORT.md) | Phạm vi đã làm và lượt test có ngày cụ thể |

Phân biệt bốn loại bằng chứng: **yêu cầu trong SRS**, **contract được duyệt**, **source đã triển khai**, **test đã thực thi**. Một yêu cầu có trong SRS hoặc một màn hình mock không chứng minh backend đã hoàn thành.

Khi gặp mâu thuẫn giữa tài liệu cũ và phần hiệu chỉnh mới, ghi đúng vị trí và trạng thái phê duyệt để owner xử lý. Không tự chọn một endpoint mới, nới quyền hoặc thay state machine rồi ghi là đã thống nhất. Phần độc lập, có yêu cầu rõ ràng vẫn có thể triển khai trong phạm vi được giao.

Không sửa nội dung SRS/Contract Candidate trong một PR tính năng thông thường. Hai tài liệu có tham chiếu checksum; thay đổi hợp đồng phải được quản lý phiên bản và owner/consumer review.

## 2. Kiến trúc và trách nhiệm từng team

### 2.1. Một sản phẩm dùng chung nền tảng

```text
Trình duyệt
  └─ React/TypeScript trong web-demo
      └─ /api/v1/... qua Vite hoặc Nginx
          └─ Một Spring Boot application
              ├─ T1: identity, security, account, audit, file nền tảng
              ├─ T2: student
              ├─ T3: internship workspace
              ├─ T4: matching
              └─ T5: cooperation / enterprise / school
                  └─ Một schema MySQL trong mỗi môi trường
                     + một chuỗi Flyway migration
```

Đây là **Modular Monolith**. Các team chia module và ownership; không chia thành năm máy chủ đăng nhập, năm schema nghiệp vụ hoặc năm frontend chính độc lập.

### 2.2. Phạm vi triển khai

| Team | Sở hữu nghiệp vụ | Công việc frontend | Phụ thuộc chính |
|---|---|---|---|
| T1 | Tài khoản, tổ chức core, membership, permission, session, account lifecycle, audit, file nền tảng; import job/identity/token/mail theo hợp đồng mới | Login, tài khoản, quản trị core, audit; component và transport chung | Adapter scope/consent/resource của owner nghiệp vụ |
| T2 | Hồ sơ, kỹ năng khai báo, dự án, chứng chỉ, CV tham chiếu, roster/eligibility, consent, preference | Cổng sinh viên; hiển thị và gửi thao tác đến API T3/T5 đúng owner | T1 identity/file, T4 taxonomy/completeness, T5 term/opportunity/invitation, T3 workspace |
| T3 | InternshipCase, cohort, nhóm, HR/Mentor assignment, task, submission, report, evaluation | Workspace HR/Mentor/School; API để UI sinh viên T2 sử dụng | T5 Placement hợp lệ, T1 identity/guard/audit/file |
| T4 | Taxonomy/alias, policy đủ dữ liệu, strict matching, scoring, lịch sử match và thống kê bảo vệ riêng tư | Màn matching/giải thích/thống kê đúng scope theo phân công | Snapshot T2, criteria T5, scope T1 |
| T5 | TermReference, xác thực DN theo trường, demand, cooperation, proposal, invitation thực tập, share, Placement | Cổng doanh nghiệp/nhà trường, màn nhập danh sách sinh viên | T1 core/import, T2 eligibility/consent, T4 matching, T3 handoff |

**Lời mời kích hoạt tài khoản của T1 khác lời mời thực tập do T5 sở hữu.** T2 cung cấp giao diện trả lời lời mời thực tập, không tạo nguồn canonical thứ hai. T5 verification theo trường cũng khác `organizations.status` của T1.

Các khả năng import/outbox/private file mới trong bảng là trách nhiệm theo tài liệu, chưa phải tất cả đã có source. Không triển khai nghiệp vụ ngoài team được giao chỉ để làm adapter trả thành công.

## 3. Bản đồ source hiện tại

```text
<repo>/
├─ pom.xml, mvnw, mvnw.cmd, .mvn/      # Build backend dùng chung
├─ src/main/java/vn/utbridora/auth/
│  ├─ UtBridoraApplication.java        # Main và gốc scan
│  ├─ api/                             # Controller/DTO/error core
│  ├─ security/                        # Session, principal, filter chain, account guard
│  ├─ authorization/                   # Policy/permission/membership/profile visibility
│  ├─ core/                            # Account/password/lifecycle service
│  ├─ user/                            # UserAccount, role/status/history/repository
│  ├─ organization/                    # Organization, Membership và repository
│  ├─ tokens/                          # Invitation/reset token/email
│  ├─ audit/                           # AuditService, registry và request ID
│  ├─ files/                           # Hiện chỉ metadata/repository
│  └─ bootstrap/                       # Tạo admin ban đầu qua cấu hình
├─ src/main/resources/
│  ├─ application.yml
│  └─ db/migration/                    # V1–V7 ở snapshot này
├─ src/test/java/vn/utbridora/auth/     # Unit, IT, migration và E2E harness
├─ web-demo/
│  ├─ src/
│  │  ├─ main.tsx                      # Mount React và import CSS
│  │  ├─ App.tsx                       # Auth, shell, menu, screen state
│  │  ├─ api.ts                        # JSON client, cookie, CSRF, ApiError
│  │  ├─ components.tsx                # Component/hook dùng chung
│  │  ├─ management.tsx                # User/org/audit core screens
│  │  ├─ style.css                     # Token màu/chữ và layout chung
│  │  └─ management.css                # Style quản trị
│  ├─ tests/                           # Playwright
│  ├─ package.json, package-lock.json
│  ├─ vite.config.ts, tsconfig.json, playwright.config.ts
│  └─ Dockerfile, nginx.conf
├─ compose.yaml                        # MySQL + Mailpit, chế độ thủ công
├─ compose.local.yaml                  # Toàn stack demo local
├─ compose.staging.yaml                # Cấu hình staging đã chuẩn bị
├─ run-local.cmd, scripts/run-local.ps1
├─ .github/workflows/verify.yml
└─ docs/
```

Không sao chép project Spring Initializr/Vite khác đè vào cây này. Các thay đổi `pom.xml`, `SecurityConfig`, permission/audit registry, `App.tsx`, CSS chung, proxy, CI và lockfile cần phối hợp người phụ trách để tránh xung đột.

## 4. Chuẩn bị môi trường và chọn cách chạy

### 4.1. Công cụ

| Thành phần | Quy ước hiện tại |
|---|---|
| JDK | **21**; kiểm tra `java -version` và `JAVA_HOME` của IDE/Maven |
| Spring Boot | **4.1.2** trong `pom.xml`; không tự nâng/hạ hàng loạt |
| Maven | Dùng wrapper `mvnw.cmd` trên Windows, `./mvnw` trên Linux/macOS |
| Frontend | React + TypeScript + Vite; cài đúng lockfile bằng `npm ci` |
| Node | **24** trong Docker/CI hiện tại |
| MySQL | **8.4**, InnoDB, UTF8MB4, UTC |
| Docker | Docker Desktop Linux containers để chạy stack và Testcontainers |
| Email demo | Mailpit, không gửi email đến sinh viên thật |

Đứng tại thư mục có `pom.xml`; không đứng ở thư mục cha `D:\HKDN-TTTV-UTH` để chạy Maven hoặc tìm `.env.local`.

### 4.2. Các địa chỉ cần phân biệt

| Cách chạy | Backend kết nối MySQL | Frontend / backend | Mailpit |
|---|---|---|---|
| BE/FE local + MySQL từ `compose.local.yaml` | `127.0.0.1:3307/utbridora` | FE 5173 → BE 8080 | UI 8026; SMTP **chưa publish ra host** |
| Toàn stack qua `run-local.cmd` | Backend container dùng `mysql:3306/utbridora` | Browser 8088 → Nginx → BE nội bộ 8080 | UI 8026; SMTP `mailpit:1025` nội bộ |
| BE/FE local + `compose.yaml` | `127.0.0.1:3306/utbridora` | FE 5173 → BE 8080 | SMTP host 1025, UI 8025 |
| BE/FE và MySQL đều cài trên máy | Địa chỉ/cổng MySQL đã cài, thường `127.0.0.1:3306` | FE 5173 → BE 8080 | Phải có SMTP demo truy cập được riêng |

Các cổng là mặc định trong file cấu hình, không phải kết quả xác nhận dịch vụ đang chạy. `localhost` bên trong container chỉ container đó; backend chạy ngoài Docker không dùng hostname service `mysql`.

Workbench chỉ là công cụ quản lý MySQL. Workbench nối `127.0.0.1:3307` nghĩa là đang xem MySQL Docker; Workbench không cung cấp server thay cho container.

### 4.3. Cách khuyến nghị khi lập trình: BE/FE local, MySQL Docker

**Nếu đã có `.env.local` đúng với database local**, mở Docker Desktop rồi chạy:

```powershell
# Tại thư mục chứa pom.xml; dùng chính stack/volume local hiện có.
docker compose --env-file .env.local -f compose.local.yaml up -d mysql
docker compose --env-file .env.local -f compose.local.yaml ps mysql
```

Chờ MySQL healthy. Nếu trước đó đã chạy toàn stack demo, có thể dừng riêng BE/FE container để tránh thao tác nhầm backend:

```powershell
docker compose --env-file .env.local -f compose.local.yaml stop backend frontend
```

**Máy mới chưa có `.env.local`:** có thể chạy `run-local.cmd start` lần đầu để launcher tạo secret ngẫu nhiên và khởi động demo, rồi dừng riêng BE/FE như trên khi chuyển sang lập trình local. Nếu đã có volume mà mất `.env.local`, phục hồi file đúng của máy đó; không tạo secret mới để thay mật khẩu database cũ.

Mở hai terminal tại gốc repo:

```powershell
# Terminal BE. Biến này bảo đảm liên kết email trỏ về FE đang phát triển.
$env:APP_PUBLIC_URL = "http://localhost:5173"
.\mvnw.cmd spring-boot:run
```

```powershell
# Terminal FE.
cd web-demo
npm ci
npm run dev
```

Lần sau không cần `npm ci` nếu dependency/lockfile chưa thay đổi và `node_modules` còn đầy đủ. Truy cập `http://localhost:5173`; `/api` được Vite chuyển đến backend `http://localhost:8080`. Không dùng trang 8088 để kiểm tra code FE đang chạy bằng Vite.

**Email khi BE local:** Mailpit trong `compose.local.yaml` không mở SMTP 1025 ra host. Muốn test invitation/reset, phải cấu hình SMTP mà BE local truy cập được. Một lựa chọn demo tạm thời, khi cổng 1025/8025 còn trống:

```powershell
# Container email riêng, không liên quan volume MySQL; thư là dữ liệu tạm.
docker run --rm --name utbridora-dev-mailpit -p 127.0.0.1:1025:1025 -p 127.0.0.1:8025:8025 axllent/mailpit:latest
```

Giữ terminal này chạy; BE local dùng `MAIL_HOST=localhost`, `MAIL_PORT=1025`, `MAIL_AUTH=false`, `MAIL_STARTTLS=false`, xem thư tại 8025. Nếu các biến cũ ghi đè mặc định, chỉnh cấu hình local/IDE tương ứng. Thư ở container tạm mất khi dừng; đây không phải outbox bền vững. Cách khác là owner hạ tầng bổ sung override publish SMTP cho stack local có review.

### 4.4. Chạy toàn bộ bằng Docker để demo

```powershell
.\run-local.cmd start
.\run-local.cmd logs
.\run-local.cmd stop
```

Chạy từng lệnh khi cần; `logs` theo dõi liên tục, dừng xem bằng Ctrl+C. UI mặc định `http://localhost:8088`, Mailpit `http://localhost:8026`, MySQL từ máy `127.0.0.1:3307`. `stop` giữ dữ liệu. Khi sửa source nhưng đang xem UI Docker, cần build lại qua launcher; Vite hot reload không cập nhật image Docker.

`BOOTSTRAP_ADMIN_PASSWORD` chỉ dùng tạo admin khi email chưa tồn tại, không đổi mật khẩu tài khoản cũ. Không đưa giá trị này vào hướng dẫn, issue hoặc commit.

### 4.5. Nếu muốn dùng MySQL cài trực tiếp trên máy

Không cần đổi mặc định dùng chung trong `application.yml`. Chỉnh các biến riêng trong `.env.local` của máy hoặc Run Configuration IDE:

| Biến | Giá trị phải tự cấu hình |
|---|---|
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/utbridora?connectionTimeZone=UTC` nếu MySQL local dùng 3306 |
| `DB_USER` | Tài khoản dành cho dự án, có quyền trên schema `utbridora` |
| `DB_PASSWORD` | Mật khẩu riêng của tài khoản đó, nhập ở cấu hình ngoài Git |
| `APP_PUBLIC_URL` | `http://localhost:5173` khi dùng FE dev |

Dịch vụ MySQL phải chạy, schema phải tồn tại và tài khoản phải có quyền chạy migration/ứng dụng phù hợp. Flyway tạo bảng, không tự cài MySQL server. Tách credential migration/runtime khi chuẩn bị vận hành theo hướng dẫn staging.

**Thứ tự cấu hình trong cách chạy hiện tại:** biến môi trường `DB_*` ghi đè giá trị file `.env.local`; file được import tương đối theo working directory. Compose tự đọc `.env` theo cơ chế Compose, Maven không tự đọc file `.env`. `.env.local` dùng `KEY=value` đơn giản, không phải script PowerShell; không thêm `$env:`, `export`, nội suy shell hoặc quote không cần thiết.

Không để `.env.local` đã đổi sang MySQL trên máy làm cấu hình cấp mật khẩu mới cho volume Docker cũ. Khi chuyển chế độ, giữ lại cấu hình đúng của từng môi trường ở nơi bảo mật và dùng tiến trình/terminal riêng. Không xóa volume để chữa lỗi kết nối.

## 5. Database và migration dùng chung

### 5.1. Một schema cho mỗi môi trường

T1 đã có: `users`, `organizations`, `memberships`, `permissions`, `role_permissions`, `one_time_tokens`, `audit_logs`, `files`, `account_status_history`, `core_locks`, `SPRING_SESSION`, `SPRING_SESSION_ATTRIBUTES`; Flyway quản lý lịch sử trong `flyway_schema_history`.

T2 thêm bảng sinh viên vào **cùng schema `utbridora`**, không tạo `student_users` chứa email/password riêng. Theo SRS §20.2 và §23.2, `student_profiles.user_id` tham chiếu danh tính T1; kỹ năng/dự án/chứng chỉ/consent thuộc T2. Roster, quan hệ trường–kỳ và eligibility cần thiết kế theo contract được chấp nhận, không suy rằng import xong là đã được trường duyệt thực tập.

Tương tự, T3/T4/T5 thêm các bảng mình sở hữu. Có database chung không cho phép service tùy tiện thay đổi dữ liệu của team khác.

### 5.2. Quy trình thêm schema

1. Liệt kê entity, owner, ID, quan hệ, trạng thái, unique constraint và query cần hỗ trợ.
2. Đối chiếu SRS/Contract: phân biệt `userId`, `profileId`, MSSV, `schoolOrgId`, `termId`, `placementId`.
3. T1 điều phối version migration theo HEAD và thứ tự merge; không để mọi team cùng tạo V8.
4. Thêm file vào `src/main/resources/db/migration/` với convention `V{version}__t{team}_{feature}.sql`; `{version}` là số được cấp, không phải tên file để dùng nguyên văn.
5. Tạo Entity khớp DDL, Repository và test constraint/query.
6. Kiểm tra database sạch, database nâng cấp có dữ liệu, Flyway validate và ORM validate.

Hiện có V1–V7. Không sửa/xóa migration cũ đã chia sẻ, sửa `flyway_schema_history`, dùng `clean/repair`, bật out-of-order hoặc đổi `ddl-auto` sang `update/create` để né lỗi.

### 5.3. Quy ước dữ liệu

- ID core dùng Java `String`, MySQL `VARCHAR(36)` chứa UUID; khóa tham chiếu cùng kiểu và collation tương thích.
- MSSV là chuỗi, giữ số 0 đầu; theo SRS import, duy nhất trong scope trường. MSSV không phải `users.id`.
- Dấu thời gian dùng `Instant`, `DATETIME(6)` và UTC; ngày học vụ dùng `LocalDate` khi contract quy định ngày thuần.
- JPA enum lưu chuỗi khớp DDL; tham khảo mapping `@JdbcTypeCode(SqlTypes.VARCHAR)` trong Entity core.
- Dùng FK/UNIQUE để bảo vệ toàn vẹn; kiểm tra trước bằng `exists...` không đủ chống hai request đồng thời.
- Chọn optimistic/pessimistic locking theo invariants và hợp đồng. `security_version` của user chỉ phục vụ thu hồi phiên, không thay `@Version` của entity nghiệp vụ.
- Không thêm cascade xóa user/org hoặc dữ liệu lịch sử nếu chưa có chính sách rõ ràng.
- Xây index theo query thực tế: owner/scope, FK, trạng thái, kỳ và pagination. Dùng số liệu/EXPLAIN để đánh giá, không suy hiệu năng từ số dòng.

`MigrationUpgradeIT` hiện kiểm tra nâng từ V2 lên V7 và kỳ vọng 5 migration. Thêm migration mới phải cập nhật kỳ vọng có chủ đích, giữ kiểm tra identity/constraint/idempotence. Dữ liệu test thêm FK về core cần cleanup con trước, cha sau; không xóa assertion để test qua.

## 6. Cách đặt và tổ chức code backend

### 6.1. Package module

Đây là cấu trúc **đề xuất khi tạo module**, chưa phải các thư mục đã tồn tại:

```text
src/main/java/vn/utbridora/auth/modules/
├─ student/       # T2
├─ internship/    # T3
├─ matching/      # T4
└─ cooperation/   # T5

# Bên trong mỗi module, chỉ tạo các lớp thực sự cần:
<module>/
├─ api/           # Controller, request/response DTO
├─ application/   # Service/use case, transaction, orchestration
├─ domain/        # Entity, enum, policy/invariants
├─ persistence/   # Repository, query/projection
└─ integration/   # Port/adapter cho contract liên team đã chốt
```

`UtBridoraApplication` ở `vn.utbridora.auth`; đặt module dưới gốc này để được component/entity/repository scan. Đặt ở `vn.utbridora.student` sẽ không tự được scan theo cấu hình hiện tại. Không tạo main application thứ hai hoặc sửa scan toàn ứng dụng chỉ để ghép project riêng.

Chia package bên trong là lựa chọn tổ chức code, không yêu cầu thêm Maven module/framework. Nếu contract đã chỉ định package/kiểu dùng chung khác, thống nhất với owner trước, không tạo hai bản DTO tương đương ở hai nơi.

### 6.2. Vai trò các lớp

| Lớp | Nên làm | Tránh làm |
|---|---|---|
| Controller | Mapping method/path, `@Valid`, nhận principal, chuyển DTO/service, trả HTTP | Nhét SQL, workflow, tự cấp role hoặc trả password hash |
| Request DTO | Khai báo input được phép, constraint và expectedVersion theo contract | Nhận actor/owner/role do client tự xác nhận |
| Application Service | Guard, tải dữ liệu đúng scope, kiểm state/invariant, transaction, gọi port/audit | Gọi vòng HTTP tới module cùng app, bỏ qua lỗi audit hoặc dùng mock allow thật |
| Entity/Policy | Mapping dữ liệu và quy tắc miền | Phụ thuộc UI hoặc serialize toàn đồ thị entity |
| Repository | Query có scope, lock/projection/page phù hợp | Query không scope rồi trả bản ghi trước khi xác minh quyền |
| Response DTO | Chỉ field phục vụ màn hình và được phép đọc | Trả trực tiếp `UserAccount`, token, internal note hoặc dữ liệu ngoài consent |
| Port/Adapter | Giao tiếp kiểu rõ ràng, owner/consumer/version thống nhất | Đọc/ghi repository nội bộ của team khác để né contract |

Ưu tiên constructor injection, tên tiếng Anh, `jakarta.validation`/`jakarta.persistence` theo stack hiện tại. Không thêm Lombok/MapStruct hay framework khác chỉ để làm theo project mẫu.

## 7. Dùng lại xác thực và phân quyền

### 7.1. Thành phần phải tái sử dụng

- `security/AppPrincipal.java`: UUID actor qua `userId()`.
- `security/SecurityConfig.java`: filter chain, BCrypt cost 12, session fixation protection và CSRF.
- `security/AccountGuardFilter.java`: trạng thái tài khoản và phiên/security version.
- `authorization/AuthorizationPolicy.java`: self/global/organization policy.
- `authorization/PermissionCatalog.java`: exact permission theo role từ database.
- `authorization/EffectivePermissionService.java`: grants phục vụ UI.
- `authorization/ProfileVisibilityPort.java`: port chia sẻ/delegation hiện tại, mặc định deny.

Core dùng session server-side JDBC, cookie HttpOnly. Không thêm JWT/localStorage hoặc login riêng cho mỗi team. Không tự thay `AuthenticationManager`, lưu SecurityContext hoặc chiến lược đổi session ID.

### 7.2. Đăng ký route mới

`SecurityConfig` kết thúc bằng `.anyRequest().denyAll()`. Controller mới chưa được matcher cho phép sẽ bị chặn, dù class đã được Spring scan.

Ví dụ **đề xuất theo BC11, chưa phải route hiện có**; chỉ đăng ký khi triển khai chức năng với contract được chấp nhận, trước fallback:

```java
.requestMatchers(HttpMethod.GET, "/api/v1/students/me/profile").authenticated()
.requestMatchers(HttpMethod.PUT, "/api/v1/students/me/profile").authenticated()
```

`.authenticated()` chỉ cho request đến lớp nghiệp vụ; service vẫn phải kiểm quyền đầy đủ. Không mở cả `/api/**`, bỏ CSRF, bỏ fallback deny hoặc tạo SecurityFilterChain thứ hai để chữa 403.

### 7.3. Actor, role, permission và scope

Lấy actor từ principal đã xác thực:

```java
String actorId = ((AppPrincipal) authentication.getPrincipal()).userId();
```

Trong service, dùng đúng các method hiện có:

```text
isSelf(authentication, userId)
hasGlobal(authentication, permission)
hasOrganization(authentication, organizationId, role, permission)
hasOrganizationForRequest(authentication, pathOrgId, bodyOrgId, role, permission)
canReadStudentProfile(authentication, studentUserId, organizationId)
```

Ví dụ **đoạn guard**, không phải toàn implementation của service:

```java
if (!authorizationPolicy.hasOrganization(
        authentication, schoolOrgId,
        Role.STUDENT, "student_profile:self:write")) {
    throw new AccessDeniedException("Access denied");
}
```

`Role` ở `vn.utbridora.auth.user.Role`, `AccessDeniedException` ở `org.springframework.security.access`. `schoolOrgId` phải được xác minh từ roster/profile thuộc actor và term authoritative; không lấy tùy ý từ body. Sau guard còn kiểm owner, expectedVersion, catalog, file binding, trạng thái và invariant của chức năng.

Đối với record đã tồn tại, query theo cả ID và scope đáng tin, rồi kiểm owner/assignment. Body/path cùng có org thì phải khớp và phải khớp org của record; so body với path thôi chưa đủ. Không chọn membership đầu tiên nếu actor có nhiều scope.

`hasOrganization` yêu cầu membership active, permission đúng, organization VERIFIED và type phù hợp. ADMIN global không tự được làm nghiệp vụ doanh nghiệp/trường. HR/Mentor là assignment nghiệp vụ T3, không thêm global role `HR`/`MENTOR` vào enum bốn role hiện có.

Mã quyền có thật xem V3; ví dụ `student_profile:self:write`, `placement:school:write`. Mã mới trong Candidate không tự hoạt động: cần registry/migration/role grant hoặc capability/delegation, guard và test sau phê duyệt. Không coi dấu `*` hoặc nhãn gộp trong tài liệu cũ là quyền đã tồn tại.

### 7.4. Các điểm phải kiểm thêm

- `canReadStudentProfile` dùng student **user ID** để so self, không dùng profile UUID riêng. Nhánh self hiện chỉ xác minh danh tính; service phải kiểm role/permission và điều kiện hồ sơ cần thiết.
- Boolean allow từ port không cho phép trả toàn hồ sơ. T2 dựng DTO theo field mask/consent/purpose/expiry; T5 sở hữu share, T2 sở hữu consent.
- Giữ deny khi chưa có adapter thật. Mock allow chỉ được dùng trong test có phạm vi rõ ràng.
- API/service phải kiểm assignment, Placement và workflow; quyền trên org không thay quyền trên từng group/task/submission.
- Giữ cơ chế tăng `security_version` khi password/membership/status thay đổi; phiên cũ bị chặn ở request tiếp theo.
- Nếu dùng `@PreAuthorize`, đặt trên public service method được gọi qua Spring proxy; tránh tự gọi qua `this` làm bỏ qua method security/transaction.

### 7.5. Thay adapter chia sẻ mặc định bằng implementation thật

`ProfileVisibilityConfiguration` hiện cấp bean mặc định qua `@ConditionalOnMissingBean(ProfileVisibilityPort.class)`. Khi T2/T5 được giao tích hợp chia sẻ hồ sơ, phối hợp một implementation thật được Spring quản lý bằng `@Bean` hoặc `@Component`, dùng chữ ký port hiện có:

```java
boolean explicitlySharedWithEnterprise(String studentId, String enterpriseOrganizationId);
boolean delegatedToSchool(String studentId, String universityOrganizationId);
```

Trong core hiện tại, `studentId` tại guard là student user ID của T1. Adapter kiểm tra consent/share, trường hoặc doanh nghiệp, purpose, thời hạn và thu hồi theo contract được chấp nhận; T2 tiếp tục lọc DTO theo field được phép. Cần application-context test chứng minh chỉ có một port hiệu lực, bean thật thay default deny đúng cách; test các ca không share, thiếu consent, hết hạn và revoke. Không giả định thêm component là đủ khi wiring chưa được kiểm tra, không dùng `@Primary` để che hai implementation mâu thuẫn.

Những yêu cầu nghiệp vụ mới và adapter này vẫn là dependency, chưa phải implementation có sẵn. Không gọi mock allow của T1 là bằng chứng tích hợp thực tế.

## 8. Transaction, API và audit

### 8.1. Luồng xử lý mutation

```text
Controller nhận DTO + Authentication
  → Service qua Spring proxy
  → Kiểm quyền/scope
  → Bắt đầu hoặc tham gia transaction và lock theo invariant
  → Đọc lại dữ liệu authoritative, kiểm version/state/owner/assignment
  → Mutation đúng owner; gọi port liên team nếu contract yêu cầu
  → Ghi audit, và outbox nếu chức năng đã triển khai theo contract
  → Commit
  → Trả DTO an toàn
```

Đây là trình tự thiết kế; lock/revalidation cụ thể phải theo contract của use case. Không chỉ kiểm trạng thái ngoài transaction rồi ghi mà không chống race. Không giữ lock qua một HTTP call hoặc thao tác SMTP dài.

Không tự xây worker/outbox toàn hệ thống chỉ để thêm một endpoint. Outbox/email retry/import mới là công việc riêng; core hiện gửi email đồng bộ, chưa có durable outbox.

### 8.2. Quy ước API

- API hiện có dùng `/api/v1`; endpoint mới lấy theo contract đã chấp nhận, không tự đổi các endpoint core.
- DTO có validation độ dài/định dạng/range; giới hạn collection, upload, page và filter ở backend.
- Phân trang core hiện có `content`, `number`, `totalElements`, `totalPages`; quy ước hiện tại page từ 0, size mặc định 25, tối đa 100. Endpoint mới tuân thủ contract riêng nếu khác.
- Sorting phải ổn định, có khóa phụ; không đưa raw tên cột từ request vào SQL.
- Không trả Entity nhạy cảm hoặc gây N+1 bằng mapping từng record mà không đánh giá query. `open-in-view=false` yêu cầu mapping/projection trong phạm vi transaction cần thiết.
- Mutation có `expectedVersion`/`Idempotency-Key` khi contract yêu cầu; frontend không thể thay logic idempotency/concurrency ở backend.
- Cập nhật `docs/openapi.json` và tài liệu API cho endpoint đã thực sự triển khai. Chưa có Swagger UI trong source hiện tại.

**Lỗi hiện tại và lỗi mục tiêu phải tách rõ:** `ApiErrorHandler` đang map validation thông thường thành 400 `INVALID_REQUEST`, conflict thành 409 `CONFLICT`; security filter có định dạng riêng và một số nhánh dùng `sendError`. Chưa có envelope lỗi thống nhất cho mọi trường hợp. Các mã 422/`errorCode`/schema mới trong Candidate cần adapter/compatibility review, không tự đổi toàn bộ response core.

Khi tích hợp API hiện có: 401 yêu cầu kiểm tra/đăng nhập lại phiên; 403 cần kiểm role/scope/owner/share/CSRF; 400 kiểm input; 409 kiểm dữ liệu trùng, state hoặc concurrent change. Với 5xx, dùng `X-Request-ID` để đối chiếu log an toàn, không gửi password/token trong ticket. Grants trả từ `/auth/me/permissions` phục vụ UI, không phải token cấp quyền cho các request sau. Không coi các policy đã chốt cho Demo/MVP như k=5 hoặc T5 sở hữu Placement là còn tùy chọn chỉ vì tài liệu cũ có nhãn OPEN; phần contract Candidate chưa signoff và chính sách đưa dữ liệu thật vào vận hành vẫn phải được phân biệt riêng.

### 8.3. AuditService hiện có

Gọi trong transaction nghiệp vụ, dùng ID đã xác minh. Ví dụ khi cập nhật hồ sơ T2 sau này:

```java
audit.writeScoped(
    actorId,
    AuditAction.STUDENT_PROFILE_UPDATED.name(),
    "student_profile",
    profileId,
    "SUCCESS",
    schoolOrgId,
    "PROFILE_UPDATED"
);
```

`AuditService` và `AuditAction` thuộc `vn.utbridora.auth.audit`. Các biến trong ví dụ do service tạo/tải từ record thật; action này đã có trong enum, nhưng luồng cập nhật hồ sơ chưa có trong source.

Quy tắc hiện tại:

- Actor/resource/org ID, khi có, phải là UUID; `resourceType` dùng chữ thường/underscore hợp lệ.
- Result nhận `SUCCESS`, `FAILURE`, `DENIED`; `reasonCode` là mã kiểm soát, không phải mô tả tùy ý.
- Action phải tồn tại trong `AuditAction`; action mới cần owner T1 review registry và test.
- Audit success tham gia transaction; lỗi phải rollback mutation, không catch rồi trả success.
- `securityDenied` dùng transaction riêng; lỗi ghi log không được biến deny thành allow.
- Request ID do core tạo, trả ở `X-Request-ID`. Không tự ghi session/CSRF/reset token, password, email/MSSV hay nội dung file vào audit.
- Nếu đọc PII phải ghi audit, đừng đặt cả use case là `readOnly=true` rồi mong ghi audit cùng transaction được bảo đảm.

## 9. Cách tổ chức và ghép frontend

### 9.1. Module frontend đề xuất

```text
web-demo/src/modules/
├─ student/
├─ internship/
├─ matching/
└─ cooperation/

<module>/
├─ pages/          # Màn hình theo use case
├─ components/     # Component chỉ dùng trong module
├─ api.ts          # Hàm gọi wrapper chung
├─ types.ts        # DTO frontend theo contract
└─ styles.css      # CSS có prefix module, nếu cần
```

Không tạo `package.json`, lockfile, `node_modules`, `createRoot` hoặc login riêng trong từng module. Chuyển source cần thiết vào app React hiện có. `main.tsx` vẫn là entry point duy nhất.

### 9.2. Ghép vào shell và điều hướng

`App.tsx` hiện dùng `screen` state và danh sách `screens`, chưa có React Router. Để thêm màn hình:

1. Tạo page module, xác định props `me`, grants và scope đã chọn nếu cần.
2. Import page vào điểm ghép shell được phân công.
3. Bổ sung menu/title/icon và nhánh render phù hợp; giữ login/logout/session-expired.
4. Khi đổi user hoặc organization, xóa dữ liệu màn hình cũ và tải lại đúng scope.
5. Nếu cần URL routing/deep link rộng hơn, review thay đổi chung trước; không âm thầm thêm router thay cả shell.

Set `permissions` hiện trong `App.tsx` chỉ gom grant ADMIN global để điều khiển menu quản trị T1. Không dùng nguyên set đó cho menu T2–T5. `context` hiện là lựa chọn xem quyền ở màn tài khoản, chưa phải organization selector nghiệp vụ.

Ví dụ hàm hiển thị nút theo scope, dùng type `Grant` đã có:

```typescript
import type { Grant } from '../../api';

export function hasScopedPermission(
  grants: Grant[], organizationId: string, role: string, permission: string
) {
  return grants.some(grant =>
    grant.organizationId === organizationId &&
    grant.role === role &&
    grant.permissions.includes(permission)
  );
}
```

Ví dụ đặt trong `src/modules/student/access.ts` thì đường dẫn import trên đúng. Nếu chuyển file xuống sâu hơn phải sửa đường dẫn. Hàm này chỉ phục vụ UX; backend vẫn quyết định quyền của từng request.

### 9.3. Component và giao diện

Tái sử dụng `components.tsx`: `Field`, `Notice`, `useAction`, `LoadingState`, `EmptyState`, `Pagination`, `StatusBadge`, `Icon`, `Brand`. Đọc props thật trước khi dùng; ví dụ `Field.onChange` nhận chuỗi, không nhận trực tiếp event DOM.

Dùng token CSS sẵn có như `--brand`, `--ink`, `--muted`, `--text-base`, `--text-md`, `--radius`; UI tiếng Việt, tên code tiếng Anh. Đặt class riêng có prefix như `.student-profile` để tránh làm đổi màn hình core. Không ghi đè toàn cục `button`, `table`, `body` từ CSS module mà chưa review.

Mỗi màn cần: loading, empty, lỗi có thể thử lại, trạng thái lưu và chống gửi trùng trong lúc pending; nhãn input rõ ràng, keyboard focus, nút có accessible name, responsive. Không render dữ liệu nhạy cảm vào hidden DOM hoặc chỉ dùng CSS để giấu.

## 10. Gọi API, session và CSRF từ frontend

### 10.1. Transport JSON dùng chung

`web-demo/src/api.ts` xuất `api`, `send`, `getCsrf`, `login`, `logout`, `whoAmI`, `ApiError` và các type core.

```typescript
// Ví dụ route core đang có; file ví dụ đặt ở src/modules/<module>/api.ts.
import { api, type Me, type Grant } from '../../api';

export const loadAccount = () => api<Me>('/auth/me');
export const loadGrants = () => api<Grant[]>('/auth/me/permissions');
```

Wrapper tự thêm `/api/v1`; không truyền `/api/v1/auth/me` lần nữa. Không hardcode `http://localhost:8080` trong page. URL tương đối cho phép cùng source chạy qua Vite dev proxy hoặc Nginx.

Mutation JSON dùng `send<ResponseDto>(path, method, body)`. Wrapper gửi `credentials: 'include'`, lấy CSRF khi cần, thêm header CSRF, xử lý 204 và lỗi. `login()` lấy lại CSRF sau đăng nhập; không tự tạo login bỏ qua bước này.

### 10.2. Flow xác thực hiện tại

```text
GET /api/v1/auth/csrf
  → POST /api/v1/auth/login với cookie + CSRF header
  → Cookie session mới; GET csrf mới
  → GET /api/v1/auth/me và /auth/me/permissions
  → Request nghiệp vụ, mutation tiếp tục kèm CSRF
  → Logout bằng POST có CSRF; phiên/cookie được xử lý bởi core
```

CSRF token giữ trong memory của transport; cookie session HttpOnly do browser quản lý. Không lưu session token/password/CSRF vào localStorage. Khi 401, đưa người dùng về đăng nhập theo sự kiện `session-expired`; không retry vô hạn. 403 có thể do quyền, scope hoặc CSRF, không tự retry mutation vì có nguy cơ tạo tác động trùng.

`ApiError` có `status`, `message`, `requestId`; hiển thị thông báo an toàn và gửi request ID cho người xử lý khi cần. Core client hiện dịch một số mã lỗi sang tiếng Việt; lỗi nghiệp vụ mới cần mapping được review, không đưa SQL/stack trace/raw server body lên UI.

### 10.3. Upload/download chưa được transport hỗ trợ đầy đủ

`api.ts` hiện ép `Content-Type: application/json` khi có body và parse JSON trừ 204. Vì vậy không dùng nguyên wrapper để gửi `FormData` hoặc nhận Blob/CSV.

Khi được giao File Service, mở rộng transport dùng chung có chủ đích: giữ cookie/CSRF/error/session handling; không tự đặt multipart boundary, kiểm MIME/size, xử lý response Blob và tên tải an toàn. T1 phải có storage/scan/resource authorizer thật; hiện chỉ có metadata.

`web-demo/nginx.conf` đang giới hạn body **64k**, có CSP same-origin. File upload theo SRS/contract cần phối hợp nâng giới hạn proxy/backend đúng endpoint, kiểm scanner và policy trước khi nghiệm thu; chạy upload ở Vite không chứng minh Nginx Docker đã hỗ trợ.

### 10.4. Các API auth cần biết

| Method / path đầy đủ | Vai trò |
|---|---|
| GET `/api/v1/auth/csrf` | Lấy headerName/token CSRF |
| POST `/api/v1/auth/login` | Đăng nhập email/password |
| POST `/api/v1/auth/logout` | Đăng xuất |
| GET `/api/v1/auth/me` | `userId`, email, displayName, memberships |
| GET `/api/v1/auth/me/permissions` | Danh sách grants theo scope |
| POST `/api/v1/auth/password/change` | Đổi mật khẩu, phải đăng nhập lại |
| POST `/api/v1/auth/password/forgot` | Response chung, tránh lộ email có tồn tại |
| POST `/api/v1/auth/password/reset` | Token và newPassword |
| POST `/api/v1/auth/invitations/accept` | Hiện nhận `{token, displayName, password}` |

Không thay body accept hiện hành bằng `{token,newPassword}` từ đề xuất import rồi làm hỏng UI core. Contract mới có yêu cầu compatibility/profile riêng, phải triển khai sau review và giữ test client cũ.

## 11. Ví dụ triển khai một chức năng T2

Ví dụ mục tiêu: **Sinh viên xem và sửa hồ sơ của mình**. Đây là quy trình thực hiện sau khi nhiệm vụ và contract được chấp nhận, không phải xác nhận chức năng đã có.

### 11.1. Phân tích trước code

Đọc SRS §7, §20.2, §23.2, §25.8 và BC11/BC12/BC21 trong Candidate. Chốt input/output, term, scope, permission, profileVersion, completeness, kỹ năng và file refs. Không tự thêm GPA/CV làm điều kiện đủ hồ sơ nếu policy không quy định.

BC11 đề xuất `GET /api/v1/students/me/profile?termId=...` và `PUT /api/v1/students/me/profile`. Đây là endpoint đề xuất cụ thể hơn ví dụ namespace chung trong tài liệu cũ; phải kiểm tra phiên bản được owner chấp nhận trước khi công bố.

### 11.2. Backend và database

| Việc | Đặt tại đâu? | Điều cần kiểm tra |
|---|---|---|
| Schema profile và bảng con trong phạm vi | `db/migration/V{được cấp}__t2_....sql` | FK về `users`, unique phù hợp, version, UTC và scope |
| `StudentProfile`, các enum/entity cần thiết | `modules/student/domain/` | Không chứa password/session, mapping khớp DDL |
| `StudentProfileRepository` | `modules/student/persistence/` | Query owner/school/term, lock/version và pagination nếu cần |
| Request/View DTO | `modules/student/api/` | Không nhận owner tùy ý; shape bám contract đã duyệt |
| `StudentProfileService` | `modules/student/application/` | Permission + owner + term + catalog + expectedVersion + audit |
| `StudentProfileController` | `modules/student/api/` | Đường dẫn, `@Valid`, principal; không xử lý nghiệp vụ trong controller |
| Matcher GET/PUT | `security/SecurityConfig.java` | Chính xác method/path, giữ deny fallback/CSRF |
| Port/adapter | Owner theo contract | T4 catalog/policy, T5 term, T1 file; không dùng allow giả |
| Test | `src/test/java/vn/utbridora/auth/modules/student/` | Owner, sai scope, CSRF, stale write, migration và dữ liệu trả về |

Không sao chép một bảng mẫu ba field rồi coi đã đáp ứng toàn BC11. Profile có version/snapshot, kỹ năng, available periods và các quy tắc đổi ngành phải bảo toàn dữ liệu; phải thiết kế đủ theo phạm vi được giao. Chỉ tạo FK tới bảng taxonomy khi migration owner T4 đã được phối hợp.

### 11.3. Frontend

Tạo `modules/student/types.ts`, `api.ts`, `pages/MyProfilePage.tsx` và component cần thiết; ghép vào shell. Nếu file API ở `src/modules/student/api.ts`, import wrapper qua `../../api`.

Với contract BC11 được chấp nhận, hàm đọc có dạng minh họa:

```typescript
import { api } from '../../api';
import type { SelfProfileView } from './types';

export const getMyProfile = (termId: string) =>
  api<SelfProfileView>(
    '/students/me/profile?termId=' + encodeURIComponent(termId)
  );
```

`SelfProfileView` chưa có trong source core; T2 định nghĩa theo contract, không ép `any` để bỏ lỗi type. Hàm ghi phải dùng đầy đủ `ProfileInput` và idempotency/version theo contract, không tự gửi partial body nếu API chưa hỗ trợ PATCH.

Màn hình tải theo term đã chọn, phân biệt chưa có hồ sơ với lỗi quyền/lỗi mạng; giữ draft khi ghi thất bại, báo xung đột và yêu cầu tải lại khi stale. Khi đổi user/scope/term, bỏ hoặc hủy response cũ để tránh hiển thị dữ liệu sai. API thành công mới hiển thị lưu thành công; không dùng `setTimeout` hoặc localStorage giả backend thật.

### 11.4. Nghiệm thu một lát cắt hoàn chỉnh

Sinh viên A đăng nhập → đọc/sửa hồ sơ A → dữ liệu lưu MySQL → tải lại còn dữ liệu → audit có record an toàn. User khác không thể thay ID để đọc/sửa A; membership bị thu hồi bị chặn; sai CSRF bị chặn; stale version không ghi đè. Sau đó mới mở các màn/kỹ năng/file/share phụ thuộc tiếp theo.

## 12. Hợp đồng và phụ thuộc giữa các team

### 12.1. Cách dùng port

Các module cùng Spring Boot nên gọi Java interface/service được owner công bố, không HTTP vòng về `localhost:8080` và không sửa repository của module khác. Owner xuất DTO/port; consumer inject interface. Tránh đặt shared DTO vào cả hai module với hai shape khác nhau.

Các port có tên trong SRS/Candidate như `StudentEligibilityPort`, `TermReadPort`, `PlacementPort` không đồng nghĩa đã có interface trong source. Chỉ `ProfileVisibilityPort` và các service core được kiểm tra mới có thể dùng ngay theo chữ ký hiện hành. Trước khi tạo port mới, kiểm tra checkout và contract để tránh trùng bean/kiểu.

ActorContext/MutationContext theo Candidate phải do backend dựng từ danh tính và scope đã xác minh; không deserialize quyền tin cậy trực tiếp từ JSON của browser.

### 12.2. Các giao điểm quan trọng

| Giao điểm | Owner và quy tắc cần giữ |
|---|---|
| T1 ↔ T2 | T1 identity, T2 profile/roster/consent; profile dùng userId T1, không tạo login khác |
| T1 ↔ T3 | T1 role/session/guard/audit; T3 xác minh HR/Mentor assignment và record scope |
| T1 ↔ T5 | T1 organization/membership, T5 verification theo trường; import T1 job, T2 roster, T5 UI/term |
| T2 ↔ T4 | T2 dữ liệu/snapshot, T4 taxonomy và completeness policy; không nhầm đủ dữ liệu với MATCH/học vụ |
| T2 ↔ T5 | T2 eligibility/consent, T5 invitation/share/Placement; đồng ý lời mời không tự là consent |
| T4 ↔ T5 | T4 kết quả theo snapshot; T5 tái kiểm eligibility/quota và ghi proposal/Placement |
| T5 ↔ T3 | T5 sole Placement owner; APPROVED chỉ cho case WAITING_START, ACTIVE/effectiveAt mới cho task/submission |
| T3 ↔ T2 | Backend task/report/evaluation ở T3, UI sinh viên ở T2; điểm chỉ trả khi đủ điều kiện công bố |

Riêng T4, baseline hiệu chỉnh dùng **k=5**, suppression và chống truy vấn suy diễn; không lấy đoạn k=3 cũ làm chuẩn. Không trả PII hoặc danh sách sinh viên cho doanh nghiệp chỉ vì đã chạy matching.

Riêng T5/T3, lệnh/sự kiện lặp, đến trễ, race quota/Placement cần idempotency/version/lock và test. Không để T3 tự tạo Placement, tự duyệt ứng viên hoặc bắt đầu thực tập chỉ từ một event APPROVED cũ.

### 12.3. Khi owner chưa có implementation

Ghi dependency với owner, contract version, field/state/permission còn thiếu và test chờ. Có thể dùng fake trong unit/consumer test hoặc mock UI có nhãn rõ ràng; production path giữ deny/fail an toàn. Test mock không thay integration test owner thật. Không dùng `return true`, hardcode student/organization hoặc dữ liệu giả trong service thật để tuyên bố hoàn thành.

## 13. File, dữ liệu demo và dữ liệu giữa các máy

Hiện `files/` chỉ có metadata/repository; chưa có storage/upload/download/scanner/resource authorizer hoàn chỉnh. T2/T3/T5 lưu `fileId` và quan hệ nghiệp vụ, không tự mở bucket public hoặc viết một file endpoint bỏ qua core. Metadata CLEAN, owner và binding phải được kiểm chứng khi chức năng file được triển khai.

**Cấu trúc database và dữ liệu là hai phần khác nhau:** Git chia sẻ code/migration; Flyway giúp các máy có cùng schema khi cùng phiên bản. Dữ liệu user/profile trên mỗi Docker volume vẫn riêng; đổi password/tạo tài khoản trên máy A không tự xuất hiện ở B. Build lại image không đồng bộ database.

Nếu cần cùng dữ liệu ban đầu, chuẩn bị seed synthetic theo schema hiện hành và hướng dẫn import có kiểm tra trùng; không phân phối session/token, `.env.local`, dump chứa dữ liệu người thật hoặc mật khẩu chung. Seed/demo là phạm vi riêng có owner/review, không sửa migration cũ để chèn dữ liệu tùy tiện. Tài khoản demo phải qua cơ chế password hashing/quyền của core; không ghi plaintext password trong SQL/version control.

Trước tích hợp chung, dùng môi trường staging được phân quyền với một database chung thay vì tự thêm replication đa chiều giữa máy cá nhân. Repo có cấu hình staging, chưa có bằng chứng server/HTTPS/backup-restore đã được triển khai hoặc nghiệm thu. Không tự deploy chỉ vì hướng dẫn có lệnh.

## 14. Kiểm thử và điều kiện hoàn thành

### 14.1. Lệnh kiểm tra

Chạy từ gốc repo, theo phạm vi thay đổi và công cụ đã chuẩn bị:

```powershell
# Unit backend.
.\mvnw.cmd test

# Unit + integration/migration trên MySQL Testcontainers; cần Docker Linux engine.
.\mvnw.cmd verify
```

```powershell
# Frontend build.
cd web-demo
npm ci
npm run build

# Chỉ cài browser nếu chưa có; đây là bước tải/cài theo môi trường.
npx playwright install chromium
cd ..

# Test harness chuẩn bị backend/database riêng và điều phối Chromium.
.\mvnw.cmd '-Dfrontend.e2e=true' verify
```

Không chạy `npm run test:e2e` rồi giả định đã có fixture/backend: cấu hình Playwright chỉ tự chạy Vite 5178; backend và dữ liệu được harness trong `CorePlatformIT` điều phối. Unit `*Test` và IT `*IT` có phạm vi khác nhau; `mvn test` không chứng minh migration/HTTP thật đã PASS.

Không dùng database local đang chứa dữ liệu làm test fixture. Giữ CSRF và authorization trong test bảo mật; `@WithMockUser` không mặc nhiên tạo `AppPrincipal`/membership thật của core. Tham khảo test hiện có để dựng đúng principal/context.

### 14.2. Những ca bắt buộc cho API nghiệp vụ

| Nhóm | Ca tối thiểu cần xem xét |
|---|---|
| Chức năng | Input hợp lệ/không hợp lệ, state transition đúng/sai, đầu ra và rollback |
| Identity/session | Chưa đăng nhập, phiên hết hạn/thu hồi, account suspended/deactivated |
| Authorization | Sai role/permission, membership revoked, org chưa VERIFIED/sai type |
| Scope/IDOR | Đổi org trong URL/body, đổi record ID, user có nhiều membership, sai owner/assignment |
| CSRF | Mutation thiếu/sai token bị chặn; token/cookie đúng hoạt động |
| Privacy | Chưa share, consent hết hạn/revoke, field ngoài whitelist không xuất hiện |
| Concurrency | Hai request cùng version/quota, retry/idempotency, event trùng/stale nếu có |
| Database | Fresh + upgrade có dữ liệu, FK/unique, Hibernate validate và không mất identity |
| Audit | Event đúng, không lộ secret/PII; lỗi audit success rollback mutation |
| Frontend | Loading/empty/error, thao tác thật, lỗi 401/403/409, scope change, responsive/keyboard |

Đánh dấu PASS chỉ khi đã chạy đúng ca, đúng source và có evidence. Phân biệt unit mock, API integration và E2E thật. Khi thiếu môi trường, ghi `NOT RUN`, lỗi cụ thể và lệnh chạy lại; không tắt test hoặc fake PASS.

Report: `target/surefire-reports/`, `target/failsafe-reports/`, `target/frontend-e2e.log`, `web-demo/test-results/`. Evidence cũ trong docs không thay lượt chạy của PR mới. CI hiện khai báo build FE và `mvn -B -Dfrontend.e2e=true verify`; phải xem kết quả thật của commit, không suy từ việc có workflow.

## 15. Git, pull request và bàn giao

### 15.1. Chia công việc

Mỗi nhánh làm một lát cắt chức năng với schema/API/guard/UI/test liên quan. Phối hợp trước khi nhiều người sửa `SecurityConfig`, `App.tsx`, permission/audit registry hoặc cùng version Flyway. Không đợi ghép bốn project hoàn chỉnh rồi mới kiểm tra tương thích.

Lệnh khởi tạo nhánh dành cho người phát triển tự thực hiện khi working tree đã được kiểm tra và thay đổi đang làm đã được bảo toàn:

```powershell
git status --short
git fetch origin
git switch main
git pull --ff-only origin main
git switch -c feature/student-profile
```

Không ép chạy chuỗi trên nếu đang có thay đổi/nhánh công việc; không reset/clean để làm sạch. Repo đã có Git history, không chạy lại `git init` như dự án mới.

### 15.2. Nội dung PR

Ghi: vấn đề và hành vi sau thay đổi; mã US/FR/contract; owner/consumer; migration mới; endpoint/DTO/permission; ảnh hưởng các team; test đã chạy và chưa chạy; file tài liệu cập nhật; dependency chưa có và cách kiểm tra thủ công.

Review từng file, stage theo chức năng, kiểm tra secret trước commit. Không commit `.env`, `.env.local`, `node_modules`, `target`, `dist`, token, database dump hoặc dữ liệu sinh viên thật. `.gitignore` không tự bỏ theo dõi file đã commit; dùng `git ls-files -- .env .env.local` để kiểm tra khi cần.

Merge thông qua review theo quy trình nhóm. AI không tự commit/push, force-push, reset, deploy hoặc xử lý volume/database ngoài phạm vi được giao. Khi người dùng yêu cầu commit/push, kiểm tra lại diff và chỉ đưa phần được phép.

## 16. Lỗi thường gặp

| Hiện tượng | Kiểm tra và cách xử lý |
|---|---|
| `Communications link failure`, `Connection refused` | MySQL/container đã chạy chưa? Cổng host có đúng 3307/3306 không? Không dùng `mysql:3306` từ BE local. Đây chưa phải bằng chứng sai password. |
| `Access denied for user` | Đã tới MySQL; kiểm đúng instance, DB_USER, DB_PASSWORD và account/host grant. Đổi env không tự đổi password trong volume cũ. |
| Không resolve được `DB_PASSWORD` | Working directory có `pom.xml`/`.env.local` không? Maven không tự đọc `.env`. Không hardcode mật khẩu vào YAML. |
| IDE vẫn dùng DB cũ | Run Configuration/terminal có `DB_*` ghi đè file. Dùng terminal mới hoặc sửa đúng biến riêng; không in tất cả env ra log. |
| MySQL local có bảng, Docker không có hoặc ngược lại | Hai instance/volume riêng; xem lại kết nối Workbench và DB_URL của BE. Không tự đồng bộ. |
| Unknown database / không có quyền tạo bảng | Schema tồn tại chưa, tài khoản có quyền migration phù hợp chưa? Kiểm trên đúng instance. |
| Flyway duplicate/checksum/schema validate | Kiểm version, DDL/Entity và lịch sử migration; khôi phục file cũ đúng nội dung hoặc thêm migration mới có review. Không clean/repair tùy tiện. |
| Controller mới không được nhận diện | Kiểm package dưới `vn.utbridora.auth`, annotation, constructor/bean và component scan. |
| Endpoint mới 403 | Kiểm matcher trước denyAll, method, permission, membership, org, record/assignment và CSRF. Không bỏ bảo mật. |
| Root BE 8080 trả 401/403 | BE là API; mở UI 5173 hoặc 8088 đúng chế độ, không dùng root BE như trang chủ React. |
| Đăng nhập được nhưng menu nghiệp vụ không hiện | Grant phải theo scope, menu ADMIN hiện không đủ; kiểm cơ chế chọn org và rule hiển thị. |
| FE 5173 lỗi proxy | BE 8080 có chạy không, `API_PROXY_TARGET` trong cấu hình Vite có đúng không? Kiểm Network tab, không đổi URL API trong từng page. |
| UI 8088 không cập nhật code mới | Đang xem Nginx/image build cũ; dùng Vite 5173 để dev hoặc rebuild bằng launcher khi demo Docker. |
| Gửi invitation/reset từ BE local thất bại | SMTP của local stack chưa publish; cấu hình Mailpit/SMTP host truy cập được, đúng APP_PUBLIC_URL. |
| Upload lỗi 413 hoặc parse JSON lỗi | Kiểm Nginx 64k, backend limit và transport FormData/Blob; metadata không chứng minh File Service hoàn chỉnh. |
| `mvn test` PASS nhưng IT chưa chạy | Dùng `verify` với Docker; đọc Failsafe report, không gộp kết quả mock thành E2E thật. |

## 17. Mẫu giao việc cho AI

Sao chép khối sau vào AI có quyền đọc repository, điền Team và một chức năng cụ thể. Có thể gửi kèm file này; chỉ gửi prompt không có source thì AI chưa thể xác minh trạng thái repo.

```text
Bạn tiếp tục dự án UT.Bridora hiện có, không tạo project mới.

TEAM: [T2/T3/T4/T5 hoặc phạm vi T1 được giao]
CHỨC NĂNG: [một use case cụ thể và mã US/FR]
ĐẦU RA: [backend/frontend/database/test/tài liệu trong phạm vi]
CONTRACT ĐƯỢC CHẤP NHẬN: [version + mục + bằng chứng owner/consumer đồng ý,
hoặc ghi rõ chưa có signoff để AI nhận diện phần phụ thuộc]

1. Đọc README.md, docs/README.md,
   docs/guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md.
   Kiểm tra git status và hướng dẫn riêng trong thư mục đang làm nếu có.
2. Đọc đầy đủ SRS hiện hành gồm bảng/phụ lục trước khi sửa
   nghiệp vụ; xác định chính xác phần liên quan và Contract Pack B01–B06.
   Phân biệt baseline, Candidate chưa duyệt, source hiện có và test đã chạy.
   Không suy đoán nội dung file thiếu hoặc tự phê duyệt chính sách.
3. Khảo sát class/API/schema/UI/test hiện tại trước khi sửa. Nêu phần tái sử
   dụng, dependency owner, contract còn thiếu và ảnh hưởng team khác.
4. Giữ Modular Monolith: một Spring Boot app, một pom.xml, một React app
   web-demo, một schema mỗi môi trường; không tạo login/JWT/user table riêng.
5. Code BE mới dưới vn.utbridora.auth.modules.<module> theo cấu trúc được
   thống nhất. Controller/DTO/service/repository/entity tách trách nhiệm.
   Actor từ AppPrincipal; service kiểm permission+scope+record+owner/
   assignment+state. Đăng ký đúng method/path trong SecurityConfig,
   giữ denyAll/CSRF/session và audit transaction.
6. Database dùng Flyway mới với version do T1 điều phối; không sửa migration
   cũ, không clean/repair/ddl-auto=update. Dùng UUID/UTC và constraint/lock
   đúng hợp đồng; không ghi dữ liệu của owner team khác trực tiếp.
7. FE mới trong web-demo/src/modules/<module>, ghép shell App.tsx hiện có.
   Dùng api.ts/components.tsx/CSS tokens, quyền theo grant và scope.
   Không hardcode backend URL, không tạo app/router/login mới tùy ý,
   không lưu session/password vào localStorage. Không coi ẩn nút là bảo mật.
8. Không tự biến port/fake/màn mock thành implementation thật. Thiếu adapter
   consent/share/assignment thì deny/fail an toàn; mock chỉ trong test/demo
   có phân biệt. Không tự đổi DTO/error/status đã công bố để lấp chỗ trống.
9. Kiểm thử phù hợp: unit, MySQL integration/migration, authorization/CSRF,
   FE build và E2E của use case. Không đụng database đang dùng để chạy test.
   Thiếu môi trường ghi NOT RUN, không fake PASS; không tắt security/test.
10. Giữ thay đổi người dùng, không đọc/in secret .env hoặc đưa PII thật vào
    demo. Không cài/nâng dependency không liên quan, không đổi kiến trúc,
    không reset/xóa file/volume, không commit/push/deploy khi chưa được giao.

Thực hiện phần được ủy quyền và có yêu cầu rõ ràng, không dừng ở kế hoạch.
Nếu thiếu quyết định nghiệp vụ quan trọng, làm phần độc lập được phép và
ghi chính xác mục bị chặn/owner cần chốt; không tự suy diễn hoặc báo xong.

Báo cáo bằng tiếng Việt:
- Chức năng thực sự đã triển khai và mã yêu cầu/contract tương ứng.
- Các file tạo/sửa và ảnh hưởng API/database/team khác.
- Lệnh kiểm thử, kết quả thực tế PASS/FAIL/NOT RUN và nơi lưu evidence.
- Giới hạn, dependency, phần chưa hoàn thành và bước tiếp theo.
```

Để AI chỉ khảo sát trước, thay câu “Thực hiện phần được ủy quyền…” bằng “Chỉ đọc và báo cáo; không sửa file, cài dependency, chạy migration hoặc thay dữ liệu”. Không đưa mật khẩu/token vào prompt.

## 18. Checklist bắt đầu và tích hợp

### Trước khi bắt đầu một chức năng

- [ ] Đúng repo/branch, đã xem thay đổi chưa commit và không ghi đè công việc khác.
- [ ] Đọc AGENTS/SRS và contract; xác định rõ trạng thái phê duyệt của phần dùng.
- [ ] Có owner và phạm vi BE/FE/schema/test; tránh làm trùng nghiệp vụ team khác.
- [ ] Có input/output/state/permission/validation/error/AC đủ cho phần được giao.
- [ ] Biết frontend đang gọi BE nào, BE đang dùng MySQL instance/volume nào.
- [ ] Dependency port/registry/file/term/taxonomy được ghi rõ, không giả là đã có.

### Trước khi gửi PR tích hợp

- [ ] Package module được scan; không thêm main app hoặc security/login riêng.
- [ ] Migration mới được cấp version, fresh/upgrade test phù hợp, ORM validate.
- [ ] Guard backend đủ role/permission/scope/owner/assignment/state; CSRF giữ bật.
- [ ] API/DTO/registry/error tương thích; exact code mới đã được review.
- [ ] Transaction/concurrency/idempotency và audit đúng yêu cầu.
- [ ] UI dùng shell/client/component chung; scope change không lộ dữ liệu cũ.
- [ ] Loading/empty/error/session expired/responsive và accessibility được kiểm tra.
- [ ] Endpoint thật có test deny/cross-tenant; mock không thay test tích hợp.
- [ ] Test đã chạy có evidence, test chưa chạy ghi NOT RUN và nguyên nhân.
- [ ] Tài liệu API/trạng thái cập nhật theo code; không ghi tính năng chưa có là xong.
- [ ] Diff không chứa secret/PII/dump/build output hoặc sửa ngoài phạm vi.
- [ ] Owner/consumer review phần liên team; không tự commit/push/deploy nếu chưa được giao.

## 19. Căn cứ đối chiếu và giới hạn xác minh

Hướng dẫn được viết từ việc đọc source/config/test và tài liệu hiện có, gồm:

- `pom.xml`, `UtBridoraApplication`, `SecurityConfig`, `AppPrincipal`, `AuthorizationPolicy`, `AuditService`, `AuditAction`, `ApiDtos`, `AuthController`, `ApiErrorHandler`, `AdminReadController`.
- Migration V1–V7, `UserAccount`, `Membership`, `MigrationUpgradeIT`.
- `web-demo/src/main.tsx`, `App.tsx`, `api.ts`, `components.tsx`, `management.tsx`, CSS, package/TypeScript/Vite/Playwright và Nginx config.
- `application.yml`, Compose, launcher, `.gitignore`, `.github/workflows/verify.yml` và hướng dẫn triển khai/bàn giao.
- SRS v1.2.4: ownership §20.2, bảo mật §20.3–20.6, DB §23, hiệu chỉnh §25.8 và đề xuất §25.9; BC11/BC21 và quy định tương thích/signoff trong Contract Candidate.

Không sửa source/SRS/Contract, không chạy migration, tạo dữ liệu hoặc triển khai môi trường trong lượt viết tài liệu này. Không đọc giá trị secret `.env.local`. Các lệnh chạy trong hướng dẫn là để người phát triển thực hiện trên môi trường phù hợp, không phải bằng chứng chúng vừa PASS. Cần xác minh lại Docker/MySQL, SMTP, quyền GitHub, CI và signoff tại thời điểm triển khai thật.
