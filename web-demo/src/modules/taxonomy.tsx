import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { api } from '../api';
import { Icon, Notice } from '../components';
import './taxonomy.css';

type TaxonomyCategory = { categoryCode: string; name: string; parentCategoryCode: string | null; status: 'ACTIVE' | 'DEPRECATED'; version: number };
type TaxonomySkill = { skillCode: string; name: string; categoryCode: string; parentSkillCode: string | null; status: 'ACTIVE' | 'DEPRECATED'; version: number };
type TaxonomyAlias = { aliasId: string; skillCode: string; alias: string; version: number };
type TaxonomyResolution = { input: string; normalizedInput: string; status: 'RESOLVED' | 'UNKNOWN'; skillCode: string | null };
type ReindexResult = { operationId: string; status: 'COMPLETED'; progressPercent: number; categoriesProcessed: number; skillsProcessed: number; aliasesProcessed: number; normalizedValuesChanged: number; taxonomyVersion: string; completedAt: string };
type TaxonomyPage<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
type NodeRef = { kind: 'category' | 'skill'; code: string };
type StatusFilter = 'ALL' | 'ACTIVE' | 'DEPRECATED';

async function loadAllPages<T>(load: (page: number) => Promise<TaxonomyPage<T>>): Promise<T[]> {
  const first = await load(0);
  const values = [...first.content];
  for (let page = 1; page < first.totalPages; page++) values.push(...(await load(page)).content);
  return values;
}

function CategoryBranch({ category, categories, skills, selected, choose, visibleCategories, visibleSkills }: {
  category: TaxonomyCategory; categories: TaxonomyCategory[]; skills: TaxonomySkill[];
  selected: NodeRef | null; choose: (node: NodeRef) => void;
  visibleCategories: Set<string>; visibleSkills: Set<string>;
}) {
  const children = categories.filter(item => item.parentCategoryCode === category.categoryCode && visibleCategories.has(item.categoryCode));
  const roots = skills.filter(item => item.categoryCode === category.categoryCode && !item.parentSkillCode && visibleSkills.has(item.skillCode));
  return <li>
    <details open>
      <summary><button type="button" className={'taxonomy-node' + (selected?.kind === 'category' && selected.code === category.categoryCode ? ' selected' : '')}
        onClick={event => { event.preventDefault(); choose({ kind: 'category', code: category.categoryCode }); }}>
        <Icon name="layers" size={17} /><span>{category.name}<small>{category.categoryCode} · Danh mục {category.status === 'ACTIVE' ? 'đang dùng' : 'đã ngừng'}</small></span>
      </button></summary>
      {(children.length > 0 || roots.length > 0) && <ul>
        {children.map(item => <CategoryBranch key={item.categoryCode} category={item} categories={categories} skills={skills} selected={selected} choose={choose} visibleCategories={visibleCategories} visibleSkills={visibleSkills} />)}
        {roots.map(item => <SkillBranch key={item.skillCode} skill={item} allSkills={skills} selected={selected} choose={choose} visibleSkills={visibleSkills} />)}
      </ul>}
    </details>
  </li>;
}

function SkillBranch({ skill, allSkills, selected, choose, visibleSkills }: {
  skill: TaxonomySkill; allSkills: TaxonomySkill[]; selected: NodeRef | null; choose: (node: NodeRef) => void; visibleSkills: Set<string>;
}) {
  const children = allSkills.filter(item => item.parentSkillCode === skill.skillCode && visibleSkills.has(item.skillCode));
  return <li><details open={children.length > 0}>
    <summary><button type="button" className={'taxonomy-node skill-node' + (selected?.kind === 'skill' && selected.code === skill.skillCode ? ' selected' : '')}
      onClick={event => { event.preventDefault(); choose({ kind: 'skill', code: skill.skillCode }); }}>
      <Icon name="key" size={16} /><span>{skill.name}<small>{skill.skillCode} · {skill.status === 'ACTIVE' ? 'Đang dùng' : 'Đã ngừng'}</small></span>
    </button></summary>
    {children.length > 0 && <ul>{children.map(item => <SkillBranch key={item.skillCode} skill={item} allSkills={allSkills} selected={selected} choose={choose} visibleSkills={visibleSkills} />)}</ul>}
  </details></li>;
}

export function SkillTaxonomy() {
  const [canWrite, setCanWrite] = useState(false);
  const [categories, setCategories] = useState<TaxonomyCategory[]>([]);
  const [skills, setSkills] = useState<TaxonomySkill[]>([]);
  const [aliases, setAliases] = useState<TaxonomyAlias[]>([]);
  const [version, setVersion] = useState('—');
  const [selected, setSelected] = useState<NodeRef | null>(null);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [reindexing, setReindexing] = useState(false);
  const [reindexResult, setReindexResult] = useState<ReindexResult | null>(null);
  const [showCreateCategory, setShowCreateCategory] = useState(false);
  const [showCreateSkill, setShowCreateSkill] = useState(false);
  const pendingCommands = useRef(new Map<string, string>());

  const [createCategoryCode, setCreateCategoryCode] = useState('');
  const [createCategoryName, setCreateCategoryName] = useState('');
  const [createParentCategoryCode, setCreateParentCategoryCode] = useState('');
  const [categoryName, setCategoryName] = useState('');
  const [parentCategoryCode, setParentCategoryCode] = useState('');
  const [createSkillCode, setCreateSkillCode] = useState('');
  const [createSkillName, setCreateSkillName] = useState('');
  const [createSkillCategoryCode, setCreateSkillCategoryCode] = useState('');
  const [createParentSkillCode, setCreateParentSkillCode] = useState('');
  const [skillName, setSkillName] = useState('');
  const [skillCategoryCode, setSkillCategoryCode] = useState('');
  const [parentSkillCode, setParentSkillCode] = useState('');
  const [aliasText, setAliasText] = useState('');
  const [lookupText, setLookupText] = useState('');
  const [lookup, setLookup] = useState<TaxonomyResolution[]>([]);

  const selectedCategory = selected?.kind === 'category' ? categories.find(item => item.categoryCode === selected.code) : undefined;
  const selectedSkill = selected?.kind === 'skill' ? skills.find(item => item.skillCode === selected.code) : undefined;

  async function command<T = unknown>(path: string, method: string, body: unknown): Promise<T> {
    if (!canWrite) throw new Error('Tài khoản chưa có quyền biên tập taxonomy.');
    const payload = JSON.stringify(body), signature = method + path + payload;
    const key = pendingCommands.current.get(signature) || crypto.randomUUID();
    pendingCommands.current.set(signature, key);
    try {
      return await api<T>(path, { method, body: payload, headers: { 'Idempotency-Key': key } });
    } finally {
      pendingCommands.current.delete(signature);
    }
  }

  async function refresh() {
    const [categoryRows, skillData] = await Promise.all([
      loadAllPages(page => api<TaxonomyPage<TaxonomyCategory>>(`/taxonomy/categories?page=${page}&size=100`)),
      (async () => {
        const first = await api<{ taxonomyVersion: string; skills: TaxonomyPage<TaxonomySkill> }>('/taxonomy/skills?includeDeprecated=true&page=0&size=100');
        const items = [...first.skills.content];
        for (let page = 1; page < first.skills.totalPages; page++) {
          const next = await api<{ skills: TaxonomyPage<TaxonomySkill> }>(`/taxonomy/skills?includeDeprecated=true&page=${page}&size=100`);
          items.push(...next.skills.content);
        }
        return { taxonomyVersion: first.taxonomyVersion, items };
      })()
    ]);
    setCategories(categoryRows);
    setSkills(skillData.items);
    setVersion(skillData.taxonomyVersion);
  }

  useEffect(() => {
    void api<{ allowed: boolean }>('/taxonomy/editor-access').then(value => setCanWrite(value.allowed)).catch(() => setCanWrite(false));
    void refresh().catch(e => setError(e instanceof Error ? e.message : 'Không tải được danh mục.'));
  }, []);
  useEffect(() => {
    if (!selectedSkill) { setAliases([]); return; }
    api<TaxonomyAlias[]>(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`)
      .then(setAliases).catch(e => setError(e instanceof Error ? e.message : 'Không tải được alias.'));
    setSkillName(selectedSkill.name);
    setSkillCategoryCode(selectedSkill.categoryCode);
    setParentSkillCode(selectedSkill.parentSkillCode || '');
  }, [selected?.kind, selected?.code]);
  useEffect(() => {
    if (!selectedCategory) return;
    setCategoryName(selectedCategory.name);
    setParentCategoryCode(selectedCategory.parentCategoryCode || '');
  }, [selected?.kind, selected?.code]);

  const visible = useMemo(() => {
    const text = query.trim().toLocaleLowerCase();
    const categoryMatches = new Set(categories.filter(item =>
      (statusFilter === 'ALL' || item.status === statusFilter) &&
      (!text || `${item.categoryCode} ${item.name}`.toLocaleLowerCase().includes(text))
    ).map(item => item.categoryCode));
    const skillMatches = new Set(skills.filter(item =>
      (statusFilter === 'ALL' || item.status === statusFilter) &&
      (!text || `${item.skillCode} ${item.name} ${item.categoryCode}`.toLocaleLowerCase().includes(text))
    ).map(item => item.skillCode));
    const visibleSkills = new Set(skillMatches);
    for (const skill of skills) if (visibleSkills.has(skill.skillCode)) {
      let parent = skill.parentSkillCode;
      while (parent) {
        visibleSkills.add(parent);
        parent = skills.find(item => item.skillCode === parent)?.parentSkillCode || null;
      }
    }
    const visibleCategories = new Set(categoryMatches);
    for (const skill of visibleSkills) {
      const code = skills.find(item => item.skillCode === skill)?.categoryCode;
      if (code) visibleCategories.add(code);
    }
    for (const code of [...visibleCategories]) {
      let parent = categories.find(item => item.categoryCode === code)?.parentCategoryCode || null;
      while (parent) {
        visibleCategories.add(parent);
        parent = categories.find(item => item.categoryCode === parent)?.parentCategoryCode || null;
      }
    }
    return { visibleCategories, visibleSkills };
  }, [categories, skills, query, statusFilter]);
  const roots = categories.filter(item => !item.parentCategoryCode && visible.visibleCategories.has(item.categoryCode));

  async function act(work: () => Promise<unknown>, done: string) {
    setBusy(true); setError(''); setMessage('');
    try { await work(); await refresh(); setMessage(done); }
    catch (e) { setError(e instanceof Error ? e.message : 'Thao tác chưa hoàn tất.'); }
    finally { setBusy(false); }
  }

  function createCategory(event: FormEvent) {
    event.preventDefault();
    void act(() => command('/taxonomy/categories', 'POST', { categoryCode: createCategoryCode, name: createCategoryName, parentCategoryCode: createParentCategoryCode || null }), 'Đã thêm danh mục.');
    setShowCreateCategory(false);
  }
  function saveCategory(event: FormEvent) {
    event.preventDefault(); if (!selectedCategory) return;
    void act(() => command(`/taxonomy/categories/${encodeURIComponent(selectedCategory.categoryCode)}`, 'PATCH', {
      expectedVersion: selectedCategory.version, name: categoryName, status: selectedCategory.status
    }), 'Đã cập nhật danh mục.');
  }
  function createSkill(event: FormEvent) {
    event.preventDefault();
    void act(() => command('/taxonomy/skills', 'POST', { skillCode: createSkillCode, name: createSkillName, categoryCode: createSkillCategoryCode, parentSkillCode: createParentSkillCode || null }), 'Đã thêm kỹ năng.');
    setShowCreateSkill(false);
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
    void act(async () => {
      await command(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`, 'POST', { alias: aliasText, expectedVersion: selectedSkill.version });
      setAliasText('');
      setAliases(await api<TaxonomyAlias[]>(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`));
    }, 'Đã thêm tên gọi khác.');
  }
  function deleteAlias(alias: TaxonomyAlias) {
    if (!selectedSkill) return;
    void act(async () => {
      await command(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases/${alias.aliasId}`, 'DELETE', { expectedVersion: alias.version });
      setAliases(await api<TaxonomyAlias[]>(`/taxonomy/skills/${encodeURIComponent(selectedSkill.skillCode)}/aliases`));
    }, 'Đã gỡ tên gọi khác.');
  }
  async function resolveNames(event: FormEvent) {
    event.preventDefault(); setError(''); setLookup([]);
    try {
      const result = await api<{ items: TaxonomyResolution[] }>('/taxonomy/skills/resolve', {
        method: 'POST', body: JSON.stringify({ values: lookupText.split('\n').map(value => value.trim()).filter(Boolean) })
      });
      setLookup(result.items);
    } catch (e) { setError(e instanceof Error ? e.message : 'Không phân giải được tên.'); }
  }
  async function reindex() {
    setReindexing(true); setError(''); setMessage(''); setReindexResult(null);
    try {
      const result = await command<ReindexResult>('/taxonomy/reindex', 'POST', {});
      setReindexResult(result);
      setVersion(result.taxonomyVersion);
      setMessage(`Tái lập chỉ mục hoàn tất: ${result.categoriesProcessed} danh mục, ${result.skillsProcessed} kỹ năng, ${result.aliasesProcessed} alias.`);
      await refresh();
    } catch (e) { setError(e instanceof Error ? e.message : 'Không thể tái lập chỉ mục.'); }
    finally { setReindexing(false); }
  }

  return <section className="taxonomy-module">
    <header className="taxonomy-summary panel">
      <div><h1>Danh mục kỹ năng chuẩn</h1>
        <p className="section-description">Quản lý mã kỹ năng chuẩn, tên gọi khác và trạng thái sử dụng.</p></div>
      <div className="taxonomy-summary-actions"><div className="taxonomy-version"><span>Phiên bản danh mục</span><strong>{version}</strong></div>
        <button type="button" className="secondary" disabled={!canWrite || reindexing || busy} onClick={() => void reindex()}>
          <Icon name="refresh" /> {reindexing ? 'Đang tái lập…' : 'Tái lập chỉ mục'}
        </button></div>
    </header>
    {error && <Notice message={error} />}{message && <div className="taxonomy-success" role="status">{message}</div>}
    {reindexing && <section className="taxonomy-reindex-result panel" role="status" aria-live="polite">
      <div className="taxonomy-reindex-heading"><div><p className="section-kicker">ĐANG XỬ LÝ</p><strong>API đang tái lập chỉ mục taxonomy</strong></div><span>Đang chạy</span></div>
      <progress aria-label="API đang tái lập chỉ mục" />
      <p>Tác vụ chạy đồng bộ. Kết quả và phần trăm hoàn tất sẽ được cập nhật từ phản hồi API.</p>
    </section>}
    {reindexResult && <section className="taxonomy-reindex-result panel" aria-live="polite">
      <div className="taxonomy-reindex-heading"><div><p className="section-kicker">KẾT QUẢ REINDEX</p><strong>{reindexResult.status} · {reindexResult.taxonomyVersion}</strong></div><span>{reindexResult.progressPercent}%</span></div>
      <progress max={100} value={reindexResult.progressPercent} aria-label="Tiến độ tái lập chỉ mục" />
      <p>{reindexResult.categoriesProcessed} danh mục · {reindexResult.skillsProcessed} kỹ năng · {reindexResult.aliasesProcessed} alias được xử lý; {reindexResult.normalizedValuesChanged} giá trị chuẩn hóa được cập nhật.</p>
      <small>Mã thao tác: {reindexResult.operationId} · Hoàn tất: {new Date(reindexResult.completedAt).toLocaleString()}</small>
    </section>}
    <div className="taxonomy-layout">
      <section className="panel taxonomy-tree-panel">
        <div className="taxonomy-heading"><div><p className="section-kicker">CẤU TRÚC</p><h2>Cây danh mục</h2></div>
          <button type="button" className="secondary" disabled={busy} onClick={() => { setSelected(null); void refresh().catch(e => setError(String(e))); }}><Icon name="refresh" /> Tải lại</button></div>
        <div className="taxonomy-filters"><label className="taxonomy-search">Tìm mã hoặc tên
          <input type="search" value={query} onChange={event => setQuery(event.target.value)} placeholder="Ví dụ: JAVA, lập trình…" /></label>
          <label>Trạng thái<select value={statusFilter} onChange={event => setStatusFilter(event.target.value as StatusFilter)}>
            <option value="ALL">Tất cả</option><option value="ACTIVE">Đang dùng</option><option value="DEPRECATED">Đã ngừng</option>
          </select></label></div>
        {roots.length === 0 ? <p className="taxonomy-empty">{categories.length || skills.length ? 'Không có mục phù hợp với bộ lọc.' : 'Chưa có danh mục. Hãy tạo danh mục đầu tiên.'}</p>
          : <ul className="taxonomy-tree">{roots.map(item => <CategoryBranch key={item.categoryCode} category={item} categories={categories} skills={skills} selected={selected} choose={setSelected} visibleCategories={visible.visibleCategories} visibleSkills={visible.visibleSkills} />)}</ul>}
      </section>
      <aside className="taxonomy-side">
        {!canWrite && <Notice message="Bạn đang xem danh mục. Quyền biên tập taxonomy được cấp theo assignment." tone="info" />}
        {(selectedCategory || selectedSkill) ? <section className="panel taxonomy-editor">
          <div className="taxonomy-heading"><div><p className="section-kicker">CHI TIẾT {selectedCategory ? 'DANH MỤC' : 'KỸ NĂNG'}</p><h2>{selectedCategory?.name || selectedSkill?.name}</h2></div>
            <button type="button" className="secondary" disabled={busy || !canWrite} onClick={toggleStatus}>{(selectedCategory?.status || selectedSkill?.status) === 'ACTIVE' ? 'Ngừng sử dụng' : 'Kích hoạt lại'}</button></div>
          <p className="taxonomy-code">Mã chuẩn <strong>{selectedCategory?.categoryCode || selectedSkill?.skillCode}</strong> · Phiên bản {selectedCategory?.version || selectedSkill?.version}</p>
          {selectedCategory ? <form onSubmit={saveCategory}>
            <label>Tên danh mục<input required maxLength={128} value={categoryName} onChange={event => setCategoryName(event.target.value)} /></label>
            <label>Danh mục cha<input disabled value={parentCategoryCode || 'Không có'} /></label>
            <button disabled={busy || !canWrite}>Lưu thay đổi</button>
          </form> : <>
            <form onSubmit={saveSkill}>
              <label>Tên chuẩn<input required maxLength={128} value={skillName} onChange={event => setSkillName(event.target.value)} /></label>
              <label>Danh mục<select required value={skillCategoryCode} onChange={event => setSkillCategoryCode(event.target.value)}>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name} · {item.categoryCode}</option>)}</select></label>
              <label>Kỹ năng cha<input value={parentSkillCode} onChange={event => setParentSkillCode(event.target.value)} placeholder="Không bắt buộc" /></label>
              <button disabled={busy || !canWrite}>Lưu thay đổi</button>
            </form>
            <div className="taxonomy-aliases"><h3>Tên gọi khác</h3>
              {aliases.length ? <ul>{aliases.map(alias => <li key={alias.aliasId}><span>{alias.alias}</span><button type="button" className="text-button" disabled={busy || !canWrite} onClick={() => deleteAlias(alias)}>Gỡ</button></li>)}</ul> : <p className="taxonomy-empty">Chưa có tên gọi khác.</p>}
              <form onSubmit={createAlias}><label>Thêm tên gọi khác<input required maxLength={128} value={aliasText} onChange={event => setAliasText(event.target.value)} placeholder="Tên thường dùng" /></label><button disabled={busy || !canWrite}>Thêm alias</button></form>
            </div>
          </>}
        </section> : <section className="panel taxonomy-editor taxonomy-editor-empty"><p className="section-kicker">CHI TIẾT</p><h2>Chọn một node trong cây</h2><p className="section-description">Thông tin, thao tác chỉnh sửa và tên gọi khác sẽ hiển thị tại đây.</p></section>}
        <section className="panel taxonomy-create-panel"><div className="taxonomy-heading"><div><p className="section-kicker">THÊM MỚI</p><h2>Tạo trong taxonomy</h2></div></div>
          <div className="taxonomy-create-actions"><button type="button" className="secondary" disabled={!canWrite} onClick={() => setShowCreateCategory(value => !value)}>Thêm danh mục</button>
            <button type="button" className="secondary" disabled={!canWrite || categories.length === 0} onClick={() => setShowCreateSkill(value => !value)}>Thêm kỹ năng</button></div>
          {showCreateCategory && <form onSubmit={createCategory}>
            <label>Mã danh mục<input required pattern="[A-Z][A-Z0-9_]{0,63}" value={createCategoryCode} onChange={event => setCreateCategoryCode(event.target.value)} placeholder="PROGRAMMING" /></label>
            <label>Tên danh mục<input required maxLength={128} value={createCategoryName} onChange={event => setCreateCategoryName(event.target.value)} /></label>
            <label>Danh mục cha<select value={createParentCategoryCode} onChange={event => setCreateParentCategoryCode(event.target.value)}><option value="">Không có</option>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name} · {item.categoryCode}</option>)}</select></label>
            <button disabled={busy || !canWrite}>Tạo danh mục</button>
          </form>}
          {showCreateSkill && <form onSubmit={createSkill}>
            <label>Mã kỹ năng<input required pattern="[A-Z][A-Z0-9_]{0,63}" value={createSkillCode} onChange={event => setCreateSkillCode(event.target.value)} placeholder="JAVA" /></label>
            <label>Tên chuẩn<input required maxLength={128} value={createSkillName} onChange={event => setCreateSkillName(event.target.value)} /></label>
            <label>Danh mục<select required value={createSkillCategoryCode} onChange={event => setCreateSkillCategoryCode(event.target.value)}><option value="">Chọn danh mục</option>{categories.map(item => <option key={item.categoryCode} value={item.categoryCode}>{item.name} · {item.categoryCode}</option>)}</select></label>
            <label>Kỹ năng cha<input value={createParentSkillCode} onChange={event => setCreateParentSkillCode(event.target.value)} placeholder="Không bắt buộc" /></label>
            <button disabled={busy || !canWrite}>Tạo kỹ năng</button>
          </form>}
        </section>
      </aside>
    </div>
    <section className="panel taxonomy-resolver"><p className="section-kicker">TRA CỨU</p><h2>Phân giải tên kỹ năng</h2>
      <p className="section-description">Nhập mỗi cách viết trên một dòng để xem chúng trỏ về mã kỹ năng nào.</p>
      <form onSubmit={resolveNames}><label>Các tên cần tra cứu<textarea rows={3} value={lookupText} onChange={event => setLookupText(event.target.value)} placeholder={'Java Core\n JAVA '} /></label><button disabled={!lookupText.trim()}>Tra cứu</button></form>
      {lookup.length > 0 && <div className="taxonomy-results">{lookup.map((item, index) => <div key={`${index}-${item.input}`}><span>{item.input}<small>{item.normalizedInput}</small></span><strong>{item.skillCode || 'Chưa khớp'}{item.skillCode ? ` · ${item.status}` : ''}</strong></div>)}</div>}
    </section>
  </section>;
}
