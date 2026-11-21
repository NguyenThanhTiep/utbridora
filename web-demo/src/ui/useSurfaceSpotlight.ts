import { useEffect, useRef } from 'react';

// Decorative pointer feedback shared by portals; it never changes React or business state.
export function useSurfaceSpotlight<T extends HTMLElement = HTMLElement>(enabled: boolean, selector: string, prefix: string) {
  const surface = useRef<T>(null);
  useEffect(() => {
    const root = surface.current;
    if (!enabled || !root) return;
    const media = window.matchMedia('(hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference)');
    const attribute = `data-${prefix}-glow`;
    const propertyX = `--${prefix}-glow-x`, propertyY = `--${prefix}-glow-y`;
    let frame = 0;
    let active: HTMLElement | null = null;
    let x = 0, y = 0;
    const clear = () => {
      cancelAnimationFrame(frame);
      frame = 0;
      if (active) {
        active.style.removeProperty(propertyX);
        active.style.removeProperty(propertyY);
        active.removeAttribute(attribute);
        active = null;
      }
    };
    const move = (event: PointerEvent) => {
      if (!media.matches || event.pointerType !== 'mouse') { clear(); return; }
      const card = event.target instanceof Element ? event.target.closest<HTMLElement>(selector) : null;
      if (!card || !root.contains(card)) { clear(); return; }
      if (card !== active) { clear(); active = card; }
      x = event.clientX;
      y = event.clientY;
      if (frame) return;
      frame = requestAnimationFrame(() => {
        frame = 0;
        if (!active?.isConnected) { clear(); return; }
        const rect = active.getBoundingClientRect();
        active.style.setProperty(propertyX, `${x - rect.left}px`);
        active.style.setProperty(propertyY, `${y - rect.top}px`);
        active.setAttribute(attribute, '');
      });
    };
    root.addEventListener('pointermove', move, { passive: true });
    root.addEventListener('pointerleave', clear);
    root.addEventListener('scroll', clear, true);
    window.addEventListener('blur', clear);
    document.addEventListener('visibilitychange', clear);
    media.addEventListener('change', clear);
    return () => {
      clear();
      root.removeEventListener('pointermove', move);
      root.removeEventListener('pointerleave', clear);
      root.removeEventListener('scroll', clear, true);
      window.removeEventListener('blur', clear);
      document.removeEventListener('visibilitychange', clear);
      media.removeEventListener('change', clear);
    };
  }, [enabled, selector, prefix]);
  return surface;
}
