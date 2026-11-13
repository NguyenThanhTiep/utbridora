import { Icon } from '../../../components';
import { pagesFor, type PortalPage, type PortalSide } from './navigation';

export function PortalNavigation({ side, page, navigate }: {
  side: PortalSide; page?: PortalPage; navigate: (page: PortalPage) => void;
}) {
  return <div className="portal-navigation">
    <p className="nav-group-label">{side === 'school' ? 'CỔNG NHÀ TRƯỜNG' : 'CỔNG DOANH NGHIỆP'}</p>
    {pagesFor(side).map(item => <button key={item.id} className={'nav' + (page === item.id ? ' active' : '')}
      aria-current={page === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}>
      <Icon name={item.icon} /><span>{item.title}</span>
    </button>)}
  </div>;
}
