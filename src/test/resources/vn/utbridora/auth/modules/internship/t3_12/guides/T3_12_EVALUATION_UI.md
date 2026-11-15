# Hướng dẫn T3-12 — đánh giá thực tập

## Demo bốn vai trò

1. Chuẩn bị tài khoản thuộc đúng trường/doanh nghiệp, kỳ, Case đang thực tập,
   rubric hợp lệ và chuỗi phân công còn hiệu lực. School cần delegation đúng
   trường/kỳ; membership đơn thuần không thay delegation. Backend kiểm mọi request.
2. Mentor đăng nhập → **Đánh giá thực tập** → chọn phân công → chọn/nhập Case.
   Chọn rubric nếu GET trả 204; điền đủ điểm 0–5, tối đa 2 chữ số thập phân.
   Ví dụ trọng số 40/60, điểm 5/3 cho tổng 76.00. Tạo DRAFT, sửa và lưu trước khi nộp.
3. Minh chứng: chọn tiêu chí, tải PDF/PNG/JPEG ≤10 MiB; tối đa 20 tệp/tiêu chí.
   File tải lên chưa tự gắn bền vững: **Lưu đánh giá** sau upload/xóa liên kết.
   PENDING được lưu trong draft, phải CLEAN trước submit. Có thể nộp không kèm file.
4. School → chọn trường/kỳ → lọc SUBMITTED → mở đánh giá → nhập căn cứ quyết định
   (1–120 ký tự) → **Công bố điểm**. Bật chính sách nhà trường nếu cho phép xem.
5. HR → chọn phân công → chọn doanh nghiệp/kỳ hoặc một hồ sơ → lưu quyền xem.
   Policy CASE ưu tiên kể cả false; policy thiếu có version 0 và phải lưu nếu
   muốn ghi đè false. HR chỉ có phạm vi nhóm/Case không có lựa chọn toàn doanh nghiệp.
6. Student → **Đánh giá thực tập** → chọn Case từ nhiệm vụ hoặc nhập mã đã cấp.
   Chỉ khi published + School bật + HR hiệu lực bật mới thấy điểm/nhận xét/minh chứng.
7. School tạo revision từ SUBMITTED/PUBLISHED với căn cứ và reasonCode dạng
   `[A-Z0-9_]{3,64}`. Mentor sửa bản DRAFT mới. Student tiếp tục thấy published cũ
   đến khi School công bố revision mới. History version khác revisionNo.

Danh sách kỳ có đường nhập mã kỳ dự phòng nếu tài khoản chỉ có quyền đánh giá mà
không có quyền đọc danh mục kỳ. Mã Case/kỳ phải là UUID hợp lệ; nhập mã không cấp quyền.
Sau đổi tài khoản, phân công, trường, kỳ hoặc Case, dữ liệu cũ bị tháo khỏi màn hình.

## API được tích hợp

Tất cả đường dẫn sau có prefix `/api/v1`. Không thêm API backend hoặc đổi DTO.

- Mentor: GET `/t3/rubric-templates?termId=…` (phân trang),
  POST `/t3/evaluations`, PUT `/t3/evaluations/{id}`,
  POST `/t3/evaluations/{id}/submit`.
- Reload/lịch sử Mentor: GET `/t3/evaluations?caseId=…` (204 nếu chưa có),
  GET `/t3/evaluations/{id}?version=N`, GET `/t3/evaluations/{id}/history`.
- School: GET `/t3/school/evaluations?termId=…&state=…`,
  POST `/t3/evaluations/{id}/publish`, POST `/t3/evaluations/{id}/revisions`;
  GET/PUT `/t3/schools/{school}/terms/{term}/result-visibility`.
- HR: GET/PUT `/t3/organizations/{org}/grade-visibility?termId=…[&caseId=…]`.
- Student: GET `/t3/me/results?caseId=…`. Tuyệt đối không lấy điểm bằng API Mentor
  rồi ẩn bằng CSS. Payload HIDDEN phải có evaluation=null; DRAFT/mismatched Case
  bị từ chối hiển thị ngay cả nếu server vô tình trả sai.
- Minh chứng: multipart POST `/files` với `resourceType=EVALUATION`,
  `resourceId=evaluationId`, `purpose=EVIDENCE`; GET metadata/content bằng transport
  có session. UI xác minh binding trước khi hiện/tải, backend giữ quyền quyết định.

Mutation dùng session + CSRF + Idempotency-Key; Mentor/HR gửi
`X-Context-Assignment-Id`. Sửa/submit/publish/revision/policy gửi expectedVersion.
Không lưu token, điểm hoặc bản nháp trong localStorage. Mất phản hồi giữ nguyên
payload/key cho nút thử lại và khóa chỉnh sửa. 409 giữ bản nháp, cần tải lại phiên
bản trước lần gửi mới. 401/403/404 đóng dữ liệu; 5xx hiển thị lỗi, không giả thành
chưa có đánh giá. Student xóa dữ liệu cũ trước reload và xác nhận lại khi cửa sổ focus.

Điểm dự kiến tính bằng số nguyên phần trăm/phần trăm điểm và HALF_UP một lần ở
tổng cuối; tổng đã lưu hiển thị từ server. Không sửa SUBMITTED/PUBLISHED trong UI.

## Chạy lại kiểm thử

Cần JDK 21+, Docker Desktop Linux engine/MySQL 8.4, Node/npm và Chromium Playwright.
Tại PowerShell, cấu hình PATH theo máy; ví dụ máy DEV07 có JDK riêng trong target/tools.

```powershell
cd C:\Users\timmy\utbridora
$env:JAVA_HOME=(Resolve-Path 'target/tools/jdk21/jdk-21.0.13+9').Path
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
$env:DOCKER_HOST='npipe:////./pipe/docker_engine'
.\mvnw.cmd -B '-Dit.test=T312EvaluationBrowserIT,T310EvaluationRuntimeIT,T310SchemaUpgradeIT,T311GradeRuntimeIT,T311SchemaUpgradeIT,Week4ConvergenceIT,Week4SchemaReferenceIT,Week4MigrationCollisionIT,T309WorkflowBrowserIT' verify
cd web-demo
npm.cmd run test:unit
npm.cmd run test:e2e -- internship-week4.ui.spec.ts internship-week3.ui.spec.ts internship.ui.spec.ts internship.unit.spec.ts
npm.cmd run build
```

Harness Java mở backend cổng ngẫu nhiên, MySQL/private storage riêng, truyền tài
khoản ephemeral qua environment cho Playwright. Không chạy spec runtime trực tiếp
rồi coi SKIP là PASS. Cổng Vite mặc định 5178 cần trống. Chạy tuần tự các browser harness.

Nếu build cũ còn SQL V38 ở vị trí cũ, làm sạch compiled output trước khi chạy;
không xóa toàn bộ target của máy DEV07 vì có tools/backup. Phiên này chuyển riêng
`target/classes` và `target/test-classes` sang `target/t3-12-prebuild-20261114` rồi
biên dịch mới. Máy checkout mới/CI có thể dùng `clean verify` bình thường.

Đọc `docs/guides/WEEK4_MIGRATION_RUNTIME.md` trước khi nâng DB thật; không tự repair,
out-of-order hoặc áp migration vào DB chung. Không dùng schema PASS để kết luận
scanner hoặc workflow T5 đã được nghiệm thu.
