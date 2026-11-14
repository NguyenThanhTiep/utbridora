import { useEffect, useState } from 'react';
import { api, type Grant, type Me } from '../../../api';
import { Icon, Notice } from '../../../components';
import type { ContractPage, TermReference } from '../types';
import '../styles.css';

type OpportunityView = {
  opportunityId: string; cooperationId: string; demandId: string;
  schoolOrgId: string; enterpriseOrgId: string; termId: string;
  enterpriseName: string; title: string; majorCodes: string[];
  dateRange: { startDate: string; endDate: string };
  benefits: string | null; location: string | null;
  publishedAt: string; closedAt: string; version: number;
};

// S01 – khung tổng quan (T2-02 tuần 1) + Bảng tin cơ hội thực tập đã công bố (T5-06/T5-09).
export function StudentOverviewPage({ me, schools, openProfile }: { me: Me; schools: Grant[]; openProfile: () => void }) {
  const defaultSchool = schools[0]?.organizationId || '';
  const [schoolId, setSchoolId] = useState(defaultSchool);
  const [terms, setTerms] = useState<TermReference[]>([]);
  const [termId, setTermId] = useState('');
  const [query, setQuery] = useState('');
  const [opportunities, setOpportunities] = useState<OpportunityView[]>([]);
  const [selectedDetail, setSelectedDetail] = useState<OpportunityView | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!schoolId) return;
    const c = new AbortController();
    void api<ContractPage<TermReference>>(`/terms?schoolOrgId=${encodeURIComponent(schoolId)}&page=0&size=25`, { signal: c.signal })
      .then(p => {
        if (c.signal.aborted) return;
        const active = p.content.filter(t => t.status === 'ACTIVE');
        const list = active.length ? active : p.content;
        setTerms(list);
        setTermId(list[0]?.termId || '');
      })
      .catch(() => { if (!c.signal.aborted) setTerms([]); });
    return () => c.abort();
  }, [schoolId]);

  useEffect(() => {
    if (!termId) { setOpportunities([]); return; }
    const c = new AbortController();
    setLoading(true); setError(''); setSelectedDetail(null);
    const params = new URLSearchParams({ termId, page: '0', size: '25', ...(query.trim() ? { q: query.trim() } : {}) });
    void api<ContractPage<OpportunityView>>(`/opportunities?${params}`, { signal: c.signal })
      .then(p => { if (!c.signal.aborted) setOpportunities(p.content); })
      .catch(() => { if (!c.signal.aborted) setError('Chưa tải được danh sách cơ hội thực tập trong kỳ này.'); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [termId, query]);

  return <div className="student-page">
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">HỒ SƠ SINH VIÊN</p><h2>Xin chào, {me.displayName}</h2>
        <p className="section-description">Hoàn thiện thông tin chung để nhà trường có căn cứ đề xuất bạn vào các cơ hội thực tập.</p></div>
        <span className="section-icon"><Icon name="graduation" size={21} /></span></div>
      <div className="student-scope"><span className="identity-label"><Icon name="building" size={15} />Phạm vi nhà trường</span>
        <ul className="student-scope-list">{schools.map(school => <li key={school.organizationId}><code title={school.organizationId || undefined}>{school.organizationId?.slice(0, 8)}</code></li>)}</ul></div>
    </section>
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">BƯỚC TIẾP THEO</p><h2>Xem thông tin chung</h2>
        <p className="section-description">Kiểm tra thông tin cơ bản trong hồ sơ của bạn.</p></div>
        <span className="section-icon"><Icon name="user" size={21} /></span></div>
      <button type="button" onClick={openProfile}>Mở thông tin chung<Icon name="arrow-right" size={17} /></button>
      <p className="access-note"><Icon name="info" size={15} />Mức hoàn thiện hồ sơ và các bước tiếp theo sẽ hiển thị khi dịch vụ hồ sơ sinh viên sẵn sàng.</p>
    </section>
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">CƠ HỘI THỰC TẬP</p><h2>Cơ hội thực tập nhà trường công bố</h2>
        <p className="section-description">Tra cứu các vị trí thực tập đang mở trong kỳ học của bạn. Nhà trường sẽ đối chiếu hồ sơ và gửi lời mời thực tập phù hợp.</p></div>
        <span className="section-icon"><Icon name="briefcase" size={21} /></span></div>
      <Notice message={error} tone="error" />
      <div className="student-opp-filters">
        {schools.length > 1 && <label>Trường học<select value={schoolId} onChange={e => setSchoolId(e.target.value)}>
          {schools.map(s => <option key={s.organizationId} value={s.organizationId || ''}>{s.organizationId?.slice(0, 8)}</option>)}
        </select></label>}
        <label>Kỳ thực tập<select value={termId} onChange={e => setTermId(e.target.value)} disabled={!terms.length}>
          {terms.length === 0 ? <option value="">Chưa có kỳ thực tập</option> : terms.map(t => <option key={t.termId} value={t.termId}>{t.termCode} ({t.startDate} – {t.endDate})</option>)}
        </select></label>
        <label>Tìm theo vị trí hoặc doanh nghiệp<input type="search" value={query} onChange={e => setQuery(e.target.value)} placeholder="Nhập tên vị trí hoặc doanh nghiệp…" /></label>
      </div>
      {loading && <p role="status">Đang tải cơ hội thực tập…</p>}
      {!loading && opportunities.length === 0 && <p className="muted">Chưa có cơ hội thực tập nào đang mở công bố trong kỳ học này.</p>}
      {opportunities.length > 0 && <div className="student-opp-list">
        {opportunities.map(opp => <article className="student-opp-card" key={opp.opportunityId}>
          <div className="student-opp-card-head">
            <div><h3>{opp.title}</h3><p className="student-opp-meta"><strong>{opp.enterpriseName}</strong>{opp.location ? ` · ${opp.location}` : ''}</p></div>
            <span className="student-opp-badge">Chỉ xem · Trường đề cử</span>
          </div>
          <p className="student-opp-meta">Thời gian thực tập: {opp.dateRange.startDate} – {opp.dateRange.endDate} · Đóng công bố: {new Date(opp.closedAt).toLocaleString('vi-VN')}</p>
          {opp.majorCodes.length > 0 && <div className="student-opp-tags">{opp.majorCodes.map(m => <span className="student-opp-tag" key={m}>Ngành: {m}</span>)}</div>}
          <button type="button" className="secondary" onClick={() => {
            if (selectedDetail?.opportunityId === opp.opportunityId) { setSelectedDetail(null); return; }
            void api<OpportunityView>(`/opportunities/${opp.opportunityId}`).then(setSelectedDetail).catch(() => setSelectedDetail(opp));
          }}>{selectedDetail?.opportunityId === opp.opportunityId ? 'Ẩn chi tiết cơ hội' : 'Xem chi tiết cơ hội'}</button>
          {selectedDetail?.opportunityId === opp.opportunityId && <div className="student-opp-detail">
            <p><strong>Doanh nghiệp tiếp nhận:</strong> {selectedDetail.enterpriseName}</p>
            <p><strong>Địa điểm làm việc:</strong> {selectedDetail.location || 'Theo sắp xếp của doanh nghiệp'}</p>
            <p><strong>Quyền lợi / Hỗ trợ:</strong> {selectedDetail.benefits || 'Theo chính sách thực tập của doanh nghiệp'}</p>
            <p><strong>Hướng dẫn tham gia:</strong> Cập nhật đầy đủ kỹ năng và lịch trống tại mục <em>Thông tin chung</em>. Khi nhà trường đề cử bạn vào vị trí này, lời mời sẽ xuất hiện tại mục <em>Lời mời & nguyện vọng</em>.</p>
          </div>}
        </article>)}
      </div>}
    </section>
  </div>;
}
