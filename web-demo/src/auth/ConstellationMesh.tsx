// Lucian UX/UI Prompt #341 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#341

import { useEffect, useRef, type KeyboardEvent, type RefObject } from 'react';
import './constellation-mesh.css';

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  b: number;
}

interface Palette {
  dot: string;
  linkHue: number;
  linkSpan: number;
  linkSat: string;
  linkLight: string;
  ptr: string;
  ptrRgb: string;
}

export interface ConstellationMeshProps {
  /** Render inside an existing hero/illustration frame instead of standalone card bounds. */
  variant?: 'standalone' | 'story';
  /** Optional host element ref to track pointer move/down even across decorative siblings. */
  interactionTargetRef?: RefObject<HTMLElement | null>;
  className?: string;
  ariaLabel?: string;
}

const LINK_RADIUS = 72;
const POINTER_RADIUS = 120;
const INITIAL_COUNT = 72;
const MAX_PARTICLES = 120;

const DEFAULT_PALETTE: Palette = {
  dot: '#e7f6f0',
  linkHue: 162,
  linkSpan: 14,
  linkSat: '78%',
  linkLight: '68%',
  ptr: '#8cf2db',
  ptrRgb: '140, 242, 219',
};

function readPalette(el: HTMLElement | null): Palette {
  if (!el || typeof window === 'undefined') return DEFAULT_PALETTE;
  const styles = window.getComputedStyle(el);
  const str = (name: string, fallback: string) => styles.getPropertyValue(name).trim() || fallback;
  const num = (name: string, fallback: number) => {
    const parsed = Number.parseFloat(styles.getPropertyValue(name));
    return Number.isFinite(parsed) ? parsed : fallback;
  };
  return {
    dot: str('--u341-dot', DEFAULT_PALETTE.dot),
    linkHue: num('--u341-link-h', DEFAULT_PALETTE.linkHue),
    linkSpan: num('--u341-link-span', DEFAULT_PALETTE.linkSpan),
    linkSat: str('--u341-link-s', DEFAULT_PALETTE.linkSat),
    linkLight: str('--u341-link-l', DEFAULT_PALETTE.linkLight),
    ptr: str('--u341-ptr', DEFAULT_PALETTE.ptr),
    ptrRgb: str('--u341-ptr-rgb', DEFAULT_PALETTE.ptrRgb),
  };
}

export function ConstellationMesh({
  variant = 'story',
  interactionTargetRef,
  className = '',
  ariaLabel = 'Mạng lưới kết nối tương tác, di chuyển con trỏ hoặc nhấn để thêm điểm kết nối',
}: ConstellationMeshProps) {
  const rootRef = useRef<HTMLDivElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const counterRef = useRef<HTMLSpanElement>(null);
  const burstFromKeyboardRef = useRef<(() => void) | null>(null);

  useEffect(() => {
    const root = rootRef.current;
    const cv = canvasRef.current;
    const ct = counterRef.current;
    if (!root || !cv || !ct) return;
    const cx = cv.getContext('2d');
    if (!cx) return;

    const motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const schemeQuery = window.matchMedia('(prefers-color-scheme: dark)');

    let rm = motionQuery.matches;
    let vis = true;
    let raf = 0;
    let last = 0;
    let palette = readPalette(root);

    let W = root.clientWidth || 340;
    let H = root.clientHeight || 330;
    const dpr = Math.min(2, window.devicePixelRatio || 1);

    const syncCanvasSize = () => {
      const nextW = Math.max(120, root.clientWidth || 340);
      const nextH = Math.max(120, root.clientHeight || 330);
      const prevW = W || nextW;
      const prevH = H || nextH;
      W = nextW;
      H = nextH;
      cv.width = Math.round(W * dpr);
      cv.height = Math.round(H * dpr);
      cx.setTransform(dpr, 0, 0, dpr, 0, 0);
      if (prevW > 0 && prevH > 0 && (prevW !== W || prevH !== H)) {
        const sx = W / prevW;
        const sy = H / prevH;
        for (const p of particles) {
          p.x = Math.max(0, Math.min(W, p.x * sx));
          p.y = Math.max(0, Math.min(H, p.y * sy));
        }
      }
    };

    const particles: Particle[] = [];
    let ptr: { x: number; y: number } | null = null;

    const add = (x: number, y: number, vx: number, vy: number, born = 0) => {
      particles.push({ x, y, vx, vy, b: born });
      if (particles.length > MAX_PARTICLES) particles.shift();
    };

    syncCanvasSize();
    for (let i = 0; i < INITIAL_COUNT; i++) {
      add(
        Math.random() * W,
        Math.random() * H,
        (Math.random() - 0.5) * 22,
        (Math.random() - 0.5) * 22,
      );
    }

    const draw = (now: number) => {
      cx.clearRect(0, 0, W, H);
      let links = 0;

      for (let a = 0; a < particles.length; a++) {
        for (let b = a + 1; b < particles.length; b++) {
          const dx = particles[a].x - particles[b].x;
          const dy = particles[a].y - particles[b].y;
          if (Math.abs(dx) > LINK_RADIUS || Math.abs(dy) > LINK_RADIUS) continue;
          const d = Math.hypot(dx, dy);
          if (d > LINK_RADIUS) continue;
          links++;
          const hue = palette.linkHue + (particles[a].x / (W || 1)) * palette.linkSpan;
          cx.strokeStyle = `hsla(${hue.toFixed(0)},${palette.linkSat},${palette.linkLight},${((1 - d / LINK_RADIUS) * 0.45).toFixed(2)})`;
          cx.lineWidth = 1;
          cx.beginPath();
          cx.moveTo(particles[a].x, particles[a].y);
          cx.lineTo(particles[b].x, particles[b].y);
          cx.stroke();
        }
      }

      if (ptr) {
        for (const p of particles) {
          const d = Math.hypot(p.x - ptr.x, p.y - ptr.y);
          if (d > POINTER_RADIUS) continue;
          links++;
          cx.strokeStyle = `rgba(${palette.ptrRgb},${((1 - d / POINTER_RADIUS) * 0.8).toFixed(2)})`;
          cx.lineWidth = 1.3;
          cx.beginPath();
          cx.moveTo(ptr.x, ptr.y);
          cx.lineTo(p.x, p.y);
          cx.stroke();
        }
        cx.fillStyle = palette.ptr;
        cx.beginPath();
        cx.arc(ptr.x, ptr.y, 3.5, 0, 6.2832);
        cx.fill();
      }

      for (const p of particles) {
        const age = (now - p.b) / 600;
        const r = p.b && age < 1 ? 1.6 + (1 - age) * 2.4 : 1.6;
        cx.fillStyle = palette.dot;
        cx.beginPath();
        cx.arc(p.x, p.y, r, 0, 6.2832);
        cx.fill();
      }

      root.dataset.nodeCount = String(particles.length);
      root.dataset.linkCount = String(links);
      ct.textContent = `${links} kết nối`;
    };

    const stop = () => {
      if (raf) {
        cancelAnimationFrame(raf);
        raf = 0;
      }
    };

    const frame = (now: number) => {
      raf = 0;
      if (!root.isConnected || !vis || rm || document.hidden) return;
      const dt = Math.min(0.05, Math.max(0, (now - last) / 1000));
      last = now;
      for (const p of particles) {
        if (ptr) {
          const dx = ptr.x - p.x;
          const dy = ptr.y - p.y;
          const d = Math.hypot(dx, dy);
          if (d < POINTER_RADIUS && d > 12) {
            p.vx += (dx / d) * 28 * dt;
            p.vy += (dy / d) * 28 * dt;
          }
        }
        const sp = Math.hypot(p.vx, p.vy);
        if (sp > 30) {
          p.vx *= 1 - 1.5 * dt;
          p.vy *= 1 - 1.5 * dt;
        }
        p.x += p.vx * dt;
        p.y += p.vy * dt;
        if (p.x < 0 || p.x > W) {
          p.vx *= -1;
          p.x = Math.max(0, Math.min(W, p.x));
        }
        if (p.y < 0 || p.y > H) {
          p.vy *= -1;
          p.y = Math.max(0, Math.min(H, p.y));
        }
      }
      draw(now);
      raf = requestAnimationFrame(frame);
    };

    const run = () => {
      if (rm || !vis || document.hidden) {
        stop();
        draw(performance.now());
        return;
      }
      if (!raf) {
        last = performance.now();
        raf = requestAnimationFrame(frame);
      }
    };

    const burstAt = (x: number, y: number) => {
      const now = performance.now();
      for (let k = 0; k < 9; k++) {
        const a = (k / 9) * Math.PI * 2 + Math.random() * 0.4;
        const s = 60 + Math.random() * 70;
        add(x, y, Math.cos(a) * s, Math.sin(a) * s, now);
      }
      if (rm) draw(now);
    };

    burstFromKeyboardRef.current = () => {
      const q = ptr ?? { x: W * 0.5, y: H * 0.5 };
      ptr = q;
      burstAt(q.x, q.y);
      if (!rm) draw(performance.now());
    };

    const toLocalPoint = (clientX: number, clientY: number) => {
      const r = cv.getBoundingClientRect();
      const k = (cv.offsetWidth || r.width || 1) / (r.width || 1);
      return {
        x: Math.max(0, Math.min(W, (clientX - r.left) * k)),
        y: Math.max(0, Math.min(H, (clientY - r.top) * k)),
      };
    };

    const host = interactionTargetRef?.current ?? cv;
    const handlePointerMove = (e: PointerEvent) => {
      ptr = toLocalPoint(e.clientX, e.clientY);
      if (rm) draw(performance.now());
    };
    const handlePointerLeave = () => {
      ptr = null;
      if (rm) draw(performance.now());
    };
    const handlePointerDown = (e: PointerEvent) => {
      const q = toLocalPoint(e.clientX, e.clientY);
      ptr = q;
      burstAt(q.x, q.y);
    };

    const handleMotionChange = () => {
      rm = motionQuery.matches;
      run();
    };
    const handleThemeRefresh = () => {
      palette = readPalette(root);
      draw(performance.now());
    };

    let io: IntersectionObserver | null = null;
    if ('IntersectionObserver' in window) {
      io = new IntersectionObserver(entries => {
        vis = entries[0]?.isIntersecting ?? true;
        run();
      });
      io.observe(root);
    }

    let ro: ResizeObserver | null = null;
    if ('ResizeObserver' in window) {
      ro = new ResizeObserver(() => {
        syncCanvasSize();
        draw(performance.now());
      });
      ro.observe(root);
    }

    const mo = new MutationObserver(handleThemeRefresh);
    mo.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme', 'class'] });
    mo.observe(root, { attributes: true, attributeFilter: ['data-theme', 'class'] });

    host.addEventListener('pointermove', handlePointerMove);
    host.addEventListener('pointerleave', handlePointerLeave);
    host.addEventListener('pointerdown', handlePointerDown);
    motionQuery.addEventListener('change', handleMotionChange);
    schemeQuery.addEventListener('change', handleThemeRefresh);
    document.addEventListener('visibilitychange', run);

    run();

    return () => {
      stop();
      io?.disconnect();
      ro?.disconnect();
      mo.disconnect();
      host.removeEventListener('pointermove', handlePointerMove);
      host.removeEventListener('pointerleave', handlePointerLeave);
      host.removeEventListener('pointerdown', handlePointerDown);
      motionQuery.removeEventListener('change', handleMotionChange);
      schemeQuery.removeEventListener('change', handleThemeRefresh);
      document.removeEventListener('visibilitychange', run);
      burstFromKeyboardRef.current = null;
    };
  }, [interactionTargetRef]);

  const handleKeyDown = (e: KeyboardEvent<HTMLCanvasElement>) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      burstFromKeyboardRef.current?.();
    }
  };

  const rootClass = `u341${variant === 'story' ? ' u341-in-story' : ''}${className ? ` ${className}` : ''}`;

  return (
    <div ref={rootRef} className={rootClass}>
      <canvas
        ref={canvasRef}
        className="u341-cv"
        role="img"
        aria-label={ariaLabel}
        tabIndex={0}
        onKeyDown={handleKeyDown}
      />
      <span ref={counterRef} className="u341-ct" aria-hidden="true" />
    </div>
  );
}
