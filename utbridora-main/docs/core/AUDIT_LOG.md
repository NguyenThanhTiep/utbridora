# Audit Log dùng chung

Giữ event code của SRS/source trong `AuditAction`. Đăng nhập dùng **LOGIN + result SUCCESS/FAILURE**, không đổi tên thành LOGIN_SUCCESS/LOGIN_FAILED. Cấp/thu hồi role dùng MEMBERSHIP_GRANTED/MEMBERSHIP_REVOKED; khóa/mở dùng ACCOUNT_STATUS_CHANGED. Nhờ vậy không phá dữ liệu và contract cũ.

## Cách ghi

```java
@Transactional
public void approve(AuthenticatedContext context, CooperationRequest request) {
    // Team nghiệp vụ kiểm tra scope, assignment, trạng thái và cập nhật request trước.
    auditService.writeScoped(context.userId(), "COOPERATION_REVIEWED", "cooperation_request",
        request.id(), "SUCCESS", request.organizationId(), "APPROVED");
}
```

Ví dụ trên là mã minh họa T5, không phải class T1 đã triển khai. API thật là `write(actor,action,resourceType,resourceId,result)` hoặc `writeScoped(...,organizationId,reasonCode)`. Gọi qua Spring bean để transaction có hiệu lực.

AuditAction bao gồm sự kiện T1, STUDENT_PROFILE_UPDATED/ACCESSED, PROFILE_SHARED/SHARE_REVOKED, COOPERATION_REVIEWED, INVITATION_RESPONDED, PLACEMENT_CREATED/TERMINATED, TASK_ASSIGNED, SUBMISSION_CREATED/REVISED, EVALUATION_SUBMITTED/PUBLISHED, MATCH_AGGREGATE_VIEWED/EXPORT_ATTEMPTED. T2–T5 chịu trách nhiệm phát sinh trong nghiệp vụ thật; T1 không tự tạo các thao tác này.

## Metadata và transaction

Mỗi event có UUID id, actor nullable, action registry, resource_type/id, result SUCCESS/FAILURE/DENIED, organization nullable, reason code có cấu trúc, request_id, source APPLICATION, event_version=1 và thời điểm UTC. IDs khi ghi mới phải UUID. Resource type là mã lowercase có underscore; không nhận mô tả tùy ý.

RequestIdFilter sinh X-Request-ID ở server, bỏ qua ID client tự gửi; MDC mang ID qua security filter trước DispatcherServlet. Job ngoài HTTP được cấp correlation UUID nếu chưa có. MDC được dọn sau request.

Audit SUCCESS tham gia transaction mutation. Audit lỗi khiến mutation thất bại và rollback; không nuốt lỗi để báo thành công. Test đã có tình huống database từ chối insert audit và kiểm tra password/version không đổi. `securityDenied` dùng REQUIRES_NEW; nếu ghi denial thất bại, quyền vẫn bị từ chối và có warning không kèm payload. AccessDenied trả trực tiếp JSON 403 để tránh ghi lặp khi error dispatch.

Không ghi password, raw session ID, CSRF/reset/invite token, URL thô, API secret, request body hoặc snapshot hồ sơ. Registry và validation metadata giảm nguy cơ ghi nhầm; service nghiệp vụ vẫn phải tuân thủ tối thiểu dữ liệu.

## Tra cứu và bảo vệ

GET `/api/v1/admin/audit-logs`: global ADMIN + audit:global:read. Bộ lọc action, actorId, organizationId, result, from inclusive, until exclusive; UTC ISO-8601. page 0–10000, size 1–100. Response AuditLogView không lộ entity/secret. Không có endpoint sửa/xóa audit. STUDENT, ENTERPRISE và SCHOOL_STAFF không có global audit.

Hiện chưa có WORM, SIEM, trường scoped auditor, chính sách retention đã ký hoặc bảo đảm chống DBA sửa dữ liệu. DB runtime/migration credentials hiện chưa tách riêng; staging guide yêu cầu tách khi vận hành thật. Health không phụ thuộc SMTP nhưng phải có giám sát delivery riêng. Không coi request_id là bằng chứng chống can thiệp.
