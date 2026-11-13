import { useEffect, useId, useState } from 'react';
import { Notice } from '../../../components';
import { readAll } from '../portal/data';
import type { PortalSide } from '../portal/navigation';
import { workflowError } from '../proposals/api';

type School = { schoolOrgId: string; name: string };
type Term = { termId: string; termCode: string };
export function NotificationScopeFields({ org, side, schoolOrgId, termId, change }: {
  org: string; side: PortalSide; schoolOrgId: string; termId: string; change: (school: string, term: string) => void;
}) {
  const id = useId();
  const [schools, setSchools] = useState<School[]>([]), [terms, setTerms] = useState<Term[]>([]), [error, setError] = useState('');
  useEffect(() => {
    if (side !== 'enterprise') return;
    const c = new AbortController();
    void readAll<School>(org, '/enterprise/demand-references/schools', c.signal)
      .then(rows => { if (!c.signal.aborted) setSchools(rows); }).catch(reason => { if (!c.signal.aborted) setError(workflowError(reason)); });
    return () => c.abort();
  }, [org, side]);
  useEffect(() => {
    setTerms([]); if (!schoolOrgId) return;
    const c = new AbortController();setError('');
    const path = side === 'enterprise' ? '/enterprise/demand-references/terms' : '/terms';
    void readAll<Term>(org, path + '?' + new URLSearchParams({ schoolOrgId }), c.signal)
      .then(rows => { if (!c.signal.aborted) setTerms(rows); }).catch(reason => { if (!c.signal.aborted) setError(workflowError(reason)); });
    return () => c.abort();
  }, [org, side, schoolOrgId]);
  return <><div className="notification-scope" role="group" aria-label="Phạm vi thông báo"><div className="notification-scope-fields">
    {side === 'enterprise' && <div><label htmlFor={id + '-school'}>Trường theo dõi</label><select id={id + '-school'} value={schoolOrgId}
      onChange={event => change(event.target.value, '')}><option value="">Tất cả trường</option>
      {schools.map(school => <option key={school.schoolOrgId} value={school.schoolOrgId}>{school.name}</option>)}
    </select></div>}
    <div><label htmlFor={id + '-term'}>Kỳ theo dõi</label><select id={id + '-term'} value={termId} disabled={!schoolOrgId}
      onChange={event => change(schoolOrgId, event.target.value)}><option value="">Tất cả kỳ</option>
      {terms.map(term => <option key={term.termId} value={term.termId}>{term.termCode}</option>)}
    </select></div></div></div>
    {error && <div className="portal-context-error"><Notice message={error} tone="error" /></div>}
  </>;
}
