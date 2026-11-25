import { useCallback, useRef } from 'react';

// Bấm mở chi tiết / biểu mẫu nằm xa chỗ bấm thì tự cuộn khối đó vào tầm nhìn, chừa chỗ cho thanh trên cùng đang dính.
// request() gọi ngay trong lúc bấm; khối có thể hiện ngay hoặc sau khi tải xong (ref dạng callback bắt lúc khối gắn vào trang).
// Chỉ cuộn theo thao tác của người dùng: tải lại / làm mới dữ liệu không cuộn; yêu cầu hết hạn sau 5 giây nếu khối không xuất hiện.
// Đầu khối đã nằm trong màn hình thì không cuộn; người dùng chọn giảm chuyển động thì cuộn ngay, không trượt.
export function useReveal<T extends HTMLElement = HTMLElement>() {
  const node = useRef<T | null>(null), until = useRef(0);
  const ref = useCallback((element: T | null) => {
    node.current = element;
    if (element && until.current > Date.now()) { until.current = 0; reveal(element); }
  }, []);
  const request = useCallback(() => {
    until.current = Date.now() + 5000;
    // Khối đã có sẵn (đổi từ mục này sang mục khác) thì cuộn sau khi React vẽ lại nội dung mới.
    requestAnimationFrame(() => { if (node.current && until.current > Date.now()) { until.current = 0; reveal(node.current); } });
  }, []);
  return { ref, request };
}

function reveal(element: HTMLElement) {
  const bar = document.querySelector<HTMLElement>('.topbar');
  const offset = (bar && /^(sticky|fixed)$/.test(getComputedStyle(bar).position) ? Math.max(0, bar.getBoundingClientRect().bottom) : 0) + 12;
  const top = element.getBoundingClientRect().top;
  if (top >= offset && top <= window.innerHeight * 0.6) return;
  element.style.scrollMarginTop = offset + 'px';
  element.scrollIntoView({ behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
}
