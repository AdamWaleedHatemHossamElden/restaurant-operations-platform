import { describe, expect, it } from 'vitest'

import {
  addLocalDays,
  localDateTimeToUtc,
  startOfLocalWeek,
  utcToLocalDateTimeValue,
  weekRange,
} from './staffTime'

describe('staff time boundaries', () => {
  it('round-trips browser-local datetime input through UTC', () => {
    const local = '2030-06-03T09:30'
    expect(utcToLocalDateTimeValue(localDateTimeToUtc(local))).toBe(local)
  })

  it('uses local Monday boundaries and exact seven-day ranges', () => {
    const monday = startOfLocalWeek(new Date('2030-06-05T12:00:00Z'))
    expect(monday.getDay()).toBe(1)
    expect(addLocalDays(monday, 7).getTime() - monday.getTime()).toBe(7 * 24 * 60 * 60 * 1000)
    const range = weekRange(monday)
    expect(new Date(range.endAt).getTime() - new Date(range.startAt).getTime()).toBe(
      7 * 24 * 60 * 60 * 1000,
    )
  })
})
