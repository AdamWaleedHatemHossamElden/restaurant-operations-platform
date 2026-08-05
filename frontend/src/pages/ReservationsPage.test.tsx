import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { Reservation } from '../features/reservations/reservationTypes'
import {
  createReservation,
  listAvailableTables,
  listReservations,
  transitionReservation,
  updateReservation,
} from '../features/reservations/reservationsApi'
import { ReservationsPage } from './ReservationsPage'

vi.mock('../features/reservations/reservationsApi', async (importOriginal) => {
  const original = await importOriginal<typeof import('../features/reservations/reservationsApi')>()
  return {
    ...original,
    createReservation: vi.fn(),
    listAvailableTables: vi.fn(),
    listReservations: vi.fn(),
    transitionReservation: vi.fn(),
    updateReservation: vi.fn(),
  }
})

const mockedCreate = vi.mocked(createReservation)
const mockedAvailability = vi.mocked(listAvailableTables)
const mockedList = vi.mocked(listReservations)
const mockedTransition = vi.mocked(transitionReservation)
const mockedUpdate = vi.mocked(updateReservation)

const table = {
  id: 4,
  tableNumber: 'R-04',
  displayName: 'Window four',
  section: 'Main',
  capacity: 6,
}
const reservation: Reservation = {
  id: 10,
  reservationCode: 'RSV-TEST123456',
  guestName: 'Ada Guest',
  guestPhone: '+12025550123',
  guestEmail: 'ada@example.com',
  partySize: 4,
  startAt: '2030-04-12T18:00:00Z',
  endAt: '2030-04-12T19:30:00Z',
  durationMinutes: 90,
  restaurantTable: table,
  status: 'PENDING',
  notes: null,
  createdAt: '2030-04-01T10:00:00Z',
  updatedAt: '2030-04-01T10:00:00Z',
  version: 0,
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  return render(
    <QueryClientProvider client={queryClient}>
      <ReservationsPage />
    </QueryClientProvider>,
  )
}

describe('reservations page', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockedList.mockResolvedValue([reservation])
    mockedAvailability.mockResolvedValue([table])
  })

  it('renders loading, empty, error, summary, and populated states', async () => {
    mockedList.mockReturnValueOnce(new Promise(() => undefined))
    const loading = render(
      <QueryClientProvider client={new QueryClient()}>
        <ReservationsPage />
      </QueryClientProvider>,
    )
    expect(screen.getByText('Loading reservations…')).toBeInTheDocument()
    loading.unmount()

    mockedList.mockResolvedValueOnce([])
    const empty = renderPage()
    expect(
      await screen.findByRole('heading', { name: 'No reservations match these filters.' }),
    ).toBeInTheDocument()
    empty.unmount()

    mockedList.mockRejectedValueOnce(new Error('Unavailable'))
    const error = renderPage()
    expect(await screen.findByRole('alert')).toHaveTextContent('Reservations could not be loaded.')
    error.unmount()

    renderPage()
    expect(await screen.findByRole('heading', { name: 'Ada Guest' })).toBeInTheDocument()
    expect(screen.getByLabelText('Reservation summary')).toHaveTextContent('1In view')
    expect(screen.getByText('R-04')).toBeInTheDocument()
  })

  it('sends date, status, assignment, search, and sorting filters', async () => {
    renderPage()
    await screen.findByRole('heading', { name: 'Ada Guest' })
    const user = userEvent.setup()

    await user.selectOptions(screen.getByLabelText('Status'), 'CONFIRMED')
    await user.selectOptions(screen.getByLabelText('Assignment'), 'false')
    await user.type(screen.getByLabelText('Guest name'), 'Ada')
    await user.type(screen.getByLabelText('Reservation code'), 'RSV-TEST')
    await user.selectOptions(screen.getByLabelText('Sort by'), 'partySize')
    await user.selectOptions(screen.getByLabelText('Direction'), 'DESC')

    await waitFor(() =>
      expect(mockedList).toHaveBeenLastCalledWith(
        expect.objectContaining({
          status: 'CONFIRMED',
          assigned: false,
          guestName: 'Ada',
          reservationCode: 'RSV-TEST',
          sortBy: 'partySize',
          direction: 'DESC',
        }),
      ),
    )
  })

  it('validates and creates an unassigned reservation with a UTC start time', async () => {
    mockedCreate.mockResolvedValue({ ...reservation, id: 11, restaurantTable: null })
    renderPage()
    await screen.findByRole('heading', { name: 'Ada Guest' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Add reservation' }))
    const dialog = screen.getByRole('dialog')
    await user.clear(within(dialog).getByLabelText('Guest name'))
    await user.clear(within(dialog).getByLabelText('Phone'))
    await user.click(within(dialog).getByRole('button', { name: 'Create reservation' }))
    expect(await within(dialog).findByText('Guest name is required')).toBeInTheDocument()
    expect(within(dialog).getByText('Enter a valid phone number')).toBeInTheDocument()

    await user.type(within(dialog).getByLabelText('Guest name'), 'New Guest')
    await user.type(within(dialog).getByLabelText('Phone'), '+12025550124')
    await user.clear(within(dialog).getByLabelText('Local date and time'))
    await user.type(within(dialog).getByLabelText('Local date and time'), '2030-04-15T19:00')
    await user.click(within(dialog).getByRole('button', { name: 'Create reservation' }))

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith(
        expect.objectContaining({
          guestName: 'New Guest',
          restaurantTableId: null,
          startAt: new Date('2030-04-15T19:00').toISOString(),
        }),
      ),
    )
    expect(await screen.findByRole('status')).toHaveTextContent('was created successfully')
    expect(mockedList.mock.calls.length).toBeGreaterThan(1)
  })

  it('loads availability and creates an assigned reservation', async () => {
    mockedCreate.mockResolvedValue(reservation)
    renderPage()
    await screen.findByRole('heading', { name: 'Ada Guest' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Add reservation' }))
    const dialog = screen.getByRole('dialog')
    await waitFor(() => expect(mockedAvailability).toHaveBeenCalled())
    await user.type(within(dialog).getByLabelText('Guest name'), 'Assigned Guest')
    await user.type(within(dialog).getByLabelText('Phone'), '+12025550125')
    await user.selectOptions(within(dialog).getByLabelText('Table assignment'), '4')
    await user.click(within(dialog).getByRole('button', { name: 'Create reservation' }))

    await waitFor(() =>
      expect(mockedCreate).toHaveBeenCalledWith(expect.objectContaining({ restaurantTableId: 4 })),
    )
  })

  it('edits with the current version and supports reassignment', async () => {
    mockedUpdate.mockResolvedValue({ ...reservation, version: 1 })
    renderPage()
    await screen.findByRole('heading', { name: 'Ada Guest' })
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'Edit' }))
    const dialog = screen.getByRole('dialog')
    await user.clear(within(dialog).getByLabelText('Guest name'))
    await user.type(within(dialog).getByLabelText('Guest name'), 'Ada Updated')
    await user.selectOptions(within(dialog).getByLabelText('Table assignment'), '4')
    await user.click(within(dialog).getByRole('button', { name: 'Save changes' }))

    await waitFor(() =>
      expect(mockedUpdate).toHaveBeenCalledWith(
        10,
        expect.objectContaining({ guestName: 'Ada Updated', restaurantTableId: 4, version: 0 }),
      ),
    )
  })

  it('shows valid status actions and confirms terminal actions', async () => {
    mockedTransition.mockResolvedValue({ ...reservation, status: 'CANCELLED', version: 1 })
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true)
    renderPage()
    await screen.findByRole('heading', { name: 'Ada Guest' })

    expect(screen.getByRole('button', { name: 'Confirm' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Seat' })).not.toBeInTheDocument()
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }))

    expect(confirm).toHaveBeenCalled()
    await waitFor(() => expect(mockedTransition).toHaveBeenCalledWith(reservation, 'CANCELLED'))
  })

  it('shows a safe conflict message without exposing backend details', async () => {
    const config = {} as InternalAxiosRequestConfig
    const response = { status: 409, data: {}, headers: {}, config } as AxiosResponse
    mockedTransition.mockRejectedValue(
      new AxiosError('Conflict', '409', config, undefined, response),
    )
    renderPage()
    await screen.findByRole('heading', { name: 'Ada Guest' })

    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }))

    expect(await screen.findByRole('status')).toHaveTextContent('data changed')
  })
})
