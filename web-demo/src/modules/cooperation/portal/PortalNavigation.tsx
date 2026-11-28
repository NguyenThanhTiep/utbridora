import { useEffect, useState } from 'react';
import { Icon } from '../../../components';
import { pagesFor, type PortalPage, type PortalSide } from './navigation';

type ExtraMenuItem = {
  id: string;
  title: string;
  icon: string;
};

type NavSubItem =
  | { kind: 'portal'; id: PortalPage; title: string }
  | { kind: 'screen'; id: string; title: string };

type SchoolNavGroup = {
  id: string;
  title: string;
  icon: string;
  children: NavSubItem[];
  emptyText?: string;
};

export function PortalNavigation({
  side,
  page,
  navigate,
  extraItems = [],
  activeScreen,
  onNavigateScreen,
  unreadCount = 0,
  isRail = false,
  onExpandRail,
}: {
  side: PortalSide;
  page?: PortalPage;
  navigate: (page: PortalPage) => void;
  extraItems?: ExtraMenuItem[];
  activeScreen?: string;
  onNavigateScreen?: (screenId: string) => void;
  unreadCount?: number;
  isRail?: boolean;
  onExpandRail?: () => void;
}) {
  const schoolGroups: SchoolNavGroup[] = [
    {
      id: 'student',
      title: 'Sinh viên',
      icon: 'user',
      children: [
        { kind: 'portal', id: 'students', title: 'Danh sách & kích hoạt' },
      ],
    },
    {
      id: 'partner',
      title: 'Doanh nghiệp đối tác',
      icon: 'building',
      children: [
        { kind: 'portal', id: 'verifications', title: 'Xác minh doanh nghiệp' },
        { kind: 'portal', id: 'cooperations', title: 'Duyệt hợp tác' },
      ],
    },
    {
      id: 'internship',
      title: 'Đợt thực tập',
      icon: 'calendar',
      children: [
        { kind: 'portal', id: 'matching', title: 'Đối chiếu hồ sơ' },
        { kind: 'portal', id: 'proposals', title: 'Đề xuất sinh viên' },
        { kind: 'portal', id: 'placements', title: 'Tiếp nhận thực tập' },
      ],
    },
    {
      id: 'reports',
      title: 'Báo cáo',
      icon: 'activity',
      children: [{ kind: 'portal', id: 'reports', title: 'Thống kê đối chiếu' }],
    },
    ...(extraItems.length > 0
      ? [
          {
            id: 'utilities',
            title: 'Tài khoản & Tiện ích',
            icon: 'layers',
            children: extraItems.map(item => ({
              kind: 'screen' as const,
              id: item.id,
              title: item.title,
            })),
          },
        ]
      : []),
  ];

  const isChildActive = (child: NavSubItem) =>
    child.kind === 'portal' ? page === child.id : activeScreen === child.id;

  const findActiveGroupId = () => {
    const matched = schoolGroups.find(g => g.children.some(isChildActive));
    return matched ? matched.id : null;
  };

  const [openGroupId, setOpenGroupId] = useState<string | null>(findActiveGroupId);

  useEffect(() => {
    if (side !== 'school') return;
    setOpenGroupId(findActiveGroupId());
  }, [side, page, activeScreen]);

  const unreadBadge = unreadCount > 99 ? '99+' : unreadCount > 0 ? String(unreadCount) : '';

  if (side !== 'school') {
    return (
      <div className={'portal-navigation is-' + side}>
        <p className="nav-group-label">CỔNG DOANH NGHIỆP</p>
        {pagesFor(side).map(item => (
          <button
            key={item.id}
            className={'nav' + (page === item.id ? ' active' : '')}
            title={item.title}
            aria-current={page === item.id ? 'page' : undefined}
            onClick={() => navigate(item.id)}
          >
            <Icon name={item.icon} />
            <span>{item.title}</span>
            {item.id === 'notifications' && unreadBadge && (
              <span className="u417-bd" data-badge={unreadBadge} aria-hidden="true" />
            )}
          </button>
        ))}
      </div>
    );
  }

  const toggleGroup = (groupId: string) => {
    if (isRail && onExpandRail) {
      onExpandRail();
      setOpenGroupId(groupId);
      return;
    }
    setOpenGroupId(prev => (prev === groupId ? null : groupId));
  };

  return (
    <div className="portal-navigation is-school is-grouped">
      <p className="nav-group-label">CỔNG NHÀ TRƯỜNG</p>

      {/* Tổng quan */}
      <button
        type="button"
        className={'nav' + (page === 'overview' ? ' active' : '')}
        title="Tổng quan"
        aria-current={page === 'overview' ? 'page' : undefined}
        onClick={() => {
          setOpenGroupId(null);
          navigate('overview');
        }}
      >
        <Icon name="grid" />
        <span>Tổng quan</span>
      </button>

      {/* Single-Open Accordion Groups */}
      {schoolGroups.map(group => {
        const isOpen = openGroupId === group.id;
        const hasActiveChild = group.children.some(isChildActive);
        return (
          <div
            key={group.id}
            className={
              'school-nav-group'
              + (isOpen ? ' is-open' : '')
              + (hasActiveChild ? ' has-active-child' : '')
            }
          >
              <button
              type="button"
              className={
                'nav school-nav-group-toggle'
                + (isOpen || hasActiveChild ? ' is-group-active' : '')
              }
              title={group.title}
              aria-expanded={isOpen && !isRail}
              onClick={() => toggleGroup(group.id)}
            >
              <Icon name={group.icon} />
              <span>{group.title}</span>
              <Icon
                name="chevron-down"
                size={15}
                className={'school-nav-chevron' + (isOpen ? ' is-open' : '')}
              />
            </button>

            <div
              className={'school-nav-collapse-wrap' + (isOpen && !isRail ? ' is-open' : '')}
              aria-hidden={!isOpen || isRail}
            >
              <div className="school-nav-sublist">
                {group.children.length > 0 ? (
                  group.children.map(child => {
                    const active = isChildActive(child);
                    return (
                      <button
                        key={child.kind + ':' + child.id}
                        type="button"
                        tabIndex={isOpen ? 0 : -1}
                        className={'nav school-nav-subitem' + (active ? ' active' : '')}
                        title={child.title}
                        aria-current={active ? 'page' : undefined}
                        onClick={() => {
                          if (child.kind === 'portal') navigate(child.id);
                          else onNavigateScreen?.(child.id);
                        }}
                      >
                        <span>{child.title}</span>
                      </button>
                    );
                  })
                ) : (
                  <p className="school-nav-empty">{group.emptyText}</p>
                )}
              </div>
            </div>
          </div>
        );
      })}

      {/* Thông báo */}
      <button
        type="button"
        className={'nav' + (page === 'notifications' ? ' active' : '')}
        title="Thông báo"
        aria-current={page === 'notifications' ? 'page' : undefined}
        onClick={() => {
          setOpenGroupId(null);
          navigate('notifications');
        }}
      >
        <Icon name="bell" />
        <span>Thông báo</span>
        {unreadBadge && (
          <span className="u417-bd" data-badge={unreadBadge} aria-hidden="true" />
        )}
      </button>
    </div>
  );
}
