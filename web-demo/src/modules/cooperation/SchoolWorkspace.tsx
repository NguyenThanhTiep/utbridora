import { useState } from 'react';
import type { Grant } from '../../api';
import { CooperationFlows, VerificationFlows } from './WorkflowViews';
export function SchoolWorkspace({ grants }: { grants: Grant[] }) {
  const schools = [...new Set(grants.filter(g => g.role === 'SCHOOL_STAFF' && g.organizationId).map(g => g.organizationId!))];
  const [selected, setSelected] = useState(schools[0] || ''); const scope = schools.includes(selected) ? selected : schools[0];
  if (!scope) return <section className="panel"><p>Chưa có phạm vi nhà trường.</p></section>;
  return <div className="stack"><section className="panel"><h2>Hợp tác với doanh nghiệp</h2><label>Nhà trường<select value={scope} onChange={e => setSelected(e.target.value)}>{schools.map(id => <option key={id} value={id}>{id}</option>)}</select></label></section><VerificationFlows key={scope + ':v'} org={scope} school /><CooperationFlows key={scope + ':c'} org={scope} school /></div>;
}
