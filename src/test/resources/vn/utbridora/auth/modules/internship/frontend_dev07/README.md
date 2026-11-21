# DEV07 — T3 frontend UI/UX, 19/11/2026

**Cập nhật 21/11:** đã pull `e3de97d`, build và rà soát bộ demo Docker mới.
Xem [kết quả cuối và lưu ý bàn giao](final_review_20261121/FINAL_REVIEW.md).
Các mốc chờ ghép/chưa chạy local bên dưới là lịch sử phiên 19–20/11.

Owner: Lại Thái Thuận. Branch: feature/t3-frontend-uiux. Base nhận: 96de49e (DEV05).
DEV07 đã commit `1243e34`. Ngày 20/11 nhận thêm remote `608e21e` (gồm DEV06
`314900a`); bản ghép đang chờ người dùng commit/push. QA03/Lead/CI và staging là xác nhận riêng.

## Nguồn và phạm vi

Đọc kế hoạch `docs/plans/T3_FRONTEND_UIUX_PLAN_2026-11-19.md`, mục 5 bàn giao
`docs/handoffs/T3_FRONTEND_DEV05_TO_DEV06_DEV07_2026-11-19.md`, tài liệu tổng hợp
`docs/plans/UTBridora_UIUX_Dev_Handoff_2026-11-10/uiux-handover/09-consolidated/UTBridora_UIUX_Tong_Hop_Cho_Dev.md`,
workspace specifications, foundations, interaction và responsive specifications.
Handoff PARTIAL là nguồn chức năng/tham chiếu; contract/API quyết định quyền và dữ liệu.
Không dùng số liệu/người minh họa trong Figma làm dữ liệu thật.

UI dùng token hiện hành của web-demo (`--brand`, `--ink`, `--line`, `--muted`),
không đổi màu toàn sản phẩm. Kanban dùng nền semantic info/warning/success từ
Foundations, có nhãn trạng thái để không phụ thuộc màu. Mobile cho cuộn ngang riêng
vùng Kanban/lịch; không để cả trang bị tràn. Breakpoint là lựa chọn triển khai,
không tuyên bố Figma có đầy đủ tablet frames.

## Đối chiếu khoảng trống → triển khai

- **T3-UI-03 / 1432:17091:** trước chưa có tổng quan Mentor. Nay nhóm mở tổng quan
  nhiệm vụ, đếm từng trạng thái theo dữ liệu trang/bộ lọc hiện tại; ghi rõ không
  phải KPI toàn kỳ. Nút chờ duyệt chuyển sang board với SUBMITTED. Không thêm KPI giả.
- **T3-UI-03 / 1432:17284:** board có sẵn API; bổ sung thanh chuyển màn, năm cột
  màu semantic, số assignment theo cột, tìm tiêu đề trong trang, loading/empty.
  GET `/t3/tasks` giữ groupId/state/dueUntil/page, thêm caseId lọc ở server.
  Không kéo thả tự đổi trạng thái. PATCH assignment vẫn qua command/session/CSRF/key/version.
- **T3-UI-03 / 1432:17369:** thêm màn danh sách và khu vực giao nhiệm vụ riêng.
  POST `/t3/tasks` dùng DTO cũ; bản nháp giữ khi đổi màn/nhấn Tải lại bảng việc/409, chỉ reset sau
  tạo thành công hoặc đổi scope. Không tự cấp quyền Mentor từ nhãn UI.
  Không lưu nội dung nháp qua F5/đóng tab hoặc chuyển tài khoản.
- **T3-UI-03 / lịch:** giữ GET `/t3/calendar`, phân trang/tháng và chi tiết task;
  tháng mặc định theo múi giờ trình duyệt, lỗi lịch tách khỏi lỗi danh sách.
  Lọc Case bằng `caseId` của API; có loading/empty. API lịch không có groupId,
  nên nhãn giải thích mục tất cả là lịch trong phạm vi quyền, có thể gồm nhiều nhóm.
- **T3-UI-04 / 1432:17441:** giữ `SubmissionWorkspace`, GET submission/review/comment,
  POST nộp/review/comment và history immutable đã có. Thẻ nhiệm vụ mở chi tiết
  theo assignment; bổ sung trình bày detail/history, không tạo API review thứ hai.
- **T3-UI-04 / 1433:17091:** báo cáo có khu vực riêng, giữ `WeeklyReports` và
  editor/revision/review/evidence hiện hữu. Form có border/spacing/responsive
  nhất quán, nội dung nháp không mất khi chuyển khu vực trong cùng scope.
- **T3-UI-05 / 1433:17212:** giữ đầy đủ Mentor score/evidence/history và Student
  HIDDEN/VISIBLE của T3-12; căn chỉnh các panel/fieldset/criterion theo shell.
  Student portal T2 `EvaluationResults` đã gọi `/t3/me/results`; không thay bằng
  API Mentor, không tạo consumer thứ ba hoặc sửa tính điểm server.

## File chung và ranh giới

- `web-demo/src/modules/internship/pages/TaskBoard.tsx`: navigation/dashboard/list,
  filter/loading/empty, bảo toàn form, lịch.
- `pages/InternshipWorkspace.tsx`: nhóm Mentor mặc định tổng quan; nhóm HR có board
  đọc để theo dõi, không có form giao/review của Mentor. Giữ bốn màn HR DEV05.
- `styles.css`: CSS scoped T3, không sửa shell/avatar/auth hay policy School/HR.
- `App.tsx` giữ menu cha/mũi tên/lối vào assignment do DEV05 bàn giao; menu trong
  nhóm tách từng khu vực công việc, không thêm route/auth/app riêng.
- `web-demo/tests/internship-uiux-dev07.spec.ts`: test mới và ảnh 1440/768/375.
- `web-demo/tests/internship-week3-runtime.spec.ts`: cập nhật thao tác chọn khu vực,
  thêm ca tạo nhiệm vụ thật, lọc Case, reload; kiểm menu mobile/Escape và ảnh shell.
- `web-demo/tests/internship-week4-runtime.spec.ts`: bổ sung kiểm tràn trang và ảnh
  rubric trong app thật ở ba kích thước; giữ assertion nghiệp vụ bốn vai trò.

Các thành phần weekly/submission/evaluation được tái sử dụng có chủ đích; không
viết lại backend, migration, permission hoặc sửa fixture để bỏ qua guard.

## Còn cần phối hợp

Đã nhận và ghép UI DEV06 `314900a` cùng các sửa điều hướng đến `608e21e`.
Chi tiết kiểm tra bản ghép nằm trong [MERGE_VALIDATION.md](MERGE_VALIDATION.md). T2 owner review
Student result; QA03 nghiệm thu bốn vai trò. Scanner T1: file PENDING phải đợi CLEAN;
không fake scanner. KPI toàn kỳ/tên sinh viên mở rộng phụ thuộc dữ liệu có quyền,
không thay bằng dữ liệu minh họa. Không thêm Case closure/coordination thuộc DEV06.

URL staging trong plan chưa truy cập được từ công cụ kiểm tra; không xác định
được image/SHA đang deploy. Không deploy/rebuild `utbridora-local:8088` hoặc seed
DB chung. Runtime test dùng container MySQL riêng.

Folder UIUX handoff do người dùng cung cấp đang untracked từ đầu phiên; không
di chuyển/xóa/stage toàn folder. Hồ sơ DEV07 lưu ở thư mục này theo quy ước cũ.

Xem [báo cáo kiểm thử](TEST_REPORT.md), [bàn giao QA](QA_HANDOFF.md) và thư mục `evidence/`.
