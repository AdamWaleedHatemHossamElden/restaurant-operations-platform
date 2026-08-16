import { Component, type ReactNode } from 'react'

type Props = { children: ReactNode }
type State = { failed: boolean }

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { failed: false }

  static getDerivedStateFromError(): State {
    return { failed: true }
  }

  componentDidCatch() {
    // React reports the exception to the console. A production telemetry provider can be
    // connected here during deployment without exposing failure details in the UI.
  }

  render() {
    if (this.state.failed) {
      return (
        <main className="page" aria-labelledby="application-error-title">
          <section className="empty-state" role="alert">
            <h1 id="application-error-title">The application could not continue</h1>
            <p>No changes were submitted. Reload the page to restore the latest server state.</p>
            <button
              className="button button--primary"
              type="button"
              onClick={() => location.reload()}
            >
              Reload application
            </button>
          </section>
        </main>
      )
    }

    return this.props.children
  }
}
