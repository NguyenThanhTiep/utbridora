# Kiểm định lại các khắc phục SRS UT.Bridora v1.2.2

Ngày kiểm định: 03/11/2026 (Asia/Bangkok). Phạm vi: G01–G34/R01–R34 của báo cáo v1.1 và FR-SYS-IMPORT-01 mới. Chỉ audit, không lập trình, không sửa SRS/source/migration/database, không chạy build/test hoặc triển khai.

## 1. Tổng kết và nguồn kiểm chứng

**Kết luận: NO GO cho việc giao Codex triển khai trọn năm team và tích hợp MVP ngay từ bản này.** Có thể tiếp tục các phần độc lập đã rõ yêu cầu theo §7. Hai P0 còn PARTIALLY FIXED; luồng import chưa đủ contract và các quyết định xử lý tài khoản/retry/resend để triển khai hoàn chỉnh.

| Chỉ số | Kết quả |
|---|---|
| Vấn đề gốc đã kiểm tra | 34/34; mỗi G ánh xạ R cùng số, không đếm việc nhắc mã là đã sửa |
| FIXED | 12: G02,G04,G05,G11,G13,G16,G17,G21,G22,G23,G24,G34 |
| PARTIALLY FIXED | 21: G01,G03,G06–G10,G12,G14–G15,G18–G20,G25–G32 |
| NOT FIXED | 1: G33, finding source cũ còn nguyên; phương án SRS đã rõ |
| CONFLICT | 0 có đủ căn cứ sau khi áp dụng quy tắc ưu tiên |
| UNVERIFIED | 0 đối với kết luận khắc phục từng mã; runtime/benchmark/test mới đều chưa xác minh |
| Ba P0 | G01 PARTIALLY FIXED; G02 FIXED ở mức đặc tả; G03 PARTIALLY FIXED |
| Import sinh viên | GO cho wireframe; NO GO cho backend/database hoàn chỉnh và nghiệm thu toàn luồng |

FIXED chỉ nghĩa là quyết định SRS cho vấn đề gốc đã đủ rõ; không đồng nghĩa code tồn tại hoặc test PASS. G33 là ngoại lệ được giữ đúng loại finding ban đầu: NOT FIXED ở source, đồng thời SPEC FIXED – NOT IMPLEMENTED. Không suy từ số FIXED thành tỷ lệ hoàn thành dự án. G29/G31 vẫn mở vì artefact RTM/recipe chưa đủ, không chỉ vì test chưa chạy.

### 1.1. Tài liệu và revision thực tế

- SRS chính thực sự tồn tại ở C:/Users/Admin/Downloads/UTBridora_SRS_Tong_The_Hop_Nhat_Day_Du_v1.2.2.docx; mã UTB-SRS-MASTER-001, nội dung ghi phiên bản v1.2.2, ngày 02/11/2026. SHA256: 5B1CA19456202F177D6A46F260386EB890C1C9C2C0A356B3B9B0AEE24B5C68DE.
- Báo cáo gốc: [UTBridora_SRS_v1.1_Implementation_Readiness_Review.md](UTBridora_SRS_v1.1_Implementation_Readiness_Review.md), đã đọc danh sách G và R thực tế. SHA256: 76C76ECEFF3C0A0BB01510313670B7A292A734D724F4EA69D4090FDD128AB06A.
- File v1.1 đối chiếu có thật: D:/HKDN-TTTV-UTH/UTBridora_SRS_Tong_The_v1.1.docx. Không tìm thấy tên chính xác UTBridora_SRS_Tong_The_CHOT_Demo_MVP_v1.1.docx trong workspace; không dựng nội dung thay thế. Re-audit dựa báo cáo cũ và v1.2.2, không thực hiện lại audit năm SRS nguồn.
- Project: D:/HKDN-TTTV-UTH/UTBridora_SourceCode/utbridora-t1-full-foundation. Local HEAD và remote main được kiểm tra đọc bằng git ls-remote, cùng commit **3c725e3dc8ed1886a84cff2d64e75b746c75766c** của [NguyenThanhTiep/utbridora](https://github.com/NguyenThanhTiep/utbridora). Kết luận source giới hạn ở snapshot này.

Đã trích xuất DOCX trong bộ nhớ theo thứ tự body, đọc các đoạn/bảng liên quan, phụ lục và header/footer; mở trực tiếp cả ba hình 20.1, 21.1, 21.2. Không có nested table, math object, textbox hoặc tracked insertion/deletion trong document.xml cần diễn giải riêng. Chưa render toàn bộ bố cục trang nên không xác nhận pagination/clipping; không dùng số trang làm bằng chứng chất lượng và không có hình liên quan nào không đọc được.

**Quy ước bằng chứng:** P001…P449 là đoạn trực tiếp trong body, tính cả đoạn trống; T01…T89 là bảng trực tiếp theo thứ tự toàn DOCX; R1 là hàng tiêu đề. Chương/mục đi kèm giúp tìm trong Word. Đây là locator kiểm định, không phải mã sẵn có trong SRS. Đường dẫn Java rút gọn ở dưới nằm trong src/main/java/vn/utbridora/auth/.

**Quy tắc ưu tiên đã áp dụng:** P105 và P279 cho các bổ sung Rxx/Gxx quyền ưu tiên so với nguồn cũ, kể cả Ch19–25. Vì vậy k=3, operational ACTIVE hoặc Placement APPROVED→IN_PROGRESS trong hình 21.2 không tự tạo CONFLICT còn hiệu lực nếu R mới thay thế rõ. Việc chưa gắn SUPERSEDED tại đoạn cũ vẫn là G32. Không hỏi lại các lựa chọn demo đã chốt và không yêu cầu phê duyệt production để bắt đầu code synthetic demo.

## 2. Bảng đối chiếu đủ 34 vấn đề

Cột cuối ghi điều đã được bổ sung và phần thực chất còn thiếu. API/port chung được gom tại G27; không hạ các policy đã rõ chỉ vì source/module tương ứng chưa có.

| ID và vấn đề gốc | Mức độ cũ | Trạng thái mới | Vị trí SRS v1.2.2 / source | Bổ sung và nội dung còn thiếu |
|---|---|---|---|---|
| G01/R01 — Placement và handoff | P0 | PARTIALLY FIXED | §9.8 T35/R2,R6; §10.5 T39/R7; §22.2 T72/R24 | Đã chốt active-set APPROVED/ACTIVE, WAITING_START và guard ACTIVE/effectiveAt. Chưa có actor/trigger/guard APPROVED→ACTIVE và mapping kết quả đóng case sang Placement. |
| G02/R02 — Student–school–term | P0 | FIXED | §9.8 T35/R3; §19.5 P304; §6.8 T18/R2,R6; §3.3 P077 | T5 sở hữu TermReference; T2 sở hữu StudentTermEligibility, UNIQUE(student,term), schoolApproved/version/approvedByAt; T5 đọc port tại đề xuất và duyệt, sai scope/chưa duyệt→422. MSSV string theo trường, termCode map canonical. Contract chi tiết chung còn G27. |
| G03/R03 — Privacy release và differencing | P0 | PARTIALLY FIXED | §8.7 T28/R2; §24.2 P398–405 | Đã chốt k=5, một release/enterprise+school+term, ledger xuyên actor/demand. Chưa có oracle ô 0, secondary suppression, equality/query hash, đợt/reset release và xử lý duy nhất “409 hoặc reuse”. |
| G04/R04 — Criteria VALID và vòng chờ matching | P1 | FIXED | §8.7 T28/R3; §7.4 T22/R4 | VALID là validation của criteria, độc lập Cooperation; Demand DRAFT/READY hợp lệ được match; đổi criteria/profile phải revalidate/rerun. Đã gỡ vòng chờ gốc. |
| G05/R05 — Operational org và school verification | P1 | FIXED | §6.7 T17/R2; P105,P279 | Giữ T1 PENDING/VERIFIED/SUSPENDED; T5 verification độc lập DRAFT/SUBMITTED/NEEDS_CHANGES/APPROVED/REJECTED, giữ ID khi yêu cầu sửa và lineage attempt. R mới ưu tiên dòng ACTIVE cũ; không cần đổi enum T1. |
| G06/R06 — Permission Registry và grant mapping | P1 | PARTIALLY FIXED | §6.7 T17/R3; §20.4 T68; §6.8 P108 | Đã giữ legacy và deny quyền lạ, nhưng mới yêu cầu “công bố registry”; chưa có mapping exact, grantor/actor/scope/action cho các quyền mới, gồm schoolRosterImport:manage. |
| G07/R07 — HR/Mentor và school delegation | P1 | PARTIALLY FIXED | §6.7 T17/R4; §20.3 T67; §20.4 T68 | Đã có owner assignment T3, scope/version/expiry/revoke và kiểm từng request. Chưa chốt ai cấp HR đầu tiên, ai được ủy quyền school/PII, owner record và port quyết định. |
| G08/R08 — Invitation TTL, decision và withdrawal | P1 | PARTIALLY FIXED | §9.8 T35/R4; §22.2 T72/R14–15; CT-04 T73/R5 | Đã có 7 ngày/max30, now≥expiresAt, terminal decision và race20. Chưa có from/to WITHDRAW và hiệu lực expiry sau ACCEPTED; API/DTO hiện chỉ ACCEPT/DECLINE. |
| G09/R09 — Reservation và capacity | P1 | PARTIALLY FIXED | §9.8 T35/R5; §21.3 P347; §13.2 T42/R30 | Đã có 5 trạng thái giữ chỗ, chuyển PLACED không cộng kép, release một lần và công thức. Chưa có reservationUntil trước/sau mời; “placed” chưa định nghĩa tập APPROVED/ACTIVE/COMPLETED/CANCELLED. |
| G10/R10 — Typed profile/criteria | P1 | PARTIALLY FIXED | §7.4 T22/R2; CT-01/02 T73/R2–3 | Đã rõ declaration/empty, level1–5, years≥0, lịch inclusive phải bao phủ kỳ và FAIL thắng UNKNOWN. Chưa có typed criterion/operator/nullability; CT-01 còn skillCodes nhưng quy tắc cần level/years. |
| G11/R11 — Preferred scoring | P1 | FIXED | §8.7 T28/R4 | MATCH-only; 100×count/total Preferred, weight=1, HALF_UP2; noPreferred0, UNKNOWN0, nonMATCHnull; tie studentUserId; version/snapshot và ví dụ66,67 đã đủ oracle. |
| G12/R12 — Completeness và catalog | P1 | PARTIALLY FIXED | §7.4 T22/R3; §7.1 T20/R6–7,R10; §7.2 T21/R4,R18 | Sáu predicate đã liệt kê, tách schoolApproved/MATCH/CV, T2 major/T4 skill owner rõ. Owner danh mục môn/định hướng của US-T2-03/17 Must vẫn OPEN, chưa được R12 thay thế. |
| G13/R13 — Profile edit và immutable snapshot | P1 | FIXED | §7.4 T22/R4 | expectedProfileVersion, snapshot/hash bất biến; liệt kê field ảnh hưởng, sửa phải rerun/revalidate trước nomination/approval; stale409; không khóa toàn profile vô hạn. |
| G14/R14 — Opportunity và Preference | P1 | PARTIALLY FIXED | §7.4 T22/R5; §22.2 T72/R11–13 | Đã chốt Opportunity là projection Cooperation APPROVED+school publish, Preference T2 có SUBMITTED/WITHDRAWN/REVIEWED. Chưa rõ ID/publication version và review/withdraw transitions, ai review, rút sau REVIEWED. |
| G15/R15 — Private file transport/scan/binding | P1 | PARTIALLY FIXED | §6.7 T17/R5; §20.6 P330–331; §22 T72–73 | Đã chốt multipart/stream, allowlist10/20/5MiB và fail-closed scan. Chưa có API/DTO bind/resource authorizer, numeric ZIP expansion limits, timeout/retry/terminal scan policy và PENDING↔PENDING_SCAN mapping. |
| G16/R16 — Revoke và direct file URL | P1 | FIXED | §6.7 T17/R6 | MVP PII chỉ qua T1/gateway, kiểm quyền mỗi GET/Range; không direct storage URL; revoke chặn request mới sau commit, không giả thu hồi bytes đã tải. |
| G17/R17 — Consent lineage và typed access policy | P1 | FIXED | §7.4 T22/R6; §20.5 P322–328; §23.4 P387 | Đã có recipient/school/term/purpose/fields, version lineage, regrant vô hiệu cũ, giao schoolPolicy∩consent∩share, race serialize theo version/revoke trước read mới. Typed wire schema còn G27; boolean port cũ là compatibility gap. |
| G18/R18 — Outbox/inbox và lifecycle events | P1 | PARTIALLY FIXED | §9.8 T35/R6; §22.5 P372–376 | Đã có 4 event/version/envelope, atomic inbox+case+audit, authoritative reread và duplicate/out-of-order tests. Chưa có event→case transition, retry bound/backoff/DLQ/reconcile/reprocess oracle. |
| G19/R19 — Idempotency TTL/retry/effect lineage | P1 | PARTIALLY FIXED | §6.7 T17/R7; §22.1 P356 | Đã tách cache24h khỏi durable effect, scope+hash+transaction và replay kiểm quyền. Chưa có IN_PROGRESS response, rollback retry, cache expired response, durable retention/repeated reissue-regrant operation identity. |
| G20/R20 — Scoped mentor onboarding | P1 | PARTIALLY FIXED | §6.7 T17/R8; §10.1 FR-05; §22 T72–73 | Đã yêu cầu MentorInvitationPort, HR scoped, new/existing email, không cấp ADMIN. Chưa có DTO/state/accept grant cho existing ACTIVE/PENDING_ACTIVATION user, revoke/expiry/error và authority. |
| G21/R21 — Task/submission transitions | P1 | FIXED | §10.5 T39/R2; P182–185; §10.1 T36/R16–17 | Actor/from/to đã cụ thể; chỉ SubmissionService commit bản nộp mới chuyển SUBMITTED; current-version409; nộp muộn mặc định cho phép, lateAllowed=false quá dueAt422. |
| G22/R22 — Group move và mentor history | P1 | FIXED | §10.5 T39/R3 | Một active group/case/cohort, move versioned/atomic/audit, task-group snapshot, giữ history; mentor mới cần handover đích danh do HR duyệt; revoke request kế. |
| G23/R23 — Rubric math và revision | P1 | FIXED | §10.5 T39/R4 | weight>0,sum100,score0..5, Total100=HALF_UPΣ(weight×score/5),2; missing criterion reject; template/bản công bố immutable; revision do trường duyệt, ví dụ76/100. |
| G24/R24 — School result visibility policy | P1 | FIXED | §10.5 T39/R5; P182; §10.3 T37/R12–13 | PUBLISHED∧school+term policy∧HR visibility mỗi GET; school defaultfalse và delegated editor; HR không sửa điểm; truth-table AC. Endpoint/DTO quản lý policy còn trong G27, không còn thiếu business predicate. |
| G25/R25 — Weekly report boundary/late | P1 | PARTIALLY FIXED | §10.5 T39/R6; §10.1 T36/R19–20 | Đã chốt Monday Asia/Ho_Chi_Minh, UNIQUE(case,weekStart), revision/history. Vẫn thiếu nguồn deadline/dueAt để tính lateFlag và tuần đầu/cuối không trọn kỳ. |
| G26/R26 — Close case và Placement outcome | P1 | PARTIALLY FIXED | §10.5 T39/R7; §19.5 P306; §9.8 T35/R2,R6 | Đã có school delegate, reason/decisionRef/version và normal prerequisites. Chưa chốt case terminal state/write guards, CaseClosed DTO và T5 NORMAL/EARLY→Placement outcome. |
| G27/R27 — Typed contracts/error/alias | P1 | PARTIALLY FIXED | §6.7 T17/R9; §13.4 P223; §22.2–4 T72–73,P359,P368 | Đã giữ core default25 và chọn errorCode/message/requestId/fieldErrors. Mới giao nhiệm vụ xuất OpenAPI/port, chưa có complete schema/enum/nullability/error/alias; CT06 yêu cầu traceId nhưng mẫu thiếu; import chưa có endpoint/port. |
| G28/R28 — Audit action registry | P1 | PARTIALLY FIXED | §6.7 T17/R10; §23.3 T76; source AuditAction/AuditService | Đã giữ LOGIN+SUCCESS/FAILURE và transaction; chưa có registry actual/mapping MEMBERSHIP_CHANGED,GRADE_* với enum core và event mới. |
| G29/R29 — RTM từng Must và evidence | P2 | PARTIALLY FIXED | §25.6 T87/R2; §17 T56–57; §25.1 T80 | Đã quy định RTM granular và trạng thái evidence; các bảng actual vẫn group-level, chưa có các hàng Must→guard/API/schema/test/oracle/evidence như G29 yêu cầu. |
| G30/R30 — Retention/purge/FK/hold/backup | P2 | PARTIALLY FIXED | §25.6 T87/R3; §19.4 P301–302 | Giữ30/90, tombstone/FK/storage/backup và clock AC. Chưa có thứ tự purge graph, hold authority/expiry/release và retention của backup/exports để chạy procedure thống nhất. |
| G31/R31 — Benchmark recipe | P2 | PARTIALLY FIXED | §25.6 T87/R4; §24.3 T77/R3–5 | Có manifest/cold-warm/raw errors, target định lượng giữ nguyên. Chưa chốt endpoint set, runs tổng/per-worker, HTTP vs engine boundary và phương pháp percentile. |
| G32/R32 — Legacy superseded/ID/route | P2 | PARTIALLY FIXED | §25.6 T87/R5; P012,P135,P139,P147,P437; §14.1 T44 | Đã có precedence P105/P279 và tách demo/production. “SUPERSEDED” chỉ xuất hiện trong lệnh yêu cầu gắn nhãn; AC09.1 vẫn trùng, legacy k3/câu số/route cắt vẫn giữ. |
| G33/R33 — N+1 source trang quản trị | P2 | NOT FIXED | §25.6 T87/R6; src/main/java/vn/utbridora/auth/api/AdminReadController.java:40–41 | SPEC FIXED – NOT IMPLEMENTED: SRS đã chốt batch userIds và≤1 membership lookup/page; source vẫn query từng user. NOT FIXED áp dụng finding source gốc, không coi đây là lỗi đặc tả mới. |
| G34/R34 — Module boundary/dependency rules | P3 | FIXED | §25.6 T87/R7; §3.2 P070; §20.2 T66 | Đã cấm import internal repo/entity/ghi bảng owner khác; public API/DTO/port, composition root, dependency+context smoke gate được quy định. Tên package/allowlist/test implementation là deliverable kỹ thuật, chưa triển khai/chưa chạy. |

## 3. Những finding gốc vẫn cần xử lý

Chỉ gồm PARTIALLY FIXED và NOT FIXED. Bảng dưới ghi vị trí, ảnh hưởng, sửa cụ thể và owner; nội dung chưa giải quyết ở cùng mã trong §2. P0/P1 cần đóng trước logic/giao điểm tương ứng; các P2 về evidence/benchmark/retention và source N+1 không chặn mọi công việc lập trình độc lập.

| ID và vị trí | Ảnh hưởng tới triển khai/kiểm định | Sửa cụ thể cần bổ sung | Team chịu trách nhiệm |
|---|---|---|---|
| G01/R01; §9.8 T35/R2,R6; §10.5 T39/R7; §22.2 T72/R24 | Workspace có thể không bao giờ mở hoặc đóng case nhưng Placement vẫn giữ active-key. | T5/T3 thêm bảng from→command/event→actor→guard→to→outbox/case; chọn rõ activation và xử lý NORMAL_COMPLETION/EARLY_TERMINATION. | T5, T3 |
| G03/R03; §8.7 T28/R2; §24.2 P398–405 | Hai implementation có thể trả các JSON/count khác nhau; chưa có oracle để xác minh chặn suy diễn. | Chốt release key/batch/reset authority, same-hash replay vs changed-hash409, mask schema và fixture 0/1/4/5, total/%/CSV/chart, budget/window nếu dùng. | T4, T5, T1/security |
| G06/R06; §6.7 T17/R3; §20.4 T68; §6.8 P108 | Tên SRS có thể bị deny hoặc alias gộp thừa quyền khi tích hợp. | Bàn giao bảng canonical↔legacy và grant rules cho từng endpoint/capability; không ánh xạ account:admin:manage thành toàn quyền. | T1 + owner T2–T5 |
| G07/R07; §6.7 T17/R4; §20.3 T67; §20.4 T68 | Dễ tự cấp quyền hoặc không có actor hợp lệ để bootstrap quản trị nghiệp vụ. | Chốt ma trận grant/revoke/initial assignment và school delegation, authoritative capability DTO, validity/revoke errors. | T1, T3, T5 |
| G08/R08; §9.8 T35/R4; §22.2 T72/R14–15; CT-04 T73/R5 | T2/T5 có thể hủy decision lịch sử hoặc giữ reservation khác nhau. | Tách decision terminal và current validity; chốt WITHDRAW from/to, bounds, reason/version/API và expiry/accepted/reservation cases. | T5, T2 |
| G09/R09; §9.8 T35/R5; §21.3 P347; §13.2 T42/R30 | Item chưa mời giữ chỗ vô hạn; completed/cancelled có thể làm sai chỉ tiêu. | Chốt deadline từng trạng thái, countedPlacementStatuses và cancellation/completion effects; test hai cán bộ và RESERVED chưa issue. | T5, T4, T2 |
| G10/R10; §7.4 T22/R2; CT-01/02 T73/R2–3 | Engine/consumer vẫn phải chọn presence hay minLevel/minYears và shape dữ liệu. | Chốt DTO skill/period/Required/Preferred và operator, đơn vị, evidence applicability; kèm JSON valid/invalid. | T2, T4, T5 |
| G12/R12; §7.4 T22/R3; §7.1 T20/R6–7,R10; §7.2 T21/R4,R18 | T2 chưa biết nguồn hợp lệ của lựa chọn môn/định hướng để lưu và validate. | Chỉ định owner/port/version/fixture catalog demo, hoặc quyết định minh thị loại phần tương ứng khỏi Must MVP. | T2, T4, T5/Leader |
| G14/R14; §7.4 T22/R5; §22.2 T72/R11–13 | Frontend/backend có thể xử lý nguyện vọng đã xét khác nhau. | Chốt opportunityId/source mapping, publish/close và Preference transition/actor/expectedVersion/DTO; không kéo theo Placement. | T2, T5 |
| G15/R15; §6.7 T17/R5; §20.6 P330–331; §22 T72–73 | Các module không có contract upload/bind/read chung; scanner/ZIP failure khó có oracle thống nhất. | Công bố file endpoints+typed metadata/binding, authorizer owner; chốt archive limits, scan timeout/retry/retention/error và legacy mapping. | T1 + T2/T3/T5 |
| G18/R18; §9.8 T35/R6; §22.5 P372–376 | Có thể retry/ACK hoặc mở/đóng case khác nhau sau cùng lỗi hoặc event tới trễ. | Chốt transition/ACK-ignore-retry-DLQ table và cấu hình hữu hạn, quyền reprocess, authoritative version checks; gắn R01/R26. | T5, T3, T1 |
| G19/R19; §6.7 T17/R7; §22.1 P356 | Client có thể không biết retry thế nào; cùng key sau24h có hai behavior đều được hiểu hợp lệ. | Thêm bảng request/state→HTTP/result; chốt expired-cache, transient rollback, operationId/expectedVersion và hiệu lực lineage qua purge. | T1 + mutation owners |
| G20/R20; §6.7 T17/R8; §10.1 FR-05; §22 T72–73 | Không thể nối port mới với invite hiện chỉ ADMIN/new email mà không tự chọn chính sách account/membership. | Chốt existing/new account matrix và two-phase accept→assignment, scope/version/token/revoke/idempotency errors; tái sử dụng identity/token primitives. | T1, T3 |
| G25/R25; §10.5 T39/R6; §10.1 T36/R19–20 | Không có expected result khách quan cho báo cáo muộn hoặc số tuần cần nộp khi đóng case. | Chốt report policy deadline/server-time, partial-week applicability, lateAllowed/submit/revision rules; thêm date-boundary fixtures. | T3, T2, trường/delegate |
| G26/R26; §10.5 T39/R7; §19.5 P306; §9.8 T35/R2,R6 | Case đóng mà Placement/active-key không thống nhất; thao tác sau đóng chưa có oracle. | Bổ sung close transition/result envelope và T5 consumer mapping; chỉ rõ quyền sửa/bổ sung sau close, không tự công nhận tín chỉ. | T3, T5 |
| G27/R27; §6.7 T17/R9; §13.4 P223; §22.2–4 T72–73,P359,P368 | Giao Codex toàn module vẫn phải tự chọn giao diện và compatibility. | Phát hành contract pack actual có version/hash, endpoint/port/schema/examples/errors/permissions/idempotency và legacy mapping; bổ sung import/visibility/withdraw/activation/close. | T1 điều phối, T2–T5 owner |
| G28/R28; §6.7 T17/R10; §23.3 T76; source AuditAction/AuditService | AuditService.valueOf có thể reject code SRS và rollback mutation. | Chốt exact action/result/resourceType/allowlist mapping, thêm code mới bằng thay đổi có review; không rename ngầm history. | T1 + T2–T5 |
| G29/R29; §25.6 T87/R2; §17 T56–57; §25.1 T80 | Không thể chứng minh coverage/trace tất cả Must; không được lấy yêu cầu “QA xuất” làm RTM đã xong. | Xuất RTM actual từng Must; phần chưa code/chưa chạy ghi NOT_RUN/UNVERIFIED và link phiên bản contract. Đây là gate nghiệm thu, không chặn mọi code độc lập. | QA Lead + T1–T5 |
| G30/R30; §25.6 T87/R3; §19.4 P301–302 | Purge có thể chặn FK hoặc giữ bản sao dữ liệu quá hạn; chưa có procedure nghiệm thu. | Bổ sung purge/anonymize graph và bảng hold/backup policy; dry-run fake Clock/DB+storage fixture. Không yêu cầu đổi mốc demo đã chốt. | T1/Ops + owners |
| G31/R31; §25.6 T87/R4; §24.3 T77/R3–5 | Hai lần benchmark có thể không cùng mẫu/measurement boundary; chưa thể so p95. | Chốt recipe/sample count/endpoint set/percentile và raw format; chỉ PASS khi thực thi. Không chặn code nghiệp vụ chỉ vì benchmark chưa chạy. | QA, T4, T1/Ops |
| G32/R32; §25.6 T87/R5; P012,P135,P139,P147,P437; §14.1 T44 | Search hoặc trace riêng từng đoạn dễ chọn oracle sai; không phải conflict k5 còn hiệu lực. | Thực sự gắn link canonical/unique AC và sửa route/câu cắt từ nguồn kiểm chứng; label dữ liệu TEST_ADMIN là capability fixture, không role thứ5. | BA/Leader T1–T5 |
| G33/R33; §25.6 T87/R6; src/main/java/vn/utbridora/auth/api/AdminReadController.java:40–41 | Pattern N query/page còn nguyên; chưa đo nên không kết luận vi phạm NFR hay độ chậm thực tế. | Khi được giao implementation riêng: batch lookup + SQL-count/paging regression; audit này không sửa code. Không chặn code các team khác. | T1, QA |

## 4. Kết luận riêng ba P0

### P0-01 — Placement và bàn giao T5→T3: PARTIALLY FIXED

T35/R2 đã giải quyết phần trọng tâm active-set: APPROVED và ACTIVE cùng giữ khóa student+term; COMPLETED/CANCELLED giải phóng khóa nhưng không xóa history. Owner T5; school authority và quyền t5:placement:approve có tại P173/T68/R15. T3 chỉ tạo WAITING_START từ APPROVED hợp lệ; Task/Submission bị chặn đến khi ACTIVE và effectiveAt≤serverNow. AC20 approve cạnh tranh và unique/transaction đã được yêu cầu, **chưa chạy**.

Còn chặn: APPROVED→ACTIVE chưa có trigger/actor/command/guard; NORMAL_COMPLETION/EARLY_TERMINATION của T39/R7 chưa có mapping Placement/case terminal và tác động active-key. T35/R6 đã thêm authoritative reread, atomic inbox và bốn event, nên không còn “chỉ dedup eventId”; tuy nhiên thiếu bảng event→case và retry/DLQ/reconcile như G18. Không đủ để hai team tự code cùng lifecycle.

**Điều kiện đóng:** T5/T3 xuất transition matrix kèm activation, future effectiveAt, cancellation, CaseClosed outcome và duplicate/out-of-order/fault cases. Việc chọn activation tự động hay school command là quyết định còn thiếu, báo cáo không tự phê duyệt một phương án.

### P0-02 — Sinh viên, trường và kỳ: FIXED trong SRS

T35/R3 xác định T5 TermReference, T2 StudentTermEligibility và nguồn schoolApproved; T5 kiểm tra authoritative ở cả đề xuất/duyệt, lỗi scope/term/chưa duyệt 422. P304 tách học vụ khỏi completeness và kết quả T4; T18/P107–111 quy định import/activation không tự phê duyệt học vụ.

Các khóa đã tách được về mặt nghiệp vụ: MSSV=studentCode dạng string, unique theo school; studentUserId=users.id identity T1; profile/registry ID nghiệp vụ không được thay cho users.id; schoolOrgId=organizations.id; termId từ T5, termCode chỉ để tra mapping. T1/T2 không tự sinh kỳ khác. Đã đủ giải quyết sự thiếu nguồn authoritative của G02; schema port/version/validation chi tiết còn phải xuất trong G27. **SPEC FIXED – NOT IMPLEMENTED; chưa xác minh DDL/port runtime hoặc test PASS.**

### P0-03 — Privacy Matching T4: PARTIALLY FIXED

T28/R2 tiến bộ thực chất: k=5; protected-release ledger theo enterprise+school+term, tối đa một release/đợt; không đổi actor/demand để né; kiểm cả JSON/CSV/chart, không trả student IDs. K=3 lịch sử không còn là lựa chọn song song.

Còn chặn: P399 chưa chọn behavior cho count 0; “ẩn bổ trợ khi cần” chưa có mask/schema/fixture quyết định; chưa định nghĩa canonical equality của query/snapshot, “gần giống”, reset/đợt release và quyền tạo đợt mới. Câu “409 hoặc tái sử dụng” chưa phân biệt trường hợp nào áp dụng; P401 không có window/budget cụ thể. Ví dụ 0/1/4/5 và 10/10/10→9/10/11 đã được yêu cầu thử, nhưng chưa có expected payload/response duy nhất để QA làm oracle.

**Điều kiện đóng:** xác định release fingerprint/batch/reset authority, same-hash replay và changed-hash behavior; đưa fixtures 0/1/4/5 cùng tổng/%/secondary/export và repeated-query expected responses vào SRS/contract. Có thể chọn MVP bảo thủ ẩn toàn bộ counters/tổng/% khi có nhóm 1–4, nhưng đó là đề xuất mới để owner quyết định, chưa là baseline. Theo chính T28/R2, **chưa qua test thì aggregate enterprise phải tắt**; tắt tạm không chứng minh Must aggregate của MVP đã hoàn thành và không tự cho toàn MVP GO.

## 5. Chức năng mới FR-SYS-IMPORT-01

### 5.1. Ba mức sẵn sàng riêng biệt

| Mức | Kết luận | Phạm vi |
|---|---|---|
| Thiết kế giao diện | **GO cho wireframe/prototype** | P108–109 đủ luồng upload→preview/errors→confirm→progress/result→lọc activation/resend. Chưa GO frontend tích hợp thật khi request/row status/error chưa chốt. |
| Backend và database hoàn chỉnh | **NO GO** | Chưa đóng I01–I06 dưới đây. Có thể làm parser/validation độc lập theo mẫu cột, khung job và tận dụng token primitives; không tự chọn account-link/resume/resend policy. |
| Kiểm thử nghiệm thu toàn luồng | **NO GO** | Có 10 IMP-AC tốt, nhưng còn thiếu oracle cho tài khoản cũ, duplicate, thay đổi sau preview, crash/resume, hai job và resend–activate. Toàn bộ test import mới **NOT RUN**. |

### 5.2. Đối chiếu 15 điểm bắt buộc

| Điểm kiểm tra | Kết quả đặc tả | Bằng chứng/phần còn lại |
|---|---|---|
| 1. Mẫu cột, loại và giới hạn file | Có nền, còn thiếu | §6.8 P108,T18: .xlsx/.csv≤5MiB/5.000 dòng, required fields rõ; parser/template/within-file duplicate và row errors còn I03. |
| 2. MSSV string/giữ số0 | Đã chốt lưu trữ | T18/R2,P110: string UTF-8,1–32,không parse số,unique theo school. Quy tắc ô Excel đã bị chuyển thành số cần I03; không hứa khôi phục số0 đã mất trong nguồn. |
| 3. Trùng MSSV theo trường và email | Một phần | T18/R2,R4,P110,T19/R9 có unique/no-auto-merge; precedence duplicate cùng file và matrix existing account còn I02/I03. |
| 4. Tài khoản đã tồn tại | Chưa đủ | P110,IMP-AC-08 chỉ safe idempotence/review; chưa đủ ma trận trạng thái/link quyền/I02. |
| 5. Không ghi đè password/dữ liệu tự khai | Đã chốt nguyên tắc | P110,T19/R4 cấm reset password/profile/consent khi trùng; phải giữ nguyên khi implement. |
| 6. Valid/failed/import lại | Một phần | P109 có job state và row status field; chưa có row enum/transition/summary và resume oracle, I01/I05. |
| 7. Retry1.000–5.000 không trùng | Một phần, chưa thử | P108 row-atomic/outbox và key,IMP-AC-01–03; key khác/hai job/crash một phần chưa đủ I05, không tuyên bố đã xử lý tải5.000. |
| 8. Email bulk retry/trạng thái/lỗi | Một phần | P109 QUEUED/SENT/FAILED và IMP-AC-07 mất SMTP; số lần/throttling/backoff/recovery chưa chốt I06. |
| 9. Token48h/một lần/resend | Một phần | P108,P110,IMP-AC-05–06 chốt48h/hash/one-use và now≥expiresAt; mốc TTL/backlog/resend invalidation/race còn I06. |
| 10. Sinh viên tự đặt mật khẩu | Đã chốt | P108,P110,IMP-AC-06: không password chung/Excel/email; giữ PasswordPolicy backend hiện hữu. |
| 11. Demo synthetic/Mailpit | Đã chốt | P111,IMP-AC-10,P301: không gửi mail thật/không Gmail tự sinh. Cổng Mailpit phụ thuộc compose profile (§6.2). |
| 12. Chuyển SMTP qua cấu hình | Đủ nguyên tắc và có config | P110; application.yml:24–36 có host/port/auth/TLS. APP_PUBLIC_URL khác tên SRS; queue bulk và kiểm thử SMTP mới chưa có. Production vẫn qua gate đã chốt. |
| 13. Cán bộ đúng school scope | Policy có, grant còn thiếu | P108,T18/R7,IMP-AC-04: suy school từ phiên,không tin file. Registry/grant của schoolRosterImport:manage và principal nhiều membership cần G06/G07/I01. |
| 14. API/data/state/AC | Chưa đủ | P108–109,T19 có flow/job states/10AC; không có actual import endpoints/request/response/ports/row enum/schema tại §22/23, I01. |
| 15. Phân công T1/T2/T5 | Owner cấp cao rõ | P107: T5 UI+TermReference; T1 job/identity/invite/mail; T2 registry/profile/eligibility. Write/identity-link port và transaction boundary cần I01/I02/I05. |

### 5.3. Sáu khoảng trống mới còn chặn import hoàn chỉnh

Các I01–I06 là finding của chức năng mới, **không cộng vào 34 finding cũ**. Đây đều là phần đặc tả cần bổ sung; source chưa có import được ghi riêng tại §6.2.

| ID | Vị trí | Phần còn thiếu | Ảnh hưởng và sửa cụ thể | Owner |
|---|---|---|---|---|
| I01 — Contract/permission | §6.8 P107–109; §22 T72–73; §23 T75 | Chưa có endpoint/request/response/rowStatus/error enum, selected-row/result schema hay T1↔T2 provisioning port; schoolRosterImport:manage chưa có grant matrix. | Không thể làm client/consumer/database contract chung; cần contract pack actual, job/row/mail transition table, logical keys/index và scoped grant. | T1 điều phối; T2/T5 |
| I02 — Existing identity/link | P110; T18/R2,R4; IMP-AC-08 T19/R9 | Chưa quyết định email ACTIVE/PENDING_ACTIVATION/SUSPENDED/DEACTIVATED, cùng email khác MSSV/trường/kỳ, bằng chứng và ai được link. Safe NEEDS_REVIEW có nhưng chưa có resolution/authority. | Nguy cơ sai membership/identity hoặc vô tình đổi trạng thái account; cần decision matrix new/existing/conflict, no overwrite/no auto-reactivate, approval/link proof và result codes. | T1 identity; T2 registry; T5 school delegate |
| I03 — File/duplicate oracle | T18/R2–6; IMP-AC-02/08 T19/R3,R9 | Chưa chọn all-duplicate-error hay first-valid-wins trong cùng file; case/normalization của MSSV, duplicate email sau canonicalization T1 và Excel numeric cell/zero; catalog/term version dùng validate chưa chỉ rõ. Email đã được T18/R4 dẫn chiếu chính sách T1 (source trim+lowercase), không phải thiếu toàn bộ quy tắc email. | Kết quả số dòng hợp lệ tùy parser/thứ tự; cần mẫuCSV/XLSX thực tế, text-cell rule, duplicate precedence, row error fixtures và catalog mapping/version. | T1 parser; T2 major/roster; T5 term |
| I04 — Preview/confirm TOCTOU | P108; IMP-AC-02 T19/R3; P109 | Confirm dùng importJobId+version nhưng snapshot/hash/expiry/selected rows chưa có. AC nói “Không ghi DB ở preview”, trong khi job/version cần lưu hoặc cơ chế khác chưa được mô tả. | Preview có thể lỗi thời/tamper; chốt cơ chế preview server-authoritative, cho phép lưu job/validation nhưng không user/token/domain writes, hash/TTL và recheck quyền/catalog/uniqueness tại confirm. | T1,T2,T5 |
| I05 — Partial commit/resume/concurrency | P108–109; IMP-AC-01–03/07 T19/R2–4,R8; R19 T17/R7 | Row-atomic nhưng chưa có durable per-row operation key/checkpoint, resume PROCESSING/FAILED, two jobs/different key, job đang chạy trả gì và totals/no-op counts. | Crash/retry có thể lặp token/email/profile hoặc bỏ sót dòng; chốt unique effect lineage, checkpoint/recovery/revalidation và deterministic job/row outcomes kể cả partial success. | T1 job/outbox; T2 port; QA |
| I06 — Mail/token resend | P109–110; IMP-AC-05–07 T19/R6–8 | QUEUED/SENT/FAILED và chữ RETRY chưa thành state machine; chưa có bounds/backoff/rate-window,48h tính từ issuance/send nào, resend invalidates old token và race activate, behavior ACTIVE account. | Email backlog có thể chứa token hết hạn; resend có nhiều link hoặc đổi sai account; chốt mail/token transitions, expiry anchor, atomic replacement, current account/version checks và retry/rate-limit/error AC. | T1 mail/token; T5 UI; QA |

Đề nghị bổ sung AC cho cùng identity đã ACTIVE/PENDING_ACTIVATION/khóa, duplicate cùng file, quyền/canonical catalog đổi sau preview, hai job trùng sinh viên khác key, crash sau commit một phần, email backlog quá 48h, resend đồng thời activate và revoke token cũ. Mỗi AC cần expected row/job/mail state và HTTP/code cụ thể; không chỉ “không tạo trùng”. Chưa đưa các đề xuất này thành yêu cầu đã được phê duyệt.

## 6. Tính nhất quán tại các giao điểm và source T1

### 6.1. Tám giao điểm liên quan trực tiếp đến phần sửa

Ownership cấp cao đã rõ hơn; chưa thấy hai owner canonical invitation/consent/Placement cùng được phép mutate sau khi áp dụng P279. Rủi ro còn lại chủ yếu nằm ở authority, schema và state transition; không tự kết luận đã có lộ dữ liệu khi chưa chạy runtime.

| Giao điểm | Owner duy nhất / consumer | Kết quả và phần còn chặn | Bằng chứng |
|---|---|---|---|
| T1↔T2 | T1 identity/token/file/auth; T2 profile/consent/roster/eligibility | R17 policy đã rõ; Boolean ProfileVisibilityPort cần typed adapter. Import provisioning/link authority/transaction còn I01/I02/I05, filebinding G15; giữ userId T1, không copy credential. | T17/R5–8;T22/R6;P107–110;G06/G07/G15/G17/G19/G27 |
| T1↔T3 | T3 HR/Mentor assignment; T1 membership/capability guard/onboarding/audit | Chưa bootstrap/grant matrix và existing-user mentor accept; audit names chưa map exact. Không biến ENTERPRISE thường thành HR/Mentor. | T17/R3–4,R8,R10;G06/G07/G20/G28 |
| T1↔T5 | T1 operational VERIFIED/import job; T5 school verification/UI/term | R05 đã tách operational/org-school. schoolRosterImport:manage và selectedschool scope vẫn cần grant/port, không tin file schoolOrgId; SMTP env sai tên cần mapping. | T17/R2–4;P107–110;T35/R3;I01–I06 |
| T2↔T4 | T2 snapshot/catalog ngành; T4 taxonomy/completeness policy/matching | Formula và snapshot-version đã chốt; typed skillCodes vslevel/years/criteria operators chưa đủ; môn/địnhhướng owner vẫn OPEN. | T22/R2–4;T28/R4;T20/T21;G10/G12/G27 |
| T2↔T5 | T5 canonical invitation/share/Placement; T2 consent/Preference/eligibility | Consent lineage và eligibility policy sửa đủ; withdrawal/current validity,Opportunity/Preference transitions và quota deadline còn thiếu; không dùng activation/ACCEPT thay schoolApproved/consent. | T22/R5–6;T35/R2–5;G08/G09/G14/G27 |
| T4↔T5 | T5 criteria/demand/proposal; T4 immutable run/protected statistics | Đã gỡ VALID↔APPROVED vòng chờ; còn typedcriteria/release oracle/counted quota. Enterprise không được nhận ID/details từrun. | T28/R2–4;T35/R5;T73/R2–4;G03/G09/G10/G27 |
| T5↔T3 | T5 Placement/outbox; T3 case/inbox/CaseClosed | Active-set và atomic dedup rõ; activation/closing outcome/per-event case transitions/retry chưa đủ. Không giao T3 tự tạo hoặc mutate Placement. | T35/R2,R6;T39/R7;G01/G18/G26/G27 |
| T3↔T2 | T3 task/submission/report/evaluation API; T2 student UI | Task/group/rubric và grade-visibility predicate đã rõ. Weekly dueAt/partial week,typed API và school policy management port cần đóng. Chỉ self/assignment đọc dữ liệu. | T39/R2–7;T72/R25–30;G25/G27 |

Các bản kế hoạch V1_T4… vẫn là nhãn nguồn bị §23.1/T74 thay thế bởi một dãy Flyway global. Checkout thực tế chỉ có V1–V7, không phát hiện migration mới/trùng version trong snapshot. Chưa kiểm tra flyway_schema_history của DB đang chạy: **UNVERIFIED**; không đủ căn cứ nói migration thực tế đã PASS.

### 6.2. Compatibility với implementation thực tế

“Chưa có implementation” của yêu cầu mới tự nó không phải lỗi SRS. Bảng phân biệt phần đã chốt đặc tả với phần cần adapter/implementation; không đề nghị viết lại T1.

| Điểm đối chiếu | Phân loại | Source thực tế | Thay đổi tương thích cần có khi triển khai |
|---|---|---|---|
| Permission/capability | CONTRACT / CODE COMPATIBILITY GAP | authorization/PermissionCatalog.java:13–22 và V3__permissions_and_membership_integrity.sql:26–30,55–57 dùng exact code; core có audit:global:read,account:admin:status/account:admin:revoke,membership:admin:write. | T17/R3 chưa phải mapping actual; T68 có code khác. Cần registry/alias/grant được review cho capability mới, không cấp ADMIN cho staff/HR. |
| Organization | SPEC FIXED; implementation cũ phù hợp R05 | organization/Organization.java:9–14 có PENDING/VERIFIED/SUSPENDED. | Giữ enum và guards; T5 thêm school verification riêng theo spec. Không migrate VERIFIED thành ACTIVE chỉ để khớp đoạn legacy. |
| Roster/account activation | SPEC mới; NOT IMPLEMENTED + COMPATIBILITY GAP | tokens/AccountTokenService.java:25–28 invite globalADMIN/newemail;:39–45 TTL48h/hash nhưng gửi email inline;:49–57 tạo user/membership khi accept, từ chối email đã tồn tại. | Roster mới tạo/link trước activation không thể gọi nguyên service hiện tại: cần scoped provisioning/activation-existing-user contract, không bỏ guard. Token hash/48h/one-use là primitives đã có, bulk outbox/resend/row jobs chưa có. |
| Consent/file | SPEC FIXED G16/G17 – NOT IMPLEMENTED; G15 contract còn thiếu | authorization/ProfileVisibilityPort.java:7–10 boolean; files/FileMetadata.java:7–11 nêu chỉmetadata,enum PENDING/CLEAN/REJECTED; không thấy upload/download/scanner implementation. | Thêm typed access adapter và file service thật sau khi contract chốt, giữ fallback deny. Cần mapping PENDING/PENDING_SCAN; không giả có bytes/storage/AV. |
| Audit | CONTRACT / CODE COMPATIBILITY GAP | audit/AuditAction.java:4–12 dùng LOGIN+result,MEMBERSHIP_GRANTED/REVOKED,EVALUATION_*; audit/AuditService.java:31 gọi AuditAction.valueOf(action). | T17/R10 giữ legacy nhưng T76/newevents chưa có mapping đầy đủ; không gửi tên chưa đăng ký trực tiếp. |
| API/error | CONTRACT / CODE COMPATIBILITY GAP | api/ApiErrorHandler.java:11–35 trả error/message; api/AdminReadController.java:30,54–56 default25,max100,page≤10000; docs/openapi.json hiện là core. | T17/R9 giữ default25 đúng nhưng target envelope/errorCode/requestId/fieldErrors,route alias/import/ports vẫn cần thực hiện sau contract sign-off; không đổi contract core ngầm. |
| N+1 | SPEC FIXED – NOT IMPLEMENTED; G33 NOT FIXED source | api/AdminReadController.java:40–41 gọi memberships.findByUserIdAndActiveTrue trong map mỗi user. | Chưa có batch hoặc benchmark mới. Không kết luận latency/NFR FAIL; SQL-count target T87/R6 là việc implementation riêng. |
| Flyway/framework | Không thấy xung đột trong checkout; DB runtime UNVERIFIED | src/main/resources/db/migration chỉV1–V7; pom.xml:7,11 Boot4.1.2/Java21; application.yml:13 validate; compose.local.yaml:4 mysql:8.4. | Chưa có migration/businessmodules/import mới; cấpV-next qua registry sau khi owner review, không sửaV1–V7. |
| Mail/config | Một phần đã cấu hình; bulk NOT IMPLEMENTED | application.yml:24–36 hỗ trợ host/port/auth/TLS;:47 dùng APP_PUBLIC_URL; EmailService.java:13–20 dùng app.public-url. compose.local.yaml:21 default host8026→container8025; compose.yaml:19 host8025. | SRS P110 ghi PUBLIC_APP_URL: cần sửa tên tài liệu hoặc alias có chủ đích. localhost8025 chỉ đúng compose.yaml,khác local profile; không gọi Mailpit luôn sai cổng. SMTP switch đã có config, chưa chạy bulk delivery. |

Java21/Spring Boot4.1.2/MySQL8.4/Flyway và Modular Monolith hiện tương thích với định hướng SRS. Không có lý do từ lần re-audit này để nâng framework hoặc đổi kiến trúc. T1 vẫn dùng session/CSRF; đăng ký chính xác routes/guards mới trong SecurityConfig khi triển khai, không bỏ denyAll/CSRF để tích hợp.

Không chạy lại JUnit/MockMvc/Testcontainers/Playwright/migration/benchmark/import/SMTP test trong re-audit. Các AC, concurrency20, privacy,5.000row và benchmark trong DOCX là **tiêu chí cần thực thi**, không phải evidence PASS. Báo cáo cũ không được dùng để tự đánh PASS cho tính năng v1.2.2.

## 7. Khả năng giao việc cho từng team

| Team | SRS đủ để code chưa? | Điều kiện còn thiếu / phần có thể bắt đầu |
|---|---|---|
| T1 | CONDITIONAL GO | Tái sử dụng core và triển khai phần đã rõ (gateway rule,batch design); trước fullmodule đóng G06/G07/G15/G19/G20/G27/G28 và import I01–I06. Không viết lại auth hay giảm CSRF. |
| T2 | CONDITIONAL GO | Profile immutable snapshot/consent policy/UI có thể làm; còn typedcontracts G10/G27,catalog G12,Preference G14,withdraw G08 và roster-link I01/I02. |
| T3 | CONDITIONAL GO | Task/submission/group/rubric/visibility policy có thể làm theo phầnFIXED; workflow thật bị chặn G01/G18/G26; cần G07/G20/G25/G27 trước giao điểm tương ứng. |
| T4 | CONDITIONAL GO | Strict classifier/scoring pure có thể làm; enterprise aggregate NO GO đến khi G03 đóng; cần typedcriteria G10,catalog G12 và contracts G27. Benchmark/evidence chạy sau implementation. |
| T5 | NO GO cho module end-to-end | Có thể làm verification R05/term R02 và UI độc lập. Chưa giao full Placement/capacity/invite/opportunity khi G01/G08/G09/G14/G18/G26/G27 và privacy G03 liên quan còn mở; UI import phụ thuộc I01–I06. |

CONDITIONAL GO ở đây chỉ dành cho công việc có scope/oracle đã rõ, không cho phép developer hoặc Codex tự chọn các quyết định còn mở rồi coi đó là baseline. Không cần đợi G29–G34 hoặc benchmark chạy xong mới làm parser, formula, task state service hay UI độc lập; nhưng phải đóng đúng contract/quy tắc còn thiếu trước logic/giao điểm liên quan.

## 8. Kết luận cuối cùng

| Câu hỏi | Trả lời |
|---|---|
| Câu1: v1.2.2 đã khắc phục đầy đủ 34 phát hiện chưa? | **Chưa.** 12 FIXED,21 PARTIALLY FIXED,1 NOT FIXED ở source; không có CONFLICT/UNVERIFIED cho từng finding sau khi đọc nguồn hiện có. |
| Câu2: đủ để code và tích hợp cả5team mà không quyết định nghiệp vụ quan trọng? | **NO GO cho toàn bộ.** Có CONDITIONAL GO các phần độc lập; activation/close, privacy oracle, authority/registry, capacity/deadlines,typed contracts và một số policy còn phải chốt. |
| Câu3: import+activation1.000–5.000 đủ đặc tả để lập trình/nghiệm thu? | **NO GO cho backend và kiểm thử toàn luồng; GO wireframe.** Cần I01–I06; không có bằng chứng chạy bulk import mới. |

**Quyết định tổng thể: NO GO — UT.BRIDORA SRS V1.2.2 cho việc giao Codex tự triển khai hoàn chỉnh và tích hợp cả năm phân hệ ngay.**

Các điều kiện thực sự còn chặn việc giao trọn logic/tích hợp:

1. **Đóng hai P0 còn mở:** activation/close/handoff Placement (G01 cùng G18/G26) và protected release/zero/secondary/cross-query oracle (G03). G02 không cần mở lại quyết định owner/eligibility đã chốt.
2. **Chốt các lựa chọn P1 còn thiếu cho luồng tương ứng:** grantor/bootstrap/delegation và permission/audit mapping (G06/G07/G28), invitation/withdraw/reservation/capacity (G08/G09), typed criteria và catalog/Preference (G10/G12/G14), file/mentor onboarding/idempotency/weekly deadline (G15/G20/G19/G25).
3. **Bàn giao contract pack thực tế thay câu “sẽ công bố”:** G27 cần DTO/port/OpenAPI/version/enum/error/alias và expected responses cho giao điểm dùng chung, gồm policy visibility/consent đã chốt về business. Thiết kế physical keys/index/transaction phải phản ánh đúng các quyết định này; chưa cần tự coi code đã có.
4. **Đóng I01–I06 cho import:** account-link decision matrix, deterministic preview/validation, per-row recovery/effect keys và token/mail/resend state machine, cùng scoped UI/API/port contracts T1/T2/T5.

G29–G32 là việc chất lượng/nghiệm thu cần hoàn thiện, G33 là tối ưu source chưa thực hiện, G34 là rule kiến trúc đã chốt nhưng test chưa chạy; không dùng các mục này để đòi mọi artefact/evidence hoàn tất trước bất kỳ dòng code nào. Ngược lại, **GO đặc tả sau khi đóng blocker không có nghĩa GO nghiệm thu phần mềm**: phải triển khai và chạy test thật ở revision/fixture/môi trường xác định. Production/dữ liệu thật/điểm học vụ thật vẫn qua cổng §25.3, không thuộc quyết định thay chính sách UTH của báo cáo này.

Chỉ tạo file báo cáo này. Không sửa tài liệu gốc, báo cáo v1.1, source, database hoặc migration; không commit/push/deploy.
