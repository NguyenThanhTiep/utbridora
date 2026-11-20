# Bàn giao frontend T3 từ DEV06 sang DEV07

Ngày 19/11/2026 · Người bàn giao **Lê Nguyễn Nhật Duy (DEV06)** · Nhánh `feature/t3-frontend-uiux`.

**T3-UI-05 phần HR và Nhà trường đã hoàn thành ở mức DEV. T3-UI-06 đã có hai màn đọc hồ sơ.** DEV07 nhận code và tài liệu trong cùng commit trên nhánh `feature/t3-frontend-uiux`. QA03 chưa nghiệm thu.

## Màn DEV07 nhận tích hợp

| Màn trong Không gian thực tập | Hành vi đã kiểm tra |
|---|---|
| HR → Đánh giá thực tập → chọn phân công HR → Cấu hình hiển thị kết quả | HR lưu và tải lại quyền xem điểm theo doanh nghiệp/kỳ hoặc theo hồ sơ. Hồ sơ chưa có chính sách riêng sẽ kế thừa. Khi đã lưu riêng, giá trị của hồ sơ được ưu tiên kể cả khi tắt. Hai điều kiện do trường quản lý hiện “Chưa xác nhận” vì API HR chỉ trả chính sách HR. |
| Nhà trường → Đánh giá thực tập → chọn kỳ | Lọc đánh giá theo trạng thái hoặc mã hồ sơ, xem phiên bản, nhập căn cứ công bố, tạo revision và chỉnh chính sách trường theo quyền. Khi có xung đột phiên bản, tải lại vẫn giữ nội dung đang nhập trong cùng hồ sơ. |
| HR → Theo dõi Case | Chọn đợt, chọn hồ sơ đã xếp nhóm để xem trạng thái và phiên bản hiện tại. Danh sách chỉ bao gồm các nhóm HR đọc được. |
| Nhà trường → Theo dõi hồ sơ thực tập | Tra theo mã Case trong trường/kỳ được ủy quyền. Khi đổi mã hoặc mất quyền, màn xóa chi tiết hồ sơ cũ. |

## Điểm nối với phần Mentor và Student của DEV07

1. **Mentor nộp → School công bố.** Dùng một hồ sơ có rubric và Mentor được phân công. Khi Mentor gửi đánh giá sang `SUBMITTED`, mở bộ lọc **Chờ công bố** bên trường, nhập căn cứ rồi công bố.
2. **Kiểm tra bên Student.** Dùng `GET /t3/me/results` của chính sinh viên. Điểm hiển thị khi có bản `PUBLISHED`, chính sách trường bật và chính sách HR có hiệu lực bật. Thử tắt riêng hồ sơ trong khi doanh nghiệp/kỳ đang bật. Kết quả mong đợi là `HIDDEN` và `evaluation=null`.
3. **Kiểm tra revision.** Tạo revision bên trường, sửa và nộp lại bên Mentor. Nếu hai chính sách vẫn bật, Student thấy điểm đã công bố trước đó cho tới khi trường công bố bản mới.

Điều hướng chung nằm ở `web-demo/src/App.tsx`. Trong `web-demo/src/modules/internship/pages/`, phần School/HR dùng `EvaluationsPage.tsx` và `SchoolGradePanels.tsx`. `EvaluationPanels.tsx` giữ các export hiện có cho luồng Mentor/Student. Hai màn Case dùng `HrCaseTracker.tsx`, `SchoolCaseLookup.tsx` và phần hiển thị chung `CaseStatus.tsx`.

## Dữ liệu demo và bằng chứng

- Trên máy DEV06, mở `http://localhost:8088`. Dùng `hr@example.test`, chọn đợt `DEMO-W1` ở màn Theo dõi Case. Với `school@example.test`, mở Theo dõi hồ sơ thực tập rồi nhập mã Case lấy từ dữ liệu demo trên máy đang dùng. Mật khẩu do bộ seed local tạo.
- Bộ demo 8088 trên máy DEV06 còn thiếu rubric, đánh giá đã nộp và ủy quyền công bố/quản lý chính sách điểm của trường. Luồng bốn vai trò đã chạy trên bộ dữ liệu riêng bằng `.\mvnw.cmd -Dtest=T312EvaluationBrowserIT test` từ thư mục gốc.
- Build đạt, 15/15 unit test, 44/44 UI test với API mô phỏng và 4/4 ca trình duyệt với Spring/MySQL thật đạt. Hai báo cáo local nằm ở `target/dev06-ui-regression.xml` và `target/t3-12-runtime-browser.xml`.

## Các mục còn chờ backend

T3-UI-06 còn thiếu API cho dashboard/KPI Nhà trường, danh sách toàn bộ Case theo đợt, lịch sử trạng thái Case, đánh giá chất lượng doanh nghiệp, yêu cầu phối hợp và đóng Case thường/sớm. Riêng màn đánh giá của trường hiện đọc được revision/version hiện tại, còn lịch sử đầy đủ cần API cho vai trò School. DEV06 đã ghi route, contract và owner cần phối hợp ở mục 4 của bản rà soát. [Bản rà soát DEV06](../reviews/DEV06_FRONTEND_P0_HANDOFF_20261119.md) · [Phân công frontend T3](../plans/T3_FRONTEND_UIUX_PLAN_2026-11-19.md)
