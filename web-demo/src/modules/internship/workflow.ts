// CP-WS2 / CP-WS3 v1.0. These wire types do not assert backend availability.
export type TaskAssignment = { assignmentId: string; caseId: string; studentUserId: string;
  state: 'NEW' | 'IN_PROGRESS' | 'SUBMITTED' | 'REVISION_REQUIRED' | 'COMPLETED'; version: number; currentSubmissionId: string | null };
export type InternshipTask = { taskId: string; title: string; description: string; groupId: string; dueAt: string;
  priority: 'LOW' | 'NORMAL' | 'HIGH'; lateAllowed: boolean; version: number; assignments: TaskAssignment[] };
export type Submission = { submissionId: string; assignmentId: string; versionNo: number; fileIds: string[];
  url: string | null; note: string | null; submittedAt: string; lateFlag: boolean };
export type SubmissionInput = { expectedAssignmentVersion: number; fileIds: string[]; url: string | null; note: string | null };
export type Review = { reviewId: string; submissionId: string; reviewedVersionNo: number; verdict: 'ACCEPT' | 'REQUEST_REVISION';
  comment: string; reviewerUserId: string; createdAt: string; assignmentVersion: number };
export type TaskComment = { commentId: string; assignmentId: string; authorUserId: string; text: string; createdAt: string };
export type WeeklyReport = { reportId: string; caseId: string; weekStart: string; revisionNo: number; version: number;
  state: 'DRAFT' | 'SUBMITTED' | 'REVIEWED'; completedWork: string; difficulties: string; nextPlan: string;
  evidenceFileIds: string[]; dueAt: string; submittedAt: string | null; reviewedAt: string | null; lateFlag: boolean };
// Additive CP-WS3 v1.1 read endpoint delivered by DEV06, ReportDto remains unchanged.
export type ReportReview = { reportId: string; revisionNo: number; reviewerUserId: string; comment: string; reviewedAt: string };
export type ReportFile = { fileId: string; resource: { resourceType: string; resourceId: string }; purpose: string;
  originalName: string; contentType: string; sizeBytes: number; sha256: string;
  scanStatus: 'PENDING' | 'CLEAN' | 'REJECTED'; createdAt: string; deletedAt: string | null };
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
export const validVersion = (version: number) => Number.isSafeInteger(version) && version >= 1;
export const textWithin = (text: string, min: number, max: number) => {
  const length = [...text.trim()].length; return length >= min && length <= max;
};
export function httpsArtifact(value: string): boolean {
  try { const url = new URL(value); return value === value.trim() && !/[\s\\]/u.test(value)
    && value.startsWith('https://') && url.protocol === 'https:' && !!url.hostname && !url.username && !url.password; }
  catch { return false; }
}
export function mondayDate(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(value + 'T00:00:00Z');
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value && date.getUTCDay() === 1;
}
