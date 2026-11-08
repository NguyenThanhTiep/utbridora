package vn.utbridora.auth.modules.internship.api;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import vn.utbridora.auth.modules.internship.domain.WorkspaceRuleException;

/** C.0 mapping for a future T3 adapter; no ControllerAdvice or change to legacy Core errors. */
public final class WorkspaceErrorMapping {
    private WorkspaceErrorMapping() {}
    public record FieldError(String field, String code, String message) {}
    public record Envelope(String errorCode, String error, String message, UUID requestId,
                           List<FieldError> fieldErrors, Integer retryAfterSeconds) {}
    public record Failure(int status, Envelope body) {}
    public static Failure map(WorkspaceRuleException failure, UUID serverRequestId) {
        Objects.requireNonNull(failure);
        Objects.requireNonNull(serverRequestId);
        return switch (failure.kind()) {
            case UNAUTHENTICATED -> error(401, "SESSION_EXPIRED", "Phiên đăng nhập không còn hiệu lực.", serverRequestId);
            case DENIED -> error(403, "ACCESS_DENIED", "Không có quyền thực hiện thao tác.", serverRequestId);
            case UNAVAILABLE -> error(503, "DEPENDENCY_UNAVAILABLE", "Dịch vụ phụ thuộc chưa khả dụng.", serverRequestId);
            case NOT_FOUND, SCOPE_MISMATCH -> error(404, "RESOURCE_NOT_FOUND", "Không tìm thấy tài nguyên trong phạm vi truy cập.", serverRequestId);
            case DUPLICATE_COHORT -> error(409, "DUPLICATE_COHORT", "Mã đợt đã tồn tại trong phạm vi quản lý.", serverRequestId);
            case GROUP_MEMBERSHIP_CONFLICT -> error(409, "GROUP_MEMBERSHIP_CONFLICT", "Thành viên nhóm đã thay đổi, vui lòng tải lại.", serverRequestId);
            case VERSION_CONFLICT -> error(409, "VERSION_CONFLICT", "Dữ liệu đã thay đổi, vui lòng tải lại.", serverRequestId);
            case STATE_CONFLICT -> error(409, "STATE_CONFLICT", "Trạng thái hiện tại không cho phép thao tác.", serverRequestId);
            case INVALID_OPERATION -> error(422, "BUSINESS_VALIDATION", "Thao tác không hợp lệ.", serverRequestId);
        };
    }
    public static Failure invalidRequest(UUID serverRequestId) {
        return error(400, "INVALID_REQUEST", "Vui lòng kiểm tra dữ liệu yêu cầu.", Objects.requireNonNull(serverRequestId));
    }
    private static Failure error(int status, String code, String message, UUID requestId) {
        return new Failure(status, new Envelope(code, code, message, requestId, List.of(), null));
    }
}
