export function FeaturePending({ children }: { children: string }) {
  return <div className="portal-pending" role="note"><span>Đang hoàn thiện</span><p>{children}</p></div>;
}
