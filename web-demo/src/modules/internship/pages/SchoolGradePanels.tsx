import { useEffect, useRef, useState } from 'react';
import { gradeAccessLost, type Evaluation, type VisibilityPolicy } from '../evaluation';
import { workflowError, workflowPages, workflowRead } from '../workflow-api';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { textWithin, validVersion, uuidPattern } from '../workflow';
import { EvaluationView } from './EvaluationView';
import './school-grades.css';

const stateNames = { DRAFT: 'Bản nháp', SUBMITTED: 'Chờ công bố', PUBLISHED: 'Đã công bố' };

function HrVisibilityConditions({ policy }: { policy: VisibilityPolicy }) {
  const inherited = policy.scope === 'CASE' && policy.version === 0;
  const hrStatus = inherited ? 'Kế thừa doanh nghiệp/kỳ' : policy.enabled ? 'Bật' : 'Tắt';
  return <section aria-label="Ba điều kiện hiển thị điểm">
    <h4>Kiểm tra 3 điều kiện hiển thị</h4>
    <dl className="t3-visibility-conditions">
      <div><dt>Đánh giá đã công bố</dt><dd>Nhà trường công bố đánh giá của từng hồ sơ.</dd><dd className="t3-grade-status">Chưa xác nhận</dd></div>
      <div><dt>Chính sách nhà trường</dt><dd>Nhà trường cho phép xem điểm trong kỳ thực tập.</dd><dd className="t3-grade-status">Chưa xác nhận</dd></div>
      <div><dt>Quyền hiển thị của HR</dt><dd>{policy.scope === 'CASE' ? 'Áp dụng cho hồ sơ đã chọn.' : 'Áp dụng cho các hồ sơ trong kỳ chưa có chính sách riêng.'}</dd>
        <dd className={'t3-grade-status' + (inherited ? '' : policy.enabled ? ' state-PUBLISHED' : ' state-SUBMITTED')}>{hrStatus}</dd></div>
    </dl>
  </section>;
}

/** Mounted by scope: drafts survive refresh, but never another school/term/assignment. */
export function VisibilityEditor(props: {
  path: string; contextId?: string; validate: (p: VisibilityPolicy) => boolean; denied: () => void; title: string;
}) {
  return <ScopedVisibilityEditor key={props.path + ':' + (props.contextId || '')} {...props} />;
}
function ScopedVisibilityEditor({ path, contextId, validate, denied, title }: Parameters<typeof VisibilityEditor>[0]) {
  const [policy, setPolicy] = useState<VisibilityPolicy | null>(null), [enabled, setEnabled] = useState(false);
  const [error, setError] = useState(''), [refresh, setRefresh] = useState(0), [saved, setSaved] = useState(false);
  const dirty = useRef(false);
  const command = useWorkflowCommand(contextId, denied, gradeAccessLost);
  useEffect(() => {
    const c = new AbortController(); setPolicy(null); setError('');
    void workflowRead<VisibilityPolicy>(path, contextId, c.signal).then(p => {
      if (c.signal.aborted) return;
      if (!validate(p) || typeof p.enabled !== 'boolean' || !Number.isSafeInteger(p.version) || p.version < 0) { denied(); return; }
      setPolicy(p); if (!dirty.current) setEnabled(p.enabled);
    }).catch(e => { if (!c.signal.aborted) { if (gradeAccessLost(e)) denied(); else setError(workflowError(e)); } });
    return () => c.abort();
  }, [path, contextId, refresh]);
  return <section className="panel stack t3-school-grades" aria-label={title}><h3>{title}</h3>
    <p>Sinh viên chỉ xem được kết quả khi đã công bố, nhà trường cho phép và chính sách HR có hiệu lực cho phép.</p>
    <button type="button" className="secondary" disabled={command.busy || command.pending} onClick={() => { command.setMessage(''); setRefresh(n => n + 1); }}>Tải lại chính sách</button>
    {error && <p role="alert">{error}</p>}{!policy && !error && <p role="status">Đang xác nhận quyền và chính sách…</p>}
    {saved && <p role="status">Đã lưu quyền xem điểm.</p>}
    {contextId && policy && <HrVisibilityConditions policy={policy} />}
    {policy && <form onSubmit={e => {
      e.preventDefault(); command.run<VisibilityPolicy>(path, { expectedVersion: policy.version, enabled }, p => {
        if (!validate(p) || typeof p.enabled !== 'boolean' || !validVersion(p.version)) { denied(); return; }
        dirty.current = false; setSaved(true); setRefresh(n => n + 1);
      }, 'PUT');
    }}><fieldset disabled={command.busy || command.pending}>
      <legend>Quyền hiển thị kết quả</legend>
      {policy.scope !== 'CASE' || policy.version > 0
        ? <p>Trạng thái đã lưu: {policy.enabled ? 'Bật' : 'Tắt'} · Version {policy.version}</p>
        : <p>Hồ sơ đang dùng chính sách doanh nghiệp/kỳ.</p>}
      {policy.scope === 'CASE' && policy.version === 0 && <p>Chưa có chính sách riêng cho hồ sơ. Lưu “Tắt” sẽ tạo ngoại lệ chặn xem điểm, kể cả khi doanh nghiệp/kỳ đang bật.</p>}
      {policy.scope === 'CASE' && policy.version > 0 && <p>Chính sách riêng này ưu tiên hơn doanh nghiệp/kỳ, kể cả khi tắt.</p>}
      <label><input type="checkbox" checked={enabled} onChange={e => { dirty.current = true; setSaved(false); setEnabled(e.target.checked); }} />Cho phép sinh viên xem điểm</label>
      {dirty.current && <p>Lựa chọn chưa lưu được giữ khi tải lại. Kiểm tra trạng thái đã lưu trước khi gửi lại.</p>}
      <button disabled={policy.version > 0 && enabled === policy.enabled}>Lưu quyền xem điểm</button>
    </fieldset></form>}<CommandFeedback command={command} />
  </section>;
}

export function SchoolEvaluations(props: {
  schoolId: string; termId: string; denied: () => void; canPublish: boolean; canPolicy: boolean;
}) {
  return <SchoolGradeScope key={props.schoolId + ':' + props.termId} {...props} />;
}
function SchoolGradeScope({ schoolId, termId, denied, canPublish, canPolicy }: Parameters<typeof SchoolEvaluations>[0]) {
  const [rows, setRows] = useState<Evaluation[]>([]), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [refresh, setRefresh] = useState(0), [selected, setSelected] = useState(''), [state, setState] = useState('');
  const [caseInput, setCaseInput] = useState(''), [caseFilter, setCaseFilter] = useState('');
  const [notice, setNotice] = useState(''), [locked, setLocked] = useState(false);
  const listPath = '/t3/school/evaluations?termId=' + termId;
  useEffect(() => {
    if (!canPublish) return;
    const c = new AbortController(); setRows([]); setLoading(true); setError('');
    void workflowPages<Evaluation>(listPath + (state ? '&state=' + state : '') + (caseFilter ? '&caseId=' + caseFilter : ''), undefined, c.signal)
      .then(v => { if (!c.signal.aborted) setRows(v); })
      .catch(e => { if (!c.signal.aborted) { if (gradeAccessLost(e)) denied(); else setError(workflowError(e)); } })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [listPath, state, caseFilter, refresh, canPublish]);
  const value = rows.find(v => v.evaluationId === selected);
  return <div className="stack t3-school-grades">
    {canPolicy && <VisibilityEditor title="Chính sách nhà trường" path={'/t3/schools/' + schoolId + '/terms/' + termId + '/result-visibility'}
      validate={p => p.schoolOrgId === schoolId && p.termId === termId} denied={denied} />}
    {canPublish && <section className="panel stack" aria-label="Danh sách đánh giá nhà trường">
      <div><p className="section-kicker">NHÀ TRƯỜNG · KẾT QUẢ THỰC TẬP</p><h2>Công bố đánh giá</h2>
        <p>Xem đánh giá Mentor đã nộp, công bố kết quả hoặc yêu cầu chỉnh sửa bằng revision.</p></div>
      <div className="t3-grade-toolbar">
        <label>Lọc trạng thái đánh giá<select disabled={locked} value={state} onChange={e => { setState(e.target.value); setSelected(''); }}>
          <option value="">Tất cả</option>{Object.entries(stateNames).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
        <form onSubmit={e => { e.preventDefault(); setCaseFilter(caseInput.trim().toLowerCase()); setSelected(''); }}>
          <label>Lọc theo mã hồ sơ<input value={caseInput} disabled={locked} placeholder="Nhập mã hồ sơ được cấp" onChange={e => setCaseInput(e.target.value)} /></label>
          <button className="secondary" disabled={locked || !!caseInput.trim() && !uuidPattern.test(caseInput.trim())}>Áp dụng bộ lọc</button>
        </form>
        <button type="button" className="secondary" disabled={locked || loading} onClick={() => setRefresh(n => n + 1)}>Tải danh sách đánh giá</button>
      </div>
      {notice && <p role="status">{notice}</p>}
      {loading && <p role="status">Đang tải danh sách…</p>}{error && <p role="alert">{error}</p>}
      {!loading && !error && <>
        <p>{rows.length} đánh giá trong bộ lọc hiện tại. Số lượng này không phải tổng số hồ sơ thực tập.</p>
        {rows.length === 0 && <p>Chưa có đánh giá trong phạm vi này. Thử chọn trạng thái khác hoặc bỏ bộ lọc hồ sơ.</p>}
        <div className="t3-grade-list">{rows.map(v => <button type="button" className={'secondary t3-grade-row' + (selected === v.evaluationId ? ' selected' : '')}
          aria-pressed={selected === v.evaluationId} disabled={locked} key={v.evaluationId} onClick={() => { setSelected(v.evaluationId); setNotice(''); }}>
          <span>Hồ sơ …{v.caseId.slice(-6)} · {v.state} · Version {v.version}</span>
          <span className={'t3-grade-status state-' + v.state}>{stateNames[v.state]}</span>
          <small>Revision {v.revisionNo} · Điểm {v.total100.toFixed(2)} / 100</small>
        </button>)}</div>
      </>}
      {!selected && rows.length > 0 && <p>Chọn một hồ sơ để xem chi tiết và xử lý.</p>}
      {selected && <SchoolDecision key={selected} evaluationId={selected} value={value} loading={loading} error={error}
        denied={denied} locked={setLocked} reload={() => setRefresh(n => n + 1)} done={message => {
          setNotice(message); setSelected(''); setRefresh(n => n + 1);
        }} />}
    </section>}
  </div>;
}

function SchoolDecision({ evaluationId, value, loading, error, denied, locked, reload, done }: {
  evaluationId: string; value?: Evaluation; loading: boolean; error: string; denied: () => void;
  locked: (v: boolean) => void; reload: () => void; done: (message: string) => void;
}) {
  const [decision, setDecision] = useState(''), [reason, setReason] = useState('');
  const command = useWorkflowCommand(undefined, denied, gradeAccessLost);
  useEffect(() => { locked(command.busy || command.pending); return () => locked(false); }, [command.busy, command.pending, locked]);
  const blocked = loading || !!error || !value || !validVersion(value.version) || command.busy || command.pending;
  const execute = (revision: boolean) => {
    if (blocked || !value) return;
    command.run<Evaluation>('/t3/evaluations/' + evaluationId + (revision ? '/revisions' : '/publish'),
      { expectedVersion: value.version, decisionRef: decision.trim(), ...(revision ? { reasonCode: reason } : {}) }, result => {
        if (result.evaluationId !== evaluationId || result.caseId !== value.caseId) { denied(); return; }
        done(revision ? 'Đã tạo revision nháp. Mentor cần chỉnh sửa và nộp lại trước khi công bố.' : 'Đã công bố điểm. Sinh viên xem được khi cả nhà trường và HR cho phép.');
      });
  };
  return <section className="stack t3-grade-detail" aria-label="Quyết định công bố">
    <h3>Chi tiết và quyết định</h3>
    <button type="button" className="secondary" disabled={command.busy || command.pending || loading} onClick={() => { command.setMessage(''); reload(); }}>Tải lại đánh giá đang chọn</button>
    <p>Căn cứ và lý do đang nhập được giữ khi tải lại phiên bản trong cùng hồ sơ. Đổi hồ sơ hoặc kỳ sẽ xóa nội dung chưa gửi.</p>
    {!loading && !error && !value && <p>Đánh giá không còn trong bộ lọc hiện tại. Hãy kiểm tra lại danh sách.</p>}
    {value && !loading && !error && <><details><summary>Mã hồ sơ đầy đủ</summary><code>{value.caseId}</code></details>
      <EvaluationView value={value} denied={denied} />
      {value.state === 'DRAFT' && <p>Đang chờ Mentor hoàn thiện và nộp đánh giá. Chỉ bản đã nộp mới được công bố.</p>}
    </>}
    {(!value || ['SUBMITTED', 'PUBLISHED'].includes(value.state)) && <fieldset disabled={blocked}>
      <legend>Căn cứ xử lý đánh giá</legend>
      <label>Căn cứ quyết định<input value={decision} onChange={e => setDecision(e.target.value)} aria-describedby="t3-decision-help" /></label>
      <small id="t3-decision-help">Nhập số hoặc mã quyết định, từ 1 đến 120 ký tự.</small>
      {value?.state === 'SUBMITTED' && <button type="button" disabled={!textWithin(decision, 1, 120)} onClick={() => execute(false)}>Công bố điểm</button>}
      <label>Mã lý do tạo revision<input value={reason} placeholder="Ví dụ: CORRECTION" onChange={e => setReason(e.target.value)} aria-describedby="t3-reason-help" /></label>
      <small id="t3-reason-help">Từ 3 đến 64 ký tự: chữ hoa A–Z, số hoặc dấu gạch dưới.</small>
      <button type="button" className="secondary" disabled={!textWithin(decision, 1, 120) || !/^[A-Z0-9_]{3,64}$/.test(reason)} onClick={() => execute(true)}>Tạo revision đánh giá</button>
      <p>Revision mới giữ kết quả đã công bố trước đó cho đến khi trường công bố lại. Quyền xem vẫn phụ thuộc chính sách School và HR.</p>
    </fieldset>}<CommandFeedback command={command} />
  </section>;
}
