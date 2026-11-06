# T3-01 — Thiết kế tiếp nhận Placement vào Inbox/Case

Ngày: 06/11/2026. DEV05 — Đoàn Phan Vĩnh Phú; QA03 — Phạm Lâm Phương Bảo.

**PROPOSAL — PENDING CROSS-TEAM REVIEW**

T3-01 tuần 1 bàn giao “Service port contract; không tự tạo Placement”. DEV05 đã triển khai hai policy thuần và unit test tại commit `3eaa52c1c18ddf1a6bdfdf859d8dcfc76d1ba011` (`3eaa52c`). Java service port, ba kiểu canonical và consumer runtime chưa được triển khai.

Tài liệu này đề nghị Lead/T1/T5/QA03 review phương án theo BC41 v1.0. Package và người tạo các kiểu dùng chung chưa được phê duyệt. T3-01 chưa được đánh dấu DONE vì acceptance I11 chưa được xác nhận đầy đủ; chưa có QA signoff hoặc kết quả GitHub CI được xác minh cho phần bàn giao này.

## 1. Phạm vi và nguồn đối chiếu

Source review: `3eaa52c1c18ddf1a6bdfdf859d8dcfc76d1ba011`, nhánh `feature/t3-placement-inbox`. Commit nền khi tạo nhánh: `0cc1daf3bf9db8a6688a2c40cb56616dec316a42`.

| Nguồn | Căn cứ và giới hạn |
|---|---|
| [Quyết định contract](../contracts/README.md) | v1.0 được người dùng chốt ngày 05/11/2026; không đồng nghĩa QA, runtime hoặc chính sách dữ liệu thật đã được duyệt |
| [Contract Pack v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) | C.0: kiểu/context; BC41: port và lifecycle; CP-WS1/WS2: Case và Task; D/E/G: permission, audit và schema |
| [Review T1-01](../contracts/T1_01_TECHNICAL_REVIEW.md) | T1-01 còn đang làm; bộ schema v1.1 và kết quả checker không chứng minh adapter runtime |
| [Hướng dẫn BE/FE](../guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md) | Package module, port nội bộ, ownership, guard, migration do T1 cấp version |
| [Workbook](../plans/UTBridora_Plan.xlsx) | T3-01 tuần 1: service port contract, nhận đúng trạng thái, không mở Task trước ACTIVE; DEV05/QA03; phụ thuộc T1-01 |
| [Trạng thái triển khai](../status/IMPLEMENTATION_STATUS.md) | Core T1 đã có; nghiệp vụ Placement/workspace chưa có trong checkout khảo sát |

SHA-256 v1.0 đã kiểm tra khớp quyết định: `9c62f229908f3c66f300455e33bdde6aef127f8e6edd99b286def0687ff9f2ef`.

Tên file v1.0 và nhãn PROPOSED/PENDING bên trong là checkpoint cũ; đọc cùng quyết định ngày 05/11/2026. Không áp dụng phê duyệt đó sang diff v1.1 Candidate.

Theo workbook, sheet `CAP NHAT CHUNG`, H11 là “Service port contract; không tự tạo Placement”; I11 yêu cầu gửi thử thông điệp đúng hợp đồng, nhận đúng trạng thái và không mở Task khi chưa ACTIVE. Hai policy là phần chuẩn bị có unit evidence; chưa hoàn thành bàn giao port Java hoặc I11 runtime.

Phạm vi review gồm bốn file Java đã commit và hai tài liệu thiết kế/test plan. Chưa có REST API, canonical DTO/context/result, consumer, persistence, migration hoặc worker mới. Các phần runtime dưới đây là yêu cầu tích hợp cần phối hợp sau này, không phải phần đã triển khai hay đề nghị DEV05 làm thay owner khác.

## 2. Hiện trạng source và ownership

Đã có package `vn.utbridora.auth.modules.internship.domain` với hai policy và hai test:

| Source tại commit 3eaa52c | Hành vi và bằng chứng |
|---|---|
| [InternshipActivityPolicy](../../src/main/java/vn/utbridora/auth/modules/internship/domain/InternshipActivityPolicy.java) / [test](../../src/test/java/vn/utbridora/auth/modules/internship/domain/InternshipActivityPolicyTest.java) | Case IN_PROGRESS, Placement ACTIVE, effectiveAt≤now; đầu vào thiếu/sai bị từ chối. 41 unit test PASS |
| [PlacementEventEnvelopePolicy](../../src/main/java/vn/utbridora/auth/modules/internship/domain/PlacementEventEnvelopePolicy.java) / [test](../../src/test/java/vn/utbridora/auth/modules/internship/domain/PlacementEventEnvelopePolicyTest.java) | Bốn event type, schemaVersion=1, giới hạn và equality của version; đầu vào thiếu/sai bị từ chối. 58 unit test PASS |

Tổng 99 unit test PASS, 0 failures/errors/skipped, BUILD SUCCESS trong lượt DEV đã ghi nhận. Chi tiết lệnh và report tại [test plan](../testing/T3_01_TEST_PLAN.md). Hai policy không đọc nguồn, không ghi dữ liệu và không đưa ra quyết định authorization đầy đủ.

Chưa thấy Java PlacementReadPort, PlacementLifecycleConsumer, ba kiểu canonical, Case entity/repository hoặc inbox/outbox dùng chung trong checkout này. Sự tồn tại trên nhánh/PR khác cần owner xác nhận. Migration hiện có V1–V7.

Core có `UtBridoraApplication` tại `vn.utbridora.auth`, Spring Security/session JDBC, `AuthorizationPolicy`, `AuditService`, `AuditAction`, JUnit/Mockito và MySQL integration harness. Tái sử dụng nền tảng hiện hữu; không tạo auth hoặc application riêng.

| Bên / thành phần | Ranh giới trách nhiệm cần đối chiếu khi review |
|---|---|
| DEV05/T3 | Service port phía tiếp nhận và hai policy T3; Case thuộc domain T3 nhưng persistence/consumer đầy đủ chưa nằm trong phần bàn giao này |
| T1 | Điều phối shared contract, trusted context, security, inbox/outbox/audit dùng chung và version migration |
| T5 | Placement producer, PlacementReadPort/PlacementView và nguồn authoritative; T3 không đọc/ghi repository nội bộ T5 |
| DEV06/T3 | Task guard/service theo T3-02/T3-05; gắn kiểm tra trạng thái và quyền tại mutation thực tế |
| QA03 | Review evidence, kiểm thử độc lập và xác nhận QA; phối hợp QA05 khi tích hợp Placement |

Bảng mô tả ranh giới phối hợp theo tài liệu/phân công, không giao thêm việc hoặc thay đổi ownership. Người tạo và nơi đặt các kiểu dùng chung vẫn cần thống nhất.

Contract cho phép T3 lưu Case, liên kết placementId, snapshot được quy định và lastAppliedVersion. Placement vẫn do T5 sở hữu; projection cục bộ không đủ để cho phép Task/Submission.

## 3. Service port và kiểu dữ liệu đề xuất

Chữ ký bàn giao chính theo BC41:

```text
PlacementLifecycleConsumer.consume(TrustedEventContext, PlacementLifecycleEvent): EventConsumeResult
```

**Package proposal — pending cross-team review:**

- `vn.utbridora.auth.contracts.placement`: PlacementLifecycleEvent, TrustedEventContext, EventConsumeResult dùng chung một bản canonical.
- `vn.utbridora.auth.modules.internship.integration`: interface tiếp nhận PlacementLifecycleConsumer phía T3.

Đây chỉ là đề xuất package dưới gốc hiện có, không tạo Maven module mới. Chưa có các kiểu này trong source và chưa có quyền tạo chúng. T1/T5 cần xác nhận kiểu trên nhánh/PR khác và người cung cấp để tránh trùng; package mới không thay quyền sở hữu Placement của T5. AppPrincipal và ProfileVisibilityPort hiện có không thay được ba kiểu BC41.

Các chữ ký dưới đây là đặc tả BC41 hiện có, **chưa phải interface Java đã triển khai**:

```text
PlacementReadPort.readForInternship(actor:ActorContext,placementId:U):Result<PlacementView>
PlacementReadPort.readCurrentForGuard(actor:ActorContext,placementId:U):Result<PlacementView>
PlacementLifecycleConsumer.consume(context:TrustedEventContext,event:PlacementLifecycleEvent):EventConsumeResult
InternshipCaseReadPort.participant(actor:ActorContext,caseId:U):Result<CaseParticipantRef>
InternshipCaseReadPort.readClosureProof(actor:ActorContext,caseId:U):Result<CaseClosureProof>
```

`readClosureProof` thuộc luồng đóng Case, chỉ ghi dependency, chưa triển khai trong lát cắt tiếp nhận. Không thêm public callback hoặc gọi HTTP vòng về cùng backend.

Đề xuất dùng Java record với mapping bên dưới. Contract v1.0 quy định dữ liệu trao đổi, chưa chốt Java class. Mọi field đều bắt buộc hiện diện; nullable chỉ cho biết giá trị có thể null. Boxed number giúp biểu diễn đầu vào thiếu; record/annotation không tự chứng minh validation đã chạy khi gọi port nội bộ.

| Kiểu | Field | Java type đề xuất | Nullable / ràng buộc v1.0 |
|---|---|---|---|
| PlacementLifecycleEvent | eventId, placementId, studentUserId, schoolOrgId, enterpriseOrgId, termId, traceId | String | Không; UUID theo C.0 |
| PlacementLifecycleEvent | eventType | String | Không; một trong bốn tên chính xác bên dưới |
| PlacementLifecycleEvent | schemaVersion | Integer | Không; bằng 1, không phải packVersion |
| PlacementLifecycleEvent | aggregateVersion, placementVersion | Long | Không; 0..9007199254740991, hai giá trị bằng nhau |
| PlacementLifecycleEvent | effectiveAt, occurredAt | Instant | Không; biểu diễn trao đổi ISO8601 UTC |
| PlacementLifecycleEvent | reasonCode | String | Có; khi khác null khớp `[A-Z0-9_]{3,64}` |
| PlacementLifecycleEvent | decisionRef | String | Có; khi khác null là Unicode trim, 1–120 codepoint |
| TrustedEventContext | producerModule | String | Không; T3 hoặc T5 |
| TrustedEventContext | deliveryOperationId, requestId | String | Không; UUID |
| EventConsumeResult | eventId | String | Không; UUID |
| EventConsumeResult | outcome | String | Không; tập outcome BC41 bên dưới |
| EventConsumeResult | appliedAggregateVersion | Long | Có; khi khác null thuộc khoảng V |
| EventConsumeResult | reasonCode | String | Không; tập reason BC41 bên dưới |
| EventConsumeResult | retryAfterSeconds | Integer | Có; v1.0 khai báo int hoặc null |

```text
Event types:
PlacementApproved, PlacementActivated, PlacementCancelled, PlacementCompleted

Outcomes:
ACK_APPLIED, ACK_DUPLICATE, ACK_STALE, RETRY, REJECT

Reasons:
APPLIED, DUPLICATE, STALE, SOURCE_NOT_FOUND,
SCOPE_MISMATCH, INVALID_SCHEMA, SOURCE_UNAVAILABLE, STATE_CONFLICT
```

TrustedEventContext do server tạo, không bind trực tiếp từ browser; DTO không tự xác thực producer. Không thêm field state vào event hoặc coi eventType là trạng thái authoritative. EventConsumeResult là kết quả port nội bộ, không phải HTTP response.

Không tự lấy constraint `retryAfterSeconds >= 0` từ schema v1.1 Candidate hoặc đặt thêm quan hệ outcome/reason/nullability ngoài BC41 đã xác định. Các điểm chưa rõ cần owner thống nhất.

## 4. Luồng runtime cần phối hợp sau này — chưa triển khai

1. Nhận lời gọi nội bộ từ adapter tin cậy; kiểm producer/context và envelope theo BC41/C.0.
2. Dùng port T5 đọc nguồn authoritative; đối chiếu placementId, studentUserId, schoolOrgId, enterpriseOrgId, termId và version. Không coi payload event là bằng chứng phê duyệt.
3. Phối hợp kiểm dedup theo eventId và `(aggregateId, version, eventType)`, cùng lastAppliedVersion của Case. Thứ tự xử lý khi event vừa trùng vừa cũ hoặc nguồn tạm lỗi cần chốt ở mục 7.
4. Áp dụng projection nguồn mới nhất, giữ lastAppliedVersion tăng đơn điệu; không cập nhật theo event cũ đè trạng thái mới.
5. Commit inbox + Case + audit của mutation T3 trong cùng transaction. Chỉ ACK_APPLIED sau khi commit thành công; test rollback phải chứng minh không có partial write.
6. Trả kết quả BC41. Nguồn không tồn tại/scope giả: REJECT; lỗi nguồn tạm thời: RETRY. Không tự đặt reason cho trường hợp chưa được contract chỉ rõ.

| Tình huống | Ràng buộc từ contract |
|---|---|
| Nguồn APPROVED | Khi tiếp nhận hợp lệ để tạo Case: WAITING_START; không mở Task/Submission. Không dùng dòng này để mở lại terminal Case hoặc ghi đè version mới hơn |
| Nguồn ACTIVE, effectiveAt đã tới | Có thể IN_PROGRESS sau xác minh approval, scope và các điều kiện contract; chưa tự tạo Task |
| Chỉ đến effectiveAt nhưng nguồn vẫn APPROVED | Tiếp tục từ chối Task; T3 không tự đổi Placement thành ACTIVE |
| Event Activated tới trước Approved | Nếu nguồn ACTIVE và approval hợp lệ: tạo WAITING_START rồi chuyển IN_PROGRESS trong cùng transaction; Approved cũ sau đó ACK_STALE |
| ACTIVE nhưng effectiveAt tương lai | Từ chối Task/Submission; mapping kết quả consumer cụ thể cần chốt, không tự suy outcome |
| Nguồn CANCELLED hoặc Case terminal | Không tạo Case hoạt động hoặc mở lại terminal Case; không phát CaseClosed ngược từ handler cancellation |
| Duplicate/stale | Không nhân bản Case hoặc ghi lại producer success audit; lastAppliedVersion không lùi |
| Nguồn COMPLETED/PENDING_APPROVAL | Không đủ điều kiện Task; mapping Case/ACK cụ thể cần owner xác nhận cho từng trạng thái local |

Task/Submission sau này phải kiểm account/role, org/resource scope, assignment, Case IN_PROGRESS, Placement ACTIVE và effectiveAt≤serverNow qua `readCurrentForGuard`. HR/Mentor là assignment, không suy từ role ENTERPRISE. Policy trạng thái PASS không thay bằng chứng authorization hoặc service integration.

## 5. Dependency persistence, audit và retry — chưa triển khai

- G quy định Case UNIQUE placement_id, version và last_applied_placement_version; cần T3 thiết kế đầy đủ DDL/locking với owner review trước cấp migration.
- Consumer inbox là hạ tầng chung do T1 sở hữu. Cần xác nhận API transaction và constraint cho cả eventId lẫn tuple dedup; không tự tạo bảng inbox riêng để né dependency.
- Consumer không phát lại audit thành công của producer T5. BC41 yêu cầu audit mutation T3, nhưng mapping action tiếp nhận/tạo/chuyển Case chưa rõ; cần T1 xác nhận. `AuditService` hiện kiểm `AuditAction.valueOf`, không thể truyền thẳng tên domain event.
- Retry theo BC41: tối đa 5 lần, các mốc 1/5/30/300/1800 giây rồi DLQ. Cần chốt owner runner, quy ước đếm attempt và reprocess; không tạo worker trong task tài liệu.
- Reprocess vẫn đọc nguồn và reconcile theo placementId. Không lưu terminal success cho lỗi tạm thời làm mất khả năng retry.
- Đọc nguồn trước mutation vẫn có rủi ro race cancellation; chiến lược lock/version/transaction liên module phải được thống nhất và test thật, không chỉ mock.

## 6. Acceptance criteria và kiểm thử

Kế hoạch chi tiết: [T3_01_TEST_PLAN.md](../testing/T3_01_TEST_PLAN.md).

| Acceptance criterion T3-01 | Bằng chứng hiện có / phần còn thiếu |
|---|---|
| Service port tiếp nhận | Chỉ có đề xuất chữ ký/package; Java interface, kiểu canonical và contract test chưa triển khai |
| Nhận đúng trạng thái | Có unit test điều kiện trạng thái với đầu vào trực tiếp; chưa chứng minh nhận event thật, đọc nguồn hoặc chuyển Case |
| Không mở Task khi chưa ACTIVE | Policy từ chối APPROVED, future effectiveAt và Case không IN_PROGRESS; chưa có bằng chứng Task service thực sự gọi guard và chặn ghi |
| Không tự tạo/phê duyệt Placement | Bốn file Java đã review không có mutation Placement; chưa có consumer runtime để kiểm integration |
| Không nhân bản khi retry | Chưa chạy MySQL constraint/concurrency/rollback inbox+Case+audit; unit policy không chứng minh dedup |

DEV unit test: **99 PASS**. Integration và QA signoff chưa thực hiện; CI chưa xác minh. I11 gồm hành vi tiếp nhận thực tế chưa được xác nhận; không đánh dấu T3-01 DONE. Test double trong bước contract sau này chỉ chứng minh truyền dữ liệu qua interface, không được trả sẵn trạng thái/ACK để giả bằng chứng cập nhật Case.

## 7. Quyết định và dependency trước khi code

| ID | Vấn đề | Người cần xác nhận / tác động |
|---|---|---|
| D01 | Vị trí Java types BC41 và shared context/result; tránh khai báo DTO/port T5 lần hai | T1/T5/T3; chặn wiring production |
| D02 | Trusted context → ActorContext SYSTEM với purpose/action/scope allowlist | T1/T5; không dùng SYSTEM như ADMIN |
| D03 | API inbox, dedup constraint, transaction và race với nguồn T5 | T1/T5/T3; chặn tích hợp bền vững |
| D04 | Action audit hợp lệ cho mutation tiếp nhận Case | T1; không tự thêm enum/action hay bỏ audit |
| D05 | DDL Case, version migration và thứ tự merge | T3 thiết kế, T1 cấp version; không tạo V8 tùy ý |
| D06 | Tổ hợp duplicate+stale; nguồn version thấp hơn event; nguồn không sẵn sàng khi duplicate; ACTIVE tương lai; mapping terminal/COMPLETED/PENDING_APPROVAL | Owner contract T1/T5/T3; không tự thêm outcome/reason hoặc transition |
| D07 | Workbook T3-01 gắn R25/R26 về weekly report/close trong khi mô tả là inbox | DEV05/Lead; đề xuất ghi dependency, chưa mở rộng implementation |
| D08 | Cơ chế runner retry/DLQ và đếm attempt | T1/T5/T3; cần chốt trước delivery runtime |

Workbook T5-07 tuần 3 có Placement approve/effectiveAt/ACTIVE + event T3; T3-02 có cohort/group/task domain. Lịch này cho phép chuẩn bị thiết kế/test độc lập, không chứng minh các dependency đã có implementation.

## 8. Câu hỏi cần Lead/T1/T5/QA03 xác nhận

1. Package canonical đề xuất có phù hợp không?
2. Ba kiểu dữ liệu dùng chung đã tồn tại trên nhánh/PR khác chưa?
3. DEV05 được tạo ba kiểu đó hay owner sẽ cung cấp?
4. Có điểm nào của BC41 v1.0 cần làm rõ trước khi triển khai Java port, gồm mapping type/nullability và lỗi chưa xác định?
5. Tiêu chí runtime I11 sẽ phối hợp tích hợp và kiểm chứng như thế nào, với nguồn T5, hạ tầng T1, Task service DEV06 và QA03/QA05?

Các câu hỏi là yêu cầu review, chưa có xác nhận thay cho reviewer. Việc duyệt tài liệu không tự phê duyệt package, canonical types hoặc runtime.

## 9. Các giai đoạn tiếp theo

| Giai đoạn | Phạm vi/file | Dependency, rủi ro và kiểm chứng |
|---|---|---|
| Cross-team review hiện tại | Bốn file Java đã commit và hai tài liệu thiết kế/test plan | Rủi ro nhầm unit policy thành runtime; giữ rõ bằng chứng và nhãn proposal |
| Đề xuất sau thống nhất canonical | Interface tiếp nhận, ba kiểu dữ liệu và một contract test; duyệt danh sách file riêng trước code | D01 và scope D07; không viết consumer/persistence trong bước contract; test double chỉ ở test |
| Chờ dependency và phân công riêng | Consumer runtime, persistence/wiring/integration và migration được cấp | D02–D06/D08, implementation T5 và Task service DEV06; cần verify MySQL/race/rollback |
| Bàn giao QA | Report gắn commit, lệnh, số test, logs, blocker | QA03 xác nhận; commit/push/PR vẫn cần quyền riêng; CI đọc kết quả đúng commit |

Phần review này không thay contract, source T1/T5/DEV06, security config, registry hoặc workbook. Chưa yêu cầu merge, không coi đề xuất là quyết định kiến trúc đã approved.
