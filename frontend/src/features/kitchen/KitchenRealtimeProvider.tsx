import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'

import { useAuth } from '../auth/authContext'
import { inventoryKeys } from '../inventory/inventoryApi'
import { orderKeys } from '../orders/ordersApi'
import { kitchenKeys } from './kitchenApi'
import { startKitchenRealtime, type KitchenConnectionState } from './kitchenRealtimeClient'
import { KitchenRealtimeContext } from './kitchenRealtimeContext'

export function KitchenRealtimeProvider({ children }: { children: ReactNode }) {
  const { isAuthenticated } = useAuth()
  const queryClient = useQueryClient()
  const [state, setState] = useState<KitchenConnectionState>('disconnected')

  useEffect(() => {
    if (!isAuthenticated) {
      return
    }
    const invalidateAll = () => {
      void queryClient.invalidateQueries({ queryKey: kitchenKeys.all })
      void queryClient.invalidateQueries({ queryKey: orderKeys.all })
      void queryClient.invalidateQueries({ queryKey: inventoryKeys.all })
    }
    const connection = startKitchenRealtime({
      onStateChange: setState,
      onConnect: invalidateAll,
      onEvent: (event) => {
        invalidateAll()
        void queryClient.invalidateQueries({ queryKey: kitchenKeys.detail(event.ticketId) })
        void queryClient.invalidateQueries({ queryKey: kitchenKeys.order(event.orderId) })
        void queryClient.invalidateQueries({ queryKey: orderKeys.detail(event.orderId) })
      },
    })
    return () => {
      void connection.stop()
    }
  }, [isAuthenticated, queryClient])

  const value = useMemo(() => (isAuthenticated ? state : 'disconnected'), [isAuthenticated, state])
  return <KitchenRealtimeContext.Provider value={value}>{children}</KitchenRealtimeContext.Provider>
}
