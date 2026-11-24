const uuidToken = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const autoGroupCode = /^G-([0-9a-f]{6,8})-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const CASE_STATES: Record<string, string> = {
  WAITING_START: 'Chờ bắt đầu',
  IN_PROGRESS: 'Đang thực tập',
  COMPLETED: 'Hoàn thành',
  CANCELLED: 'Đã hủy',
  TERMINATED: 'Đã dừng',
};

const ISSUE_CATEGORIES: Record<string, string> = {
  DELAY: 'Chậm tiến độ',
  COMMUNICATION: 'Trao đổi & phối hợp',
  OTHER: 'Vấn đề khác',
};

const ISSUE_STATES: Record<string, string> = {
  OPEN: 'Mới mở',
  IN_PROGRESS: 'Đang xử lý',
  RESOLVED: 'Đã giải quyết',
};

const ASSIGNMENT_STATES: Record<string, string> = {
  NEW: 'Mới giao',
  IN_PROGRESS: 'Đang thực hiện',
  SUBMITTED: 'Đã nộp',
  REVISION_REQUIRED: 'Cần bổ sung',
  COMPLETED: 'Hoàn thành',
};

const INVITATION_STATES: Record<string, string> = {
  MEMBERSHIP_PENDING: 'Chờ chấp thuận',
  ACCEPTED: 'Đã chấp thuận',
  REVIEW_REQUIRED: 'Cần rà soát',
  REVOKED: 'Đã thu hồi',
};

const EVALUATION_STATES: Record<string, string> = {
  DRAFT: 'Bản nháp',
  SUBMITTED: 'Chờ công bố',
  PUBLISHED: 'Đã công bố',
};

const ROLE_KINDS: Record<string, string> = {
  HR: 'Nhân sự HR',
  MENTOR: 'Người hướng dẫn',
  COORDINATOR: 'Điều phối viên',
};

/** Shortens raw UUID or auto-generated `G-<uuid>` codes for user-facing headings while preserving readable codes. */
export function formatGroupCode(code: string): string {
  const auto = autoGroupCode.exec(code.trim());
  if (auto) return `G-${auto[1].slice(0, 6).toUpperCase()}`;
  if (uuidToken.test(code.trim())) return `#${code.trim().slice(-6).toUpperCase()}`;
  return code;
}

export function formatEntityShort(id: string, prefix = '#'): string {
  const clean = id.trim();
  if (!clean) return '—';
  return `${prefix}${clean.slice(-6).toUpperCase()}`;
}

export function formatUserLabel(userId: string, currentUserId?: string, roleHint = 'Thành viên'): string {
  const clean = userId.trim();
  if (!clean) return roleHint;
  if (currentUserId && clean.toLowerCase() === currentUserId.toLowerCase()) return 'Bạn';
  return `${roleHint} #${clean.slice(-6).toUpperCase()}`;
}

export function roleKindLabel(kind: string): string {
  return ROLE_KINDS[kind] || kind;
}

export function caseStateLabel(state: string): string {
  return CASE_STATES[state] || state;
}

export function issueCategoryLabel(category: string): string {
  return ISSUE_CATEGORIES[category] || category;
}

export function issueStateLabel(state: string): string {
  return ISSUE_STATES[state] || state;
}

export function assignmentStateLabel(state: string): string {
  return ASSIGNMENT_STATES[state] || state;
}

export function invitationStateLabel(state: string): string {
  return INVITATION_STATES[state] || state;
}

export function evaluationStateLabel(state: string): string {
  return EVALUATION_STATES[state] || state;
}
