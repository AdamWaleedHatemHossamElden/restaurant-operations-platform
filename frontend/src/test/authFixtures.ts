import type { AuthSession, CurrentUser } from '../features/auth/authTypes'

export const testUser: CurrentUser = {
  id: 42,
  email: 'operator@example.test',
  displayName: 'Alex Operator',
  enabled: true,
  roles: ['ADMIN'],
}

export const testSession: AuthSession = {
  accessToken: 'test-access-token-kept-in-memory',
  tokenType: 'Bearer',
  expiresIn: 900,
  user: testUser,
}
