// Test entry only, never imported by the production application.
import { createRoot } from 'react-dom/client';
import { EnterpriseProposals } from '../../src/modules/cooperation/proposals/EnterpriseProposals';
import { SchoolProposals } from '../../src/modules/cooperation/proposals/SchoolProposals';
import { InvitationConsentPage } from '../../src/modules/student/pages/InvitationConsentPage';
import { PlacementWorkspace } from '../../src/modules/cooperation/proposals/PlacementWorkspace';
import { EnterpriseMatchingSummary } from '../../src/modules/cooperation/proposals/EnterpriseMatchingSummary';
import { PlacementOverview } from '../../src/modules/cooperation/proposals/PlacementOverview';
import type { Demand } from '../../src/modules/cooperation/types';
import '../../src/style.css';
import '../../src/modules/cooperation/styles.css';
const school = new URLSearchParams(location.search).has('school');
const student = new URLSearchParams(location.search).has('student');
const workflow = new URLSearchParams(location.search).has('workflow');
const placement = new URLSearchParams(location.search).has('enterprise-placement');
const schoolPlacement = new URLSearchParams(location.search).has('school-placement');
const progress = new URLSearchParams(location.search).get('progress');
const aggregate = new URLSearchParams(location.search).has('aggregate');
const demand: Demand = { demandId: 'demand-1', enterpriseOrgId: 'enterprise-1', state: 'READY', version: 2, criteriaVersion: 1, validationState: 'VALID', cooperationId: 'coop-1', softDeletedAt: null,
  data: { schoolOrgId: 'school-1', termId: 'term-1', title: 'Nhu cầu kiểm thử', quantity: 3, description: 'Dữ liệu kiểm thử', location: null, benefits: null, deadline: '2026-11-23T00:00:00Z',
    required: { majorCodes: ['SE'], skills: [], dateRange: { startDate: '2026-11-24', endDate: '2027-02-24' } }, preferred: [], academicCatalogVersion: 'school-demo-v1', taxonomyVersion: 'taxonomy-v1' } };
createRoot(document.getElementById('root')!).render(<main className="cooperation-workspace" style={{ padding: 24 }}>
  {progress ? <PlacementOverview org={progress === 'school' ? 'school-1' : 'enterprise-1'} school={progress === 'school'} select={row => window.dispatchEvent(new CustomEvent('progress-selected', { detail: row }))} /> : schoolPlacement ? <PlacementWorkspace org="school-1" school cooperationId="coop-1" demand={demand} /> : aggregate ? <EnterpriseMatchingSummary org="enterprise-1" demand={demand} /> : student ? <InvitationConsentPage /> : school ? <SchoolProposals org="school-1" cooperationId="coop-1" demand={workflow ? demand : undefined} />
    : placement ? <PlacementWorkspace org="enterprise-1" school={false} cooperationId="coop-1" demand={demand} /> : <EnterpriseProposals org="enterprise-1" cooperationId="coop-1" />}
</main>);
