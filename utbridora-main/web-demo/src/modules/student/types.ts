// DTO theo BC11 (Contract Pack v1.0 Candidate, dòng 753-770; trùng nguyên văn v1.1 dòng 721-738).
// Binding dùng chung cho API thật và UI; thay đổi schema phải đối chiếu contract hiện hành.
export type Code = string;
export type DateRange = { startDate: string; endDate: string };
export type SkillSource = 'SELF_DECLARED' | 'SCHOOL_VERIFIED';
export type CanonicalSkillInput = { skillCode: Code; level: number; yearsPractice: number; note: string | null };
export type SelfSkillView = { skillCode: Code; level: number; yearsPractice: number; source: SkillSource; note: string | null };
export type CustomSkillInput = { customSkillId: string | null; name: string; categoryCode: Code; level: number; yearsPractice: number; note: string | null };
export type CustomSkillView = { customSkillId: string; name: string; categoryCode: Code; level: number; yearsPractice: number; note: string | null };
export type Project = { projectId: string | null; title: string; description: string; role: string; skillCodes: Code[]; url: string | null; evidenceFileIds: string[] };
export type Certificate = { certificateId: string | null; title: string; issuer: string; issuedOn: string; expiresOn: string | null; fileId: string | null };

// Đủ 14 trường bắt buộc; PUT không nhận body thiếu trường và không có PATCH.
export type ProfileInput = {
  expectedProfileVersion: number; termId: string; majorCode: Code | null;
  skillsDeclared: boolean; skills: CanonicalSkillInput[]; customSkills: CustomSkillInput[];
  availabilityDeclared: boolean; availablePeriods: DateRange[];
  orientationCodes: Code[]; subjectCodes: Code[]; projects: Project[]; certificates: Certificate[];
  cvFileId: string | null; phone: string | null;
};
export type SelfProfileData = {
  termId: string; majorCode: Code | null;
  skillsDeclared: boolean; skills: SelfSkillView[]; customSkills: CustomSkillView[];
  availabilityDeclared: boolean; availablePeriods: DateRange[];
  orientationCodes: Code[]; subjectCodes: Code[]; projects: Project[]; certificates: Certificate[];
  cvFileId: string | null; phone: string | null;
};

export type CompletenessCheckCode = 'MAJOR_VALID' | 'STUDENT_TERM_PRESENT' | 'SKILLS_DECLARED' | 'SKILLS_VALID' | 'AVAILABILITY_DECLARED' | 'AVAILABILITY_VALID';
export type CompletenessReason = 'OK' | 'MAJOR_MISSING' | 'MAJOR_INVALID' | 'STUDENT_TERM_MISSING' | 'SKILLS_NOT_DECLARED' | 'SKILL_INVALID' | 'AVAILABILITY_NOT_DECLARED' | 'PERIOD_INVALID';
export type CompletenessResult = { policyVersion: 'demo-v1'; complete: boolean; checks: { code: CompletenessCheckCode; passed: boolean; reasonCode: CompletenessReason }[] };
export type SelfProfileView = { studentUserId: string; schoolOrgId: string; profileVersion: number; profile: SelfProfileData; completeness: CompletenessResult };

// BC21 (v1.0 dòng 857): học kỳ do T5 sở hữu.
export type TermReference = { termId: string; termCode: Code; schoolOrgId: string; startDate: string; endDate: string; status: 'DRAFT' | 'ACTIVE' | 'CLOSED'; version: number };
// Page theo C.0 (v1.0 dòng 89); khác Page của core vốn dùng `number`.
export type ContractPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
