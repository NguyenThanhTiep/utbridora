import { useEffect, useMemo, useState } from 'react';
import { ApiError, type Grant } from '../../../api';
import { Notice } from '../../../components';
import { workspacePermission } from '../access';
import type { CaseRef } from '../cases';
import { uuidPattern, validVersion } from '../workflow';
import { workflowRead } from '../workflow-api';
import { CaseStatus } from './CaseStatus';
import { ScopeOperations } from './OperationsPage';
import type { OperationsScope } from '../operations-types';

type Lookup = { caseId: string };

export function SchoolCaseLookup({ grants,userId }: { grants: Grant[];userId:string }) {
  const schoolIds = [...new Set(grants.flatMap(grant =>
    grant.role === 'SCHOOL_STAFF' && grant.organizationId && grant.permissions.includes(workspacePermission)
      ? [grant.organizationId] : []))];
  const [selectedSchool, setSelectedSchool] = useState('');
  const schoolId = schoolIds.includes(selectedSchool) ? selectedSchool : schoolIds[0];
  if (!schoolId) return null;

  return <div className="stack t3-evaluations">
    {schoolIds.length > 1 && <section className="panel"><label>Trường tra cứu<select value={schoolId}
      onChange={event => setSelectedSchool(event.target.value)}>
      {schoolIds.map(id => <option key={id} value={id}>Trường …{id.slice(-6)}</option>)}
    </select></label></section>}
    <ScopedLookup key={schoolId} schoolId={schoolId} userId={userId} permissions={grants.find(g=>g.role==='SCHOOL_STAFF' && g.organizationId===schoolId && g.permissions.includes(workspacePermission))!.permissions} />
  </div>;
}

function ScopedLookup({ schoolId,userId,permissions }: { schoolId: string;userId:string;permissions:string[] }) {
  const [input, setInput] = useState('');
  const [request, setRequest] = useState<Lookup | null>(null);
  const [response, setResponse] = useState<{ request: Lookup; value?: CaseRef; message?: string } | null>(null);
  const result = request && response?.request === request ? response : null;
  const caseId = input.trim().toLowerCase();
  const loading = !!request && !result;
  const [operations,setOperations]=useState<Lookup|null>(null);
  const termId=result?.value?.termId;
  const scope=useMemo<OperationsScope|null>(()=>termId ? {mode:'school',schoolId,termId,userId,permissions} : null,[schoolId,termId,userId,permissions]);

  useEffect(() => {
    if (!request) return;
    const controller = new AbortController();
    void workflowRead<CaseRef>('/t3/cases/' + request.caseId, undefined, controller.signal).then(value => {
      if (controller.signal.aborted) return;
      if (value?.caseId !== request.caseId || value.schoolOrgId !== schoolId || !validVersion(value.version)) {
        setResponse({ request, message: 'Không thể xác nhận hồ sơ thuộc trường đang tra cứu.' });
        return;
      }
      setResponse({ request, value });
    }).catch(error => {
      if (controller.signal.aborted) return;
      const denied = error instanceof ApiError && [401, 403, 404].includes(error.status);
      setResponse({ request, message: denied
        ? 'Không tìm thấy hồ sơ trong phạm vi trường và kỳ bạn được ủy quyền.'
        : 'Không tải được hồ sơ thực tập. Vui lòng thử lại.' });
    });
    return () => controller.abort();
  }, [request, schoolId]);

  return <section className="panel stack" aria-label="Tra cứu hồ sơ nhà trường">
    <form className="stack" onSubmit={event => {
      event.preventDefault();
      if (!loading && uuidPattern.test(caseId)) { setResponse(null); setRequest({ caseId }); }
    }}>
      <label>Mã hồ sơ thực tập<input value={input} onChange={event => {
        setInput(event.target.value); setRequest(null); setResponse(null);
      }} aria-describedby="school-case-help" /></label>
      <small id="school-case-help">Dùng mã hồ sơ được cấp. Máy chủ kiểm tra ủy quyền theo trường và kỳ.</small>
      <button disabled={!uuidPattern.test(caseId) || loading}>{request ? 'Tải lại hồ sơ' : 'Tra cứu hồ sơ'}</button>
    </form>
    {loading && <p role="status">Đang kiểm tra quyền truy cập hồ sơ…</p>}
    {result?.message && <Notice message={result.message} tone="error" />}
    {result?.value && <section className="stack" aria-label="Thông tin hồ sơ">
      {operations!==request && <><h2>Trạng thái hồ sơ thực tập</h2>
      <CaseStatus value={result.value} /></>}
      {scope && (operations===request ? <ScopeOperations scope={scope} caseId={result.value.caseId} denied={()=>setResponse({request:result.request,message:'Quyền truy cập hồ sơ đã thay đổi.'})}/> :
        <button onClick={()=>setOperations(request)}>Mở quản lý hồ sơ và kỳ thực tập</button>)}
      {operations!==request && <details><summary>Mã vị trí và phạm vi</summary>
        <p>Mã tiếp nhận thực tập <code>{result.value.placementId}</code></p>
        <p>Mã trường <code>{result.value.schoolOrgId}</code></p>
        <p>Mã kỳ <code>{result.value.termId}</code></p>
        <p>Phiên bản {result.value.version}</p>
      </details>}
    </section>}
  </section>;
}
