import { useEffect, useId, useRef, useState } from 'react';
import { api } from '../../../api';
import { Notice } from '../../../components';
import type { DemandPage } from '../types';
import type { FormData } from './form';
import { failureMessage } from './errors';
import { readSkillCatalog } from './skillCatalog';

type School = { schoolOrgId: string; name: string };
type Term = { termId: string; termCode: string; startDate: string; endDate: string };
type Academic = { schoolOrgId: string; termId: string; academicCatalogVersion: string };
type Choice = { value: string; label: string };
const base = '/enterprise/demand-references';

async function readPages<T>(org: string, path: string, signal: AbortSignal): Promise<T[]> {
  const read = (page: number) => api<Omit<DemandPage, 'content'> & { content: T[] }>(
    `${base}/${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`, { signal, headers: { 'X-Organization-Id': org } });
  const first = await read(0), items = [...first.content];
  for (let page = 1; page < first.totalPages; page++) items.push(...(await read(page)).content);
  return items;
}

function useChoices<T>(key: string, revision: number, read: (signal: AbortSignal) => Promise<T>) {
  const request = useRef(read);request.current = read;
  const [state, setState] = useState<{ key: string; value?: T; error?: string }>({ key: '' });
  useEffect(() => {
    if (!key) return;
    const c = new AbortController();setState({ key });
    void request.current(c.signal).then(value => { if (!c.signal.aborted) setState({ key, value }); })
      .catch(error => { if (!c.signal.aborted) setState({ key, error: failureMessage(error) }); });
    return () => c.abort();
  }, [key, revision]);
  const current = key && state.key === key ? state : null;
  return { value: current?.value, error: current?.error, loading: !!key && !current?.value && !current?.error };
}

function ReferenceSelect({ label, value, options, placeholder, disabled, error, change }: {
  label: string; value: string; options: Choice[]; placeholder: string; disabled?: boolean; error: string; change: (value: string) => void;
}) {
  const id = useId();
  return <div><label htmlFor={id}>{label}</label><select id={id} required value={value} disabled={disabled}
    aria-invalid={!!error} aria-describedby={error ? id + '-error' : undefined} onChange={e => change(e.target.value)}>
    <option value="">{placeholder}</option>
    {value && !options.some(option => option.value === value) && <option value={value} disabled>{value} (đã chọn trước đó)</option>}
    {options.map(option => <option key={option.value} value={option.value}>{option.label}</option>)}
  </select>{error && <p className="field-error" id={id + '-error'}>{error}</p>}</div>;
}

export function DemandReferenceFields({ org, form, existing, change, fieldError }: {
  org: string; form: FormData; existing: boolean; change: (values: Partial<FormData>) => void; fieldError: (field: string) => string;
}) {
  const [revision, setRevision] = useState(0);
  const schools = useChoices(org, revision, signal => readPages<School>(org, 'schools', signal));
  const terms = useChoices(form.schoolOrgId ? org + ':' + form.schoolOrgId : '', revision,
    signal => readPages<Term>(org, 'terms?' + new URLSearchParams({ schoolOrgId: form.schoolOrgId }), signal));
  const academic = useChoices(form.schoolOrgId && form.termId ? org + ':' + form.schoolOrgId + ':' + form.termId : '', revision,
    signal => api<Academic>(base + '/academic-catalog?' + new URLSearchParams({ schoolOrgId: form.schoolOrgId, termId: form.termId }),
      { signal, headers: { 'X-Organization-Id': org } }));
  const taxonomy = useChoices(org, revision, signal => readSkillCatalog('', 0, signal));
  const errors = [...new Set([schools.error, terms.error, academic.error, taxonomy.error].filter(Boolean))];
  return <div className="cooperation-reference-fields">
    <div className="cooperation-form-grid">
      <ReferenceSelect label="Trường nhận nhu cầu" value={form.schoolOrgId} options={(schools.value || []).map(s => ({ value: s.schoolOrgId, label: s.name }))}
        placeholder={schools.loading ? 'Đang tải trường…' : 'Chọn trường'} disabled={existing || schools.loading || !schools.value?.length}
        error={fieldError('schoolOrgId')} change={schoolOrgId => change({ schoolOrgId, termId: '', academicCatalogVersion: '', majorCodes: '' })} />
      <ReferenceSelect label="Kỳ thực tập" value={form.termId} options={(terms.value || []).map(t => ({ value: t.termId, label: `${t.termCode} (${t.startDate} – ${t.endDate})` }))}
        placeholder={!form.schoolOrgId ? 'Chọn trường trước' : terms.loading ? 'Đang tải kỳ…' : 'Chọn kỳ thực tập'}
        disabled={existing || !form.schoolOrgId || terms.loading || !terms.value?.length} error={fieldError('termId')}
        change={termId => change({ termId, academicCatalogVersion: '', majorCodes: '' })} />
      <ReferenceSelect label="Phiên bản danh mục học vụ" value={form.academicCatalogVersion}
        options={academic.value ? [{ value: academic.value.academicCatalogVersion, label: academic.value.academicCatalogVersion + ' (hiện hành)' }] : []}
        placeholder={!form.termId ? 'Chọn kỳ trước' : academic.loading ? 'Đang tải phiên bản…' : 'Chọn phiên bản học vụ'}
        disabled={!academic.value || academic.loading} error={fieldError('academicCatalogVersion')} change={academicCatalogVersion => change({ academicCatalogVersion })} />
      <ReferenceSelect label="Phiên bản danh mục kỹ năng" value={form.taxonomyVersion}
        options={taxonomy.value ? [{ value: taxonomy.value.taxonomyVersion, label: taxonomy.value.taxonomyVersion + ' (hiện hành)' }] : []}
        placeholder={taxonomy.loading ? 'Đang tải phiên bản…' : 'Chọn phiên bản kỹ năng'} disabled={!taxonomy.value || taxonomy.loading}
        error={fieldError('taxonomyVersion')} change={taxonomyVersion => change({ taxonomyVersion })} />
    </div>
    {schools.value?.length === 0 && <p role="status">Chưa có trường đã xác minh để nhận nhu cầu.</p>}
    {form.schoolOrgId && terms.value?.length === 0 && <p role="status">Trường này chưa có kỳ thực tập đang hoạt động.</p>}
    {errors.map(message => <Notice key={message} message={message!} tone="error" />)}
    <button type="button" className="secondary" onClick={() => setRevision(n => n + 1)}>Tải lại trường, kỳ và danh mục</button>
  </div>;
}
