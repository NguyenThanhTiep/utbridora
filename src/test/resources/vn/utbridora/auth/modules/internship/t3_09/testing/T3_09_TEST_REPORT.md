# Báo cáo DEV T3-09 — 12/11/2026, sau tích hợp GET Review

Owner: DEV07 — Lại Thái Thuận. Nhánh `feature/t3-week3`, HEAD `4d187ab` + merge
main `c3d0dbc` chưa commit + source DEV07 trong working tree.

**DEV PASS — sẵn sàng bàn giao QA cho luồng nộp URL, đọc nhận xét, bình luận và báo
cáo tuần.** API GET của DEV05 đã tích hợp, không còn chờ đọc lại nhận xét bài nộp.
Không thay QA/Lead signoff hoặc GitHub CI. Scanner/file-mode/T5 liên team có giới hạn
riêng ở cuối báo cáo; không tuyên bố nghiệm thu các hệ thống đó.

## Kết quả thực thi cuối

- TypeScript source/test và Vite build: **PASS**, 71 modules.
- Frontend: **73 PASS, 0 fail/error/skip**: T3-09 UI 27, T3-09 unit 8,
  T3-03 UI 18, T3-03 unit 17, student terms unit 3.
- Toàn bộ Maven unit: **1.264 ca, 1.263 PASS, 1 SKIP**, 0 fail/error.
  Skip duy nhất là PrivateFileStorage symlink trên Windows.
- Maven integration được chọn: **41/41 PASS**, 0 skip:
  MigrationUpgradeIT 3; T307SchemaUpgradeIT 1; T307SubmissionRuntimeIT 20;
  T308CommentRuntimeIT 7; T308ReportRuntimeIT 8; T308SchemaUpgradeIT 1;
  T309WorkflowBrowserIT 1.
- T309 harness chạy **4/4 browser thật PASS**, rồi kiểm trạng thái/phiên bản/nội dung
  trong DB. Bốn ca browser nằm trong một Java IT, không cộng thành 45 Java IT.
- **Maven verify tổng hợp BUILD SUCCESS** trên JDK 21.0.13+9, đóng gói JAR thành công.
- Contract bundle `--check`: PASS; verifier **868/868 PASS**.
- Git không còn conflict; diff thường/cached `--check` PASS. 12 file main staged
  vẫn giữ riêng, không commit/push. 11 file T3-03 giữ nguyên nội dung chuẩn hóa,
  7 evidence T3-03 giữ nguyên byte; toàn bộ evidence T3-09 trước lượt GET giữ nguyên.

## Acceptance browser thật

1. Student URL v1 → Mentor REQUEST_REVISION → **student reload đọc comment/verdict**
   → bắt đầu sửa → v2 → GET204, không lẫn review v1 → Mentor ACCEPT → student reload
   đọc ACCEPT/comment v2 → chọn v1 còn nguyên yêu cầu sửa. V1 URL/note bất biến.
   Bình luận student/Mentor đọc lại sau reload; board/calendar cập nhật trạng thái.
2. Report tạo/nộp → Mentor review → student reload đọc feedback → tạo/sửa revision2
   → xem lại revision1 và nhận xét cũ. DB kiểm nội dung và review lịch sử còn nguyên.
3. Upload PDF thật PENDING → lưu draft refs → submit bị FILE_NOT_CLEAN → reload vẫn
   giữ draft và file. Không tự cập nhật CLEAN, không giả lập antivirus.
4. Outsider không đọc submission/review của người khác (404); HR đọc review đã lưu
   nhưng không có mutation controls trong workspace; logout xóa view.

Browser dùng Spring HTTP/session/CSRF/JDBC/MySQL thật, không route interception.
DB và private storage riêng, tài khoản synthetic; không seed/reset DB Docker chung.

## Kiểm thử UI bổ sung

8 ca mới: reload/GET204/GET context không mutation headers; sai submissionId;
sai reviewedVersionNo; 401; 404; delayed response khi đổi version; delayed response
khi đổi context; 503 hiện lỗi và retry. Text review được React escape.
Các ca cũ tiếp tục kiểm URL, phân trang, version, 409 giữ draft, idempotency retry,
CSRF/session, scope, report history/evidence và form 320px.

Lượt UI đầu có 71 PASS/2 FAIL do locator `getByLabel` cũ bắt cả section review mới
lẫn textarea. Đã sửa test sang textbox role/name chính xác; lượt cuối 73 PASS.
Không bỏ ca hay tăng skip. Kết quả 65 ca và backend tại `59ef6d2` là checkpoint
trước, không dùng thay bằng chứng cho GET mới.

## Lệnh tái hiện

Cần Docker Desktop, JDK21, Node/npm, frontend dependencies theo lockfile và Chromium
Playwright; npm phải có trong PATH để Java harness khởi chạy frontend. Chạy từ repo root:

```powershell
npm.cmd --prefix web-demo run build
$env:E2E_REPORT_FILE = '../target/t3-09-review-get-ui.xml'
npm.cmd --prefix web-demo run test:e2e -- internship-week3.ui.spec.ts internship.ui.spec.ts internship.unit.spec.ts internship-workflow.spec.ts student-terms.spec.ts
.\mvnw.cmd -B '-Dit.test=T309WorkflowBrowserIT,T307SubmissionRuntimeIT,T308CommentRuntimeIT,T308ReportRuntimeIT,T307SchemaUpgradeIT,T308SchemaUpgradeIT,MigrationUpgradeIT' verify
```

Không chạy hai bộ browser đồng thời vì cùng Vite port5178. Runtime spec chỉ chạy
qua Java harness cấp E2E_T309_CONFIG; chạy npm riêng thiếu config sẽ skip và không
được coi là acceptance. Bộ Maven trên chọn 7 IT suites, không phải toàn bộ IT repo.
Contract checker: `scripts/contracts/build_contract_bundle.py --check` và
`scripts/contracts/verify_contracts.py --output target/t3-09-review-get-contracts.json`
với Python environment đã cài dependency theo repo.

## Evidence

- [Snapshot GET mới: counts, tên ca, hash source/evidence](../evidence/T3_09_REVIEW_GET_VERIFICATION_2026_11_12.json).
- [JUnit 73 frontend](../evidence/T3_09_REVIEW_GET_UI_RESULTS_2026_11_12.xml).
- [JUnit 4 runtime browser](../evidence/T3_09_REVIEW_GET_RUNTIME_RESULTS_2026_11_12.xml).
- [Contract checks](../evidence/T3_09_REVIEW_GET_CONTRACTS_2026_11_12.json).
- Ảnh `../evidence/t3-09-review-get-submission-20261112.png`,
  `t3-09-review-get-report-history-20261112.png`, `t3-09-review-get-pending-evidence-20261112.png`.
- Log local: `target/t3-09-review-get-{build,ui,verify}.log`,
  `target/t3-09-runtime-browser.log`, Maven surefire/failsafe reports. Không commit
  log/environment của máy; snapshot chỉ giữ counts/tên ca/hash.

Evidence 10/11 và lượt 12/11 trước GET vẫn giữ nguyên. Các trạng thái “chờ GET” trong
JSON lịch sử phản ánh thời điểm cũ; báo cáo này và snapshot REVIEW_GET là hiện tại.

## Giới hạn bàn giao

- T1-07 scanner chưa có: upload mới PENDING; submit/download đòi CLEAN. CLEAN
  download trong fixture UI và dữ liệu kiểm policy backend không chứng minh scanner.
- File-only Submission vẫn fail-closed; phạm vi T3-09 đã kiểm là URL.
- V30 của T5 chưa có trong nhánh T3; phối hợp V30 trước V31 cho DB chung. Phiên này
  chỉ migrate Testcontainers riêng, không bật out-of-order hoặc sửa migration cũ.
- Placement ACTIVE/Case IN_PROGRESS synthetic là prerequisite; chưa kiểm nghiệm
  quy trình duyệt/kích hoạt Placement liên team trên dữ liệu thực.

## Daily report gửi Lead

- Đã làm: bảo toàn code/stash, cập nhật `4d187ab`; hoàn thiện xem review v1/v2 sau
  reload; bổ sung bảo vệ quyền/scope và 8 test; build và kiểm thử nêu trên PASS;
  cập nhật README/API guide/QA handoff/evidence đúng thư mục task.
- Khó khăn đã xử lý: nhánh đang merge main và có file chưa commit; đã backup rồi
  phục hồi đủ, không conflict. Locator UI trùng đã chỉnh và chạy lại PASS.
- Kế hoạch tiếp: người dùng review/commit/push, QA03 kiểm theo handoff; phối hợp
  Lead/T1/T5 cho scanner và kiểm thử liên team. Không còn chờ GET từ DEV05.
