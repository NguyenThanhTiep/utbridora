import { useEffect, useRef, useState } from 'react';
import { apiBlob } from '../../../api';
import { accessLost, contextHeaders, workflowError, workflowRead } from '../workflow-api';
import type { ReportFile } from '../workflow';

export function ReportEvidence({ reportId, fileIds, contextId, editable, disabled, changed, denied }: {
  reportId: string; fileIds: string[]; contextId?: string; editable: boolean; disabled: boolean;
  changed: (ids: string[]) => void; denied: () => void;
}) {
  const [files, setFiles] = useState<ReportFile[]>([]), [message, setMessage] = useState(''), [refresh, setRefresh] = useState(0);
  const [busy, setBusy] = useState(false), life = useRef<AbortController | null>(null);
  useEffect(() => { const c = new AbortController(); life.current = c; return () => c.abort(); }, []);
  const idsKey = fileIds.join(',');
  useEffect(() => {
    const c = new AbortController(); setMessage(''); setFiles([]);
    void Promise.all(fileIds.map(id => workflowRead<ReportFile>('/files/' + encodeURIComponent(id) + '/metadata', contextId, c.signal)))
      .then(values => {
        if (c.signal.aborted) return;
        if (values.some((v, i) => v.fileId !== fileIds[i] || v.resource.resourceId !== reportId
          || v.resource.resourceType !== 'WEEKLY_REPORT' || v.purpose !== 'WEEKLY_REPORT' || v.deletedAt)) { denied(); return; }
        setFiles(values);
      }).catch(error => { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setMessage(workflowError(error)); } });
    return () => c.abort();
  }, [idsKey, contextId, reportId, refresh]);
  const download = async (file: ReportFile) => {
    const signal = life.current?.signal; if (!signal || busy) return;
    setBusy(true); setMessage('');
    try {
      const blob = await apiBlob('/files/' + encodeURIComponent(file.fileId) + '/content',
        { headers: contextHeaders(contextId), signal: AbortSignal.any([signal, AbortSignal.timeout(30_000)]) });
      if (signal.aborted) return;
      const url = URL.createObjectURL(blob), anchor = document.createElement('a');
      anchor.href = url; anchor.download = file.originalName; document.body.append(anchor); anchor.click(); anchor.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
    } catch (error) { if (!signal.aborted) { setFiles([]); if (accessLost(error)) denied(); else setMessage(workflowError(error)); } }
    finally { if (!signal.aborted) setBusy(false); }
  };
  return <section aria-label="Minh chứng báo cáo" className="stack"><h5>Minh chứng đính kèm ({fileIds.length}/20)</h5>
    {message && <p role="alert">{message}</p>}
    {fileIds.length > 0 && <button type="button" className="secondary" disabled={busy || disabled} onClick={() => setRefresh(n => n + 1)}>Kiểm tra trạng thái minh chứng</button>}
    {fileIds.map(id => { const file = files.find(value => value.fileId === id); return <div key={id}>
      <p>{file ? file.originalName + ' · ' + ({ PENDING: 'Đang chờ quét', CLEAN: 'Đã kiểm tra', REJECTED: 'Bị từ chối' }[file.scanStatus]) : 'Tệp …' + id.slice(-6) + ' · Chưa xác nhận trạng thái'}</p>
      <button type="button" disabled={busy || !file || file.scanStatus !== 'CLEAN'} onClick={() => file && void download(file)}>Tải minh chứng …{id.slice(-6)}</button>
      {editable && <button type="button" className="secondary" disabled={disabled || busy} onClick={() => changed(fileIds.filter(value => value !== id))}>Gỡ minh chứng …{id.slice(-6)}</button>}
    </div>; })}
    {files.some(file => file.scanStatus !== 'CLEAN') && <p>Tệp cần được máy chủ xác nhận sạch trước khi nộp. Có thể lưu bản nháp khi đang chờ quét.</p>}
  </section>;
}
