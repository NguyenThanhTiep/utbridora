import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { getOpportunity, listMyPreferences, newIdempotencyKey, sendPreference } from '../api';
import { dateTime, groupPreferences, isStale, preferenceError, preferenceLabels, type PreferenceGroup } from '../preferences';
import type { OpportunityView, PreferenceView, WithdrawPreference } from '../types';
import '../styles.css';

// S06 (tab nguyện vọng) và S06-A (BC22): T2 sở hữu Preference; rút chỉ khi SUBMITTED, REVIEWED giữ bất biến.
// 'missing' = T5 trả 404 (đã đóng hoặc ngoài phạm vi); 'unavailable' = chưa đọc được từ T5 (lỗi khác).
type OpportunityRef = OpportunityView | 'missing' | 'unavailable';

export function PreferencesPage() {
  const [preferences, setPreferences] = useState<PreferenceView[] | null>(null), [loadError, setLoadError] = useState('');
  const [attempt, setAttempt] = useState(0), [opportunities, setOpportunities] = useState<Record<string, OpportunityRef>>({});
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const pending = useRef(new Map<string, string>());
  useEffect(() => {
    const controller = new AbortController();
    setLoadError('');
    listMyPreferences(controller.signal)
      .then(items => { if (!controller.signal.aborted) setPreferences(items); })
      .catch(failure => { if (!controller.signal.aborted) setLoadError(preferenceError(failure)); });
    return () => controller.abort();
  }, [attempt]);
  // Tên cơ hội đọc từ T5; cơ hội đã đóng hoặc ngoài phạm vi thì không còn hiển thị.
  useEffect(() => {
    if (!preferences) return;
    let active = true;
    for (const id of new Set(preferences.map(item => item.opportunityId))) {
      if (id in opportunities) continue;
      getOpportunity(id).then(view => { if (active) setOpportunities(previous => ({ ...previous, [id]: view })); })
        .catch(failure => { if (active) setOpportunities(previous => ({ ...previous, [id]: failure instanceof ApiError && failure.status === 404 ? 'missing' : 'unavailable' })); });
    }
    return () => { active = false; };
  }, [preferences]);

  const withdraw = async (preference: PreferenceView) => {
    if (!window.confirm('Rút nguyện vọng này? Khi cơ hội còn hạn, bạn vẫn có thể gửi nguyện vọng mới.')) return;
    const body: WithdrawPreference = { action: 'WITHDRAW', preferenceId: preference.preferenceId, expectedVersion: preference.version, reasonCode: 'STUDENT_WITHDREW' };
    const ref = JSON.stringify(body);
    if (!pending.current.has(ref)) pending.current.set(ref, newIdempotencyKey());
    setBusy(true); setError(''); setNotice('');
    try {
      await sendPreference(body, pending.current.get(ref)!);
      pending.current.delete(ref);
      setNotice('Đã rút nguyện vọng.');
      setAttempt(previous => previous + 1);
    } catch (failure) {
      setError(preferenceError(failure));
      if (isStale(failure)) setAttempt(previous => previous + 1);
    } finally { setBusy(false); }
  };

  const groups = preferences ? groupPreferences(preferences) : [];
  return <div className="student-page">
    <Notice message={error} tone="error" /><Notice message={notice} tone="success" />
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">NGUYỆN VỌNG</p><h2>Nguyện vọng đã gửi</h2>
        <p className="muted">Nhà trường xét nguyện vọng; bạn rút được khi chưa xét. Nguyện vọng không tự tạo lời mời, chia sẻ hồ sơ hay Placement.</p></div>
        <button type="button" className="secondary" disabled={busy} onClick={() => { setError(''); setNotice(''); setAttempt(previous => previous + 1); }}>
          <Icon name="refresh" size={16} />Tải lại nguyện vọng</button></div>
      {loadError ? <EmptyState icon="alert" title="Chưa tải được nguyện vọng" description={loadError}>
        <button type="button" className="secondary" onClick={() => setAttempt(previous => previous + 1)}><Icon name="refresh" size={16} />Thử lại</button></EmptyState>
        : preferences === null ? <LoadingState label="Đang tải nguyện vọng…" />
        : groups.length === 0 ? <EmptyState icon="check-circle" title="Chưa có nguyện vọng" description="Mở Cơ hội thực tập để gửi nguyện vọng cho vị trí bạn muốn." />
        : <ul className="student-list">{groups.map(group => <PreferenceItem key={group.current.termId + ':' + group.current.opportunityId} group={group}
          opportunity={opportunities[group.current.opportunityId]} busy={busy} withdraw={withdraw} />)}</ul>}
    </section>
  </div>;
}

function PreferenceItem({ group, opportunity, busy, withdraw }: {
  group: PreferenceGroup; opportunity: OpportunityRef | undefined; busy: boolean; withdraw: (preference: PreferenceView) => void;
}) {
  const [open, setOpen] = useState(false);
  const { current, history } = group;
  const title = opportunity === undefined ? 'Đang tải tên cơ hội…' : opportunity === 'missing' ? 'Cơ hội đã đóng hoặc không còn hiển thị'
    : opportunity === 'unavailable' ? 'Chưa tải được thông tin cơ hội' : opportunity.title;
  return <li className="student-list-item">
    <h3>{title}</h3>
    {typeof opportunity === 'object' && <p className="student-list-meta">{opportunity.enterpriseName}</p>}
    <p className={'student-status ' + current.status.toLowerCase()}>{preferenceLabels[current.status]}</p>
    {current.message && <p className="student-list-meta">Lời nhắn: {current.message}</p>}
    {current.status === 'REVIEWED' && <p className="student-list-meta">Nguyện vọng đã được xét nên không thể rút. Muốn đổi ý, hãy gửi nguyện vọng mới từ Cơ hội thực tập.</p>}
    <div className="button-row">
      {current.status === 'SUBMITTED' && <button type="button" className="secondary" disabled={busy} onClick={() => withdraw(current)}>Rút nguyện vọng</button>}
      <button type="button" className="text-button" aria-expanded={open} onClick={() => setOpen(previous => !previous)}>{open ? 'Ẩn chi tiết' : 'Xem chi tiết'}</button>
    </div>
    {open && <dl className="student-detail">
      <div><dt>Lần gửi</dt><dd>{current.intentVersion}</dd></div>
      <div><dt>Gửi lúc</dt><dd>{dateTime(current.createdAt)}</dd></div>
      {current.withdrawnAt && <div><dt>Rút lúc</dt><dd>{dateTime(current.withdrawnAt)}</dd></div>}
      {current.reviewedAt && <div><dt>Nhà trường xét lúc</dt><dd>{dateTime(current.reviewedAt)}</dd></div>}
      {history.length > 0 && <div className="student-detail-wide"><dt>Các lần trước</dt><dd><ul>{history.map(item =>
        <li key={item.preferenceId + ':' + item.intentVersion + ':' + item.version}>Lần {item.intentVersion}: {preferenceLabels[item.status]} · {dateTime(item.createdAt)}</li>)}</ul></dd></div>}
    </dl>}
  </li>;
}
