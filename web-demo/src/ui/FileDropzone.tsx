// Lucian UX/UI Prompt #032 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#032
import { useId, useRef, useState, type ChangeEvent, type InputHTMLAttributes } from 'react';
import './file-dropzone.css';

type UploadState = 'selected' | 'uploading' | 'processing' | 'success' | 'error';
type Props = Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'children' | 'multiple' | 'onChange'> & {
  label: string;
  hint: string;
  maxBytes: number;
  validateFile?: (file: File) => string;
  file?: File | null;
  state?: UploadState;
  statusText?: string;
  /** Only provide a percentage when the transport reports actual byte progress. */
  progress?: number;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
  onRetry?: () => void;
  retryLabel?: string;
  retryDisabled?: boolean;
};

const states: Record<UploadState, string> = {
  selected: 'Đã chọn, chưa tải lên', uploading: 'Đang tải lên…', processing: 'Đang kiểm tra…',
  success: 'Đã hoàn tất', error: 'Chưa hoàn tất tải tệp',
};
const size = (bytes: number) => bytes >= 1024 * 1024
  ? `${(bytes / (1024 * 1024)).toLocaleString('vi-VN', { maximumFractionDigits: 1 })} MiB`
  : `${Math.max(1, Math.ceil(bytes / 1024))} KiB`;

function accepts(file: File, accept: string) {
  return !accept || accept.split(',').some(value => {
    const rule = value.trim().toLowerCase(), mime = file.type.toLowerCase();
    return rule.startsWith('.') ? file.name.toLowerCase().endsWith(rule)
      : rule.endsWith('/*') ? mime.startsWith(rule.slice(0, -1)) : mime === rule;
  });
}

/** A single-file picker; upload, retry keys and scan approval remain owned by the caller. */
export function FileDropzone({ label, hint, maxBytes, validateFile, file, state = 'selected', statusText, progress,
  onChange, onRetry, retryLabel = 'Thử lại', retryDisabled = false, id, disabled, accept = '',
  className = '', ...inputProps }: Props) {
  const generated = useId(), inputId = id || generated;
  const input = useRef<HTMLInputElement>(null), depth = useRef(0);
  const [over, setOver] = useState(false), [error, setError] = useState('');
  const [picked, setPicked] = useState<File | null>(null);
  const selected = file === undefined ? picked : file;
  const isDisabled = () => !input.current || input.current.matches(':disabled');
  const percent = progress === undefined || !Number.isFinite(progress) ? undefined : Math.min(100, Math.max(0, progress));
  const working = state === 'uploading' || state === 'processing';
  const changed = (event: ChangeEvent<HTMLInputElement>) => {
    if (isDisabled()) return;
    const files = Array.from(event.currentTarget.files || []);
    if (!files.length) return;
    const next = files[0];
    const problem = files.length > 1 ? 'Vui lòng chọn mỗi lần một tệp.'
      : validateFile?.(next) || (!accepts(next, accept) ? `Định dạng tệp không được hỗ trợ. ${hint}`
      : next.size === 0 ? 'Tệp rỗng. Vui lòng chọn tệp có nội dung.'
      : next.size > maxBytes ? `Tệp vượt quá giới hạn ${size(maxBytes)}.` : '');
    setError(problem);
    if (problem) { event.currentTarget.value = ''; return; }
    setPicked(next);
    onChange(event);
  };
  return <div className={'u032' + (over ? ' is-over' : '') + (disabled ? ' is-disabled' : '')}>
    <label className="u032-label" htmlFor={inputId}>{label}</label>
    <button className="u032-zone" type="button" disabled={disabled} aria-label="Mở hộp chọn tệp"
      aria-controls={inputId} aria-describedby={inputId + '-hint'}
      onClick={() => { if (!isDisabled()) { input.current!.value = ''; input.current!.click(); } }}
      onDragEnter={event => { event.preventDefault(); if (!isDisabled() && event.dataTransfer.types.includes('Files')) { depth.current++; setOver(true); } }}
      onDragOver={event => { event.preventDefault(); event.dataTransfer.dropEffect = isDisabled() ? 'none' : 'copy'; }}
      onDragLeave={() => { if (--depth.current <= 0) { depth.current = 0; setOver(false); } }}
      onDrop={event => {
        event.preventDefault(); depth.current = 0; setOver(false);
        if (isDisabled() || !event.dataTransfer.files.length) return;
        if (event.dataTransfer.files.length > 1) { setError('Vui lòng chọn mỗi lần một tệp.'); return; }
        // Dispatch the same native change event as the picker, preserving each form's handler.
        input.current!.files = event.dataTransfer.files;
        input.current!.dispatchEvent(new Event('change', { bubbles: true }));
      }}>
      <svg className="u032-dash" aria-hidden="true"><rect width="100%" height="100%" rx="25" /></svg>
      <span className="u032-orb" aria-hidden="true"><svg viewBox="0 0 28 28"><path d="M8.5 20.5H7a5 5 0 0 1-.6-10 7 7 0 0 1 13.4-1.6A5.5 5.5 0 0 1 21 20.5h-1.5M14 13v10M10.5 16.5L14 13l3.5 3.5" /></svg></span>
      <b>Kéo thả tệp vào đây hoặc <em>chọn tệp</em></b>
      <small id={inputId + '-hint'}>{hint}</small>
    </button>
    <input {...inputProps} id={inputId} ref={input} type="file" accept={accept} disabled={disabled}
      className={'u032-file ' + className} tabIndex={-1} aria-label={inputProps['aria-label'] || label}
      aria-invalid={!!error || inputProps['aria-invalid']}
      aria-describedby={[inputProps['aria-describedby'], inputId + '-hint', error && inputId + '-error'].filter(Boolean).join(' ')} onChange={changed} />
    {error && <p className="u032-error" id={inputId + '-error'} role="alert">{error}</p>}
    {selected && <ul className="u032-list" aria-live="polite"><li className={'u032-row ' + (state === 'success' ? 'done' : state === 'error' ? 'fail' : '')} aria-busy={working}>
      <span className="u032-ic" aria-hidden="true">{selected.name.split('.').pop()?.slice(0, 4).toUpperCase() || 'FILE'}</span>
      <span className="u032-meta"><b title={selected.name}>{selected.name}</b><small>{size(selected.size)} · {statusText || states[state]}</small></span>
      {state === 'error' && onRetry ? <button className="u032-retry" type="button" disabled={retryDisabled} onClick={onRetry}>{retryLabel}</button>
        : <span className="u032-st" aria-hidden="true">{working ? <span className="u032-spin" />
          : state === 'success' ? <svg viewBox="0 0 16 16"><path d="M3.5 8.4l3 3 6-6.4" /></svg> : state === 'error' ? '!' : '↑'}</span>}
      {state !== 'selected' && <span className={'u032-bar' + (working && percent === undefined ? ' indeterminate' : '')}
        role={working ? 'progressbar' : undefined} aria-hidden={working ? undefined : true}
        aria-label={working ? 'Tiến trình: ' + selected.name : undefined} aria-valuemin={working ? 0 : undefined}
        aria-valuemax={working ? 100 : undefined} aria-valuenow={working ? percent : undefined}
        aria-valuetext={working ? statusText || states[state] : undefined}
        style={working && percent === undefined ? undefined : { transform: `scaleX(${state === 'success' ? 1 : (percent ?? 0) / 100})` }} />}
    </li></ul>}
  </div>;
}
