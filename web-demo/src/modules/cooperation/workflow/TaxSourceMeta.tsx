export function TaxSourceMeta({ provider, checkedAt }: { provider: string | null; checkedAt: string }) {
  const date = new Date(checkedAt), validDate = Number.isFinite(date.getTime());
  const source = provider === 'mock' ? 'Dữ liệu minh họa ngoại tuyến' : provider === 'casso' ? 'Nguồn: Casso' : provider ? 'Nguồn: ' + provider : '';
  return <div className="tax-source-meta">
    {source && <span>{source}</span>}
    {validDate && <time dateTime={checkedAt}>Dữ liệu tra cứu: {date.toLocaleString('vi-VN', {
      day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
    })}</time>}
  </div>;
}
