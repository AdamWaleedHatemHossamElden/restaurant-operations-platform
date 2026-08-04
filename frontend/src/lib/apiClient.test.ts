import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { apiClient, setApiAccessToken, setApiAuthRecoveryHandler } from './apiClient'

const originalAdapter = apiClient.defaults.adapter

function success(config: InternalAxiosRequestConfig): AxiosResponse {
  return {
    data: { ok: true },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
  }
}

function unauthorized(config: InternalAxiosRequestConfig) {
  return new AxiosError('Unauthorized', 'ERR_BAD_REQUEST', config, undefined, {
    data: {},
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  })
}

describe('authenticated API client', () => {
  beforeEach(() => {
    setApiAccessToken(null)
    setApiAuthRecoveryHandler(null)
  })

  afterEach(() => {
    apiClient.defaults.adapter = originalAdapter
    setApiAccessToken(null)
    setApiAuthRecoveryHandler(null)
    vi.clearAllMocks()
  })

  it('attaches the in-memory access token as a Bearer credential', async () => {
    let authorization: unknown
    apiClient.defaults.adapter = async (config) => {
      authorization = config.headers.get('Authorization')
      return success(config)
    }
    setApiAccessToken('memory-only-token')

    await apiClient.get('/protected')

    expect(authorization).toBe('Bearer memory-only-token')
  })

  it('shares one refresh operation across simultaneous 401 responses', async () => {
    let finishRecovery: ((token: string | null) => void) | undefined
    const recover = vi.fn(
      () =>
        new Promise<string | null>((resolve) => {
          finishRecovery = resolve
        }),
    )
    const attempts = new Map<string, number>()

    apiClient.defaults.adapter = async (config) => {
      const path = config.url ?? 'unknown'
      attempts.set(path, (attempts.get(path) ?? 0) + 1)
      if (config.headers.get('Authorization') === 'Bearer expired-token') {
        throw unauthorized(config)
      }
      return success(config)
    }
    setApiAccessToken('expired-token')
    setApiAuthRecoveryHandler(recover)

    const first = apiClient.get('/protected/one')
    const second = apiClient.get('/protected/two')
    await vi.waitFor(() => expect(recover).toHaveBeenCalledTimes(1))
    setApiAccessToken('rotated-token')
    finishRecovery?.('rotated-token')

    await expect(Promise.all([first, second])).resolves.toHaveLength(2)
    expect(recover).toHaveBeenCalledTimes(1)
    expect(attempts.get('/protected/one')).toBe(2)
    expect(attempts.get('/protected/two')).toBe(2)
  })

  it('stops after one retry and does not create an infinite refresh loop', async () => {
    const recover = vi.fn(async () => {
      setApiAccessToken('still-invalid-token')
      return 'still-invalid-token'
    })
    let attempts = 0
    apiClient.defaults.adapter = async (config) => {
      attempts += 1
      throw unauthorized(config)
    }
    setApiAccessToken('expired-token')
    setApiAuthRecoveryHandler(recover)

    await expect(apiClient.get('/protected')).rejects.toMatchObject({ response: { status: 401 } })
    expect(recover).toHaveBeenCalledTimes(1)
    expect(attempts).toBe(2)
  })

  it('rejects safely and leaves later requests unauthenticated after failed recovery', async () => {
    const observedAuthorization: unknown[] = []
    const recover = vi.fn(async () => {
      setApiAccessToken(null)
      return null
    })
    apiClient.defaults.adapter = async (config) => {
      observedAuthorization.push(config.headers.get('Authorization'))
      if (observedAuthorization.length === 1) {
        throw unauthorized(config)
      }
      return success(config)
    }
    setApiAccessToken('expired-token')
    setApiAuthRecoveryHandler(recover)

    await expect(apiClient.get('/protected')).rejects.toMatchObject({ response: { status: 401 } })
    await apiClient.get('/public-after-failure')

    expect(recover).toHaveBeenCalledTimes(1)
    expect(observedAuthorization).toEqual(['Bearer expired-token', undefined])
  })
})
