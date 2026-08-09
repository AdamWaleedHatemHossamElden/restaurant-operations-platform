export function formatEur(decimal: string): string {
  const [integerPart, fractionPart = ''] = decimal.split('.')
  const grouped = BigInt(integerPart || '0').toLocaleString('en-US')
  return `€${grouped}.${fractionPart.padEnd(2, '0').slice(0, 2)}`
}
