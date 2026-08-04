import { render, screen } from '@testing-library/react'
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

vi.mock('../features/auth/authApi', () => ({
  currentUserRequest: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(),
  refreshRequest: vi.fn(),
}))

vi.mock('../features/health/HealthStatus', () => ({
  HealthStatus: () => <section aria-label="Backend connection">Connected</section>,
}))

const mockedCurrentUser = vi.mocked(currentUserRequest)
const mockedLogin = vi.mocked(loginRequest)
const mockedLogout = vi.mocked(logoutRequest)
const mockedRefresh = vi.mocked(refreshRequest)

function renderRoute(initialEntry: string) {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] })
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
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
    expect(
      screen.queryByRole('heading', { name: 'Good service starts with a clear view.' }),
    ).not.toBeInTheDocument()
  })

  it('redirects an unauthenticated protected request to login', async () => {
    renderRoute('/dashboard')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('renders an authenticated route after startup recovery', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/dashboard')

    expect(
      await screen.findByRole('heading', { name: 'Good service starts with a clear view.' }),
    ).toBeInTheDocument()
    expect(screen.getByText(testUser.email)).toBeInTheDocument()
  })

  it('redirects an already authenticated user away from login', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderRoute('/login')

    expect(
      await screen.findByRole('heading', { name: 'Good service starts with a clear view.' }),
    ).toBeInTheDocument()
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

    expect(
      await screen.findByRole('heading', { name: 'Good service starts with a clear view.' }),
    ).toBeInTheDocument()
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
    expect(
      await screen.findByRole('heading', { name: 'Good service starts with a clear view.' }),
    ).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/login')
    expect(mockedLogout).toHaveBeenCalledTimes(1)
  })
})
