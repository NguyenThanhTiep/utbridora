// Browser fixture only; no backend or production route is introduced.
import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowDetailDialog } from '../../src/modules/cooperation/ui/WorkflowDetailDialog';
import '../../src/style.css';

function Harness() {
  const [open, setOpen] = useState(false), [nested, setNested] = useState(false), [blocked, setBlocked] = useState(false);
  return <main style={{ padding: '550px 16px 1800px' }}>
    <button onClick={() => setOpen(true)}>Xem chi tiết</button>
    {open && <WorkflowDetailDialog label="Chi tiết hồ sơ thử nghiệm" blocked={blocked} onClose={() => setOpen(false)}>
      <header><h2>Chi tiết hồ sơ thử nghiệm</h2><button onClick={() => setOpen(false)} disabled={blocked}>Đóng hồ sơ</button></header>
      <button onClick={() => setBlocked(value => !value)}>Đổi trạng thái đang xử lý</button>
      <button onClick={() => setNested(true)}>Mở thao tác bên trong</button>
      {Array.from({ length: 40 }, (_, i) => <p key={i} style={{ padding: 12, overflowWrap: 'anywhere' }}>Thông tin hồ sơ {i + 1}: {'Nội dung dài để kiểm tra vùng cuộn chi tiết. '.repeat(4)}</p>)}
      <button>Thao tác cuối hồ sơ</button>
      {nested && <WorkflowDetailDialog compact label="Thao tác hồ sơ" onClose={() => setNested(false)}>
        <h2>Thao tác hồ sơ</h2><button onClick={() => setNested(false)}>Đóng thao tác</button>
      </WorkflowDetailDialog>}
    </WorkflowDetailDialog>}
  </main>;
}
createRoot(document.getElementById('root')!).render(<Harness />);
