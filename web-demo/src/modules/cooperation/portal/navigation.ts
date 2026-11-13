export type PortalPage = 'matching' | 'overview' | 'verifications' | 'demands' | 'statistics' | 'cooperations' | 'proposals' | 'placements' | 'notifications';
export type PortalSide = 'school' | 'enterprise';

const pages: { id: PortalPage; title: string; icon: string; schoolTitle?: string; enterpriseOnly?: boolean; schoolOnly?: boolean }[] = [
  { id: 'overview', title: 'Tổng quan', icon: 'grid' },
  { id: 'verifications', title: 'Xác minh theo trường', schoolTitle: 'Xác minh doanh nghiệp', icon: 'shield' },
  { id: 'demands', title: 'Nhu cầu thực tập', icon: 'briefcase', enterpriseOnly: true },
  { id: 'statistics', title: 'Thống kê bảo mật', icon: 'activity', enterpriseOnly: true },
  { id: 'cooperations', title: 'Hợp tác', schoolTitle: 'Duyệt hợp tác', icon: 'building' },
  { id: 'matching', title: 'Đối chiếu hồ sơ', icon: 'activity', schoolOnly: true },
  { id: 'proposals', title: 'Ứng viên đề xuất', schoolTitle: 'Đề xuất sinh viên', icon: 'users' },
  { id: 'placements', title: 'Tiếp nhận thực tập', icon: 'graduation' },
  { id: 'notifications', title: 'Thông báo', icon: 'bell' },
];

export function pagesFor(side: PortalSide) {
  return pages.filter(page => side === 'enterprise' ? !page.schoolOnly : !page.enterpriseOnly)
    .map(page => ({ ...page, title: side === 'school' ? page.schoolTitle || page.title : page.title }));
}

export function portalPageTitle(side: PortalSide, page: PortalPage) {
  return pagesFor(side).find(item => item.id === page)?.title || 'Tổng quan';
}
