import { api } from '../../../api';
import type { CodeKind } from './taxCode';

export type TaxAssessment = {
  rawRegistrationCode: string; normalizedTaxCode: string; codeKind: CodeKind; checksumValid: boolean | null;
  lookupStatus: 'FOUND' | 'NOT_FOUND' | 'UNAVAILABLE' | 'SKIPPED';
  taxpayerStatus: 'ACTIVE' | 'SUSPENDED' | 'DISSOLVED' | 'UNKNOWN'; taxpayerStatusLabel: string | null;
  officialName: string | null; officialAddress: string | null; taxDepartment: string | null; provider: string | null; checkedAt: string;
  verdict: 'MATCHED_ACTIVE' | 'INFO_MISMATCH' | 'INACTIVE_OR_INVALID' | 'UNVERIFIED_OFFLINE';
  nameMatch: { kind: string; score: number } | null; addressMatch: { kind: string; score: number } | null;
  mismatchedFields: string[]; reasons: string[];
};
export const lookupEnterpriseTaxCode = (orgId: string, taxCode: string, signal?: AbortSignal) =>
  api<TaxAssessment>('/enterprise/verifications/tax-lookup?taxCode=' + encodeURIComponent(taxCode), { headers: { 'X-Organization-Id': orgId }, signal });
export const fetchSchoolTaxAssessment = (orgId: string, verificationId: string, signal?: AbortSignal) =>
  api<TaxAssessment>('/school/verifications/' + encodeURIComponent(verificationId) + '/tax-assessment', { headers: { 'X-Organization-Id': orgId }, signal });
export type VerificationTaxCheck = {
  verificationId: string; submissionNo: number; state: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  attemptCount: number; nextAttemptAt: string | null; completedAt: string | null; assessment: TaxAssessment | null;
};
export const fetchSchoolTaxCheck = (orgId: string, verificationId: string, signal?: AbortSignal, refresh = false) =>
  api<VerificationTaxCheck>('/school/verifications/' + encodeURIComponent(verificationId) + '/tax-check', {
    method: refresh ? 'POST' : 'GET', headers: { 'X-Organization-Id': orgId }, signal,
  });
