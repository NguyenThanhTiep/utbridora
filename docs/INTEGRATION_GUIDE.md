# Hướng dẫn tích hợp T2–T5

Các team thêm module trong **cùng modular monolith** Spring Boot/Java21, dùng chung users, organizations, session và audit. Không tạo lại login, JWT, bảng tài khoản theo role hoặc microservice riêng để thay core.

## Đăng nhập và gọi API

Frontend dùng flow CSRF → login → CSRF mới → me; xem ví dụ trong API_DOCUMENTATION và `web-demo/src/api.ts`. API wrapper giữ HttpOnly session phía browser, CSRF trong memory, xử lý 401/403 và loading/error. Sử dụng cùng origin hoặc Vite proxy; không thêm wildcard credential CORS.

Ví dụ login body `{"email":"demo@example.test","password":"<secret nhập lúc chạy>"}`. Response `/auth/me` có `userId,email,displayName,memberships[{role,organizationId}]`. `/auth/me/permissions` trả grant theo scope đã kiểm chứng; chỉ phục vụ menu, không phải token cấp quyền cho API tiếp theo.

## Kiểm tra ở backend

```java
@PreAuthorize("@authorizationPolicy.hasOrganization(authentication,#orgId,T(vn.utbridora.auth.user.Role).ENTERPRISE,'cooperation:enterprise:create')")
public DemandView create(String orgId, DemandCommand command) {
    // T5: lấy actor từ Authentication, đối chiếu record/org/state, rồi tạo theo orgId đã kiểm chứng.
    return demandService.createForOrganization(orgId, command);
}
```

Đây là ví dụ tích hợp, DemandView/DemandCommand thuộc T5. Trong SecurityConfig đăng ký path mới chính xác dưới `.authenticated()` và giữ fallback denyAll. Guard phải ở service hoặc service kiểm tra lại khi có nhiều entry point. Không dùng mỗi `hasRole('ENTERPRISE')` để quyết định record-level access.

Actor ID lấy từ `(AppPrincipal) authentication.getPrincipal()`, không nhận actorId do frontend tự khai. Nếu body cũ có orgId, dùng `hasOrganizationForRequest` để buộc path/body trùng nhau; service query `findByIdAndOrganizationId`. Với tài nguyên đã tồn tại, **so sánh organization_id của bản ghi**, không chỉ body/path. Owner guard dùng `isSelf`, sau đó kiểm tra role và workflow cần thiết.

## Phần bàn giao theo team

| Team | T1 cung cấp | Team nghiệp vụ phải làm |
|---|---|---|
| T2 | userId, me, isSelf, ProfileVisibilityPort, file metadata | Hồ sơ, field-level projection, consent và audit đọc thật |
| T3 | principal, org guard, AuditService | Placement/mentor assignment, trạng thái task/evaluation |
| T4 | org/permission aggregate, identity ổn định | Ngưỡng ẩn danh, chống suy ngược nhóm nhỏ, export guard |
| T5 | organizationId, memberships, org guard, profile port | Xác thực tổ chức, hợp tác, Placement, share/consent/revoke |

`ProfileVisibilityPort` boolean không trả field được chia sẻ. Adapter `@Bean` hoặc `@Component` của T2/T5 thay bean `@ConditionalOnMissingBean`. Phải có test khi không share, thiếu consent, quá hạn và revoke; chỉ trả true khi mọi điều kiện hợp lệ, sau đó T2 lọc field ở projection. Adapter mặc định deny. Test mock allow của T1 không triển khai quyết định chia sẻ thật.

## Audit và dữ liệu

Gọi `AuditService.writeScoped` trong cùng transaction sau thay đổi business; dùng event code trong AuditAction. Resource/actor/org IDs là UUID. Không truyền token, raw URL, request body hoặc mô tả cá nhân vào reasonCode. Audit SUCCESS hỏng thì rollback. `securityDenied` là giao dịch độc lập và không làm mở quyền khi ghi thất bại.

users.id và organizations.id là VARCHAR36, thời gian UTC. Team tự sở hữu migration bảng nghiệp vụ; phối hợp số version để không trùng V1–V7 của T1. Không sửa migration đã chia sẻ. DB chung không tự động đồng nghĩa được đọc mọi bảng qua API.

## Lỗi và kiểm thử

401: login lại, không retry vòng lặp. 403: kiểm tra role/org/share/CSRF, không bỏ guard. 400: validation/transition/token. 409: concurrent/duplicate. 5xx: đối chiếu X-Request-ID, không ghi token vào ticket. Mọi endpoint nghiệp vụ mới cần test cross-tenant URL/body, owner, revoked membership, ADMIN không có business permission và rollback audit.

Các điểm OPEN của SRS vẫn cần PM/Leader ký: delegated school, field/time/purpose share, mentor placement, anonymity threshold, retention, staging ownership. T1 chưa thể nghiệm thu phần nghiệp vụ thay các team.
