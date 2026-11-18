# T3-10 — DEV05: Rubric và đánh giá bất biến

Ngày: 14/11/2026. Nhánh: `feature/t3-week4`; base: `f731320e300aead220e0d584ecbcd77f704e348e`.
Đây là triển khai DEV; kết quả QA03, Lead và CI được ghi nhận riêng.

## 1. Đối chiếu Plan và nguồn yêu cầu

| Nguồn | Phạm vi DEV05 | Cách thực hiện |
|---|---|---|
| `CAP NHAT CHUNG`, dòng 62, T3-10 | Rubric và đánh giá phiên bản bất biến; phụ thuộc T3-07 | Tạo/sửa/submit, tính điểm server-side, snapshot và lịch sử |
| `T3 - THUC TAP`, dòng 24, FR-20 | Mentor chấm trọng số tổng 100; lưu version template và minh chứng; chưa tự công bố | DTO CP-WS4, rubric theo trường/kỳ, File Service T1 |
| `T3 - THUC TAP`, dòng 38, R23 | Weight > 0, tổng 100; score [0,5]; HALF_UP; không ghi đè SUBMITTED/PUBLISHED | BigDecimal, snapshot mỗi lần lưu, CAS version, test âm tính |
| `CAP NHAT CHUNG`, dòng 63, T3-11 | DEV06: school publish/revision và điều kiện xem điểm | Chuẩn bị root/published pointer; API và chính sách này thuộc DEV06 |
| `CAP NHAT CHUNG`, dòng 64, T3-12 | DEV07: UI rubric/publish/kết quả | API và cách xử lý lỗi/reload ở mục 3 |

Nguồn: [Plan](../plans/UTBridora_Plan.xlsx), [bàn giao tuần 4](../handoffs/T3_TUAN3_BAN_GIAO_TUAN4.md),
[quyết định contract](../contracts/README.md), CP-WS4/C.0/D/E và SRS v1.2.4 FR-20/R23.
Quyết định ngày 13/11 đã duyệt đúng snapshot SRS/Contract v1.1; nhãn Candidate và
ghi chú chờ duyệt trong tài liệu cũ không phải một yêu cầu xin duyệt lại.

- Contract SHA-256: `805b2db30c7a9d4acd76c3d4ed93eeefa149d121a9848f846441294101c68012`.
- SRS SHA-256: `92efaaec4efb66bfe0fa69e5de6a706476f93f58f808575982b9d95662438bd4`.
- Không sửa hai snapshot, không sửa các ô trạng thái Plan thành QA PASS.
- Bàn giao cũ nhắc domain rubric/evaluation sẵn có, nhưng base hiện tại chưa có lớp
  nghiệp vụ T3 tương ứng; implementation này tái sử dụng nền tảng workspace/core.

### Phụ thuộc và người nhận bàn giao

| Loại | Task / owner | Trạng thái và ảnh hưởng |
|---|---|---|
| Phụ thuộc trực tiếp theo Plan | T3-07 / DEV05 | Đã tích hợp vào main; chạy lại hồi quy submission/review trong lượt verify này |
| Nền tảng dùng lại | T1 identity, delegation, ledger/audit và File Service T1-06 | Có trên main; T3-10 chỉ thêm adapter EVALUATION và hai audit action đúng registry |
| Dữ liệu nguồn | T5 Placement và T3 Case/group/Mentor | Mutation cần nguồn ACTIVE/effective/approved và Case IN_PROGRESS; không tự tạo/phê duyệt Placement thật |
| Minh chứng file | Scanner T1-07 / T1 | Chưa có scanner; submit không file hoạt động, có file phải chờ CLEAN thực tế |
| Consumer | T3-11 / DEV06 | Nối school revision/publish, published pointer và chính sách xem điểm |
| Consumer | T3-12 / DEV07 | Dùng API rubric, lưu/submit, reload/history; UI công bố/kết quả còn phụ thuộc T3-11 |

T3-10 không phải chờ DEV06/DEV07 triển khai consumer để kiểm thử phần Mentor.
Demo đầy đủ từ chấm đến sinh viên xem điểm vẫn cần cả T3-11 và T3-12.

## 2. Hành vi đã triển khai

- Rubric có `templateId`, `templateVersion`, các criterion; thuộc đúng school/term.
  GET trả phiên bản mới nhất của từng template, sắp `templateId ASC`, có phân trang.
  Catalog được cấp bằng fixture có scope; không có CRUD template trong MVP.
  [SQL fixture cho DB riêng](../testing/fixtures/T3_10_RUBRIC_PRIVATE_DB.sql) không tự chạy;
  phải chọn đúng school/term và template UUID mới. Test tự tạo fixture riêng.
- Một Case có một evaluation root. Tạo root thứ hai trả `409 EVALUATION_ALREADY_EXISTS`.
- Scores phải đủ, đúng và duy nhất mỗi criterion. Không tự thêm điểm mặc định hoặc
  dùng điểm sao doanh nghiệp. Score/weight không đi qua `double`.
- `total100 = ROUND_HALF_UP(sum(weight * score / 5), 2)`. Ví dụ 40/60 và 5/3 = 76.00.
  Chỉ làm tròn tổng cuối; precision sai ở input bị từ chối, không tự làm tròn input.
- Create tạo DRAFT version 1/revisionNo 1. Mỗi PUT tạo snapshot version +1.
  Submit tạo snapshot SUBMITTED version +1; các snapshot trước không UPDATE/DELETE.
- Mentor không thể sửa hoặc submit lại SUBMITTED/PUBLISHED bằng một thao tác mới.
  Replay đúng key/body vẫn trả kết quả cũ sau khi kiểm tra quyền hiện hành.
- Quyền Mentor theo Case/group hiện tại, parent grants và membership thực tế.
  HR/Student/School không dùng API Mentor để xem điểm nháp. Đổi nhóm/thu hồi quyền
  có hiệu lực với cả đọc và replay. Không lấy quyền từ role/score trong request.
- Mutation khóa nguồn Placement rồi Case, tiếp đến root; kiểm ACTIVE/effectiveAt,
  approvedAt và Case IN_PROGRESS. Đọc lịch sử của Mentor còn quyền không cần Case active.
- Audit, ledger, snapshot, file refs và root pointer cùng transaction. Audit chỉ có
  `caseId`, `templateVersion`, `evaluationVersion`, không có điểm/comment/minh chứng.

## 3. API bàn giao DEV07

Prefix `/api/v1/t3`. Dùng session cookie, `X-Context-Assignment-Id` của Mentor hiện hành.
POST/PUT cần CSRF và UUID `Idempotency-Key`. GET không cần hai mutation header đó.

| Method / route | Kết quả |
|---|---|
| GET `/rubric-templates?termId=UUID&page=0&size=25` | `200 Page<RubricTemplate>`; scope trường/kỳ |
| POST `/evaluations` | `201 EvaluationDto`, DRAFT |
| PUT `/evaluations/{evaluationId}` | `200 EvaluationDto`, chỉ DRAFT |
| POST `/evaluations/{evaluationId}/submit` | `200 EvaluationDto`, SUBMITTED |
| GET `/evaluations?caseId=UUID` | `200` bản hiện hành; `204` nếu Mentor được phép nhưng Case chưa có evaluation |
| GET `/evaluations/{evaluationId}` | `200` snapshot hiện hành, Mentor đúng Case |
| GET `/evaluations/{evaluationId}?version=N` | `200` snapshot version N; không phải revisionNo |
| GET `/evaluations/{evaluationId}/history?page=0&size=25` | `200 Page<EvaluationDto>`, version DESC |

Các GET evaluation là phần đọc bổ sung cho reload/history, được ghi tại đây;
không chỉnh nội dung contract v1.1 đã đóng băng. DTO giữ nguyên CP-WS4.
Không dùng các GET Mentor này để thay thế StudentResultDto/triple gate của DEV06.

Ví dụ create, thay UUID bằng Case/template thực tế đã được phép:

```json
{
  "caseId": "00000000-0000-4000-8000-000000000081",
  "templateId": "00000000-0000-4000-8000-000000000091",
  "templateVersion": 1,
  "scores": [
    {"criterionCode":"QUALITY","score":5,"comment":null,"evidenceFileIds":[]},
    {"criterionCode":"PROCESS","score":3,"comment":"Cần bổ sung kiểm thử","evidenceFileIds":[]}
  ]
}
```

PUT dùng `{"expectedVersion":1,"scores":[...]}` với toàn bộ scores.
Submit dùng `{"expectedVersion":2}`. Mọi field của Score phải có, kể cả comment null.
Từ chối field dư/trùng/thiếu, numeric string, score ngoài miền và version không nguyên.
GET history/template có page 0..10000, size 1..100; query trùng hoặc ngoài danh sách bị chặn.

Reload: gọi GET theo Case để tìm root, xử lý 204 trước khi parse JSON; nếu có root,
hiển thị snapshot hiện hành và tải history khi cần. Mỗi thao tác mới dùng key mới.
Nếu response thất lạc, retry đúng key/body; 409 thì tải lại current version và giữ
form chưa gửi. Không lấy version của bản lịch sử để ghi lên bản hiện hành.

| Lỗi | Xử lý |
|---|---|
| 400 SCHEMA_VALIDATION / INVALID_REQUEST | Sửa shape/kiểu dữ liệu/header; không coercion |
| 401 SESSION_EXPIRED | Đăng nhập lại, xóa dữ liệu private đang hiển thị |
| 403 ACCESS_DENIED / 404 RESOURCE_NOT_FOUND | Kiểm lại scope; không hiển thị điểm cache |
| 409 VERSION_CONFLICT / STATE_CONFLICT | Tải lại snapshot; không tự ghi đè |
| 409 EVALUATION_ALREADY_EXISTS | GET theo Case để tiếp tục root hiện có |
| 409 IDEMPOTENCY_KEY_REUSED | Không dùng lại key với payload khác |
| 422 RUBRIC_INVALID | Đủ/đúng criterion và tổng trọng số 100 |
| 422 FILE_BINDING_INVALID / 409 FILE_NOT_CLEAN | Đúng root/purpose và chờ kết quả scanner |

## 4. Minh chứng file

Create yêu cầu mọi `evidenceFileIds=[]`. Sau khi có root:

1. Upload qua T1 `POST /api/v1/files`, multipart `resourceType=EVALUATION`,
   `resourceId=evaluationId`, `purpose=EVIDENCE`. Giới hạn/MIME do T1 kiểm tra.
2. PUT scores gắn file ID. Draft chấp nhận file PENDING hoặc CLEAN có đúng binding.
3. Submit yêu cầu mọi file CLEAN và storage tồn tại theo FileReadPort thực tế.

`EvaluationFilePolicy` kiểm quyền Mentor hiện hành. Mentor thay thế chỉ đọc được
file đã gắn vào snapshot, không đọc upload chưa gắn của người khác. Student/HR
chưa được đọc file đánh giá qua adapter này; DEV06 cần tích hợp chính sách công bố.
Không có file vẫn submit hợp lệ. Scanner T1-07 chưa được triển khai trong task này;
CLEAN trong IT là fixture đầu ra scanner, không phải bằng chứng quét antivirus.

## 5. Dữ liệu và điểm nối DEV06

Migration mới: `db/migration/base/V38__t3_rubric_evaluation_history.sql`, sau main V37.
V38 cần được T1/reviewer đối chiếu số global trước khi merge. Không sửa V1–V37,
không Flyway repair/reset/out-of-order và không áp vào DB shared trong phiên này.

| Bảng | Nội dung |
|---|---|
| internship_rubric_templates | PK templateId/version, school/term, JSON text chính xác; runtime chỉ đọc |
| internship_evaluations | Một root/Case; current_version, published_version nullable |
| internship_evaluation_versions | Snapshot INSERT-only theo root/version; revisionNo/state/template refs, total100, người lưu/thời gian |
| internship_evaluation_files | FK file và snapshot; giữ refs lịch sử |

Snapshot JSON dùng LONGTEXT + JSON_VALID để giữ decimal text chính xác; total100
có cột DECIMAL(5,2). FK kép buộc current/published pointer thuộc cùng root.
Template đã dùng được snapshot; không chỉnh row template cũ để đổi điểm, cấp version mới.
Không có template writer/CRUD; người cấp fixture phải giữ nguyên các version đã cấp.

DEV06 tiếp tục với evaluationId là ID root ổn định, version tăng cho mọi mutation,
revisionNo tăng khi trường tạo revision. Khi tạo DRAFT revision mới, giữ
published_version cũ; chỉ đổi published_version khi publish thành công.
Phải tái kiểm school delegation, current Case/Placement và CAS trước mọi mutation,
thêm đúng audit action của T3-11, không lấy quyền từ adapter Mentor.
StudentResult chỉ đọc published snapshot khi đủ ba điều kiện; không dùng current
pointer nếu current đang DRAFT/SUBMITTED. Không có API publish/revision/student result
được bật bởi T3-10. Normal Case closure vẫn là luồng riêng.

Nền tảng tái sử dụng: `AuthorizationPolicy`, `WorkspaceAuthority`, `CaseRuntime`,
`PlacementReadPort`, `OperationLedger`, `AuditService`, `FileService`.
Chỉ thêm audit subset EVALUATION_DRAFT_SAVED/SUBMITTED theo registry đã duyệt.
Permission `internship:enterprise:evaluate` đã có; không tăng role ceiling.
SecurityConfig nhận đúng route PUT `/api/v1/t3/evaluations/*` với session/CSRF;
service vẫn bắt buộc quyền evaluate và Mentor đúng Case cho mọi request.

## 6. Kiểm thử và trạng thái bàn giao

**DEV verified ngày 14/11/2026; chưa commit/push, chưa nghiệm thu QA03/Lead/CI.**
Build sạch hoàn tất lúc 10:06 (+07), lượt kiểm tra migration bổ sung hoàn tất lúc
10:19 (+07); cả hai BUILD SUCCESS. Test dùng MySQL 8.4/Testcontainers và private
file root riêng, JDK 24.0.1 biên dịch `release 21`.

| Kiểm tra | Kết quả thực tế |
|---|---|
| Toàn bộ unit test | 1.319 PASS, 1 SKIP, 0 lỗi; gồm 41 unit test mới cho T3-10 |
| 10 bộ integration được chọn, qua hai lượt chạy | 69 PASS, 1 SKIP, 0 lỗi |
| Riêng T3-10 HTTP / schema | 15/15 + 1/1 PASS |
| Hồi quy T3-07 / T3-08 | Submission 20, comment 7, report 8 và các schema test đều PASS |
| Week3MigrationIT | 1 test PASS, kiểm đủ 13 lộ trình main/T2/T4/T5 hội tụ V38, giữ history/checksum và dữ liệu bất biến |
| Contract / audit registry / portability | 868/868, 3/3 và 1/1 PASS; bundle `--check` PASS |
| Diff / Markdown | `git diff --check` PASS; kiểm tra code fences, liên kết nội bộ và khoảng trắng PASS |

Hai SKIP: `PrivateFileStorageTest.rejectsSymlinkObjectAndRoot` bị abort trên Windows;
`InternshipRuntimeIT.realHrAndMentorBrowserFlow` chưa bật `frontend.e2e`.
Không suy rộng kết quả này thành toàn bộ IT của repository, browser E2E hoặc Java 21
runtime đã PASS. Không chạy UI mới trong task backend này.

Lệnh build/hồi quy đã chạy:

```powershell
.\mvnw.cmd -B '-Dit.test=T310EvaluationRuntimeIT,T310SchemaUpgradeIT,T307SubmissionRuntimeIT,T307SchemaUpgradeIT,T308CommentRuntimeIT,T308ReportRuntimeIT,T308SchemaUpgradeIT,MigrationUpgradeIT,InternshipRuntimeIT' clean verify
.\mvnw.cmd -B '-Dit.test=Week3MigrationIT' test-compile failsafe:integration-test failsafe:verify
```

Lượt thứ hai kiểm tra test migration được cập nhật kỳ vọng từ V37 lên V38; không
thay source nghiệp vụ giữa hai lượt. Đã giữ các kiểm tra dữ liệu/constraint cũ,
chỉ tăng số migration theo V38 và bổ sung assertion bảng evaluation cho các lineage.

[Bằng chứng JSON](../testing/evidence/T3_10_DEV_VERIFICATION.json) ghi tổng số,
từng suite/SKIP, SHA-256 report, log và snapshot source. Source fingerprint:
`ec7d362fd6a5f81c1364444200d98f3527fa955da7cdcf461dedd1aa49dc3924`.
Log đầy đủ nằm local tại `.work/t3-10-regression.log` và `.work/t3-10-lineages.log`.

Trong các lượt chạy trước khi chốt, đã sửa lỗi route PUT bị security từ chối;
build sạch cũng loại bỏ classpath migration cũ trong `target`. Kết quả ở bảng trên
là các lượt chạy sau khi sửa, không dùng kết quả lỗi trước đó để tính PASS.

Coverage: công thức 76.00, rounding một lần/HALF_UP, decimal sát biên, input strict,
template version/snapshot, DRAFT/SUBMITTED/PUBLISHED, history/reload, denied roles,
revoked parent, đổi nhóm, current source, CSRF, replay/concurrency, file binding/CLEAN,
audit rollback và nâng cấp V37→V38 cùng FK/unique constraints.

File cá nhân/AI notes và `.work` không thuộc commit. Không deploy/restart/seed DB
shared, không tự tạo Placement nghiệp vụ thật, không thay QA03/Lead ký nghiệm thu.
