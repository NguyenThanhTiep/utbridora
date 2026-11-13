import { useEffect, useState } from 'react';
import { ApiError, type Grant } from '../../api';
import { listTerms } from './api';
import type { PreferenceStatus, PreferenceView, TermReference } from './types';

export const preferenceLabels: Record<PreferenceStatus, string> = { SUBMITTED: 'Đã gửi, chờ nhà trường xét', WITHDRAWN: 'Đã rút', REVIEWED: 'Nhà trường đã xét' };
// Mã lỗi theo BC22 và ledger dùng chung; không hiện nguyên văn message của server.
const errorMessages: Record<string, string> = {
  OPPORTUNITY_CLOSED: 'Cơ hội đã hết hạn nhận nguyện vọng.',
  PREFERENCE_REVIEWED: 'Nguyện vọng đã được nhà trường xét nên không thể rút. Muốn đổi ý, hãy gửi nguyện vọng mới.',
  VERSION_CONFLICT: 'Nguyện vọng vừa thay đổi; đã tải lại bản mới nhất, lời nhắn của bạn vẫn được giữ.',
  STATE_CONFLICT: 'Nguyện vọng đang ở trạng thái khác; đã tải lại. Muốn gửi lại, hãy rút nguyện vọng đang chờ xét trước.',
  RESOURCE_NOT_FOUND: 'Cơ hội hoặc nguyện vọng không còn hiển thị với bạn.',
  DEPENDENCY_UNAVAILABLE: 'Dịch vụ cơ hội thực tập chưa sẵn sàng. Vui lòng thử lại sau.',
  REQUEST_IN_PROGRESS: 'Yêu cầu trước đang được xử lý. Đợi giây lát rồi tải lại.'
};
// Các lỗi này nghĩa là dữ liệu trên màn đã cũ: tải lại nhưng giữ nội dung đang nhập.
export const STALE_PREFERENCE_CODES = new Set(['OPPORTUNITY_CLOSED', 'PREFERENCE_REVIEWED', 'VERSION_CONFLICT', 'STATE_CONFLICT']);
export const preferenceError = (error: unknown) => !(error instanceof ApiError) ? (error instanceof Error ? error.message : 'Thao tác chưa hoàn tất.')
  : (error.errorCode && errorMessages[error.errorCode]) || error.message + (error.requestId ? ' Mã yêu cầu: ' + error.requestId + '.' : '');
export const isStale = (error: unknown) => error instanceof ApiError && !!error.errorCode && STALE_PREFERENCE_CODES.has(error.errorCode);
export const dateTime = (value: string) => new Date(value).toLocaleString('vi-VN');
export const date = (value: string) => new Date(value + 'T00:00:00').toLocaleDateString('vi-VN');

// Mỗi sinh viên/cơ hội/kỳ có một intent hiện hành: intentVersion lớn nhất; các bản trước là lịch sử.
export type PreferenceGroup = { current: PreferenceView; history: PreferenceView[] };
export function groupPreferences(preferences: PreferenceView[]): PreferenceGroup[] {
  const groups = new Map<string, PreferenceView[]>();
  for (const item of preferences) groups.set(item.termId + ':' + item.opportunityId, [...(groups.get(item.termId + ':' + item.opportunityId) ?? []), item]);
  return [...groups.values()].map(items => {
    const [current, ...history] = [...items].sort((a, b) => b.intentVersion - a.intentVersion || b.version - a.version);
    return { current, history };
  }).sort((a, b) => b.current.createdAt.localeCompare(a.current.createdAt));
}

// Học kỳ ACTIVE của các trường sinh viên đang thuộc (BC21, T5 sở hữu); chỉ một kỳ thì tự chọn.
export function useStudentTerm(schools: Grant[]) {
  const [terms, setTerms] = useState<TermReference[] | null>(null), [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0), [termId, setTermId] = useState('');
  const schoolIds = schools.map(school => school.organizationId).filter((id): id is string => !!id).join(',');
  useEffect(() => {
    let active = true;
    setTerms(null); setFailed(false);
    Promise.all(schoolIds.split(',').filter(Boolean).map(listTerms)).then(results => {
      if (!active) return;
      const open = results.flat().filter(term => term.status === 'ACTIVE');
      setTerms(open);
      setTermId(previous => open.some(term => term.termId === previous) ? previous : open.length === 1 ? open[0].termId : '');
    }).catch(() => { if (active) setFailed(true); });
    return () => { active = false; };
  }, [schoolIds, attempt]);
  return { terms, failed, termId, setTermId, retry: () => setAttempt(previous => previous + 1) };
}
