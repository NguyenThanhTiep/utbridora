export type Membership = { role: string; organizationId: string | null };
export type Me = { userId: string; email: string; displayName: string; memberships: Membership[] };
export type Grant = Membership & { permissions: string[] };
export type Page<T> = { content: T[]; number: number; totalElements: number; totalPages: number };
export type User = Me & { status: string; lockedUntil: string | null };
export type Organization = { id: string; name: string; type: string; status: string };
export type Audit = { id: string; actorId: string | null; action: string; result: string; resourceType: string;
  resourceId: string | null; organizationId: string | null; requestId: string; occurredAt: string; reasonCode: string | null };
export type History = { id: string; actorId: string; fromStatus: string; toStatus: string; reasonCode: string; changedAt: string };
type Csrf = { headerName: string; token: string };
let csrf: Csrf | null = null;
export type FieldError = { field: string; code: string; message: string };
export type ApiFieldError = FieldError;
export class ApiError extends Error {
  constructor(public status: number, message: string, public requestId: string | null,
    public errorCode: string | null = null, public fieldErrors: FieldError[] = []) { super(message); }
}
const messages: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email, mật khẩu không đúng hoặc tài khoản chưa sẵn sàng.',
  INVALID_REQUEST: 'Thông tin chưa hợp lệ. Kiểm tra dữ liệu, phạm vi vai trò và trạng thái thao tác.',
  CONFLICT: 'Dữ liệu vừa thay đổi hoặc đã tồn tại. Vui lòng tải lại.',
  SESSION_EXPIRED: 'Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.'
};
// ErrorEnvelope (Contract C.0) adds errorCode/fieldErrors; legacy core bodies only carry error/message.
const reasonOf = (value: unknown) => typeof value === 'string' && /^[A-Z0-9_]{3,64}$/.test(value) ? value : null;
const isFieldError = (value: unknown): value is FieldError => typeof value === 'object' && value !== null
  && ['field', 'code', 'message'].every(key => typeof (value as Record<string, unknown>)[key] === 'string');
async function errorOf(response: Response): Promise<ApiError> {
  const body: { error?: string; errorCode?: string; requestId?: string; message?: string; fieldErrors?: unknown } = await response.json().catch(() => ({}));
  const code = reasonOf(body.errorCode) || reasonOf(body.error);
  const message = response.status === 401 ? messages[code || 'SESSION_EXPIRED'] || messages.SESSION_EXPIRED
    : response.status === 403 ? code === 'CSRF_INVALID'
      ? 'Mã bảo vệ đã hết hạn. Thử lưu lại để lấy mã mới; nội dung nhập vẫn được giữ.'
      : code === 'ACCESS_DENIED' && body.errorCode
        ? 'Bạn không có quyền thực hiện thao tác này trong tổ chức đang chọn.'
        : 'Bạn không có quyền hoặc mã bảo vệ đã hết hạn. Kiểm tra quyền rồi thử lại.'
    : response.status === 429 ? 'Có quá nhiều yêu cầu. Vui lòng thử lại sau.'
    : messages[code || ''] || 'Không thể xử lý yêu cầu. Vui lòng thử lại sau.';
  const fields: ApiFieldError[] = Array.isArray(body.fieldErrors) ? body.fieldErrors.filter((field): field is ApiFieldError =>
    field != null && typeof field.field === 'string' && typeof field.code === 'string' && typeof field.message === 'string') : [];
  return new ApiError(response.status, message, response.headers.get('X-Request-ID') || (typeof body.requestId === 'string' ? body.requestId : null), code, fields.filter(isFieldError));
}
export async function getCsrf(): Promise<Csrf> {
  const response = await fetch('/api/v1/auth/csrf', { credentials: 'include', cache: 'no-store' });
  if (!response.ok) throw await errorOf(response);
  csrf = await response.json() as Csrf;
  return csrf;
}
export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const mutation = !['GET', 'HEAD', 'OPTIONS'].includes((init.method || 'GET').toUpperCase());
  if (mutation && !csrf) await getCsrf();
  const headers = new Headers(init.headers);
  if (init.body) headers.set('Content-Type', 'application/json');
  if (mutation && csrf) headers.set(csrf.headerName, csrf.token);
  let response: Response;
  try { response = await fetch('/api/v1' + path, { ...init, credentials: 'include', cache: 'no-store', headers }); }
  catch { throw new Error('Không kết nối được máy chủ. Kiểm tra mạng và thử lại.'); }
  if (!response.ok) {
    if ([401, 403].includes(response.status)) csrf = null;
    if (response.status === 401 && !['/auth/login', '/auth/me'].includes(path)) window.dispatchEvent(new Event('session-expired'));
    throw await errorOf(response);
  }
  return response.status === 204 ? undefined as T : response.json() as Promise<T>;
}
export const send = <T = { message: string }>(path: string, method: string, body?: unknown) =>
  api<T>(path, { method, body: body === undefined ? undefined : JSON.stringify(body) });
export async function login(email: string, password: string): Promise<Me> {
  const me = await send<Me>('/auth/login', 'POST', { email, password });
  csrf = null;
  await getCsrf();
  return me;
}
export async function logout(): Promise<void> {
  await send('/auth/logout', 'POST');
  csrf = null;
}
export const whoAmI = () => api<Me>('/auth/me');
