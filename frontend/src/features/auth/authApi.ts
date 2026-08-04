import { authenticationClient } from '../../lib/apiClient'
import {
  authSessionSchema,
  currentUserSchema,
  type AuthSession,
  type CurrentUser,
  type LoginCredentials,
} from './authTypes'

const csrfHeaders = {
  'X-CSRF-Protection': '1',
}

export async function loginRequest(credentials: LoginCredentials): Promise<AuthSession> {
  const response = await authenticationClient.post<unknown>('/auth/login', credentials)
  return authSessionSchema.parse(response.data)
}

export async function refreshRequest(): Promise<AuthSession> {
  const response = await authenticationClient.post<unknown>('/auth/refresh', undefined, {
    headers: csrfHeaders,
  })
  return authSessionSchema.parse(response.data)
}

export async function currentUserRequest(accessToken: string): Promise<CurrentUser> {
  const response = await authenticationClient.get<unknown>('/auth/me', {
    headers: { Authorization: `Bearer ${accessToken}` },
  })
  return currentUserSchema.parse(response.data)
}

export async function logoutRequest(): Promise<void> {
  await authenticationClient.post('/auth/logout', undefined, { headers: csrfHeaders })
}
