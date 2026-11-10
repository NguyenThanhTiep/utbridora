# Tuần 2 — phạm vi triển khai và kiểm chứng DEV

Đối chiếu Plan, sheet CAP NHAT CHUNG dòng 24–41 (13 DEV + 5 QA), SRS v1.2.4 và Contract Pack v1.0 đã chốt. Bản này bổ sung cho [tuần 1](WEEK1_ACCEPTANCE.md), không sửa trạng thái Tester/Lead trong workbook và không thay chữ ký nghiệm thu.

Baseline: `6687a940cd2f42a6351fb6c3511286c763c81f6b`. Nhánh triển khai: `feature/week2-implementation`; bản phát hành tuần 2 trên main đóng gói code, tests và [bàn giao T2–T5](../README.md#bàn-giao-tuần-2--tuần-3). Evidence ghi trạng thái tại lúc chạy trước commit, do đó giữ `uncommitted=true`; không dùng trường này để suy trạng thái Git hiện tại. Source ứng dụng không đổi sau lần kiểm chứng được ghi nhận. Docker dùng chung chưa triển khai lại.

## Ánh xạ task với implementation

| Task | Output thực tế | Phép kiểm chính |
|---|---|---|
| T1-04 | Lifecycle delegation theo school/term/permission/field/time, parent chain, CAS revoke và invalidation session của descendants; T3 assignment lifecycle | Week2WorkflowIT: child thật, đọc bằng phiên HTTP, revoke parent, phiên cũ 401 và phiên mới bị từ chối; InternshipRuntimeIT/CorePlatformIT hồi quy |
| T1-05 | ActorContext do server tạo; dùng AuditService và OperationLedger hiện có; action/metadata allowlist; quyền role là ceiling, không thay scoped grant | Unit/audit registry; HTTP malformed actor, CSRF, sai role và ADMIN không đọc profile; OperationLedgerIT kiểm transaction/rollback |
| T2-04 | CompletenessPolicy T4 với đúng sáu checks, T2 gọi provider thật; immutable SnapshotSet/ProfileSnapshot, hash/version, assertCurrentForDecision | StudentProfileIT; StrictClassifierTest; Week2WorkflowIT capture/nominate qua T2 thật |
| T2-05 | Editor canonical/custom skills, level/years/note, lưu toàn bộ profile và hiển thị lý do thiếu | week2-runtime.spec.ts: sửa kỹ năng, lưu, reload từ backend thật; student-runtime và UX suite hồi quy |
| T2-06 | Consent grant/revoke/regrant lineage theo student/school/term/recipient/purpose, fields/expiry/policy/version; current locked port | Week2WorkflowIT: explicit consent/share/read rồi revoke và read bị chặn; regrant không phục hồi share cũ; tranh chấp revoke/regrant; expiry/recipient |
| T3-04 | Confirm coordinator, HR/Mentor theo scope/parent/time; T1 Mentor invite/self-accept/token activation; revoke descendants; handover rõ ràng | Week2WorkflowIT: invite/accept/revoke, move/handover/move again; MentorOnboardingIT: SMTP Mailpit + T1 token thật, không tạo user trước accept, callback T3 và replay token bị chặn |
| T3-05 | Create/read/list task kế thừa; student start NEW/REVISION_REQUIRED → IN_PROGRESS; lịch có phân trang và scope; không mở submission/review trước tuần 3 | Week2WorkflowIT: owner start, calendar, không tự COMPLETED; InternshipRuntimeIT: gate Case/Placement và sai scope |
| T3-06 | Board năm trạng thái, filter/detail/create Task, lịch tháng và student own tasks | week2-runtime.spec.ts: Mentor giao việc thật, sinh viên đăng nhập thấy và bắt đầu làm; internship browser hồi quy |
| T4-03 | Strict classifier FAIL > UNKNOWN; required AND, declared empty, exact alias, level/years, union ngày inclusive | StrictClassifierTest thực thi oracle S1–S11 độc lập từ docs/testing/fixtures; Preferred 66.67 và ngày giáp nhau |
| T4-04 | Persisted MatchRun/results, immutable dataset/criteria refs, Preferred HALF_UP hai chữ số chỉ MATCH; tie theo studentUserId; school API | Week2WorkflowIT gọi DemandCriteriaPort T5 và snapshot T2 thật, persist/read/nominate; unit classifier/scoring |
| T5-04 | Cooperation create/atomic submit/approve/request-info/reject/resubmit/reopen/history; recheck verification; unique demand/cooperation và immutable submission | Week2WorkflowIT: atomic submit, replay, approve/stale; BusinessModulesIT kiểm verification/state/history hồi quy |
| T5-05 | Nominate từ run MATCH/current + schoolApproved authoritative, batch/CAS/quota/reservation; invitation principal student, expiry/version/history/withdraw | Week2WorkflowIT: false approval bị chặn, thật approval rồi nominate; 20 ACCEPT cạnh tranh, 20 ACCEPT/DECLINE cạnh tranh; expired và school nhận thay bị chặn; không auto consent/Placement |
| T5-08 | UI Verification/Demand/Cooperation nối API, scope/version, decision/request-info/reopen/resubmit; Demand dùng lại form đã có | week2-runtime.spec.ts: school duyệt Cooperation rồi reload; demand-runtime và backend Verification suite hồi quy |

## QA-01 đến QA-05

Đây là kiểm chứng DEV tự động tương ứng các dòng QA. Chưa có xác nhận của người phụ trách QA hoặc Lead.

| QA | Bằng chứng | Giới hạn |
|---|---|---|
| QA-01 | Core/ledger/audit/migration suite; HTTP tuần 2 thử unauthenticated, CSRF thiếu, role sai, student owner sai, field actor tự khai, ADMIN profile; revoke chain/session | Chưa phải pentest hoặc human QA |
| QA-02 | Profile/skills browser thật; consent share/read/revoke, lineage và expiry trong MySQL riêng | Không có download CV/evidence qua File Service tuần 3 |
| QA-03 | Gate Placement/Case và assignment từ suite nền; task browser thật; multi-group, handover và revoke mới | Placement ACTIVE ban đầu là prerequisite synthetic, không chứng minh workflow tạo Placement tuần 3 |
| QA-04 | Classifier production chạy trên oracle S1–S11, score/date/alias assertions; persisted run integration | Chưa mở enterprise aggregate/privacy release tuần 3 |
| QA-05 | Verification backend thật, Cooperation/nomination/invitation thật và browser school approve | Verification upload/scan là File Service tuần 3; chưa có browser từ đầu upload chứng cứ đến approve |

## Kết quả chạy

Kết quả ngày 09/11/2026 được ghi trong [evidence DEV](evidence/WEEK2_DEV_VERIFICATION.json), checkpoint `.work/week2-20261109/RESULT.md` và `verification-receipt.json` bên cạnh worktree.

| Bộ kiểm tra | Kết quả cuối |
|---|---|
| JUnit unit | 485/485 PASS |
| JUnit integration (báo cáo mới nhất từng class) | 125/125 PASS, 0 skipped |
| Playwright (UX/unit UI và các luồng backend thật) | 87 PASS, 4 skipped có điều kiện trong Core run; 0 FAIL |
| Frontend | Build production, typecheck ứng dụng và tests PASS |
| Contract | 868/868 PASS; portability 1 PASS; audit registry 3 PASS; oracle tĩnh 11 ca hợp lệ |

Lượt Maven toàn bộ chạy 125 integration, chỉ một failure ở Week2WorkflowIT: helper browser lấy CSRF trước khi login hoàn tất khi chuẩn bị hai lời mời UI. Đã sửa chờ login trong test, chạy lại riêng StrictClassifierTest + Week2WorkflowIT bằng Maven verify, kết quả **BUILD SUCCESS**. Application source không đổi giữa full run và lần kiểm tra lại; giữ bằng chứng các suite không bị ảnh hưởng. Evidence ghi riêng full run thất bại và targeted recheck thành công; tổng 125 phía trên là kết quả mới nhất theo từng class, không là một full run xanh duy nhất.

Core browser: 83 PASS + 4 skipped; bốn ca chỉ chạy ở các fixture chuyên biệt đã được chạy riêng: Demand, Internship, Student profile và Week2. Week2 browser xác minh skills save/reload, consent grant/revoke, Cooperation approve/reload, Mentor giao việc, Student bắt đầu và lịch, cùng ACCEPT và DECLINE rồi reload qua backend thật.

Một lượt full cũ hơn phát hiện mock Demand chưa khai báo hai panel mới, orphan Vite sau timeout và kỳ vọng InterimCompleteness cũ; đã sửa và giữ log ngoài repo. Bộ UX Demand/Internship riêng 35/35 PASS; các test Student cần fixture từ CorePlatformIT. Không coi lần chạy độc lập thiếu fixture là lỗi ứng dụng hoặc dùng mock để nghiệm thu workflow thật.

## Ranh giới dữ liệu và nghiệm thu

Các test runtime dùng Testcontainers MySQL riêng, port động; MentorOnboardingIT dùng Mailpit riêng. SMTP test không gửi email ra ngoài. Demo seed chỉ bật vào database test mới; không chạy importer/seed vào utbridora-local. Verification/school delegation/Placement nền được ghi rõ là synthetic prerequisites.

Migrations mới V22–V27; V1–V21 giữ nguyên. MigrationUpgradeIT kiểm upgrade và constraint. Giờ DATETIME của các luồng mới ghi UTC rõ ràng, tránh lệch timezone JVM Windows.

Phần kéo tối thiểu từ tuần 3 để đáp ứng tuần 2: student invitation UI và share/projection để kiểm consent khi đọc thật. ACCEPT, consent, share và Placement là bốn quyết định độc lập. Chưa triển khai toàn bộ opportunities/preferences, enterprise decision/Placement approval/activation, submission/review hoặc privacy gate tuần 3.

Verification UI giữ fail-closed: có thể tạo nháp và xét duyệt hồ sơ có chứng cứ liên kết hợp lệ; không tạo file CLEAN, không giả upload, không bỏ gate evidence. File upload/scan/download vẫn thuộc T1-06 và phụ thuộc tuần 3. Mentor activation dùng T1 SMTP sau commit; durable mail outbox/retry B06 chưa được tuyên bố hoàn tất.

Chưa triển khai lên cổng 8088, chưa có 5 team truy cập thực tế hoặc human QA/Lead signoff. CI tại lúc ghi evidence là NOT_RUN; xem [GitHub Actions](https://github.com/NguyenThanhTiep/utbridora/actions/workflows/verify.yml) của đúng commit để biết kết quả sau push. Không phục hồi hay tích hợp commit QA f606b90.
