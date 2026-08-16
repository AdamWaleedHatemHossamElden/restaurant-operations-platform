import { Client, type IMessage } from '@stomp/stompjs'

import { getApiAccessToken, recoverApiAccessToken } from '../../lib/apiClient'
import { env } from '../../lib/env'
import { kitchenRealtimeEventSchema, type KitchenRealtimeEvent } from './kitchenTypes'

export type KitchenConnectionState = 'connecting' | 'connected' | 'reconnecting' | 'disconnected'

type Options = {
  onEvent: (event: KitchenRealtimeEvent) => void
  onConnect: () => void
  onStateChange: (state: KitchenConnectionState) => void
}

export function deriveKitchenWebSocketUrl(apiBaseUrl: string) {
  const url = new URL(apiBaseUrl)
  url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:'
  url.pathname = `${url.pathname.replace(/\/api\/v1\/?$/, '')}/ws`
  url.search = ''
  url.hash = ''
  return url.toString()
}

function websocketUrl() {
  return deriveKitchenWebSocketUrl(env.VITE_API_BASE_URL)
}

function tokenExpiry(token: string) {
  try {
    const encoded = token.split('.')[1]
    if (!encoded) return null
    const normalized = encoded.replace(/-/g, '+').replace(/_/g, '/')
    const claims = JSON.parse(atob(normalized)) as { exp?: unknown }
    return typeof claims.exp === 'number' ? claims.exp * 1000 : null
  } catch {
    return null
  }
}

export function startKitchenRealtime(options: Options) {
  let stopped = false
  let renewalTimer: ReturnType<typeof setTimeout> | null = null
  const clearRenewal = () => {
    if (renewalTimer) clearTimeout(renewalTimer)
    renewalTimer = null
  }

  const client = new Client({
    brokerURL: websocketUrl(),
    reconnectDelay: 5_000,
    connectionTimeout: 8_000,
    heartbeatIncoming: 10_000,
    heartbeatOutgoing: 10_000,
    debug: () => undefined,
  })

  client.beforeConnect = async () => {
    options.onStateChange(client.connected ? 'connected' : 'connecting')
    let token = getApiAccessToken()
    const expiresAt = token ? tokenExpiry(token) : null
    if (!token || (expiresAt !== null && expiresAt - Date.now() <= 60_000)) {
      token = await recoverApiAccessToken()
    }
    if (!token || stopped) throw new Error('Authentication is unavailable')
    client.connectHeaders = { Authorization: `Bearer ${token}` }
  }

  client.onConnect = () => {
    options.onStateChange('connected')
    options.onConnect()
    client.subscribe('/topic/kitchen', (message: IMessage) => {
      try {
        const parsed = kitchenRealtimeEventSchema.safeParse(JSON.parse(message.body))
        if (parsed.success) options.onEvent(parsed.data)
      } catch {
        // Invalid notification payloads are ignored; REST remains authoritative.
      }
    })
    clearRenewal()
    const token = getApiAccessToken()
    const expiresAt = token ? tokenExpiry(token) : null
    if (expiresAt !== null) {
      const delay = Math.max(1_000, expiresAt - Date.now() - 30_000)
      renewalTimer = setTimeout(() => {
        if (!stopped) {
          void client.deactivate().then(() => {
            if (!stopped) client.activate()
          })
        }
      }, delay)
    }
  }
  client.onWebSocketClose = () => {
    clearRenewal()
    options.onStateChange(stopped ? 'disconnected' : 'reconnecting')
  }
  client.onStompError = () => options.onStateChange('reconnecting')
  client.onWebSocketError = () => options.onStateChange('reconnecting')
  client.activate()

  return {
    stop: async () => {
      stopped = true
      clearRenewal()
      await client.deactivate()
      options.onStateChange('disconnected')
    },
  }
}
