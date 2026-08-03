import { Link } from 'react-router-dom'

export function NotFoundPage() {
  return (
    <section className="page not-found" aria-labelledby="not-found-title">
      <p className="eyebrow">404</p>
      <h1 id="not-found-title">Page not found</h1>
      <p>The requested foundation page does not exist.</p>
      <Link className="button button--link" to="/">
        Return home
      </Link>
    </section>
  )
}
