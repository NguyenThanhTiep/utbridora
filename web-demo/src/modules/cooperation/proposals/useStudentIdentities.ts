import { useEffect, useState } from 'react';
import { api } from '../../../api';

export type StudentIdentity = {
  displayName?: string | null;
  studentCode?: string | null;
  majorCode?: string | null;
};

type ProjectionResponse = {
  studentUserId: string;
  allowedFields: string[];
  data: StudentIdentity;
};

export function formatCriterionLabel(code: string): string {
  if (code === 'MAJOR') return 'Ngành học';
  if (code === 'AVAILABILITY') return 'Lịch thực tập';
  if (code === 'TERM') return 'Kỳ thực tập';
  if (code.startsWith('SKILL:')) return `Kỹ năng ${code.slice(6).replace(/_/g, ' ')}`;
  return code.replace(/_/g, ' ');
}

export function useStudentIdentities(studentIds: string[], termId?: string) {
  const [state, setState] = useState<{ termId: string; entries: Record<string, StudentIdentity | null> }>({
    termId: '',
    entries: {},
  });
  const activeEntries = termId && state.termId === termId ? state.entries : {};
  const idListKey = studentIds.join(',');

  useEffect(() => {
    if (!termId || !studentIds.length) return;
    const current = state.termId === termId ? state.entries : {};
    const pending = [...new Set(studentIds)].filter(id => id && current[id] === undefined);
    if (!pending.length) return;

    const controller = new AbortController();
    const query = new URLSearchParams({ termId, purpose: 'SCHOOL_REVIEW' });
    ['studentCode', 'displayName', 'majorCode'].forEach(field => query.append('fields', field));

    void Promise.all(
      pending.map(async id => {
        try {
          const res = await api<ProjectionResponse>(
            `/students/${encodeURIComponent(id)}/profile?${query}`,
            { signal: controller.signal },
          );
          return [id, res.data ?? {}] as const;
        } catch {
          return [id, null] as const;
        }
      }),
    ).then(entries => {
      if (!controller.signal.aborted) {
        setState(prev => ({
          termId,
          entries: { ...(prev.termId === termId ? prev.entries : {}), ...Object.fromEntries(entries) },
        }));
      }
    });

    return () => controller.abort();
  }, [termId, idListKey]);

  return activeEntries;
}
