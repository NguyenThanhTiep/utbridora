export type SchoolEvaluation = {
  evaluationId: string; state: 'DRAFT' | 'PENDING' | 'SUBMITTED' | 'PUBLISHED'; total100: number | null;
  revisionNo: number | null; publishedAt: string | null; visibilityMode: 'SUMMARY_ONLY' | 'DETAILED' | null; comments: string[];
};
export const evaluationLabels = { DRAFT: 'Đang chấm', PENDING: 'Chờ đánh giá', SUBMITTED: 'Đã nộp đánh giá', PUBLISHED: 'Đã công bố' };
const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object';

/** Read-only adapter for the current evaluation DTO and a scoped summary projection. No inferred grade policy. */
export function schoolEvaluation(value: unknown, scope: { caseId: string; placementId?: string; termId: string }): SchoolEvaluation | null {
  if (!object(value)) throw new Error('Invalid evaluation');
  if (typeof value.caseId !== 'string' && typeof value.placementId !== 'string') throw new Error('Unscoped evaluation');
  if (value.caseId !== undefined && value.caseId !== scope.caseId || value.placementId !== undefined && value.placementId !== scope.placementId
    || value.termId !== undefined && value.termId !== scope.termId) return null;
  const state = value.publicationStatus ?? value.state, score = value.summaryScore ?? value.total100 ?? null;
  if (typeof value.evaluationId !== 'string' || typeof state !== 'string' || !Object.hasOwn(evaluationLabels, state)
    || value.state !== undefined && value.publicationStatus !== undefined && value.state !== value.publicationStatus
    || score !== null && (typeof score !== 'number' || !Number.isFinite(score) || score < 0 || score > 100)
    || state === 'PUBLISHED' && score === null
    || value.revisionNo != null && (!Number.isSafeInteger(value.revisionNo) || Number(value.revisionNo) < 1)
    || value.publishedAt != null && (typeof value.publishedAt !== 'string' || !Number.isFinite(Date.parse(value.publishedAt)))
    || value.visibilityMode != null && !['SUMMARY_ONLY', 'DETAILED'].includes(String(value.visibilityMode))) throw new Error('Invalid evaluation fields');
  const comments: string[] = [];
  const append = (comment: unknown) => {if (typeof comment === 'string' && comment.trim()) comments.push(comment);};
  if (Array.isArray(value.comments)) value.comments.forEach(append);else append(value.comments);
  if (Array.isArray(value.scores)) value.scores.forEach(row => {if (object(row)) append(row.comment);});
  return { evaluationId: value.evaluationId, state: state as SchoolEvaluation['state'], total100: score as number | null,
    revisionNo: typeof value.revisionNo === 'number' ? value.revisionNo : null, publishedAt: typeof value.publishedAt === 'string' ? value.publishedAt : null,
    visibilityMode: value.visibilityMode as SchoolEvaluation['visibilityMode'] ?? null, comments: [...new Set(comments)] };
}
