# T3-03 — Kiểm thử DEV và daily report

## Cập nhật mới nhất — tích hợp nhánh DEV05/DEV06, 06/11/2026

**DEV07 — Lại Thái Thuận | Team 3 | T3-03 | UTC+7**

Đã fetch/pull, cất và khôi phục đầy đủ thay đổi T3-03. `origin/main` vẫn là
`0cc1daf`; các thay đổi mới nằm trên nhánh của DEV05/DEV06:

- `feature/t3-placement-inbox` tại `53120d7`: fast-forward vào nhánh T3-03,
  bao gồm commit policy `3eaa52c` và tài liệu thiết kế/test plan.
- `feature/t3-cohort-group-domain` tại `01f0c48`: merge local bằng `--no-commit`,
  không có conflict. **Đang chờ người thực hiện tạo merge commit.**

Không tạo commit/push/PR. Sáu file DEV06 đang staged do Git merge; phần DEV07 và
manifest vẫn chưa stage. Nhánh hiện có HEAD `53120d7`, MERGE_HEAD `01f0c48`.
Stash mới `3a0625ed1efc136b36b1217e9241281eff2b133c` được giữ cùng stash cũ.
Mọi file T3-03 trước lượt này đã được đối chiếu Git blob ngay sau restore và khớp.
**Không apply stash lần nữa. Không chạy merge/pull tiếp khi merge hiện tại chưa commit.**

### Những gì DEV07 đã thay đổi sau khi đọc hai nhánh

- Sửa số Mentor trên card/chi tiết nhóm: đếm userId duy nhất. DEV06 `GroupDto`
  cho phép Mentor trùng ID; độ dài mảng có thể lớn hơn số người được phân công.
- Thêm fixture wire JSON dùng chung giữa frontend và test Java. Hai test
  `WorkspaceFrontendContractTest` đọc/serialize bằng chính DTO của DEV06, đối
  chiếu mọi field và quan hệ cohort/group. Kiểm tra nhóm rỗng, Mentor trùng ID,
  version 0 và 9007199254740991. Frontend test dùng cùng dữ liệu này.
- Thêm hai UI regression cho Mentor trùng ID và nhóm có danh sách thành viên rỗng.
- Tái sinh `docs/contracts/v1.1/manifest.json` bằng script có sẵn: **chỉ thêm 5
  sourceHashes** của Java DEV05/DEV06. Không sửa hash nguồn cũ, schema, API,
  registry hoặc trạng thái phê duyệt. Đây là cập nhật cần thiết để job contracts
  không báo bundle drift sau khi thêm source backend.
- Giữ `pendingWorkspaceGateway`. Hai nhánh mới chỉ cung cấp DTO/policy/test;
  chưa có API/controller/service/repository/migration T3 để nối dữ liệu thật.

### Kiểm thử lượt tích hợp

- **PASS — frontend build** và type-check strict cho test/harness, gồm JSON import.
- **PASS — 35/35 test T3** chạy riêng: 17 unit + 18 UI/shell fixture, 0 skipped.
- **PASS — bundle check và 868/868 contract checks** sau cập nhật 5 hash manifest.
- **PASS — 174/174 backend unit**: 28 core + 99 DEV05 + 45 DEV06 + 2 consumer
  compatibility DEV07.
- **PASS — full Maven verify:** `mvnw.cmd -B -Dfrontend.e2e=true verify`, JDK
  21.0.13+9, **BUILD SUCCESS** lúc **20:36:37 UTC+7**, khoảng **1 phút 50 giây**.
- **PASS — 49/49 integration/migration core** với MySQL 8.4 Testcontainers.
- **PASS — 49/49 Playwright:** 17 unit T3 + 18 UI/shell fixture T3 + 14 core/UX,
  khoảng **39 giây**. Không có failure/error/skipped trong lượt cuối.
- **PASS — kiểm tra bundle frontend:** không có marker fixture/test-only trong
  production bundle. Không thay dependency/lockfile hoặc bỏ test để đạt PASS.
- **PASS — Docker cleanup:** không còn container chạy sau full verify.

[Bằng chứng lượt tích hợp DEV05/DEV06](evidence/T3_03_DEPENDENCY_VERIFICATION_2026_11_06.json)
ghi SHA hai nhánh, trạng thái merge chưa commit, hash source, môi trường và kết
quả từng suite. Log cuối ở `target/t3-03-dev05-dev06-verify.log`,
`target/frontend-e2e.log`; chỉ lưu báo cáo tổng hợp, không commit log/system properties.
Các test integration hiện có là core/migration; **chưa có test HTTP T3 thật** vì
owner chưa cung cấp runtime. Contract test mới chỉ kiểm DTO owner/consumer.

Lượt đầu full verify thất bại trước khi Playwright thu thập test vì import JSON
thiếu `with { type: 'json' }` trên Node 24. Đã sửa cú pháp và chạy riêng 35 test
T3 PASS trước khi chạy lại full verify. Không tắt/bỏ qua test. Log lượt đầu giữ
ở `target/t3-03-dev05-dev06-verify-first.log` và
`target/t3-03-dev05-dev06-frontend-first.log` (ignored).

### Phụ thuộc và daily report bổ sung

**Đã làm:** Tích hợp source DEV05/DEV06 vào nhánh task, bảo toàn code cũ; đối chiếu
DTO owner/consumer, sửa đếm Mentor, bổ sung test tương thích Java/frontend và
regression UI, cập nhật manifest cho CI và tài liệu bàn giao.
Build/type-check, 868 contract checks, 174 backend unit, 49 integration/migration
và 49 Playwright đều PASS ở lượt cuối.

**Còn chờ:** API đọc `ContextAssignment` của người đăng nhập và cơ chế chọn
context; API cohort/group đúng scope; quyền runtime `t3:workspace:read` phối hợp
T1. DEV05 vẫn thiếu consumer/port/persistence tiếp nhận Placement và phụ thuộc
nguồn T5; DEV06 mới có DTO/policy, chưa có REST runtime. Không đồng nhất
`TaskDtos.AssignmentDto` với phân công HR/Mentor `ContextAssignment`.

**Tiếp theo:** Owner bàn giao contract/API runtime rồi DEV07 nối gateway, xử lý
phân trang và kiểm thử HR/Mentor thật với sai scope, revoke/hết hạn; QA03 kiểm
tra độc lập. Sau người thực hiện tự commit/push, CI/PR sẽ chạy theo quy trình team.

**Trạng thái:** Khung UI và tương thích DTO đã chuẩn bị; chưa thể kết luận toàn
bộ T3-03 với dữ liệu thật hoàn tất. Chưa có QA signoff. Hai nhánh dependency chưa
vào main, nên PR của nhánh này sẽ chứa cả commit DEV05/DEV06 cho tới khi main có
các dependency đó; cần ghi rõ cho reviewer.

### Nhật ký Git lượt tích hợp

Các lệnh thực tế, theo thứ tự (lần merge đầu bị Git chặn, không thay đổi index):

```powershell
git stash push -u -m "wip: T3-03 before DEV05 DEV06 integration 2026-11-06"
git fetch origin
git switch main
git pull --ff-only origin main
git switch feature/t3-03-hr-mentor-workspace
git merge --ff-only main
git merge --ff-only origin/feature/t3-placement-inbox
git merge --no-ff --no-commit origin/feature/t3-cohort-group-domain
# Lần trên bị chặn: chưa có danh tính Git. Khôi phục code để làm phần độc lập:
git stash apply 'stash@{0}'
# Sau khi người thực hiện cung cấp email, cấu hình riêng repo:
git config --local user.name "Lại Thái Thuận"
git config --local user.email "laithaithuan14@gmail.com"
git merge --no-ff --no-commit origin/feature/t3-cohort-group-domain
# Sau tái sinh bundle, 4 file sau chỉ đổi LF/CRLF và Git blob khớp index;
# khôi phục định dạng checkout, giữ manifest có 5 hash mới:
git restore --worktree -- docs/contracts/v1.1/audit_registry.csv docs/contracts/v1.1/examples.json docs/contracts/v1.1/permission_matrix.csv docs/contracts/v1.1/schemas.json
```

`fetch` chỉ lấy nhánh/commit remote. `--ff-only` tiến nhánh đến commit đã tồn tại,
không tạo commit mới. `--no-ff --no-commit` ghép hai lịch sử nhưng dừng trước khi
tạo merge commit; Git tự stage file được ghép. `stash apply` giữ bản backup.
Cấu hình tác giả chỉ áp dụng repo này, không thay cấu hình global.

Hướng dẫn hoàn tất hai commit và push ở cuối tài liệu là lệnh cho người thực
hiện, chưa được AI chạy. Kết quả 13:20 ngày 06/11 bên dưới là lịch sử trước khi
nhận hai nhánh, không thay bằng chứng của lượt tích hợp này.

## Lịch sử — cập nhật main và Docker lúc 13:20, 06/11/2026

**DEV07 — Lại Thái Thuận | Team 3 | T3-03 | UTC+7**

Đã stash cả file mới, pull `origin/main`, cập nhật nhánh task và khôi phục công
việc. `main` vẫn là `0cc1daf`: không có commit mới hơn bản đã đọc ngày 05/11.
Người thực hiện xác nhận thông báo của lead có thể là bản này. Không có conflict;
19 file được đối chiếu với Git blob của stash ngay sau restore đều khớp nội dung
(Git chuyển LF/CRLF ở một số file). Sau đó chỉ cập nhật tài liệu/bằng chứng kiểm
thử; không thay code chức năng, contract hay dependency khai báo.

Các lệnh Git đã thực hiện, theo thứ tự:

```powershell
git stash push -u -m "wip: T3-03 before main update 2026-11-06"
git switch main
git pull --ff-only origin main
git switch feature/t3-03-hr-mentor-workspace
git merge --ff-only main
git stash apply 'stash@{0}'
```

`-u` cất cả file chưa được Git theo dõi, trừ file ignored. `--ff-only` chỉ cập
nhật khi có thể tiến thẳng lịch sử, không tạo merge commit. `apply` khôi phục
nhưng giữ bản dự phòng. Stash `1ad11ebdce9cf2f5e2c92e52cc33195e19fdfeee` còn được
giữ; **không apply lần nữa vì code đã khôi phục**. Chưa stage/commit/push/PR.

### Kết quả kiểm tra ngày 06/11

- **PASS — build frontend:** TypeScript + Vite production build.
- **PASS — contract bundle:** `build_contract_bundle.py --check`; không sửa bundle.
- **PASS — contract validation:** **868/868**, 0 failed. Đây là schema/static/pure
  fixture checks, không xác nhận runtime T3 hoặc phê duyệt candidate v1.1.
- **PASS — full Maven verify trên JDK 21:** `mvnw.cmd -B -Dfrontend.e2e=true verify`,
  **BUILD SUCCESS**, hoàn tất **13:20:47**, thời gian khoảng **2 phút 32 giây**.
- **PASS — backend unit:** **28/28**, 0 failure/error/skipped.
- **PASS — backend integration/migration:** **49/49** (CorePlatformIT 48,
  MigrationUpgradeIT 1), 0 failure/error/skipped; MySQL **8.4** qua Testcontainers.
- **PASS — Playwright:** **47/47**, 0 failure/error/skipped, khoảng **38,2 giây**.
  Gồm 17 unit T3, 16 UI/shell fixture T3 và 14 test core/UX hiện có. Không gọi
  toàn bộ 47 test là E2E T3 với backend thật.
- **PASS — Docker cleanup:** không còn container chạy sau kiểm thử.

Môi trường: Temurin **21.0.13+9**, Node **24.22.1**, npm **11.6.2**; Docker Desktop
**4.86.0**, Engine **29.7.2**, context `desktop-linux`. JDK 21 được tải từ Adoptium,
kiểm tra SHA-256 và đặt trong `target/tools`; JAVA_HOME/PATH chỉ đặt cho tiến trình
kiểm thử. Python dependencies kiểm tra contract nằm trong venv riêng dưới `target`.
Các công cụ/log này được Git ignore. Testcontainers dùng DB tạm và đã tự dọn;
không tạo bộ dữ liệu T3 thật hay chạy demo backend thường trực.

[Bằng chứng tổng hợp ngày 06/11](evidence/T3_03_FULL_VERIFICATION_2026_11_06.json)
chỉ lưu kết quả suite, môi trường và hash source; không sao chép system properties
hoặc log SQL vào repo. Log local: `target/t3-03-full-verify-2026-11-06.log`,
`target/frontend-e2e.log`; XML local ở `target/surefire-reports`,
`target/failsafe-reports`, `web-demo/test-results/e2e.xml`. Contract checker dùng
`checkedAtUtc` để xác định thời điểm chạy vì trường `date` đang cố định ở 05/11.

### Daily report 06/11 để gửi lead

**Đã làm:** Bảo toàn code T3-03 bằng stash, kiểm tra/cập nhật main và khôi phục
nhánh task. Đọc lại contract pack, xác nhận DTO T3 liên quan không đổi. Thiết lập
JDK 21 cho kiểm thử, kiểm tra Docker/MySQL và chạy full regression: frontend build,
868 contract checks, 28 backend unit, 49 integration/migration và 47 Playwright
đều PASS. Cập nhật tài liệu bàn giao và bằng chứng kiểm thử.

**Khó khăn/phụ thuộc:** Đã xử lý vướng Docker/JDK của ngày 05/11. Còn chờ DEV05
(T3-01: Inbox/Case nhận Placement), DEV06 (T3-02: cohort/group/task API); Team 3 cần
chốt API đọc phân công HR/Mentor và cơ chế chọn context. T1 cần hỗ trợ runtime
`t3:workspace:read`; T5 cung cấp Placement/Term cho luồng dữ liệu thật. Xem
[điểm bàn giao cụ thể](../guides/T3_03_HR_MENTOR_WORKSPACE.md#điểm-bàn-giao-cho-dev05dev06-và-t1).

**Kế hoạch tiếp theo:** Sau khi backend/contract liên quan được bàn giao, nối
gateway thật, chạy test quyền/scope/revoke/hết hạn với HR/Mentor thật rồi bàn giao
QA03. Người thực hiện tự review, commit và push; PR/CI thực hiện sau.

**Trạng thái:** Đang làm — khung UI và self-test PASS, chờ tích hợp backend/QA.
Full regression core PASS không đồng nghĩa luồng T3 thật hoặc SRS đã nghiệm thu.

## Lịch sử kiểm thử ngày 05/11/2026

Phần dưới lưu nguyên kết quả ngày 05/11. Trạng thái thiếu Docker/JDK 21 đã được
giải quyết bởi lượt kiểm tra 06/11 ở trên; phần chờ runtime T3/QA vẫn còn áp dụng.

Ngày: **05/11/2026**, múi giờ UTC+7. Người thực hiện: **DEV07 — Lại Thái Thuận**.
Nhánh: `feature/t3-03-hr-mentor-workspace`. Nền: `0cc1daf` (pull fast-forward từ
`origin/main`, trước đó local ở `faae044`). Toàn bộ thay đổi T3-03 chưa commit/push.

## Kết quả thực thi

- **PASS — build frontend**: `npm run build` (`tsc -b` + Vite production build).
- **PASS — 33/33 test T3**: `npm run test:internship`, gồm **17 unit** và **16 UI/shell
  fixture**; 0 failed, 0 skipped. Lượt cuối bắt đầu **16:40:14**, khoảng **8,6 giây**.
- **PASS — type-check test/harness**: chạy TypeScript strict riêng cho hai file test
  và harness (build thông thường chỉ type-check `src`/Vite config).
- **PASS — 28/28 unit test backend core**: `mvnw.cmd test`, 0 failure/error/skipped,
  hoàn tất **16:44:00**. Dùng JDK **25.0.4.1** sẵn trong extension Java của VS Code,
  compiler `--release 21`; chỉ đặt JAVA_HOME cho tiến trình chạy test. Không thay
  JDK hệ thống hoặc pom.xml. Đây chưa phải kiểm chứng chạy trên JDK 21 như CI.
- **PASS — kiểm tra responsive**: test 1280px, 390px, 320px; ảnh desktop/mobile đã
  xem lại, không tràn ngang và mã scope đầy đủ có thể đọc dưới selector.
- **PASS — kiểm tra bundle**: không tìm thấy marker `test-only`,
  `SYNTHETIC-TEST-ONLY`, `mentor@example.test` trong `web-demo/dist`.
- **PASS — `git diff --check`** trước bàn giao.

Môi trường frontend: Windows, Node 24.22.1, npm 11.6.2, TypeScript 5.9.3,
Vite 7.3.7, Playwright 1.64.1, Chromium headless build 1248. Dependencies cài bằng
`npm ci` theo lockfile có sẵn; không thay phiên bản khai báo/lockfile.

Các lượt đầu gặp lỗi môi trường: thiếu Chromium, JAVA_HOME chưa cấu hình và quyền
cache trong sandbox. Đã cài browser test và sử dụng JDK có sẵn để chạy lại; các kết
quả PASS phía trên là từ lượt chạy sau khi xử lý, không bỏ qua test để lấy PASS.

## Phạm vi đã kiểm thử

Unit kiểm tra menu discovery khác quyền truy cập; HR/Mentor/coordinator; actor
khác; membership bị thu hồi; sai org; grant sai role/permission; ngày hiệu lực,
hết hạn đúng boundary; thiếu group cho Mentor; case-only scope; cohort sai
enterprise/school/term; group không thuộc cohort/mentor; gateway mặc định đóng;
GET helper dùng đúng URL, session cookie, AbortSignal và shape business Page.

UI kiểm tra chọn phân công/đợt/nhóm và quay về; Mentor đúng nhóm; từ chối phản hồi
nhóm/trường ngoài scope; không gọi dữ liệu khi chưa có phân công hợp lệ; empty;
loading/error/retry; 403; response cũ khi đổi assignment/cohort; hết hạn hoặc
refresh sau revoke làm ẩn dữ liệu; responsive/keyboard; shell pending; logout;
ADMIN không tự có menu; session-expired đưa về đăng nhập.

## Chưa kiểm thử hoặc chưa nghiệm thu

- **NOT RUN — backend integration, migration và full E2E với MySQL Testcontainers**:
  chưa có Docker CLI/engine sẵn dùng trong môi trường kiểm tra. Chưa chạy
  `mvnw.cmd -Dfrontend.e2e=true verify`; kết quả unit không thay kết quả này.
- **NOT RUN — luồng T3 backend thật**: checkout chưa có API assignment/cohort/group
  runtime, chưa có cơ chế chọn context và permission mới. UI thật dừng ở trạng
  thái chờ tích hợp. Test fixture không chứng minh authorization ở server.
- **NOT RUN — GitHub CI, QA03 và nghiệm thu SRS**: chưa commit, push hoặc tạo PR;
  không dùng kết quả DEV để tự đổi trạng thái QA/Leader.

FR-07/FR-08 chỉ được chuẩn bị khung trong T3-03, chưa triển khai và nghiệm thu toàn
bộ dashboard, lịch, board hoặc thống kê. Đề xuất giữ task **Đang làm — khung UI
xong, chờ API phân công/tích hợp và QA**. Không sửa trạng thái trên Excel.

## Bằng chứng

- [Snapshot JSON: kết quả, môi trường và hash source](evidence/T3_03_DEV_VERIFICATION.json).
- [JUnit XML của 33 test T3](evidence/T3_03_UI_UNIT_RESULTS.xml).
- [Ảnh UI fixture desktop 1280px](evidence/t3-03/groups-1280.png).
- [Ảnh UI fixture mobile 320px](evidence/t3-03/groups-320.png).

Log đầy đủ có thể tạo lại bằng các lệnh trong [hướng dẫn T3-03](../guides/T3_03_HR_MENTOR_WORKSPACE.md).
Kết quả Surefire local ở `target/surefire-reports`; không đưa system properties
trong XML backend vào repo. Ảnh sử dụng dữ liệu tổng hợp, không phải nhóm thực tập thật.

## Daily report để gửi lead

**DEV07 — Lại Thái Thuận | Team 3 | 05/11/2026 | T3-03**

**Đã làm:**

- Đối chiếu task T3-03 trong sheet CAP NHAT CHUNG với SRS FR-07/FR-08, contract và
  hướng dẫn BE/FE/Git mới nhất trong repo.
- Pull main đến `0cc1daf`, tạo nhánh `feature/t3-03-hr-mentor-workspace`.
- Hoàn thiện khung UI HR/Mentor: chọn phân công, chọn đợt thực tập, xem nhóm và
  thông tin nhóm. Tái sử dụng shell/auth của T1.
- Bổ sung kiểm tra phạm vi, loading/empty/error/retry, xử lý phản hồi cũ và phân
  công hết hạn; không hiển thị dữ liệu mock trong app thật.
- Build frontend PASS; 17 unit + 16 UI fixture T3 PASS; 28 unit backend core PASS
  trên JDK 25, target 21. Kiểm tra giao diện desktop/mobile và viết tài liệu bàn giao.

**Khó khăn/phụ thuộc:**

- Chưa có API đọc assignment HR/Mentor và cơ chế chọn context từ DEV05/DEV06;
  permission `t3:workspace:read` cũng chưa có trong runtime core. App đang chờ tích hợp.
- Chưa chạy integration/full E2E do môi trường chưa có Docker sẵn dùng; vẫn cần
  chạy kiểm tra theo môi trường chuẩn JDK 21 và QA03 xác nhận.

**Kế hoạch tiếp theo:**

- Nhờ DEV05/DEV06 phối hợp chốt API assignment, context và nhóm đúng scope; T1
  xác nhận permission liên quan. Nối gateway thật sau khi thống nhất contract.
- Chạy luồng đăng nhập HR/Mentor thật và các ca sai nhóm, sai tổ chức, revoke,
  hết hạn; bàn giao QA03 kiểm thử.
- Review diff, tự commit/push nhánh, sau đó mở PR theo quy trình nhóm.

**Trạng thái:** Khung UI và self-test đã xong; còn chờ backend/tích hợp/QA.
Chưa commit/push/PR. Chưa nghiệm thu SRS.

## Chuẩn bị commit/push do người thực hiện tự làm

**Áp dụng cho trạng thái merge chờ commit hiện tại.** Làm từng bước, dừng nếu có
lỗi. Đọc diff trước; file mới cần mở xem vì `git diff` không liệt kê nội dung file
untracked. Đầu tiên hoàn tất merge dependency, giữ lịch sử/tác giả DEV05/DEV06.
Hiện index chỉ có 6 file DEV06; thêm manifest 5 hash cho cùng commit tích hợp.

```powershell
git status --short --branch
git diff --check
git diff --cached --stat
git diff --cached
git diff -- docs/contracts/v1.1/manifest.json
git add -- docs/contracts/v1.1/manifest.json
git diff --cached --check
git commit -m "merge: integrate T3 placement and cohort group dependencies"
```

Tiếp theo commit riêng phần DEV07 (gồm consumer test Java do DEV07 thêm). Không
dùng `git commit -a` cho merge phía trên; không `git add .` để tránh gom lẫn việc.

```powershell
git status --short --branch
git diff -- web-demo/src/App.tsx web-demo/package.json docs/README.md

git add -- web-demo/src/App.tsx web-demo/package.json web-demo/src/modules/internship web-demo/tests/internship.unit.spec.ts web-demo/tests/internship.ui.spec.ts web-demo/tests/fixtures/internship.ts web-demo/tests/fixtures/internship-wire.json web-demo/tests/fixtures/internship.html web-demo/tests/fixtures/internship-harness.tsx src/test/java/vn/utbridora/auth/modules/internship/contract/WorkspaceFrontendContractTest.java docs/README.md docs/guides/T3_03_HR_MENTOR_WORKSPACE.md docs/testing/T3_03_TEST_REPORT.md docs/testing/evidence/T3_03_DEV_VERIFICATION.json docs/testing/evidence/T3_03_FULL_VERIFICATION_2026_11_06.json docs/testing/evidence/T3_03_DEPENDENCY_VERIFICATION_2026_11_06.json docs/testing/evidence/T3_03_UI_UNIT_RESULTS.xml docs/testing/evidence/t3-03
git diff --cached --check
git diff --cached --stat
git diff --cached

git commit -m "feat(internship): add scoped HR and mentor workspace shell"
git push -u origin feature/t3-03-hr-mentor-workspace
```

Các lệnh stage/commit/push trong phần này chỉ là hướng dẫn, chưa được AI thực thi.
Sáu file DEV06 có sẵn trong index do thao tác merge, không phải stage code DEV07.
PR/review/CI/merge lên main/xóa nhánh vẫn do người thực hiện xử lý sau.
