import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { authenticationClient } from '../../lib/apiClient'
import { testSession } from '../../test/authFixtures'
import { currentUserRequest, loginRequest, logoutRequest, refreshRequest } from './authApi'

const originalAdapter = authenticationClient.defaults.adapter

function response(config: InternalAxiosRequestConfig, data: unknown, status = 200): AxiosResponse {
  return { data, status, statusText: 'OK', headers: {}, config }
}

describe('authentication API', () => {
  afterEach(() => {
    authenticationClient.defaults.adapter = originalAdapter
  })

  it('uses credentialed requests and the CSRF header for refresh and logout', async () => {
    const requests: InternalAxiosRequestConfig[] = []
    authenticationClient.defaults.adapter = async (config) => {
      requests.push(config)
      return response(config, config.url === '/auth/refresh' ? testSession : undefined)
    }

    await refreshRequest()
    await logoutRequest()

    expect(authenticationClient.defaults.withCredentials).toBe(true)
    expect(requests.map((request) => request.url)).toEqual(['/auth/refresh', '/auth/logout'])
    expect(requests.every((request) => request.headers.get('X-CSRF-Protection') === '1')).toBe(true)
  })

  it('sends the Bearer token when retrieving the current user', async () => {
    let authorization: unknown
    authenticationClient.defaults.adapter = async (config) => {
      authorization = config.headers.get('Authorization')
      return response(config, testSession.user)
    }

    await expect(currentUserRequest('memory-token')).resolves.toEqual(testSession.user)
    expect(authorization).toBe('Bearer memory-token')
  })

  it('rejects malformed authentication responses instead of trusting them', async () => {
    authenticationClient.defaults.adapter = async (config) =>
      response(config, { tokenType: 'Bearer' })

    await expect(
      loginRequest({ email: 'operator@example.test', password: 'not-a-real-secret' }),
    ).rejects.toMatchObject({ name: 'ZodError' })
  })
})
