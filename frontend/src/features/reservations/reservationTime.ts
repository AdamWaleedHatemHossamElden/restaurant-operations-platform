export function localDateTimeToUtc(value: string): string {
  const instant = new Date(value)
  if (Number.isNaN(instant.getTime())) {
    throw new Error('Invalid local date and time')
  }
  return instant.toISOString()
}

export function utcToLocalDateTimeValue(value: string): string {
  const instant = new Date(value)
  const year = instant.getFullYear()
  const month = String(instant.getMonth() + 1).padStart(2, '0')
  const day = String(instant.getDate()).padStart(2, '0')
  const hour = String(instant.getHours()).padStart(2, '0')
  const minute = String(instant.getMinutes()).padStart(2, '0')
  return `${year}-${month}-${day}T${hour}:${minute}`
}

export function defaultReservationLocalTime(): string {
  const instant = new Date(Date.now() + 60 * 60 * 1000)
  instant.setMinutes(Math.ceil(instant.getMinutes() / 15) * 15, 0, 0)
  return utcToLocalDateTimeValue(instant.toISOString())
}

export function formatReservationTime(value: string): string {
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

export function localDateBoundaryToUtc(value: string, endOfDay = false): string | undefined {
  if (!value) {
    return undefined
  }
  return new Date(`${value}T${endOfDay ? '23:59:59.999' : '00:00:00'}`).toISOString()
}
