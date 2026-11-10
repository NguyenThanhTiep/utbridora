import { api } from '../../api';
import type { ContractPage, DateRange, ProfileInput, SelfProfileView, TermReference } from './types';

// BC11 (Contract Pack v1.0, dòng 778). CSRF do api() của core tự thêm cho PUT.
export const getMyProfile = (termId: string) =>
  api<SelfProfileView>('/students/me/profile?termId=' + encodeURIComponent(termId));
export const saveMyProfile = (input: ProfileInput, idempotencyKey: string) =>
  api<SelfProfileView>('/students/me/profile', { method: 'PUT', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(input) });

export type AcademicCatalogItem = { code: string; name: string; majorCodes: string[]; status: 'ACTIVE' | 'INACTIVE' };
export async function listMajors(termId: string): Promise<AcademicCatalogItem[]> {
  type Catalog = { catalogVersion: string; entries: ContractPage<AcademicCatalogItem> };
  const read = (page: number, version?: string) => api<Catalog>('/students/me/catalogs?' + new URLSearchParams({
    termId, kind: 'MAJOR', page: String(page), size: '100', ...(version ? { expectedCatalogVersion: version } : {})
  }));
  const first = await read(0), entries = [...first.entries.content];
  for (let page = 1; page < first.entries.totalPages; page++) entries.push(...(await read(page, first.catalogVersion)).entries.content);
  return entries.filter(entry => entry.status === 'ACTIVE');
}

// BC21 (v1.0 dòng 865): T5 sở hữu TermReference; size tối đa 100 theo PageQuery (C.0 dòng 89).
export async function listTerms(schoolOrgId: string): Promise<TermReference[]> {
  const read = (page: number) => api<ContractPage<TermReference>>(
    '/terms?' + new URLSearchParams({ schoolOrgId, page: String(page), size: '100' }));
  const first = await read(0);
  const terms = [...first.content];
  for (let page = 1; page < first.totalPages; page++) terms.push(...(await read(page)).content);
  return terms;
}

export type Opportunity = {
  opportunityId: string; cooperationId: string; demandId: string; schoolOrgId: string; enterpriseOrgId: string;
  termId: string; enterpriseName: string; title: string; majorCodes: string[];
  dateRange: DateRange; benefits: string | null; location: string | null;
  publishedAt: string; closedAt: string; version: number;
};
export type StudentPreference = {
  preferenceId: string; studentUserId: string; schoolOrgId: string; termId: string; opportunityId: string;
  intentVersion: number; version: number; status: 'SUBMITTED' | 'WITHDRAWN' | 'REVIEWED'; message: string | null;
  createdAt: string; withdrawnAt: string | null; reviewedAt: string | null; reviewedByUserId: string | null;
};
export async function listOpportunities(filters: { termId: string; q?: string; majorCode?: string; page?: number; size?: number }) {
  return api<ContractPage<Opportunity>>('/opportunities?' + new URLSearchParams({
    termId: filters.termId, page: String(filters.page ?? 0), size: String(filters.size ?? 25),
    ...(filters.q ? { q: filters.q } : {}), ...(filters.majorCode ? { majorCode: filters.majorCode } : {})
  }));
}
export async function listMyPreferences(termId: string): Promise<StudentPreference[]> {
  const read = (page: number) => api<ContractPage<StudentPreference>>('/students/me/preferences?' +
    new URLSearchParams({ termId, page: String(page), size: '100' }));
  const first = await read(0), entries = [...first.content];
  for (let page = 1; page < first.totalPages; page++) entries.push(...(await read(page)).content);
  return entries;
}
export function submitPreference(input: { opportunityId: string; message: string | null; expectedIntentVersion: number }, idempotencyKey: string) {
  return api<StudentPreference>('/students/me/preferences', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ action: 'SUBMIT', ...input })
  });
}
export function withdrawPreference(preference: StudentPreference, idempotencyKey: string) {
  return api<StudentPreference>('/students/me/preferences', {
    method: 'POST', headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ action: 'WITHDRAW', preferenceId: preference.preferenceId,
      expectedVersion: preference.version, reasonCode: 'STUDENT_WITHDREW' })
  });
}

// crypto.randomUUID chỉ có trong secure context (localhost/HTTPS); dự phòng UUID v4 từ getRandomValues.
export function newIdempotencyKey() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 0x0f) | 0x40; bytes[8] = (bytes[8] & 0x3f) | 0x80;
  const hex = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

// Chép đủ 14 trường để PUT không làm mất dữ liệu đã khai. Kỹ năng ghi không có `source`;
// Server xác định nguồn xác minh; client không gửi trường source.
export function toProfileInput(view: SelfProfileView): ProfileInput {
  const profile = view.profile;
  return {
    expectedProfileVersion: view.profileVersion, termId: profile.termId, majorCode: profile.majorCode,
    skillsDeclared: profile.skillsDeclared,
    skills: profile.skills.map(({ skillCode, level, yearsPractice, note }) => ({ skillCode, level, yearsPractice, note })),
    customSkills: profile.customSkills.map(({ customSkillId, name, categoryCode, level, yearsPractice, note }) => ({ customSkillId, name, categoryCode, level, yearsPractice, note })),
    availabilityDeclared: profile.availabilityDeclared,
    availablePeriods: profile.availablePeriods.map(({ startDate, endDate }) => ({ startDate, endDate })),
    orientationCodes: [...profile.orientationCodes], subjectCodes: [...profile.subjectCodes],
    projects: profile.projects.map(({ projectId, title, description, role, skillCodes, url, evidenceFileIds }) =>
      ({ projectId, title, description, role, skillCodes: [...skillCodes], url, evidenceFileIds: [...evidenceFileIds] })),
    certificates: profile.certificates.map(({ certificateId, title, issuer, issuedOn, expiresOn, fileId }) => ({ certificateId, title, issuer, issuedOn, expiresOn, fileId })),
    cvFileId: profile.cvFileId, phone: profile.phone
  };
}
