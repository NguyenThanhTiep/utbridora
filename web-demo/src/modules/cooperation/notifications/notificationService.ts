import type { Demand } from '../types';
import type { CooperationRecord, VerificationRecord } from '../WorkflowViews';
import type { EnterpriseProposal, SchoolProposal } from '../proposals/types';
import type { PortalPage, PortalSide } from '../portal/navigation';
import { fieldLabels } from '../workflow/types';

export type NotificationTarget = { page: PortalPage; recordId: string; cooperationId?: string; proposalId?: string };
export type PortalNotification = {
  id: string; category: 'verification' | 'cooperation' | 'proposal' | 'placement'; title: string; description: string;
  related: string; occurredAt: string | null; tone: 'success' | 'warning' | 'danger' | 'info'; target: NotificationTarget;
};
export type NotificationScope = { org: string; side: PortalSide; viewerId: string; schoolOrgId: string; termId: string };
export type ProposalNotificationSource = { cooperation: CooperationRecord; items: (EnterpriseProposal | SchoolProposal)[] };
const timestamp = (value?: string | null) => value && Number.isFinite(Date.parse(value)) ? value : null;
function latest<T extends { version: number }>(rows: T[], key: (row: T) => string) {
  const result = new Map<string, T>();
  for (const row of rows) if (!result.has(key(row)) || result.get(key(row))!.version <= row.version) result.set(key(row), row);
  return [...result.values()];
}

/** Current authorized records, not a server event history or a source of workflow authority. */
export function projectNotifications(scope: NotificationScope, verifications: VerificationRecord[], cooperations: CooperationRecord[],
  demands: Demand[], proposals: ProposalNotificationSource[]): PortalNotification[] {
  const result: PortalNotification[] = [], school = scope.side === 'school';
  const names = new Map(demands.map(demand => [demand.demandId, demand.data.title]));
  const demandSchools = new Map(demands.map(demand => [demand.demandId, demand.data.schoolOrgId]));
  const currentCooperations = latest(cooperations, row => row.cooperationId);
  const add = (kind: string, id: string, version: number, state: string, notification: Omit<PortalNotification, 'id'>) => {
    result.push({ ...notification, id: `${scope.side}:${scope.viewerId}:${scope.org}:${kind}:${id}:${version}:${state}` });
  };
  const inCooperationScope = (row: CooperationRecord) => (!scope.schoolOrgId || (row.schoolOrgId || demandSchools.get(row.demandId)) === scope.schoolOrgId)
    && (!scope.termId || row.termId === scope.termId)
    && (!row.enterpriseOrgId || school || row.enterpriseOrgId === scope.org)
    && (!row.schoolOrgId || !school || row.schoolOrgId === scope.org);
  for (const row of latest(verifications, row => row.verificationId)) {
    if (scope.schoolOrgId && row.data.schoolOrgId !== scope.schoolOrgId || school && row.data.schoolOrgId !== scope.org
      || !school && row.enterpriseOrgId && row.enterpriseOrgId !== scope.org) continue;
    const target: NotificationTarget = { page: 'verifications', recordId: row.verificationId };
    if (school && row.state === 'SUBMITTED') add('VER', row.verificationId, row.version, row.state, {
      category: 'verification', title: `Doanh nghiệp ${row.data.registeredName} vừa gửi hồ sơ xác minh mới cần duyệt`,
      description: 'Kiểm tra hồ sơ và phản hồi cho doanh nghiệp.', related: row.data.registeredName,
      occurredAt: timestamp(row.submittedAt), tone: 'warning', target,
    });
    if (!school && row.state === 'NEEDS_CHANGES') add('VER', row.verificationId, row.version, row.state, {
      category: 'verification', title: 'Nhà trường yêu cầu bổ sung hồ sơ BM-03',
      description: row.missingFields.length ? 'Cần bổ sung: ' + row.missingFields.map(field => fieldLabels[field] || field).join(', ') : 'Xem phản hồi trong hồ sơ xác minh.',
      related: row.data.registeredName, occurredAt: timestamp(row.decidedAt), tone: 'warning', target,
    });
    if (!school && row.state === 'APPROVED') add('VER', row.verificationId, row.version, row.state, {
      category: 'verification', title: 'Hồ sơ xác minh BM-03 đã được nhà trường phê duyệt', description: 'Kết quả áp dụng tại trường nhận hồ sơ.',
      related: row.data.registeredName, occurredAt: timestamp(row.decidedAt), tone: 'success', target,
    });
  }
  for (const row of currentCooperations.filter(inCooperationScope)) {
    const related = names.get(row.demandId) || 'Đề nghị hợp tác · ' + row.cooperationId.slice(0, 8);
    const target: NotificationTarget = { page: 'cooperations', recordId: row.cooperationId, cooperationId: row.cooperationId };
    if (school && row.state === 'SUBMITTED') add('COOP', row.cooperationId, row.version, row.state, {
      category: 'cooperation', title: 'Doanh nghiệp vừa gửi đề nghị hợp tác mới cho kỳ thực tập', description: 'Đề nghị đang chờ nhà trường xét duyệt.',
      related, occurredAt: timestamp(row.submittedAt), tone: 'warning', target,
    });
    if (!school && ['APPROVED', 'REJECTED'].includes(row.state)) add('COOP', row.cooperationId, row.version, row.state, {
      category: 'cooperation', title: row.state === 'APPROVED' ? 'Đề nghị hợp tác BM-02 đã được duyệt' : 'Đề nghị hợp tác BM-02 bị từ chối',
      description: row.state === 'APPROVED' ? 'Nhà trường có thể tiếp tục đề xuất sinh viên.' : 'Xem phản hồi trước khi mở lại đề nghị.',
      related, occurredAt: null, tone: row.state === 'APPROVED' ? 'success' : 'danger', target,
    });
  }
  for (const group of proposals) {
    const current = currentCooperations.find(row => row.cooperationId === group.cooperation.cooperationId);
    if (!current || !inCooperationScope(current) || current.state !== 'APPROVED') continue;
    for (const row of latest(group.items, row => row.proposalItemId)) {
      if (row.cooperationId !== group.cooperation.cooperationId) continue;
      const related = names.get(group.cooperation.demandId) || 'Hợp tác · ' + row.cooperationId.slice(0, 8);
      if (!school && row.state === 'SHARED') add('PROP', row.proposalItemId, row.version, row.state + ':' + row.shareId, {
        category: 'proposal', title: 'Nhà trường đã chia sẻ hồ sơ ứng viên đủ điều kiện', description: 'Xem hồ sơ được chia sẻ và phản hồi tiếp nhận.',
        related, occurredAt: null, tone: 'info', target: { page: 'proposals', recordId: row.proposalItemId, cooperationId: row.cooperationId, proposalId: row.proposalItemId },
      });
      if (school && row.state === 'ENTERPRISE_ACCEPTED' && 'placementId' in row && !row.placementId) add('PLC', row.proposalItemId, row.version, row.state, {
        category: 'placement', title: 'Doanh nghiệp đã chấp thuận tiếp nhận sinh viên', description: 'Kiểm tra điều kiện để lập hồ sơ tiếp nhận thực tập.',
        related, occurredAt: null, tone: 'success', target: { page: 'placements', recordId: row.proposalItemId, cooperationId: row.cooperationId, proposalId: row.proposalItemId },
      });
    }
  }
  return [...new Map(result.map(row => [row.id, row])).values()].sort((a, b) =>
    (b.occurredAt ? Date.parse(b.occurredAt) : 0) - (a.occurredAt ? Date.parse(a.occurredAt) : 0) || a.id.localeCompare(b.id));
}
export const readStorageKey = (org: string) => `utbridora_read_notifs_${org}`;
export function storedReadIds(org: string): string[] {
  try { const value: unknown = JSON.parse(localStorage.getItem(readStorageKey(org)) || '[]');
    return Array.isArray(value) ? value.filter((id): id is string => typeof id === 'string') : [];
  } catch { return []; }
}
export function saveReadIds(org: string, ids: string[]) {
  const merged = [...new Set([...storedReadIds(org), ...ids])];
  try { localStorage.setItem(readStorageKey(org), JSON.stringify(merged)); } catch { /* Read state still works in memory when storage is unavailable. */ }
  return merged;
}
