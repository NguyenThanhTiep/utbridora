import { useId } from 'react';
import { Icon } from '../../../components';

/** One introduction frame for every enterprise function, including the overview. */
export function EnterprisePageHeader({ title, breadcrumb, kicker, description, icon, refresh }: {
  title: string; breadcrumb: string; kicker: string; description: string; icon: string; refresh?: () => void;
}) {
  const titleId = useId();
  return <div className="enterprise-page-intro">
    <div className="enterprise-page-toolbar">
      <p className="portal-breadcrumb">Cổng doanh nghiệp <span aria-hidden="true">/</span> {breadcrumb}</p>
      {refresh && <button type="button" className="secondary" onClick={refresh}><Icon name="refresh" size={16} />Tải lại dữ liệu</button>}
    </div>
    <header className="portal-welcome enterprise-page-heading" aria-labelledby={titleId}>
      <div><p className="section-kicker">{kicker}</p><h2 id={titleId}>{title}</h2><p>{description}</p></div>
      <span className="portal-welcome-icon" aria-hidden="true"><Icon name={icon} size={38} /></span>
    </header>
  </div>;
}
