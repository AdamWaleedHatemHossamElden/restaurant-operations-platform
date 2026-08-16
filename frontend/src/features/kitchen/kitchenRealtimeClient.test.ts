import { describe, expect, it } from 'vitest'

import { deriveKitchenWebSocketUrl } from './kitchenRealtimeClient'

describe('deriveKitchenWebSocketUrl', () => {
  it('derives the production WSS endpoint from the versioned HTTPS API URL', () => {
    expect(deriveKitchenWebSocketUrl('https://api.example.com/api/v1')).toBe(
      'wss://api.example.com/ws',
    )
  })

  it('derives the local WS endpoint from the versioned HTTP API URL', () => {
    expect(deriveKitchenWebSocketUrl('http://localhost:8080/api/v1')).toBe('ws://localhost:8080/ws')
  })
})
