import { useEffect, useRef, useState } from 'react';
import { Notice } from '../../../components';
import type { SchoolProposal } from './types';
import { useWorkflowCommand } from './useWorkflowCommand';
import { workflowError } from './api';
import { profileFieldLabels } from './ProfileFieldLabels';

function localDate(value: number) {
  const date = new Date(value);
  return new Date(value - date.getTimezoneOffset() * 60000).toISOString().slice(0, 19);
}
export function ShareEditor({ org, item, changed, pendingChanged, locked }: {
  org: string; item: SchoolProposal; changed: (notice: string) => void; pendingChanged: (pending: boolean) => void; locked: boolean;
}) {
  const [open, setOpen] = useState(false), [selected, setSelected] = useState<string[]>(item.shareableFields || []), [error, setError] = useState('');
  const [revoking, setRevoking] = useState(false), [reason, setReason] = useState('');
  const limit = Math.min(Date.parse(item.activeConsentExpiresAt || ''), Date.parse(item.reservationUntil));
  const [expiresAt, setExpiresAt] = useState(Number.isFinite(limit) ? localDate(limit) : '');
  const changedRef = useRef(changed), pendingRef = useRef(pendingChanged); changedRef.current = changed; pendingRef.current = pendingChanged;
  const command = useWorkflowCommand(org, () => { setOpen(false); changedRef.current(revoking ? 'Đã thu hồi quyền doanh nghiệp xem bản chia sẻ này.' : 'Đã cấp chia sẻ hồ sơ theo quyền sinh viên đồng ý.'); }, error => changedRef.current(workflowError(error)));
  useEffect(() => { pendingRef.current(command.blocked); return () => pendingRef.current(false); }, [command.blocked]);
  const ready = !!item.activeConsentId && !!item.activeConsentVersion && !!item.activeConsentExpiresAt
    && !!item.shareableFields?.length && !!item.invitationId && Number.isFinite(limit) && Date.now() < limit;
  const canShare = ready && (item.state === 'ACCEPTED' && !item.shareId || ['SHARED', 'ENTERPRISE_ACCEPTED'].includes(item.state) && item.shareNeedsUpdate);
  return <div className="proposal-share-editor">
    <p>{item.shareIsActive ? 'Đã cấp chia sẻ' : item.shareId ? 'Chia sẻ cũ không còn hiệu lực' : 'Chưa chia sẻ'}</p>
    {!item.activeConsentId && ['ACCEPTED', 'SHARED', 'ENTERPRISE_ACCEPTED'].includes(item.state) && <p>Chờ sinh viên cấp quyền chia sẻ còn hiệu lực.</p>}
    {canShare && item.state === 'ENTERPRISE_ACCEPTED' && <p>Doanh nghiệp đã đồng ý tiếp nhận. Cập nhật chia sẻ vẫn giữ nguyên quyết định này.</p>}
    <Notice message={error || command.message} tone="error" />
    {canShare && !open && <button className="secondary" disabled={locked} onClick={() => setOpen(true)}>{item.shareId ? 'Cập nhật chia sẻ' : 'Cấp chia sẻ'}</button>}
    {item.shareIsActive && item.shareId && item.shareVersion && !revoking && <button className="secondary" disabled={locked || command.blocked} onClick={() => setRevoking(true)}>Thu hồi chia sẻ hồ sơ</button>}
    {revoking && <form onSubmit={event => {
      event.preventDefault(); if (!item.shareId || !item.shareVersion || !reason) return;
      void command.submit(`/school/profile-shares/${item.shareId}/revoke`, 'POST', { expectedVersion: item.shareVersion, reasonCode: reason });
    }}><fieldset disabled={locked || command.blocked}><legend>Thu hồi quyền xem hồ sơ của doanh nghiệp</legend>
      <p>Thao tác này thu hồi bản chia sẻ hiện tại, vẫn giữ trạng thái lời mời và quyết định tiếp nhận.</p>
      <label>Lý do thu hồi<select required value={reason} onChange={event => setReason(event.target.value)}><option value="">Chọn lý do</option>
        <option value="SCHOOL_REVIEW_REQUIRED">Cần rà soát lại hồ sơ</option><option value="SHARING_SCOPE_CHANGED">Điều chỉnh phạm vi chia sẻ</option><option value="SCHOOL_REVOKED">Thu hồi theo quyết định nhà trường</option>
      </select></label><button disabled={!reason}>Xác nhận thu hồi chia sẻ</button><button type="button" className="secondary" onClick={() => setRevoking(false)}>Hủy thu hồi</button>
    </fieldset></form>}
    {open && <form onSubmit={event => {
      event.preventDefault();
      const expiry = Date.parse(expiresAt);
      if (!selected.length || !Number.isFinite(expiry) || expiry <= Date.now() || expiry > limit) {
        setError('Chọn thông tin được phép chia sẻ và thời hạn còn hiệu lực, không vượt quá consent hoặc thời gian giữ chỗ.'); return;
      }
      setError(''); void command.submit(`/school/proposals/${item.proposalItemId}/shares`, 'POST', {
        proposalItemId: item.proposalItemId, consentId: item.activeConsentId, expectedConsentVersion: item.activeConsentVersion,
        expectedProposalVersion: item.version, fieldWhitelist: selected, expiresAt: new Date(expiry).toISOString(),
      });
    }}><fieldset disabled={locked || command.blocked || !canShare}><legend>Thông tin cấp cho doanh nghiệp</legend>
      <p>Sinh viên đã đồng ý các mục dưới đây. Nhà trường chọn phạm vi chia sẻ cho doanh nghiệp.</p>
      {item.shareableFields.map(field => <label key={field}><input type="checkbox" checked={selected.includes(field)}
        onChange={event => setSelected(event.target.checked ? [...selected, field] : selected.filter(value => value !== field))} />{profileFieldLabels[field] || field}</label>)}
      <label>Hiệu lực chia sẻ đến<input required type="datetime-local" step="1" max={localDate(limit)} value={expiresAt} onChange={event => setExpiresAt(event.target.value)} /></label>
      <div className="button-row"><button disabled={!selected.length}>Xác nhận cấp chia sẻ</button><button type="button" className="secondary" onClick={() => setOpen(false)}>Hủy cấp chia sẻ</button></div>
    </fieldset></form>}
    {command.retryable && <button disabled={command.busy} onClick={() => void command.retry()}>Thử lại lần cấp chia sẻ đã gửi</button>}
  </div>;
}
