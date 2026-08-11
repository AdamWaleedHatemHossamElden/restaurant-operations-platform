import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { KitchenRealtimeProvider } from './KitchenRealtimeProvider'
import { useKitchenRealtimeState } from './kitchenRealtimeContext'
import type { KitchenRealtimeEvent } from './kitchenTypes'

const mocks = vi.hoisted(() => ({
  authenticated: true,
  start: vi.fn(),
  stop: vi.fn(async () => undefined),
  options: null as null | {
    onConnect: () => void
    onEvent: (event: KitchenRealtimeEvent) => void
    onStateChange: (state: 'connecting' | 'connected' | 'reconnecting' | 'disconnected') => void
  },
}))

vi.mock('../auth/authContext', () => ({
  useAuth: () => ({ isAuthenticated: mocks.authenticated }),
}))
vi.mock('./kitchenRealtimeClient', () => ({
  startKitchenRealtime: (options: typeof mocks.options) => {
    mocks.options = options
    mocks.start(options)
    return { stop: mocks.stop }
  },
}))

function StateProbe() {
  return <span>{useKitchenRealtimeState()}</span>
}

function view(client: QueryClient) {
  return (
    <QueryClientProvider client={client}>
      <KitchenRealtimeProvider>
        <StateProbe />
      </KitchenRealtimeProvider>
    </QueryClientProvider>
  )
}

describe('kitchen realtime provider', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.authenticated = true
    mocks.options = null
  })

  it('keeps one connection across rerenders and invalidates REST state on connect and events', async () => {
    const client = new QueryClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const rendered = render(view(client))
    expect(mocks.start).toHaveBeenCalledTimes(1)

    rendered.rerender(view(client))
    expect(mocks.start).toHaveBeenCalledTimes(1)

    act(() => mocks.options?.onStateChange('connected'))
    expect(screen.getByText('connected')).toBeInTheDocument()
    act(() => mocks.options?.onConnect())
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['kitchen'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['orders'] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['inventory'] })

    act(() =>
      mocks.options?.onEvent({
        eventType: 'KITCHEN_ITEM_STATUS_CHANGED',
        ticketId: 4,
        orderId: 7,
        orderNumber: 'ORD-1',
        ticketStatus: 'PREPARING',
        kitchenItemId: 9,
        kitchenItemStatus: 'PREPARING',
        timestamp: '2030-01-01T10:00:00Z',
      }),
    )
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['kitchen', 'tickets', 4] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['kitchen', 'orders', 7] })
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['orders', 7] })
  })

  it('treats every reconnect as missed-event recovery and disconnects on logout', async () => {
    const client = new QueryClient()
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    const rendered = render(view(client))
    act(() => {
      mocks.options?.onConnect()
      mocks.options?.onConnect()
    })
    expect(invalidate).toHaveBeenCalledTimes(6)

    mocks.authenticated = false
    rendered.rerender(view(client))
    await waitFor(() => expect(mocks.stop).toHaveBeenCalledTimes(1))
    expect(screen.getByText('disconnected')).toBeInTheDocument()
  })
})
