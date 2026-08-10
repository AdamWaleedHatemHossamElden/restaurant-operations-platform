import { RouterProvider } from 'react-router-dom'

import { AuthProvider } from '../features/auth/AuthProvider'
import { KitchenRealtimeProvider } from '../features/kitchen/KitchenRealtimeProvider'
import { router } from './router'

export function App() {
  return (
    <AuthProvider>
      <KitchenRealtimeProvider>
        <RouterProvider router={router} />
      </KitchenRealtimeProvider>
    </AuthProvider>
  )
}
