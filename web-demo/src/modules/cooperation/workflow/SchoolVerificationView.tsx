import { useEffect, useState, type RefObject } from 'react';
import { Icon, Notice } from '../../../components';
import { fieldLabels, states, type Verification } from './types';

const schoolStateLabels: Record<string, string> = {
  SUBMITTED: 'Chờ duyệt',
  NEEDS_CHANGES: 'Cần bổ sung',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Từ chối',
};

const verificationFields = [
  'registrationCode',
  'registeredName',
  'address',
  'contactName',
  'contactEmail',
  'contactPhone',
  'evidenceFileIds',
] as const;

function formatSubmittedDate(iso?: string | null, version = 1) {
  if (!iso) return `v${version}`;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return `v${version}`;
  const day = String(d.getUTCDate()).padStart(2, '0');
  const month = String(d.getUTCMonth() + 1).padStart(2, '0');
  const year = d.getUTCFullYear();
  return `${day}/${month}/${year}`;
}

function formatDossierCode(index: number) {
  return `BM03-${String(index + 1).padStart(3, '0')}`;
}

export function SchoolVerificationView({
  items,
  loading,
  error,
  actionMessage,
  retryable,
  retrying,
  busy,
  onRetry,
  onReload,
  onDecide,
  focusId,
  focusedRecord,
  focusedRef,
  page,
  total,
  onPageChange,
}: {
  items: Verification[];
  loading: boolean;
  error: string;
  actionMessage: string;
  retryable: boolean;
  retrying: boolean;
  busy: boolean;
  onRetry: () => void;
  onReload: () => void;
  onDecide: (
    verification: Verification,
    payload: { decision: string; reasonCode: string | null; decisionRef: string; missingFields: string[] },
  ) => Promise<void>;
  focusId?: string;
  focusedRecord: Verification | null;
  focusedRef: RefObject<HTMLElement | null>;
  page: number;
  total: number;
  onPageChange: (next: number) => void;
}) {
  const [stateFilter, setStateFilter] = useState('');
  const [searchText, setSearchText] = useState('');
  const [scopeFilter, setScopeFilter] = useState('ALL');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [decisionMode, setDecisionMode] = useState<'IDLE' | 'REQUEST_INFO' | 'REJECT'>('IDLE');
  const [decisionRef, setDecisionRef] = useState('');
  const [missingFields, setMissingFields] = useState<string[]>([]);
  const [validationHint, setValidationHint] = useState('');

  useEffect(() => {
    setSelectedId(null);
  }, [focusId]);

  const orderedItems = [...items].sort((a, b) => {
    const timeA = a.submittedAt ? Date.parse(a.submittedAt) : Number.NaN;
    const timeB = b.submittedAt ? Date.parse(b.submittedAt) : Number.NaN;
    if (!Number.isNaN(timeA) && !Number.isNaN(timeB) && timeA !== timeB) {
      return timeA - timeB;
    }
    return a.data.registeredName.localeCompare(b.data.registeredName, 'vi', {
      numeric: true,
      sensitivity: 'base',
    });
  });

  const filteredItems = orderedItems.filter((v, idx) => {
    if (stateFilter && v.state !== stateFilter) return false;
    if (scopeFilter === 'WITH_EVIDENCE' && v.data.evidenceFileIds.length === 0) return false;
    if (scopeFilter === 'MISSING_EVIDENCE' && v.data.evidenceFileIds.length > 0 && v.missingFields.length === 0) return false;
    if (searchText.trim()) {
      const q = searchText.trim().toLowerCase();
      const code = formatDossierCode(page * 25 + idx).toLowerCase();
      return (
        code.includes(q)
        || v.data.registeredName.toLowerCase().includes(q)
        || v.data.registrationCode.toLowerCase().includes(q)
        || v.data.contactName.toLowerCase().includes(q)
        || v.data.contactEmail.toLowerCase().includes(q)
      );
    }
    return true;
  });

  const summaryCards = [
    { state: 'SUBMITTED', label: 'Chờ duyệt', icon: 'clock', count: items.filter(v => v.state === 'SUBMITTED').length, tone: 'kpi-pending' },
    { state: 'NEEDS_CHANGES', label: 'Cần bổ sung', icon: 'alert', count: items.filter(v => v.state === 'NEEDS_CHANGES').length, tone: 'kpi-warning' },
    { state: 'APPROVED', label: 'Đã duyệt', icon: 'check-circle', count: items.filter(v => v.state === 'APPROVED').length, tone: 'kpi-approved' },
  ];

  const activeRecord =
    selectedId === '__NONE__'
      ? null
      : selectedId
        ? filteredItems.find(v => v.verificationId === selectedId)
          || (focusedRecord?.verificationId === selectedId ? focusedRecord : null)
        : focusId
          ? focusedRecord || filteredItems.find(v => v.verificationId === focusId) || null
          : null;

  const toggleRow = (verificationId: string) => {
    setSelectedId(current => {
      const currentOpen =
        current === '__NONE__'
          ? null
          : current || (focusId ? activeRecord?.verificationId || null : null);
      return currentOpen === verificationId ? '__NONE__' : verificationId;
    });
  };

  useEffect(() => {
    setValidationHint('');
    setDecisionMode('IDLE');
    if (activeRecord) {
      setDecisionRef(activeRecord.decisionRef || '');
      setMissingFields(activeRecord.missingFields || []);
    }
  }, [activeRecord?.verificationId, focusId]);

  const toggleMissingField = (field: string) => {
    setMissingFields(prev => (prev.includes(field) ? prev.filter(f => f !== field) : [...prev, field]));
  };

  const handleApprove = () => {
    if (!activeRecord || activeRecord.state !== 'SUBMITTED') return;
    setValidationHint('');
    void onDecide(activeRecord, {
      decision: 'APPROVE',
      reasonCode: null,
      decisionRef: decisionRef.trim() || 'QD-PHEDUYET-2026',
      missingFields: [],
    });
  };

  const handleRequestInfo = () => {
    if (!activeRecord || activeRecord.state !== 'SUBMITTED') return;
    if (decisionMode !== 'REQUEST_INFO') {
      setDecisionMode('REQUEST_INFO');
      setValidationHint('');
      return;
    }
    const trimmed = decisionRef.trim();
    if (!trimmed) {
      setValidationHint('Vui lòng nhập lý do / căn cứ yêu cầu bổ sung.');
      return;
    }
    if (missingFields.length === 0) {
      setValidationHint('Vui lòng chọn ít nhất 01 mục thông tin cần bổ sung.');
      return;
    }
    setValidationHint('');
    void onDecide(activeRecord, {
      decision: 'REQUEST_INFO',
      reasonCode: 'ADDITIONAL_INFO_REQUIRED',
      decisionRef: trimmed,
      missingFields,
    });
  };

  const handleReject = () => {
    if (!activeRecord || activeRecord.state !== 'SUBMITTED') return;
    if (decisionMode !== 'REJECT') {
      setDecisionMode('REJECT');
      setValidationHint('');
      return;
    }
    const trimmed = decisionRef.trim();
    if (!trimmed) {
      setValidationHint('Vui lòng nhập lý do / căn cứ từ chối hồ sơ.');
      return;
    }
    setValidationHint('');
    void onDecide(activeRecord, {
      decision: 'REJECT',
      reasonCode: 'REVIEW_REJECTED',
      decisionRef: trimmed,
      missingFields: [],
    });
  };

  return (
    <section className="panel workflow-surface school-verification-surface">
      {/* 3 Figma Pastel KPI Cards */}
      <div className="workflow-kpi-grid school-summary-strip" aria-label="Thống kê hồ sơ xác minh">
        {summaryCards.map(card => (
          <button
            key={card.state}
            type="button"
            className={`kpi-card ${card.tone}${stateFilter === card.state ? ' is-active' : ''}`}
            aria-pressed={stateFilter === card.state}
            title={stateFilter === card.state ? 'Bỏ lọc trạng thái' : `Lọc: ${card.label}`}
            onClick={() => setStateFilter(current => (current === card.state ? '' : card.state))}
          >
            <span className="kpi-card-top">
              <span className="kpi-card-label">{card.label}</span>
              <span className="school-stat-icon-tile" aria-hidden="true">
                <Icon name={card.icon} size={18} />
              </span>
            </span>
            <span className="kpi-card-bottom">
              <strong className="kpi-card-value">{card.count}</strong>
              <span className="kpi-card-hint" aria-hidden="true">
                {stateFilter === card.state ? 'Đang lọc · Bỏ lọc' : 'Nhấn để lọc →'}
              </span>
            </span>
          </button>
        ))}
      </div>

      <div className="school-section-card">
        <div className="school-section-head">
          <div className="school-section-title">
            <span className="school-section-icon is-verifications" aria-hidden="true">
              <Icon name="shield" size={18} />
            </span>
            <h3>Hồ sơ xác minh doanh nghiệp</h3>
          </div>
          {items.length > 0 && (
            <span className="workflow-count-pill">
              {filteredItems.length}/{items.length} hồ sơ
            </span>
          )}
        </div>

        <Notice message={error || actionMessage || validationHint} tone="error" />
        {retryable && (
          <button disabled={retrying} onClick={onRetry}>
            Thử lại yêu cầu đã gửi
          </button>
        )}

        {/* 4-Column Figma Filter Bar */}
        <div className="school-verification-filter-bar" role="search" aria-label="Bộ lọc hồ sơ xác minh">
          <label className="school-vfilter-field">
            <span className="cooperation-sr-only">Tìm kiếm hồ sơ xác minh</span>
            <input
              type="search"
              aria-label="Tìm kiếm hồ sơ xác minh"
              placeholder="Tìm kiếm / mã hồ sơ..."
              value={searchText}
              onChange={e => setSearchText(e.target.value)}
            />
          </label>
          <label className="school-vfilter-field">
            <span className="cooperation-sr-only">Lọc trạng thái xác minh</span>
            <select aria-label="Lọc trạng thái xác minh" value={stateFilter} onChange={e => setStateFilter(e.target.value)}>
              <option value="">Bộ lọc trạng thái</option>
              {Object.entries(schoolStateLabels).map(([key, label]) => (
                <option key={key} value={key}>
                  {label}
                </option>
              ))}
            </select>
          </label>
          <label className="school-vfilter-field">
            <span className="cooperation-sr-only">Kỳ thực tập / Trường</span>
            <select aria-label="Kỳ thực tập / Trường" value={scopeFilter} onChange={e => setScopeFilter(e.target.value)}>
              <option value="ALL">Kỳ thực tập / Trường</option>
              <option value="WITH_EVIDENCE">Có đính kèm minh chứng ĐKKD</option>
              <option value="MISSING_EVIDENCE">Cần bổ sung minh chứng</option>
            </select>
          </label>
          <div className="school-vfilter-actions">
            <button type="button" className="t5-btn-teal school-vfilter-submit" disabled={busy} onClick={onReload}>
              Lọc
            </button>
            {(stateFilter || searchText || scopeFilter !== 'ALL') && (
              <button
                type="button"
                className="secondary"
                onClick={() => {
                  setStateFilter('');
                  setSearchText('');
                  setScopeFilter('ALL');
                }}
              >
                Xóa lọc
              </button>
            )}
          </div>
        </div>

        {loading && <p className="rc-loading-state" role="status">Đang tải hồ sơ xác minh…</p>}
        {!loading && focusId && !activeRecord && !error && <p className="rc-loading-state" role="status">Đang mở hồ sơ đã chọn…</p>}

        {/* Verification Table */}
        <div className="proposal-table school-verification-table">
          <table>
            <thead>
              <tr>
                <th>Doanh nghiệp</th>
                <th>Mã hồ sơ</th>
                <th>Ngày gửi</th>
                <th>Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {filteredItems.map((v, idx) => {
                const isSelected = activeRecord?.verificationId === v.verificationId;
                const itemIndex = orderedItems.indexOf(v);
                const dossierCode = formatDossierCode(page * 25 + (itemIndex >= 0 ? itemIndex : idx));
                return (
                  <tr
                    key={v.verificationId}
                    className={isSelected ? 'table-row-selected' : undefined}
                    aria-selected={isSelected}
                    onClick={() => toggleRow(v.verificationId)}
                  >
                    <td>
                      <strong className="bm03-company-name">{v.data.registeredName}</strong>
                    </td>
                    <td>
                      <span className="bm03-code-text">{dossierCode}</span>
                    </td>
                    <td>
                      <span className="bm03-date-text">{formatSubmittedDate(v.submittedAt, v.version)}</span>
                    </td>
                    <td>
                      <span className="bm03-status-cell">
                        <span className={`status-pill pill-${v.state.toLowerCase()}`}>
                          {schoolStateLabels[v.state] || states[v.state] || v.state}
                        </span>
                        <Icon
                          name="chevron-down"
                          size={15}
                          className={`bm03-row-caret${isSelected ? ' is-open' : ''}`}
                        />
                      </span>
                    </td>
                  </tr>
                );
              })}
              {!loading && !error && filteredItems.length === 0 && (
                <tr>
                  <td colSpan={4}>
                    {items.length === 0
                      ? 'Chưa có hồ sơ xác minh trong phạm vi này.'
                      : 'Không có hồ sơ xác minh khớp với bộ lọc hiện tại.'}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>

        {!loading && !error && filteredItems.length > 0 && !activeRecord && (
          <p className="bm03-select-hint">Nhấn vào một doanh nghiệp trong bảng để xem chi tiết hồ sơ và xét duyệt.</p>
        )}

        {total > 1 && (
          <div className="button-row">
            <button className="secondary" disabled={page === 0} onClick={() => onPageChange(page - 1)}>
              Trước
            </button>
            <span>
              Trang {page + 1}/{Math.max(1, total)}
            </span>
            <button className="secondary" disabled={page + 1 >= total} onClick={() => onPageChange(page + 1)}>
              Sau
            </button>
          </div>
        )}
      </div>

      {/* Natural Administrative Dossier Detail Panel (opens on row click or task focus) */}
      {activeRecord && (() => {
        const activeIdx = orderedItems.findIndex(v => v.verificationId === activeRecord.verificationId);
        const activeCode = activeIdx >= 0 ? formatDossierCode(page * 25 + activeIdx) : 'BM-03';
        const submittedDate = formatSubmittedDate(activeRecord.submittedAt, activeRecord.version);
        const statusLabel = schoolStateLabels[activeRecord.state] || states[activeRecord.state] || activeRecord.state;

        return (
          <article
            key={activeRecord.verificationId}
            id="school-bm03-detail"
            ref={activeRecord.verificationId === focusId ? focusedRef : undefined}
            tabIndex={-1}
            className="bm03-detail-card"
            aria-label="Chi tiết hồ sơ xác minh BM-03"
          >
            <div className="bm03-detail-header">
              <div className="bm03-header-text">
                <div className="bm03-title-row">
                  <h3>Hồ sơ BM-03 · {activeRecord.data.registeredName}</h3>
                  <span className={`status-pill pill-${activeRecord.state.toLowerCase()}`}>{statusLabel}</span>
                </div>
                <p className="bm03-meta-line">
                  Mã hồ sơ {activeCode} · MST {activeRecord.data.registrationCode || '—'} · Ngày gửi {submittedDate} (Bản v{activeRecord.version})
                </p>
              </div>
              <button
                type="button"
                className="bm03-close-btn"
                aria-label="Đóng chi tiết hồ sơ"
                onClick={() => setSelectedId('__NONE__')}
              >
                <Icon name="close" size={14} />
                <span>Đóng</span>
              </button>
            </div>

            <div className="bm03-detail-body">
              {activeRecord.missingFields.length > 0 && (
                <div className="bm03-missing-callout" role="note">
                  <Icon name="alert" size={15} />
                  <span>
                    Mục cần bổ sung: <strong>{activeRecord.missingFields.map(f => fieldLabels[f] || f).join(', ')}</strong>
                  </span>
                </div>
              )}

              <div className="bm03-expanded-section">
                <div>
                  <p className="bm03-section-label">Thông tin pháp nhân &amp; liên hệ</p>
                  <dl className="bm03-dl-grid">
                    <div className="bm03-dl-row">
                      <dt>Tên đăng ký doanh nghiệp</dt>
                      <dd>{activeRecord.data.registeredName}</dd>
                    </div>
                    <div className="bm03-dl-row">
                      <dt>Mã số thuế / ĐKKD</dt>
                      <dd>
                        <code className="bm03-code-plain bm03-mono">{activeRecord.data.registrationCode || '—'}</code>
                      </dd>
                    </div>
                    <div className="bm03-dl-row">
                      <dt>Người phụ trách liên hệ</dt>
                      <dd>{activeRecord.data.contactName || '—'}</dd>
                    </div>
                    <div className="bm03-dl-row">
                      <dt>Số điện thoại</dt>
                      <dd>
                        {activeRecord.data.contactPhone ? (
                          <a className="bm03-inline-link bm03-mono" href={`tel:${activeRecord.data.contactPhone}`}>
                            {activeRecord.data.contactPhone}
                          </a>
                        ) : '—'}
                      </dd>
                    </div>
                    <div className="bm03-dl-row">
                      <dt>Email liên hệ</dt>
                      <dd>
                        {activeRecord.data.contactEmail ? (
                          <a className="bm03-inline-link" href={`mailto:${activeRecord.data.contactEmail}`}>
                            {activeRecord.data.contactEmail}
                          </a>
                        ) : '—'}
                      </dd>
                    </div>
                    <div className="bm03-dl-row">
                      <dt>Ngày gửi &amp; Phiên bản</dt>
                      <dd>
                        {submittedDate} · Phiên bản v{activeRecord.version}
                      </dd>
                    </div>
                    <div className="bm03-dl-row is-full">
                      <dt>Địa chỉ trụ sở</dt>
                      <dd>{activeRecord.data.address || '—'}</dd>
                    </div>
                  </dl>
                </div>

                <div className="bm03-lower-grid">
                  <div className="bm03-subblock">
                    <p className="bm03-section-label">
                      Tài liệu đính kèm ({activeRecord.data.evidenceFileIds.length})
                    </p>
                    {activeRecord.data.evidenceFileIds.length > 0 ? (
                      <ul className="bm03-file-list">
                        {activeRecord.data.evidenceFileIds.map((fileId, index) => (
                          <li key={fileId} className="bm03-file-item">
                            <span className="bm03-file-name">
                              <Icon name="file" size={15} />
                              <span>Giấy phép ĐKKD / Minh chứng #{index + 1}</span>
                            </span>
                            <code className="bm03-file-id bm03-mono">…{fileId.slice(-8)}</code>
                          </li>
                        ))}
                      </ul>
                    ) : (
                      <p className="bm03-empty-note">Chưa có tệp minh chứng đính kèm trong hồ sơ này.</p>
                    )}
                  </div>

                  <div className="bm03-subblock">
                    <p className="bm03-section-label">Kết quả xét duyệt</p>
                    <div className="bm03-decision-card">
                      <div className="bm03-decision-row">
                        <span className="bm03-decision-label">Trạng thái</span>
                        <span className="bm03-decision-value">{statusLabel}</span>
                      </div>
                      <div className="bm03-decision-row">
                        <span className="bm03-decision-label">Căn cứ / Mã quyết định</span>
                        {activeRecord.decisionRef ? (
                          <code className="bm03-code-plain bm03-mono">{activeRecord.decisionRef}</code>
                        ) : (
                          <span className="bm03-decision-muted">Chưa ban hành</span>
                        )}
                      </div>
                      {activeRecord.decidedAt && (
                        <div className="bm03-decision-row">
                          <span className="bm03-decision-label">Ngày xét duyệt</span>
                          <span className="bm03-decision-value">
                            {formatSubmittedDate(activeRecord.decidedAt, activeRecord.version)}
                          </span>
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              </div>

              {/* Progressive Decision Input — only opens when user clicks Yêu cầu bổ sung or Từ chối */}
              {activeRecord.state === 'SUBMITTED' && decisionMode !== 'IDLE' && (
                <div className="bm03-inline-decision-box">
                  <label className="bm03-ref-label">
                    {decisionMode === 'REJECT' ? 'Lý do / Căn cứ từ chối hồ sơ' : 'Lý do / Căn cứ yêu cầu bổ sung'}
                    <input
                      type="text"
                      aria-label="Căn cứ quyết định"
                      maxLength={120}
                      disabled={busy}
                      placeholder={
                        decisionMode === 'REJECT'
                          ? 'Nhập căn cứ hoặc lý do từ chối (VD: QD-TUCHOI-2026)...'
                          : 'Nhập căn cứ hoặc nội dung yêu cầu bổ sung (VD: TB-BOSUNG-2026)...'
                      }
                      value={decisionRef}
                      onChange={e => {
                        setDecisionRef(e.target.value);
                        if (validationHint) setValidationHint('');
                      }}
                    />
                  </label>

                  {decisionMode === 'REQUEST_INFO' && (
                    <div className="bm03-missing-selector">
                      <span className="bm03-missing-title">Chọn mục cần doanh nghiệp bổ sung:</span>
                      <div className="bm03-missing-chips">
                        {verificationFields.map(field => {
                          const active = missingFields.includes(field);
                          return (
                            <button
                              key={field}
                              type="button"
                              aria-pressed={active}
                              disabled={busy}
                              className={`bm03-missing-chip${active ? ' is-selected' : ''}`}
                              onClick={() => toggleMissingField(field)}
                            >
                              {fieldLabels[field] || field}
                            </button>
                          );
                        })}
                      </div>
                    </div>
                  )}
                </div>
              )}

              {/* Bottom Action Footer — only for SUBMITTED */}
              {activeRecord.state === 'SUBMITTED' && (
                <div className="bm03-action-footer">
                  <p className="bm03-action-note">
                    Cần nhập căn cứ quyết định khi yêu cầu bổ sung hoặc từ chối.
                  </p>
                  <div className="bm03-action-buttons">
                    {decisionMode !== 'IDLE' && (
                      <button
                        type="button"
                        className="secondary bm03-btn-cancel"
                        disabled={busy}
                        onClick={() => {
                          setDecisionMode('IDLE');
                          setValidationHint('');
                        }}
                      >
                        Hủy
                      </button>
                    )}
                    <button
                      type="button"
                      className={`secondary bm03-btn-request${decisionMode === 'REQUEST_INFO' ? ' is-confirming' : ''}`}
                      disabled={busy}
                      onClick={handleRequestInfo}
                    >
                      {decisionMode === 'REQUEST_INFO' ? 'Xác nhận yêu cầu bổ sung' : 'Yêu cầu bổ sung'}
                    </button>
                    <button
                      type="button"
                      className={`secondary bm03-btn-reject${decisionMode === 'REJECT' ? ' is-confirming' : ''}`}
                      disabled={busy}
                      onClick={handleReject}
                    >
                      {decisionMode === 'REJECT' ? 'Xác nhận từ chối' : 'Từ chối'}
                    </button>
                    <button
                      type="button"
                      className="t5-btn-teal bm03-btn-approve"
                      disabled={busy}
                      onClick={handleApprove}
                    >
                      Phê duyệt
                    </button>
                  </div>
                </div>
              )}
            </div>
          </article>
        );
      })()}
    </section>
  );
}
