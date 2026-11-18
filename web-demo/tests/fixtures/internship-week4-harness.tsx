// Synthetic API fixture. Production panels/transport; no backend authorization claim.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { MentorEvaluation, StudentEvaluation, SchoolEvaluations, VisibilityEditor } from '../../src/modules/internship/pages/EvaluationPanels';
import { EvaluationsPage } from '../../src/modules/internship/pages/EvaluationsPage';
import { me } from './internship';
import '../../src/style.css';
import '../../src/modules/internship/styles.css';
export const caseId='00000000-0000-4000-8000-000000000001', termId='00000000-0000-4000-8000-000000000002';
function Harness() {
  const mode=new URLSearchParams(location.search).get('role') || 'mentor';
  const [closed,setClosed]=useState(false),[other,setOther]=useState(false);
  const scope=other?'00000000-0000-4000-8000-000000000099':caseId,denied=()=>setClosed(true);
  if(mode==='root')return <EvaluationsPage me={me} grants={[{...me.memberships[0],permissions:['internship:enterprise:evaluate','t3:grade_visibility:manage','t3:workspace:read']}]}/>;
  return <main className="t3-evaluations stack" style={{padding:12}}><button onClick={()=>setOther(v=>!v)}>Đổi hồ sơ fixture</button>
    {closed?<p role="alert">Đã đóng dữ liệu fixture</p>:<div key={scope}>
      {mode==='mentor'?<MentorEvaluation caseId={scope} termId={termId} contextId="mentor-context" denied={denied}/>
        :mode==='student'?<StudentEvaluation caseId={scope} denied={denied}/>
        :mode==='school'?<SchoolEvaluations schoolId="school" termId={termId} canPublish canPolicy denied={denied}/>
        :<VisibilityEditor title="Policy fixture" path="/t3/organizations/org/grade-visibility" contextId="hr-context" validate={p=>p.termId===termId} denied={denied}/>}
    </div>}</main>;
}
createRoot(document.getElementById('root')!).render(<Harness/>);
