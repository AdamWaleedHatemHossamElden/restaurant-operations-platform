import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it } from 'vitest'

import { NotFoundPage } from './NotFoundPage'

describe('not-found route', () => {
  it('renders the 404 page for an unknown route', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    )
    expect(screen.getByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
  })
})
