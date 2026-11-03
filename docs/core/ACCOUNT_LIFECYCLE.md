# Vòng đời tài khoản

Code giữ 4 trạng thái đã được SRS/source xác nhận: PENDING_ACTIVATION, ACTIVE, SUSPENDED, DEACTIVATED. Không bổ sung flow khôi phục DEACTIVATED hoặc ép kích hoạt qua admin.

| Nguồn | Đích được phép qua admin |
|---|---|
| PENDING_ACTIVATION | SUSPENDED, DEACTIVATED |
| ACTIVE | SUSPENDED, DEACTIVATED |
| SUSPENDED | ACTIVE, DEACTIVATED |
| DEACTIVATED | Không có trạng thái khác |

Gửi cùng trạng thái là no-op. Tất cả chuyển trạng thái khác bị từ chối. Cần permission `account:admin:status`, reasonCode `[A-Z0-9_]{3,64}`, không tự khóa/vô hiệu hóa mình. Cấm thu hồi ADMIN của chính actor. Thao tác làm mất admin cuối bị chặn; database mutex dùng chung giữa các instance và kiểm tra lại actor sau khi đợi lock.

Mutation lưu actor, from/to, reason, thời điểm UTC trong `account_status_history`, tăng security_version, reset failed_attempts/locked_until và ghi `ACCOUNT_STATUS_CHANGED` cùng transaction. Các phiên cũ nhận 401 ở request kế tiếp. Mở khóa không khôi phục phiên cũ.

`PATCH /api/v1/admin/users/{id}/revoke-sessions` chỉ tăng version và ghi `SESSIONS_REVOKED`, không đổi status. `GET /api/v1/admin/users/{id}/status-history` phân trang, chỉ global ADMIN có quyền quản trị trạng thái.

## Kích hoạt và khóa credential

Luồng hiện hành: admin mời email mới; token 48 giờ, hash SHA-256; người nhận chấp nhận lời mời với mật khẩu hợp lệ, tạo user ACTIVE và membership trong một transaction. Không provision PENDING trước. SRS ghi flow PENDING activation riêng là OPEN; chưa có API kích hoạt account import sẵn, không tự giả định được nghiệp vụ này.

Sai mật khẩu 5 lần liên tiếp đặt locked_until trong 15 phút, không đổi users.status. Hết khóa có thể đăng nhập lại. Reset/đổi mật khẩu băm BCrypt cost 12, tăng version; reset token 30 phút một lần. Password tối thiểu 12 code point, tối đa 72 byte UTF-8. Session timeout 30 phút không hoạt động.

Account ACTIVE, membership.active và organization VERIFIED là ba điều kiện riêng. Xác thực doanh nghiệp thực tế thuộc T5. Không khóa account để thay quy trình xác thực tổ chức.

## Bằng chứng và phần còn lại

`AccountStatusPolicyTest` và `CorePlatformIT.lifecycleTransitionMatrixIsEnforcedInDatabase` kiểm tra cả 16 cặp trạng thái. Integration test còn kiểm tra history, rollback, session cũ, self-disable và hai admin thu hồi/khóa lẫn nhau đồng thời. Kết quả thực thi nằm trong TEST_REPORT. Flow pre-provision/activation riêng, MFA và quy trình phục hồi đặc biệt vẫn chờ thiết kế/phê duyệt.
