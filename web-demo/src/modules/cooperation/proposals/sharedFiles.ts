import { ApiError } from '../../../api';
import type { SharedFile } from './types';

const extensions: Record<string, string> = { 'application/pdf': 'pdf', 'image/png': 'png', 'image/jpeg': 'jpg' };
const limit = 10 * 1024 * 1024;

/** CP-FILE1 content is binary. T1 must recheck current resource/consent authority on every GET. */
export async function sharedFileBlob(org: string, file: SharedFile, signal: AbortSignal) {
  if (!/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(file.fileId) || file.scanStatus !== 'CLEAN'
      || !Object.hasOwn(extensions, file.contentType) || file.sizeBytes <= 0 || file.sizeBytes > limit)
    throw new ApiError(409, 'Tệp chưa đủ điều kiện để tải xuống.', null, 'FILE_NOT_CLEAN');
  const response = await fetch(`/api/v1/files/${encodeURIComponent(file.fileId)}/content`, {
    credentials: 'include', cache: 'no-store', signal, headers: { 'X-Organization-Id': org },
  });
  if (!response.ok) {
    if (response.status === 401) window.dispatchEvent(new Event('session-expired'));
    const body = await response.json().catch(() => null) as {errorCode?: unknown; requestId?: unknown} | null;
    const code = typeof body?.errorCode === 'string' && /^[A-Z0-9_]{3,64}$/.test(body.errorCode) ? body.errorCode : null;
    throw new ApiError(response.status, response.status === 503 ? 'Chưa tải được tệp. Vui lòng thử lại sau.'
      : 'Không tải được tệp trong phạm vi được chia sẻ hiện hành.', response.headers.get('X-Request-ID')
        || (typeof body?.requestId === 'string' ? body.requestId : null), code);
  }
  const mime = response.headers.get('Content-Type')?.split(';')[0].trim();
  if (response.status !== 200 || mime !== file.contentType || Number(response.headers.get('Content-Length')) > limit)
    throw new ApiError(502, 'Không xác minh được nội dung tệp tải xuống.', null, 'FILE_CONTENT_INVALID');
  const blob = await response.blob();
  if (blob.size !== file.sizeBytes || blob.size > limit)
    throw new ApiError(502, 'Không xác minh được nội dung tệp tải xuống.', null, 'FILE_CONTENT_INVALID');
  return {blob, filename: `${file.fileId}.${extensions[file.contentType]}`};
}
