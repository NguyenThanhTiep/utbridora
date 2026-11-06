# T3-01 — Kế hoạch kiểm thử tiếp nhận Placement

Ngày: 06/11/2026. DEV05 thực hiện self-test; QA03 xác nhận QA.

**PROPOSAL — PENDING CROSS-TEAM REVIEW**

Đã có hai policy thuần tại commit `3eaa52c1c18ddf1a6bdfdf859d8dcfc76d1ba011` (`3eaa52c`) với 99 unit test PASS trong lượt DEV đã ghi nhận. Java service port, ba kiểu canonical và consumer runtime chưa triển khai. Kết quả policy không đồng nghĩa 27 nhóm acceptance/integration dưới đây đã PASS; T3-01 chưa DONE, chưa có QA signoff hoặc kết quả GitHub CI được xác minh.

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
| C02, C03 | Policy envelope đã PASS cho event type, schemaVersion, khoảng version, equality và đầu vào thiếu/sai của bốn tham số này | Shape đầy đủ, UUID/timestamp, field lạ, kết quả REJECT/INVALID_SCHEMA và không ghi Case qua consumer |
| S01–S03 | Policy trạng thái/thời gian đã PASS, gồm APPROVED, ACTIVE, Case IN_PROGRESS và effectiveAt trước/đúng/sau now | Đọc nguồn T5, authorization và Task/Submission service gọi guard thật |
| S05, S06, S08 | Policy từ chối các giá trị Placement/Case không đủ điều kiện, gồm CANCELLED, Case terminal và PENDING_APPROVAL | Đọc lại nguồn, không mở lại Case, race và bằng chứng approval cho ACTIVE; policy không nhận approval proof |
| C01, C04, C05, S04, S07 | Chưa có test tiếp nhận/chuyển trạng thái hoặc kiểm trust/scope | Cần canonical types, Java port và runtime tương ứng |
| I01–I06, E01–E04, A01, A02, O02 | NOT_RUN; không thuộc bằng chứng của hai policy | Inbox/transaction, retry/DLQ, nguồn T5, service guard và phối hợp owner |
| O01 | Review bốn file Java tại 3eaa52c: policy thuần không gọi mutation/repository T5 | Chưa có consumer hoặc integration để chứng minh luồng tiếp nhận không thay dữ liệu T5 |

## 3. Kết quả DEV đã ghi nhận và giới hạn

Lệnh của lượt kiểm thử hai policy đã ghi nhận:

```powershell
.\mvnw.cmd '-Dtest=InternshipActivityPolicyTest,PlacementEventEnvelopePolicyTest' test
```

| Test class | Tests / PASS | Failures | Errors | Skipped |
|---|---|---|---|---|
| InternshipActivityPolicyTest | 41 / 41 | 0 | 0 | 0 |
| PlacementEventEnvelopePolicyTest | 58 / 58 | 0 | 0 | 0 |
| Tổng | 99 / 99 | 0 | 0 | 0 |

Kết quả lượt DEV: **BUILD SUCCESS**. Source đối chiếu: commit `3eaa52c`. Phiên cập nhật tài liệu này không chạy lại Maven; đây là kết quả kiểm thử đã có, không phải kết quả mới hoặc GitHub CI.

Report local của lượt DEV, không phải artifact CI đã công bố:

- `target/surefire-reports/vn.utbridora.auth.modules.internship.domain.InternshipActivityPolicyTest.txt`
- `target/surefire-reports/vn.utbridora.auth.modules.internship.domain.PlacementEventEnvelopePolicyTest.txt`

Các report local không nằm trong danh sách file đề nghị commit. Reviewer có thể chạy lại lệnh trên tại commit tương ứng. Không đưa raw report chứa thông tin môi trường vào tài liệu review.

| Bằng chứng | Chứng minh được | Không chứng minh được |
|---|---|---|
| Hai policy và 99 unit test đã chạy | Điều kiện trạng thái/thời gian và bốn tham số envelope | Nhận event thật, full DTO validation, đọc nguồn, authorization, cập nhật Case hoặc Task guard thực tế |
| Contract test với test double — chưa triển khai | Sau khi được duyệt: chữ ký Java, mapping dữ liệu và lời gọi qua interface | Test double trả sẵn ACK/trạng thái không chứng minh runtime đã xử lý event |
| MySQL integration — NOT_RUN | Khi thực thi: constraint, transaction/rollback, concurrency trong harness | Delivery production hoặc permission chưa gắn vào service |
| T3–T5 integration — NOT_RUN | Khi thực thi: adapter authoritative và guards trong phạm vi test | Nghiệm thu tự động toàn bộ hệ thống |
| GitHub CI — chưa xác minh | Kết quả job của đúng commit khi có evidence | QA03 signoff |

Lệnh integration dự kiến, chỉ sau khi implementation, MySQL Testcontainers, migration và adapter cần thiết sẵn sàng trên môi trường được phép:

```powershell
.\mvnw.cmd verify
```

Chưa chạy integration trong phạm vi bằng chứng này. Không thêm dependency hoặc sửa lỗi baseline ngoài task. Report integration chỉ được dẫn khi thực sự sinh từ lượt chạy tương ứng; không coi evidence T1-01 trước đây là kết quả T3-01.

## 4. Bàn giao QA03

Report sau triển khai phải ghi commit SHA/branch/PR nếu có, contract và hash, IDs test đã chạy, lệnh, số test/pass/fail/skip, report path, môi trường, fixture, blocker và cách tái hiện lỗi. Phân biệt gate trạng thái đơn lẻ với việc service Task thật gọi guard và không ghi dữ liệu khi bị deny.

QA03 hoặc người được phân quyền xác nhận QA; DEV không tự đánh dấu QA PASS. CI phải đọc run đúng commit. Các ca bị chặn phải ghi BLOCKED với dependency thực tế khi bước chạy được yêu cầu; 27 kịch bản đầy đủ hiện vẫn là kế hoạch chưa chạy. Acceptance I11 về tiếp nhận và Task guard runtime chưa được xác nhận, không đánh dấu T3-01 DONE từ 99 unit test.

## 5. Kiểm thử phần service port được đề xuất

Theo mục 3 và 8 của [thiết kế](../reviews/T3_01_PLACEMENT_INBOX_DESIGN.md), package canonical đề xuất là `vn.utbridora.auth.contracts.placement` cho PlacementLifecycleEvent, TrustedEventContext, EventConsumeResult; interface PlacementLifecycleConsumer dự kiến ở `vn.utbridora.auth.modules.internship.integration`. Tất cả đang chờ Lead/T1/T5 thống nhất vị trí, người cung cấp và mapping Java; chưa tạo các file này.

Sau khi được duyệt, contract test tối thiểu dự kiến kiểm chữ ký `consume(TrustedEventContext, PlacementLifecycleEvent): EventConsumeResult`, field/type/nullability theo BC41 v1.0 và truyền dữ liệu qua test double. Tái sử dụng hai policy hiện có để kiểm điều kiện envelope/trạng thái/thời gian; không tạo loại dùng chung thứ hai trong test. Record hoặc chữ ký interface không tự validate, xác thực context hoặc cập nhật Case; nếu cần validation mới phải duyệt rõ phạm vi và test trước khi code.

1. Lead/T1/T5 giải quyết D01 và xác nhận ba kiểu canonical có tồn tại ở nhánh/PR khác hay không; Lead làm rõ phạm vi I11/D07.
2. DEV05 duyệt danh sách file source/test cụ thể sau xác nhận canonical. Không tự mở rộng sang consumer, persistence, API hoặc migration.
3. Runtime cần owner giải quyết D02–D06/D08 và có implementation để kiểm các kịch bản đầy đủ; Task service/guard thuộc DEV06 theo T3-02/T3-05, không giả bằng ACK hoặc trạng thái hard-code trong test.
4. Hạ tầng T1/T5 và migration được cấp phải sẵn sàng trước integration. QA03 phối hợp owner và QA05 để kiểm I11, retry/rollback/race; 99 unit test chỉ là bằng chứng DEV của hai policy.
