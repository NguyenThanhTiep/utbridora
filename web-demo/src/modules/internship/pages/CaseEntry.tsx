import { useEffect, useState } from 'react';
import { type Me, type Grant } from '../../../api';
import { Notice } from '../../../components';
import { InternshipWorkspace } from './InternshipWorkspace';
import { readCase, type CaseRef } from '../cases';

const states: Record<string, string> = { WAITING_START: 'Chờ bắt đầu', IN_PROGRESS: 'Đang thực tập', COMPLETED: 'Đã hoàn thành', CANCELLED: 'Đã hủy', TERMINATED: 'Đã dừng' };

/** Deep link remains a T3 read: the Placement link itself confers no case/task authority. */
export function CaseEntry({ caseId, me, grants }: { caseId: string; me: Me; grants: Grant[] }) {
  const [value, setValue] = useState<CaseRef | null>(null), [error, setError] = useState(''), [revision, setRevision] = useState(0);
  useEffect(() => {
    const c = new AbortController();setValue(null);setError('');
    void readCase(caseId, me, grants, c.signal).then(result => {
      if (c.signal.aborted) return;
      if (result.caseId !== caseId) {setError('Không thể xác nhận hồ sơ thực tập.');return;}
      setValue(result);
    }).catch(() => {if (!c.signal.aborted) setError('Không đọc được hồ sơ thực tập. Kiểm tra phân công và quyền truy cập hiện hành.');});
    return () => c.abort();
  }, [caseId, revision, me, grants]);
  return <div className="stack"><section className="panel"><h2>Hồ sơ thực tập</h2><Notice message={error} tone="error" />
    <button className="secondary" onClick={() => setRevision(n => n + 1)}>Tải lại hồ sơ thực tập</button>
    {!value && !error && <p role="status">Đang kiểm tra quyền truy cập hồ sơ…</p>}
    {value?.caseId === caseId && <><p>Mã hồ sơ: {value.caseId}</p><p>Trạng thái: {states[value.state] || value.state}</p>
      {value.state === 'WAITING_START' && <p>Chưa được giao hoặc nộp nhiệm vụ trước khi tiếp nhận thực tập được kích hoạt.</p>}</>}
    </section>{value?.caseId === caseId && me.memberships.some(m => m.role === 'ENTERPRISE')
      && <InternshipWorkspace me={me} grants={grants} initialCase={value} />}</div>;
}
