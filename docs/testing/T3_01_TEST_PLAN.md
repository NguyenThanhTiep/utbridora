# T3-01 — Kế hoạch kiểm thử tiếp nhận Placement

Ngày soạn: 06/11/2026; cập nhật: 07/11/2026 (Asia/Saigon). DEV05 thực hiện self-test; QA03 xác nhận QA.

**PROPOSAL — PENDING CROSS-TEAM REVIEW**

Checkpoint `3eaa52c` có hai policy/99 test; `53120d7` bổ sung design/test plan. Milestone `f28a318` đã có ba record canonical, interface PlacementLifecycleConsumer và 174 contract/value test, tổng 273 test T3 PASS. Lead báo cáo riêng 301 unit test trên bản sao f28a318 (thêm 28 Core). Kết quả chạy lại working tree sau refactor được ghi riêng tại mục 3. Consumer runtime và I11 chưa có evidence; 27 kịch bản đầy đủ bên dưới chưa được nâng thành PASS. T3-01 giữ IN PROGRESS, chưa QA signoff.

Chỉ đạo bổ sung ngày 07/11/2026 xác nhận **T1 đã hoàn tất bàn giao nền tảng**. DEV05 tái sử dụng Core/Contract đã giao, không chờ bàn giao lại. Điểm nối chưa xác định phải ghi class/method, revision và ca bị chặn theo mục 2 của thiết kế; nguồn Placement/provider T5 được theo dõi riêng.

Thiết kế và dependency: [T3_01_PLACEMENT_INBOX_DESIGN.md](../reviews/T3_01_PLACEMENT_INBOX_DESIGN.md). Nguồn expected behavior: [Contract v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md), C.0, BC41, CP-WS1/WS2, E/G. Không dùng schema v1.1 Candidate làm oracle được duyệt thay v1.0.

## 1. Điều kiện và dữ liệu test

- Chỉ dùng UUID/dữ liệu synthetic. Actor/context do test harness tạo theo cơ chế trusted được owner duyệt; không dùng credential hoặc dữ liệu người thật.
- Fixture nguồn T5 tách biệt event để thử source mới hơn event, cancellation chưa được truyền tới T3 và lỗi đọc nguồn.
- Clock cố định; kiểm effectiveAt trước, đúng và sau serverNow. Kiểm boundary theo precision storage thống nhất, không dựa vào sleep.
- Fake/mock T5 chỉ ở test. Test không chứng minh port T5 thật hoặc outbox đã triển khai.
- Unit test không khởi động DB; MySQL integration chỉ chạy sau khi owner cho phép môi trường test và migration sẵn sàng.
- Không chạy DB/Docker chung, Flyway repair/clean hoặc thay dữ liệu thật để chuẩn bị test.

## 2. Ma trận test

Ma trận giữ đủ 27 nhóm kiểm thử. **Kịch bản đầy đủ ở từng hàng chưa được thực thi (NOT_RUN)**; một số điều kiện con đã được kiểm thử bằng hai policy, được đối chiếu riêng bên dưới. Cột tầng là kế hoạch kiểm chứng sau khi có implementation và dependency tương ứng, không phải xác nhận test đó đã tồn tại. Các ca persistence/delivery/Task service cần phân công và tích hợp riêng, không phải đề nghị DEV05 tự xây dựng toàn bộ runtime.

| ID | Tầng | Tình huống | Expected / dependency |
|---|---|---|---|
| C01 | Contract/unit | Event đúng shape BC41, nguồn APPROVED đúng scope | Nhận Case WAITING_START; không tạo Task hoặc Placement |
| C02 | Contract/unit | Thiếu field, field lạ, UUID/version/timestamp sai, schemaVersion khác 1, enum lạ | Từ chối dữ liệu sai theo C.0/BC41; không ghi Case; representation lỗi khi eventId không hợp lệ cần thiết kế adapter thống nhất |
| C03 | Unit | aggregateVersion khác placementVersion | REJECT/INVALID_SCHEMA; không có mutation |
| C04 | Unit/integration | school/enterprise/term/student/placement ID không khớp nguồn | REJECT theo BC41; không rò dữ liệu scope khác |
| C05 | Unit/integration | Context không trusted hoặc producer không được phép | Không áp dụng mutation; mapping lỗi theo D02/D06, không tự đặt mã mới |
| S01 | Unit/service | Nguồn APPROVED dù effectiveAt đã qua | Task/Submission bị từ chối; không tự activate Placement |
| S02 | Unit/service | Nguồn ACTIVE, Case IN_PROGRESS, effectiveAt tương lai | Task/Submission bị từ chối; outcome consumer theo D06 |
| S03 | Unit/service | ACTIVE, Case IN_PROGRESS, effectiveAt đúng now hoặc trước now; đủ quyền/scope | Qua gate trạng thái; đây chưa phải bằng chứng mọi điều kiện tạo Task đều hợp lệ |
| S04 | Unit/integration | Activated v3 tới trước Approved v2, nguồn ACTIVE v3 có approval hợp lệ | Một Case IN_PROGRESS, lastAppliedVersion=3; v2 sau đó ACK_STALE |
| S05 | Unit/service | Local Case đang IN_PROGRESS nhưng nguồn đã CANCELLED v4 | Guard đọc lại nguồn, deny mutation, không có Task/Submission mới |
| S06 | Unit/integration | Case terminal nhận event ACTIVE cũ | Không mở lại Case; không cho mutation mới |
| S07 | Unit/integration | Nguồn mới hơn event, gồm ACTIVE/APPROVED/CANCELLED/COMPLETED | Áp dụng nguồn authoritative; version không lùi; mapping chưa rõ chờ D06 |
| S08 | Unit | Nguồn PENDING_APPROVAL hoặc ACTIVE nhưng thiếu bằng chứng approval hợp lệ | Không cho Task; tiêu chí proof và kết quả consumer cần owner xác nhận |
| I01 | Unit/MySQL | Nhận lại cùng eventId | ACK_DUPLICATE khi đúng trường hợp BC41; không thêm Case hoặc lặp audit mutation |
| I02 | MySQL | Khác eventId nhưng cùng aggregateId/version/eventType | Dedup bảo vệ theo tuple; mapping kết quả cụ thể chốt cùng D03/D06 |
| I03 | Unit/MySQL | Event version thấp hơn lastAppliedVersion | ACK_STALE, không hạ version hoặc trạng thái |
| I04 | MySQL | Hai consumer đồng thời nhận cùng event/Placement | Không nhân bản Case; UNIQUE placement_id và dedup được bảo vệ; chỉ một mutation áp dụng |
| I05 | MySQL | Audit hoặc ghi Case thất bại giữa transaction | Inbox/Case/audit rollback đồng bộ; không ACK_APPLIED, retry sau đó vẫn xử lý được |
| I06 | MySQL | Crash sau commit nhưng trước ACK, gửi lại | Không áp dụng mutation lần hai; dữ liệu và audit giữ đúng một lần |
| E01 | Unit/integration | Nguồn không tồn tại | REJECT/SOURCE_NOT_FOUND; không tạo Case |
| E02 | Unit/integration | Nguồn tạm không sẵn sàng | RETRY/SOURCE_UNAVAILABLE; không đóng băng success; retryAfterSeconds theo delivery policy đã thống nhất |
| E03 | Unit/integration | Source version thấp hơn event, hoặc event vừa duplicate vừa stale/nguồn lỗi | Expected chi tiết chờ D06; không tự chọn thứ tự ưu tiên |
| E04 | Delivery integration | Lỗi liên tiếp, hết retry và Ops reprocess | Lịch 1/5/30/300/1800 giây và DLQ theo BC41; cách đếm chờ D08; reprocess đọc lại nguồn |
| A01 | Service/integration | User đúng role nhưng sai org/resource scope/assignment; tài khoản không hợp lệ | Mutation bị từ chối; không chỉ test nút UI; phụ thuộc guard/context thật |
| A02 | Service/integration | Nguồn bị cancel đồng thời với Task mutation | Không vượt invariant; chiến lược lock/version phải chốt ở D03 trước viết oracle cụ thể |
| O01 | Review/unit/integration | Consumer nhận mọi event được hỗ trợ | Không gọi mutation/repository T5; chỉ đọc Placement; integration kiểm không thay bảng T5 |
| O02 | Integration | Xử lý PlacementCancelled | Không phát CaseClosed ngược gây vòng lặp; terminal Case không mở lại |

### Đối chiếu bằng chứng policy với ma trận

| Nhóm | Bằng chứng DEV hiện có | Chưa được chứng minh |
|---|---|---|
| C02, C03 | Tại f28a318: policy và constructor test PASS cho required-null, UUID, type/schema/version/equality, timestamp non-null, Reason/Ref. Sau refactor contract giữ rule thuần và policy T3 delegate; kiểm hồi quy tại mục 3 | Wire parser/field lạ/nullable field bị bỏ, kiểu số/timestamp wire; adapter map REJECT/INVALID_SCHEMA và không ghi Case. Constructor ném IllegalArgumentException trước delivery, không tự tạo result |
| S01–S03 | Policy trạng thái/thời gian đã PASS, gồm APPROVED, ACTIVE, Case IN_PROGRESS và effectiveAt trước/đúng/sau now | Đọc nguồn T5, authorization và Task/Submission service gọi guard thật |
| S05, S06, S08 | Policy từ chối các giá trị Placement/Case không đủ điều kiện, gồm CANCELLED, Case terminal và PENDING_APPROVAL | Đọc lại nguồn, không mở lại Case, race và bằng chứng approval cho ACTIVE; policy không nhận approval proof |
| C01, C04, C05, S04, S07 | Có test chữ ký/truyền fixture BC41 qua interface và validation syntax context; chưa kiểm nhận nguồn/chuyển Case hoặc trust/scope thật | Canonical types/port đã có; cần adapter xác minh T5, provider Placement T5, implementation consumer và nối Core đã bàn giao |
| I01–I06, E01–E04, A01, A02, O02 | NOT_RUN; không thuộc bằng chứng của hai policy | Inbox/transaction, retry/DLQ, nguồn T5, service guard và phối hợp owner |
| O01 | Review chín file Java T3-01 tại f28a318: không gọi mutation/repository T5 | Chưa có consumer hoặc integration để chứng minh luồng tiếp nhận không thay dữ liệu T5 |

## 3. Kết quả DEV đã ghi nhận và giới hạn

Lệnh DEV của milestone f28a318 đã ghi nhận:

```powershell
.\mvnw.cmd '-Dtest=InternshipActivityPolicyTest,PlacementEventEnvelopePolicyTest,PlacementLifecycleContractTest' test
```

| Test class | Tests / PASS | Failures | Errors | Skipped |
|---|---|---|---|---|
| InternshipActivityPolicyTest | 41 / 41 | 0 | 0 | 0 |
| PlacementEventEnvelopePolicyTest | 58 / 58 | 0 | 0 | 0 |
| PlacementLifecycleContractTest | 174 / 174 | 0 | 0 | 0 |
| Tổng T3 tại f28a318 | 273 / 273 | 0 | 0 | 0 |

Kết quả lượt DEV milestone: **BUILD SUCCESS**, 0 failures/errors/skipped. Báo cáo Lead cho f28a318 có lệnh `.\mvnw.cmd test`: 301 PASS gồm 273 T3 + 28 Core. Đây là evidence của Lead trên bản sao, tách biệt lượt DEV milestone và lượt chạy sau refactor.

Hồi quy DEV working tree sau f28a318 đã thực thi ngày 07/11/2026, kết thúc 10:16:22 +07:00: `.\mvnw.cmd test` — **301 PASS, 0 failures, 0 errors, 0 skipped, BUILD SUCCESS**. Gồm 41 activity + 58 envelope + 174 contract/value + 28 Core; đây là lượt DEV mới sau refactor, tách biệt báo cáo Lead trên milestone. Phạm vi sửa đúng event, policy envelope, contract test và hai tài liệu; giữ nguyên wire shape, nullable/int v1.0 và constructor exception.

Kiểm độc lập dependency: biên dịch chỉ ba record bằng javac --release 21, classpath/sourcepath trống, output dưới target/contract-isolation/classes — PASS (exit 0). Không đưa source/class T3 hoặc dependency framework vào lệnh. Shared contract không còn import modules.*. Maven dùng JDK 24.0.1, compiler target release 21; chưa coi đây là lượt chạy local bằng JDK 21 hoặc CI của refactor.

Report local của lượt DEV, không phải artifact CI đã công bố:

- `target/surefire-reports/vn.utbridora.auth.modules.internship.domain.InternshipActivityPolicyTest.txt`
- `target/surefire-reports/vn.utbridora.auth.modules.internship.domain.PlacementEventEnvelopePolicyTest.txt`
- `target/surefire-reports/vn.utbridora.auth.modules.internship.integration.PlacementLifecycleContractTest.txt`

Các report local không nằm trong danh sách file đề nghị commit. Reviewer có thể chạy lại lệnh trên tại commit tương ứng. Không đưa raw report chứa thông tin môi trường vào tài liệu review.

| Bằng chứng | Chứng minh được | Không chứng minh được |
|---|---|---|
| Hai policy và 99 unit test đã chạy | Điều kiện trạng thái/thời gian và bốn tham số envelope | Nhận event thật, đọc nguồn, authorization, cập nhật Case hoặc Task guard thực tế |
| 174 contract/value test đã chạy tại f28a318 | Constructor validation, 15/3/5 component, nullable/type, fixture và chữ ký consume; event type không thay trạng thái authoritative | Wire parser/unknown fields/trust và delivery error mapping; test double trả ACK fixture không chứng minh runtime xử lý event |
| MySQL integration — NOT_RUN | Khi thực thi: constraint, transaction/rollback, concurrency trong harness | Delivery production hoặc permission chưa gắn vào service |
| T3–T5 integration — NOT_RUN | Khi thực thi: adapter authoritative và guards trong phạm vi test | Nghiệm thu tự động toàn bộ hệ thống |
| GitHub CI tại f28a318 | verify SUCCESS; contracts FAILURE (run dẫn bên dưới) | CI tổng thể PASS, QA03 signoff hoặc kết quả cho working tree chưa push |

Lệnh integration dự kiến, chỉ sau khi implementation, MySQL Testcontainers, migration và adapter cần thiết sẵn sàng trên môi trường được phép:

```powershell
.\mvnw.cmd verify
```

Chưa chạy integration trong phạm vi bằng chứng này. Không thêm dependency hoặc sửa lỗi baseline ngoài task. Report integration chỉ được dẫn khi thực sự sinh từ lượt chạy tương ứng; không coi evidence T1-01 trước đây là kết quả T3-01.

CI của f28a318: [PR run 42330544752](https://github.com/NguyenThanhTiep/utbridora/actions/runs/42330544752), [push run 42330538676](https://github.com/NguyenThanhTiep/utbridora/actions/runs/42330538676); verify SUCCESS, contracts FAILURE. Builder dùng sorted(Path), khác thứ tự Windows/POSIX; manifest thiếu đúng sáu production source (ba record, hai policy, interface), source cũ không đổi hash tại milestone. Sau refactor, hai source đã sửa phải có hash mới khi đồng bộ. Chờ T1 xác nhận cách đồng bộ manifest; không áp patch, sửa builder, chạy generator hoặc đổi bundle trong lượt này. Chưa tuyên bố contracts/CI tổng thể PASS.

Retry result: test hiện giữ Integer nullable, kể cả số âm theo int|null v1.0; schema v1.1 có minimum:0. Không đổi test/contract để tự áp v1.1. Runner thực tế phải tuân lịch retry BC41 và không dùng số âm để hẹn delivery.

## 4. Bàn giao QA03

Report sau triển khai phải ghi commit SHA/branch/PR nếu có, contract và hash, IDs test đã chạy, lệnh, số test/pass/fail/skip, report path, môi trường, fixture, blocker và cách tái hiện lỗi. Phân biệt gate trạng thái đơn lẻ với việc service Task thật gọi guard và không ghi dữ liệu khi bị deny.

QA03 hoặc người được phân quyền xác nhận QA; DEV không tự đánh dấu QA PASS. CI phải đọc run đúng commit. Các ca bị chặn phải ghi BLOCKED với dependency cụ thể khi bước chạy được yêu cầu; 27 kịch bản đầy đủ vẫn là kế hoạch chưa chạy. Không ghi chung là T1 chưa bàn giao. Acceptance I11 về tiếp nhận và Task guard runtime chưa được xác nhận; số test policy/value không chứng minh T3-01 DONE.

## 5. Service port đã có và phạm vi kiểm hồi quy

Package `vn.utbridora.auth.contracts.placement` chứa ba record do DEV05 cung cấp theo xác nhận Lead/T1 và T5; interface ở `vn.utbridora.auth.modules.internship.integration`. Đã có source tại f28a318, không chờ tạo lại. Mapping và giới hạn tích hợp tại mục 2/3 của [thiết kế](../reviews/T3_01_PLACEMENT_INBOX_DESIGN.md).

Refactor giữ rule type/schema/version ở PlacementLifecycleEvent.hasSupportedTypeAndVersions; constructor và policy T3 sử dụng cùng rule. Test hiện có được bổ sung kiểm trực tiếp rule canonical: bốn event type ở version 0/1/MAX, schema thiếu/sai, version thiếu/âm/vượt MAX/khác nhau; vẫn kiểm constructor reject trước delivery. Bộ 58 policy test kiểm hành vi public cũ sau delegate. Bộ activity 41 test giữ điều kiện IN_PROGRESS/ACTIVE/effectiveAt, không suy state từ event type. Không tạo consumer runtime hoặc test giả Case đã cập nhật.

1. Kiểm rule shared và cả ba lớp test T3, sau đó toàn bộ unit suite; kiểm contract package không import modules.* và có thể biên dịch bằng JDK độc lập source T3.
2. Kiểm diff đúng năm file đã duyệt, không đổi record component, enum, nullable/retry constraint, contract hoặc Core. Không mở rộng consumer, persistence, API hoặc migration.
3. Runtime T3 sẽ nối Core đã bàn giao theo phân công riêng: AuditService.write/writeScoped và guard/context theo API hiện hữu. Các điểm adapter trusted/inbox/dedup/action Case chưa xác định locator tại f28a318/0cc1daf phải được báo cụ thể; không tự quy thành T1 chưa bàn giao.
4. Provider PlacementReadPort.readForInternship/readCurrentForGuard và PlacementView T5 chưa tìm thấy tại các refs đã kiểm, gồm T5 c299c4f. Ca đọc nguồn, stale/duplicate, Case transaction chưa có implementation để kiểm; Task guard thuộc DEV06. QA03/QA05 phối hợp kiểm I11 sau tích hợp, không giả bằng ACK hoặc state hard-code.
