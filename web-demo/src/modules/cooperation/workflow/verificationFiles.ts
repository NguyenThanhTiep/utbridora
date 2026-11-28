import { api, apiBlob, ApiError } from '../../../api';

export type VerificationFile = {
  fileId: string; resource: { resourceType: string; resourceId: string }; purpose: string;
  originalName: string; contentType: string; sizeBytes: number; scanStatus: 'PENDING' | 'CLEAN' | 'REJECTED'; deletedAt: string | null;
};
export const verificationFileProblem = (file: File) =>
  !['application/pdf', 'image/png', 'image/jpeg'].includes(file.type) ? 'Chỉ nhận tài liệu PDF, PNG hoặc JPEG.'
    : file.size === 0 ? 'Tài liệu đang rỗng.' : file.size > 10 * 1024 * 1024 ? 'Mỗi tài liệu tối đa 10 MiB.' : '';
export function validateVerificationFile(file: VerificationFile, recordId: string) {
  if (!file || !/^[a-f0-9-]{36}$/i.test(file.fileId) || file.resource?.resourceType !== 'ENTERPRISE_VERIFICATION'
    || file.resource.resourceId !== recordId || file.purpose !== 'VERIFICATION' || file.deletedAt
    || !['PENDING', 'CLEAN', 'REJECTED'].includes(file.scanStatus)) throw new Error('Invalid verification binding');
  return file;
}
export function uploadVerificationFile(org: string, recordId: string, file: File, key: string, signal: AbortSignal) {
  const body = new FormData(); body.set('file', file); body.set('resourceType', 'ENTERPRISE_VERIFICATION');
  body.set('resourceId', recordId); body.set('purpose', 'VERIFICATION');
  return api<VerificationFile>('/files', { method: 'POST', body, signal,
    headers: { 'Idempotency-Key': key, 'X-Organization-Id': org } });
}
export const verificationFileMetadata = (org: string, id: string, signal: AbortSignal) =>
  api<VerificationFile>('/files/' + encodeURIComponent(id) + '/metadata', { signal, headers: { 'X-Organization-Id': org } });
export async function downloadVerificationFile(org: string, file: VerificationFile, signal: AbortSignal) {
  const blob = await apiBlob('/files/' + encodeURIComponent(file.fileId) + '/content', { signal, headers: { 'X-Organization-Id': org } });
  if (signal.aborted) return;
  const url = URL.createObjectURL(blob), anchor = document.createElement('a');
  try { anchor.href = url; anchor.download = file.originalName; document.body.append(anchor); anchor.click(); }
  finally { anchor.remove(); setTimeout(() => URL.revokeObjectURL(url), 1000); }
}
export function verificationFileError(error: unknown) {
  if (error instanceof ApiError) {
    const messages: Record<string, string> = {
      FILE_TOO_LARGE: 'Mỗi tài liệu tối đa 10 MiB.', FILE_STORAGE_UNAVAILABLE: 'Kho tài liệu tạm thời chưa sẵn sàng. Vui lòng thử lại.',
      FILE_NOT_CLEAN: 'Tài liệu chưa được xác nhận an toàn.', BUSINESS_VALIDATION: 'Định dạng hoặc nội dung tài liệu chưa hợp lệ.',
      RESOURCE_NOT_FOUND: 'Tài liệu không còn tồn tại hoặc bạn không có quyền truy cập.',
    };
    return messages[error.errorCode || ''] || error.message;
  }
  return 'Chưa xác nhận được kết quả tải tài liệu. Thử lại sẽ dùng đúng yêu cầu vừa gửi.';
}
