import { useEffect, useState } from 'react';
import { EmptyState, Icon, LoadingState } from '../../../components';
import type { CaseRef } from '../../internship/cases';
import { workflowError } from '../../internship/workflow-api';
import { getMyCase, listMyInvitations, listTerms } from '../api';
import { useMyCaseIds } from '../internship';
import { StudentKpi } from './StudentUI';
import { date } from '../preferences';
import type { InternshipInvitation, TermReference } from '../types';
import '../styles.css';

const caseStates: Record<string, string> = { WAITING_START: 'Chờ bắt đầu', IN_PROGRESS: 'Đang thực tập', COMPLETED: 'Đã hoàn thành', TERMINATED: 'Đã dừng', CANCELLED: 'Đã hủy' };
type Loaded = { cases: CaseRef[]; invitations: InternshipInvitation[]; terms: TermReference[] };

// T2-F22: vị trí đã chốt (lời mời đã nhận có Placement, BC23) và kỳ thực tập (hồ sơ T3 CP-WS1, học kỳ T5 BC21). Chỉ đọc API thật.
export function MyInternship({ userId }: { userId: string }) {
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
  return <section className="panel student-internship" aria-label="Thực tập của tôi">
    <div className="section-heading"><div><p className="section-kicker">KỲ THỰC TẬP CỦA TÔI</p><h2>Vị trí đã chốt và kỳ thực tập</h2>
      <p className="section-description">Đọc từ lời mời bạn đã nhận và hồ sơ thực tập do nhà trường, doanh nghiệp quản lý.</p></div>
      <button type="button" className="secondary" disabled={!failure && !data} onClick={retry}><Icon name="refresh" size={16} />Tải lại thông tin thực tập</button></div>
    {failure ? <p className="student-error-note" role="alert">{failure}</p>
      : !data ? <LoadingState label="Đang tải thông tin thực tập…" />
      : data.cases.length === 0 && waiting.length === 0
        // Không kết luận "chưa có thực tập": thiếu nhiệm vụ thì chưa biết caseId, Placement có thể đã tồn tại.
        ? <EmptyState icon="briefcase" title="Chưa có thông tin thực tập để hiển thị"
          description="Vị trí hiện ở đây khi bạn nhận lời mời và nhà trường tạo Placement. Hồ sơ và kỳ thực tập hiện khi người hướng dẫn giao nhiệm vụ đầu tiên." />
      : <ul className="student-list">
        {data.cases.map(item => {
          const invitation = data.invitations.find(value => value.placementId === item.placementId), term = data.terms.find(value => value.termId === item.termId);
          return <li className="student-list-item" key={item.caseId}>
            <h3>{invitation?.title ?? 'Vị trí thực tập'}</h3>
            {invitation && <p className="student-company">{invitation.enterpriseName}</p>}
            <div className="student-kpis student-kpis-inner">
              <StudentKpi label="Trạng thái kỳ" value={caseStates[item.state] ?? item.state} />
              <StudentKpi label="Công việc đang làm" value={count(item.caseId, 'IN_PROGRESS') + ' nhiệm vụ'} />
              <StudentKpi label="Chờ mentor phản hồi" value={count(item.caseId, 'SUBMITTED') + ' bài nộp'} />
            </div>
            {term && <p className="student-list-meta">Kỳ {term.termCode}: {date(term.startDate)} – {date(term.endDate)}</p>}
            {item.state === 'WAITING_START' && <p className="student-list-meta">Chưa nhận hoặc nộp nhiệm vụ trước khi thực tập được kích hoạt.</p>}
          </li>;
        })}
        {waiting.map(item => <li className="student-list-item" key={item.invitationId}>
          <h3>{item.title}</h3><p className="student-list-meta">{item.enterpriseName}</p>
          <p className="student-list-meta">Đã nhận lời mời và có Placement. Hồ sơ thực tập hiện ở đây khi người hướng dẫn giao nhiệm vụ đầu tiên.</p>
        </li>)}
      </ul>}
  </section>;
}
