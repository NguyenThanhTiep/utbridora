import { api, apiBlob } from '../../api';

// CP-FILE (v1.0 dòng 264–310): T1 sở hữu lưu trữ, quét và quyền tải; hồ sơ T2 chỉ giữ fileId.
// STUDENT_PROFILE: resourceId là studentUserId; CV và minh chứng là PDF/PNG/JPEG tối đa 10 MiB (dòng 278).
export type ProfileFilePurpose = 'CV' | 'EVIDENCE';
export type ScanStatus = 'PENDING' | 'CLEAN' | 'REJECTED';
export type ProfileFile = {
  fileId: string; resource: { resourceType: string; resourceId: string }; purpose: string; originalName: string;
  contentType: string; sizeBytes: number; sha256: string; scanStatus: ScanStatus; createdAt: string; deletedAt: string | null;
};
export const scanLabels: Record<ScanStatus, string> = { PENDING: 'Đang chờ quét', CLEAN: 'Đã xác nhận an toàn', REJECTED: 'Bị từ chối' };
const ALLOWED_TYPES = ['application/pdf', 'image/png', 'image/jpeg'], MAX_BYTES = 10 * 1024 * 1024;
export const fileProblem = (file: File) => !ALLOWED_TYPES.includes(file.type) ? 'Chỉ nhận tệp PDF, PNG hoặc JPEG.'
  : file.size === 0 ? 'Tệp đang rỗng.' : file.size > MAX_BYTES ? 'Tệp vượt quá 10 MiB.' : '';

// Tệp mới luôn PENDING; chỉ gắn vào hồ sơ được khi T1 xác nhận CLEAN (BC11 dòng 778).
export function uploadProfileFile(studentUserId: string, purpose: ProfileFilePurpose, file: File, idempotencyKey: string) {
  const body = new FormData();
  body.set('file', file); body.set('resourceType', 'STUDENT_PROFILE'); body.set('resourceId', studentUserId); body.set('purpose', purpose);
  return api<ProfileFile>('/files', { method: 'POST', body, headers: { 'Idempotency-Key': idempotencyKey } });
}
export const getFileMetadata = (fileId: string, signal?: AbortSignal) =>
  api<ProfileFile>('/files/' + encodeURIComponent(fileId) + '/metadata', { signal });
// Server kiểm lại quyền và CLEAN ở mỗi lần tải; không có URL lưu trữ công khai.
export async function downloadProfileFile(file: ProfileFile) {
  const url = URL.createObjectURL(await apiBlob('/files/' + encodeURIComponent(file.fileId) + '/content'));
  try {
    const anchor = document.createElement('a');
    anchor.href = url; anchor.download = file.originalName; document.body.append(anchor); anchor.click(); anchor.remove();
  } finally { setTimeout(() => URL.revokeObjectURL(url), 0); }
}
