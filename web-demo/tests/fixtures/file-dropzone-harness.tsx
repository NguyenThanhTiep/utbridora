// Browser-only fixture, excluded from the production entry point.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { FileDropzone } from '../../src/ui/FileDropzone';
import '../../src/style.css';
import '../../src/modules/cooperation/styles.css';
import '../../src/modules/cooperation/portal/redesign-content.css';
import '../../src/modules/internship/redesign-t3-content.css';
import '../../src/modules/student/styles.css';

function Harness() {
  const [file, setFile] = useState<File | null>(null), [calls, setCalls] = useState(0), [locked, setLocked] = useState(false);
  const [state, setState] = useState<'selected' | 'uploading' | 'processing' | 'success' | 'error'>('selected');
  const workspace = new URLSearchParams(location.search).get('workspace');
  return <main className={workspace === 'student' ? 'student-page' : workspace === 'internship' ? 'workspace-native-t3' : 'workspace-t5 workspace-role-portal'} style={{ padding: 16 }}>
    <div className="portal-workspace is-enterprise"><section className="portal-page-stage">
      <fieldset disabled={locked} style={{ minWidth: 0 }}><legend>Minh chứng</legend>
        <FileDropzone label="Tệp thử nghiệm" hint="PDF hoặc CSV · Tối đa 5 MiB" maxBytes={5*1024*1024} accept=".pdf,.csv"
          file={file} state={state} onRetry={() => setState('uploading')} onChange={event => {
            setFile(event.currentTarget.files?.[0] || null); setCalls(n => n + 1); setState('selected');
          }} />
      </fieldset>
      <output aria-label="Số lần chọn">{calls}</output>
      <button onClick={() => setLocked(v => !v)}>Khóa chọn tệp</button>
      <button onClick={() => setFile(null)}>Xóa tệp đã chọn</button>
      <label>Trạng thái thử nghiệm<select value={state} onChange={e => setState(e.target.value as typeof state)}>
        {['selected', 'uploading', 'processing', 'success', 'error'].map(s => <option key={s}>{s}</option>)}
      </select></label>
    </section></div>
  </main>;
}
createRoot(document.getElementById('root')!).render(<Harness />);
