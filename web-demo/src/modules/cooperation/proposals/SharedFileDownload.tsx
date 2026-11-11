import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import { scopedRead, workflowError } from './api';
import { sharedFileBlob } from './sharedFiles';
import type { SharedFile, SharedProfile } from './types';

export type SharedFileContext = { org: string; proposalItemId: string; denied: (message: string) => void };
export function SharedFileDownload({ context, file, field, validUntil }: {
  context: SharedFileContext; file: SharedFile; field: 'cv' | 'evidence'; validUntil: string;
}) {
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const pending = useRef<AbortController | null>(null), mounted = useRef(true), denied = useRef(context.denied);
  denied.current = context.denied;
  useEffect(() => {mounted.current = true;return () => {mounted.current = false;pending.current?.abort();};}, []);
  const download = async () => {
    if (pending.current) return;
    const c = new AbortController();pending.current = c;setBusy(true);setError('');
    try {
      const current = await scopedRead<SharedProfile>(context.org, `/enterprise/proposals/${context.proposalItemId}/profile`, c.signal);
      const currentFile = field === 'cv' ? current.data.cv : current.data.evidence?.find(f => f.fileId === file.fileId);
      if (!current.allowedFields.includes(field) || !currentFile || currentFile.fileId !== file.fileId
          || !Number.isFinite(Date.parse(current.validUntil)) || Date.now() >= Date.parse(current.validUntil))
        throw new ApiError(403, 'Quyền tải tệp đã thay đổi hoặc hết hạn.', null, 'ACCESS_DENIED');
      const {blob, filename} = await sharedFileBlob(context.org, currentFile, c.signal);
      if (c.signal.aborted || !mounted.current || Date.now() >= Date.parse(current.validUntil) || Date.now() >= Date.parse(validUntil)) return;
      const url = URL.createObjectURL(blob), link = document.createElement('a');
      link.href = url;link.download = filename;document.body.append(link);link.click();link.remove();
      window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (reason) {
      if (!c.signal.aborted && mounted.current) {
        const message = workflowError(reason);setError(message);
        if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) denied.current(message);
      }
    } finally {if (pending.current === c) pending.current = null;if (mounted.current) setBusy(false);}
  };
  return <div><button type="button" className="secondary" disabled={busy || file.scanStatus !== 'CLEAN'} onClick={() => void download()}>
    {busy ? 'Đang tải tệp…' : field === 'cv' ? 'Tải CV' : 'Tải minh chứng'}
  </button><Notice message={error} tone="error" /></div>;
}
