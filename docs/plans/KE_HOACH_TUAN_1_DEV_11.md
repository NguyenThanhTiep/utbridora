# Kế hoạch tuần 1 — Dev 11 / T5

Ngày lập: **04/11/2026**. Trạng thái: **kế hoạch đề xuất để thực hiện**, chưa phải báo cáo hoàn thành.

**Cập nhật 05/11/2026:** đã triển khai bản code candidate cho Demand DRAFT, criteria, version/snapshot, audit/history, idempotency và FE thô. Người dùng chọn nhập đủ schema khi lưu nháp; signoff liên team chưa xác nhận nên runtime mặc định giữ gate chờ. Xem [bàn giao implementation](../guides/T5_DEMAND_HANDOFF.md) và [kết quả kiểm thử](../testing/T5_DEMAND_TEST_REPORT.md). Các checkbox nghiệm thu dưới đây vẫn cần QA05/Leader đánh giá.

**Điều chỉnh sau triển khai:** đã xóa các test Demand mới theo yêu cầu người dùng; giữ test core. Báo cáo kiểm thử là bằng chứng lịch sử trước khi xóa, checklist dưới đây vẫn là kế hoạch tham chiếu.

**Mục tiêu cuối tuần:** Dev 11 bàn giao phần backend tạo/chỉnh sửa/đọc nhu cầu thực tập ở trạng thái DRAFT, bộ tiêu chí REQUIRED/PREFERRED hợp lệ và snapshot tiêu chí bất biến có phiên bản để T4 sử dụng. Dev 12 dùng API này cho form nhu cầu; QA05 kiểm thử và Leader duyệt kết quả.

## 1. Phân công xác nhận từ tài liệu

Nguồn chính: [UTBridora_Plan.xlsx](UTBridora_Plan.xlsx), sheet **CAP NHAT CHUNG**, hàng **17**.

| Nội dung | Phân công trong workbook |
|---|---|
| Người thực hiện / team / QA | DEV11 / T5 / QA05 |
| Mã công việc | T5-02 |
| Tên công việc | Demand, Required/Preferred criteria và opportunity |
| Phạm vi | Doanh nghiệp khai vị trí, chỉ tiêu, tiêu chí bắt buộc/ưu tiên; lưu dữ liệu theo phiên bản để Matching sử dụng |
| Bàn giao | API nhu cầu, snapshot immutable version |
| Kiểm tra | Tạo nhu cầu, đọc lại tiêu chí đúng phiên bản, T4 nhận dữ liệu |
| Phụ thuộc chính | T4-01 và T1-01 |
| Yêu cầu SRS | DEM-01, DEM-02, DEM-03, DEM-04, DEM-07 |

Sheet **T5 - DOANH NGHIEP**, hàng **6–10**, đều ghi **“Tuần 1 (một phần)”**. Hoàn thành T5-02 không đồng nghĩa nghiệm thu toàn bộ module T5 hoặc toàn bộ các yêu cầu SRS liên quan.

Workbook có khung **5 tuần / 35 ngày**, chưa gắn ngày bắt đầu hoặc chia lịch từng ngày cho Dev 11. Kế hoạch dưới đây đề xuất **5 ngày triển khai chính + 2 ngày QA, sửa lỗi và bàn giao**. Ngày 1–7 là thứ tự công việc, không mặc định là thứ trong tuần hoặc ngày nghỉ phải làm.

## 2. Việc Dev 11 làm trong tuần này

| Hạng mục | Đầu ra cần có |
|---|---|
| Demand DRAFT | API tạo, cập nhật, danh sách có phân trang theo đúng doanh nghiệp; đọc lại được dữ liệu và phiên bản đã lưu |
| Required/Preferred | Validate tiêu chí, lấy mã kỹ năng chuẩn từ T4, không cho trùng mã giữa hai nhóm |
| Version và snapshot | Sửa với expectedVersion; lịch sử phiên bản; snapshot đã cấp cho T4 không bị sửa theo Demand hiện tại |
| Scope và audit | Dùng identity/membership/guard T1; kiểm owner, trạng thái, CSRF; audit và lịch sử thay đổi |
| Bàn giao | Contract API cho Dev 12; contract snapshot cho T4; test và hướng dẫn tái hiện cho QA05 |
| Opportunity | Làm rõ phạm vi và thống nhất hợp đồng với T2/T5; chỉ chuẩn bị thiết kế/fixture trong tuần 1 nếu chưa có Cooperation APPROVED thật |

**Ranh giới trách nhiệm:** Dev 12 phụ trách khung UI doanh nghiệp/nhà trường và form Demand theo T5-03. Dev 11 hỗ trợ DTO, lỗi API và ghép luồng lưu nháp; không nhận toàn bộ frontend. T4 sở hữu thuật toán phân loại, tính điểm và bảo vệ thống kê; Dev 11 cung cấp tiêu chí nguồn chính xác.

**Opportunity cần làm rõ ngay ngày 1:** tên T5-02 có nhắc opportunity, nhưng các dòng SRS tuần 1 chỉ liệt kê DEM-01/02/03/04/07. BC22 đề xuất opportunity là projection từ Cooperation APPROVED đã công bố, `opportunityId = cooperationId`; đây không phải một Demand hoặc bảng ứng tuyển độc lập. Kế hoạch này đề xuất bàn giao thiết kế/fixture opportunity và ghi phụ thuộc; nếu Leader yêu cầu luồng thật ngay tuần 1 thì cần điều chỉnh mốc với owner Cooperation và T2. Preference vẫn do T2 sở hữu.

Cooperation gửi/duyệt/sửa thuộc **T5-04, tuần 2** của Dev 11 (sheet CAP NHAT CHUNG, hàng 34). Invitation, share, Placement, reopen/restore và quy trình nhiều vòng không nằm trong đầu ra tuần này. Lưu nháp không tự tạo Cooperation; không thêm nút “Hoàn tất” riêng.

## 3. Những điểm phải thống nhất đầu tuần

[Trạng thái triển khai](../status/IMPLEMENTATION_STATUS.md) ghi core T1 đã có, migration V1–V7; chưa có module nghiệp vụ T2–T5 trong checkout được đối chiếu. [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx) có phần đề xuất hợp nhất; [Contract Pack](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) còn Candidate. Các route/DTO Candidate dưới đây là mục tiêu cần owner/consumer review, không phải API đã chạy.

| Phối hợp | Cần thống nhất / nhận bàn giao |
|---|---|
| **Dev 01 / T1 — T1-01** | Phiên bản contract, DTO/error, cách chọn org từ membership đã xác thực khi user có nhiều org, permission, route, audit; T1 cấp số migration và review file core dùng chung |
| **Dev 08 / T4 — T4-01** | Catalog kỹ năng, canonical code/alias/taxonomyVersion; hợp đồng đọc criteria cho T4 và consumer test |
| **Dev 10 / T5 — T5-01** | Term/school hợp lệ, dữ liệu tổ chức, trạng thái school verification; không đồng nhất verification theo trường với organizations.status của T1 |
| **Dev 12 / T5 — T5-03** | Form, DTO create/update/list, expectedVersion, lỗi theo field và xử lý 401/403/404/409/422 |
| **Owner T2 + T4** | Mã ngành/catalogVersion cần cho tiêu chí; xác định nguồn dữ liệu hợp lệ, tránh Dev 11 tạo catalog thay team sở hữu |
| **QA05 + Leader T5** | Chốt lát cắt nghiệm thu tuần 1, fixture, test case, cách ghi PASS/FAIL và phạm vi opportunity |

Các quyết định cần ghi thành nội dung review cụ thể:

1. **Gate tạo Demand:** DEM-01 còn nêu D-10 chờ PM/T1 chốt gate xác thực theo trường; CP-T5UI1 Candidate đề xuất org T1 VERIFIED và school/term/catalog hợp lệ khi tạo nháp, còn school verification ở bước gửi hợp tác. Ghi nhận khác biệt này; chưa chốt thì giữ deny cho phần thiếu contract, không tự nâng org hoặc dùng adapter luôn cho phép.
2. **Draft chưa đủ dữ liệu:** chốt trường nào được thiếu khi lưu nháp và lúc nào `validationState = VALID`; schema CP-T5UI1 hiện có nhiều trường bắt buộc. T4 chỉ nhận snapshot hợp lệ, không coi mọi DRAFT là đủ điều kiện matching.
3. **Version:** phân biệt version Demand và criteriaVersion; thống nhất khi nào tăng mỗi loại, cách lưu snapshot/lịch sử và cách trả lỗi khi T4 yêu cầu version cũ. Không âm thầm trả tiêu chí mới thay version được yêu cầu.
4. **Audit:** BC E.3 đề xuất `DEMAND_CHANGED`; enum `AuditAction` hiện tại chưa có mã này. T1 cần review bổ sung registry/metadata phù hợp; không giả định chỉ cần truyền chuỗi mới là chạy được.

Trong lúc chờ các quyết định này, vẫn làm được thiết kế, validator thuần, fixture, unit test và draft PR. Phần dùng mock phải ghi rõ; chưa tính là tích hợp thật hoặc QA PASS liên team.

## 4. Lịch thực hiện từng ngày

| Ngày | Việc chính của Dev 11 | Đầu ra / điều kiện kết thúc ngày |
|---|---|---|
| **1 — Hiểu việc và chốt giao tiếp** | Chạy được môi trường theo guide; đọc DEM-01/02/03/04/07, BC31 và CP-T5UI1; trao đổi các điểm ở mục 3; gửi DTO mẫu sớm cho Dev 12 và T4; mở nhánh `feature/t5-demand-snapshot` | Checklist scope, hợp đồng dự thảo có trạng thái review, danh sách dependency/owner; có fixture tối thiểu cho hai doanh nghiệp và hai trường |
| **2 — Schema và quy tắc dữ liệu** | Thiết kế Demand, criteria, snapshot/history; thêm migration theo số T1 cấp; entity/repository; validator số lượng/ngày/skill/catalog; version và owner bất biến | Migration fresh/upgrade và mapping được kiểm; unit test validator; schema/DTO đủ để Dev 12 xây form và T4 chuẩn bị consumer |
| **3 — API Demand DRAFT** | Làm create/update/list theo scope; lấy actor/org từ ngữ cảnh đã xác thực; kiểm role/membership/org/state/version/CSRF; ghi audit/history trong transaction | Tạo → đọc → sửa nháp hoạt động qua API thật; sai org/version bị chặn; request/response mẫu cho QA05 và Dev 12 |
| **4 — Snapshot bàn giao T4** | Triển khai criteria snapshot/port theo contract đã chấp nhận; taxonomy/catalog version và criteriaHash nếu đã chốt; kiểm snapshot cũ không đổi sau sửa; xử lý version lệch; ghép consumer T4 khi có | T4 đọc đúng tiêu chí/version; có test bất biến và stale version; ghi rõ phần nào là provider/fixture, phần nào đã có consumer thật |
| **5 — Ghép form với Dev 12** | Hỗ trợ UI lưu nháp/đọc lại/sửa; hiển thị lỗi validate và xung đột version; bổ sung HTTP/DB test; rà scope opportunity cùng T2/Dev 10; hoàn thiện PR | Demo form dùng API thật trong phạm vi đã ghép; tài liệu API/snapshot, test matrix và draft PR sẵn để QA kiểm |
| **6 — QA và sửa lỗi** | QA05 chạy case chức năng, cross-org, CSRF, version, snapshot, audit; Dev 11 sửa lỗi; T1 review migration/security/audit và T4 review consumer | Evidence gắn đúng commit; lỗi có kết quả retest; dependency chưa xong ghi rõ owner, tác động và bước tiếp theo |
| **7 — Bàn giao tuần 1** | Demo kịch bản dưới đây; hoàn tất PR review; cập nhật workbook bằng link PR và evidence; ghi đầu việc tuần 2 | DEV xong + QA PASS + test link + PR/evidence + Leader duyệt mới đánh dấu task đạt; SRS nghiệm thu riêng theo workbook |

Nếu nhóm chỉ dành 5 ngày làm việc cho tuần này, cần gom review/QA vào ngày 3–5 và giảm phần opportunity xuống thiết kế; thống nhất lại lịch với QA05 thay vì bỏ các kiểm tra bắt buộc.

## 5. Bản đồ triển khai và hợp đồng dự kiến

Đặt backend trong cấu trúc đề xuất của repository, dưới gốc scan hiện có:

```text
src/main/java/vn/utbridora/auth/modules/cooperation/
  api/           # Controller và request/response DTO
  application/   # Use case Demand, transaction, snapshot
  domain/        # Demand, criteria, state/version policy
  persistence/   # Repository, scoped query, snapshot/history
  integration/   # Port/adapter với T1, T4 và catalog liên quan

src/main/resources/db/migration/
  V{so_T1_cap}__t5_demand_criteria.sql

src/test/java/vn/utbridora/auth/modules/cooperation/
  ...            # Unit, HTTP/DB và consumer contract test
```

Tên package con, bảng và lớp cụ thể là lựa chọn triển khai cần review, chưa phải các file đã có. Dùng lại session, principal, guard và AuditService của T1; giữ migration V1–V7. Không tạo project/backend/database đăng nhập riêng.

**API mục tiêu theo CP-T5UI1 Candidate, chỉ triển khai/tích hợp theo phiên bản được chấp nhận:**

| API / port | Đầu ra tuần 1 |
|---|---|
| `POST /api/v1/enterprise/demands` | Tạo Demand DRAFT, trả 201 DemandDto; không tự gửi hợp tác |
| `PATCH /api/v1/enterprise/demands/{id}` | `{expectedVersion, data}`; chỉ owner sửa DRAFT chưa khóa; trả version mới |
| `GET /api/v1/enterprise/demands` | Danh sách phân trang đúng doanh nghiệp, có dữ liệu/version để đọc lại; filter theo contract |
| `DemandCriteriaPort.read(...)` | T4 lấy DemandCriteriaSnapshot đúng scope/version cho purpose `EVALUATE`; các purpose nomination/Placement là phụ thuộc giai đoạn sau |

CP-T5UI1 trong Candidate chưa liệt kê riêng GET Demand theo ID. Nếu form cần endpoint detail, bổ sung vào bản contract review cùng owner/consumer; không ghi nó là route đã được công bố. Không sửa trực tiếp Candidate đang được SRS tham chiếu checksum.

Snapshot theo BC31 gồm nhóm ID Demand/school/enterprise/term; quantity; required/preferred; demandVersion/criteriaVersion; validationState; criteriaHash; academicCatalogVersion/taxonomyVersion; cooperation reference và capturedAt. DRAFT/READY có tiêu chí VALID được đánh giá trước khi Cooperation APPROVED; matching không tự tạo hợp tác. Browser không gửi bộ tiêu chí tùy ý để thay dữ liệu nguồn của T5.

Quy tắc cần giữ:

- REQUIRED dùng AND; PREFERRED phục vụ ưu tiên và không cứu một REQUIRED thất bại. T4 thực hiện phân loại/score.
- Dùng canonical skill code T4; không tự suy Spring Boot thành Java, không tự tạo taxonomy riêng.
- Theo BC31 Candidate: `majorCodes=[]` nghĩa không giới hạn ngành; `required.skills=[]` không tự làm FAIL kỹ năng, vẫn xét ngành/lịch. Hai nhóm không trùng skillCode; ngưỡng level/years theo schema đã thống nhất.
- Owner enterprise bất biến; school/term và các field không được đổi theo contract. Query theo ID kèm scope, không tin owner/role do browser gửi.
- Mutation theo quy ước CSRF, version và idempotency key của contract được chấp nhận. Giữ `.anyRequest().denyAll()`; thêm matcher chính xác rồi kiểm quyền nghiệp vụ.
- Audit SUCCESS cùng transaction: lỗi ghi audit phải rollback Demand/snapshot/history; không ghi token, secret hoặc toàn bộ request body.

## 6. Checklist kiểm thử giao QA05

Đây là **các ca cần thực hiện**, chưa có kết quả chạy trong kế hoạch này.

| Nhóm | Kịch bản / kết quả cần kiểm |
|---|---|
| Luồng hợp lệ | Doanh nghiệp đúng quyền tạo Demand DRAFT; đọc lại đúng trường, kỳ, số lượng, tiêu chí, version; chưa có Cooperation |
| Input | Chặn quantity không hợp lệ, ngày bắt đầu sau ngày kết thúc, mã ngành/kỹ năng không hợp lệ, trùng Required/Preferred; chốt rõ draft thiếu dữ liệu |
| Criteria | Required/Preferred được giữ đúng loại; alias theo T4; không tự suy diễn Java/Spring Boot; không đổi dữ liệu nguồn theo request matching của browser |
| Quyền | Chưa đăng nhập, sai role/permission, membership hết hiệu lực, account bị khóa hoặc org chưa VERIFIED bị từ chối |
| Cách ly dữ liệu | DN A không đọc/sửa Demand DN B dù đổi ID hoặc org trong request; user nhiều membership không chuyển owner bản ghi; kiểm school/term reference đúng scope |
| CSRF | Request tạo/sửa thiếu hoặc sai CSRF bị chặn; session và CSRF hợp lệ hoạt động |
| State/version | Sửa bản ghi bị khóa hoặc version cũ bị từ chối; hai request cùng expectedVersion không cùng ghi thành công; không mất cập nhật |
| Snapshot | Lấy snapshot v1 → sửa tiêu chí thành v2 → snapshot v1 vẫn nguyên; T4 đọc đúng version hoặc nhận lỗi stale đúng contract, không âm thầm dùng v2 |
| Tích hợp T4 | Consumer nhận DTO/schema/version thực tế; phân biệt test fixture với lần ghép thật; Demand DRAFT/VALID không bị yêu cầu Cooperation APPROVED để evaluate |
| Audit/history | Ghi đúng actor/org/resource/action; lỗi audit rollback mutation; enterprise_org không thay; lịch sử/snapshot cũ còn nguyên |
| Database | Migration mới chạy fresh và upgrade trên MySQL; FK/index/version hợp lệ; dữ liệu identity T1 được giữ |
| UI phối hợp Dev 12 | Lưu/đọc/sửa nháp qua API thật; loading/error/empty và lỗi field/version thể hiện đúng; không báo gửi hợp tác thành công khi mới lưu nháp |

Lệnh kiểm tra backend từ gốc repository khi triển khai:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

`verify` dùng MySQL Testcontainers, cần Docker. `test` không chứng minh HTTP/migration integration đã PASS. Nếu có thay đổi/ghép UI, phối hợp Dev 12 chạy `npm run build` trong `web-demo` và E2E theo [guide §14](../guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md#14-kiểm-thử-và-điều-kiện-hoàn-thành). Ghi test chưa chạy là `NOT RUN` cùng lý do; không dùng số PASS lịch sử làm evidence của tuần này.

## 7. Kịch bản demo và điều kiện hoàn thành

Demo cuối tuần bằng dữ liệu giả lập:

1. Đăng nhập doanh nghiệp A đã có quyền và trạng thái phù hợp.
2. Tạo nhu cầu nháp với REQUIRED Java và SQL, PREFERRED React; lưu và đọc lại đúng dữ liệu/version.
3. T4 đọc snapshot tiêu chí hợp lệ theo contract đã thống nhất.
4. Sửa nháp với version hiện hành; kiểm snapshot trước đó không đổi và consumer phát hiện version lệch đúng quy định.
5. Đổi sang doanh nghiệp B; đọc/sửa nhu cầu A bị từ chối. Thử sửa bằng version cũ cũng bị từ chối.
6. Cho QA xem lịch sử/audit và test report. Việc lưu nháp vẫn chưa tạo/gửi Cooperation hoặc công bố opportunity.

Checklist bàn giao:

- [ ] DEM-01/02/03/04/07 đã được ánh xạ tới phần làm tuần 1 và test tương ứng.
- [ ] API/DTO/permission/error/snapshot có review owner/consumer đúng phiên bản.
- [ ] Migration và thay đổi security/audit được T1 review; build và các test cần thiết có evidence.
- [ ] Dev 12 nhận DTO, ví dụ request/response và cách xử lý lỗi; trạng thái ghép UI được ghi thật.
- [ ] T4 nhận snapshot đúng version; nếu chưa có consumer thật thì ghi dependency, chưa nhận hoàn thành kiểm tra liên team.
- [ ] QA05 ghi PASS/FAIL, link báo cáo và commit; Leader duyệt phần hoàn thành.
- [ ] Workbook cập nhật task T5-02, link PR/code/test; nghiệm thu SRS được đánh giá riêng, không tự đánh dấu toàn bộ T5 hoàn tất.
- [ ] Opportunity và các phần chuyển tuần 2 có owner/mốc tiếp theo rõ ràng.

Nếu bị chậm phụ thuộc, ưu tiên giữ API nháp, validation, scope, version/snapshot và evidence trong lát cắt tuần 1. Đưa phần chưa ghép thật vào mục còn thiếu; không bù bằng UI mock báo thành công hoặc adapter tự cho phép.

## 8. Nguồn đối chiếu

| Tài liệu | Vị trí sử dụng |
|---|---|
| [Workbook kế hoạch](UTBridora_Plan.xlsx) | CAP NHAT CHUNG hàng 17; người phối hợp hàng 5, 14, 16, 18, 23; tuần 2 hàng 34. T5 - DOANH NGHIEP hàng 6–10. HUONG DAN và TONG QUAN: quy trình DEV → QA → Leader và nghiệm thu task/SRS |
| [SRS v1.2.4](../srs/UTBridora_SRS_Tong_The_v1.2.4.docx) | §9.2 DEM; §9.6 user stories; §23 hợp đồng/dữ liệu; §25.9 đề xuất hợp nhất. Đọc cùng [trạng thái SRS](../srs/README.md) |
| [Contract Pack Candidate](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md) | BC22 Opportunity/Preference; BC31 DemandCriteria; CP-T5UI1 REST T5; BC42 đọc school verification; E.1/E.3 permission/audit. Đọc cùng [trạng thái contract](../contracts/README.md) |
| [Hướng dẫn BE/FE](../guides/HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md) | §5–8 schema/module/security/audit; §12 phụ thuộc; §14–15 test/PR |
| [Hướng dẫn GitHub](../guides/HUONG_DAN_LAM_VIEC_VOI_GITHUB.md) | Branch theo chức năng, Draft PR, review và bằng chứng kiểm tra |
| [Trạng thái triển khai](../status/IMPLEMENTATION_STATUS.md) | Snapshot 04/11/2026 và phân biệt kết quả test lịch sử |

Phần kế hoạch được lập trước khi code. Cập nhật triển khai ngày 05/11 nằm trong tài liệu bàn giao và báo cáo kiểm thử ở đầu file; chưa cập nhật trạng thái hoàn thành trong workbook hoặc coi SRS đã được nghiệm thu.
