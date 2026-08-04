import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { routes } from '../app/router'
import { AuthProvider } from '../features/auth/AuthProvider'
import {
  currentUserRequest,
  loginRequest,
  logoutRequest,
  refreshRequest,
} from '../features/auth/authApi'
import { testSession } from '../test/authFixtures'

vi.mock('../features/auth/authApi', () => ({
  currentUserRequest: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(),
  refreshRequest: vi.fn(),
}))

vi.mock('../features/health/HealthStatus', () => ({
  HealthStatus: () => <section aria-label="Backend connection">Connected</section>,
}))

const mockedLogin = vi.mocked(loginRequest)
const mockedRefresh = vi.mocked(refreshRequest)

function renderLogin(initialEntry = '/login') {
  const router = createMemoryRouter(routes, { initialEntries: [initialEntry] })
  render(
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>,
  )
  return router
}

async function completeForm() {
  const user = userEvent.setup()
  await user.type(screen.getByLabelText('Email address'), 'operator@example.test')
  await user.type(screen.getByLabelText('Password'), 'valid-password')
  return user
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(currentUserRequest).mockReset()
    vi.mocked(logoutRequest).mockReset()
    mockedRefresh.mockRejectedValue(new Error('No existing session'))
  })

  it('shows useful validation and does not submit invalid input', async () => {
    renderLogin()
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()

    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Email address'), 'not-an-email')
    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument()
    expect(screen.getByText('Enter your password')).toBeInTheDocument()
    expect(mockedLogin).not.toHaveBeenCalled()
  })

  it('logs in once and redirects to the authenticated dashboard', async () => {
    let finishLogin: ((session: typeof testSession) => void) | undefined
    mockedLogin.mockReturnValue(
      new Promise((resolve) => {
        finishLogin = resolve
      }),
    )
    renderLogin()
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    const user = await completeForm()

    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))
    const submittingButton = screen.getByRole('button', { name: 'Signing in…' })
    expect(submittingButton).toBeDisabled()
    await user.click(submittingButton)
    expect(mockedLogin).toHaveBeenCalledTimes(1)

    finishLogin?.(testSession)
    expect(
      await screen.findByRole('heading', { name: 'Good service starts with a clear view.' }),
    ).toBeInTheDocument()
    expect(screen.getByText(testSession.user.email)).toBeInTheDocument()
  })

  it('shows the same safe generic message for rejected authentication', async () => {
    mockedLogin.mockRejectedValue(new Error('Raw backend detail that must not render'))
    renderLogin()
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    const user = await completeForm()

    await user.click(screen.getByRole('button', { name: 'Sign in securely' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Unable to sign in. Check your credentials and try again.',
    )
    expect(screen.queryByText(/Raw backend detail/)).not.toBeInTheDocument()
  })
})
