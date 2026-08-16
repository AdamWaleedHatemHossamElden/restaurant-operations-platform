import { render, screen, within } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { AuthProvider } from '../features/auth/AuthProvider'
import {
  currentUserRequest,
  loginRequest,
  logoutRequest,
  refreshRequest,
} from '../features/auth/authApi'
import { testSession, testUser } from '../test/authFixtures'
import { routes } from './router'

vi.mock('../features/reports/reportsApi', () => ({
  getOverviewReport: vi.fn().mockResolvedValue({
    completedOrders: 12,
    completedOrderValue: '420.00',
    averageCompletedOrderValue: '35.00',
    paymentsReceived: '390.00',
    paymentCount: 10,
    reservations: 8,
  }),
  reportKeys: { section: (...parts: unknown[]) => ['reports', ...parts] },
}))

vi.mock('../features/auth/authApi', () => ({
  currentUserRequest: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(),
  refreshRequest: vi.fn(),
}))

vi.mock('../features/health/HealthStatus', () => ({
  HealthStatus: () => <section aria-label="Backend connection">Connected</section>,
}))

vi.mock('../pages/TablesPage', () => ({
  TablesPage: () => <h1>Restaurant tables</h1>,
}))

vi.mock('../pages/ReservationsPage', () => ({
  ReservationsPage: () => <h1>Reservations</h1>,
}))

vi.mock('../pages/MenuPage', () => ({
  MenuPage: () => <h1>Menu management</h1>,
}))

vi.mock('../pages/OrdersPage', () => ({
  OrdersPage: () => <h1>Orders</h1>,
}))

vi.mock('../pages/OrderDetailPage', () => ({
  OrderDetailPage: () => <h1>Order detail</h1>,
}))

vi.mock('../pages/KitchenPage', () => ({
  KitchenPage: () => <h1>Kitchen display</h1>,
}))

vi.mock('../pages/InventoryPage', () => ({
  InventoryPage: () => <h1>Inventory workspace</h1>,
}))

vi.mock('../pages/StaffPage', () => ({
  StaffPage: () => <h1>Staff scheduling</h1>,
}))

vi.mock('../pages/PaymentsPage', () => ({
  PaymentsPage: () => <h1>Payments workspace</h1>,
}))

vi.mock('../pages/ReportsPage', () => ({
  ReportsPage: () => <h1>Reports workspace</h1>,
}))

const mockedCurrentUser = vi.mocked(currentUserRequest)
const mockedLogin = vi.mocked(loginRequest)
const mockedLogout = vi.mocked(logoutRequest)
const mockedRefresh = vi.mocked(refreshRequest)

function renderRoute(initialEntry: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] })
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <RouterProvider router={router} />
      </AuthProvider>
    </QueryClientProvider>,
  )
  return router
}

describe('authentication routing', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedRefresh.mockRejectedValue(new Error('No session'))
  })

  it('does not render protected or login content while session recovery is pending', () => {
    mockedRefresh.mockReturnValue(new Promise(() => undefined))
    renderRoute('/dashboard')

    expect(screen.getByText('Restoring your secure session…')).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: /Welcome back/ })).not.toBeInTheDocument()
  })

  it('redirects an unauthenticated protected request to login', async () => {
    renderRoute('/dashboard')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('protects the table-management route and renders it after recovery', async () => {
    const unauthenticated = renderRoute('/tables')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    unauthenticated.dispose()

    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/tables')
    expect(await screen.findByRole('heading', { name: 'Restaurant tables' })).toBeInTheDocument()
  })

  it('protects the reservation-management route and renders it after recovery', async () => {
    const unauthenticated = renderRoute('/reservations')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    unauthenticated.dispose()

    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/reservations')
    expect(await screen.findByRole('heading', { name: 'Reservations' })).toBeInTheDocument()
  })

  it('protects the menu-management route and renders it after recovery', async () => {
    const unauthenticated = renderRoute('/menu')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    unauthenticated.dispose()

    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/menu')
    expect(await screen.findByRole('heading', { name: 'Menu management' })).toBeInTheDocument()
  })

  it.each([
    ['/orders', 'Orders'],
    ['/orders/42', 'Order detail'],
    ['/kitchen', 'Kitchen display'],
    ['/inventory', 'Inventory workspace'],
    ['/staff', 'Staff scheduling'],
    ['/payments', 'Payments workspace'],
    ['/reports', 'Reports workspace'],
  ])('protects the order route %s and renders it after recovery', async (path, heading) => {
    const unauthenticated = renderRoute(path)
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    unauthenticated.dispose()

    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute(path)
    expect(await screen.findByRole('heading', { name: heading })).toBeInTheDocument()
  })

  it('renders an authenticated route after startup recovery', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/dashboard')

    expect(await screen.findByRole('heading', { name: /Welcome back/ })).toBeInTheDocument()
    expect(screen.getByText(testUser.displayName)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Kitchen' })).toHaveAttribute('href', '/kitchen')
    expect(screen.getByRole('link', { name: 'Staff' })).toHaveAttribute('href', '/staff')
    expect(screen.getAllByRole('link', { name: 'Payments' })[0]).toHaveAttribute(
      'href',
      '/payments',
    )
    expect(screen.getAllByRole('link', { name: 'Reports' })[0]).toHaveAttribute('href', '/reports')
    expect(screen.getByRole('link', { name: /Kitchen display/ })).toHaveAttribute(
      'href',
      '/kitchen',
    )
    expect(screen.queryByText(/Phase \d/)).not.toBeInTheDocument()
  })

  it('gives every primary navigation destination a stable accessible name', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/dashboard')
    await screen.findByRole('heading', { name: /Welcome back/ })

    const navigation = screen.getByRole('navigation', { name: 'Primary navigation' })
    const destinations = [
      'Dashboard',
      'Tables',
      'Reservations',
      'Orders',
      'Kitchen',
      'Payments',
      'Menu',
      'Inventory',
      'Staff',
      'Reports',
    ]
    destinations.forEach((name) => {
      expect(within(navigation).getByRole('link', { name })).toBeInTheDocument()
    })
  })

  it('keeps the mobile drawer unmounted while closed and restores focus for every close path', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    const router = renderRoute('/dashboard')
    await screen.findByRole('heading', { name: /Welcome back/ })
    const user = userEvent.setup()

    const trigger = screen.getByRole('button', { name: 'Open navigation menu' })
    expect(screen.queryByLabelText('Mobile application navigation')).not.toBeInTheDocument()
    await user.click(trigger)
    expect(trigger).toHaveAttribute('aria-expanded', 'true')
    const drawer = screen.getByLabelText('Mobile application navigation')
    expect(drawer).toHaveClass('sidebar--open')
    expect(within(drawer).getByRole('button', { name: 'Close navigation menu' })).toHaveFocus()
    expect(document.body.style.overflow).toBe('hidden')

    within(drawer).getByRole('button', { name: 'Sign out' }).focus()
    await user.tab()
    expect(
      within(drawer).getByRole('link', { name: 'Restaurant Operations dashboard' }),
    ).toHaveFocus()
    await user.tab({ shift: true })
    expect(within(drawer).getByRole('button', { name: 'Sign out' })).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByLabelText('Mobile application navigation')).not.toBeInTheDocument()
    expect(trigger).toHaveAttribute('aria-expanded', 'false')
    expect(trigger).toHaveFocus()
    expect(document.body.style.overflow).toBe('')

    await user.click(trigger)
    const reopenedDrawer = screen.getByLabelText('Mobile application navigation')
    await user.click(within(reopenedDrawer).getByRole('link', { name: 'Tables' }))
    expect(await screen.findByRole('heading', { name: 'Restaurant tables' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/tables')
    expect(screen.queryByLabelText('Mobile application navigation')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()

    await user.click(trigger)
    expect(screen.getByLabelText('Mobile application navigation')).toBeInTheDocument()
    const backdrop = document.querySelector<HTMLButtonElement>('.sidebar-backdrop')
    expect(backdrop).not.toBeNull()
    await user.click(backdrop!)
    expect(screen.queryByLabelText('Mobile application navigation')).not.toBeInTheDocument()
    expect(trigger).toHaveAttribute('aria-expanded', 'false')
    expect(trigger).toHaveFocus()
  })

  it('redirects an already authenticated user away from login', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/login')

    expect(await screen.findByRole('heading', { name: /Welcome back/ })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument()
  })

  it('returns to the originally requested protected destination after login', async () => {
    mockedLogin.mockResolvedValue(testSession)
    const router = renderRoute('/dashboard?view=service')
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email address'), 'operator@example.test')
    await user.type(screen.getByLabelText('Password'), 'valid-password')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))

    expect(await screen.findByRole('heading', { name: /Welcome back/ })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/dashboard')
    expect(router.state.location.search).toBe('?view=service')
  })

  it.each([
    ['successful logout', false],
    ['logout network failure', true],
  ])('%s clears access and redirects to login', async (_label, shouldFail) => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    if (shouldFail) {
      mockedLogout.mockRejectedValue(new Error('Network unavailable'))
    } else {
      mockedLogout.mockResolvedValue()
    }
    const router = renderRoute('/dashboard')
    expect(await screen.findByRole('heading', { name: /Welcome back/ })).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(mockedLogout).toHaveBeenCalledTimes(1)
  })
})
