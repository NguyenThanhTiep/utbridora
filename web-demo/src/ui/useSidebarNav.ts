import { useCallback, useEffect, useLayoutEffect, useRef, type KeyboardEvent, type RefObject } from 'react';

function isVisibleNavButton(button: HTMLButtonElement): boolean {
  if (button.disabled || button.tabIndex < 0) return false;
  if (button.closest('[aria-hidden="true"]')) return false;
  if (button.closest('.is-rail') && button.closest('.school-nav-collapse-wrap, .internship-submenu')) return false;
  return button.getClientRects().length > 0 && button.getBoundingClientRect().height > 0;
}

function findActiveNavButton(track: HTMLElement, isRail: boolean): HTMLButtonElement | null {
  const activeButtons = Array.from(track.querySelectorAll<HTMLButtonElement>('button.nav.active'));
  for (const button of activeButtons) {
    const collapseWrap = button.closest<HTMLElement>('.school-nav-collapse-wrap');
    if (collapseWrap && (isRail || !collapseWrap.classList.contains('is-open'))) continue;
    const subWrap = button.closest<HTMLElement>('.internship-submenu');
    if (subWrap && isRail) continue;
    if (button.getClientRects().length > 0 && button.getBoundingClientRect().height > 0) return button;
  }

  const groupFallback = track.querySelector<HTMLButtonElement>(
    '.school-nav-group.has-active-child > button.school-nav-group-toggle, button.internship-parent.active',
  );
  if (groupFallback && groupFallback.getClientRects().length > 0 && groupFallback.getBoundingClientRect().height > 0) {
    return groupFallback;
  }

  return null;
}

/** Coordinates Lucian #417 active-pill tracking and vertical arrow-key navigation inside the sidebar. */
export function useSidebarNav(
  trackRef: RefObject<HTMLDivElement | null>,
  { isRail, syncKey }: { isRail: boolean; syncKey: string },
) {
  const didMeasureRef = useRef(false);
  const rafRef = useRef(0);

  const measurePill = useCallback(() => {
    const track = trackRef.current;
    if (!track) return;

    const target = findActiveNavButton(track, isRail);
    if (!target) {
      track.style.setProperty('--u417-po', '0');
      track.setAttribute('data-u417-ready', 'true');
      return;
    }

    const trackRect = track.getBoundingClientRect();
    const targetRect = target.getBoundingClientRect();
    if (trackRect.height === 0 || targetRect.height === 0) return;

    const isSubitem =
      target.classList.contains('school-nav-subitem') || target.classList.contains('internship-subnav');
    const pillHeight = isSubitem ? 18 : 22;
    const py = targetRect.top - trackRect.top + (targetRect.height - pillHeight) / 2;

    const workspace = track.closest('.workspace');
    const darkSidebar = !!workspace?.matches('.workspace-admin, .workspace-t5, .workspace-student');
    const leftOffset = targetRect.left - trackRect.left;

    let px: number;
    if (!darkSidebar) {
      px = isRail ? leftOffset + 3 : -14;
    } else if (!isRail && target.classList.contains('school-nav-subitem')) {
      px = leftOffset + 11;
    } else if (!isRail && target.classList.contains('internship-subnav')) {
      px = leftOffset + 4;
    } else {
      px = leftOffset + 5;
    }

    track.style.setProperty('--u417-px', `${Math.round(px * 10) / 10}px`);
    track.style.setProperty('--u417-py', `${Math.round(py * 10) / 10}px`);
    track.style.setProperty('--u417-ph', `${pillHeight}px`);
    track.style.setProperty('--u417-po', '1');
    track.setAttribute('data-u417-ready', 'true');
  }, [trackRef, isRail]);

  useLayoutEffect(() => {
    const track = trackRef.current;
    if (!track) {
      didMeasureRef.current = false;
      return undefined;
    }

    if (!didMeasureRef.current) {
      track.setAttribute('data-u417-animate', 'false');
      measurePill();
      didMeasureRef.current = true;
      const enableFrame = requestAnimationFrame(() => {
        track.setAttribute('data-u417-animate', 'true');
      });
      return () => cancelAnimationFrame(enableFrame);
    }

    measurePill();
    return undefined;
  }, [trackRef, measurePill, syncKey]);

  useEffect(() => {
    const track = trackRef.current;
    if (!track) return undefined;

    const scheduleMeasure = () => {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = requestAnimationFrame(measurePill);
    };

    const observer = new ResizeObserver(scheduleMeasure);
    observer.observe(track);

    const mutator = new MutationObserver(scheduleMeasure);
    mutator.observe(track, { attributes: true, attributeFilter: ['class', 'aria-expanded', 'aria-current'], subtree: true });

    const aside = track.closest('.sidebar');
    aside?.addEventListener('transitionend', scheduleMeasure);
    window.addEventListener('resize', scheduleMeasure);

    return () => {
      cancelAnimationFrame(rafRef.current);
      observer.disconnect();
      mutator.disconnect();
      aside?.removeEventListener('transitionend', scheduleMeasure);
      window.removeEventListener('resize', scheduleMeasure);
    };
  }, [trackRef, measurePill, syncKey]);

  const onNavKeyDown = useCallback(
    (event: KeyboardEvent<HTMLElement>) => {
      if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
      const track = trackRef.current;
      if (!track) return;

      const active = document.activeElement;
      if (!(active instanceof HTMLButtonElement) || !active.classList.contains('nav') || !track.contains(active)) {
        return;
      }

      const items = Array.from(track.querySelectorAll<HTMLButtonElement>('button.nav')).filter(isVisibleNavButton);
      if (items.length === 0) return;

      const currentIndex = items.indexOf(active);
      if (currentIndex < 0) return;

      event.preventDefault();
      let nextIndex = currentIndex;
      if (event.key === 'ArrowDown') nextIndex = (currentIndex + 1) % items.length;
      else if (event.key === 'ArrowUp') nextIndex = (currentIndex - 1 + items.length) % items.length;
      else if (event.key === 'Home') nextIndex = 0;
      else if (event.key === 'End') nextIndex = items.length - 1;

      const nextButton = items[nextIndex];
      nextButton.focus();
      nextButton.scrollIntoView({ block: 'nearest' });
    },
    [trackRef],
  );

  return { onNavKeyDown };
}
