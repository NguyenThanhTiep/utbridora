import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from '../../../api';
import { Notice } from '../../../components';
import { newIdempotencyKey, saveMyProfile, toProfileInput } from '../api';
import type { CanonicalSkillInput, CustomSkillInput, SelfProfileView, ContractPage } from '../types';
import { readSkillCatalog, type CatalogSkill } from '../../cooperation/demand/skillCatalog';

const checkLabels: Record<string, string> = {
  MAJOR_VALID: 'Ngành hợp lệ', STUDENT_TERM_PRESENT: 'Liên kết sinh viên với học kỳ', SKILLS_DECLARED: 'Đã khai báo kỹ năng',
  SKILLS_VALID: 'Kỹ năng chuẩn hợp lệ', AVAILABILITY_DECLARED: 'Đã khai báo lịch rảnh', AVAILABILITY_VALID: 'Khoảng thời gian hợp lệ'
};
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
  return <section className="panel workflow-surface"><h2>Kỹ năng và mức hoàn thiện hồ sơ</h2>
    <p>{view.completeness.complete ? 'Đủ thông tin theo chính sách hồ sơ.' : 'Hồ sơ còn thông tin cần bổ sung.'}</p>
    <ul aria-label="Mức hoàn thiện hồ sơ">{view.completeness.checks.map(c => <li key={c.code}>{c.passed ? 'Đạt' : 'Cần bổ sung'}: {checkLabels[c.code]}</li>)}</ul>
    <Notice message={message} tone="error" />
    {disabled && <p>Lưu hoặc tải lại thông tin chung trước khi chỉnh kỹ năng.</p>}
    <form onSubmit={save}><fieldset disabled={disabled || busy}>
      <label><input type="checkbox" checked={declared} onChange={e => setDeclared(e.target.checked)} />Tôi đã khai báo kỹ năng, kể cả khi danh sách rỗng</label>
      {declared && <><label>Tìm kỹ năng chuẩn<input value={query} onChange={e => setQuery(e.target.value)} /></label>
        <div className="button-row">{catalog.filter(c => !skills.some(s => s.skillCode === c.skillCode)).map(c => <button key={c.skillCode} className="secondary" type="button" onClick={() => setSkills([...skills, { skillCode: c.skillCode, level: 1, yearsPractice: 0, note: null }])}>Thêm {c.name}</button>)}</div>
        {skills.map((s, i) => <fieldset key={s.skillCode}><legend>{s.skillCode} · Tự khai</legend>
          <label>Mức độ (1–5)<input type="number" min="1" max="5" required value={s.level} onChange={e => update(i, { level: Number(e.target.value) })} /></label>
          <label>Số năm thực hành<input type="number" min="0" step="0.01" required value={s.yearsPractice} onChange={e => update(i, { yearsPractice: Number(e.target.value) })} /></label>
          <label>Ghi chú<input maxLength={1000} value={s.note || ''} onChange={e => update(i, { note: e.target.value || null })} /></label>
          <button type="button" className="secondary" onClick={() => setSkills(skills.filter((_, n) => n !== i))}>Xóa {s.skillCode}</button>
        </fieldset>)}
        <h3>Kỹ năng ngoài danh mục</h3><p>Các kỹ năng này được lưu trong hồ sơ và chưa dùng để đối chiếu tiêu chí tuyển.</p>
        {custom.map((s, i) => <fieldset key={s.customSkillId || i}><legend>Kỹ năng tự khai {i + 1}</legend>
          <label>Tên<input required maxLength={128} value={s.name} onChange={e => updateCustom(i, { name: e.target.value })} /></label>
          <label>Nhóm<select required value={s.categoryCode} onChange={e => updateCustom(i, { categoryCode: e.target.value })}><option value="">Chọn nhóm</option>{categories.map(c => <option key={c.categoryCode} value={c.categoryCode}>{c.name}</option>)}</select></label>
          <label>Mức độ<input type="number" min="1" max="5" required value={s.level} onChange={e => updateCustom(i, { level: Number(e.target.value) })} /></label>
          <label>Số năm<input type="number" min="0" step="0.01" required value={s.yearsPractice} onChange={e => updateCustom(i, { yearsPractice: Number(e.target.value) })} /></label>
          <label>Ghi chú<input maxLength={1000} value={s.note || ''} onChange={e => updateCustom(i, { note: e.target.value || null })} /></label>
          <button type="button" className="secondary" onClick={() => setCustom(custom.filter((_, n) => n !== i))}>Xóa kỹ năng tự khai {i + 1}</button>
        </fieldset>)}<button type="button" className="secondary" onClick={() => setCustom([...custom, { customSkillId: null, name: '', categoryCode: '', level: 1, yearsPractice: 0, note: null }])}>Thêm kỹ năng ngoài danh mục</button>
      </>}
      <button type="submit">{busy ? 'Đang lưu…' : 'Lưu kỹ năng'}</button>
    </fieldset></form>
  </section>;
}
