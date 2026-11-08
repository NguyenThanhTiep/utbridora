import { ApiError } from '../../../api';
export function failureMessage(error: unknown): string {
  if (!(error instanceof ApiError)) return error instanceof Error ? error.message : 'Không thể xử lý yêu cầu.';
  const codes: Record<string, string> = {
    FEATURE_DISABLED: 'Chức năng nhu cầu chưa được bật trong môi trường này. Liên hệ người phụ trách để bật chức năng.',
    CATALOG_CHANGED: 'Danh mục đã thay đổi. Cập nhật phiên bản và mã danh mục trước khi lưu lại.',
    CONTRACT_PENDING_SIGNOFF: 'Chức năng nhu cầu chưa được bật trong môi trường này. Bạn có thể chuẩn bị nội dung; liên hệ người phụ trách để bật chức năng.',
    CONTRACT_NOT_APPROVED: 'Chức năng nhu cầu chưa được bật. Vui lòng liên hệ người phụ trách để hoàn tất cấu hình.',
    CONTRACT_NOT_SIGNED_OFF: 'Chức năng nhu cầu chưa được bật. Vui lòng liên hệ người phụ trách để hoàn tất cấu hình.',
    DEPENDENCY_UNAVAILABLE: 'Chưa thể xác minh danh mục hoặc kỳ thực tập. Dữ liệu nhập vẫn được giữ; vui lòng thử lại sau.',
    INVALID_CATALOG_REFERENCE: 'Mã ngành, kỹ năng hoặc phiên bản danh mục không hợp lệ. Hãy dùng mã chính thức do người phụ trách cung cấp.',
    DEMAND_LOCKED: 'Nhu cầu đã khóa và không còn được sửa bản nháp. Tải lại danh sách để xem trạng thái mới.',
    REQUEST_IN_PROGRESS: 'Yêu cầu trước đang được xử lý. Chờ một chút rồi thử lại để nhận đúng kết quả.',
    VERSION_CONFLICT: 'Nhu cầu đã thay đổi. Tải lại danh sách rồi mở lại bản nháp trước khi sửa.'
  };
  const fallback = error.status === 503 ? 'Chức năng hoặc dịch vụ danh mục chưa sẵn sàng. Dữ liệu nhập vẫn được giữ để thử lại.'
    : error.status === 422 ? 'Thông tin hoặc tiêu chí chưa hợp lệ. Kiểm tra mã danh mục, kỳ, ngày và các ngưỡng kỹ năng.'
    : error.status === 409 ? 'Dữ liệu đã thay đổi hoặc nhu cầu đang khóa. Tải lại danh sách trước khi tiếp tục.' : error.message;
  return (codes[error.errorCode || ''] || fallback) + (error.requestId ? ` Mã hỗ trợ: ${error.requestId}.` : '');
}
