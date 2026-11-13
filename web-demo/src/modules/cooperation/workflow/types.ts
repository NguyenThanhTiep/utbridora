import type { Demand } from '../types';

export type CooperationRecord = { cooperationId: string; demandId: string; state: string; version: number; submissionNo: number; termId: string;
  schoolOrgId?: string; enterpriseOrgId?: string; submittedAt?: string | null };
export type Cooperation = CooperationRecord;
export type VerificationInput = { schoolOrgId: string; previousVerificationId: string | null; registrationCode: string; registeredName: string; address: string; contactName: string; contactEmail: string; contactPhone: string; evidenceFileIds: string[] };
export type VerificationRecord = { verificationId: string; state: string; version: number; data: VerificationInput; missingFields: string[]; decisionRef: string | null;
  enterpriseOrgId?: string; schoolOrgId?: string; submittedAt?: string | null; decidedAt?: string | null };
export type Verification = VerificationRecord;
export type Detail = { demand: Demand; cooperation: Cooperation; feedback: { reasonCode: string | null; missingFields: string[]; decisionRef: string } | null };

export const states: Record<string, string> = {
  DRAFT: 'Bản nháp',
  SUBMITTED: 'Đã gửi',
  NEEDS_CHANGES: 'Cần bổ sung',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
};

export const fieldLabels: Record<string, string> = {
  title: 'Tiêu đề',
  description: 'Mô tả',
  quantity: 'Chỉ tiêu',
  required: 'Tiêu chí bắt buộc',
  preferred: 'Tiêu chí ưu tiên',
  deadline: 'Hạn tuyển',
  registrationCode: 'Mã đăng ký',
  registeredName: 'Tên đăng ký',
  address: 'Địa chỉ',
  contactName: 'Người liên hệ',
  contactEmail: 'Email liên hệ',
  contactPhone: 'Điện thoại',
  evidenceFileIds: 'Minh chứng',
};
