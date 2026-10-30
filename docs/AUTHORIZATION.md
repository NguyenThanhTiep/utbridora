# Authorization

Backend áp dụng RBAC + organization scope + business rules. Quyền cho phép cần account ACTIVE, principal có security_version hiện hành, membership active, permission trong database, đúng loại/trạng thái tổ chức, ownership/share và điều kiện nghiệp vụ.

## Các guard dùng chung

| Guard | Ý nghĩa |
|---|---|
| `isSelf(authentication,userId)` | Danh tính hiện hành trùng owner |
| `hasGlobal(authentication,permission)` | ADMIN global active và permission đã seed |
| `hasOrganization(authentication,orgId,role,permission)` | Membership đúng org, org VERIFIED, loại phù hợp role; role ADMIN bị từ chối |
| `hasOrganizationForRequest(authentication,pathOrg,bodyOrg,role,permission)` | Body org trùng path org rồi mới kiểm tra scope |
| `canReadStudentProfile(authentication,studentId,orgId)` | Owner hoặc quyền enterprise/school kèm quyết định từ ProfileVisibilityPort |

Account được kiểm tra ở HTTP filter và trong policy để bảo vệ cả service method. Actor cho API lấy từ `AppPrincipal`; `actorId` tham số nội bộ không được bind từ JSON. Các mutation quản trị kiểm tra lại actor sau database lock để chặn actor bị thu hồi quyền trong lúc chờ.

## Chính sách HTTP

Public chỉ có CSRF, login, forgot, reset, accept invitation và health tối thiểu. `/admin/**` yêu cầu ROLE_ADMIN ở filter và permission global tại method/service. `/auth/**` còn lại và `/demo/**` yêu cầu authentication. Các path khác mặc định từ chối. Error dispatch được phép để trả đúng status lỗi, không mở quyền cho request trực tiếp tới tài nguyên nghiệp vụ.

Khi tích hợp route mới, cập nhật SecurityConfig với path chính xác yêu cầu authentication, sau đó thêm method guard và record-level query. Không đổi `anyRequest().denyAll()` thành permitAll. Ví dụ kiểm tra body chỉ được triển khai ở endpoint **test-only** `TestAuthorizationEndpoints`; T1 không tạo chức năng hợp tác giả thay T5.

## Organization và ownership

ENTERPRISE chỉ gắn org ENTERPRISE; STUDENT/SCHOOL_STAFF chỉ gắn UNIVERSITY; ADMIN chỉ global (org null). Một user có nhiều membership. Thay đổi active membership tăng security_version; cấp trùng đang active là no-op. Organization PENDING hoặc SUSPENDED bị từ chối tại guard cần VERIFIED, kể cả session đã tồn tại.

Service nghiệp vụ phải query tài nguyên với `id AND organization_id` và kiểm tra owner/state/assignment. Membership ở cả hai org không cho phép di chuyển bản ghi từ org này sang org khác. Nếu body chứa organizationId thì phải đối chiếu path; ưu tiên bỏ field trùng khi thiết kế API mới.

## Hồ sơ sinh viên

`ProfileVisibilityPort` hiện có hai phương thức boolean `explicitlySharedWithEnterprise` và `delegatedToSchool`. Adapter mặc định luôn false. Boolean chỉ là điều kiện truy cập tổng quát; **không phải danh sách field đã được chia sẻ**. T2/T5 phải kiểm tra consent, thời hạn, purpose và lọc field ở service/projection trước serialize; chỉ trả true khi quyết định hợp lệ. ADMIN kỹ thuật không tự có quyền xem hồ sơ.

Quyền mentor/Placement do T3/T5; thống kê và ngưỡng ẩn danh do T4. Ma trận `contracts/permission_matrix.csv` vẫn là DRAFT_NEEDS_SIGNOFF, chưa có xác nhận nghiệp vụ. Test adapter mock không phải bằng chứng chia sẻ dữ liệu thật.

## Kiểm thử và giới hạn

Policy unit test và MySQL/HTTP test bao phủ sai role, đổi org URL/body, đa membership, org đổi trạng thái, revoked/stale principal, ADMIN cố gọi business guard, adapter mặc định deny và actor bị thu hồi lúc đua. Audit từ chối không lưu URL thô/token. Các API nghiệp vụ mới của T2–T5 vẫn phải có test IDOR riêng.
