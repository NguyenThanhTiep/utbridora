# Kiểm định cuối có trọng tâm SRS UT.Bridora v1.2.3

Ngày: 03/11/2026 (Asia/Bangkok). Phạm vi: các mã còn mở từ re-audit v1.2.2 và I01–I06; không kiểm định lại toàn bộ dự án. Chỉ tạo báo cáo này; không sửa SRS/source/DB/migration, không chạy test, không commit/push/deploy.

## 1. Executive Summary

**Hai P0 G01 và G03 đã FIXED ở mức SPEC. Tuy nhiên, NO GO cho việc giao Codex triển khai và tích hợp trọn năm team ngay mà không chốt thêm contract.** Blocker còn lại tập trung ở contract/API/registry thực tế và ba phần của import: dữ liệu điều khiển API, điều kiện hoàn tất job, phục hồi email thất bại. Không cần viết lại SRS; có thể bổ sung đúng các bảng tại §25.8.

| Phạm vi đếm | FIXED | PARTIALLY FIXED | NOT FIXED | CONFLICT | UNVERIFIED |
|---|---:|---:|---:|---:|---:|
| 23 mã trọng tâm:2P0+15P1+6import | 15 | 8 | 0 | 0 | 0 |
| Cộng5 mục theo dõi G29–G33:28 mã | 16 | 12 | 0 | 0 | 0 |

Dòng thứ hai có **G33 SPEC đã rõ từ v1.2.2**, không phải một sửa code mới: N+1 vẫn chưa sửa/đo. Theo yêu cầu lần này, không đánh SRS NOT FIXED chỉ vì implementation chưa có. Các số trên là trạng thái đặc tả/contract, không phải tỷ lệ hoàn thành code hay kết quả test.

- **G01/G03:** đã đóng thiếu sót nghiệp vụ gốc; G02 giữ FIXED, không mở lại.
- **G27:** PARTIALLY FIXED; §25.8.4 có API import tối thiểu thực sự, nhưng T90/R14,T94/R4 vẫn yêu cầu owner viết các schema thiếu trước Codex. Không coi lời hứa này là contract pack hoàn chỉnh.
- **I01–I06:** I02/I03/I04 FIXED; I01/I05/I06 PARTIALLY FIXED.
- **Không phát hiện CONFLICT nghiêm trọng mới có đủ căn cứ.** Khác biệt target/code được ghi CODE COMPATIBILITY GAP, không coi thay đổi có chủ đích là mâu thuẫn SRS.
- **SPEC FIXED ≠ IMPLEMENTED ≠ TEST PASS.** Không có build/test/import5.000/privacy/benchmark mới được thực thi trong lần kiểm định này.

### 1.1. Nguồn và cách dẫn chứng

SRS đọc tại C:/Users/Admin/Downloads/UTBridora_SRS_Tong_The_Hieu_Chinh_ReAudit_v1.2.3.docx; SHA256 **20D12904427A30F706BD3E60A16D4ED8C87AD60F48C0E941E0A09E9F463976C1**. Báo cáo đối chiếu: [re-audit v1.2.2](UTBridora_SRS_v1.2.2_Fix_Verification.md), SHA256 **BF5EEF2568ACDBBF2F8F7266219E094A9DAA81D4CEA38DAAA01AE43132497C73**. Báo cáo v1.1 có sẵn để truy vết finding gốc; không dựng nguồn thay thế.

Source local và remote main cùng **3c725e3dc8ed1886a84cff2d64e75b746c75766c**, xác minh đọc bằng git ls-remote. Chỉ khảo sát các file T1 liên quan; không khẳng định về nhánh chưa được cung cấp. Repository: [NguyenThanhTiep/utbridora](https://github.com/NguyenThanhTiep/utbridora).

Đã đọc toàn bộ §25.8, các bảng/đoạn liên quan Ch6–25, phụ lục/header/footer và mở cả3 sơ đồ kỹ thuật. Phần trước §25.8 giữ nguyên nội dung nghiệp vụ v1.2.2, chủ yếu đổi metadata/priority; hình giữ nguyên. Không có nested table/textbox/math/tracked insertion/deletion bị bỏ qua. Chưa render toàn bộ layout trang, không chứng nhận clipping/phân trang. Báo cáo Markdown được giữ gọn; không dùng số trang/bảng làm bằng chứng chất lượng.

Locator: P là thứ tự đoạn trực tiếp trong body, gồm đoạn trống; T là bảng trực tiếp theo thứ tự toàn DOCX; R1 là header. §25.8 ở P445–459,T88–94. Đường dẫn Java rút gọn bên dưới thuộc src/main/java/vn/utbridora/auth/.

**Ưu tiên áp dụng P279/P446:** quy tắc chính xác §25.8 thay diễn đạt cũ về cùng yêu cầu. Do đó hình21.2 APPROVED→IN_PROGRESS, mẫu3counters,PENDING_SCAN/k3/enumACTIVE cũ không tự tạo CONFLICT. Không yêu cầu thêm role toàn cục hay phê duyệt UTH production để lập trình demo synthetic.

## 2. Đối chiếu các mã còn mở

FIXED là đủ quyết định cho finding được xét; contract dùng chung vẫn phải đóng G27. Với G15/G20, finding gốc bao gồm cả public port/API nên giữ PARTIALLY FIXED dù policy đã được sửa, không hạ vì thiếu code.

| Mã | v1.2.2 | v1.2.3 | Bằng chứng SRS | Kết quả / phần còn thiếu |
|---|---|---|---|---|
| G01 | PARTIALLY FIXED | FIXED | §25.8.1 P448–450; T88/R2–6 | Active-key APPROVED/ACTIVE; scheduler UTC hoặc staff cùng guard; WAITING_START chỉ bắt đầu sau ACTIVE/effectiveAt; normal/early/cancel và stale-event đã có outcome. DTO/consumer cases chung ở G27. |
| G03 | PARTIALLY FIXED | FIXED | §25.8.2 P452; T89/R2–9 | Có fingerprint/ledger/batch authority, same-release replay/khác409,0–4 SUPPRESSED,5–9/10–19/20+; cấm raw/total/%/breakdown trên mọi output. Không còn thiếu privacy oracle gốc. |
| G06 | PARTIALLY FIXED | PARTIALLY FIXED | §25.8.3 T90/R2; T68; §25.8.5 T94/R4–5 | Đã chốt import code và authority; chưa có bảng canonical↔legacy/action thực tế cho các code còn khác core. B02. |
| G07 | PARTIALLY FIXED | FIXED | T90/R2; §6.7 T17/R4 | ADMIN bootstrap delegate demo, school delegate quản import/grade, coordinator được trường xác nhận cấp HR; scope/version/revoke và không tự nâng global role đã rõ. Registry/DTO chung còn G06/G27. |
| G08 | PARTIALLY FIXED | FIXED | T90/R3; T35/R4 | Chỉ PENDING tự EXPIRED; ACCEPTED không hết hiệu lực theo deadline cũ; WITHDRAW trước Placement→WITHDRAWN, sau Placement409; history/reason/version và reservation rõ. |
| G09 | PARTIALLY FIXED | FIXED | T90/R4; T35/R5 | RESERVED24h, INVITED theo expiresAt, accepted/share giữ tối đa min7ngày/consent/deadline. PlacedCount DISTINCT APPROVED/ACTIVE/COMPLETED, loại CANCELLED; không cộng kép. |
| G10 | PARTIALLY FIXED | FIXED | T90/R5; T22/R2; CT-01/02 T73/R2–3 | Có skill object/level/yearsPractice/source, minimum≥, union periods/inclusive, declaration/null/empty semantics. Wire schema còn thuộc G27; không còn tự chọn toán tử nghiệp vụ. |
| G12 | PARTIALLY FIXED | FIXED | T90/R6 | T2 sở hữu major/subject/orientation catalogs school+term với code/version/ACTIVE; ngoài catalog422. T4 skill taxonomy; giữ sáu completeness predicate đã chốt. |
| G14 | PARTIALLY FIXED | FIXED | T90/R7 | opportunityId=cooperationId, publication window/school; Preference student withdrawal trước school review, REVIEWED terminal và version intent mới. Contract public còn G27. |
| G15 | PARTIALLY FIXED | PARTIALLY FIXED | T90/R8; §25.8.5 T94/R4 | Policy đã sửa:3 endpoints,multipart,binding,10/20/5MiB,ZIP100MiB/1000entry,scan60s/retry/PENDING fail-closed. Còn response metadata/upload và typed resource-authorizer/binding contract; xử lý chung B01/G27. |
| G18 | PARTIALLY FIXED | FIXED | P449–450; T90/R9; T35/R6 | Atomic outbox/inbox+case+audit, authoritative version,case/event transitions,retry5 với lịch cụ thể,DLQ/reprocess/reconcile,ACK-IGNORE/REJECT/RETRY đã rõ. Consumer conformance tests chưa chạy. |
| G19 | PARTIALLY FIXED | FIXED | T90/R10; T17/R7 | UUID key/scope/hash; đang xử lý409+Retry-After; đổi body409; cache24h hết hạn trả409 và không chạy lại; durable effect/business UNIQUE giữ độc lập cache. Không còn chỉ câu “retry an toàn”. |
| G20 | PARTIALLY FIXED | PARTIALLY FIXED | T90/R11; T17/R8; T38/R8 | Policy new/existing ACTIVE/PENDING_ACTIVATION/blocked và chủ tài khoản accept đã sửa. MentorInvitationPort input/output/accept callback/error chưa có contract thực tế; B01/G27. |
| G25 | PARTIALLY FIXED | FIXED | T90/R12; T39/R6 | Tuần Monday00:00 Asia/Ho_Chi_Minh, giao≥1ngày với case đều áp dụng; hạn Tuesday23:59:59 tuần kế; sau hạn vẫn nhận late=true; normal close phải đủ reviewed reports. |
| G26 | PARTIALLY FIXED | FIXED | T90/R13; T88/R4–5; P449; T39/R7 | NORMAL→COMPLETED đủ gates; EARLY→TERMINATED có school decision; terminal khóa ghi; T5 maps NORMAL/ EARLY→COMPLETED/CANCELLED sau authority. |
| G27 | PARTIALLY FIXED | PARTIALLY FIXED | T90/R14; T94/R4; P459; T38,T73,T91 | Có minimum import API nhưng chính SRS vẫn yêu cầu owner viết schema thiếu trước Codex. Chưa đủ DTO/types/nullability/errors/examples/ports của giao điểm Must; repository chỉ core OpenAPI và permission CSV. B01. |
| G28 | PARTIALLY FIXED | PARTIALLY FIXED | T90/R15; T94/R5; T76 | Đã giữ LOGIN+result,MEMBERSHIP_GRANTED/REVOKED,EVALUATION_* và alias GRADE. IMPORT_*/PLACEMENT_*/CONSENT_* vẫn wildcard; thiếu exact registry/mapping metadata/resource. B03. |
| I01 | PARTIALLY FIXED | PARTIALLY FIXED | §25.8.4 T91/R2–7; T92/R2–3 | Có upload/preview/confirm/progress/rows/resume/resend và guard. Chưa trả jobVersion/rowId cho thao tác cần chúng; 422 row/schema chưa tách lỗi file với preview có dòng lỗi. B04. |
| I02 | PARTIALLY FIXED | FIXED | T93/R2–7 | New email→invite; ACTIVE đúngschool+MSSV→ALREADY_LINKED; chưa xác minh/cross-school/blocked→NEEDS_REVIEW; PENDING_ACTIVATION đúngroster→reissue. Đủ decision matrix import, không bắt thêm review-resolution workflow ngoài scope. |
| I03 | PARTIALLY FIXED | FIXED | T18; T93/R8–9; P456 | Tất cả dòng duplicate INVALID; email trim+lowercase; MSSV trim/giữ0/case và numeric-cell INVALID; catalog versions lưu/so lại. Không còn first-wins ambiguity. |
| I04 | PARTIALLY FIXED | FIXED | P456; T91/R3; T92/R2 | Server staging/hash/selected rows/version,preview30phút; recheck quyền/catalog/term/unique,stale409. Preview chỉ ghi staging/validation, chưa domain user/token/roster. |
| I05 | PARTIALLY FIXED | PARTIALLY FIXED | P456; T91/R4–5; T92/R2–3; T93/R3 | Đã có row-atomic/checkpoint/UNIQUE/cross-job retry. Chưa chốt job hoàn tất khi provisioning/outbox hay mail/activation; ALREADY_LINKED không có terminal row/count branch. B05. |
| I06 | PARTIALLY FIXED | PARTIALLY FIXED | T91/R6–7; T92/R4–5; P457 | Đã chốt TTL48h từ issuedAt,old-token revoke,backlog reissue,3retry,cooldown/rate,Mailpit/APP_PUBLIC_URL. Mail FAILED sau roster commit chưa thuộc resume hay resend; B06. |

### 2.1. Năm mục theo dõi, không chặn mọi code độc lập

| Mã | v1.2.2 | v1.2.3 | Bằng chứng | Trạng thái ngắn |
|---|---|---|---|---|
| G29 | PARTIALLY FIXED | PARTIALLY FIXED | T87/R2; T94/R6 | RTM từng Must vẫn là deliverable chưa có bảng granular; không chặn code độc lập. |
| G30 | PARTIALLY FIXED | PARTIALLY FIXED | T87/R3; T94/R6 | Giữ30/90; purge/FK/hold/backup procedure chưa bổ sung chi tiết; gate nghiệm thu/vận hành. |
| G31 | PARTIALLY FIXED | PARTIALLY FIXED | T87/R4; T94/R6 | Recipe endpoint set/sample boundary/percentile vẫn chưa chốt; benchmark NOT TESTED. |
| G32 | PARTIALLY FIXED | PARTIALLY FIXED | P279,P446; T87/R5; P147,T44 | Priority mới rõ, nhưng cleanup AC/route/superseded tại chỗ chưa làm; không báo legacy là conflict mới. |
| G33 | NOT FIXED(source); SPEC đã rõ | FIXED | T87/R6; P459; AdminReadController.java:40–41 | SPEC đã rõ từv1.2.2; source N+1 vẫn NOT IMPLEMENTED, chưa đo. Không tính đây là sửa code trongv1.2.3. |

### 2.2. Hai P0 đã có oracle để chuyển thành test

**G01:** T88 chốt UNIQUE active-key cho APPROVED/ACTIVE; scheduler UTC và lệnh school staff dùng cùng guard chuyển ACTIVE khi effectiveAt đã tới. T3 tạo WAITING_START từ APPROVED hợp lệ, chỉ IN_PROGRESS/Task/Submission khi reread ACTIVE. Cancel school-scoped và CaseClosed NORMAL/EARLY có mapping Placement COMPLETED/CANCELLED; terminal khóa ghi, giữ history. P450 có atomic inbox/outbox, version, retry5 với lịch1/5/30/300/1800giây,DLQ,reprocess/reconcile và ACK-IGNORE/REJECT/RETRY. T3/T5 đã có cùng đích nghiệp vụ để phát triển qua contract, không còn phải tự chọn activation/close policy. Cần consumer test Activated tới trước Approved, cancel tới trễ, scheduler/manual/cancel race20; các test này **NOT TESTED**, không phải P0 mới.

**G03:** T89 chốt một protected release/enterprise+school+term+demoBatchId; canonical fingerprint/snapshot, cùng release replay, khác fingerprint409 PRIVACY_RELEASE_LOCKED. T1/Ops mở batch mới sau review T4+QA; actor/demand/filter/export không reset ledger. Counts0/1/4→SUPPRESSED;5→5-9;10→10-19;20→20+. JSON/CSV/chart chỉ cùng eligibleBand; không rawCount,total,%,PII hay nhiềuô. Việc thay nhiều counters bằng một band đã loại trường hợp phải chọn thuật toán secondary suppression giữa nhiềuô ở MVP; không tiếp tục báo policy cũ là blocker. Chưa test thì tắt enterprise aggregate theo T89/R9, không giả PASS privacy.

## 3. Những khoảng trống thực sự còn chặn giao trọn logic/tích hợp

Chỉ có sáu nhóm sửa ưu tiên dưới đây; G15/G20 được xử lý trong G27 để tránh nhân đôi công việc. Mỗi sửa là đề xuất cần owner xác nhận, không phải nội dung đã được báo cáo tự bổ sung vào SRS.

| Mã | Vị trí | Thiếu thông tin gì | Tác động và sửa cụ thể | Team |
|---|---|---|---|---|
| B01 — G27,G15,G20 | T90/R8,R11,R14; T94/R4; T38,T73,T91 | Contract vẫn thiếu typed I/O/required-null-enum/status/error/permission/version/examples và owner-consumer cho file response/binding-authorizer,MentorInvitationPort/accept callback,workflow T3↔T2 và summary/detail T4↔T5. T94 tự nhận SRS chưa là OpenAPI hoàn chỉnh. | Hai team có thể chọn body/return/error khác nhau dù domain policy giống. Bổ sung đúng DTO/schema/port và1request-response hợp lệ/lỗi cho giao điểm Must; không chỉ bảo owner “sẽ viết”. Import DTO cụ thể sửa thêm B04. | T1 điều phối; T2–T5 owner/consumer |
| B02 — G06 | T90/R2; T68/R2–4,R17; docs/contracts/permission_matrix.csv | Chưa có exact canonical↔legacy/action mapping; ví dụ audit:read:global vs audit:global:read, t3:tasks:write vs internship:enterprise:task_write; account:admin:manage còn gộp nhiều action. | Developer không được tự gộp quyền để hết403. Điền registry từng action/scope/grantor, giữ quyền legacy đang chạy,split/map code rõ và negative AC. Không cấp HR chỉ vì ENTERPRISE. | T1 + owner nghiệp vụ |
| B03 — G28 | T90/R15; T94/R5; T76; AuditAction.java:4–12/AuditService.java:31 | IMPORT_*/PLACEMENT_*/CONSENT_* là prefix, chưa là action registry; MEMBERSHIP_CHANGED legacy chưa có mapping changeType; metadata/resource/result chưa được liệt kê đầy đủ. | Producer không thể chọn code tùy ý vì source Enum.valueOf sẽ reject. Chốt exact action/result/resource/metadata allowlist và legacy mapping; tách domain eventType khỏi AuditAction, thêm mới có review. | T1 audit + T2–T5 producer |
| B04 — I01/G27 | T91/R2–6; T92/R2–3 | resume yêu cầu expectedJobVersion nhưng GETjob không công bố jobVersion; resend cần rowId nhưng output rows mới nêu rowNo. Preview “422 row/schema” chưa phân biệt file fail với job thành công có invalidRows để partial confirm. | Frontend không có giá trị để gọi đúng contract; QA có thể mong200 hoặc422 cùng file có dòng lỗi. Bổ sung jobVersion,rowId/types và field availability; chọn rõ HTTP cho lỗi cấu trúc so với lỗi từng dòng, cùng response examples. | T1 API/job; T5 frontend; T2 consumer |
| B05 — I05/I01 | T92/R2–3; T93/R3; P456; T91/R4–5 | Chưa chốt PROCESSING→COMPLETED/COMPLETED_WITH_ERRORS tính theo domain+outbox commit hay chờSENT/ACTIVATED. Row chain chỉ tới ACTIVATED, không có terminal ALREADY_LINKED/skip branch và tổng hợp counts tương ứng. | Hai backend có thể để job chờ sinh viên vô hạn hoặc báo hoàn tất khác nhau. Chốt điều kiện job terminal,success/no-op/error row states và cách tính total/processed/createdOrLinked/failed/review; checkpoint/resume không lặp effect đã commit. Không cần tự xây mới flow review. | T1 job; T2 provisioning; T5 UI; QA |
| B06 — I06/I05 | T91/R5–6; T92/R3–5; P456 | Email FAILED sau3retry có thể xảy ra khi roster đã commit. resume chỉ dòngTRANSIENT_FAILED chưa committed effect; resend chỉ dòngINVITE_SENT. Chưa chỉ rõ command/recovery cho email chưa gửi thành công/rowINVITE_QUEUED. | Sinh viên có thể không nhận được link và cán bộ không có đường phục hồi được đặc tả. Cho retry/reissue mailFAILED qua command rõ hoặc mở eligibility resend theo state; giữ scope/cooldown/48h/current-token rules, không chạy lại provisioning; thêmAC SMTP fail→manual recovery và resend–activate race. | T1 mail/token/job; T5 UI; QA |

### 3.1. Import5.000: đủ ở mức nào?

| Phần | Kết luận | Giới hạn |
|---|---|---|
| UI/wireframe | GO | Upload→preview→select/confirm→progress/result→resend đã rõ. Frontend tích hợp thật cần B01/B04–B06. |
| Backend/domain | CONDITIONAL GO cho phần độc lập | Parser/duplicate/catalog/preview và identity matrix có thể làm theo I02–I04; chưa giao toàn flow khi job/mail terminal và contract chưa đủ. |
| Database | CONDITIONAL GO cho thiết kế | Ownership, UNIQUE,checkpoint/versions đã rõ; chốt job/row/mail terminal,atomic boundaries và DTO trước migration tích hợp. Không chạy/tạo migration trong audit. |
| Toàn luồng5.000 và nghiệm thu | NO GO hiện tại | Còn B04–B06 và contract; chưa có evidence thực thi1.000/5.000,crash/resume,resend–activate. Không suy năng lực xử lý từ số dòng tối đa. |

NEEDS_REVIEW là kết quả import an toàn cho identity chưa xác minh/khác trường/khóa; không tự yêu cầu xây thêm workflow xử lý review để đóng I02. Nếu owner chọn đưa resolution vào Must MVP thì mới cần contract riêng trong G27. Nhánh ALREADY_LINKED vẫn phải có row/job outcome và counts rõ theo B05.

### 3.2. Source compatibility khác với lỗi SRS

| Điểm | Source / SRS | Đối chiếu |
|---|---|---|
| Organization/file scan | Organization.java:10; FileMetadata.java:11; T88/R3,T90/R8 | VERIFIED giữ nguyên. Scan PENDING mới khớp enum source, không migrate sang PENDING_SCAN chỉ vì dòng cũ. Storage/scanner còn NOT IMPLEMENTED, không phải lý do mở lại numeric policy. |
| Activation public body | ApiDtos.java:14; AuthController.java:105–107; T91/R7 | Source nhận token/displayName/password; target nhận token/newPassword và422/409. CODE COMPATIBILITY GAP: ghi mapping/alias/legacy strategy và nguồn displayName từ roster cho target; không âm thầm phá client cũ. G27 chịu trách nhiệm contract, audit không sửa endpoint. |
| Invitation/identity/mail | AccountTokenService.java:25–28,39–59; EmailService.java:13–20; P457,T93 | Source chỉ globalADMIN/newemail, tạo user khi accept và gửi mail inline; target scoped roster/mentor/outbox/reissue là NOT IMPLEMENTED. T93R2 tạo user khi accept đã tương thích hướng hiện có; mở rộng qua port, không bỏ guard. APP_PUBLIC_URL nay đúng tên source. |
| Permission/audit | PermissionCatalog.java:13–22; V3:26–30; AuditAction.java:4–12; AuditService.java:31 | Exact lookup/valueOf hiện hữu; cần B02/B03 và adapter/registry mới. Không coi action prefix/targetpermission là đã được source chấp nhận. |
| Auth/error/N+1 | SecurityConfig.java:41–55; ApiErrorHandler.java:11–35; AdminReadController.java:40–41 | Giữ session/CSRF/denyAll, đăng ký routes+guards cụ thể khi code. Error envelope mới cần compatibility mapping; N+1 vẫn source task chưa sửa/đo. Không suy latency hayPASS từ SRS. |

Không thấy role toàn cục thứ5, owner thứhai cho invitation hoặc việc cho T3 tạo Placement trong quy tắc mới. T5 vẫn canonical internship invitation/Placement; T2 consent/roster; T3 case; T4 matching; T1 identity. PENDING_MEMBERSHIP_ACCEPTANCE phải là trạng thái luồng membership/assignment chờ nhận, không tự thêm vào users.status hay role nền tảng.

Source vẫn Java21/Boot4.1.2/MySQL8.4,FlywayV1–V7,ddl-auto=validate. Không thấy migration mới/trùng trong checkout; DB flyway_schema_history runtime **UNVERIFIED**. Không có lý do đổi framework/kiến trúc hoặc vô hiệu session/CSRF để thực hiện phần bổ sung.

## 4. Đánh giá từng team

| Team | Kết luận | Có thể bắt đầu | Điều kiện còn chặn |
|---|---|---|---|
| T1 | CONDITIONAL GO | Core reuse,token primitives,file scan policy,idempotency/domain tests. | B01–B06 theo phần T1; scoped registry/file/mentor/import wire contracts phải đủ trước triển khai API. |
| T2 | CONDITIONAL GO | Profile/consent snapshots,catalog và roster validation theo rule đã chốt. | B01/B04/B05 cho provisioning/bind/row results; không tự thay identity hoặc invitation owner. |
| T3 | CONDITIONAL GO | Task/group/rubric/weekly-report và Placement/case transition services. | B01/B02/B03:typed ports,HR/mentor grant mapping,audit; consumer out-of-order/race tests chưa chạy. |
| T4 | CONDITIONAL GO | Strict classifier/scoring và protected-band policy với fixtures T89. | B01–B03 cho publicsummary/detail/permission/audit; chỉ mở enterprise aggregate sau privacy tests thật. |
| T5 | CONDITIONAL GO | Term/verification,Placement/reservation/invitation/Opportunity state machine và UI. | B01 và B04–B06 cho cross-team DTO/import UI; registry cùngT1. Không tự thiết kế lại output/quy tắc import. |

CONDITIONAL GO chỉ áp dụng phần có rule/oracle đã rõ; không phải giấy phép tự chọn JSON/schema/grant/eventcode còn thiếu. Khi một contract cụ thể được hoàn tất, có thể giao nhiệm vụ module đó ngay, không cần đợi N+1/benchmark hoặc mọi artefact nghiệm thu của team khác.

## 5. Kết luận cuối cùng

| Câu hỏi bắt buộc | Trả lời |
|---|---|
|1.Đã khắc phục đủ thiếu sót v1.2.2? | **Chưa đầy đủ.** Hai P0 và phần lớn policy P1/import đã sửa thực chất. Còn8 mã trọng tâm PARTIALLY FIXED, chủ yếu contract và finalization/recovery của import; G29–G32 theo dõi riêng. |
|2.Đủ giao Codex tự triển khai và tích hợp cả5team, không suy diễn nghiệp vụ quan trọng? | **NO GO cho trọn phạm vi hiện tại.** Có thể code phần độc lập; G27 chưa đạt chính gate T90/R14,T94/R4 và B04–B06 còn quyết định ảnh hưởng kết quả người dùng. |
|3.Còn thiếu SRS hay chỉ code/test? | **Cả hai, phải tách riêng.** B01–B06 là thiếu đặc tả/contract cần quyết định; storage/scanner/outbox/adapters/5teammodules/test/evidence là implementation và kiểm thử chưa hoàn thành. G33 là source task; không dùng code chưa viết để đánh NOT FIXED SRS. |

**Quyết định: NO GO — UT.BRIDORA SRS V1.2.3 cho giao Codex triển khai hoàn chỉnh và tích hợp cả năm team ngay.** Điều kiện chuyển sang GO lập trình/tích hợp là đóng **B01–B06** bằng nội dung/contract thực tế, không chỉ ghi “sẽ công bố”; không cần viết lại SRS hoặc mở lại G02/các policy đã FIXED.

Sau đó, GO về đặc tả vẫn khác GO nghiệm thu phần mềm: phải triển khai và có evidence test gắn revision/fixture/môi trường, không dùng mock thay giao điểm nghiệm thu. Production/dữ liệu thật vẫn theo gate §25.3; báo cáo không tự quyết định chính sách học vụ hoặc dữ liệu UTH.
