import {
  BarChart3,
  CalendarDays,
  ChefHat,
  ClipboardList,
  CreditCard,
  Home,
  LayoutGrid,
  LogOut,
  Menu as MenuIcon,
  PackageOpen,
  PanelLeftClose,
  PanelLeftOpen,
  Settings2,
  TableProperties,
  Users,
  X,
} from 'lucide-react'
import { useEffect, useRef, useState, type ComponentType, type RefObject } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'

import { useAuth } from '../../features/auth/authContext'

type NavigationItem = {
  label: string
  to: string
  icon: ComponentType<{ size?: number; strokeWidth?: number; 'aria-hidden'?: boolean | 'true' }>
}

const navigationGroups: { label: string; items: NavigationItem[] }[] = [
  {
    label: 'Overview',
    items: [{ label: 'Dashboard', to: '/dashboard', icon: Home }],
  },
  {
    label: 'Service',
    items: [
      { label: 'Tables', to: '/tables', icon: TableProperties },
      { label: 'Reservations', to: '/reservations', icon: CalendarDays },
      { label: 'Orders', to: '/orders', icon: ClipboardList },
      { label: 'Kitchen', to: '/kitchen', icon: ChefHat },
      { label: 'Payments', to: '/payments', icon: CreditCard },
    ],
  },
  {
    label: 'Management',
    items: [
      { label: 'Menu', to: '/menu', icon: MenuIcon },
      { label: 'Inventory', to: '/inventory', icon: PackageOpen },
      { label: 'Staff', to: '/staff', icon: Users },
      { label: 'Reports', to: '/reports', icon: BarChart3 },
    ],
  },
]

type SidebarProps = {
  id: string
  mobile: boolean
  closeButtonRef?: RefObject<HTMLButtonElement | null>
  displayName: string
  roles: string
  userInitial: string
  isSigningOut: boolean
  onClose: () => void
  onNavigate: () => void
  onSignOut: () => void
}

function Sidebar({
  id,
  mobile,
  closeButtonRef,
  displayName,
  roles,
  userInitial,
  isSigningOut,
  onClose,
  onNavigate,
  onSignOut,
}: SidebarProps) {
  return (
    <aside
      className={`sidebar sidebar--${mobile ? 'mobile sidebar--open' : 'desktop'}`}
      id={id}
      aria-label={mobile ? 'Mobile application navigation' : 'Application navigation'}
    >
      <div className="sidebar__brand-row">
        <NavLink className="brand" to="/dashboard" aria-label="Restaurant Operations dashboard">
          <span className="brand-mark" aria-hidden="true">
            <ChefHat size={22} />
          </span>
          <span className="brand-copy">
            <strong>Ember</strong>
            <small>Restaurant operations</small>
          </span>
        </NavLink>
        {mobile && (
          <button
            ref={closeButtonRef}
            className="icon-button sidebar__mobile-close"
            type="button"
            aria-label="Close navigation menu"
            onClick={onClose}
          >
            <X size={20} />
          </button>
        )}
      </div>

      <nav className="sidebar-nav" aria-label={mobile ? 'Mobile navigation' : 'Primary navigation'}>
        {navigationGroups.map((group) => (
          <div className="sidebar-nav__group" key={group.label}>
            <p className="sidebar-nav__label">{group.label}</p>
            {group.items.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  className="sidebar-nav__link"
                  to={item.to}
                  aria-label={item.label}
                  onClick={mobile ? onNavigate : undefined}
                >
                  <Icon size={19} strokeWidth={1.9} aria-hidden="true" />
                  <span>{item.label}</span>
                </NavLink>
              )
            })}
          </div>
        ))}
      </nav>

      <div className="sidebar__footer">
        <div className="sidebar-user">
          <span className="sidebar-user__avatar" aria-hidden="true">
            {userInitial}
          </span>
          <span className="sidebar-user__copy">
            <strong>{displayName}</strong>
            <small>{roles}</small>
          </span>
        </div>
        <button
          className="sidebar-logout"
          type="button"
          onClick={onSignOut}
          disabled={isSigningOut}
          aria-label={isSigningOut ? 'Signing out' : 'Sign out'}
        >
          <LogOut size={18} aria-hidden="true" />
          <span>{isSigningOut ? 'Signing out…' : 'Sign out'}</span>
        </button>
      </div>
    </aside>
  )
}

export function AppLayout() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [isDrawerOpen, setIsDrawerOpen] = useState(false)
  const [isCompact, setIsCompact] = useState(false)
  const [isSigningOut, setIsSigningOut] = useState(false)
  const drawerCloseButton = useRef<HTMLButtonElement>(null)
  const drawerTrigger = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    if (!isDrawerOpen) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    drawerCloseButton.current?.focus()
    const drawer = drawerCloseButton.current?.closest('aside')

    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        setIsDrawerOpen(false)
        drawerTrigger.current?.focus()
        return
      }
      if (event.key === 'Tab' && drawer) {
        const focusable = Array.from(
          drawer.querySelectorAll<HTMLElement>(
            'a[href], button:not([disabled]), [tabindex]:not([tabindex="-1"])',
          ),
        )
        const first = focusable[0]
        const last = focusable[focusable.length - 1]
        const active = document.activeElement
        if (event.shiftKey && (active === first || !drawer.contains(active))) {
          event.preventDefault()
          last?.focus()
        } else if (!event.shiftKey && (active === last || !drawer.contains(active))) {
          event.preventDefault()
          first?.focus()
        }
      }
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [isDrawerOpen])

  const closeDrawer = () => {
    setIsDrawerOpen(false)
    drawerTrigger.current?.focus()
  }

  const signOut = async () => {
    if (isSigningOut) return
    setIsSigningOut(true)
    await auth.logout()
    navigate('/login', { replace: true })
  }

  const userInitial = auth.user?.displayName.charAt(0).toUpperCase() ?? 'A'
  const displayName = auth.user?.displayName ?? 'Administrator'
  const roles = auth.user?.roles.join(', ') ?? 'Operations'

  return (
    <div className={`app-shell${isCompact ? ' app-shell--compact' : ''}`}>
      <a className="skip-link" href="#main-content">
        Skip to main content
      </a>

      <header className="mobile-header">
        <NavLink className="brand" to="/dashboard" aria-label="Restaurant Operations dashboard">
          <span className="brand-mark" aria-hidden="true">
            <ChefHat size={22} />
          </span>
          <span className="brand-copy">
            <strong>Ember</strong>
            <small>Restaurant operations</small>
          </span>
        </NavLink>
        <button
          ref={drawerTrigger}
          className="icon-button mobile-menu-button"
          type="button"
          aria-label="Open navigation menu"
          aria-expanded={isDrawerOpen}
          aria-controls="mobile-primary-sidebar"
          onClick={() => setIsDrawerOpen(true)}
        >
          <MenuIcon size={22} />
        </button>
      </header>

      {isDrawerOpen && (
        <button
          className="sidebar-backdrop"
          type="button"
          aria-label="Close navigation menu"
          onClick={closeDrawer}
        />
      )}

      <Sidebar
        id="desktop-primary-sidebar"
        mobile={false}
        displayName={displayName}
        roles={roles}
        userInitial={userInitial}
        isSigningOut={isSigningOut}
        onClose={closeDrawer}
        onNavigate={closeDrawer}
        onSignOut={signOut}
      />

      {isDrawerOpen && (
        <Sidebar
          id="mobile-primary-sidebar"
          mobile
          closeButtonRef={drawerCloseButton}
          displayName={displayName}
          roles={roles}
          userInitial={userInitial}
          isSigningOut={isSigningOut}
          onClose={closeDrawer}
          onNavigate={closeDrawer}
          onSignOut={signOut}
        />
      )}

      <button
        className="sidebar-collapse"
        type="button"
        aria-label={isCompact ? 'Expand navigation sidebar' : 'Collapse navigation sidebar'}
        aria-pressed={isCompact}
        onClick={() => setIsCompact((value) => !value)}
      >
        {isCompact ? <PanelLeftOpen size={18} /> : <PanelLeftClose size={18} />}
      </button>

      <main className="app-main" id="main-content" tabIndex={-1}>
        <Outlet />
      </main>
      <div className="app-utility-mark" aria-hidden="true">
        <LayoutGrid size={14} />
        <span>Live operations</span>
        <Settings2 size={13} />
      </div>
    </div>
  )
}
