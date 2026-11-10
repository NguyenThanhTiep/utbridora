import { api } from '../../api';
import type {
  ConsentGrant, ContractPage, GrantConsent, InternshipInvitation, InvitationDecision, InvitationWithdrawal, OpportunityView,
  PreferenceView, ProfileInput, RevokeConsent, SchoolSharingPolicy, SelfProfileView, SubmitPreference, TermReference, WithdrawPreference
} from './types';

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

// Đọc đủ mọi trang; size tối đa 100 theo PageQuery (C.0 dòng 89).
async function readAllPages<T>(path: string, signal?: AbortSignal): Promise<T[]> {
  const items: T[] = [];
  for (let page = 0, total = 1; page < total; page++) {
    const result = await api<ContractPage<T>>(path + '?' + new URLSearchParams({ page: String(page), size: '100' }), { signal });
    items.push(...result.content);
    total = result.totalPages;
  }
  return items;
}
// Lệnh ghi dùng Idempotency-Key do trang giữ để thử lại cùng nội dung không tạo hiệu ứng thứ hai.
const command = <T,>(path: string, body: unknown, idempotencyKey: string) =>
  api<T>(path, { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify(body) });

// BC23 (v1.0 dòng 940): chủ lời mời lấy từ phiên đăng nhập, không gửi trong body.
export const listMyInvitations = (signal?: AbortSignal) => readAllPages<InternshipInvitation>('/invitations/me', signal);
export const getInvitation = (invitationId: string, signal?: AbortSignal) =>
  api<InternshipInvitation>('/invitations/' + encodeURIComponent(invitationId), { signal });
export const decideInvitation = (invitationId: string, decision: InvitationDecision, idempotencyKey: string) =>
  command<InternshipInvitation>('/invitations/' + encodeURIComponent(invitationId) + '/decision', decision, idempotencyKey);
export const withdrawInvitation = (invitationId: string, withdrawal: InvitationWithdrawal, idempotencyKey: string) =>
  command<InternshipInvitation>('/invitations/' + encodeURIComponent(invitationId) + '/withdraw', withdrawal, idempotencyKey);

// BC24 (v1.0 dòng 964): consent độc lập với quyết định nhận lời mời.
export const listMyConsents = (signal?: AbortSignal) => readAllPages<ConsentGrant>('/students/me/consents', signal);
export const grantConsent = (request: GrantConsent, idempotencyKey: string) =>
  command<ConsentGrant>('/students/me/consents', request, idempotencyKey);
export const revokeConsent = (consentId: string, request: RevokeConsent, idempotencyKey: string) =>
  command<ConsentGrant>('/students/me/consents/' + encodeURIComponent(consentId) + '/revoke', request, idempotencyKey);
export const getSharingPolicy = (termId: string, signal?: AbortSignal) =>
  api<SchoolSharingPolicy>('/school/terms/' + encodeURIComponent(termId) + '/profile-sharing-policy', { signal });

// BC22 (v1.0 dòng 902): T5 chỉ trả cơ hội đã công bố, đúng trường/kỳ và còn trong khoảng [publishedAt, closedAt).
export type OpportunityFilter = { termId: string; q: string; majorCode: string; page: number };
export const listOpportunities = ({ termId, q, majorCode, page }: OpportunityFilter, signal?: AbortSignal) =>
  api<ContractPage<OpportunityView>>('/opportunities?' + new URLSearchParams({
    termId, ...(q ? { q } : {}), ...(majorCode ? { majorCode } : {}), page: String(page), size: '20'
  }), { signal });
export const getOpportunity = (opportunityId: string, signal?: AbortSignal) =>
  api<OpportunityView>('/opportunities/' + encodeURIComponent(opportunityId), { signal });
// BC22 (v1.0 dòng 904): T2 sở hữu Preference; POST cùng route nhận SUBMIT hoặc WITHDRAW, chủ thể lấy từ phiên đăng nhập.
export const listMyPreferences = (signal?: AbortSignal) => readAllPages<PreferenceView>('/students/me/preferences', signal);
export const sendPreference = (request: SubmitPreference | WithdrawPreference, idempotencyKey: string) =>
  command<PreferenceView>('/students/me/preferences', request, idempotencyKey);

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
