// Browser fixture only; this entry is never included by the production app.
import { createRoot } from 'react-dom/client';
import { StudentRosterPanel } from '../../src/modules/cooperation/portal/StudentRosterPanel';
import '../../src/style.css';
import '../../src/modules/cooperation/styles.css';
import '../../src/modules/cooperation/portal/features.css';
createRoot(document.getElementById('root')!).render(<main className="cooperation-workspace"><StudentRosterPanel org="school-1" viewerId="staff-1" /></main>);
