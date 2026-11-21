import { useEffect, useId, useRef, useState } from 'react';

export function CooperationDescription({ text }: { text: string }) {
  const id = useId();
  const paragraph = useRef<HTMLParagraphElement>(null);
  const [expanded, setExpanded] = useState(false), [overflowing, setOverflowing] = useState(false);
  useEffect(() => {
    const node = paragraph.current;
    if (!node) return;
    let active = true;
    const measure = () => {
      if (!active) return;
      const style = getComputedStyle(node);
      const lineHeight = parseFloat(style.lineHeight) || parseFloat(style.fontSize) * 1.8;
      setOverflowing(node.scrollHeight > lineHeight * 6 + 2);
    };
    const frame = requestAnimationFrame(measure);
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(measure);
    observer?.observe(node);
    window.addEventListener('resize', measure);
    void document.fonts?.ready.then(measure);
    return () => { active = false; cancelAnimationFrame(frame); observer?.disconnect(); window.removeEventListener('resize', measure); };
  }, [text]);
  return <div className="school-coop-description"><h4>Nội dung nhu cầu</h4>
    <p id={id} ref={paragraph} className={expanded ? undefined : 'is-clamped'}>{text}</p>
    {overflowing && <button type="button" className="secondary school-coop-expand" aria-expanded={expanded} aria-controls={id}
      onClick={() => setExpanded(value => !value)}>{expanded ? 'Thu gọn nội dung' : 'Xem thêm nội dung'}</button>}
  </div>;
}
