import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient } from '../../lib/apiClient'
import { testSession, testUser } from '../../test/authFixtures'
import { AuthProvider } from './AuthProvider'
import { currentUserRequest, loginRequest, logoutRequest, refreshRequest } from './authApi'
import { useAuth } from './authContext'

vi.mock('./authApi', () => ({
  currentUserRequest: vi.fn(),
  loginRequest: vi.fn(),
  logoutRequest: vi.fn(),
  refreshRequest: vi.fn(),
}))

const mockedCurrentUser = vi.mocked(currentUserRequest)
const mockedLogin = vi.mocked(loginRequest)
const mockedLogout = vi.mocked(logoutRequest)
const mockedRefresh = vi.mocked(refreshRequest)
const originalAdapter = apiClient.defaults.adapter

function unauthorized(config: InternalAxiosRequestConfig) {
  const response: AxiosResponse = {
    data: {},
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  }
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, response)
}

function AuthProbe() {
  const auth = useAuth()
  return (
    <div>
      <p>
        {auth.isInitializing ? 'initializing' : auth.isAuthenticated ? 'authenticated' : 'guest'}
      </p>
      <p>{auth.user?.email ?? 'no user'}</p>
      <button
        type="button"
        onClick={() =>
          void auth
            .login({ email: 'operator@example.test', password: 'safe' })
            .catch(() => undefined)
        }
      >
        Login
      </button>
      <button type="button" onClick={() => void auth.logout()}>
        Logout
      </button>
      <button type="button" onClick={() => void auth.recoverSession()}>
        Recover
      </button>
    </div>
  )
}

function renderProvider() {
  return render(
    <AuthProvider>
      <AuthProbe />
    </AuthProvider>,
  )
}

describe('AuthProvider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedRefresh.mockRejectedValue(new Error('No refresh session'))
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
  })

  it('recovers a startup session through refresh and current-user lookup', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)

    renderProvider()

    expect(screen.getByText('initializing')).toBeInTheDocument()
    expect(await screen.findByText('authenticated')).toBeInTheDocument()
    expect(screen.getByText(testUser.email)).toBeInTheDocument()
    expect(mockedRefresh).toHaveBeenCalledTimes(1)
    expect(mockedCurrentUser).toHaveBeenCalledWith(testSession.accessToken)
  })

  it('finishes startup unauthenticated when refresh fails', async () => {
    renderProvider()

    expect(await screen.findByText('guest')).toBeInTheDocument()
    expect(screen.getByText('no user')).toBeInTheDocument()
    expect(mockedCurrentUser).not.toHaveBeenCalled()
  })

  it('cannot restore authentication when logout invalidates an in-flight startup refresh', async () => {
    let finishRefresh: ((session: typeof testSession) => void) | undefined
    mockedRefresh.mockReturnValue(
      new Promise((resolve) => {
        finishRefresh = resolve
      }),
    )
    mockedLogout.mockResolvedValue()

    renderProvider()
    expect(screen.getByText('initializing')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))
    expect(await screen.findByText('guest')).toBeInTheDocument()

    finishRefresh?.(testSession)
    await Promise.resolve()
    await Promise.resolve()

    expect(screen.getByText('guest')).toBeInTheDocument()
    expect(screen.getByText('no user')).toBeInTheDocument()
    expect(mockedCurrentUser).not.toHaveBeenCalled()
  })

  it('ignores an in-flight refresh that completes after logout', async () => {
    mockedRefresh.mockResolvedValueOnce(testSession)
    mockedCurrentUser.mockResolvedValueOnce(testUser)
    mockedLogout.mockResolvedValue()
    renderProvider()
    expect(await screen.findByText('authenticated')).toBeInTheDocument()

    let finishRefresh: ((session: typeof testSession) => void) | undefined
    mockedRefresh.mockReturnValueOnce(
      new Promise((resolve) => {
        finishRefresh = resolve
      }),
    )
    await userEvent.click(screen.getByRole('button', { name: 'Recover' }))
    await vi.waitFor(() => expect(mockedRefresh).toHaveBeenCalledTimes(2))
    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))

    finishRefresh?.({ ...testSession, accessToken: 'stale-recovered-token' })
    await Promise.resolve()
    await Promise.resolve()
    await vi.waitFor(() => expect(screen.getByText('guest')).toBeInTheDocument())
    expect(mockedCurrentUser).toHaveBeenCalledTimes(1)
    expect(screen.getByText('no user')).toBeInTheDocument()
  })

  it('ignores a current-user response that completes after logout', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    let finishCurrentUser: ((user: typeof testUser) => void) | undefined
    mockedCurrentUser.mockReturnValue(
      new Promise((resolve) => {
        finishCurrentUser = resolve
      }),
    )
    mockedLogout.mockResolvedValue()

    renderProvider()
    await vi.waitFor(() => expect(mockedCurrentUser).toHaveBeenCalledTimes(1))
    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))
    finishCurrentUser?.(testUser)
    await Promise.resolve()
    await Promise.resolve()

    expect(await screen.findByText('guest')).toBeInTheDocument()
    expect(screen.getByText('no user')).toBeInTheDocument()
    expect(screen.queryByText('authenticated')).not.toBeInTheDocument()
  })

  it('does not start refresh recovery for a delayed 401 after logout', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    mockedLogout.mockResolvedValue()
    renderProvider()
    expect(await screen.findByText('authenticated')).toBeInTheDocument()

    let rejectProtectedRequest: (() => void) | undefined
    apiClient.defaults.adapter = (config) =>
      new Promise((_resolve, reject) => {
        rejectProtectedRequest = () => reject(unauthorized(config))
      })
    const protectedRequest = apiClient.get('/protected')
    const rejectedRequest = expect(protectedRequest).rejects.toMatchObject({
      response: { status: 401 },
    })
    await vi.waitFor(() => expect(rejectProtectedRequest).toBeTypeOf('function'))

    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))
    rejectProtectedRequest?.()
    await rejectedRequest

    expect(mockedRefresh).toHaveBeenCalledTimes(1)
    expect(screen.getByText('guest')).toBeInTheDocument()
  })

  it('ignores a login response from a superseded session generation', async () => {
    mockedLogout.mockResolvedValue()
    let finishLogin: ((session: typeof testSession) => void) | undefined
    mockedLogin.mockReturnValue(
      new Promise((resolve) => {
        finishLogin = resolve
      }),
    )
    renderProvider()
    expect(await screen.findByText('guest')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Login' }))
    await vi.waitFor(() => expect(mockedLogin).toHaveBeenCalledTimes(1))
    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))
    finishLogin?.(testSession)
    await Promise.resolve()
    await Promise.resolve()

    expect(screen.getByText('guest')).toBeInTheDocument()
    expect(screen.getByText('no user')).toBeInTheDocument()
    expect(screen.queryByText('authenticated')).not.toBeInTheDocument()
  })

  it('continues to support normal recovery in the current session generation', async () => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    renderProvider()
    expect(await screen.findByText('authenticated')).toBeInTheDocument()

    mockedRefresh.mockResolvedValueOnce({ ...testSession, accessToken: 'fresh-access-token' })
    mockedCurrentUser.mockResolvedValueOnce(testUser)
    await userEvent.click(screen.getByRole('button', { name: 'Recover' }))

    await vi.waitFor(() => expect(mockedRefresh).toHaveBeenCalledTimes(2))
    await vi.waitFor(() => expect(mockedCurrentUser).toHaveBeenCalledTimes(2))
    expect(screen.getByText('authenticated')).toBeInTheDocument()
    expect(screen.getByText(testUser.email)).toBeInTheDocument()
  })

  it('never persists tokens during recovery or login', async () => {
    const localStorageWrite = vi.spyOn(Storage.prototype, 'setItem')
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    mockedLogin.mockResolvedValue({ ...testSession, accessToken: 'replacement-memory-token' })

    renderProvider()
    expect(await screen.findByText('authenticated')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Login' }))

    expect(localStorageWrite).not.toHaveBeenCalled()
    expect(window.localStorage).toHaveLength(0)
    expect(window.sessionStorage).toHaveLength(0)
  })

  it.each([
    ['successful remote logout', false],
    ['failed remote logout', true],
  ])('clears local authentication after %s', async (_label, shouldFail) => {
    mockedRefresh.mockResolvedValue(testSession)
    mockedCurrentUser.mockResolvedValue(testUser)
    if (shouldFail) {
      mockedLogout.mockRejectedValue(new Error('Network unavailable'))
    } else {
      mockedLogout.mockResolvedValue()
    }

    renderProvider()
    expect(await screen.findByText('authenticated')).toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Logout' }))

    expect(await screen.findByText('guest')).toBeInTheDocument()
    expect(mockedLogout).toHaveBeenCalledTimes(1)
  })
})
