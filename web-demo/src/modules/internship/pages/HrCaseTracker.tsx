import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import type { CaseRef } from '../cases';
import type { InternshipGroup, WorkspaceAssignment } from '../types';
import { formatEntityShort, formatGroupCode } from '../displayLabels';
import { uuidPattern, validVersion } from '../workflow';
import { workflowRead } from '../workflow-api';
import { CaseStatus } from './CaseStatus';

export function HrCaseTracker({ assignment, groups }: { assignment: WorkspaceAssignment; groups: InternshipGroup[] }) {
  const [caseId, setCaseId] = useState('');
  const [groupId, setGroupId] = useState('');
  const choices = groups.map(group => ({ ...group, caseIds: group.caseIds.filter(id => uuidPattern.test(id)) }))
    .filter(group => group.caseIds.length > 0 && (!groupId || group.groupId === groupId));
  const selected = choices.some(group => group.caseIds.includes(caseId)) ? caseId : '';

  return <section className="panel stack t3-case-tracker" aria-label="Theo dõi Case cho HR">
    <h3>Trạng thái hồ sơ thực tập</h3>
    <p>Danh sách gồm các hồ sơ đã được xếp vào nhóm trong đợt thực tập đang chọn.</p>
    <div className="t3-filter-pair">{groups.length > 1 && <label>Nhóm thực tập<select value={groupId} onChange={event => {
      setGroupId(event.target.value); setCaseId('');
    }}><option value="">Tất cả nhóm</option>{groups.map(group =>
      <option key={group.groupId} value={group.groupId}>{formatGroupCode(group.code)}</option>)}</select></label>}
    {choices.length === 0 ? <p>{groupId ? 'Nhóm đang chọn chưa có hồ sơ.' : 'Chưa có hồ sơ đã xếp nhóm trong đợt này.'}</p> : <label>Hồ sơ theo dõi<select
      value={selected} onChange={event => setCaseId(event.target.value)}>
      <option value="">Chọn hồ sơ…</option>
      {choices.map(group => <optgroup key={group.groupId} label={'Nhóm ' + formatGroupCode(group.code)}>
        {group.caseIds.map(id => <option key={id} value={id}>Hồ sơ {formatEntityShort(id)}</option>)}
      </optgroup>)}
    </select></label>}</div>
    {choices.length > 0 && <div className="t3-table-scroll"><table><thead><tr><th>Hồ sơ thực tập</th><th>Nhóm phụ trách</th><th>Thao tác</th></tr></thead>
      <tbody>{choices.flatMap(group=>group.caseIds.map(id=><tr key={group.groupId + id}><th scope="row" title={id}><span className="t3-code-pill">Hồ sơ {formatEntityShort(id)}</span></th>
        <td title={group.code}>{formatGroupCode(group.code)}</td><td><button className="secondary" aria-pressed={selected===id} onClick={()=>setCaseId(id)}>Xem hồ sơ</button></td></tr>))}</tbody></table></div>}
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
      <a className="t3-case-link" href={'/workspace/cases/' + encodeURIComponent(value.caseId)}>Mở hồ sơ thực tập</a>
    </section>}
    <button type="button" className="secondary" disabled={loading} onClick={() => {
      setValue(null); setError(''); setRevision(n => n + 1);
    }}>Tải lại trạng thái</button>
  </div>;
}
