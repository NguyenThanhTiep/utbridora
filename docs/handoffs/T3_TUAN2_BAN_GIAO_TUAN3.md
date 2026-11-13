# Bàn giao T3 — kết quả tuần 2 và hướng dẫn tuần 3

**Cập nhật T1-06 ngày 10/11/2026:** bản DEV trên nhánh bàn giao `feature/t1-06-private-files` có private upload PDF cho TaskAssignment IN_PROGRESS, owner read và FileReadPort tối thiểu; chưa deploy. File mới PENDING; scanner, ZIP và WeeklyReport authorizer còn thiếu. URL mode vẫn làm độc lập được; xem [boundary tích hợp mới](../guides/T1_06_PRIVATE_FILES.md). Các ghi nhận thiếu runtime bên dưới phản ánh snapshot tuần 2.

Ngày lập: 09/11/2026. Người nhận: Team Thực tập, DEV05/DEV06/DEV07 và QA03. Mục tiêu: kế thừa workspace/assignment/task đã chạy, bổ sung Submission/Review/Comment/Weekly Report và UI tương ứng.

## 1. Baseline và nguồn phải đọc

- Bản phát hành tuần 2 trên `main` chứa code, tests và tài liệu bàn giao này; base `6687a940cd2f42a6351fb6c3511286c763c81f6b`, nhánh triển khai `feature/week2-implementation`. Nhận đúng commit tuần 2 bằng fetch/pull trên checkout sạch của team và kiểm Git log.
- Worktree tạo bản bàn giao: `D:/HKDN-TTTV-UTH/.work/week2-20261109/repo`. Kiểm các lớp ở mục 3 và migrations đến V27 trước khi làm tiếp; tiếp tục trong cùng project T3 hiện có.
- Fingerprint source chuẩn hóa LF: `77e7dda5c8e9e5d6f035609e4f2ac06b8ac6d8f7ca557c9c47e5ca9a5f932e63`, không phải commit Git.
- [Plan](../plans/UTBridora_Plan.xlsx): CAP NHAT CHUNG, tuần 2 dòng 29–31; tuần 3 dòng 45–47, QA03 dòng 55.
- [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx): FR-05/06/09–19, R21/R22, §25.8.1, §25.8.3 G18.
- [Contract v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md): CP-AUTHZ, CP-MENTOR1, CP-WS1/2/3, BC41, CP-FILE1, registry D/E. [Quyết định chốt](../contracts/README.md) đã thay việc chờ ký liên team trong ghi nhận lịch sử; không xin chốt lại nội dung đã có.
- [Kết quả tuần 2](../testing/WEEK2_ACCEPTANCE.md), [evidence](../testing/evidence/WEEK2_DEV_VERIFICATION.json), [runtime](../guides/WEEK2_RUNTIME.md) là nguồn trạng thái mới hơn các mô tả shell/pending cũ.

Evidence giữ nguyên trạng thái tại thời điểm kiểm chứng trước commit (`uncommitted=true`, CI chưa chạy khi ghi nhận); xem Git log và GitHub Actions của commit nhận được để biết trạng thái phát hành/CI mới nhất.

## 2. Tuần 2 đã có gì và chưa có gì

| Task/owner | Đã triển khai | Kiểm chứng |
|---|---|---|
| T3-04 — DEV05/QA03 | Coordinator/HR/Mentor assignment theo scope và parent; grant/revoke/time/CAS; T1 invite Mentor có owner acceptance; multi-group; handover sau move | Week2WorkflowIT kiểm invite/accept/revoke, move→deny→handover→allow→move lại→deny; revoke parent làm mất quyền/session. MentorOnboardingIT dùng MySQL + Mailpit riêng, token T1 thật |
| T3-05 — DEV06/QA03 | Task create/read/list, deadline/priority, assignment NEW; student start NEW hoặc REVISION_REQUIRED→IN_PROGRESS; calendar theo scope | InternshipRuntimeIT và Week2WorkflowIT kiểm current Placement/Case, student owner, không tự COMPLETED, lịch thật |
| T3-06 — DEV07/QA03 | HR/Mentor workspace, chọn assignment/cohort/group; board năm trạng thái, filter/detail/create task và lịch tháng; student own tasks | Chromium thật: Mentor giao việc, student đăng nhập thấy và bắt đầu làm; lịch có task tương ứng |
| Nền có từ tuần 1, giữ lại | T5 producer/outbox/read adapter; T3 PlacementInbox consumer, Case WAITING_START/IN_PROGRESS, group/move/history | Duplicate/stale/out-of-order, rollback và source cancellation đã có regression |

**Chưa có runtime Submission, Review, Comment hoặc Weekly Report.** Có field `currentSubmissionId` trong AssignmentDto không chứng minh đã có submission workflow. Case/Placement ACTIVE trong demo và đầu vào một số test là prerequisite synthetic, không chứng minh T5-07 đã hoàn thành.

Evidence chung: 485 unit PASS, 125 integration PASS theo report mới nhất từng class, 87 Playwright PASS + 4 skipped theo fixture, build và 868 contract checks PASS. Full run cuối có lỗi đồng bộ login/CSRF trong helper Week2; sửa test rồi recheck riêng thành công. Chưa có human QA/Lead signoff hoặc 5 team truy cập thực tế.

## 3. Bản đồ source và API đang chạy

BE tính từ `src/main/java/vn/utbridora/auth/`.

| Source | Trách nhiệm nên kế thừa |
|---|---|
| [WorkspaceService](../../src/main/java/vn/utbridora/auth/modules/internship/application/WorkspaceService.java), [WorkspacePorts](../../src/main/java/vn/utbridora/auth/modules/internship/application/WorkspacePorts.java) | Domain orchestration create/read task, unit-of-work và access seams |
| [WorkspaceRuntime](../../src/main/java/vn/utbridora/auth/modules/internship/infrastructure/WorkspaceRuntime.java), [JdbcWorkspaceSession](../../src/main/java/vn/utbridora/auth/modules/internship/infrastructure/JdbcWorkspaceSession.java) | Runtime/read pagination, cohort/group/move và persistence |
| [WorkspaceAuthority](../../src/main/java/vn/utbridora/auth/modules/internship/infrastructure/WorkspaceAuthority.java), [WorkspaceAssignmentService](../../src/main/java/vn/utbridora/auth/modules/internship/infrastructure/WorkspaceAssignmentService.java) | Current assignment, chain/revoke/expiry và handover |
| [CaseRuntime](../../src/main/java/vn/utbridora/auth/modules/internship/infrastructure/CaseRuntime.java), [InternshipActivityPolicy](../../src/main/java/vn/utbridora/auth/modules/internship/domain/InternshipActivityPolicy.java) | Guard dùng current T5 source; khóa Placement/Case đến commit |
| [WorkspaceTaskActions](../../src/main/java/vn/utbridora/auth/modules/internship/infrastructure/WorkspaceTaskActions.java), [TaskDtos](../../src/main/java/vn/utbridora/auth/modules/internship/api/TaskDtos.java) | Start assignment, calendar và DTO hiện có |
| [PlacementInbox](../../src/main/java/vn/utbridora/auth/modules/internship/infrastructure/PlacementInbox.java), [PlacementReadPort](../../src/main/java/vn/utbridora/auth/contracts/placement/PlacementReadPort.java) | Consumer thật và nguồn authoritative T5; interface comment cũ không thay implementation này |
| [TaskBoard](../../web-demo/src/modules/internship/pages/TaskBoard.tsx), [InternshipWorkspace](../../web-demo/src/modules/internship/pages/InternshipWorkspace.tsx), [live gateway](../../web-demo/src/modules/internship/api.ts) | UI thật; giữ selected context và chống response cũ ghi đè scope mới |
| [V21](../../src/main/resources/db/migration/base/V21__internship_runtime.sql), [V26](../../src/main/resources/db/migration/base/V26__mentor_onboarding_references.sql), [V27](../../src/main/resources/db/migration/base/V27__explicit_mentor_handovers.sql) | Tables task/assignment/group/Case, mentor provenance và handover |

Các route đã chạy:

| Method/path | Guard/cách dùng |
|---|---|
| GET `/api/v1/t3/context-assignments/me` | Chọn assignment hợp lệ; không lấy membership đầu tiên tùy tiện |
| GET `/api/v1/t3/cohorts`, `/cohorts/{id}/groups`, `/mentors/me/groups` | HR/Mentor gửi X-Context-Assignment-Id hiện hành |
| GET `/api/v1/t3/cases/{id}` | CaseRef theo owner/scope; không có tên/MSSV mặc định |
| GET/POST `/api/v1/t3/tasks`; GET `/api/v1/t3/tasks/{id}` | POST chỉ Mentor đủ quyền của mọi case được giao |
| PATCH `/api/v1/t3/assignments/{id}/status` | Student owner, expectedVersion, state=IN_PROGRESS |
| GET `/api/v1/t3/calendar?from=...&until=...` | Khoảng [from,until), tối đa 62 ngày, phân trang |
| POST `/api/v1/t3/cases/{id}/mentor-handover` | HR đúng scope; body caseId khớp path; không sửa artifact cũ |

Bốn role core vẫn là ADMIN, SCHOOL_STAFF, ENTERPRISE, STUDENT. HR/Mentor là scoped assignment trong ENTERPRISE. Token/mật khẩu/onboarding thuộc T1; không tạo kho credential T3 thứ hai.

## 4. Trình tự tuần 3 và dependency thực tế

1. **DEV05/T3-07:** Submission URL → immutable version → review → comment; cung cấp DTO/API cho DEV07/T2.
2. **DEV06/T3-08:** Report root/revision/draft/submit/review và tính tuần/hạn.
3. **DEV07/T3-09:** Mở rộng TaskBoard/workspace hiện có, nối UI student/Mentor với API mới.
4. **QA03:** kiểm từng lát cắt rồi kết hợp luồng Placement thật từ T5.

Plan T3-07 ghi phụ thuộc T5-06; để nghiệm thu từ đầu đến cuối còn cần **T5-07 tạo APPROVED rồi ACTIVE**. Có thể phát triển/test T3 trên database riêng với prerequisite ghi rõ, nhưng không gọi fixture ACTIVE là workflow Placement đã nghiệm thu. Case hoạt động có enum `IN_PROGRESS`; `ACTIVE` là trạng thái Placement.

### 4.1. T3-07 — Submission URL/version/review

Các route sau là **target CP-WS2 cần bổ sung**, không phải API đã có:

| Method/path | Input/output chính |
|---|---|
| POST `/api/v1/t3/assignments/{assignmentId}/submissions` | SubmissionInput →201 SubmissionDto |
| GET cùng path | Page<SubmissionDto>, versionNo DESC,submissionId ASC |
| POST `/api/v1/t3/submissions/{submissionId}/reviews` | ReviewInput →201 ReviewDto |
| GET/POST `/api/v1/t3/assignments/{assignmentId}/comments` | Page<CommentDto> hoặc CommentInput→201 CommentDto |

Các bước triển khai:

1. Thêm schema submission + artifact refs, review và comment trong một migration mới được cấp số toàn repo. UNIQUE(assignmentId,versionNo); assignment trỏ current submission. Không tạo task/assignment table trùng V21.
2. Owner từ principal; xác minh account/membership/assignment current và re-read Placement trước mutation. Case IN_PROGRESS, Placement ACTIVE, effectiveAt đã tới; local Case/event cũ không đủ chứng minh được ghi.
3. Nộp chỉ từ IN_PROGRESS với expectedAssignmentVersion. Khóa assignment, tính versionNo nguyên tử, insert artifact version mới và chuyển assignment SUBMITTED trong cùng transaction.
4. Giữ nguyên URL/file list/note của v1 khi có v2. Không update nội dung submission cũ, không tái dùng row mới nhất để hiển thị lịch sử.
5. Review chỉ Mentor đúng assignment/handover, không mặc nhiên HR; assignment phải SUBMITTED và submissionId phải là bản current. Review v1 khi current=v2 trả 409 STALE_SUBMISSION.
6. ACCEPT chuyển COMPLETED; REQUEST_REVISION chuyển REVISION_REQUIRED, comment bắt buộc. Student dùng endpoint start hiện có để về IN_PROGRESS rồi nộp v2.
7. Comment append-only, lưu actor/time từ server; không lộ assignment của student khác; không log text/note/URL vào audit metadata.
8. Tính lateFlag ở server: now=dueAt đúng hạn; now>dueAt thì lateFlag=true nếu lateAllowed, ngược lại 422 SUBMISSION_LATE. Dùng Clock kiểm tra biên, không phụ thuộc sleep của test.

Ví dụ URL target hợp lệ, thay version bằng giá trị vừa GET:

```json
{"expectedAssignmentVersion":2,"fileIds":[],"url":"https://example.test/demo/submission-v1","note":"Bản nộp URL"}
```

URL phải là HTTPSUrl của C.0, không userinfo; **backend không fetch URL**. Chọn đúng một dạng URL hoặc 1–20 file IDs distinct. Rỗng cả hai hoặc có cả hai trả SUBMISSION_ARTIFACT_INVALID. Dùng URL để hoàn thiện tuần 3 khi T1 file chưa sẵn; file branch phải fail-closed, không tự gán CLEAN.

### 4.2. T3-08 — Weekly Report và lịch sử

| Target cần triển khai theo CP-WS3 | Hành vi |
|---|---|
| POST `/api/v1/t3/reports` | ReportInput →201 root DRAFT |
| PATCH `/api/v1/t3/reports/{id}` | UpdateReport current DRAFT, expectedVersion |
| POST `/api/v1/t3/reports/{id}/submit` | SubmitReport → SUBMITTED |
| POST `/api/v1/t3/reports/{id}/reviews` | Mentor review current SUBMITTED → REVIEWED |
| POST `/api/v1/t3/reports/{id}/revisions` | reason/version →201 DRAFT revision tiếp theo |
| GET `/api/v1/t3/reports/{id}?revisionNo=...`; GET `/api/v1/t3/reports?caseId=...` | Đọc current hoặc revision cụ thể và list có scope |

1. UNIQUE(caseId,weekStart); reportId là root ổn định, revisionNo/version có nghĩa riêng. Giữ một current draft; SUBMITTED/REVIEWED bất biến, sửa bằng revision mới kèm reason.
2. weekStart là thứ Hai theo Asia/Ho_Chi_Minh. Tuần giao ít nhất một ngày với [Placement.startDate,Placement.endDate] đều hợp lệ, kể cả tuần đầu/cuối không trọn; không suy khoảng thực tập từ tên term.
3. dueAt là thứ Ba 23:59:59 của tuần kế tiếp, chuyển UTC lưu DATETIME. DRAFT chưa nộp lateFlag=false; submit tại dueAt đúng hạn, sau đó vẫn nhận và đánh dấu trễ.
4. Tạo root đầu tiên evidenceFileIds=[] để có resource thật. Upload sau với resourceType=WEEKLY_REPORT, resourceId=reportId, purpose=WEEKLY_REPORT; PATCH draft gắn refs. DRAFT được giữ PENDING; submit chỉ khi toàn bộ file CLEAN/bind đúng.
5. Tạo revision mới clone nội dung/refs, giữ v1 đã review; comment của Mentor gắn đúng revision, không sửa lại review cũ.

### 4.3. T3-09 — UI dùng API thật

- Kế thừa board/detail/calendar và navigation; thêm form URL/note, lịch sử v1/v2, người review/verdict/comment và trạng thái đang xử lý.
- Student chỉ thấy assignments bản thân; Mentor chỉ thấy scope được chọn. Đổi assignment/cohort/group hoặc session revoke phải hủy/loại response cũ và clear dữ liệu không còn quyền.
- Màn report chọn case/tuần đúng scope; đọc revision cũ qua API, không dựng history từ local state. Nút edit chỉ cho current draft hoặc tạo revision theo state.
- 409 giữ nội dung nhập và đề nghị tải current version; lỗi/timeout giữ key + payload để retry. Reload phải thấy đúng kết quả đã lưu.
- UI không PATCH SUBMITTED/COMPLETED để thay cho submit/review. Không làm màn xem điểm chưa công bố hoặc đóng Case tuần sau chỉ để kết thúc demo.

## 5. Các điểm integration cần xử lý khi bắt đầu

| Điểm hiện tại | Việc cần làm |
|---|---|
| Start endpoint hiện trả 409 STATE_CONFLICT cho state không được phép, CP-WS2 quy định 422 INVALID_ASSIGNMENT_TRANSITION | Đối chiếu input enum và error mapping, đồng bộ implementation/test/UI có chủ ý; không đoán từ nhãn nút |
| WorkflowValues.body có nullable allowlist hẹp | SubmissionInput cho phép url/note=null; DTO report cũng có quy tắc riêng. Viết parser/validation đúng từng DTO, giữ reject unknown/coercion, không mở object tùy ý |
| WorkflowAuditRegistry chỉ triển khai subset tuần 2 | Thêm đúng action/resource/metadata cần cho SUBMISSION_CREATED/REVISED/REVIEWED, TASK_COMMENTED và WEEKLY_REPORT_* theo E; dùng AuditService hiện có |
| Mentor chuyển nhóm | Tái sử dụng current assignment + handover guard khi đọc/review task cũ; group mới không tự đọc mọi artifact lịch sử |
| File API T1 chưa có runtime đầy đủ | URL mode vẫn làm được; file branch cần T1 requireCleanBound và authorizer T3, không đọc storage path trực tiếp |
| Transport api<T> hiện gán Content-Type JSON cho body | Không đưa FormData vào helper JSON rồi gọi đó là upload; dùng transport multipart được T1 tích hợp với session/CSRF đúng |

Domain/history/audit/OperationLedger phải cùng transaction. Thứ tự lock thống nhất với CaseRuntime và T5 để giảm deadlock; lỗi tranh chấp giữ hướng retry cùng key. Các action DENIED phải có cách ghi không làm mở quyền hoặc giả success.

## 6. Tích hợp T5/T1/T2

- T5 sở hữu Placement; dùng PlacementReadPort/PlacementLifecycleConsumer hiện có, không tạo public REST callback nhận event từ browser và không update bảng placements tại T3.
- T5 APPROVED tạo WAITING_START; chỉ ACTIVE/effectiveAt hợp lệ mới IN_PROGRESS. Activated tới trước Approved, event lặp/stale hoặc source CANCELLED phải dùng quy tắc BC41 có sẵn.
- Submission/Report mới phải gọi lại current source tại mutation, kể cả worker chưa chuyển local Case sau cancel. Đừng tin cờ ACTIVE từ browser hoặc event cũ.
- T1 sở hữu private file storage/scan, identity, session và Mentor account token. T3 cung cấp authorizer resource TASK_ASSIGNMENT/WEEKLY_REPORT, chỉ giữ file IDs.
- T2 gọi các API T3 từ UI sinh viên, không có submission/report canonical tại T2. Thống nhất DTO/error bằng consumer test, không thêm endpoint alias ngoài contract chỉ để nối giao diện.
- Migrations mới sau V27 được phối hợp T1 về số GLOBAL, không sửa V1–V27, không dành V28 đồng thời cho mọi team.

## 7. QA03 — ca nghiệm thu tối thiểu

| Nhóm ca | Expected |
|---|---|
| Student A nộp v1; Mentor REQUEST_REVISION; A start, nộp v2 | v1 còn nguyên, v2 current; review v1 bị STALE_SUBMISSION |
| 20 submit cùng key; 20 request cạnh tranh version | Một effect cho replay; versionNo không trùng, không ghi đè/audit-success thừa |
| Sai student, Mentor sai nhóm, HR review, revoked/expired parent | Bị chặn, không lộ artifact/response private |
| Move→chưa handover→handover→move tiếp | Quyền lịch sử đúng tại mỗi bước; không mutate artifact cũ |
| Placement APPROVED tương lai; ACTIVE; CANCELLED chưa nhận event | Chỉ ACTIVE/effectiveAt tới mới ghi; source CANCELLED chặn ngay |
| now=dueAt và now>dueAt, lateAllowed true/false | Flag/error đúng; không tính ở client |
| URL HTTP/userinfo, cả URL+file, file khác binding/PENDING | Từ chối toàn bộ, không có submission/refs/audit-success mồ côi |
| Report tuần đầu/cuối không trọn; thứ Hai sai; duplicate root | Khoảng tuần/hạn/unique đúng, lỗi REPORT_WEEK_INVALID hoặc REPORT_ALREADY_EXISTS |
| Report v1 REVIEWED→revision v2, đọc lại v1 | Root ID ổn định, v1/comment cũ bất biến |
| Lỗi audit hoặc crash/retry | Rollback transaction; retry không nhân đôi effect |
| Browser student nộp URL, Mentor review, reload hai tài khoản | Dữ liệu backend thật, scope đúng và lịch sử tồn tại sau reload |

Test mới đề xuất: `SubmissionIT`, `WeeklyReportIT`, `internship-submission-runtime.spec.ts` (chưa có trong snapshot). Kế thừa InternshipRuntimeIT/Week2WorkflowIT thay vì làm lại fixture/guard đã đủ.

```powershell
.\mvnw.cmd '-Dit.test=InternshipRuntimeIT,MentorOnboardingIT,Week2WorkflowIT' '-Dfrontend.e2e=true' verify
Push-Location web-demo
npm.cmd run build
Pop-Location
```

Sau khi thêm test mới, chạy chúng cùng hồi quy ảnh hưởng; full verify/contract checks lúc hội tụ. Testcontainers dùng DB/port riêng; Playwright dùng Vite 5178 nên chạy tuần tự. Các file browser runtime phải có fixture/backend từ harness Maven tương ứng.

## 8. Điều kiện bàn giao lại

Gửi branch/SHA/snapshot, migrations/DTO/route đổi, kết quả QA03 từng ca (PASS/FAIL/NOT RUN), log/report đúng source, fixture prerequisite và giới hạn file/Placement còn lại. Chưa có scan hoặc luồng Placement thật thì ghi đúng phần NOT RUN; human QA/Lead ký riêng.

Giữ stack chung utbridora-local:8088, password/database/volume; không seed/import/reset. Dev mới dùng [LOCAL_DEMO](../guides/LOCAL_DEMO.md) với DB riêng. Không in secret, không reset/clean repo gốc, không phục hồi f606b90; chỉ commit/push/merge khi được yêu cầu.

Phối hợp theo [T5](T5_TUAN2_BAN_GIAO_TUAN3.md), [T2](T2_TUAN2_BAN_GIAO_TUAN3.md), [T4](T4_TUAN2_BAN_GIAO_TUAN3.md).
