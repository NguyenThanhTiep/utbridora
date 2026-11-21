import { useCallback, useEffect, useState } from 'react';
import { EmptyState, Icon, Notice } from '../../../components';
import { EnterpriseCandidateCard } from './EnterpriseCandidateCard';
import { ProposalStateFilter } from './ProposalStateFilter';
import './styles.css';
import './enterprise-candidates.css';
import { canLeaveDemand } from '../navigation';
import { useInfiniteCandidates } from './useInfiniteCandidates';

export function EnterpriseProposals({ org, cooperationId, focusProposalId }: { org: string; cooperationId: string; focusProposalId?: string }) {
  const [focusId, setFocusId] = useState(focusProposalId || '');
  const [revision, setRevision] = useState(0), [expandedId, setExpandedId] = useState<string | null>(null);
  const [notice, setNotice] = useState('');
  const [pending, setPending] = useState<Set<string>>(new Set());
  const [state, setState] = useState('');
  const markPending = useCallback((id: string, active: boolean) => setPending(old => {
    if (old.has(id) === active) return old;
    const next = new Set(old); if (active) next.add(id); else next.delete(id); return next;
  }), []);
  const { result, loading, loadingMore, error, moreError, hasMore, loadMore, sentinel } = useInfiniteCandidates(org, cooperationId, state, focusId, revision, pending.size > 0);
  const refresh = () => setRevision(value => value + 1);
  useEffect(() => {
    const revalidate = () => { if (!document.hidden && pending.size === 0 && canLeaveDemand()) setRevision(n => n + 1); };
    window.addEventListener('focus', revalidate);document.addEventListener('visibilitychange', revalidate);
    return () => {window.removeEventListener('focus', revalidate);document.removeEventListener('visibilitychange', revalidate);};
  }, [pending]);
  // A single candidate or notification target can open immediately; longer lists stay compact.
  const activeId = expandedId ?? (focusId || (result?.totalElements === 1 ? result.content[0]?.proposalItemId : ''));
  const toggle = (id: string) => {
    if (pending.size > 0 || !canLeaveDemand()) return;
    setExpandedId(activeId === id ? '' : id);
  };
  return <section className="proposal-workspace enterprise-proposals" aria-label="Ứng viên được đề xuất">
    <div className="placement-header-bar candidate-list-toolbar">
      <div className="workflow-section-heading">
        <h3>Ứng viên được đề xuất</h3>
        {result && result.totalElements > 0 && <span className="workflow-count-pill">{result.totalElements} ứng viên</span>}
      </div>
      <div className="candidate-list-controls">{!focusId &&
        <ProposalStateFilter school={false} value={state} disabled={loading || pending.size > 0} changed={value => {if (canLeaveDemand()) {setState(value);setExpandedId(null);}}} />}
        <button className="secondary" title="Tải lại ứng viên" disabled={loading || pending.size > 0} onClick={() => {if (canLeaveDemand()) refresh();}}><Icon name="refresh" size={16} /><span className="candidate-refresh-text">Tải lại ứng viên</span></button>
      </div>
    </div>
    <Notice message={error} tone="error" />{notice && <p role="status">{notice}</p>}
    {focusId && <button className="secondary" disabled={pending.size > 0} onClick={() => { if (canLeaveDemand()) { setFocusId('');setExpandedId(null); } }}>Xem tất cả ứng viên</button>}
    {loading && <p className="candidate-list-loading" role="status"><span className="spinner" />Đang tải ứng viên…</p>}
    {result?.totalElements === 0 && <EmptyState icon="users"
      title={focusId ? 'Hồ sơ này đã thay đổi hoặc không còn được chia sẻ.' : state ? 'Không có ứng viên được chia sẻ trong trạng thái đã chọn.' : 'Chưa có hồ sơ được chia sẻ hợp lệ.'}
      description={state ? 'Chọn trạng thái khác để xem lại danh sách.' : 'Hồ sơ sẽ xuất hiện sau khi sinh viên đồng ý và nhà trường chia sẻ cho doanh nghiệp.'} />}
    {result && result.totalElements > 0 && <p className="candidate-loaded-count" aria-live="polite" aria-atomic="true">Đã tải {result.content.length} / {result.totalElements} ứng viên</p>}
    <div className="candidate-card-list">{result?.content.map((item, n) => <EnterpriseCandidateCard key={item.proposalItemId + ':' + item.version + ':' + revision}
      org={org} item={item} number={n + 1} expanded={activeId === item.proposalItemId} onToggle={() => toggle(item.proposalItemId)}
      locked={pending.size > 0 && !pending.has(item.proposalItemId)} markPending={markPending} changed={value => { setNotice(value); refresh(); }} />)}</div>
    {hasMore && <div ref={sentinel} className="candidate-load-more" aria-busy={loadingMore}>
      {moreError && <Notice message={moreError} tone="error" />}
      {loadingMore ? <p role="status"><span className="spinner" />Đang tải thêm ứng viên…</p> : <>
        {!moreError && <p>Cuộn xuống để xem thêm ứng viên</p>}
        <button type="button" className="secondary" disabled={pending.size > 0} onClick={() => void loadMore(true)}>{moreError ? 'Thử tải thêm ứng viên' : 'Tải thêm ứng viên'}<Icon name="chevron-down" size={16} /></button>
      </>}
    </div>}
    {result && !hasMore && !focusId && result.content.length > 10 && <p className="candidate-list-end">Đã hiển thị tất cả {result.content.length} ứng viên.</p>}
  </section>;
}
