import { createContext, useContext } from 'react'

import type { KitchenConnectionState } from './kitchenRealtimeClient'

export const KitchenRealtimeContext = createContext<KitchenConnectionState>('disconnected')

export function useKitchenRealtimeState() {
  return useContext(KitchenRealtimeContext)
}
