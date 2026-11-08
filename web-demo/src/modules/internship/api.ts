import { api } from '../../api';
import type { Cohort, InternshipGroup, WorkspaceGateway, WorkspacePage } from './types';

export class WorkspacePendingError extends Error {
  constructor() { super('Không gian thực tập đang chờ kết nối dữ liệu phân công. Vui lòng quay lại sau.'); }
}

export class WorkspaceScopeError extends Error {
  constructor() { super('Không thể xác nhận phạm vi dữ liệu. Vui lòng tải lại hoặc liên hệ người phụ trách.'); }
}

// Deliberately fail closed until DEV05/DEV06 publish the authoritative assignment
// read API AND the selected-context transport. Do not infer assignments from grants.
export const pendingWorkspaceGateway: WorkspaceGateway = {
  readAssignments: async () => { throw new WorkspacePendingError(); },
  readCohorts: async () => { throw new WorkspacePendingError(); },
  readGroups: async () => { throw new WorkspacePendingError(); }
};

// Ready for the owner adapter to compose after scope selection is agreed.
// These do not invent an assignment endpoint, scope header or query parameter.
export const workspaceReads = {
  cohorts: (termId: string, page: number, signal: AbortSignal) => api<WorkspacePage<Cohort>>(
    '/t3/cohorts?' + new URLSearchParams({ termId, page: String(page), size: '25' }), { signal }),
  cohortGroups: (cohortId: string, page: number, signal: AbortSignal) => api<WorkspacePage<InternshipGroup>>(
    '/t3/cohorts/' + encodeURIComponent(cohortId) + '/groups?'
      + new URLSearchParams({ page: String(page), size: '25' }), { signal }),
  mentorGroups: (page: number, signal: AbortSignal) => api<WorkspacePage<InternshipGroup>>(
    '/t3/mentors/me/groups?' + new URLSearchParams({ page: String(page), size: '25' }), { signal })
};
