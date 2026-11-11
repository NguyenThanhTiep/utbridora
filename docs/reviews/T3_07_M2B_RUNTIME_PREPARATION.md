# T3-07 — M2B HTTP preparation và đề xuất runtime

**PROPOSAL — PENDING LEAD/T1 REVIEW**

DEV05, 10/11/2026. Phạm vi phiên này: HTTP adapter + unit/controller tests; chỉ đề xuất schema/JDBC và integration test. Không cấp số migration, sửa Core audit/permission, cấu hình DB runtime hoặc nghiệm thu T3-07.

HTTP Preparation đã được review; phần Runtime bên dưới vẫn là đề xuất chưa triển khai. Quyết định Lead về số migration và quyền bổ sung audit được ghi nhận riêng, không đồng nghĩa đã phê duyệt toàn bộ Runtime.

## 1. Revision, contract và trạng thái đã xác minh

- Nhánh chung `feature/t3-week3`; HEAD nền trước commit checkpoint Preparation là `0293e181c8a18be5711e9cf88aed544c79bc026f`. M1 `2d023fb`, M2A `0293e18` đã commit/push. Sau fetch trước checkpoint, remote T3 cùng HEAD nền, không có commit mới cần đồng bộ.
- Fetch toàn origin trước đây từng lỗi quyền ghi object; các lượt rà soát sau và lượt checkpoint hiện tại đã fetch thành công. Không thay đổi Git permissions, checkout, merge/rebase/cherry-pick hoặc tích hợp nhánh T1-06 trong checkpoint này.
- Nguồn contract: [quyết định v1.0](../contracts/README.md), [CP-WS2/C.0/E v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md). Không tự áp v1.1 Candidate.
- [V21](../../src/main/resources/db/migration/V21__internship_runtime.sql) đã tạo `internship_tasks`, `internship_task_assignments`, assignment version/current_submission_id. Không tạo lại các bảng/field này. Lead đã cấp `V29__t3_submission_review_comment.sql` cho T3; bắt buộc baseline/migration theo thứ tự **V28 → V29**.
- V28 là `src/main/resources/db/migration/V28__private_file_service.sql`, thuộc nhánh `feature/t1-06-private-files`, commit `065c5be15f19df2fe8100c42248c5bd6d9c4a956`. Tại snapshot rà soát, origin/main `f1ef217` và T3 `0293e18` chưa chứa V28; migration hiện có trên T3 cao nhất V27. Cần phương án tích hợp baseline T1-06 được Lead/nhóm thống nhất trước Runtime V29, không chỉ chép riêng SQL V28 hoặc tự merge/rebase/cherry-pick.
- `SubmissionService`/`SubmissionPorts` M2A chưa có production bean/adapter. `CaseRuntime` và T5 `PlacementReadPort.readCurrentForGuard`/`JdbcPlacementRead` đã có source; thiếu phần nối M2B và bằng chứng tích hợp, không phải chưa bàn giao toàn bộ nền tảng T1/T5.
- `AuditAction.SUBMISSION_CREATED`/`SUBMISSION_REVISED` đã có. Lead cho phép DEV05 bổ sung **riêng SUBMISSION_CREATED** vào `WorkflowAuditRegistry` với đúng `Spec("submission", Set.of("assignmentId", "versionNo", "fileId", "fileIds", "lateFlag"))`. Chưa sửa registry trong Preparation; không sửa AuditAction và không suy ra quyền bổ sung SUBMISSION_REVISED/REVIEWED/TASK_COMMENTED trong checkpoint này.
- Khi Runtime được duyệt, gọi `AuditService.workflow()` trong cùng transaction; resourceId=submissionId, org từ school scope đã kiểm quyền. Producer URL-v1 dùng fileIds=[]; trường fileId trong allowlist không thêm field fileId vào wire DTO. Không đưa URL/note vào audit. Giữ các entry FILE_* của T1-06 và đồng bộ manifest sourceHashes trên baseline cuối cùng trước contract checks.

### Phân công T3-07 và ranh giới Comment

- [Workbook phân công](../plans/UTBridora_Plan.xlsx), sheet `T3 - THUC TAP`: dòng 17/18/37, cột G/J giao FR-15/FR-16/R21 cho DEV05/T3-07; dòng 19 giao FR-17 Task Comment cho DEV06/T3-08, dòng 20/21 giao FR-18/FR-19 cho DEV06/T3-08. Sheet `CAP NHAT CHUNG` dòng 45/46 xác nhận cùng phân công; dòng 47 giao UI T3-09 cho DEV07.
- [Bàn giao tuần 2](../handoffs/T3_TUAN2_BAN_GIAO_TUAN3.md), mục 4/4.1 đưa Comment API vào T3-07, khác hai sheet trên. Theo phạm vi DEV05 đã làm rõ: **không triển khai FR-17 trong T3-07**; phần bàn giao bị lệch cần Lead/nhóm thống nhất, không tự sửa tài liệu bàn giao trong checkpoint này.
- Review comment là `ReviewInput.comment` của FR-16/DEV05, gắn submission/version và verdict; CP-WS2 yêu cầu comment không rỗng cho cả ACCEPT và REQUEST_REVISION. Task Comment FR-17/DEV06 là `CommentInput.text`, append-only theo assignment, có actor/time và GET/POST `/api/v1/t3/assignments/{assignmentId}/comments`; không thay thế Review và không tự chuyển trạng thái submission.
- M1 đã có CommentInput/CommentDto, parser `SubmissionJson.comment()` và unit test như contract scaffold. DEV06 cần tái sử dụng/phối hợp sửa các file này, tránh tạo DTO/parser trùng. M2A và HTTP Preparation hiện chỉ có submit URL-v1/history; chưa có Task Comment service/controller/persistence.

## 2. Phần HTTP độc lập được triển khai

| Route | Mapping | Giới hạn |
|---|---|---|
| POST `/api/v1/t3/assignments/{assignmentId}/submissions` | Body qua `SubmissionJson.submission`; Idempotency-Key UUID; gọi `SubmissionService.submitUrlV1`; trả 201 `SubmissionDto` | Chỉ URL v1; service từ chối file mode khi chưa có CLEAN/binding adapter và không tạo lại v1 |
| GET cùng path | page=0,size=25 mặc định; range C.0; gọi `SubmissionService.history`; dùng `WorkspaceRuntime.Page<SubmissionDto>` hiện có | Không đọc JDBC tại controller, không dựng history từ current assignment |

- `SubmissionController` nhận `ObjectProvider<SubmissionService>`. Chưa có service bean thật: 503 DEPENDENCY_UNAVAILABLE, không đăng ký placeholder service/persistence. Không tạo `@Configuration` hoặc thay WorkspaceConfiguration trong phiên này.
- Giữ authentication/CSRF của `SecurityConfig` hiện có; không thêm public route hoặc role mapping. Controller không nhận actor/org/state từ body. Ownership/current scoped permission/Case/Placement vẫn do application + adapters kiểm.
- `SubmissionErrors` chỉ áp dụng cho controller mới; dùng `WorkspaceErrorMapping` và `OperationErrors` Core để giữ envelope 6 field, server requestId, Retry-After, denial audit. Không thay handler/controller cũ. Lỗi không dự kiến không trả raw exception/SQL/payload.
- `SubmissionControllerTest` dùng MockMvc standalone, RequestIdFilter thật và mock service/port; có ca đi qua service M2A thật với mocked storage/access boundaries để kiểm foreign owner trả 404 trước mutation. Không chạy SecurityFilterChain, JDBC hoặc transaction database thật.
- POST/GET được định nghĩa không đồng nghĩa API đã hoạt động end-to-end: request hợp lệ vẫn 503 khi chưa nối service thật.
- 503 khi thiếu service chỉ là chặn tạm thời của Preparation. Runtime phải có service/adapters thật và chứng minh POST201/GET200 qua integration; không dùng 503 hoặc mock response làm bằng chứng hoàn thành API production.

## 3. Thiết kế adapter đề xuất — chưa triển khai

Tất cả tên file/bảng/query dưới đây là đề xuất cần review; không tạo migration hoặc adapter ở phiên preparation.

### File dự kiến cho runtime sau phê duyệt

- `src/main/java/vn/utbridora/auth/modules/internship/infrastructure/JdbcSubmissionSession.java`: thực thi SubmissionPorts.Session; scoped queries, current reads/locks, insert-only submission + assignment CAS, gọi Core ledger/audit trong cùng TX.
- `src/main/java/vn/utbridora/auth/modules/internship/infrastructure/TransactionalSubmissionUnitOfWork.java`: TransactionTemplate REQUIRED theo pattern TransactionalWorkspaceUnitOfWork, rollback mọi exception; không dùng REQUIRES_NEW cho effect/ledger/success audit.
- `src/main/java/vn/utbridora/auth/modules/internship/infrastructure/CoreSubmissionAccess.java`: current account/security version/membership, exact submission_write, self owner; read qua WorkspacePorts.Access/CoreWorkspaceAccess + WorkspaceAuthority handover/delegation. Không thêm auth song song.
- `src/main/java/vn/utbridora/auth/modules/internship/infrastructure/SubmissionConfiguration.java`: chỉ sau dependency sẵn sàng, tạo service bean thật với adapters/CaseRuntime/Clock/UUID supplier. Không sửa WorkspaceConfiguration của DEV06.
- `src/main/resources/db/migration/V29__t3_submission_review_comment.sql`: số/tên đã được Lead cấp; nội dung DDL và phương án tích hợp V28 vẫn cần review trước triển khai. Không tạo file trong checkpoint Preparation.
- `src/main/java/vn/utbridora/auth/audit/WorkflowAuditRegistry.java`: được phép thêm riêng Spec SUBMISSION_CREATED nêu trên ở giai đoạn Runtime được duyệt; checkpoint này không sửa Core.
- `docs/contracts/v1.1/manifest.json`: cập nhật sourceHashes theo source cuối cùng sau đồng bộ T1-06/Runtime và chạy contract checks. Không đổi contract v1.0/v1.1 hoặc trạng thái phê duyệt Candidate để làm check PASS.

### Schema tối thiểu cho URL v1 — đề xuất

| Thành phần | Đề xuất | Ràng buộc |
|---|---|---|
| `internship_submissions.id` | VARCHAR(36) PK, UUID từ server | Không tái dùng ID |
| `assignment_id` | VARCHAR(36), FK assignment hiện có | NOT NULL, index phục vụ history |
| `version_no` | INT | >=1; UNIQUE(assignment_id,version_no) |
| `url` | TEXT nullable theo CP-WS2 | M2 chỉ insert URL non-null qua validation HTTPS/no-userinfo; không fetch URL |
| `note` | TEXT nullable, utf8mb4 | Unicode trim/codepoint 0..2000 tại DTO |
| `submitted_at` | DATETIME(6), UTC | Thời gian server, quyết định precision nhất quán với response/cache |
| `late_flag` | BOOLEAN NOT NULL | Tính từ dueAt/lateAllowed đã đọc lại dưới lock |

`fileIds=[]` cho mọi row do URL-v1 writer tạo. Không thêm cột fileId đơn, bytes/path lưu trữ hoặc tự gán CLEAN. Khi file mode được phê duyệt, owner phải thống nhất bảng artifact refs có ordinal và uniqueness, phục vụ đúng danh sách/version bất biến; không suy fileIds cho một row file-mode nếu adapter refs chưa tồn tại.

Giữ `internship_task_assignments.current_submission_id` hiện có. FK pointer, dữ liệu cũ và DDL vẫn cần review trước triển khai migration; không tự ALTER dữ liệu hoặc sửa migration V21. DEV05 không update/delete nội dung submission cũ; nộp v2 và Review thuộc milestone sau của T3-07. FR-17 Task Comment runtime thuộc DEV06, không phải milestone runtime của DEV05.

Bảng trên chỉ là đề xuất phần Submission URL-v1, không tự thu hẹp toàn bộ V29 thành Submission-only. Giữ số/tên V29 đã được cấp. Schema Review/Task Comment nếu đã được Lead duyệt phải được giữ phục vụ milestone sau; nếu mới cấp số/tên thì Lead/DEV06 cần xác nhận DDL/owner trước triển khai. Không tự xóa phần Comment, tạo migration Comment trùng hoặc diễn giải quyền quản lý migration thành quyền làm runtime FR-17.

### Query và transaction dự kiến

1. Đọc assignment/task/group/cohort gốc để xác định trusted scope; owner từ stored student_user_id, actor từ AppPrincipal. Không khóa group/cohort quá sớm chỉ để tái dùng helper: `JdbcWorkspaceSession.group/cohort` hiện có FOR UPDATE; cần review thứ tự lock khi viết adapter mới.
2. Revalidate quyền hiện hành bằng Core (đặc biệt holdOrganization cho mutation) trước ledger/replay; kiểm scope trường/doanh nghiệp/term, không coi role hoặc ENTERPRISE membership là assignment.
3. Delegate `OperationLedger.execute(command, SubmissionDto.class, mutation, result -> result.submissionId().toString())` trong TX của UnitOfWork. Giữ command/hash của service M2A: actor/org/operation + key; assignmentId nằm trong payload hash, cùng key khác assignment phải conflict.
4. Trong callback: CaseRuntime gọi T5 authoritative source FOR SHARE, sau đó Case FOR UPDATE; yêu cầu Case IN_PROGRESS, Placement ACTIVE, effectiveAt/approvedAt đã tới và student/scope khớp. Source lỗi/thiếu phải fail closed, không fallback event/browser/local ACTIVE.
5. Đọc hiện hành và khóa task/assignment sau guard; kiểm lại owner/scope/deadline, expected version, state IN_PROGRESS và current_submission_id null. Giữ task dueAt/lateAllowed ổn định đến commit. Không chỉ đọc snapshot trước TX hoặc tin giá trị query đầu trong REPEATABLE READ. Giữ thứ tự lock thống nhất với start/Task và các luồng tuần 2; cần kiểm deadlock thật với T1/DEV06 trước runtime.
6. Kiểm `EXISTS(SELECT 1 FROM internship_submissions WHERE assignment_id=?)`. Có history thì từ chối tạo v1, không overwrite. Insert version_no=1 + URL/note/time/lateFlag; UNIQUE là chốt chống race.
7. CAS bằng prepared parameters, dạng đề xuất:

```sql
UPDATE internship_task_assignments
SET state='SUBMITTED', version=version+1, current_submission_id=?
WHERE id=? AND version=? AND state='IN_PROGRESS' AND current_submission_id IS NULL;
```

Chỉ thành công khi affected rows=1; ngược lại domain conflict và rollback insert. Chặn version vượt safe integer qua policy/CAS. Không update Task/Case version thay assignment version.

8. Mandatory audit dùng enum SUBMISSION_CREATED, resource submission, metadata allowlist E: assignmentId,versionNo,fileIds,lateFlag; không URL/note/free text. Audit failure, crash hoặc lỗi CAS phải rollback cả submission, assignment, audit-success và ledger DONE. Không thay bằng writeScoped để né registry chưa hỗ trợ.
9. History sau current read authorization: `WHERE assignment_id=? ORDER BY version_no DESC,id ASC LIMIT ? OFFSET ?`, COUNT cùng assignment/snapshot nhất quán. Bind params; không ghép SQL từ client. Không require ACTIVE chỉ để đọc lịch sử nếu viewer vẫn có quyền; download file có guard riêng.

Clock runtime cần thống nhất độ chính xác DATETIME(6)/UTC với DTO/cache: ví dụ Clock tick microsecond tại cấu hình và bind/read UTC theo WorkflowValues. Đây là quyết định tích hợp cần chốt, chưa thay Clock/service/source hiện có. Deadline đúng hạn tại equality; DB boundary tests dùng độ phân giải thực tế của stored dueAt.

## 4. Integration test cần chạy khi runtime/database sẵn sàng

Tất cả ca dưới đây **NOT_RUN / BLOCKED** ở preparation. Đề xuất file mới `src/test/java/vn/utbridora/auth/modules/internship/SubmissionIT.java`, kế thừa fixture thật từ InternshipRuntimeIT/Week2WorkflowIT; không mock Core auth, source T5, ledger hoặc DB cho bằng chứng tích hợp.

| Nhóm | Acceptance/evidence phải có |
|---|---|
| Flyway + boot | Baseline T1-06 đã được nhóm thống nhất tích hợp; V28 → V29 đúng nội dung/checksum và thứ tự trong flyway_schema_history, không trùng version/table; application khởi động với adapters thật; không phải 503 fallback |
| URL v1 | Owner hợp lệ POST201; một immutable row version1, assignment SUBMITTED/version+1/current pointer đúng; GET200/reload đúng URL/note/time/lateFlag |
| Auth + scope | Anonymous401, CSRF thiếu/sai403 theo security hiện có; foreign student404; quyền/membership/account revoked deny; viewer Mentor/handover/HR/delegation hiện hành đúng scope cho history |
| Source guard | Case terminal/not-IN_PROGRESS, Placement APPROVED/CANCELLED/COMPLETED/future effectiveAt hoặc source không đọc được: không effect; kiểm cả local Case IN_PROGRESS cũ sau source cancel và race cancel/submit |
| Version/state | Stale409 VERSION_CONFLICT, state409 STATE_CONFLICT, MAX không tăng; fresh key không tạo v1 lần hai kể cả pointer thiếu nhưng có history |
| Deadline | equality đúng hạn; sau stored dueAt thì lateFlag hoặc422 SUBMISSION_LATE; kiểm sau lock wait, UTC/microsecond, rollback không success audit |
| Replay + concurrency | 20 concurrent requests cùng key/payload -> cùng kết quả, một row/audit-success; cùng key khác URL/note/assignment409; key mới cùng expectedVersion chỉ một writer thành công |
| Ledger lifecycle | REQUEST_IN_PROGRESS/Retry-After, response expiry, rollback/retry cùng key; cache replay phải revalidate current auth; không nhân đôi effect sau crash |
| Atomicity/audit | Audit registry/persistence FAIL hoặc CAS FAIL -> không row/refs/success audit/DONE; injected failure từng điểm và retry thành công; log không URL/note/token/SQL |
| History | Stored snapshots/order/count/pagination nhất quán; v1 không bị thay khi current pointer đổi. End-to-end v1->review->v2 chờ M3/M4, chưa thuộc nghiệm thu preparation |
| Files/dependency | File request không được gán CLEAN giả; hiện503; khi CP-FILE sẵn, kiểm CLEAN/bind đúng toàn bộ list và rollback toàn request. Không gọi HTTP/DNS để fetch URL |
| Hồi quy tuần 2 | Task start, group move/handover, cohort scope, source lock order và Session/CSRF; không tác động Weekly Report/UI của DEV06/DEV07 |

`/api/v1/t3` **đã nằm trong DemandSecurityErrors.applies** tại source hiện có; SecurityConfig sử dụng handler này cho lỗi session/CSRF trước MVC. Nhận định trước đây rằng T3 chưa được handler bao phủ là sai và được đính chính tại checkpoint này. Security integration của luồng Submission mới chưa chạy: cần xác minh 401/CSRF403/envelope/server requestId qua filter chain thật. Advice controller không xử lý lỗi đã bị filter chain chặn; chưa có căn cứ sửa Core security chỉ từ các MockMvc standalone test.

## 5. Evidence và bàn giao phiên preparation

- Checkpoint chỉ gồm SubmissionController.java, SubmissionErrors.java, SubmissionControllerTest.java và tài liệu này. Giữ nguyên ba file Java đã review; chỉ cập nhật nội dung tài liệu. Các file AI cá nhân/package-lock và untracked ngoài phạm vi không được stage/commit; không cập nhật file ngữ cảnh cũ khi chỉ được phép sửa tài liệu này.
- Local DEV unit/controller test: **421 PASS**, 0 failures/errors/skipped, **BUILD SUCCESS**, lượt chạy kết thúc 10/11/2026 10:28:24 +07:00. Gồm 56 controller test mới + 354 test M1/M2A/Core đã chọn trước đó + 11 WorkspaceErrorMappingTest. Không chạy integration/DB/SecurityFilterChain thật.
- Lệnh thực thi: `.\mvnw.cmd '-Dtest=SubmissionControllerTest,SubmissionServiceTest,SubmissionDtosTest,SubmissionJsonTest,SubmissionPolicyTest,TaskDtosTest,CohortGroupDtosTest,InternshipActivityPolicyTest,WorkspaceDomainTest,WorkspaceServiceTest,CoreWorkspaceAccessTest,WorkspaceFrontendContractTest,OperationLedgerTest,WorkspaceErrorMappingTest' test`.
- Evidence local (ignored, không commit): `target/t3-07-m2b-unit-test.log`, `target/surefire-reports/TEST-vn.utbridora.auth.modules.internship.api.SubmissionControllerTest.xml` và 13 XML suite tương ứng trong cùng lượt. Lượt đầu có 52 failures do helper test đếm nhầm 7 thay vì 6 field envelope; đã sửa kỳ vọng theo Core và cách restub Mockito trong test, không sửa response Core.
- DEV05 cho phép commit/push đúng bốn file checkpoint sau review diff/whitespace, Surefire và staged list. HEAD trước checkpoint là `0293e18`; SHA checkpoint theo lịch sử Git sau commit. Trước push phải fetch lại: remote T3 tiến lên thì dừng báo cáo, không ghi đè/force push hoặc tự đồng bộ bằng merge/rebase/cherry-pick.
- V29 và quyền thêm riêng SUBMISSION_CREATED đã được cấp, không còn báo là đang chờ cấp số/quyền này. Dependency còn lại: phương án tích hợp V28/T1-06 trên nhánh chung, review nội dung DDL/ownership schema Comment, phê duyệt Runtime adapters và kiểm thử DB/security/transaction thật. Manifest hiện cần đồng bộ sourceHashes (lượt review trước ghi nhận thiếu 7 source Java Submission); không sửa manifest hoặc chạy generator trong checkpoint này.
- Chưa integration, QA signoff hoặc CI mới, chưa T3-07 DONE. Chỉ công bố checkpoint Preparation; không triển khai migration/JDBC/audit/M3/M4 hoặc FR-17, không tích hợp T1-06, không merge main. Dừng sau checkpoint để DEV05 review.
