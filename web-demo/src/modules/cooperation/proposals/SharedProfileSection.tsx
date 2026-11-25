import { useId, useState, type ReactNode } from 'react';

export function SharedProfileSection<T>({ title, items, empty, render, limit = 3, listClass = '' }: {
  title: string; items: T[]; empty: string; render: (item: T, index: number) => ReactNode; limit?: number; listClass?: string;
}) {
  const id = useId(), [expanded, setExpanded] = useState(false);
  return <section className="candidate-profile-section" aria-labelledby={id + '-title'}>
    <div className="candidate-profile-section-heading"><h5 id={id + '-title'}>{title}</h5>
      {items.length > 0 && <span className="candidate-section-count">{items.length}</span>}
    </div>
    {items.length ? <ul id={id} className={'candidate-profile-items ' + listClass} role="list">
      {(expanded ? items : items.slice(0, limit)).map((item, index) => <li key={index}>{render(item, index)}</li>)}
    </ul> : <p className="candidate-section-empty">{empty}</p>}
    {items.length > limit && <button type="button" className="secondary candidate-show-more" aria-expanded={expanded} aria-controls={id}
      onClick={() => setExpanded(value => !value)}>{expanded ? `Thu gọn ${title.toLocaleLowerCase('vi')}` : `Xem tất cả ${items.length} ${title.toLocaleLowerCase('vi')}`}</button>}
  </section>;
}
