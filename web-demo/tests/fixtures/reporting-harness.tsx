// Browser fixtures only; excluded from the production entry.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { CooperationSubmissionHistory } from '../../src/modules/cooperation/workflow/CooperationSubmissionHistory';
import { MatchingStatistics } from '../../src/modules/matching/MatchingStatistics';
import '../../src/style.css';
function Harness() {
  const [org,setOrg]=useState('school-one');
  return <main style={{padding:'1rem',minWidth:0}}>{location.search.includes('statistics')?<MatchingStatistics />:<>
    <button onClick={()=>setOrg('school-two')}>Chuyển trường kiểm thử</button><CooperationSubmissionHistory org={org} cooperationId="cooperation-one" />
  </>}</main>;
}
createRoot(document.getElementById('root')!).render(<Harness />);
