# Đưa code T2–T5 vào Core Platform

Tài liệu này hướng dẫn ghép module nghiệp vụ vào source hiện tại. Chưa có source T2–T5 được cung cấp trong lần hướng dẫn này, nên cây thư mục, endpoint và lớp nghiệp vụ bên dưới là đề xuất tích hợp, không phải chức năng đã triển khai.

## 1. Mô hình triển khai chung

Toàn bộ sản phẩm tiếp tục là **Modular Monolith**:

- Một ứng dụng Spring Boot, một `pom.xml`, một main application.
- Một frontend React trong `web-demo`, một `package.json` và lockfile dùng chung.
- Một schema MySQL cho một môi trường chạy; Flyway quản lý cả core và bảng nghiệp vụ.
- Các module dùng chung identity, tổ chức, membership, session, CSRF và AuditService.

Mỗi máy phát triển có database Docker local riêng. Git chia sẻ source và migration, không tự đồng bộ dữ liệu giữa các máy. Khi triển khai staging chung, các module chạy trong cùng ứng dụng và sử dụng database staging đã thống nhất.

Không lấy toàn bộ project riêng của một team ghi đè lên project core. Chuyển các lớp, màn hình, dependency và migration cần thiết thành module trong cấu trúc chung. Giữ cơ chế đăng nhập hiện có; không ghép thêm ứng dụng JWT hoặc SecurityFilterChain độc lập.

## 2. Chuẩn bị nhánh và source đầu vào

Người mới clone repo:

```powershell
git clone https://github.com/NguyenThanhTiep/utbridora.git
cd utbridora
git switch -c feature/student-profile
```

Nếu đã clone trước đó:

```powershell
git switch main
git pull --ff-only origin main
git switch -c feature/student-profile
```

Tên nhánh theo chức năng: `feature/student-profile`, `feature/internship-tasks`, `feature/matching-report`, `feature/cooperation-request`. Mỗi chức năng tạo pull request và review trước khi merge.

Repo Public cho phép đọc/clone; muốn push nhánh lên repo gốc cần quyền cộng tác. Người chưa có quyền có thể gửi pull request từ fork theo quy trình nhóm.

Team gửi kèm: danh sách lớp/màn hình, API, schema, dependency, permission cần dùng, chủ sở hữu dữ liệu, test và các cấu hình cần bổ sung. Giữ `pom.xml`, `application.yml`, main application và các entry point frontend chung; bổ sung thay đổi có review thay vì thay cả file.

## 3. Cấu trúc backend đề xuất

```text
src/main/java/vn/utbridora/auth/
├── UtBridoraApplication.java
├── api/ security/ authorization/ user/ organization/ audit/ ...  # Core hiện có
└── modules/                                                    # Phần bổ sung
    ├── student/       # T2: hồ sơ, minh chứng và dữ liệu sinh viên
    ├── internship/    # T3: task, submission, evaluation
    ├── matching/      # T4: tổng hợp, thống kê, matching
    └── cooperation/   # T5: hợp tác, xác thực tổ chức, chia sẻ
```

Mỗi module có thể dùng các package con `controller`, `service`, `repository`, `entity`, `dto`, `integration` khi cần. Ví dụ:

```text
vn.utbridora.auth.modules.student.controller.StudentProfileController
vn.utbridora.auth.modules.student.service.StudentProfileService
vn.utbridora.auth.modules.student.repository.StudentProfileRepository
vn.utbridora.auth.modules.student.entity.StudentProfile
vn.utbridora.auth.modules.student.dto.StudentProfileView
```

**Package gốc hiện là `vn.utbridora.auth`** vì main application nằm tại đó. Đặt module mới bên dưới gốc này để component, entity và repository được phát hiện. Package ngang cấp như `vn.utbridora.student` chưa được scan theo cấu hình hiện tại. Chỉ mở rộng `scanBasePackages` chưa bảo đảm giải quyết cả entity và repository scan; việc đổi gốc package cần một thay đổi tích hợp riêng.

Tên lớp/bean nên thể hiện domain, tránh tạo thêm `UserController`, `SecurityConfig` hoặc các bean trùng tên với core. Nếu source cũ dùng `javax.persistence`/`javax.validation`, kiểm tra và chuyển sang `jakarta` phù hợp Java 21/Spring Boot hiện tại. Giữ phiên bản framework; dependency mới phải được kiểm tra tương thích.

## 4. Phân chia dữ liệu và trách nhiệm

| Phần | Thành phần sử dụng lại | Team triển khai thêm |
|---|---|---|
| T2 | `users.id`, principal, owner guard, profile visibility port, file metadata | Hồ sơ/minh chứng, DTO theo quyền, lọc trường được chia sẻ |
| T3 | Identity, membership/org guard, audit | Task/submission/evaluation và kiểm tra mentor/assignment/workflow |
| T4 | Identity, permission/org scope | Truy vấn tổng hợp, ngưỡng ẩn danh, chống suy ngược nhóm nhỏ, export guard |
| T5 | `organizations.id`, membership, org guard, profile port | Xác thực doanh nghiệp, hợp tác và quyết định chia sẻ/thu hồi |

Placement cần T3/T5 xác nhận **một module sở hữu bản ghi và migration**; module còn lại dùng contract đọc/kiểm tra tương ứng. Không tạo hai bảng Placement độc lập cho cùng một nghiệp vụ. Nguyên tắc này áp dụng cả consent và profile share giữa T2/T5.

Giữ nguyên `users`, `organizations`, `memberships` và các bảng core. Hồ sơ sinh viên tham chiếu `users.id`; không tạo tài khoản sinh viên/doanh nghiệp riêng có password/session riêng. DTO trả về client không được chứa `passwordHash`, token hoặc nguyên entity `UserAccount`.

## 5. Database và migration

Migration mới đặt tại:

```text
src/main/resources/db/migration/
```

Hiện repo có V1–V7. Ví dụ tên migration tiếp theo là `V8__student_profiles.sql`, nhưng số thực tế phải được điều phối trước khi sử dụng.

Quy trình:

1. Thống nhất bảng, owner, FK, permission và phụ thuộc giữa các module.
2. Đăng ký version trong một danh sách migration chung do người tích hợp quản lý.
3. Thống nhất thứ tự merge và chạy migration theo phụ thuộc. Không để các team cùng tự dùng V8.
4. Không áp dụng version cao lên môi trường chung rồi mới đưa version thấp hơn vào sau. Cấp các dải số riêng cho từng team không tự giải quyết được vấn đề thứ tự.
5. Kiểm thử nhánh bằng MySQL Testcontainers dùng một lần; database local có dữ liệu và staging chỉ nâng cấp theo tập migration đã hợp nhất, theo thứ tự được kiểm tra.
6. Kiểm tra cả database trống và nâng cấp database có dữ liệu. Không sửa migration đã áp dụng; không dùng `repair`, `clean` hoặc bật out-of-order chỉ để che lỗi version/checksum.

Quy ước hiện tại:

- ID dùng UUID dạng `String`/`VARCHAR(36)`; actor/resource/org ID gửi vào AuditService phải là UUID.
- FK người dùng và tổ chức trỏ `users(id)`, `organizations(id)`.
- Thời gian nghiệp vụ dùng `Instant`, lưu UTC theo cấu hình chung.
- Hibernate giữ `ddl-auto: validate`; schema thay đổi bằng Flyway.
- Enum JPA cần khớp kiểu SQL. Khi migration dùng VARCHAR, tham khảo entity core với `@Enumerated(EnumType.STRING)` và `@JdbcTypeCode(SqlTypes.VARCHAR)` để tránh Hibernate suy ra native ENUM không khớp.
- Permission mới phải được thêm vào `permissions` và `role_permissions` bằng migration. V3 có unique `(resource, action)`, nên cần kiểm tra cả cặp này và code permission.
- Bốn role hiện có là hợp đồng dùng chung; thêm role mới cần thống nhất ngoài phạm vi seed một permission.

**Test cần chú ý:** `MigrationUpgradeIT` hiện kiểm tra cố định 5 migration được chạy khi nâng V2 lên V7. Khi thêm V8+, cập nhật kỳ vọng/latest version có chủ đích và giữ kiểm tra dữ liệu, constraint, idempotence. Nếu test tạo thêm dữ liệu có FK về core, cập nhật cleanup theo thứ tự con trước, cha sau; không bỏ assertion hoặc tắt security để test xanh.

## 6. Đưa API mới vào security chain

Endpoint nghiệp vụ dùng prefix `/api/v1`. Namespace dưới đây là đề xuất cần thống nhất trước khi công bố:

```text
/api/v1/student-profiles/...                     # T2
/api/v1/organizations/{orgId}/internships/...     # T3
/api/v1/organizations/{orgId}/matching/...        # T4
/api/v1/organizations/{orgId}/cooperation/...     # T5
```

`SecurityConfig` hiện kết thúc bằng `.anyRequest().denyAll()`. API thêm vào controller sẽ bị chặn nếu chưa đăng ký path. Thêm matcher method/path chính xác **trước fallback**, ví dụ cho API tạo Placement khi contract đã được duyệt:

```java
.requestMatchers(HttpMethod.POST,
    "/api/v1/organizations/*/placements").authenticated()
```

Đây chỉ là cho phép request đã đăng nhập tới lớp nghiệp vụ. Service vẫn phải kiểm tra quyền, org, record và workflow. Không chuyển toàn bộ `/api/**` sang `permitAll`, không bỏ CSRF hoặc thay fallback deny để xử lý 403.

## 7. Dùng quyền và danh tính core trong service

Các API thực tế của `AuthorizationPolicy`:

```text
isSelf(authentication, userId)
hasGlobal(authentication, permission)
hasOrganization(authentication, organizationId, role, permission)
hasOrganizationForRequest(authentication, pathOrgId, bodyOrgId, role, permission)
canReadStudentProfile(authentication, studentUserId, organizationId)
```

Actor lấy từ principal đã xác thực:

```java
String actorId = ((AppPrincipal) authentication.getPrincipal()).userId();
```

Không nhận actor từ body. `Authentication.getName()` không phải hợp đồng UUID actor của core; dùng `AppPrincipal.userId()`.

Ví dụ annotation cho phương thức tạo Placement của module sở hữu. `PlacementView`/`PlacementCommand` là kiểu do team nghiệp vụ bổ sung; đoạn này minh họa guard, không phải implementation đã có:

```java
@PreAuthorize("""
    @authorizationPolicy.hasOrganization(
        authentication, #orgId,
        T(vn.utbridora.auth.user.Role).SCHOOL_STAFF,
        'placement:school:write')
    """)
@Transactional
```

Gắn vào public service method có parameter tên `orgId`, chẳng hạn `create(Authentication authentication, String orgId, PlacementCommand command)`. Controller gọi service được Spring inject để method-security/transaction proxy có hiệu lực; gọi nội bộ qua `this` không áp dụng guard theo cách này.

`hasOrganization` hiện yêu cầu membership active, permission được cấp và tổ chức VERIFIED đúng type. Guard này **không thay thế** các kiểm tra sau:

- Org thật của record trong database có khớp phạm vi đang truy cập không?
- Actor có sở hữu hồ sơ/submission hoặc được phân công mentor cho Placement không?
- Tài nguyên và workflow có cho phép hành động hiện tại không?
- Student, school và enterprise liên quan có quan hệ hợp lệ không?

Với bản ghi đã tồn tại, query theo cả ID và scope, ví dụ repository nghiệp vụ `findByIdAndOrganizationId(...)`, rồi kiểm tra assignment/owner. Nếu người dùng có membership ở nhiều org, vẫn phải kiểm tra org của bản ghi đang thao tác. Đối với tạo mới, gán scope đã xác minh từ service; không tin `organizationId` tự khai trong body. Body cũ có scope trùng path cần dùng `hasOrganizationForRequest` hoặc kiểm tra tương đương.

ADMIN toàn cục không tự có quyền nghiệp vụ. Nếu nghiệp vụ cần người quản trị được thao tác, phải có chính sách rõ ràng và test tương ứng.

## 8. Tích hợp quyết định chia sẻ hồ sơ T2/T5

Core đã có `ProfileVisibilityPort`:

```java
boolean explicitlySharedWithEnterprise(String studentId, String enterpriseOrganizationId);
boolean delegatedToSchool(String studentId, String universityOrganizationId);
```

T2/T5 thống nhất **một implementation thật** và dữ liệu chủ sở hữu. Implementation phải kiểm tra share/consent, phạm vi, thời hạn, purpose và thu hồi theo nghiệp vụ được duyệt. Giữ deny mặc định khi chưa có dữ liệu hoặc chưa hoàn thiện adapter. Có test application context chứng minh adapter thật thay bean mặc định và chỉ có một port được sử dụng.

Lưu ý quan trọng: `canReadStudentProfile` kiểm tra `isSelf` theo `users.id`. Nếu bảng hồ sơ có `profiles.id` riêng, phải lấy `profile.userId` từ record để gọi guard, không truyền nhầm profile ID hoặc user ID do client tự khai.

Boolean cho phép đọc không có nghĩa được trả toàn bộ hồ sơ. T2 vẫn phải tạo DTO/projection chỉ chứa các trường được phép theo quyết định chia sẻ. Test mock trả allow của core không phải adapter nghiệp vụ để đưa lên môi trường thật.

## 9. Ghi AuditService đúng hợp đồng

Gọi `writeScoped` trong transaction nghiệp vụ, sau khi cập nhật dữ liệu và trước khi trả kết quả. Ví dụ khi tạo Placement thành công:

```java
audit.writeScoped(
    actorId,
    AuditAction.PLACEMENT_CREATED.name(),
    "placement",
    placementId,
    "SUCCESS",
    orgId,
    "PLACEMENT_CREATED"
);
```

Đây là đoạn gọi từ implementation module, các ID được lấy từ bản ghi/danh tính đã xác minh. `actorId`, `placementId`, `orgId` phải là UUID. `resourceType` dùng chữ thường/underscore; `reasonCode` là mã kiểm soát, không phải câu mô tả tự do.

Action phải tồn tại trong `AuditAction`; ví dụ đã có `TASK_ASSIGNED`, `SUBMISSION_CREATED`, `EVALUATION_SUBMITTED`, `PROFILE_SHARED`, `COOPERATION_REVIEWED`. Dù parameter là String, service kiểm tra enum và sẽ từ chối event không được đăng ký. Event mới cần review registry/test/tài liệu, không tùy đặt một chuỗi ở module riêng.

Audit SUCCESS lỗi sẽ làm transaction nghiệp vụ rollback; không catch rồi bỏ qua lỗi để trả thành công. Không đặt transaction `readOnly=true` nếu cùng transaction đó phải ghi audit đọc dữ liệu nhạy cảm. Không truyền password, token, raw URL/body hoặc dữ liệu cá nhân vào audit. Core tự xử lý correlation ID.

## 10. Ghép frontend vào shell hiện tại

```text
web-demo/src/
├── App.tsx                 # Auth, shell, menu và chọn màn hình hiện tại
├── api.ts                  # Client JSON/session/CSRF dùng chung
├── components.tsx          # Field, Notice, LoadingState, EmptyState...
└── modules/
    ├── student/
    ├── internship/
    ├── matching/
    └── cooperation/
```

Chuyển page/component/type/API client cần thiết từ source team vào module; bổ sung import và mục màn hình trong `App.tsx`. Giữ login, session handling và layout chung. App hiện chọn màn bằng state `screen`, chưa có React Router. Việc thay routing toàn ứng dụng cần thống nhất riêng.

Ví dụ gọi endpoint đề xuất của T2 sau khi backend đã triển khai và được đăng ký:

```typescript
import { api, send } from '../../api';

// StudentProfileView/UpdateStudentProfile thuộc module T2.
export const getMyProfile = () =>
  api<StudentProfileView>('/student-profiles/me');

export const updateMyProfile = (body: UpdateStudentProfile) =>
  send<StudentProfileView>('/student-profiles/me', 'PUT', body);
```

Client tự thêm `/api/v1`, session cookie và CSRF cho mutation. Không truyền thêm `/api/v1` lần nữa hoặc hardcode URL `localhost:8080`. Nhờ URL tương đối, Vite và Nginx cùng chuyển tiếp được API.

**Menu nghiệp vụ:** Set `permissions` hiện tại chỉ lấy grant ADMIN global cho các màn quản trị core. Không dùng nguyên Set này cho T2–T5. Chọn grant theo role, tổ chức đang chọn và permission nghiệp vụ. Ví dụ logic hiển thị:

```typescript
const canUseOrganizationFeature = (
  permission: string, organizationId: string, role: string
) => grants.some(grant =>
  grant.organizationId === organizationId &&
  grant.role === role &&
  grant.permissions.includes(permission)
);
```

`context` hiện chỉ là index xem quyền trong màn tài khoản, chưa phải bộ chọn tổ chức nghiệp vụ. Team thêm bộ chọn scope có chủ đích và truyền org đang chọn vào page. Backend vẫn xác minh lại mọi request; ẩn/hiện menu không cấp quyền.

**Upload và export:** `api.ts` hiện dành cho JSON, ép Content-Type khi có body và parse JSON trừ 204. Upload `FormData`/download Blob hoặc CSV cần mở rộng transport có chủ đích, giữ cookie và CSRF; không dùng nguyên wrapper như thể đã hỗ trợ. Nginx hiện giới hạn body `64k`; T2 cần phối hợp giới hạn proxy/backend và storage/AV nếu có upload. Core files hiện chỉ có metadata.

Giữ một `package.json`/lockfile chung. Thêm dependency thật sự cần thiết và kiểm tra tương thích React/TypeScript hiện tại; không chép đè lockfile từ một project frontend khác.

## 11. Kiểm thử trước khi hợp nhất

Mỗi endpoint thật của module cần có test phù hợp:

- Chưa đăng nhập, sai role/permission, membership đã thu hồi.
- Tổ chức chưa VERIFIED hoặc sai type.
- Đổi org trong URL/body; đổi record ID sang dữ liệu org khác.
- Có membership ở nhiều org nhưng record không thuộc scope đang thao tác.
- Sai owner, thiếu assignment/mentor hoặc workflow không hợp lệ.
- ADMIN cố thao tác khi chưa có quyền nghiệp vụ tương ứng.
- Thiếu/sai CSRF đối với mutation.
- Hồ sơ chưa share, hết hạn, thiếu consent, đã revoke; DTO không lộ field ngoài phạm vi.
- Audit thành công, và rollback nghiệp vụ khi audit lỗi.
- Migration database sạch và nâng cấp có dữ liệu; không phá identity và constraint cũ.

Không chỉ test đường dẫn `/demo/...` hoặc mock allow rồi xem là endpoint thật đã được bảo vệ. Gộp code/test của chức năng trong pull request; cập nhật OpenAPI, tài liệu và báo cáo kết quả thực thi.

Lệnh từ thư mục chứa `pom.xml`:

```powershell
.\mvnw.cmd test
cd web-demo
npm ci
npm run build
npx playwright install chromium
cd ..
.\mvnw.cmd '-Dfrontend.e2e=true' verify
```

Docker Linux engine cần hoạt động cho MySQL Testcontainers. Phân biệt PASS/FAIL/NOT RUN; các số 28/49/14 trong báo cáo cũ là bằng chứng của snapshot cũ, không phải kết quả tự động cho source mới đã ghép module.

CI hiện build frontend và chạy `mvn -B -Dfrontend.e2e=true verify`. Thêm hoặc điều chỉnh test có chủ đích theo migration/module mới; giữ test quan trọng của core.

## 12. Merge và chạy bộ source đã tích hợp

Trước pull request, cập nhật từ `main` và giải quyết xung đột có review, đặc biệt tại `pom.xml`, migration, SecurityConfig, permission/audit registry, `App.tsx` và lockfile. Trong ví dụ dưới, thay `DUONG_DAN_FILE_DA_REVIEW` bằng từng file/thư mục thật của chức năng, bao gồm test và các file dùng chung đã sửa.

```powershell
git fetch origin
git merge origin/main
# Kiểm thử lại và review diff sau khi giải quyết xung đột.
git add -- "DUONG_DAN_FILE_DA_REVIEW"
git commit -m "feat(student): add student profile management"
git push -u origin feature/student-profile
```

Thay đường dẫn minh họa bằng file thực tế, rồi tạo pull request. Người tích hợp kiểm tra quyền, ownership, thứ tự migration và CI trước khi merge. Nên tích hợp từng luồng end-to-end nhỏ: một API + schema + guard + UI + test, thay vì ghép toàn bộ bốn project cùng lúc.

Sau khi merge và kéo source mới về, tại thư mục chứa `pom.xml`:

```powershell
git switch main
git pull --ff-only origin main
.\run-local.cmd start
```

Launcher build lại backend/frontend, chạy container và chờ health; Flyway áp dụng migration mới theo cấu hình. Image backend hiện build với `-DskipTests`, nên launcher chạy thành công **không thay thế** lượt unit/integration/E2E trước đó.

- Ứng dụng Docker: `http://localhost:8088`.
- Workbench: `127.0.0.1:3307`, schema `utbridora`, user `bridora`, mật khẩu trong `.env.local` của máy đó.
- Backend IDE: mặc định MySQL Docker qua cổng 3307, đọc `.env.local` khi chạy từ thư mục gốc; biến môi trường cũ có thể ghi đè.
- Backend trong Docker: dùng địa chỉ nội bộ `mysql:3306`.

Chỉ chuẩn bị/triển khai staging theo [DEPLOYMENT_GUIDE](DEPLOYMENT_GUIDE.md) khi có môi trường và cấu hình phù hợp. Chạy local không phải bằng chứng deploy staging/production.

## Checklist cho mỗi pull request nghiệp vụ

- [ ] Module đúng package, được component/entity/repository scan.
- [ ] Giữ contract core, không tạo identity/session hoặc schema vai trò trùng.
- [ ] API/DTO/schema/permission được thống nhất; migration có owner và thứ tự rõ ràng.
- [ ] Matcher đúng method/path; guard service và record/owner/workflow checks đầy đủ.
- [ ] Audit action/ID/transaction hợp lệ; có kiểm tra dữ liệu nhạy cảm.
- [ ] UI ghép vào shell, quyền theo scope, có loading/error/empty và client CSRF/session.
- [ ] Module có test thật, migration upgrade test được cập nhật có chủ đích; CI PASS.
- [ ] OpenAPI, tài liệu tích hợp và dữ liệu demo an toàn được cập nhật.
- [ ] Source/PR không chứa `.env.local`, mật khẩu, token, database dump hoặc dữ liệu sinh viên thật.
- [ ] Dependency còn chờ team khác được ghi rõ; không dùng adapter allow giả để đánh dấu hoàn thành.
