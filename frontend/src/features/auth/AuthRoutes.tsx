import { Navigate, Outlet, useLocation } from 'react-router-dom'

import { safePostLoginTarget } from './authNavigation'
import { useAuth } from './authContext'

export function SessionInitialization() {
  return (
    <main className="session-loading" aria-live="polite" aria-busy="true">
      <div className="session-loading__mark" aria-hidden="true">
        RO
      </div>
      <p>Restoring your secure session&hellip;</p>
    </main>
  )
}

export function ProtectedRoute() {
  const auth = useAuth()
  const location = useLocation()

  if (auth.isInitializing) {
    return <SessionInitialization />
  }

  if (!auth.isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location }} />
  }

  return <Outlet />
}

export function AnonymousOnlyRoute() {
  const auth = useAuth()
  const location = useLocation()

  if (auth.isInitializing) {
    return <SessionInitialization />
  }

  if (auth.isAuthenticated) {
    return <Navigate to={safePostLoginTarget(location.state)} replace />
  }

  return <Outlet />
}
