# UT.Bridora Contract Pack B01–B06 v1.1 Candidate

Ngày hiệu chỉnh: **05/11/2026**. Phạm vi: hoàn thiện kỹ thuật T1-01; không triển khai module nghiệp vụ hoặc thay schema database.

**Trạng thái:** TECHNICAL REVIEW CANDIDATE. Người dùng đã chốt bản v1.0 ngày 05/11/2026 theo [quyết định](README.md#quyết-định-chốt-contract--05112026); quyết định đó không tự phê duyệt diff của bản v1.1. Bản này bổ sung Data Dictionary và schema kiểm được bằng máy, giữ nguyên nghiệp vụ, route, enum và ranh giới các team đã nêu trong v1.0. Không đưa vòng ký riêng T2–T5 trở lại thành điều kiện chặn công việc DEV.

Nguồn bất biến:

- [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx), SHA-256 92efaaec4efb66bfe0fa69e5de6a706476f93f58f808575982b9d95662438bd4.
- [Contract v1.0](UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md), SHA-256 9c62f229908f3c66f300455e33bdde6aef127f8e6edd99b286def0687ff9f2ef.
- Core API/DTO, permission seed V3, AuditAction và migration V1–V7 trong repository. Phiên bản source, file hash và kết quả kiểm tra ghi trong [evidence DEV](../testing/evidence/T1_01_CONTRACT_REVIEW.json).

## Cách đọc phiên bản này

Các mục A–J kế thừa nội dung v1.0. Nhãn PROPOSED/NOT TESTED trong phần kế thừa mô tả mức triển khai nghiệp vụ hoặc checkpoint nguồn, không phải kết quả bộ kiểm tra schema của v1.1. Mục K là diff kỹ thuật của v1.1 và được ưu tiên khi làm rõ kiểu dữ liệu/ánh xạ lưu trữ. [Báo cáo review T1-01](T1_01_TECHNICAL_REVIEW.md) phân biệt SPEC, IMPLEMENTED, DEV PASS, QA và NOT RUN.

Schema chuẩn có thể chạy kiểm tra ở [v1.1/schemas.json](v1.1/schemas.json); registry và danh mục operation ở [v1.1/manifest.json](v1.1/manifest.json); ví dụ giữ locator tại [v1.1/examples.json](v1.1/examples.json). Không có HTTP endpoint mới hoặc Java port mới được triển khai chỉ bởi việc xuất các file này. Version của pack 1.1 khác header opt-in activation 1.1; client legacy vẫn dùng body/status cũ. Các family có wire contract không đổi giữ version 1.0.

## A. Tóm tắt quyết định

|Blocker|Vấn đề hiện tại|Phương án duy nhất khuyến nghị|Lý do|Tác động / đánh đổi|Owner|Trạng thái|
|---|---|---|---|---|---|---|
|B01|Thiếu DTO/port; REST target chưa thống nhất|Shared Java DTO/ports trong monolith, REST UI theo C|Một owner cho mỗi nghiệp vụ|Cần adapters và consumer tests sau duyệt, giữ core cũ|T1 điều phối, T2–T5 owner|PROPOSED|
|B02|Tên quyền SRS khác catalog thật|Giữ 18 mã V3, thêm D; alias theo action, tách nhãn gộp|Không phá catalog hoặc mở rộng ADMIN/HR|Thêm seeds/capability guard và securityVersion invalidation|T1; T3 assignment, T5 school guard|PROPOSED|
|B03|Action wildcard/label khác enum|Exact registry E; tách domain events|Giữ lịch sử và phát đúng enum|Thêm metadata_json và action mới qua migration review|T1 và producers|PROPOSED|
|B04|Import thiếu ID/version và quy tắc lỗi|Một multipart preview gộp upload, DTO đầy đủ F|UI có keys tiếp tục an toàn|Thay tên target cũ theo compatibility mapping|T1 API, T5 UI, T2 port|PROPOSED|
|B05|Completion/counters mơ hồ|Terminal theo selected-row provisioning + durable outbox|Không treo job theo SMTP/activation|Thêm invariant, lease/fencing, resume theo committed effects|T1 job, T2 roster|PROPOSED|
|B06|Mail FAILED sau commit chưa phục hồi|Resend cho pending claim, rotate token dưới lock|Không import lại; một current token|Claim/encrypted outbox/quota; SMTP at-least-once|T1 token/mail, T5 UI|PROPOSED|

Phương án khác đã cân nhắc: B01 gọi REST giữa mọi bean tăng API trùng; B02 rename toàn bộ quyền phá compatibility; B03 dùng tên event làm action sai enum; B04 thêm upload roster thứ hai tăng trạng thái trung gian; B05 chờ email/activation khiến job treo; B06 import lại để gửi thư dễ lặp provisioning. Mỗi blocker chọn đúng một phương án trong bảng. Không đổi kiến trúc/công nghệ.

## B. Đặc tả bổ sung có thể hợp nhất vào SRS

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** Các mã sau là MUST đề xuất, contract/AC chi tiết được dẫn chiếu trong cùng tài liệu.

|Mã|Tiền điều kiện / yêu cầu|Hậu điều kiện / lỗi / Acceptance Criteria|
|---|---|---|
|BRP-B01-01|Giao điểm MUST dùng C/F, port cùng transaction manager; module không ghi repository của owner khác.|Given request mẫu, When đúng scope/version, Then đúng typed output; sai schema400; một nguồn canonical.|
|BRP-B01-02|ActorContext chỉ từ session/guard hoặc worker purpose đã đăng ký; không nhận role/securityVersion client.|Given foreign ID, When đổi URL/body, Then403/404 và zero effect; thiếu adapter phải DENY.|
|BRP-B02-01|Giữ18 permission V3; mapping/bổ sung theo D; HR/Mentor là assignment.|Global ADMIN không tự có business/PII; grant/revoke bump securityVersion cùng audit/history, session cũ401 request kế.|
|BRP-B03-01|Action/result/resource/metadata theo E; mutation+audit+outbox cùng TX.|Unknown/audit failure rollback500 an toàn; denial audit lỗi vẫn DENY; domain event không tự thành action.|
|BRP-B04-01|Upload+preview một multipart endpoint; confirm chỉ chọn VALID rows còn hạn.|File cấu trúc sai422; dòng sai vẫn201 preview; hết hạn409; foreign job404; preview chưa tạo user/roster/token.|
|BRP-B05-01|Mỗi row commit provisioning/T2 port/claim/token/outbox/effect/audit nguyên tử; user mới chỉ tạo khi accept.|Job chốt theo F, không chờ SMTP/activation; ALREADY_LINKED thành công; NEEDS_REVIEW không tự merge.|
|BRP-B05-02|Lease/fencing/checkpoint; retry/resume giữ effect key, kiểm authority trước mỗi row.|Given crash sau600 commit, When resume1000rows, Then chỉ400 còn lại; revoke làm FAILED, staff cùng school có thể resume và được audit.|
|BRP-B06-01|Durable private mail outbox, generation dưới claim lock trước khi user tồn tại; TTL48h/hash, không mật khẩu chung.|Given COMPLETED/mail FAILED, When resend hợp lệ, Then202 mail/token mới, không provisioning lại.|
|BRP-B06-02|Resend/accept serialized theo F, một current token/activation purpose.|Hai thứ tự commit có outcome F.5; không hai identity/live token; SMTP timeout có thể gây thư trùng cùng link.|

## C. Contract Pack MUST phiên bản đề xuất 1.0

### C.0. Quy ước schema, lỗi và mutation dùng chung

Schema mới là closed object. field:T bắt buộc/non-null; field:T|null bắt buộc, có thể null; field?:T được bỏ qua, nhưng không null nếu có. Array phải hiện diện; [] là rỗng. Không đồng nhất null/omitted/[] khi có declaration flag. Casing là tên wire chính thức của đề xuất.

- U=UUID string; V=JSON integer0..9007199254740991, DB BIGINT; I=ISO8601 UTC Instant; D=yyyy-MM-dd; H=lowercase SHA256 hex64. Email/MSSV không phải khóa ID.
- Text(a..b)=Unicode trim, giới hạn codepoints. Email=trim+lowercase Locale.ROOT, tối đa254 theo T1. Reason=[A-Z0-9_]{3,64}. Ref=Text(1..120), tham chiếu quyết định do actor đủ thẩm quyền xác nhận; client gửi Ref không tạo quyền.
- HTTPSUrl=URL tuyệt đối https, không userinfo; server không fetch. Role=STUDENT|ENTERPRISE|SCHOOL_STAFF|ADMIN. AccountStatus=PENDING_ACTIVATION|ACTIVE|SUSPENDED|DEACTIVATED. PermissionCode là exact code ở D. MutationAck={resourceId:U,version:V,changedAt:I}.
- PageQuery={page?:int[0..10000],size?:int[1..100]}; defaults page0,size25. Page&lt;T&gt;={content:T[],page:int,size:int,totalElements:long,totalPages:int}. Mặc định sort createdAt DESC,id ASC; rows sort rowNo ASC,id ASC. Sort khác chỉ khi operation công bố. Counters không âm; không làm tròn V qua JavaScript.
- ActorContext={actorId:U|null,actorKind:USER|SYSTEM,requestId:U,operationId:U,authenticatedMemberships:server-only,currentSecurityVersion:server-only}. Provider đọc lại authoritative state; SYSTEM có purpose/action/scope allowlist, không ADMIN ngầm. RequestIdFilter sinh UUID như source, browser không giả actor/trace.
- MutationContext={operationId:U,idempotencyKey:U,expectedVersion:V|null}. Create chưa có aggregate dùng null; update có version theo DTO. Public body không nhận ActorContext.
- REST mutation mới đòi Idempotency-Key:UUID. Scope gồm actor/org/operation. Hash canonical sort object keys, giữ array order trừ selectedRowIds/caseIds là set sort UUID. Hash file gồm SHA256 bytes và semantic resource/purpose. Password/token không nằm response cache/log.
- Cùng key/hash replay kết quả gốc trong24giờ sau current authentication/authorization; không effect/audit success lần hai. IN_PROGRESS409 REQUEST_IN_PROGRESS + Retry-After1; khác body409 IDEMPOTENCY_KEY_REUSED; cache hết nhưng effect còn409 IDEMPOTENCY_RESPONSE_EXPIRED, không rerun.
- Rollback không DONE giả; transient503 có thể retry cùng key. Durable effect sống đến workflow đóng và cleanup demo được phép. ID purge không tái dùng; không hứa retry vô hạn qua DB reset.
- Read-modify-write kiểm version/resource/state trong TX. Stale409 VERSION_CONFLICT; state sai409 STATE_CONFLICT. Core cũ giữ method/status/body, trừ opt-in adapter ghi rõ. Body idempotencyKey legacy chỉ nhận ở alias được công bố; header/body khác nhau400 IDEMPOTENCY_KEY_MISMATCH.

Error mới (mọi field bắt buộc, retryAfterSeconds nullable):

```json
{"errorCode":"VERSION_CONFLICT","error":"VERSION_CONFLICT","message":"Dữ liệu đã thay đổi, vui lòng tải lại.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

FieldError={field:Text(1..200),code:Reason,message:Text(1..300)}. Message không chứa raw exception, SQL, token hoặc PII của record khác. API mới error=errorCode. Core cũ giữ error/status hiện hữu; bổ sung errorCode/requestId/fieldErrors qua adapter.

|HTTP|errorCode exact / hoàn cảnh|
|---|---|
|400|INVALID_REQUEST, SCHEMA_VALIDATION, INVALID_PAGINATION, IDEMPOTENCY_KEY_MISMATCH|
|401|SESSION_EXPIRED, INVALID_CREDENTIALS theo auth|
|403|ACCESS_DENIED, CSRF_INVALID; không lộ tenant khác|
|404|RESOURCE_NOT_FOUND; không tồn tại hoặc che foreign scope|
|409|VERSION_CONFLICT, STATE_CONFLICT, REQUEST_IN_PROGRESS, IDEMPOTENCY_KEY_REUSED, IDEMPOTENCY_RESPONSE_EXPIRED và code domain của family|
|413|FILE_TOO_LARGE|
|416|INVALID_RANGE; sau kiểm quyền file|
|422|BUSINESS_VALIDATION, INVALID_SKILL, INVALID_CATALOG_REFERENCE, FILE_STRUCTURE_INVALID, UNSUPPORTED_FILE_FORMAT, IMPORT_ROW_LIMIT_EXCEEDED, INVALID_ROW_SELECTION, TOKEN_INVALID, TOKEN_EXPIRED, PASSWORD_POLICY_VIOLATION và code domain|
|429|RESEND_RATE_LIMITED; Retry-After khớp retryAfterSeconds|
|500|AUDIT_WRITE_FAILED, INTERNAL_ERROR; rollback, response an toàn|
|503|DEPENDENCY_UNAVAILABLE, SCANNER_UNAVAILABLE, TRANSIENT_FAILURE và code domain; không success trước commit|

Port trả Result&lt;T&gt;=Success&lt;T&gt; hoặc DomainError{errorCode:Reason,fieldErrors:FieldError[],retryAfterSeconds:int|null}, adapter map HTTP theo bảng; không giả HTTP giữa hai bean. DomainException làm rollback. Nhãn generic T71 map sang detail code của family; legacy error/status core được giữ, client mới dùng errorCode. Permission theo D, biết ID không đồng nghĩa có quyền.

Session: credentials include, HttpOnly cookie. Mọi browser mutation giữ CSRF, kể cả activation public; lấy /api/v1/auth/csrf và refresh sau login. Không localStorage session token, không trả session ID, không disable CSRF. Staging HTTPS dùng Secure cookie; demo loopback giữ profile hiện có.

**C-AC chung cho từng operation:** Given fixture và request mẫu của family; When đúng permission/version/state; Then đúng output/status. Missing field/sai enum400, business422, stale409, actor thiếu401/403, foreign resource404; không mutation/audit success/outbox. Error example áp dụng envelope C.0 đầy đủ với code/message tương ứng. H là GWT bổ sung, chưa thực thi.

### CP-AUTH1 — Danh tính và core API (T1 → T2, T3, T4, T5), version 1.0

**Đã có trong source:** method, request body và status của core API tại `docs/openapi.json`, `ApiDtos` và các controller. Các API này giữ profile legacy. **PROPOSED:** bổ sung các port đọc danh tính/thu hồi phiên và profile activation opt-in dưới đây; không thay route hoặc âm thầm đổi body của client cũ.

```text
PasswordInput = string không rỗng, tối đa 1000 ký tự; KHÔNG trim hoặc chuẩn hóa
NewPassword = PasswordInput, tối thiểu 12 Unicode codepoints và tối đa 72 UTF-8 bytes
TokenInput = string không rỗng, tối đa 128 ký tự; KHÔNG ghi log hoặc trả lại trong response
MembershipDto {role:Role,organizationId:U|null}
MeDto {userId:U,email:Email,displayName:Text(1..150),memberships:MembershipDto[]}
PermissionGrantDto {role:Role,organizationId:U|null,permissions:PermissionCode[]}
MessageDto {message:Text(1..300)}
OrgDto {id:U,name:Text(1..200),type:UNIVERSITY|ENTERPRISE,status:PENDING|VERIFIED|SUSPENDED,createdAt:I}
OrganizationReadPort.getIdentity(organizationId:U,actor:ActorContext):Result<OrgDto>
CurrentIdentityPort.current(actor:ActorContext):Result<MeDto>
AccountSecurityPort.invalidate(userIds:U[1..1000],reason:Reason,actor:ActorContext,mutation:MutationContext):Result<{securityVersions:{userId:U,version:V}[]}>
```

Mọi field trong các response trên là bắt buộc; chỉ field có `|null` mới nhận null. Danh sách membership/permission có thể rỗng. `AccountSecurityPort` chỉ nhận lời gọi từ workflow account, membership hoặc capability đã đăng ký; không cung cấp API để client tăng securityVersion của user bất kỳ. Danh sách userIds phải distinct, được khóa theo thứ tự UUID và được kiểm tra phạm vi của workflow gọi.

`CurrentIdentityPort` chỉ đọc principal hiện tại. `OrganizationReadPort` trả identity/status của organization mà actor hoặc trusted service có quan hệ/phạm vi hợp lệ; không xác nhận T5 school verification. Account ACTIVE vẫn được gọi `/auth/me` và đổi mật khẩu khi organization bị đình chỉ; các API nghiệp vụ kiểm org/assignment riêng. Danh tính trong MeDto không tự cấp quyền đọc hồ sơ sinh viên khác.

| REST giữ nguyên | Request → Response | Permission/guard và lỗi |
|---|---|---|
| GET /api/v1/auth/csrf | Không body → 200 {headerName:string,token:string} | Public; CSRF token do server sinh, không phải session token |
| POST /api/v1/auth/login | {email:Email,password:PasswordInput} → 200 MeDto | Public + CSRF; password quá 72 UTF-8 bytes hoặc credential/account không hợp lệ đều trả 401 INVALID_CREDENTIALS |
| GET /api/v1/auth/me; GET /api/v1/auth/me/permissions | Không body → 200 MeDto; 200 PermissionGrantDto[] | account:self:read; danh sách permission chỉ là UI hint, không thay scoped authorization |
| POST /api/v1/auth/logout | Không có field nghiệp vụ → 200 MessageDto | Session + CSRF; hủy server session/cookie theo cấu hình |
| POST /api/v1/auth/password/change | {currentPassword:PasswordInput,newPassword:NewPassword} → 200 MessageDto | account:self:password_change; xác minh mật khẩu hiện tại, tăng securityVersion và thu hồi phiên cũ |
| POST /api/v1/auth/password/forgot | {email:Email} → 200 MessageDto | Public + CSRF; cùng response cho email biết/không biết và lỗi gửi mail để chống enumeration |
| POST /api/v1/auth/password/reset | {token:TokenInput,newPassword:NewPassword} → 200 MessageDto | Public + CSRF; reset token một lần, hạn 30 phút; thu hồi các phiên cũ |
| POST /api/v1/admin/organizations | {name:Text(1..200),type:UNIVERSITY\|ENTERPRISE} → 200 OrgDto | organization:admin:create; server đặt status=PENDING |
| POST /api/v1/admin/invitations | {email:Email,role:Role,organizationId:U\|null} → 200 MessageDto | membership:admin:write; role/org hợp lệ; giữ hành vi legacy từ chối email đã có user |
| POST hoặc DELETE /api/v1/admin/memberships | {userId:U,role:Role,organizationId:U\|null} → 200 MessageDto | membership:admin:write; ADMIN có scope null, role khác đúng loại org; bảo vệ admin cuối cùng và tăng securityVersion |
| PATCH /api/v1/admin/users/{id}/status | {status:AccountStatus,reasonCode:Reason} → 200 MessageDto | account:admin:status; đúng transition, lý do, history/audit và guard admin cuối cùng |
| PATCH /api/v1/admin/users/{id}/revoke-sessions | Không có field nghiệp vụ → 200 MessageDto | account:admin:revoke; tăng securityVersion |
| GET /api/v1/admin/users; /api/v1/admin/organizations; /api/v1/admin/users/{id}/status-history; /api/v1/admin/audit-logs | page, size và các filter hiện hữu → 200 với schema Page legacy được dẫn chiếu trong core OpenAPI | Lần lượt account:admin:status, membership:admin:write, account:admin:status, audit:global:read; không serialize passwordHash hoặc securityVersion |

Profile legacy giữ nguyên HTTP status và error body hiện hữu, kể cả validation lỗi trả 400; việc ghi NewPassword trong bảng là điều kiện backend, không tự đổi status legacy thành 422. Không bắt client cũ bổ sung Idempotency-Key. Các port/profile REST mới tuân theo C.0. AccountStatus vẫn là PENDING_ACTIVATION/ACTIVE/SUSPENDED/DEACTIVATED; HR/Mentor không trở thành Role hoặc AccountStatus mới.

**Activation compatibility chọn duy nhất:** tiếp tục `POST /api/v1/auth/invitations/accept`.

- Không có header opt-in: giữ body `{token,displayName,password}`, response 200 MessageDto và status/error legacy.
- Client mới gửi `X-UTB-Contract-Version: 1.1`, CSRF và Idempotency-Key theo C.0. Body đóng là `{token:TokenInput,newPassword:NewPassword,displayName?:Text(1..150)}`. Không chấp nhận đồng thời `password` và `newPassword`; sai schema trả 400 SCHEMA_VALIDATION.
- Profile 1.1 trả 422 TOKEN_EXPIRED, 409 TOKEN_REVOKED, 409 TOKEN_ALREADY_USED hoặc 422 PASSWORD_POLICY_VIOLATION đúng tình huống. Token không hợp lệ trả 422 TOKEN_INVALID, không tiết lộ email của claim.
- Nếu roster request bỏ displayName, dùng fullName đã lưu trong staging. Nếu claim không có staged fullName, như Mentor mới, displayName bắt buộc; thiếu trả 422 BUSINESS_VALIDATION với fieldErrors của displayName. Tên hiển thị T1 không ghi đè academic fullName/profile T2.
- Invariant activation tại F áp dụng cho cả hai profile; cần reconcile/backfill token legacy trước khi bật luồng mới. Không auto-login, không trả password/token/hash trong response hoặc idempotency cache.

Ví dụ `GET /api/v1/auth/me` → 200:

```json
{"userId":"00000000-0000-4000-8000-000000000001","email":"student001@example.test","displayName":"Sinh viên Demo 001","memberships":[{"role":"STUDENT","organizationId":"00000000-0000-4000-8000-000000000011"}]}
```

Request activation 1.1 minh họa dùng placeholder, không phải credential seed hoặc mật khẩu dùng chung:

```json
{"token":"<SERVER_ISSUED_ONE_TIME_TOKEN>","newPassword":"<USER_CHOSEN_PASSWORD_MEETING_POLICY>"}
```

Success 200:

```json
{"message":"Account activated; please sign in"}
```

Error 422 của profile 1.1:

```json
{"errorCode":"TOKEN_EXPIRED","error":"TOKEN_EXPIRED","message":"Liên kết đã hết hạn. Vui lòng yêu cầu gửi lại.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-AUTH:** Given token đã dùng thành công; When client 1.1 accept lại bằng operation khác; Then 409 TOKEN_ALREADY_USED, không thêm user/membership. Given mật khẩu vừa đổi; When dùng session cũ; Then 401 SESSION_EXPIRED. Given client legacy gửi displayName/password; When gọi route cũ; Then không bị ép chuyển sang newPassword hoặc thêm header mới.

### CP-AUTHZ1 — School delegation và capability (T1 ↔ T3/T5), version 1.0

**PROPOSED:** T1 sở hữu school delegation; T3 sở hữu coordinator confirmation và HR/Mentor assignment. Các module gọi port, không ghi repository của owner khác. Giữ bốn global role; không tạo bảng roles/user_roles mới để biểu diễn HR hoặc Mentor.

```text
Scope {schoolOrgId:U,enterpriseOrgId:U|null,termId:U,groupId:U|null,caseId:U|null}
CapabilityDecision {allowed:boolean,reasonCode:Reason,assignmentId:U|null,assignmentVersion:V|null,validUntil:I|null}
SchoolDelegation {id:U,userId:U,schoolOrgId:U,termId:U,permissionCodes:PermissionCode[1..50],profileReadFields:ProfileField[],validFrom:I,validUntil:I,active:boolean,version:V,grantedBy:U,decisionRef:Ref}
BootstrapSchoolDelegation {userId:U,schoolOrgId:U,termId:U,permissionCodes:PermissionCode[1..50],profileReadFields:ProfileField[],validFrom:I,validUntil:I,decisionRef:Ref}
GrantSchoolDelegation {userId:U,permissionCodes:PermissionCode[1..50],profileReadFields:ProfileField[],validFrom:I,validUntil:I,decisionRef:Ref}
ConfirmCoordinator {userId:U,enterpriseOrgId:U,termId:U,validFrom:I,validUntil:I,decisionRef:Ref}
GrantHrAssignment {userId:U,scope:Scope,validFrom:I,validUntil:I,decisionRef:Ref}
GrantMentorAssignment {userId:U,validFrom:I,validUntil:I,decisionRef:Ref}
RevokeAssignment {expectedVersion:V,reasonCode:Reason}
RevocationAck {id:U,active:false,version:V}
ContextAssignment {id:U,kind:COORDINATOR|HR|MENTOR,userId:U,scope:Scope,active:boolean,validFrom:I,validUntil:I,version:V,grantedBy:U,decisionRef:Ref}
SchoolDelegationPort.check(actor:ActorContext,permission:PermissionCode,scope:Scope):CapabilityDecision
WorkspaceCapabilityPort.check(actor:ActorContext,action:PermissionCode,scope:Scope):CapabilityDecision
```

Field và nullability theo C.0; các array permission/field mask phải distinct. ProfileField dùng enum tại BC24. Scope của trường có enterpriseOrgId/groupId/caseId=null; coordinator có enterpriseOrgId nhưng groupId/caseId=null. HR/Mentor có enterpriseOrgId; nếu có groupId/caseId thì các resource phải cùng school, enterprise và term. Mentor assignment theo group phải có groupId; caseId chỉ thu hẹp thêm phạm vi.

Điều kiện thời gian là `validFrom ≤ serverNow < validUntil` khi sử dụng quyền; khi grant phải có validFrom &lt; validUntil và không vượt phạm vi/thời hạn grantor. Kind của assignment ánh xạ sang tập capability ở D, không suy từ Role ENTERPRISE. Permission đã biết vẫn bị từ chối nếu assignment không tồn tại, hết hạn, bị revoke hoặc không khớp scope. Kết quả deny có allowed=false và các assignmentId/assignmentVersion/validUntil=null; không tiết lộ assignment của tenant khác.

**Alias và field mask:** chuẩn hóa duy nhất các alias exact trong D trước khi tra catalog; không prefix-match và không chấp nhận combined permission rộng. Có `student_profile:school:read` thì profileReadFields phải khác []; không có quyền đó thì []. Field mask phải là subset của grantor và được kiểm qua ProfileAccessDecision tại BC24. Matching school detail muốn chiếu PII cũng phải có delegation đọc profile và mask tương ứng; quyền xem MatchRun không tự cấp toàn bộ profile. Bootstrap chỉ dùng decision/fixture demo đã xác định scope và mask, không tự cấp PII cho ADMIN.

Mỗi request kiểm authoritative assignment và parent confirmation còn hiệu lực; cache phải kiểm version. Grant/revoke gọi AccountSecurityPort trong cùng transaction với history/audit; T3 không ghi users repository. Khi root delegation/coordinator bị revoke, descendant capability mất hiệu lực ngay và các user bị ảnh hưởng được invalidation theo D; không tiếp tục cho phép chỉ vì row con còn active=true.

| REST PROPOSED | Input → output | Authority, validation và lỗi |
|---|---|---|
| POST /api/v1/admin/school-delegations | BootstrapSchoolDelegation → 201 SchoolDelegation | Global ADMIN chỉ bootstrap synthetic demo theo decision đã audit; subject có SCHOOL_STAFF membership đúng trường; không bootstrap PII cho chính mình |
| POST /api/v1/school/terms/{termId}/delegations | GrantSchoolDelegation → 201 SchoolDelegation | school:delegation:manage; school lấy từ TermReference và authority hiện hành; permission/mask/thời hạn không vượt grantor; cấm self escalation |
| DELETE /api/v1/school/delegations/{id} | RevokeAssignment → 200 RevocationAck | Authority đúng school/term; stale 409 VERSION_CONFLICT; ngoài scope 404 RESOURCE_NOT_FOUND |
| POST /api/v1/t3/coordinators/confirm | ConfirmCoordinator → 201 ContextAssignment | t3:coordinator:confirm; school delegate đúng term; subject có ENTERPRISE membership, org VERIFIED và T5 school verification APPROVED |
| POST /api/v1/t3/hr-assignments | GrantHrAssignment → 201 ContextAssignment | t3:hr:manage; coordinator đã được xác nhận đúng scope; không self grant; subject có ENTERPRISE membership phù hợp |
| POST /api/v1/t3/groups/{groupId}/mentors | GrantMentorAssignment → 201 ContextAssignment | t3:mentors:manage; HR được quản group; scope lấy từ group, membership ENTERPRISE active; nếu đi qua invitation phải có owner acceptance |
| DELETE /api/v1/t3/context-assignments/{id} | RevokeAssignment → 200 RevocationAck | Grantor authority tương ứng và version hiện hành; root revoke làm descendant mất quyền theo quy tắc trên |

Các mutation mới dùng CSRF, Idempotency-Key và MutationContext của C.0; create có expectedVersion=null ở context, revoke lấy expectedVersion từ body. school/enterprise trong request chỉ là resource selection, không là bằng chứng quyền. Khóa target user trước scoped assignment và kiểm current grantor version trong cùng transaction. HR không được cấp t3:grades:publish bằng assignment enterprise.

Ví dụ request grant HR của coordinator hợp lệ:

```json
{"userId":"00000000-0000-4000-8000-000000000002","scope":{"schoolOrgId":"00000000-0000-4000-8000-000000000011","enterpriseOrgId":"00000000-0000-4000-8000-000000000012","termId":"00000000-0000-4000-8000-000000000021","groupId":null,"caseId":null},"validFrom":"2026-11-03T00:00:00Z","validUntil":"2027-03-01T00:00:00Z","decisionRef":"DEMO-HR-001"}
```

Response 201 đầy đủ:

```json
{"id":"00000000-0000-4000-8000-000000000061","kind":"HR","userId":"00000000-0000-4000-8000-000000000002","scope":{"schoolOrgId":"00000000-0000-4000-8000-000000000011","enterpriseOrgId":"00000000-0000-4000-8000-000000000012","termId":"00000000-0000-4000-8000-000000000021","groupId":null,"caseId":null},"active":true,"validFrom":"2026-11-03T00:00:00Z","validUntil":"2027-03-01T00:00:00Z","version":1,"grantedBy":"00000000-0000-4000-8000-000000000003","decisionRef":"DEMO-HR-001"}
```

Response 403 khi actor chỉ là ENTERPRISE, không có coordinator capability:

```json
{"errorCode":"ACCESS_DENIED","error":"ACCESS_DENIED","message":"Bạn không có quyền cấp phân công trong phạm vi này.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-AUTHZ:** Given coordinator đúng scope và subject có membership hợp lệ; When grant HR; Then 201 với kind=HR, đúng school/enterprise/term và một audit success. Given HR không có Mentor assignment; When review submission; Then 403. Given revoke đã commit; When session cũ gửi request kế; Then 401, và sau đăng nhập lại vẫn 403 nếu chưa được grant lại. Given school delegation chỉ có mask {majorCode,skills}; When yêu cầu thêm email ngoài mask; Then không trả email.

### CP-FILE1 — File upload/metadata/content và authorizer (T1 ↔ T2/T3/T5), version 1.0

SRS §25.8.3 T90/R8 đã chốt endpoint, giới hạn và scan policy. **PROPOSED:** hoàn thiện response/binding/authorizer dưới đây. Chọn private filesystem volume qua `FILE_STORAGE_ROOT`, không đặt trong public directory; storage SPI cho phép thay adapter sau này. Scanner phải là adapter thực và fail closed; mock CLEAN không phải bằng chứng nghiệm thu.

```text
ResourceType = STUDENT_PROFILE|TASK_ASSIGNMENT|WEEKLY_REPORT|EVALUATION|ENTERPRISE_VERIFICATION|IMPORT_JOB|COHORT
FilePurpose = CV|EVIDENCE|SUBMISSION|WEEKLY_REPORT|VERIFICATION|ROSTER_IMPORT|PLACEMENT_IMPORT
FileContentType = application/pdf|image/png|image/jpeg|application/zip|text/csv|application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
ResourceRef {resourceType:ResourceType,resourceId:U}
FileDto {fileId:U,resource:ResourceRef,purpose:FilePurpose,originalName:Text(1..255),contentType:FileContentType,sizeBytes:long[1..20971520],sha256:H,scanStatus:PENDING|CLEAN|REJECTED,createdAt:I,deletedAt:I|null}
FileAuthorization {allowed:boolean,reasonCode:Reason,resourceVersion:V|null,consentVersion:V|null,shareVersion:V|null,validUntil:I|null}
FileResourceAuthorizer.authorize(actor:ActorContext,resource:ResourceRef,purpose:FilePurpose,action:UPLOAD|READ_METADATA|DOWNLOAD,at:I):FileAuthorization
FileReadPort.requireCleanBound(fileId:U,resource:ResourceRef,purpose:FilePurpose,actor:ActorContext):Result<FileDto>
```

Mọi response field là bắt buộc với nullability như schema. FileAuthorization được tính tại serverNow; caller không gửi thời điểm tùy ý để đọc bằng consent đã hết hạn. allowed=true phải có resourceVersion; denied có thể để các version=null để không tiết lộ resource ngoài scope. FileDto không có storageKey, absolute path hoặc owner email.

| Resource/purpose được hỗ trợ | Owner authorizer | Kiểu file và giới hạn |
|---|---|---|
| STUDENT_PROFILE với CV hoặc EVIDENCE | T2 | PDF/PNG/JPEG, tối đa 10 MiB; resourceId là studentUserId dùng để tra profile owner |
| TASK_ASSIGNMENT với SUBMISSION | T3 | PDF/ZIP, tối đa 20 MiB |
| WEEKLY_REPORT với WEEKLY_REPORT; EVALUATION với EVIDENCE | T3 | PDF/PNG/JPEG, tối đa 10 MiB |
| ENTERPRISE_VERIFICATION với VERIFICATION | T5 | PDF/PNG/JPEG, tối đa 10 MiB |
| IMPORT_JOB với ROSTER_IMPORT | T1 | CSV/XLSX, tối đa 5 MiB/5.000 data rows |
| COHORT với PLACEMENT_IMPORT | T3 | CSV/XLSX, tối đa 5 MiB/1.000 data rows |

Resource phải tồn tại. Draft report/evaluation được tạo trước khi upload; submission file gắn vào assignment đã có. Một file chỉ có một resource/purpose bất biến; port attach phải đối chiếu lại binding, CLEAN và quyền ghi hiện hành. GET lịch sử kiểm quyền đọc, không tự áp điều kiện ghi của resource đã đóng.

Roster preview vẫn dùng endpoint multipart riêng tại CP-IMPORT như SRS đã chốt và reuse private quarantine/scan service; không bắt client upload fileId trước khi có job. Cặp resource/purpose ngoài bảng trả 422 BUSINESS_VALIDATION. Các module gọi authorizer của owner qua composition root; adapter thiếu hoặc không biết ResourceType phải DENY.

| Operation | Request → Response | Permission, validation và lỗi |
|---|---|---|
| POST /api/v1/files | multipart {file,resourceType,resourceId,purpose} → 202 FileDto với scanStatus=PENDING | file:upload + resource WRITE; CSRF/Idempotency-Key; hash gồm binary và binding; vượt size 413 FILE_TOO_LARGE; MIME sai 422 BUSINESS_VALIDATION |
| GET /api/v1/files/{fileId}/metadata | Không body → 200 FileDto | file:download + resource READ; owner có thể xem scan status; ngoài scope 404 RESOURCE_NOT_FOUND |
| GET /api/v1/files/{fileId}/content | Optional Range, một byte range → 200 binary stream hoặc 206 | file:download + resource READ + CLEAN; 409 FILE_NOT_CLEAN; 416 INVALID_RANGE chỉ sau khi đã kiểm quyền |

MIME phải phù hợp nội dung, không chỉ extension. XLSX/ZIP bị giới hạn tổng giải nén 100 MiB và 1.000 entries; từ chối zip-slip/symlink, không chạy macro/formula hoặc external entity. CSV phải qua UTF-8/structure validation của import. Tên file không trở thành storage path. Scan có timeout 60 giây mỗi attempt; lần đầu và tối đa ba retry sau 5/30/120 giây; hết retry chuyển REJECTED. Binary không lưu trong MySQL và file quarantine không được publish.

Content response dùng content type đã xác minh, Content-Disposition attachment với tên an toàn, X-Content-Type-Options: nosniff và Cache-Control: private, no-store. Lỗi trả error JSON C.0; response thành công của content là binary, không JSON. Kiểm lại session/assignment/consent cho từng GET/Range; kiểm authorization trước khi trả scan status hoặc kích thước qua lỗi.

**Field mask:** CV/evidence phụ thuộc consent phải qua ProfileAccessDecision của BC24, với field `cv` hoặc `evidence` được cho phép; assignment không tự mở student profile. Owner/self là nhánh riêng. Không trả direct presigned storage URL cho PII. Khi trả metadata cho ENTERPRISE, originalName/Content-Disposition dùng tên trung tính từ fileId và extension đã xác minh, tránh lộ tên cá nhân qua filename. Revoke chặn request mới sau commit, không cam kết thu lại bytes đã tải hoặc response đã bắt đầu.

Ví dụ multipart: resourceType=TASK_ASSIGNMENT, resourceId=00000000-0000-4000-8000-000000000031, purpose=SUBMISSION, file=synthetic.pdf. Response 202:

```json
{"fileId":"00000000-0000-4000-8000-000000000041","resource":{"resourceType":"TASK_ASSIGNMENT","resourceId":"00000000-0000-4000-8000-000000000031"},"purpose":"SUBMISSION","originalName":"synthetic.pdf","contentType":"application/pdf","sizeBytes":1200,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","scanStatus":"PENDING","createdAt":"2026-11-03T03:00:00Z","deletedAt":null}
```

Error 409 khi owner tải file chưa CLEAN:

```json
{"errorCode":"FILE_NOT_CLEAN","error":"FILE_NOT_CLEAN","message":"Tệp chưa đủ điều kiện để tải xuống.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-FILE:** Given file CLEAN gắn assignment A; When sinh viên B thay fileId; Then 404 và không trả bytes. Given enterprise từng có quyền CV nhưng consent đã revoke; When GET/Range link cũ; Then 403/404 và không trả bytes. Given report đã SUBMITTED; When upload thay minh chứng của revision đó; Then 409 STATE_CONFLICT. Given scanner lỗi đến hết retry; Then file REJECTED và mọi content request hợp lệ về quyền vẫn bị chặn.

### CP-MENTOR1 — MentorInvitationPort và owner acceptance (T3 ↔ T1), version 1.0

**SRS hiện hành:** HR có scope được mời Mentor; người đã ACTIVE phải tự chấp thuận, người mới tự đặt mật khẩu, không cấp ADMIN. **PROPOSED:** các DTO, route và callback dưới đây hoàn thiện public contract; không gọi thẳng service legacy vốn chỉ cho global ADMIN.

```text
MentorInviteState = NEW_IDENTITY_PENDING|ACCOUNT_PENDING|MEMBERSHIP_PENDING|ACCEPTED|REVOKED|REVIEW_REQUIRED
MentorInviteRequest {email:Email,enterpriseOrgId:U,groupIds:U[1..100],termId:U,decisionRef:Ref}
MentorInviteDto {invitationId:U,version:V,state:MentorInviteState,enterpriseOrgId:U,groupIds:U[1..100],termId:U,expiresAt:I|null}
AcceptMentorMembership {expectedVersion:V}
RevokeMentorInvite {expectedVersion:V,reasonCode:Reason}
MentorInvitationPort.issue(input:MentorInviteRequest,actor:ActorContext,mutation:MutationContext):Result<MentorInviteDto>
MentorInvitationPort.acceptMembership(invitationId:U,expectedVersion:V,actor:ActorContext,mutation:MutationContext):Result<MentorInviteDto>
MentorInvitationPort.revoke(invitationId:U,expectedVersion:V,reason:Reason,actor:ActorContext,mutation:MutationContext):Result<MentorInviteDto>
MentorAssignmentPort.activateAccepted(invitationId:U,userId:U,expectedInviteVersion:V,actor:ActorContext,mutation:MutationContext):Result<ContextAssignment[]>
```

Các field là bắt buộc theo C.0. expiresAt=null chỉ khi REVIEW_REQUIRED và chưa phát hành invitation có thể accept; các state còn lại giữ hạn đã phát hành. groupIds phải distinct và mọi group cùng enterprise, school, term đã chọn. Backend đọc scope từ group/TermReference rồi đối chiếu HR assignment hiện hành; enterpriseOrgId trong body không tự chứng minh quyền.

T1 sở hữu identity và canonical onboarding invitation; T3 giữ pending assignment/reference và assignment đã kích hoạt. Assignment phát sinh khi accept có validFrom=acceptAt và validUntil=min(current HR parent.validUntil, confirmed coordinator.validUntil, 00:00 Asia/Ho_Chi_Minh của ngày sau term.endDate chuyển UTC); validUntil phải lớn hơn acceptAt, term phải ACTIVE và root authority còn hiệu lực, nếu không409 STATE_CONFLICT. Đây là công thức PROPOSED, không để từng team chọn thời hạn. Invitation/token pending vẫn có hạn48giờ; việc hết authority có thể chặn accept sớm hơn token expiry. Orchestrator tại composition root gọi public port của từng owner trong cùng transaction manager, không ghi repository chéo và không tạo invitation canonical thứ hai. Callback activateAccepted là Java port nội bộ, không có HTTP callback công khai. Port chỉ nhận trusted T1 acceptance workflow, kiểm invitation/version/subject và authority của HR đã mời; không cho browser tự gửi ActorContext hoặc tự kích hoạt assignment.

| REST PROPOSED | Request → Response | Permission, validation và lỗi |
|---|---|---|
| POST /api/v1/t3/mentor-invitations | MentorInviteRequest → 202 MentorInviteDto | t3:mentor:invite + HR quản được mọi group; scope khác 403/404; CSRF/Idempotency-Key |
| POST /api/v1/t3/mentor-invitations/{id}/accept | AcceptMentorMembership → 200 MentorInviteDto với state=ACCEPTED | Session của đúng existing user được mời; CSRF/key/version; sai chủ thể 404, stale 409 VERSION_CONFLICT |
| POST /api/v1/t3/mentor-invitations/{id}/revoke | RevokeMentorInvite → 200 MentorInviteDto với state=REVOKED | HR đúng scope; CSRF/key/version; không thu hồi account hoặc membership ngoài quyền quản lý |

Luồng duy nhất theo trạng thái account:

- Email đã có ACTIVE user: state=MEMBERSHIP_PENDING. Notification MVP là danh sách trong ứng dụng qua GET /api/v1/t3/mentor-invitations/me, trả200 Page&lt;MentorInviteDto&gt; (PageQuery); chỉ đúng existing user được mời, mọi role đã xác thực có thể đọc/accept invitation của mình. Không mở profile/group data ngoài DTO. Danh sách chứa route đăng nhập và invitation reference; không cần reset-password token hoặc một SMTP pipeline thứ hai. Đúng chủ tài khoản đăng nhập để accept; không bắt nhập mật khẩu mới hoặc token kích hoạt.
- Email mới: tạo pending identity claim, state=NEW_IDENTITY_PENDING, phát token 48 giờ. CP-AUTH1 activation tạo user ACTIVE, membership ENTERPRISE phù hợp và gọi callback T3 trong cùng transaction; không tạo membership STUDENT cho Mentor.
- PENDING_ACTIVATION khớp cùng Mentor claim: state=ACCOUNT_PENDING; chỉ reissue theo token/cooldown policy F, thu hồi token cũ. Pending claim thuộc roster/org/context khác không được takeover và trả REVIEW_REQUIRED.
- SUSPENDED/DEACTIVATED hoặc identity conflict: REVIEW_REQUIRED; không reset mật khẩu, không tự ACTIVE hoặc grant assignment.

Accept phải kiểm lại account, invitation, current HR authority và membership/org/group. Với user ACTIVE, T1 chỉ thêm hoặc tái sử dụng membership ENTERPRISE đúng org; không đổi credential. T3 kích hoạt từng assignment đúng pending scope, audit và AccountSecurityPort invalidation trong cùng transaction. Khi membership/capability đổi, session cũ có thể nhận 401 ở request kế và người dùng đăng nhập lại; response accept không phải auto-login.

Pending invitation có `now ≥ expiresAt` trả 409 INVITATION_EXPIRED dù cleanup chưa chạy; ACCEPTED không tự mất assignment vì deadline invitation đã qua. Revoke chặn activation/accept mới; nếu đã ACCEPTED thì thu hồi các assignment phát sinh từ invitation qua CP-AUTHZ, giữ các quyền/membership không thuộc thao tác này. Revoke pending invitation ghi ACCOUNT_INVITATION_REVOKED; revoke assignment đã có ghi CAPABILITY_REVOKED theo từng assignment. Revoke và accept serialize theo version/claim; replay cùng key không thêm user, membership, assignment hoặc audit success thứ hai.

Ví dụ request issue, header có CSRF và Idempotency-Key:

```json
{"email":"mentor@example.test","enterpriseOrgId":"00000000-0000-4000-8000-000000000012","groupIds":["00000000-0000-4000-8000-000000000022"],"termId":"00000000-0000-4000-8000-000000000021","decisionRef":"DEMO-MENTOR-001"}
```

Response 202 khi email đã có user ACTIVE:

```json
{"invitationId":"00000000-0000-4000-8000-000000000051","version":1,"state":"MEMBERSHIP_PENDING","enterpriseOrgId":"00000000-0000-4000-8000-000000000012","groupIds":["00000000-0000-4000-8000-000000000022"],"termId":"00000000-0000-4000-8000-000000000021","expiresAt":"2026-11-05T03:00:00Z"}
```

Request của đúng owner: POST /api/v1/t3/mentor-invitations/00000000-0000-4000-8000-000000000051/accept:

```json
{"expectedVersion":1}
```

Response 200 đầy đủ:

```json
{"invitationId":"00000000-0000-4000-8000-000000000051","version":2,"state":"ACCEPTED","enterpriseOrgId":"00000000-0000-4000-8000-000000000012","groupIds":["00000000-0000-4000-8000-000000000022"],"termId":"00000000-0000-4000-8000-000000000021","expiresAt":"2026-11-05T03:00:00Z"}
```

Response 403 cho actor không có scoped HR capability:

```json
{"errorCode":"ACCESS_DENIED","error":"ACCESS_DENIED","message":"Bạn không có quyền mời Mentor cho nhóm này.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

Response 409 khi owner accept pending invitation đã hết hạn:

```json
{"errorCode":"INVITATION_EXPIRED","error":"INVITATION_EXPIRED","message":"Lời mời Mentor đã hết hạn.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-MENTOR:** Given ACTIVE email và HR đúng group; When đúng owner accept rồi replay cùng key; Then một identity, một membership phù hợp và mỗi group tối đa một assignment active, không reset mật khẩu hoặc grant ADMIN. Given owner khác; When accept bằng invitationId đã biết; Then 404 và không có mutation. Given revoke commit trước accept; Then 409 STATE_CONFLICT/TOKEN_REVOKED theo entrypoint và không kích hoạt assignment. Given HR chỉ quản group A; When request thêm group B; Then không tạo invitation hoặc assignment B.

### CP-WS1 — Cohort, nhóm và tiếp nhận Placement (T3 → T2/T5 UI), version 1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** T3 sở hữu cohort, group và case; T5 cung cấp PlacementRead; T2 chỉ sở hữu giao diện sinh viên. Căn cứ: SRS T36/FR-01–13, T38, T39/R3. Scope của một cohort gồm enterpriseOrgId + schoolOrgId + termId.

```text
CohortDto {cohortId:U,enterpriseOrgId:U,schoolOrgId:U,termId:U,code:Text(1..64),name:Text(1..200),version:V}
GroupDto {groupId:U,cohortId:U,code:Text(1..64),caseIds:U[],mentorUserIds:U[],version:V}
CaseRef {caseId:U,placementId:U,studentUserId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U,state:WAITING_START|IN_PROGRESS|COMPLETED|TERMINATED|CANCELLED,version:V}
CohortQuery {termId?:U,page?:int[0..10000],size?:int[1..100]}
CreateCohort {termId:U,code:Text(1..64),name:Text(1..200)}
BatchCases {expectedVersion:V,placementIds:U[1..1000],previewId:U|null,fileHash:H|null}
BatchCaseResult {cohortId:U,version:V,items:BatchCaseItem[]}
BatchCaseItem {placementId:U,caseId:U|null,outcome:ADDED|ALREADY_PRESENT|INVALID,reasonCode:NONE|ALREADY_PRESENT|PLACEMENT_UNAVAILABLE}
PlacementPreview {previewId:U,cohortVersion:V,fileHash:H,expiresAt:I,rows:PlacementPreviewRow[]}
PlacementPreviewRow {rowNo:int>=1,placementId:U|null,status:VALID|INVALID,errorCode:INVALID_IDENTIFIER|AMBIGUOUS_STUDENT|PLACEMENT_UNAVAILABLE|null}
AutoGroupPreview {mode:PREVIEW,groupSize:int[1..1000],expectedCohortVersion:V}
GroupingPlan {planId:U,cohortVersion:V,expiresAt:I,groups:PlannedGroup[]}
PlannedGroup {ordinal:int>=1,caseIds:U[1..1000]}
ConfirmGrouping {mode:CONFIRM,planId:U,expectedCohortVersion:V}
AssignGroupMember {caseId:U,expectedCaseVersion:V,expectedGroupVersion:V}
MoveCase {caseId:U,toGroupId:U,expectedCaseVersion:V,expectedFromGroupVersion:V,expectedToGroupVersion:V,reasonCode:Reason}
GroupMembershipResult {case:CaseRef,toGroupId:U,fromGroupVersion:V|null,toGroupVersion:V}
Handover {caseId:U,newMentorUserId:U,expectedCaseVersion:V,reasonCode:Reason}
```

Hệ thống phải suy ra enterpriseOrgId từ HR context đã chọn và được xác minh; schoolOrgId lấy từ TermReference. Không tự chọn membership đầu tiên khi actor có nhiều scope. Mọi Placement/case trong cohort phải khớp cả ba ID. Mã cohort duy nhất theo enterprise/school/term.

Nhóm tự động sắp caseId tăng dần rồi chia liên tiếp theo groupSize. Plan có hạn 15 phút, giữ cohortVersion và chỉ chứa case hợp lệ trong scope. Confirm phải kiểm lại version, trạng thái Placement và tính duy nhất; không dùng danh sách preview đã lỗi thời. Mỗi case có tối đa một active group trong cohort. Move giữ nguyên task-group snapshot, submission và lịch sử. Mentor nhóm mới không được đọc/review task cũ nếu chưa có handover hợp lệ; file vẫn qua guard riêng.

| Method và endpoint đầy đủ | Request → response | Permission, tiền điều kiện và lỗi |
|---|---|---|
| GET /api/v1/t3/cohorts | CohortQuery →200 Page&lt;CohortDto&gt; | t3:workspace:read; lọc theo scope thực tế của actor; sort code ASC,cohortId ASC. |
| POST /api/v1/t3/cohorts | CreateCohort →201 CohortDto | t3:cohorts:manage và HR đúng enterprise/school/term; trùng mã409 DUPLICATE_COHORT. |
| POST /api/v1/t3/cohorts/{cohortId}/placements/preview | Multipart file .csv/.xlsx ≤5MiB,≤1000 dòng và expectedCohortVersion →200 PlacementPreview | HR đúng scope; chỉ ghi staging. File sai cấu trúc422 FILE_STRUCTURE_INVALID; quá dòng422 PLACEMENT_ROW_LIMIT_EXCEEDED; quá dung lượng413 FILE_TOO_LARGE. |
| POST /api/v1/t3/cohorts/{cohortId}/members:batch | BatchCases →200 BatchCaseResult | HR; Placement APPROVED/ACTIVE hợp lệ. Các dòng INVALID không nhận; valid rows và kết quả commit cùng transaction. Không tạo Placement mới. |
| POST /api/v1/t3/cohorts/{cohortId}/groups:auto | oneOf AutoGroupPreview / ConfirmGrouping →200 GroupingPlan /200 GroupDto[] | HR; Idempotency-Key bắt buộc. Confirm cùng plan không tạo nhóm lần hai; hết hạn409 GROUPING_PLAN_EXPIRED; không còn case để chia422 NO_GROUPABLE_CASES. |
| POST /api/v1/t3/groups/{groupId}/members | AssignGroupMember →201 GroupMembershipResult | HR; case chưa có active group trong cohort; hai request cạnh tranh chỉ một thành công, request còn lại409 GROUP_MEMBERSHIP_CONFLICT. |
| POST /api/v1/t3/groups/{fromGroupId}/members:move | MoveCase →200 GroupMembershipResult | HR có quyền tại cả nhóm nguồn/đích; path fromGroupId phải là nhóm hiện tại; version sai409 VERSION_CONFLICT. |
| POST /api/v1/t3/cases/{caseId}/mentor-handover | Handover →200 MutationAck | t3:mentors:manage; body caseId phải khớp path; mentor mới có active assignment đúng scope. Không cấp quyền sửa artifact cũ. |
| GET /api/v1/t3/mentors/me/groups | PageQuery →200 Page&lt;GroupDto&gt; | Mentor chỉ thấy nhóm có assignment hiện hành; sort code ASC,groupId ASC. |
| GET /api/v1/t3/cohorts/{cohortId}/groups | PageQuery →200 Page&lt;GroupDto&gt; | t3:workspace:read; sinh viên chỉ thấy nhóm của mình và caseIds của bản thân; không serialize danh sách sinh viên khác. |
| GET /api/v1/t3/cases/{caseId} | Không body →200 CaseRef | Owner student, assigned Mentor/HR hoặc delegated school. Muốn tên/MSSV phải dùng ProfileAccessDecision, không tự mở PII từ CaseRef. |

Placement preview nhận cột placementId; có thể nhận studentUserId thay thế nếu server resolve được đúng một Placement đang chiếm chỗ trong school/term của cohort. Dòng mơ hồ có status INVALID,errorCode=AMBIGUOUS_STUDENT, không chọn tùy tiện một Placement. Với confirm từ file, BatchCases.previewId và fileHash đều non-null, placementIds phải là tập con các dòng VALID của preview còn hạn và cohortVersion không đổi; sai409 PLACEMENT_PREVIEW_STALE. Với batch ID nhập trực tiếp, cả hai trường phải null và mọi ID được kiểm lại tại server. Preview không tạo account hoặc student roster.

Ví dụ POST /api/v1/t3/cohorts:
```json
{"termId":"00000000-0000-4000-8000-000000000021","code":"DEMO-2026-A","name":"Đợt thực tập Demo"}
```
Response 201:
```json
{"cohortId":"00000000-0000-4000-8000-000000000061","enterpriseOrgId":"00000000-0000-4000-8000-000000000012","schoolOrgId":"00000000-0000-4000-8000-000000000011","termId":"00000000-0000-4000-8000-000000000021","code":"DEMO-2026-A","name":"Đợt thực tập Demo","version":1}
```
Response 409:
```json
{"errorCode":"DUPLICATE_COHORT","error":"DUPLICATE_COHORT","message":"Mã đợt đã tồn tại trong phạm vi quản lý.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```
**AC-WS1:** Given 40 Placement hợp lệ và groupSize=10; When HR confirm plan còn hạn; Then tạo 4 nhóm, mỗi case có một active group và không tạo Placement mới. Given hai HR đồng thời move cùng case; When commit; Then chỉ một current group, request dùng version cũ trả409 và lịch sử còn nguyên. Trạng thái kiểm thử: NOT TESTED.

### CP-WS2 — Task, assignment, submission, review và comment (T3 → T2 UI), version 1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** T3 sở hữu toàn bộ nghiệp vụ dưới đây; T2 gọi API từ giao diện sinh viên. Quy tắc state giữ T39/R2, không cho client tự chuyển SUBMITTED/COMPLETED.

```text
TaskPriority=LOW|NORMAL|HIGH
AssignmentState=NEW|IN_PROGRESS|SUBMITTED|REVISION_REQUIRED|COMPLETED
CreateTask {groupId:U,title:Text(1..200),description:Text(1..10000),dueAt:I,priority:TaskPriority,caseIds:U[1..1000],lateAllowed?:boolean}
AssignmentDto {assignmentId:U,caseId:U,studentUserId:U,state:AssignmentState,version:V,currentSubmissionId:U|null}
TaskDto {taskId:U,groupId:U,title:Text(1..200),description:Text(1..10000),dueAt:I,priority:TaskPriority,lateAllowed:boolean,version:V,assignments:AssignmentDto[]}
TaskQuery {groupId?:U,caseId?:U,state?:AssignmentState,dueFrom?:I,dueUntil?:I,page?:int[0..10000],size?:int[1..100]}
StartAssignment {expectedVersion:V,state:IN_PROGRESS}
SubmissionInput {expectedAssignmentVersion:V,fileIds:U[0..20],url:HTTPSUrl|null,note:Text(0..2000)|null}
SubmissionDto {submissionId:U,assignmentId:U,versionNo:int>=1,fileIds:U[0..20],url:HTTPSUrl|null,note:Text(0..2000)|null,submittedAt:I,lateFlag:boolean}
ReviewInput {expectedAssignmentVersion:V,verdict:ACCEPT|REQUEST_REVISION,comment:Text(1..2000)}
ReviewDto {reviewId:U,submissionId:U,reviewedVersionNo:int>=1,verdict:ACCEPT|REQUEST_REVISION,comment:Text(1..2000),reviewerUserId:U,createdAt:I,assignmentVersion:V}
CommentInput {text:Text(1..2000)}
CommentDto {commentId:U,assignmentId:U,authorUserId:U,text:Text(1..2000),createdAt:I}
```

SubmissionInput chọn đúng một dạng: (1) url=null và fileIds chứa 1–20 UUID phân biệt; hoặc (2) url non-null và fileIds=[]. Không nhận cả file và URL, không nhận bộ nộp rỗng; vi phạm trả 422 SUBMISSION_ARTIFACT_INVALID. Client upload từng file qua CP-FILE với resourceType=TASK_ASSIGNMENT, resourceId=assignmentId, purpose=SUBMISSION rồi nộp một danh sách fileIds. Mọi file phải CLEAN và bind đúng assignment; chỉ một file chưa đạt cũng từ chối toàn bộ submission, không commit một phần. URL phải là HTTPSUrl theo C.0, không userinfo; backend không fetch URL.

Một Submission version đại diện cho một bộ nộp gồm danh sách file hoặc một URL. Giữ thứ tự fileIds được gửi; nộp lại tạo version mới, không sửa danh sách hoặc bytes của version cũ. Đây là contract PROPOSED thực hiện yêu cầu chọn nhiều tệp của US-T2-11; field fileId đơn trong target cũ được thay bằng fileIds, không nhận đồng thời hai schema. Source T1 File API vẫn upload một file mỗi request, không bị đổi contract. CreateTask.lateAllowed mặc định true khi bỏ qua; TaskDto luôn trả boolean thực tế. Khi serverNow=dueAt vẫn nhận đúng hạn; serverNow&gt;dueAt thì lateFlag=true, hoặc trả 422 SUBMISSION_LATE nếu lateAllowed=false.

Task/submission/comment mutation yêu cầu case IN_PROGRESS, Placement ACTIVE và effectiveAt đã tới; case terminal không được ghi mới. Sinh viên chỉ thấy assignment của mình trong TaskDto. Mentor phải có assignment/handover hiện hành đúng group/case; HR không mặc nhiên được review. SubmissionService commit bộ artifact versionN, toàn bộ liên kết file và assignment SUBMITTED trong cùng transaction, UNIQUE(assignmentId,versionNo), giữ nguyên v1 sau v2. ACCEPT chuyển COMPLETED; REQUEST_REVISION chuyển REVISION_REQUIRED và comment không rỗng.

| Method và endpoint đầy đủ | Request → response | Permission và lỗi |
|---|---|---|
| POST /api/v1/t3/tasks | CreateTask →201 TaskDto | internship:enterprise:task_write và Mentor của mọi caseIds trong group; duplicate caseIds422 BUSINESS_VALIDATION. |
| GET /api/v1/t3/tasks | TaskQuery →200 Page&lt;TaskDto&gt; | t3:workspace:read; student self, Mentor assigned, HR/delegated school đúng scope; sort dueAt ASC,taskId ASC. |
| GET /api/v1/t3/tasks/{taskId} | Không body →200 TaskDto | Cùng read guard; không serialize assignment thuộc sinh viên ngoài quyền. |
| PATCH /api/v1/t3/assignments/{assignmentId}/status | StartAssignment →200 AssignmentDto | internship:student:submission_write và owner; chỉ NEW/REVISION_REQUIRED→IN_PROGRESS; transition khác422 INVALID_ASSIGNMENT_TRANSITION. |
| POST /api/v1/t3/assignments/{assignmentId}/submissions | SubmissionInput →201 SubmissionDto | Owner, state IN_PROGRESS, expectedAssignmentVersion; file chưa CLEAN409 FILE_NOT_CLEAN; version sai409 VERSION_CONFLICT; late forbidden422 SUBMISSION_LATE. |
| GET /api/v1/t3/assignments/{assignmentId}/submissions | PageQuery →200 Page&lt;SubmissionDto&gt; | Viewer đúng assignment; sort versionNo DESC,submissionId ASC; quyền GET file kiểm riêng ở gateway. |
| POST /api/v1/t3/submissions/{submissionId}/reviews | ReviewInput →201 ReviewDto | Assigned Mentor; assignment SUBMITTED và submission hiện hành; review bản cũ409 STALE_SUBMISSION. |
| GET /api/v1/t3/assignments/{assignmentId}/comments | PageQuery →200 Page&lt;CommentDto&gt; | t3:workspace:read và owner/assigned viewer; sort createdAt ASC,commentId ASC. |
| POST /api/v1/t3/assignments/{assignmentId}/comments | CommentInput →201 CommentDto | Student self hoặc assigned Mentor có t3:comments:write; append-only, có Idempotency-Key; audit không chứa text tự do. |

Ví dụ POST /api/v1/t3/assignments/00000000-0000-4000-8000-000000000031/submissions:
```json
{"expectedAssignmentVersion":2,"fileIds":["00000000-0000-4000-8000-000000000041","00000000-0000-4000-8000-000000000042"],"url":null,"note":"Bộ nộp demo gồm báo cáo và mã nguồn"}
```
Response 201:
```json
{"submissionId":"00000000-0000-4000-8000-000000000071","assignmentId":"00000000-0000-4000-8000-000000000031","versionNo":1,"fileIds":["00000000-0000-4000-8000-000000000041","00000000-0000-4000-8000-000000000042"],"url":null,"note":"Bộ nộp demo gồm báo cáo và mã nguồn","submittedAt":"2026-11-03T03:00:00Z","lateFlag":false}
```
Response 409 khi mentor review v1 sau khi v2 đã là current submission:
```json
{"errorCode":"STALE_SUBMISSION","error":"STALE_SUBMISSION","message":"Bản nộp này không còn là phiên bản hiện hành.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```
**AC-WS2:** Given SV B biết assignmentId của SV A; When PATCH/POST; Then 404 và không effect. Given một bộ nộp có hai file CLEAN cùng assignment; When submit; Then 201 với một version chứa đúng hai file refs; nếu một file PENDING hoặc thuộc assignment khác thì toàn bộ request thất bại, không có version/liên kết/audit-success mới. Given 21 file hoặc UUID lặp; Then 422 SUBMISSION_ARTIFACT_INVALID. Given 20 request submit cùng key/payload; When commit; Then một submission version và một success audit. Given nộp lại sau yêu cầu sửa; Then v1 giữ nguyên file list và v2 có file list riêng. Given mentor assignment bị revoke; When review request tiếp theo; Then deny. Trạng thái kiểm thử: NOT TESTED.

### CP-WS3 — Báo cáo tuần (T3 → T2 UI), version 1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** T3 sở hữu report root, revisions và review. T2 chỉ gửi request/hiển thị kết quả. Căn cứ: T39/R6, T90/R12.

```text
ReportState=DRAFT|SUBMITTED|REVIEWED
ReportInput {caseId:U,weekStart:D,completedWork:Text(1..10000),difficulties:Text(0..5000),nextPlan:Text(1..5000),evidenceFileIds:U[0..20]}
ReportDto {reportId:U,caseId:U,weekStart:D,revisionNo:int>=1,version:V,state:ReportState,completedWork:Text(1..10000),difficulties:Text(0..5000),nextPlan:Text(1..5000),evidenceFileIds:U[0..20],dueAt:I,submittedAt:I|null,reviewedAt:I|null,lateFlag:boolean}
UpdateReport {expectedVersion:V,completedWork:Text(1..10000),difficulties:Text(0..5000),nextPlan:Text(1..5000),evidenceFileIds:U[0..20]}
SubmitReport {expectedVersion:V}
ReportReview {expectedVersion:V,comment:Text(1..2000)}
CreateReportRevision {expectedVersion:V,reasonCode:Reason}
ReportQuery {caseId:U,state?:ReportState,page?:int[0..10000],size?:int[1..100]}
ReportReadQuery {revisionNo?:int>=1}
```

Report root có UNIQUE(caseId,weekStart); reportId giữ ổn định qua revisions. Bản SUBMITTED/REVIEWED bất biến; sửa nội dung phải tạo revision DRAFT mới với reason và giữ phiên bản cũ. Chỉ một current draft/report root. File bind vào report root, không mất binding khi tạo revision.

Do File API yêu cầu resource có thật, lần tạo report root đầu tiên phải gửi evidenceFileIds=[]. Sau khi nhận reportId, client upload file bind vào report đó rồi PATCH draft để gắn fileIds; non-empty fileIds ở create đầu tiên trả422 FILE_BINDING_INVALID. Submit kiểm mọi file CLEAN, đúng owner và đúng report binding. DRAFT vẫn có thể giữ file PENDING để chờ scan.

weekStart phải là thứ Hai theo Asia/Ho_Chi_Minh. Mọi tuần giao ít nhất một ngày với [case.startDate,case.endDate] đều áp dụng, kể cả tuần đầu/cuối không trọn. dueAt là thứ Ba 23:59:59 của tuần kế tiếp, chuyển sang UTC để lưu. Nộp tại dueAt là đúng hạn; sau dueAt vẫn nhận và lateFlag=true. DRAFT chưa nộp có lateFlag=false; server tính flag khi submit, không tin client khai dueAt/lateFlag. REVIEWED revision cũ không bị sửa khi revision mới được nộp.

| Method và endpoint đầy đủ | Request → response | Permission, state và lỗi |
|---|---|---|
| POST /api/v1/t3/reports | ReportInput →201 ReportDto, state DRAFT | Student owner có internship:student:submission_write; case IN_PROGRESS; tuần hợp lệ; report root đã tồn tại409 REPORT_ALREADY_EXISTS. |
| PATCH /api/v1/t3/reports/{reportId} | UpdateReport →200 ReportDto | Owner, current DRAFT, expectedVersion; file sai binding422 FILE_BINDING_INVALID; version sai409 VERSION_CONFLICT. |
| POST /api/v1/t3/reports/{reportId}/submit | SubmitReport →200 ReportDto, state SUBMITTED | Owner, current DRAFT, case/Placement đang active; kiểm nội dung/files; Idempotency-Key bắt buộc. |
| POST /api/v1/t3/reports/{reportId}/reviews | ReportReview →200 ReportDto, state REVIEWED | Assigned Mentor có internship:enterprise:task_write; chỉ current SUBMITTED; lưu comment và reviewer từ server. |
| POST /api/v1/t3/reports/{reportId}/revisions | CreateReportRevision →201 ReportDto, revisionNo+1,state DRAFT | Student owner, case active; current SUBMITTED/REVIEWED; clone nội dung/file refs, reason+audit; không ghi đè bản đã review. |
| GET /api/v1/t3/reports/{reportId} | ReportReadQuery →200 ReportDto | t3:workspace:read đúng case; bỏ revisionNo lấy current; revision cụ thể vẫn cùng row/scope guard. |
| GET /api/v1/t3/reports | ReportQuery →200 Page&lt;ReportDto&gt; | Same guard; mỗi root trả current revision; sort weekStart DESC,reportId ASC. |

Ví dụ POST /api/v1/t3/reports; fixture case có startDate=2026-11-04 và endDate=2026-12-31:
```json
{
  "caseId":"00000000-0000-4000-8000-000000000081",
  "weekStart":"2026-11-02",
  "completedWork":"Hoàn thành API demo",
  "difficulties":"",
  "nextPlan":"Kiểm thử phân quyền",
  "evidenceFileIds":[]
}
```
Response 201:
```json
{
  "reportId":"00000000-0000-4000-8000-000000000082",
  "caseId":"00000000-0000-4000-8000-000000000081",
  "weekStart":"2026-11-02",
  "revisionNo":1,
  "version":1,
  "state":"DRAFT",
  "completedWork":"Hoàn thành API demo",
  "difficulties":"",
  "nextPlan":"Kiểm thử phân quyền",
  "evidenceFileIds":[],
  "dueAt":"2026-11-10T16:59:59Z",
  "submittedAt":null,
  "reviewedAt":null,
  "lateFlag":false
}
```
Response 422 khi weekStart không phải thứ Hai hoặc tuần không giao với khoảng case:
```json
{
  "errorCode":"REPORT_WEEK_INVALID",
  "error":"REPORT_WEEK_INVALID",
  "message":"Tuần báo cáo không hợp lệ đối với kỳ thực tập.",
  "requestId":"00000000-0000-4000-8000-000000000099",
  "fieldErrors":[{"field":"weekStart","code":"REPORT_WEEK_INVALID","message":"Chọn thứ Hai của một tuần có giao với thời gian thực tập."}],
  "retryAfterSeconds":null
}
```
**AC-WS3:** Given case bắt đầu thứ Tư 2026-11-04; When tạo báo cáo weekStart=2026-11-02; Then HTTP 201, dueAt=2026-11-10T16:59:59Z. Given submit tại dueAt/sau dueAt; Then lateFlag=false/true tương ứng. Given revision 1 đã REVIEWED và tạo revision 2; Then revision 1 còn nguyên; normal close khi current revision chưa REVIEWED trả 409 CASE_CLOSE_PRECONDITION_FAILED. Trạng thái kiểm thử: NOT TESTED.

### CP-WS4 — Rubric, evaluation, hiển thị điểm và đóng case (T3 ↔ T2/T5), version 1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** T3 sở hữu rubric/evaluation/case; T2 là UI student; T5 chỉ xử lý Placement outcome qua BC41. Căn cứ: T39/R4–5,R7; T90/R13.

```text
EvaluationState=DRAFT|SUBMITTED|PUBLISHED
Score {criterionCode:Text(1..64),score:decimal(3,2)[0..5],comment:Text(0..2000)|null,evidenceFileIds:U[0..20]}
RubricCriterion {criterionCode:Text(1..64),label:Text(1..200),weight:decimal(5,2)>0}
RubricTemplate {templateId:U,templateVersion:V,criteria:RubricCriterion[1..100]}
EvaluationInput {caseId:U,templateId:U,templateVersion:V,scores:Score[1..100]}
EvaluationDto {evaluationId:U,caseId:U,template:RubricTemplate,scores:Score[1..100],total100:decimal(5,2)[0..100],state:EvaluationState,revisionNo:int>=1,version:V,publishedAt:I|null,decisionRef:Ref|null}
UpdateEvaluation {expectedVersion:V,scores:Score[1..100]}
SubmitEvaluation {expectedVersion:V}
PublishEvaluation {expectedVersion:V,decisionRef:Ref}
CreateEvaluationRevision {expectedVersion:V,decisionRef:Ref,reasonCode:Reason}
SetVisibility {expectedVersion:V,enabled:boolean}
SchoolResultVisibilityDto {schoolOrgId:U,termId:U,enabled:boolean,version:V}
GradeVisibilityDto {enterpriseOrgId:U,termId:U,caseId:U|null,scope:ORGANIZATION_TERM|CASE,enabled:boolean,version:V}
StudentResultDto {caseId:U,visibility:VISIBLE|HIDDEN,evaluation:EvaluationDto|null,reasonCode:VISIBLE|NOT_AVAILABLE}
CloseCase {expectedVersion:V,decision:NORMAL_COMPLETION|EARLY_TERMINATION,decisionRef:Ref,reasonCode:Reason}
EvaluationQuery {termId:U,cohortId?:U,state?:EvaluationState,page?:int[0..10000],size?:int[1..100]}
GradeVisibilityQuery {termId:U,caseId?:U}
```

Mỗi rubric criterion có weight&gt;0 và tổng weight=100.00. Scores phải chứa đúng một mục cho mỗi criterion, không thiếu/trùng/thừa; total100=ROUND_HALF_UP(Σ(weight×score/5),2). Template immutable sau khi đã dùng. Thay đổi template không sửa evaluation cũ. Điểm sao doanh nghiệp không tham gia công thức.

Một case có một evaluation root; tạo root thứ hai trả409 EVALUATION_ALREADY_EXISTS. Khi tạo evaluation, evidenceFileIds của mọi score phải rỗng. Sau khi có evaluationId, mentor upload file với resourceType=EVALUATION, resourceId=evaluationId, purpose=EVIDENCE theo CP-FILE, giới hạn 10MiB và MIME allowlist tương ứng; sau đó PUT scores để gắn file refs. Submit yêu cầu file CLEAN. Minh chứng không bắt buộc: evidenceFileIds=[] vẫn hợp lệ. File refs chưa có binding hợp lệ trả422 FILE_BINDING_INVALID; audit chỉ ghi ID/version cho phép, không ghi nội dung minh chứng hoặc comment.

Chỉ DRAFT được PUT scores. SUBMITTED/PUBLISHED không sửa đè. School có quyền tạo revision DRAFT từ SUBMITTED/PUBLISHED với decisionRef/reason; mentor chấm/submit lại và trường publish lại. Bản PUBLISHED trước vẫn là published result cho đến khi revision mới PUBLISHED. Case terminal cấm ghi/revision; đây không phải quy trình công nhận tín chỉ.

| Method và endpoint đầy đủ | Request → response | Permission, state và lỗi |
|---|---|---|
| GET /api/v1/t3/rubric-templates | Query termId:U,PageQuery →200 Page&lt;RubricTemplate&gt; | t3:workspace:read đúng term/scope; fixture T3 có version cụ thể; sort templateId ASC. Không thêm yêu cầu UI template CRUD vào MVP. |
| POST /api/v1/t3/evaluations | EvaluationInput →201 EvaluationDto,DRAFT | internship:enterprise:evaluate và Mentor đúng case; case active; templateVersion sai409 VERSION_CONFLICT; rubric không hợp lệ422 RUBRIC_INVALID. |
| PUT /api/v1/t3/evaluations/{evaluationId} | UpdateEvaluation →200 EvaluationDto | Same Mentor guard, current DRAFT; scores khớp template; expectedVersion bắt buộc. |
| POST /api/v1/t3/evaluations/{evaluationId}/submit | SubmitEvaluation →200 EvaluationDto,SUBMITTED | Mentor có quyền; chỉ DRAFT; kiểm đủ scores/files; snapshot template không đổi. |
| POST /api/v1/t3/evaluations/{evaluationId}/publish | PublishEvaluation →200 EvaluationDto,PUBLISHED | t3:grades:publish và school delegation đúng case; chỉ SUBMITTED; HR/Mentor không tự publish. |
| POST /api/v1/t3/evaluations/{evaluationId}/revisions | CreateEvaluationRevision →201 EvaluationDto,DRAFT | School có t3:grades:publish; từ current SUBMITTED/PUBLISHED, case active; giữ revision và published pointer cũ. |
| GET /api/v1/t3/schools/{schoolOrgId}/terms/{termId}/result-visibility | Không body →200 SchoolResultVisibilityDto | t3:result_policy:manage và school delegation; thiếu record trả enabled=false,version0. |
| PUT /api/v1/t3/schools/{schoolOrgId}/terms/{termId}/result-visibility | SetVisibility →200 SchoolResultVisibilityDto | Cùng guard; expectedVersion0 cho lần tạo đầu; không cho HR sửa school policy. |
| GET /api/v1/t3/organizations/{enterpriseOrgId}/grade-visibility | GradeVisibilityQuery →200 GradeVisibilityDto | t3:grade_visibility:manage và HR đúng org/term hoặc case; bỏ caseId nghĩa policy ORGANIZATION_TERM. |
| PUT /api/v1/t3/organizations/{enterpriseOrgId}/grade-visibility | GradeVisibilityQuery + SetVisibility →200 GradeVisibilityDto | HR enterprise-wide mới viết policy ORGANIZATION_TERM; HR group chỉ viết CASE thuộc group được giao. |
| GET /api/v1/t3/me/results | Query caseId:U →200 StudentResultDto | Student owner; chỉ trả published evaluation khi school policy và effective HR visibility đều bật. |
| GET /api/v1/t3/school/evaluations | EvaluationQuery →200 Page&lt;EvaluationDto&gt; | t3:workspace:read và school delegation có grade purpose; sort caseId ASC,revisionNo DESC. |
| POST /api/v1/t3/cases/{caseId}/close | CloseCase →200 CaseRef | t3:cases:close và school delegation. NORMAL cần endDate đã tới, mọi task COMPLETED, current reports REVIEWED, current evaluation PUBLISHED; thiếu409 CASE_CLOSE_PRECONDITION_FAILED. EARLY cần school decision/reason; phát CaseClosed, không sửa bảng T5. |

Case policy HR nếu có thì ưu tiên; thiếu case policy dùng org/term policy, thiếu cả hai là false. School policy là điều kiện độc lập, không bị HR override. StudentResultDto HIDDEN luôn evaluation=null,reasonCode=NOT_AVAILABLE, không tiết lộ bản nháp hoặc chi tiết lý do policy của người không có quyền. T3 case đóng normal thành COMPLETED, early thành TERMINATED; T5 xử lý Placement theo BC41.

Ví dụ POST /api/v1/t3/evaluations; fixture template có QUALITY40% và PROCESS60%:
```json
{
  "caseId":"00000000-0000-4000-8000-000000000081",
  "templateId":"00000000-0000-4000-8000-000000000091",
  "templateVersion":1,
  "scores":[
    {"criterionCode":"QUALITY","score":5.00,"comment":null,"evidenceFileIds":[]},
    {"criterionCode":"PROCESS","score":3.00,"comment":null,"evidenceFileIds":[]}
  ]
}
```
Response 201:
```json
{
  "evaluationId":"00000000-0000-4000-8000-000000000093",
  "caseId":"00000000-0000-4000-8000-000000000081",
  "template":{
    "templateId":"00000000-0000-4000-8000-000000000091",
    "templateVersion":1,
    "criteria":[
      {"criterionCode":"QUALITY","label":"Chất lượng công việc","weight":40.00},
      {"criterionCode":"PROCESS","label":"Quy trình thực hiện","weight":60.00}
    ]
  },
  "scores":[
    {"criterionCode":"QUALITY","score":5.00,"comment":null,"evidenceFileIds":[]},
    {"criterionCode":"PROCESS","score":3.00,"comment":null,"evidenceFileIds":[]}
  ],
  "total100":76.00,"state":"DRAFT","revisionNo":1,"version":1,
  "publishedAt":null,"decisionRef":null
}
```
Response 422 khi thiếu criterion:
```json
{
  "errorCode":"RUBRIC_INVALID","error":"RUBRIC_INVALID",
  "message":"Phiếu đánh giá chưa có đủ tiêu chí của rubric.",
  "requestId":"00000000-0000-4000-8000-000000000099",
  "fieldErrors":[{"field":"scores","code":"MISSING_CRITERION","message":"Phải cung cấp đúng một điểm cho mỗi tiêu chí."}],
  "retryAfterSeconds":null
}
```
**AC-WS4:** Given weights 40/60 và scores 5/3; When tạo/submit evaluation; Then total100=76.00. Given PUBLISHED nhưng school policy=false; When student GET kết quả; Then HIDDEN/evaluation=null dù HR bật. Given cả hai policy=true; Then owner thấy đúng published revision; điểm sao doanh nghiệp không làm total100 thay đổi. Trạng thái kiểm thử: NOT TESTED.

### CP-WS5 — Dashboard, lịch, đánh giá doanh nghiệp và phối hợp (T3 → T2/T5 UI), version 1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** T3 sở hữu các view/record vận hành case; không sở hữu quyết định Cooperation hoặc school verification của T5. Các enum CoordinationIssue dưới đây là datatype đề xuất cho FR-26, không phải tuyên bố source đã có.

```text
AssignmentCount {state:AssignmentState,count:int>=0}
WorkspaceSummary {cohortCount:int>=0,caseCount:int>=0,groupCount:int>=0,mentorCount:int>=0,assignmentCounts:AssignmentCount[5],lateAssignmentCount:int>=0,pendingReviewCount:int>=0}
DashboardQuery {termId:U,cohortId?:U}
CalendarQuery {from:I,until:I,caseId?:U,page?:int[0..10000],size?:int[1..100]}
CalendarEvent {taskId:U,assignmentId:U|null,title:Text(1..200),dueAt:I,state:AssignmentState|null}
EnterpriseQualityCriterion {code:Text(1..64),label:Text(1..200)}
EnterpriseQualityTemplate {templateId:U,version:V,criteria:EnterpriseQualityCriterion[1..100]}
EnterpriseRating {criterionCode:Text(1..64),stars:int[1..5],comment:Text(0..2000)|null}
EnterpriseQualityInput {enterpriseOrgId:U,termId:U,templateId:U,templateVersion:V,ratings:EnterpriseRating[1..100],note:Text(0..2000)|null}
EnterpriseQualityDto {id:U,input:EnterpriseQualityInput,schoolOrgId:U,version:V,createdAt:I}
IssueCategory=DELAY|COMMUNICATION|OTHER
IssueState=OPEN|IN_PROGRESS|RESOLVED
CreateCoordinationIssue {caseId:U,category:IssueCategory,description:Text(1..5000),ownerUserId:U}
UpdateCoordinationIssue {expectedVersion:V,state:IN_PROGRESS|RESOLVED}
CoordinationIssue {issueId:U,caseId:U,category:IssueCategory,description:Text(1..5000),state:IssueState,ownerUserId:U,version:V,createdAt:I,updatedAt:I}
IssueQuery {caseId:U,state?:IssueState,page?:int[0..10000],size?:int[1..100]}
```

Dashboard chỉ đếm distinct cohort/case/group/mentor và assignment trong scope đã được xác minh. assignmentCounts có đúng năm state của AssignmentState, state không có bản ghi trả count0. lateAssignmentCount đếm assignment chưa COMPLETED có dueAt&lt;serverNow; pendingReviewCount đếm SUBMITTED. Đây là workspace đã có Placement hợp lệ, không phải aggregate tuyển dụng trước hợp tác; không dùng endpoint này để né protected release của T4.

Lịch lọc dueAt trong [from,until), from&lt;until và khoảng tối đa62 ngày; sort dueAt ASC,taskId ASC,assignmentId ASC. Student chỉ nhận event cho assignment bản thân với assignmentId/state non-null. Mentor/HR/school có thể nhận một task event đại diện cho task thuộc scope, khi đó assignmentId/state=null; không gộp nhiều state cá nhân thành một state giả.

Enterprise quality dùng template fixture T3 có version và criterion codes cụ thể. ratings phải chứa đúng mỗi criterion một lần, stars1..5; không tính vào điểm sinh viên và không thay kết quả KYC. Coordination issue là vấn đề vận hành case; OPEN→IN_PROGRESS→RESOLVED, RESOLVED terminal; không sửa Cooperation/Placement qua issue status.

| Method và endpoint đầy đủ | Request → response | Permission và lỗi |
|---|---|---|
| GET /api/v1/t3/hr/dashboard | DashboardQuery →200 WorkspaceSummary | t3:workspace:read và HR đúng enterprise/school/term; không đếm case ngoài HR scope. |
| GET /api/v1/t3/mentor/dashboard | DashboardQuery →200 WorkspaceSummary | t3:workspace:read và active Mentor assignment; chỉ nhóm/case được giao. |
| GET /api/v1/t3/school/dashboard | DashboardQuery →200 WorkspaceSummary | t3:workspace:read và school delegation; không cộng trường khác. |
| GET /api/v1/t3/calendar | CalendarQuery →200 Page&lt;CalendarEvent&gt; | t3:workspace:read, owner/assigned/delegated scope; khoảng sai422 CALENDAR_RANGE_INVALID. |
| GET /api/v1/t3/enterprise-quality/templates | Query termId:U,PageQuery →200 Page&lt;EnterpriseQualityTemplate&gt; | Delegated school đúng term; fixture read-only; sort templateId ASC. |
| POST /api/v1/t3/school/enterprise-feedback | EnterpriseQualityInput →201 EnterpriseQualityDto | t3:enterprise_quality:school:write; school có Placement/collaboration hợp lệ với enterprise; sai template/ratings422 ENTERPRISE_RUBRIC_INVALID. |
| POST /api/v1/t3/school/issues | CreateCoordinationIssue →201 CoordinationIssue,OPEN | t3:coordination:manage và school delegate; owner phải là school staff/HR/Mentor hợp lệ cho case; owner sai422 ISSUE_OWNER_INVALID. |
| PATCH /api/v1/t3/school/issues/{issueId} | UpdateCoordinationIssue →200 CoordinationIssue | t3:coordination:manage và delegated school hoặc HR đúng case; transition khác409 STATE_CONFLICT; expectedVersion bắt buộc. |
| GET /api/v1/t3/school/issues | IssueQuery →200 Page&lt;CoordinationIssue&gt; | t3:workspace:read và school/HR/Mentor đúng case; sort createdAt DESC,issueId ASC. |

Ví dụ POST /api/v1/t3/school/enterprise-feedback; fixture template version1 chỉ có criterion MENTOR_SUPPORT:
```json
{
  "enterpriseOrgId":"00000000-0000-4000-8000-000000000012",
  "termId":"00000000-0000-4000-8000-000000000021",
  "templateId":"00000000-0000-4000-8000-000000000092",
  "templateVersion":1,
  "ratings":[{"criterionCode":"MENTOR_SUPPORT","stars":4,"comment":null}],
  "note":null
}
```
Response 201:
```json
{
  "id":"00000000-0000-4000-8000-000000000094",
  "input":{
    "enterpriseOrgId":"00000000-0000-4000-8000-000000000012",
    "termId":"00000000-0000-4000-8000-000000000021",
    "templateId":"00000000-0000-4000-8000-000000000092",
    "templateVersion":1,
    "ratings":[{"criterionCode":"MENTOR_SUPPORT","stars":4,"comment":null}],
    "note":null
  },
  "schoolOrgId":"00000000-0000-4000-8000-000000000011",
  "version":1,"createdAt":"2026-11-03T03:00:00Z"
}
```
Response 404 khi actor trường B truy cập issue/collaboration chỉ thuộc trường A:
```json
{
  "errorCode":"RESOURCE_NOT_FOUND","error":"RESOURCE_NOT_FOUND",
  "message":"Không tìm thấy tài nguyên trong phạm vi được phép.",
  "requestId":"00000000-0000-4000-8000-000000000099",
  "fieldErrors":[],"retryAfterSeconds":null
}
```
**AC-WS5:** Given mentor được giao đúng hai nhóm; When đọc dashboard/calendar; Then chỉ có case/task thuộc hai nhóm đó. Given quality rating4 sao; When ghi feedback; Then evaluation.total100 của sinh viên không đổi. Given school B biết issueId của A; When GET/PATCH; Then404, không effect và không có success audit. Trạng thái kiểm thử: NOT TESTED.

### BC11 — Profile/self editor, immutable snapshot và completeness (T2↔T4/T5), version1.0

**Owner T2; owner CompletenessPolicy là T4.** Consumer gồm T4, T5 và giao diện sinh viên T2. Căn cứ SRS T22/R2–4, T73/R2, T90/R5–6. T2 không sở hữu lời mời thực tập hoặc Placement. Checkout chưa có backend T2; các schema dưới đây là **PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT**.

```text
Code=Text(1..64)
DateRange {startDate:D,endDate:D} // start<=end,inclusive
SkillValue {skillCode:Code,level:int[1..5],yearsPractice:decimal>=0,source:SELF_DECLARED|SCHOOL_VERIFIED}
CanonicalSkillInput {skillCode:Code,level:int[1..5],yearsPractice:decimal>=0,note:Text(0..1000)|null}
SelfSkillView {skillCode:Code,level:int[1..5],yearsPractice:decimal>=0,source:SELF_DECLARED|SCHOOL_VERIFIED,note:Text(0..1000)|null}
CustomSkillInput {customSkillId:U|null,name:Text(1..128),categoryCode:Code,level:int[1..5],yearsPractice:decimal>=0,note:Text(0..1000)|null}
CustomSkillView {customSkillId:U,name:Text(1..128),categoryCode:Code,level:int[1..5],yearsPractice:decimal>=0,note:Text(0..1000)|null}
SkillCriterion {skillCode:Code,minLevel:int[1..5]|null,minYearsPractice:decimal>=0|null}
Project {projectId:U|null,title:Text(1..200),description:Text(0..5000),role:Text(0..200),skillCodes:Code[],url:HTTPSUrl|null,evidenceFileIds:U[]}
Certificate {certificateId:U|null,title:Text(1..200),issuer:Text(1..200),issuedOn:D,expiresOn:D|null,fileId:U|null}
ProfileInput {expectedProfileVersion:V,termId:U,majorCode:Code|null,skillsDeclared:boolean,skills:CanonicalSkillInput[],customSkills:CustomSkillInput[],availabilityDeclared:boolean,availablePeriods:DateRange[],orientationCodes:Code[],subjectCodes:Code[],projects:Project[],certificates:Certificate[],cvFileId:U|null,phone:Text(8..20)|null}
SelfProfileData {termId:U,majorCode:Code|null,skillsDeclared:boolean,skills:SelfSkillView[],customSkills:CustomSkillView[],availabilityDeclared:boolean,availablePeriods:DateRange[],orientationCodes:Code[],subjectCodes:Code[],projects:Project[],certificates:Certificate[],cvFileId:U|null,phone:Text(8..20)|null}
ProfileSnapshot {profileSnapshotId:U,studentUserId:U,schoolOrgId:U,termId:U,majorCode:Code|null,skillsDeclared:boolean,skills:SkillValue[],availabilityDeclared:boolean,availablePeriods:DateRange[],profileVersion:V,relevantHash:H,completenessVersion:Code,academicCatalogVersion:Code,taxonomyVersion:Code,capturedAt:I}
SnapshotSet {datasetSnapshotId:U,schoolOrgId:U,termId:U,profileSnapshotVersion:H,academicCatalogVersion:Code,taxonomyVersion:Code,capturedAt:I,profileCount:int}
SnapshotPage {datasetSnapshotId:U,profileSnapshotVersion:H,items:ProfileSnapshot[],nextCursor:Text(1..512)|null}
DecisionProfileRef {studentUserId:U,profileSnapshotId:U,profileVersion:V,relevantHash:H}
CompletenessResult {policyVersion:demo-v1,complete:boolean,checks:{code:MAJOR_VALID|STUDENT_TERM_PRESENT|SKILLS_DECLARED|SKILLS_VALID|AVAILABILITY_DECLARED|AVAILABILITY_VALID,passed:boolean,reasonCode:OK|MAJOR_MISSING|MAJOR_INVALID|STUDENT_TERM_MISSING|SKILLS_NOT_DECLARED|SKILL_INVALID|AVAILABILITY_NOT_DECLARED|PERIOD_INVALID}[6]}
SelfProfileView {studentUserId:U,schoolOrgId:U,profileVersion:V,profile:SelfProfileData,completeness:CompletenessResult}
ProfileDecisionValidation {checkedCount:int[1..5000],validatedAt:I}
StudentProfileSnapshotPort.captureForMatching(actor:ActorContext,request:{termId:U,expectedAcademicCatalogVersion:Code,expectedTaxonomyVersion:Code},mutation:MutationContext):Result<SnapshotSet>
StudentProfileSnapshotPort.readPage(actor:ActorContext,request:{datasetSnapshotId:U,cursor:Text(1..512)|null,size:int[1..100]}):Result<SnapshotPage>
StudentProfileSnapshotPort.readSnapshot(actor:ActorContext,profileSnapshotId:U):Result<ProfileSnapshot>
StudentProfileSnapshotPort.assertCurrentForDecision(actor:ActorContext,request:{refs:DecisionProfileRef[1..5000]}):Result<ProfileDecisionValidation>
CompletenessPort.evaluate(actor:ActorContext,request:{profile:ProfileSnapshot,studentTermPresent:boolean,majorCodeValid:boolean}):Result<CompletenessResult>
```
GET /api/v1/students/me/profile?termId=U trả 200 SelfProfileView với student_profile:self:read. PUT /api/v1/students/me/profile nhận ProfileInput, trả 200 SelfProfileView với student_profile:self:write; áp dụng MutationContext, Idempotency-Key và CSRF theo C.0. Actor/owner lấy từ principal. expectedProfileVersion cũ trả 409 PROFILE_STALE. Dữ liệu kỹ năng do sinh viên sửa luôn có source=SELF_DECLARED; client không được tự gửi SCHOOL_VERIFIED. Mã ngành và lựa chọn học vụ mới phải thuộc catalog ACTIVE đúng school/term; fileId phải CLEAN, thuộc owner và đúng binding STUDENT_PROFILE. majorCode=null thể hiện chưa khai để completeness đánh giá, không phải đã được trường phê duyệt. Không tự chia sẻ tên, số điện thoại hoặc dữ liệu học vụ.

**Bao phủ US-T2-02/04/16 (T21/R3,R5,R17):** note lưu ghi chú của kỹ năng canonical. customSkills cho phép khai kỹ năng/kiến thức ngoài catalog kèm nhóm categoryCode hợp lệ; ID null khi thêm, server cấp UUID; sửa ID thuộc người khác trả 404. T2 sở hữu bản khai custom, không tạo canonical skill/alias ở T4. CT-01 strict chỉ nhận canonical skills, bỏ customSkills và note khỏi snapshot matching. Custom skill không thỏa Required/PREFERRED chỉ vì tên gần giống; muốn map canonical phải có quyết định mapping T4 được review và version lại, không tự thêm workflow mapping vào MUST MVP.

Khi majorCode thay đổi, PUT phải giữ các record đã khai: canonical skillCode, customSkillId, projectId, certificateId, file refs và lựa chọn học vụ trước đó. Omit dữ liệu cũ trong cùng request đổi ngành trả 422 MAJOR_CHANGE_WOULD_DROP_DATA, không xóa âm thầm. Record đã khai nhưng không thuộc catalog ngành mới vẫn được giữ và đọc; UI có thể đánh dấu ngoài ngành hiện tại. Chỉ các lựa chọn mới phải qua catalog mới. Nếu muốn xóa/sửa record chủ động, gửi request riêng với majorCode không đổi; thay đổi vẫn có version/history. Như vậy đổi ngành không làm mất kỹ năng, dự án hoặc minh chứng đã khai.

Completeness có đúng sáu check trong CompletenessResult và complete là phép AND của cả sáu. T2 tính studentTermPresent và majorCodeValid từ roster/catalog authoritative, không nhận hai boolean này từ browser; majorCodeValid=false khi majorCode null hoặc không ACTIVE trong catalog đang xét. Khi skillsDeclared=false, cả skills và customSkills phải []; khi availabilityDeclared=false, availablePeriods phải []. declared=true với [] là khai báo rỗng có chủ ý, không phải UNKNOWN. SKILLS_VALID kiểm canonical skills trong CT-01; custom skill hợp lệ được giữ trong hồ sơ nhưng không giả thành canonical code hoặc điều kiện thứ bảy. Không thêm CV/GPA làm predicate. Capture tạo snapshot bất biến, nhất quán, gồm các STUDENT có liên kết và membership active đúng school/term; không lọc theo schoolApproved hoặc completeness. profileCount chỉ dùng nội bộ, không serialize cho doanh nghiệp.

profileSnapshotVersion của tập là SHA-256 trên các ref (studentUserId,profileVersion,relevantHash) đã sắp theo studentUserId; khác version số của từng hồ sơ. Khi nominate/approve, T5 gọi port kiểm lại dữ liệu trong cùng transaction; T2 khóa profile theo studentUserId tăng dần đến commit. relevantHash thay đổi trả 409 PROFILE_STALE, không tạo proposal hoặc giữ quota. Nếu chỉ profileVersion đổi do phone/note/custom data không tham gia criteria nhưng relevantHash giữ nguyên, T2 tái xác minh dưới lock và chấp nhận; không bắt rerun hoặc sửa snapshot kết quả cũ. relevantHash chỉ gồm majorCode, canonical skills/level/years/source, term và availability đã canonicalize. Dataset của demoBatch do Ops lựa chọn và đóng băng; truy vấn doanh nghiệp không tự capture dataset mới. Capture không gọi CompletenessPort, tránh vòng phụ thuộc T2→T4→T2; GET profile có thể gọi pure policy T4 để dựng SelfProfileView.

Ví dụ lời gọi nội bộ StudentProfileSnapshotPort.captureForMatching; ActorContext và MutationContext do backend cấp, không nhận từ trình duyệt:

```json
{"termId":"44444444-4444-4444-8444-444444444444","expectedAcademicCatalogVersion":"school-demo-v1","expectedTaxonomyVersion":"skills-demo-v1"}
```

Success&lt;SnapshotSet&gt;:
```json
{"datasetSnapshotId":"cccccccc-cccc-4ccc-8ccc-cccccccccccc","schoolOrgId":"22222222-2222-4222-8222-222222222222","termId":"44444444-4444-4444-8444-444444444444","profileSnapshotVersion":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","academicCatalogVersion":"school-demo-v1","taxonomyVersion":"skills-demo-v1","capturedAt":"2026-11-03T08:00:00Z","profileCount":400}
```
Ví dụ lỗi DomainError; REST adapter ánh xạ HTTP 409:

```json
{"errorCode":"PROFILE_STALE","error":"PROFILE_STALE","message":"Hồ sơ đã thay đổi, cần đánh giá lại trước khi đề xuất.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-BC11:** Given MatchRun dùng profile version3; When sinh viên sửa dữ liệu ảnh hưởng criteria thành version4 rồi school nominate bằng ref version3; Then 409 PROFILE_STALE, không proposal/quota/outbox mới. Given major/term hợp lệ, skillsDeclared=true, availabilityDeclared=true và các mảng []; When đánh giá; Then đủ sáu điều kiện dữ liệu nhưng Required JAVA hoặc lịch thực tập vẫn NOT_MATCH. Given custom skill tên “Java nâng cao” chưa có mapping; Then không tự PASS Required JAVA. Given đổi ngành và omit project cũ; Then 422 MAJOR_CHANGE_WOULD_DROP_DATA, project không bị xóa.

### BC12 — Skill taxonomy và academic catalogs (T4/T2→T2/T5/T1), version1.0

```text
SkillCatalogItem {skillCode:Code,name:Text(1..128),categoryCode:Code,parentSkillCode:Code|null,status:ACTIVE|DEPRECATED,version:V}
SkillCatalogPage {taxonomyVersion:Code,skills:Page<SkillCatalogItem>}
SkillResolution {input:Text(1..128),normalizedInput:string,status:RESOLVED|UNKNOWN,skillCode:Code|null}
ResolveSkillsRequest {values:Text(1..128)[1..100]}
ResolveSkillsResponse {taxonomyVersion:Code,items:SkillResolution[]}
AcademicCatalogItem {code:Code,name:Text(1..128),majorCodes:Code[],status:ACTIVE|INACTIVE}
AcademicCatalogPage {schoolOrgId:U,termId:U,kind:MAJOR|SUBJECT|ORIENTATION,catalogVersion:Code,entries:Page<AcademicCatalogItem>}
SkillCatalogPort.list(actor:ActorContext,request:{q:Text(1..128)|null,includeDeprecated:boolean,page:int,size:int}):Result<SkillCatalogPage>
SkillCatalogPort.resolve(actor:ActorContext,request:ResolveSkillsRequest):Result<ResolveSkillsResponse>
AcademicCatalogPort.list(actor:ActorContext,request:{termId:U,kind:MAJOR|SUBJECT|ORIENTATION,expectedCatalogVersion:Code|null,page:int,size:int}):Result<AcademicCatalogPage>
```
GET /api/v1/taxonomy/skills nhận q, includeDeprecated=false, page và size; trả 200 SkillCatalogPage. POST /api/v1/taxonomy/skills/resolve nhận ResolveSkillsRequest, trả 200 ResolveSkillsResponse. Hai route yêu cầu session hợp lệ và taxonomy:skills:read. GET /api/v1/students/me/catalogs?termId=U&kind=MAJOR|SUBJECT|ORIENTATION&page=0&size=25 trả 200 AcademicCatalogPage, chỉ cho quan hệ student–school–term của principal. Import T1 sử dụng AcademicCatalogPort nội bộ; không gọi vòng qua HTTP. Scope ngoài quyền trả 404 RESOURCE_NOT_FOUND; version catalog đổi trả 409 CATALOG_CHANGED; code không hợp lệ trả 422 CATALOG_CODE_INVALID.

Resolve chuẩn hóa Unicode NFKC, trim, gộp khoảng trắng và lowercase Locale.ROOT rồi so exact alias. UNKNOWN không tự tạo skill và không suy diễn Spring Boot⇒JAVA. Response giữ thứ tự input để consumer đối chiếu từng phần tử.

Các route quản lý taxonomy MUST dùng taxonomy:skills:write và capability TAX được cấp đích danh; ADMIN không tự có quyền nghiệp vụ. Mutation nhận Idempotency-Key/CSRF; update/delete alias có expectedVersion. CategoryDto={categoryCode:Code,name:Text(1..128),parentCategoryCode:Code|null,status:ACTIVE|DEPRECATED,version:V}; SkillAliasDto={aliasId:U,skillCode:Code,alias:Text(1..128),version:V}.

| Route đầy đủ | Request → response |
|---|---|
| GET /api/v1/taxonomy/categories | page,size → 200 Page&lt;CategoryDto&gt; |
| POST /api/v1/taxonomy/categories | {categoryCode:Code,name:Text(1..128),parentCategoryCode:Code\|null} → 201 CategoryDto ACTIVE |
| PATCH /api/v1/taxonomy/categories/{code} | {expectedVersion:V,name:Text(1..128),status:ACTIVE\|DEPRECATED} → 200 CategoryDto |
| POST /api/v1/taxonomy/skills | {skillCode:Code,name:Text(1..128),categoryCode:Code,parentSkillCode:Code\|null} → 201 SkillCatalogItem ACTIVE |
| PATCH /api/v1/taxonomy/skills/{code} | {expectedVersion:V,name:Text(1..128),categoryCode:Code,parentSkillCode:Code\|null,status:ACTIVE\|DEPRECATED} → 200 SkillCatalogItem |
| POST /api/v1/taxonomy/skills/{code}/aliases | {alias:Text(1..128),expectedVersion:V} → 201 SkillAliasDto |
| DELETE /api/v1/taxonomy/skills/{code}/aliases/{aliasId} | {expectedVersion:V} → 200 MutationAck |

Alias normalized phải unique; parent phải tồn tại và không tạo chu trình, nếu vi phạm trả 409 TAXONOMY_CONFLICT. Deprecate giữ lịch sử/snapshot, không hard-delete skill đã được tham chiếu. Mỗi thay đổi bump version và audit. Academic catalog là fixture versioned do T2 sở hữu; không bổ sung UI CRUD học vụ ngoài phạm vi MUST đã chốt.

Ví dụ request resolve:
```json
{"values":[" JAVA Core ","Spring Boot","skill-khong-co"]}
```
Response 200:
```json
{"taxonomyVersion":"skills-demo-v1","items":[{"input":" JAVA Core ","normalizedInput":"java core","status":"RESOLVED","skillCode":"JAVA"},{"input":"Spring Boot","normalizedInput":"spring boot","status":"RESOLVED","skillCode":"SPRING_BOOT"},{"input":"skill-khong-co","normalizedInput":"skill-khong-co","status":"UNKNOWN","skillCode":null}]}
```
Ví dụ lỗi khi thêm alias đã thuộc skill khác, HTTP 409:

```json
{"errorCode":"TAXONOMY_CONFLICT","error":"TAXONOMY_CONFLICT","message":"Alias đã được sử dụng bởi kỹ năng khác.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[{"field":"alias","code":"ALIAS_ALREADY_EXISTS","message":"Alias phải duy nhất sau chuẩn hóa."}],"retryAfterSeconds":null}
```

**AC-BC12:** Given school delegate không có TAX capability; When sửa taxonomy global; Then 403 ACCESS_DENIED. Given alias không biết; When resolve; Then UNKNOWN và không tạo Required skill ngầm.

### BC21 — TermReference và Eligibility (T5/T2→T1/T3/T4/T5), version1.0

```text
TermReference {termId:U,termCode:Code,schoolOrgId:U,startDate:D,endDate:D,status:DRAFT|ACTIVE|CLOSED,version:V}
StudentTermEligibility {studentUserId:U,schoolOrgId:U,termId:U,studentCode:Text(1..32),majorCode:Code,schoolApproved:boolean,version:V,approvedByUserId:U|null,approvedAt:I|null,decisionReasonCode:Reason|null}
EligibilityDecision {studentUserId:U,termId:U,schoolApproved:boolean,reasonCode:Reason,decisionRef:Ref,expectedVersion:V}
TermReadPort.get(actor:ActorContext,termId:U):Result<TermReference>
StudentEligibilityPort.read(actor:ActorContext,request:{studentUserId:U,termId:U}):Result<StudentTermEligibility>
StudentEligibilityPort.decide(actor:ActorContext,request:EligibilityDecision,mutation:MutationContext):Result<StudentTermEligibility>
StudentEligibilityPort.assertEligibleForPlacement(actor:ActorContext,request:{studentUserId:U,termId:U,expectedVersion:V}):Result<StudentTermEligibility>
```
T5 sở hữu TermReference; T2 sở hữu StudentTermEligibility. Term ACTIVE là reference được phép dùng trong demo, không phải Placement đã bắt đầu. GET /api/v1/terms?schoolOrgId=U&page=0&size=25 trả 200 Page&lt;TermReference&gt;; schoolOrgId chỉ là filter trong scope đã xác thực của actor hoặc trusted service. POST /api/v1/school/student-eligibilities/decisions nhận EligibilityDecision, trả 200 StudentTermEligibility; yêu cầu student:eligibility:school:approve và delegation đúng school/term. GET /api/v1/students/me/eligibility?termId=U chỉ trả dữ liệu của principal; T5 đọc qua port nội bộ, không gọi HTTP vòng lại.

Khi schoolApproved=true, server điền approvedByUserId/approvedAt; khi thu hồi thành false, hai field hiện hành này là null và lịch sử vẫn giữ đầy đủ actor/thời gian/lý do của quyết định. Không suy approval từ MATCH, completeness hoặc import. assertEligibleForPlacement khóa record authoritative đến commit của T5; version đổi trả 409 ELIGIBILITY_CHANGED, chưa duyệt trả 422 SCHOOL_APPROVAL_REQUIRED, trường/kỳ không phù hợp trả 422 STUDENT_TERM_MISMATCH. Term DRAFT/CLOSED không được approve Placement, trả 422 TERM_INVALID. Truy cập user ngoài scope bị chặn 404 trước khi trả các lỗi nghiệp vụ này. Thu hồi schoolApproved không tự hủy Placement đã có.

Ví dụ request của StudentEligibilityPort.assertEligibleForPlacement:

```json
{"studentUserId":"11111111-1111-4111-8111-111111111111","termId":"44444444-4444-4444-8444-444444444444","expectedVersion":3}
```

Success&lt;StudentTermEligibility&gt;:

```json
{"studentUserId":"11111111-1111-4111-8111-111111111111","schoolOrgId":"22222222-2222-4222-8222-222222222222","termId":"44444444-4444-4444-8444-444444444444","studentCode":"000001","majorCode":"SE","schoolApproved":true,"version":3,"approvedByUserId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","approvedAt":"2026-11-03T08:00:00Z","decisionReasonCode":"DEMO_ELIGIBLE"}
```

Lỗi DomainError được adapter ánh xạ HTTP 422:

```json
{"errorCode":"SCHOOL_APPROVAL_REQUIRED","error":"SCHOOL_APPROVAL_REQUIRED","message":"Sinh viên chưa được trường xác nhận đủ điều kiện trong kỳ này.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-BC21:** Given hồ sơ complete và kết quả MATCH nhưng schoolApproved=false; When T5 tạo hoặc approve Placement; Then không Placement/outbox mới và trả SCHOOL_APPROVAL_REQUIRED.

### BC22 — Opportunity/Preference (T5/T2), version1.0

```text
OpportunityView {opportunityId:U,cooperationId:U,demandId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U,enterpriseName:string,title:string,majorCodes:Code[],dateRange:DateRange,benefits:string|null,location:string|null,publishedAt:I,closedAt:I,version:V}
PreferenceView {preferenceId:U,studentUserId:U,schoolOrgId:U,termId:U,opportunityId:U,intentVersion:int>=1,version:V,status:SUBMITTED|WITHDRAWN|REVIEWED,message:Text(0..1000)|null,createdAt:I,withdrawnAt:I|null,reviewedAt:I|null,reviewedByUserId:U|null}
SubmitPreference {action:SUBMIT,opportunityId:U,message:Text(0..1000)|null,expectedIntentVersion:int>=0}
WithdrawPreference {action:WITHDRAW,preferenceId:U,expectedVersion:V,reasonCode:Reason}
OpportunityPort.list(actor:ActorContext,request:{termId:U,q:Text(1..150)|null,majorCode:Code|null,enterpriseOrgId:U|null,page:int,size:int}):Result<Page<OpportunityView>>
OpportunityPort.publish(actor:ActorContext,request:{cooperationId:U,publishedAt:I,closedAt:I,expectedVersion:V},mutation:MutationContext):Result<OpportunityView>
PreferencePort.submit(actor:ActorContext,request:SubmitPreference,mutation:MutationContext):Result<PreferenceView>
PreferencePort.withdraw(actor:ActorContext,request:WithdrawPreference,mutation:MutationContext):Result<PreferenceView>
PreferencePort.review(actor:ActorContext,preferenceId:U,request:{expectedVersion:V,reasonCode:Reason},mutation:MutationContext):Result<PreferenceView>
```
GET /api/v1/opportunities nhận các filter của OpportunityPort.list và trả 200 Page&lt;OpportunityView&gt;; GET /api/v1/opportunities/{id} trả 200 OpportunityView. T5 là owner duy nhất của projection, opportunityId=cooperationId. Chỉ hiển thị Cooperation APPROVED, đúng school/student-term và publishedAt≤serverNow&lt;closedAt. PUT /api/v1/school/cooperations/{id}/publication nhận {publishedAt:I,closedAt:I,expectedVersion:V}, trả 200 OpportunityView; yêu cầu opportunity:school:publish, delegation school/term và closedAt&gt;publishedAt. Không tạo Demand thứ hai.

T2 sở hữu GET /api/v1/students/me/preferences → 200 Page&lt;PreferenceView&gt; và POST cùng route nhận discriminated union SubmitPreference hoặc WithdrawPreference → 201 khi submit, 200 khi withdraw. Principal là student owner; yêu cầu student:preference:self:manage. POST /api/v1/school/preferences/{id}/review nhận {expectedVersion:V,reasonCode:Reason} → 200 PreferenceView, yêu cầu student:preference:school:review và delegation đúng school/term. UI T5 gọi route do T2 sở hữu; không tạo bảng preference canonical trong T5.

Mỗi student/opportunity/term chỉ có một current intent. SUBMITTED→WITHDRAWN do student trước review; SUBMITTED→REVIEWED do cán bộ trường. Intent đã REVIEWED là terminal; submit thay đổi tạo intentVersion mới và giữ lịch sử cũ. expectedIntentVersion=0 cho lần đầu, bằng current intentVersion cho bản tiếp theo; stale trả 409 VERSION_CONFLICT. Opportunity đã đóng trả 409 OPPORTUNITY_CLOSED; rút sau review trả 409 PREFERENCE_REVIEWED. Các thao tác này không tự đổi invitation, consent hoặc Placement.

Ví dụ POST /api/v1/students/me/preferences:

```json
{"action":"SUBMIT","opportunityId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","message":"Đăng ký vị trí demo","expectedIntentVersion":0}
```

Response 201:

```json
{"preferenceId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","studentUserId":"11111111-1111-4111-8111-111111111111","schoolOrgId":"22222222-2222-4222-8222-222222222222","termId":"44444444-4444-4444-8444-444444444444","opportunityId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","intentVersion":1,"version":1,"status":"SUBMITTED","message":"Đăng ký vị trí demo","createdAt":"2026-11-03T08:00:00Z","withdrawnAt":null,"reviewedAt":null,"reviewedByUserId":null}
```

Ví dụ lỗi withdraw sau review, HTTP 409:

```json
{"errorCode":"PREFERENCE_REVIEWED","error":"PREFERENCE_REVIEWED","message":"Nguyện vọng đã được xét; cần gửi phiên bản nguyện vọng mới để thay đổi.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-BC22:** Given intent đã REVIEWED; When student withdraw; Then 409 PREFERENCE_REVIEWED và không thay đổi Placement. School B không review intent của school A.

### BC23 — Internship Invitation (T5 canonical → UI T2), version 1.0

```text
InvitationState=PENDING|ACCEPTED|DECLINED|EXPIRED|WITHDRAWN
InternshipInvitation {invitationId:U,proposalItemId:U,placementCandidateId:U,studentUserId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U,opportunityId:U,title:string,enterpriseName:string,state:PENDING|ACCEPTED|DECLINED|EXPIRED|WITHDRAWN,expiresAt:I,decidedAt:I|null,decisionReasonCode:Reason|null,version:V,placementId:U|null}
InvitationDecision {decision:ACCEPT|DECLINE,expectedVersion:V,reasonCode:Reason|null,reasonText:Text(0..1000)|null}
InvitationWithdrawal {expectedVersion:V,reasonCode:Reason,reasonText:Text(0..1000)|null}
InternshipInvitationPort.listMine(actor:ActorContext,request:{state:InvitationState|null,termId:U|null,page:int,size:int}):Result<Page<InternshipInvitation>>
InternshipInvitationPort.readMine(actor:ActorContext,invitationId:U):Result<InternshipInvitation>
InternshipInvitationPort.decide(actor:ActorContext,invitationId:U,request:InvitationDecision,mutation:MutationContext):Result<InternshipInvitation>
InternshipInvitationPort.withdraw(actor:ActorContext,invitationId:U,request:InvitationWithdrawal,mutation:MutationContext):Result<InternshipInvitation>
```
T5 sở hữu GET /api/v1/invitations/me → 200 Page&lt;InternshipInvitation&gt;, GET /api/v1/invitations/{id} → 200 InternshipInvitation, POST /api/v1/invitations/{id}/decision nhận InvitationDecision và POST /api/v1/invitations/{id}/withdraw nhận InvitationWithdrawal → 200 InternshipInvitation. Student owner lấy từ principal, không từ body; mutation dùng key/CSRF. placementCandidateId là alias của proposalItemId, không phải entity danh tính mới. T2 chỉ sở hữu UI/intent reference, không lưu canonical invitation decision.

Chỉ PENDING nhận ACCEPT/DECLINE; nếu serverNow≥expiresAt thì trả 409 INVITATION_EXPIRED dù expiry worker chưa chạy. DECLINE yêu cầu reasonCode; ACCEPT dùng reasonCode/reasonText=null. WITHDRAW chỉ từ ACCEPTED trước Placement, cần reason/version; trạng thái khác trả 409 INVITATION_STATE_CONFLICT, đã có Placement trả 409 PLACEMENT_ALREADY_EXISTS. ACCEPTED không tự hết hiệu lực theo deadline phản hồi cũ; reservation vẫn chịu TTL riêng theo SRS. ACCEPT không tạo consent/share. TTL lời mời mặc định 7 ngày, tối đa 30 ngày và không vượt hạn tuyển/kỳ; invitation/item/quota được khóa trong cùng transaction.

Ví dụ POST /api/v1/invitations/77777777-7777-4777-8777-777777777777/decision:

```json
{"decision":"ACCEPT","expectedVersion":2,"reasonCode":null,"reasonText":null}
```

Response 200:

```json
{"invitationId":"77777777-7777-4777-8777-777777777777","proposalItemId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","placementCandidateId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","studentUserId":"11111111-1111-4111-8111-111111111111","schoolOrgId":"22222222-2222-4222-8222-222222222222","enterpriseOrgId":"33333333-3333-4333-8333-333333333333","termId":"44444444-4444-4444-8444-444444444444","opportunityId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","title":"Thực tập Java demo","enterpriseName":"Doanh nghiệp Demo A","state":"ACCEPTED","expiresAt":"2026-11-10T08:00:00Z","decidedAt":"2026-11-03T08:00:00Z","decisionReasonCode":null,"version":3,"placementId":null}
```

Lỗi HTTP 409 khi phản hồi đúng hoặc sau hạn:

```json
{"errorCode":"INVITATION_EXPIRED","error":"INVITATION_EXPIRED","message":"Lời mời đã hết hạn phản hồi.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-BC23:** Given 20 ACCEPT/DECLINE cùng version của một PENDING invitation; When commit cạnh tranh; Then chỉ một decision. WITHDRAW sau ACCEPTED là action riêng được serialize và append history, không sửa đè decision cũ.

### BC24 — Consent/share/policy và ProfileAccess (T2/T5→T1 facade), version1.0

```text
ProfileField=displayName|studentCode|majorCode|skills|projects|certificates|cv|evidence|email|phone|gpa|transcript
SchoolSharingPolicy {schoolOrgId:U,termId:U,version:V,allowedFields:ProfileField[],updatedAt:I|null,updatedByUserId:U|null}
GrantConsent {invitationId:U,purpose:INTERNSHIP_SELECTION,fieldWhitelist:ProfileField[1..12],expiresAt:I,policyVersion:Code,expectedSchoolPolicyVersion:V,expectedLineageVersion:V}
ConsentGrant {consentId:U,consentVersion:V,lineageVersion:V,studentUserId:U,schoolOrgId:U,termId:U,recipientOrgId:U,invitationId:U,purpose:INTERNSHIP_SELECTION,fieldWhitelist:ProfileField[],grantedAt:I,expiresAt:I,revokedAt:I|null,policyVersion:Code,replacedByConsentId:U|null}
GrantProfileShare {proposalItemId:U,consentId:U,expectedConsentVersion:V,expectedProposalVersion:V,fieldWhitelist:ProfileField[1..12],expiresAt:I}
ProfileShareGrant {shareId:U,shareVersion:V,consentId:U,consentVersion:V,studentUserId:U,schoolOrgId:U,termId:U,recipientOrgId:U,proposalItemId:U,purpose:INTERNSHIP_SELECTION,fieldWhitelist:ProfileField[],validFrom:I,expiresAt:I,revokedAt:I|null,grantedByUserId:U}
GrantCheck {consentId:U,consentVersion:V,shareId:U|null,studentUserId:U,schoolOrgId:U,termId:U,recipientOrgId:U,purpose:INTERNSHIP_SELECTION,requestedFields:ProfileField[1..12]}
GrantValidation {valid:boolean,allowedFields:ProfileField[],validUntil:I|null,currentVersion:V|null,reasonCode:ALLOWED|MISSING|REVOKED|EXPIRED|REPLACED|SCOPE_MISMATCH|PURPOSE_MISMATCH|FIELD_DENIED}
ProfileAccessQuery {studentUserId:U,termId:U,shareId:U|null,requestedFields:ProfileField[1..12],purpose:SCHOOL_REVIEW|INTERNSHIP_SELECTION}
ProfileAccessDecision {allowed:boolean,allowedFields:ProfileField[],validUntil:I|null,consentVersion:V|null,shareVersion:V|null,schoolPolicyVersion:V,reasonCode:ALLOWED|DENIED|REVOKED|EXPIRED|SCOPE_MISMATCH}
```
ProfileField có đúng 12 mã; mọi fieldWhitelist/requestedFields là tập không trùng, tối đa 12 phần tử. MVP hỗ trợ displayName, studentCode, majorCode, skills, projects, certificates, cv, evidence, email và phone. gpa/transcript là mã dự phòng chưa có data source MVP; yêu cầu dùng chúng trả 422 PROFILE_FIELD_UNSUPPORTED, không giả có điểm học vụ thật.

ProfileProjection là named type đóng: {studentUserId:U,profileVersion:V,allowedFields:ProfileField[],data:ProfileDataProjection}. ProfileDataProjection có additionalProperties=false và chỉ có các property đã được allowed: displayName:Text(1..150)|null, studentCode:Text(1..32)|null, majorCode:Code|null, skills:ProfileSkillProjection[], projects:Project[], certificates:Certificate[], cv:FileDto|null, evidence:FileDto[], email:Email|null, phone:Text(8..20)|null. ProfileSkillProjection là union đóng {kind:CANONICAL,skill:SelfSkillView} hoặc {kind:CUSTOM,skill:CustomSkillView}; field consent “skills” kiểm soát cả hai, không thêm mã ProfileField thứ13. CT-01 vẫn chỉ SkillValue[] canonical, không đổi strict matching. Property ngoài allowedFields phải **omit**, không trả null để gợi lộ dữ liệu. Field được phép nhưng chưa có dữ liệu dùng null hoặc [] đúng kiểu trên; không dùng Map mở để thêm dữ liệu tùy ý.

```text
SchoolSharingPolicyPort.get(actor:ActorContext,schoolOrgId:U,termId:U):Result<SchoolSharingPolicy>
SchoolSharingPolicyPort.update(actor:ActorContext,termId:U,request:{expectedVersion:V,allowedFields:ProfileField[]},mutation:MutationContext):Result<SchoolSharingPolicy>
StudentConsentPort.grant(actor:ActorContext,request:GrantConsent,mutation:MutationContext):Result<ConsentGrant>
StudentConsentPort.revoke(actor:ActorContext,consentId:U,request:{expectedLineageVersion:V,reasonCode:Reason},mutation:MutationContext):Result<ConsentGrant>
StudentConsentPort.check(actor:ActorContext,request:GrantCheck):Result<GrantValidation>
StudentConsentPort.lockAndCheckForShare(actor:ActorContext,request:GrantCheck):Result<GrantValidation>
ProfileSharePort.grant(actor:ActorContext,request:GrantProfileShare,mutation:MutationContext):Result<ProfileShareGrant>
ProfileSharePort.revoke(actor:ActorContext,shareId:U,request:{expectedVersion:V,reasonCode:Reason},mutation:MutationContext):Result<ProfileShareGrant>
ProfileSharePort.check(actor:ActorContext,request:GrantCheck):Result<GrantValidation>
ProfileAccessPort.decide(actor:ActorContext,request:ProfileAccessQuery):Result<ProfileAccessDecision>
StudentProfileProjectionPort.readAllowed(actor:ActorContext,request:ProfileAccessQuery,decision:ProfileAccessDecision):Result<ProfileProjection>
```
T2 sở hữu consent; T5 sở hữu share và SchoolSharingPolicy; T1 façade kết hợp các kiểm tra authoritative mới nhất. Chưa có SchoolSharingPolicy thì version=0, allowedFields=[majorCode,skills,projects]; đây chỉ là giới hạn được phép xin consent, không tự cấp share hoặc quyền đọc hồ sơ. GET /api/v1/school/terms/{termId}/profile-sharing-policy trả 200 SchoolSharingPolicy; PUT cùng route nhận {expectedVersion:V,allowedFields:ProfileField[]}, trả 200 và yêu cầu student_profile:share:school:manage trong school delegation đúng term. School review cần student_profile:school:read và profileReadFields được cấp đích danh, không đòi consent dành cho enterprise. Trường A không đọc trường B; global ADMIN không bypass.

GET /api/v1/students/me/consents nhận page,size, trả 200 Page&lt;ConsentGrant&gt; của principal. POST cùng route nhận GrantConsent, trả 201 ConsentGrant; POST /api/v1/students/me/consents/{id}/revoke nhận {expectedLineageVersion:V,reasonCode:Reason}, trả 200 ConsentGrant. Cả ba dùng student:consent:self:manage.

POST /api/v1/school/proposals/{id}/shares nhận GrantProfileShare, trả 201 ProfileShareGrant; proposalItemId phải bằng path id. POST /api/v1/school/profile-shares/{id}/revoke nhận {expectedVersion:V,reasonCode:Reason}, trả 200 ProfileShareGrant. School cần student_profile:share:school:manage và delegation đúng school/term. Grant share đọc lại invitation và proposal hiện hành, chỉ cho proposal ACCEPTED với invitation ACCEPTED; valid consent và capacity được tái kiểm tra trong transaction, rồi proposal ACCEPTED→SHARED. Không đổi student decision bằng việc cấp share.

GET /api/v1/students/{studentUserId}/profile?termId=U&shareId=U&fields=majorCode,skills&purpose=INTERNSHIP_SELECTION trả 200 ProfileProjection qua guard façade T1/provider T2. Enterprise cần student_profile:shared:read, shareId hợp lệ và own recipient org; school review dùng purpose=SCHOOL_REVIEW và explicit field delegation. Không truyền shareId cho school review; owner self dùng BC11. Deny trả 403 hoặc foreign resource 404; giao tập field phải khác rỗng. Query fields không tăng quyền. Thay policy/consent/share có hiệu lực với request đọc tiếp theo; không cache response PII để bỏ qua guard.

T2 suy recipient/school/term từ invitation thuộc student, không nhận org tùy ý trong body. GrantConsent là quyết định độc lập: không tự ACCEPT lời mời và không ép phải cấp consent để ACCEPT; share mới bắt buộc invitation ACCEPTED như trên. expiresAt phải trong (serverNow,serverNow+30ngày], fields thuộc school policy; vi phạm trả 422 CONSENT_EXPIRY_INVALID hoặc CONSENT_FIELDS_NOT_ALLOWED. expectedSchoolPolicyVersion và expectedLineageVersion được kiểm riêng; lineage lock theo (student,school,term,enterprise,purpose), version0 cho lần đầu. Regrant tăng version và vô hiệu lineage cũ.

T5 gọi lockAndCheckForShare của T2 trong cùng transaction; consent expired/revoked trả 409 CONSENT_INVALID, version đổi trả 409 CONSENT_CHANGED. expiresAt của share không vượt consent expiry; fieldWhitelist sai không được tự cắt bớt âm thầm. Effective fields = school policy∩consent∩share∩requestedFields. Đọc CV/evidence còn kiểm CLEAN và resource binding tại từng GET/Range. Revoke sau Placement chỉ chặn các lần đọc mới, không tự hủy Placement.

Ví dụ request grant consent:
```json
{"invitationId":"77777777-7777-4777-8777-777777777777","purpose":"INTERNSHIP_SELECTION","fieldWhitelist":["majorCode","skills"],"expiresAt":"2026-11-20T08:00:00Z","policyVersion":"demo-consent-v1","expectedSchoolPolicyVersion":0,"expectedLineageVersion":0}
```
Response 201 ConsentGrant:

```json
{"consentId":"dddddddd-dddd-4ddd-8ddd-dddddddddddd","consentVersion":1,"lineageVersion":1,"studentUserId":"11111111-1111-4111-8111-111111111111","schoolOrgId":"22222222-2222-4222-8222-222222222222","termId":"44444444-4444-4444-8444-444444444444","recipientOrgId":"33333333-3333-4333-8333-333333333333","invitationId":"77777777-7777-4777-8777-777777777777","purpose":"INTERNSHIP_SELECTION","fieldWhitelist":["majorCode","skills"],"grantedAt":"2026-11-03T08:00:00Z","expiresAt":"2026-11-20T08:00:00Z","revokedAt":null,"policyVersion":"demo-consent-v1","replacedByConsentId":null}
```

Ví dụ lỗi yêu cầu email khi school policy chưa cho phép, HTTP 422:

```json
{"errorCode":"CONSENT_FIELDS_NOT_ALLOWED","error":"CONSENT_FIELDS_NOT_ALLOWED","message":"Danh sách trường dữ liệu vượt chính sách chia sẻ của trường.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[{"field":"fieldWhitelist","code":"FIELD_DENIED","message":"email chưa được chính sách hiện hành cho phép."}],"retryAfterSeconds":null}
```

**AC-BC24:** Given valid consent/share; When revoke commit trước GET profile hoặc GET/Range bằng link cũ; Then deny và không trả bytes PII. Existing Placement không tự cancel. Assignment đơn lẻ không cấp quyền đọc PII; share không được tạo nếu proposal chưa ACCEPTED.

### BC31 — DemandCriteria và ProposalNomination (T5↔T4), version1.0

```text
RequiredCriteria {majorCodes:Code[],skills:SkillCriterion[],dateRange:DateRange}
DemandCriteriaSnapshot {demandId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U,quantity:int>=1,demandVersion:V,criteriaVersion:V,validationState:VALID,required:RequiredCriteria,preferred:SkillCriterion[],criteriaHash:H,academicCatalogVersion:Code,taxonomyVersion:Code,cooperationId:U|null,cooperationState:CooperationState|null,capturedAt:I}
NominationCandidate {studentUserId:U,profileSnapshotId:U,profileVersion:V,relevantHash:H}
NominateCandidates {cooperationId:U,matchRunId:U,candidates:NominationCandidate[1..5000],expectedDemandVersion:V,expectedCriteriaVersion:V,expectedCooperationVersion:V,expectedBatchVersion:V|null}
NominationResult {batchId:U,batchVersion:V,items:{proposalItemId:U,studentUserId:U,state:RESERVED,reservationUntil:I}[],quantity:int,placedCount:int,activeReservations:int,openForNomination:int}
DemandCriteriaPort.read(actor:ActorContext,request:{demandId:U,expectedCriteriaVersion:V|null,purpose:EVALUATE|NOMINATE|PLACEMENT}):Result<DemandCriteriaSnapshot>
ProposalNominationPort.nominate(actor:ActorContext,request:NominateCandidates,mutation:MutationContext):Result<NominationResult>
```
Required skills là AND. minLevel/minYearsPractice=null nghĩa chỉ kiểm có skill; mỗi ngưỡng khác null phải đạt phép so sánh ≥. majorCodes=[] nghĩa không giới hạn ngành; code không thuộc catalog trả 422 INVALID_CATALOG_REFERENCE. Hợp các availablePeriods phải bao phủ toàn dateRange. required.skills=[] không làm hồ sơ FAIL vì skill, nhưng vẫn kiểm availability/major. Không lặp skillCode giữa Required và Preferred. Criteria hash dùng compact canonical JSON: sort object keys và các tập skill/major code; decimal chuẩn hóa, null tường minh. Không đưa actor, demandId hoặc timestamp vào criteriaHash.

Browser EVALUATE chỉ gửi demandId và version; T4 đọc criteria authoritative qua T5. Demand DRAFT/READY có validationState=VALID được đánh giá mà không cần Cooperation APPROVED, tránh vòng chờ matching–approval.

Nominate chỉ dành cho school có matching:school:nominate và t5:proposal:manage đúng delegation. Cooperation phải APPROVED; MatchRun COMPLETED đúng criteria/profile hiện hành; mọi candidate phải MATCH và schoolApproved. T5 tái kiểm eligibility/capacity trong cùng transaction và là owner **duy nhất** của batch/item/reservation. Candidate refs do T4 dựng từ run immutable, không tin profile refs browser tự gửi.

Một batch/cooperation; student đã xuất hiện trong batch, kể cả đã DECLINED, trả 409 PROPOSAL_ALREADY_EXISTS. expectedBatchVersion=null chỉ cho lần tạo đầu; batch đã có mà caller vẫn gửi null trả 409 PROPOSAL_BATCH_EXISTS. Append dùng version hiện hành. Toàn bộ candidates thành công hoặc rollback, không partial nomination; khóa quota và các candidate theo studentUserId tăng dần. Item mới RESERVED với reservationUntil=min(serverNow+24giờ,demand.deadline). Lỗi version/state: 409 CRITERIA_STALE, MATCH_RUN_STALE, PROFILE_STALE hoặc CAPACITY_EXCEEDED; lỗi candidate: 422 CANDIDATE_NOT_MATCH hoặc SCHOOL_APPROVAL_REQUIRED.

Ví dụ request nội bộ ProposalNominationPort.nominate, được BC32 dựng từ run:

```json
{"cooperationId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","matchRunId":"66666666-6666-4666-8666-666666666666","candidates":[{"studentUserId":"11111111-1111-4111-8111-111111111111","profileSnapshotId":"ffffffff-ffff-4fff-8fff-ffffffffffff","profileVersion":3,"relevantHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}],"expectedDemandVersion":4,"expectedCriteriaVersion":3,"expectedCooperationVersion":2,"expectedBatchVersion":null}
```

Success&lt;NominationResult&gt;; REST route canonical BC32 ánh xạ 201:

```json
{"batchId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","batchVersion":1,"items":[{"proposalItemId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","studentUserId":"11111111-1111-4111-8111-111111111111","state":"RESERVED","reservationUntil":"2026-11-04T08:00:00Z"}],"quantity":2,"placedCount":1,"activeReservations":1,"openForNomination":0}
```

Lỗi HTTP 409 nếu slot đã được giao dịch khác giữ:

```json
{"errorCode":"CAPACITY_EXCEEDED","error":"CAPACITY_EXCEEDED","message":"Chỉ tiêu còn trống không đủ cho danh sách đề xuất.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-BC31:** Given Demand DRAFT/VALID; When evaluate; Then không tự tạo Cooperation. Given một slot còn lại và 20 nomination cạnh tranh; Then chỉ một candidate giữ được slot, không overbook hoặc tạo batch thứ hai.

### BC32 — MatchRun/MatchResult/ProtectedAggregate (T4→T5/T2UI), version1.0

```text
MatchRunRef {matchRunId:U,demandId:U,demandVersion:V,criteriaVersion:V,criteriaHash:H,datasetSnapshotId:U,profileSnapshotVersion:H,ruleVersion:Code,taxonomyVersion:Code,scoringVersion:Code,state:PENDING|RUNNING|COMPLETED|FAILED,createdAt:I,completedAt:I|null,failureCode:ENGINE_UNAVAILABLE|INVALID_SNAPSHOT|null}
CriterionEvaluation {criterionCode:Code,type:REQUIRED|PREFERRED,kind:MAJOR|SKILL|AVAILABILITY,status:PASS|FAIL|UNKNOWN,reasonCode:Code}
MatchResult {studentUserId:U,profileSnapshotId:U,profileVersion:V,relevantHash:H,overallStatus:MATCH|MISSING_DATA|NOT_MATCH,fitScore:decimal[0..100]|null,details:CriterionEvaluation[]}
SchoolMatchSummary {run:MatchRunRef,matched:int,missingData:int,notMatched:int}
SchoolMatchDetailsPage {run:MatchRunRef,results:Page<MatchResult>}
ProtectedAggregate {releaseId:U,matchRunId:U,eligibleBand:SUPPRESSED|5-9|10-19|20+}
EvaluateMatchRequest {demandId:U,expectedDemandVersion:V,expectedCriteriaVersion:V}
EvaluateMatchResponse={audience:ENTERPRISE,release:ProtectedAggregate}|{audience:SCHOOL,summary:SchoolMatchSummary}
NominateMatchRequest {studentUserIds:U[1..5000],expectedDemandVersion:V,expectedCriteriaVersion:V,expectedCooperationVersion:V,expectedBatchVersion:V|null}
MatchingPort.evaluate(actor:ActorContext,request:EvaluateMatchRequest,mutation:MutationContext):Result<EvaluateMatchResponse>
MatchingPort.readRunForNomination(actor:ActorContext,matchRunId:U):Result<MatchRunRef>
MatchingPort.details(actor:ActorContext,matchRunId:U,request:{overallStatus:MATCH|MISSING_DATA|NOT_MATCH,page:int,size:int}):Result<SchoolMatchDetailsPage>
MatchingPort.protectedSummary(actor:ActorContext,matchRunId:U):Result<ProtectedAggregate>
MatchingPort.nominate(actor:ActorContext,matchRunId:U,request:NominateMatchRequest,mutation:MutationContext):Result<NominationResult>
```
POST /api/v1/matching/evaluate nhận EvaluateMatchRequest, trả 200 EvaluateMatchResponse sau khi hoàn tất; yêu cầu matching:run:execute, CSRF và key. Engine không khả dụng trả 503 MATCH_ENGINE_UNAVAILABLE, không trả partial counters. Audience được server xác định từ permission/scope, không cho browser chọn nhánh SCHOOL.

GET /api/v1/matching/runs/{id}/summary trả 200 ProtectedAggregate cho enterprise sở hữu release với matching:enterprise:aggregate_read; run chưa hoàn tất trả 409 MATCH_RUN_NOT_READY. GET /api/v1/matching/runs/{id}/details nhận overallStatus,page,size, trả 200 SchoolMatchDetailsPage với results là Page chuẩn; chỉ matching:school:detail_read và delegation đúng school/term, enterprise nhận 403. POST /api/v1/matching/runs/{id}/nominate nhận NominateMatchRequest, trả 201 NominationResult; T4 dựng immutable candidate refs và gọi port T5 BC31. Đây là REST path nomination duy nhất. Endpoint barriers nhiều ô không được bật cho enterprise MVP; trả 409 PRIVACY_BREAKDOWN_DISABLED.

Enterprise DTO không có total, page, student IDs, fingerprint hoặc profileCount. Ledger UNIQUE(enterpriseOrgId,schoolOrgId,termId,demoBatchId); fingerprint theo T89, dataset đóng băng bởi Ops/QA, không query live để tạo release mới. Cùng fingerprint replay DTO đã bảo vệ; khác fingerprint trong cùng batch trả 409 PRIVACY_RELEASE_LOCKED. Đổi actor/demand/filter/export không vượt ledger.

Count0–4→SUPPRESSED;5–9→5-9;10–19→10-19;≥20→20+. Không raw count, phần trăm hoặc breakdown nhiều ô. SchoolMatchDetails chứa IDs và criterion evaluation; nếu UI cần tên/MSSV phải compose qua guard façade T1, không mirror PII trong kết quả engine. Required FAIL ưu tiên UNKNOWN; chỉ MATCH có score, tính count Preferred/HALF_UP2 theo SRS; Preferred UNKNOWN tính0, không có Preferred score0, sort fitScore DESC rồi studentUserId ASC.

Ví dụ request evaluate:
```json
{"demandId":"55555555-5555-4555-8555-555555555555","expectedDemandVersion":4,"expectedCriteriaVersion":3}
```
```json
{"audience":"ENTERPRISE","release":{"releaseId":"bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb","matchRunId":"66666666-6666-4666-8666-666666666666","eligibleBand":"5-9"}}
```
Ví dụ lỗi release khác fingerprint trong cùng đợt, HTTP 409:

```json
{"errorCode":"PRIVACY_RELEASE_LOCKED","error":"PRIVACY_RELEASE_LOCKED","message":"Đợt demo đã có bản thống kê được bảo vệ cho phạm vi này.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-BC32:** Given fixtures 0/1/4/5/10/20; When xem JSON/chart; Then nhận đúng band, không rawCount. Given release hiện hành; When actor hoặc demand khác gửi criteriaHash khác trong cùng batch; Then 409 PRIVACY_RELEASE_LOCKED, không ledger/release thứ hai.

### BC41 — PlacementRead/lifecycle events/CaseClosed (T5↔T3), version1.0

```text
PlacementView {placementId:U,studentUserId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U,demandId:U,cooperationId:U,proposalItemId:U,state:PENDING_APPROVAL|APPROVED|ACTIVE|CANCELLED|COMPLETED,version:V,effectiveAt:I,startDate:D,endDate:D,approvedAt:I|null,cancelledAt:I|null,completedAt:I|null,decisionRef:Ref|null}
CaseParticipantRef {caseId:U,placementId:U,studentUserId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U}
CaseClosureProof {caseId:U,placementId:U,caseVersion:V,state:COMPLETED|TERMINATED,decision:NORMAL_COMPLETION|EARLY_TERMINATION,decisionRef:Ref,reasonCode:Reason,decisionActorUserId:U,schoolOrgId:U,termId:U,closedAt:I,endDateReached:boolean,allTasksCompleted:boolean,allReportsReviewed:boolean,evaluationPublished:boolean}
PlacementLifecycleEvent {eventId:U,eventType:PlacementApproved|PlacementActivated|PlacementCancelled|PlacementCompleted,schemaVersion:1,placementId:U,aggregateVersion:V,placementVersion:V,studentUserId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U,effectiveAt:I,occurredAt:I,traceId:U,reasonCode:Reason|null,decisionRef:Ref|null}
CaseClosedEvent {eventId:U,eventType:CaseClosed,schemaVersion:1,caseId:U,placementId:U,aggregateVersion:V,caseVersion:V,decision:NORMAL_COMPLETION|EARLY_TERMINATION,decisionRef:Ref,reasonCode:Reason,closedAt:I,traceId:U}
EventConsumeResult {eventId:U,outcome:ACK_APPLIED|ACK_DUPLICATE|ACK_STALE|RETRY|REJECT,appliedAggregateVersion:V|null,reasonCode:APPLIED|DUPLICATE|STALE|SOURCE_NOT_FOUND|SCOPE_MISMATCH|INVALID_SCHEMA|SOURCE_UNAVAILABLE|STATE_CONFLICT,retryAfterSeconds:int|null}
TrustedEventContext {producerModule:T3|T5,deliveryOperationId:U,requestId:U} // server-created; never a browser body
PlacementReadPort.readForInternship(actor:ActorContext,placementId:U):Result<PlacementView>
PlacementReadPort.readCurrentForGuard(actor:ActorContext,placementId:U):Result<PlacementView>
InternshipCaseReadPort.participant(actor:ActorContext,caseId:U):Result<CaseParticipantRef>
InternshipCaseReadPort.readClosureProof(actor:ActorContext,caseId:U):Result<CaseClosureProof>
PlacementLifecycleConsumer.consume(context:TrustedEventContext,event:PlacementLifecycleEvent):EventConsumeResult
CaseClosedConsumer.consume(context:TrustedEventContext,event:CaseClosedEvent):EventConsumeResult
```
Đây là port nội bộ, không public REST callback. T5 là owner duy nhất của Placement; T3 là owner duy nhất của Case. aggregateVersion phải bằng placementVersion hoặc caseVersion tương ứng; khác nhau trả outcome REJECT/INVALID_SCHEMA. T5 cung cấp snapshot startDate/endDate; T3 không tự suy ngày từ tên kỳ. ParticipantRef chỉ có IDs; đọc PII phải qua ProfileAccess. Mỗi task/submission mutation còn reread Placement ACTIVE và effectiveAt≤serverNow, dù local case/event chưa cập nhật.

Producer commit business+audit+outbox cùng transaction; consumer commit inbox+case+audit cùng transaction. Dedup theo eventId và (aggregateId,version,eventType). Source current version không thấp hơn event version; nguồn không tồn tại/scope giả trả REJECT, lỗi tạm thời trả RETRY. Consumer áp dụng projection authoritative mới nhất với lastAppliedVersion tăng đơn điệu.

PlacementActivated đến trước PlacementApproved khi chưa có case: xác minh Placement ACTIVE đã có approval hợp lệ, tạo WAITING_START rồi chuyển IN_PROGRESS trong cùng transaction; không đợi event Approved cũ. Nếu nguồn CANCELLED thì không tạo case hoạt động hoặc mở lại terminal case. Event version cũ trả ACK_STALE; duplicate trả ACK_DUPLICATE. CaseClosed chỉ do authorized school close phát; handler PlacementCancelled không phát CaseClosed ngược lại gây loop. T5 reread ClosureProof/authority: NORMAL đủ gate→COMPLETED, EARLY hợp lệ→CANCELLED; terminal cùng outcome ACK, không release quota lần hai. Retry tối đa 5 lần theo 1/5/30/300/1800 giây rồi DLQ; Ops reprocess vẫn reread source và reconcile theo placementId.

Ví dụ event:
```json
{"eventId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","eventType":"PlacementActivated","schemaVersion":1,"placementId":"99999999-9999-4999-8999-999999999999","aggregateVersion":3,"placementVersion":3,"studentUserId":"11111111-1111-4111-8111-111111111111","schoolOrgId":"22222222-2222-4222-8222-222222222222","enterpriseOrgId":"33333333-3333-4333-8333-333333333333","termId":"44444444-4444-8444-4444-444444444444","effectiveAt":"2026-11-03T00:00:00Z","occurredAt":"2026-11-03T00:00:01Z","traceId":"00000000-0000-4000-8000-000000000099","reasonCode":null,"decisionRef":null}
```
EventConsumeResult thành công; không phải HTTP response:

```json
{"eventId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","outcome":"ACK_APPLIED","appliedAggregateVersion":3,"reasonCode":"APPLIED","retryAfterSeconds":null}
```

Ví dụ aggregateVersion khác placementVersion trong envelope:

```json
{"eventId":"eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee","outcome":"REJECT","appliedAggregateVersion":null,"reasonCode":"INVALID_SCHEMA","retryAfterSeconds":null}
```

**AC-BC41:** Given Activated version3 tới trước Approved version2; When consumer xử lý rồi nhận event cũ; Then một case IN_PROGRESS, lastAppliedVersion3 và ACK_STALE cho version2. Given source CANCELLED version4 nhưng local event chưa cập nhật; When student tạo Task/Submission; Then deny.

### BC42 — Organization-school verification read (T5→T1/T3), version1.0

```text
SchoolVerificationView {enterpriseOrgId:U,schoolOrgId:U,verificationId:U|null,status:DRAFT|SUBMITTED|NEEDS_CHANGES|APPROVED|REJECTED|null,version:V|null,approvedAt:I|null,schoolApproved:boolean}
OrganizationSchoolVerificationPort.get(actor:ActorContext,enterpriseOrgId:U,schoolOrgId:U):Result<SchoolVerificationView>
```
T5 là canonical owner; T1/T3 chỉ gọi từ workflow xác nhận coordinator hoặc guard Placement với trusted purpose/scope. Thiếu verification trong phạm vi hợp lệ trả Success với các field nullable và schoolApproved=false, không tự phê duyệt. Organization T1 VERIFIED là guard độc lập với school verification APPROVED.

Ví dụ tham số enterpriseOrgId=33333333-3333-4333-8333-333333333333, schoolOrgId=22222222-2222-4222-8222-222222222222; Success&lt;SchoolVerificationView&gt;:

```json
{"enterpriseOrgId":"33333333-3333-4333-8333-333333333333","schoolOrgId":"22222222-2222-4222-8222-222222222222","verificationId":null,"status":null,"version":null,"approvedAt":null,"schoolApproved":false}
```

DomainError khi actor không có scope; REST adapter nếu có ánh xạ HTTP 404:

```json
{"errorCode":"RESOURCE_NOT_FOUND","error":"RESOURCE_NOT_FOUND","message":"Không tìm thấy tài nguyên trong phạm vi truy cập.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-BC42:** Given org T1 VERIFIED nhưng T5 school verification chưa APPROVED; When confirm coordinator hoặc activate Placement; Then deny, không sửa trạng thái users.


### CP-T5UI1 — Façade T5 dùng chung với giao diện T2/T3/T4, version1.0

**Owner T5; consumer UI T5, các port BC21–BC42. PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** Các route dưới là contract target trong SRS T42/T72, chưa có backend T5 trong checkout. Dùng một application service cho mỗi nghiệp vụ; façade REST không tạo nguồn dữ liệu mới. Canonical Verification/Cooperation dùng NEEDS_CHANGES theo T17/R2,T70/R4; adapter legacy NEEDS_INFO→NEEDS_CHANGES, PENDING_VERIFICATION→SUBMITTED, VERIFIED của school verification→APPROVED. Organization T1 giữ VERIFIED, không map org status thành APPROVED.

```text
VerificationState=DRAFT|SUBMITTED|NEEDS_CHANGES|APPROVED|REJECTED
CooperationState=DRAFT|SUBMITTED|NEEDS_CHANGES|APPROVED|REJECTED|CANCELLED
ProposalState=RESERVED|INVITED|ACCEPTED|SHARED|ENTERPRISE_ACCEPTED|PLACED|DECLINED|EXPIRED|REVOKED
VerificationInput {schoolOrgId:U,previousVerificationId:U|null,registrationCode:Text(1..64),registeredName:Text(1..200),address:Text(1..500),contactName:Text(1..150),contactEmail:Email,contactPhone:Text(8..20),evidenceFileIds:U[0..20]}
VerificationDto {verificationId:U,enterpriseOrgId:U,schoolOrgId:U,state:VerificationState,submissionNo:int,version:V,previousVerificationId:U|null,data:VerificationInput,submittedAt:I|null,decidedAt:I|null,decisionRef:Ref|null,missingFields:Text(1..64)[]}
VerificationDecision {expectedVersion:V,decision:APPROVE|REJECT|REQUEST_INFO,reasonCode:Reason|null,decisionRef:Ref,missingFields:Text(1..64)[]}
DemandInput {schoolOrgId:U,termId:U,title:Text(1..200),quantity:int[1..5000],description:Text(1..10000),location:Text(1..500)|null,benefits:Text(0..2000)|null,deadline:I,required:RequiredCriteria,preferred:SkillCriterion[],academicCatalogVersion:Code,taxonomyVersion:Code}
DemandDto {demandId:U,enterpriseOrgId:U,state:DRAFT|READY,version:V,criteriaVersion:V,validationState:VALID|INVALID,data:DemandInput,cooperationId:U|null,softDeletedAt:I|null}
CooperationDto {cooperationId:U,demandId:U,schoolOrgId:U,enterpriseOrgId:U,termId:U,state:CooperationState,submissionNo:int,version:V,submittedAt:I|null,decisionRef:Ref|null}
CooperationDecision {expectedVersion:V,decision:APPROVE|REJECT|REQUEST_INFO,reasonCode:Reason|null,decisionRef:Ref,missingFields:Text(1..64)[]}
SubmissionHistory {submissionNo:int,submittedAt:I,demandVersion:V,criteriaVersion:V,quantitySnapshot:int,decision:APPROVE|REJECT|REQUEST_INFO|null,decidedAt:I|null,reasonCode:Reason|null,decisionRef:Ref|null}
ProposalItemView {proposalItemId:U,batchId:U,studentUserId:U,cooperationId:U,state:ProposalState,version:V,reservationUntil:I,invitationId:U|null,shareId:U|null,placementId:U|null}
CapacityView {cooperationId:U,demandId:U,quantity:int,placedCount:int,activeReservations:int,openForNomination:int,declinedCount:int}
CreatePlacement {proposalItemId:U,expectedProposalVersion:V,expectedEligibilityVersion:V,effectiveAt:I,startDate:D,endDate:D}
PlacementCommand {expectedVersion:V,decisionRef:Ref,reasonCode:Reason}
InternshipLink {placementId:U,caseId:U|null,state:WAITING_HANDOFF|AVAILABLE,route:Text(1..300)|null}
PlacementSummary {demandId:U,cooperationId:U|null,target:int,placed:int,pending:int,remaining:int}
```

Tất cả domain mutation dùng key, CSRF, current permission và expectedVersion theo C.0; request body không được đổi owner enterprise/school của aggregate hiện có. List mặc định size=25, dùng page/size chuẩn, filter enum exact, sort createdAt DESC rồi id ASC. Placement có thêm filter state, termId, demandId, cooperationId. ID ngoài scope trả 404; state/version sai trả 409; validation nghiệp vụ trả 422; dependency tạm thời lỗi trả 503. Field quyết định trong body không chứng minh quyền của caller. Contact/evidence chỉ dành cho enterprise owner và delegated reviewer, không đi qua matching aggregate. registrationCode demo chỉ là dữ liệu giả, không chứng minh xác thực pháp lý doanh nghiệp thật.

| REST endpoint exact | DTO/status | Permission / tiền điều kiện / lỗi domain |
|---|---|---|
| POST /api/v1/enterprise/verifications | VerificationInput→201 VerificationDto DRAFT | enterprise_verification:enterprise:manage; owner từ membership; previous ID chỉ REJECTED cùng org/school;409 VERIFICATION_ALREADY_OPEN |
| PATCH /api/v1/enterprise/verifications/{id} | {expectedVersion:V,data:VerificationInput}→200 VerificationDto | Owner; DRAFT/NEEDS_CHANGES; school/lineage immutable; 422 VERIFICATION_DATA_INVALID |
| PATCH /api/v1/enterprise/verifications/{id}/submit | {expectedVersion:V}→200 VerificationDto SUBMITTED | Owner; đủ contact/registration và ít nhất 1 evidence CLEAN đúng binding; current school; version; không sửa history |
| GET /api/v1/enterprise/verifications; GET /api/v1/school/verifications | page,size,schoolOrgId?,state?→200 Page&lt;VerificationDto&gt; | Enterprise own hoặc enterprise_verification:school:review + SD; không mở contact cross-school |
| PATCH /api/v1/school/verifications/{id}/decision | VerificationDecision→200 VerificationDto | enterprise_verification:school:review; SUBMITTED; REJECT/REQUEST_INFO cần reason, REQUEST_INFO nonempty missingFields; APPROVE missingFields=[] |
| POST /api/v1/enterprise/demands | DemandInput→201 DemandDto DRAFT | cooperation:enterprise:create; org VERIFIED; school/term catalogs hợp lệ; không tự submit |
| PATCH /api/v1/enterprise/demands/{id} | {expectedVersion:V,data:DemandInput}→200 DemandDto | cooperation:enterprise:create; DRAFT/unlocked; owner và term immutable sau tạo; 409 DEMAND_LOCKED; 422 INVALID_CRITERIA |
| GET /api/v1/enterprise/demands | page,size,termId?,state?,includeDeleted?→200 Page&lt;DemandDto&gt; | Enterprise own; version/criteria đọc từ T5; không truyền criteria client vào matching |
| PATCH /api/v1/enterprise/demands/{id}/reopen; PATCH /api/v1/enterprise/demands/{id}/restore | {expectedVersion:V,reasonCode:Reason}→200 DemandDto | Demand READY chưa Cooperation hoặc Cooperation REJECTED, không downstream hiệu lực; restore chỉ softDeletedAt!=null; 409 DEMAND_LOCKED; không dùng Demand CANCELLED |
| POST /api/v1/enterprise/cooperations | {demandId:U,expectedDemandVersion:V}→201 CooperationDto DRAFT | enterprise:cooperation:submit; unique demand; tạo draft chưa phải đã gửi; 409 COOPERATION_ALREADY_EXISTS |
| PATCH /api/v1/enterprise/cooperations/{id}/submit | {expectedVersion:V,expectedDemandVersion:V}→200 CooperationDto SUBMITTED | enterprise:cooperation:submit; DRAFT/NEEDS_CHANGES; internal validateAndReady; org VERIFIED + school APPROVED; snapshot immutable; 422 SCHOOL_VERIFICATION_REQUIRED |
| POST /api/v1/enterprise/demands/submit-with-cooperation | {demandId:U,expectedDemandVersion:V}→200 {demand:DemandDto,cooperation:CooperationDto} | Canonical UI Gửi: existing saved DRAFT chưa Cooperation; create unique DRAFT coop + validateAndReady + submit atomic. Không nhúng một luồng tạo Demand thứ hai. Lỗi rollback, không SUBMITTED giả. |
| PATCH /api/v1/enterprise/cooperations/{id}/reopen | {expectedVersion:V,reasonCode:Reason}→200 CooperationDto DRAFT | Owner; REJECTED, không downstream hiệu lực; giữ ID, append history. CANCELLED legacy read-only khi chưa có policy hủy được phê duyệt. |
| GET /api/v1/school/cooperations; GET /api/v1/enterprise/cooperations | page,size,termId?,state?→200 Page&lt;CooperationDto&gt; | cooperation:school:review+SD hoặc enterprise owner |
| PATCH /api/v1/school/cooperations/{id}/decision | CooperationDecision→200 CooperationDto | cooperation:school:review+SD; chỉ SUBMITTED; reason cho REJECT/REQUEST_INFO; APPROVE giữ snapshot, không sửa Demand |
| GET /api/v1/school/cooperations/{id}/submission-history | page,size→200 Page&lt;SubmissionHistory&gt; | Delegated reviewer; snapshot theo submissionNo, không raw credentials/PII |
| GET /api/v1/school/cooperations/{id}/capacity | none→200 CapacityView | t5:proposal:manage+SD; T90/R4 exact counters; no hidden student profile |
| GET /api/v1/school/cooperations/{id}/proposals; GET /api/v1/enterprise/cooperations/{id}/proposals | page,size,state?→200 Page&lt;ProposalItemView&gt; | School t5:proposal:manage; enterprise proposal:enterprise:decide; enterprise trả studentUserId chỉ nếu valid explicit share, nếu chưa share thì item bị lọc, không ID leakage |
| POST /api/v1/school/proposals/{id}/invite | {expectedVersion:V,expiresAt:I\|null}→202 InternshipInvitation | t5:proposal:manage; RESERVED còn reservation. null dùng min(now+7ngày,demand.deadline,term.endDate cuối ngày UTC); giá trị explicit phải &gt;now,≤now+30ngày và không vượt hạn tuyển/kỳ, sai trả 422 INVITATION_EXPIRY_INVALID; một invitation hiệu lực/item |
| PATCH /api/v1/enterprise/proposals/{id}/decision | {expectedVersion:V,decision:ACCEPT\|DECLINE,reasonCode:Reason\|null}→200 ProposalItemView | proposal:enterprise:decide; owner SHARED, current consent/share; DECLINE reason; ACCEPT→ENTERPRISE_ACCEPTED; không tạo Placement |
| POST /api/v1/school/placements | CreatePlacement→201 PlacementView PENDING_APPROVAL | placement:school:write+SD; accepted SV, consent/share, enterprise accepted, schoolApproved, criteria/profile current;422 PLACEMENT_GATE_FAILED |
| POST /api/v1/placements/{id}/approve | PlacementCommand→200 PlacementView APPROVED | placement:school:write+SD; PENDING_APPROVAL; recheck tất cả gates/quota; unique active-key;409 ACTIVE_PLACEMENT_EXISTS/CAPACITY_EXCEEDED |
| POST /api/v1/placements/{id}/activate | PlacementCommand→200 PlacementView ACTIVE | placement:school:write+SD; APPROVED; now&gt;=effectiveAt, valid term/org/school verification;409 PLACEMENT_NOT_EFFECTIVE; scheduler cùng guard |
| POST /api/v1/placements/{id}/cancel | PlacementCommand→200 PlacementView CANCELLED | placement:school:write+SD; PENDING_APPROVAL/APPROVED/ACTIVE; reason; terminal409 STATE_CONFLICT |
| GET /api/v1/enterprise/placements; GET /api/v1/school/placements | page,size,termId?,demandId?,cooperationId?,state?→200 Page&lt;PlacementView&gt; | Owner enterprise hoặc school delegate; projection không thêm student name/PII mặc định |
| GET /api/v1/enterprise/placements/summary; GET /api/v1/school/placements/summary | demandId→200 PlacementSummary | Cùng read scope; target từ approved submission, placed DISTINCT APPROVED/ACTIVE/COMPLETED; remaining=max(0,target-placed); pending DISTINCT chưa Placement |
| GET /api/v1/enterprise/placements/{id}/internship-link; GET /api/v1/school/placements/{id}/internship-link | none→200 InternshipLink | Scoped owner; pending event→WAITING_HANDOFF/null route; available→relative /workspace/cases/{caseId}; thiếu adapter503 DEPENDENCY_UNAVAILABLE |

Mọi Placement read kiểm account ACTIVE và membership hiện hành ở T1. Enterprise chỉ đọc own Placement; school dùng placement:school:write hoặc t3:workspace:read với delegation đúng school/term, không coi quyền approve là global read. Case link vẫn qua guard T3 khi click. Placement APPROVED chưa ACTIVE không cho Task/Submission. Slot lock và unique active-key được thực thi ở approve; PENDING_APPROVAL không chiếm active-key/placedCount, còn proposal reservation vẫn giữ theo T90/R4 đến approve/cancel/expire.

**Một canonical nomination path:** POST /api/v1/matching/runs/{id}/nominate (BC32) → MatchingPort → ProposalNominationPort (BC31). Các target cũ POST /api/v1/school/cooperations/{id}/proposal-batches và POST /api/v1/school/proposal-batches/{id}/items chưa có implementation T5 trong checkout; đề xuất thay chúng trong SRS bằng path này, không tạo route ghi batch thứ hai. Một batch/cooperation; lần đầu expectedBatchVersion=null, append cùng batch dùng version hiện hành.

Target /api/v1/cooperations được thay bằng /api/v1/enterprise/cooperations; target /api/v1/proposals/{id}/issue-invitations được thay bằng POST /api/v1/school/proposals/{id}/invite với id là proposalItemId. **Không tạo alias runtime cho các target chưa có consumer/source trong phạm vi đã kiểm tra.** Khi hợp nhất SRS phải đánh dấu target cũ SUPERSEDED và cập nhật consumer contract cùng lúc. Core API T1 đã tồn tại giữ compatibility như C.0; đây không phải việc đổi endpoint core đang chạy.

Proposal phase ACCEPTED là canonical theo T90/R4, legacy STUDENT_ACCEPTED→ACCEPTED; tách invitation ACCEPTED/version riêng. Consent/share mới không tự thay student decision. School verification REQUEST_INFO giữ ID/submissionNo history; REJECTED không reopen chính attempt mà tạo attempt mới previousVerificationId. Cooperation REJECTED được reopen cùng ID; không copy quy tắc verification vào cooperation.

Ví dụ create Cooperation: request với key hợp lệ:

```json
{"demandId":"55555555-5555-4555-8555-555555555555","expectedDemandVersion":4}
```
```json
{"cooperationId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","demandId":"55555555-5555-4555-8555-555555555555","schoolOrgId":"22222222-2222-4222-8222-222222222222","enterpriseOrgId":"33333333-3333-4333-8333-333333333333","termId":"44444444-4444-4444-8444-444444444444","state":"DRAFT","submissionNo":0,"version":1,"submittedAt":null,"decisionRef":null}
```

Lỗi HTTP 422 khi submit mà school verification chưa APPROVED:

```json
{"errorCode":"SCHOOL_VERIFICATION_REQUIRED","error":"SCHOOL_VERIFICATION_REQUIRED","message":"Doanh nghiệp chưa được trường phê duyệt.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

**AC-T5UI:** Given Demand DRAFT hợp lệ; When submit-with-cooperation gặp audit failure; Then Demand/Cooperation/outbox rollback, không READY/SUBMITTED nửa chừng. Given 20 approve cho một student/term; Then tối đa 1 APPROVED, các request xung đột trả 409 ACTIVE_PLACEMENT_EXISTS, không vượt quota. Given enterprise A đổi ID sang item của enterprise B; Then 404 và không trả projection studentUserId. Những AC này **NOT TESTED**.


## D. Permission Registry 1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** Đối chiếu V3__permissions_and_membership_integrity.sql, PermissionCatalog, AuthorizationPolicy và docs/contracts/permission_matrix.csv. Giữ nguyên 18 mã V3; thêm 33 mã riêng dưới đây. Không INSERT alias thành permission trùng UNIQUE(resource,action); alias là manifest versioned của adapter, normalize trước PermissionCatalog lookup.

Role ceiling là điều kiện cần. Quyết định còn kiểm account ACTIVE/securityVersion, membership, operational org VERIFIED cho nghiệp vụ tổ chức, delegation/assignment có hiệu lực, owner và trạng thái tài nguyên. Auth/self giữ guard hiện hữu, không bắt org VERIFIED để đọc /me hoặc đổi mật khẩu. ALL là bốn role hiện hữu, không phải role mới. /me/permissions chỉ gợi ý UI; V3 đã cấp task_write/evaluate cho role ENTERPRISE và school_read cho SCHOOL_STAFF, vì vậy phải bổ sung business guard thay vì coi role catalog là đủ.

Grantor: CORE=catalog/membership hiện hữu, ADMIN cấp membership theo guard; SELF=quyền owner không ủy quyền; SD=school delegate được phép grant đúng school/term và subset quyền; EC=enterprise coordinator được trường xác nhận; HR=assignment HR hiện hành; TAX=named editor manifest do Leader T1 + T4 ký cho taxonomy toàn dự án; RESOURCE=quyền trên resource, FileService không tự mở quyền. Trusted service purpose được đăng ký riêng, không là human role.

### D.1. Giữ nguyên các mã V3

| Permission canonical | Permission legacy | Hành động | Role ceiling | Scope / điều kiện bổ sung | Grantor | Team owner |
|---|---|---|---|---|---|---|
| account:self:read | account:self:read | Đọc danh tính bản thân | ALL | SELF; không áp org guard vào auth/self | CORE/SELF | T1 |
| account:self:password_change | account:self:password_change | Đổi mật khẩu bản thân | ALL | SELF; xác minh mật khẩu hiện tại | CORE/SELF | T1 |
| account:admin:status | account:admin:status | Đọc identity/status history; đổi trạng thái account | ADMIN | GLOBAL; transition/reason; admin cuối cùng | CORE | T1 |
| account:admin:revoke | account:admin:revoke | Thu hồi session | ADMIN | GLOBAL; account đích hợp lệ | CORE | T1 |
| membership:admin:write | membership:admin:write | Account invitation; grant/revoke membership | ADMIN | GLOBAL; role/org hợp lệ; admin cuối cùng | CORE | T1 |
| organization:admin:create | organization:admin:create | Tạo identity tổ chức; demo operational verification hiện hữu | ADMIN | GLOBAL; tạo PENDING; demo verification không thay T5 | CORE | T1 |
| audit:global:read | audit:global:read | Tra cứu audit | ADMIN | GLOBAL; phân trang, không kéo theo quyền profile/file | CORE | T1 |
| student_profile:self:read | student_profile:self:read | Đọc profile bản thân | STUDENT | SELF: studentUserId=principal | SELF | T2 |
| student_profile:self:write | student_profile:self:write | Sửa profile/skills/project/evidence | STUDENT | SELF; version; file binding | SELF | T2 |
| student_profile:shared:read | student_profile:shared:read | Enterprise đọc profile được chia sẻ | ENTERPRISE | Đúng enterprise + consent/share/policy còn hiệu lực | CORE+RESOURCE | T1/T2/T5 |
| student_profile:school:read | student_profile:school:read | School đọc profile | SCHOOL_STAFF | Delegation đúng school/term + profileReadFields | SD | T1/T2 |
| cooperation:enterprise:create | cooperation:enterprise:create | Demand authoring | ENTERPRISE | Owner; allowed state/version; không gồm school decision | CORE | T5 |
| cooperation:school:review | cooperation:school:review | Review Cooperation | SCHOOL_STAFF | Delegation school/term; current submission/version | SD | T5 |
| placement:school:write | placement:school:write | Create/approve/manual activate/cancel Placement | SCHOOL_STAFF | Delegation; command gates; quota/eligibility/unique active-key | SD | T5 |
| internship:enterprise:task_write | internship:enterprise:task_write | Task; review submission/report | ENTERPRISE | MENTOR đúng group/case; Placement ACTIVE | HR→MENTOR | T3 |
| internship:student:submission_write | internship:student:submission_write | Task status/submission/weekly report | STUDENT | Own case IN_PROGRESS; file/version; terminal cấm ghi | SELF | T3 |
| internship:enterprise:evaluate | internship:enterprise:evaluate | Draft/submit rubric evaluation | ENTERPRISE | MENTOR đúng case; không tự publish | HR→MENTOR | T3 |
| matching:enterprise:aggregate_read | matching:enterprise:aggregate_read | Protected aggregate/release | ENTERPRISE | Own demand/release; privacy ledger; band only | CORE | T4 |

### D.2. Mã bổ sung

| Permission canonical | Permission legacy | Hành động | Role ceiling | Scope / điều kiện bổ sung | Grantor | Team owner |
|---|---|---|---|---|---|---|
| school:delegation:manage | Không có code đã lưu; alias nếu có ở D.3 | Grant/revoke school capabilities | SCHOOL_STAFF | Đúng school/term; subset quyền và thời hạn được grant; cấm self escalation | ADMIN demo bootstrap; SD | T1 |
| schoolRosterImport:manage | Không có code đã lưu; alias nếu có ở D.3 | Preview/confirm/read/resume/resend import | SCHOOL_STAFF | School suy từ term/job; current delegation/version/cooldown | SD | T1 |
| student:preference:self:manage | Không có code đã lưu; alias nếu có ở D.3 | Submit/withdraw Preference | STUDENT | Owner; withdraw trước REVIEWED | SELF | T2 |
| student:preference:school:review | Không có code đã lưu; alias nếu có ở D.3 | Review Preference | SCHOOL_STAFF | Delegation school/term; current intent/version | SD | T2 |
| student:consent:self:manage | Không có code đã lưu; alias nếu có ở D.3 | Grant/revoke consent | STUDENT | Owner; recipient/purpose/fields/expiry; không consent thay SV | SELF | T2 |
| student:eligibility:school:approve | Không có code đã lưu; alias nếu có ở D.3 | Đặt/thu hồi schoolApproved | SCHOOL_STAFF | School/term; reason/decisionRef/version | SD | T2 |
| opportunity:school:publish | Không có code đã lưu; alias nếu có ở D.3 | Publish/close Opportunity projection | SCHOOL_STAFF | Cooperation APPROVED; school/term; version/window | SD | T5 |
| t5:proposal:manage | Không có code đã lưu; alias nếu có ở D.3 | Reservation/issue/revoke invitation | SCHOOL_STAFF | School/term; quota/eligibility/state/version | SD | T5 |
| student_profile:share:school:manage | Không có code đã lưu; alias nếu có ở D.3 | School policy; grant/revoke explicit share | SCHOOL_STAFF | School/term; consent hiện hành; subset fields | SD | T5 |
| enterprise:cooperation:submit | Không có code đã lưu; alias nếu có ở D.3 | Submit/resubmit/reopen Cooperation | ENTERPRISE | Owner; org VERIFIED; school verification APPROVED; version | CORE | T5 |
| enterprise_verification:enterprise:manage | Không có code đã lưu; alias nếu có ở D.3 | Draft/update/submit verification | ENTERPRISE | Owner; immutable school; attempt/state/version | CORE | T5 |
| enterprise_verification:school:review | Không có code đã lưu; alias nếu có ở D.3 | Approve/reject/request changes verification | SCHOOL_STAFF | Assigned school; current submission; reason/version | SD | T5 |
| proposal:enterprise:decide | Không có code đã lưu; alias nếu có ở D.3 | Enterprise accept/decline proposal | ENTERPRISE | Owner; valid share/consent; item version | CORE | T5 |
| matching:run:execute | Không có code đã lưu; alias nếu có ở D.3 | Evaluate matching | ENTERPRISE hoặc SCHOOL_STAFF | Own demand hoặc delegated school/term; VALID criteria | CORE hoặc SD | T4 |
| matching:school:detail_read | Không có code đã lưu; alias nếu có ở D.3 | Đọc MatchResult chi tiết | SCHOOL_STAFF | Delegation school/term; không enterprise/admin fallback | SD | T4 |
| matching:school:nominate | Không có code đã lưu; alias nếu có ở D.3 | MATCH → proposal T5 | SCHOOL_STAFF | Delegation; current run/criteria/profile; quota; đồng thời t5:proposal:manage | SD | T4/T5 |
| taxonomy:skills:read | Không có code đã lưu; alias nếu có ở D.3 | Đọc skill/alias | ALL | Authenticated; không trả dữ liệu sinh viên | CORE | T4 |
| taxonomy:skills:write | Không có code đã lưu; alias nếu có ở D.3 | CRUD category/skill/alias | SCHOOL_STAFF hoặc ADMIN | Named global TAX assignment; không role mặc định | TAX | T4 |
| t3:coordinator:confirm | Không có code đã lưu; alias nếu có ở D.3 | Xác nhận enterprise coordinator | SCHOOL_STAFF | Delegation + T5 school verification + enterprise/term | SD | T3 |
| t3:hr:manage | Không có code đã lưu; alias nếu có ở D.3 | Grant/revoke HR | ENTERPRISE | Confirmed coordinator đúng enterprise/school/term; không tự grant | EC | T3 |
| t3:cohorts:manage | Không có code đã lưu; alias nếu có ở D.3 | Cohort/Placement import/grouping | ENTERPRISE | HR đúng enterprise/school/term/group | EC→HR | T3 |
| t3:mentors:manage | Không có code đã lưu; alias nếu có ở D.3 | Assign/revoke/handover Mentor | ENTERPRISE | HR đúng group/case; active ENTERPRISE membership | EC→HR | T3 |
| t3:mentor:invite | Không có code đã lưu; alias nếu có ở D.3 | Mời Mentor qua scoped port | ENTERPRISE | HR; mọi group thuộc scope; không mời ADMIN | EC→HR | T1/T3 |
| t3:workspace:read | Không có code đã lưu; alias nếu có ở D.3 | Cohort/case/task/report/result/dashboard | STUDENT hoặc ENTERPRISE hoặc SCHOOL_STAFF | Self case; assigned HR/Mentor; school delegate; result visibility gate | SELF/HR/SD | T3 |
| t3:comments:write | Không có code đã lưu; alias nếu có ở D.3 | Append task comment | STUDENT hoặc ENTERPRISE | Self assignment hoặc assigned Mentor; case active | SELF/HR | T3 |
| t3:grades:publish | Không có code đã lưu; alias nếu có ở D.3 | Publish; authorize evaluation revision | SCHOOL_STAFF | Delegation school/term; current evaluation | SD | T3 |
| t3:grade_visibility:manage | Không có code đã lưu; alias nếu có ở D.3 | HR bật/tắt hiển thị điểm | ENTERPRISE | HR đúng org/term/case; không sửa điểm/school policy | EC→HR | T3 |
| t3:result_policy:manage | Không có code đã lưu; alias nếu có ở D.3 | School result visibility | SCHOOL_STAFF | Delegation school/term; default false; version | SD | T3 |
| t3:cases:close | Không có code đã lưu; alias nếu có ở D.3 | Normal/early close Case | SCHOOL_STAFF | Delegation; close gates; decisionRef/reason/version | SD | T3 |
| t3:enterprise_quality:school:write | Không có code đã lưu; alias nếu có ở D.3 | Enterprise quality feedback | SCHOOL_STAFF | School/collaboration scope; rubric riêng | SD | T3 |
| t3:coordination:manage | Không có code đã lưu; alias nếu có ở D.3 | Coordination issue | ENTERPRISE hoặc SCHOOL_STAFF | HR hoặc delegate đúng case; không sửa Cooperation | HR/SD | T3 |
| file:upload | Không có code đã lưu; alias nếu có ở D.3 | Private multipart upload | ALL | RESOURCE authorizer xác nhận quyền ghi/purpose | RESOURCE | T1 |
| file:download | Không có code đã lưu; alias nếu có ở D.3 | Metadata/content/Range | ALL | RESOURCE read; CLEAN cho bytes; consent/share/assignment hiện hành | RESOURCE | T1 |

### D.3. Alias và tách quyền

| Tên trong SRS/legacy adapter | Canonical/action | Giới hạn |
|---|---|---|
| audit:read:global | audit:global:read | Chỉ audit global |
| student:profile:self:read | student_profile:self:read | Owner |
| student:profile:self:write | student_profile:self:write | Owner/version |
| enterprise:demand:manage | cooperation:enterprise:create | Chỉ Demand authoring; không mở rộng submit/approve |
| t5:placement:approve | placement:school:write | Chỉ command APPROVE, vẫn đủ gates |
| t3:tasks:write | internship:enterprise:task_write | MENTOR đúng assignment |
| t3:assignment:self:submit | internship:student:submission_write | Own assignment |
| account:admin:manage | Không grant/không alias rộng | Tách status/revoke/membership theo action |
| student:profile:self:read_write | Không grant/không alias rộng | Tách read và write |
| NOT_GRANTED; NOT_IMPLEMENTED | Không phải permission | Sentinel của CSV, không seed/lookup như code |

Không seed alias rộng hay dùng prefix matching. Unknown code DENY. T1 sở hữu school delegation; T3 sở hữu COORDINATOR/HR/MENTOR; enum Role vẫn STUDENT/ENTERPRISE/SCHOOL_STAFF/ADMIN. SchoolDelegation phải có profileReadFields:ProfileField[]; có student_profile:school:read thì field mask không rỗng, là subset field mask được phép cấp của grantor; không có quyền đó thì []. ADMIN bootstrap demo không được tự cấp PII cho mình, không grant business capability bằng global ADMIN ngoài bootstrap được audit. TAX không được school delegate tự cấp cho toàn dự án.

Grant/revoke membership/capability và account status phải tăng securityVersion của các user bị ảnh hưởng cùng transaction, history/audit. Root coordinator/delegation bị revoke làm descendant capability mất quyền ngay; invalidation gồm các descendant user theo index cấp quyền, không chỉ grantor. Request kế trên session cũ trả401 SESSION_EXPIRED; đăng nhập lại vẫn phải kiểm assignment hiện hành. Global ADMIN muốn làm nghiệp vụ phải có business membership + delegation độc lập, đúng scope. Không cấp ADMIN cho HR/Mentor.

### D.4. Oracle kiểm thử quyền

| ID | Positive Given–When–Then | Negative Given–When–Then |
|---|---|---|
| PER-01 | Staff được grant import school A/term X → preview hợp lệ201 | Cùng staff dùng term của B →404; giả schoolOrgId trong body →400 và không effect |
| PER-02 | Mentor đúng group review latest submission →201 | HR không Mentor hoặc Mentor group khác →403/404 |
| PER-03 | Global ADMIN đọc audit →200 | Chỉ global ADMIN đọc student profile/file →403, không bytes |
| PER-04 | Enterprise có share + consent + fields hợp lệ →200 projection | Thu hồi consent rồi GET hoặc Range bằng link cũ →403/404 |
| PER-05 | School publisher publish current SUBMITTED evaluation →200 | Mentor/HR publish →403; school B dùng ID của A →404 |
| PER-06 | Named TAX editor update skill →200 | School delegate chỉ có school scope hoặc ADMIN không TAX →403 |
| PER-07 | Grant/revoke hợp lệ làm securityVersion tăng một lần | Session cũ request kế →401; login mới không khôi phục grant bị revoke |
| PER-08 | Alias audit:read:global normalize đúng canonical | account:admin:manage hoặc unknown code không thành wildcard |

Các oracle trên là SPEC PROPOSED, **NOT TESTED**; không có thay đổi quyền nào được áp dụng trong phiên này.


## E. Audit Action Registry 1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** Giữ 30 enum source; bổ sung 63 exact action. Không có mã wildcard IMPORT_* hoặc PLACEMENT_* để emit. Audit là lịch sử thao tác; domain event là thông điệp tích hợp, hai registry khác nhau.

### E.1. Envelope, kiểu dữ liệu và chính sách lỗi

Giữ các cột actor_id, action, resource_type, resource_id, organization_id, result, request_id, reason_code, source, event_version, created_at. actorId/resourceId/organizationId là UUID hoặc null, không phải email/MSSV hay chuỗi SYSTEM. source của worker là SYSTEM_WORKER, actorId=null, initiatedBy:U có thể bổ sung cho worker bắt nguồn từ lệnh người dùng. RequestIdFilter hiện sinh UUID và không tin request ID inbound: response/audit dùng cùng request attribute. Event consumer dùng traceId từ trusted event và ghi eventId trong inbox; không cho browser tùy đặt trace.

Đề xuất thêm metadata_json JSON NULL, giới hạn 4096 ký tự JSON canonical. Mỗi bảng dưới là allowlist, không phải yêu cầu ghi tất cả key; key không có giá trị được bỏ qua, không tự sinh PII để điền. initiatedBy:U và operationId:U là common allowlist khi có trusted command. Null hợp lệ theo bản ghi lịch sử, không rewrite. Các key *Id là U. securityVersion, version, aggregateVersion, jobVersion, rowVersion, claimVersion, consentVersion, shareVersion, placementVersion, caseVersion, profileVersion, evaluationVersion, assignmentVersion, rosterVersion, reportVersion, previewVersion, checkpointVersion, demandVersion, criteriaVersion, templateVersion, cohortVersion và runVersion là V; submissionNo/revisionNo là số đếm. ruleVersion, taxonomyVersion, scoringVersion, completenessVersion và academicCatalogVersion là Code; profileSnapshotVersion là H. Không dùng wildcard *Version để ép mọi version thành số. Counts là int không âm; attemptNo=1..4 cho mail/scan; sizeBytes là long không âm; timestamps là I; weekStart là D; hash/fingerprint là H; fieldCodes là ProfileField[]; changedFieldCodes là tên field schema, không chứa value. Các action enum/decision/verdict/state/errorClass phải khớp DTO/state catalog của tài liệu, không nhận free text. decisionRef là Ref không chứa dữ liệu cá nhân. resourceType khớp [a-z][a-z0-9_]{0,79}; reasonCode khớp Reason.

organizationId: account self/admin toàn cục có thể null; membership/org dùng scope org tương ứng; school delegation/import/eligibility/consent/share/proposal/Placement và school actions dùng schoolOrgId; task/report/evaluation/file workspace dùng enterpriseOrgId; privacy/match enterprise release dùng enterpriseOrgId, school detail dùng schoolOrgId. Org đối tác cần truy vết dùng UUID metadata được liệt kê; không chọn ngẫu nhiên theo actor hiện tại.

Mutation thành công + audit + outbox/operation cùng REQUIRED transaction. Unknown action/metadata hoặc DB audit failure phải ném lỗi chuyên biệt, map500 AUDIT_WRITE_FAILED và rollback; đây là thay đổi đề xuất, không phải hành vi đã có (IllegalArgumentException hiện có thể map400). Denial ghi REQUIRES_NEW; nếu ghi denial lỗi vẫn DENY và phát cảnh báo kỹ thuật đã lọc, không cấp quyền. COMPLETED_WITH_ERRORS vẫn audit SUCCESS với counts/state, không thêm result PARTIAL. Replay mutation không ghi success lần hai; sensitive GET mới ghi access mới.

Metadata fileIds:U[0..20]; producer submission mới dùng fileIds, legacy fileId chỉ khi một file, không ghi đồng thời hai dạng. INVITATION_RESPONDED.decision là ACCEPT/DECLINE/WITHDRAW do server suy từ command, không giới hạn nhầm vào request enum ACCEPT/DECLINE; PREFERENCE_REVIEWED.decision=REVIEW. Các operation metadata: DEMAND_CHANGED=CREATE/UPDATE/REOPEN/SOFT_DELETE/RESTORE; COOPERATION_DRAFT_SAVED=CREATE/REOPEN; OPPORTUNITY_PUBLICATION_CHANGED=PUBLISH/CLOSE; TAXONOMY_CHANGED=CREATE/UPDATE/DEPRECATE/DELETE_ALIAS với entityType=CATEGORY/SKILL/ALIAS; COORDINATION_ISSUE_CHANGED=CREATE/START/RESOLVE.

Không ghi password, raw/hash token, session ID, CSRF, API secret, encrypted token payload, nội dung mail/CV/comment, fullName/email/MSSV, storageKey hay absolute path. Audit không phải nơi lưu chi tiết lỗi import có PII.

### E.2. Action hiện hữu giữ nguyên

| Action canonical | Legacy mapping | Result | Resource type (producer mới) | Metadata cho phép | Producer |
|---|---|---|---|---|---|
| LOGIN | LOGIN | SUCCESS/FAILURE | user | reasonCode | T1 |
| LOGOUT | LOGOUT | SUCCESS | user | — | T1 |
| PASSWORD_CHANGED | PASSWORD_CHANGED | SUCCESS | user | securityVersion | T1 |
| PASSWORD_RESET | PASSWORD_RESET | SUCCESS | user | securityVersion | T1 |
| INVITATION_CREATED | INVITATION_CREATED | SUCCESS | account_invitation | purpose,role,operationId | T1 |
| ACCOUNT_ACTIVATED | ACCOUNT_ACTIVATED | SUCCESS | user | purpose,operationId,rosterId | T1 |
| ACCOUNT_STATUS_CHANGED | ACCOUNT_STATUS_CHANGED | SUCCESS | user | fromStatus,toStatus,securityVersion,reasonCode | T1 |
| MEMBERSHIP_GRANTED | MEMBERSHIP_GRANTED | SUCCESS | membership | role,scopeOrgId,securityVersion,operationId | T1 |
| MEMBERSHIP_REVOKED | MEMBERSHIP_REVOKED | SUCCESS | membership | role,scopeOrgId,securityVersion,reasonCode,operationId | T1 |
| SESSIONS_REVOKED | SESSIONS_REVOKED | SUCCESS | user | securityVersion,reasonCode | T1 |
| ACCESS_DENIED | ACCESS_DENIED | DENIED | http_request | reasonCode,knownActionCode | T1 guards |
| ORG_CREATED | ORG_CREATED | SUCCESS | organization | organizationType | T1 |
| ORG_DEMO_VERIFIED | ORG_DEMO_VERIFIED | SUCCESS | organization | demoFixtureRef | T1 demo |
| STUDENT_PROFILE_UPDATED | STUDENT_PROFILE_UPDATED | SUCCESS | student_profile | profileVersion,changedFieldCodes | T2 |
| STUDENT_EVIDENCE_ADDED | STUDENT_EVIDENCE_ADDED | SUCCESS | student_evidence | fileId,evidenceType,profileVersion | T2 |
| STUDENT_PROFILE_ACCESSED | STUDENT_PROFILE_ACCESSED | SUCCESS | student_profile | accessPurpose,fieldCodes,consentId,consentVersion,shareId,shareVersion | T1/T2 |
| INVITATION_RESPONDED | INVITATION_RESPONDED | SUCCESS | internship_invitation | decision,invitationVersion,operationId | T5 |
| ENTERPRISE_VERIFIED | ENTERPRISE_VERIFIED | SUCCESS | enterprise_verification | schoolOrgId,submissionNo,version,decisionRef | T5 |
| COOPERATION_REVIEWED | COOPERATION_REVIEWED | SUCCESS | cooperation | decision,submissionNo,version,reasonCode | T5 |
| PROFILE_SHARED | PROFILE_SHARED | SUCCESS | profile_share | consentId,consentVersion,shareVersion,fieldCodes,expiresAt | T5 |
| PROFILE_SHARE_REVOKED | PROFILE_SHARE_REVOKED | SUCCESS | profile_share | shareVersion,reasonCode | T5 |
| PLACEMENT_CREATED | PLACEMENT_CREATED | SUCCESS | placement | termId,placementVersion,operationId | T5 |
| PLACEMENT_TERMINATED | PLACEMENT_TERMINATED | SUCCESS | placement | fromStatus,toStatus,reasonCode,placementVersion | Legacy |
| TASK_ASSIGNED | TASK_ASSIGNED | SUCCESS | task_assignment | taskId,caseId,groupId,assignmentVersion | T3 |
| SUBMISSION_CREATED | SUBMISSION_CREATED | SUCCESS | submission | assignmentId,versionNo,fileId,fileIds,lateFlag | T3 |
| SUBMISSION_REVISED | SUBMISSION_REVISED | SUCCESS | submission | assignmentId,versionNo,previousSubmissionId,fileId,fileIds,lateFlag | T3 |
| EVALUATION_SUBMITTED | EVALUATION_SUBMITTED | SUCCESS | evaluation | caseId,templateVersion,evaluationVersion | T3 |
| EVALUATION_PUBLISHED | EVALUATION_PUBLISHED | SUCCESS | evaluation | caseId,evaluationVersion,decisionRef | T3 |
| MATCH_AGGREGATE_VIEWED | MATCH_AGGREGATE_VIEWED | SUCCESS | protected_release | releaseFingerprint,eligibleBand,ruleVersion | T4 |
| MATCH_EXPORT_ATTEMPTED | MATCH_EXPORT_ATTEMPTED | SUCCESS/DENIED | protected_release | format,reasonCode,releaseFingerprint | T4 |

INVITATION_CREATED lịch sử hiện dùng resource user/null; adapter vẫn đọc được. Producer mới dùng account_invitation; không rewrite bản ghi cũ. PLACEMENT_TERMINATED tiếp tục đọc được, producer mới dùng action cụ thể dưới đây.

### E.3. Action bổ sung exact

| Action canonical | Legacy mapping | Result | Resource type | Metadata cho phép | Producer |
|---|---|---|---|---|---|
| CAPABILITY_GRANTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | capability_assignment | permissionCode,scopeKind,scopeId,termId,assignmentKind,validFrom,validUntil,securityVersion,decisionRef | T1/T3 |
| CAPABILITY_REVOKED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | capability_assignment | permissionCode,scopeKind,scopeId,termId,assignmentKind,securityVersion,reasonCode | T1/T3 |
| MENTOR_INVITATION_ACCEPTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | account_invitation | enterpriseOrgId,assignmentId,operationId | T1/T3 |
| STUDENT_IMPORT_PREVIEWED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_import_job | totalRows,validRows,invalidRows,needsReviewRows,previewVersion,fileHash | T1 |
| STUDENT_IMPORT_CONFIRMED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_import_job | selectedCount,jobVersion,operationId | T1 |
| STUDENT_IMPORT_RESUMED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_import_job | retryRowCount,jobVersion,operationId | T1 |
| STUDENT_IMPORT_COMPLETED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_import_job | jobState,totalRows,processedRows,provisionedRows,alreadyLinkedRows,reviewRows,failedRows | T1 |
| STUDENT_IMPORT_FAILED | Không có mã lưu cũ; alias ở E.4 | FAILURE | student_import_job | jobVersion,errorClass,checkpointVersion | T1 |
| STUDENT_ROSTER_PROVISIONED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_import_row | rosterId,rowNo,operationId,rosterVersion | T1/T2 port |
| STUDENT_ROSTER_ALREADY_LINKED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_import_row | rosterId,rowNo,operationId,rosterVersion | T1/T2 port |
| ACCOUNT_INVITATION_REVOKED | Không có mã lưu cũ | SUCCESS | account_invitation | purpose,reasonCode,operationId | T1 |
| ACTIVATION_INVITATION_REISSUED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | account_invitation | rowId,operationId,reasonCode,expiresAt | T1 |
| ACTIVATION_EMAIL_SENT | Không có mã lưu cũ; alias ở E.4 | SUCCESS | email_outbox | messageId,attemptNo,operationId | T1 worker |
| ACTIVATION_EMAIL_FAILED | Không có mã lưu cũ; alias ở E.4 | FAILURE | email_outbox | messageId,attemptNo,errorClass,terminal,operationId | T1 worker |
| CONSENT_GRANTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_consent | recipientOrgId,schoolOrgId,termId,purpose,fieldCodes,consentVersion,expiresAt | T2 |
| CONSENT_REVOKED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_consent | consentVersion,reasonCode | T2 |
| STUDENT_ELIGIBILITY_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_term_eligibility | schoolOrgId,termId,fromApproved,toApproved,version,decisionRef | T2 |
| PREFERENCE_SUBMITTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_preference | opportunityId,termId,version | T2 |
| PREFERENCE_WITHDRAWN | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_preference | version,reasonCode | T2 |
| PREFERENCE_REVIEWED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | student_preference | version,decision,decisionRef | T2 |
| ENTERPRISE_VERIFICATION_DRAFT_SAVED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | enterprise_verification | schoolOrgId,submissionNo,version | T5 |
| ENTERPRISE_VERIFICATION_SUBMITTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | enterprise_verification | schoolOrgId,submissionNo,version | T5 |
| ENTERPRISE_VERIFICATION_REJECTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | enterprise_verification | schoolOrgId,submissionNo,version,reasonCode | T5 |
| ENTERPRISE_VERIFICATION_CHANGES_REQUESTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | enterprise_verification | schoolOrgId,submissionNo,version,missingFieldCodes,reasonCode | T5 |
| DEMAND_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | demand | operation,demandVersion,criteriaVersion | T5 |
| COOPERATION_DRAFT_SAVED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | cooperation | operation,version | T5 |
| COOPERATION_SUBMITTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | cooperation | submissionNo,demandVersion,version | T5 |
| OPPORTUNITY_PUBLICATION_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | cooperation | publishedAt,closedAt,version,operation | T5 |
| SCHOOL_PROFILE_SHARING_POLICY_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | school_sharing_policy | schoolOrgId,termId,fieldCodes,version | T5 |
| PROPOSAL_ITEM_RESERVED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | proposal_item | batchId,reservationUntil,version,operationId | T5 |
| PROPOSAL_ITEM_DECIDED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | proposal_item | fromStatus,toStatus,version,reasonCode,operationId | T5 |
| INTERNSHIP_INVITATION_ISSUED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | internship_invitation | proposalItemId,expiresAt,version,operationId | T5 |
| INTERNSHIP_INVITATION_EXPIRED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | internship_invitation | version,expiredAt | T5 |
| INTERNSHIP_INVITATION_REVOKED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | internship_invitation | version,reasonCode | T5 |
| PLACEMENT_APPROVED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | placement | termId,placementVersion,decisionRef,operationId | T5 |
| PLACEMENT_ACTIVATED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | placement | termId,placementVersion,effectiveAt,operationId | T5 |
| PLACEMENT_CANCELLED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | placement | termId,placementVersion,decisionRef,reasonCode,operationId | T5 |
| PLACEMENT_COMPLETED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | placement | termId,placementVersion,caseId,caseVersion,decisionRef,operationId | T5 |
| COHORT_CREATED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | internship_cohort | enterpriseOrgId,termId,cohortVersion | T3 |
| GROUP_MEMBERSHIP_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | internship_case | cohortId,fromGroupId,toGroupId,version,reasonCode | T3 |
| TASK_UPDATED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | task_assignment | taskId,caseId,fromStatus,toStatus,assignmentVersion | T3 |
| TASK_COMMENTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | task_assignment | commentId,assignmentVersion | T3 |
| SUBMISSION_REVIEWED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | submission | assignmentId,reviewedVersion,verdict,assignmentVersion | T3 |
| WEEKLY_REPORT_DRAFT_SAVED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | weekly_report | caseId,weekStart,revisionNo,reportVersion | T3 |
| WEEKLY_REPORT_REVISION_CREATED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | weekly_report | caseId,weekStart,revisionNo,reportVersion,reasonCode | T3 |
| WEEKLY_REPORT_SUBMITTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | weekly_report | caseId,weekStart,revisionNo,lateFlag | T3 |
| WEEKLY_REPORT_REVIEWED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | weekly_report | caseId,weekStart,revisionNo,reportVersion | T3 |
| EVALUATION_DRAFT_SAVED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | evaluation | caseId,templateVersion,evaluationVersion | T3 |
| EVALUATION_REVISION_CREATED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | evaluation | caseId,previousEvaluationId,revisionNo,decisionRef | T3 |
| GRADE_VISIBILITY_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | grade_visibility | enterpriseOrgId,caseId,enabled,version | T3 |
| SCHOOL_RESULT_VISIBILITY_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | school_result_policy | schoolOrgId,termId,enabled,version,decisionRef | T3 |
| CASE_CLOSED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | internship_case | placementId,caseVersion,decision,decisionRef,reasonCode | T3 |
| ENTERPRISE_QUALITY_EVALUATED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | enterprise_quality_evaluation | enterpriseOrgId,schoolOrgId,termId,version | T3 |
| COORDINATION_ISSUE_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | coordination_issue | enterpriseOrgId,schoolOrgId,operation,version | T3 |
| TAXONOMY_CHANGED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | skill_taxonomy_entry | entityType,operation,skillCode,taxonomyVersion | T4 |
| MATCH_RUN_REQUESTED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | match_run | demandId,criteriaHash,criteriaVersion,ruleVersion,taxonomyVersion | T4 |
| MATCH_RUN_COMPLETED | Không có mã lưu cũ; alias ở E.4 | SUCCESS/FAILURE | match_run | demandId,runVersion,status,ruleVersion | T4 |
| MATCH_DETAILS_VIEWED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | match_run | schoolOrgId,termId,page,size | T4 |
| PRIVACY_RELEASE_CREATED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | protected_release | demoBatchId,releaseFingerprint,eligibleBand,ruleVersion | T4 |
| PRIVACY_RELEASE_BLOCKED | Không có mã lưu cũ; alias ở E.4 | DENIED | protected_release | demoBatchId,reasonCode,releaseFingerprint | T4 |
| FILE_UPLOADED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | file | purpose,contentType,sizeBytes,scanStatus | T1 |
| FILE_SCAN_COMPLETED | Không có mã lưu cũ; alias ở E.4 | SUCCESS/FAILURE | file | scanStatus,attemptNo,reasonCode | T1 worker |
| FILE_DOWNLOADED | Không có mã lưu cũ; alias ở E.4 | SUCCESS | file | purpose,resourceType,resourceId,rangeRequest,consentVersion,shareVersion | T1 |

### E.4. Compatibility mapping và domain event

| Legacy/source label | Canonical/result | Điều kiện |
|---|---|---|
| LOGIN_SUCCESS | LOGIN / SUCCESS | Không tạo action enum mới |
| LOGIN_FAILURE; LOGIN_FAILED | LOGIN / FAILURE | Giữ nguyên source enum |
| MEMBERSHIP_CHANGED | MEMBERSHIP_GRANTED hoặc MEMBERSHIP_REVOKED | Bắt buộc changeType=GRANT/REVOKE; thiếu thì reject |
| ROLE_GRANTED; ROLE_REVOKED | MEMBERSHIP_GRANTED; MEMBERSHIP_REVOKED | Chỉ khi có membership/role/scope, không dùng cho HR/Mentor capability |
| PROFILE_SHARE_GRANTED | PROFILE_SHARED | Cùng share ID/version |
| PROFILE_SHARE_READ | STUDENT_PROFILE_ACCESSED | T1/T2 access facade, field codes đã cho phép |
| GRADE_SUBMITTED; GRADE_PUBLISHED | EVALUATION_SUBMITTED; EVALUATION_PUBLISHED | Evaluation ID/version |
| GRADE_VISIBILITY_CHANGED | GRADE_VISIBILITY_CHANGED | Giữ exact canonical mới |
| COOPERATION_APPROVED; COOPERATION_REJECTED | COOPERATION_REVIEWED | decision=APPROVE/REJECT |
| INVITATION_ISSUED; INVITATION_DECIDED; INVITATION_EXPIRED | INTERNSHIP_INVITATION_ISSUED; INVITATION_RESPONDED; INTERNSHIP_INVITATION_EXPIRED | Chỉ invitationKind=INTERNSHIP; không nhầm T1 activation |
| SUBMISSION_REVIEWED; REVIEWED | SUBMISSION_REVIEWED | REVIEWED chỉ nhận khi resourceType=submission |
| MATCH_RESULTS_VIEWED | MATCH_AGGREGATE_VIEWED hoặc MATCH_DETAILS_VIEWED | audience=ENTERPRISE/SCHOOL bắt buộc |
| MATCH_EXPORT | MATCH_EXPORT_ATTEMPTED | SUCCESS protected export; DENIED raw/breakdown |
| PLACEMENT_TERMINATED | PLACEMENT_TERMINATED | Giữ đọc lịch sử; không đoán CANCELLED hay COMPLETED |

Các tên PlacementApproved/PlacementActivated/PlacementCancelled/PlacementCompleted ánh xạ audit producer T5 tương ứng PLACEMENT_APPROVED/ACTIVATED/CANCELLED/COMPLETED; CaseClosed → CASE_CLOSED bởi T3; StudentAccountActivated → ACCOUNT_ACTIVATED bởi T1. Domain event không được truyền trực tiếp vào AuditService.valueOf. Consumer chỉ audit mutation mình thực sự áp dụng; inbox duplicate/stale không ghi lại producer success.

AuditEventPort.write(actor, {action:AuditAction,resourceType:string,resourceId:U|null,organizationId:U|null,result:SUCCESS|FAILURE|DENIED,reasonCode:Reason|null,metadata:allowlist-object,eventVersion:1}) chạy cùng transaction owner. Actor/request/source lấy trusted context; API user không có route ghi action tùy ý. GET /api/v1/admin/audit-logs giữ core filter/page contract và audit:global:read; metadata trả chỉ registry-safe, không sửa/xóa tùy tiện.

**AC-AUDIT:** Given dữ liệu LOGIN/SUCCESS và PLACEMENT_TERMINATED cũ; When nâng registry; Then vẫn đọc được. Given producer emit PlacementActivated như action hoặc metadata token; When write; Then reject và rollback mutation, không500 giả thành success. Given SMTP retry; When lần4 thất bại; Then một terminal FAILURE với terminal=true, không ghi token. Tất cả **NOT TESTED**.


## F. CP-IMPORT1 — Import Job & Email State Machines, version1.0

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** Thay nội dung thiếu của §25.8.4/T91–T93 theo B04–B06; giữ file5MiB/5000rows, preview30phút, token48giờ, MSSV dạng chuỗi, T1 identity/T2 roster/T5 UI. Đây là contract target, chưa có implementation.

### F.1. Upload, preview, tiến độ, rows, confirm, resume và resend

Owner API/job/token/mail T1; T2 cung cấp StudentRosterProvisionPort; T5 sở hữu UI cán bộ trường. Tất cả route school đòi SCHOOL_STAFF + schoolRosterImport:manage có delegation hiện hành. Preview lấy schoolOrgId từ TermReadPort(termId) rồi kiểm quyền; các request còn lại lấy school từ job. Không nhận schoolOrgId của client làm quyền, không tin org trong file. Staff có nhiều trường chọn term hợp lệ của trường được grant. POST có session/CSRF và Idempotency-Key UUID; foreign job/row trả404 RESOURCE_NOT_FOUND.

```text
JobState=VALIDATING|PREVIEW_READY|EXPIRED|PROCESSING|COMPLETED|COMPLETED_WITH_ERRORS|FAILED
ValidationState=VALID|INVALID|NEEDS_REVIEW
RowState=NOT_SELECTED|QUEUED|PROCESSING|PROVISIONED|ALREADY_LINKED|NEEDS_REVIEW|PERMANENT_FAILED|TRANSIENT_FAILED
MailState=NOT_REQUIRED|NOT_READY|QUEUED|SENDING|RETRY_WAIT|SENT|FAILED|SUPERSEDED|CANCELLED
ActivationState=NOT_REQUIRED|PENDING|ACTIVE|BLOCKED
ImportJobDto {importJobId:U,jobVersion:V,jobState:JobState,fileHash:H,termId:U,previewExpiresAt:I,totalRows:int,validRows:int,invalidRows:int,previewNeedsReviewRows:int,selectedRows:int,queuedRows:int,processingRows:int,transientFailedRows:int,provisionedRows:int,alreadyLinkedRows:int,needsReviewRows:int,permanentFailedRows:int,processedRows:int,successRows:int,startedAt:I|null,finishedAt:I|null,observedAt:I}
ImportSourceData {studentCode:Text(1..32)|null,email:Email|null,fullName:Text(2..150)|null,majorCode:Code|null,termCode:Code|null,cohortCode:Code|null,classCode:Code|null}
ImportRowDto {rowId:U,rowNo:int[2..5001],rowVersion:V,sourceData:ImportSourceData,validationState:ValidationState,selected:boolean,rowState:RowState,errorCodes:ImportRowError[],rosterId:U|null,provisioningEffectId:U|null,userId:U|null,provisioningOutcome:CREATED_PENDING|REUSED_PENDING|LINKED_EXISTING|null,activationState:ActivationState,invitationVersion:V|null,currentMailId:U|null,mailState:MailState}
ImportPreviewDto {job:ImportJobDto,rows:Page<ImportRowDto>}
ConfirmImport {expectedJobVersion:V,fileHash:H,selectedRowIds:U[1..5000]}
ImportAccepted {importJobId:U,jobVersion:V,jobState:PROCESSING,selectedRows:int}
ResumeImport {expectedJobVersion:V}
ResendActivation {expectedRowVersion:V,expectedInvitationVersion:V}
ResendAccepted {rowId:U,rowVersion:V,invitationVersion:V,mailId:U,mailState:QUEUED,acceptedAt:I}
ImportRowError=STUDENT_CODE_REQUIRED|STUDENT_CODE_MUST_BE_TEXT|STUDENT_CODE_INVALID|EMAIL_REQUIRED|EMAIL_INVALID|FULL_NAME_REQUIRED|FULL_NAME_INVALID|MAJOR_INVALID|TERM_CODE_INVALID|DUPLICATE_IN_FILE|IDENTITY_CONFLICT|ACCOUNT_BLOCKED|MEMBERSHIP_REVOKED|ROSTER_METADATA_CONFLICT|TRANSIENT_DEPENDENCY|AUTHORITY_REVOKED|REFERENCE_DATA_CHANGED|PERMANENT_VALIDATION
```

sourceData chỉ chứa giá trị staging của chính file trong scope trường, không chèn PII lấy từ account/trường khác để giải thích conflict. Giá trị không parse được trả null với error code; giữ bản raw riêng trong staging có kiểm soát, không log. rowNo là số thứ tự bản ghi đã parse, header=1 (CSV quoted newline không làm tăng số record). userId/mail/activation là projection hiện hành của claim; không cập nhật fan-out mọi job row khi account kích hoạt.

| Endpoint exact | Request | Thành công | Lỗi domain |
|---|---|---|---|
| POST /api/v1/school/student-imports/preview | multipart file,termId; key header | 201 ImportPreviewDto, job PREVIEW_READY; rows page0,size25 | 413 FILE_TOO_LARGE;422 UNSUPPORTED_FILE_FORMAT/FILE_STRUCTURE_INVALID/IMPORT_ROW_LIMIT_EXCEEDED;503 SCANNER_UNAVAILABLE |
| POST /api/v1/school/student-imports/{importJobId}/confirm | ConfirmImport; key header | 202 ImportAccepted | 409 PREVIEW_EXPIRED/PREVIEW_STALE/STATE_CONFLICT;422 INVALID_ROW_SELECTION |
| GET /api/v1/school/student-imports/{importJobId} | none | 200 ImportJobDto | 404 foreign/missing; không trả token |
| GET /api/v1/school/student-imports/{importJobId}/rows | page,size; validationState?,rowState?,mailState?; sort=rowNo ASC,id ASC | 200 Page&lt;ImportRowDto&gt; | 400 INVALID_PAGINATION/INVALID_REQUEST |
| POST /api/v1/school/student-imports/{importJobId}/resume | ResumeImport; key header | 202 ImportAccepted khi còn unfinished rows;200 ImportJobDto terminal khi chỉ cần recovery finalize | 409 JOB_IN_PROGRESS nếu lease đang chạy;409 STATE_CONFLICT nếu không FAILED |
| POST /api/v1/school/student-imports/{importJobId}/rows/{rowId}/resend | ResendActivation; key header | 202 ResendAccepted; job/counters không đổi | 409 ACCOUNT_ALREADY_ACTIVE/ACTIVATION_NOT_PENDING/STATE_CONFLICT/MAIL_SEND_IN_PROGRESS;429 RESEND_RATE_LIMITED |

Upload và preview là một POST multipart canonical; không thêm bước upload roster thứ hai.

Compatibility import: các tên T91 cũ là target tài liệu, chưa có API source để phá. Thay tại chỗ jobId→importJobId, previewVersion→jobVersion, fileSha256→fileHash, expiresAt→previewExpiresAt, expectedPreviewVersion→expectedJobVersion, selectedRowNumbers→selectedRowIds; row number hiển thị vẫn giữ rowNo. Không nhận đồng thời hai schema. Client prototype phải lấy rowId do preview trả, không tự tạo UUID từ rowNo. Placeholder path {jobId}/{importJobId} không đổi URL. Body idempotencyKey target cũ chuyển sang header UUID; không tuyên bố adapter legacy import đã tồn tại. CSV UTF-8 (BOM được phép), dấu phẩy. Theo T18, năm header bắt buộc là studentCode,email,fullName,majorCode,termCode; thứ tự được đổi. cohortCode/classCode là metadata tùy chọn, không tạo cohort T3. schoolOrgId không là nguồn quyền; nếu file có cột này phải bằng trường suy từ term/delegation, khác trả403 ACCESS_DENIED toàn request. schoolApproved là cột bị cấm (422 FILE_STRUCTURE_INVALID/FORBIDDEN_COLUMN). Không header trùng hoặc cột lạ ngoài danh sách trên; .xlsx một worksheet dữ liệu, hàng1 cùng header, không merged cells/formula trong vùng dữ liệu. Trailing rows rỗng hoàn toàn bỏ qua; hàng rỗng giữa dữ liệu là INVALID. MSSV1..32 ký tự sau trim, giữ case và số0 đầu; ô Excel phải kiểu text. fullName2..150 theo T18, email theo canonical T1, majorCode phải active catalog đúng term. termCode từng row phải bằng canonical TermReference.termCode của termId đã chọn; sai là INVALID/TERM_CODE_INVALID, không tự tạo/mapping mơ hồ mã kỳ. CSV chứa công thức không được thực thi, Excel formula bị INVALID theo field; không tự đoán numeric MSSV.

File không đọc được/sai encoding/header/container hoặc &gt;5000 data rows: lỗi toàn file422, không có preview selectable. Oversize413. File đúng cấu trúc nhưng có lỗi dòng, kể cả mọi dòng đều INVALID:201 PREVIEW_READY; cả hai dòng trùng MSSV theo case hoặc email canonical trong cùng file đều INVALID/DUPLICATE_IN_FILE. Identity/school/MSSV conflict phát hiện lúc preview là NEEDS_REVIEW, không VALID; do đó không selectable. Conflict mới phát sinh sau confirm trở thành terminal row NEEDS_REVIEW. Reference/catalog trở nên không hợp lệ sau confirm, trước row commit: PERMANENT_FAILED/REFERENCE_DATA_CHANGED. Không tự mở một workflow quyết định liên kết danh tính để lấp thiếu scope.

Preview chỉ staging và file quarantine/scan, không user/roster/token/profile. Chỉ trả201 khi file CLEAN và phân loại hoàn tất; scanner timeout retry policy CP-FILE; scanner unavailable503, không preview có thể confirm. Không khẳng định độ trễ đọc5000rows khi chưa benchmark. Preview lưu hash bytes, canonical row hash, catalog/term/uniqueness snapshot. Confirm sau auth/schema/idempotency replay kiểm theo thứ tự: nếu job EXPIRED hoặc PREVIEW_READY và serverNow&gt;=previewExpiresAt thì409 PREVIEW_EXPIRED; state khác PREVIEW_READY thì409 STATE_CONFLICT; sau đó expectedJobVersion (409 STATE_CONFLICT), fileHash/catalog/term/row snapshot (409 PREVIEW_STALE), rồi selectedRowIds nonempty/distinct/thuộc job/VALID (422 INVALID_ROW_SELECTION). Reaper đổi EXPIRED không làm cùng điều kiện hết hạn trả ngẫu nhiên STATE_CONFLICT. selectedRowIds là set; server không nhận lại dữ liệu nguồn để ghi đè snapshot. Tại now=expiresAt đã hết hạn. Worker xử lý selected rows theo rowNo tăng dần; lỗi reference/catalog mới phát sinh sau confirm là PERMANENT_FAILED/REFERENCE_DATA_CHANGED. Preview validationState/counters giữ nguyên lịch sử, không đổi theo outcome xử lý.

### F.2. StudentRosterProvisionPort (T1→T2)

```text
EnsureRoster {schoolOrgId:U,studentCode:Text(1..32),termId:U,majorCode:Code,emailCanonical:Email,fullName:Text(2..150),cohortCode:Code|null,classCode:Code|null,expectedCatalogVersion:Code,operationId:U}
AccountOnboardingRef {userId:U|null,status:AccountStatus|null,hasRequiredMembership:boolean,securityVersion:V|null}
AccountProvisioningPort.inspectForOnboarding(actor:ActorContext,input:{emailCanonical:Email,scopeOrgId:U,purpose:ROSTER|MENTOR}):Result<AccountOnboardingRef>
StudentAccountActivatedEvent {eventId:U,eventType:StudentAccountActivated,schemaVersion:1,aggregateId:U,aggregateVersion:V,userId:U,rosterId:U,rosterVersion:V,schoolOrgId:U,termIds:U[],occurredAt:I,traceId:U}
RosterProvisionResult {rosterId:U,rosterVersion:V,status:CREATED_PENDING|REUSED_PENDING|ALREADY_LINKED|NEEDS_REVIEW,userId:U|null,reasonCode:OK|IDENTITY_CONFLICT|ACCOUNT_BLOCKED|MEMBERSHIP_REVOKED|ROSTER_METADATA_CONFLICT}
BindActivatedUser {rosterId:U,userId:U,expectedRosterVersion:V,operationId:U}
BindResult {rosterId:U,userId:U,rosterVersion:V,linkedTermIds:U[],profileCreated:boolean}
StudentRosterProvisionPort.ensureRoster(actor,EnsureRoster,mutation):Result<RosterProvisionResult>
StudentRosterProvisionPort.bindActivatedUser(actor,BindActivatedUser,mutation):Result<BindResult>
StudentRosterProvisionPort.readForActivation(actor,rosterId:U):Result<{rosterId:U,rosterVersion:V,schoolOrgId:U,studentCode:Text(1..32),emailCanonical:Email,userId:U|null,termIds:U[]}>
```

T1 orchestrator và T2 public port dùng cùng transaction manager. AccountProvisioningPort thuộc T1, chỉ trusted onboarding purpose có school/enterprise authority được gọi; không có REST tra email công khai. ABSENT trả userId/status/securityVersion=null và hasRequiredMembership=false; có user thì status/account guard hiện hành quyết định xử lý, không để T2 tự suy từ email. T2 ensureRoster chỉ quản dữ liệu roster; T1 xác nhận account/membership trước khi chốt ALREADY_LINKED. AccountOnboardingRef chỉ trả cho purpose trusted: ABSENT có các nullable field=null, hasRequiredMembership=false; giá trị này không được serialize qua UI hoặc dùng làm API user enumeration.

StudentAccountActivatedEvent là thông báo sau bind atomic: aggregateId=userId, aggregateVersion=claimVersion tại accept, rosterVersion là phiên bản đã bind. Inbox T2 kiểm claim/binding authoritative và ACK duplicate/stale; không tạo profile lần hai hoặc overwrite eligibility. Worker dùng retry/DLQ của BC41. Event chỉ có IDs/version, không email/token/password. Identity mới không phải domain event thay cho audit ACCOUNT_ACTIVATED. T2 không quản password/email identity canonical, T1 không ghi repository T2. New email: provisioning commit roster+rosterTerm, claim/current token/encrypted mail outbox/effect/audit; **chưa tạo users/membership/student_profile/StudentTermEligibility**. Accept mới materialize identity/profile/eligibility. Existing ACTIVE chỉ ALREADY_LINKED khi school+MSSV đã xác thực và STUDENT membership hợp lệ; term mới schoolApproved=false; giữ mọi approval/profile/consent/mật khẩu hiện hữu. Existing ACTIVE chưa liên kết hoặc membership bị revoke →NEEDS_REVIEW, không tự thêm trường/role.

Hai job cùng school+MSSV+term+email: unique effect và claim lock cho một provisioning effect; row thứ hai PROVISIONED/REUSED_PENDING (hoặc ALREADY_LINKED nếu đã ACTIVE), không phát token/mail mới chỉ vì import lại. Để thống nhất T93/R5, REISSUE_ACTIVATION là khả năng qua resend/worker rotation có kiểm soát ở F.5, không phải reissue vô điều kiện ở mỗi import. Khác metadata canonical →NEEDS_REVIEW. T2 readForActivation là trusted workflow purpose, không public PII query.

### F.3. Ví dụ đủ field và lỗi xác định

Multipart hợp lệ: file=students-demo.csv,termId=44444444-4444-4444-8444-444444444444; Idempotency-Key=00000000-0000-4000-8000-000000000201. Fixture gồm1dòng hợp lệ và1dòng numeric MSSV. Response201:

```json
{"job":{"importJobId":"11111111-1111-4111-8111-111111111111","jobVersion":1,"jobState":"PREVIEW_READY","fileHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","termId":"44444444-4444-4444-8444-444444444444","previewExpiresAt":"2026-11-03T03:30:00Z","totalRows":2,"validRows":1,"invalidRows":1,"previewNeedsReviewRows":0,"selectedRows":0,"queuedRows":0,"processingRows":0,"transientFailedRows":0,"provisionedRows":0,"alreadyLinkedRows":0,"needsReviewRows":0,"permanentFailedRows":0,"processedRows":0,"successRows":0,"startedAt":null,"finishedAt":null,"observedAt":"2026-11-03T03:00:00Z"},"rows":{"content":[{"rowId":"33333333-3333-4333-8333-333333333333","rowNo":2,"rowVersion":1,"sourceData":{"studentCode":"000001","email":"student001@example.test","fullName":"Sinh viên Demo 001","majorCode":"SE","termCode":"2026-T1","cohortCode":null,"classCode":null},"validationState":"VALID","selected":false,"rowState":"NOT_SELECTED","errorCodes":[],"rosterId":null,"provisioningEffectId":null,"userId":null,"provisioningOutcome":null,"activationState":"NOT_REQUIRED","invitationVersion":null,"currentMailId":null,"mailState":"NOT_REQUIRED"},{"rowId":"44444444-4444-4444-8444-444444444444","rowNo":3,"rowVersion":1,"sourceData":{"studentCode":null,"email":"student002@example.test","fullName":"Sinh viên Demo 002","majorCode":"SE","termCode":"2026-T1","cohortCode":null,"classCode":null},"validationState":"INVALID","selected":false,"rowState":"NOT_SELECTED","errorCodes":["STUDENT_CODE_MUST_BE_TEXT"],"rosterId":null,"provisioningEffectId":null,"userId":null,"provisioningOutcome":null,"activationState":"NOT_REQUIRED","invitationVersion":null,"currentMailId":null,"mailState":"NOT_REQUIRED"}],"page":0,"size":25,"totalElements":2,"totalPages":1}}
```

Confirm request và success202 (key mới):

```json
{"expectedJobVersion":1,"fileHash":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","selectedRowIds":["33333333-3333-4333-8333-333333333333"]}
```
```json
{"importJobId":"11111111-1111-4111-8111-111111111111","jobVersion":2,"jobState":"PROCESSING","selectedRows":1}
```

Lỗi toàn file422:

```json
{"errorCode":"FILE_STRUCTURE_INVALID","error":"FILE_STRUCTURE_INVALID","message":"Tệp thiếu cột studentCode.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[{"field":"columns.studentCode","code":"REQUIRED_COLUMN_MISSING","message":"Thiếu cột bắt buộc."}],"retryAfterSeconds":null}
```

Confirm hết hạn409:

```json
{"errorCode":"PREVIEW_EXPIRED","error":"PREVIEW_EXPIRED","message":"Bản xem trước đã hết hạn. Vui lòng tải lại tệp.","requestId":"00000000-0000-4000-8000-000000000099","fieldErrors":[],"retryAfterSeconds":null}
```

Catalog/hash đổi trả409 cùng envelope với code PREVIEW_STALE, message yêu cầu preview lại. Retry cùng key confirm trả nguyên ImportAccepted gốc theo C.0; import job mới cùng sinh viên trả terminal row tham chiếu cùng roster/effect theo F.2, không trả lỗi duplicate giả.

### F.4. ImportJob/ImportRow state transition và counters

| From | Command / guard | To / effect |
|---|---|---|
| VALIDATING | File CLEAN, cấu trúc hợp lệ, phân loại và staging commit | PREVIEW_READY; không domain student effect |
| VALIDATING | Lỗi hạ tầng trước response | FAILED nội bộ; POST trả503; không selectable preview; client upload/preview lại |
| PREVIEW_READY | serverNow&gt;=previewExpiresAt, chưa confirm | EXPIRED; không domain effect |
| PREVIEW_READY | Confirm hợp lệ và selectedRows&gt;0 | PROCESSING; selected VALID rows→QUEUED |
| PROCESSING | Tất cả selected row terminal thành công | COMPLETED |
| PROCESSING | Không unfinished, có NEEDS_REVIEW/PERMANENT_FAILED | COMPLETED_WITH_ERRORS |
| PROCESSING | Hạ tầng/lease crash hoặc row hết retry vẫn transient | FAILED; giữ checkpoint/effects |
| FAILED | Resume hợp lệ, có unfinished committed-selection rows | PROCESSING; chỉ unfinished rows |
| COMPLETED/COMPLETED_WITH_ERRORS/EXPIRED | Mail retry/resend/activation | Giữ nguyên job; không reopen provisioning |

| Row from | Trigger | Row to / effect |
|---|---|---|
| NOT_SELECTED | Confirm chọn VALID row | QUEUED; selected=true; không chọn invalid/review |
| QUEUED | Worker có lease/fencing hợp lệ | PROCESSING |
| PROCESSING | Roster+claim/current token/mail outbox/effect/audit commit | PROVISIONED; CREATED_PENDING hoặc REUSED_PENDING |
| PROCESSING | Identity ACTIVE đã linked đúng scope | ALREADY_LINKED; không tạo user/token/mail |
| PROCESSING | Identity/membership/metadata conflict cần xác minh | NEEDS_REVIEW; không auto-merge |
| PROCESSING | Dữ liệu không hợp lệ vĩnh viễn phát sinh lúc xử lý | PERMANENT_FAILED; mã cố định |
| PROCESSING | Lỗi dependency/DB tạm thời, TX rollback | TRANSIENT_FAILED; chưa committed effect |
| TRANSIENT_FAILED | Retry budget còn; đến hạn | PROCESSING cùng operation/effect key |
| PROVISIONED/ALREADY_LINKED/NEEDS_REVIEW/PERMANENT_FAILED | Mail/activation hoặc resume | Row state terminal giữ nguyên; chỉ projection mail/activation thay đổi |

Retry row: lần đầu +3retry sau1/5/30giây. Hết retry vẫn transient →job FAILED, không đổi lỗi hạ tầng thành COMPLETED_WITH_ERRORS. Resume cấp budget mới cho unfinished rows, giữ key/effect. Job lease120giây, heartbeat30giây, leaseEpoch fencing; worker phải kiểm epoch trong TX row. Reaper sau lease hết hạn kiểm committed rows trước: nếu tất cả selected row terminal thì finalize COMPLETED/COMPLETED_WITH_ERRORS, không đánh FAILED giả; nếu còn unfinished thì FAILED và reclaim PROCESSING chưa committed thành TRANSIENT_FAILED. Row committed không chạy lại. Transaction row cuối phải chốt counters, terminal job và completion audit cùng lúc; finalizer/reaper dùng unique operation(jobId,IMPORT_FINALIZE) chống duplicate. Resume FAILED có0unfinished chỉ thực hiện finalize, trả200 ImportJobDto terminal thay vì202PROCESSING. Confirm/resume lưu initiating actor/delegation version; trước mỗi row kiểm authority hiện hành. Revoke →FAILED với lastErrorCode=AUTHORITY_REVOKED, không xử lý tiếp; cán bộ khác có quyền cùng school có thể resume và trở thành actor mới được audit.

```text
selectedRows = queuedRows + processingRows + transientFailedRows
             + provisionedRows + alreadyLinkedRows + needsReviewRows + permanentFailedRows
processedRows = provisionedRows + alreadyLinkedRows + needsReviewRows + permanentFailedRows
successRows = provisionedRows + alreadyLinkedRows
COMPLETED iff processedRows=selectedRows AND needsReviewRows+permanentFailedRows=0
COMPLETED_WITH_ERRORS iff processedRows=selectedRows AND needsReviewRows+permanentFailedRows>0
```

Preview counts tính mọi source row; provisioning counts chỉ selected. GET job lấy một DB snapshot nhất quán, không các COUNT ở thời điểm khác nhau. Cập nhật row/checkpoint/counter/jobVersion cùng TX; có thể tính lại counts từ rows nếu counter cache sai. jobVersion tăng mỗi committed thay đổi provisioning, không tăng theo SMTP/activation. startedAt từ confirm; finishedAt là lần kết thúc hiện tại (FAILED/COMPLETED*), reset null khi resume; observedAt là thời điểm read. POST resume không áp dụng VALIDATING-failed không có selection; trường hợp đó preview lại.

Ledger cross-job chỉ lưu thành công. NEEDS_REVIEW/PERMANENT_FAILED không tạo permanent success effect; job cũ giữ terminal, fresh preview/job có thể đánh giá lại sau khi authoritative data được người có quyền sửa. Không tự tạo workflow review mới trong phạm vi sáu blocker.

Counterexamples bắt buộc:5000rows có200INVALID,4800selected đều thành công →COMPLETED,success4800,invalid200; không giả5000 imported.2PROVISIONED+1ALREADY_LINKED+1NEEDS_REVIEW →COMPLETED_WITH_ERRORS,processed4,success3.1TRANSIENT_FAILED chưa giải quyết →FAILED, không completed. SMTP down hoặc student chưa click không ngăn COMPLETED khi durable outbox/effects đã commit. ALREADY_LINKED là thành công với createdUserCount0. Global report dùng COUNT(DISTINCT rosterId/effectId) theo chỉ tiêu xác định, không SUM(successRows) qua nhiều job.

### F.5. Claim, token và mail recovery

T1 invitation_claims có UNIQUE emailCanonical làm lock anchor trước khi users tồn tại. ClaimContext=ROSTER|MENTOR|LEGACY; context pending khác scope/purpose không được bị takeover: REVIEW_REQUIRED/NEEDS_REVIEW, không merge. ClaimState=PENDING|ACTIVATED|REVIEW_REQUIRED; claimVersion/generation/currentTokenId được quản cùng TX. Trong activation/reissue, lấy claim bằng hash token/email đã canonical từ server; lock claim →user nếu có →T2 roster/term theo ID tăng dần →token/mail. All workflows dùng thứ tự này; không giữ SMTP network call trong DB TX.

TokenState là projection theo thứ tự ưu tiên cố định: unknown token422 TOKEN_INVALID; known consumed409 TOKEN_ALREADY_USED; known revoked/obsolete generation409 TOKEN_REVOKED; current expired422 TOKEN_EXPIRED; chỉ còn ACTIVE mới có thể accept. Cụ thể: CONSUMED nếu consumedAt!=null; REVOKED nếu revokedAt!=null hoặc không còn current generation; EXPIRED nếu now&gt;=expiresAt; ACTIVE nếu còn lại và claim PENDING. Token hợp lệ còn phải đúng context/user/scope; claim không PENDING hoặc context/account bị khóa trả409 ACTIVATION_NOT_PENDING (sau các token consumed/revoked/expired checks), không dùng activation token làm reset token. TTL48giờ từ issuedAt UTC. Chỉ hash lưu one_time_tokens. Một current token/activation purpose/claim; replacement revoke token cũ trước insert mới.

Accept hiện hành qua CP-AUTH1 profile1.1: claim lock + password policy + token checks; user mới →tạo ACTIVE, membership STUDENT, bind T2 roster và profile/eligibility (new term schoolApproved=false), consume token, audit+StudentAccountActivated outbox+idempotent effect cùng TX. Với legacy PENDING_ACTIVATION đúng identity giữ userId và cho tự đặt password; ACTIVE/blocked từ flow khác không reset/merge. StudentAccountActivated chỉ thông báo/reconcile idempotent sau bind atomic, không là cơ chế duy nhất để liên kết nên không có khoảng user ACTIVE nhưng roster chưa bound. T3 Mentor dùng callback port riêng trong cùng workflow, không tạo STUDENT cho mentor.

| Mail from | Trigger/guard | To / expected effect |
|---|---|---|
| QUEUED | Worker claim lease60s; generation/token hợp lệ | SENDING |
| SENDING | SMTP/Mailpit chấp nhận | SENT; sentAt; không có nghĩa người dùng mở thư |
| SENDING | Temporary failure; còn retry | RETRY_WAIT; nextAttemptAt |
| RETRY_WAIT | Đến hạn, token hiện hành | SENDING cùng messageId/link |
| SENDING | Permanent SMTP failure hoặc hết3retry | FAILED; lưu errorClass đã lọc; audit FAILURE |
| SENDING | Lease hết hạn, kết quả SMTP không rõ | Reclaim→RETRY_WAIT cùng messageId nếu generation còn hợp lệ; at-least-once |
| QUEUED/RETRY_WAIT | Token hết hạn trước send | SUPERSEDED; atomically revoke + generation mới/token/mail QUEUED |
| QUEUED/RETRY_WAIT | Claim ACTIVE hoặc generation bị thay | CANCELLED hoặc SUPERSEDED; không gửi token sai |
| FAILED/SENT/QUEUED/RETRY_WAIT | Staff resend hợp lệ, không SENDING lease live | Message cũ giữ terminal FAILED/SENT hoặc pending→SUPERSEDED; token cũ revoked; message mới QUEUED |
| SENDING với lease live | Staff resend | 409 MAIL_SEND_IN_PROGRESS; không rotate lúc transport đang gửi |

SMTP retry: initial+3retry5/30/120giây, tối đa4attempt/message; SMTP timeout hữu hạn theo application.yml hiện có. Worker batch20, concurrency2, global dispatch cap60attempt/phút mặc định, kiểm bằng DB dispatch lease/budget để nhiều instance không bypass; biến MAIL_DISPATCH_PER_MINUTE được cấu hình5..300 cho staging, acceptance fixture dùng60. Mailpit demo/SMTP staging thay bằng environment, không đổi nghiệp vụ, không tự gửi tới dữ liệu thật.

Resend eligibility: row PROVISIONED + claim PENDING + account chưa ACTIVE/blocked; job có thể COMPLETED/FAILED. Bao phủ mailFAILED, SENT chưa activate, pending QUEUED/RETRY_WAIT; không yêu cầu row INVITE_SENT. Chỉ thay token/mail, không ensureRoster/provision lại. Cooldown5phút từ issuance gần nhất; tối đa3manual reissue trong rolling24giờ theo canonical email+activation purpose, xuyên job/actor/row. Automatic rotation chỉ cho unsent backlog, key=(claimId,oldGeneration), không đếm manual quota và không phát lại vô hạn do import; giữ claim theo retention demo đã được cho phép.

Guard resend sau auth/scope và key replay: claim/account state (ACTIVE409 ACCOUNT_ALREADY_ACTIVE,blocked409 ACTIVATION_NOT_PENDING) →expectedRowVersion/expectedInvitationVersion (409 STATE_CONFLICT) →SENDING lease guard409 MAIL_SEND_IN_PROGRESS →cooldown/quota429 RESEND_RATE_LIMITED. Vì vậy hai key khác nhau cùng expected version: winner202; loser409 STATE_CONFLICT trước quota. 429 có Retry-After=ceil(số giây sớm nhất cả cooldown và rolling quota cùng thỏa), retryAfterSeconds bằng header.

Resend thành công tăng claim generation/version, revoke token cũ, tạo token/outbox mới+audit+operation atomic; response202 đầy đủ:

```json
{"rowId":"33333333-3333-4333-8333-333333333333","rowVersion":4,"invitationVersion":2,"mailId":"00000000-0000-4000-8000-000000000301","mailState":"QUEUED","acceptedAt":"2026-11-03T04:00:00Z"}
```

rowVersion phản ánh row provisioning nên resend không tăng rowVersion; ví dụ4 là version provisioning hiện hành. invitationVersion là claimVersion hiện hành tăng khi issue/activate/revoke; mail retry không tăng claimVersion. Mail hiện tại được resolve từ generation, không lấy mail copy của job cũ. Nếu token đã hết hạn sau SENT/FAILED, staff resend mới phát token; không để import retry bypass rate limit.

| Race/failure | Expected response / invariant |
|---|---|
| Activation commit trước resend | Accept200; new resend409 ACCOUNT_ALREADY_ACTIVE; đúng một identity/bind, không token mới |
| Resend commit trước activation bằng link cũ | Resend202; accept cũ409 TOKEN_REVOKED; link generation mới có thể accept |
| Hai accept cùng token | Một200; còn lại409 TOKEN_ALREADY_USED; không duplicate user/profile/membership |
| Hai resend cùng key/hash | Một generation/mail/audit-success; retry replay202 gốc sau current authorization |
| Hai resend khác key, cùng expected version | Một202; còn lại409 STATE_CONFLICT; một current token |
| Job COMPLETED nhưng mail FAILED | Resend202; job/counters giữ nguyên; không provisioning |
| Token expired queued trước send | Một worker rotation, oldmail SUPERSEDED; worker thứ hai no-op qua unique operation |
| SMTP accepted rồi network timeout | Có thể mail trùng cùng Message-ID/link; chỉ một current token và một identity effect |
| Old mail đang in-flight sau reissue | Có thể nhận thư cũ; backend từ chối old link409 TOKEN_REVOKED |
| Account blocked khi worker sắp gửi | Không gửi; pending mail CANCELLED; claim REVIEW_REQUIRED; staff không tự ACTIVE |

Worker cần token sau commit: private mail outbox lưu encrypted transport payload AES-256-GCM (env key, keyId/nonce/tag), không plaintext. one_time_tokens chỉ hash; domain outbox chỉ IDs, không ciphertext/token. Xóa encrypted payload khi SENT/FAILED/SUPERSEDED/CANCELLED; FAILED phục hồi bằng token mới. Hash/token/ciphertext không trả API/log/audit. Thừa nhận SMTP at-least-once; không tuyên bố exactly-once delivery từ messageId.

**AC-IMPORT:** các fixture và expected output tại F.3–F.5 và H là chuẩn consumer contract; toàn bộ **NOT TESTED**.


## G. Database/Data Dictionary bổ sung

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT. Không có DDL/migration được tạo hoặc chạy.** Giữ Java21, Spring Boot hiện hữu, MySQL8.4/InnoDB/UTF8MB4/Flyway, ddl-auto=validate. UUID lưu VARCHAR(36) cùng định dạng hiện tại, timestamps DATETIME(6) UTC, version BIGINT NOT NULL DEFAULT0, JSON chỉ metadata/snapshot đã có schema, không thay khóa quan hệ bằng JSON. Mọi update dùng WHERE id=? AND version=? hoặc khóa pessimistic cho tranh chấp liên aggregate; số affected rows sai phải rollback.

### G.1. Phần bổ sung để B01–B06 có thể triển khai

| Owner / bảng | Cột và nullability | Khóa / index / invariant |
|---|---|---|
| T1 student_import_jobs | id:U PK; school_org_id:U FK organizations; term_id:U FK T5 terms; actor_id:U FK users; state:JobState; version:BIGINT; file_hash:CHAR64; staging_file_id:U FK files; catalog_version:VARCHAR64; term_version:BIGINT; preview_expires_at:I; total_rows/selected_rows:int; last_error_code:VARCHAR64 NULL; lease_owner:VARCHAR100 NULL,lease_epoch:BIGINT,lease_until:I NULL; created_at:I,started_at/finished_at:I NULL | Index(school_org_id,state,created_at,id); index(state,lease_until); CHECK rows0..5000. Counters có thể materialize nhưng phải cập nhật cùng row hoặc tính từ một read snapshot. |
| T1 student_import_rows | id:U PK; job_id:U FK; row_no:int; row_hash:CHAR64; normalized_snapshot:JSON; validation_state; selected:boolean; row_state; error_codes:JSON; operation_id:U; provisioning_effect_id/roster_id/claim_id/user_id:U NULL; version:BIGINT; attempt_count:int; next_attempt_at:I NULL | UNIQUE(job_id,row_no); UNIQUE(job_id,operation_id); index(job_id,selected,row_state,row_no); index(row_state,next_attempt_at). FK refs owner tồn tại; CHECK selected=false→NOT_SELECTED cho preview rows. |
| T1 student_import_effects | id:U PK; effect_key:CHAR64; payload_hash:CHAR64; school_org_id:U; term_id:U; roster_id:U FK T2; claim_id:U NULL FK claims; user_id:U NULL; created_at:I | UNIQUE(effect_key). effectKey=SHA256 canonical school+studentCode+term+email; chỉ lưu successful committed PROVISIONED/ALREADY_LINKED, không đóng băng NEEDS_REVIEW. Metadata conflict không silent overwrite. Đây là ledger, không student registry thứ hai. |
| T1 invitation_claims | id:U PK; email_canonical:VARCHAR254; context_kind:ROSTER/MENTOR/LEGACY; school_org_id:U NULL; roster_id:U NULL; invitation_ref_id:U NULL; expected_user_id/user_id:U NULL FK users; claim_state:PENDING/ACTIVATED/REVIEW_REQUIRED; version:BIGINT; generation:BIGINT; current_token_id:U NULL FK one_time_tokens; last_issued_at:I NULL; created_at/updated_at:I | UNIQUE(email_canonical) theo chính sách uniqueness identity hiện hữu; lock anchor trước user. Pending context mới không takeover context khác. current_token thuộc chính claim/generation. ROSTER bắt school/roster, MENTOR bắt invitation_ref. |
| T1 one_time_tokens (bổ sung) | claim_id:U NULL FK; generation:BIGINT NULL; issued_at:I; revoked_at:I NULL; revocation_reason:VARCHAR64 NULL. Giữ token_hash,purpose,user_id,email,invite_role,organization_id,expires_at,consumed_at,created_at | Giữ token_hash UNIQUE. Generated current_invitation_claim_id=claim_id khi purpose=INVITATION và consumed_at/revoked_at đềuNULL, ngược lạiNULL; UNIQUE(current_invitation_claim_id). Không dùng NOW trong generated expression. Token expired phải revoke trước replacement để giải phóng unique. Password-reset legacy claimNULL giữ hành vi riêng. |
| T1 activation_mail_outbox | id:U PK; message_id:U; claim_id/token_id:U FK; generation:BIGINT; template_code:VARCHAR64; state:MailState (chỉ queue transport states); issuance_reason:INITIAL/MANUAL_RESEND/EXPIRED_BEFORE_DELIVERY; operation_id:U; initiated_by:U NULL; attempt_count:int; next_attempt_at:I NULL; lease_owner:VARCHAR100 NULL,lease_until:I NULL; version:BIGINT; encrypted_payload:VARBINARY NULL,key_id:VARCHAR64 NULL,nonce:BINARY12 NULL,tag:BINARY16 NULL; last_error_class:VARCHAR64 NULL; created_at:I,sent_at:I NULL | UNIQUE(message_id); UNIQUE(operation_id); UNIQUE(claim_id,generation,template_code); index(state,next_attempt_at,id); index(claim_id,issuance_reason,created_at) cho quota. CHECK encrypted quartet đồng thời NULL hoặc đủ; terminal payload bị xóa. NOT_REQUIRED/NOT_READY chỉ API projection, không outbox row state. |
| T1 mail_dispatch_budget | budget_key:VARCHAR64 PK; window_start:I; attempts:int; version:BIGINT; dispatcher lease fields nullable | Một budget global cho activation SMTP, không chia theo actor để né cap; claim/budget lock không giữ trong network call. Policy F.5 mặc định60/phút. |
| T2 student_rosters | id:U PK; school_org_id:U FK; student_code:VARCHAR32 COLLATE utf8mb4_0900_as_cs; user_id:U NULL FK users; requested_email:VARCHAR254; full_name:VARCHAR150; version:BIGINT | UNIQUE(school_org_id,student_code); UNIQUE(school_org_id,user_id), MySQL cho nhiềuNULL. MSSV trim giữ case/0; collation case-sensitive cụ thể, không thừa hưởng ai_ci làm gộp MSSV. |
| T2 student_roster_terms | roster_id:U FK; term_id:U FK; major_code:VARCHAR64; catalog_version:VARCHAR64; cohort_code/class_code:VARCHAR64 NULL; version:BIGINT | PK(roster_id,term_id); roster school phải khớp term.school. Tồn tại được trước user. |
| T2 student_profiles / student_term_eligibility | Profiles giữ UNIQUE(user_id); eligibility có user_id,school_org_id,term_id,student_code,major_code,school_approved:boolean,approved_by:U NULL,approved_at:I NULL,reason_code:VARCHAR64 NULL,version | UNIQUE eligibility(user_id,term_id); FK roster/term đúng school. Chỉ bind/materialize sau user tồn tại; new term false, không reset record cũ. History append-only cho decision. |
| T1 school_delegations | id:U PK; user_id/school_org_id/term_id:U FK; permission_codes:JSON exact registry; profile_read_fields:JSON ProfileField[]; parent_delegation_id:U NULL FK self; active:boolean; valid_from/valid_until:I; version; granted_by:U; decision_ref:VARCHAR120 | Index(user_id,school_org_id,term_id,active,valid_until); index(parent_delegation_id,active); không hai active grant cùng user/school/term/permission bằng grant-lines unique hoặc nullable generated active key. Không giả MySQL có partial unique index. |
| T3 context_assignments | id:U PK; kind:COORDINATOR/HR/MENTOR; user_id,school_org_id,enterprise_org_id,term_id:U; group_id/case_id:U NULL; parent_assignment_id:U NULL; active,valid_from,valid_until,version,granted_by,decision_ref | Index(user,enterprise,school,term,active); index(parent_assignment_id,active); generated normalized_scope_key thayNULL bằng sentinel an toàn + kind; UNIQUE active assignment(user,kind,normalized_scope_key) bằng nullable generated key. User có thể nhiều nhóm. |
| T1 files (bổ sung binding) | resource_type:ResourceType; resource_id:U; purpose:FilePurpose; scan_status:PENDING/CLEAN/REJECTED; scan_attempt:int; scanner_reason_code:VARCHAR64 NULL; scan_next_attempt_at:I NULL; version. Giữ storage_key/private metadata hiện hữu | Index(resource_type,resource_id,purpose); index(scan_status,scan_next_attempt_at); binding/purpose immutable; FileResourceAuthorizer kiểm resource thật, FK đa hình không thể giả FOREIGN KEY một cột tới nhiều bảng. Có adapter integrity check. |
| T1 audit_logs (bổ sung) | metadata_json:JSON NULL; giữ toàn bộ cột/action cũ, không thay kiểu ID | Index(org,created_at,id), action/time và request_id theo truy vấn hiện hữu; metadata whitelist trước write, không index PII. Nếu mở enum/check cần migration mới đã review, không update dữ liệu cũ. |
| T1 common operation_records | id:U PK; actor_id:U NULL; actor_scope_key:VARCHAR200; operation_code:VARCHAR100; idempotency_key:U; payload_hash:CHAR64; state:IN_PROGRESS/DONE; effect_ref:U NULL; response_status:int NULL,response_json:JSON NULL,response_expires_at:I NULL; created_at:I | UNIQUE(actor_scope_key,operation_code,idempotency_key), actor_scope_key normalized không nullable. Durable effect ref sống qua cache24h; rollback không DONE. Tokens/password không ở response_json. Tái dùng common ledger một nơi, không một bảng idempotency mỗi module. |
| T1 common domain_outbox / consumer_inbox | Outbox: event_id:U PK,producer,event_type,schema_version:int,aggregate_id:U,aggregate_version:BIGINT,payload:JSON,trace_id:U,state,attempts,next_attempt_at; inbox: consumer_code,event_id,aggregate_id,aggregate_version,applied_at | UNIQUE outbox(producer,aggregate_id,aggregate_version,event_type); inbox PK(consumer_code,event_id); apply/inbox/audit atomic; chỉ IDs/snapshot nghiệp vụ được phép, không raw token. |
| T5 terms / school_sharing_policy | terms:term_id,school_org_id,code,start_date,end_date,status,version; policy:school_org_id,term_id,allowed_fields:JSON,version,updated_by/at | UNIQUE terms(school_org_id,code); policy PK(school_org_id,term_id); missing policy trả version0/defaultfields theo BC24, không tự insert fake approved. |
| T2 consent lineage / T5 shares | lineage key(student_user_id,school_org_id,term_id,recipient_org_id,purpose),current_version; grant id/version/expiry/revoked/replaced; share id,consent_id,consent_version,proposal_item_id,fields,expiry,version | Unique lineage; regrant/revoke dưới lineage lock; share không sao chép profile. Index recipient/term/expiry và proposal. |
| T5 quota / proposals / placements | Batch UNIQUE cooperation_id; item UNIQUE(batch_id,student_user_id) cả declined; quota row theo demand/approved submission; placements version,state,student,term,proposal refs | placement nullable generated active_student_key=studentId khi state APPROVED/ACTIVE, active_term_key=termId cùng guard; UNIQUE(active_student_key,active_term_key). Mỗi proposal chỉ một Placement record UNIQUE(proposal_item_id), giữ cancel history. Approval/quota trong một TX. |
| T3 case / group / submissions / reports / evaluation | Case UNIQUE placement_id,version,last_applied_placement_version; current group membership; submission immutable version; report root+revisions; evaluation root/revision+published pointer | UNIQUE(cohort_id,case_id) current group theo active generated key; UNIQUE(assignment_id,version_no); thêm T3 t3_submission_files(submission_id:U FK submissions,file_id:U FK files,ordinal:int[1..20]), PK(submission_id,file_id), UNIQUE(submission_id,ordinal); UNIQUE(case_id,week_start) report root; UNIQUE(report_id,revision_no); UNIQUE(case_id,revision_no) evaluation. Đối chiếu các bảng T3 SRS trước allocate V-next. |
| T4 match / privacy release | Immutable snapshots/runs/results; protected_release enterprise,school,term,demo_batch_id,release_fingerprint,response_json,version | UNIQUE(enterprise_org_id,school_org_id,term_id,demo_batch_id); UNIQUE(run_id,student_user_id); index run/status/fitScore/user; protected DTO không raw counts. Lock release key trước publish, không dựa cache để chặn truy vấn khác. |
| T1 mentor_onboarding_invitations / mentor_invitation_groups; T3 pending_mentor_assignments | T1 invitation: id:U PK, email_canonical:Email, existing_user_id:U NULL FK users, enterprise_org_id/school_org_id/term_id:U FK owners, issued_by:U FK users, hr_assignment_id:U FK T3, decision_ref:VARCHAR120, claim_id:U NULL, state:MentorInviteState, version:BIGINT, created_at/expires_at:I, accepted_at/revoked_at:I NULL. Groups: invitation_id/group_id:U; T3 pending row có id,invitation_id,group_id,user_id NULL,version và active_assignment_id NULL. | T1 groups PK(invitation_id,group_id), FK group owner T3; invitation existing_user_id dùng owner GET/accept. T3 UNIQUE(invitation_id,group_id); canonical invitation state thuộc T1, pending assignment không là invitation thứ hai. Claim MENTOR.invitation_ref_id trỏ invitation này; ROSTER claim không dùng bảng Mentor. Active assignment validity theo công thức CP-MENTOR1. |

### G.2. Locking, tính toàn vẹn và rollout

Common idempotency chọn một cách phục hồi: IN_PROGRESS chỉ được giữ trong transaction đang thực thi, không commit reservation riêng rồi bỏ quên. UNIQUE key khóa các request cạnh tranh; chờ lock tối đa5giây rồi map409 REQUEST_IN_PROGRESS/Retry-After1. Domain effect/audit/outbox/DONE+response commit cùng transaction. Crash rollback toàn bộ transaction và giải phóng key; retry cùng key có thể thực hiện nếu chưa effect commit. Async job/file upload trả202 chỉ sau DONE của command nhận việc, worker dùng lease riêng. Không có IN_PROGRESS durable vô thời hạn cần người dùng đoán cách mở khóa.

Provisioning/accept locks claim trước user, rồi roster/term theo ID, token/outbox; worker mail chỉ claim/token/mail và không lấy job-row lock sau claim khi API giữ row lock. API resend đọc row optimistic không lock row trước claim. Domain decision T5 lấy quota →proposal/invitation →student-term active-key →consent/profile locks theo userId tăng dần; tất cả service công bố thứ tự này và retry DB deadlock như transient với cùng operation key. Unique DB là lớp bảo vệ cuối; không chỉ SELECT count rồi INSERT.

Foreign keys theo ownership được tạo ở cùng MySQL sau bảng owner; cross-module chỉ tham chiếu IDs/public port ở Java, không cascade delete hồ sơ/audit bằng cách xóa user. Dùng RESTRICT và account lifecycle; không hard delete lịch sử. Dữ liệu file binding đa hình cần integrity service thay FK giả.

Identity email uniqueness phải dùng cùng normalization/collation hiện hữu của users. Trước thêm claims unique cần kiểm collisions theo collation đó, không âm thầm đổi email toàn DB. Token legacy không claim phải backfill/reconcile bằng kế hoạch migration đã review: xác định context/current generation, revoke dư, giữ lịch sử. Không assume DB sạch, không chạy cleanup trong tài liệu này.

Cần migration V-next cho permission seeds, metadata/binding/capability/import/outbox và owner modules; T1 allocate version tuần tự toàn repository. Không tự đặt V8 cho mọi team, không sửa V1–V7 đã áp dụng. Review dữ liệu tồn tại trước NOT NULL/UNIQUE, nullable/backfill rồi validate constraint. ddl-auto=validate được giữ; không dùng update/create để bỏ qua mismatch.

Schema có thể triển khai trên MySQL8.4 nhưng chưa chạy migration/integration/benchmark. Không kết luận N+1 hay throughput đã đạt. Consumer list dùng pagination đã định nghĩa; projection/batch ports tránh truy vấn profile N lần, cần SQL plan/load evidence khi implementation.


## H. Acceptance Tests — toàn bộ NOT TESTED

Đây là bộ test có oracle để owner/consumer triển khai sau phê duyệt, không phải kết quả thực thi. Không chạy unit/integration/build/migration/load/SMTP trong phiên này. Dùng synthetic @example.test, Clock cố định và barrier kiểm race. Nghiệm thu dùng MySQL8.4, session/CSRF/scope và adapter scanner/SMTP thật; mock chỉ phục vụ unit/consumer contract.

| ID | Owner | Phạm vi | Given | When | Then / expected result | Kết quả |
|---|---|---|---|---|---|---|
| H01 | T1–T5 | API/port schema | Fixture đầy đủ theo mỗi family | Deserialize/gọi đúng scope/version | Đúng type/nullability/status; không mất ID/version/reference | NOT TESTED |
| H02 | T1–T5 | Schema âm tính | Thiếu required, enum sai hoặc field lạ | Gọi mutation với CSRF hợp lệ | 400 SCHEMA_VALIDATION; zero mutation/audit success/outbox | NOT TESTED |
| H03 | T1 | Core compatibility | Client cũ và audit/token history cũ | Gọi core API và activation legacy | Giữ method/body/status cũ; history đọc được | NOT TESTED |
| H04 | T1 | Session/CSRF | Thiếu CSRF; sau đó session hết hạn | Gọi protected mutation | 403 CSRF_INVALID hoặc401 SESSION_EXPIRED; không bỏ CSRF | NOT TESTED |
| H05 | T1–T5 | Idempotency concurrent | 20 request cùng actor/scope/key/hash | Gọi đồng thời | Một effect/outbox/audit success; replay gốc hoặc409 IN_PROGRESS | NOT TESTED |
| H06 | T1–T5 | Idempotency expiry | Cache24h hết nhưng effect còn | Retry cùng key; rồi đổi body | 409 IDEMPOTENCY_RESPONSE_EXPIRED; khác body409 IDEMPOTENCY_KEY_REUSED; không rerun | NOT TESTED |
| H07 | T1/T5 | School scope | Staff A có import, term/job/row của B | Thay URL/termId hoặc thêm schoolOrgId | Foreign404; field lạ400; không lộ PII/token/mail B | NOT TESTED |
| H08 | T1/T3 | Revoke | Session và assignment đang hợp lệ | Revoke rồi gọi request kế và login lại | Session cũ401; login mới vẫn403 nếu grant bị revoke | NOT TESTED |
| H09 | T1/T2 | ADMIN/PII | Global ADMIN không business delegation | GET profile/file Range | 403/404 và0bytes; không global bypass | NOT TESTED |
| H10 | T1/T3 | Mentor invitation | ACTIVE email, owner đúng; HR đúng group | Issue và owner accept rồi replay key | Một user/membership ENTERPRISE/assignment; không reset password/ADMIN | NOT TESTED |
| H11 | T1 | Audit alias | LOGIN_SUCCESS label và LOGIN/SUCCESS history | Normalize rồi ghi; thử action PlacementActivated | LOGIN/SUCCESS hợp lệ; event dùng nhầm action500 AUDIT_WRITE_FAILED/rollback | NOT TESTED |
| H12 | T1–T5 | Audit privacy/failure | Metadata có token/email; denial audit DB lỗi | Write hoặc truy cập action bị cấm | Reject metadata; rollback mutation; denial vẫn DENY | NOT TESTED |
| H13 | T1/T2/T3 | File | Oversize, spoofed MIME, scanner timeout | Upload và tải content | 413/422; hết retry→REJECTED; bytes chỉ CLEAN | NOT TESTED |
| H14 | T1/T2 | File consent | File CLEAN đã được share | Revoke commit rồi GET/Range link cũ | 403/404 và0bytes cho request mới | NOT TESTED |
| H15 | T2/T4 | Completeness/snapshot | Declared=false so với true[]; snapshot v3 | Evaluate; sửa v4 rồi nominate v3 | UNKNOWN khác FAIL; đúng6checks;409 PROFILE_STALE/zero quota effect | NOT TESTED |
| H16 | T2/T4 | Taxonomy/catalog | Exact alias và unknown alias; catalog sai school | Resolve/update profile/import | Unknown không tạo skill; invalid422 hoặc scope404 | NOT TESTED |
| H17 | T2/T5 | Eligibility | Complete và MATCH nhưng schoolApproved=false | Create/approve Placement | 422 SCHOOL_APPROVAL_REQUIRED/PLACEMENT_GATE_FAILED; không active Placement | NOT TESTED |
| H18 | T2/T5 | Preference/invitation | Intent REVIEWED; accepted invitation quá hạn phản hồi | Withdraw intent; đọc invite; withdraw sau Placement | 409 PREFERENCE_REVIEWED; invite vẫn ACCEPTED; sau Placement409 | NOT TESTED |
| H19 | T2/T5 | Consent race | Consent/share hiện hành | Regrant/revoke cùng lúc share/read | Một thứ tự serial; share không dựa lineage cũ; read sau revoke deny | NOT TESTED |
| H20 | T4/T5 | Strict/scoring | Required FAIL+UNKNOWN; Preferred2/3; no Preferred | Evaluate snapshot | NOT_MATCH scoreNULL; MATCH66.67; no Preferred0; tie userId ASC | NOT TESTED |
| H21 | T4 | Privacy band | Counts0,1,4,5,9,10,19,20 | GET JSON/chart | SUPPRESSED, SUPPRESSED, SUPPRESSED,5-9,5-9,10-19,10-19,20+; không raw/total/% | NOT TESTED |
| H22 | T4/T5 | Privacy guard | Release cùng org/school/term/batch đã có | Đổi actor/demand/criteria, gọi breakdown/export | Same fingerprint replay; khác409 PRIVACY_RELEASE_LOCKED; không release thứ hai | NOT TESTED |
| H23 | T5 | Atomic submit | Demand DRAFT hợp lệ; audit lỗi | Submit-with-cooperation | 500 AUDIT_WRITE_FAILED; rollback cả Demand/Cooperation/outbox | NOT TESTED |
| H24 | T5 | Quota/active key | Một slot;20 approvals cạnh tranh student/term | Approve đồng thời | Tối đa một APPROVED; không overbook; losers409 ACTIVE_PLACEMENT_EXISTS/CAPACITY_EXCEEDED | NOT TESTED |
| H25 | T5/T3 | Event order | Activated v3 trước Approved v2; source ACTIVE | Consume v3, duplicate v3, rồi v2 | Một case IN_PROGRESS/version3; ACK_DUPLICATE/ACK_STALE; không duplicate effect | NOT TESTED |
| H26 | T5/T3 | Event lag/cancel | Source CANCELLED4, local case IN_PROGRESS3 | Thử Task/Submission trước consumer | Authoritative guard deny; consumer chuyển TERMINATED; không echo CaseClosed | NOT TESTED |
| H27 | T3/T2 | Task/submission | Own assignment IN_PROGRESS; current submission | Submit cạnh tranh; review old; student PATCH COMPLETED | Một version; stale review409; invalid transition422 | NOT TESTED |
| H28 | T3/T2 | Weekly report | Case bắt đầu giữa tuần, giao ít nhất1ngày | Submit trước/sau next Tue23:59:59 HCM; revision | Đúng dueAt/late; old revision immutable; report chưa review chặn normal close | NOT TESTED |
| H29 | T3/T2 | Grade visibility | PUBLISHED; school hoặc HR gate=false | GET self; rồi đủ gates; Mentor thử publish | Hidden evaluation=null; đủ gates đúng score; Mentor403 | NOT TESTED |
| H30 | T3/T5 | Case close | Normal prerequisites thiếu; hoặc valid EARLY decision | Close và consume CaseClosed | Normal409 CASE_CLOSE_PRECONDITION_FAILED; early→TERMINATED/T5 CANCELLED; terminal cấm ghi | NOT TESTED |
| H31 | T1/T2/T5 | Import1000 | 1000 synthetic rows, SMTP tắt | Preview/confirm/worker | 1000 PROVISIONED, job COMPLETED, outbox durable; chưa user/profile trước accept | NOT TESTED |
| H32 | T1/T2/T5 | Import5000 | 4800VALID+200INVALID, file≤5MiB | Preview page25, confirm4800 | 201 counts4800/200,200pages; COMPLETED success4800, không5000 imported | NOT TESTED |
| H33 | T1 | File vs row | Thiếu header/5001rows; file hợp lệ có numeric MSSV/duplicates | Preview từng file | Whole-file422; row errors201; tất cả duplicate INVALID; confirm[]422 | NOT TESTED |
| H34 | T1/T2 | Preview stale | Now=expiresAt hoặc catalog đổi trước confirm | Confirm VALID selection | 409 PREVIEW_EXPIRED/PREVIEW_STALE; zero domain effects | NOT TESTED |
| H35 | T1/T2 | Cross-job | Hai job/key cùng school/MSSV/term/email pending | Provision cạnh tranh | Một roster/claim/token/mail/effect; row2 REUSED_PENDING; không né resend quota | NOT TESTED |
| H36 | T1/T2 | Existing ACTIVE | Đã linked đúng school/MSSV, dữ liệu cũ hợp lệ | Import thêm term; thử membership revoked | ALREADY_LINKED; term mới false; không overwrite; revoked→NEEDS_REVIEW | NOT TESTED |
| H37 | T1/T2 | Crash/resume | 600/1000rows committed, worker crash | Lease hết, reaper FAILED, resume | Chỉ400remaining;600effects giữ; worker cũ fenced | NOT TESTED |
| H38 | T1 | Counters/retry | 2PROVISIONED+1ALREADY+1review; job khác transient hết budget | Finalize và resume | First WITH_ERRORS processed4/success3; transient job FAILED, không completed | NOT TESTED |
| H39 | T1 | SMTP retry | Temporary fail3lần rồi accepted lần4; permanent fail | Worker với clock/backoff5/30/120 | Tối đa4attempt; SENT hoặcFAILED; job/counters không đổi | NOT TESTED |
| H40 | T1 | Expired backlog | Token hết48h, mail QUEUED; hai workers | Dispatch cạnh tranh | Một generation mới, old SUPERSEDED; một current token | NOT TESTED |
| H41 | T1/T5 | FAILED after completed | Job COMPLETED/mail FAILED, claim PENDING, cooldown đủ | Staff resend current version | 202 new mail/token, old revoked; không provisioning/job/counter đổi | NOT TESTED |
| H42 | T1 | Resend rate/version | Cooldown&lt;5min hoặc3manual/24h; hai keys cùng version | New resend; chạy concurrency | 429+Retry-After đúng; winner202/loser409 STATE_CONFLICT; không token thứ hai | NOT TESTED |
| H43 | T1/T2 | Activation/resend race | Valid token/current claim | Chạy cả hai thứ tự commit bằng barrier | Activation wins200/resend409 ACTIVE; resend wins202/old accept409 REVOKED | NOT TESTED |
| H44 | T1/T2 | Token expiry/replay | Known consumed/revoked/current expired token | Accept profile1.1 | 409 TOKEN_ALREADY_USED/409 TOKEN_REVOKED/422 TOKEN_EXPIRED; password policy12codepoints/72bytes | NOT TESTED |
| H45 | T1 | SMTP uncertain ack | SMTP đã nhận nhưng client timeout | Reclaim/retry cùng Message-ID | Có thể thư trùng; một token/effect; không claim exactly-once | NOT TESTED |
| H46 | T1–T5 | Migration compatibility | Fresh DB và snapshot V1–V7 có history | Future reviewed V-next + ORM validate | Giữ history, không duplicate versions/constraints; chưa thực thi trong nhiệm vụ này | NOT TESTED |
| H47 | T1/T4/T3 | Benchmark evidence | 400/5000profiles và50users, ghi môi trường | Future benchmark theo KPI Ch24 | Lưu p95/throughput/error thật; hiện không suy tốc độ từ số rows | NOT TESTED |

Mỗi operation trong C/F có positive schema test và negative missing-field/enum/stale/scope theo C-AC. Các family CP-AUTH1, CP-AUTHZ1, CP-FILE1, CP-MENTOR1, CP-WS1–5, BC11–12, BC21–24, BC31–32, BC41–42, CP-T5UI1, CP-IMPORT1 đều phải có consumer contract coverage. Generic schema test không thay security/integration test.


## I. Vị trí đề xuất cập nhật SRS

**PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** Hợp nhất tại chỗ, không append lại toàn báo cáo như chương mới. P/T là locator extraction, không phải số trang Word.

| Vị trí hiện hữu | Cập nhật tại chỗ | Nguồn proposal / owner |
|---|---|---|
| Ch6 §6.2–6.7, P094–P105/T17 | Thay mô tả registry/delegation/audit/file còn khái quát; giữ endpoint core | CP-AUTH1, CP-AUTHZ1, CP-FILE1, CP-MENTOR1, D/E; T1 |
| Ch6 §6.8, P106–P113 | Thay sequence import-row gắn với SMTP/activation và quy tắc completion | F/G, H31–H45; T1/T2/T5 |
| Ch7 §7.3–7.4, P118–P128/T22 | Điền profile/snapshot/catalog/eligibility/consent/preference DTO; T2 chỉ UI invitation/workspace | BC11–12, BC21–24, CP-WS; T2 |
| Ch8 §8.6–8.7/T27–T28 | Điền matching DTO/ports; giữ scoring/bands/privacy; không bật Should breakdown/implies | BC31–32, D/E; T4 |
| Ch9 §9.1–9.8, P156–P176 và Ch13.2/T42 | Chuẩn hóa NEEDS_CHANGES, T5 owner; exact REST schema/status và một nomination path | BC21–24, BC31, BC41–42, CP-T5UI1; T5 |
| Ch10 §10.3–10.5, P189–P194/T38–T39 | Điền workspace view/command DTO, HR/Mentor guard, visibility và CaseClosed proof | CP-AUTHZ1, CP-MENTOR1, CP-WS1–5, BC41; T3 |
| Ch20 §20.2–20.6, P313–P330/T66–T68 | Thay nhãn permission gộp bằng registry/alias exact; xác định field mask/owner/file/audit | D/E, BC24; T1 và owner |
| Ch22 §22.1, P353–P356/T71 | Giữ HTTP status class; detail errorCode và legacy adapter; requestId/idempotency | C.0, CP-AUTH1; T1 |
| Ch22 §22.2–22.4, P357–P370/T72–T73 | Thay DTO tối thiểu/placeholder bằng typed pack. CT01→BC11; CT02→BC31; CT03→BC32; CT04→BC23; CT05→BC24; CT06→BC41 | C/F; owner và consumer ký schema |
| Ch22 §22.5, P371–P376 | Điền inbox/outbox/ACK theo source hiện hành, retry/DLQ; tách private mail outbox | BC41, F.5, G; T1/T3/T5 |
| Ch23 §23.1–23.4, P378–P389/T74–T76 | Bổ sung dictionary/constraints/locking; tách AuditAction và DomainEvent; không sửa migrations cũ | G/E; T1 điều phối DB owner |
| §25.8.3, P453/T90 R2,R8,R10,R11,R14,R15 | Thay lời yêu cầu tự viết contract bằng tham chiếu exact C/D/E/F; giữ policy Placement/privacy đã chốt | B01–B03 và compatibility F; T1 |
| §25.8.4, P454–P457/T91–T93 | Thay T91 schema/names/errors; T92 tách job-row-mail-token; làm rõ T93R5 reissue có kiểm soát | F.1–F.5/G/H31–H45; T1/T2/T5 |
| §25.8.5, P458–P459/T94 | Giữ cổng signoff/evidence; proposal chưa approved, tests chưa chạy | J; Leader T1 và owners/consumers |

Các tên/JSON target cũ được gắn alias/deprecated rõ ràng, không có hai schema canonical. Tài liệu hợp nhất có version và signoff mới được ghi SPEC APPROVED. Không tự chạy các thay đổi được mô tả ở đây.

## J. Kết luận checkpoint v1.0 được giữ để truy vết

1. **B01–B06 có phương án đề xuất; chưa được đóng chính thức tại checkpoint này.** B01 có typed contract, owner, guard, lỗi, version và ví dụ; B02 giữ18 mã và bổ sung/mapping exact; B03 giữ30 action và registry mới; B04 trả đủ IDs/version/selection/hash/expiry; B05 chốt theo provisioning + durable outbox; B06 phục hồi mail FAILED qua resend serialized, không provisioning lại. Đây không phải xác nhận các blocker đã đóng trong SRS gốc hoặc source hiện tại.

2. **Cần ký bộ quyết định cụ thể này trước khi chấp thuận.** Leader T1 ký C.0, core compatibility/error adapter, D/E và phương án migration. T2+T5 ký roster/identity binding, consent/projection và catalog. T3+T5 ký Placement/case event/proof. T4+T2+T5 ký snapshot/nomination/privacy DTO. T1 Security/DB review claim locks, token transport encryption, backfill legacy tokens, field masks và unique/collation. Owners xác nhận đúng các schema/counters/retry/cooldown đã đề xuất, không để mỗi team tự chọn lại. Hiện **CHƯA CÓ phê duyệt**; không mở lại policy Demo/MVP đã FIXED.

3. **Chưa chốt GO từ checkpoint này.** Cần hoàn tất các mục rà soát còn lại ở đầu báo cáo, được owner/consumer phê duyệt và hợp nhất vào SRS trước khi giao triển khai theo contract. Công việc implementation và evidence còn gồm DTO/adapters/guards, migrations được review, private file/scanner, import/mail workers, token locks, UI và contract/integration/race/load tests. Chưa có kết quả nghiệm thu hoặc điều kiện vận hành dữ liệu thật/production.

|Trạng thái|Tại thời điểm bàn giao|
|---|---|
|SPEC PROPOSED|B01–B06 được đề xuất trong file này|
|SPEC APPROVED|Chưa có phê duyệt; SRS gốc không bị sửa|
|NOT IMPLEMENTED|Các mở rộng chưa được lập trình trong nhiệm vụ này; core hiện hữu được dẫn riêng|
|NOT TESTED|Không chạy build/test/migration/SMTP/benchmark; H là yêu cầu test, không phải PASS|

**Quyết định bàn giao hiện tại: CHECKPOINT SAVED – CÒN RÀ SOÁT CUỐI – PROPOSED – CHỜ LEADER T1 PHÊ DUYỆT.** Không commit/push/deploy, không thay database hoặc source.


## K. Diff kỹ thuật v1.1 so với v1.0

### K.1. Named schema cho các kiểu đã mô tả bằng văn xuôi hoặc inline

Các tên sau chỉ đặt tên cho cấu trúc đã có ở C.0, CP-AUTH1, BC11, BC12, BC21, BC24 và CP-T5UI1; không thêm endpoint, quyền hoặc nghiệp vụ. Legacy request được tách khỏi request mới để không vô tình áp closed-body/new-password rules lên client cũ.

```text
Role=STUDENT|ENTERPRISE|SCHOOL_STAFF|ADMIN
AccountStatus=PENDING_ACTIVATION|ACTIVE|SUSPENDED|DEACTIVATED
FieldError {field:Text(1..200),code:Reason,message:Text(1..300)}
ErrorEnvelope {errorCode:Reason,error:Reason,message:Text(1..300),requestId:U,fieldErrors:FieldError[],retryAfterSeconds:int>=0|null}
DomainError {errorCode:Reason,fieldErrors:FieldError[],retryAfterSeconds:int>=0|null}
AuditEventInput {action:AuditAction,resourceType:AuditResourceType,resourceId:U|null,organizationId:U|null,result:SUCCESS|FAILURE|DENIED,reasonCode:Reason|null,metadata:AuditMetadata,eventVersion:1}
AuditEventPort.write(actor:ActorContext,input:AuditEventInput):void
ActivationRequestV11 {token:TokenInput,newPassword:NewPassword,displayName?:Text(1..150)}
CaptureProfilesRequest {termId:U,expectedAcademicCatalogVersion:Code,expectedTaxonomyVersion:Code}
AssertEligibilityRequest {studentUserId:U,termId:U,expectedVersion:V}
SubmitCooperationRequest {demandId:U,expectedDemandVersion:V}
CategoryDto {categoryCode:Code,name:Text(1..128),parentCategoryCode:Code|null,status:ACTIVE|DEPRECATED,version:V}
SkillAliasDto {aliasId:U,skillCode:Code,alias:Text(1..128),version:V}
ProfileProjection {studentUserId:U,profileVersion:V,allowedFields:ProfileField[],data:ProfileDataProjection}
ProfileDataProjection {displayName?:Text(1..150)|null,studentCode?:Text(1..32)|null,majorCode?:Code|null,skills?:ProfileSkillProjection[],projects?:Project[],certificates?:Certificate[],cv?:FileDto|null,evidence?:FileDto[],email?:Email|null,phone?:Text(8..20)|null}
ProfileSkillProjection={kind:CANONICAL,skill:SelfSkillView}|{kind:CUSTOM,skill:CustomSkillView}
PageQuery {page?:int[0..10000],size?:int[1..100]}
```

ProfileProjection.data phải có đúng các property trong allowedFields; không dùng null cho property chưa được cấp. gpa/transcript vẫn là mã dự phòng bị từ chối theo BC24. Schema kiểm shape/enum/nullability; kiểm tra DEV bổ sung đối chiếu field mask. Runtime vẫn phải lấy quyết định quyền từ dữ liệu có thẩm quyền.

ErrorEnvelope.error phải bằng errorCode; DomainError là kiểu nội bộ, adapter bổ sung message/requestId và không giả HTTP giữa bean. Retry-After lấy từ retryAfterSeconds khi operation công bố header. Không coi mọi DomainError đều là cùng một HTTP status.

### K.2. Hoàn thiện Data Dictionary G cho hồ sơ T2

Đây là ánh xạ lưu trữ đề xuất để thực hiện các DTO/rule đã có ở BC11, không phải DDL đã chạy. T2 sở hữu dữ liệu; không đưa credential hoặc bảng identity thứ hai vào T2. Các trường không NULL bên dưới là NOT NULL; ID vẫn UUID VARCHAR(36), thời gian UTC, version BIGINT.

| DTO / field | Owner / bảng hoặc projection | Kiểu, nullability và ràng buộc |
|---|---|---|
| CanonicalSkillInput / SelfSkillView | T2 student_profile_skills | profile_id:U FK student_profiles; skill_code:VARCHAR64 tham chiếu taxonomy T4; level:INT 1..5; years_practice:DECIMAL >=0; source:SELF_DECLARED/SCHOOL_VERIFIED; note:VARCHAR1000 NULL. UNIQUE(profile_id,skill_code). Client self-write không được tự đặt SCHOOL_VERIFIED. Precision DECIMAL phải được T2 chốt theo độ chính xác dữ liệu nguồn trước migration; schema wire không tự thêm trần số năm. |
| CustomSkillInput / CustomSkillView | T2 student_custom_skills | id:U PK tương ứng customSkillId; profile_id:U FK; name:VARCHAR128; category_code:VARCHAR64 tham chiếu category T4; level:INT 1..5; years_practice:DECIMAL >=0; note:VARCHAR1000 NULL. customSkillId=null chỉ ở input tạo mới; ID thuộc profile khác bị từ chối. Không FK custom skill vào canonical skill, không tự tạo alias. |
| ProfileInput / SelfProfileData | T2 profile aggregate và các relation của owner | expectedProfileVersion/profileVersion ánh xạ version của aggregate. termId tham chiếu T5 terms và student-term T2 đúng school; majorCode có thể null khi chưa khai; skillsDeclared/availabilityDeclared NOT NULL. profile không tự tạo schoolApproved. T2 phân bổ trường theo term trong thiết kế module trước DDL; không suy từ UNIQUE(user_id) rằng mọi term phải ghi đè cùng dữ liệu. |
| projects / certificates / file refs | T2 relations của profile | Giữ projectId/certificateId/customSkillId và file bindings khi đổi ngành. Mỗi fileId tham chiếu T1 files, có kiểm owner/binding/scan. Không cascade xóa theo đổi majorCode. Có lịch sử/version theo BC11; không copy bytes hoặc storageKey vào DTO profile. |
| subjectCodes / orientationCodes | T2 profile selections và academic catalogs | Mã mới phải ACTIVE đúng school/term/major; lựa chọn cũ giữ lại khi đổi ngành, không suy mã cũ là lựa chọn mới. Mảng rỗng là khai báo rỗng, không thay bằng null. |
| ProfileSnapshot / SkillValue | T2 immutable snapshot phục vụ T4/T5 | Canonical skill gồm skillCode/level/yearsPractice/source; loại note và customSkills khỏi CT-01/relevantHash đúng BC11. Snapshot riêng với self editor, không mất note/custom data trong hồ sơ gốc. |
| ImportJobDto.observedAt / counters | T1 projection từ một read snapshot | observedAt là thời điểm đọc, không phải cột bắt buộc cần ghi mỗi GET. Các counter là int không âm, không vượt 5.000; quan hệ counter theo F.4. jobVersion không tăng do SMTP/activation. |
| ImportRowDto.rowVersion / invitationVersion | T1 student_import_rows.version / invitation_claims.version | Hai version khác nhau; resend không tăng rowVersion provisioning. invitationVersion có thể null trước claim. MailState NOT_REQUIRED/NOT_READY chỉ projection, không lưu thành state outbox. |

G.1 v1.0 là tổng quan, bảng K.2 bổ sung các field note/custom skill và giải thích projection còn thiếu. Không kết luận thiết kế bảng nghiệp vụ T2 hoàn tất: phân bổ dữ liệu theo term và precision DECIMAL phải được owner thiết kế trước migration. Đây là phần thiết kế module, không tự tạo chính sách học vụ mới.

### K.3. Registry, tương thích và giới hạn kiểm thử

- Permission registry xuất đủ 18 mã hiện có và 33 mã bổ sung; alias là exact mapping kèm action restriction, không seed alias hoặc sentinel. CSV nền tảng được giữ nguyên để truy vết; manifest đối chiếu từng dòng với mã canonical, không coi nhãn CSV là quyền runtime mới.
- Audit registry xuất đủ 30 action enum và 63 action bổ sung; giữ legacy result/resource đọc được. versionNo/reviewedVersionNo/reviewedVersion/revisionNo/submissionNo là số đếm nguyên >=1, khác V và các Code version đã định nghĩa ở E.1. AuditAction/AuditMetadata được sinh từ registry E với allowlist riêng từng action, AuditResourceType là mã lowercase theo E.1. Schema AuditEventInput dành cho producer mới; không áp resource type mới để phủ nhận khả năng đọc log legacy. Không tự ghi PII để điền metadata.
- Schema dùng JSON Schema Draft 2020-12. Bộ kiểm tra bật format UUID/date/UTC Instant/HTTPS và bổ sung giới hạn UTF-8 bytes của NewPassword. Legacy DTO giữ validation đang có ở source; không áp closed-body hoặc profile 1.1 lên API legacy.
- Mỗi chữ ký port/consumer trong code fence có schema Input cho tham số nghiệp vụ và Value cho giá trị Success. ActorContext/MutationContext/TrustedEventContext là tham số server-only được liệt kê riêng, không đưa vào body browser. Result<T> là kiểu trả nội bộ; schema Value mô tả T, DomainError là nhánh lỗi, không tự tạo HTTP success wrapper. AuditEventPort.write trả void nội bộ, Value=null chỉ là biểu diễn kiểm tra kiểu, không là response REST mới.
- Kiểm tra JSON/registry/source mapping là test DEV tĩnh. Counter, field mask và artifact exclusivity kiểm trên fixture chỉ chứng minh oracle contract; không chứng minh transaction, security guard, SMTP, race hoặc throughput của module chưa được triển khai.
- Những test runtime core đã chạy được ghi theo run/commit riêng. Các acceptance H và PER liên quan module mới vẫn NOT RUN; không dùng kết quả schema thay QA hoặc consumer/producer chạy thật.

### K.4. Vị trí SRS và phê duyệt phiên bản

SRS §25.8.1–2 giữ Placement/privacy baseline, §25.8.3 G06/G07/G10/G12/G15/G27/G28 quy định guard/DTO/audit, §25.9.1–4 và bảng giao điểm §25.9.6 là nguồn của pack. Hình 3 rút gọn nhảy APPROVED→IN_PROGRESS; khi lập trình phải dùng state machine §25.8.1 (APPROVED→WAITING_START, ACTIVE→IN_PROGRESS) đã được văn bản hiệu chỉnh chốt. Không sửa hình/SRS gốc trong nhiệm vụ này.

Không thay file Word hoặc hash v1.0 để trỏ ngầm sang v1.1. Người dùng review diff v1.1 như một phiên bản kỹ thuật riêng; không ghi giả xác nhận của T2–T5. Mẫu review và phần bàn giao theo team ở báo cáo T1-01.
