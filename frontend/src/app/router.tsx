import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom'

import { AppLayout } from '../components/layout/AppLayout'
import { AnonymousOnlyRoute, ProtectedRoute } from '../features/auth/AuthRoutes'
import { DashboardPage } from '../pages/DashboardPage'
import { LoginPage } from '../pages/LoginPage'
import { MenuPage } from '../pages/MenuPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { ReservationsPage } from '../pages/ReservationsPage'
import { TablesPage } from '../pages/TablesPage'

export const routes: RouteObject[] = [
  {
    element: <AnonymousOnlyRoute />,
    children: [{ path: '/login', element: <LoginPage /> }],
  },
  {
    element: <ProtectedRoute />,
    children: [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'tables', element: <TablesPage /> },
          { path: 'reservations', element: <ReservationsPage /> },
          { path: 'menu', element: <MenuPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
