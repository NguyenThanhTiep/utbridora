import { useEffect, useState } from 'react';
import { ApiError, api, type Grant, type Me } from '../../../api';
import { Icon, Notice } from '../../../components';
import { getMyProfile, listMyInvitations } from '../api';
import { StudentKpi, StudentNote } from '../components/StudentUI';
import { useMyCaseIds } from '../internship';
import { useStudentTerm } from '../preferences';
import type { ContractPage, SelfProfileView, TermReference } from '../types';
import '../styles.css';

type OpportunityView = {
  opportunityId: string; cooperationId: string; demandId: string;
  schoolOrgId: string; enterpriseOrgId: string; termId: string;
  enterpriseName: string; title: string; majorCodes: string[];
  dateRange: { startDate: string; endDate: string };
  benefits: string | null; location: string | null;
  publishedAt: string; closedAt: string; version: number;
};
type Loaded<T> = T | 'loading' | 'error';
const ACTIONABLE = ['NEW', 'IN_PROGRESS', 'REVISION_REQUIRED'];

// S01 – tổng quan theo frame 477:11121 (lời chào, 3 thẻ số liệu, việc cần làm) + Bảng tin cơ hội thực tập đã công bố (T5-06/T5-09).
// Số liệu chỉ đếm từ API thật: tiêu chí hồ sơ (BC11), lời mời chờ phản hồi (BC23), phân công nhiệm vụ của chính sinh viên (T3).
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

  // Chỉ đọc hồ sơ khi đã xác định đúng một học kỳ đang mở; chưa có học kỳ thì không gọi API hồ sơ.
  const profileTerm = useStudentTerm(schools);
  const [profile, setProfile] = useState<Loaded<SelfProfileView | 'missing'> | null>(null);
  const [pendingInvitations, setPendingInvitations] = useState<Loaded<number>>('loading');
  const { assignments, error: taskError } = useMyCaseIds(me.userId);

  useEffect(() => {
    if (!profileTerm.termId) { setProfile(null); return; }
    let active = true;
    setProfile('loading');
    getMyProfile(profileTerm.termId).then(view => { if (active) setProfile(view); },
      failure => { if (active) setProfile(failure instanceof ApiError && failure.status === 404 ? 'missing' : 'error'); });
    return () => { active = false; };
  }, [profileTerm.termId]);
  useEffect(() => {
    const c = new AbortController();
    listMyInvitations(c.signal).then(items => { if (!c.signal.aborted) setPendingInvitations(items.filter(item => item.state === 'PENDING' && Date.now() < Date.parse(item.expiresAt)).length); })
      .catch(() => { if (!c.signal.aborted) setPendingInvitations('error'); });
    return () => c.abort();
  }, []);

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

  const view = typeof profile === 'object' && profile !== null ? profile : null;
  const passed = view?.completeness.checks.filter(check => check.passed).length ?? 0, total = view?.completeness.checks.length ?? 0;
  const failing = (codes: string[]) => !!view?.completeness.checks.some(check => codes.includes(check.code) && !check.passed);
  const actionable = assignments?.filter(item => ACTIONABLE.includes(item.state)).length;
  const todos = [
    ...(profile === 'missing' ? ['Tạo hồ sơ sinh viên ở Thông tin chung'] : []),
    ...(failing(['MAJOR_VALID']) ? ['Khai báo ngành học hợp lệ'] : []),
    ...(failing(['SKILLS_DECLARED', 'SKILLS_VALID']) ? ['Bổ sung kỹ năng chuẩn theo danh mục'] : []),
    ...(failing(['AVAILABILITY_DECLARED', 'AVAILABILITY_VALID']) ? ['Xác nhận lịch rảnh của kỳ học'] : []),
    ...(typeof pendingInvitations === 'number' && pendingInvitations > 0 ? ['Kiểm tra lời mời đang chờ phản hồi'] : []),
    ...(actionable ? ['Thực hiện các nhiệm vụ được giao'] : [])
  ];
  const completenessValue = !profileTerm.terms ? '…' : !profileTerm.termId ? 'Chưa xác định học kỳ'
    : profile === 'loading' || profile === null ? '…' : profile === 'missing' ? 'Chưa có hồ sơ' : profile === 'error' ? 'Chưa tải được' : passed + '/' + total + ' tiêu chí';

  return <div className="student-page">
    <section className="panel student-welcome">
      <h2>Xin chào, {me.displayName}</h2>
      <p className="student-card-note">Hoàn thiện thông tin chung để nhà trường có căn cứ đề xuất bạn vào các cơ hội thực tập.</p>
      {view && <span className={'student-status ' + (view.completeness.complete ? 'tone-success' : 'tone-brand')}>{view.completeness.complete ? 'Hồ sơ đã đủ thông tin' : 'Hồ sơ cần cập nhật'}</span>}
      <div className="student-scope"><span className="student-scope-label">Phạm vi nhà trường</span>
        <ul className="student-scope-list">{schools.map(school => <li key={school.organizationId}><code title={school.organizationId || undefined}>{school.organizationId?.slice(0, 8)}</code></li>)}</ul></div>
    </section>

    <div className="student-kpis">
      <StudentKpi label="Độ hoàn thiện hồ sơ" value={completenessValue}>
        <button type="button" className="text-button student-kpi-link" onClick={openProfile}>Mở thông tin chung<Icon name="arrow-right" size={14} /></button>
      </StudentKpi>
      <StudentKpi label="Lời mời thực tập" value={pendingInvitations === 'loading' ? '…' : pendingInvitations === 'error' ? 'Chưa tải được' : pendingInvitations + ' lời mời'}>
        <p className="student-kpi-hint">Đang chờ bạn phản hồi · xem ở Lời mời &amp; nguyện vọng</p>
      </StudentKpi>
      <StudentKpi label="Việc cần làm" value={taskError ? 'Chưa tải được' : actionable === undefined ? '…' : actionable + ' việc'}>
        <p className="student-kpi-hint">Nhiệm vụ mới, đang làm hoặc cần sửa · xem ở Nhiệm vụ &amp; sản phẩm</p>
      </StudentKpi>
    </div>

    <section className="panel">
      <h2>Việc cần làm</h2>
      {todos.length === 0 ? <p className="student-card-note">Chưa có việc cần làm từ hồ sơ, lời mời hoặc nhiệm vụ đã tải.</p>
        : <ul className="student-checklist">{todos.map(item => <li key={item}><span aria-hidden="true">○</span>{item}</li>)}</ul>}
    </section>

    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">CƠ HỘI THỰC TẬP</p><h2>Cơ hội thực tập nhà trường công bố</h2>
        <p className="section-description">Tra cứu các vị trí thực tập đang mở trong kỳ học của bạn. Nhà trường sẽ đối chiếu hồ sơ và gửi lời mời thực tập phù hợp.</p></div></div>
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
      <StudentNote>Chỉ các cơ hội đã được phê duyệt và công bố mới hiển thị cho sinh viên.</StudentNote>
      {loading && <p role="status">Đang tải cơ hội thực tập…</p>}
      {!loading && opportunities.length === 0 && <p className="muted">Chưa có cơ hội thực tập nào đang mở công bố trong kỳ học này.</p>}
      {opportunities.length > 0 && <div className="student-opp-list">
        {opportunities.map(opp => <article className="student-opp-card" key={opp.opportunityId}>
          <div className="student-opp-card-head">
            <h3>{opp.title}</h3>
            <span className="student-opp-badge">Chỉ xem · Trường đề cử</span>
          </div>
          <p className="student-opp-company">{opp.enterpriseName}{opp.location ? ` · ${opp.location}` : ''}</p>
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
