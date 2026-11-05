# T5-02 — Bàn giao phần nhu cầu thực tập tuần 1

**Cập nhật 05/11:** Dev11/Dev12 đã bổ sung bảo vệ form, khung nhà trường, lỗi cạnh trường và browser test với backend thật. Adapter danh mục mẫu nay tắt mặc định; xem [hướng dẫn local và tích hợp](T5_WEEK1_LOCAL_AND_INTEGRATION.md) trước khi chạy demo hoặc ghép T4.

Ngày triển khai: **05/11/2026**. Owner: Dev 11; UI phối hợp Dev 12; QA05.

Đây là implementation candidate để review. Người dùng xác nhận **chưa rõ signoff T1/T4/T5**, và chọn **nhập đủ thông tin theo schema Candidate khi lưu nháp**. Không thay đổi trạng thái phê duyệt của SRS/Contract Pack.

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

`app.t5.demand.contract-approved` mặc định **false**, có thể cấu hình bằng `T5_DEMAND_CONTRACT_APPROVED`. Sau khi kiểm session/quyền, API trả **503 CONTRACT_PENDING_SIGNOFF** khi chưa xác nhận contract. Không tự bật cờ trong `.env` của máy.

Ngay cả khi contract được xác nhận, mutation và đọc criteria vẫn cần implementation thật của **DemandReferencePort** để kiểm school/term/catalog/taxonomy và gate đã thống nhất. Adapter mặc định trả **503 DEPENDENCY_UNAVAILABLE**, không có fallback cho phép mọi mã. Reference fixture từng dùng trong kiểm thử đã được xóa cùng test Demand theo yêu cầu người dùng; không có adapter runtime cho phép giả.

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

Lỗi nghiệp vụ của module có `errorCode,error,message,requestId,fieldErrors,retryAfterSeconds`. Các lỗi session/CSRF phát sinh tại filter core giữ response legacy. Foreign Demand trả 404; sai org grant 403; version/state conflict 409; criteria không hợp lệ 422; contract/reference chưa sẵn sàng 503.

## 4. Version, snapshot và retry

Mỗi update được chấp nhận tăng `version`. Chính sách **đề xuất chờ T4 xác nhận**: `criteriaVersion` chỉ tăng khi semantic hash đổi. Hash chứa school, term, quantity, Required/Preferred, academicCatalogVersion và taxonomyVersion; không chứa title/description/location/benefits/deadline, actor, demandId hoặc timestamp. Set ngành/kỹ năng được sắp xếp; decimal chuẩn hóa, null giữ tường minh.

Deadline vẫn thuộc Demand version và lịch sử. Các nghiệp vụ nomination/Placement giai đoạn sau phải kiểm cả demandVersion; không dùng riêng criteriaVersion để bỏ qua thay đổi deadline. Muốn đổi tập field ảnh hưởng hash phải thống nhất trước khi tích hợp, không coi chính sách này là signoff.

`t5_demand_revisions` append theo `(demand_id,version)`, lưu data, snapshot, actor và operation. Không có API sửa/xóa revision. `DemandCriteriaPort.read` trả snapshot của revision hiện hành; expectedCriteriaVersion khác trả CRITERIA_STALE. Snapshot cũ được giữ trong lịch sử, không bị ghi đè. Purpose NOMINATE/PLACEMENT và actor SCHOOL chưa hỗ trợ, luôn từ chối.

Port local hiện nhận Spring `Authentication`/org đã xác minh và dùng `DemandFailure`; adapter sang `ActorContext`/`Result<T>` của bộ contract chung vẫn cần T1/T4 thống nhất. Không coi chữ ký Java local là contract liên team đã phê duyệt.

Idempotency command và effect/audit commit cùng transaction. Cùng key/body replay response cũ sau kiểm quyền hiện hành; khác body trả IDEMPOTENCY_KEY_REUSED. Sau 24 giờ trả IDEMPOTENCY_RESPONSE_EXPIRED, không tạo lại effect. Request cùng actor có thể chờ lock rồi nhận replay; lock conflict được chuyển thành REQUEST_IN_PROGRESS/Retry-After. Chưa cam kết mọi request đang chạy đều nhận 409 ngay lập tức.

FE giữ cùng key/body khi kết quả save chưa rõ do mạng/5xx. Lỗi contract/reference được biết là chưa lưu nên cho sửa form; thay body sẽ tạo key mới. Tránh nhấn lưu mới trên giao dịch chưa xác định kết quả.

## 5. Migration và phần cần review trước tích hợp chung

Thêm **V8__t5_demand_drafts.sql** theo HEAD local V1–V7. **V8 là số đề xuất local, chưa được T1 cấp slot cho môi trường chung.** T1 cần điều phối version theo HEAD tại lúc merge; chỉ đổi tên migration trước khi nó được áp dụng ở môi trường dùng chung. Không sửa V1–V7.

Ba bảng: `t5_demands`, `t5_demand_revisions`, `t5_demand_commands`; FK về tổ chức/user core, không cascade xóa. Term/catalog được kiểm qua owner port vì chưa có bảng canonical tương ứng trong checkout; không dựng bảng kỳ/ngành/kỹ năng riêng của Dev 11. Flyway vẫn áp dụng schema mới khi app boot kể cả feature gate false; chưa chạy migration vào database local/staging của người dùng trong lượt triển khai này.

Audit thêm action `DEMAND_CHANGED`, reason `DEMAND_CREATE`/`DEMAND_UPDATE`. Version/operation/actor chi tiết nằm trong revision. Chưa triển khai metadata_json audit của toàn Contract Pack; T1 cần review tương thích registry trước merge.

Các thay đổi file chung: SecurityConfig matcher chính xác, AuditAction, cấu hình gate, App.tsx/menu, ApiError hỗ trợ fieldErrors và migration upgrade assertion. Không nâng framework hoặc thêm dependency.

## 6. Opportunity và việc còn chờ

Opportunity chưa có endpoint runtime trong lát cắt này. Thiết kế bàn giao: T5 cung cấp projection của Cooperation đã APPROVED, `opportunityId=cooperationId`; lấy title/location/criteria từ Demand liên kết, school/term và publication window từ owner nghiệp vụ. T2 giữ Preference. Demand DRAFT không được tự xuất hiện thành opportunity.

Còn chờ: signoff CP-T5UI1/BC31/header org/chính sách version/gate school; reference adapter T2/T4/T5; consumer T4 thật, school-scoped evaluation; slot migration T1; QA05/Leader nghiệm thu. Không đánh dấu toàn bộ T5-02 hoặc SRS đã hoàn thành chỉ vì code/test local chạy.

## 7. Kiểm tra

Ngày 05/11/2026, theo yêu cầu người dùng, đã viết lại `DemandDomainTest.java`, `DemandIT.java` và `web-demo/tests/demand.spec.ts` sau lần xóa trước đó; bổ sung `DemandServiceTest.java`. Đây là bộ test theo source hiện tại, không phải khôi phục nguyên văn mọi test cũ. Coverage và kết quả mới xem [báo cáo T5](../testing/T5_DEMAND_TEST_REPORT.md). Evidence JSON cũ vẫn là lịch sử, không đại diện lượt chạy mới.

Tại gốc repo với JDK đã cấu hình và Docker Linux engine:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

`test` chạy unit test; `verify` chạy thêm integration test. Chạy riêng phần tích hợp Nhu cầu bằng `.\mvnw.cmd '-Dit.test=DemandIT' verify`. `MigrationUpgradeIT` vẫn kiểm nâng V2→V8 vì migration V8 còn trong source. DemandIT dùng MySQL Testcontainers riêng, không dùng database của người dùng.

Frontend:

```powershell
cd web-demo
npm ci
npm run build
npx playwright test tests/demand.spec.ts
```

Core E2E dùng harness `mvnw.cmd '-Dfrontend.e2e=true' verify` theo guide hiện có. Demand browser tests dùng API giả lập và không cần harness core; không coi chúng là kiểm chứng FE nối backend thật.
