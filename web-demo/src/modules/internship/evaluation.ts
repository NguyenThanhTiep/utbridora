import { ApiError } from '../../api';
import { accessLost } from './workflow-api';
import { textWithin, validVersion } from './workflow';
export type Criterion = { criterionCode: string; label: string; weight: number };
export type Rubric = { templateId: string; templateVersion: number; criteria: Criterion[] };
export type Score = { criterionCode: string; score: number; comment: string | null; evidenceFileIds: string[] };
export type Evaluation = { evaluationId: string; caseId: string; template: Rubric; scores: Score[]; total100: number;
  state: 'DRAFT' | 'SUBMITTED' | 'PUBLISHED'; revisionNo: number; version: number; publishedAt: string | null; decisionRef: string | null };
export type ScoreDraft = Omit<Score, 'score' | 'comment'> & { score: string; comment: string };
export type StudentResult = { caseId: string; visibility: 'VISIBLE' | 'HIDDEN'; evaluation: Evaluation | null; reasonCode: 'VISIBLE' | 'NOT_AVAILABLE' };
export type VisibilityPolicy = { enabled: boolean; version: number; termId: string; schoolOrgId?: string;
  enterpriseOrgId?: string; caseId?: string | null; scope?: 'ORGANIZATION_TERM' | 'CASE' };
export const gradeAccessLost = (e: unknown) => accessLost(e) || e instanceof ApiError && e.status === 404;
export const scoreValid = (s: string) => /^(?:[0-4](?:\.\d{1,2})?|5(?:\.0{1,2})?)$/.test(s);
const cents = (s: string) => { const [a,b=''] = s.split('.'); return BigInt(a) * 100n + BigInt(b.padEnd(2,'0')); };
export function rubricValid(t: Rubric): boolean {
  return validVersion(t.templateVersion) && t.criteria.length > 0 && t.criteria.length <= 100
    && new Set(t.criteria.map(c => c.criterionCode)).size === t.criteria.length
    && t.criteria.every(c => Number.isFinite(c.weight) && c.weight > 0 && c.weight <= 100 && Number(c.weight.toFixed(2)) === c.weight)
    && t.criteria.reduce((sum,c) => sum + cents(c.weight.toFixed(2)),0n) === 10000n;
}
export const toDraft = (v: Evaluation): ScoreDraft[] => v.scores.map(s => ({ ...s, score: String(s.score), comment: s.comment || '', evidenceFileIds: [...s.evidenceFileIds] }));
export function scoresValid(t: Rubric, scores: ScoreDraft[]) {
  return rubricValid(t) && scores.length === t.criteria.length && new Set(scores.map(s => s.criterionCode)).size === scores.length
    && t.criteria.every(c => scores.some(s => s.criterionCode === c.criterionCode))
    && scores.every(s => scoreValid(s.score) && textWithin(s.comment,0,2000) && s.evidenceFileIds.length <= 20);
}
// Integer hundredths, one HALF_UP rounding of the final total. Server remains authoritative.
export function previewTotal(t: Rubric, scores: ScoreDraft[]): string | null {
  if (!scoresValid(t,scores)) return null;
  const sum = t.criteria.reduce((n,c) => n + cents(c.weight.toFixed(2)) * cents(scores.find(s => s.criterionCode === c.criterionCode)!.score),0n);
  const rounded = (sum + 250n) / 500n; return (rounded / 100n) + '.' + String(rounded % 100n).padStart(2,'0');
}
export const wireScores = (scores: ScoreDraft[]): Score[] => scores.map(s => ({ ...s, score: Number(s.score), comment: s.comment.trim() || null }));
export function studentEvaluation(result: StudentResult, caseId: string): Evaluation | null {
  if (result.caseId !== caseId) throw new Error('Mismatched result');
  if (result.visibility === 'HIDDEN' && result.evaluation === null && result.reasonCode === 'NOT_AVAILABLE') return null;
  const v=result.evaluation;
  if (result.visibility !== 'VISIBLE' || result.reasonCode !== 'VISIBLE' || !v || v.caseId !== caseId
    || v.state !== 'PUBLISHED' || !v.publishedAt || !v.decisionRef || !validVersion(v.version)) throw new Error('Invalid published result');
  return v;
}
