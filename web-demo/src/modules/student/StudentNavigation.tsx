import { Icon } from '../../components';

type MenuItem = { id: string; title: string; icon: string; group?: string };

/** Keep T2 destinations intact while presenting them in the shared portal sidebar. */
export function StudentNavigation({ items, activeScreen, navigate }: {
  items: MenuItem[]; activeScreen: string; navigate: (screen: string) => void;
}) {
  const utilities = items.filter(item => item.group !== 'student');
  const entry = (item: MenuItem) => <button type="button" key={item.id}
    className={'nav' + (activeScreen === item.id ? ' active' : '')}
    title={item.title}
    aria-current={activeScreen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}>
    <Icon name={item.icon} /><span>{item.title}</span>
  </button>;
  return <div className="student-navigation">
    <p className="nav-group-label student-nav-group">SINH VIÊN</p>
    {items.filter(item => item.group === 'student').map(entry)}
    {utilities.length > 0 && <><p className="nav-group-label student-nav-utilities">TÀI KHOẢN & TIỆN ÍCH</p>{utilities.map(entry)}</>}
  </div>;
}
