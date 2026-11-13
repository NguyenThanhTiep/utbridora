import { useEffect, useState } from 'react';
import type { SchoolProposal } from './types';
import { profileFieldLabels } from './ProfileFieldLabels';

export function ConsentStatus({ item }: { item: SchoolProposal }) {
  const [now, setNow] = useState(Date.now);
  const until = Date.parse(item.activeConsentExpiresAt || '');
  useEffect(() => {
    if (!Number.isFinite(until) || until <= Date.now()) return;
    const timer = window.setTimeout(() => setNow(Date.now()), Math.min(until - Date.now() + 1, 2_147_483_647));
    return () => window.clearTimeout(timer);
  }, [until, now]);
  if (!item.activeConsentId || !item.activeConsentVersion || !Number.isFinite(until) || Math.max(now, Date.now()) >= until)
    return <p>Chưa có đồng ý chia sẻ còn hiệu lực.</p>;
  return <div><p>Đồng ý chia sẻ còn hiệu lực.</p>
    <p>Đến <time dateTime={item.activeConsentExpiresAt!}>{new Date(until).toLocaleString('vi-VN')}</time></p>
    <p>Thông tin có thể chia sẻ: {item.shareableFields.length
      ? item.shareableFields.map(f => profileFieldLabels[f] || f).join(', ') : 'Không có mục nào thuộc chính sách hiện hành.'}</p>
    <small>Mục đích: đánh giá thực tập. Nhà trường cấp chia sẻ ở bước riêng.</small>
  </div>;
}
