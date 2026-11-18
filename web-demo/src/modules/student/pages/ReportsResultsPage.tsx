import { useCallback, useEffect, useState } from 'react';
import type { Me } from '../../../api';
import { WeeklyReports } from '../../internship/pages/WeeklyReports';
import { EvaluationResults } from '../components/EvaluationResults';
import { StudentTabs } from '../components/StudentTabs';

// S08 (SRS 14.1) "Báo cáo & đánh giá" gồm 2 tab: báo cáo tuần dùng nguyên màn T3 (F26, API T3 thật);
// kết quả đánh giá chỉ đọc StudentResultDto CP-WS4 (F27), không tự tính hay dựng điểm.
export type StudentReportTab = 'reports' | 'results';
const tabs: { id: StudentReportTab; label: string }[] = [{ id: 'reports', label: 'Báo cáo tuần' }, { id: 'results', label: 'Kết quả đánh giá' }];

export function ReportsResultsPage({ me }: { me: Me }) {
  const [tab, setTab] = useState<StudentReportTab>('reports');
  return <StudentTabs label="Báo cáo và đánh giá" idPrefix="student-report" tabs={tabs} tab={tab} setTab={setTab}
    render={id => id === 'reports' ? <ScopedReports /> : <EvaluationResults userId={me.userId} />} />;
}

// Như bảng việc T3: mất phiên/quyền thì đóng dữ liệu báo cáo, chỉ mở lại khi người dùng xác nhận.
function ScopedReports() {
  const [denied, setDenied] = useState(false), [generation, setGeneration] = useState(0);
  const deny = useCallback(() => setDenied(true), []);
  useEffect(() => { window.addEventListener('session-expired', deny); return () => window.removeEventListener('session-expired', deny); }, [deny]);
  return denied ? <section className="panel"><p role="alert">Phiên hoặc quyền truy cập đã thay đổi. Dữ liệu báo cáo đã được đóng.</p>
    <button type="button" onClick={() => { setDenied(false); setGeneration(previous => previous + 1); }}>Xác nhận lại quyền truy cập</button></section>
    : <WeeklyReports key={generation} student mentor={false} denied={deny} />;
}
