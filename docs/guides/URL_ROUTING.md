# Điều hướng theo URL

Bản sửa này kế thừa BE–FE lượt 1–3 tại `046c9eb4adb444ba59541b8c1384214d44480000`. URL là nguồn trạng thái cho màn hình, trang con cổng, phân mục HR, tab năng lực/lời mời/nguyện vọng và các chi tiết đã nối dưới đây. Không thay API backend, permission, SQL, tài liệu/contract đóng băng hoặc cấu hình runtime.

## Địa chỉ màn hình

| Phạm vi | URL |
|---|---|
| Đăng nhập / quên mật khẩu | `/dang-nhap`, `/quen-mat-khau` |
| Liên kết email hiện hữu | `/reset-password#token=…`, `/accept-invitation#token=…` |
| Tài khoản cá nhân | `/tai-khoan` |
| Cổng doanh nghiệp | `/enterprise/overview`, `/enterprise/demands`, `/enterprise/verifications`, `/enterprise/statistics`, `/enterprise/cooperations`, `/enterprise/proposals`, `/enterprise/placements`, `/enterprise/notifications` |
| Cổng trường | `/school/overview`, `/school/students`, `/school/verifications`, `/school/cooperations`, `/school/matching`, `/school/proposals`, `/school/placements`, `/school/notifications` |
| Sinh viên theo SRS §14.1 | `/tong-quan`, `/ho-so`, `/ho-so/nang-luc?tab=du-an`, `/co-hoi`, `/loi-moi`, `/nguyen-vong`, `/thuc-tap/nhiem-vu`, `/thuc-tap/bao-cao-danh-gia` |
| Tab năng lực | `tab=du-an`, `chung-chi`, `cv`, `minh-chung` |
| Chi tiết sinh viên | `/co-hoi/{id}`, `/loi-moi/{id}`, `/nguyen-vong/{id}` |
| HR/Mentor | `/workspace/internship/overview`, `/workspace/internship/cohorts`, `/workspace/internship/groups`, `/workspace/internship/mentors`, `/workspace/internship/cases` |
| Hồ sơ Case hiện hữu | `/workspace/cases/{id}`, tùy chọn `?followup=policy`, `quality`, `close` |
| Phân công / quyền trường | `/workspace/assignments`, `/workspace/delegations`, `/school/preferences`, `/school/internship-cases` |
| Lời mời Mentor | `/workspace/mentor-invitations`, `/enterprise/mentor-invitations` |
| Vận hành / đánh giá | `/workspace/operations`, `/workspace/evaluations` |
| Admin | `/admin/overview`, `/admin/users`, `/admin/organizations`, `/admin/audit`, `/admin/matching-statistics` |
| Danh mục kỹ năng | `/danh-muc/ky-nang` |

`/` chọn màn mặc định theo các quy tắc vai trò hiện có rồi replace sang URL cụ thể; không làm đổi quyền hoặc chính sách tài khoản nhiều vai trò. `/login` là alias của `/dang-nhap`, `/forgot-password` là alias của `/quen-mat-khau`. `/ho-so/ky-nang` dẫn tới màn hồ sơ hiện có `/ho-so`, nơi đang quản lý kỹ năng; sửa routing không tự tạo thêm một màn nghiệp vụ S03. Các liên kết Case, email cũ được giữ tương thích.

## Phạm vi và chi tiết

Cổng có thể giữ `orgId`, `schoolId`, `termId`, `recordId`, `cooperationId`, `proposalId` trong query. `orgId` chỉ được dùng khi thuộc danh sách tổ chức hiện hành của người dùng; ID ngoài danh sách hiện thông báo, không chuyển ngầm sang tổ chức đầu tiên. Trường dùng `termId` cho danh sách trong kỳ; phía doanh nghiệp, schoolId/termId tiếp tục là bộ lọc phạm vi thông báo theo hành vi cũ. Chuyển tổ chức/kỳ xóa chi tiết trước đó. Quyền theo kỳ, consent, profile mask và quyền Case vẫn do server kiểm tra trên mỗi API tương ứng.

Mở nội dung hợp tác ghi `orgId`/`recordId`, cho phép sao chép URL và tải lại đúng hồ sơ. Đích từ thông báo/việc cần làm giữ record/cooperation/proposal tương ứng. Ứng viên và tiếp nhận giữ hợp tác đã chọn khi chuyển qua lại; đích tiếp nhận đang mở dùng recordId để phục hồi chi tiết. Chọn ID hợp tác không còn trong phạm vi không được tự đọc một hợp tác khác thay thế.

Chi tiết cơ hội/lời mời/nguyện vọng sinh viên đọc lại từ API hiện hữu theo người dùng đã đăng nhập. Bookmark nguyện vọng cũ hiển thị đúng lần gửi đó và chỉ đọc, không chuyển thao tác rút sang lần gửi mới. Tab năng lực và tab lời mời/nguyện vọng được khôi phục từ URL. Các bộ lọc tìm kiếm, số trang danh sách, bản nháp chưa lưu, thao tác mở popup và lựa chọn sâu bên trong editor vẫn là trạng thái tạm; không đưa nội dung bản nhập hoặc credential vào URL.

## Lịch sử và đăng nhập

Menu tạo history entry; tải lại đọc URL, Back/Forward phục hồi đích tương ứng. Chọn lại cùng đích không thêm entry trùng. Liên kết nội bộ đã nhận diện (đặc biệt Case) đi qua cùng cơ chế; download, API/file links, mailto, liên kết ngoài, tab mới/modifier và fragment email/skip-link giữ hành vi trình duyệt.

Liên kết chức năng trước đăng nhập giữ đích trong bộ nhớ/URL hiện hành; đăng nhập xong mở đúng chức năng nếu có quyền. Hết phiên hoặc đăng xuất gỡ dữ liệu UI; quay lại protected URL vẫn yêu cầu đăng nhập. Không nhận return URL bên ngoài từ query và không lưu password/token trong history state hoặc localStorage. Token email được giữ trong bộ nhớ và xóa fragment khỏi thanh địa chỉ, giữ nguyên query hợp lệ như Case followup. Khi đã có phiên và mở link email, vẫn yêu cầu đổi phiên rõ ràng như trước.

Đích không tồn tại hiển thị “Không tìm thấy trang”; đích có thật nhưng thiếu quyền hiển thị “Không có quyền truy cập” và không dựng component đọc dữ liệu đặc quyền. URL không tự cấp quyền; API hiện hành tiếp tục quyết định quyền truy cập.

## Bảo vệ bản nhập và kiểm chứng

Giữ các guard `demand-before-leave`/`beforeunload` của editor và lệnh đang xử lý/chưa rõ kết quả. Với Back/Forward trong cùng document, từ chối rời màn sẽ phục hồi đúng entry cũ bằng history.go, giữ nguyên editor và các entry Forward. Handler lồng nhau trong cùng thao tác chỉ hỏi một lần. Kết quả ghi chưa xác định tiếp tục giữ đúng payload/idempotency key cho thử lại; routing không tự gửi lại lệnh.

Kiểm tra gồm URL/Reload/Back/Forward desktop và mobile; guard bản nhập thật và lùi nhiều entry; known/unknown/forbidden routes; nhiều vai trò; token email; Case; tổ chức ngoài phạm vi; tab và chi tiết sinh viên, bookmark nguyện vọng cũ. Các ca HTTP mô phỏng tách khỏi Chromium + session/MySQL thật trong CooperationReportingIT. Bộ hồi quy hiện hữu tiếp tục kiểm API/contract/build, phân công, consent, workflow, ClamAV và vận hành.

Nginx hiện có `try_files $uri $uri/ /index.html`; không thay cấu hình này. Cần giữ SPA fallback khi triển khai reverse proxy khác. Bằng chứng DEV không thay QA/Lead/nhà trường nghiệm thu, R31 hoặc VPS acceptance. Nhánh sửa này chưa tự merge main hoặc deploy/restart môi trường demo.
