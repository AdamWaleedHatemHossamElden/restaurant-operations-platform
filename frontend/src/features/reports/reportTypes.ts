import { z } from 'zod'

export const reportGroupBySchema = z.enum(['DAY', 'WEEK', 'MONTH'])
export type ReportGroupBy = z.infer<typeof reportGroupBySchema>

const periodSchema = z.object({ from: z.string(), to: z.string() })
const timePointSchema = z.object({ bucket: z.string(), count: z.number(), amount: z.string() })
const countMetricSchema = z.object({ key: z.string(), count: z.number() })

export const overviewReportSchema = z.object({
  period: periodSchema,
  completedOrders: z.number(),
  completedOrderValue: z.string(),
  averageCompletedOrderValue: z.string(),
  paymentsReceived: z.string(),
  paymentCount: z.number(),
  reconciledPaymentCount: z.number(),
  invoiceCount: z.number(),
  reservations: z.number(),
  readyKitchenTickets: z.number(),
  scheduledStaffHours: z.string(),
})

export const salesReportSchema = z.object({
  period: periodSchema,
  groupBy: reportGroupBySchema,
  completedOrders: z.number(),
  completedOrderValue: z.string(),
  averageCompletedOrderValue: z.string(),
  series: z.array(timePointSchema),
  byTable: z.array(
    z.object({
      tableId: z.number(),
      tableNumber: z.string(),
      displayName: z.string(),
      completedOrders: z.number(),
      completedOrderValue: z.string(),
    }),
  ),
  topOrders: z.array(
    z.object({
      orderId: z.number(),
      orderNumber: z.string(),
      completedAt: z.string(),
      total: z.string(),
    }),
  ),
})

export const menuPerformanceReportSchema = z.object({
  period: periodSchema,
  top: z.number(),
  items: z.array(
    z.object({
      menuItemId: z.number(),
      itemCode: z.string(),
      itemName: z.string(),
      quantitySold: z.number(),
      completedOrders: z.number(),
      completedOrderLineValue: z.string(),
      averageQuantityPerOrder: z.string(),
    }),
  ),
})

export const paymentsReportSchema = z.object({
  period: periodSchema,
  groupBy: reportGroupBySchema,
  paymentsReceived: z.string(),
  paymentCount: z.number(),
  averagePaymentAmount: z.string(),
  reconciledCount: z.number(),
  unreconciledCount: z.number(),
  reconciledAmount: z.string(),
  unreconciledAmount: z.string(),
  byMethod: z.array(z.object({ method: z.string(), count: z.number(), amount: z.string() })),
  series: z.array(timePointSchema),
})

export const reservationsReportSchema = z.object({
  period: periodSchema,
  reservations: z.number(),
  plannedGuests: z.number(),
  averagePartySize: z.string(),
  byStatus: z.array(countMetricSchema),
  byTable: z.array(
    z.object({
      tableId: z.number().nullable(),
      tableNumber: z.string().nullable(),
      displayName: z.string().nullable(),
      reservations: z.number(),
      plannedGuests: z.number(),
    }),
  ),
})

export const kitchenReportSchema = z.object({
  period: periodSchema,
  ticketsCreated: z.number(),
  readyTickets: z.number(),
  cancelledTickets: z.number(),
  averagePreparationMinutes: z.string(),
  byStatus: z.array(countMetricSchema),
  itemPreparation: z.array(
    z.object({ itemCode: z.string(), itemName: z.string(), quantity: z.number() }),
  ),
})

export const inventoryReportSchema = z.object({
  period: periodSchema,
  movementCount: z.number(),
  currentLowStockItems: z.number(),
  movementCounts: z.array(countMetricSchema),
  items: z.array(
    z.object({
      inventoryItemId: z.number(),
      code: z.string(),
      name: z.string(),
      unit: z.string(),
      receipt: z.string(),
      usage: z.string(),
      waste: z.string(),
      adjustmentIn: z.string(),
      adjustmentOut: z.string(),
      netMovement: z.string(),
      currentOnHand: z.string(),
      currentlyLowStock: z.boolean(),
    }),
  ),
})

const hoursMetricSchema = z.object({ key: z.string(), shifts: z.number(), hours: z.string() })
export const staffReportSchema = z.object({
  period: periodSchema,
  shiftCount: z.number(),
  scheduledCount: z.number(),
  completedCount: z.number(),
  cancelledCount: z.number(),
  scheduledHours: z.string(),
  completedShiftHours: z.string(),
  hoursByRole: z.array(hoursMetricSchema),
  hoursByEmployee: z.array(
    z.object({
      employeeId: z.number(),
      employeeCode: z.string(),
      employeeName: z.string(),
      shifts: z.number(),
      scheduledHours: z.string(),
      completedShiftHours: z.string(),
    }),
  ),
})

export type OverviewReport = z.infer<typeof overviewReportSchema>
export type SalesReport = z.infer<typeof salesReportSchema>
export type MenuPerformanceReport = z.infer<typeof menuPerformanceReportSchema>
export type PaymentsReport = z.infer<typeof paymentsReportSchema>
export type ReservationsReport = z.infer<typeof reservationsReportSchema>
export type KitchenReport = z.infer<typeof kitchenReportSchema>
export type InventoryReport = z.infer<typeof inventoryReportSchema>
export type StaffReport = z.infer<typeof staffReportSchema>

export type ReportRange = { from: string; to: string }
export type ExportableReport =
  'sales' | 'menu' | 'payments' | 'reservations' | 'inventory' | 'staff'
