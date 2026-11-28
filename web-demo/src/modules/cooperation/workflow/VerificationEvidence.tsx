import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import { Icon } from '../../../components';
import { FileDropzone } from '../../../ui/FileDropzone';
import { newIdempotencyKey } from '../../student/api';
import type { VerificationInput } from './types';
import { downloadVerificationFile, uploadVerificationFile, validateVerificationFile, verificationFileError,
  verificationFileMetadata, verificationFileProblem, type VerificationFile } from './verificationFiles';
import './verification-evidence.css';

export type QueuedEvidenceFile = {
  id: string;
  file: File;
  key: string;
};

export type VerificationEvidenceSeed = {
  pending?: VerificationFile[];
  failed?: { file: File; key: string; message: string; retryable: boolean } | null;
  notice?: string;
};

const formatFileSize = (bytes: number) =>
  bytes >= 1024 * 1024
    ? `${(bytes / (1024 * 1024)).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} MiB`
    : `${Math.max(1, Math.ceil(bytes / 1024))} KiB`;

export function VerificationEvidence({
  org,
  recordId,
  data,
  disabled,
  update,
  onBusyChange,
  queuedFiles = [],
  onQueueAdd,
  onQueueRemove,
  seed,
}: {
  org: string;
  recordId?: string;
  data: VerificationInput;
  disabled: boolean;
  update: (apply: (current: VerificationInput) => VerificationInput) => void;
  onBusyChange: (busy: boolean) => void;
  queuedFiles?: QueuedEvidenceFile[];
  onQueueAdd?: (file: File) => void;
  onQueueRemove?: (index: number) => void;
  seed?: VerificationEvidenceSeed;
}) {
  const [files, setFiles] = useState<VerificationFile[]>([]);
  const [pendingList, setPendingList] = useState<VerificationFile[]>(() => seed?.pending ?? []);
  const [sending, setSending] = useState(false);
  const [retryable, setRetryable] = useState(() => Boolean(seed?.failed?.retryable));
  const [downloading, setDownloading] = useState(false);
  const [message, setMessage] = useState(() => seed?.notice || seed?.failed?.message || '');
  const [metadataError, setMetadataError] = useState('');
  const [uploadFile, setUploadFile] = useState<File | null>(() => seed?.failed?.file ?? null);
  const [uploadFailed, setUploadFailed] = useState(() => Boolean(seed?.failed));
  const attempt = useRef<{ file: File; key: string } | null>(
    seed?.failed ? { file: seed.failed.file, key: seed.failed.key } : null,
  );
  const active = useRef(false);
  const life = useRef<AbortController | null>(null);
  const handlers = useRef({ update, onBusyChange }); handlers.current = { update, onBusyChange };
  const idsKey = data.evidenceFileIds.join(',');
  const pendingIdsKey = pendingList.map(item => `${item.fileId}:${item.scanStatus}`).join(',');
  const hasPendingScan = pendingList.some(item => item.scanStatus === 'PENDING');
  const hasRejectedScan = pendingList.some(item => item.scanStatus === 'REJECTED');
  const firstPendingOrRejected = pendingList.find(item => item.scanStatus === 'PENDING')
    ?? pendingList.find(item => item.scanStatus === 'REJECTED')
    ?? null;
  const blocked = sending || retryable || hasPendingScan;
  useEffect(() => { const controller = new AbortController(); life.current = controller;
    return () => { controller.abort(); handlers.current.onBusyChange(false); };
  }, [org, recordId]);
  useEffect(() => { handlers.current.onBusyChange(blocked); }, [blocked]);
  useEffect(() => {
    const controller = new AbortController(); setFiles([]); setMetadataError('');
    if (recordId && idsKey) void Promise.all(idsKey.split(',').map(id => verificationFileMetadata(org, id, controller.signal)))
      .then(values => { if (!controller.signal.aborted) setFiles(values.map(file => validateVerificationFile(file, recordId))); })
      .catch(error => { if (!controller.signal.aborted) { setFiles([]); setMetadataError(verificationFileError(error)); } });
    return () => controller.abort();
  }, [org, recordId, idsKey]);
  useEffect(() => {
    const waiting = pendingList.filter(item => item.scanStatus === 'PENDING');
    if (!recordId || !waiting.length) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    const check = async () => {
      try {
        const checked = await Promise.all(
          waiting.map(async item => {
            const next = validateVerificationFile(await verificationFileMetadata(org, item.fileId, controller.signal), recordId);
            if (next.fileId !== item.fileId) throw new Error('Unexpected file');
            return next;
          }),
        );
        if (controller.signal.aborted) return;
        const cleanIds = checked.filter(item => item.scanStatus === 'CLEAN').map(item => item.fileId);
        if (cleanIds.length) {
          handlers.current.update(current => ({ ...current, evidenceFileIds: [...new Set([...current.evidenceFileIds, ...cleanIds])] }));
        }
        setPendingList(current =>
          current
            .map(item => checked.find(next => next.fileId === item.fileId) ?? item)
            .filter(item => item.scanStatus !== 'CLEAN'),
        );
        if (checked.some(item => item.scanStatus === 'REJECTED')) {
          setMessage('Tài liệu bị từ chối sau kiểm tra an toàn. Vui lòng chọn tài liệu khác.');
          if (checked.every(item => item.scanStatus !== 'PENDING')) return;
        } else if (cleanIds.length && checked.every(item => item.scanStatus === 'CLEAN')) {
          setMessage('Tài liệu đã được xác nhận an toàn. Bấm Lưu hồ sơ xác minh để lưu minh chứng.');
          return;
        }
      } catch (error) {
        if (controller.signal.aborted) return;
        setMessage(verificationFileError(error));
        if (error instanceof ApiError && [401, 403, 404].includes(error.status)) {
          setPendingList(current => current.filter(item => item.scanStatus !== 'PENDING'));
          setUploadFailed(true);
          return;
        }
      }
      timer = setTimeout(() => void check(), 3000);
    };
    void check();
    return () => { controller.abort(); clearTimeout(timer); };
  }, [org, recordId, pendingIdsKey]);

  async function upload(file?: File) {
    if (!recordId) {
      if (!file || disabled || blocked) return;
      if (queuedFiles.length >= 20) {
        setMessage('Tối đa 20 tài liệu minh chứng cho mỗi hồ sơ.');
        return;
      }
      const problem = verificationFileProblem(file);
      if (problem) {
        setMessage(problem);
        return;
      }
      setMessage('');
      onQueueAdd?.(file);
      return;
    }
    const signal = life.current?.signal;
    if (!signal || signal.aborted || active.current || disabled) return;
    if (file) {
      if (blocked || data.evidenceFileIds.length >= 20) return;
      const problem = verificationFileProblem(file); if (problem) { setMessage(problem); return; }
      attempt.current = { file, key: newIdempotencyKey() };
      setPendingList(current => current.filter(item => item.scanStatus === 'PENDING'));
      setUploadFile(file);
    }
    const request = attempt.current; if (!request) return;
    active.current = true; setSending(true); setUploadFailed(false); setMessage(''); handlers.current.onBusyChange(true);
    try {
      const result = validateVerificationFile(await uploadVerificationFile(org, recordId, request.file, request.key, signal), recordId);
      if (signal.aborted) return;
      attempt.current = null; setRetryable(false);
      if (result.scanStatus === 'CLEAN') {
        handlers.current.update(current => ({ ...current, evidenceFileIds: [...new Set([...current.evidenceFileIds, result.fileId])] }));
        setMessage('Tài liệu đã được xác nhận an toàn. Bấm Lưu hồ sơ xác minh để lưu minh chứng.');
      } else {
        setPendingList(current => [...current.filter(item => item.fileId !== result.fileId && item.scanStatus === 'PENDING'), result]);
      }
    } catch (error) {
      if (signal.aborted) return;
      const definitive = error instanceof ApiError && [400, 401, 403, 404, 409, 413, 422].includes(error.status) && error.errorCode !== 'REQUEST_IN_PROGRESS';
      if (definitive) attempt.current = null;
      setUploadFailed(true);
      setRetryable(!definitive); setMessage(verificationFileError(error));
    } finally { active.current = false; if (!signal.aborted) setSending(false); }
  }
  async function download(file: VerificationFile) {
    const signal = life.current?.signal; if (!signal || downloading) return;
    setDownloading(true); setMessage('');
    try { await downloadVerificationFile(org, file, signal); }
    catch (error) { if (!signal.aborted) setMessage(verificationFileError(error)); }
    finally { if (!signal.aborted) setDownloading(false); }
  }
  const latestQueuedFile = !recordId && queuedFiles.length ? queuedFiles[queuedFiles.length - 1].file : null;
  const dropzoneState = !recordId
    ? 'selected'
    : sending
      ? 'uploading'
      : uploadFailed || hasRejectedScan
        ? 'error'
        : hasPendingScan
          ? 'processing'
          : 'success';
  const dropzoneStatusText = !recordId
    ? 'Đã chọn · Sẽ tự tải lên khi bấm Lưu hồ sơ xác minh'
    : firstPendingOrRejected?.scanStatus === 'PENDING'
      ? 'Đang chờ kiểm tra an toàn'
      : uploadFailed
        ? 'Tải tệp chưa thành công'
        : firstPendingOrRejected?.scanStatus === 'REJECTED'
          ? 'Bị từ chối sau kiểm tra an toàn'
          : sending
            ? 'Đang tải tài liệu…'
            : 'Đã xác nhận an toàn';

  return <section className="portal-evidence verification-evidence" aria-label="Tài liệu minh chứng xác minh">
    <div className="portal-evidence-heading"><Icon name="layers" /><h4>Tài liệu minh chứng</h4></div>
    {!recordId ? <>
      <p className="verification-evidence-note">Chọn sẵn tài liệu minh chứng bên dưới (PDF, PNG, JPEG; tối đa 10 MiB mỗi tệp và 20 tệp) để tự tải lên khi tạo hồ sơ, hoặc Lưu hồ sơ trước, sau đó chọn Bổ sung hồ sơ để tải tài liệu minh chứng.</p>
      <FileDropzone
        label="Chọn tài liệu minh chứng"
        aria-label="Chọn tài liệu minh chứng"
        hint="PDF, PNG hoặc JPEG · Tối đa 10 MiB"
        maxBytes={10 * 1024 * 1024}
        validateFile={verificationFileProblem}
        accept="application/pdf,image/png,image/jpeg"
        disabled={disabled || queuedFiles.length >= 20}
        file={latestQueuedFile}
        state={dropzoneState}
        statusText={dropzoneStatusText}
        onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file) void upload(file); }}
      />
      {message && <p role="status">{message}</p>}
      {queuedFiles.length > 0 && <ul className="verification-evidence-list">
        {queuedFiles.map((item, index) => (
          <li key={item.id}>
            <div>
              <strong>Tài liệu chờ tải {index + 1} · {item.file.name}</strong>
              <small>{formatFileSize(item.file.size)} · Sẽ tự tải lên khi bấm Lưu hồ sơ xác minh</small>
            </div>
            <div className="button-row">
              <button
                type="button"
                className="secondary"
                disabled={disabled}
                onClick={() => onQueueRemove?.(index)}
              >Gỡ tệp {index + 1}</button>
            </div>
          </li>
        ))}
      </ul>}
    </> : <>
      <p className="verification-evidence-note">Đính kèm giấy đăng ký doanh nghiệp hoặc tài liệu xác minh. Nhận PDF, PNG, JPEG; tối đa 10 MiB mỗi tệp và 20 tệp. Tài liệu cần được xác nhận an toàn trước khi gửi.</p>
      <FileDropzone label="Chọn tài liệu minh chứng" aria-label="Chọn tài liệu minh chứng"
        hint="PDF, PNG hoặc JPEG · Tối đa 10 MiB" maxBytes={10 * 1024 * 1024} validateFile={verificationFileProblem}
        accept="application/pdf,image/png,image/jpeg" disabled={disabled || blocked || data.evidenceFileIds.length >= 20}
        file={uploadFile} state={dropzoneState}
        statusText={dropzoneStatusText}
        onRetry={retryable ? () => void upload() : undefined} retryLabel="Thử lại tải tài liệu" retryDisabled={sending || disabled}
        onChange={event => { const file = event.target.files?.[0]; event.target.value = ''; if (file) void upload(file); }} />
      {sending && <p role="status">Đang tải tài liệu…</p>}
      {hasPendingScan && <p role="status" className="verification-scan-note">Đang chờ kiểm tra an toàn tài liệu. Kết quả sẽ tự cập nhật; chưa thể lưu minh chứng khi đang chờ quét.</p>}
      {hasRejectedScan && <p role="alert">Tài liệu chưa đạt kiểm tra an toàn, chưa được gắn vào hồ sơ.</p>}
      {message && <p role="status">{message}</p>}
      {metadataError && <p role="alert">{metadataError}</p>}
      {data.evidenceFileIds.length > 0 && <ul className="verification-evidence-list">{data.evidenceFileIds.map((id, index) => {
        const file = files.find(item => item.fileId === id);
        return <li key={id}><div><strong>Minh chứng {index + 1}</strong><small>{file ? ({ CLEAN: 'Đã xác nhận an toàn', PENDING: 'Đang chờ quét', REJECTED: 'Bị từ chối' }[file.scanStatus]) : 'Đang kiểm tra trạng thái'}</small></div>
          <div className="button-row"><button type="button" className="secondary" disabled={disabled || downloading || file?.scanStatus !== 'CLEAN'} onClick={() => file && void download(file)}>Tải minh chứng {index + 1}</button>
            <button type="button" className="secondary" disabled={disabled || blocked} onClick={() => update(current => ({ ...current, evidenceFileIds: current.evidenceFileIds.filter(value => value !== id) }))}>Gỡ minh chứng {index + 1}</button></div>
        </li>;
      })}</ul>}
    </>}
  </section>;
}
