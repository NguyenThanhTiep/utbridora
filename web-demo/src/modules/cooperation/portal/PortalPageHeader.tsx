import { useId } from 'react';
import { Icon } from '../../../components';
import type { PortalSide } from './navigation';

/** A single page frame for both portals, including their overview pages. */
export function PortalPageHeader({ side, title, breadcrumb, kicker, description, icon, refresh }: {
  side: PortalSide; title: string; breadcrumb: string; kicker: string; description: string; icon: string; refresh?: () => void;
}) {
  const titleId = useId();
  const enterprise = side === 'enterprise';
  return <div className={'portal-page-intro' + (enterprise ? ' enterprise-page-intro' : '')}>
    <div className={'portal-page-toolbar' + (enterprise ? ' enterprise-page-toolbar' : '')}>
      <p className="portal-breadcrumb">{enterprise ? 'Cổng doanh nghiệp' : 'Cổng nhà trường'} <span aria-hidden="true">/</span> {breadcrumb}</p>
      {refresh && <button type="button" className="secondary" onClick={refresh}><Icon name="refresh" size={16} />Tải lại dữ liệu</button>}
    </div>
    <header className={'portal-welcome portal-page-heading' + (enterprise ? ' enterprise-page-heading' : '')} aria-labelledby={titleId}>
      <div><p className="section-kicker">{kicker}</p><h2 id={titleId}>{title}</h2><p>{description}</p></div>
      <span className="portal-welcome-icon" aria-hidden="true"><Icon name={icon} size={38} /></span>
    </header>
  </div>;
}
