import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { HealthStatus } from './HealthStatus'
import { fetchHealth } from './healthApi'

vi.mock('./healthApi', () => ({ fetchHealth: vi.fn() }))

const mockedFetchHealth = vi.mocked(fetchHealth)

function renderHealthStatus() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <HealthStatus />
    </QueryClientProvider>,
  )
}

describe('HealthStatus', () => {
  afterEach(() => vi.clearAllMocks())

  it('renders the loading state', () => {
    mockedFetchHealth.mockReturnValue(new Promise(() => undefined))
    renderHealthStatus()
    expect(screen.getByText('Checking connection…')).toBeInTheDocument()
  })

  it('renders the connected state', async () => {
    mockedFetchHealth.mockResolvedValue({ status: 'UP', service: 'restaurant-operations-backend' })
    renderHealthStatus()
    expect(await screen.findByRole('heading', { name: 'Connected' })).toBeInTheDocument()
    expect(screen.getByText('restaurant-operations-backend')).toBeInTheDocument()
  })

  it('renders an error state with a retry action', async () => {
    mockedFetchHealth.mockRejectedValue(new Error('Network unavailable'))
    renderHealthStatus()
    expect(await screen.findByRole('alert')).toHaveTextContent('Backend unavailable')
    expect(screen.getByRole('button', { name: 'Retry connection' })).toBeEnabled()
  })
})
