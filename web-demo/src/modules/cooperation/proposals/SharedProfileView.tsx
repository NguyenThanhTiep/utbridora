import type { SharedFile, SharedProfile } from './types';
import { formatDateDMY } from '../../../ui/dateFormat';
import { SharedFileDownload, type SharedFileContext } from './SharedFileDownload';
import { Icon } from '../../../components';
import { SharedProfileSection } from './SharedProfileSection';

const allowedLabels: Record<string, string> = {
  displayName: 'Họ tên',
  studentCode: 'Mã SV',
  email: 'Email',
  phone: 'Điện thoại',
  majorCode: 'Chuyên ngành',
  skills: 'Kỹ năng',
  projects: 'Dự án',
  certificates: 'Chứng chỉ',
  cv: 'CV',
  evidence: 'Minh chứng',
};

function fileDescription(file: SharedFile) {
  const type = file.contentType === 'application/pdf' ? 'PDF' : file.contentType;
  const unit = file.sizeBytes < 1024 ? 1 : file.sizeBytes < 1024 * 1024 ? 1024 : 1024 * 1024;
  const size = (file.sizeBytes / unit).toLocaleString('vi-VN', { maximumFractionDigits: 1 });
  return `${type} · ${size} ${unit === 1 ? 'B' : unit === 1024 ? 'KB' : 'MB'}`;
}

export function SharedProfileView({ profile, files, hideDisplayName = false }: { profile: SharedProfile; files: SharedFileContext; hideDisplayName?: boolean }) {
  const allowed = new Set(profile.allowedFields), data = profile.data;
  return <div className="proposal-profile">
    <div className="proposal-whitelist-bar" aria-label="Phạm vi trường dữ liệu được chia sẻ">
      <Icon name="shield" size={17} /><span>Thông tin được sinh viên cho phép chia sẻ</span>
      <details className="candidate-sharing-fields"><summary>Xem phạm vi chia sẻ</summary><div className="proposal-whitelist-chips">
        {profile.allowedFields.map(field => <span key={field} className="placement-seq-badge">{allowedLabels[field] || field}</span>)}
      </div></details>
    </div>
    {['studentCode', 'email', 'phone', 'majorCode', ...(!hideDisplayName ? ['displayName'] : [])].some(field => allowed.has(field)) && <dl className="candidate-contact-grid">
      {allowed.has('displayName') && !hideDisplayName && <div><dt>Họ tên</dt><dd>{data.displayName || 'Chưa khai báo'}</dd></div>}
      {allowed.has('studentCode') && <div><dt>Mã sinh viên</dt><dd>{data.studentCode || 'Chưa khai báo'}</dd></div>}
      {allowed.has('email') && <div><dt>Email</dt><dd>{data.email || 'Chưa khai báo'}</dd></div>}
      {allowed.has('phone') && <div><dt>Điện thoại</dt><dd>{data.phone || 'Chưa khai báo'}</dd></div>}
      {allowed.has('majorCode') && <div><dt>Chuyên ngành</dt><dd>{data.majorCode || 'Chưa khai báo'}</dd></div>}
    </dl>}
    {allowed.has('skills') && <SharedProfileSection title="Kỹ năng" items={data.skills || []} limit={6} listClass="proposal-skills"
      empty="Chưa có kỹ năng được khai báo." render={entry => {
        const s = entry.skill || (entry as unknown as { skillCode?: string; name?: string; level?: number; yearsPractice?: number });
        return <><b>{(entry.kind === 'CANONICAL' || !entry.kind) ? (s.skillCode || s.name) : s.name}</b>
          <span>{'Cấp độ '}{s.level ?? 1}{' · '}{s.yearsPractice ?? 0} năm{entry.kind === 'CUSTOM' ? ' · Tự khai báo' : ''}</span>
          {'note' in s && s.note ? <p>{s.note}</p> : null}</>;
      }} />}
    {allowed.has('projects') && <SharedProfileSection title="Dự án" items={data.projects || []} listClass="candidate-project-items" empty="Chưa có dự án được khai báo." render={project => <>
      <strong>{project.title}</strong><p>{project.description}</p><p>Vai trò: {project.role}</p><p>{project.skillCodes.join(', ')}</p>
    </>} />}
    {allowed.has('certificates') && <SharedProfileSection title="Chứng chỉ" items={data.certificates || []} empty="Chưa có chứng chỉ được khai báo." render={certificate => <>
      <strong>{certificate.title}</strong><p>{certificate.issuer} · Cấp ngày {formatDateDMY(certificate.issuedOn)}{certificate.expiresOn ? ` · Hết hạn ${formatDateDMY(certificate.expiresOn)}` : ''}</p>
    </>} />}
    {allowed.has('cv') && <section className="candidate-profile-section candidate-cv"><h5>CV</h5><div className="candidate-file-row"><span className="candidate-file-icon" aria-hidden="true"><Icon name="file" size={22} /></span>
      <p>{data.cv ? fileDescription(data.cv) : 'Chưa có CV được chia sẻ.'}</p>
      {data.cv && <SharedFileDownload key={data.cv.fileId} context={files} file={data.cv} field="cv" validUntil={profile.validUntil} />}</div></section>}
    {allowed.has('evidence') && <SharedProfileSection title="Minh chứng" items={data.evidence || []} empty="Chưa có minh chứng được chia sẻ." render={file => <div className="candidate-file-row">
      <span className="candidate-file-icon" aria-hidden="true"><Icon name="file" size={22} /></span><p>{fileDescription(file)}</p>
      <SharedFileDownload context={files} file={file} field="evidence" validUntil={profile.validUntil} />
    </div>} />}
  </div>;
}
