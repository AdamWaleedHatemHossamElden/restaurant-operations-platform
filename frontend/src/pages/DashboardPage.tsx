import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Link } from 'react-router-dom'

import { useAuth } from '../features/auth/authContext'
import { HealthStatus } from '../features/health/HealthStatus'

export function DashboardPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [isSigningOut, setIsSigningOut] = useState(false)

  if (!auth.user) {
    return null
  }

  const signOut = async () => {
    if (isSigningOut) {
      return
    }
    setIsSigningOut(true)
    await auth.logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="page dashboard-page">
      <section className="dashboard-welcome" aria-labelledby="dashboard-title">
        <div>
          <p className="eyebrow">Operations workspace</p>
          <h1 id="dashboard-title">Good service starts with a clear view.</h1>
          <p>
            Welcome, <strong>{auth.user.displayName}</strong>. Authentication is active; restaurant
            workflows will arrive in their planned phases.
          </p>
        </div>
        <button
          className="button button--secondary"
          type="button"
          onClick={signOut}
          disabled={isSigningOut}
        >
          {isSigningOut ? 'Signing out…' : 'Sign out'}
        </button>
      </section>

      <section className="identity-card" aria-labelledby="identity-title">
        <div className="identity-card__header">
          <span className="identity-avatar" aria-hidden="true">
            {auth.user.displayName.charAt(0).toUpperCase()}
          </span>
          <div>
            <p className="eyebrow">Current session</p>
            <h2 id="identity-title">{auth.user.displayName}</h2>
          </div>
        </div>
        <dl className="identity-details">
          <div>
            <dt>Email</dt>
            <dd>{auth.user.email}</dd>
          </div>
          <div>
            <dt>Roles</dt>
            <dd className="role-list">
              {auth.user.roles.map((role) => (
                <span className="role-chip" key={role}>
                  {role}
                </span>
              ))}
            </dd>
          </div>
        </dl>
      </section>

      <HealthStatus />

      <section className="phase-notice" aria-labelledby="orders-workspace-title">
        <p className="eyebrow">Phase 4B</p>
        <h2 id="orders-workspace-title">Capture service without losing price history.</h2>
        <p>
          Create table orders, select menu modifiers, preserve pricing snapshots, and progress each
          order through its controlled lifecycle.
        </p>
        <Link className="button button--primary button--link" to="/orders">
          Manage orders
        </Link>
      </section>

      <section className="phase-notice" aria-labelledby="menu-workspace-title">
        <p className="eyebrow">Phase 4A</p>
        <h2 id="menu-workspace-title">Shape the menu catalog.</h2>
        <p>
          Configure categories, decimal prices, sale availability, and reusable modifier choices.
        </p>
        <Link className="button button--primary button--link" to="/menu">
          Manage menu
        </Link>
      </section>

      <section className="phase-notice" aria-labelledby="reservations-workspace-title">
        <p className="eyebrow">Phase 3B</p>
        <h2 id="reservations-workspace-title">Plan the next arrival.</h2>
        <p>
          Create reservations, find suitable tables, and move guests through a controlled service
          workflow.
        </p>
        <Link className="button button--primary button--link" to="/reservations">
          Manage reservations
        </Link>
      </section>

      <section className="phase-notice" aria-labelledby="tables-workspace-title">
        <p className="eyebrow">Phase 3A</p>
        <h2 id="tables-workspace-title">Shape the dining room.</h2>
        <p>
          Manage table capacity, sections, service status, and active records from one workspace.
        </p>
        <Link className="button button--primary button--link" to="/tables">
          Manage tables
        </Link>
      </section>

      <section className="phase-notice" aria-labelledby="phase-notice-title">
        <p className="eyebrow">Secure foundation</p>
        <h2 id="phase-notice-title">Your authenticated workspace is ready.</h2>
        <p>
          Access remains protected by memory-only access tokens and backend-managed refresh cookies.
        </p>
      </section>
    </div>
  )
}
