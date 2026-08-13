import type { ReportRange } from './reportTypes'

export type ReportPreset = 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'THIS_MONTH'

function startOfLocalDay(value: Date) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate())
}

function addLocalDays(value: Date, days: number) {
  return new Date(value.getFullYear(), value.getMonth(), value.getDate() + days)
}

export function presetRange(preset: ReportPreset, now = new Date()): ReportRange {
  const today = startOfLocalDay(now)
  const tomorrow = addLocalDays(today, 1)
  if (preset === 'TODAY') return { from: today.toISOString(), to: tomorrow.toISOString() }
  if (preset === 'LAST_7_DAYS')
    return { from: addLocalDays(tomorrow, -7).toISOString(), to: tomorrow.toISOString() }
  if (preset === 'LAST_30_DAYS')
    return { from: addLocalDays(tomorrow, -30).toISOString(), to: tomorrow.toISOString() }
  return {
    from: new Date(now.getFullYear(), now.getMonth(), 1).toISOString(),
    to: new Date(now.getFullYear(), now.getMonth() + 1, 1).toISOString(),
  }
}

export function localDateInput(instant: string) {
  const date = new Date(instant)
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function customRange(from: string, toExclusive: string): ReportRange | null {
  if (!from || !toExclusive) return null
  const start = new Date(`${from}T00:00:00`)
  const end = new Date(`${toExclusive}T00:00:00`)
  if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime()) || start >= end) return null
  return { from: start.toISOString(), to: end.toISOString() }
}
