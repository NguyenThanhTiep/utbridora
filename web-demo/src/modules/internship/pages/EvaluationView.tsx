import type { Evaluation } from '../evaluation';
import { evaluationStateLabel } from '../displayLabels';
import { ReportEvidence } from './ReportEvidence';

export function EvaluationView({ value, contextId, denied, student = false }: { value: Evaluation; contextId?: string; denied: () => void; student?: boolean }) {
  return <section className="panel stack" aria-label="Nội dung đánh giá">
    {student ? <><p>Trạng thái: {value.state} · Revision {value.revisionNo} · Version {value.version}</p>
      <p>Phiên bản rubric: {value.template.templateVersion}</p></> : <>
      <p><span className={'t3-grade-status state-' + value.state}>{evaluationStateLabel(value.state)}</span> · Lần đánh giá {value.revisionNo} · Mẫu rubric v{value.template.templateVersion}</p>
      <p className="t3-tech-meta">Trạng thái: {value.state} · Revision {value.revisionNo} · Version {value.version}</p></>}
    <h3>Điểm tổng: {value.total100.toFixed(2)} / 100</h3>
    {value.publishedAt && <p>Công bố: {new Date(value.publishedAt).toLocaleString('vi-VN')} · Căn cứ: {value.decisionRef}</p>}
    {value.template.criteria.map(c => { const s=value.scores.find(v => v.criterionCode === c.criterionCode); return <article key={c.criterionCode}>
      <h4>{c.label} · {c.weight}%</h4><p>Điểm: {s?.score} / 5</p><p className="t3-preserve-text">{s?.comment || 'Không có nhận xét'}</p>
      {!!s?.evidenceFileIds.length && <ReportEvidence evaluation reportId={value.evaluationId} fileIds={s.evidenceFileIds}
        contextId={contextId} editable={false} disabled={false} changed={() => {}} denied={denied} />}
    </article>; })}
  </section>;
}
