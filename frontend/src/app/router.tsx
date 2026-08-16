import { createBrowserRouter, Navigate, type RouteObject } from 'react-router-dom'

import { AppLayout } from '../components/layout/AppLayout'
import { AnonymousOnlyRoute, ProtectedRoute } from '../features/auth/AuthRoutes'
import { DashboardPage } from '../pages/DashboardPage'
import { LoginPage } from '../pages/LoginPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import {
  InventoryPage,
  KitchenPage,
  MenuPage,
  OrderDetailPage,
  OrdersPage,
  PaymentsPage,
  ReportsPage,
  ReservationsPage,
  StaffPage,
  TablesPage,
} from './deferredPages'

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
          { path: 'orders', element: <OrdersPage /> },
          { path: 'orders/:orderId', element: <OrderDetailPage /> },
          { path: 'payments', element: <PaymentsPage /> },
          { path: 'kitchen', element: <KitchenPage /> },
          { path: 'inventory', element: <InventoryPage /> },
          { path: 'staff', element: <StaffPage /> },
          { path: 'reports', element: <ReportsPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]

export const router = createBrowserRouter(routes)
