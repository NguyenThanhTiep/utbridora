import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { ApiError, type Grant } from '../../../api';
import { Icon, Notice } from '../../../components';
import { listTerms, listMajors, listOpportunities, listMyPreferences, newIdempotencyKey, submitPreference, withdrawPreference,
  type AcademicCatalogItem, type Opportunity, type StudentPreference } from '../api';
import type { TermReference } from '../types';
import '../styles.css';

type LoadState = 'loading' | 'ready' | 'error';
const codeLabels: Record<string, string> = {
  SE: 'Công nghệ phần mềm', AI: 'Trí tuệ nhân tạo', CS: 'Khoa học máy tính', IT: 'Công nghệ thông tin'
};
const statusLabels: Record<StudentPreference['status'], string> = {
  SUBMITTED: 'Đã gửi nhà trường', WITHDRAWN: 'Đã rút', REVIEWED: 'Đã được nhà trường xét'
};

function termLabel(term: TermReference) {
  const termOne = /(?:^|[-_])(?:HK|SEMESTER|WEEK)[-_]?1(?:$|[-_])/i.test(term.termCode);
  return `${termOne ? 'Học kỳ 1 · ' : ''}${term.termCode} · ${term.schoolOrgId.slice(0, 8)}`;
}

export function OpportunityPreferencesPage({ schools, openInvitations }: { schools: Grant[]; openInvitations: () => void }) {
  const [terms, setTerms] = useState<TermReference[]>([]);
  const [termId, setTermId] = useState('');
  const [termState, setTermState] = useState<LoadState>('loading');
  const [termRetry, setTermRetry] = useState(0);
  const [majorCatalog, setMajorCatalog] = useState<AcademicCatalogItem[]>([]);
  const [majorState, setMajorState] = useState<LoadState>('loading');
  const [majorRetry, setMajorRetry] = useState(0);
  const [opportunities, setOpportunities] = useState<Opportunity[]>([]);
  const [preferences, setPreferences] = useState<StudentPreference[]>([]);
  const [loadState, setLoadState] = useState<LoadState>('loading');
  const [query, setQuery] = useState('');
  const [searchText, setSearchText] = useState('');
  const [filterText, setFilterText] = useState('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [selectedId, setSelectedId] = useState('');
  const [draftMessage, setDraftMessage] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const pending = useRef(new Map<string, string>());
  const schoolIds = useMemo(() => schools.flatMap(school => school.organizationId ? [school.organizationId] : []).join(','), [schools]);

  useEffect(() => {
    let active = true;
    setTerms([]); setTermId(''); setTermState('loading');
    void Promise.all(schoolIds.split(',').filter(Boolean).map(listTerms)).then(pages => {
      if (!active) return;
      const activeTerms = pages.flat().filter(term => term.status === 'ACTIVE')
        .filter((term, index, all) => all.findIndex(other => other.termId === term.termId) === index)
        .sort((a, b) => a.startDate.localeCompare(b.startDate) || a.termCode.localeCompare(b.termCode));
      setTerms(activeTerms);
      setTermId(activeTerms.length === 1 ? activeTerms[0].termId : '');
      setTermState('ready');
    }).catch(() => { if (active) setTermState('error'); });
    return () => { active = false; };
  }, [schoolIds, termRetry]);

  useEffect(() => {
    let active = true;
    setMajorCatalog([]);
    if (!termId) { setMajorState('ready'); return () => { active = false; }; }
    setMajorState('loading');
    void listMajors(termId).then(items => {
      if (active) { setMajorCatalog(items); setMajorState('ready'); }
    }, () => {
      if (active) { setMajorCatalog([]); setMajorState('error'); }
    });
    return () => { active = false; };
  }, [termId, majorRetry]);

  useEffect(() => {
    let active = true;
    if (!termId) { setOpportunities([]); setPreferences([]); setTotalPages(0); setLoadState('ready'); return; }
    setLoadState('loading'); setNotice('');
    void Promise.all([
      listOpportunities({ termId, q: query.trim() || undefined, majorCode: filterText || undefined, page, size: 25 }),
      listMyPreferences(termId)
    ]).then(([opportunityPage, preferenceRows]) => {
      if (!active) return;
      setOpportunities(opportunityPage.content);
      setPreferences(preferenceRows);
      setTotalPages(opportunityPage.totalPages);
      setLoadState('ready');
      setSelectedId(current => opportunityPage.content.some(item => item.opportunityId === current)
        ? current : opportunityPage.content[0]?.opportunityId || '');
    }).catch((failure: unknown) => {
      if (!active) return;
      setLoadState('error');
      setNotice(failure instanceof ApiError && failure.status === 404
        ? 'Học kỳ này chưa được liên kết với hồ sơ sinh viên của bạn.'
        : 'Không tải được cơ hội hoặc nguyện vọng. Vui lòng thử lại.');
    });
    return () => { active = false; };
  }, [termId, query, filterText, page, refresh]);

  const majorOptions = useMemo(() => {
    const catalogCodes = new Set(majorCatalog.map(item => item.code));
    const extraCodes = opportunities.flatMap(item => item.majorCodes).filter(code => !catalogCodes.has(code));
    const extras = Array.from(new Set(extraCodes)).map(code => ({ code, name: code, majorCodes: [], status: 'ACTIVE' as const }));
    return [...majorCatalog, ...extras].sort((a, b) => a.code.localeCompare(b.code));
  }, [majorCatalog, opportunities]);
  const searchSuggestions = useMemo(() => Array.from(new Set(
    opportunities.flatMap(item => [item.title, item.enterpriseName]).filter(value => value.trim().length > 0)
  )).slice(0, 8), [opportunities]);
  const selected = opportunities.find(item => item.opportunityId === selectedId) || null;
  const preference = selected ? preferences.find(item => item.opportunityId === selected.opportunityId) || null : null;
  const term = terms.find(item => item.termId === termId);
  const mutate = async (ref: string, action: (key: string) => Promise<StudentPreference>) => {
    if (!pending.current.has(ref)) pending.current.set(ref, newIdempotencyKey());
    const key = pending.current.get(ref)!;
    setBusy(true); setNotice('');
    try {
      await action(key);
      pending.current.delete(ref);
      setDraftMessage('');
      setRefresh(value => value + 1);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 409) {
        setNotice(failure.errorCode === 'PREFERENCE_REVIEWED'
          ? 'Nguyện vọng đã được nhà trường xét; hãy gửi phiên bản mới nếu muốn thay đổi.'
          : 'Dữ liệu vừa thay đổi hoặc cơ hội đã đóng. Hãy tải lại trước khi thử lại.');
        setRefresh(value => value + 1);
      } else {
        setNotice(failure instanceof Error ? failure.message : 'Thao tác chưa hoàn tất.');
      }
    } finally { setBusy(false); }
  };

  const sendPreference = (event: FormEvent) => {
    event.preventDefault();
    if (!selected || !termId || busy) return;
    const expectedIntentVersion = preference?.intentVersion ?? 0;
    const input = { opportunityId: selected.opportunityId, message: draftMessage.trim() || null, expectedIntentVersion };
    const ref = 'submit' + JSON.stringify(input);
    void mutate(ref, key => submitPreference(input, key));
  };

  const withdraw = () => {
    if (!preference || preference.status !== 'SUBMITTED' || busy) return;
    const ref = 'withdraw' + preference.preferenceId + preference.version;
    void mutate(ref, key => withdrawPreference(preference, key));
  };

  return <div className="student-page student-opportunities">
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">CƠ HỘI THỰC TẬP</p>
        <h2>Cơ hội phù hợp với học kỳ</h2><p className="section-description">Cơ hội được nhà trường phê duyệt và đang mở đăng ký trong học kỳ đã liên kết.</p></div>
        <span className="section-icon"><Icon name="briefcase" size={21} /></span></div>
      {termState === 'loading' ? <p>Đang tải học kỳ…</p>
        : termState === 'error' ? <div><p>Không tải được học kỳ.</p><button className="secondary" onClick={() => setTermRetry(value => value + 1)}>Thử tải lại</button></div>
        : terms.length === 0 ? <p>Chưa có học kỳ đang hoạt động trong phạm vi trường của bạn.</p>
        : <label className="student-term-select">Học kỳ<select value={termId} onChange={event => {
          setTermId(event.target.value); setSelectedId(''); setPage(0); setFilterText('');
        }}>
          <option value="">Chọn học kỳ</option>{terms.map(item => <option key={item.termId} value={item.termId}>{termLabel(item)}</option>)}
        </select></label>}
      {term && <p className="student-opportunity-period">{term.startDate} – {term.endDate}</p>}
      <form className="student-opportunity-filters" onSubmit={event => {
        event.preventDefault(); setPage(0); setFilterText(''); setQuery(searchText);
      }}>
        <label>Tìm kiếm cơ hội<input name="q" list="student-opportunity-suggestions" maxLength={150} placeholder="Tên vị trí hoặc doanh nghiệp" value={searchText}
          onChange={event => setSearchText(event.target.value)} /></label>
        <label>Ngành<select value={filterText} onChange={event => { setFilterText(event.target.value); setPage(0); }}>
          <option value="">Tất cả ngành</option>{majorOptions.map(item => <option value={item.code} key={item.code}>
            {codeLabels[item.code] || item.name} ({item.code})
          </option>)}
        </select></label>
        <button type="submit" disabled={!termId || loadState === 'loading'}>Tìm cơ hội</button>
      </form>
      {termId && <datalist id="student-opportunity-suggestions">
        {searchSuggestions.map(value => <option value={value} key={value} />)}
      </datalist>}
      {termId && <div className="student-opportunity-suggestions" aria-label="Gợi ý tìm kiếm">
        <span>Gợi ý vị trí/doanh nghiệp:</span>
        {searchSuggestions.length > 0
          ? searchSuggestions.map(value => <button type="button" className="text-button" key={value} onClick={() => {
            setSearchText(value); setQuery(value); setPage(0);
          }} aria-label={`Gợi ý tìm kiếm ${value}`}>{value}</button>)
          : <span>{loadState === 'loading' ? 'Đang tải…' : 'Chưa có cơ hội để gợi ý. Có thể thử từ khóa: Java, Backend, Data Analyst hoặc tên doanh nghiệp.'}</span>}
      </div>}
      {termId && majorState === 'error' && <div className="student-major-error">
        <span>Không tải được danh mục ngành của học kỳ này.</span>
        <button type="button" className="text-button" onClick={() => setMajorRetry(value => value + 1)}>Thử lại</button>
      </div>}
      {notice && <Notice message={notice} tone="error" />}
      <button type="button" className="secondary student-invitation-link" onClick={openInvitations}>
        <Icon name="mail" size={17} />Xem lời mời và quyền chia sẻ
      </button>
    </section>

    {termId && <div className="student-opportunity-layout">
      <section className="panel student-opportunity-list" aria-label="Danh sách cơ hội">
        <div className="student-opportunity-list-heading"><h2>Cơ hội đang mở</h2><button className="secondary" disabled={loadState === 'loading'} onClick={() => setRefresh(value => value + 1)}>Tải lại</button></div>
        {loadState === 'loading' ? <p>Đang tải cơ hội…</p>
          : loadState === 'error' ? <p>Danh sách chưa sẵn sàng.</p>
          : opportunities.length === 0 ? <p>Chưa có cơ hội đang mở với bộ lọc đã chọn.</p>
          : opportunities.map(item => <button key={item.opportunityId} type="button"
              className={'student-opportunity-item' + (item.opportunityId === selectedId ? ' selected' : '')}
              onClick={() => { setSelectedId(item.opportunityId); setDraftMessage(''); }}>
              <strong>{item.title}</strong><span>{item.enterpriseName}</span>
              <small>Đóng {new Date(item.closedAt).toLocaleDateString('vi-VN')}</small>
            </button>)}
        {totalPages > 1 && <div className="button-row"><button type="button" className="secondary" disabled={page === 0 || loadState === 'loading'} onClick={() => setPage(value => value - 1)}>Trang trước</button>
          <span>Trang {page + 1}/{totalPages}</span><button type="button" className="secondary" disabled={page + 1 >= totalPages || loadState === 'loading'} onClick={() => setPage(value => value + 1)}>Trang sau</button></div>}
      </section>
      <section className="panel student-opportunity-detail" aria-live="polite">
        {!selected ? <p>Chọn một cơ hội để xem chi tiết và gửi nguyện vọng.</p> : <>
          <p className="section-kicker">{selected.enterpriseName}</p><h2>{selected.title}</h2>
          <p>{selected.location || 'Địa điểm chưa được cung cấp'}</p>
          <p>Thời gian: {selected.dateRange.startDate} – {selected.dateRange.endDate}</p>
          <p>Hạn đăng ký: {new Date(selected.closedAt).toLocaleString('vi-VN')}</p>
          <p>Ngành phù hợp: {selected.majorCodes.length ? selected.majorCodes.map(code => codeLabels[code] || code).join(', ') : 'Không giới hạn theo ngành'}</p>
          {selected.benefits && <p>Quyền lợi: {selected.benefits}</p>}
          {preference && <div className="student-preference-status"><strong>{statusLabels[preference.status]}</strong>
            <span>Phiên bản nguyện vọng {preference.intentVersion} · cập nhật {new Date(preference.createdAt).toLocaleString('vi-VN')}</span>
            {preference.message && <p>{preference.message}</p>}
            {preference.status === 'SUBMITTED' && <button className="secondary" type="button" disabled={busy} onClick={withdraw}>Rút nguyện vọng</button>}
          </div>}
            {preference?.status === 'REVIEWED' && <p className="access-note">Nguyện vọng đã được xét. Nếu muốn thay đổi, hãy gửi một phiên bản nguyện vọng mới.</p>}
            <form onSubmit={sendPreference}>
              <label>Lời nhắn với nhà trường<textarea maxLength={1000} rows={4} value={draftMessage}
                onChange={event => setDraftMessage(event.target.value)} placeholder="Điều gì khiến bạn quan tâm đến cơ hội này?" /></label>
              <button type="submit" disabled={busy || loadState !== 'ready'}>{preference ? 'Gửi phiên bản nguyện vọng mới' : 'Gửi nguyện vọng'}</button>
            </form>
          <p className="access-note">Gửi nguyện vọng không tự tạo lời mời, quyền chia sẻ hồ sơ hoặc Placement. Bạn phản hồi lời mời và cấp quyền chia sẻ riêng.</p>
        </>}
      </section>
    </div>}
  </div>;
}
