import { api, ApiError } from '../../api';
import { newIdempotencyKey } from '../student/api';
import type { WorkspacePage } from './types';

export const contextHeaders = (contextId?: string): Record<string, string> => contextId ? { 'X-Context-Assignment-Id': contextId } : {};
export const accessLost = (error: unknown) => error instanceof ApiError
  && (error.status === 401 || error.status === 403 && error.errorCode !== 'CSRF_INVALID');
export function workflowError(error: unknown): string {
  if (!(error instanceof ApiError)) return 'Chưa nhận được kết quả từ máy chủ. Giữ nguyên thao tác để thử lại.';
  const message = accessLost(error) ? 'Phiên hoặc quyền truy cập đã thay đổi. Vui lòng xác nhận lại phạm vi.'
    : error.status === 409 ? 'Dữ liệu đã thay đổi hoặc yêu cầu đang xử lý. Nội dung nhập được giữ; tải phiên bản hiện tại trước khi gửi lại.'
    : error.status === 404 || error.status === 503 ? 'Chức năng chưa sẵn sàng hoặc dữ liệu không còn truy cập được. Vui lòng thử lại sau.'
    : error.status === 422 ? 'Dữ liệu hoặc trạng thái chưa hợp lệ. Kiểm tra nội dung, hạn nộp và trạng thái thực tập.' : error.message;
  return message + (error.errorCode ? ' [' + error.errorCode + ']' : '') + (error.requestId ? ' Mã hỗ trợ: ' + error.requestId : '');
}
export async function workflowRead<T>(path: string, contextId: string | undefined, signal: AbortSignal): Promise<T> {
  const timer = AbortSignal.timeout(20_000);
  return api<T>(path, { headers: contextHeaders(contextId), signal: AbortSignal.any([signal, timer]) });
}
export async function workflowPages<T>(path: string, contextId: string | undefined, signal: AbortSignal): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0, total = 1; page < total; page++) {
    const result = await workflowRead<WorkspacePage<T>>(path + (path.includes('?') ? '&' : '?') + 'size=100&page=' + page, contextId, signal);
    if (signal.aborted) throw new DOMException('Aborted', 'AbortError');
    if (!Array.isArray(result.content) || result.page !== page || !Number.isInteger(result.totalPages) || result.totalPages < 0 || result.totalPages > 10001)
      throw new Error('Invalid page');
    items.push(...result.content); total = result.totalPages;
  }
  return items;
}
// Store the exact serialized payload AND key until the server gives a definite outcome.
// Scope-owned component holds this only in memory; never localStorage or cross-account reuse.
export type WorkflowAttempt = { path: string; method: string; body: string | FormData; key: string };
export const newAttempt = (path: string, method: string, payload: unknown) =>
  ({ path, method, body: JSON.stringify(payload), key: newIdempotencyKey() });
export function executeAttempt<T>(attempt: WorkflowAttempt, contextId: string | undefined, signal: AbortSignal): Promise<T> {
  return api<T>(attempt.path, { method: attempt.method, body: attempt.body,
    headers: { ...contextHeaders(contextId), 'Idempotency-Key': attempt.key }, signal: AbortSignal.any([signal, AbortSignal.timeout(20_000)]) });
}
export const uncertainOutcome = (error: unknown) => !(error instanceof ApiError) || error.status >= 500
  || error.status === 429 || ['REQUEST_IN_PROGRESS', 'CSRF_INVALID'].includes(error.errorCode || '');
