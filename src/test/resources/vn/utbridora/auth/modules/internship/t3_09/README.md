# T3-09 / DEV07 — UI nộp bài, nhận xét và báo cáo tuần

Owner: Lại Thái Thuận (DEV07). QA phụ trách: QA03. Cập nhật: 12/11/2026.

**Đã tích hợp GET nhận xét bài nộp của DEV05 tại `4d187ab`.** Student đọc lại
yêu cầu sửa sau reload, xem riêng nhận xét v1/v2; bản chưa review hiển thị đúng 204.
Phần phụ thuộc API của DEV05/DEV06 đã nối đầy đủ cho luồng URL và báo cáo tuần.
Không đánh dấu QA/Lead signoff thay nhóm; scanner/file-mode/T5 liên team vẫn có giới hạn riêng.
Xem kết quả thực thi và giới hạn trong [báo cáo hiện tại](testing/T3_09_TEST_REPORT.md).

## Nguồn và phạm vi

- `docs/plans/UTBridora_Plan.xlsx`, chỉ sheet `CAP NHAT CHUNG`: T3-06 dòng 31;
  T3-07/T3-08/T3-09 dòng 45–47. T3-09 phụ thuộc T3-07 và T3-08; tiêu chí nghiệm thu
  là sinh viên gửi URL, Mentor xem và yêu cầu sửa trên backend thật.
- `docs/handoffs/T3_TUAN2_BAN_GIAO_TUAN3.md`, đặc biệt mục 3, 4.3, 7.
- `docs/contracts/README.md` và Contract Pack v1.0: C.0, CP-WS2, CP-WS3.
  v1.1 Candidate không tự thay thế toàn bộ v1.0; riêng GET Report Review additive
  do DEV06 giao cùng backend được dùng theo CP-WS3 v1.1, không đổi ReportDto.
- T3-06 do người khác triển khai: TaskBoard đã có năm trạng thái, lọc/chi tiết,
  Mentor giao Task, student bắt đầu làm và lịch tháng; sử dụng API Task thật.
  Báo cáo tuần 2 ghi nhận kiểm thử runtime/browser cho các luồng đó. Đây là kết
  quả lịch sử, không phải lượt test backend mới của checkpoint này.
- Theo workbook, Task Comment FR-17 thuộc DEV06/T3-08; ReviewInput.comment thuộc
  DEV05/T3-07. Bàn giao mục 4.1 gộp Comment vào T3-07; không tự sửa ownership.

## Vị trí lưu

- UI và transport: `web-demo/src/modules/internship/`:
  `workflow.ts`, `workflow-api.ts`, `useWorkflowCommand.tsx`,
  `pages/TaskBoard.tsx`, `pages/SubmissionWorkspace.tsx`, `pages/WeeklyReports.tsx`,
  `pages/ReportEvidence.tsx`, `styles.css`. Transport chung `web-demo/src/api.ts`
  bổ sung multipart đúng boundary và đọc Blob, dùng lại session/CSRF/error handling.
- Entry sinh viên hiện có: `web-demo/src/App.tsx`, mục Công việc của tôi;
  thêm key theo userId để không giữ state giữa hai tài khoản.
- Test thực thi: `web-demo/tests/unit/internship-workflow.spec.ts`,
  `web-demo/tests/internship-week3.ui.spec.ts` và fixture `web-demo/tests/fixtures/internship-week3*`.
- Test browser thật: `web-demo/tests/internship-week3-runtime.spec.ts`, khởi động
  bởi `src/test/java/vn/utbridora/auth/T309WorkflowBrowserIT.java`. Java harness
  đặt cùng package các runtime IT T307/T308 hiện có, tạo MySQL và private storage riêng.
- Tài liệu task: [hướng dẫn](guides/T3_09_WORKFLOW_UI.md),
  [báo cáo kiểm thử](testing/T3_09_TEST_REPORT.md), [bàn giao QA](testing/T3_09_QA_HANDOFF.md).
- Minh chứng checkpoint: `evidence/` bên cạnh README này.

Giữ source/test React trong `web-demo` để Vite, TypeScript và Playwright tìm đúng
entry. `src/test/resources/.../t3_09` chỉ chứa tài liệu/minh chứng, cùng quy ước
đã áp dụng cho T3-03. Không đặt báo cáo Markdown vào package Java API.

## Backend đã nhận và điểm còn chờ

1. DEV05 đã có v1/v2, POST review, guards, ledger/audit và V28/V29; DEV06 đã có
   Comment, Report/revision/review, GET review theo revision, file policy và V31.
   [Ghi chú tích hợp](guides/T3_09_BACKEND_INTEGRATION.md) liệt kê API và owner.
2. **Đã tích hợp GET review bài nộp** `/api/v1/t3/submissions/{id}/reviews`.
   UI đọc ReviewDto đã lưu theo từng version; 204 là chưa có review, 401/404 đóng
   dữ liệu riêng tư, 503 hiển thị lỗi và cho retry. Không dùng Comment thay Review.
   Report review cũng đã đọc được từ API DEV06. Xem [bàn giao API mới](guides/T3_09_REVIEW_GET_HANDOFF.md).
3. T1-07 scanner chưa có trong baseline: upload minh chứng trả PENDING; bản nháp
   lưu được, submit/download phải chờ CLEAN. UI không có nút/endpoint tự gán CLEAN.
   File-mode Submission vẫn fail-closed ở DEV05; đường nghiệm thu tuần 3 là URL.
4. V30 thuộc T5 chưa có trên nhánh T3. Test chạy DB riêng từ migrations có trong
   checkout; triển khai DB dùng chung cần Lead tích hợp V30 trước V31. Không bật
   out-of-order hoặc áp V31 vào DB local chung trong phiên này.
5. Test dùng prerequisite Placement ACTIVE/Case IN_PROGRESS synthetic. Không
   chứng minh workflow phê duyệt/kích hoạt T5-07 hoặc antivirus đã nghiệm thu.

## Git

Lần cập nhật GET mới: backup ZIP/patch trong `target/t3-09-before-review-get-20261112`,
stash tracked/untracked, pull --ff-only `59ef6d2` → `4d187ab`, khôi phục merge main
`c3d0dbc` và apply stash thành công, không có conflict. Backup lượt trước vẫn giữ.
Chỉ cập nhật nhánh T3 được yêu cầu; không ghép thêm main mới hoặc nhánh team khác.
Stash 12/11 được giữ, **đã apply, không apply lại**. Chưa commit/push. Git còn
MERGE_HEAD với 12 file main staged; code DEV07/T3-03 chưa stage để review riêng.
Không dùng `git add .` gộp nhầm tất cả. Chi tiết lệnh nằm trong ghi chú tích hợp.
