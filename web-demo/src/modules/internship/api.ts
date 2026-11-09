import { api } from '../../api';
import type { Cohort, ContextAssignment, InternshipGroup, WorkspaceAssignment, WorkspaceGateway, WorkspacePage } from './types';

export class WorkspacePendingError extends Error {
  constructor() { super('Không gian thực tập đang chờ kết nối dữ liệu phân công. Vui lòng quay lại sau.'); }
}

export class WorkspaceScopeError extends Error {
  constructor() { super('Không thể xác nhận phạm vi dữ liệu. Vui lòng tải lại hoặc liên hệ người phụ trách.'); }
}

// Explicit unavailable gateway for fixture/backward compatibility; the application uses liveWorkspaceGateway.
export const pendingWorkspaceGateway: WorkspaceGateway = {
  readAssignments: async () => { throw new WorkspacePendingError(); },
  readCohorts: async () => { throw new WorkspacePendingError(); },
  readGroups: async () => { throw new WorkspacePendingError(); }
};

const selectedHeaders = (assignment: WorkspaceAssignment) => ({ 'X-Context-Assignment-Id': assignment.id });
async function allPages<T>(read: (page: number) => Promise<WorkspacePage<T>>): Promise<T[]> {
  const first = await read(0), content = [...first.content];
  for (let page = 1; page < first.totalPages; page++) content.push(...(await read(page)).content);
  return content;
}
// Runtime binding documented in docs/guides/WEEK1_RUNTIME.md. Server revalidates every selected assignment.
export const liveWorkspaceGateway: WorkspaceGateway = {
  readAssignments: signal => api<ContextAssignment[]>('/t3/context-assignments/me', { signal }),
  readCohorts: (assignment, signal) => allPages(page => api<WorkspacePage<Cohort>>('/t3/cohorts?'
    + new URLSearchParams({ termId: assignment.scope.termId, page: String(page), size: '100' }), { signal, headers: selectedHeaders(assignment) })),
  readGroups: (assignment, cohort, signal) => allPages(page => api<WorkspacePage<InternshipGroup>>('/t3/cohorts/'
    + encodeURIComponent(cohort.cohortId) + '/groups?' + new URLSearchParams({ page: String(page), size: '100' }), { signal, headers: selectedHeaders(assignment) }))
};

// Low-level canonical reads retained for consumers; the live gateway adds the selected context.
export const workspaceReads = {
  cohorts: (termId: string, page: number, signal: AbortSignal) => api<WorkspacePage<Cohort>>(
    '/t3/cohorts?' + new URLSearchParams({ termId, page: String(page), size: '25' }), { signal }),
  cohortGroups: (cohortId: string, page: number, signal: AbortSignal) => api<WorkspacePage<InternshipGroup>>(
    '/t3/cohorts/' + encodeURIComponent(cohortId) + '/groups?'
      + new URLSearchParams({ page: String(page), size: '25' }), { signal }),
  mentorGroups: (page: number, signal: AbortSignal) => api<WorkspacePage<InternshipGroup>>(
    '/t3/mentors/me/groups?' + new URLSearchParams({ page: String(page), size: '25' }), { signal })
};
