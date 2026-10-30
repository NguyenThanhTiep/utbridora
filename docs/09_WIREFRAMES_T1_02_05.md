> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [API_DOCUMENTATION.md](API_DOCUMENTATION.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# Wireframe UI T1 cho công việc #2–5

Đây là **phác thảo màn hình**, không phải phần React đã code hoặc UI đã được duyệt. T1 sở hữu layout + quản trị nền tảng; T2–T5 sử dụng các component chung. Wireframe hoạt động theo permission và trạng thái từ backend, không dùng việc ẩn nút thay cho bảo mật.

## Màn 1: Quản lý tài khoản – Admin

```text
┌──────────────────────────────────────────────────────────────────────┐
│ UT.Bridora    [Dashboard] [Tài khoản] [Phân quyền] [Audit]    [Admin] │
├──────────────────────────────────────────────────────────────────────┤
│ TÀI KHOẢN                         [Tìm email] [Trạng thái v] [+ Mời] │
├─────────────────────────┬────────────┬────────────┬─────────────────┤
│ Email/Tên               │ Role(s)    │ Trạng thái │ Thao tác        │
├─────────────────────────┼────────────┼────────────┼─────────────────┤
│ sv01@example.com        │ Student    │ Active     │ [Chi tiết]      │
│ dn01@example.com        │ Enterprise │ Suspended  │ [Chi tiết]      │
└─────────────────────────┴────────────┴────────────┴─────────────────┘
```

Chỉ admin có permission phù hợp nhìn thấy quản trị account. Dữ liệu cần gồm `userId,email,displayName,status,lockedUntil,memberships`; API danh sách người dùng **chưa xây**, cần bổ sung có pagination + kiểm soát PII trước khi UI hoạt động.

## Màn 2: Chi tiết trạng thái tài khoản

```text
┌────────────────────── CHI TIẾT USER U002 ────────────────────────────┐
│ Email: dn01@example.com    Trạng thái: ACTIVE                       │
│ Membership: ENTERPRISE / ORG_A                                      │
│ Last login: (chưa thu thập chuẩn hóa)                                │
│                                                                      │
│ [Khóa tài khoản] [Thu hồi phiên] [Xem lịch sử]                       │
├──────────────────────────────────────────────────────────────────────┤
│ Modal: Chọn thao tác SUSPEND                                         │
│ Lý do (reason code): [SECURITY_REVIEW v]                             │
│ [Hủy]                                          [Xác nhận khóa]      │
└──────────────────────────────────────────────────────────────────────┘
```

Trạng thái chuyển phải hợp lệ theo `AccountStatusPolicy`. Không hiển thị nút `ACTIVE` cho account `DEACTIVATED` vì chưa có luồng mở lại. `last login` không có bảng riêng trong starter, không được tự tạo dữ liệu giả khi chưa có source.

## Màn 3: Quản trị vai trò và tổ chức

```text
┌──────────────────────── MEMBERSHIP U002 ─────────────────────────────┐
│ Tổ chức                      Vai trò          Trạng thái              │
│ Công ty ABC (ORG_A)          ENTERPRISE       Active  [Thu hồi]       │
│                                                                      │
│ Cấp vai trò mới:                                                     │
│ [Chọn tổ chức v] [Chọn vai trò v]                        [Cấp quyền] │
│                                                                      │
│ Quyền theo vai trò (read-only):                                       │
│ cooperation:enterprise:create                                        │
│ matching:enterprise:aggregate_read                                   │
│ student_profile:shared:read (cần explicit share từng hồ sơ)          │
└──────────────────────────────────────────────────────────────────────┘
```

Backend cấp role chỉ qua service T1, cần kiểm tra mapping role ↔ type tổ chức; không cho doanh nghiệp tự gửi role ADMIN. Quyền hiệu lực theo org có thể xem qua `/auth/me/permissions` cho chính tài khoản, không có API admin liệt kê quyền người khác trong starter.

## Màn 4: Xem audit log (Admin global)

```text
┌───────────────────────── NHẬT KÝ HỆ THỐNG ────────────────────────────┐
│ [Action v] [Actor ID] [Ngày bắt đầu] [Ngày kết thúc]     [Lọc]       │
├───────────────────┬──────────────┬──────────────┬─────────────────────┤
│ Thời gian UTC     │ Actor ID     │ Event        │ Kết quả              │
├───────────────────┼──────────────┼──────────────┼─────────────────────┤
│ 2026-10-29 07:00  │ U003         │ USER_STATUS  │ SUCCESS              │
│ 2026-10-29 07:01  │ U001         │ ACCESS_DENIED│ DENIED               │
├───────────────────┴──────────────┴──────────────┴─────────────────────┤
│ [Xem chi tiết]                   Trang 1 / 5   [Trước] [Sau]        │
└──────────────────────────────────────────────────────────────────────┘
```

API audit starter hỗ trợ `page,size,action,actorId`; filter ngày là ý tưởng wireframe, **chưa có API date range**; khi code phải bổ sung phân trang/sort và dữ liệu UTC đúng. Chỉ admin global có quyền xem danh sách này.

## Màn 5: Trạng thái và quyền của tài khoản đang đăng nhập

```text
┌────────────────────── TÀI KHOẢN CỦA TÔI ─────────────────────────────┐
│ Họ tên: Nguyễn Văn A          Email: sv01@example.com              │
│ Trạng thái: ACTIVE                                                    │
│ Vai trò theo tổ chức:                                                │
│   [UTH / STUDENT] [Công ty ABC / ENTERPRISE]                        │
│                                                                       │
│ Quyền hiện tại theo tổ chức được chọn (UI hints):                   │
│   student_profile:self:read                                          │
│   internship:student:submission_write                                │
│                                                                       │
│ [Đổi mật khẩu]   [Đăng xuất]                                         │
└──────────────────────────────────────────────────────────────────────┘
```

Hồ sơ giao diện lấy `/auth/me` và `/auth/me/permissions`. Người dùng không thể chỉnh `roles` hoặc `security_version` ở đây. Quyền cho phép thao tác vẫn phải được backend kiểm tra theo tài nguyên.

## Các màn ngoài phạm vi T1

- T2: hồ sơ năng lực, lời mời nghiệp vụ, đánh giá của sinh viên.
- T3: nhiệm vụ, nộp bài, chat, báo cáo tiến độ, tiêu chí đánh giá.
- T4: bảng thống kê aggregate/ẩn danh, kết quả đối chiếu.
- T5: xác thực doanh nghiệp, phiếu nhu cầu, hợp tác, đề xuất, Placement.

T1 chỉ xây layout và component chung để các team triển khai các màn này theo cùng chuẩn.
