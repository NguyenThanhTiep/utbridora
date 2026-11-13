import { useEffect, useRef } from 'react';
import { Notice } from '../../../components';
import type { SchoolProposal } from './types';
import { useWorkflowCommand } from './useWorkflowCommand';
import { workflowError } from './api';

export function InvitationAction({ org, item, locked, changed, pendingChanged }: {
  org: string; item: SchoolProposal; locked: boolean; changed: (notice: string) => void; pendingChanged: (value: boolean) => void;
}) {
  const pendingRef = useRef(pendingChanged); pendingRef.current = pendingChanged;
  const command = useWorkflowCommand(org, () => changed('Đã gửi lời mời. Sinh viên cần tự đăng nhập để nhận lời và quyết định chia sẻ hồ sơ.'), error => changed(workflowError(error)));
  useEffect(() => { pendingRef.current(command.blocked); return () => pendingRef.current(false); }, [command.blocked]);
  if (item.state !== 'RESERVED' || item.invitationId) return null;
  const expired = Date.now() >= Date.parse(item.reservationUntil);
  return <div><Notice message={command.message} tone="error" />
    <button className="secondary" disabled={locked || command.blocked || expired} onClick={() => {
      if (window.confirm('Gửi lời mời thực tập cho sinh viên này? Sinh viên sẽ tự quyết định nhận hoặc từ chối.'))
        void command.submit(`/school/proposals/${item.proposalItemId}/invite`, 'POST', { expectedVersion: item.version, expiresAt: null });
    }}>Gửi lời mời thực tập</button>{expired && <p>Đã hết thời gian giữ chỗ.</p>}
    {command.retryable && <button disabled={command.busy} onClick={() => void command.retry()}>Thử lại lần gửi lời mời</button>}
  </div>;
}
