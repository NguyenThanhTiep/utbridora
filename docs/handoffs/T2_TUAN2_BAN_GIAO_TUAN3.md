# Bàn giao T2 — kết quả tuần 2 và hướng dẫn tuần 3

**Cập nhật T1-06 ngày 10/11/2026:** bản DEV trên nhánh bàn giao `feature/t1-06-private-files` có API upload SELF/metadata/content và FileReadPort tối thiểu; chưa deploy. File mới PENDING, chưa scanner hay gắn CV/dự án/chứng chỉ T2-09. Đọc [hướng dẫn và boundary mới](../guides/T1_06_PRIVATE_FILES.md) trước các ghi nhận dependency tại snapshot tuần 2 bên dưới.

Ngày lập: 09/11/2026. Người nhận: Team Sinh viên, DEV02/DEV03/DEV04 và QA02. Tài liệu dành cho người tiếp nhận source hiện có; các phần ghi “cần triển khai” là công việc tiếp theo, chưa phải API đã chạy.

## 1. Nhận đúng phiên bản và đọc đúng nguồn

- Bản phát hành tuần 2 trên `main` chứa code, tests và tài liệu bàn giao này; base `6687a940cd2f42a6351fb6c3511286c763c81f6b`, nhánh triển khai `feature/week2-implementation`. Nhận đúng commit tuần 2 bằng fetch/pull trên checkout sạch của team, kiểm tra Git log và các file mục 3 trước khi làm tiếp.
- Worktree tạo bản bàn giao: `D:/HKDN-TTTV-UTH/.work/week2-20261109/repo`. Kiểm tra có các lớp trong mục 3 và migrations đến V27. Không tạo project Student độc lập hoặc dựng lại phần đã có.
- Fingerprint source tuần 2 chuẩn hóa LF: `77e7dda5c8e9e5d6f035609e4f2ac06b8ac6d8f7ca557c9c47e5ca9a5f932e63`. Đây là hash snapshot, không phải commit Git.
- [Plan](../plans/UTBridora_Plan.xlsx): sheet CAP NHAT CHUNG, tuần 2 dòng 26–28; tuần 3 dòng 43–44 và QA02 dòng 54.
- [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx): US-T2-02/03/04/06/07/08/09/10, R14/R17, quyết định §25.8.3 G08/G10/G12/G14.
- [Contract v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md): BC11, BC21–BC24, CP-FILE1; [quyết định chốt](../contracts/README.md). Nhãn “chờ ký” trong bản lịch sử không yêu cầu xin chốt lại contract đã được người dùng chấp thuận. v1.1 là tài liệu bổ sung kỹ thuật, không tự thay v1.0.
- Đọc [đối chiếu tuần 2](../testing/WEEK2_ACCEPTANCE.md), [evidence](../testing/evidence/WEEK2_DEV_VERIFICATION.json), [runtime/API](../guides/WEEK2_RUNTIME.md). Nội dung checkpoint mới ưu tiên hơn ghi nhận “chưa có module” trong hướng dẫn ngày 04/11.

Evidence giữ nguyên trạng thái tại thời điểm kiểm chứng trước commit (`uncommitted=true`, CI chưa chạy khi ghi nhận); xem Git log và GitHub Actions của commit nhận được để biết trạng thái phát hành/CI mới nhất.

## 2. Tuần 2 đã làm được gì

| Task/owner theo Plan | Implementation đã có | Bằng chứng và giới hạn |
|---|---|---|
| T2-04 — DEV02, QA02 | Lưu canonical/custom skills; completeness đúng sáu checks qua policy T4; immutable SnapshotSet/ProfileSnapshot, profileVersion/relevantHash và kiểm current khi nominate | StudentProfileIT và Week2WorkflowIT dùng nguồn T2/T4/T5 thật. Snapshot không đồng nghĩa school approval hoặc consent |
| T2-05 — DEV03, QA02 | SkillsEditor chọn mã chuẩn, khai kỹ năng ngoài danh mục, level/years/note, hiển thị lý do thiếu; lưu/reload backend | week2-runtime.spec.ts và student-runtime.spec.ts. Custom skill/note không tự được tính thành canonical MATCH |
| T2-06 — DEV04, QA02 | Consent grant/revoke/regrant theo student/school/term/recipient/purpose; field/expiry/policy/lineage version; locked check cho T5 | Đã kiểm chia sẻ/đọc thật, revoke chặn đọc sau commit; regrant không phục hồi share cũ; race revoke/regrant và expiry |
| Phần tối thiểu kéo trước từ tuần 3 | UI list lời mời, ACCEPT/DECLINE/withdraw và consent độc lập; T5 vẫn sở hữu invitation | Browser đã nhận và từ chối hai lời mời thật rồi reload. Chưa có Opportunity/Preference backend/UI đầy đủ |
| Phụ thuộc bổ sung | T2 có StudentEligibilityService quyết định schoolApproved và kiểm nomination | Port Placement có expectedEligibilityVersion theo BC21 vẫn cần bổ sung; không gọi tên interface cũ là integration đã hoàn tất |

Completeness gồm MAJOR_VALID, STUDENT_TERM_PRESENT, SKILLS_DECLARED, SKILLS_VALID, AVAILABILITY_DECLARED, AVAILABILITY_VALID. `declared=true + []` là khai báo rỗng có chủ ý; không tự thêm CV/GPA hoặc schoolApproved thành điều kiện thứ bảy.

Kết quả tự động chung của snapshot: 485 unit PASS, 125 integration PASS theo báo cáo mới nhất từng class, 87 Playwright PASS + 4 skipped theo fixture, frontend build PASS, 868 contract checks PASS. Full run có một lỗi helper browser lấy CSRF trước khi login hoàn tất; đã sửa và chạy lại riêng Week2WorkflowIT thành công. Đây không phải một full run xanh duy nhất, cũng chưa có human QA/Lead signoff hoặc xác nhận 5 team truy cập.

## 3. Bản đồ source cần kế thừa

Các đường dẫn bên dưới tính từ gốc repository; BE = `src/main/java/vn/utbridora/auth/`.

| Nơi đọc/sửa | Vai trò |
|---|---|
| [StudentProfileService](../../src/main/java/vn/utbridora/auth/modules/student/application/StudentProfileService.java), [StudentProfileRepository](../../src/main/java/vn/utbridora/auth/modules/student/persistence/StudentProfileRepository.java) | Owner profile, toàn bộ PUT và version; giữ dữ liệu cũ khi đổi ngành |
| [StudentProfileSnapshots](../../src/main/java/vn/utbridora/auth/modules/student/application/StudentProfileSnapshots.java), [snapshot port](../../src/main/java/vn/utbridora/auth/modules/student/port/StudentProfileSnapshotPort.java) | Snapshot matching bất biến, canonical-only, relevantHash; phone/note/custom không làm stale matching nếu hash không đổi |
| [StudentConsentService](../../src/main/java/vn/utbridora/auth/modules/student/application/StudentConsentService.java), [consent port](../../src/main/java/vn/utbridora/auth/modules/student/port/StudentConsentPort.java) | Owner consent/lineage; lockAndCheckForShare tham gia transaction của caller |
| [StudentEligibilityService](../../src/main/java/vn/utbridora/auth/modules/student/application/StudentEligibilityService.java), [eligibility port](../../src/main/java/vn/utbridora/auth/modules/student/port/StudentEligibilityPort.java) | schoolApproved authoritative; hiện port chỉ có assertEligibleForNomination |
| [InvitationConsentPage](../../web-demo/src/modules/student/pages/InvitationConsentPage.tsx) | UI đã dùng invitation T5 + consent T2; giữ phần này khi thêm cơ hội/nguyện vọng |
| [SkillsEditor](../../web-demo/src/modules/student/components/SkillsEditor.tsx), [MyProfilePage](../../web-demo/src/modules/student/pages/MyProfilePage.tsx) | Editor thật và form profile hiện tại |
| [Transport chung](../../web-demo/src/api.ts), [student API](../../web-demo/src/modules/student/api.ts), [App](../../web-demo/src/App.tsx) | Session/cookie/CSRF, ApiError, menu; mở rộng trong cùng ứng dụng |
| [V25](../../src/main/resources/db/migration/V25__consent_and_profile_sharing.sql) | School policy, consent lineage/grant, profile share; không tạo các bảng canonical trùng |

Pure completeness thuộc T4; capture snapshot T2 không gọi ngược policy T4. Không đưa nhánh mới vào chuỗi gây vòng phụ thuộc T2→T4→T2.

## 4. API hiện có để dùng ngay

Mọi route trong bảng đã có implementation ở snapshot tuần 2. Mutation dùng cookie session, CSRF, Idempotency-Key UUID và version mới nhất; không gửi actor/student owner tự khai.

| Method/path | Dùng thế nào |
|---|---|
| GET/PUT `/api/v1/students/me/profile` | GET có termId; PUT là toàn bộ ProfileInput, không chỉ gửi delta skills |
| GET `/api/v1/students/me/catalogs?termId=...&kind=MAJOR` | kind bắt buộc: MAJOR/SUBJECT/ORIENTATION; thêm page/size khi phân trang, expectedCatalogVersion khi kiểm phiên bản |
| GET `/api/v1/taxonomy/skills`; POST `/api/v1/taxonomy/skills/resolve` | Danh mục kỹ năng chuẩn và alias do T4 sở hữu; resolve là truy vấn, theo DTO ResolveSkillsRequest |
| GET `/api/v1/invitations/me?page=0&size=25` | T5 trả lời mời của principal, không dùng SCHOOL_STAFF để nhận thay |
| GET `/api/v1/invitations/{id}` | Detail T5; dùng version trả về cho lệnh tiếp |
| POST `/api/v1/invitations/{id}/decision` | ACCEPT hoặc DECLINE; expectedVersion, reasonCode, reasonText đều có key |
| POST `/api/v1/invitations/{id}/withdraw` | ACCEPTED trước Placement; reason/version; không tự hủy Placement |
| GET/POST `/api/v1/students/me/consents` | POST GrantConsent độc lập với ACCEPT |
| POST `/api/v1/students/me/consents/{id}/revoke` | expectedLineageVersion + reasonCode |
| GET `/api/v1/school/terms/{termId}/profile-sharing-policy` | Student đã liên kết được đọc policy của kỳ để dựng field picker |

Ví dụ decision, thay ID/version bằng dữ liệu vừa đọc:

```json
{"decision":"ACCEPT","expectedVersion":1,"reasonCode":null,"reasonText":null}
```

DECLINE dùng `reasonCode="STUDENT_DECLINED"`; không đổi sang token onboarding Mentor. Nhận lời không tự consent, share hoặc Placement.

## 5. Tuần 3: trình tự thực hiện T2-07 và T2-08

### Bước 1 — Chốt phần tái sử dụng và ánh xạ ID

T2-07 do DEV04, T2-08 do DEV03; QA02 kiểm xuyên suốt. Tách danh sách “đã có” ở mục 2 khỏi backlog: chỉ bổ sung Opportunity/Preference và hoàn thiện màn hiện có.

**Điểm cần sửa khi ghép T5:** BC22 quy định `opportunityId=cooperationId`. Hiện `ProposalService.invitation(...)` của T5 đang gán field opportunityId từ demand_id. Cùng T5 sửa mapping/adapter và thêm test, không dùng demandId làm khóa cơ hội hoặc tạo bảng cơ hội thứ hai tại T2. Phần decision/consent hiện tại không dựa vào field này, nhưng liên kết màn cơ hội sẽ phụ thuộc nó.

### Bước 2 — Nối Opportunity projection do T5 sở hữu

Các route sau là **contract target cần triển khai**, chưa có controller runtime trong snapshot này:

| Owner | Target |
|---|---|
| T5 | GET `/api/v1/opportunities`: termId, q, majorCode, enterpriseOrgId, page, size |
| T5 | GET `/api/v1/opportunities/{id}` |
| T5 | PUT `/api/v1/school/cooperations/{id}/publication`: publishedAt, closedAt, expectedVersion |

T5 chỉ công bố Cooperation APPROVED, đúng school/student-term, publishedAt ≤ serverNow < closedAt. T2 đọc qua API/port, không query bảng T5 để tự dựng một projection canonical khác. Dùng dữ liệu fixture có nhãn rõ khi phát triển form riêng; chỉ ghi integration PASS sau khi gọi provider T5 thật.

### Bước 3 — Viết Preference owner T2

1. Thêm DTO/service/repository trong `modules/student`; bảng root/current intent và lịch sử cần giữ các phiên bản. Kiểm tra schema đã có trước khi thêm migration.
2. Triển khai GET/POST `/api/v1/students/me/preferences` và POST `/api/v1/school/preferences/{id}/review` theo BC22.
3. Student owner lấy từ principal; school/term/recipient lấy từ Opportunity authoritative. Không nhận studentUserId hoặc trạng thái REVIEWED do browser tự khai.
4. Lần đầu expectedIntentVersion=0. SUBMITTED→WITHDRAWN do student trước REVIEWED; SUBMITTED→REVIEWED do school có `student:preference:school:review` và delegation đúng school/term.
5. Intent đã REVIEWED giữ bất biến. Submit ý định mới tạo intentVersion tiếp theo và giữ history; withdraw intent đã review trả 409 PREFERENCE_REVIEWED.
6. Unique/current intent theo student/opportunity/term; serialize version dưới lock. Domain + history + success audit + OperationLedger cùng transaction; lỗi audit phải rollback.
7. Preference không tự tạo invitation, consent hoặc Placement. UI T5 review phải gọi API T2, không tạo preference canonical tại T5.

Ví dụ target POST submit:

```json
{"action":"SUBMIT","opportunityId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","message":"Mong muốn thực tập vị trí này","expectedIntentVersion":0}
```

Đây là ví dụ contract, không phải ID tồn tại trong database demo. Union SUBMIT/WITHDRAW phải đóng schema, kiểm field bắt buộc/nullable theo từng nhánh. `WorkflowValues.body` hiện có nullable allowlist theo tên; không áp dụng máy móc cho `message=null` rồi làm request hợp lệ bị 400.

### Bước 4 — Hoàn thiện UI T2-08

- Thêm danh sách cơ hội, filter/pagination, detail quyền lợi/địa điểm/hạn và nút gửi nguyện vọng; hiển thị loading/empty/error/retry rõ.
- Thêm danh sách ý định hiện hành/history cần thiết; cho rút đúng state, thông báo OPPORTUNITY_CLOSED/PREFERENCE_REVIEWED/version conflict; reload từ backend sau mutation.
- Kế thừa InvitationConsentPage cho invitation/consent. Không pre-check consent; hiển thị riêng student decision, consent validity và trạng thái Placement do T5 cung cấp.
- Khi có nhiều lời mời cùng enterprise/school/term/purpose, lineage dùng chung; không reset về 0 theo từng invitation. Giữ key/payload/expiry của request chưa rõ kết quả để retry idempotent.
- Gọi `api<T>` từ transport chung, xử lý 401 bằng cơ chế session-expired, 409 bằng reload có giữ draft, 403 không tự đổi account/role.
- Tuần 3 chưa bao trọn CV/dự án/chứng chỉ hoặc màn xem điểm tuần 4. Nếu thêm file picker, fileId phải đi qua T1 với CLEAN/binding hợp lệ.

### Bước 5 — Bổ sung seam eligibility cho T5-07

Đây là phần phụ thuộc cần phối hợp, không dựng lại eligibility:

- Mở rộng adapter/port hiện có để đáp ứng BC21 `assertEligibleForPlacement(studentUserId,termId,expectedVersion)`, trả record version authoritative và giữ lock đến commit T5.
- Kiểm school/student/term/membership/account hiện hành; schoolApproved=false trả SCHOOL_APPROVAL_REQUIRED, stale trả ELIGIBILITY_CHANGED, term không ACTIVE bị chặn.
- Giữ principal/scope do server tạo; không để T5 copy quyết định trường sang một bảng “approved” khác. `student/port/SchoolApprovalPort` cũ chỉ là seam, cần xem provider/caller trước khi dùng.

## 6. Phụ thuộc và quy tắc dùng chung

| Team | T2 cần/đưa gì | Cách tiếp tục |
|---|---|---|
| T1 | Auth/CSRF, delegation, AuditService/OperationLedger đã có; T1-06 file API còn làm | Không chờ ký lại contract; thiếu file provider giữ deny, vẫn làm Opportunity/Preference được |
| T4 | Taxonomy/completeness đã có | Tái sử dụng; không tự tạo taxonomy/alias hoặc thay predicates |
| T5 | Opportunity publication, invitation owner, share/Placement; nhận eligibility port từ T2 | Hai team kiểm mapping opportunityId và test consumer/provider thật |
| T3 | Task/submission/report API là owner T3 | Không tạo bản ghi submission/report canonical tại T2; UI dùng chung App/transport |

Một chuỗi Flyway toàn repo, hiện cuối V27. Số mới từ V28 trở đi phải phối hợp T1 để tránh trùng với các team; không sửa migration đã áp dụng. Quyền/action mới đối chiếu registry D/E, chỉ thêm subset cần dùng; ADMIN không tự có quyền đọc hồ sơ.

## 7. QA02 — ca tối thiểu và điều kiện bàn giao

| Ca | Kết quả cần chứng minh qua backend thật |
|---|---|
| Opportunity đúng/sai school, trước publish, tại closedAt | Chỉ thấy đúng scope và khoảng [publishedAt,closedAt) |
| Submit lần đầu, retry cùng key, body đổi cùng key | Một intent/effect/audit; payload khác bị chặn |
| Withdraw trước review; review rồi withdraw; submit intent tiếp | State/version/history đúng, intent cũ không bị sửa |
| Trường B review nguyện vọng trường A; student B đổi ID | 403/404 theo contract, không lộ dữ liệu/effect |
| ACCEPT/DECLINE rồi đọc consent/Placement | Không tự có consent/share/Placement; có thể nhận lời dù chưa consent |
| Revoke/regrant, sai enterprise/purpose/field, expiry | Lần đọc sau revoke bị chặn; grant mới không hồi sinh share cũ |
| Thay phone/note so với thay skill/availability trước nominate | Relevant hash cho phép thay đổi không liên quan; dữ liệu matching thay đổi bị stale |
| Browser reload và 320/390 px | Dữ liệu thật được giữ; không tràn trang, không mất draft sau lỗi |
| Eligibility version thay đổi khi T5 approve | T5 bị chặn bằng port T2; không có Placement/outbox mồ côi |

Kế thừa test hiện có, không sửa oracle để khớp implementation. Có thể bổ sung `StudentPreferenceIT` và `student-opportunity-runtime.spec.ts` (tên đề xuất, hiện chưa tồn tại).

Chạy từ root trên database Testcontainers riêng:

```powershell
.\mvnw.cmd '-Dit.test=StudentProfileIT,StudentDataConstraintsIT,Week2WorkflowIT' '-Dfrontend.e2e=true' verify
Push-Location web-demo
npm.cmd run build
Pop-Location
```

Sau khi có test Preference mới, chạy thêm lớp đó và các consumer test ảnh hưởng. Khi tích hợp cuối chạy full verify/contract checks theo [hướng dẫn runtime](../guides/WEEK2_RUNTIME.md). Browser dùng Vite 5178, không chạy hai bộ song song; chờ login hoàn tất rồi mới lấy CSRF mới. Không chạy cả Playwright suite độc lập thiếu fixture Core.

## 8. Môi trường và nội dung team gửi lại

- Máy dev mới dùng [LOCAL_DEMO](../guides/LOCAL_DEMO.md) / `run-local.cmd start` để tạo database riêng. Docker không đồng bộ database giữa máy.
- Stack chung `utbridora-local`, cổng 8088 vẫn bản main tuần 1. Không seed/import/reset database hoặc volume chung; không in `.env.local-demo`, token/password vào log. Admin giữ mật khẩu riêng.
- Không sửa/reset/clean local main repo gốc và không khôi phục commit QA f606b90. Chỉ commit/push/merge khi được yêu cầu trong phiên làm việc tương ứng.
- Bàn giao lại: branch/SHA hoặc snapshot chính xác; task đã làm/phần chưa chạy; DTO/route/migration đổi; test command + report của đúng source; kết quả QA02; lỗi/phụ thuộc còn lại. Tách DEV evidence, fixture và human QA/Lead signoff.
- Đọc cùng [T5](T5_TUAN2_BAN_GIAO_TUAN3.md), [T3](T3_TUAN2_BAN_GIAO_TUAN3.md), [T4](T4_TUAN2_BAN_GIAO_TUAN3.md) khi tích hợp.
