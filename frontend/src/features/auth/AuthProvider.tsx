import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'

import { setApiAccessToken, setApiAuthRecoveryHandler } from '../../lib/apiClient'
import { currentUserRequest, loginRequest, logoutRequest, refreshRequest } from './authApi'
import { AuthContext, type AuthContextValue } from './authContext'
import type { CurrentUser, LoginCredentials } from './authTypes'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [isInitializing, setIsInitializing] = useState(true)
  const initializationStarted = useRef(false)
  const sessionGeneration = useRef(0)
  const recoveryEnabled = useRef(true)

  const clearLocalSession = useCallback(() => {
    setApiAccessToken(null)
    setUser(null)
  }, [])

  const blockRecoveryAndClearSession = useCallback(() => {
    recoveryEnabled.current = false
    setApiAuthRecoveryHandler(null)
    clearLocalSession()
  }, [clearLocalSession])

  const recoverAccessToken = useCallback(async (): Promise<string | null> => {
    const operationGeneration = sessionGeneration.current
    if (!recoveryEnabled.current) {
      return null
    }

    try {
      const session = await refreshRequest()
      if (operationGeneration !== sessionGeneration.current || !recoveryEnabled.current) {
        return null
      }

      setApiAccessToken(session.accessToken)
      const recoveredUser = await currentUserRequest(session.accessToken)

      if (operationGeneration !== sessionGeneration.current || !recoveryEnabled.current) {
        return null
      }

      if (!recoveredUser.enabled) {
        blockRecoveryAndClearSession()
        return null
      }

      setUser(recoveredUser)
      return session.accessToken
    } catch {
      if (operationGeneration === sessionGeneration.current) {
        blockRecoveryAndClearSession()
      }
      return null
    }
  }, [blockRecoveryAndClearSession])

  const recoverSession = useCallback(async () => {
    return (await recoverAccessToken()) !== null
  }, [recoverAccessToken])

  useEffect(() => {
    setApiAuthRecoveryHandler(recoverAccessToken)
    return () => setApiAuthRecoveryHandler(null)
  }, [recoverAccessToken])

  useEffect(() => {
    if (initializationStarted.current) {
      return
    }
    initializationStarted.current = true
    const operationGeneration = sessionGeneration.current
    void recoverSession().finally(() => {
      if (operationGeneration === sessionGeneration.current) {
        setIsInitializing(false)
      }
    })
  }, [recoverSession])

  const login = useCallback(
    async (credentials: LoginCredentials) => {
      const operationGeneration = sessionGeneration.current
      try {
        const session = await loginRequest(credentials)
        if (operationGeneration !== sessionGeneration.current) {
          throw new Error('Authentication operation was superseded')
        }
        if (!session.user.enabled) {
          throw new Error('Authenticated user is disabled')
        }
        recoveryEnabled.current = true
        setApiAccessToken(session.accessToken)
        setUser(session.user)
        setApiAuthRecoveryHandler(recoverAccessToken)
      } catch (error) {
        if (operationGeneration === sessionGeneration.current) {
          blockRecoveryAndClearSession()
        }
        throw error
      }
    },
    [blockRecoveryAndClearSession, recoverAccessToken],
  )

  const logout = useCallback(async () => {
    sessionGeneration.current += 1
    blockRecoveryAndClearSession()
    setIsInitializing(false)

    try {
      await logoutRequest()
    } catch {
      // The local session was already invalidated before the network request.
    }
  }, [blockRecoveryAndClearSession])

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isInitializing,
      login,
      logout,
      recoverSession,
    }),
    [isInitializing, login, logout, recoverSession, user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
