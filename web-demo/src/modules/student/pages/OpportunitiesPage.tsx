import { useEffect, useId, useRef, useState, type FormEvent } from 'react';
import type { Grant } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice, Pagination } from '../../../components';
import { getOpportunity, listMajors, listMyPreferences, listOpportunities, newIdempotencyKey, sendPreference, type AcademicCatalogItem } from '../api';
import { date, dateTime, groupPreferences, isStale, preferenceError, preferenceLabels, useStudentTerm } from '../preferences';
import type { ContractPage, OpportunityView, PreferenceView, SubmitPreference } from '../types';
import '../styles.css';

// S05 và S05-D (BC22): T5 sở hữu và công bố cơ hội; T2 chỉ đọc qua API, không tự dựng dữ liệu cơ hội.
type Filter = { q: string; majorCode: string; page: number };
const majorNames = (codes: string[], majors: AcademicCatalogItem[]) =>
  codes.map(code => majors.find(major => major.code === code)?.name ?? code).join(', ') || 'Không giới hạn ngành';

export function OpportunitiesPage({ schools, openPreferences }: { schools: Grant[]; openPreferences: () => void }) {
  const { terms, failed, termId, setTermId, retry } = useStudentTerm(schools);
  const [majors, setMajors] = useState<AcademicCatalogItem[]>([]);
  const [draft, setDraft] = useState({ q: '', majorCode: '' });
  const [filter, setFilter] = useState<Filter>({ q: '', majorCode: '', page: 0 });
  const [result, setResult] = useState<ContractPage<OpportunityView> | null>(null);
  const [error, setError] = useState(''), [attempt, setAttempt] = useState(0);
  const [selected, setSelected] = useState<string | null>(null);
  const searchId = useId(), majorId = useId(), termSelectId = useId();

  useEffect(() => {
    if (!termId) return;
    let active = true;
    setMajors([]);
    // Bộ lọc ngành là tùy chọn: không tải được danh mục thì vẫn xem được danh sách cơ hội.
    listMajors(termId).then(items => { if (active) setMajors(items); }).catch(() => undefined);
    return () => { active = false; };
  }, [termId]);
  useEffect(() => {
    if (!termId) return;
    const controller = new AbortController();
    setResult(null); setError('');
    listOpportunities({ termId, ...filter }, controller.signal)
      .then(page => { if (!controller.signal.aborted) setResult(page); })
      .catch(failure => { if (!controller.signal.aborted) setError(preferenceError(failure)); });
    return () => controller.abort();
  }, [termId, filter, attempt]);

  if (selected) return <OpportunityDetail opportunityId={selected} majors={majors} back={() => setSelected(null)} openPreferences={openPreferences} />;
  const search = (event: FormEvent) => { event.preventDefault(); setFilter({ q: draft.q.trim(), majorCode: draft.majorCode, page: 0 }); };
  const content = terms === null && !failed ? <LoadingState label="Đang tải học kỳ…" />
    : failed || terms === null ? <EmptyState icon="calendar" title="Chưa xác định học kỳ" description="Chưa tải được danh sách học kỳ của trường.">
      <button type="button" className="secondary" onClick={retry}><Icon name="refresh" size={16} />Thử lại</button></EmptyState>
    : terms.length === 0 ? <EmptyState icon="calendar" title="Chưa xác định học kỳ" description="Trường chưa có học kỳ đang mở nên chưa có cơ hội để hiển thị." />
    : !termId ? <EmptyState icon="calendar" title="Chọn học kỳ" description="Trường có nhiều học kỳ đang mở. Chọn học kỳ để xem cơ hội tương ứng." />
    : <>
      <form className="student-filter" onSubmit={search}>
        <div className="field"><label htmlFor={searchId}>Vị trí hoặc doanh nghiệp</label>
          <input id={searchId} maxLength={150} value={draft.q} placeholder="Ví dụ: Java, Doanh nghiệp A" onChange={event => setDraft({ ...draft, q: event.target.value })} /></div>
        <div className="field"><label htmlFor={majorId}>Ngành</label>
          <select id={majorId} value={draft.majorCode} onChange={event => setDraft({ ...draft, majorCode: event.target.value })}>
            <option value="">Tất cả ngành</option>{majors.map(major => <option key={major.code} value={major.code}>{major.name}</option>)}
          </select></div>
        <button type="submit" className="secondary"><Icon name="search" size={16} />Tìm cơ hội</button>
      </form>
      {error ? <EmptyState icon="alert" title="Chưa tải được cơ hội thực tập" description={error}>
        <button type="button" className="secondary" onClick={() => setAttempt(previous => previous + 1)}><Icon name="refresh" size={16} />Thử lại</button></EmptyState>
        : result === null ? <LoadingState label="Đang tải cơ hội…" />
        : result.content.length === 0 ? <EmptyState icon="briefcase" title="Chưa có cơ hội phù hợp" description="Chỉ cơ hội đã được nhà trường công bố và còn hạn mới hiển thị." />
        : <>
          <ul className="student-list">{result.content.map(opportunity => <li className="student-list-item" key={opportunity.opportunityId}>
            <h3>{opportunity.title}</h3>
            <p className="student-list-meta">{opportunity.enterpriseName} · {majorNames(opportunity.majorCodes, majors)}</p>
            <p className="student-list-meta">Thực tập {date(opportunity.dateRange.startDate)} – {date(opportunity.dateRange.endDate)} · Nhận nguyện vọng đến {dateTime(opportunity.closedAt)}</p>
            <div className="button-row"><button type="button" className="secondary" onClick={() => setSelected(opportunity.opportunityId)}>Xem chi tiết</button></div>
          </li>)}</ul>
          <Pagination page={{ content: [], number: result.page, totalElements: result.totalElements, totalPages: result.totalPages }}
            change={page => setFilter(previous => ({ ...previous, page }))} busy={false} />
        </>}
    </>;
  return <div className="student-page">
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">CƠ HỘI THỰC TẬP</p><h2>Cơ hội đang mở</h2>
        <p className="muted">Cơ hội do nhà trường công bố từ các hợp tác đã duyệt. Gửi nguyện vọng không tự tạo lời mời hay Placement.</p></div>
        {terms && terms.length > 1 && <div className="field student-term-select"><label htmlFor={termSelectId}>Học kỳ</label>
          <select id={termSelectId} value={termId} onChange={event => { setTermId(event.target.value); setFilter(previous => ({ ...previous, page: 0 })); }}>
            <option value="">Chọn học kỳ</option>{terms.map(term => <option key={term.termId} value={term.termId}>{term.termCode}</option>)}
          </select></div>}
      </div>
      {content}
    </section>
  </div>;
}

function OpportunityDetail({ opportunityId, majors, back, openPreferences }: {
  opportunityId: string; majors: AcademicCatalogItem[]; back: () => void; openPreferences: () => void;
}) {
  const [opportunity, setOpportunity] = useState<OpportunityView | null>(null), [loadError, setLoadError] = useState('');
  const [preferences, setPreferences] = useState<PreferenceView[] | null>(null), [preferencesFailed, setPreferencesFailed] = useState(false);
  const [attempt, setAttempt] = useState(0), [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [notice, setNotice] = useState('');
  const pending = useRef(new Map<string, string>());
  const messageId = useId();
  useEffect(() => {
    const controller = new AbortController();
    setLoadError('');
    getOpportunity(opportunityId, controller.signal)
      .then(view => { if (!controller.signal.aborted) setOpportunity(view); })
      .catch(failure => { if (!controller.signal.aborted) setLoadError(preferenceError(failure)); });
    listMyPreferences(controller.signal)
      .then(items => { if (!controller.signal.aborted) { setPreferences(items); setPreferencesFailed(false); } })
      .catch(() => { if (!controller.signal.aborted) setPreferencesFailed(true); });
    return () => controller.abort();
  }, [opportunityId, attempt]);

  const current = opportunity && preferences
    ? groupPreferences(preferences.filter(item => item.opportunityId === opportunityId && item.termId === opportunity.termId))[0]?.current : undefined;
  const closed = !!opportunity && Date.now() >= Date.parse(opportunity.closedAt);
  const length = [...message.trim()].length;
  // Lần đầu expectedIntentVersion=0; các lần sau bằng intentVersion hiện hành (BC22).
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!opportunity || preferences === null || closed || current?.status === 'SUBMITTED' || length > 1000) return;
    const body: SubmitPreference = { action: 'SUBMIT', opportunityId: opportunity.opportunityId, message: message.trim() || null, expectedIntentVersion: current?.intentVersion ?? 0 };
    const ref = JSON.stringify(body);
    if (!pending.current.has(ref)) pending.current.set(ref, newIdempotencyKey());
    setBusy(true); setError(''); setNotice('');
    try {
      await sendPreference(body, pending.current.get(ref)!);
      pending.current.delete(ref); setMessage('');
      setNotice('Đã gửi nguyện vọng. Nhà trường sẽ xét; gửi nguyện vọng không tự tạo lời mời.');
      setAttempt(previous => previous + 1);
    } catch (failure) {
      setError(preferenceError(failure));
      if (isStale(failure)) setAttempt(previous => previous + 1);
    } finally { setBusy(false); }
  };

  return <div className="student-page">
    <div><button type="button" className="text-button" onClick={back}><Icon name="arrow-left" size={16} />Quay lại danh sách</button></div>
    <Notice message={notice} tone="success" />
    {loadError ? <section className="panel"><EmptyState icon="alert" title="Chưa tải được cơ hội" description={loadError}>
      <button type="button" className="secondary" onClick={() => setAttempt(previous => previous + 1)}><Icon name="refresh" size={16} />Thử lại</button></EmptyState></section>
      : !opportunity ? <section className="panel"><LoadingState label="Đang tải cơ hội…" /></section>
      : <>
        <section className="panel">
          <div className="section-heading"><div><p className="section-kicker">{opportunity.enterpriseName}</p><h2>{opportunity.title}</h2></div></div>
          <dl className="student-detail">
            <div><dt>Ngành phù hợp</dt><dd>{majorNames(opportunity.majorCodes, majors)}</dd></div>
            <div><dt>Thời gian thực tập</dt><dd>{date(opportunity.dateRange.startDate)} – {date(opportunity.dateRange.endDate)}</dd></div>
            <div><dt>Địa điểm</dt><dd>{opportunity.location ?? 'Chưa cập nhật'}</dd></div>
            <div><dt>Quyền lợi</dt><dd>{opportunity.benefits ?? 'Chưa cập nhật'}</dd></div>
            <div><dt>Công bố lúc</dt><dd>{dateTime(opportunity.publishedAt)}</dd></div>
            <div><dt>Nhận nguyện vọng đến</dt><dd>{dateTime(opportunity.closedAt)}</dd></div>
          </dl>
        </section>
        <section className="panel">
          <div className="section-heading"><div><p className="section-kicker">NGUYỆN VỌNG</p><h2>Đăng ký vị trí này</h2></div></div>
          <Notice message={error} tone="error" />
          {preferencesFailed ? <p className="student-error-note">Chưa tải được nguyện vọng của bạn nên chưa thể gửi.{' '}
            <button type="button" className="text-button" onClick={() => setAttempt(previous => previous + 1)}>Thử lại</button></p>
            : preferences === null ? <LoadingState label="Đang tải nguyện vọng…" />
            : <>
              {current && <p className="student-list-meta">Nguyện vọng gần nhất (lần {current.intentVersion}): {preferenceLabels[current.status]} · gửi lúc {dateTime(current.createdAt)}</p>}
              {current?.status === 'SUBMITTED' ? <div className="button-row"><button type="button" className="secondary" onClick={openPreferences}>Xem hoặc rút nguyện vọng</button></div>
                : closed ? <p className="student-list-meta">Cơ hội đã hết hạn nhận nguyện vọng.</p>
                : <form onSubmit={submit}>
                  <div className="field"><label htmlFor={messageId}>Lời nhắn cho nhà trường (không bắt buộc)</label>
                    <textarea id={messageId} rows={4} value={message} disabled={busy} onChange={event => setMessage(event.target.value)} /></div>
                  {length > 1000 && <p className="student-field-error">Lời nhắn tối đa 1000 ký tự.</p>}
                  <p className="student-list-meta">{length}/1000 ký tự. Gửi nguyện vọng không tự tạo lời mời, chia sẻ hồ sơ hay Placement.</p>
                  <div className="button-row"><button type="submit" disabled={busy || length > 1000}>
                    {busy ? <span className="spinner spinner-light" /> : <Icon name="check" size={16} />}Gửi nguyện vọng</button></div>
                </form>}
            </>}
        </section>
      </>}
  </div>;
}
