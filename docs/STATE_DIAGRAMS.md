> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [ACCOUNT_LIFECYCLE.md](ACCOUNT_LIFECYCLE.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

> ⚠️ **TÀI LIỆU AUTHENTICATION GỐC (#1):** xem `00_TONG_QUAN_T1_02_05.md` và `05_API_HANDOFF.md` để biết API #2–#5 đã cập nhật. Endpoint xác thực tổ chức chỉ còn demo-only dưới `/api/v1/demo/admin/...`.

# Sơ đồ trạng thái – Team 1

## 1. Account (đề xuất; trạng thái PENDING chưa có flow tạo user riêng ở starter)

```mermaid
stateDiagram-v2
    [*] --> PENDING_ACTIVATION: tạo hồ sơ / thư mời
    PENDING_ACTIVATION --> ACTIVE: xác thực/kích hoạt hợp lệ
    PENDING_ACTIVATION --> DEACTIVATED: hủy
    ACTIVE --> SUSPENDED: admin có quyền khóa
    SUSPENDED --> ACTIVE: admin có quyền mở khóa
    ACTIVE --> DEACTIVATED: vô hiệu hóa
    SUSPENDED --> DEACTIVATED: vô hiệu hóa
```

## 2. Session

```mermaid
stateDiagram-v2
    [*] --> ANONYMOUS
    ANONYMOUS --> AUTHENTICATED: email + password + csrf hợp lệ
    AUTHENTICATED --> LOGGED_OUT: POST logout + csrf
    AUTHENTICATED --> EXPIRED: timeout 30 phút
    AUTHENTICATED --> REVOKED: password/status/security_version thay đổi
    LOGGED_OUT --> ANONYMOUS
    EXPIRED --> ANONYMOUS
    REVOKED --> ANONYMOUS
```

## 3. One-time invitations / reset

```mermaid
stateDiagram-v2
    [*] --> ISSUED
    ISSUED --> CONSUMED: submit token hợp lệ trước hạn
    ISSUED --> EXPIRED: quá hạn
    ISSUED --> REVOKED: token mới thay thế (reset) hoặc admin hủy (tương lai)
```

## 4. Organization demo

```mermaid
stateDiagram-v2
    [*] --> PENDING: admin tạo organization
    PENDING --> VERIFIED: admin demo / T5 duyệt sau này
    VERIFIED --> SUSPENDED: có rủi ro, quyền đã cấp
    SUSPENDED --> VERIFIED: kiểm tra lại
```

**Ghi chú:** Sơ đồ state mô tả cả mục tiêu thiết kế; starter chưa triển khai tất cả transition như tạo PENDING, DEACTIVATED -> ACTIVE, quản lý thiết bị; riêng xác thực doanh nghiệp thực tế phải do T5 thiết kế.
