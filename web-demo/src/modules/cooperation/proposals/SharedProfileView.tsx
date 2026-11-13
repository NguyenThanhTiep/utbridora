import type { SharedProfile } from './types';
import { SharedFileDownload, type SharedFileContext } from './SharedFileDownload';

export function SharedProfileView({ profile, files }: { profile: SharedProfile; files: SharedFileContext }) {
  const allowed = new Set(profile.allowedFields), data = profile.data;
  return <div className="proposal-profile">
    {allowed.has('displayName') && <p><strong>Họ tên:</strong> {data.displayName || 'Chưa khai báo'}</p>}
    {allowed.has('studentCode') && <p><strong>Mã sinh viên:</strong> {data.studentCode || 'Chưa khai báo'}</p>}
    {allowed.has('email') && <p><strong>Email:</strong> {data.email || 'Chưa khai báo'}</p>}
    {allowed.has('phone') && <p><strong>Điện thoại:</strong> {data.phone || 'Chưa khai báo'}</p>}
    {allowed.has('majorCode') && <p><strong>Chuyên ngành:</strong> {data.majorCode || 'Chưa khai báo'}</p>}
    {allowed.has('skills') && <div><h5>Kỹ năng</h5><ul className="proposal-skills">
      {(data.skills || []).map((entry, n) => {
        const s = entry.skill || (entry as unknown as { skillCode?: string; name?: string; level?: number; yearsPractice?: number });
        return <li key={n}>{(entry.kind === 'CANONICAL' || !entry.kind) ? (s.skillCode || s.name) : s.name}
          {' · Cấp độ '}{s.level ?? 1}{' · '}{s.yearsPractice ?? 0} năm{entry.kind === 'CUSTOM' ? ' · Tự khai báo' : ''}
          {'note' in s && s.note ? <p>{s.note}</p> : null}</li>;
      })}
    </ul>{!data.skills?.length && <p>Chưa có kỹ năng được khai báo.</p>}</div>}
    {allowed.has('projects') && <div><h5>Dự án</h5>{(data.projects || []).map(project => <div key={project.projectId}>
      <strong>{project.title}</strong><p>{project.description}</p><p>Vai trò: {project.role}</p><p>{project.skillCodes.join(', ')}</p>
    </div>)}{!data.projects?.length && <p>Chưa có dự án được khai báo.</p>}</div>}
    {allowed.has('certificates') && <div><h5>Chứng chỉ</h5>{(data.certificates || []).map(certificate => <div key={certificate.certificateId}>
      <strong>{certificate.title}</strong><p>{certificate.issuer} · Cấp ngày {certificate.issuedOn}{certificate.expiresOn ? ` · Hết hạn ${certificate.expiresOn}` : ''}</p>
    </div>)}{!data.certificates?.length && <p>Chưa có chứng chỉ được khai báo.</p>}</div>}
    {allowed.has('cv') && <div><p><strong>CV:</strong> {data.cv ? `${data.cv.contentType} · ${data.cv.sizeBytes} byte` : 'Chưa có CV được chia sẻ.'}</p>
      {data.cv && <SharedFileDownload key={data.cv.fileId} context={files} file={data.cv} field="cv" validUntil={profile.validUntil} />}</div>}
    {allowed.has('evidence') && <div><h5>Minh chứng</h5>{(data.evidence || []).map(file => <div key={file.fileId}><p>{file.contentType} · {file.sizeBytes} byte</p>
      <SharedFileDownload context={files} file={file} field="evidence" validUntil={profile.validUntil} /></div>)}
      {!data.evidence?.length && <p>Chưa có minh chứng được chia sẻ.</p>}</div>}
  </div>;
}
