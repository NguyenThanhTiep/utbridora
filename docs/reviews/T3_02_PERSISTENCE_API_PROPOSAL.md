# T3-02 — Đề xuất persistence và điểm nối API

DEV06 — 07/11/2026, Asia/Saigon. **PROPOSED / CHƯA DUYỆT / CHƯA CHẠY DDL.**
Tài liệu review, không là migration, quyết định status hoặc xác nhận QA.

## Căn cứ và phạm vi

- Workbook `CAP NHAT CHUNG` dòng 12: T3-02 tuần 1, DEV06/QA03;
  Entity/API cơ bản và acceptance tạo nhóm/nhiệm vụ, chặn sửa trái quyền.
- Dòng 30: T3-05 tuần 2, Task API/trạng thái/lịch; dependency T3-02/T3-04.
  Lát cắt hiện tại chỉ Task create/read sơ bộ, không Submission/Review/lịch.
- `T3 - THUC TAP` dòng 5/6: FR-04/FR-02. FR-02 còn yêu cầu lưu status,
  nhưng chưa có enum/default/transition được chốt trong CP-WS1.
- Contract v1.0 C.0, CP-AUTHZ1, CP-WS1/2, BC21/41 và G là baseline.
  Unique Cohort: enterprise + school + term + code. Không thêm constraint
  từ v1.1 chưa được phê duyệt. Core V1 dùng UUID dạng VARCHAR(36).
- DEV05 `origin/feature/t3-placement-inbox` tại e256903 có record/interface
  lifecycle; Case Read Port/provider chưa có. Case Runtime SKIP theo DEV06.

## Dictionary đề xuất cho owner review

Các UUID dùng VARCHAR(36), version BIGINT trong [0,9007199254740991].
Tên bảng/cột dưới đây là đề xuất, chưa cấp version Flyway. Không thêm bảng
Case, Placement, Term, assignment HR/Mentor, inbox/outbox/idempotency của owner khác.

| Bảng | Cột tối thiểu đề xuất | Key / index / invariant |
|---|---|---|
| t3_cohorts | id, enterprise_org_id, school_org_id, term_id, code VARCHAR64, name VARCHAR200, version | PK id; UNIQUE(enterprise_org_id,school_org_id,term_id,code); index(scope,code,id) cho sort CP-WS1. Status chưa xác định, chưa đưa vào DDL |
| t3_groups | id, cohort_id, code VARCHAR64, version | PK id; FK cohort; UNIQUE(id,cohort_id) để FK composite chống membership gán group khác cohort; index(cohort_id,code,id). Không tự thêm UNIQUE(cohort_id,code) khi contract chưa quy định |
| t3_group_memberships | id, cohort_id, group_id, case_id, joined_at, left_at nullable, reason_code nullable, version | PK id; FK(group_id,cohort_id) → groups(id,cohort_id); generated current_case_id=case_id khi left_at IS NULL, ngược lại NULL; UNIQUE(cohort_id,current_case_id); index(case_id,left_at), index(group_id,left_at). Giữ row cũ khi move |
| t3_tasks | id, group_id, title VARCHAR200, description TEXT, due_at, priority VARCHAR6, late_allowed BOOLEAN, version | PK id; FK group; index(group_id,due_at,id). group_id là snapshot bất biến; không đổi khi Case chuyển group |
| t3_task_assignments | id, task_id, case_id, student_user_id, state VARCHAR17, version, current_submission_id nullable | PK id; FK task; UNIQUE(task_id,case_id); index(case_id,state,task_id), index(student_user_id,state,task_id). Create state NEW, submission NULL; không triển khai transition ở T3-02 |

`mentorUserIds` không lưu danh sách JSON trong group: resolve qua owner assignment,
không tạo hệ phân quyền thứ hai. Case ID và student ID chỉ lấy từ nguồn tin cậy.
`current_submission_id` giữ nullable để ánh xạ DTO; FK Submission chờ owner/schema,
không tạo bảng hoặc provider Submission trong T3-02.

## DDL trọng tâm để review (không thực thi)

Ví dụ dưới đây minh họa key và quan hệ nội bộ; **không phải DDL hoàn chỉnh để apply**.
FK org/user/term/Case cần đối chiếu tên bảng, kiểu và collation owner trước duyệt.
Không tạo bảng thay owner để làm FK chạy được.

```sql
CREATE TABLE t3_cohorts (
  id VARCHAR(36) PRIMARY KEY,
  enterprise_org_id VARCHAR(36) NOT NULL,
  school_org_id VARCHAR(36) NOT NULL,
  term_id VARCHAR(36) NOT NULL,
  code VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
  name VARCHAR(200) NOT NULL,
  version BIGINT NOT NULL,
  CONSTRAINT ck_t3_cohort_version CHECK (version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT uq_t3_cohort_scope_code UNIQUE (enterprise_org_id,school_org_id,term_id,code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t3_groups (
  id VARCHAR(36) PRIMARY KEY,
  cohort_id VARCHAR(36) NOT NULL,
  code VARCHAR(64) COLLATE utf8mb4_0900_bin NOT NULL,
  version BIGINT NOT NULL,
  CONSTRAINT uq_t3_group_cohort UNIQUE (id,cohort_id),
  CONSTRAINT fk_t3_group_cohort FOREIGN KEY (cohort_id) REFERENCES t3_cohorts(id),
  CONSTRAINT ck_t3_group_version CHECK (version BETWEEN 0 AND 9007199254740991),
  INDEX ix_t3_group_list (cohort_id,code,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t3_group_memberships (
  id VARCHAR(36) PRIMARY KEY,
  cohort_id VARCHAR(36) NOT NULL,
  group_id VARCHAR(36) NOT NULL,
  case_id VARCHAR(36) NOT NULL,
  joined_at DATETIME(6) NOT NULL,
  left_at DATETIME(6) NULL,
  reason_code VARCHAR(64) NULL,
  version BIGINT NOT NULL,
  current_case_id VARCHAR(36) GENERATED ALWAYS AS
    (CASE WHEN left_at IS NULL THEN case_id ELSE NULL END) STORED,
  CONSTRAINT uq_t3_current_group UNIQUE (cohort_id,current_case_id),
  CONSTRAINT fk_t3_member_group FOREIGN KEY (group_id,cohort_id) REFERENCES t3_groups(id,cohort_id),
  CONSTRAINT ck_t3_member_version CHECK (version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT ck_t3_member_time CHECK (left_at IS NULL OR left_at >= joined_at),
  INDEX ix_t3_member_case (case_id,left_at),
  INDEX ix_t3_member_group (group_id,left_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t3_tasks (
  id VARCHAR(36) PRIMARY KEY,
  group_id VARCHAR(36) NOT NULL,
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  due_at DATETIME(6) NOT NULL,
  priority VARCHAR(6) NOT NULL,
  late_allowed BOOLEAN NOT NULL,
  version BIGINT NOT NULL,
  CONSTRAINT fk_t3_task_group FOREIGN KEY (group_id) REFERENCES t3_groups(id),
  CONSTRAINT ck_t3_task_version CHECK (version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT ck_t3_task_priority CHECK (priority IN ('LOW','NORMAL','HIGH')),
  CONSTRAINT ck_t3_task_description CHECK (CHAR_LENGTH(description) BETWEEN 1 AND 10000),
  INDEX ix_t3_task_list (group_id,due_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE t3_task_assignments (
  id VARCHAR(36) PRIMARY KEY,
  task_id VARCHAR(36) NOT NULL,
  case_id VARCHAR(36) NOT NULL,
  student_user_id VARCHAR(36) NOT NULL,
  state VARCHAR(17) NOT NULL,
  version BIGINT NOT NULL,
  current_submission_id VARCHAR(36) NULL,
  CONSTRAINT uq_t3_task_case UNIQUE (task_id,case_id),
  CONSTRAINT fk_t3_assignment_task FOREIGN KEY (task_id) REFERENCES t3_tasks(id),
  CONSTRAINT ck_t3_assignment_version CHECK (version BETWEEN 0 AND 9007199254740991),
  CONSTRAINT ck_t3_assignment_state CHECK
    (state IN ('NEW','IN_PROGRESS','SUBMITTED','REVISION_REQUIRED','COMPLETED')),
  INDEX ix_t3_assignment_case (case_id,state,task_id),
  INDEX ix_t3_assignment_student (student_user_id,state,task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

Code trim Unicode ở domain trước key lookup/insert; không lower-case hoặc bỏ dấu
khi baseline chưa yêu cầu. Đề xuất binary collation nhằm giữ equality giống Java;
owner cần duyệt rõ trước DDL (không suy default ai_ci là đúng). UUID phải có
canonical formatting ở adapter; collation UUID/FK khớp Core. Text validation vẫn
phải kiểm codepoint ở application, không chỉ trông vào VARCHAR.

Task/assignment DDL minh họa ở trên vẫn chờ review dictionary: priority theo enum
LOW/NORMAL/HIGH; state theo AssignmentState v1.0, không thêm enum nghiệp vụ.
`due_at` đề xuất DATETIME(6) UTC; Instant Java có nano precision, cần quyết định
precision/round-trip trước mapping, không âm thầm truncate input. DTO không tự
thêm điều kiện dueAt > now vì baseline chưa quy định.

## JPA và transaction

- Có thể ánh xạ UUID sang VARCHAR(36), long/Long sang BIGINT, EnumType.STRING,
  Instant sang cột UTC khi precision được chốt. Tách entity khỏi domain record.
- `@Version` cho Cohort/Group/Task/Assignment; kiểm initial version (domain hiện
  tạo 1 theo ví dụ CP-WS1) với provider JPA trước duyệt mapping. Không sửa version
  thủ công ngoài cơ chế CAS. Generated current_case_id: insertable/updatable=false.
- T1 cấp migration V-next sau duyệt schema; không tự chọn V8. Chưa tạo Entity/
  Repository vì schema/status/precision chưa được phê duyệt.
- Cohort: lookup composite key để báo duplicate sớm; UNIQUE DB là hàng rào race
  cuối. Chuyển đúng constraint thành DUPLICATE_COHORT, không map mọi integrity
  failure thành duplicate. Constraint/FK lỗi khác không tiết lộ SQL/PII.
- Add/move: lock group theo UUID thứ tự nhất quán, kiểm current membership và
  CAS source/target version; Case version qua public owner port cùng transaction.
  Không ghi repository Case của DEV05. Move đóng row cũ, append row mới và reason;
  bump version của resource theo contract. Không sửa Task group snapshot.
- Create Task: recheck quyền cho mọi Case, current membership, Case IN_PROGRESS,
  Placement ACTIVE/effectiveAt tại mutation; bảo vệ race cancel/revoke với owner
  bằng transaction/locking/version fence đã thống nhất. Không dùng Activated event.
- Domain writes + membership history + audit hợp lệ cùng transaction; audit lỗi
  phải rollback. Mapping action/metadata phải được T1 chỉ rõ, không dùng enum sai.
- Adapter `TransactionalWorkspaceUnitOfWork` dùng Spring REQUIRED; nếu có outer
  transaction thì outer quyết định commit cuối. Session adapter thật phải dùng
  cùng transaction manager. Unit test transaction manager không chứng minh MySQL.
- C.0 idempotency phải dùng common ledger của T1 ở command boundary trước khi
  mở REST mutation. Service hiện chưa có MutationContext hoặc ledger adapter;
  không tạo bảng idempotency riêng và không tuyên bố API đã đủ điều kiện mở.

## Kế hoạch API tối thiểu

| Operation baseline | Phần code độc lập | Điều kiện còn thiếu trước đăng ký endpoint |
|---|---|---|
| POST /api/v1/t3/cohorts | DTO/domain/create service + Core access composition | Term/HR authority thật, permission seed đúng owner, schema/status, persistence/audit/idempotency |
| POST /api/v1/t3/groups/{groupId}/members | add service, current membership/version | Case owner port/check + mutation/version protocol, transaction/history; output CaseRef phải từ owner, không chế response |
| POST /api/v1/t3/groups/{fromGroupId}/members:move | move source/target guards và immutable transfer | Cùng điều kiện add; mapping GroupMembershipResult từ Case sau commit |
| POST /api/v1/t3/tasks; GET /api/v1/t3/tasks/{taskId} | create/read sơ bộ, filter assignment theo quyền | Mentor/delegation + Case/Placement provider, persistence và command boundary. Không mở workflow T3-05 |
| Tạo Group thủ công | Domain Group đã validate snapshot | Chờ method/path/request/response được owner chốt; không dùng auto-group thay FR-04 |

Các GET list/query DTO đã có, service list chưa triển khai khi chưa có scoped
query adapter; không lấy tất cả dữ liệu rồi phân trang trước kiểm quyền. Chưa mở
auto-group, import, remove member, mentor handover, Submission/Review hoặc UI.
WorkspaceErrorMapping ánh xạ nội bộ theo C.0 (401/403/404/409/422/503), không có
ControllerAdvice và không thay error legacy Core. HTTP binding strict/presence,
CSRF, idempotency header và production HTTP error vẫn cần kiểm tích hợp riêng.

## Quyết định cần owner trả lời

1. Lead: Cohort status tuần 1 gồm giá trị/default nào, lưu nội bộ hay có diff wire?
2. Lead/T3: duyệt dictionary, equality code, precision Instant và initial versions
   ở trên hay chỉ rõ thay đổi; T1 cấp version/thứ tự migration nào?
3. Lead/owner contract: method/path/body/response tạo Group thủ công là gì?
4. DEV05: public operation nào kiểm và bump Case version cùng mutation Group;
   revision/type/method và transaction protocol nào được bàn giao? Không yêu cầu
   hoàn thiện toàn bộ Inbox/Case runtime trong lượt này.
5. Owner assignment/delegation + T1: provider/method authoritative và permission/
   audit/common idempotency mapping hiện hành cho các operation trên là gì?
6. Qua đầu mối T3-01/T1-01, T5 chỉ rõ Term/Placement provider và cơ chế chống race
   cancel ở mutation. Đây là dependency tích hợp cụ thể, không thêm toàn T5 làm
   dependency chính thức tuần 1.

Code domain/application và adapter composition có thể review riêng. Chưa có DB,
HTTP, provider thật hay acceptance/QA production evidence.

Kiểm tra local ngày 07/11/2026: Maven unit regression 154 PASS, không fail/error/
skip. Bundle `--check` FAIL `manifest.json` drift; 13 source T3 chưa có trong
sourceHashes. Không sửa bundle ở task DEV06 này; T1 điều phối cập nhật snapshot
khi review source. Chưa khẳng định remote CI hoặc khả năng merge đã đạt.
