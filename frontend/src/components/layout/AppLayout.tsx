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
          <div className="nav-links">
            <NavLink to="/dashboard">Dashboard</NavLink>
            <NavLink to="/tables">Tables</NavLink>
            <NavLink to="/reservations">Reservations</NavLink>
            <NavLink to="/menu">Menu</NavLink>
            <NavLink to="/orders">Orders</NavLink>
            <NavLink to="/kitchen">Kitchen</NavLink>
          </div>
          <span className="phase-chip">Admin workspace</span>
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
