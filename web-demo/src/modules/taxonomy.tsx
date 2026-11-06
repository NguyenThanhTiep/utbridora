// DEV08 đã tạo giao diện cây danh mục và biểu mẫu quản lý kỹ năng, trạng thái, tên gọi khác và phân giải tên.
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { api, send } from '../api';
import { Icon, Notice } from '../components';
import './taxonomy.css';

type TaxonomyCategory = { categoryCode: string; name: string; parentCategoryCode: string | null; status: 'ACTIVE' | 'DEPRECATED'; version: number };
type TaxonomySkill = { skillCode: string; name: string; categoryCode: string; parentSkillCode: string | null; status: 'ACTIVE' | 'DEPRECATED'; version: number };
type TaxonomyAlias = { aliasId: string; skillCode: string; alias: string; version: number };
type TaxonomyResolution = { input: string; normalizedInput: string; status: 'RESOLVED' | 'UNKNOWN'; skillCode: string | null };
type TaxonomyPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
type NodeRef = { kind: 'category' | 'skill'; code: string };

// DEV08 tải đủ các trang để cây danh mục không bỏ sót dữ liệu khi vượt quá 100 mục.
async function loadAllPages<T>(load: (page: number) => Promise<TaxonomyPage<T>>): Promise<T[]> {
  const firstPage = await load(0);
  const values = [...firstPage.content];
  for (let page = 1; page < firstPage.totalPages; page++) {
    values.push(...(await load(page)).content);
  }
  return values;
}

function CategoryBranch({ category, categories, skills, selected, choose }: {
  category: TaxonomyCategory; categories: TaxonomyCategory[]; skills: TaxonomySkill[];
  selected: NodeRef | null; choose: (node: NodeRef) => void;
}) {
  const children = categories.filter(item => item.parentCategoryCode === category.categoryCode);
  const roots = skills.filter(item => item.categoryCode === category.categoryCode && !item.parentSkillCode);
  return <li>
    <details open>
      <summary><button type="button" className={'taxonomy-node' + (selected?.kind === 'category' && selected.code === category.categoryCode ? ' selected' : '')}
        onClick={event => { event.preventDefault(); choose({ kind: 'category', code: category.categoryCode }); }}>
        <Icon name="layers" size={17} /><span>{category.name}<small>{category.categoryCode} · Danh mục {category.status === 'ACTIVE' ? 'đang dùng' : 'đã ngừng'}</small></span>
      </button></summary>
      {(children.length > 0 || roots.length > 0) && <ul>
        {children.map(item => <CategoryBranch key={item.categoryCode} category={item} categories={categories} skills={skills} selected={selected} choose={choose} />)}
        {roots.map(item => <SkillBranch key={item.skillCode} skill={item} allSkills={skills} selected={selected} choose={choose} />)}
      </ul>}
    </details>
  </li>;
}

function SkillBranch({ skill, allSkills, selected, choose }: {
  skill: TaxonomySkill; allSkills: TaxonomySkill[]; selected: NodeRef | null; choose: (node: NodeRef) => void;
}) {
  const children = allSkills.filter(item => item.parentSkillCode === skill.skillCode);
  return <li><details open={children.length > 0}>
    <summary><button type="button" className={'taxonomy-node skill-node' + (selected?.kind === 'skill' && selected.code === skill.skillCode ? ' selected' : '')}
      onClick={event => { event.preventDefault(); choose({ kind: 'skill', code: skill.skillCode }); }}>
      <Icon name="key" size={16} /><span>{skill.name}<small>{skill.skillCode} · {skill.status === 'ACTIVE' ? 'Đang dùng' : 'Đã ngừng'}</small></span>
    </button></summary>
    {children.length > 0 && <ul>{children.map(item => <SkillBranch key={item.skillCode} skill={item} allSkills={allSkills} selected={selected} choose={choose} />)}</ul>}
  </details></li>;
}

export function SkillTaxonomy() {
  const [categories, setCategories] = useState<TaxonomyCategory[]>([]);
  const [skills, setSkills] = useState<TaxonomySkill[]>([]);
  const [aliases, setAliases] = useState<TaxonomyAlias[]>([]);
  const [version, setVersion] = useState('—');
  const [selected, setSelected] = useState<NodeRef | null>(null);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [categoryCode, setCategoryCode] = useState('');
  const [categoryName, setCategoryName] = useState('');
  const [parentCategoryCode, setParentCategoryCode] = useState('');
  const [skillCode, setSkillCode] = useState('');
  const [skillName, setSkillName] = useState('');
  const [skillCategoryCode, setSkillCategoryCode] = useState('');
  const [parentSkillCode, setParentSkillCode] = useState('');
  const [aliasText, setAliasText] = useState('');
  const [lookupText, setLookupText] = useState('');
  const [lookup, setLookup] = useState<TaxonomyResolution[]>([]);

  const selectedCategory = selected?.kind === 'category' ? categories.find(item => item.categoryCode === selected.code) : undefined;
  const selectedSkill = selected?.kind === 'skill' ? skills.find(item => item.skillCode === selected.code) : undefined;
  const roots = useMemo(() => categories.filter(item => !item.parentCategoryCode), [categories]);

  async function refresh() {
    const [categoryItems, skillData] = await Promise.all([
      loadAllPages(page => api<TaxonomyPage<TaxonomyCategory>>(`/taxonomy/categories/page?page=${page}&size=100`)),
      (async () => {
        const first = await api<{ taxonomyVersion: string; skills: TaxonomyPage<TaxonomySkill> }>(
          '/taxonomy/skills?includeDeprecated=true&page=0&size=100');
        const items = [...first.skills.content];
        for (let page = 1; page < first.skills.totalPages; page++) {
          const next = await api<{ skills: TaxonomyPage<TaxonomySkill> }>(
            `/taxonomy/skills?includeDeprecated=true&page=${page}&size=100`);
          items.push(...next.skills.content);
        }
        return { taxonomyVersion: first.taxonomyVersion, items };
      })()
    ]);
    setCategories(categoryItems);
    setSkills(skillData.items);
    setVersion(skillData.taxonomyVersion);
  }

  useEffect(() => { refresh().catch(e => setError(e instanceof Error ? e.message : 'Không tải được danh mục.')); }, []);
  useEffect(() => {
    if (!selectedSkill) { setAliases([]); return; }
    api<TaxonomyAlias[]>(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`)
      .then(setAliases).catch(e => setError(e instanceof Error ? e.message : 'Không tải được tên gọi khác.'));
    setSkillCode(selectedSkill.skillCode); setSkillName(selectedSkill.name); setSkillCategoryCode(selectedSkill.categoryCode);
    setParentSkillCode(selectedSkill.parentSkillCode || '');
  }, [selected?.kind, selected?.code]);
  useEffect(() => {
    if (!selectedCategory) return;
    setCategoryCode(selectedCategory.categoryCode); setCategoryName(selectedCategory.name);
    setParentCategoryCode(selectedCategory.parentCategoryCode || '');
  }, [selected?.kind, selected?.code]);

  async function act(work: () => Promise<unknown>, done: string) {
    setBusy(true); setError(''); setMessage('');
    try { await work(); await refresh(); setMessage(done); }
    catch (e) { setError(e instanceof Error ? e.message : 'Thao tác chưa hoàn tất.'); }
    finally { setBusy(false); }
  }

  function createCategory(event: FormEvent) {
    event.preventDefault();
    void act(() => send('/taxonomy/categories', 'POST', { categoryCode, name: categoryName, parentCategoryCode: parentCategoryCode || null }), 'Đã thêm danh mục.');
  }
  function saveCategory(event: FormEvent) {
    event.preventDefault(); if (!selectedCategory) return;
    void act(() => send(`/taxonomy/categories/${encodeURIComponent(selectedCategory.categoryCode)}`, 'PATCH', {
      expectedVersion: selectedCategory.version, name: categoryName, status: selectedCategory.status
    }), 'Đã cập nhật danh mục.');
  }
  function createSkill(event: FormEvent) {
    event.preventDefault();
    void act(() => send('/taxonomy/skills', 'POST', { skillCode, name: skillName, categoryCode: skillCategoryCode, parentSkillCode: parentSkillCode || null }), 'Đã thêm kỹ năng.');
  }
  function saveSkill(event: FormEvent) {
    event.preventDefault(); if (!selectedSkill) return;
    void act(() => send(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}`, 'PATCH', {
      expectedVersion: selectedSkill.version, name: skillName, categoryCode: skillCategoryCode,
      parentSkillCode: parentSkillCode || null, status: selectedSkill.status
    }), 'Đã cập nhật kỹ năng.');
  }
  function toggleStatus() {
    if (selectedCategory) void act(() => send(`/taxonomy/categories/${encodeURIComponent(selectedCategory.categoryCode)}`, 'PATCH', {
      expectedVersion: selectedCategory.version, name: selectedCategory.name,
      status: selectedCategory.status === 'ACTIVE' ? 'DEPRECATED' : 'ACTIVE'
    }), 'Đã đổi trạng thái danh mục.');
    if (selectedSkill) void act(() => send(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}`, 'PATCH', {
      expectedVersion: selectedSkill.version, name: selectedSkill.name, categoryCode: selectedSkill.categoryCode,
      parentSkillCode: selectedSkill.parentSkillCode, status: selectedSkill.status === 'ACTIVE' ? 'DEPRECATED' : 'ACTIVE'
    }), 'Đã đổi trạng thái kỹ năng.');
  }
  function createAlias(event: FormEvent) {
    event.preventDefault(); if (!selectedSkill) return;
    void act(() => send(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`, 'POST', {
      alias: aliasText, expectedVersion: selectedSkill.version
    }), 'Đã thêm tên gọi khác.');
  }
  function deleteAlias(alias: TaxonomyAlias) {
    if (!selectedSkill) return;
    void act(() => send(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases/${alias.aliasId}`, 'DELETE', { expectedVersion: alias.version }), 'Đã gỡ tên gọi khác.');
  }
  async function resolveNames(event: FormEvent) {
    event.preventDefault(); setError(''); setLookup([]);
    try {
      const result = await send<{ items: TaxonomyResolution[] }>('/taxonomy/skills/resolve', 'POST', { values: lookupText.split('\n').map(x => x.trim()).filter(Boolean) });
      setLookup(result.items);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không phân giải được tên.'); }
  }

  return <section className="taxonomy-module">
    <header className="taxonomy-summary panel"><div><p className="section-kicker">T4 · TAXONOMY</p><h1>Danh mục kỹ năng chuẩn</h1>
      <p className="section-description">Một mã chuẩn cho kỹ năng và mọi tên gọi tương đương, dùng thống nhất giữa các nhóm.</p></div>
      <div className="taxonomy-version"><span>Phiên bản danh mục</span><strong>{version}</strong></div>
    </header>
    {error && <Notice message={error} />}{message && <div className="taxonomy-success" role="status">{message}</div>}
    <div className="taxonomy-layout">
      <section className="panel taxonomy-tree-panel"><div className="taxonomy-heading"><div><p className="section-kicker">CẤU TRÚC</p><h2>Cây danh mục</h2></div>
        <button type="button" className="secondary" onClick={() => { setSelected(null); void refresh().catch(e => setError(String(e))); }}><Icon name="refresh" /> Tải lại</button></div>
        {roots.length === 0 ? <p className="taxonomy-empty">Chưa có danh mục. Có thể tạo danh mục đầu tiên ở bên cạnh.</p>
          : <ul className="taxonomy-tree">{roots.map(item => <CategoryBranch key={item.categoryCode} category={item} categories={categories} skills={skills} selected={selected} choose={setSelected} />)}</ul>}
      </section>
      <div className="taxonomy-side">
        <section className="panel"><p className="section-kicker">THÊM MỚI</p><h2>Tạo danh mục</h2>
          <form onSubmit={createCategory}><label>Mã danh mục<input required pattern="[A-Z][A-Z0-9_]{0,63}" value={categoryCode} onChange={e => setCategoryCode(e.target.value)} placeholder="PROGRAMMING" /></label>
            <label>Tên danh mục<input required maxLength={128} value={categoryName} onChange={e => setCategoryName(e.target.value)} /></label>
            <label>Mã danh mục cha<select value={parentCategoryCode} onChange={e => setParentCategoryCode(e.target.value)}><option value="">Không có</option>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name} · {item.categoryCode}</option>)}</select></label>
            <button disabled={busy}>Thêm danh mục</button></form>
        </section>
        <section className="panel"><p className="section-kicker">THÊM MỚI</p><h2>Tạo kỹ năng</h2>
          <form onSubmit={createSkill}><label>Mã kỹ năng<input required pattern="[A-Z][A-Z0-9_]{0,63}" value={skillCode} onChange={e => setSkillCode(e.target.value)} placeholder="JAVA" /></label>
            <label>Tên chuẩn<input required maxLength={128} value={skillName} onChange={e => setSkillName(e.target.value)} /></label>
            <label>Danh mục<select required value={skillCategoryCode} onChange={e => setSkillCategoryCode(e.target.value)}><option value="">Chọn danh mục</option>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name} · {item.categoryCode}</option>)}</select></label>
            <label>Mã kỹ năng cha (nếu có)<input value={parentSkillCode} onChange={e => setParentSkillCode(e.target.value)} /></label>
            <button disabled={busy || categories.length === 0}>Thêm kỹ năng</button></form>
        </section>
      </div>
      {(selectedCategory || selectedSkill) && <section className="panel taxonomy-editor"><div className="taxonomy-heading"><div><p className="section-kicker">ĐANG CHỌN</p><h2>{selectedCategory ? selectedCategory.name : selectedSkill?.name}</h2></div>
        <button className="secondary" disabled={busy} onClick={toggleStatus}>{(selectedCategory?.status || selectedSkill?.status) === 'ACTIVE' ? 'Ngừng sử dụng' : 'Kích hoạt lại'}</button></div>
        {selectedCategory ? <form onSubmit={saveCategory}><label>Tên danh mục<input required value={categoryName} onChange={e => setCategoryName(e.target.value)} /></label><button disabled={busy}>Lưu thay đổi</button></form>
          : <><form onSubmit={saveSkill}><label>Tên chuẩn<input required value={skillName} onChange={e => setSkillName(e.target.value)} /></label>
            <label>Danh mục<select value={skillCategoryCode} onChange={e => setSkillCategoryCode(e.target.value)}>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name}</option>)}</select></label>
            <label>Kỹ năng cha<input value={parentSkillCode} onChange={e => setParentSkillCode(e.target.value)} /></label><button disabled={busy}>Lưu thay đổi</button></form>
            <div className="taxonomy-aliases"><h3>Tên gọi khác</h3>{aliases.length ? <ul>{aliases.map(alias => <li key={alias.aliasId}><span>{alias.alias}</span><button type="button" className="text-button" disabled={busy} onClick={() => deleteAlias(alias)}>Gỡ</button></li>)}</ul> : <p className="taxonomy-empty">Chưa có tên gọi khác.</p>}
              <form onSubmit={createAlias}><label>Thêm tên gọi khác<input required maxLength={128} value={aliasText} onChange={e => setAliasText(e.target.value)} placeholder="Tên thường dùng" /></label><button disabled={busy}>Thêm alias</button></form></div></>}
      </section>}
    </div>
    <section className="panel taxonomy-resolver"><p className="section-kicker">TRA CỨU</p><h2>Phân giải tên kỹ năng</h2><p className="section-description">Nhập mỗi cách viết trên một dòng để xem chúng cùng trỏ về mã kỹ năng nào.</p>
      <form onSubmit={resolveNames}><label>Các tên cần tra cứu<textarea rows={3} value={lookupText} onChange={e => setLookupText(e.target.value)} placeholder={'Java Core\n JAVA '} /></label><button disabled={!lookupText.trim()}>Tra cứu</button></form>
      {lookup.length > 0 && <div className="taxonomy-results">{lookup.map((item, index) => <div key={`${index}-${item.input}`}><span>{item.input}</span><strong>{item.skillCode || 'Chưa khớp'}{item.skillCode ? ` · ${item.status}` : ''}</strong></div>)}</div>}
    </section>
  </section>;
}
