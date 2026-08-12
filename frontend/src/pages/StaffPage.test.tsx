import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as staffApi from '../features/staff/staffApi'
import { addLocalDays, startOfLocalWeek } from '../features/staff/staffTime'
import type { Availability, Employee, Shift } from '../features/staff/staffTypes'
import { StaffPage } from './StaffPage'

vi.mock('../features/staff/staffApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/staff/staffApi')>()
  return {
    ...original,
    listEmployees: vi.fn(),
    listAvailability: vi.fn(),
    listShifts: vi.fn(),
    saveEmployee: vi.fn(),
    toggleEmployee: vi.fn(),
    saveAvailability: vi.fn(),
    removeAvailability: vi.fn(),
    saveShift: vi.fn(),
    transitionShift: vi.fn(),
  }
})

const week = startOfLocalWeek()
const at = (day: number, hour: number) => {
  const value = addLocalDays(week, day)
  value.setHours(hour, 0, 0, 0)
  return value.toISOString()
}
const employee: Employee = {
  id: 1,
  employeeCode: 'EMP001',
  firstName: 'Maria',
  lastName: 'Rossi',
  email: 'maria@example.test',
  phone: null,
  defaultOperationalRole: 'WAITER',
  employmentStartDate: null,
  active: true,
  version: 0,
  createdAt: at(0, 8),
  updatedAt: at(0, 8),
}
const availability: Availability = {
  id: 2,
  employeeId: employee.id,
  startAt: at(0, 9),
  endAt: at(0, 17),
  notes: null,
  version: 0,
  createdAt: at(0, 8),
  updatedAt: at(0, 8),
}
const scheduled: Shift = {
  id: 3,
  employee: {
    id: employee.id,
    employeeCode: employee.employeeCode,
    firstName: employee.firstName,
    lastName: employee.lastName,
    defaultOperationalRole: employee.defaultOperationalRole,
    active: true,
  },
  operationalRole: 'HOST',
  startAt: at(0, 10),
  endAt: at(0, 16),
  durationMinutes: 360,
  status: 'SCHEDULED',
  notes: null,
  completedAt: null,
  cancelledAt: null,
  version: 1,
  createdAt: at(0, 8),
  updatedAt: at(0, 8),
}
const completed: Shift = {
  ...scheduled,
  id: 4,
  startAt: at(1, 10),
  endAt: at(1, 16),
  status: 'COMPLETED',
  completedAt: at(1, 16),
}

function renderPage() {
  render(
    <QueryClientProvider
      client={
        new QueryClient({
          defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
        })
      }
    >
      <StaffPage />
    </QueryClientProvider>,
  )
}

describe('staff page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(staffApi.listEmployees).mockResolvedValue([employee])
    vi.mocked(staffApi.listAvailability).mockResolvedValue([availability])
    vi.mocked(staffApi.listShifts).mockResolvedValue([scheduled, completed])
    vi.mocked(staffApi.saveEmployee).mockResolvedValue(employee)
    vi.mocked(staffApi.saveAvailability).mockResolvedValue(availability)
    vi.mocked(staffApi.saveShift).mockResolvedValue(scheduled)
    vi.mocked(staffApi.transitionShift).mockResolvedValue({
      ...scheduled,
      status: 'CANCELLED',
      cancelledAt: new Date().toISOString(),
      version: 2,
    })
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('loads employees and applies search and operational-role filters', async () => {
    renderPage()
    expect(await screen.findByRole('heading', { name: 'Maria Rossi' })).toBeInTheDocument()
    const user = userEvent.setup()
    await user.type(screen.getByLabelText('Search'), 'EMP001')
    await user.selectOptions(screen.getByLabelText('Role'), 'WAITER')
    await waitFor(() =>
      expect(staffApi.listEmployees).toHaveBeenCalledWith(
        expect.objectContaining({ search: 'EMP001', operationalRole: 'WAITER' }),
      ),
    )
  })

  it('creates an employee with an operational role that is visibly scheduling-only', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'Maria Rossi' })
    const user = userEvent.setup()
    await user.click(screen.getByRole('button', { name: 'Create employee' }))
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('Employee code'), 'EMP002')
    await user.type(within(dialog).getByLabelText('First name'), 'Nikos')
    await user.type(within(dialog).getByLabelText('Last name'), 'Pappas')
    await user.selectOptions(within(dialog).getByLabelText('Default role'), 'KITCHEN')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))
    await waitFor(() => expect(staffApi.saveEmployee).toHaveBeenCalled())
  })

  it('shows the future-shift conflict when employee deactivation is rejected', async () => {
    vi.mocked(staffApi.toggleEmployee).mockRejectedValue({
      isAxiosError: true,
      response: {
        status: 409,
        data: { message: 'Cancel future scheduled shifts before deactivating this employee' },
      },
    })
    renderPage()
    const user = userEvent.setup()
    const card = (await screen.findByRole('heading', { name: 'Maria Rossi' })).closest('article')

    await user.click(within(card!).getByRole('button', { name: 'Deactivate' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Cancel future scheduled shifts before deactivating this employee',
    )
  })

  it('shows date-specific availability beside existing shifts and removes it explicitly', async () => {
    vi.mocked(staffApi.removeAvailability).mockResolvedValue()
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Maria Rossi' })
    await user.click(screen.getByRole('button', { name: 'Availability' }))
    await user.selectOptions(screen.getByLabelText('Employee'), employee.id.toString())
    expect(await screen.findByRole('heading', { name: "Maria's availability" })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Existing shifts' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Remove' }))
    await waitFor(() =>
      expect(staffApi.removeAvailability).toHaveBeenCalledWith(availability, expect.anything()),
    )
    expect(window.confirm).toHaveBeenCalled()
  })

  it('renders a mobile-stackable week and keeps terminal shifts read-only', async () => {
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Maria Rossi' })
    await user.click(screen.getByRole('button', { name: 'Schedule' }))
    expect(await screen.findAllByText('Maria Rossi')).toHaveLength(2)
    const completedCard = screen.getByText('COMPLETED').closest('article')
    expect(completedCard).not.toBeNull()
    expect(within(completedCard!).queryByRole('button')).not.toBeInTheDocument()
    const scheduledCard = screen.getByText('SCHEDULED').closest('article')
    await user.click(within(scheduledCard!).getByRole('button', { name: 'Cancel' }))
    await waitFor(() =>
      expect(staffApi.transitionShift).toHaveBeenCalledWith(scheduled, 'CANCELLED'),
    )
  })

  it('keeps the shift dialog stable while a local date-time value is incomplete', async () => {
    renderPage()
    const user = userEvent.setup()
    await screen.findByRole('heading', { name: 'Maria Rossi' })
    await user.click(screen.getByRole('button', { name: 'Schedule' }))
    await user.click(screen.getByRole('button', { name: 'Create shift' }))
    const dialog = screen.getByRole('dialog', { name: 'Create shift' })

    await user.clear(within(dialog).getByLabelText('Local start'))

    expect(dialog).toBeInTheDocument()
    expect(within(dialog).getByText('Selected week availability')).toBeInTheDocument()
  })
})
