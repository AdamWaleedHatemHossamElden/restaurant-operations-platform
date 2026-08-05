import { describe, expect, it } from 'vitest'

import { localDateTimeToUtc, utcToLocalDateTimeValue } from './reservationTime'

describe('reservation time conversion', () => {
  it('converts browser-local input to the equivalent UTC instant', () => {
    const localValue = '2030-04-12T18:30'

    expect(new Date(localDateTimeToUtc(localValue)).getTime()).toBe(new Date(localValue).getTime())
  })

  it('round-trips an API UTC instant through a local datetime value', () => {
    const utcValue = '2030-04-12T15:30:00.000Z'

    expect(new Date(localDateTimeToUtc(utcToLocalDateTimeValue(utcValue))).getTime()).toBe(
      new Date(utcValue).getTime(),
    )
  })
})
