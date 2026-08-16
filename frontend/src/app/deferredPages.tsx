import { lazy, Suspense, type ComponentType } from 'react'

function named<T extends string>(name: T) {
  return (module: Record<T, ComponentType>) => ({ default: module[name] })
}

function deferred(Page: ComponentType) {
  return function DeferredPage() {
    return (
      <Suspense
        fallback={
          <div className="page table-state" role="status">
            Loading workspace&hellip;
          </div>
        }
      >
        <Page />
      </Suspense>
    )
  }
}

export const TablesPage = deferred(
  lazy(() => import('../pages/TablesPage').then(named('TablesPage'))),
)
export const ReservationsPage = deferred(
  lazy(() => import('../pages/ReservationsPage').then(named('ReservationsPage'))),
)
export const MenuPage = deferred(lazy(() => import('../pages/MenuPage').then(named('MenuPage'))))
export const OrdersPage = deferred(
  lazy(() => import('../pages/OrdersPage').then(named('OrdersPage'))),
)
export const OrderDetailPage = deferred(
  lazy(() => import('../pages/OrderDetailPage').then(named('OrderDetailPage'))),
)
export const PaymentsPage = deferred(
  lazy(() => import('../pages/PaymentsPage').then(named('PaymentsPage'))),
)
export const KitchenPage = deferred(
  lazy(() => import('../pages/KitchenPage').then(named('KitchenPage'))),
)
export const InventoryPage = deferred(
  lazy(() => import('../pages/InventoryPage').then(named('InventoryPage'))),
)
export const StaffPage = deferred(lazy(() => import('../pages/StaffPage').then(named('StaffPage'))))
export const ReportsPage = deferred(
  lazy(() => import('../pages/ReportsPage').then(named('ReportsPage'))),
)
