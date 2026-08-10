import axios, { AxiosHeaders, type InternalAxiosRequestConfig } from 'axios'

import { env } from './env'

const clientOptions = {
  baseURL: env.VITE_API_BASE_URL,
  timeout: 5_000,
  withCredentials: true,
  headers: {
    Accept: 'application/json',
  },
}

export const authenticationClient = axios.create(clientOptions)
export const apiClient = axios.create(clientOptions)

type RetriableRequestConfig = InternalAxiosRequestConfig & {
  authRetryAttempted?: boolean
}

type AuthRecoveryHandler = () => Promise<string | null>

let accessToken: string | null = null
let authRecoveryHandler: AuthRecoveryHandler | null = null
let recoveryInFlight: Promise<string | null> | null = null

export function setApiAccessToken(token: string | null) {
  accessToken = token
}

export function getApiAccessToken() {
  return accessToken
}

export function setApiAuthRecoveryHandler(handler: AuthRecoveryHandler | null) {
  authRecoveryHandler = handler
  if (!handler) {
    recoveryInFlight = null
  }
}

export async function recoverApiAccessToken(): Promise<string | null> {
  if (!authRecoveryHandler) return null
  if (!recoveryInFlight) {
    const recovery = authRecoveryHandler()
    recoveryInFlight = recovery
    const clearCompletedRecovery = () => {
      if (recoveryInFlight === recovery) recoveryInFlight = null
    }
    void recovery.then(clearCompletedRecovery, clearCompletedRecovery)
  }
  return recoveryInFlight
}

apiClient.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers = AxiosHeaders.from(config.headers)
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401 || !error.config) {
      return Promise.reject(error)
    }

    const request = error.config as RetriableRequestConfig
    const requestAuthorization = AxiosHeaders.from(request.headers).get('Authorization')

    if (request.authRetryAttempted || !requestAuthorization || !authRecoveryHandler) {
      return Promise.reject(error)
    }

    request.authRetryAttempted = true

    const currentAuthorization = accessToken ? `Bearer ${accessToken}` : null
    if (currentAuthorization && requestAuthorization !== currentAuthorization) {
      request.headers = AxiosHeaders.from(request.headers)
      request.headers.set('Authorization', currentAuthorization)
      return apiClient.request(request)
    }

    const recoveredToken = await recoverApiAccessToken()
    if (!recoveredToken) {
      return Promise.reject(error)
    }

    request.headers = AxiosHeaders.from(request.headers)
    request.headers.set('Authorization', `Bearer ${recoveredToken}`)
    return apiClient.request(request)
  },
)
