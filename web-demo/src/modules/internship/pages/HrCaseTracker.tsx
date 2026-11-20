import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import type { CaseRef } from '../cases';
import type { InternshipGroup, WorkspaceAssignment } from '../types';
import { uuidPattern, validVersion } from '../workflow';
import { workflowRead } from '../workflow-api';
import { CaseStatus } from './CaseStatus';

export function HrCaseTracker({ assignment, groups }: { assignment: WorkspaceAssignment; groups: InternshipGroup[] }) {
  const [caseId, setCaseId] = useState('');
  const choices = groups.map(group => ({ ...group, caseIds: group.caseIds.filter(id => uuidPattern.test(id)) }))
    .filter(group => group.caseIds.length > 0);
  const selected = choices.some(group => group.caseIds.includes(caseId)) ? caseId : '';

  return <section className="panel stack" aria-label="Theo dõi Case cho HR">
    <h3>Trạng thái hồ sơ thực tập</h3>
    <p>Danh sách gồm các hồ sơ đã được xếp vào nhóm trong đợt thực tập đang chọn.</p>
    {choices.length === 0 ? <p>Chưa có hồ sơ đã xếp nhóm trong đợt này.</p> : <label>Hồ sơ theo dõi<select
      value={selected} onChange={event => setCaseId(event.target.value)}>
      <option value="">Chọn hồ sơ…</option>
      {choices.map(group => <optgroup key={group.groupId} label={'Nhóm ' + group.code}>
        {group.caseIds.map(id => <option key={id} value={id}>Hồ sơ …{id.slice(-8)}</option>)}
      </optgroup>)}
    </select></label>}
    {selected && <CaseRead key={selected} caseId={selected} assignment={assignment} />}
  </section>;
}

function CaseRead({ caseId, assignment }: { caseId: string; assignment: WorkspaceAssignment }) {
  const [revision, setRevision] = useState(0);
  const [value, setValue] = useState<CaseRef | null>(null);
  const [error, setError] = useState('');
  const { schoolOrgId, termId, enterpriseOrgId } = assignment.scope;
  useEffect(() => {
    const controller = new AbortController();
    void workflowRead<CaseRef>('/t3/cases/' + caseId, assignment.id, controller.signal).then(result => {
      if (controller.signal.aborted) return;
      if (result?.caseId !== caseId || result.schoolOrgId !== schoolOrgId || result.termId !== termId
        || result.enterpriseOrgId !== enterpriseOrgId || !validVersion(result.version)) {
        setError('Không thể xác nhận hồ sơ thuộc phạm vi phân công HR.');
        return;
      }
      setValue(result);
    }).catch(error => {
      if (controller.signal.aborted) return;
      const denied = error instanceof ApiError && [401, 403, 404].includes(error.status);
      setError(denied ? 'Bạn không còn quyền xem hồ sơ này. Hãy kiểm tra lại phân công.'
        : 'Không tải được hồ sơ thực tập. Vui lòng thử lại.');
    });
    return () => controller.abort();
  }, [caseId, assignment.id, schoolOrgId, termId, enterpriseOrgId, revision]);

  const loading = !value && !error;
  return <div className="stack">
    {loading && <p role="status">Đang tải trạng thái hồ sơ…</p>}
    {error && <Notice message={error} tone="error" />}
    {value && <section className="panel stack" aria-label="Chi tiết Case">
      <CaseStatus value={value} />
      <details><summary>Mã tiếp nhận và phiên bản</summary>
        <p>Mã Placement <code>{value.placementId}</code></p>
        <p>Phiên bản {value.version}</p>
      </details>
    </section>}
    <button type="button" className="secondary" disabled={loading} onClick={() => {
      setValue(null); setError(''); setRevision(n => n + 1);
    }}>Tải lại trạng thái</button>
  </div>;
}
