import { useEffect, useId, useState } from 'react';
import { LoadingState, Notice } from '../../../components';
import { failureMessage } from './errors';
import { readSkillCatalog, type CatalogSkill, type SkillCatalog } from './skillCatalog';

export function SkillCatalogPicker({ selected, onPick, onVersion }: {
  selected: string[];
  onPick: (skill: CatalogSkill, group: 'required' | 'preferred', version: string) => void;
  onVersion: (version: string) => void;
}) {
  const [open, setOpen] = useState(false), [query, setQuery] = useState('');
  const [page, setPage] = useState(0), [refresh, setRefresh] = useState(0);
  const [catalog, setCatalog] = useState<SkillCatalog | null>(null);
  const [loading, setLoading] = useState(false), [error, setError] = useState('');
  const searchId = useId();
  useEffect(() => {
    if (!open) return;
    let active = true;
    const controller = new AbortController();
    setLoading(true); setCatalog(null); setError('');
    const timer = window.setTimeout(() => {
      void readSkillCatalog(query, page, controller.signal).then(data => {
        if (active) setCatalog(data);
      }).catch(failure => {
        if (active) setError(failureMessage(failure));
      }).finally(() => { if (active) setLoading(false); });
    }, 250);
    return () => { active = false; window.clearTimeout(timer); controller.abort(); };
  }, [open, query, page, refresh]);
  return <div className="cooperation-catalog">
    <button type="button" className="secondary" aria-expanded={open} onClick={() => setOpen(value => !value)}>
      {open ? 'Đóng tra cứu kỹ năng' : 'Tra cứu kỹ năng'}
    </button>
    {open && <>
      <label htmlFor={searchId}>Tìm kỹ năng theo tên hoặc mã</label>
      <input id={searchId} value={query} maxLength={128} onChange={event => { setQuery(event.target.value); setPage(0); setCatalog(null); }} />
      <p className="field-hint">Chọn kỹ năng sẽ thêm mã chuẩn và cập nhật phiên bản danh mục cho bản nháp.</p>
      <Notice message={error} tone="error" />
      {error && <button type="button" className="secondary" onClick={() => setRefresh(value => value + 1)}>Thử tải kỹ năng lại</button>}
      {loading && <LoadingState />}
      {catalog && <>
        <p>Phiên bản kỹ năng: <strong>{catalog.taxonomyVersion}</strong></p>
        <button type="button" className="secondary" onClick={() => onVersion(catalog.taxonomyVersion)}>Dùng phiên bản kỹ năng này</button>
        {catalog.skills.content.length === 0 ? <p role="status">{query.trim()
          ? 'Không có kỹ năng phù hợp trong danh mục.'
          : 'Danh mục kỹ năng chưa có dữ liệu. Cần bổ sung kỹ năng trước khi chọn.'}</p> :
          <ul className="cooperation-demand-list" aria-label="Kết quả tra cứu kỹ năng">
            {catalog.skills.content.map(skill => <li key={skill.skillCode}>
              <strong>{skill.name}</strong> <span>({skill.skillCode})</span>
              <div className="button-row">
                <button type="button" className="secondary" disabled={selected.includes(skill.skillCode) || skill.status !== 'ACTIVE'}
                  onClick={() => onPick(skill, 'required', catalog.taxonomyVersion)}>Chọn {skill.skillCode} bắt buộc</button>
                <button type="button" className="secondary" disabled={selected.includes(skill.skillCode) || skill.status !== 'ACTIVE'}
                  onClick={() => onPick(skill, 'preferred', catalog.taxonomyVersion)}>Chọn {skill.skillCode} ưu tiên</button>
              </div>
            </li>)}
          </ul>}
        <div className="button-row">
          <button type="button" className="secondary" disabled={catalog.skills.page === 0} onClick={() => { setPage(page - 1); setCatalog(null); }}>Kỹ năng trước</button>
          <span>Trang {catalog.skills.page + 1}/{Math.max(1, catalog.skills.totalPages)}</span>
          <button type="button" className="secondary" disabled={catalog.skills.page + 1 >= catalog.skills.totalPages} onClick={() => { setPage(page + 1); setCatalog(null); }}>Kỹ năng sau</button>
        </div>
      </>}
    </>}
  </div>;
}
