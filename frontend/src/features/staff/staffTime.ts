import { localDateTimeToUtc, utcToLocalDateTimeValue } from '../reservations/reservationTime'

export { localDateTimeToUtc, utcToLocalDateTimeValue }

export function startOfLocalWeek(value = new Date()): Date {
  const result = new Date(value)
  const day = result.getDay()
  const mondayOffset = day === 0 ? -6 : 1 - day
  result.setDate(result.getDate() + mondayOffset)
  result.setHours(0, 0, 0, 0)
  return result
}

export function addLocalDays(value: Date, days: number): Date {
  const result = new Date(value)
  result.setDate(result.getDate() + days)
  return result
}

export function weekRange(value: Date): { startAt: string; endAt: string } {
  const start = startOfLocalWeek(value)
  return { startAt: start.toISOString(), endAt: addLocalDays(start, 7).toISOString() }
}

export function defaultShiftTimes(): { startLocal: string; endLocal: string } {
  const start = new Date()
  start.setDate(start.getDate() + 1)
  start.setHours(9, 0, 0, 0)
  const end = new Date(start)
  end.setHours(17)
  return {
    startLocal: utcToLocalDateTimeValue(start.toISOString()),
    endLocal: utcToLocalDateTimeValue(end.toISOString()),
  }
}

export function formatLocalDate(value: Date | string): string {
  return new Intl.DateTimeFormat(undefined, {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  }).format(new Date(value))
}

export function formatLocalTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, { hour: 'numeric', minute: '2-digit' }).format(
    new Date(value),
  )
}
