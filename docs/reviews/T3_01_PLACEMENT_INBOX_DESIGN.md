# T3-01 — Thiết kế tiếp nhận Placement vào Inbox/Case

Ngày soạn: 06/11/2026; cập nhật: 07/11/2026 (Asia/Saigon). DEV05 — Đoàn Phan Vĩnh Phú; QA03 — Phạm Lâm Phương Bảo.

**PROPOSAL — PENDING CROSS-TEAM REVIEW**

T3-01 tuần 1 bàn giao “Service port contract; không tự tạo Placement”. Checkpoint lịch sử `3eaa52c` có hai policy và 99 unit test; `53120d7` thêm design/test plan. Milestone `f28a3189a3a88530c9dbe2ebcfc0f6542b6d7c00` (`f28a318`) đã có ba record canonical, interface PlacementLifecycleConsumer và 174 contract/value test. Consumer runtime, inbox/Case và I11 chưa có evidence thực thi trên nhánh DEV05.

Lead/T1 và T5 đã đồng ý package canonical và DEV05 cung cấp ba record theo xác nhận của DEV05 ngày 07/11/2026. Chỉ đạo bổ sung mới nhất xác nhận **T1 đã hoàn tất bàn giao nền tảng**; DEV05 tích hợp Core/Contract đã giao, không chờ bàn giao lại. Nhãn proposal áp dụng cho nội dung cross-team review còn lại, không phủ nhận quyết định package đã có. T3-01 giữ IN PROGRESS; chưa xác nhận I11, QA signoff hoặc CI tổng thể PASS.

## 1. Phạm vi và nguồn đối chiếu

Source review: `f28a318`, nhánh `feature/t3-placement-inbox`; refactor trong working tree sau milestone này được kiểm thử riêng ở test plan. Commit nền: `0cc1daf3bf9db8a6688a2c40cb56616dec316a42` (`0cc1daf`).

| Nguồn | Căn cứ và giới hạn |
|---|---|
| [Quyết định contract](../contracts/README.md) | v1.0 được người dùng chốt ngày 05/11/2026; không đồng nghĩa QA, runtime hoặc chính sách dữ liệu thật đã được duyệt |
| [Contract Pack v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) | C.0: kiểu/context; BC41: port và lifecycle; CP-WS1/WS2: Case và Task; D/E/G: permission, audit và schema |
| [Review T1-01](../contracts/T1_01_TECHNICAL_REVIEW.md), [bàn giao Core](../status/HANDOFF_CHECKLIST.md) | T1 đã bàn giao Core/Contract theo chỉ đạo mới nhất; v1.1 Candidate và evidence checker vẫn tách biệt runtime T3 |
| [Hướng dẫn BE/FE](../guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md) | Package module, port nội bộ, ownership, guard, migration do T1 cấp version |
| [Workbook](../plans/UTBridora_Plan.xlsx) | T3-01 tuần 1: service port contract, nhận đúng trạng thái, không mở Task trước ACTIVE; DEV05/QA03; phụ thuộc T1-01 |
| [Trạng thái triển khai](../status/IMPLEMENTATION_STATUS.md) | Core T1 đã có; nghiệp vụ Placement/workspace chưa có trong checkout khảo sát |

SHA-256 v1.0 đã kiểm tra khớp quyết định: `9c62f229908f3c66f300455e33bdde6aef127f8e6edd99b286def0687ff9f2ef`.

Tên file v1.0 và nhãn PROPOSED/PENDING bên trong là checkpoint cũ; đọc cùng quyết định ngày 05/11/2026. Không áp dụng phê duyệt đó sang diff v1.1 Candidate.

Theo workbook, sheet `CAP NHAT CHUNG`, H11 là “Service port contract; không tự tạo Placement”; I11 yêu cầu gửi thử thông điệp đúng hợp đồng, nhận đúng trạng thái và không mở Task khi chưa ACTIVE. Port Java/value contract đã có tại f28a318; truyền fixture qua interface chưa chứng minh I11 runtime.

Phạm vi nhánh gồm chín file Java và hai tài liệu. Lượt sửa feedback chỉ chạm năm file: PlacementLifecycleEvent, PlacementEventEnvelopePolicy, PlacementLifecycleContractTest và hai tài liệu này. Chưa tạo consumer implementation, REST API, persistence, migration hoặc worker. Luồng runtime bên dưới là yêu cầu cho giai đoạn được giao riêng.

## 2. Hiện trạng source và ownership

Đã có package `vn.utbridora.auth.modules.internship.domain` với hai policy và hai test:

| Source tại commit 3eaa52c | Hành vi và bằng chứng |
|---|---|
| [InternshipActivityPolicy](../../src/main/java/vn/utbridora/auth/modules/internship/domain/InternshipActivityPolicy.java) / [test](../../src/test/java/vn/utbridora/auth/modules/internship/domain/InternshipActivityPolicyTest.java) | Case IN_PROGRESS, Placement ACTIVE, effectiveAt≤now; đầu vào thiếu/sai bị từ chối. 41 unit test PASS |
| [PlacementEventEnvelopePolicy](../../src/main/java/vn/utbridora/auth/modules/internship/domain/PlacementEventEnvelopePolicy.java) / [test](../../src/test/java/vn/utbridora/auth/modules/internship/domain/PlacementEventEnvelopePolicyTest.java) | Bốn event type, schemaVersion=1, giới hạn và equality của version; đầu vào thiếu/sai bị từ chối. 58 unit test PASS |

Tại f28a318 còn có [PlacementLifecycleEvent](../../src/main/java/vn/utbridora/auth/contracts/placement/PlacementLifecycleEvent.java), [TrustedEventContext](../../src/main/java/vn/utbridora/auth/contracts/placement/TrustedEventContext.java), [EventConsumeResult](../../src/main/java/vn/utbridora/auth/contracts/placement/EventConsumeResult.java), [PlacementLifecycleConsumer](../../src/main/java/vn/utbridora/auth/modules/internship/integration/PlacementLifecycleConsumer.java) và [contract test](../../src/test/java/vn/utbridora/auth/modules/internship/integration/PlacementLifecycleContractTest.java). Constructor thực thi validation và ném IllegalArgumentException với thông báo không chứa giá trị đầu vào theo quyết định DEV05.

DEV đã chạy 273 test T3 tại f28a318 (41 + 58 + 174), BUILD SUCCESS. Lead báo cáo riêng 301 test (thêm 28 Core) trên bản sao f28a318; kết quả hồi quy working tree hiện tại ghi ở [test plan](../testing/T3_01_TEST_PLAN.md). Policy/record không đọc nguồn hoặc ghi dữ liệu; tạo result ACK_APPLIED không phải chứng cứ transaction đã commit.

Đã kiểm source HEAD f28a318 và remote refs ngày 07/11/2026, gồm origin/main 0cc1daf và origin/feature/t5-term-verification c299c4f. Chưa tìm thấy định nghĩa Java PlacementReadPort/PlacementView/provider. Ca nhận Activated rồi đọc nguồn và ca readCurrentForGuard khi tạo Task chưa nối được tới nguồn T5 thật; không tạo provider production giả. Migration checkout hiện có V1–V7.

Core có `UtBridoraApplication` tại `vn.utbridora.auth`, Spring Security/session JDBC, `AuthorizationPolicy`, `AuditService`, `AuditAction`, JUnit/Mockito và MySQL integration harness. Tái sử dụng nền tảng hiện hữu; không tạo auth hoặc application riêng.

### Core T1 đã bàn giao và điểm nối cụ thể

Đối chiếu tại f28a318 (Core kế thừa 0cc1daf), với [HANDOFF_CHECKLIST](../status/HANDOFF_CHECKLIST.md), [ARCHITECTURE](../core/ARCHITECTURE.md), [AUDIT_LOG](../core/AUDIT_LOG.md), [OpenAPI](../openapi.json) và BC41 v1.0:

| Thành phần đã có | DEV05 có thể tái sử dụng / phần cần đối chiếu khi nối runtime |
|---|---|
| AuditService.write / writeScoped | @Transactional, tham gia transaction mutation đang có; failure phải rollback. Dùng action hợp lệ từ AuditAction |
| AuditService.securityDenied | REQUIRES_NEW để giữ denial qua rollback; không dùng thay audit success của Case |
| AuthorizationPolicy.isSelf / hasOrganization / hasGlobal | Kiểm account/securityVersion/membership/scope theo API Core; assignment và trạng thái Case/Placement do service nghiệp vụ bổ sung |
| RequestIdFilter.doFilterInternal | Sinh request ID phía server và MDC; đây là HTTP correlation, không tự xác thực producer của event |
| TrustedEventContext và PlacementLifecycleConsumer | Value contract/chữ ký đã có; constructor chỉ kiểm syntax. Chưa có Java adapter xác minh T5 và chuyển context sang actor SYSTEM/purpose/scope tại revision đã kiểm |
| Inbox/dedup cho consume(context,event) | Chưa xác định class/method nhận eventId cùng tuple aggregate/version/type trong src/main/java tại f28a318/0cc1daf; ca retry hai delivery và commit inbox+Case+audit chưa thể kiểm qua API cụ thể |
| AuditAction cho tiếp nhận/chuyển Case | AuditService.store gọi AuditAction.valueOf; enum runtime có PLACEMENT_CREATED/PLACEMENT_TERMINATED nhưng chưa có action tiếp nhận/chuyển Case được xác định. Cần đối chiếu mapping cụ thể trước khi ghi mutation Case |

Những điểm chưa nối ở bảng là giới hạn implementation/locator trên revision này, không phải kết luận T1 chưa bàn giao. Nếu triển khai runtime được giao riêng, báo chính xác API/mapping và ca bị chặn để Lead/T1 chỉ dẫn sử dụng bản bàn giao hoặc điều phối thay đổi Core. Không tự xây thêm trong lượt refactor này.

| Bên / thành phần | Ranh giới trách nhiệm cần đối chiếu khi review |
|---|---|
| DEV05/T3 | Service port phía tiếp nhận và hai policy T3; Case thuộc domain T3 nhưng persistence/consumer đầy đủ chưa nằm trong phần bàn giao này |
| T1 / DEV05 tích hợp | T1 đã bàn giao Core/Contract; DEV05 tái sử dụng theo API hiện có. T1 tiếp tục điều phối thay đổi Core/CI/migration và chỉ dẫn điểm nối cụ thể khi cần |
| T5 | Placement producer, PlacementReadPort/PlacementView và nguồn authoritative; T3 không đọc/ghi repository nội bộ T5 |
| DEV06/T3 | Task guard/service theo T3-02/T3-05; gắn kiểm tra trạng thái và quyền tại mutation thực tế |
| QA03 | Review evidence, kiểm thử độc lập và xác nhận QA; phối hợp QA05 khi tích hợp Placement |

Bảng mô tả ranh giới phối hợp theo tài liệu/phân công, không giao thêm việc hoặc thay đổi ownership. Package và DEV05 cung cấp ba record đã được xác nhận; việc nghiệm thu runtime là bước riêng.

Contract cho phép T3 lưu Case, liên kết placementId, snapshot được quy định và lastAppliedVersion. Placement vẫn do T5 sở hữu; projection cục bộ không đủ để cho phép Task/Submission.

## 3. Service port và kiểu dữ liệu đã triển khai

Chữ ký bàn giao chính theo BC41:

```text
PlacementLifecycleConsumer.consume(TrustedEventContext, PlacementLifecycleEvent): EventConsumeResult
```

**Package đã được Lead/T1 và T5 đồng ý theo xác nhận DEV05:**

- `vn.utbridora.auth.contracts.placement`: PlacementLifecycleEvent, TrustedEventContext, EventConsumeResult dùng chung một bản canonical.
- `vn.utbridora.auth.modules.internship.integration`: interface tiếp nhận PlacementLifecycleConsumer phía T3.

Ba record và interface đã có tại f28a318; dùng chung một bản canonical, không tạo Maven module mới. AppPrincipal và ProfileVisibilityPort không thay cho ba kiểu BC41. Placement vẫn do T5 sở hữu.

Refactor sau review: PlacementLifecycleEvent.hasSupportedTypeAndVersions giữ một nguồn rule thuần type/schema/version trong contract; constructor và policy T3 cùng dùng rule này. Chiều dependency là T3 policy → shared contract, shared contract không import modules.*. Giữ nguyên 15 component event, chữ ký consumer và hành vi fail-closed. Không thay BC41 hoặc tạo consumer implementation.

Các chữ ký dưới đây là đặc tả BC41. **Chỉ PlacementLifecycleConsumer đã có interface Java trong nhánh DEV05**; những port khác là dependency cần nối, không được coi là implementation đã chạy:

```text
PlacementReadPort.readForInternship(actor:ActorContext,placementId:U):Result<PlacementView>
PlacementReadPort.readCurrentForGuard(actor:ActorContext,placementId:U):Result<PlacementView>
PlacementLifecycleConsumer.consume(context:TrustedEventContext,event:PlacementLifecycleEvent):EventConsumeResult
InternshipCaseReadPort.participant(actor:ActorContext,caseId:U):Result<CaseParticipantRef>
InternshipCaseReadPort.readClosureProof(actor:ActorContext,caseId:U):Result<CaseClosureProof>
```

`readClosureProof` thuộc luồng đóng Case, chỉ ghi dependency, chưa triển khai trong lát cắt tiếp nhận. Không thêm public callback hoặc gọi HTTP vòng về cùng backend.

Ba Java record đã triển khai mapping bên dưới. Mọi field wire đều bắt buộc hiện diện; nullable chỉ cho biết giá trị có thể null. Constructor kiểm giá trị ngay khi tạo record. Adapter wire vẫn phải kiểm field bị bỏ qua/field lạ, kiểu số/timestamp và mapping lỗi; record không phân biệt field nullable bị thiếu với null. Chưa có adapter này trong milestone contract.

| Kiểu | Field | Java type hiện có | Nullable / ràng buộc v1.0 |
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

EventConsumeResult giữ retryAfterSeconds là Integer nullable theo int|null v1.0, kể cả giá trị âm. Schema v1.1 Candidate thêm minimum:0; chưa tuyên bố tương thích đầy đủ v1.1 và không tự áp constraint mới. Adapter/runner thật phải tuân lịch retry BC41, không dùng số âm để hẹn delivery. Outcome/reason pairing chưa được tự bổ sung.

Khi constructor từ chối dữ liệu, chưa gọi consumer: adapter tương lai phải map version lệch thành REJECT/INVALID_SCHEMA khi có eventId hợp lệ. Với eventId sai, không chế UUID hoặc result success. Quyết định dùng IllegalArgumentException tại constructor đã có; representation lỗi wire/delivery vẫn thuộc biên tích hợp.

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
- T1 đã bàn giao nền tảng; DEV05 đối chiếu điểm nối inbox/dedup cụ thể trong mục 2 khi được giao runtime, không tự tạo bảng chung hoặc kết luận phải chờ T1 bàn giao lại.
- Consumer dùng AuditService.write/writeScoped trong transaction mutation T3; không phát lại audit thành công của producer T5. Mapping action tiếp nhận/tạo/chuyển Case phải hợp lệ với AuditAction ở revision tích hợp (điểm cụ thể tại mục 2); không truyền tên domain event vào AuditAction.valueOf.
- Retry theo BC41: tối đa 5 lần, các mốc 1/5/30/300/1800 giây rồi DLQ. Cần chốt owner runner, quy ước đếm attempt và reprocess; không tạo worker trong task tài liệu.
- Reprocess vẫn đọc nguồn và reconcile theo placementId. Không lưu terminal success cho lỗi tạm thời làm mất khả năng retry.
- Đọc nguồn trước mutation vẫn có rủi ro race cancellation; chiến lược lock/version/transaction liên module phải được thống nhất và test thật, không chỉ mock.

## 6. Acceptance criteria và kiểm thử

Kế hoạch chi tiết: [T3_01_TEST_PLAN.md](../testing/T3_01_TEST_PLAN.md).

| Acceptance criterion T3-01 | Bằng chứng hiện có / phần còn thiếu |
|---|---|
| Service port tiếp nhận | Có ba record, interface và 174 contract/value test tại f28a318; chưa có consumer runtime |
| Nhận đúng trạng thái | Có unit test điều kiện trạng thái với đầu vào trực tiếp; chưa chứng minh nhận event thật, đọc nguồn hoặc chuyển Case |
| Không mở Task khi chưa ACTIVE | Policy từ chối APPROVED, future effectiveAt và Case không IN_PROGRESS; chưa có bằng chứng Task service thực sự gọi guard và chặn ghi |
| Không tự tạo/phê duyệt Placement | Chín file Java T3-01 tại f28a318 không có mutation Placement; chưa có consumer runtime để kiểm integration |
| Không nhân bản khi retry | Chưa chạy MySQL constraint/concurrency/rollback inbox+Case+audit; unit policy không chứng minh dedup |

DEV tại milestone f28a318: **273 test T3 PASS**; Lead báo cáo thêm 28 Core, tổng 301 trên bản sao milestone. Sau refactor, DEV đã chạy toàn bộ unit suite: **301 PASS, 0 failures/errors/skipped, BUILD SUCCESS** ngày 07/11/2026; ba record biên dịch độc lập source T3 cũng PASS. Lệnh/report và điều kiện chạy nằm trong test plan. Integration T3-01 và QA signoff chưa thực hiện. Test double chỉ chứng minh chữ ký/truyền fixture, không chứng minh cập nhật Case hoặc I11. T3-01 giữ IN PROGRESS.

CI của f28a318 đã xác minh: [PR run 42330544752](https://github.com/NguyenThanhTiep/utbridora/actions/runs/42330544752) và [push run 42330538676](https://github.com/NguyenThanhTiep/utbridora/actions/runs/42330538676): verify SUCCESS, contracts FAILURE. Không suy số ca tích hợp/E2E từ trạng thái job, không gọi CI tổng thể PASS và không dùng run này làm evidence của refactor chưa push.

Builder tại 0cc1daf/f28a318 dùng sorted(Path) cho Java/SQL rồi so text manifest, dẫn tới thứ tự Windows/POSIX khác nhau. Manifest của f28a318 còn thiếu sáu source: ba record, hai policy và interface; hash source cũ không đổi. Sau refactor, hash của PlacementLifecycleEvent/PlacementEventEnvelopePolicy cũng cần phản ánh bytes mới khi đồng bộ. Chờ T1 xác nhận cách đồng bộ manifest trên nhánh DEV05; không áp patch, sửa builder hoặc tự chạy generator trong lượt này. Patch cũ chỉ thêm hai policy không phù hợp HEAD hiện tại. Việc điều phối CI này tách biệt trạng thái T1 đã bàn giao Core.

## 7. Quyết định và dependency trước khi code

| ID | Vấn đề | Người cần xác nhận / tác động |
|---|---|---|
| D01 | ĐÃ XÁC NHẬN: package contracts.placement, DEV05 cung cấp ba record; interface ở modules.internship.integration | Có source tại f28a318; T5 dùng lại, không tạo bản thứ hai |
| D02 | Điểm nối server xác minh T5 → SYSTEM/purpose/scope, chưa xác định Java adapter tại f28a318 | DEV05 đối chiếu bản bàn giao T1 và báo locator/ca cụ thể; không dùng SYSTEM như ADMIN |
| D03 | Điểm nối inbox/dedup tại mục 2; consumer/Case transaction chưa triển khai; race với nguồn T5 cần kiểm thật | DEV05 tích hợp nền tảng đã giao theo phân công riêng, phối hợp T5; không chờ bàn giao T1 lại |
| D04 | Mapping action tiếp nhận/chuyển Case chưa xác định trong AuditAction tại f28a318 | AuditService đã có; đối chiếu action cụ thể với Lead/T1 trước mutation, không tự thêm enum hoặc bỏ audit |
| D05 | DDL Case, version migration và thứ tự merge | T3 thiết kế, T1 cấp version; không tạo V8 tùy ý |
| D06 | Tổ hợp duplicate+stale; nguồn version thấp hơn event; nguồn không sẵn sàng khi duplicate; ACTIVE tương lai; mapping terminal/COMPLETED/PENDING_APPROVAL | Owner contract T1/T5/T3; không tự thêm outcome/reason hoặc transition |
| D07 | Workbook T3-01 gắn R25/R26 về weekly report/close trong khi mô tả là inbox | DEV05/Lead; đề xuất ghi dependency, chưa mở rộng implementation |
| D08 | Cơ chế runner retry/DLQ và đếm attempt | T1/T5/T3; cần chốt trước delivery runtime |

Workbook T5-07 tuần 3 có Placement approve/effectiveAt/ACTIVE + event T3; T3-02 có cohort/group/task domain. Lịch này cho phép chuẩn bị thiết kế/test độc lập, không chứng minh các dependency đã có implementation.

## 8. Câu hỏi cần Lead/T1/T5/QA03 xác nhận

1. Package và người cung cấp đã xác nhận; review refactor một nguồn rule trong shared contract, không mở lại ownership/chữ ký BC41.
2. Chỉ dẫn class/method tích hợp cụ thể cho các điểm chưa nối tại mục 2: trusted adapter, inbox/dedup và audit action Case; T1 đã bàn giao, DEV05 đối chiếu revision/mapping khi nối runtime.
3. T5 công bố PlacementReadPort.readForInternship/readCurrentForGuard cùng PlacementView/provider và producer/outbox ở revision nào để kiểm nguồn authoritative?
4. Mapping lỗi constructor tại adapter, các tổ hợp D06 và khác biệt retryAfterSeconds sẽ được xử lý thế nào khi tích hợp, giữ baseline v1.0 hiện hành?
5. Tiêu chí runtime I11 sẽ phối hợp kiểm chứng thế nào với nền tảng T1 đã giao, nguồn T5, Task service DEV06 và QA03/QA05? T1 xác nhận quy trình đồng bộ manifest trên nhánh DEV05 riêng với phạm vi code này.

Các câu hỏi là yêu cầu review điểm nối còn lại; quyết định package/người cung cấp đã có không bị rút lại. Không ghi thay nghiệm thu runtime/QA hoặc phê duyệt v1.1.

## 9. Các giai đoạn tiếp theo

| Giai đoạn | Phạm vi/file | Dependency, rủi ro và kiểm chứng |
|---|---|---|
| Milestone f28a318 đã có | Hai policy, ba record, interface, ba lớp test và hai tài liệu | 273 test T3 PASS; chưa nghiệm thu runtime |
| Feedback hiện tại | Refactor event/policy, cập nhật contract test và hai tài liệu; đúng năm file | Shared contract độc lập T3; hồi quy toàn bộ unit; chưa commit/push |
| Tích hợp theo phân công riêng | Consumer runtime, persistence/wiring/integration và migration được cấp | D02–D06/D08, dùng Core T1 đã giao; cần provider Placement T5 và Task service DEV06, kiểm MySQL/race/rollback |
| Bàn giao QA | Report gắn commit, lệnh, số test, logs, blocker | QA03 xác nhận; commit/push/PR vẫn cần quyền riêng; CI đọc kết quả đúng commit |

Phần review này không thay contract, source T1/T5/DEV06, security config, registry hoặc workbook. Chưa yêu cầu merge, không coi đề xuất là quyết định kiến trúc đã approved.
