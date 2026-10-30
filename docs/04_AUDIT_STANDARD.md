> Tài liệu baseline được giữ để truy vết thiết kế ban đầu. Code và kết quả kiểm thử sau triển khai ngày 29/10/2026 xem [AUDIT_LOG.md](AUDIT_LOG.md) và [báo cáo thực thi](TEST_REPORT.md); các câu “chưa triển khai/chưa test” bên dưới mô tả baseline, không phải trạng thái mới nhất.

# Công việc 5 – Audit Log tiêu chuẩn dùng chung cho 5 team

## 1. Mục tiêu và ranh giới

Audit ghi **ai thực hiện hành động gì trên tài nguyên nào, thuộc tổ chức nào, khi nào, kết quả, lý do, mã truy vết**. Không phải full request logger. Audit phải hỗ trợ điều tra phê duyệt/chia sẻ/đánh giá và các thất bại phân quyền, nhưng không được trở thành nguồn rò rỉ dữ liệu nhạy cảm.

T1 sở hữu `AuditService`/schema/read API. T2/T3/T4/T5 phải gọi audit đúng lúc thay đổi nghiệp vụ, không xây bảng audit riêng.

## 2. Schema audit_logs

| Cột | Vai trò | Ví dụ |
|---|---|---|
| id | event UUID | `c8...` |
| actor_id | user identity hoặc null cho anonymous | `U001` |
| action | event code không gian tên | `PROFILE_SHARED` |
| resource_type | domain table/type, không chứa PII | `student_profile` |
| resource_id | id tài nguyên (không phải raw text) | UUID |
| organization_id | phạm vi liên quan | UUID |
| result | SUCCESS / FAILURE / DENIED | `SUCCESS` |
| reason_code | mã lý do có kiểm soát | `AUTHORIZATION_DENIED` |
| request_id | correlation id do server sinh | UUID |
| source | nguồn sự kiện | `APPLICATION` |
| event_version | schema event | `1` |
| created_at | UTC | ISO timestamp |

`AuditLogView` là response DTO; không xuất trực tiếp Entity JPA. `RequestIdFilter` phát `X-Request-ID` cho mỗi HTTP request. Không tin request ID tự gửi bởi client là chứng cứ có thẩm quyền.

## 3. Danh mục event phải phát sinh

| Module sở hữu action | Event | Khi nào |
|---|---|---|
| T1 | LOGIN / LOGOUT / PASSWORD_CHANGED / PASSWORD_RESET | thay đổi/đăng nhập |
| T1 | INVITATION_CREATED / ACCOUNT_ACTIVATED | cấp identity |
| T1 | ACCOUNT_STATUS_CHANGED / MEMBERSHIP_GRANTED / MEMBERSHIP_REVOKED / SESSIONS_REVOKED | thay đổi quyền/trạng thái |
| T1 | ACCESS_DENIED | yêu cầu không có quyền (audit security) |
| T2 | STUDENT_PROFILE_UPDATED / STUDENT_EVIDENCE_ADDED | sửa hồ sơ, thêm minh chứng |
| T2/T5 | INVITATION_RESPONDED | xác nhận/từ chối lời mời nghiệp vụ |
| T5 | ENTERPRISE_VERIFIED / COOPERATION_REVIEWED | xét duyệt doanh nghiệp và yêu cầu |
| T5 | PROFILE_SHARED / PROFILE_SHARE_REVOKED | chia sẻ / thu hồi quyền |
| T5 | PLACEMENT_CREATED / PLACEMENT_TERMINATED | tiếp nhận sinh viên |
| T3 | TASK_ASSIGNED / SUBMISSION_CREATED / SUBMISSION_REVISED | giao việc, nộp, sửa |
| T3 | EVALUATION_SUBMITTED / EVALUATION_PUBLISHED | đánh giá / công bố |
| T4 | MATCH_AGGREGATE_VIEWED / MATCH_EXPORT_ATTEMPTED | thống kê và xuất dữ liệu |

Đối với xem hồ sơ nhạy cảm, T2/T5 phát `STUDENT_PROFILE_ACCESSED` **khi quyền đã kiểm tra và truy cập thật sự được thực hiện**. Log event phải có mục đích sử dụng/phân loại theo quyết định nghiệp vụ nếu được quy định, không ghi snapshot hồ sơ.

## 4. Mẫu sử dụng trong service nghiệp vụ

```java
@Transactional
public void approve(String actorId,String organizationId,String requestId){
    // 1. Check authenticated actor + school membership + assigned request + current state
    // 2. Update request status in T5 repository
    auditService.writeScoped(actorId,"COOPERATION_REVIEWED","cooperation_request",
        requestId,"SUCCESS",organizationId,"APPROVED");
}
```

Audit thành công trong **cùng transaction** với thay đổi nghiệp vụ: nếu thay đổi rollback thì log thành công cũng rollback. Audit từ chối qua `AuditService.securityDenied` có `REQUIRES_NEW` để có thể tồn tại khi request bị chặn. Với workflow đa hệ thống và transaction bất đồng bộ cần outbox + reliable delivery (chưa code).

## 5. Quyền xem và privacy

- Starter chỉ có global ADMIN và permission `audit:global:read`; Cán bộ trường không được cấp mặc định.
- Nếu triển khai school-scoped auditor: mới thêm permission `audit:school:read`, query bắt buộc WHERE `organization_id` đúng trường; kiểm tra thay đổi quyền và test cross-tenant trước khi công bố.
- Người dùng có thể được cung cấp lịch sử liên quan tài khoản mình qua API riêng sau đánh giá quyền riêng tư, **không** mở trang admin logs.
- Không chứa password, mã OTP, raw token, reset URL, session ID, file nội dung, email toàn bộ người khác, CMND/CCCD, IP thô khi chưa có chính sách phù hợp.
- Nhân sự chỉ được dùng audit cho mục đích có thẩm quyền; cần retention và hướng xóa/ẩn danh theo chính sách được phê duyệt.

## 6. Bảo vệ chứng cứ audit

- Application DB user lý tưởng chỉ INSERT/SELECT theo API đã cấp, admin đặc quyền có quy trình riêng; quyền UPDATE/DELETE audit nên ngăn ở mức DB hoặc chuyển audit sang storage append-only.
- Sử dụng backup immutable/WORM hoặc forward SIEM nếu cần chống người có quyền DBA thay đổi; **starter hiện chưa có WORM/tamper-evidence**.
- Mã request_id giúp theo dấu request nhưng **không** tự chứng minh dữ liệu không bị sửa.
- Search log có pagination tối đa 100/batch và lọc actor/action; alert bất thường (login failure burst, mass denied) chưa được phát triển.

## 7. Acceptance criteria

1. Admin có quyền xem log theo trang; student và doanh nghiệp không xem `/admin/audit-logs`.
2. Thao tác grant/revoke membership, status, revoke sessions phát action đúng, reason code nếu cần.
3. Một request bị AccessDenied có `ACCESS_DENIED` + `AUTHORIZATION_DENIED` và không lưu path chứa dữ liệu nhạy cảm.
4. Audit thành công cùng transaction nghiệp vụ; test rollback không có `SUCCESS` sai.
5. Dữ liệu trả về không có password/token/request body.
6. Xem dữ liệu nhạy cảm chỉ khi nghiệp vụ cho phép, event chỉ ghi metadata tối thiểu.
