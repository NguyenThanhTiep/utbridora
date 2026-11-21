import { useEffect, useState } from 'react';

const messages = [
  ['Kết nối tiềm năng', 'Kiến tạo tương lai.'],
  ['Học từ trải nghiệm', 'Vững bước vào nghề.'],
  ['Kết nối nhà trường', 'Đồng hành doanh nghiệp.'],
  ['Khám phá cơ hội', 'Phát triển bản thân.'],
] as const;

export function RotatingHeadline() {
  const [index, setIndex] = useState(0);
  const [canAnimate, setCanAnimate] = useState(false);
  const [exiting, setExiting] = useState(false);

  useEffect(() => {
    // The introduction is hidden on small screens; don't run timers there or in a background tab.
    const media = window.matchMedia('(min-width: 701px) and (prefers-reduced-motion: no-preference)');
    const sync = () => setCanAnimate(media.matches && !document.hidden);
    sync();
    media.addEventListener('change', sync);
    document.addEventListener('visibilitychange', sync);
    return () => {
      media.removeEventListener('change', sync);
      document.removeEventListener('visibilitychange', sync);
    };
  }, []);

  useEffect(() => {
    setExiting(false);
    if (!canAnimate) return;
    let swap: ReturnType<typeof setTimeout> | undefined;
    const hold = setTimeout(() => {
      setExiting(true);
      swap = setTimeout(() => setIndex(value => (value + 1) % messages.length), 260);
    }, 3000);
    return () => { clearTimeout(hold); clearTimeout(swap); };
  }, [index, canAnimate]);

  return <div className="auth-rotating-message" data-rotating={canAnimate} data-tone={index}>
    <h1 aria-label="Kết nối tiềm năng. Kiến tạo tương lai.">
      <span key={index} className={'auth-rotating-copy' + (exiting ? ' is-exiting' : '')} aria-hidden="true">
        <span className="auth-title-line is-accent">{messages[index][0]}</span><br />
        <span className="auth-title-line is-accent">{messages[index][1]}</span>
      </span>
    </h1>
  </div>;
}
