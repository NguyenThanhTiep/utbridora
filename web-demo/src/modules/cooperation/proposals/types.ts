export type EnterpriseProposal = {
  proposalItemId: string; cooperationId: string; state: string; version: number;
  shareId: string; reservationUntil: string;
};
export type SchoolProposal = Omit<EnterpriseProposal, 'shareId'> & {
  studentUserId: string; invitationId: string | null; shareId: string | null; placementId: string | null;
  activeConsentId: string | null; activeConsentVersion: number | null; activeConsentExpiresAt: string | null;
  shareableFields: string[]; shareIsActive: boolean; shareNeedsUpdate: boolean;
  shareVersion: number | null; eligibilityVersion: number;
};
export type Capacity = {
  quantity: number; activeReservations: number; placedCount: number; openForNomination: number; declinedCount: number;
};
export type SharedProfile = {
  profileVersion: number; allowedFields: string[]; validUntil: string;
  data: {
    displayName?: string | null; studentCode?: string | null; email?: string | null; phone?: string | null;
    majorCode?: string | null;
    skills?: { kind: 'CANONICAL' | 'CUSTOM'; skill: { skillCode?: string; name?: string; level: number; yearsPractice: number; note?: string | null } }[];
    projects?: { projectId: string; title: string; description: string; role: string; skillCodes: string[]; url?: string | null }[];
    certificates?: { certificateId: string; title: string; issuer: string; issuedOn: string; expiresOn: string | null }[];
    cv?: SharedFile | null; evidence?: SharedFile[];
  };
};
export type SharedFile = { fileId: string; contentType: string; sizeBytes: number; scanStatus: string };
export type Publication = { cooperationId: string; publishedAt: string | null; closedAt: string | null; version: number };
export const proposalStates: Record<string, string> = {
  RESERVED: 'Đang giữ chỗ', INVITED: 'Đã gửi lời mời', ACCEPTED: 'Sinh viên đã đồng ý',
  SHARED: 'Đã chia sẻ hồ sơ', ENTERPRISE_ACCEPTED: 'Doanh nghiệp đã đồng ý',
  PLACED: 'Đã chốt thực tập', DECLINED: 'Đã từ chối', EXPIRED: 'Hết hạn', REVOKED: 'Đã rút lại',
};
