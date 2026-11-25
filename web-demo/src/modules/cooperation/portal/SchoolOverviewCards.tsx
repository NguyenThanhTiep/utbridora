import { useEffect, useId, useState } from 'react';
import { Icon } from '../../../components';
import { formatDateDMY } from '../../../ui/dateFormat';
import type { CooperationRecord, VerificationRecord } from '../WorkflowViews';
import { readAll, type Resource } from './data';
import type { PortalPage } from './navigation';
import './school-overview-cards.css';

type Placement = { studentUserId: string; schoolOrgId: string; termId: string; state: string };
type Term = { termId: string; schoolOrgId: string; termCode: string; startDate?: string; endDate?: string };
type ReferenceData = { key: string; students: number | null; terms: Term[] | null; loadingStudents: boolean; loadingTerms: boolean };

function useOverviewReferences(organizationId: string, revision: number, termId: string) {
  const key = organizationId + ':' + revision + ':' + termId;
  const [data, setData] = useState<ReferenceData | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    setData({ key, students: null, terms: null, loadingStudents: true, loadingTerms: true });
    const placementQuery = new URLSearchParams({ state: 'ACTIVE', ...(termId ? { termId } : {}) });
    void readAll<Placement>(organizationId, '/school/placements?' + placementQuery, controller.signal).then(rows => {
      if (rows.some(row => row.schoolOrgId !== organizationId || row.state !== 'ACTIVE' || !row.studentUserId || (termId && row.termId !== termId))) throw new Error('Invalid placement scope');
      if (!controller.signal.aborted) setData(old => old?.key === key ? { ...old, students: new Set(rows.map(row => row.studentUserId)).size } : old);
    }).catch(() => { /* The card remains unavailable; a denied request never becomes a zero. */ })
      .finally(() => { if (!controller.signal.aborted) setData(old => old?.key === key ? { ...old, loadingStudents: false } : old); });
    void readAll<Term>(organizationId, '/terms?' + new URLSearchParams({ schoolOrgId: organizationId }), controller.signal).then(rows => {
      if (rows.some(row => row.schoolOrgId !== organizationId || !row.termId || !row.termCode)) throw new Error('Invalid term scope');
      if (!controller.signal.aborted) setData(old => old?.key === key ? { ...old, terms: [...new Map(rows.filter(row => !termId || row.termId === termId).map(row => [row.termId, row])).values()] } : old);
    }).catch(() => { /* Keep the other cards usable when the term service is unavailable. */ })
      .finally(() => { if (!controller.signal.aborted) setData(old => old?.key === key ? { ...old, loadingTerms: false } : old); });
    return () => controller.abort();
  }, [key, organizationId, termId]);
  return data?.key === key ? data : { key, students: null, terms: null, loadingStudents: true, loadingTerms: true };
}

export function SchoolOverviewCards({ organizationId, revision, cooperations, verifications, navigate, termId = '' }: {
  organizationId: string; revision: number; cooperations: Resource<CooperationRecord>; verifications: Resource<VerificationRecord>; termId?: string;
  navigate: (page: PortalPage) => void;
}) {
  const references = useOverviewReferences(organizationId, revision, termId);
  const [showTerms, setShowTerms] = useState(false);
  const termsId = useId();
  const approved = cooperations.items.filter(row => row.state === 'APPROVED');
  const partners = cooperations.loading || cooperations.error
    || approved.some(row => row.schoolOrgId !== organizationId || !row.enterpriseOrgId)
    ? null : new Set(approved.map(row => row.enterpriseOrgId)).size;
  const pending = verifications.loading || verifications.error
    ? null : verifications.items.filter(row => row.state === 'SUBMITTED').length;
  const cards = [
    { title: 'Sinh viên thực tập', value: references.students, badge: 'SV', icon: 'graduation', tone: 'mint', description: 'Đang thực tập',
      loading: references.loadingStudents, open: () => navigate('placements') },
    { title: 'Doanh nghiệp đối tác', value: partners, badge: 'DN', icon: 'building', tone: 'blue', description: 'Đã duyệt hợp tác',
      loading: cooperations.loading, open: () => navigate('cooperations') },
    { title: 'Đợt thực tập', value: references.terms?.length ?? null, badge: 'ĐT', icon: 'calendar', tone: 'violet', description: 'Kỳ của nhà trường',
      loading: references.loadingTerms, open: () => setShowTerms(value => !value) },
    { title: 'Hồ sơ chờ duyệt', value: pending, badge: 'HS', icon: 'shield', tone: 'warm', description: termId ? 'Chờ xác minh của toàn trường' : 'Chờ xác minh',
      loading: verifications.loading, open: () => navigate('verifications') },
  ];
  return <>
    <div className="portal-stat-grid school-overview-grid" aria-label="Tình hình tổng quan nhà trường">
      {cards.map((card, index) => <button key={card.badge} type="button" className={'portal-stat school-overview-stat tone-' + card.tone}
        aria-label={`${card.title}: ${card.value ?? 'Chưa có số liệu'}`} aria-busy={card.loading}
        aria-expanded={card.badge === 'ĐT' ? showTerms : undefined} aria-controls={card.badge === 'ĐT' ? termsId : undefined}
        disabled={card.badge === 'ĐT' && references.terms === null} onClick={card.open}>
        <span className="portal-stat-title">
          <span>{card.title}</span>
          <span className="school-stat-meta-pills">
            <span className="school-stat-badge" aria-hidden="true">{card.badge}</span>
            <span className="school-stat-order" aria-hidden="true">{String(index + 1).padStart(2, '0')}</span>
          </span>
        </span>
        <span className="school-stat-metric">
          <strong>{card.value ?? '—'}</strong>
          <span className="portal-stat-icon school-stat-icon-tile" aria-hidden="true">
            <Icon name={card.icon} size={20} />
          </span>
        </span>
        <span className="school-stat-description">{card.loading ? 'Đang tải dữ liệu…' : card.value === null ? 'Chưa tải được số liệu' : card.description}</span>
        <span className="school-stat-link">Xem chi tiết <Icon name="arrow-right" size={13} /></span>
      </button>)}
    </div>
    {showTerms && references.terms && <section className="panel school-term-details" id={termsId} aria-label="Danh sách đợt thực tập">
      <div className="section-heading"><h2>Đợt thực tập của nhà trường</h2><button type="button" className="secondary" onClick={() => setShowTerms(false)}>Đóng danh sách đợt</button></div>
      {references.terms.length ? <div className="proposal-table"><table><thead><tr><th>Kỳ thực tập</th><th>Ngày bắt đầu</th><th>Ngày kết thúc</th></tr></thead>
        <tbody>{references.terms.map(term => <tr key={term.termId}><th scope="row">{term.termCode}</th><td>{formatDateDMY(term.startDate)}</td><td>{formatDateDMY(term.endDate)}</td></tr>)}</tbody>
      </table></div> : <p>Chưa có kỳ thực tập trong phạm vi nhà trường.</p>}
    </section>}
  </>;
}
