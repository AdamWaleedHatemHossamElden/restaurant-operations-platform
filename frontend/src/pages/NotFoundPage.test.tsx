import { render, screen } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { routes } from '../app/router'

describe('not-found route', () => {
  it('renders the 404 page for an unknown route', () => {
    const router = createMemoryRouter(routes, { initialEntries: ['/not-a-real-page'] })
    render(<RouterProvider router={router} />)
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
