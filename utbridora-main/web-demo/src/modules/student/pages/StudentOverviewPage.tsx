import type { Grant, Me } from '../../../api';
import { Icon } from '../../../components';
import '../styles.css';

// S01 – khung tổng quan (T2-02 tuần 1). Mức hoàn thiện hồ sơ thuộc T2-05.
export function StudentOverviewPage({ me, schools, openProfile }: { me: Me; schools: Grant[]; openProfile: () => void }) {
  return <div className="student-page">
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">HỒ SƠ SINH VIÊN</p><h2>Xin chào, {me.displayName}</h2>
        <p className="section-description">Hoàn thiện thông tin chung để nhà trường có căn cứ đề xuất bạn vào các cơ hội thực tập.</p></div>
        <span className="section-icon"><Icon name="graduation" size={21} /></span></div>
      <div className="student-scope"><span className="identity-label"><Icon name="building" size={15} />Phạm vi nhà trường</span>
        <ul className="student-scope-list">{schools.map(school => <li key={school.organizationId}><code title={school.organizationId || undefined}>{school.organizationId?.slice(0, 8)}</code></li>)}</ul></div>
    </section>
    <section className="panel">
      <div className="section-heading"><div><p className="section-kicker">BƯỚC TIẾP THEO</p><h2>Xem thông tin chung</h2>
        <p className="section-description">Kiểm tra thông tin cơ bản trong hồ sơ của bạn.</p></div>
        <span className="section-icon"><Icon name="user" size={21} /></span></div>
      <button type="button" onClick={openProfile}>Mở thông tin chung<Icon name="arrow-right" size={17} /></button>
      <p className="access-note"><Icon name="info" size={15} />Mức hoàn thiện hồ sơ và các bước tiếp theo sẽ hiển thị khi dịch vụ hồ sơ sinh viên sẵn sàng.</p>
    </section>
  </div>;
}
