export type OperationFeedback = {
  id: number;
  revision: number;
  key: string;
  tone: 'success' | 'error' | 'info';
  title: string;
  description: string;
};
type MutationFeedback = { key: string; title: string; description: string };

let sequence = 0;
let feedback: OperationFeedback[] = [];
const listeners = new Set<() => void>();
const emit = () => listeners.forEach(listener => listener());
export const operationFeedbackStore = {
  subscribe(listener: () => void) { listeners.add(listener); return () => { listeners.delete(listener); }; },
  getSnapshot: () => feedback,
};
export function dismissOperationFeedback(id: number) {
  feedback = feedback.filter(item => item.id !== id);
  emit();
}
export function clearOperationFeedback() {
  feedback = [];
  emit();
}
function showFeedback(value: Omit<OperationFeedback, 'id' | 'revision'>) {
  const existing = feedback.find(item => item.key === value.key);
  const item = { ...value, id: existing?.id ?? ++sequence, revision: ++sequence };
  feedback = [...feedback.filter(current => current.id !== item.id), item].slice(-3);
  emit();
}

// All modules use api()/send(). Reads and read-like POSTs must stay quiet.
export function mutationFeedback(path: string, init: RequestInit): MutationFeedback | null {
  const method = (init.method || 'GET').toUpperCase();
  const route = path.split('?')[0].replace(/\/$/, '');
  if (['GET', 'HEAD', 'OPTIONS'].includes(method)
    || /^\/auth\/(login|logout)$/.test(route)
    || /\/(resolve|preview|validate|export|check|handoff-check|readiness)$/.test(route)) return null;

  let title = method === 'DELETE' ? 'Xóa thành công' : ['PUT', 'PATCH'].includes(method) ? 'Lưu thành công' : 'Thao tác thành công';
  const resources: [RegExp, string][] = [
    [/\/auth\/password/, 'Bảo mật tài khoản'],
    [/\/auth\/invitations|\/admin\/invitations/, 'Lời mời tài khoản'],
    [/\/admin\/organizations/, 'Tổ chức'],
    [/\/admin\/memberships/, 'Vai trò và quyền truy cập'],
    [/\/admin\/users/, 'Tài khoản người dùng'],
    [/delegations/, 'Phân công quyền cán bộ'],
    [/\/taxonomy\//, 'Danh mục kỹ năng'],
    [/\/demands/, 'Nhu cầu thực tập'],
    [/\/cooperations/, 'Đề nghị hợp tác'],
    [/\/verifications/, 'Hồ sơ xác minh doanh nghiệp'],
    [/student-eligibilit/, 'Điều kiện thực tập'],
    [/student-import/, 'Danh sách sinh viên'],
    [/profile-shares|\/shares$/, 'Chia sẻ hồ sơ sinh viên'],
    [/\/consents/, 'Quyền chia sẻ hồ sơ'],
    [/\/students\/me\/profile/, 'Hồ sơ cá nhân'],
    [/\/preferences/, 'Nguyện vọng thực tập'],
    [/\/invitations|\/invite$/, 'Lời mời thực tập'],
    [/\/publication$/, 'Công bố cơ hội thực tập'],
    [/\/matching\//, 'Đối chiếu và đề xuất sinh viên'],
    [/\/proposals/, 'Ứng viên đề xuất'],
    [/\/placements/, 'Tiếp nhận thực tập'],
    [/\/files/, 'Tài liệu'],
    [/\/t3\//, 'Quản lý thực tập'],
    [/\/terms/, 'Kỳ thực tập'],
  ];
  const description = resources.find(([pattern]) => pattern.test(route))?.[1] || 'Yêu cầu của bạn đã được xử lý.';

  if (method === 'POST' && /\/(organizations|invitations|school-delegations|delegations|categories|skills|aliases|demands|cooperations|verifications|placements|cohorts|groups|tasks|reports|certificates|projects)$/.test(route)) title = 'Tạo thành công';
  if (/\/profile$/.test(route)) title = 'Lưu thành công';
  if (/\/(submit|submit-with-cooperation|invite|resend|resend-invitation|send)$/.test(route)) title = 'Gửi thành công';
  if (/\/decision(s)?$/.test(route)) title = 'Đã ghi nhận quyết định';
  if (/\/approve$/.test(route)) title = 'Phê duyệt thành công';
  if (/\/activate$/.test(route)) title = 'Kích hoạt thành công';
  if (/\/(cancel|withdraw)$/.test(route)) title = 'Đã ghi nhận yêu cầu hủy';
  if (/\/soft-delete$/.test(route)) title = 'Xóa thành công';
  if (/\/restore$/.test(route)) title = 'Khôi phục thành công';
  if (/\/reopen$/.test(route)) title = 'Mở lại thành công';
  if (/\/revoke(-sessions)?$/.test(route) || method === 'DELETE' && /memberships|delegations/.test(route)) title = 'Thu hồi thành công';
  if (/\/nominate$/.test(route)) title = 'Đề xuất thành công';
  if (/\/evaluate$/.test(route)) title = 'Đã hoàn tất đối chiếu';
  if (/\/admin\/memberships$/.test(route) && method === 'POST') title = 'Cấp vai trò thành công';
  if (/\/password\/(change|reset)$/.test(route)) title = 'Đã cập nhật mật khẩu';
  if (/\/password\/forgot$/.test(route)) title = 'Đã tiếp nhận yêu cầu';
  if (/\/auth\/invitations\/accept$/.test(route)) title = 'Kích hoạt tài khoản thành công';

  const key = method + ':' + route + ':' + (new Headers(init.headers).get('Idempotency-Key') || '');
  return { key, title, description };
}
export function mutationSucceeded(operation: MutationFeedback, status: number) {
  showFeedback({ ...operation, tone: status === 202 ? 'info' : 'success',
    ...(status === 202 ? { title: 'Đã tiếp nhận yêu cầu', description: 'Hệ thống đang xử lý. Theo dõi kết quả tại trang hiện tại.' } : {}) });
}
export function mutationFailed(operation: MutationFeedback, error: unknown) {
  const status = typeof error === 'object' && error !== null && 'status' in error ? Number(error.status) : null;
  const inProgress = typeof error === 'object' && error !== null && 'errorCode' in error && error.errorCode === 'REQUEST_IN_PROGRESS';
  const uncertain = status === null || status >= 500 || inProgress;
  showFeedback({ key: operation.key, tone: uncertain ? 'info' : 'error',
    title: uncertain ? 'Chưa xác nhận được kết quả' : 'Chưa thể hoàn tất thao tác',
    description: uncertain ? 'Giữ nội dung đang nhập và kiểm tra hướng dẫn tại biểu mẫu trước khi thử lại.'
      : operation.description + ' · Kiểm tra thông tin và hướng dẫn tại biểu mẫu để tiếp tục.' });
}
