import { useEffect, useState } from 'react';
import { workflowPages } from '../internship/workflow-api';
import type { InternshipTask } from '../internship/workflow';

// Contract v1.0 chưa có API "hồ sơ thực tập của tôi": caseId chỉ lấy từ phân công nhiệm vụ T3 của chính sinh viên.
// Chưa được giao nhiệm vụ thì chưa biết caseId; không tự tạo hay đoán caseId.
export function useMyCaseIds(userId: string) {
  const [caseIds, setCaseIds] = useState<string[] | null>(null), [error, setError] = useState<unknown>(null), [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const c = new AbortController(); setCaseIds(null); setError(null);
    void workflowPages<InternshipTask>('/t3/tasks', undefined, c.signal).then(tasks => {
      if (!c.signal.aborted) setCaseIds([...new Set(tasks.flatMap(task => task.assignments.filter(a => a.studentUserId === userId).map(a => a.caseId)))]);
    }).catch(failure => { if (!c.signal.aborted) setError(failure); });
    return () => c.abort();
  }, [userId, attempt]);
  return { caseIds, error, retry: () => setAttempt(previous => previous + 1) };
}
