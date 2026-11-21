import { useEffect, useState } from 'react';
import { workflowPages } from '../internship/workflow-api';
import type { InternshipTask, TaskAssignment } from '../internship/workflow';

// Contract v1.0 chưa có API "hồ sơ thực tập của tôi": caseId chỉ lấy từ phân công nhiệm vụ T3 của chính sinh viên.
// Chưa được giao nhiệm vụ thì chưa biết caseId; không tự tạo hay đoán caseId.
export function useMyCaseIds(userId: string) {
  const [caseIds, setCaseIds] = useState<string[] | null>(null), [error, setError] = useState<unknown>(null), [attempt, setAttempt] = useState(0);
  // Phân công của chính sinh viên, dùng để đếm việc theo trạng thái T3 trả về (không tự tính trạng thái).
  const [assignments, setAssignments] = useState<TaskAssignment[] | null>(null);
  // Nhiệm vụ có phân công cho sinh viên (cùng lần đọc /t3/tasks), dùng cho lịch hạn nộp ở Tổng quan.
  const [myTasks, setMyTasks] = useState<InternshipTask[] | null>(null);
  useEffect(() => {
    const c = new AbortController(); setCaseIds(null); setAssignments(null); setMyTasks(null); setError(null);
    void workflowPages<InternshipTask>('/t3/tasks', undefined, c.signal).then(tasks => {
      if (c.signal.aborted) return;
      const mine = tasks.flatMap(task => task.assignments.filter(a => a.studentUserId === userId));
      setAssignments(mine); setCaseIds([...new Set(mine.map(a => a.caseId))]);
      setMyTasks(tasks.filter(task => task.assignments.some(a => a.studentUserId === userId)));
    }).catch(failure => { if (!c.signal.aborted) setError(failure); });
    return () => c.abort();
  }, [userId, attempt]);
  return { caseIds, assignments, tasks: myTasks, error, retry: () => setAttempt(previous => previous + 1) };
}
