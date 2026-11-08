# Kiểm thử Nhu cầu — khôi phục ngày 05/11/2026

## Hoàn thiện bàn giao T5-02 / T5-03 — 06/11/2026

Phạm vi được người dùng xác nhận: đảm nhiệm cả Dev11/T5-02 và Dev12/T5-03 tuần 1. Khung nhà trường hiển thị trạng thái chờ backend đúng yêu cầu T5-03, không coi các workflow tuần sau là phần thiếu phải tự triển khai.

Đã bổ sung defensive copy cho collection của `DemandCriteriaSnapshot`, bao gồm khi deserialize từ revision. Test `consumerReceivesImmutableVersionedSnapshotThroughFacade` gọi facade thật với principal/session context phía server: kiểm trường DTO BC31, collection không sửa được, bản đã nhận giữ nguyên sau update, version cũ trả CRITERIA_STALE và bản mới có hash/version mới. Đây là test bên gọi của provider T5, chưa phải consumer Matching T4 thật hoặc shared ActorContext T1.

Lệnh `.\mvnw.cmd '-Dit.test=DemandTaxonomyIT' verify`: **57 unit PASS; 20 integration case, 19 PASS/1 SKIP**, không lỗi. Case SKIP là browser harness do không bật `demand.e2e`; kết quả browser trước đó ghi ở mục tiếp theo, không gộp thành lượt này. Log: `target/t5-02-snapshot-handoff.log`.

Fixture Opportunity BC22 đã có tại `docs/contracts/fixtures/t5-opportunity-week1.json`; kiểm JSON, tập field, opportunityId=cooperationId và publication window đạt. Chưa chạy nghiệp vụ publish/list Opportunity vì tuần 1 chỉ bàn giao thiết kế/fixture. OpenAPI Demand đã cập nhật feature gate và error envelope theo implementation.

## Cập nhật 06/11/2026 — chia module FE và ghép kỳ Dev10

Đã đọc TEAM_IMPLEMENTATION_GUIDE và tách `DemandWorkspace` thành scope, danh sách, editor, nhóm kỹ năng, chuyển đổi form và thông báo lỗi. `App.tsx` dùng public entry point của T5. Xem [README module](../../web-demo/src/modules/cooperation/README.md).

- Frontend build PASS; 15/15 test `demand.spec.ts` PASS sau refactor, gồm chống mất dữ liệu, scope, retry, quyền, lỗi field và mobile. Đây là browser test dùng HTTP mock.
- Maven unit: 57 PASS.
- `DemandIT,DemandTaxonomyIT,MigrationUpgradeIT`: 32 case, 30 PASS, 2 SKIP, không lỗi. Hai case SKIP là browser harness vì lượt này chưa bật `demand.e2e`. Log: `target/dev11-term-integration.log`.
- `DemandTaxonomyIT` đã đổi từ mock toàn bộ academic source sang **bảng kỹ năng T4 và bảng kỳ Dev10 thật**, chỉ mock `DemandMajorCatalogPort` của T2. Thêm ca kỳ thiếu/DRAFT/CLOSED/sai trường, catalog sai scope/version/mã ngành và kỳ đóng sau khi tạo Demand; edit/snapshot bị chặn nhưng revision cũ được giữ.
- Migration upgrade V2→V11 PASS trên MySQL Testcontainers riêng, giữ các assertion dữ liệu/constraint/idempotence hiện có. Không áp dụng V11 lên DB phát triển của người dùng.
- Lượt riêng `DemandTaxonomyIT#browserUsesRealDemandApiAndDatabase -Ddemand.e2e=true`: 1 PASS, kiểm đăng nhập → tạo → tải lại → sửa → tải lại qua FE đã tách module, Spring và MySQL thật. Taxonomy/kỳ thật; catalog ngành vẫn fixture T2. Log: `target/dev11-term-browser.log`, `target/demand-live-e2e.log`. Case này là một trong hai harness bị SKIP ở lượt trên, không phải test mới ngoài suite.

Chưa xác nhận hoàn thành toàn bộ Dev11: thiếu nguồn ngành T2 thực tế, consumer/context BC31 dùng chung và review/QA liên team. Source và migration lấy từ Dev10 không đồng nghĩa đã nhập dữ liệu kỳ vào môi trường đang chạy. Các kết quả ở các mục sau là lịch sử, không thay thế kết quả của source mới.

## Cập nhật ghép source T4

Xem [bàn giao Dev11–T4](../guides/DEV11_T4_INTEGRATION_HANDOFF.md): V9 taxonomy + V10 metadata, kỹ năng canonical thật, academic fixture riêng trong test. Error envelope filter và metadata Demand đã bổ sung. Lượt cuối có 57 unit PASS; integration chính 76 PASS/1 SKIP, không lỗi; frontend build và 15 browser mock PASS. Core browser harness bị skip, không phải toàn bộ core chưa chạy. Lượt cuối riêng browser JAVA/T4 và security HTTP: 2 PASS. Các kết quả dưới đây là lịch sử trước tích hợp này.

## Cập nhật Dev12 sau review T1-01

Build frontend PASS; **14/14 browser tests `demand.spec.ts` PASS**. Thêm 5 case: mất phiên khi lưu (đóng editor, không lưu draft vào browser storage), xác nhận bỏ thay đổi, structured CSRF_INVALID, structured ACCESS_DENIED với requestId từ body, chặn đổi org khi save đang pending và không hiển thị dữ liệu org trước sau khi đổi thành công.

Frontend ưu tiên errorCode, fallback error legacy; thông báo feature gate không còn kết luận v1.0 chưa chốt. 403 legacy chưa phân biệt được quyền/CSRF vẫn dùng thông báo kết hợp. Backend security envelope chưa được sửa trong lượt Dev12 này; các test lỗi mới dùng API mock, không chứng minh backend đã trả envelope mới. Không chạy lại Java/backend integration trong lượt chỉ thay frontend này. Evidence browser: `web-demo/test-results/e2e.xml`.

Tên tổ chức và danh mục chọn từ API thật vẫn chờ nguồn được phân quyền. Test mất phiên chứng minh dữ liệu không lọt sang màn hình đăng nhập, không phải khôi phục draft sau đăng nhập. Hành vi mất phiên giữ cơ chế xóa trạng thái nhạy cảm hiện có.

## Cập nhật sau hoàn thiện Dev11/Dev12 tuần 1

- Build frontend: PASS.
- Unit Nhu cầu: 24 PASS (12 domain, 9 service, 3 reference configuration). Ba test configuration được chạy riêng sau lượt verify.
- DemandIT: 12 PASS khi bật `-Ddemand.e2e=true`: gồm 11 test API/DB/port và 1 harness browser thật. Đã thêm expiry replay, filter/pagination/soft-delete.
- Browser thật: 1 PASS, đã tính trong harness ở trên; create/reload/edit/reload qua Vite → Spring → MySQL Testcontainers, không mock HTTP. Reference catalogs vẫn dùng adapter demo.
- Browser API giả lập: 9 PASS, thêm chặn mất dữ liệu/chuyển scope và khung nhà trường; kiểm tra field error/focus.
- 28 core unit test PASS cùng lượt verify. Chưa chạy lại toàn bộ core integration/E2E trong lượt này.

Lệnh: `.\mvnw.cmd '-Dit.test=DemandIT' '-Ddemand.e2e=true' verify`; `.\mvnw.cmd '-Dtest=DemandReferenceConfigurationTest' test`; `npx.cmd playwright test tests/demand.spec.ts`; `npm.cmd run build`.

Evidence cập nhật: `target/demand-week1-verify.log`, `target/demand-live-e2e.log`, Surefire/Failsafe XML. Playwright JUnit output bị ghi lại theo lượt chạy, dùng log harness để phân biệt browser thật và mock. Xem [cách chạy local và bàn giao](../guides/T5_DEMAND_HANDOFF.md).

Các mục bên dưới ghi lại lượt khôi phục trước cải tiến này. Hạn chế về chưa có browser nối backend, chưa test expiry/filter/soft-delete đã được xử lý ở lượt cập nhật; danh mục authoritative và nghiệm thu liên team vẫn còn chờ.

Theo yêu cầu người dùng, viết lại các file test đã xóa theo source hiện tại. Không khẳng định khôi phục nguyên văn bộ test buổi sáng hoặc giữ nguyên số lượng test lịch sử.

## Phạm vi

| File | Số test | Nội dung |
|---|---:|---|
| `DemandDomainTest.java` | 12 | Schema chặt chẽ, quantity, ngày, kỹ năng trùng giữa hai nhóm, collection bất biến, hash tiêu chí |
| `DemandServiceTest.java` | 9 | Phiên/quyền, contract gate, tạo/sửa, replay, stale version, dependency failure; repository và reference được mock |
| `DemandIT.java` | 9 | HTTP thật, cookie/CSRF thật, MySQL 8.4/Flyway riêng: tạo–đọc–sửa, immutable revision, snapshot port, idempotency, cross-org, stale/locked draft, catalog rejection rollback, audit rollback, mất quyền khi org chưa verified, sửa đồng thời |
| `web-demo/tests/demand.spec.ts` | 7 | Ẩn menu khi thiếu grant, contract gate, tạo/sửa, retry giữ key/payload, lỗi field, conflict, viewport mobile; API giả lập |

Mỗi test có thể chứa nhiều assertion. Test không tạo tài khoản hoặc đổi trạng thái tổ chức trong database phát triển. Audit fault injection chỉ áp dụng container test, giới hạn theo actor và bỏ constraint trong `finally`.

## Chạy lại

Kết quả lượt cuối: **PASS** — 21 unit Nhu cầu + 9 integration Nhu cầu + 7 browser Nhu cầu = **37 test Nhu cầu**. Maven còn chạy 28 core unit tests, tất cả PASS (49 unit tổng cộng). Không có test bị skip. Hai lỗi fixture/selector ở lượt đầu đã sửa và chạy lại thành công; không có sửa production code trong lượt khôi phục này.

JDK hỗ trợ compiler release 21; Docker Linux engine cho integration test:

```powershell
.\mvnw.cmd '-Dit.test=DemandIT' verify
cd web-demo
npx.cmd playwright test tests/demand.spec.ts
```

Playwright cần dependencies và Chromium đã cài; cấu hình tự mở Vite riêng cổng 5178. Java lượt này dùng JDK 25.0.4.1, target release 21.

## Mức độ đầy đủ

Bộ test bao phủ luồng chính và các rủi ro quan trọng của bản nháp tuần 1. Chưa đủ để nghiệm thu tích hợp toàn hệ thống:

- Chưa có browser test Nhu cầu nối backend/database thật; browser tests dùng route mocks.
- DefaultDemandReferenceAdapter chỉ kiểm mã ngành/kỹ năng trong danh sách tĩnh. PASS không chứng minh trường/kỳ/catalog version được xác minh từ T2/T4/T5 thật.
- Contract gate và dependency unavailable được kiểm ở unit service; chưa phục hồi hai Spring context test riêng cho cấu hình gate/reference như bộ cũ.
- Chưa bao phủ đầy đủ expiry idempotency 24 giờ, soft-delete, pagination/filter và mọi biên dữ liệu/concurrency.
- Chưa chạy lại CorePlatformIT, MigrationUpgradeIT hoặc core browser E2E trong lượt này; core unit tests có chạy cùng Maven verify.
- Không đánh dấu contract CP-T5UI1/BC31 đã phê duyệt dựa trên test. Integration test bật contract bằng cấu hình test riêng.

Evidence mới: `target/demand-restored-verify.log`, `target/surefire-reports`, `target/failsafe-reports/TEST-vn.utbridora.auth.DemandIT.xml`, `web-demo/test-results/e2e.xml`. JSON evidence cũ là lịch sử trước khi xóa test.
