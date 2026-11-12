# T3-08 — Đề xuất schema và tích hợp Weekly Report

> Cập nhật 12/11/2026: đã triển khai V31 theo yêu cầu hoàn tất T3-08; V30 thuộc T5.
> Xem [implementation và evidence mới](T3_08_COMPLETION_EVIDENCE.md).
> Nội dung PROPOSED/chờ dưới đây là snapshot ngày 10/11, không phải trạng thái hiện tại.

Ngày: 10/11/2026, Asia/Ho_Chi_Minh. Owner: DEV06; QA: QA03.

**PROPOSED — chưa duyệt schema, chưa cấp số migration, chưa chạy DDL.**
Không sửa V28 của T1 hoặc V29; ownership phần FR-17 trong V29 chờ Lead xác nhận.
Không tự suy ra V30 hoặc cấp số migration mới.
T1 cấp số tuần tự sau khi đối chiếu nhánh chung và toàn repository.
Tài liệu này không thay Contract Pack, phân công, phê duyệt hay evidence runtime.

## Căn cứ và phạm vi

- Baseline triển khai: branch `feature/t3-week3`, HEAD `2d023fb`.
- Milestone Domain/Application đã commit ở `7d291df`; remote ref Team 3 đã biết là
  `3e6a573` (Submission persistence/wiring), chưa tích hợp. Local ahead 1 / behind 4.
  Phiên HTTP/guard 10/11 giữ checkout, không fetch/sync/commit/push. Phần mới còn local.
- `UTBridora_Plan.xlsx`: CAP NHAT CHUNG dòng 46; T3 dòng 20–21; DEV06/T3-08, QA03.
- SRS v1.2.4: FR-18, FR-19, R25/G25; Contract Pack v1.0 CP-WS3, C.0 và E.3.
- Case, cohort/group, assignments: V21; handover: V27; common operation ledger: V8.
- `PlacementReadPort`, `CaseRuntime`, `WorkspaceAuthority`, `WorkspaceRuntime.caseRef`,
  `TransactionalWorkspaceUnitOfWork`, `OperationLedger`, `AuditService.workflow` hiện có.
- Cập nhật phân công theo thông tin người dùng ngày 10/11: DEV06 sở hữu FR-17 Task
  Comment, FR-18 Weekly Report và FR-19 Report Review. DEV05 sở hữu FR-15/FR-16 và
  Submission history. Ownership schema FR-17 trong V29 còn chờ Lead xác nhận chính thức.
- Report Review gắn một report revision và chuyển SUBMITTED → REVIEWED. Các bảng đề
  xuất dưới đây chỉ dành cho FR-18/19, không dùng Task Comment hay TASK_COMMENTED thay
  Report Review. FR-17 được theo dõi riêng tại [bảng lũy kế DEV06](DEV06_TASK_PROGRESS.md).

## Đối chiếu dependency thực tế

| Dependency | Source hiện có | Ảnh hưởng T3-08 |
|---|---|---|
| Quyền student ghi | V3 `internship:student:submission_write` + STUDENT ceiling | Có seed; vẫn cần own Case + account/membership hiện hành |
| Quyền Mentor review | V3 `internship:enterprise:task_write` + ENTERPRISE ceiling | Có seed; vẫn cần current MENTOR đúng group/case, không cấp HR review |
| Quyền đọc workspace | V21 `t3:workspace:read`, STUDENT/ENTERPRISE/SCHOOL_STAFF | Có seed; phải self/assigned/delegated, không ADMIN bypass |
| WEEKLY_REPORT_DRAFT_SAVED | E.3 có; AuditAction/WorkflowAuditRegistry chưa có | Chặn commit runtime Weekly Report qua T1 audit |
| WEEKLY_REPORT_REVISION_CREATED | E.3 có; runtime chưa có | Tương tự |
| WEEKLY_REPORT_SUBMITTED | E.3 có; runtime chưa có | Tương tự |
| WEEKLY_REPORT_REVIEWED | E.3 có; runtime chưa có | Tương tự |
| TASK_COMMENTED | CP-WS2/FR-17 thuộc DEV06; runtime chưa có | Chặn audit Task Comment production, không chặn logic FR-18/19 |
| t3:comments:write | Registry D có; chưa thấy seed tại checkout | Chặn quyền ghi FR-17 runtime; không dùng submission_write/task_write thay thế |
| SUBMISSION_REVIEWED | Riêng CP-WS2/FR-16 thuộc DEV05 | Không là blocker chung của Weekly Report |
| Case/Placement | CaseRuntime.read + authoritative PlacementReadPort có thật | Có thể compose snapshot và source checks; không cần chờ Submission |
| Giữ Case ổn định tới commit | Guard Task có public CaseChecks; generic Report fence còn thiếu | FR-17 có TaskCommentCaseGuard dùng Target thật và RC; Report vẫn cần public Case fence phù hợp, không tạo Task/Cohort giả |
| Transaction/ledger | Có từ tuần 2 | Adapter T3-08 sử dụng lại, không tạo ledger/transaction manager mới |
| File | Checkout có metadata `files`; remote `origin/feature/t1-06-private-files` đã có V28, `FileReadPort`, `FileService`, `FileResourceAuthorizer` và enum WEEKLY_REPORT, chưa tích hợp vào checkout | Text-only dùng []; sau tích hợp T1 vẫn cần authorizer/binding Report thật, không copy T1 classes hay giả CLEAN |
| Persistence report | Chưa có migration/bảng | Chặn storage/DB integration và API thật, không chặn domain/unit/adapter độc lập |

Đây là kết quả đọc source/seed, không khẳng định database chung đã apply đủ migration.
Không tự thêm permission seed hoặc AuditAction; không thay báo cáo QA/Lead.

## Phần DEV06 hoàn thiện độc lập ngày 10/11

- Domain `WeeklyReport.restore` kiểm lịch sử có revision liên tục, version tăng, root
  version khớp current, không có historical draft, deadline đúng tuần và lateFlag khớp
  submittedAt chính xác. Không replay submit/review/audit khi dựng lại aggregate.
  Đây là factory khôi phục storage-independent, chưa phải JDBC repository/DDL đã duyệt.
- ReportDto chỉ nhận revision thuộc aggregate được chiếu; không ghép nội dung report khác.
- Command kiểm kết quả cached/new effect đúng target report; create phải đúng case/tuần.
  Sai binding hoặc null provider result phải thất bại trước commit.
- Command, audit append và Case mutation fence yêu cầu transaction cho phép ghi;
  UnitOfWork query vẫn dùng được trong read-only transaction. Không tạo manager/ledger mới.
- Các port/fence còn thiếu giữ fail-closed; không thêm controller/bean production.
- Kiểm thử DEV 10/11: 694 unit/service/contract-in-process PASS, 0 fail/error/skip;
  trong đó 136 test Weekly Report. Báo cáo trong target/surefire-reports của lượt chạy.
  Có test contract ở package integration nhưng không khởi động MySQL/Testcontainers.
  Không có lượt database IT/migration/HTTP/E2E mới trong phiên này.

## Nguồn trạng thái và giới hạn tài liệu

Google Sheet gid 841999674 không truy cập được qua công cụ hiện có, nên đối chiếu phân
công dựa workbook local. CAP NHAT CHUNG dòng 12/30/46 xác định DEV06 tuần 1–3; dòng
63/80 là T3-11 tuần 4 và T3-14 tuần 5, chưa tự triển khai trước lịch/phạm vi được giao.
Workbook còn mapping FR/UI khác với owner task tổng; không sửa workbook để tự chốt owner.

Phần còn thiếu của Weekly Report được tách theo kỹ thuật: report storage, public Case
fence, T1 audit, HTTP/security integration, file branch và quyết định read comment.
Không dùng trạng thái kiểm thử của thành viên khác làm điều kiện dừng công việc DEV06.

Cập nhật FR-17: DEV06 đã thêm value/service/consumer ports và bridge Access/Audit/ledger,
tái sử dụng CommentInput/Dto/SubmissionJson.comment của DEV05; không sửa các file đó.
Không viết schema Task Comment trong proposal Weekly Report, không sửa V29 và không
quyết định Assignment.version có tăng khi append comment. Receipt trả version authoritative
cho audit; Lead/DEV05 cần chốt transaction/lock/version protocol khi giao storage adapter.

## Mô hình lưu trữ đề xuất

Root giữ identity, business key, current revision và CAS version. Revision giữ nội dung,
trạng thái, last report version, reason và thời điểm nộp; review và file refs gắn revision.
`version` là token tăng trên root, không reset khi tạo revision; `revision_no` chỉ tăng khi
POST revisions. Ví dụ root v1 draft → v2 submitted → v3 reviewed → v4 revision 2 draft.
Lịch sử revision 1 giữ snapshot version 3. T1/Lead duyệt dictionary trước migration.

| Bảng đề xuất | Khóa và invariant |
|---|---|
| internship_weekly_reports | PK id; UNIQUE(case_id,week_start); FK Case; current_revision_no >=1; root version 1..MAX_V |
| internship_weekly_report_revisions | PK(report_id,revision_no); FK root; nội dung current DRAFT được sửa; submitted/reviewed content không bị ghi đè |
| internship_weekly_report_reviews | PK(report_id,revision_no); FK revision + reviewer users.id; một review bất biến mỗi revision |
| internship_weekly_report_files | PK(report_id,revision_no,ordinal_no); FK revision + files.id; giữ thứ tự refs của từng revision |

Không copy school/enterprise/term hay trạng thái Placement thành nguồn authority mới.
Case root đã có các ID này; quyền phải đọc lại các owner hiện hành.

Cập nhật 10/11/2026 15:02 +07:00: lớp HTTP FR-17/18/19 đã code và kiểm thử standalone,
nhưng mặc định OFF qua `app.t3.week3.http.enabled`; chưa có persistence/provider wiring
production. Không bật gate khi schema/audit/guard chưa đủ. PATCH Report còn bị shared
SecurityConfig deny và cần T1 review, không sửa cấu hình chung trong phiên này.
File consumer port nhận Authentication gốc để chuyển sang ActorContext của T1 sau bàn giao.
Regression cuối 804 PASS; gồm 44 test HTTP/guard mới. Không chạy DB/Flyway/production API.
Danh mục file, 9 route và checklist owner/deliverable ở [tiến độ DEV06](DEV06_TASK_PROGRESS.md).

### DDL minh họa, không phải migration có thể apply ngay

Tên/index/constraint, charset/collation và chính sách precision dưới đây chờ T1/Lead review.
Không ghi file vào `src/main/resources/db/migration/` trong phạm vi hiện tại.

```sql
CREATE TABLE internship_weekly_reports (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 case_id VARCHAR(36) NOT NULL,
 week_start DATE NOT NULL,
 due_at DATETIME(6) NOT NULL,
 current_revision_no INT NOT NULL,
 version BIGINT NOT NULL,
 UNIQUE(case_id,week_start),
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 CHECK(WEEKDAY(week_start)=0),
 CHECK(current_revision_no>=1),
 CHECK(version BETWEEN 1 AND 9007199254740991),
 INDEX ix_weekly_case_week(case_id,week_start,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_weekly_report_revisions (
 report_id VARCHAR(36) NOT NULL,
 revision_no INT NOT NULL,
 report_version BIGINT NOT NULL,
 state VARCHAR(16) NOT NULL,
 completed_work MEDIUMTEXT NOT NULL,
 difficulties TEXT NOT NULL,
 next_plan TEXT NOT NULL,
 reason_code VARCHAR(64) COLLATE utf8mb4_bin NULL,
 submitted_at DATETIME(6) NULL,
 submitted_submicro_nanos SMALLINT NULL,
 late_flag BOOLEAN NOT NULL,
 PRIMARY KEY(report_id,revision_no),
 FOREIGN KEY(report_id) REFERENCES internship_weekly_reports(id),
 CHECK(revision_no>=1),
 CHECK(report_version BETWEEN 1 AND 9007199254740991),
 CHECK(state IN ('DRAFT','SUBMITTED','REVIEWED')),
 CHECK(CHAR_LENGTH(completed_work) BETWEEN 1 AND 10000),
 CHECK(CHAR_LENGTH(difficulties) BETWEEN 0 AND 5000),
 CHECK(CHAR_LENGTH(next_plan) BETWEEN 1 AND 5000),
 CHECK((revision_no=1 AND reason_code IS NULL)
    OR (revision_no>1 AND reason_code IS NOT NULL AND reason_code REGEXP '^[A-Z0-9_]{3,64}$')),
 CHECK((state='DRAFT' AND submitted_at IS NULL AND submitted_submicro_nanos IS NULL AND late_flag=false)
    OR (state IN ('SUBMITTED','REVIEWED') AND submitted_at IS NOT NULL
        AND submitted_submicro_nanos IS NOT NULL AND submitted_submicro_nanos BETWEEN 0 AND 999))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_weekly_report_reviews (
 report_id VARCHAR(36) NOT NULL,
 revision_no INT NOT NULL,
 reviewer_user_id VARCHAR(36) NOT NULL,
 comment TEXT NOT NULL,
 reviewed_at DATETIME(6) NOT NULL,
 reviewed_submicro_nanos SMALLINT NOT NULL,
 PRIMARY KEY(report_id,revision_no),
 FOREIGN KEY(report_id,revision_no) REFERENCES internship_weekly_report_revisions(report_id,revision_no),
 FOREIGN KEY(reviewer_user_id) REFERENCES users(id),
 CHECK(CHAR_LENGTH(comment) BETWEEN 1 AND 2000),
 CHECK(reviewed_submicro_nanos BETWEEN 0 AND 999)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE internship_weekly_report_files (
 report_id VARCHAR(36) NOT NULL,
 revision_no INT NOT NULL,
 ordinal_no SMALLINT NOT NULL,
 file_id VARCHAR(36) NOT NULL,
 PRIMARY KEY(report_id,revision_no,ordinal_no),
 FOREIGN KEY(report_id,revision_no) REFERENCES internship_weekly_report_revisions(report_id,revision_no),
 FOREIGN KEY(file_id) REFERENCES files(id),
 CHECK(ordinal_no BETWEEN 1 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

DB constraints không thay guard ứng dụng. Đặc biệt: current_revision_no phải trỏ revision
có thật; current state REVIEWED phải có đúng một review; revision lịch sử không được DRAFT.
MySQL không có deferred FK cho vòng root/current revision: đề xuất không thêm FK vòng;
insert root + revision 1 và update current pointer trong cùng transaction, xác minh bằng IT.
Một current draft được bảo đảm bởi root pointer + serialized root CAS; không cho hai request
cùng version append revision. Không dùng cascade delete để bỏ lịch sử/audit.

CP-WS3 v1.0 không quy định evidenceFileIds là set. Không tự thêm unique(file_id) hoặc dedup
array; bảng refs giữ ordinal. Giới hạn 20 và UUID syntax ở parser/domain; T1 file port kiểm
owner/binding/purpose/CLEAN. FK `files.id` chỉ giữ tham chiếu, không chứng minh quyền tải.

### Precision và thời gian

- week_start là DATE theo Asia/Ho_Chi_Minh. due_at được tính từ thứ Ba tuần sau 23:59:59,
  lưu UTC; không nhận dueAt hoặc lateFlag từ browser.
- Clock/Instant có nanosecond, DATETIME(6) chỉ giữ microsecond. Không âm thầm truncate thời
  điểm submit sau dueAt 1ns thành đúng dueAt khi reload.
- Đề xuất cần duyệt: lưu UTC DATETIME(6) sau khi truncate microsecond, cộng cột remainder
  `instant.nano % 1000` (0..999); khi đọc cộng lại remainder. Áp dụng submit/review;
  dueAt nguyên giây nên không cần remainder. Adapter phải round-trip chính xác.
- Một lựa chọn khác cần quyết định riêng là giới hạn precision API/Clock; không áp dụng
  lựa chọn đó vào code hiện tại hoặc contract đóng băng để làm test PASS.
- SRS G25 nói mở tuần thứ Hai 00:00; CP-WS3 chỉ quy định Monday/intersection/deadline.
  Lead cần chốt có từ chối tạo draft tuần tương lai không và mã lỗi; không tự thêm rule.

## Protocol tích hợp đề xuất

1. Outer transaction dùng manager chung qua TransactionalWorkspaceUnitOfWork; lấy report
   Session cùng connection/transaction, không tự tạo datasource hoặc transaction thứ hai.
2. Parse strict DTO; actor lấy từ principal T1, recheck account/securityVersion/membership.
3. Preflight current quyền owner/Mentor trước OperationLedger.execute, kể cả cached replay.
   Không kiểm expectedVersion/state DRAFT trước replay để chặn nhầm kết quả đã commit.
4. Với effect mới: PlacementReadPort giữ source; public Case fence giữ/check version Case;
   lock/CAS root sau đó theo protocol DEV05/T1 thống nhất. Case snapshot/version check
   không có lock chỉ phát hiện drift quan sát được, không đủ thay fence đến commit.
5. Root insert UNIQUE hoặc update CAS; ghi revision, review, refs; append đúng E.3 qua
   AuditService.workflow. Audit/DB/file/guard lỗi phải rollback cả effect và ledger.
6. T3 dùng common OperationLedger, không tạo report_commands. Payload hash chứa reportId
   và request; org scope lấy từ owner resource, không từ body client.
7. Trả đúng 201(create/revision), 200(update/submit/review); chưa mở controller khi schema,
   guard, T1 audit hoặc các adapter bắt buộc chưa đủ. Không tự sửa SecurityConfig.

## Các quyết định còn chờ

| Owner | Nội dung cần xác nhận |
|---|---|
| T1 | Số migration sau V27 thực tế; dictionary/DDL/precision/FK/index; audit enum + registry đủ bốn WEEKLY_REPORT_*; nguồn hash bundle |
| DEV05/Lead | Public Case fence cho Report độc lập Task/Cohort; transaction manager và lock protocol chống Case close/cancel/move race |
| Lead/DEV07 | ReportDto v1.0 không có comment/reviewer dù ReportReview nhận comment; chốt read projection bằng diff/version đúng quy trình, không tự mở API mới |
| Lead | Quy tắc draft tuần tương lai và trạng thái giới hạn sau Case close; giữ read history theo quyền hiện hành |
| T1 File | Binding root/purpose/owner, scan, download authorizer và lifecycle file refs |
| QA03 | Acceptance/runtime và reviewer đúng scope; unit PASS không thay QA signoff |

## Kiểm thử sau khi đủ điều kiện

- Migration clean + upgrade trên MySQL riêng được phép; UNIQUE root, FK, current pointer,
  CAS cạnh tranh, revision bất biến, timestamp round-trip (dueAt và dueAt+1ns).
- New effect/replay/khác payload, 20 create/submit/revise cạnh tranh; audit failure/crash rollback.
- Case IN_PROGRESS + source ACTIVE/effectiveAt; source cancel khi event chưa đến; race close/revoke/move.
- Student sai chủ, Mentor sai nhóm/HR/revoke/parent expiry, school delegation, ADMIN không có PII.
- File draft PENDING, submit CLEAN và binding; [] không cần scanner để ghi report dạng text.
- HTTP/CSRF/schema/paging và UI student/Mentor reload qua backend thật.

Không có các lượt IT/migration/E2E mới trong tài liệu này. Kết quả unit thực chạy được báo
riêng trong phiên làm việc; không tự ghi workbook hoàn thành hoặc QA PASS.
