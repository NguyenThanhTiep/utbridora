import { useEffect, useRef, useState } from 'react';

/** Read shared projections near the viewport; an open decision always remains active. */
export function useCandidateVisibility(expanded: boolean) {
  const cardRef = useRef<HTMLElement>(null);
  const [visible, setVisible] = useState(false);
  useEffect(() => {
    if (!cardRef.current) return;
    if (!('IntersectionObserver' in window)) { setVisible(true); return; }
    const observer = new IntersectionObserver(entries => setVisible(entries.some(entry => entry.isIntersecting)), { rootMargin: '160px 0px' });
    observer.observe(cardRef.current);
    return () => observer.disconnect();
  }, []);
  return { cardRef, enabled: visible || expanded };
}
