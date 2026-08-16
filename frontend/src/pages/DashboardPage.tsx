import { useQuery } from '@tanstack/react-query'
import {
  ArrowRight,
  BarChart3,
  CalendarDays,
  ChefHat,
  ClipboardList,
  CreditCard,
  Euro,
  PackageOpen,
  ReceiptText,
  TableProperties,
  TrendingUp,
  Users,
} from 'lucide-react'
import { Link } from 'react-router-dom'

import { useAuth } from '../features/auth/authContext'
import { HealthStatus } from '../features/health/HealthStatus'
import { formatEur } from '../features/menu/money'
import { getOverviewReport, reportKeys } from '../features/reports/reportsApi'
import { presetRange } from '../features/reports/reportTime'

const quickActions = [
  {
    to: '/orders',
    label: 'Open orders',
    description: 'Capture service and manage active checks.',
    icon: ClipboardList,
  },
  {
    to: '/reservations',
    label: 'Reservations',
    description: 'Review arrivals and table assignments.',
    icon: CalendarDays,
  },
  {
    to: '/kitchen',
    label: 'Kitchen display',
    description: 'Monitor live preparation and ticket flow.',
    icon: ChefHat,
  },
  {
    to: '/inventory',
    label: 'Inventory',
    description: 'Check stock levels and purchasing.',
    icon: PackageOpen,
  },
]

const managementLinks = [
  { to: '/tables', label: 'Dining room', icon: TableProperties },
  { to: '/menu', label: 'Menu catalog', icon: ReceiptText },
  { to: '/staff', label: 'Staff schedule', icon: Users },
  { to: '/payments', label: 'Payments', icon: CreditCard },
  { to: '/reports', label: 'Reports', icon: BarChart3 },
]

export function DashboardPage() {
  const auth = useAuth()
  const range = presetRange('LAST_30_DAYS')
  const overview = useQuery({
    queryKey: reportKeys.section('dashboard-overview', range),
    queryFn: () => getOverviewReport(range),
  })

  if (!auth.user) return null

  const firstName = auth.user.displayName.split(' ')[0]
  const metrics = overview.data
    ? [
        {
          label: 'Completed order value',
          value: formatEur(overview.data.completedOrderValue),
          detail: `${overview.data.completedOrders} completed orders`,
          icon: Euro,
        },
        {
          label: 'Average order',
          value: formatEur(overview.data.averageCompletedOrderValue),
          detail: 'Last 30 days',
          icon: TrendingUp,
        },
        {
          label: 'Payments received',
          value: formatEur(overview.data.paymentsReceived),
          detail: `${overview.data.paymentCount} settlements`,
          icon: CreditCard,
        },
        {
          label: 'Reservations',
          value: overview.data.reservations,
          detail: 'Planned in this period',
          icon: CalendarDays,
        },
      ]
    : []

  return (
    <div className="page dashboard-page">
      <header className="dashboard-welcome">
        <div>
          <p className="eyebrow">Operations overview</p>
          <h1>Welcome back, {firstName}.</h1>
          <p>
            Here is the current pulse of your restaurant and the tools for today&rsquo;s service.
          </p>
        </div>
        <div className="dashboard-date" aria-label="Current date">
          <CalendarDays size={18} aria-hidden="true" />
          <span>
            {new Intl.DateTimeFormat(undefined, {
              weekday: 'long',
              month: 'long',
              day: 'numeric',
            }).format(new Date())}
          </span>
        </div>
      </header>

      <section className="dashboard-section" aria-labelledby="dashboard-performance-title">
        <div className="section-heading">
          <div>
            <p className="eyebrow">Last 30 days</p>
            <h2 id="dashboard-performance-title">Performance at a glance</h2>
          </div>
          <Link className="text-link" to="/reports">
            View all reports <ArrowRight size={16} aria-hidden="true" />
          </Link>
        </div>
        {overview.isPending ? (
          <div className="metric-grid" aria-label="Loading performance metrics" aria-busy="true">
            {[0, 1, 2, 3].map((item) => (
              <div className="metric-card metric-card--loading" key={item} />
            ))}
          </div>
        ) : overview.isError ? (
          <div className="inline-state inline-state--error" role="alert">
            <div>
              <strong>Performance data is unavailable</strong>
              <span>Your operational tools are still available.</span>
            </div>
            <button
              className="button button--secondary"
              type="button"
              onClick={() => overview.refetch()}
            >
              Retry
            </button>
          </div>
        ) : (
          <div className="metric-grid">
            {metrics.map((metric) => {
              const Icon = metric.icon
              return (
                <article className="metric-card" key={metric.label}>
                  <div className="metric-card__icon" aria-hidden="true">
                    <Icon size={20} />
                  </div>
                  <span>{metric.label}</span>
                  <strong>{metric.value}</strong>
                  <small>{metric.detail}</small>
                </article>
              )
            })}
          </div>
        )}
      </section>

      <div className="dashboard-columns">
        <section className="dashboard-section" aria-labelledby="quick-actions-title">
          <div className="section-heading">
            <div>
              <p className="eyebrow">Start here</p>
              <h2 id="quick-actions-title">Quick access</h2>
            </div>
          </div>
          <div className="quick-action-grid">
            {quickActions.map((action) => {
              const Icon = action.icon
              return (
                <Link className="quick-action" to={action.to} key={action.to}>
                  <span className="quick-action__icon" aria-hidden="true">
                    <Icon size={21} />
                  </span>
                  <span>
                    <strong>{action.label}</strong>
                    <small>{action.description}</small>
                  </span>
                  <ArrowRight size={17} aria-hidden="true" />
                </Link>
              )
            })}
          </div>
        </section>

        <aside className="dashboard-side-column" aria-label="Workspace status and links">
          <HealthStatus />
          <section className="management-links" aria-labelledby="management-links-title">
            <div className="section-heading">
              <h2 id="management-links-title">Manage</h2>
            </div>
            {managementLinks.map((item) => {
              const Icon = item.icon
              return (
                <Link to={item.to} key={item.to}>
                  <Icon size={18} aria-hidden="true" />
                  <span>{item.label}</span>
                  <ArrowRight size={15} aria-hidden="true" />
                </Link>
              )
            })}
          </section>
        </aside>
      </div>
    </div>
  )
}
