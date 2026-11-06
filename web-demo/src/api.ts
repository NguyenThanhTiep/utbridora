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
export class ApiError extends Error {
  constructor(public status: number, message: string, public requestId: string | null) { super(message); }
}
const messages: Record<string, string> = {
  INVALID_CREDENTIALS: 'Email, mật khẩu không đúng hoặc tài khoản chưa sẵn sàng.',
  INVALID_REQUEST: 'Thông tin chưa hợp lệ. Kiểm tra dữ liệu, phạm vi vai trò và trạng thái thao tác.',
  CONFLICT: 'Dữ liệu vừa thay đổi hoặc đã tồn tại. Vui lòng tải lại.',
  SESSION_EXPIRED: 'Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.'
};
async function errorOf(response: Response): Promise<ApiError> {
  const body: { error?: string; message?: string } = await response.json().catch(() => ({}));
  const message = response.status === 401 ? messages[body.error || 'SESSION_EXPIRED'] || messages.SESSION_EXPIRED
    : response.status === 403 ? 'Bạn không có quyền hoặc mã bảo vệ đã hết hạn. Tải lại trang trước khi thử lại.'
    : response.status === 429 ? 'Có quá nhiều yêu cầu. Vui lòng thử lại sau.'
    : messages[body.error || ''] || 'Không thể xử lý yêu cầu. Vui lòng thử lại sau.';
  return new ApiError(response.status, message, response.headers.get('X-Request-ID'));
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
