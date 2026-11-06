# T5-02 / T5-03 — Bàn giao Dev11 và Dev12 tuần 1

**Cập nhật 06/11/2026:** người dùng đảm nhiệm cả Dev11 và Dev12. Phạm vi tuần này chỉ gồm **T5-02** và **T5-03**, theo sheet CAP NHAT CHUNG hàng 17–18 trong [workbook](../plans/UTBridora_Plan.xlsx). Xem [cấu trúc module](../../web-demo/src/modules/cooperation/README.md) và [kết quả kiểm thử](../testing/T5_DEMAND_TEST_REPORT.md).

| Nhiệm vụ | Phần bàn giao | Trạng thái |
|---|---|---|
| Dev11 / T5-02 | API nhu cầu DRAFT, Required/Preferred, version/history, snapshot bất biến, guard, audit, taxonomy T4 và đọc kỳ Dev10 | Đã có code và test phía T5; còn ghép nguồn ngành T2 và consumer T4 thật |
| Dev12 / T5-03 | Khung doanh nghiệp/nhà trường, form schema chuẩn, giữ nội dung khi lỗi, phân trang, lỗi field/version, retry, quyền theo org, responsive | Đã triển khai và kiểm thử phạm vi tuần 1; chờ QA05/Leader nghiệm thu |
| Opportunity trong T5-02 | Thiết kế projection và fixture BC22 | Bàn giao minh họa; chưa mở runtime khi chưa có Cooperation APPROVED |

T5-03 tuần 1 yêu cầu phần chưa có backend ghi rõ đang chờ. Vì vậy các API tra cứu danh mục, xử lý xác minh, gửi/duyệt hợp tác, Placement chưa sẵn sàng không biến thành nhiệm vụ phải làm thay owner. Chỉ ghép phần thuộc hai task trên khi nguồn được bàn giao.

Contract v1.0 đã được người dùng chốt, theo [review T1-01](../contracts/T1_01_TECHNICAL_REVIEW.md); không chờ ký lại. Người dùng chọn nhập đủ thông tin theo schema khi lưu nháp. Trạng thái DEV, tích hợp liên team và nghiệm thu QA được ghi riêng.

## 1. Phần đã viết

- API tạo/list/update Demand DRAFT, phân trang theo doanh nghiệp đang chọn.
- Validation cấu trúc JSON đóng: không nhận owner/actor/state từ browser; kiểm quantity, ngày, kỹ năng trùng, ngưỡng level/years và độ dài Unicode.
- Tái sử dụng session/CSRF, membership, organization guard và AuditService của T1.
- Optimistic version; lịch sử bất biến chứa actor/operation; snapshot tiêu chí bất biến theo revision.
- Idempotency theo actor/org/operation/key: replay kết quả gốc, chặn khác body, không chạy lại effect sau 24 giờ.
- Giao diện thô trong shell: chọn doanh nghiệp, danh sách, form tạo/sửa, lỗi field, conflict và retry khi mất kết nối. Giao diện dùng API thật; fixture chỉ nằm trong test.
- Port đọc snapshot cho purpose EVALUATE với actor ENTERPRISE đúng org. Chưa ghép consumer T4 thật.

Source BE: `src/main/java/vn/utbridora/auth/modules/cooperation/`. Source FE: `web-demo/src/modules/cooperation/`.

## 2. Trạng thái chạy mặc định

`app.t5.demand.contract-approved` mặc định **false**, cấu hình bằng `T5_DEMAND_CONTRACT_APPROVED`. Tên giữ để tương thích nhưng đây là công tắc triển khai: sau khi kiểm session/quyền, API trả **503 FEATURE_DISABLED** nếu tắt. Lỗi này không có nghĩa contract chưa chốt.

Khi tắt adapter demo, `DemandReferencePort` dùng mã/version thật từ T4 và `TermAcademicReferences` đọc kỳ Dev10. `DemandMajorCatalogPort` vẫn cần implementation từ nguồn ngành T2; thiếu nguồn trả **503 DEPENDENCY_UNAVAILABLE**. Adapter danh mục tĩnh chỉ dùng khi bật cấu hình demo rõ ràng, không chứng minh đã ghép nguồn thật. Test đã được khôi phục; fixture ngành chỉ nằm trong test tích hợp.

FE có menu **Nhu cầu thực tập** khi grant ENTERPRISE có `cooperation:enterprise:create`. ADMIN global không tự thấy menu. Khi backend chưa sẵn sàng, form giữ dữ liệu nhưng không báo lưu thành công. Bản nhập chỉ nằm trong bộ nhớ trang; tải lại hoặc đổi doanh nghiệp sẽ bỏ bản nhập đó.

## 3. HTTP contract đang triển khai

Đặc tả máy đọc: [t5-demand.openapi.json](../contracts/t5-demand.openapi.json). Nguồn thiết kế là **CP-T5UI1** và **BC31** trong Candidate; BC42 là school verification, không phải mục REST Demand.

| Method / path | Hành vi |
|---|---|
| POST `/api/v1/enterprise/demands` | Body DemandInput đầy đủ → 201 DemandDto DRAFT, version/criteriaVersion bắt đầu 1 |
| GET `/api/v1/enterprise/demands` | `page=0,size=25`, tùy chọn `termId,state,includeDeleted`; size tối đa 100 → Page có `page` (khác `number` của API core) |
| PATCH `/api/v1/enterprise/demands/{id}` | `{expectedVersion,data}` → 200 DemandDto; owner/school/term không đổi, chỉ DRAFT chưa xóa |

Mọi request dùng cookie session và header **X-Organization-Id: UUID**. Đây là lựa chọn tích hợp đề xuất cho bản code này, cần T1/consumer review; backend kiểm membership/quyền của org được chọn, không tin header như bằng chứng cấp quyền. Mutation thêm CSRF và **Idempotency-Key: UUID**. Không có GET detail riêng, delete, reopen, restore hoặc submit Cooperation.

Body tạo mới (các UUID/mã dưới đây chỉ là ví dụ; không phải dữ liệu sẵn có):

```json
{
  "schoolOrgId": "22222222-2222-4222-8222-222222222222",
  "termId": "44444444-4444-4444-8444-444444444444",
  "title": "Thực tập Java",
  "quantity": 3,
  "description": "Tham gia phát triển ứng dụng dưới sự hướng dẫn của mentor.",
  "location": null,
  "benefits": null,
  "deadline": "2027-01-10T10:00:00Z",
  "required": {
    "majorCodes": ["IT"],
    "skills": [{"skillCode":"JAVA","minLevel":2,"minYearsPractice":null}],
    "dateRange": {"startDate":"2027-02-01","endDate":"2027-04-30"}
  },
  "preferred": [],
  "academicCatalogVersion": "academic-v1",
  "taxonomyVersion": "taxonomy-v1"
}
```

Mọi field trong ví dụ phải hiện diện. `location`, `benefits`, `minLevel`, `minYearsPractice` được null. Các array phải có mặt, có thể rỗng; `majorCodes=[]` nghĩa không giới hạn ngành. Nháp trong bản này luôn đủ schema và chỉ trở thành VALID sau khi reference provider xác minh.

Lỗi nghiệp vụ của module có `errorCode,error,message,requestId,fieldErrors,retryAfterSeconds`. Filter của route Demand đã trả envelope mới cho 401/403/CSRF; core khác giữ legacy. Foreign Demand trả 404; sai org grant 403; version/state conflict 409; criteria không hợp lệ 422; chức năng/nguồn tham chiếu chưa sẵn sàng 503.

## 4. Version, snapshot và retry

Mỗi update được chấp nhận tăng `version`. Chính sách **đề xuất chờ T4 xác nhận**: `criteriaVersion` chỉ tăng khi semantic hash đổi. Hash chứa school, term, quantity, Required/Preferred, academicCatalogVersion và taxonomyVersion; không chứa title/description/location/benefits/deadline, actor, demandId hoặc timestamp. Set ngành/kỹ năng được sắp xếp; decimal chuẩn hóa, null giữ tường minh.

Deadline vẫn thuộc Demand version và lịch sử. Các nghiệp vụ nomination/Placement giai đoạn sau phải kiểm cả demandVersion; không dùng riêng criteriaVersion để bỏ qua thay đổi deadline. Muốn đổi tập field ảnh hưởng hash phải thống nhất trước khi tích hợp, không coi chính sách này là signoff.

`t5_demand_revisions` append theo `(demand_id,version)`, lưu data, snapshot, actor và operation. Không có API sửa/xóa revision. `DemandCriteriaPort.read` trả snapshot của revision hiện hành; expectedCriteriaVersion khác trả CRITERIA_STALE. Snapshot cũ được giữ trong lịch sử, không bị ghi đè. Purpose NOMINATE/PLACEMENT và actor SCHOOL chưa hỗ trợ, luôn từ chối.

`DemandCriteriaFacade` bọc port local thành Success/DomainError và context từ workflow đã xác thực. Snapshot trả ra cũng sao chép collection thành bất biến, không chỉ lưu revision bất biến trong DB. Context này còn là kiểu local; chưa thay thế runtime context chung của T1. Test bên gọi chứng minh phía provider T5; consumer Matching T4 thật vẫn cần ghép và kiểm tra riêng.

Idempotency command và effect/audit commit cùng transaction. Cùng key/body replay response cũ sau kiểm quyền hiện hành; khác body trả IDEMPOTENCY_KEY_REUSED. Sau 24 giờ trả IDEMPOTENCY_RESPONSE_EXPIRED, không tạo lại effect. Request cùng actor có thể chờ lock rồi nhận replay; lock conflict được chuyển thành REQUEST_IN_PROGRESS/Retry-After. Chưa cam kết mọi request đang chạy đều nhận 409 ngay lập tức.

FE giữ cùng key/body khi kết quả save chưa rõ do mạng/5xx. Lỗi contract/reference được biết là chưa lưu nên cho sửa form; thay body sẽ tạo key mới. Tránh nhấn lưu mới trên giao dịch chưa xác định kết quả.

## 5. Migration và phần cần review trước tích hợp chung

Thêm **V8__t5_demand_drafts.sql** theo HEAD local V1–V7. **V8 là số đề xuất local, chưa được T1 cấp slot cho môi trường chung.** T1 cần điều phối version theo HEAD tại lúc merge; chỉ đổi tên migration trước khi nó được áp dụng ở môi trường dùng chung. Không sửa V1–V7.

Ba bảng Demand: `t5_demands`, `t5_demand_revisions`, `t5_demand_commands`; FK về tổ chức/user core, không cascade xóa. V9 nhận DDL taxonomy T4; V10 thêm metadata audit; V11 nhận phần bảng `terms` từ nhánh Dev10 `feature/t5-term-verification` (7353971), không nhập V8 trùng version của nhánh đó. Không tạo registry ngành riêng. Khi merge toàn nhánh Dev10 phải hợp nhất migration để tránh tạo `terms` lần hai. Các migration mới chỉ được kiểm trên Testcontainers trong lượt này, chưa áp dụng vào DB local/staging của người dùng.

Audit `DEMAND_CHANGED` có reason `DEMAND_CREATE`/`DEMAND_UPDATE`; metadata allowlist gồm operation/demandVersion/criteriaVersion, ghi cùng transaction. Không tuyên bố đã triển khai metadata của toàn Contract Pack; thay đổi core audit/migration vẫn cần review khi hợp nhất.

Các thay đổi file chung: SecurityConfig matcher chính xác, AuditAction, cấu hình gate, App.tsx/menu, ApiError hỗ trợ fieldErrors và migration upgrade assertion. Không nâng framework hoặc thêm dependency.

## 6. Opportunity và việc còn chờ

Opportunity chưa có endpoint runtime trong lát cắt này. Thiết kế bàn giao: T5 cung cấp projection của Cooperation đã APPROVED, `opportunityId=cooperationId`; lấy title/location/criteria từ Demand liên kết, school/term và publication window từ owner nghiệp vụ. T2 giữ Preference. Demand DRAFT không được tự xuất hiện thành opportunity.

Fixture: [t5-opportunity-week1.json](../contracts/fixtures/t5-opportunity-week1.json), hoàn toàn synthetic, không seed vào DB. Tại thời điểm kiểm cố định `2026-11-06T00:00:00Z`, chỉ được hiển thị nếu Cooperation tương ứng APPROVED và actor đúng school/student-term. DRAFT, sai scope, trước publishedAt hoặc tại/sau closedAt không được hiển thị. Fixture chỉ biểu diễn OpportunityView, không giả lập bản ghi đã duyệt trong runtime.

Phụ thuộc tích hợp: nguồn ngành/version T2 (Dev04), consumer/context BC31 chung và review migration; QA05/Leader nghiệm thu riêng. Đó là việc phối hợp cần nhận bàn giao, không phải yêu cầu người dùng code thêm module của team khác để hoàn thành phần mình.

## 7. Kiểm tra

Ngày 05/11/2026, theo yêu cầu người dùng, đã viết lại `DemandDomainTest.java`, `DemandIT.java` và `web-demo/tests/demand.spec.ts` sau lần xóa trước đó; bổ sung `DemandServiceTest.java`. Đây là bộ test theo source hiện tại, không phải khôi phục nguyên văn mọi test cũ. Coverage và kết quả mới xem [báo cáo T5](../testing/T5_DEMAND_TEST_REPORT.md). Evidence JSON cũ vẫn là lịch sử, không đại diện lượt chạy mới.

Tại gốc repo với JDK đã cấu hình và Docker Linux engine:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

`test` chạy unit test; `verify` chạy thêm integration test. Chạy riêng phần tích hợp bằng `.\mvnw.cmd '-Dit.test=DemandIT,DemandTaxonomyIT,MigrationUpgradeIT' verify`. `MigrationUpgradeIT` kiểm nâng V2→V11. Test dùng MySQL Testcontainers riêng, không dùng database của người dùng.

Frontend:

```powershell
cd web-demo
npm ci
npm run build
npx playwright test tests/demand.spec.ts
```

`demand.spec.ts` dùng API giả lập. Luồng browser nối backend/DB thật chạy bằng `.\mvnw.cmd '-Dit.test=DemandTaxonomyIT#browserUsesRealDemandApiAndDatabase' '-Ddemand.e2e=true' verify`; riêng catalog ngành vẫn dùng fixture T2. Không nhầm hai loại bằng chứng. Core E2E dùng `-Dfrontend.e2e=true` theo guide chung.
