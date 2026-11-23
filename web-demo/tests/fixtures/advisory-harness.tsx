import { createRoot } from 'react-dom/client';
import { AdvisoryPanel } from '../../src/modules/matching/AdvisoryPanel';
import '../../src/style.css';
import '../../src/modules/matching/matching.css';
const id = 'a17e2026-1121-4000-8000-000000000001';
createRoot(document.getElementById('root')!).render(<main style={{ maxWidth: '1100px', margin: '2rem auto', padding: '1rem' }}>
  <p>Giao diện kiểm thử với dữ liệu tổng hợp</p>
  <AdvisoryPanel run={{ matchRunId: id, demandVersion: 2, criteriaVersion: 1, profileSnapshotVersion: 'a'.repeat(64) }}
    termId={id} cooperationId={id} organizationId={id} canCreate canNominate onNomination={() => { document.title = 'nomination-sent'; }} />
</main>);
