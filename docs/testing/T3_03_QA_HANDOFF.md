# T3-03 — Bàn giao kiểm thử khung UI HR/Mentor

Ngày chuẩn bị: **07/11/2026**, UTC+7. DEV07 — Lại Thái Thuận.
Người kiểm thử phụ trách: QA03 — Phạm Lâm Phương Bảo.

## Phạm vi bàn giao

Nhánh: `feature/t3-03-hr-mentor-workspace`. Nền DEV07 đã push: `50a238c`.
Dependency mới đã ghép local: DEV05 `e256903`, DEV06 `c0850f5`.
Tại lúc chuẩn bị tài liệu, người thực hiện chưa tạo commit/push lượt cập nhật này.
QA dùng SHA mới sau khi DEV07 push, ghi SHA đó vào biên bản; không dùng `50a238c`
để xác nhận các thay đổi ngày 07/11. Tài liệu không tự gửi cho QA hoặc xác nhận QA PASS.

Đề nghị kiểm thử **khung UI, điều hướng theo phân công, scope và tương thích
DTO/domain**, đúng phạm vi T3-03 tuần 1 đã đối chiếu sheet CAP NHAT CHUNG dòng 13.
Các màn được kiểm bằng fixture tổng hợp có nhãn rõ trong tên test. Đây không phải
bàn giao dashboard FR-07/FR-08 đầy đủ, workflow Task, Case hoặc backend T3 hoàn chỉnh.

Trong ứng dụng thật, ENTERPRISE có điểm vào “Không gian thực tập”; màn **Chờ tích
hợp dữ liệu** là hành vi dự kiến tại revision này. Không có tài khoản HR/Mentor
thật hay dữ liệu T3 được seed cho lần bàn giao. Không xem fixture là dữ liệu thật.

## Chuẩn bị và chạy lại

Dùng JDK 21, Node 24/npm theo lockfile. Full verify cần Docker Linux engine;
MySQL 8.4 được Testcontainers tạo và dọn, không dùng DB chứa dữ liệu cá nhân.
Chạy từng lệnh, dừng khi có lỗi. Lệnh dưới dùng PowerShell tại gốc repo.

```powershell
git status --short --branch
git log -1 --oneline
Push-Location web-demo
npm ci
npm run build
npx playwright install chromium
npm run test:internship
Pop-Location
.\mvnw.cmd '-Dtest=WorkspaceFrontendContractTest' test
```

`test:internship` chạy 17 unit + 18 UI/shell fixture và không cần backend/Docker.
Để xem Chromium thao tác các ca fixture tự động, tại `web-demo` chạy:

```powershell
npm run test:internship -- --headed
```

Không mở trực tiếp `tests/fixtures/internship.html` rồi coi là demo độc lập: các
request `/api/v1/test-only/...` cần được Playwright intercept, không có endpoint
tương ứng trên server. Fixture JSON chỉ ở test, không đưa vào production entry.

Full regression từ gốc repo:

```powershell
.\mvnw.cmd -B '-Dfrontend.e2e=true' verify
```

Lệnh này chạy backend unit, core HTTP/migration và Playwright. Không chạy riêng
`npm run test:e2e` rồi giả định backend đã có: CorePlatformIT chuẩn bị auth/data
core riêng. Kiểm contract theo [hướng dẫn checker](../../scripts/contracts/README.md).

## Các nhóm ca QA có thể kiểm ngay

- **Q01 — điều hướng:** HR chọn rõ phân công → đợt → nhóm → quay lại danh sách;
  Mentor chỉ mở nhóm được giao; không tự chọn phân công đầu tiên.
- **Q02 — quyền frontend:** không có assignment, assignment sai user/org, bị revoke,
  chưa hiệu lực/hết hạn, thiếu grant exact hoặc case-only scope đều không mở dữ liệu.
  ADMIN không có quyền HR/Mentor mặc định. Test này không chứng minh guard server.
- **Q03 — phạm vi response:** cohort khác trường/doanh nghiệp/kỳ, group khác cohort
  hoặc nhóm Mentor khác bị từ chối trước khi render tên/số lượng.
- **Q04 — vòng đời tải:** loading, empty, lỗi mạng/403 và retry; đổi phân công/đợt
  không hiển thị response cũ; refresh revoke/hết hạn xóa dữ liệu nhóm đang mở.
- **Q05 — số lượng:** Mentor ID lặp chỉ đếm một người; nhóm hiện hữu với danh sách
  hồ sơ/Mentor rỗng hiển thị 0; không coi đó là lỗi hoặc mất nhóm.
- **Q06 — giao diện:** kích thước 1280/390/320px không tràn ngang; thao tác chọn/xem
  bằng bàn phím; logout/session-expired đưa về đăng nhập và bỏ workspace.
- **Q07 — tương thích owner/consumer:** hai Java consumer tests đọc cùng fixture
  với frontend, đi qua DTO và domain.toDto() của DEV06; giữ nguyên scope IDs,
  Unicode, danh sách rỗng/trùng Mentor và version 0/9007199254740991.
- **Q08 — đường chạy thật đang chờ:** shell không tự gọi endpoint assignment mới,
  không lấy quyền HR/Mentor từ ENTERPRISE membership; gateway mặc định báo pending.

Test tương ứng: `web-demo/tests/internship.unit.spec.ts`,
`web-demo/tests/internship.ui.spec.ts`,
`src/test/java/vn/utbridora/auth/modules/internship/contract/WorkspaceFrontendContractTest.java`.

## Các ca dữ liệu thật chưa thể chạy

**B01 — đăng nhập HR/Mentor và chọn context thật: BLOCKED.** Chưa có API đọc
ContextAssignment của actor hiện tại, provider kiểm parent/revoke/hiệu lực,
transport chọn context và grant runtime `t3:workspace:read`. Đầu mối Team 3/owner
assignment phối hợp T1 xác nhận endpoint, response, permission và cách chọn context.
Không dùng `TaskDtos.AssignmentDto` làm ContextAssignment.

**B02 — đọc danh sách cohort/group từ DB và phân trang: BLOCKED.** DEV06 đã có
domain/service nhưng chưa có list service/scoped query adapter, REST controller,
persistence/provider/migration. Các GET trong contract chưa phải API chạy được.
Cần response business Page đầy đủ và scope đã xác minh trước khi nối gateway.

**B03 — Case/Placement và tác vụ phụ thuộc: DEFERRED theo thông báo DEV06 qua người
thực hiện.** Case runtime và Case Read Port/provider phụ thuộc phần Team 5 dự kiến
tuần 3. Không tạo fake provider production, không trả ACK thành công để giả Case,
không cho mở Task từ event APPROVED/ACTIVE cũ. Đây không phải test PASS hoặc
`@Disabled`: ca runtime chưa có implementation để thực thi.

**B04 — bảo mật HTTP T3, IDOR, revoke và concurrency với DB thật: BLOCKED** bởi
B01/B02 và phần liên quan B03. Unit/mock UI và các test core không thay bằng
chứng server T3 kiểm quyền. Chỉ đánh giá sau khi adapter runtime được owner bàn giao.

Việc DEV05/DEV06 thông báo hoàn thành phần được giao không đồng nghĩa những
provider/API nằm ngoài phần bàn giao của họ đã tồn tại. Không yêu cầu làm toàn
Team 5 trước khi QA kiểm khung UI; cũng không coi B01/B02 đã giải quyết chỉ vì B03
được để lại. Không áp dụng DDL proposal hoặc tự chọn version Flyway để mở khóa.

## Bằng chứng và ghi nhận kết quả

Kết quả DEV lần này: [báo cáo mới nhất](T3_03_TEST_REPORT.md) và
[snapshot 07/11](evidence/T3_03_QA_VERIFICATION_2026_11_07.json).
Log local: `target/t3-03-qa-verify-2026-11-07.log`, `target/frontend-e2e.log`,
`target/surefire-reports`, `target/failsafe-reports`, `web-demo/test-results`.
Không commit raw backend XML/system properties, log SQL hoặc thông tin đăng nhập.

QA ghi riêng: SHA kiểm thử, môi trường, Q01–Q08 PASS/FAIL, B01/B02/B04 BLOCKED,
B03 DEFERRED, lỗi và bằng chứng. Sau khi runtime có sẵn, ghi lượt kiểm thử mới thay
vì sửa snapshot cũ thành PASS. Quyết định QA/nghiệm thu do QA/Lead thực hiện.

## Nội dung để DEV07 gửi kèm bàn giao

> DEV07 bàn giao T3-03 — khung UI HR/Mentor, điều hướng theo phân công và scope.
> Đã cập nhật dependency DEV05 e256903, DEV06 c0850f5; kiểm tra DTO và domain
> tương thích frontend. Nhờ QA03 kiểm các ca Q01–Q08 tại commit mới sau push.
> UI dữ liệu thật còn chờ assignment/context và cohort/group HTTP providers;
> Case runtime theo thông báo DEV06 để giai đoạn Team 5 tuần 3. Chi tiết phạm vi,
> lệnh chạy và bằng chứng ở docs/testing/T3_03_QA_HANDOFF.md.

Người thực hiện bổ sung link commit/PR sau push. Chưa gửi nội dung này tự động.
