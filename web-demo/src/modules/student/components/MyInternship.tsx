import { useEffect, useState, type CSSProperties, type ReactNode } from 'react';
import { EmptyState, Icon, LoadingState } from '../../../components';
import type { CaseRef } from '../../internship/cases';
import { workflowError } from '../../internship/workflow-api';
import { getMyCase, listMyInvitations, listTerms } from '../api';
import { useMyCaseIds } from '../internship';
import { StudentIcon, termProgress } from './StudentUI';
import { date } from '../preferences';
import type { InternshipInvitation, TermReference } from '../types';
import '../styles.css';

const caseStates: Record<string, string> = { WAITING_START: 'Chờ bắt đầu', IN_PROGRESS: 'Đang thực tập', COMPLETED: 'Đã hoàn thành', TERMINATED: 'Đã dừng', CANCELLED: 'Đã hủy' };
// Tông chip trạng thái kỳ: đang thực tập xanh lá, chờ bắt đầu vàng, đã hoàn thành xanh dương, dừng/hủy đỏ.
const caseTones: Record<string, string> = { WAITING_START: 'tone-warning', IN_PROGRESS: 'tone-success', COMPLETED: 'tone-info', TERMINATED: 'rejected', CANCELLED: 'rejected' };
const pad = (value: number) => String(value).padStart(2, '0');
type Loaded = { cases: CaseRef[]; invitations: InternshipInvitation[]; terms: TermReference[] };

// Tiến độ học kỳ của hồ sơ (BC21 startDate/endDate) tính theo ngày trên máy: % đã qua, tuần thứ mấy, còn bao nhiêu ngày.
// Chỉ là phép tính hiển thị từ ngày kỳ do API trả, không phải dữ liệu tiến độ thực tập của T3.
// Hồ sơ T3 đã COMPLETED thì thanh đầy kèm "Đã hoàn thành thực tập" thay cho tiến độ học kỳ (kỳ có thể còn chạy tiếp).
function TermProgress({ term, completed }: { term: TermReference; completed: boolean }) {
  const progress = termProgress(term.startDate, term.endDate), { status, week, weeks, daysLeft, daysUntil } = progress;
  const percent = completed ? 100 : progress.percent;
  const text = completed ? 'Đã hoàn thành thực tập' : status === 'before' ? 'Chưa bắt đầu · còn ' + daysUntil + ' ngày' : status === 'after' ? 'Kỳ đã kết thúc'
    : 'Tuần ' + week + '/' + weeks + ' · còn ' + daysLeft + ' ngày';
  const label = (completed ? 'Tiến độ thực tập kỳ ' : 'Tiến độ kỳ ') + term.termCode;
  return <div className={'student-term-progress' + (completed ? ' is-done' : '')}>
    <div className="student-term-progress-head"><span>{label}</span><b>{text}</b></div>
    <div className="student-term-bar" role="progressbar" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={percent}
      aria-valuetext={percent + '%, ' + text} style={{ '--value': percent + '%' } as CSSProperties}><span /></div>
    <div className="student-term-progress-ends"><span>{date(term.startDate)}</span><span>{completed ? '100% hoàn thành' : percent + '% đã qua'}</span><span>{date(term.endDate)}</span></div>
  </div>;
}

// T2-F22: vị trí đã chốt (lời mời đã nhận có Placement, BC23) và kỳ thực tập (hồ sơ T3 CP-WS1, học kỳ T5 BC21). Chỉ đọc API thật.
// children: ghi chú hiển thị ở chân thẻ (điều kiện bắt đầu nhiệm vụ của S07).
export function MyInternship({ userId, children }: { userId: string; children?: ReactNode }) {
  const { caseIds, assignments, error: caseError, retry } = useMyCaseIds(userId);
  // Frame 477:12406 "Kỳ thực tập của tôi": 3 ô số liệu đếm từ phân công T3 của chính sinh viên theo hồ sơ.
  const count = (caseId: string, state: string) => assignments?.filter(item => item.caseId === caseId && item.state === state).length ?? 0;
  const [data, setData] = useState<Loaded | null>(null), [error, setError] = useState('');
  useEffect(() => {
    if (!caseIds) return;
    const c = new AbortController(); setData(null); setError('');
    void (async () => {
      const [invitations, cases] = await Promise.all([listMyInvitations(c.signal), Promise.all(caseIds.map(id => getMyCase(id, c.signal)))]);
      // Hồ sơ trả về phải đúng mã đã hỏi; sai thì bỏ, không hiển thị.
      const own = cases.filter((item, index) => item.caseId === caseIds[index]);
      const terms = (await Promise.all([...new Set(own.map(item => item.schoolOrgId))].map(listTerms))).flat();
      if (!c.signal.aborted) setData({ cases: own, invitations: invitations.filter(item => item.state === 'ACCEPTED' && !!item.placementId), terms });
    })().catch(failure => { if (!c.signal.aborted) setError(workflowError(failure)); });
    return () => c.abort();
  }, [caseIds]);
  const failure = caseError ? workflowError(caseError) : error;
  const waiting = data ? data.invitations.filter(item => !data.cases.some(value => value.placementId === item.placementId)) : [];
  // Thẻ ngang gọn (không lặp 3 ô số liệu lớn kiểu Tổng quan): nhãn mục + nút tải lại, rồi mỗi vị trí một hàng gồm biểu tượng,
  // tên vị trí, doanh nghiệp và các chip kỳ thực tập / trạng thái / số việc; ghi chú điều kiện ở chân thẻ.
  return <section className="panel student-internship" aria-label="Thực tập của tôi">
    <div className="student-internship-top"><h2 className="student-internship-kicker">Kỳ thực tập của tôi</h2>
      <button type="button" className="secondary" disabled={!failure && !data} onClick={retry}><Icon name="refresh" size={15} />Tải lại thông tin thực tập</button></div>
    {failure ? <p className="student-error-note" role="alert">{failure}</p>
      : !data ? <LoadingState label="Đang tải thông tin thực tập…" />
      : data.cases.length === 0 && waiting.length === 0
        // Không kết luận "chưa có thực tập": thiếu nhiệm vụ thì chưa biết caseId, Placement có thể đã tồn tại.
        ? <EmptyState icon="briefcase" title="Chưa có thông tin thực tập để hiển thị"
          description="Vị trí hiện ở đây khi bạn nhận lời mời và nhà trường tạo Placement. Hồ sơ và kỳ thực tập hiện khi người hướng dẫn giao nhiệm vụ đầu tiên." />
      : <ul className="student-list">
        {data.cases.map(item => {
          const invitation = data.invitations.find(value => value.placementId === item.placementId), term = data.terms.find(value => value.termId === item.termId);
          // Thẻ dạng vé: phần chính (vị trí, doanh nghiệp, trạng thái) | đường đục lỗ | cuống vé (kỳ, ngày bắt đầu → kết thúc);
          // dưới vé là tiến độ kỳ và số việc. Dấu ":" sau mã kỳ chỉ cho trình đọc màn hình để câu đọc liền "Kỳ …: Bắt đầu … Kết thúc …".
          return <li className="student-ticket-item" key={item.caseId}>
            <div className="student-ticket">
              <div className="student-ticket-main">
                <span className="student-internship-icon" aria-hidden="true"><Icon name="briefcase" size={24} /></span>
                <div><p className="student-ticket-label">Vị trí đã chốt</p><h3>{invitation?.title ?? 'Vị trí thực tập'}</h3>
                  {invitation && <p className="student-company">{invitation.enterpriseName}</p>}
                  <span className={'student-status student-internship-state ' + (caseTones[item.state] ?? '')}>{caseStates[item.state] ?? item.state}</span></div>
              </div>
              {term && <div className="student-ticket-stub"><p className="student-ticket-term">
                <span className="student-ticket-code">Kỳ {term.termCode}<span className="student-sr-only">:</span></span>{' '}
                <span className="student-ticket-dates"><span><small>Bắt đầu</small> <b>{date(term.startDate)}</b></span>
                  <Icon name="arrow-right" size={16} /><span><small>Kết thúc</small> <b>{date(term.endDate)}</b></span></span></p></div>}
            </div>
            <div className="student-ticket-foot">
              {term && <TermProgress term={term} completed={item.state === 'COMPLETED'} />}
              <div className="student-internship-chips">
                <span className="student-internship-count tone-blue" title="Nhiệm vụ đang ở trạng thái Đang làm"><StudentIcon name="pencil" size={14} /><b>{pad(count(item.caseId, 'IN_PROGRESS'))}</b> đang làm</span>
                <span className="student-internship-count tone-amber" title="Bài nộp đang chờ nhận xét"><Icon name="clock" size={14} /><b>{pad(count(item.caseId, 'SUBMITTED'))}</b> chờ mentor phản hồi</span>
              </div>
            </div>
            {item.state === 'WAITING_START' && <p className="student-list-meta">Chưa nhận hoặc nộp nhiệm vụ trước khi thực tập được kích hoạt.</p>}
          </li>;
        })}
        {waiting.map(item => <li className="student-list-item student-internship-card" key={item.invitationId}>
          <div className="student-internship-head">
            <span className="student-internship-icon" aria-hidden="true"><Icon name="briefcase" size={20} /></span>
            <div><h3>{item.title}</h3><p className="student-company">{item.enterpriseName}</p></div>
          </div>
          <p className="student-list-meta">Đã nhận lời mời và có Placement. Hồ sơ thực tập hiện ở đây khi người hướng dẫn giao nhiệm vụ đầu tiên.</p>
        </li>)}
      </ul>}
    {children && <p className="student-internship-note"><Icon name="info" size={16} />{children}</p>}
  </section>;
}
