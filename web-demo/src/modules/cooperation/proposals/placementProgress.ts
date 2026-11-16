export type OperationalCounters = { target: number; placed: number; pending: number; remaining: number };
export type SchoolPipelineCounters = { reservations: number; declined: number; enterpriseAccepted: number;
  pendingCreation: number; pendingApproval: number; openForNomination: number };
export type ProgressCounters = OperationalCounters & Partial<SchoolPipelineCounters>;
export type PlacementProgress = { demandId: string; title: string; schoolOrgId: string; schoolName: string; enterpriseOrgId: string; enterpriseName: string;
  termId: string; termCode: string; cooperationId: string | null; cooperationState: string | null; status: string; counters: ProgressCounters };
export type ProgressPage = { content: PlacementProgress[]; page: number; size: number; totalElements: number; totalPages: number; totals: ProgressCounters;
  schools: { id: string; name: string }[]; terms: { id: string; name: string }[] };
export const progressLabels: Record<string, string> = { FULL: 'Đủ chỉ tiêu', SHORT: 'Còn thiếu chỉ tiêu', WAITING: 'Đang chờ xử lý', REJECTED: 'Hợp tác bị từ chối', NO_COOP: 'Chưa có hợp tác' };
const operationalKeys = ['target', 'placed', 'pending', 'remaining'] as const;
const schoolKeys = ['reservations', 'declined', 'enterpriseAccepted', 'pendingCreation', 'pendingApproval', 'openForNomination'] as const;
const object = (value: unknown): value is Record<string, unknown> => !!value && typeof value === 'object';
function validCounters(value: unknown, school: boolean) {
  return object(value) && [...operationalKeys, ...(school ? schoolKeys : [])].every(key => Number.isInteger(value[key]) && Number(value[key]) >= 0)
    && (school || schoolKeys.every(key => !Object.hasOwn(value, key)));
}
export function validProgress(value: unknown, org: string, school: boolean): value is ProgressPage {
  return object(value) && !!value.totals && Array.isArray(value.content) && validCounters(value.totals, school)
    && ['schools', 'terms'].every(key => Array.isArray(value[key]) && value[key].every(choice => object(choice) && typeof choice.id === 'string' && typeof choice.name === 'string'))
    && ['page', 'totalElements', 'totalPages'].every(key => Number.isInteger(value[key]) && Number(value[key]) >= 0)
    && value.content.every(row => object(row) && (school ? row.schoolOrgId : row.enterpriseOrgId) === org
      && typeof row.demandId === 'string' && typeof row.status === 'string' && Object.hasOwn(progressLabels, row.status) && validCounters(row.counters, school));
}
