# T3-03 — Khung UI HR/Mentor và nhóm thực tập

Owner: DEV07 — Lại Thái Thuận. QA phụ trách: QA03 — Phạm Lâm Phương Bảo.

Phạm vi giao việc lấy từ `UTBridora_Plan.xlsm`, sheet `CAP NHAT CHUNG`, hàng 13,
các cột B–J và R: khung UI HR/Mentor, điều hướng đúng assignment, chuẩn bị tích hợp;
trace FR-07/FR-08. Không sửa workbook và không mở rộng sang task các tuần tiếp theo.

## Phần đã triển khai

- Module `web-demo/src/modules/internship`, ghép vào shell `App.tsx` hiện có.
- Menu “Không gian thực tập” cho tài khoản có membership ENTERPRISE theo tổ chức.
  Đây chỉ là điểm vào màn hình; membership không cấp vai trò HR/Mentor.
- Chọn rõ phân công, doanh nghiệp/trường/kỳ, đợt thực tập rồi nhóm; không tự chọn
  membership/phân công đầu tiên. Mã scope đầy đủ hiển thị bên dưới selector để
  người dùng đối chiếu cả khi nhãn trong selector bị rút gọn bởi màn hình nhỏ.
- Kiểm tra user, membership, grant exact `t3:workspace:read`, kind, scope, active,
  validFrom/validUntil. ADMIN global không mặc nhiên vào workspace nghiệp vụ.
- Mentor cần groupId và có tên trong `mentorUserIds`; HR có groupId chỉ mở nhóm
  tương ứng. Case-only assignment bị từ chối ở màn tổng quan nhóm để không mở rộng quyền.
- Phản hồi chứa cohort/nhóm ngoài scope bị từ chối toàn bộ trước khi render, gồm
  tên nhóm, mã và số lượng. Không đưa caseIds/mentorUserIds thành danh sách người dùng.
- Hủy request/bỏ phản hồi cũ khi đổi phân công/đợt hoặc unmount. Hết hạn phân công
  làm ẩn nhóm đang mở; tải lại phân công xóa toàn bộ lựa chọn và dữ liệu cũ.
- Loading, empty, lỗi quyền, lỗi mạng, retry; responsive và thao tác bằng bàn phím.
- Đếm Mentor theo userId duy nhất ở danh sách/chi tiết: `GroupDto` của DEV06 cho
  phép ID Mentor lặp, nên không dùng độ dài mảng để đếm người.
- Giữ auth/session/CSRF và transport `api.ts` của T1; không đổi security, schema,
  migration, permission seed hoặc audit registry.

## Trạng thái tích hợp hiện tại

Ngày **07/11/2026**, đã cập nhật hai nhánh dependency vào nhánh T3-03:

- DEV05 `feature/t3-placement-inbox` tại `e256903`: canonical lifecycle values,
  interface `PlacementLifecycleConsumer`, policy và contract/unit test. Interface
  không phải consumer implementation; Case Read Port/provider/runtime vẫn chưa có.
- DEV06 `feature/t3-cohort-group-domain` tại `c0850f5`: thêm domain Cohort/Group/Task,
  `WorkspaceService`, consumer ports, access/transaction composition và test. Các
  class chưa đăng ký Spring bean; chưa có REST controller, scoped list service,
  persistence/provider hoặc migration. DDL trong tài liệu là proposal chưa duyệt.

Hai nhánh được ghép local và dừng trước commit để người thực hiện tự commit/push.
Nền DEV07 đã push ngày 06/11 là `50a238c`; `origin/main` vẫn ở `0cc1daf`. Không
suy tình trạng hoàn thành task của owner thành việc API đã sẵn sàng cho frontend.

Thông báo DEV06 qua người thực hiện: Case runtime và Case Read Port/provider được
để lại vì phụ thuộc phần Team 5 dự kiến tuần 3. T3-03 có thể bàn giao kiểm thử khung
UI độc lập với Case runtime, nhưng vẫn chưa có **API đọc phân công/cohort/group,
transport chọn context và quyền runtime `t3:workspace:read`** để nối dữ liệu thật.
Đây là các điểm tích hợp riêng, không tự được giải quyết chỉ bằng việc bỏ qua Case.

Vì vậy app thật sử dụng `pendingWorkspaceGateway`: hiển thị **Chờ tích hợp dữ liệu**,
không tạo nhóm giả, không suy HR/Mentor từ role ENTERPRISE và không gửi request tới
endpoint tự đặt. Màn hiện tại chưa chứng minh đăng nhập HR/Mentor với dữ liệu thật
hay được QA nghiệm thu. Không ghi tiến độ toàn task “Hoàn thành” chỉ từ test fixture.

T3-03 là khung điều hướng, chưa triển khai đầy đủ dashboard FR-07/FR-08, task board,
lịch, tạo/chuyển nhóm, phân công mentor, Placement, chấm điểm hay công bố kết quả.
Các số lượng trong chi tiết nhóm lấy từ GroupDto của gateway, không phải thống kê
dashboard. Giai đoạn hiện tại hiển thị ID scope vì ContextAssignment không có tên
trường/doanh nghiệp/kỳ; adapter thật có thể bổ sung lookup tên được phép đọc.

## Căn cứ hợp đồng

- [Contract v1.0](../contracts/UTBridora_Contract_Pack_B01_B06_v1.0_Candidate.md):
  C.0 (business Page), CP-AUTHZ1 (ContextAssignment/Scope), CP-WS1 (CohortDto/GroupDto).
- [Trạng thái contract](../contracts/README.md): v1.0 là căn cứ được ghi nhận trong repo;
  v1.1 là diff kỹ thuật riêng. Thay đổi này không sửa hoặc tự ký nhận contract.
- [Hướng dẫn BE/FE](HUONG_DAN_PHAT_TRIEN_BE_FE_CHO_CAC_TEAM.md), mục 9–10 và 14.

`WorkspacePage<T>` dùng `page`, khác `Page<T>` core dùng `number`. Không sửa kiểu
core để ép hai giao thức thành một.

## Điểm bàn giao cho DEV05/DEV06 và T1

Đối chiếu `origin/main` ngày **07/11/2026**: remote vẫn ở
`0cc1daf`. Năm declaration
`Scope`, `ContextAssignment`, `CohortDto`, `GroupDto`, `CohortQuery` giữ nguyên
giữa v1.0 và v1.1. Bundle v1.1 vẫn ghi `t3:workspace:read` là
`catalogImplemented: false`; không có GET đọc danh sách ContextAssignment hoặc
transport chọn HR context mới. Không cần đổi DTO/UI chỉ vì thông báo cập nhật
contract. Hai nhánh mới có thêm domain/service/port nhưng vẫn không có GET runtime
để nối gateway. Giữ nguyên chữ ký endpoint đã công bố và trạng thái chờ tích hợp.

Thứ tự phối hợp để nối dữ liệu thật:

- **T1 / đầu mối Team 3:** tái sử dụng core auth đã có; cần xác nhận cách cấp
  permission `t3:workspace:read` và provider authoritative cho assignment. Không
  thay role core hoặc coi ENTERPRISE membership là quyền HR/Mentor.
- **T3-01 / DEV05:** đã có chữ ký lifecycle; Case/Placement runtime là dependency
  giai đoạn sau. T3-03 không tự tạo hay phê duyệt Placement, không giả Case provider.
- **T3-02 / DEV06 và owner assignment:** cần HTTP GET danh sách cohort/group đúng
  scope, phân trang business Page, API đọc assignment của actor hiện tại và cách
  chọn context phía server. `CoreWorkspaceAccess.BusinessAuthority` mới là
  consumer interface, không phải provider hoặc endpoint sẵn dùng.
- **T5 (gián tiếp):** Placement/Term và dữ liệu nguồn để nghiệm thu luồng thực tập
  thật; chỉ ACTIVE mới mở thao tác Task theo contract.
- **QA03:** kiểm thử lại với backend thật trước nghiệm thu. Trong thời gian chờ,
  khung UI, unit/UI fixture và regression core có thể kiểm tra độc lập.

`WorkspaceGateway` là interface TypeScript nội bộ, **không phải REST API mới**:

```typescript
readAssignments(signal): Promise<ContextAssignment[]>
readCohorts(assignment, signal): Promise<Cohort[]>
readGroups(assignment, cohort, signal): Promise<InternshipGroup[]>
```

Trước khi thay `pendingWorkspaceGateway`, cần chốt các mục sau:

1. API đọc phân công của actor hiện tại, bao gồm hiệu lực parent coordinator và
   revocation. Không lấy user/actor từ query do client tự khai để cấp quyền.
2. Cơ chế chọn context doanh nghiệp/trường/kỳ/phân công ở server. Contract yêu cầu
   scope đã xác minh nhưng chưa công bố transport chọn context cho các GET này.
   Không tự thêm header/query parameter rồi xem đó là contract liên team.
3. T1 công bố grant `t3:workspace:read` đúng scope, giữ bốn role core và kiểm lại
   membership/org/assignment authoritative ở mỗi request nghiệp vụ.
4. Adapter chuyển các trang API thành danh sách **đầy đủ trong scope đã chọn**;
   không chỉ lấy trang đầu rồi báo tổng số nhóm. Nếu dữ liệu lớn, thống nhất thêm
   phân trang ở gateway/UI; lỗi hoặc tải thiếu trang phải báo lỗi, không trả thành công một phần.
5. Cohort phải khớp enterprise/school/term. Mentor chỉ nhận nhóm được giao hiện hành;
   HR bị giới hạn group/case không được dùng API rộng để mở dữ liệu ngoài scope.
6. Backend bảo vệ cả direct API request, kể cả client bỏ qua UI. Các kiểm tra frontend
   chỉ phục vụ điều hướng/phòng hiển thị nhầm, không thay authorization ở server.

`api.ts` của module đã có ba hàm đọc theo endpoint CP-WS1, dùng wrapper core:

```text
GET /api/v1/t3/cohorts?termId=...&page=...&size=25
GET /api/v1/t3/cohorts/{cohortId}/groups?page=...&size=25
GET /api/v1/t3/mentors/me/groups?page=...&size=25
```

Các helper này chưa được nối vào gateway mặc định; không tự gọi API thiếu cơ chế
context đã được thống nhất. Adapter tương lai phải xử lý 401/403/404, abort và
stale response; không catch lỗi quyền thành mảng rỗng hoặc fallback sang mock.

### Phần tương thích đã xác minh với DEV06

`web-demo/tests/fixtures/internship-wire.json` chứa dữ liệu tổng hợp dùng chung
cho test frontend và `WorkspaceFrontendContractTest` phía Java. Java đọc/serialize
qua chính `CohortGroupDtos.CohortDto/GroupDto` của DEV06, rồi kiểm cả chuyển đổi
`Cohort/InternshipGroup.toDto()` mới tại `c0850f5`; frontend dùng cùng fixture
trong kiểm tra scope và UI. Có nhóm bình thường, nhóm rỗng, Mentor trùng ID và
version 0 / 9007199254740991. Test này phát hiện lệch tên/kiểu field hoặc mất dữ
liệu giữa owner/consumer; không chứng minh REST API, context, auth hoặc DB T3.

`TaskDtos.AssignmentDto` là phân công Task cho Case/sinh viên; **không thay thế**
`ContextAssignment` HR/Mentor của CP-AUTHZ1. Policy trạng thái từ DEV05 dành cho
guard backend; không đưa policy thuần hoặc event cũ vào UI để tự cấp quyền thao tác.

## Chạy kiểm thử

Tại `web-demo`, dùng Node và npm theo hướng dẫn chung:

```powershell
npm ci
npm run build
npx playwright install chromium
npm run test:internship
```

`test:internship` dùng Playwright sẵn có, không thêm dependency. Các test mới cũng
được thu thập bởi `npm run test:e2e` trong CI hiện tại. Lệnh chạy riêng không cần
backend, Docker hoặc tài khoản thật.

- `tests/internship.unit.spec.ts`: kiểm tra access/scope/validity và transport GET.
- `tests/internship.ui.spec.ts`: UI với gateway fixture và shell với auth mock.
- `tests/fixtures/internship-harness.tsx` + HTML: entry chỉ cho kiểm thử Vite, không
  được import từ production entry. `/api/v1/test-only/...` là URL được Playwright
  intercept tại fixture, không phải endpoint backend để team triển khai.
- `WorkspaceFrontendContractTest`: test Java đọc đúng fixture wire frontend,
  chạy cùng Maven unit; cần source DTO của DEV06 trong checkout.

Không truy cập fixture thủ công rồi coi đó là demo dữ liệu thật. Xem
[báo cáo và daily report](../testing/T3_03_TEST_REPORT.md) để phân biệt kết quả DEV,
phần NOT RUN và dependency còn thiếu.

[Gói bàn giao QA03](../testing/T3_03_QA_HANDOFF.md) ghi lệnh chạy, các nhóm ca
khung UI có thể kiểm ngay và điều kiện mở các ca HTTP/data thật còn bị chặn.

## Kiểm tra sau khi có backend thật

QA03 cần đăng nhập HR, Mentor, ENTERPRISE không có assignment và ADMIN; kiểm tra
nhiều trường/doanh nghiệp/kỳ, Mentor nhiều nhóm, group giới hạn và case-only scope.
Thử đổi group/cohort ID trực tiếp ở request, revoke parent/assignment, hết hạn và
session cũ; xác nhận server từ chối cả khi client bỏ qua selector. Chạy regression
auth/session/CSRF và các test T3 thật trước khi kết luận nghiệm thu FR-07/FR-08.
