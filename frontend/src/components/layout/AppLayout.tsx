import { NavLink, Outlet } from 'react-router-dom'

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="site-header">
        <nav className="nav" aria-label="Primary navigation">
          <NavLink className="brand" to="/dashboard" aria-label="Restaurant Operations dashboard">
            <span className="brand-mark" aria-hidden="true">
              RO
            </span>
            <span>Restaurant Operations</span>
          </NavLink>
          <span className="phase-chip">Secure workspace</span>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="site-footer">
        <p>Restaurant Operations Platform &middot; Authenticated workspace</p>
      </footer>
    </div>
  )
}
