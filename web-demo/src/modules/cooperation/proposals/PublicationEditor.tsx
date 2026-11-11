import { useEffect, useState } from 'react';
import { Notice } from '../../../components';
import { scopedRead, workflowError } from './api';
import type { Publication } from './types';
import { useWorkflowCommand } from './useWorkflowCommand';

function localDate(value: string | null) {
  if (!value) return '';
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}
export function PublicationEditor({ org, cooperationId }: { org: string; cooperationId: string }) {
  const [value, setValue] = useState<Publication | null>(null), [start, setStart] = useState(''), [end, setEnd] = useState('');
  const [revision, setRevision] = useState(0), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const path = `/school/cooperations/${cooperationId}/publication`;
  const apply = (data: Publication) => { setValue(data); setStart(localDate(data.publishedAt)); setEnd(localDate(data.closedAt)); };
  const command = useWorkflowCommand<Publication>(org, data => { apply(data); setNotice('Đã lưu thời gian công bố cơ hội.'); },
    () => { setValue(null); setRevision(n => n + 1); });
  useEffect(() => {
    const c = new AbortController(); setError('');
    void scopedRead<Publication>(org, path, c.signal).then(data => { if (!c.signal.aborted) apply(data); })
      .catch(reason => { if (!c.signal.aborted) setError(workflowError(reason)); });
    return () => c.abort();
  }, [org, path, revision]);
  return <section className="proposal-publication"><h4>Công bố cơ hội cho sinh viên</h4>
    <Notice message={error || command.message} tone="error" /><p role="status">{notice}</p>
    {!value && !error && <p>Đang tải thời gian công bố…</p>}
    {error && <button className="secondary" onClick={() => setRevision(n => n + 1)}>Tải lại thời gian công bố</button>}
    {value && <form onSubmit={e => {
      e.preventDefault(); setNotice('');
      if (!start || !end || Date.parse(start) >= Date.parse(end)) { setError('Thời điểm đóng phải sau thời điểm công bố.'); return; }
      setError(''); void command.submit(path, 'PUT', { expectedVersion: value.version, publishedAt: new Date(start).toISOString(), closedAt: new Date(end).toISOString() });
    }}><fieldset disabled={command.blocked}><div className="cooperation-form-grid">
      <label>Thời điểm công bố<input type="datetime-local" required value={start} onChange={e => setStart(e.target.value)} /></label>
      <label>Thời điểm đóng<input type="datetime-local" required value={end} onChange={e => setEnd(e.target.value)} /></label>
    </div><p>Giờ trên thiết bị của bạn. Sinh viên cùng trường, cùng kỳ xem được cơ hội trong khoảng thời gian này.</p>
      <button>Lưu thời gian công bố</button></fieldset></form>}
    {command.retryable && <button disabled={command.busy} onClick={() => void command.retry()}>Thử lại lần công bố đã gửi</button>}
  </section>;
}
