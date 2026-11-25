import { useEffect, useId, useState } from 'react';
import { Icon, Notice } from '../../../components';
import { proposalStates, type EnterpriseProposal } from './types';
import { SharedProfileView } from './SharedProfileView';
import { useWorkflowCommand } from './useWorkflowCommand';
import { useSharedProfile } from './useSharedProfile';
import { useCandidateVisibility } from './useCandidateVisibility';

export function EnterpriseCandidateCard({ org, item, number, changed, locked, markPending, expanded, onToggle }: {
  org: string; item: EnterpriseProposal; number: number; changed: (notice: string) => void;
  locked: boolean; markPending: (id: string, active: boolean) => void;
  expanded: boolean; onToggle: () => void;
}) {
  const { cardRef, enabled } = useCandidateVisibility(expanded);
  const { profile, error, clear } = useSharedProfile(org, item, enabled);
  const [declining, setDeclining] = useState(false), [reason, setReason] = useState('');
  const [expired, setExpired] = useState(Date.now() >= Date.parse(item.reservationUntil));
  const [unavailable, setUnavailable] = useState(false), [fileError, setFileError] = useState('');
  const reasonId = useId();
  const detailsId = useId();
  // Header identity must disappear with the same permission/expiry gate as the profile body.
  const name = profile?.allowedFields.includes('displayName') ? profile.data.displayName?.trim() : '';
  const identityNote = name ? 'Hồ sơ sinh viên được chia sẻ'
    : profile ? profile.allowedFields.includes('displayName') ? 'Hồ sơ chưa có họ tên sinh viên.' : 'Họ tên chưa được cấp quyền chia sẻ.'
    : 'Thông tin theo phạm vi sinh viên đồng ý chia sẻ';
  const initials = name ? name.split(/\s+/).slice(-2).map(part => part[0]).join('').toLocaleUpperCase('vi') : '';
  const command = useWorkflowCommand<EnterpriseProposal>(org,
    value => changed(value.state === 'ENTERPRISE_ACCEPTED' ? 'Đã ghi nhận đồng ý tiếp nhận. Nhà trường sẽ thực hiện bước chốt thực tập.' : 'Đã ghi nhận từ chối ứng viên.'),
    () => { clear();setUnavailable(true); });
  useEffect(() => {
    markPending(item.proposalItemId, command.blocked);
    return () => markPending(item.proposalItemId, false);
  }, [item.proposalItemId, command.blocked, markPending]);
  useEffect(() => {
    let timer: number | undefined;
    const update = () => {
      const remaining = Date.parse(item.reservationUntil) - Date.now();
      setExpired(!Number.isFinite(remaining) || remaining <= 0);
      if (remaining > 0) timer = window.setTimeout(update, Math.min(remaining, 2_147_483_647));
    };
    update();
    return () => window.clearTimeout(timer);
  }, [org, item.proposalItemId, item.reservationUntil]);
  const decide = (decision: 'ACCEPT' | 'DECLINE') => command.submit(`/enterprise/proposals/${item.proposalItemId}/decision`, 'PATCH',
    { expectedVersion: item.version, decision, ...(decision === 'DECLINE' ? { reasonCode: reason } : {}) });

  return <article ref={cardRef} className={'proposal-card enterprise-candidate-card candidate-state-' + item.state.toLowerCase() + (expanded ? ' is-expanded' : ' is-collapsed')}>
    <header className="cooperation-demand-title candidate-card-header" onClick={event => {
      if (locked || command.blocked || window.getSelection()?.toString().trim()) return;
      if (event.target instanceof Element && event.target.closest('button, a, input, select, textarea, label')) return;
      onToggle();
    }}>
      <div className="candidate-identity"><span className="candidate-avatar" aria-hidden="true">{initials || <Icon name="user" size={24} />}</span>
        <div><h4>{name || `Ứng viên ${number}`}</h4>{(expanded || !name) && <p>{identityNote}</p>}</div>
      </div>
      <span className={`cooperation-state candidate-state-badge state-${item.state.toLowerCase()}`}>
        <span aria-hidden="true" className="candidate-state-dot" />{item.state === 'DECLINED' ? 'Doanh nghiệp đã từ chối' : proposalStates[item.state] || item.state}
      </span>
      <button type="button" className="secondary candidate-details-toggle" aria-expanded={expanded} aria-controls={detailsId}
        disabled={locked || command.blocked} onClick={onToggle}>{expanded ? 'Thu gọn hồ sơ' : 'Xem hồ sơ'}<Icon name="chevron-down" size={16} /></button>
    </header>
    <div id={detailsId} className="candidate-expanded-content" hidden={!expanded}>{expanded && <>
    <Notice message={fileError || error || command.message} tone="error" />
    {profile && <SharedProfileView profile={profile} hideDisplayName={!!name} files={{ org, proposalItemId: item.proposalItemId,
      denied: message => {setFileError(message);clear();setUnavailable(true);} }} />}
    {!profile && !error && !unavailable && <p className="candidate-profile-loading" role="status"><span className="spinner" />Đang tải hồ sơ được chia sẻ…</p>}
    {unavailable && <button type="button" className="secondary" disabled={locked} onClick={() => changed('Đã yêu cầu tải lại trạng thái ứng viên.')}><Icon name="refresh" size={16} />Kiểm tra lại ứng viên</button>}
    {command.retryable && <div className="candidate-retry"><p>Chưa xác định được kết quả. Thử lại để kiểm tra quyết định đã gửi.</p>
      <button type="button" disabled={command.busy} onClick={() => void command.retry()}>Thử lại quyết định đã gửi</button>
    </div>}
    {item.state === 'SHARED' && !unavailable && <footer className="candidate-decision-footer">
      <div className={`reservation-box ${expired ? 'expired' : 'active'}`}><Icon name="clock" size={17} />
        <p>{expired ? 'Đã hết thời gian giữ chỗ.' : `Giữ chỗ đến ${new Date(item.reservationUntil).toLocaleString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' })}`}</p>
      </div>
      <fieldset disabled={locked || command.blocked || expired || !profile}>
        {declining ? <form className="candidate-decline-form" onSubmit={event => {event.preventDefault();if (reason) void decide('DECLINE');}}>
          <div><label htmlFor={reasonId}>Lý do từ chối</label><select id={reasonId} required value={reason} onChange={event => setReason(event.target.value)}>
            <option value="">Chọn lý do</option><option value="SKILL_MISMATCH">Kỹ năng chưa phù hợp</option>
            <option value="SCHEDULE_CONFLICT">Không phù hợp lịch thực tập</option><option value="OTHER">Lý do khác</option>
          </select></div><div className="button-row"><button className="candidate-confirm-decline" disabled={!reason}>Xác nhận từ chối</button>
            <button type="button" className="secondary" onClick={() => setDeclining(false)}>Hủy</button></div>
        </form> : <div className="button-row"><button type="button" className="btn-enterprise-accept" onClick={() => void decide('ACCEPT')}><Icon name="check" size={17} />Tiếp nhận thực tập</button>
          <button type="button" className="secondary btn-enterprise-decline" onClick={() => setDeclining(true)}>Từ chối</button></div>}
      </fieldset>
    </footer>}
    {item.state === 'ENTERPRISE_ACCEPTED' && <p className="candidate-next-step"><Icon name="check-circle" size={18} />Đã đồng ý tiếp nhận. Nhà trường sẽ thực hiện bước chốt thực tập.</p>}
    </>}</div>
  </article>;
}
