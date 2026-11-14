import { useState } from 'react';
import { Icon } from '../../../components';
import { canLeaveDemand } from '../navigation';
import '../portal/features.css';
import { SchoolPlacementFollowUp } from './SchoolPlacementFollowUp';

export function WorkspaceHandoff({ link, placementId, effectiveAt, locked, schoolOrgId, termId, placementState }: {
  link: { state: 'WAITING_HANDOFF' | 'AVAILABLE'; route: string | null; caseId?: string | null }; placementId: string; effectiveAt: string; locked: boolean;
  schoolOrgId?: string; termId?: string; placementState?: string;
}) {
  const [expanded, setExpanded] = useState(false);
  const caseId = link.caseId ?? link.route?.split('/').pop();
  const available = link.state === 'AVAILABLE' && !!link.route && /^\/workspace\/cases\/[0-9a-f-]{36}$/i.test(link.route)
    && link.route === `/workspace/cases/${caseId}`;
  return <div className="portal-handoff">
    {!available && <p className="handoff-waiting">Đang chờ bàn giao sang không gian thực tập.</p>}
    {available && <p className="portal-handoff-ready"><Icon name="check-circle" size={18} />Không gian thực tập đã sẵn sàng.</p>}
    <div className="button-row"><button type="button" className="secondary" aria-expanded={expanded} onClick={() => setExpanded(value => !value)}>Chi tiết bàn giao</button>
      {available && <a href={link.route!} aria-disabled={locked} onClick={event => { if (locked || !canLeaveDemand()) event.preventDefault(); }}>Mở hồ sơ thực tập</a>}
    </div>
    {expanded && <section className="portal-handoff-details" aria-label="Chi tiết bàn giao không gian thực tập">
      <h4>Bàn giao sang không gian thực tập</h4>
      <dl className="portal-key-values"><div><dt>Nguồn</dt><dd>Hồ sơ tiếp nhận thực tập</dd></div>
        <div><dt>Điểm đến</dt><dd>Không gian thực tập</dd></div>
        <div><dt>Trạng thái</dt><dd>{available ? 'Đã bàn giao' : 'Đang chờ bàn giao'}</dd></div>
        <div><dt>Hiệu lực từ</dt><dd>{new Date(effectiveAt).toLocaleString('vi-VN')}</dd></div>
        <div><dt>Mã tiếp nhận</dt><dd><code>{placementId}</code></dd></div>
      </dl><p>Quyền giao việc và nộp bài được kiểm tra khi mở hồ sơ thực tập.</p>
      {available && schoolOrgId && termId && caseId && <SchoolPlacementFollowUp org={schoolOrgId} termId={termId} caseId={caseId} placementId={placementId} placementState={placementState} />}
    </section>}
  </div>;
}
