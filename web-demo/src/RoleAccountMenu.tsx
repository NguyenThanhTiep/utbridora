import { useEffect, useRef, useState } from 'react';
import { Icon } from './components';
import './RoleAccountMenu.css';

export function RoleAccountMenu({
  badge,
  label,
  triggerLabel,
  kicker,
  title,
  email,
  variant = 'portal',
  overviewLabel = 'Tổng quan',
  onOverview,
  onAccount,
  onLogout,
  logoutDisabled = false,
}: {
  badge: string;
  label: string;
  triggerLabel?: string;
  kicker?: string;
  title?: string;
  email?: string;
  variant?: 'portal' | 'topbar';
  overviewLabel?: string;
  onOverview?: () => void;
  onAccount?: () => void;
  onLogout: () => void;
  logoutDisabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    if (!open) return;
    const onPointerDown = (event: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(event.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpen(false);
        triggerRef.current?.focus();
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  return (
    <div className="role-menu-wrapper" ref={rootRef}>
      <button
        type="button"
        ref={triggerRef}
        className={'portal-role role-menu-trigger' + (variant === 'topbar' ? ' is-topbar workspace-account-trigger' : '') + (open ? ' is-open' : '')}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={`${label} — Mở menu tài khoản`}
        onClick={() => setOpen(prev => !prev)}
      >
        <span>{badge}</span>
        <b className="role-menu-label workspace-account-label" title={title || triggerLabel || label}>{triggerLabel || label}</b>
        <Icon name="chevron-down" size={14} className="role-menu-caret workspace-account-caret" />
      </button>

      {open && (
        <div className="role-menu-popover" role="region" aria-label={`Menu ${label}`}>
          <div className="role-menu-header">
            <span className="role-menu-header-avatar">{badge}</span>
            <div className="role-menu-header-meta">
              {kicker && <span className="role-menu-header-kicker">{kicker}</span>}
              <strong>{title || label}</strong>
              {email && <small title={email}>{email}</small>}
            </div>
          </div>

          <div className="role-menu-list">
            {onOverview && (
              <button
                type="button"
                className="role-menu-item"
                onClick={() => {
                  setOpen(false);
                  onOverview();
                }}
              >
                <Icon name="grid" size={16} />
                <span>{overviewLabel}</span>
              </button>
            )}
            {onAccount && (
              <button
                type="button"
                className="role-menu-item"
                onClick={() => {
                  setOpen(false);
                  onAccount();
                }}
              >
                <Icon name="user" size={16} />
                <span>Tài khoản của tôi</span>
              </button>
            )}
            {(onOverview || onAccount) && <div className="role-menu-divider" aria-hidden="true" />}
            <button
              type="button"
              className="role-menu-item is-danger"
              disabled={logoutDisabled}
              onClick={() => {
                setOpen(false);
                onLogout();
              }}
            >
              <Icon name="logout" size={16} />
              <span>Đăng xuất</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
