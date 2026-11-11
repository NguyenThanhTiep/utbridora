import { useEffect, useState } from 'react';
import type { EnterpriseProposal, SharedProfile } from './types';
import { scopedRead, workflowError } from './api';

/** Keep commands mounted during revalidation; only the sensitive projection is discarded. */
export function useSharedProfile(org: string, item: EnterpriseProposal) {
  const scope = `${org}:${item.proposalItemId}:${item.shareId}:${item.version}`;
  const [attempt, setAttempt] = useState(0), [hidden, setHidden] = useState(document.hidden);
  const [value, setValue] = useState<{ scope: string; profile: SharedProfile } | null>(null), [error, setError] = useState('');
  useEffect(() => {
    const clear = () => { setHidden(true); setValue(null); };
    const refresh = () => {
      if (document.hidden) { clear(); return; }
      setHidden(false);setValue(null);setAttempt(n => n + 1);
    };
    window.addEventListener('blur', clear);
    window.addEventListener('focus', refresh);
    document.addEventListener('visibilitychange', refresh);
    const timer = window.setInterval(() => { if (!document.hidden) refresh(); }, 30000);
    return () => { window.removeEventListener('blur', clear);window.removeEventListener('focus', refresh);
      document.removeEventListener('visibilitychange', refresh);window.clearInterval(timer); };
  }, [scope]);
  useEffect(() => {
    const c = new AbortController();setValue(null);setError('');
    if (hidden) return () => c.abort();
    void scopedRead<SharedProfile>(org, `/enterprise/proposals/${item.proposalItemId}/profile`, c.signal).then(profile => {
      if (c.signal.aborted) return;
      if (!Number.isFinite(Date.parse(profile.validUntil)) || Date.parse(profile.validUntil) <= Date.now()) {
        setError('Quyền xem hồ sơ đã hết hạn.');return;
      }
      setValue({scope, profile});
    }).catch(reason => { if (!c.signal.aborted) {setValue(null);setError(workflowError(reason));} });
    return () => c.abort();
  }, [org, item.proposalItemId, scope, attempt, hidden]);
  useEffect(() => {
    if (!value || value.scope !== scope) return;
    const expire = () => {setValue(null);setError('Quyền xem hồ sơ đã hết hạn.');};
    const remaining = Date.parse(value.profile.validUntil) - Date.now();
    if (remaining <= 0) { expire(); return; }
    // Periodic revalidation handles grants longer than a browser timeout can represent.
    const timer = window.setTimeout(expire, Math.min(remaining, 2_147_483_647));
    return () => window.clearTimeout(timer);
  }, [value, scope]);
  return {profile: !hidden && value?.scope === scope && Date.parse(value.profile.validUntil) > Date.now() ? value.profile : null,
    error, clear: () => setValue(null)};
}
