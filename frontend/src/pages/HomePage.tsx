import { HealthStatus } from '../features/health/HealthStatus'

export function HomePage() {
  return (
    <div className="page home-page">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">Professional operations foundation</p>
        <h1 id="page-title">Restaurant Operations Platform</h1>
        <p className="hero-copy">
          A modern modular-monolith foundation for coordinating restaurant operations. Phase 1
          establishes the architecture, database migrations, API boundary, and test tooling.
        </p>
        <div className="foundation-note">
          <strong>Foundation only.</strong> Business workflows and authentication begin in later
          phases.
        </div>
      </section>

      <HealthStatus />

      <section className="principles" aria-labelledby="principles-title">
        <div>
          <p className="eyebrow">Phase 1 scope</p>
          <h2 id="principles-title">Built for the next controlled step</h2>
        </div>
        <ul>
          <li>React and TypeScript application shell</li>
          <li>Versioned Spring Boot REST boundary</li>
          <li>MySQL schema managed through Flyway</li>
          <li>Unit and integration-test foundations</li>
        </ul>
      </section>
    </div>
  )
}
