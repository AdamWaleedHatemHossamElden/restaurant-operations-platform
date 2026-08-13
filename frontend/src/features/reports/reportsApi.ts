import { apiClient } from '../../lib/apiClient'
import {
  inventoryReportSchema,
  kitchenReportSchema,
  menuPerformanceReportSchema,
  overviewReportSchema,
  paymentsReportSchema,
  reservationsReportSchema,
  salesReportSchema,
  staffReportSchema,
  type ExportableReport,
  type ReportGroupBy,
  type ReportRange,
} from './reportTypes'

export const reportKeys = {
  all: ['reports'] as const,
  section: (section: string, range: ReportRange, groupBy?: ReportGroupBy) =>
    ['reports', section, range.from, range.to, groupBy] as const,
}

const rangeParams = (range: ReportRange) => ({ from: range.from, to: range.to })

export async function getOverviewReport(range: ReportRange) {
  return overviewReportSchema.parse(
    (await apiClient.get('/reports/overview', { params: rangeParams(range) })).data,
  )
}

export async function getSalesReport(range: ReportRange, groupBy: ReportGroupBy) {
  return salesReportSchema.parse(
    (await apiClient.get('/reports/sales', { params: { ...rangeParams(range), groupBy } })).data,
  )
}

export async function getMenuPerformanceReport(range: ReportRange) {
  return menuPerformanceReportSchema.parse(
    (await apiClient.get('/reports/menu-performance', { params: rangeParams(range) })).data,
  )
}

export async function getPaymentsReport(range: ReportRange, groupBy: ReportGroupBy) {
  return paymentsReportSchema.parse(
    (await apiClient.get('/reports/payments', { params: { ...rangeParams(range), groupBy } })).data,
  )
}

export async function getReservationsReport(range: ReportRange) {
  return reservationsReportSchema.parse(
    (await apiClient.get('/reports/reservations', { params: rangeParams(range) })).data,
  )
}

export async function getKitchenReport(range: ReportRange) {
  return kitchenReportSchema.parse(
    (await apiClient.get('/reports/kitchen', { params: rangeParams(range) })).data,
  )
}

export async function getInventoryReport(range: ReportRange) {
  return inventoryReportSchema.parse(
    (await apiClient.get('/reports/inventory', { params: rangeParams(range) })).data,
  )
}

export async function getStaffReport(range: ReportRange) {
  return staffReportSchema.parse(
    (await apiClient.get('/reports/staff', { params: rangeParams(range) })).data,
  )
}

export async function downloadReport(
  report: ExportableReport,
  range: ReportRange,
  groupBy: ReportGroupBy,
) {
  const response = await apiClient.get(`/reports/exports/${report}.csv`, {
    params: { ...rangeParams(range), groupBy },
    responseType: 'blob',
  })
  const disposition = String(response.headers['content-disposition'] ?? '')
  const filename =
    disposition.match(/filename="?([^";]+)"?/i)?.[1] ?? `restaurant-${report}-report.csv`
  const url = URL.createObjectURL(response.data)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = filename
  anchor.click()
  URL.revokeObjectURL(url)
}
