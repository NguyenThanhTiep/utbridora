# Hướng dẫn UI T3-09

Cập nhật 12/11/2026 với backend DEV05/DEV06 tại `4d187ab`. Xem thêm
[ghi chú API, Git và dependency](T3_09_BACKEND_INTEGRATION.md).

## Sử dụng

Sinh viên mở **Công việc của tôi**, chọn nhiệm vụ. Trong trạng thái IN_PROGRESS,
nhập URL HTTPS không chứa user/password, ghi chú tối đa 2.000 codepoints rồi
**Gửi bài nộp**. Browser gọi POST Submission với expectedAssignmentVersion và
fileIds=[], không PATCH trạng thái thành SUBMITTED. Lịch sử được GET từ server,
đọc đủ trang; mỗi version mở URL riêng. Backend quyết định hạn nộp/lateFlag.

Mentor mở **Không gian thực tập**, chọn phân công → đợt → nhóm → nhiệm vụ → hồ sơ.
Chỉ submission current khi assignment SUBMITTED có form review. ACCEPT và
REQUEST_REVISION đều bắt buộc comment. Gửi review bằng POST `/submissions/{id}/reviews`.
Sau REQUEST_REVISION, sinh viên gọi start hiện có để về IN_PROGRESS rồi nộp v2.
HR xem dữ liệu nhưng không có form nộp, review, comment hoặc sửa report.
Các nút này là hướng dẫn UX; server luôn phải kiểm quyền hiện hành.

**Trao đổi về nhiệm vụ** dùng Comment API append-only, tách khỏi comment của
review. Text render bằng React, không chèn HTML. URL bất thường từ response
không được biến thành liên kết có thể chạy mã.

Chọn **Mở báo cáo tuần**, chọn hồ sơ. Mentor/HR lấy các case trong group hiện tại;
sinh viên lấy case từ toàn bộ trang Task API, độc lập bộ lọc trên board. Nếu chưa
có Task, sinh viên có thể nhập mã hồ sơ được cấp; GET `/t3/cases/{id}` phải xác
nhận quyền và trả đúng caseId trước khi đưa vào lựa chọn. Chưa có case-list API
trong contract đang dùng, nên không tự tạo endpoint khác.

Student chọn thứ Hai bắt đầu tuần và nhập việc đã làm/khó khăn/kế hoạch. Server
kiểm tuần giao với khoảng Placement và tính hạn theo Asia/Ho_Chi_Minh. UI không
tự tính lateFlag hoặc từ chối tuần đầu/cuối không trọn. Tạo root với fileIds rỗng;
PATCH chỉ current DRAFT. Nội dung chưa lưu phải được lưu trước khi submit.
SUBMITTED/REVIEWED muốn sửa phải tạo revision mới kèm reasonCode. Dropdown lịch
sử gọi GET report?revisionNo=..., bản lịch sử luôn chỉ đọc, kể cả bản DRAFT cũ.

## Xem nhận xét bài nộp

Trong lịch sử bài nộp, chọn **Xem nhận xét bản vN**. UI mặc định mở bản mới nhất;
hiển thị kết quả Chấp nhận/Yêu cầu sửa, người nhận xét, thời gian và nội dung từ GET
`/t3/submissions/{submissionId}/reviews`. Sau POST review, UI đọc lại GET; reload
trình duyệt vẫn thấy dữ liệu server. Bản v2 chưa review không hiển thị nhận xét v1.

204: “Bản nộp này chưa có nhận xét”. 401/404: đóng workspace để không giữ feedback
đã mất quyền. 503/lỗi mạng: hiện lỗi, có nút **Tải lại nhận xét bài nộp**; không coi
là chưa review. Đổi version/context hủy GET cũ và bỏ phản hồi đến chậm. Version
assignment trong review là lịch sử; thao tác mới luôn lấy version assignment hiện tại.

## Lỗi và retry

- 409 VERSION_CONFLICT/STALE_SUBMISSION: giữ nội dung, hiển thị errorCode/requestId;
  **Tải phiên bản hiện tại** cập nhật expectedVersion. Người dùng xem lại rồi gửi
  bằng operation key mới. Refresh report không ghi đè phần text đã sửa.
- Mất kết nối, timeout 20 giây, 5xx, 429 hoặc REQUEST_IN_PROGRESS: giữ nguyên
  serialized payload + Idempotency-Key, khóa chỉnh sửa/gửi mới; nút **Thử lại thao
  tác vừa gửi** dùng cùng payload/key. Không tự retry liên tục.
- CSRF_INVALID: không coi là thu hồi quyền; retry cùng payload/key để transport
  Core lấy CSRF mới. 401 hoặc 403 khác: đóng toàn scope và xóa dữ liệu riêng tư.
- 404/503 được hiển thị là dữ liệu/chức năng chưa truy cập được; không báo lưu
  thành công, không dựng kết quả giả thay API.
- Đổi phân công/group/user, đóng chi tiết, thoát màn hoặc thu hồi session sẽ hủy
  request phía browser và bỏ state scope. Abort không hủy transaction đã chạy ở
  server: nếu thoát trong lúc gửi, khi quay lại phải tải kết quả đã lưu trước
  khi tạo thao tác mới. Retry trong cùng màn giữ key; không lưu draft/key riêng tư
  xuyên session vào localStorage.

## Minh chứng và nhận xét báo cáo

Sau khi có report root, dùng **Chọn tệp minh chứng → Tải lên minh chứng → Lưu bản
nháp**. Hỗ trợ PDF/PNG/JPEG tối đa 10 MiB mỗi tệp, 20 tệp mỗi báo cáo. Tệp mới ở
PENDING được giữ trong draft; server kiểm CLEAN trước submit/download. **Gỡ minh
chứng** chỉ thay refs bản nháp; lưu để xác nhận thay đổi. **Kiểm tra trạng thái
minh chứng** lấy metadata mới từ T1. Browser tải binary qua API có session/context.

Với report REVIEWED, UI đọc GET `/reports/{id}/reviews?revisionNo=N` để hiển thị
người nhận xét, nội dung và thời gian. Đổi revision tải đúng review của revision
đó; current draft mới không hiển thị nhầm review bản trước.

## Giới hạn hiện tại

Submission, Comment và Report đã nối backend được giao. Review bài nộp và report
đều đọc lại từ server sau reload. File-mode Submission chưa có; UI tuần 3 dùng URL.
Máy quét file thật T1-07 chưa có trong baseline; không tự chuyển CLEAN. Source
Placement ACTIVE trong test là prerequisite, không thay nghiệm thu T5-07.

Backend/migrations/Core auth, SRS và Contract không thay đổi trong T3-09. Bản
bàn giao tuần 2 và chứng cứ T3-03 được giữ nguyên trạng thái lịch sử.
