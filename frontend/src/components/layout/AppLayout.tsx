import { NavLink, Outlet } from 'react-router-dom'

export function AppLayout() {
  return (
    <div className="app-shell">
      <header className="site-header">
        <nav className="nav" aria-label="Primary navigation">
          <NavLink className="brand" to="/" aria-label="Restaurant Operations Platform home">
            <span className="brand-mark" aria-hidden="true">
              RO
            </span>
            <span>Restaurant Operations</span>
          </NavLink>
          <span className="phase-chip">Phase 1 · Foundation</span>
        </nav>
      </header>
      <main>
        <Outlet />
      </main>
      <footer className="site-footer">
        <p>Independent portfolio project · Technical foundation in progress</p>
      </footer>
    </div>
  )
}
