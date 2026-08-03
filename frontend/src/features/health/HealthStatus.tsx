import { useQuery } from '@tanstack/react-query'

import { fetchHealth } from './healthApi'

export function HealthStatus() {
  const healthQuery = useQuery({
    queryKey: ['backend-health'],
    queryFn: fetchHealth,
  })

  if (healthQuery.isPending) {
    return (
      <section className="status-card" aria-live="polite" aria-busy="true">
        <span className="status-indicator status-indicator--loading" aria-hidden="true" />
        <div>
          <p className="eyebrow">Backend connection</p>
          <h2>Checking connection…</h2>
          <p>Contacting the local API health endpoint.</p>
        </div>
      </section>
    )
  }

  if (healthQuery.isError) {
    return (
      <section className="status-card status-card--error" role="alert">
        <span className="status-indicator status-indicator--error" aria-hidden="true" />
        <div>
          <p className="eyebrow">Backend connection</p>
          <h2>Backend unavailable</h2>
          <p>Start the API and MySQL, then try the connection again.</p>
          <button className="button" type="button" onClick={() => healthQuery.refetch()}>
            Retry connection
          </button>
        </div>
      </section>
    )
  }

  return (
    <section className="status-card status-card--success" aria-live="polite">
      <span className="status-indicator status-indicator--success" aria-hidden="true" />
      <div>
        <p className="eyebrow">Backend connection</p>
        <h2>Connected</h2>
        <p>
          <code>{healthQuery.data.service}</code> reports {healthQuery.data.status}.
        </p>
      </div>
    </section>
  )
}
