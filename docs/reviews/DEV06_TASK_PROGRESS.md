# DEV06 — Bảng đối chiếu lũy kế và phạm vi T3-08

> 14/11/2026: đã triển khai T3-11 trên feature/t3-week4 từ base DEV05 e480abc.
> Publish/revision, School/HR policies, Student triple gate và evidence-file guard: xem [bàn giao T3-11](T3_11_IMPLEMENTATION_HANDOFF.md). Kết quả DEV ghi trong evidence; chưa QA/commit/push/merge. Các mục dưới giữ làm lịch sử.
> Bổ sung V40 hội tụ các lịch sử V38 T1/T3: [kiểm thử và giới hạn](T3_V40_CONVERGENCE_VERIFICATION.md), [cấu hình database](../guides/WEEK4_MIGRATION_RUNTIME.md). Các báo cáo V39 trước đó là bằng chứng lịch sử, không thay kết quả V40.

> Bản triển khai mới nhất 12/11/2026: [T3_08_COMPLETION_EVIDENCE.md](T3_08_COMPLETION_EVIDENCE.md).
> Đã bổ sung V31, Report/Review DB runtime, file integration và API đọc nhận xét.
> Các ghi chú chờ migration trong snapshot cũ bên dưới được thay thế bởi bản mới này.

> Cập nhật runtime 12/11/2026: xem [bàn giao mới](T3_08_RUNTIME_HANDOFF.md).
> Đã qua 751 kiểm thử hồi quy, 7 kiểm thử Comment HTTP/MySQL và 868 kiểm tra contract.
> Weekly Report còn chờ migration được duyệt và kiểm thử Report/file trên DB; chưa hoàn tất T3-08.
> Các mốc bên dưới là lịch sử trước bản tích hợp trong worktree riêng.

Cập nhật 10/11/2026, Asia/Ho_Chi_Minh. Phạm vi: tiến độ DEV06, không đánh giá công việc
kiểm thử của thành viên khác. Snapshot Git mới nhất đã kiểm: `feature/t3-week3`, HEAD
`7d291df`, remote tracking ref `3e6a573`; ahead 1 / behind 4. Phiên triển khai HTTP/guard
bên dưới không fetch/sync/commit/push; số liệu dựa trên remote ref đã có tại checkout.

## Quyết định phân công hiện hành

Theo thông tin người dùng cung cấp sau khi DEV05 đối chiếu sheet `T3 - THUC TAP` và
`CAP NHAT CHUNG`:

| Phạm vi | Owner nghiệp vụ hiện hành | Trạng thái schema |
|---|---|---|
| FR-15, FR-16, Submission history | DEV05 | Không tự sửa source hoặc migration của DEV05 |
| FR-17 Task Comment | **DEV06** | Chờ Lead xác nhận chính thức ownership schema trong V29 |
| FR-18 Weekly Report | **DEV06** | Report schema ở mức PROPOSED, T1 cấp số riêng |
| FR-19 Report Review | **DEV06** | Gắn report revision; không trộn với Submission Review |

Handoff T3 tuần 2 → 3 còn đặt comment trong T3-07 là checkpoint cũ. Không mặc định giao
FR-17 cho DEV05 vì DTO/parser do DEV05 viết hoặc vì bảng comment có thể nằm trong V29.
Không sửa handoff của Leader hay workbook để tự chốt ownership schema còn pending.

## Lũy kế tuần 1–5

Nguồn workbook local `docs/plans/UTBridora_Plan.xlsx`; SRS v1.2.4; Contract Pack v1.0
được README phê duyệt viện dẫn. Google Sheet chưa đối chiếu được trong lượt trước;
thông tin phân công FR-17 mới ở trên là chỉ đạo người dùng, không giả là đọc online.

| Tuần / task | Locator / FR / acceptance DEV | Source và phần đã có | Phần còn thiếu hoặc cần phối hợp |
|---|---|---|---|
| 1 / T3-02 | CAP NHAT CHUNG dòng 12; FR-02/04; Cohort/Group/Task cơ bản, scope, uniqueness và chặn ghi trái tổ chức | DEV06: DTO/domain, WorkspaceService, CoreWorkspaceAccess, transaction consumer tại 01f0c48/c0850f5. Leader bổ sung WorkspaceRuntime/JdbcWorkspaceSession/API/V21 tại 757ddbf | Persistence dùng JDBC, không tạo JPA trùng. Manual group removal/create và Cohort status chưa đủ luồng/contract để kết luận toàn FR hoàn tất |
| 2 / T3-05 | Dòng 30; FR-09/11–14; Task deadline/priority, independent assignments, start/calendar, Placement ACTIVE | WorkspaceRuntime.createTask/readTask/tasks; WorkspaceTaskActions.start/calendar; WorkspaceTaskController. Leader bổ sung workflow tuần 2 tại f1ef217 | Mã lỗi Start hiện 409 STATE_CONFLICT khác CP-WS2 422 INVALID_ASSIGNMENT_TRANSITION; chi tiết Submission history ghép từ DEV05, không viết lại |
| 3 / T3-08 / FR-17 | Dòng 46 và sheet T3 dòng 19; append/list comment đúng assignment, actor/time server, không đọc chéo | Reuse CommentInput/CommentDto/SubmissionJson.comment; DEV06 có TaskComment, TaskCommentService/Ports, Access/Audit/Commands, unit/service test | Chờ ownership schema V29, storage adapter, assignment/Case fences, t3:comments:write seed và TASK_COMMENTED runtime; chưa mở API |
| 3 / T3-08 / FR-18 | Dòng 46 và sheet T3 dòng 20; báo cáo case/tuần, immutable revision, server dueAt/lateFlag | Milestone 11 file 7d291df; WeeklyReport/Policy/restore, ReportDtos/Json, WeeklyReportService/Ports và các adapter độc lập | Report schema/repository, public Case fence, audit/runtime/HTTP integration, file branch và quyết định tuần tương lai |
| 3 / T3-08 / FR-19 | Sheet T3 dòng 21; Mentor review current SUBMITTED → REVIEWED, giữ lịch sử cũ | WeeklyReport.review; service review guard; comment/reviewer lưu trong revision, không sửa review cũ | ReportDto v1.0 chưa có comment/reviewer projection cho UI; cần quyết định contract trước khi thêm field/API |
| 4 / T3-11 | Dòng 63; FR-21/22, R24; school publish + school policy + HR visibility, cả ba điều kiện trước GET | Chưa thấy runtime evaluation/visibility tương ứng trong checkout T3 | Phụ thuộc T3-10 và T1 delegation; chưa tự triển khai trước phạm vi được giao |
| 5 / T3-14 | Dòng 80; sửa lỗi Task/Report/điểm, bảo toàn version và tránh lộ điểm | Chưa thực hiện dưới task này | Triển khai theo lỗi DEV được giao và dependency T3-11, không coi test hiện tại là hoàn tất tuần 5 |

Các dòng FR/UI khác còn mapping DEV06 vào T3-03/T3-06/T3-12 trong khi bảng task tổng
giao DEV07; cần chốt trách nhiệm backend/UI, không tự nhận frontend. Sau ngày 35 còn
FR-03/23/25–27 và phần mở rộng FR-28–30. FR-03 đã có preview/confirm do Leader bổ sung;
không tự đổi lịch hoặc trạng thái workbook vì source đã có một phần.

## Ba loại comment khác nhau

| Loại | Resource / DTO | Người ghi và tác động |
|---|---|---|
| Task Comment / FR-17 / DEV06 | Assignment; CommentInput{text}, CommentDto{commentId,assignmentId,authorUserId,text,createdAt} | Student self hoặc assigned Mentor với t3:comments:write. Append-only, không review submission, không chuyển state assignment. GET sort createdAt ASC,commentId ASC |
| Submission Review Comment / FR-16 / DEV05 | Submission version; ReviewInput{expectedAssignmentVersion,verdict,comment}, ReviewDto | Mentor review current submission; ACCEPT → COMPLETED, REQUEST_REVISION → REVISION_REQUIRED; Submission history do DEV05 sở hữu |
| Weekly Report Review / FR-19 / DEV06 | Current report revision; ReportReview{expectedVersion,comment} | Mentor đúng scope; SUBMITTED → REVIEWED; bản cũ giữ nguyên khi tạo revision mới. Không dùng CommentDto để tự thay đổi ReportDto v1.0 |

## Tái sử dụng từ DEV05

- Giữ nguyên `SubmissionDtos.CommentInput` và `CommentDto`, không tạo wire types trùng.
- `TaskCommentCommands` gọi trực tiếp `SubmissionJson.comment(String)`: object đóng,
  chỉ có text, Unicode trim/codepoint 1..2000, từ chối actor/verdict/reportId client khai.
- Giữ nguyên và chạy regression `SubmissionDtosTest` / `SubmissionJsonTest` cho validation,
  serialization, required/null/type/coercion/unknown/duplicate/trailing JSON. Các test mới
  tập trung vào actor/scope/transaction/append/history và command orchestration FR-17.
- Reuse Workspace UnitOfWork, common OperationLedger, Case/Placement port và scoped
  authority/handover hiện có. Không copy SubmissionService từ remote chưa tích hợp.

## FR-17 độc lập đã triển khai

1. TaskComment là immutable append value; IDs/author/time lấy từ server, text dùng DTO sẵn.
2. Application create/list và preflight trước ledger; không thêm expectedVersion vào body
   vì CommentInput v1.0 chỉ có text. Assignment version được đọc/giữ qua owner port.
3. Kiểm target Cohort/Group/Task/Assignment và Case school/enterprise/term/student; Case
   IN_PROGRESS, Placement ACTIVE/effectiveAt và approvedAt hợp lệ khi ghi. Read history
   vẫn kiểm quyền hiện hành, không buộc Case còn active.
4. Case fence + assignment fence trước append; drift/resource substitution/missing provider
   fail-closed. Recheck writer sau fence. Không update Assignment/Submission repositories.
5. List chỉ một assignment, phân trang trước trả, kiểm binding/order/duplicate IDs.
6. Ledger cùng transaction; reauthorize trước cached replay, không append effect/audit lần hai.
   Kết quả trả đúng assignment/actor/text. Read-only transaction không được ghi.
7. TASK_COMMENTED có resourceId=assignmentId; metadata chỉ commentId,assignmentVersion,
   không có text. Thiếu enum/registry thì từ chối, không thay bằng action Report/Submission.

## Phần cần xác nhận trước runtime

| Owner | Quyết định / deliverable |
|---|---|
| Lead | Ownership table/index/FK/DAO FR-17 trong V29; ranh giới DEV06/DEV05, tránh hai migration/table cùng nghiệp vụ |
| Lead/DEV05 | Assignment lookup/fence và Case fence public; append có bump Assignment.version hay giữ version hiện hành; protocol lock và audit version. Không tự áp quyết định vào code DEV05 |
| T1 | Seed/ceiling exact t3:comments:write; TASK_COMMENTED và bốn WEEKLY_REPORT_* vào audit runtime; common metadata/rollback |
| Lead/T1/DEV06 | Schema/persistence Weekly Report theo proposal riêng, không chiếm V28/V29 hoặc tự chọn V30 |
| Lead/DEV07 | Read projection comment/reviewer Report; tích hợp UI khi có backend thật |

Task Comment là text-only, không phải chờ File Service để hoàn thiện logic độc lập.
File Service chỉ liên quan nhánh evidence của Report/Submission. Chưa tạo controller/bean,
repository giả, SQL runtime hoặc migration; không tự phê duyệt các phần pending.

## Kiểm thử DEV trong lượt cập nhật

Maven offline `test`, chọn toàn bộ `*Test.java` trong package internship cùng
OperationLedgerTest và AuthorizationPolicyTest: **725 PASS, 0 failure/error/skipped**.
FR-17 mới: 31 test (value 1, service 19, access/audit 6, commands 5). Weekly Report:
136 test. Các test DTO/parser của DEV05 chạy nguyên trạng trong regression.

Các store/manager/mock chỉ ở test. Không chạy MySQL/Testcontainers IT, migration,
HTTP hoặc E2E. Chưa chứng minh DB uniqueness, lock race, durable replay hay runtime API.
Đây là bằng chứng DEV của snapshot hiện tại, không là kết quả công việc của thành viên khác.

## Phiên dữ liệu mẫu và fake adapter — 10/11/2026, 14:15 +07:00

Phạm vi DEV06: FR-17 Task Comment, FR-18 Weekly Report, FR-19 Report Review. Không sửa
source DEV05/T1, contract, permission, audit registry hay migration. Không ghi file AI cá nhân.

### Phần code bổ sung

- `WeeklyReportService`: kiểm lại writer/active sau khi chờ Case/file guard; ghi nhận thời
  điểm submit/review sau lần kiểm quyền cuối. Tránh lateFlag sai khi request bắt đầu đúng
  hạn nhưng chờ lock/scan/authority qua dueAt. Giữ thứ tự kiểm state/version trước file.
- `TaskCommentService`: kiểm lại quyền và lấy createdAt sau guard; timestamp ghi sau lần
  kiểm quyền cuối, không dùng thời điểm request bắt đầu. Không đổi DTO/Assignment state.
- `WeeklyReportServiceTest`: xác nhận Mentor permission được kiểm trước và sau guard.
- `Dev06WorkflowFixture`: fixture dùng chung cho cả ba luồng trong src/test, có dữ liệu
  Case/Placement/Task/Assignment/actor và nội dung Report/Comment/Review tổng hợp.
- `Dev06WorkflowScenarioTest`: 35 ca mới qua commands + application service thực.

### Runtime có sẵn và phần fake

| Nhóm | Tái sử dụng / giới hạn |
|---|---|
| Transaction | Dùng chính TransactionalWorkspaceUnitOfWork và WeeklyReportUnitOfWork. Reuse Manager có sẵn từ WeeklyReportAdapterTestSupport; manager không có DB |
| Business/DTO/parser | Domain, application service, command adapter, CommentInput/Dto và parser hiện hữu; không copy hoặc sửa source DEV05 |
| Identity/authority, Case/Placement/Assignment | Adapter production hiện hữu tiếp tục nối Core/WorkspaceAuthority/CaseRuntime/PlacementReadPort. Scenario mới dùng dữ liệu quyền/scope/fence giả, không chứng minh JDBC guard thực |
| Report/Comment persistence và audit | Một memory state chung tham gia callback transaction Spring. Snapshot rollback cả root/revisions/comment/event/cache; failAudit sau khi append cũng phải hoàn tác |
| OperationLedger | Commands vẫn phụ thuộc OperationLedger chung. Scenario fake riêng I/O committed replay bằng Mockito, dùng payloadHash thật; không tạo ledger production thứ hai. Không mô phỏng expiry/reservation/durable DB locking |
| Files | FakeFileReadPort trong src/test implement consumer WeeklyReportPorts.Files. Mô phỏng PENDING/CLEAN/REJECTED, FILE_NOT_CLEAN và binding root/owner/purpose. Không copy T1 FileReadPort từ nhánh chưa tích hợp hoặc cấp quyền download |

Không có fake Spring Bean, profile production hay endpoint mới. Thiếu dependency thật
tiếp tục fail-closed; các adapter plain class hiện tại chưa tự trở thành API production.
Memory store giữ Assignment.version trong receipt fixture; đây không là quyết định tăng
hay giữ version của implementation thật.

### Bằng chứng DEV mới

- Lượt đầu fixture có school/enterprise đảo thứ tự, guard từ chối; đã sửa dữ liệu mẫu,
  không nới guard. Lượt chọn 3 class sau sửa: 82 PASS trước ca Clock cuối cùng.
- Regression cuối: Maven offline test, chọn toàn bộ internship `*Test.java` cùng
  OperationLedgerTest và AuthorizationPolicyTest: **760 PASS, 0 failure/error/skipped**,
  BUILD SUCCESS lúc 14:15:42 +07:00. Trong đó Dev06WorkflowScenarioTest: **35 PASS**.
- Bao phủ owner/outsider, permission/revoke/expiry/replay, Case/Placement/Assignment sai,
  fence thiếu/drift, root duplicate/stale version, frozen revision, audit/commit/outer
  transaction rollback, CLEAN/NOT_CLEAN/binding mismatch và thời điểm dueAt/qua dueAt.
- 20 request đồng thời: cùng key create/comment/submit/review/revise chỉ một effect;
  key khác giữ root unique/CAS, append comment không mất lịch sử. Memory lock toàn cục
  cố ý tuần tự hóa truy cập; PASS không chứng minh race/UNIQUE/lock của MySQL.
- NOT RUN: ứng dụng, Flyway, DB integration, HTTP/E2E, scanner/storage thật và durable
  ledger replay. Không thực hiện hoặc đánh giá nhiệm vụ kiểm thử của thành viên khác.

### Chuyển sang adapter thật

1. Lead chốt ownership schema FR-17 trong V29, semantics Assignment.version và cấp số
   riêng cho schema Report; giữ proposal hiện có, không tự chiếm V28/V29/V30.
2. Owner bàn giao Assignment lookup/fence và Case mutation fence phù hợp. Task có thể
   dùng public CaseChecks với Target thật; Report độc lập Task cần owner fence phù hợp,
   không tạo Cohort/Task giả để gọi guard. Reuse Core/WorkspaceAuthority/PlacementReadPort.
3. Implement Report Session/Comment Session trên persistence thật, join manager/connection
   hiện có: UNIQUE root, CAS version, history append-only và current pointer atomically.
4. T1 cung cấp permission t3:comments:write, TASK_COMMENTED và bốn WEEKLY_REPORT_* trong
   runtime registry; nối AuditService thật cùng transaction và OperationLedger bean chung.
5. Nối consumer Files sang T1 FileReadPort và nguồn metadata được thống nhất cho draft
   PENDING; giữ owner/root/purpose/scan check. File Service không chặn Comment text-only.
6. Lead/DEV07 chốt read projection comment/reviewer của ReportDto; chưa tự thêm field
   hoặc route ngoài CP-WS3 v1.0. Khi đủ dependency mới wire production, kiểm DB/HTTP trong
   môi trường được phép. Các service/domain không phụ thuộc các fake trong src/test.

## Phiên hoàn thiện HTTP/guard — 10/11/2026, 15:02 +07:00

Yêu cầu: tiếp tục hết phần tuần 3 DEV06 đủ căn cứ và liệt kê phần còn thiếu. Phân công
giữ CAP NHAT CHUNG dòng 46 và T3 - THUC TAP dòng 19–21: FR-17/18/19. Không thay schema
PROPOSED thành approved, không sửa V28/V29, source DEV05/T1 hoặc file AI cá nhân.

### Đã triển khai thêm

- FR-17 có CaseGuard riêng, adapter `TaskCommentCaseGuard` gọi public CaseChecks với
  Cohort/Group/Case thật rồi kiểm snapshot authoritative sau lock. Không còn bắt FR-17
  phụ thuộc generic Report CaseFence. Yêu cầu writable READ_COMMITTED; RR/default không
  rõ isolation fail-closed, không tự thay transaction manager/config chung.
- Controller cho 9 route đúng CP-WS2/3. Mutation chỉ đi qua Commands/OperationLedger;
  query qua service. UUID/key/query trùng/unknown, actor, body đóng, DTO và status được
  kiểm tại transport; không thêm field comment/reviewer vào ReportDto.
- Controller và advice có gate `app.t3.week3.http.enabled`, mặc định OFF. Chưa cung cấp
  service/provider Spring Bean production. Test xác nhận mặc định không có controller;
  bật gate khi thiếu provider làm context fail, không cài fake để khởi động.
- File consumer port nhận Authentication gốc để adapter tương lai chuyển sang T1
  ActorContext mà không dựng actor mới từ owner UUID hoặc bỏ securityVersion.
- HTTP test qua service/domain thật + fixture chung: comment, hai report revisions,
  Mentor feedback, đọc lại bản cũ, rollback audit, replay và file chưa CLEAN.

### Route đã code, chưa kích hoạt runtime

| Method / path dưới /api/v1/t3 | Input / output |
|---|---|
| GET /assignments/{assignmentId}/comments | page/size → CommentPage |
| POST /assignments/{assignmentId}/comments | CommentInput →201 CommentDto |
| POST /reports | ReportInput →201 ReportDto |
| PATCH /reports/{reportId} | UpdateReport →200 ReportDto |
| POST /reports/{reportId}/submit | SubmitReport →200 ReportDto |
| POST /reports/{reportId}/reviews | ReportReview →200 ReportDto |
| POST /reports/{reportId}/revisions | CreateReportRevision →201 ReportDto |
| GET /reports/{reportId} | revisionNo optional → ReportDto |
| GET /reports | ReportQuery → ReportPage |

Không bật gate chỉ để demo. Production SecurityConfig vẫn default-deny PATCH /reports/*;
T1 phải review matcher phù hợp khi cho phép tích hợp. Các GET/POST hiện hữu vẫn cần
session, CSRF cho mutation, scoped service guards và audit thật, không chỉ route matcher.

### Kiểm thử thực chạy

- Lượt đầu có lỗi setup Mockito re-stub và token CSRF raw; đã sửa test dùng XOR token
  đúng cơ chế mặc định Spring Security, không tắt masking/CSRF.
- Targeted: 95 PASS; HTTP workflow thêm 3 PASS.
- Regression cuối offline Maven test: **804 PASS, 0 failure/error/skipped**, BUILD SUCCESS
  lúc 15:02:24 +07:00. So với snapshot 760, thêm **44 ca**: Week3HttpControllerTest 30,
  TaskCommentCaseGuardTest 11, Dev06HttpWorkflowTest 3.
- Có MVC dispatcher, serialization, scoped error envelope và CsrfFilter thật trong test
  cô lập. Không khởi động ứng dụng/production chain, không MySQL/Flyway/DB integration.
  Memory rollback/lock không chứng minh database durable transaction hoặc DB race.

### Phần chưa hoàn thành và deliverable cần nhận

| Cần nhận từ owner | Phần DEV06 làm tiếp sau bàn giao |
|---|---|
| Lead/T1: ownership Comment trong V29, semantics Assignment.version; duyệt Report dictionary và cấp số migration riêng | Implement Comment Session và Report Session thật: unique/CAS/history/current pointer/append-only, không đặt SQL runtime trên schema chưa duyệt |
| DEV05: tích hợp branch/Session Assignment thật theo quy trình chung được phép; public lookup/lock cùng transaction RC | Bọc AssignmentContext/assignment/lockAssignment thành TaskCommentPorts.Assignments; không copy SQL hoặc tạo bảng assignment mới |
| Lead/DEV05: public Case fence cho Report độc lập Task; composition RC phù hợp | Wire Case/Placement guard, CaseGuard FR-17 và transaction boundary thật. FR-17 không chờ fence Report, nhưng vẫn cần Assignment adapter/storage thật |
| T1: seed/ceiling t3:comments:write; TASK_COMMENTED và bốn WEEKLY_REPORT_* runtime; review PATCH route | Nối access/audit adapters hiện có; composition service/command beans và production HTTP khi guard/storage đầy đủ |
| T1 File: FileReadPort/metadata/resource-authorizer bàn giao trên checkout/schema thật | Adapter Files dùng Authentication gốc, kiểm owner/root/purpose/PENDING/CLEAN. Report text-only [] có thể tích hợp trước File |
| Lead/DEV07: chốt read projection comment/reviewer của Report | Giữ wire v1.0 hiện tại; chỉ thêm projection sau diff/version được duyệt, bàn giao UI API thật |

Chưa thể báo hoàn thành toàn bộ T3-08 runtime. Phần JDBC/wiring/DB-HTTP integration là
công việc DEV06 còn phải làm khi đủ schema/provider; không lấy phần việc của người khác
làm bằng chứng hoàn tất và không chờ dữ liệu thật để dừng các phần độc lập đã được giao.

### File của lượt này

- Tạo main: api/Week3RequestValues.java, TaskCommentController.java,
  WeeklyReportController.java, Week3HttpErrors.java; infrastructure/TaskCommentCaseGuard.java.
- Sửa main thuộc DEV06: application/TaskCommentPorts.java, TaskCommentService.java,
  WeeklyReportPorts.java, WeeklyReportService.java.
- Tạo test: api/Week3HttpControllerTest.java; infrastructure/TaskCommentCaseGuardTest.java,
  Dev06HttpWorkflowTest.java.
- Sửa test DEV06: application/TaskCommentServiceTest.java, WeeklyReportServiceTest.java;
  infrastructure/Dev06WorkflowFixture.java.
- Cập nhật tài liệu DEV06: DEV06_TASK_PROGRESS.md và T3_08_WEEKLY_REPORT_SCHEMA_PROPOSAL.md.

Các đường dẫn Java trên nằm dưới src/main hoặc src/test/java/vn/utbridora/auth/modules/internship.
Không commit/push/merge/rebase hoặc đổi branch; commit 7d291df và file local khác được giữ.
