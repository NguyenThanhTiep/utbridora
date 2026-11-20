# DEV07 — hướng dẫn kiểm tra frontend T3

Ngày: 19/11/2026. T3-UI-03/04 và Mentor/Student của T3-UI-05.
Đây là bàn giao DEV self-test; QA03/Lead/CI xác nhận độc lập trên commit sau khi DEV07 push.

## Điểm vào và thao tác

1. Đăng nhập Mentor có assignment còn hiệu lực → **Không gian thực tập** → chọn
   phân công/đợt → **Xem nhóm**. Mặc định mở **Tổng quan nhiệm vụ**. Số liệu là
   assignment trong trang hiện tại; không dùng làm KPI toàn kỳ.
2. **Bảng Kanban**: năm trạng thái; lọc trạng thái, Case, hạn; tìm tiêu đề chỉ
   trong trang. **Danh sách nhiệm vụ** dùng cùng dữ liệu và mở cùng chi tiết.
   Cột Kanban cuộn ngang riêng trên mobile, không kéo thả thay trạng thái.
3. **Giao nhiệm vụ mới**: nhập tiêu đề/mô tả/hạn/ưu tiên, chọn Case trong nhóm.
   Lưu thành công mới xóa form. Đổi khu vực rồi quay lại vẫn giữ bản nháp.
   Khi 409, giữ nội dung, xem thông báo và tải dữ liệu mới trước khi thử lại.
   F5/đóng tab/đổi scope không có cơ chế lưu nháp local.
4. **Lịch công việc**: chọn tháng và Case. Mục tất cả là phạm vi quyền của
   assignment, có thể gồm nhiều nhóm vì endpoint không có groupId. Chọn một
   nhiệm vụ để đọc chi tiết; lỗi lịch không làm giả thành danh sách rỗng.
5. Student → **Nhiệm vụ & sản phẩm** → bắt đầu/nộp URL v1. Mentor mở bài → yêu cầu
   sửa. Student reload, xem nhận xét → nộp v2. Mentor ACCEPT; cả hai đọc lại
   lịch sử v1 và nhận xét đúng version. HR chỉ đọc, không có nút review Mentor.
6. Student → **Báo cáo & đánh giá** để tạo/sửa/nộp báo cáo; Mentor → **Báo cáo tuần**
   → **Mở báo cáo tuần** → chọn Case để nhận xét. Sau reload, phản hồi vẫn còn;
   revision không sửa nội dung bản lịch sử. PENDING có thể lưu nháp, không được nộp.
7. Mentor → **Đánh giá thực tập** → phân công/Case/rubric. Ví dụ trọng số 40/60,
   điểm 5/3 cho tổng 76.00 theo server. Kiểm nháp, submit, lịch sử và minh chứng.
   School publish, School/HR bật policy theo đúng owner. Student xem ở portal
   **Báo cáo & đánh giá → Kết quả đánh giá**; thiếu một gate thì HIDDEN, không có điểm.
   Revision mới chưa publish phải giữ kết quả published cũ; CASE policy ưu tiên.

## Kiểm tra quyền và hiển thị

- 1440/768/375 px: không tràn toàn trang, các trường và nhãn đọc được; riêng Kanban/lịch cho cuộn ngang.
- Tab/Enter chọn khu vực; focus rõ. Mobile mở menu, Escape đóng và trả focus về nút menu.
- Đổi assignment/Case, session hết hạn, logout: không hiển thị dữ liệu của scope cũ.
- 401/403/404 đóng dữ liệu riêng; 409 giữ draft; mất response thử lại cùng operation/key.
- Student dùng `/api/v1/t3/me/results`, không gọi API Mentor để xem điểm.
- Các ảnh/runtime ở đây dùng tài khoản và dữ liệu synthetic trong DB riêng; không dùng dữ liệu thật để demo.

## Phối hợp trước nghiệm thu

- Đã nhận UI DEV06 `314900a` và sửa điều hướng đến `608e21e`; xem MERGE_VALIDATION.md cho bản tích hợp.
- T2 owner review phần kết quả trong portal Student. DEV05 review thay đổi nhỏ tại InternshipWorkspace.
- T1 scanner và QA chạy chu trình upload → CLEAN thật. Bộ runtime hiện kiểm PENDING bị chặn;
  không giả lập scanner thành CLEAN trong DB. Các case CLEAN fixture không chứng minh scanner đã chạy.
- Owner staging xác nhận SHA/image và deploy, rồi QA smoke bốn vai trò trên URL thật.
- Folder UIUX handoff đang untracked do người dùng cung cấp; không add toàn repo một cách máy móc.

Kết quả, lệnh chạy và bằng chứng: [TEST_REPORT.md](TEST_REPORT.md).
