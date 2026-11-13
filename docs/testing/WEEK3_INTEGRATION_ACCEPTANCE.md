# Đối chiếu và kiểm chứng DEV tích hợp tuần 3

Ngày 13/11/2026. Base main `e22596ac9b40ecaf7b4896ec9199ccf583b6a36c`; code tích hợp được kiểm chứng tại `450e78d1fbd27a2da46462d3899239185cb7b946`. [Evidence của source và các lượt kiểm](evidence/WEEK3_INTEGRATION_VERIFICATION.json), [runtime/migration](../guides/WEEK3_INTEGRATION_RUNTIME.md). Không phải biên bản QA/Lead hoặc nghiệm thu SRS; không deploy 8088.

## Nguồn, thứ tự tích hợp và quan hệ với main

Fetch origin trước khi chọn nhánh; cả năm HEAD dưới đây chưa là ancestor của main ban đầu. Main đã có Core/T1 delegation UI, tuần 1/2 và tests được dùng lại. Tích hợp provider trước consumer, giữ lịch sử nguồn; không kiểm tra/merge/khôi phục nhánh tester/QA hoặc commit f606b90.

| Nguồn | SHA đã kiểm | Merge trên nhánh tích hợp |
|---|---|---|
| origin/feature/t1-06-private-files | `065c5be15f19df2fe8100c42248c5bd6d9c4a956` | `7f5e4bf` |
| origin/feature/t5-week3-implementation | `850a41c027f75660e4643b9c59d09141a8d0c51e` | `b72808b` |
| origin/feature/t2-week3 | `64af78d1c6c4e4546df5f77185d95b731cbc86f7` | `0498608` |
| origin/feature/t4-week3 | `9420f33becbea209e87b60164787db828630b27e` | `7d7ac22` |
| origin/feature/t3-week3 | `965678ae59094b6c9a58d75d8cd107d33d956a89` | `8468bab` |

Không xóa nhánh. Các fixes tích hợp nằm sau các merge trên cùng nhánh `integration/week3-20261113`; không cần push ngược fixes lên từng feature. Revert có chủ đích hành vi GPA của T4 commit `9420f33`: v1.0 reserve GPA/transcript chưa có source MVP; không tự duyệt Candidate. Giữ các commit nguồn trong ancestry và migration/data legacy nguyên vẹn. Chưa mở AI hoặc nghiệp vụ ngoài tuần 3/bàn giao tuần 4.

## Ma trận từng task

| Task / Plan / owner / QA | Acceptance trọng tâm | Implementation và provider/consumer | Trạng thái tại bản tích hợp | Evidence DEV và giới hạn |
|---|---|---|---|---|
| T1-06 /42 /DEV01 /QA01 | Private upload, owner/download deny; scanner còn thiếu phải ghi rõ | T1 FileService/Storage, ResourcePolicy, metadata/content/Range; T2/T3/T5 chỉ refs/consumer adapters | Có trong feature T1, đã merge; thêm bridge shared CV và current attachment guard | PrivateFileServiceIT, FileProxyIT, sharedCv test; CLEAN chỉ fixture, scanner T1-07 chưa triển khai |
| T2-07 /43 /DEV04 /QA02 | Opportunity/Preference; ACCEPT≠consent≠Placement | T2 Preference + history/ledger/audit; T5 OpportunityPort/publication/invitation; real adapter qua ActorContext | Đã ghép provider thật, bỏ production fallback; giữ history review/intent | StudentPreferenceIT fixture được ghi nhãn; EnterpriseProposalIT true provider/replay/history; workflow browser thật; chưa human QA |
| T2-08 /44 /DEV03 /QA02 | Student xem/phản hồi API thật, reload | OpportunitiesPage, PreferencesPage, S06 InvitationConsent tabs; App/transport chung | Backend/UI hiện có; giữ cảnh báo rút lời mời T5 khi ghép enhanced UI T2 | Core UI fixtures tách real week3-integration-runtime and student preference harness; mobile/scope/errors; không gọi mock là integration |
| T3-07 /45 /DEV05 /QA03 | URL v1/v2 không ghi đè; chỉ ACTIVE | SubmissionService/JdbcSubmissionSession, review/read; CaseRuntime current source T5; T1 file gate | Đã merge schema V29/runtime + GET reviews | T307SubmissionRuntimeIT, T307SchemaUpgradeIT, T309 browser; URL không fetch; chưa scanner/human QA |
| T3-08 /46 /DEV06 /QA03 | Report theo tuần/revision/history, Mentor đúng nhóm | TaskCommentService, WeeklyReportService/JDBC, V31; root trước upload, CLEAN trước submit | Đã merge domain/persistence/controllers/policies | T308CommentRuntimeIT, T308ReportRuntimeIT, T308SchemaUpgradeIT; audit/late+1ns/version/concurrency; scanner fixture only |
| T3-09 /47 /DEV07 /QA03 | Student URL→Mentor yêu cầu sửa, report API thật | SubmissionWorkspace, TaskBoard, WeeklyReports, multipart transport và workflow command | Đã nối API thật; không tạo Task/Report owner T2 thứ hai | T309WorkflowBrowserIT runtime student/mentor/HR/outsider; UI fixtures/evidence nhánh nguồn là snapshot riêng |
| T4-05 /48 /DEV08 /QA04 | k=5, frozen one-release, replay/409; JSON/CSV/chart no raw/PII | MatchingService + ProtectedAggregatePolicy, T2 frozen dataset, T5 authoritative criteria; ledger UNIQUE org/school/term/batch | Đã merge và sửa GET gate/current authority, UUID header, replay audit và denial audit | Bands HTTP0/1/4/5/9/10/19/20, privacy20 race/audit rollback, Week2 R03 tests; gate mặc định đóng; chưa QA04 privacy signoff/rollout |
| T4-06 /49 /DEV09 /QA04 | UI trường đổi skill/criteria →rerun đổi kết quả thật | MatchingWorkspace T2 snapshots/T5 criteria/capacity; nomination canonical T5; BC24 PII purpose/fields | Đã merge, không dùng fixed outcomes trong runtime; giữ strict-v1 | week3-integration-runtime browser sửa profile qua API T2 rồi rerun trường; Strict oracle; 1.000 fixture pairs không phải số SV/NFR400 |
| T5-06 /50 /DEV11 /QA05 | Whitelist/purpose/time; revoke chặn đọc tiếp | T5 share/policy/proposal decision; T1 ProfileAccessFacade; T2 consent/projection; T1 file bridge | Đã merge và sửa read-decision rollback khi thử các quyền, shared file/binding/current refs | EnterpriseProposalIT whitelist/regrant/revoke/expiry/scope/audit; current Range denial; không scanner thật/human QA |
| T5-07 /51 /DEV10 /QA05 | PENDING→school APPROVED→due ACTIVE, one active key, T3 source | PlacementService + publisher/outbox worker; T2 current eligibility/profile; T3 inbox/Case; new SYSTEM activation worker | Đã bổ sung current gates, demand/quota locks, exact-term auth trước replay, strict parser và SYSTEM history | 20 competing approvals, audit/outbox rollback, Clock boundary/manual+system, event duplicate/reverse/cancel; không normal closure/Completed shortcut |
| T5-09 /52 /DEV12 /QA05 | School/enterprise thao tác thật, state tồn tại sau reload | SchoolProposals/EnterpriseProposals/PlacementWorkspace, publication/share/consent, CaseEntry | Tái dùng UI hiện có, không giữ bản Placement UI trùng đã revert ở feature HEAD | week3-runtime browser + proposal/school UI fixtures; future APPROVED/cancel phân biệt ACTIVE prerequisite cũ; tuần4 còn search/polish/QA |

Các dòng QA tuần 3 (QA-01..05, Plan53..57) được chuẩn bị bằng tests/evidence DEV tương ứng, **không tự chuyển thành QA PASS**. Các cột Plan có thể còn “Chưa làm”; workbook không bị sửa để giả nghiệm thu. Trạng thái code dựa implementation và test hiện tại, không dựa tên commit “complete”.

## Các fixes có lý do tích hợp

1. **Flyway collision:** tách historical locations, giữ nguyên 38 SQL lịch sử và thêm Java V32 hội tụ/V33 SYSTEM history. Default main và từng T2/T4/T5 lineage được test riêng; không đổi history/repair/reset/out-of-order. GPA V30/V31 có location riêng chỉ để validate khi đã áp dụng; DB chưa có V31 không mới chạy backfill immutable JSON. Test đối chiếu current/revision/snapshot/hash T4 trước/sau. GPA non-null cần preflight/data review, không silently drop criterion.
2. **Provider thật:** nối Opportunity T2→T5; sửa current eligibility/profile/criteria tại Placement và khóa quota/reservations bằng current read thay vì RR snapshot. Không thêm một canonical owner hay route nominate khác.
3. **Scope/replay:** Placement require đúng school+term trước cả ledger replay; school list/summary không lấy quyền term khác. Enterprise decision kiểm share hiện hành trước replay, không trả dữ liệu cũ sau revoke.
4. **Private files:** consumer DTO bridge sang T1; shared/school đọc đúng current attachment/CLEAN/binding/consent. Read decision DENIED không làm transaction đọc thành rollback-only khi còn quyền hợp lệ khác; SQL/audit lỗi thực vẫn rollback.
5. **Privacy:** enterprise GET/export/chart cũng phải qua gate/current authority; same fingerprint không re-emit run completion; denial audit giữ DENIED và allowlist, lỗi ghi denial không biến thành release hoặc lộ input/count.
6. **UI/runtime:** kết hợp App/delegation/student tabs/T3/CaseEntry, giữ cảnh báo withdraw; body Placement closed/no coercion và error C.0. Demo privilege upgrade phải opt-in riêng; không tự đổi membership khi khởi động bản mới.
7. **T3 schema gate:** nhận đúng script ở locations đệ quy và migration hội tụ V32 của legacy; vẫn kiểm cột thực và Flyway validate, không bỏ dependency gate. Gate được kiểm lại trên cả 13 đường DB.
8. **GET đồng thời:** Cooperation list/detail dùng transaction chỉ đọc và không lấy khóa mutation; tránh vòng khóa Cooperation→delegation giữa list và Promise.all(detail) của Matching. Giữ khóa phê duyệt/Placement và kiểm quyền hiện hành; có test 20 reader cạnh tranh.

## Cách hiểu kết quả kiểm chứng

Kết quả tại code `450e78d1fbd27a2da46462d3899239185cb7b946`: [CI PASS](https://github.com/NguyenThanhTiep/utbridora/actions/runs/43352236892); **1.270 unit PASS, 223 integration PASS, browser 164 PASS + 16 SKIP theo context**, không có FAIL/ERROR. Các harness runtime thật T2/T3/T5 chạy riêng; SKIP trong harness khác không thay chúng. Contract **868/868 PASS**, audit registry3, portability1, oracle SRS11; frontend build/typecheck PASS. Migration13states PASS gồm bảo toàn immutable JSON/hash T4. Điều chỉnh chụp ảnh test sau code này chờ menu responsive đóng và chụp riêng panel Matching; runtime không đổi, CI của SHA main cuối được theo dõi riêng sau push.

Harness consent sau SHA trên còn được tăng độ chính xác: chờ POST grant201, xác định đúng consent vừa cấp thay vì nhãn active trên toàn trang, rồi kiểm đúng revoke URL/lineageVersion, HTTP200, revokedAt và trạng thái đã thu hồi. Guard HR bị chặn đọc và Placement vẫn APPROVED được giữ nguyên. Lượt main817b345 có một lỗi browser do nhãn consent cũ; report đỏ được lưu trong checkpoint, không gọi lượt đó PASS. CI đúng SHA main sau bản sửa được ghi ở checkpoint/kết quả cuối, không suy từ CI code450e78d.

Số liệu cuối trong evidence JSON được sinh từ JUnit/Playwright reports thực và source hash. Mỗi lượt đỏ được giữ trong checkpoint ngoài repo; không gọi lượt full có failure là BUILD SUCCESS, không cộng report bị overwrite thành hai lần PASS. Các suite targeted sau fixes chỉ thay class/scope đã chạy, có tên/lệnh/thời điểm rõ. CI phải là đúng SHA main sau push, không dùng CI e22596a cũ để chứng minh bản mới.

Contracts chạy builder `--check`, verifier, audit registry, portability và oracle S1–S11; backend chạy unit/IT và browser thật; frontend production build/typecheck. Migration có 13 trạng thái gồm clean và các history states, đều trên DB riêng. Các test dùng synthetic prerequisites có nhãn; scanner, human QA/Lead, workload400/5.000/concurrency50 và deploy8088 không được suy ra từ tests này.

Lượt full local đầu tích lũy container test, làm backend shared cũ bị OOM-kill (exit137). Đã dừng local IT, chỉ dọn container test của lượt đó và start lại đúng container/image backend cũ; bốn container shared đã healthy. Không rebuild/deploy source mới, seed/import, đổi account/password/membership hoặc xóa volume. Các lượt full sau dùng CI riêng. Chi tiết incident và các lượt đỏ trong evidence/checkpoint; không ghi shared runtime “không hề restart”.

## Bàn giao tuần 4

[T2](../handoffs/T2_TUAN3_BAN_GIAO_TUAN4.md), [T3](../handoffs/T3_TUAN3_BAN_GIAO_TUAN4.md), [T4](../handoffs/T4_TUAN3_BAN_GIAO_TUAN4.md), [T5](../handoffs/T5_TUAN3_BAN_GIAO_TUAN4.md). Mã task/DEV/QA/acceptance lấy nguyên từ Plan. Không yêu cầu làm lại các provider và code đã tích hợp; dependencies thực còn mở là scanner/evaluation/visibility và các kiểm thử/UX/NFR tuần 4 được nêu riêng.
