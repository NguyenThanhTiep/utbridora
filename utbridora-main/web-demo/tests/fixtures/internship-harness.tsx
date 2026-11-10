// Test entry only. Not imported by src/main.tsx or included in the production build.
import { createRoot } from 'react-dom/client';
import { api } from '../../src/api';
import { InternshipWorkspace } from '../../src/modules/internship/pages/InternshipWorkspace';
import type { WorkspaceGateway } from '../../src/modules/internship/types';
import { me, grants } from './internship';
import '../../src/style.css';

const gateway: WorkspaceGateway = {
  readAssignments: signal => api('/test-only/t3/assignments', { signal }),
  readCohorts: (assignment, signal) => api('/test-only/t3/cohorts/' + assignment.id, { signal }),
  readGroups: (assignment, cohort, signal) => api('/test-only/t3/groups/' + assignment.id + '/' + cohort.cohortId, { signal })
};
createRoot(document.getElementById('root')!).render(<main style={{ padding: 16, maxWidth: 1200, margin: 'auto' }}>
  <InternshipWorkspace me={me} grants={grants} gateway={gateway} />
</main>);
