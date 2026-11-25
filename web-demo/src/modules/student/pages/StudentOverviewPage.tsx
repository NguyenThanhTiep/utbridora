import { useEffect, useId, useState, type CSSProperties } from 'react';
import { ApiError, api, type Grant, type Me } from '../../../api';
import { Icon, Notice } from '../../../components';
import type { TaskAssignment } from '../../internship/workflow';
import { getMyProfile, listMyInvitations } from '../api';
import { StudentIcon, StudentNote, StudentStat, orgInitials, termProgress } from '../components/StudentUI';
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
const TONES = ['green', 'blue', 'purple', 'amber'] as const;
const WEEKDAYS = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy'];
const WEEK_SHORT = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const day = (value: string) => value.split('-').reverse().join('/');
// Lời chào theo buổi và ngày hôm nay, tính theo giờ máy.
const greetingOf = (hour: number) => hour < 11 ? 'Chào buổi sáng' : hour < 13 ? 'Chào buổi trưa' : hour < 18 ? 'Chào buổi chiều' : 'Chào buổi tối';
// Cùng nhãn với bảng nhiệm vụ T3 (TaskBoard).
const ASSIGNMENT_STATES: Record<TaskAssignment['state'], string> = { NEW: 'Chưa bắt đầu', IN_PROGRESS: 'Đang làm', SUBMITTED: 'Đã nộp', REVISION_REQUIRED: 'Cần sửa', COMPLETED: 'Hoàn thành' };
const pad = (value: number) => String(value).padStart(2, '0');
const shortDate = (value: Date) => pad(value.getDate()) + '/' + pad(value.getMonth() + 1);

// S01 – giao diện theo frame Trang chủ 477:12990 (banner chào, 4 thẻ số liệu, việc cần thực hiện, lịch tuần), nội dung giữ của
// frame Tổng quan 477:11121 + Bảng tin cơ hội thực tập đã công bố (T5-06/T5-09). Không dùng ảnh nền trường của frame vì hệ thống dùng chung cho nhiều trường.
// Số liệu chỉ đếm từ API thật: tiêu chí hồ sơ (BC11), lời mời chờ phản hồi (BC23), phân công nhiệm vụ của chính sinh viên (T3),
// số cơ hội trong kỳ (thay ô "10 chức năng" viết cứng của Figma). Lịch tuần là hạn nhiệm vụ T3 đã tải, không thêm lời gọi API.
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
  const [openTotal, setOpenTotal] = useState<Loaded<number>>('loading');
  const statsId = useId(), heroId = useId();

  // Chỉ đọc hồ sơ khi đã xác định đúng một học kỳ đang mở; chưa có học kỳ thì không gọi API hồ sơ.
  const profileTerm = useStudentTerm(schools);
  const [profile, setProfile] = useState<Loaded<SelfProfileView | 'missing'> | null>(null);
  const [pendingInvitations, setPendingInvitations] = useState<Loaded<number>>('loading');
  const { assignments, tasks, error: taskError } = useMyCaseIds(me.userId);

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
        if (!list.length) setOpenTotal(0);
      })
      .catch(() => { if (!c.signal.aborted) { setTerms([]); setOpenTotal('error'); } });
    return () => c.abort();
  }, [schoolId]);

  useEffect(() => {
    if (!termId) { setOpportunities([]); return; }
    const c = new AbortController();
    setLoading(true); setError(''); setSelectedDetail(null);
    // Thẻ số liệu chỉ đếm khi không lọc theo từ khóa, để luôn là tổng số cơ hội của kỳ.
    const counting = !query.trim();
    if (counting) setOpenTotal('loading');
    const params = new URLSearchParams({ termId, page: '0', size: '25', ...(query.trim() ? { q: query.trim() } : {}) });
    void api<ContractPage<OpportunityView>>(`/opportunities?${params}`, { signal: c.signal })
      .then(p => { if (!c.signal.aborted) { setOpportunities(p.content); if (counting) setOpenTotal(p.totalElements); } })
      .catch(() => { if (!c.signal.aborted) { setError('Chưa tải được danh sách cơ hội thực tập trong kỳ này.'); if (counting) setOpenTotal('error'); } })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [termId, query]);

  const view = typeof profile === 'object' && profile !== null ? profile : null;
  const passed = view?.completeness.checks.filter(check => check.passed).length ?? 0, total = view?.completeness.checks.length ?? 0;
  const failing = (codes: string[]) => !!view?.completeness.checks.some(check => codes.includes(check.code) && !check.passed);
  const actionable = assignments?.filter(item => ACTIONABLE.includes(item.state)).length;
  // Việc thuộc hồ sơ mở được Thông tin chung; việc ở màn khác chỉ ghi tên mục vì trang này không có lối điều hướng sang đó.
  // Biểu tượng và tông theo loại việc: hồ sơ (xanh lá), lời mời (xanh dương), nhiệm vụ (tím).
  type Todo = { text: string; where?: string; icon: string; tone: (typeof TONES)[number] };
  const profileTodo = (text: string): Todo => ({ text, icon: 'user', tone: 'green' });
  const todos: Todo[] = [
    ...(profile === 'missing' ? [profileTodo('Tạo hồ sơ sinh viên ở Thông tin chung')] : []),
    ...(failing(['MAJOR_VALID']) ? [profileTodo('Khai báo ngành học hợp lệ')] : []),
    ...(failing(['SKILLS_DECLARED', 'SKILLS_VALID']) ? [profileTodo('Bổ sung kỹ năng chuẩn theo danh mục')] : []),
    ...(failing(['AVAILABILITY_DECLARED', 'AVAILABILITY_VALID']) ? [profileTodo('Xác nhận lịch rảnh của kỳ học')] : []),
    ...(typeof pendingInvitations === 'number' && pendingInvitations > 0 ? [{ text: 'Kiểm tra lời mời đang chờ phản hồi', where: 'Lời mời & nguyện vọng', icon: 'mail', tone: 'blue' as const }] : []),
    ...(actionable ? [{ text: 'Thực hiện các nhiệm vụ được giao', where: 'Nhiệm vụ & sản phẩm', icon: 'check', tone: 'purple' as const }] : [])
  ];
  const completenessValue = !profileTerm.terms ? '…' : !profileTerm.termId ? 'Chưa xác định học kỳ'
    : profile === 'loading' || profile === null ? '…' : profile === 'missing' ? 'Chưa có hồ sơ' : profile === 'error' ? 'Chưa tải được' : pad(passed) + '/' + pad(total);
  // Tuần tính từ thứ Hai 00:00 tới hết Chủ nhật theo giờ máy.
  const weekFrom = new Date(); weekFrom.setHours(0, 0, 0, 0); weekFrom.setDate(weekFrom.getDate() - (weekFrom.getDay() + 6) % 7);
  const weekUntil = new Date(weekFrom); weekUntil.setDate(weekUntil.getDate() + 7);
  const lastDay = new Date(weekUntil); lastDay.setDate(lastDay.getDate() - 1);
  const thisWeek = tasks?.filter(task => { const due = Date.parse(task.dueAt); return due >= weekFrom.getTime() && due < weekUntil.getTime(); })
    .sort((a, b) => Date.parse(a.dueAt) - Date.parse(b.dueAt));

  const now = new Date(), term = profileTerm.terms?.find(item => item.termId === profileTerm.termId);
  const progress = term ? termProgress(term.startDate, term.endDate) : null;
  // Dải 7 ngày của tuần (thứ Hai → Chủ nhật), đánh dấu hôm nay và số hạn nhiệm vụ từng ngày.
  const weekDays = Array.from({ length: 7 }, (_, index) => { const date = new Date(weekFrom); date.setDate(date.getDate() + index);
    return { date, today: date.toDateString() === now.toDateString(), due: thisWeek?.filter(task => new Date(task.dueAt).toDateString() === date.toDateString()).length ?? 0 }; });

  // S01 "Bảng điều khiển": hero nền xanh đậm (lời chào theo buổi, ngày hôm nay, chip học kỳ / hồ sơ / phạm vi trường, nút hồ sơ) với
  // vòng tiến độ học kỳ bên phải; 4 thẻ số liệu; việc cần làm + lịch tuần (dải 7 ngày); bảng tin cơ hội thực tập.
  return <div className="student-page">
    <section className="student-dash-hero" aria-labelledby={heroId}>
      <div className="student-dash-hero-text">
        <p className="student-dash-kicker"><StudentIcon name={now.getHours() < 18 ? 'sun' : 'moon'} size={16} />{greetingOf(now.getHours())} · {WEEKDAYS[now.getDay()]}, {shortDate(now)}/{now.getFullYear()}</p>
        <h2 id={heroId}>Xin chào, {me.displayName}!</h2>
        <p>Theo dõi hồ sơ, khám phá cơ hội thực tập và quản lý tiến độ học tập của bạn tại một nơi.</p>
        <ul className="student-dash-chips" aria-label="Thông tin nhanh">
          {term && <li><Icon name="calendar" size={14} />Kỳ {term.termCode} · {day(term.startDate)} – {day(term.endDate)}</li>}
          {view && <li className={view.completeness.complete ? 'is-ok' : 'is-warn'}><Icon name="check-circle" size={14} />{view.completeness.complete ? 'Hồ sơ đã đủ thông tin' : 'Hồ sơ cần cập nhật'}</li>}
          <li title={schools.map(school => school.organizationId).join(', ')}><Icon name="university" size={14} />Phạm vi nhà trường · {schools.map(school => school.organizationId?.slice(0, 8)).join(', ')}</li>
        </ul>
        <button type="button" className="student-dash-cta" onClick={openProfile}>{view?.completeness.complete ? 'Xem hồ sơ' : 'Hoàn thiện hồ sơ'}<Icon name="arrow-right" size={16} /></button>
      </div>
      <div className="student-dash-term">
        {progress && term ? <><div className="student-dash-ring" role="progressbar" aria-label={'Tiến độ học kỳ ' + term.termCode} aria-valuemin={0} aria-valuemax={100} aria-valuenow={progress.percent}
          style={{ '--value': progress.percent } as CSSProperties}>
          <span>{progress.status === 'during' ? <><b>Tuần {progress.week}</b><small>trên {progress.weeks} tuần</small></> : <b>{progress.status === 'before' ? 'Sắp bắt đầu' : 'Đã kết thúc'}</b>}</span></div>
          <p>Học kỳ {term.termCode}</p>
          <small>{progress.status === 'during' ? 'Còn ' + progress.daysLeft + ' ngày · ' + progress.percent + '% đã qua' : progress.status === 'before' ? 'Còn ' + progress.daysUntil + ' ngày nữa bắt đầu' : 'Kỳ học đã khép lại'}</small></>
          : <><div className="student-dash-ring is-empty"><span><Icon name="calendar" size={26} /></span></div><p>Tiến độ học kỳ</p>
            <small>{profileTerm.terms === null ? 'Đang tải học kỳ…' : 'Chưa xác định học kỳ đang mở'}</small></>}
      </div>
    </section>

    <section aria-labelledby={statsId}>
      <h2 className="student-home-title" id={statsId}>Tổng quan của bạn</h2>
      <div className="student-home-stats">
        <StudentStat tone="green" icon="check-circle" value={completenessValue} label="Tiêu chí hồ sơ" progress={view && total ? Math.round(passed / total * 100) : undefined}
          hint={view ? (view.completeness.complete ? 'Hồ sơ đã đủ thông tin' : 'Hồ sơ đang hoàn thiện') : 'Độ hoàn thiện hồ sơ'}>
          <button type="button" className="text-button student-kpi-link" onClick={openProfile}>Mở thông tin chung<Icon name="arrow-right" size={14} /></button>
        </StudentStat>
        <StudentStat tone="blue" icon="mail" value={pendingInvitations === 'loading' ? '…' : pendingInvitations === 'error' ? 'Chưa tải được' : pad(pendingInvitations)}
          label="Lời mời thực tập" hint="Đang chờ bạn phản hồi · xem ở Lời mời & nguyện vọng" />
        <StudentStat tone="purple" icon="check" value={taskError ? 'Chưa tải được' : actionable === undefined ? '…' : pad(actionable)}
          label="Việc cần làm" hint="Nhiệm vụ mới, đang làm hoặc cần sửa · xem ở Nhiệm vụ & sản phẩm" />
        <StudentStat tone="amber" icon="briefcase" value={openTotal === 'loading' ? '…' : openTotal === 'error' ? 'Chưa tải được' : pad(openTotal)}
          label="Cơ hội thực tập" hint="Nhà trường công bố trong kỳ · xem bên dưới" />
      </div>
    </section>

    <div className="student-home-grid">
      <section className="panel">
        <div className="student-home-head"><span className="student-home-head-icon" aria-hidden="true"><Icon name="check-circle" size={20} /></span>
          <div><h2>Việc cần thực hiện</h2><p className="student-home-sub">Những việc bạn nên kiểm tra tiếp theo</p></div>
          {todos.length > 0 && <span className="student-home-count" aria-label={todos.length + ' việc'}>{todos.length}</span>}</div>
        {todos.length === 0 ? <div className="student-home-done"><span aria-hidden="true"><Icon name="check" size={22} /></span><p className="student-card-note">Chưa có việc cần làm từ hồ sơ, lời mời hoặc nhiệm vụ đã tải.</p></div>
          : <ul className="student-home-list">{todos.map(todo => <li key={todo.text} className={'student-home-row tone-' + todo.tone}>
            {todo.where ? <div><span className="student-home-row-icon" aria-hidden="true"><Icon name={todo.icon} size={17} /></span><span className="student-home-row-text">{todo.text}<small>Xem ở mục {todo.where}</small></span></div>
              : <button type="button" onClick={openProfile}><span className="student-home-row-icon" aria-hidden="true"><Icon name={todo.icon} size={17} /></span><span className="student-home-row-text">{todo.text}<small>Mở Thông tin chung</small></span><Icon name="arrow-right" size={16} /></button>}
          </li>)}</ul>}
      </section>
      <section className="panel">
        <div className="student-home-head"><span className="student-home-head-icon" aria-hidden="true"><Icon name="calendar" size={20} /></span>
          <div><h2>Lịch cá nhân / Tuần này</h2><p className="student-home-sub">Hạn nhiệm vụ thực tập từ {shortDate(weekFrom)} đến {shortDate(lastDay)}</p></div></div>
        <ol className="student-week" aria-label="Các ngày trong tuần">{weekDays.map((item, index) => <li key={index} className={(item.today ? 'is-today' : '') + (item.due ? ' has-due' : '')}>
          <small>{WEEK_SHORT[index]}</small><b>{item.date.getDate()}</b>
          <span className="student-week-dot" aria-hidden="true" />{item.today && <span className="student-sr-only">Hôm nay.</span>}{item.due > 0 && <span className="student-sr-only">{item.due} hạn nhiệm vụ.</span>}</li>)}</ol>
        {taskError ? <p className="student-card-note">Chưa tải được nhiệm vụ để dựng lịch tuần.</p>
          : !thisWeek ? <p className="student-card-note" role="status">Đang tải lịch tuần…</p>
          : thisWeek.length === 0 ? <p className="student-card-note">Tuần này chưa có hạn nhiệm vụ nào. Xem toàn bộ ở Nhiệm vụ &amp; sản phẩm.</p>
          : <ul className="student-home-list">{thisWeek.map((task, index) => {
            const due = new Date(task.dueAt), state = task.assignments.find(item => item.studentUserId === me.userId)?.state;
            return <li key={task.taskId} className={'student-home-row tone-' + TONES[index % TONES.length]}><div>
              <span className="student-home-row-date" aria-hidden="true"><small>{WEEK_SHORT[(due.getDay() + 6) % 7]}</small><b>{pad(due.getDate())}</b></span>
              <span className="student-home-row-text"><strong>{WEEKDAYS[due.getDay()]} · {task.title}</strong>
                <small>Hạn {pad(due.getHours())}:{pad(due.getMinutes())} ngày {shortDate(due)}{state ? ' · ' + ASSIGNMENT_STATES[state] : ''}</small></span>
            </div></li>;
          })}</ul>}
      </section>
    </div>

    <section className="panel student-home-opps">
      <div className="section-heading"><div className="student-home-head"><span className="student-home-head-icon" aria-hidden="true"><Icon name="briefcase" size={20} /></span>
        <div><p className="section-kicker">CƠ HỘI THỰC TẬP</p><h2>Cơ hội thực tập nhà trường công bố</h2>
        <p className="section-description">Tra cứu các vị trí thực tập đang mở trong kỳ học của bạn. Nhà trường sẽ đối chiếu hồ sơ và gửi lời mời thực tập phù hợp.</p></div></div></div>
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
            <span className="student-offer-logo" aria-hidden="true">{orgInitials(opp.enterpriseName)}</span>
            <h3>{opp.title}</h3>
            <span className="student-opp-badge">Chỉ xem · Trường đề cử</span>
          </div>
          <p className="student-opp-company">{opp.enterpriseName}{opp.location ? ` · ${opp.location}` : ''}</p>
          <p className="student-opp-meta">Thời gian thực tập: {opp.dateRange.startDate} – {opp.dateRange.endDate} · Đóng công bố: {new Date(opp.closedAt).toLocaleString('vi-VN')}</p>
          {opp.majorCodes.length > 0 && <div className="student-opp-tags">{opp.majorCodes.map(m => <span className="student-opp-tag" key={m}>Ngành: {m}</span>)}</div>}
          <button type="button" className="student-opp-more" aria-expanded={selectedDetail?.opportunityId === opp.opportunityId} onClick={() => {
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
