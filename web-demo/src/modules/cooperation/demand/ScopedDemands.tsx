import { useEffect, useId, useRef, useState } from 'react';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { PagerNav } from '../../../ui/PagerNav';
import { askConfirm } from '../../../confirm';
import { formatDateDMY } from '../../../ui/dateFormat';
import { listDemands } from '../api';
import type { Demand, DemandPage } from '../types';
import { canLeaveDemand } from '../navigation';
import { DemandEditor } from './DemandEditor';
import { failureMessage } from './errors';
import { DemandSummary } from './DemandSummary';
import { WorkflowDetailDialog } from '../ui/WorkflowDetailDialog';
import { recordRow } from '../ui/recordRow';
import { DemandSkillSummary } from './DemandSkillSummary';
import { useSchoolNames } from '../portal/useSchoolNames';
import { api } from '../../../api';
import { newIdempotencyKey } from '../../student/api';
import { DemandStatus, demandStatus } from './DemandStatus';
import type { Resource } from '../portal/data';
import type { CooperationRecord } from '../workflow/types';
import { demandInScope } from '../portal/scope';
import './demands.css';

export function ScopedDemands({ organizationId, cooperations, onLoaded, schoolOrgId = '', termId = '' }: {
  organizationId: string; cooperations: Resource<CooperationRecord>; onLoaded?: (items: Demand[]) => void; schoolOrgId?: string; termId?: string;
}) {
  const [pageIndex, setPageIndex] = useState(0), [refresh, setRefresh] = useState(0);
  const [page, setPage] = useState<DemandPage | null>(null), [loading, setLoading] = useState(true);
  const [listError, setListError] = useState(''), [success, setSuccess] = useState('');
  const [editor, setEditor] = useState<{ record: Demand | null; key: number } | null>(null);
  const [saving, setSaving] = useState(false), [unconfirmed, setUnconfirmed] = useState(false);
  const [includeDeleted, setIncludeDeleted] = useState(false);
  const [search, setSearch] = useState(''), [status, setStatus] = useState(''), [previewId, setPreviewId] = useState('');
  const prefix = useId();
  const nextEditor = useRef(0);
  const editorOpener = useRef<HTMLElement | null>(null);
  const previewOpener = useRef<HTMLElement | null>(null);
  const loadedRef = useRef(onLoaded);
  loadedRef.current = onLoaded;
  const submitKeys = useRef(new Map<string, string>());
  const permits = (record: Demand, allowedStates: string[]) => {
    if (!record.cooperationId) return true;
    if (cooperations.loading || cooperations.error) return false;
    const cooperation = cooperations.items.find(item => item.cooperationId === record.cooperationId
      && item.demandId === record.demandId && item.termId === record.data.termId
      && (!item.enterpriseOrgId || item.enterpriseOrgId === organizationId)
      && (!item.schoolOrgId || item.schoolOrgId === record.data.schoolOrgId));
    return Boolean(cooperation && allowedStates.includes(cooperation.state));
  };
  const lifecycleConfig = {
    reopen: {
      confirm: 'Mở lại nhu cầu để chỉnh sửa? Khi gửi hợp tác phải xác nhận phiên bản mới.',
      reasonCode: 'ENTERPRISE_REOPENED',
      success: 'Đã mở lại bản nháp.',
    },
    'soft-delete': {
      confirm: 'Xóa mềm nhu cầu này? Bạn có thể bật “Hiển thị cả nhu cầu đã xóa” để khôi phục lại sau.',
      reasonCode: 'ENTERPRISE_DELETED',
      success: 'Đã xóa nhu cầu.',
    },
    restore: {
      confirm: 'Khôi phục nhu cầu này về bản nháp?',
      reasonCode: 'ENTERPRISE_RESTORED',
      success: 'Đã khôi phục nhu cầu.',
    },
  } as const;
  const lifecycleAllowed = (record: Demand) => permits(record, ['DRAFT', 'REJECTED']);
  const lifecycle = async (record: Demand, operation: keyof typeof lifecycleConfig) => {
    if (saving || unconfirmed || !lifecycleAllowed(record)) return;
    const config = lifecycleConfig[operation];
    if (!canLeaveDemand() || !await askConfirm({
      title: operation === 'restore' ? 'Khôi phục nhu cầu?' : operation === 'soft-delete' ? 'Xóa nhu cầu?' : 'Mở lại nhu cầu?',
      message: config.confirm,
      confirmLabel: operation === 'restore' ? 'Khôi phục nhu cầu' : operation === 'soft-delete' ? 'Xóa nhu cầu' : 'Mở lại để chỉnh sửa',
      tone: operation === 'soft-delete' ? 'danger' : 'primary',
    })) return;
    const ref = operation + ':' + record.demandId + ':' + record.version;
    if (!submitKeys.current.has(ref)) submitKeys.current.set(ref, newIdempotencyKey());
    setSaving(true); setListError(''); setSuccess('');
    try {
      await api('/enterprise/demands/' + record.demandId + '/' + operation, { method: 'PATCH',
        headers: { 'X-Organization-Id': organizationId, 'Idempotency-Key': submitKeys.current.get(ref)! },
        body: JSON.stringify({ expectedVersion: record.version, reasonCode: config.reasonCode }) });
      submitKeys.current.delete(ref); setEditor(null);
      if (operation === 'soft-delete' && !includeDeleted && page && page.content.length === 1 && pageIndex > 0) setPageIndex(pageIndex - 1);
      else setRefresh(n => n + 1);
      setSuccess(config.success);
      window.dispatchEvent(new Event('cooperation-data-updated'));
    } catch (error) { setListError(failureMessage(error)); } finally { setSaving(false); }
  };
  const submitCooperation = async (record: Demand) => {
    const ref = record.demandId + ':' + record.version;
    if (!submitKeys.current.has(ref)) submitKeys.current.set(ref, newIdempotencyKey()); setSaving(true); setListError('');
    try { await api('/enterprise/demands/submit-with-cooperation', { method: 'POST', headers: { 'X-Organization-Id': organizationId, 'Idempotency-Key': submitKeys.current.get(ref)! }, body: JSON.stringify({ demandId: record.demandId, expectedDemandVersion: record.version }) }); submitKeys.current.delete(ref); setSuccess('Đã gửi đề nghị hợp tác.'); setRefresh(n => n + 1); window.dispatchEvent(new Event('cooperation-updated')); }
    catch (error) { setListError(failureMessage(error)); } finally { setSaving(false); }
  };
  useEffect(() => {
    let active = true;
    const controller = new AbortController();
    setLoading(true); setListError(''); setPage(null); setPreviewId('');
    void listDemands(organizationId, pageIndex, controller.signal, includeDeleted, { schoolOrgId, termId }).then(result => {
      if (active) {
        if (result.content.some(row => row.enterpriseOrgId !== organizationId || !demandInScope(row, { schoolOrgId, termId }))) throw new Error('Nhu cầu không thuộc phạm vi đang chọn.');
        setPage(result);
        loadedRef.current?.(result.content);
      }
    }).catch(error => {
      if (active) setListError(failureMessage(error));
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; controller.abort(); };
  }, [organizationId, schoolOrgId, termId, pageIndex, refresh, includeDeleted]);

  const open = (record: Demand | null) => {
    if (!canLeaveDemand()) return;
    editorOpener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    setSuccess(''); setUnconfirmed(false); setPreviewId('');
    setEditor({ record, key: ++nextEditor.current });
  };
  const blocked = saving || unconfirmed;
  const openPreview = (record: Demand, opener: HTMLElement, toggle = false) => {
    if (blocked || !canLeaveDemand()) return;
    previewOpener.current = opener;
    setEditor(null);
    setPreviewId(current => toggle && current === record.demandId ? '' : record.demandId);
  };
  const schools = useSchoolNames(organizationId, true);
  const query = search.trim().toLocaleLowerCase('vi');
  const visible = (page?.content || []).filter(record => (!status || demandStatus(record) === status)
    && (!query || [record.data.title, schools[record.data.schoolOrgId], record.data.location].filter(Boolean).join(' ').toLocaleLowerCase('vi').includes(query)));
  const preview = visible.find(record => record.demandId === previewId);
  return <>
    <section className="panel demand-list-panel">
      <div className="section-heading cooperation-heading"><div className="workflow-section-heading">
        <h3 className="workflow-subtoolbar-title">Danh sách nhu cầu</h3>
        {page && page.totalElements > 0 && <span className="workflow-count-pill">{page.totalElements} nhu cầu</span>}
        </div>
        <div className="button-row demand-list-actions">
          <button className="secondary" disabled={loading || blocked} onClick={() => {
            setRefresh(value => value + 1); window.dispatchEvent(new Event('cooperation-data-updated'));
          }}><Icon name="refresh" size={16} />Tải lại</button>
          <button className="t5-btn-teal" disabled={blocked} onClick={() => open(null)}><Icon name="plus" size={16} />Tạo nhu cầu</button>
        </div>
      </div>
      <Notice message={success} tone="success" />
      <Notice message={listError} tone="error" />
      <div className="demand-filter-toolbar">
      <label className="demand-deleted-filter" htmlFor={prefix + '-include-deleted'}>
        <input id={prefix + '-include-deleted'} type="checkbox" checked={includeDeleted} disabled={blocked}
          onChange={event => { if (canLeaveDemand()) {setIncludeDeleted(event.target.checked);setPageIndex(0);} }} />
        <span>Hiển thị cả nhu cầu đã xóa</span>
      </label>
      {!loading && page && page.content.length > 0 && <div className="demand-list-filters">
          <label className="demand-search" htmlFor={prefix + '-search'}>Tìm trong trang
            <span><Icon name="search" size={18} /><input id={prefix + '-search'} type="search" value={search}
              placeholder="Tên nhu cầu, trường hoặc địa điểm…" onChange={event => {setSearch(event.target.value);setPreviewId('');}} /></span>
          </label>
          <div className="demand-filter-status"><label htmlFor={prefix + '-status'}>Trạng thái nhu cầu</label><select id={prefix + '-status'} value={status}
            onChange={event => {setStatus(event.target.value);setPreviewId('');}}>
            <option value="">Tất cả trạng thái</option><option value="DRAFT">Bản nháp</option>
            <option value="READY">Đã sẵn sàng</option><option value="SENT">Đã gửi hợp tác</option>
          </select></div>
          {(search || status) && <button type="button" className="secondary" onClick={() => {setSearch('');setStatus('');setPreviewId('');}}>Xóa bộ lọc nhu cầu</button>}
        </div>}
      </div>
      {loading ? <LoadingState /> : page && page.content.length === 0 ? <EmptyState icon="briefcase" title="Chưa có nhu cầu"
        description="Tạo bản nháp đầu tiên cho doanh nghiệp này." /> : page && <>
        {(search || status) && <p className="demand-filter-result" role="status">{visible.length}/{page.content.length} nhu cầu trong trang hiện tại</p>}
        {!visible.length ? <EmptyState icon="search" title="Không tìm thấy nhu cầu phù hợp" description="Thử tên khác hoặc xóa bộ lọc để xem lại danh sách." /> :
        <div className="proposal-table portal-demands-table enterprise-mobile-list">
          <table>
            <colgroup><col className="demand-col-identity" /><col className="demand-col-status" />
              <col className="demand-col-period" /><col className="demand-col-quantity" /><col className="demand-col-actions" /></colgroup>
            <thead><tr><th>Vị trí / nhu cầu</th><th>Trạng thái</th><th>Thời gian thực tập</th><th>Chỉ tiêu</th><th>Thao tác</th></tr></thead>
            <tbody>{visible.map(record => <tr key={record.demandId}
              className={'demand-clickable-row' + (previewId === record.demandId ? ' table-row-selected' : '')}
              {...recordRow(row => openPreview(record, row), blocked)} aria-expanded={previewId === record.demandId}
              aria-controls={previewId === record.demandId ? prefix + '-preview' : undefined}
              >
              <th scope="row" data-label="Vị trí / nhu cầu"><div className="demand-list-identity"><span className="demand-position-icon"><Icon name="briefcase" size={19} /></span><div>
                <h3>{record.data.title}</h3><p className="demand-school-name">{schools[record.data.schoolOrgId] || 'Trường đối tác'}</p>
                <DemandSkillSummary required={record.data.required.skills.length} preferred={record.data.preferred.length} />
              </div></div></th>
              <td data-label="Trạng thái"><DemandStatus demand={record} />
                {record.validationState !== 'VALID' && <p className="field-hint">Tiêu chí cần kiểm tra</p>}</td>
              <td data-label="Thời gian thực tập"><div className="demand-period"><span><Icon name="calendar" size={15} />{formatDateDMY(record.data.required.dateRange.startDate)}</span><span>đến {formatDateDMY(record.data.required.dateRange.endDate)}</span></div></td>
              <td data-label="Chỉ tiêu"><span className="demand-quantity"><b>{record.data.quantity}</b> vị trí</span></td>
              <td data-label="Thao tác"><div className="button-row demand-row-actions">
                <button className="secondary" id={prefix + '-view-' + record.demandId} aria-expanded={previewId === record.demandId}
                  aria-controls={prefix + '-preview'} disabled={blocked} onClick={event => openPreview(record, event.currentTarget, true)}><Icon name="eye" size={14} />Xem chi tiết<span className="cooperation-sr-only"> {record.data.title}</span></button>
                {record.softDeletedAt ? lifecycleAllowed(record) && <button className="secondary" disabled={blocked} onClick={() => void lifecycle(record, 'restore')}><Icon name="refresh" size={14} />Khôi phục nhu cầu<span className="cooperation-sr-only"> {record.data.title}</span></button>
                  : record.state === 'READY' && lifecycleAllowed(record) && <button className="secondary" disabled={blocked} onClick={() => void lifecycle(record, 'reopen')}><Icon name="refresh" size={14} />Mở lại bản nháp<span className="cooperation-sr-only"> {record.data.title}</span></button>}
                {record.state === 'DRAFT' && !record.softDeletedAt && permits(record, ['DRAFT', 'REJECTED', 'NEEDS_CHANGES']) && <button className="secondary" disabled={blocked}
                  onClick={() => open(record)}><Icon name="edit" size={14} />Sửa bản nháp<span className="cooperation-sr-only"> {record.data.title}</span></button>}
                {!record.cooperationId && record.state === 'DRAFT' && !record.softDeletedAt && <button className="t5-btn-teal" disabled={blocked}
                  onClick={() => void submitCooperation(record)}><Icon name="arrow-right" size={14} />Gửi yêu cầu hợp tác<span className="cooperation-sr-only"> {record.data.title}</span></button>}
                {!record.softDeletedAt && lifecycleAllowed(record) && <button className="danger" disabled={blocked}
                  onClick={() => void lifecycle(record, 'soft-delete')}><Icon name="trash" size={14} />Xóa nhu cầu<span className="cooperation-sr-only"> {record.data.title}</span></button>}
              </div></td>
            </tr>)}</tbody>
          </table>
        </div>}
        {page.totalPages > 1 && <PagerNav page={page.page} totalPages={page.totalPages} totalElements={page.totalElements} busy={blocked} onChange={setPageIndex} ariaLabel="Phân trang nhu cầu" />}
      </>}
    </section>
    {preview && <WorkflowDetailDialog key={preview.demandId} label="Chi tiết nhu cầu thực tập" onClose={() => setPreviewId('')}>
      <DemandSummary key={preview.demandId + ':' + preview.version} id={prefix + '-preview'} demand={preview} schoolName={schools[preview.data.schoolOrgId]}
      onClose={() => {
        setPreviewId('');
        if (previewOpener.current?.isConnected) previewOpener.current.focus();
        else document.getElementById(prefix + '-view-' + preview.demandId)?.focus();
      }} /></WorkflowDetailDialog>}
    {editor && <DemandEditor key={editor.key} organizationId={organizationId} existing={editor.record} schoolOrgId={schoolOrgId} termId={termId}
      onBusy={setSaving} onUnconfirmed={setUnconfirmed} onClose={() => { if (canLeaveDemand()) {setEditor(null);editorOpener.current?.focus();} }} onSaved={record => {
        setEditor(null); setUnconfirmed(false); setSuccess(`Đã lưu bản nháp “${record.data.title}”, phiên bản ${record.version}.`);
        setPageIndex(0); setRefresh(value => value + 1);
        window.dispatchEvent(new Event('cooperation-data-updated'));
      }} />}
  </>;
}
