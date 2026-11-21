// DEV08 đã tạo giao diện cây danh mục và biểu mẫu quản lý kỹ năng, trạng thái, tên gọi khác và phân giải tên.
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { api, send } from '../api';
import { EmptyState, Icon, Notice } from '../components';
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
  const [tab, setTab] = useState<'tree' | 'aliases'>('tree');
  const [search, setSearch] = useState('');
  const [editorMode, setEditorMode] = useState<'category' | 'skill' | 'edit' | null>(null);
  const [canWrite, setCanWrite] = useState(false);
  const pendingCommands = useRef(new Map<string, string>());
  async function command<T = unknown>(path: string, method: string, body: unknown): Promise<T> {
    if (!canWrite) throw new Error('Tài khoản chưa có quyền biên tập TAX.');
    const payload = JSON.stringify(body), signature = method + path + payload;
    const key = pendingCommands.current.get(signature) || crypto.randomUUID();
    pendingCommands.current.set(signature, key);
    const result = await api<T>(path, { method, body: payload, headers: { 'Idempotency-Key': key } });
    pendingCommands.current.delete(signature);
    return result;
  }
  useEffect(() => { void api<{ allowed: boolean }>('/taxonomy/editor-access').then(value => setCanWrite(value.allowed)).catch(() => setCanWrite(false)); }, []);
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
  const selectedItem = selectedSkill || selectedCategory;
  const parentName = selectedSkill
    ? skills.find(item => item.skillCode === selectedSkill.parentSkillCode)?.name || categories.find(item => item.categoryCode === selectedSkill.categoryCode)?.name
    : categories.find(item => item.categoryCode === selectedCategory?.parentCategoryCode)?.name;
  const chooseNode = (node: NodeRef) => { setSelected(node); setEditorMode(null); };
  const openCreator = (kind: 'category' | 'skill') => {
    if (!canWrite || busy) return;
    const category = selectedCategory?.categoryCode || selectedSkill?.categoryCode || '';
    setSelected(null); setEditorMode(kind);
    if (kind === 'category') { setCategoryCode(''); setCategoryName(''); setParentCategoryCode(category); }
    else { setSkillCode(''); setSkillName(''); setSkillCategoryCode(category); setParentSkillCode(''); }
  };
  const filtered = useMemo(() => {
    const query = search.trim().toLocaleLowerCase('vi-VN');
    if (!query) return { categories, skills };
    const categoryCodes = new Set<string>(), skillCodes = new Set<string>();
    const addCategory = (code: string | null) => {
      if (!code || categoryCodes.has(code)) return;
      categoryCodes.add(code);
      addCategory(categories.find(item => item.categoryCode === code)?.parentCategoryCode || null);
    };
    const addSkill = (code: string | null) => {
      if (!code || skillCodes.has(code)) return;
      skillCodes.add(code);
      const skill = skills.find(item => item.skillCode === code);
      if (skill) { addCategory(skill.categoryCode); addSkill(skill.parentSkillCode); }
    };
    categories.filter(item => (item.name + ' ' + item.categoryCode).toLocaleLowerCase('vi-VN').includes(query)).forEach(item => addCategory(item.categoryCode));
    skills.filter(item => (item.name + ' ' + item.skillCode).toLocaleLowerCase('vi-VN').includes(query)).forEach(item => addSkill(item.skillCode));
    return { categories: categories.filter(item => categoryCodes.has(item.categoryCode)), skills: skills.filter(item => skillCodes.has(item.skillCode)) };
  }, [search, categories, skills]);
  const roots = filtered.categories.filter(item => !item.parentCategoryCode);

  async function refresh() {
    const [categoryItems, skillData] = await Promise.all([
      loadAllPages(page => api<TaxonomyPage<TaxonomyCategory>>(`/taxonomy/categories?page=${page}&size=100`)),
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
    let active = true;
    setAliases([]);
    api<TaxonomyAlias[]>(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`)
      .then(value => { if (active) setAliases(value); }).catch(e => { if (active) setError(e instanceof Error ? e.message : 'Không tải được tên gọi khác.'); });
    setSkillCode(selectedSkill.skillCode); setSkillName(selectedSkill.name); setSkillCategoryCode(selectedSkill.categoryCode);
    setParentSkillCode(selectedSkill.parentSkillCode || '');
    return () => { active = false; };
  }, [selected?.kind, selected?.code, selectedSkill?.version]);
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
    void act(() => command('/taxonomy/categories', 'POST', { categoryCode, name: categoryName, parentCategoryCode: parentCategoryCode || null }), 'Đã thêm danh mục.');
  }
  function saveCategory(event: FormEvent) {
    event.preventDefault(); if (!selectedCategory) return;
    void act(() => command(`/taxonomy/categories/${encodeURIComponent(selectedCategory.categoryCode)}`, 'PATCH', {
      expectedVersion: selectedCategory.version, name: categoryName, status: selectedCategory.status
    }), 'Đã cập nhật danh mục.');
  }
  function createSkill(event: FormEvent) {
    event.preventDefault();
    void act(() => command('/taxonomy/skills', 'POST', { skillCode, name: skillName, categoryCode: skillCategoryCode, parentSkillCode: parentSkillCode || null }), 'Đã thêm kỹ năng.');
  }
  function saveSkill(event: FormEvent) {
    event.preventDefault(); if (!selectedSkill) return;
    void act(() => command(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}`, 'PATCH', {
      expectedVersion: selectedSkill.version, name: skillName, categoryCode: skillCategoryCode,
      parentSkillCode: parentSkillCode || null, status: selectedSkill.status
    }), 'Đã cập nhật kỹ năng.');
  }
  function toggleStatus() {
    if (selectedCategory) void act(() => command(`/taxonomy/categories/${encodeURIComponent(selectedCategory.categoryCode)}`, 'PATCH', {
      expectedVersion: selectedCategory.version, name: selectedCategory.name,
      status: selectedCategory.status === 'ACTIVE' ? 'DEPRECATED' : 'ACTIVE'
    }), 'Đã đổi trạng thái danh mục.');
    if (selectedSkill) void act(() => command(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}`, 'PATCH', {
      expectedVersion: selectedSkill.version, name: selectedSkill.name, categoryCode: selectedSkill.categoryCode,
      parentSkillCode: selectedSkill.parentSkillCode, status: selectedSkill.status === 'ACTIVE' ? 'DEPRECATED' : 'ACTIVE'
    }), 'Đã đổi trạng thái kỹ năng.');
  }
  function createAlias(event: FormEvent) {
    event.preventDefault(); if (!selectedSkill) return;
    void act(() => command(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`, 'POST', {
      alias: aliasText, expectedVersion: selectedSkill.version
    }), 'Đã thêm tên gọi khác.');
  }
  function deleteAlias(alias: TaxonomyAlias) {
    if (!selectedSkill) return;
    void act(() => command(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases/${alias.aliasId}`, 'DELETE', { expectedVersion: alias.version }), 'Đã gỡ tên gọi khác.');
  }
  async function resolveNames(event: FormEvent) {
    event.preventDefault(); setError(''); setLookup([]);
    try {
      const result = await send<{ items: TaxonomyResolution[] }>('/taxonomy/skills/resolve', 'POST', { values: lookupText.split('\n').map(x => x.trim()).filter(Boolean) });
      setLookup(result.items);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không phân giải được tên.'); }
  }

  return <section className="taxonomy-module">
    <header className="taxonomy-summary"><h2>Danh mục kỹ năng chuẩn hóa</h2>
      <div className="taxonomy-version"><span>Phiên bản danh mục</span><strong>{version}</strong></div>
    </header>
    {error && <Notice message={error} />}{message && <div className="taxonomy-success" role="status">{message}</div>}
    <div className="taxonomy-tabs" role="tablist" aria-label="Chế độ danh mục">
      {([['tree', 'Cây kỹ năng chuẩn'], ['aliases', 'Tên thay thế & Tra cứu']] as const).map(([id, title]) => <button
        type="button" role="tab" key={id} id={'taxonomy-tab-' + id} aria-selected={tab === id} tabIndex={tab === id ? 0 : -1}
        aria-controls={'taxonomy-panel-' + id} onClick={() => setTab(id)} onKeyDown={event => {
          if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
          event.preventDefault();
          const next = event.key === 'Home' ? 'tree' : event.key === 'End' ? 'aliases' : id === 'tree' ? 'aliases' : 'tree';
          setTab(next); document.getElementById('taxonomy-tab-' + next)?.focus();
        }}>{title}</button>)}
    </div>
    <div className="taxonomy-layout" role="tabpanel" id="taxonomy-panel-tree" aria-labelledby="taxonomy-tab-tree" hidden={tab !== 'tree'}>
      <section className="panel taxonomy-tree-panel"><div className="taxonomy-heading"><div><p className="section-kicker">CẤU TRÚC</p><h2>Cây danh mục</h2></div>
        <button type="button" className="secondary" disabled={busy} onClick={() => { setSelected(null); setEditorMode(null); void refresh().catch(e => setError(String(e))); }}><Icon name="refresh" /> Tải lại</button></div>
        <label>Tìm kỹ năng<input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder="Tìm theo tên hoặc mã" /></label>
        {roots.length === 0 ? <p className="taxonomy-empty">{search.trim() ? 'Không có kỹ năng hoặc danh mục phù hợp.' : 'Chưa có danh mục. Có thể tạo danh mục đầu tiên ở bên cạnh.'}</p>
          : <ul className="taxonomy-tree">{roots.map(item => <CategoryBranch key={item.categoryCode} category={item} categories={filtered.categories} skills={filtered.skills} selected={selected} choose={chooseNode} />)}</ul>}
        <div className="taxonomy-tree-actions"><button type="button" className="secondary" disabled={busy || !canWrite} onClick={() => openCreator('category')}>Thêm danh mục</button>
          <button type="button" className="secondary" disabled={busy || !canWrite || categories.length === 0} onClick={() => openCreator('skill')}>Thêm kỹ năng</button>
          <button type="button" className="secondary" disabled={busy || !canWrite || !selectedItem} onClick={toggleStatus}>{selectedItem?.status === 'DEPRECATED' ? 'Kích hoạt lại' : 'Ngừng sử dụng'}</button></div>
        {!canWrite && <p className="taxonomy-empty">Bạn đang xem danh mục. Quyền biên tập được cấp đích danh.</p>}
      </section>
      <section className="panel taxonomy-editor">
        {editorMode === 'category' ? <><div className="taxonomy-heading"><h2>Tạo danh mục</h2><button type="button" className="text-button" disabled={busy} onClick={() => setEditorMode(null)}>Đóng biểu mẫu</button></div>
          <form onSubmit={createCategory}><label>Mã danh mục<input required pattern="[A-Z][A-Z0-9_]{0,63}" value={categoryCode} onChange={e => setCategoryCode(e.target.value)} placeholder="PROGRAMMING" /></label>
            <label>Tên danh mục<input required maxLength={128} value={categoryName} onChange={e => setCategoryName(e.target.value)} /></label>
            <label>Mã danh mục cha<select value={parentCategoryCode} onChange={e => setParentCategoryCode(e.target.value)}><option value="">Không có</option>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name} · {item.categoryCode}</option>)}</select></label>
            <button disabled={busy || !canWrite}>Lưu danh mục mới</button></form>
        </> : editorMode === 'skill' ? <><div className="taxonomy-heading"><h2>Tạo kỹ năng</h2><button type="button" className="text-button" disabled={busy} onClick={() => setEditorMode(null)}>Đóng biểu mẫu</button></div>
          <form onSubmit={createSkill}><label>Mã kỹ năng<input required pattern="[A-Z][A-Z0-9_]{0,63}" value={skillCode} onChange={e => setSkillCode(e.target.value)} placeholder="JAVA" /></label>
            <label>Tên chuẩn<input required maxLength={128} value={skillName} onChange={e => setSkillName(e.target.value)} /></label>
            <label>Danh mục<select required value={skillCategoryCode} onChange={e => setSkillCategoryCode(e.target.value)}><option value="">Chọn danh mục</option>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name} · {item.categoryCode}</option>)}</select></label>
            <label>Mã kỹ năng cha (nếu có)<input value={parentSkillCode} onChange={e => setParentSkillCode(e.target.value)} /></label>
            <button disabled={busy || !canWrite || categories.length === 0}>Lưu kỹ năng mới</button></form>
        </> : selectedItem ? <><div className="taxonomy-heading"><h2>{selectedSkill ? 'Chi tiết kỹ năng' : 'Chi tiết danh mục'}</h2>
          <span className={'status-badge ' + selectedItem.status.toLowerCase()}><span className="status-dot" aria-hidden="true" />{selectedItem.status === 'ACTIVE' ? 'Đang hoạt động' : 'Ngừng sử dụng'}</span></div>
          <div className="taxonomy-detail-copy"><p className="section-kicker">{selectedSkill ? 'KỸ NĂNG CHUẨN' : 'DANH MỤC'}</p><h3>{selectedItem.name}</h3>
            <code className="subtle-code">{selectedSkill?.skillCode || selectedCategory?.categoryCode}</code>
            <p>Nhóm cha: <strong>{parentName || 'Không có'}</strong></p>
            {selectedSkill && <p>Tên thay thế: <span>{aliases.length ? aliases.map(alias => alias.alias).join(', ') : 'Chưa có tên thay thế'}</span></p>}</div>
          <div className="button-row"><button type="button" className="secondary" disabled={busy || !canWrite} aria-expanded={editorMode === 'edit'} onClick={() => setEditorMode(value => value === 'edit' ? null : 'edit')}>Chỉnh sửa</button>
            {selectedSkill && <button type="button" onClick={() => setTab('aliases')}>Xem tên thay thế</button>}</div>
        {editorMode === 'edit' && (selectedCategory ? <form onSubmit={saveCategory}><label>Tên danh mục<input required value={categoryName} onChange={e => setCategoryName(e.target.value)} /></label><button disabled={busy || !canWrite}>Lưu thay đổi</button></form>
          : <><form onSubmit={saveSkill}><label>Tên chuẩn<input required value={skillName} onChange={e => setSkillName(e.target.value)} /></label>
            <label>Danh mục<select value={skillCategoryCode} onChange={e => setSkillCategoryCode(e.target.value)}>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name}</option>)}</select></label>
            <label>Kỹ năng cha<input value={parentSkillCode} onChange={e => setParentSkillCode(e.target.value)} /></label><button disabled={busy || !canWrite}>Lưu thay đổi</button></form>
            </>)}
        </> : <EmptyState icon="layers" title="Chọn mục trong cây kỹ năng" description="Xem thông tin, tên thay thế hoặc mở biểu mẫu thêm mới bằng các nút bên trái." />}
      </section>
    </div>
    <div className="taxonomy-alias-layout" role="tabpanel" id="taxonomy-panel-aliases" aria-labelledby="taxonomy-tab-aliases" hidden={tab !== 'aliases'}>
      <section className="panel"><h2>Tên thay thế</h2><label>Kỹ năng quản lý tên thay thế<select value={selectedSkill?.skillCode || ''} onChange={event => setSelected(event.target.value ? { kind: 'skill', code: event.target.value } : null)}><option value="">Chọn kỹ năng</option>{skills.map(skill => <option key={skill.skillCode} value={skill.skillCode}>{skill.name} · {skill.skillCode}</option>)}</select></label>
        {selectedSkill ? <div className="taxonomy-aliases"><h3>Tên gọi khác</h3>{aliases.length ? <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên thay thế</th><th>Kỹ năng chuẩn</th><th>Thao tác</th></tr></thead><tbody>{aliases.map(alias => <tr key={alias.aliasId}><td>{alias.alias}</td><td>{selectedSkill.name}</td><td><button type="button" className="text-button" disabled={busy || !canWrite} onClick={() => deleteAlias(alias)}>Gỡ</button></td></tr>)}</tbody></table></div> : <p className="taxonomy-empty">Chưa có tên gọi khác.</p>}
          <form onSubmit={createAlias}><label>Thêm tên gọi khác<input required maxLength={128} value={aliasText} onChange={event => setAliasText(event.target.value)} placeholder="Tên thường dùng" /></label><button disabled={busy || !canWrite}>Thêm alias</button></form></div> : <p className="taxonomy-empty">Chọn kỹ năng để xem và quản lý tên thay thế.</p>}</section>
    <section className="panel taxonomy-resolver"><p className="section-kicker">TRA CỨU</p><h2>Phân giải tên kỹ năng</h2><p className="section-description">Nhập mỗi cách viết trên một dòng để xem chúng cùng trỏ về mã kỹ năng nào.</p>
      <form onSubmit={resolveNames}><label>Các tên cần tra cứu<textarea rows={3} value={lookupText} onChange={e => setLookupText(e.target.value)} placeholder={'Java Core\n JAVA '} /></label><button disabled={!lookupText.trim()}>Tra cứu</button></form>
      {lookup.length > 0 && <div className="table-wrap"><table className="data-table"><thead><tr><th>Tên tra cứu</th><th>Tên chuẩn hóa</th><th>Mã kỹ năng</th><th>Kết quả</th></tr></thead><tbody>{lookup.map((item, index) => <tr key={`${index}-${item.input}`}><td>{item.input}</td><td>{item.normalizedInput}</td><td>{item.skillCode || '—'}</td><td>{item.status === 'RESOLVED' ? 'Đã phân giải' : 'Chưa khớp'}</td></tr>)}</tbody></table></div>}
    </section>
    </div>
  </section>;
}
