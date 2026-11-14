// Browser contract fixture only. No production import or backend authorization claim.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { TaskBoard } from '../../src/modules/internship/pages/TaskBoard';
import { group, mentor, hr } from './internship';
import type { WorkspaceAssignment } from '../../src/modules/internship/types';
import '../../src/style.css';
function Harness() {
  const [role, setRole] = useState(new URLSearchParams(location.search).get('role') || 'student');
  const [other, setOther] = useState(false);
  return <main style={{ padding: 12 }}><label>Fixture role<select value={role} onChange={e => setRole(e.target.value)}>
    <option value="student">Student</option><option value="mentor">Mentor</option><option value="hr">HR</option></select></label>
    <button onClick={() => setOther(v => !v)}>Đổi phạm vi fixture</button>
    <button onClick={() => window.dispatchEvent(new Event('session-expired'))}>Thu hồi phiên fixture</button>
    <TaskBoard key={role} student={role === 'student'} assignment={role === 'student' ? undefined :
      { ...(role === 'mentor' ? mentor : hr), id: other ? '00000000-0000-4000-8000-000000000099' : (role === 'mentor' ? mentor.id : hr.id) } as WorkspaceAssignment}
      group={role === 'student' ? undefined : { ...group, groupId: other ? '00000000-0000-4000-8000-000000000098' : group.groupId }} />
  </main>;
}
createRoot(document.getElementById('root')!).render(<Harness />);
