# Cấp và thu hồi quyền cán bộ theo trường/kỳ

Task bổ sung **T1-04-UI**, tuần 2, DEV01/QA01. Xem [API đọc và phạm vi quyền](../contracts/T1_04_UI_READ_API.md). Màn hình dùng dịch vụ T1 hiện có, không dùng SQL hoặc seed để cấp quyền vận hành.

## Khởi tạo demo bằng admin

1. Đăng nhập admin bằng mật khẩu riêng của admin. Mở **Phân công quyền cán bộ**.
2. Chọn **Trường và kỳ** đúng nơi nhận đề nghị. Danh sách có phân trang khi có nhiều kỳ.
3. Tìm và chọn cán bộ có membership SCHOOL_STAFF trong trường đó.
4. Chọn **Xét duyệt hợp tác được phân công** để cán bộ đọc/duyệt đề nghị. Chỉ chọn thêm **Quản lý phân công quyền cán bộ** khi muốn giao quyền ủy quyền tiếp.
5. Chọn thời hạn và nhập căn cứ bắt đầu bằng `DEMO-`, rồi **Cấp phân công**.
6. Cán bộ nhận quyền đăng nhập lại. Với `school@example.test`, chọn DEMO School A/kỳ demo tương ứng rồi mở **Hợp tác doanh nghiệp → Đề nghị hợp tác → Tải lại đề nghị**.

Đường admin này là bootstrap demo có audit theo CP-AUTHZ1, không phải cơ chế tự cấp quyền nghiệp vụ/PII cho admin. Không dùng mật khẩu demo thay mật khẩu admin.

## Ủy quyền nghiệp vụ

Cán bộ đã có delegation `school:delegation:manage` vào cùng màn hình, chọn kỳ và **Phân công cấp quyền**. Chọn cán bộ khác, tập quyền và thời hạn nằm trong một authority đang có. Nếu cấp quyền đọc hồ sơ sinh viên, phải chọn rõ các thông tin được đọc. Không gộp hai parent để cấp tập quyền vượt từng parent.

Đăng nhập thành công hoặc có membership SCHOOL_STAFF chưa đủ quyền ủy quyền. Nếu chưa có chuỗi quản lý hợp lệ, màn hình báo **Chưa có kỳ được quản lý**.

## Kiểm tra và thu hồi

Danh sách/chi tiết hiển thị cán bộ, căn cứ, người cấp, thời hạn, version và quyền hiệu lực. Phân công active vẫn có thể mất hiệu lực do parent/account/membership/thời hạn. Chọn **Xem phân công**, chọn lý do, rồi **Thu hồi phân công**. Nút chỉ có khi đúng người cấp và còn thẩm quyền; backend luôn kiểm lại, kể cả khi nút đang hiển thị từ dữ liệu cũ.

Thu hồi parent khiến descendants mất quyền ngay request tiếp theo, dù row con vẫn active. Phiên cũ bị vô hiệu; đăng nhập mới vẫn phải qua scope guard.

## Kiểm thử trên môi trường riêng

```powershell
npm.cmd --prefix web-demo run build
.\mvnw.cmd '-Dit.test=SchoolDelegationManagementIT,Week2WorkflowIT' '-Dfrontend.e2e=true' verify
```

Test dùng MySQL Testcontainers và Chromium qua Vite proxy đến backend port ngẫu nhiên; không trỏ datasource vào `utbridora-local`. Báo cáo ở `target/failsafe-reports`, `target/delegations-browser-results.xml`, `target/delegations-browser.log`; ảnh desktop/mobile ở `target/delegations-browser-*.png`.

Commit/push mã nguồn không tự triển khai vào Docker 8088 và không tự cấp thêm quyền cho tài khoản trong database dùng hằng ngày. Giữ nguyên database, tài khoản, mật khẩu và volume hiện có khi triển khai; không chạy lại importer/seed để sửa quyền.
