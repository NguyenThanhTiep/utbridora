import { useEffect, useRef, useState } from 'react';
import { api, apiBlob, ApiError } from '../../../api';
import { EmptyState, Icon, LoadingState, Notice } from '../../../components';
import { PagerNav } from '../../../ui/PagerNav';
import { workflowError } from '../proposals/api';
import { recordRow } from '../ui/recordRow';
import { WorkflowDetailDialog } from '../ui/WorkflowDetailDialog';
import './school-reports.css';

type Counts = { matched: number; missingData: number; notMatched: number };
type ReportRow = Counts & { matchRunId: string; demandId: string; title: string; enterpriseName: string; termId: string;
  termCode: string; completedAt: string; demandVersion: number; criteriaVersion: number; criteriaCurrent: boolean };
type Report = { schoolOrgId: string; content: ReportRow[]; page: number; size: number; totalElements: number; totalPages: number;
  totals: Counts & { demands: number; compared: number }; generatedAt: string };
const count = (value: unknown): value is number => typeof value === 'number' && Number.isSafeInteger(value) && value >= 0;
const counts = (value: Counts) => ['matched', 'missingData', 'notMatched'].every(key => count(value[key as keyof Counts]));
function valid(value: Report, org: string, term: string, page: number): boolean {
  return !!value && value.schoolOrgId === org && value.page === page && value.size === 10 && count(value.totalElements)
    && count(value.totalPages) && value.totalPages === Math.ceil(value.totalElements / 10) && !!value.totals
    && counts(value.totals) && value.totals.demands === value.totalElements && count(value.totals.compared)
    && value.totals.compared === value.totals.matched + value.totals.missingData + value.totals.notMatched
    && Number.isFinite(Date.parse(value.generatedAt)) && Array.isArray(value.content) && value.content.length <= 10
    && value.content.every(row => !!row && counts(row) && ['matchRunId', 'demandId', 'title', 'enterpriseName', 'termId', 'termCode'].every(key => typeof row[key as keyof ReportRow] === 'string')
      && (!term || row.termId === term) && typeof row.criteriaCurrent === 'boolean' && Number.isFinite(Date.parse(row.completedAt)))
    && new Set(value.content.map(row => row.demandId)).size === value.content.length;
}
const time = (value: string) => new Date(value).toLocaleString('vi-VN');
const number = (value: number) => value.toLocaleString('vi-VN');
class ReportFailure extends Error {}
const failure = (error: unknown) => error instanceof ReportFailure ? error.message : error instanceof ApiError && [403, 404].includes(error.status)
  ? 'Bạn chưa được phân công xem kết quả đối chiếu trong trường/kỳ này hoặc quyền đã hết hiệu lực.'
  : error instanceof ApiError && error.errorCode === 'REPORT_TOO_LARGE'
    ? 'Báo cáo có quá nhiều nhu cầu. Chọn một kỳ hoặc thu hẹp bộ lọc rồi xuất lại.' : workflowError(error);

export function SchoolMatchingReport({ org, termId = '', openMatching }: { org: string; termId?: string; openMatching: () => void }) {
  return <ReportScope key={org + ':' + termId} org={org} termId={termId} openMatching={openMatching} />;
}
function ReportScope({ org, termId, openMatching }: { org: string; termId: string; openMatching: () => void }) {
  const [report, setReport] = useState<Report | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [draft, setDraft] = useState(''), [query, setQuery] = useState(''), [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<ReportRow | null>(null), [exporting, setExporting] = useState(false), [notice, setNotice] = useState('');
  const download = useRef<AbortController | null>(null);
  const filters = () => new URLSearchParams({ ...(termId ? { termId } : {}), ...(query ? { q: query } : {}) });
  useEffect(() => () => download.current?.abort(), []);
  useEffect(() => {
    const controller = new AbortController();setLoading(true);setReport(null);setSelected(null);setError('');setNotice('');
    const params = new URLSearchParams({ ...(termId ? { termId } : {}), ...(query ? { q: query } : {}), page: String(page), size: '10' });
    void api<Report>('/school/matching-reports?' + params, { signal: AbortSignal.any([controller.signal, AbortSignal.timeout(20000)]), headers: { 'X-Organization-Id': org } }).then(value => {
      if (controller.signal.aborted) return;
      if (!valid(value, org, termId, page)) throw new ReportFailure('Chưa nhận được báo cáo hợp lệ trong phạm vi đã chọn. Vui lòng tải lại.');
      if (page > 0 && page >= value.totalPages) {setPage(Math.max(0, value.totalPages - 1));return;}
      setReport(value);
    }).catch(reason => { if (!controller.signal.aborted) {setReport(null);setError(failure(reason));} })
      .finally(() => {if (!controller.signal.aborted) setLoading(false);});
    return () => controller.abort();
  }, [org, termId, query, page, revision]);
  const exportCsv = async () => {
    if (!report?.totalElements || loading || exporting) return;
    const controller = new AbortController();download.current = controller;setExporting(true);setError('');setNotice('');
    try {
      const blob = await apiBlob('/school/matching-reports/export.csv?' + filters(), { signal: AbortSignal.any([controller.signal, AbortSignal.timeout(60000)]), headers: { 'X-Organization-Id': org } });
      if (controller.signal.aborted) return;
      if (!blob.type.startsWith('text/csv')) throw new ReportFailure('Máy chủ chưa trả về tệp CSV hợp lệ. Vui lòng thử lại.');
      const url = URL.createObjectURL(blob), link = document.createElement('a');
      link.href = url;link.download = `bao-cao-doi-chieu-${new Date().toISOString().slice(0, 10)}.csv`;
      document.body.append(link);link.click();link.remove();window.setTimeout(() => URL.revokeObjectURL(url), 1000);
      setNotice('Đã tải báo cáo CSV theo bộ lọc hiện tại.');
    } catch (reason) {
      if (!controller.signal.aborted) {
        if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) {setReport(null);setSelected(null);}
        setError(failure(reason));
      }
    } finally {if (!controller.signal.aborted) setExporting(false);}
  };
  const metrics: [number | undefined, string, string][] = [
    [report?.totals.compared, 'Lượt hồ sơ đối chiếu', 'total'], [report?.totals.matched, 'Đáp ứng', 'match'],
    [report?.totals.missingData, 'Thiếu dữ liệu', 'missing'], [report?.totals.notMatched, 'Không đáp ứng', 'unmatched'],
  ];
  return <section className="school-matching-report" aria-label="Báo cáo thống kê đối chiếu">
    <div className="school-report-toolbar"><div><h3>Kết quả đối chiếu theo nhu cầu</h3>{report && <p>{number(report.totalElements)} nhu cầu · Cập nhật {time(report.generatedAt)}</p>}</div>
      <div className="button-row"><button type="button" className="secondary" disabled={loading || exporting} onClick={() => setRevision(value => value + 1)}><Icon name="refresh" size={16} />Tải lại báo cáo</button>
        <button type="button" disabled={loading || exporting || !report?.totalElements} onClick={() => void exportCsv()}><Icon name="download" size={16} />{exporting ? 'Đang xuất báo cáo…' : 'Xuất CSV'}</button></div>
    </div>
    <p className="school-report-note">Mỗi nhu cầu lấy kết quả đối chiếu gần nhất đã hoàn tất. Một sinh viên có thể được đối chiếu với nhiều nhu cầu; số lượt hồ sơ không phải số sinh viên duy nhất.</p>
    <form className="school-report-filter" onSubmit={event => {event.preventDefault();setQuery(draft.trim());setPage(0);setNotice('');}}>
      <label>Tìm nhu cầu hoặc doanh nghiệp<input type="search" maxLength={150} value={draft} disabled={exporting} onChange={event => setDraft(event.target.value)} placeholder="Nhập tên nhu cầu, doanh nghiệp hoặc mã kỳ…" /></label>
      <button type="submit" disabled={loading || exporting}>Áp dụng bộ lọc</button>
      {query && <button type="button" className="secondary" disabled={loading || exporting} onClick={() => {setDraft('');setQuery('');setPage(0);}}>Xóa bộ lọc báo cáo</button>}
    </form>
    <Notice message={error} tone="error" /><Notice message={notice} tone="success" />
    <div className="school-report-metrics" aria-label="Tổng hợp kết quả đối chiếu">{metrics.map(([value, title, tone]) => <div key={tone} className={'school-report-metric is-' + tone}><span>{title}</span><strong>{value === undefined ? '—' : number(value)}</strong></div>)}</div>
    {loading ? <LoadingState label="Đang tải báo cáo đối chiếu…" /> : report && report.totalElements === 0 ? <div className="school-report-empty"><EmptyState icon="activity" title="Chưa có kết quả đối chiếu trong phạm vi này"
      description="Chọn kỳ khác hoặc chạy đối chiếu hồ sơ cho nhu cầu cần báo cáo." /><button type="button" className="secondary" onClick={openMatching}>Mở đối chiếu hồ sơ</button></div>
      : report && <><div className="school-report-table"><table><caption>Kết quả gần nhất của từng nhu cầu</caption><thead><tr><th>Nhu cầu / Doanh nghiệp</th><th>Kỳ thực tập</th><th>Thời điểm đối chiếu</th><th>Đáp ứng</th><th>Thiếu dữ liệu</th><th>Không đáp ứng</th><th>Thao tác</th></tr></thead>
        <tbody>{report.content.map(row => <tr key={row.matchRunId} {...recordRow(() => setSelected(row), exporting)}><th scope="row"><strong>{row.title}</strong><small>{row.enterpriseName}</small>
          {!row.criteriaCurrent && <span className="school-report-stale">Tiêu chí đã thay đổi</span>}</th><td data-label="Kỳ thực tập">{row.termCode}</td><td data-label="Thời điểm đối chiếu">{time(row.completedAt)}</td>
          <td data-label="Đáp ứng" className="is-match">{number(row.matched)}</td><td data-label="Thiếu dữ liệu" className="is-missing">{number(row.missingData)}</td><td data-label="Không đáp ứng" className="is-unmatched">{number(row.notMatched)}</td>
          <td><button type="button" className="secondary" disabled={exporting} aria-label={'Xem báo cáo ' + row.title} onClick={() => setSelected(row)}>Xem chi tiết<Icon name="arrow-right" size={14} /></button></td></tr>)}</tbody></table></div>
        <PagerNav page={page} totalPages={report.totalPages} totalElements={report.totalElements} unitLabel="nhu cầu" busy={loading || exporting} onChange={setPage} ariaLabel="Phân trang báo cáo đối chiếu" /></>}
    {selected && <WorkflowDetailDialog label={'Báo cáo đối chiếu ' + selected.title} onClose={() => setSelected(null)} compact>
      <header className="candidate-dialog-header"><div><h3>{selected.title}</h3><p>{selected.enterpriseName} · {selected.termCode}</p></div><button type="button" className="secondary" onClick={() => setSelected(null)}>Đóng báo cáo</button></header>
      <p>Đối chiếu lúc {time(selected.completedAt)}</p>
      <div className="school-report-metrics">{[[selected.matched, 'Đáp ứng', 'match'], [selected.missingData, 'Thiếu dữ liệu', 'missing'], [selected.notMatched, 'Không đáp ứng', 'unmatched']].map(([value, title, tone]) =>
        <div key={tone} className={'school-report-metric is-' + tone}><span>{title}</span><strong>{number(Number(value))}</strong></div>)}</div>
      {!selected.criteriaCurrent && <Notice tone="error" message="Tiêu chí của nhu cầu đã thay đổi sau lần đối chiếu này. Cần chạy lại đối chiếu để có báo cáo theo tiêu chí mới." />}
      <p className="school-report-note">Số liệu được ghi nhận tại thời điểm đối chiếu. Báo cáo và tệp xuất chỉ chứa số liệu tổng hợp, không kèm hồ sơ cá nhân sinh viên.</p>
    </WorkflowDetailDialog>}
  </section>;
}
