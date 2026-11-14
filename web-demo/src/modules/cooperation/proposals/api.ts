import { api, ApiError } from '../../../api';
export function scopedRead<T>(org: string, path: string, signal: AbortSignal) {
  return api<T>(path, { signal, headers: { 'X-Organization-Id': org } });
}
export function workflowError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Chưa nhận được phản hồi từ máy chủ. Kiểm tra kết nối rồi thử lại.';
  const messages: Record<string, string> = {
    VERSION_CONFLICT: 'Dữ liệu vừa thay đổi. Hãy tải lại và kiểm tra trước khi quyết định.',
    STATE_CONFLICT: 'Đề xuất không còn ở trạng thái cho phép thao tác. Hãy kiểm tra dữ liệu mới.',
    PROPOSAL_STATE_CONFLICT: 'Đề xuất không còn cho phép cấp hoặc cập nhật chia sẻ. Hãy kiểm tra trạng thái mới.',
    CONSENT_INVALID: 'Quyền chia sẻ của sinh viên đã thay đổi hoặc hết hiệu lực. Hãy kiểm tra consent mới trước khi cấp chia sẻ.',
    CONSENT_EXPIRY_INVALID: 'Thời hạn chia sẻ phải còn hiệu lực và không vượt quá thời hạn sinh viên đã đồng ý.',
    CONSENT_FIELDS_NOT_ALLOWED: 'Thông tin đã chọn không còn thuộc phạm vi được phép chia sẻ.',
    MATCH_RUN_STALE: 'Tiêu chí hoặc hồ sơ đã thay đổi. Hãy tìm lại sinh viên phù hợp trước khi đề xuất.',
    CRITERIA_STALE: 'Tiêu chí tuyển vừa thay đổi. Tải lại nội dung đề nghị rồi tìm sinh viên lại.',
    CAPACITY_EXCEEDED: 'Không còn đủ chỉ tiêu. Tải lại tiến trình để kiểm tra số chỗ còn lại.',
    PROPOSAL_ALREADY_EXISTS: 'Sinh viên đã có trong đề xuất của đề nghị này.',
    PROPOSAL_BATCH_EXISTS: 'Đề xuất đã được tạo ở phiên khác. Tìm lại kết quả để bổ sung đúng danh sách hiện có.',
    CANDIDATE_NOT_MATCH: 'Sinh viên chưa đạt đủ tiêu chí bắt buộc của đề nghị.',
    SCHOOL_APPROVAL_REQUIRED: 'Sinh viên chưa được trường xác nhận đủ điều kiện thực tập.',
    PROFILE_STALE: 'Hồ sơ sinh viên vừa thay đổi. Hãy chạy lại đối chiếu trước khi đề xuất.',
    ELIGIBILITY_CHANGED: 'Điều kiện sinh viên vừa thay đổi. Tải lại kết quả trước khi xác nhận.',
    PLACEMENT_NOT_EFFECTIVE: 'Chưa đến thời điểm có hiệu lực để kích hoạt thực tập.',
    PLACEMENT_GATE_FAILED: 'Hồ sơ tiếp nhận chưa đủ điều kiện. Kiểm tra lời mời, chia sẻ, xác nhận sinh viên và tiêu chí hiện hành.',
    ACTIVE_PLACEMENT_EXISTS: 'Sinh viên đã có một đợt thực tập được phê duyệt hoặc đang hoạt động trong kỳ này. Tải lại trang để xem trạng thái mới nhất.',
    PLACEMENT_ALREADY_EXISTS: 'Hồ sơ tiếp nhận đã tồn tại cho ứng viên này.',
    INVITATION_NOT_ACCEPTED: 'Sinh viên chưa chấp nhận lời mời. Kiểm tra lại trạng thái lời mời trước khi tạo tiếp nhận.',
    TERM_INVALID: 'Kỳ thực tập chưa ở trạng thái hoạt động.',
    REQUEST_IN_PROGRESS: 'Yêu cầu đang được xử lý. Chờ kết quả rồi thử lại sau vài giây.',
    RESERVATION_EXPIRED: 'Thời gian giữ chỗ đã hết. Không thể thay đổi đề xuất này.',
    ACCESS_DENIED: 'Bạn không còn quyền truy cập hoặc hồ sơ đã ngừng được chia sẻ.',
    RESOURCE_NOT_FOUND: 'Nội dung không còn tồn tại hoặc không thuộc phạm vi của bạn.',
    INVALID_PERIOD: 'Thời điểm đóng phải sau thời điểm công bố.',
    SCHEMA_VALIDATION: 'Thông tin chưa hợp lệ. Kiểm tra lý do và các trường bắt buộc.',
  };
  return (messages[error.errorCode || ''] || error.message) + (error.requestId ? ` Mã hỗ trợ: ${error.requestId}.` : '');
}
