import { useEffect, useId, useRef } from 'react';
import { createRoot } from 'react-dom/client';
import { Icon } from './components';
import './confirm.css';

export type ConfirmOptions = { title: string; message: string; confirmLabel: string; cancelLabel?: string; tone?: 'primary' | 'danger' };

// Hộp xác nhận dùng chung thay cho window.confirm (hộp của trình duyệt không tạo kiểu được): <dialog> modal nền mờ như hộp
// "Hỗ trợ truy cập", ô biểu tượng, tiêu đề, nội dung, nút "Quay lại" + nút xác nhận (đỏ khi xóa / rút / hủy). Esc hoặc bấm nền mờ
// = Quay lại (không dùng chữ "Hủy" để khỏi lẫn với thao tác "Hủy tiếp nhận"); con trỏ đặt sẵn ở nút Quay lại (nút đầu tiên) để Enter
// không vô tình xác nhận; đóng xong trả con trỏ về chỗ vừa bấm.
// Chỗ cần câu trả lời ngay trong sự kiện (chặn rời bản nhập chưa lưu) vẫn dùng window.confirm vì hộp này trả lời sau.
export function askConfirm(options: ConfirmOptions): Promise<boolean> {
  return new Promise(resolve => {
    const host = document.createElement('div'), previous = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    document.body.append(host);
    const root = createRoot(host);
    const finish = (value: boolean) => {
      previous?.focus({ preventScroll: true }); resolve(value);
      // Gỡ hộp sau khi sự kiện bấm xong, không tháo root ngay trong lúc nó đang xử lý sự kiện.
      setTimeout(() => { root.unmount(); host.remove(); });
    };
    root.render(<ConfirmDialog {...options} finish={finish} />);
  });
}

function ConfirmDialog({ title, message, confirmLabel, cancelLabel = 'Quay lại', tone = 'primary', finish }: ConfirmOptions & { finish: (value: boolean) => void }) {
  const dialog = useRef<HTMLDialogElement>(null), done = useRef(false), id = useId();
  useEffect(() => { if (dialog.current && !dialog.current.open) dialog.current.showModal(); }, []);
  const close = (value: boolean) => { if (done.current) return; done.current = true; dialog.current?.close(); finish(value); };
  const danger = tone === 'danger';
  return <dialog ref={dialog} className={'confirm-dialog' + (danger ? ' is-danger' : '')} aria-labelledby={id + '-title'} aria-describedby={id + '-message'}
    onCancel={event => { event.preventDefault(); close(false); }} onClick={event => { if (event.target === event.currentTarget) close(false); }}>
    <div className="confirm-dialog-body">
      <span className="confirm-dialog-icon" aria-hidden="true"><Icon name={danger ? 'alert' : 'info'} size={22} /></span>
      <h2 id={id + '-title'}>{title}</h2>
      <p id={id + '-message'}>{message}</p>
      <div className="confirm-dialog-actions">
        <button type="button" className="secondary" onClick={() => close(false)}>{cancelLabel}</button>
        <button type="button" className={danger ? 'confirm-dialog-danger' : undefined} onClick={() => close(true)}>{confirmLabel}</button>
      </div>
    </div>
  </dialog>;
}
