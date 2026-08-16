import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import { Dialog } from './Dialog'

function DialogHarness({ onClose = () => undefined }: { onClose?: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        Add record
      </button>
      {open && (
        <Dialog
          className="dialog"
          labelledBy="test-dialog-title"
          onClose={() => {
            onClose()
            setOpen(false)
          }}
        >
          <header>
            <h2 id="test-dialog-title">Create record</h2>
            <button type="button" aria-label="Close dialog" onClick={() => setOpen(false)}>
              Close
            </button>
          </header>
          <label>
            Name
            <input />
          </label>
          <button type="button">Cancel</button>
          <button type="button">Save</button>
        </Dialog>
      )}
    </>
  )
}

describe('Dialog', () => {
  it('provides modal semantics and moves focus to the first meaningful form control', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)

    await user.click(screen.getByRole('button', { name: 'Add record' }))

    expect(screen.getByRole('dialog', { name: 'Create record' })).toHaveAttribute(
      'aria-modal',
      'true',
    )
    expect(screen.getByRole('textbox', { name: 'Name' })).toHaveFocus()
  })

  it('traps forward and reverse tab navigation inside the dialog', async () => {
    const user = userEvent.setup()
    render(<DialogHarness />)
    await user.click(screen.getByRole('button', { name: 'Add record' }))

    screen.getByRole('button', { name: 'Save' }).focus()
    await user.tab()
    expect(screen.getByRole('button', { name: 'Close dialog' })).toHaveFocus()

    await user.tab({ shift: true })
    expect(screen.getByRole('button', { name: 'Save' })).toHaveFocus()
  })

  it('closes on Escape and restores focus to the opener', async () => {
    const onClose = vi.fn()
    const user = userEvent.setup()
    render(<DialogHarness onClose={onClose} />)
    const trigger = screen.getByRole('button', { name: 'Add record' })
    await user.click(trigger)

    await user.keyboard('{Escape}')

    expect(onClose).toHaveBeenCalledOnce()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
