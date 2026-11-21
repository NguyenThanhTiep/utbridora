import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from '../../../api';
import { Icon, Notice } from '../../../components';
import { newIdempotencyKey, saveMyProfile, toProfileInput } from '../api';
import type { CanonicalSkillInput, CustomSkillInput, SelfProfileView, ContractPage } from '../types';
import { readSkillCatalog, type CatalogSkill } from '../../cooperation/demand/skillCatalog';

export function SkillsEditor({ view, disabled, onSaved }: { view: SelfProfileView; disabled: boolean; onSaved: (view: SelfProfileView) => void }) {
  const [declared, setDeclared] = useState(view.profile.skillsDeclared);
  const [skills, setSkills] = useState<CanonicalSkillInput[]>(toProfileInput(view).skills);
  const [custom, setCustom] = useState<CustomSkillInput[]>(toProfileInput(view).customSkills);
  const [query, setQuery] = useState(''), [catalog, setCatalog] = useState<CatalogSkill[]>([]);
  const [categories, setCategories] = useState<{ categoryCode: string; name: string; status: string }[]>([]);
  const [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const pending = useRef<{ body: string; key: string } | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    const timer = setTimeout(() => { void readSkillCatalog(query, 0, controller.signal).then(r => setCatalog(r.skills.content))
      .catch(() => { if (!controller.signal.aborted) setMessage('Chưa tải được danh mục kỹ năng. Vui lòng thử lại.'); }); }, 200);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [query]);
  useEffect(() => {
    const controller = new AbortController();
    void (async () => {
      const items: typeof categories = []; let page = 0, total = 1;
      while (page < total) { const r = await api<ContractPage<typeof categories[number]>>('/taxonomy/categories?page=' + page + '&size=100', { signal: controller.signal }); items.push(...r.content); total = r.totalPages; page++; }
      if (!controller.signal.aborted) setCategories(items.filter(c => c.status === 'ACTIVE'));
    })().catch(() => { if (!controller.signal.aborted) setMessage('Chưa tải được nhóm kỹ năng.'); });
    return () => controller.abort();
  }, []);
  const update = (index: number, patch: Partial<CanonicalSkillInput>) => setSkills(items => items.map((s, i) => i === index ? { ...s, ...patch } : s));
  const updateCustom = (index: number, patch: Partial<CustomSkillInput>) => setCustom(items => items.map((s, i) => i === index ? { ...s, ...patch } : s));
  const save = async (event: FormEvent) => {
    event.preventDefault(); if (disabled || busy) return;
    const input = { ...toProfileInput(view), skillsDeclared: declared, skills: declared ? skills : [], customSkills: declared ? custom : [] };
    if (!declared && (skills.length || custom.length) && !window.confirm('Bỏ khai báo sẽ xóa các kỹ năng trong bản lưu này. Tiếp tục?')) return;
    const body = JSON.stringify(input); if (pending.current?.body !== body) pending.current = { body, key: newIdempotencyKey() };
    setBusy(true); setMessage('');
    try { const saved = await saveMyProfile(input, pending.current.key); pending.current = null; onSaved(saved); }
    catch { setMessage('Chưa lưu được kỹ năng. Nếu hồ sơ đã thay đổi ở nơi khác, tải lại trước khi lưu.'); }
    finally { setBusy(false); }
  };
  const suggestions = catalog.filter(c => !skills.some(s => s.skillCode === c.skillCode));
  // Frame 477:11292 "Kỹ năng đã khai báo": hai nhóm (trong / ngoài danh mục), mỗi kỹ năng là một thẻ có tên, dòng tóm tắt,
  // nút xóa ở góc và các ô sửa; nút lưu tách xuống cuối. Tiêu chí hoàn thiện hiển thị ở thẻ hồ sơ đầu trang (CompletenessChecklist).
  return <section className="panel workflow-surface student-skills"><h2 className="student-card-title"><Icon name="layers" size={17} />Kỹ năng và mức hoàn thiện hồ sơ</h2>
    <p className="student-card-note">{view.completeness.complete ? 'Đủ thông tin theo chính sách hồ sơ.' : 'Hồ sơ còn thông tin cần bổ sung.'}</p>
    <Notice message={message} tone="error" />
    {disabled && <p>Lưu hoặc tải lại thông tin chung trước khi chỉnh kỹ năng.</p>}
    <form onSubmit={save}><fieldset disabled={disabled || busy}>
      <label className="student-checkbox"><input type="checkbox" checked={declared} onChange={e => setDeclared(e.target.checked)} />Tôi đã khai báo kỹ năng, kể cả khi danh sách rỗng</label>
      {declared && <>
        <div className="student-skill-group">
          <div className="student-skill-head"><h3>Kỹ năng trong danh mục</h3><span className="student-status tone-brand">{skills.length} kỹ năng</span></div>
          <label>Tìm kỹ năng chuẩn<input type="search" value={query} placeholder="Nhập tên kỹ năng, ví dụ: Java" onChange={e => setQuery(e.target.value)} /></label>
          {suggestions.length > 0 && <div className="student-skill-suggest">{suggestions.map(c => <button key={c.skillCode} className="secondary" type="button"
            onClick={() => setSkills([...skills, { skillCode: c.skillCode, level: 1, yearsPractice: 0, note: null }])}><Icon name="plus" size={15} />Thêm {c.name}</button>)}</div>}
          {skills.length === 0 && <p className="student-card-note">Chưa có kỹ năng chuẩn nào. Tìm theo tên rồi bấm "Thêm" để đưa vào hồ sơ.</p>}
          {skills.map((s, i) => <fieldset className="student-skill-row" key={s.skillCode}><legend>{s.skillCode} <span className="student-skill-source">Tự khai</span></legend>
            <button type="button" className="text-button student-skill-remove" onClick={() => setSkills(skills.filter((_, n) => n !== i))}><Icon name="close" size={15} />Xóa<span className="student-sr-only"> {s.skillCode}</span></button>
            <p className="student-skill-summary">Cấp độ {s.level}/5 · {s.yearsPractice} năm thực hành · danh mục kỹ năng</p>
            <label>Mức độ (1–5)<input type="number" min="1" max="5" required value={s.level} onChange={e => update(i, { level: Number(e.target.value) })} /></label>
            <label>Số năm thực hành<input type="number" min="0" step="any" required value={s.yearsPractice} onChange={e => update(i, { yearsPractice: Number(e.target.value) })} /></label>
            <label className="student-skill-note">Ghi chú<input maxLength={1000} value={s.note || ''} onChange={e => update(i, { note: e.target.value || null })} /></label>
          </fieldset>)}
        </div>
        <div className="student-skill-group">
          <div className="student-skill-head"><h3>Kỹ năng ngoài danh mục</h3><span className="student-status">{custom.length} kỹ năng</span></div>
          <p className="student-card-note">Các kỹ năng này được lưu trong hồ sơ và chưa dùng để đối chiếu tiêu chí tuyển.</p>
          {custom.map((s, i) => <fieldset className="student-skill-row custom" key={s.customSkillId || i}><legend>Kỹ năng tự khai {i + 1}</legend>
            <button type="button" className="text-button student-skill-remove" onClick={() => setCustom(custom.filter((_, n) => n !== i))}><Icon name="close" size={15} />Xóa<span className="student-sr-only"> kỹ năng tự khai {i + 1}</span></button>
            <p className="student-skill-summary">Kỹ năng tự do: {s.name.trim() || 'chưa đặt tên'} · Cấp độ {s.level}/5 · chưa chuẩn hóa</p>
            <label>Tên<input required maxLength={128} value={s.name} onChange={e => updateCustom(i, { name: e.target.value })} /></label>
            <label>Nhóm<select required value={s.categoryCode} onChange={e => updateCustom(i, { categoryCode: e.target.value })}><option value="">Chọn nhóm</option>{categories.map(c => <option key={c.categoryCode} value={c.categoryCode}>{c.name}</option>)}</select></label>
            <label>Mức độ<input type="number" min="1" max="5" required value={s.level} onChange={e => updateCustom(i, { level: Number(e.target.value) })} /></label>
            <label>Số năm<input type="number" min="0" step="any" required value={s.yearsPractice} onChange={e => updateCustom(i, { yearsPractice: Number(e.target.value) })} /></label>
            <label className="student-skill-note">Ghi chú<input maxLength={1000} value={s.note || ''} onChange={e => updateCustom(i, { note: e.target.value || null })} /></label>
          </fieldset>)}
          <button type="button" className="secondary student-skill-add" onClick={() => setCustom([...custom, { customSkillId: null, name: '', categoryCode: '', level: 1, yearsPractice: 0, note: null }])}><Icon name="plus" size={16} />Thêm kỹ năng ngoài danh mục</button>
        </div>
      </>}
      <div className="student-skill-actions"><button type="submit">{busy ? 'Đang lưu…' : 'Lưu kỹ năng'}</button></div>
    </fieldset></form>
  </section>;
}
