export type RoleCode = 'ADMIN' | 'BUYER' | 'PURCHASE_MANAGER' | 'DIRECTOR'

export type OrderStatus = 'DRAFT' | 'APPROVING' | 'APPROVED' | 'REJECTED' | 'CANCELED'

export interface Result<T> {
  code: number
  message: string
  data: T
  timestamp: number
}

export interface PageResult<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
  pages: number
}

export interface UserBrief {
  id: number
  username: string
  realName: string
  deptId: number
  status: number
  roles: RoleCode[]
}

export interface LoginResult {
  userId: number
  username: string
  realName: string
  roles: RoleCode[]
  currentRole: RoleCode
  loginTime: string
}

export interface RoleItem {
  id: number
  roleCode: RoleCode
  roleName: string
  description: string
  sort: number
}

export interface SupplierItem {
  id: number
  supplierCode: string
  supplierName: string
  contactName: string
  contactPhone: string
  address: string
  status: number
}

export interface MaterialItem {
  id: number
  materialCode: string
  materialName: string
  spec: string
  unit: string
  category: string
  refPrice: number
  taxRate: number
  status: number
}

export interface DictItem {
  dictCode: string
  dictName: string
  dictValue: string
  sort: number
}

export interface OrderItem {
  id?: number
  lineNo: number
  materialId: number
  materialName?: string
  spec?: string
  unit?: string
  qty: number
  price: number
  taxRate: number
  amount?: number
  remark?: string
}

export interface OrderListItem {
  id: number
  orderNo: string
  title: string
  supplierId: number
  supplierName: string
  buyerId: number
  buyerName: string
  orderDate: string
  totalQty: number
  totalAmount: number
  status: OrderStatus
  currentNode: number
  createTime: string
}

export interface ApprovalTaskItem {
  id: number
  nodeSeq: number
  nodeRole: RoleCode
  nodeName: string
  approverId?: number
  approverName?: string
  version?: number
  status: 'WAITING' | 'PENDING' | 'APPROVED' | 'REJECTED' | 'INVALID'
  approveTime?: string
  opinion?: string
}

export interface ApprovalLogItem {
  id: number
  nodeSeq?: number
  nodeRole?: RoleCode
  action: string
  actionName: string
  fromStatus?: string
  toStatus?: string
  operatorName: string
  opinion?: string
  operateTime: string
}

export interface OrderDetail {
  id: number
  orderNo: string
  title: string
  supplierId: number
  supplierName: string
  buyerId: number
  buyerName: string
  currency: string
  orderDate: string
  expectDate?: string
  totalQty: number
  totalAmount: number
  status: OrderStatus
  currentNode: number
  version: number
  remark?: string
  submitTime?: string
  finishTime?: string
  createTime: string
  items: OrderItem[]
  tasks: ApprovalTaskItem[]
  logs: ApprovalLogItem[]
  actions: string[]
}

export interface OrderSavePayload {
  title: string
  supplierId: number
  currency?: string
  orderDate: string
  expectDate?: string
  remark?: string
  version?: number
  items: OrderItem[]
}

export interface ApprovalTodoItem {
  taskId: number
  orderId: number
  orderNo: string
  title: string
  totalAmount: number
  buyerName: string
  submitTime: string
  nodeSeq: number
  nodeName: string
  overtime: boolean
}

export interface ReportSummary {
  totalCount: number
  totalAmount: number
  draftCount: number
  approvingCount: number
  approvingAmount: number
  approvedCount: number
  rejectedCount: number
  canceledCount: number
}

export interface StatusStat {
  status: string
  statusName: string
  count: number
  amount: number
  color: string
}

export interface TimeStat {
  granularity: string
  dataTime: string
  points: { period: string; count: number; amount: number }[]
}

/** 统计维度：供应商 / 采购员 / 物料 / 部门 */
export type RankGroupBy = 'supplier' | 'buyer' | 'material' | 'dept'

export interface RankStat {
  /** 维度编码 */
  code: string
  /** 维度名称 */
  name: string
  /** 订单数 */
  orderCount: number
  /** 数量合计 */
  qty: number
  /** 含税金额合计 */
  amount: number
  /** 金额占比（百分比） */
  percent: number
  /** 维度主键，用于穿透联查 */
  linkValue: string
}

export interface RankResult {
  groupBy: RankGroupBy
  topN: number
  rows: RankStat[]
  total: {
    orderCount: number
    qty: number
    amount: number
  }
  dataTime: string
}

// ===== P2P 全流程类型 =====

/** 收货单列表项 */
export interface ReceiptListItem {
  id: number
  receiptNo: string
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  receiptDate: string
  totalQty: number
  totalAmount: number
  status: 'DRAFT' | 'CONFIRMED' | 'CANCELED'
  createTime: string
}

/** 收货单详情 */
export interface ReceiptDetail {
  id: number
  receiptNo: string
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  receiptDate: string
  totalQty: number
  totalAmount: number
  status: string
  remark?: string
  createTime: string
  items: ReceiptDetailItem[]
}

export interface ReceiptDetailItem {
  id: number
  orderItemId: number
  materialId: number
  materialName: string
  spec?: string
  unit: string
  orderedQty: number
  receivedQty: number
  unitPrice: number
  taxRate: number
  taxAmount: number
  amount: number
  remark?: string
}

/** 收货单创建 */
export interface ReceiptCreatePayload {
  orderId: number
  receiptDate?: string
  remark?: string
  items: { orderItemId: number; receivedQty: number; remark?: string }[]
}

/** 发票列表项 */
export interface InvoiceListItem {
  id: number
  invoiceNo: string
  receiptId: number
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  invoiceDate: string
  invoiceAmount: number
  taxAmount: number
  totalAmount: number
  invoiceStatus: 'UNCHECKED' | 'CHECKED' | 'PAID' | 'RED_FLUSHED'
  invoiceType?: 'BLUE' | 'RED'
  relatedInvoiceNo?: string
  createTime: string
}

/** 发票创建 */
export interface InvoiceCreatePayload {
  receiptId: number
  invoiceDate?: string
  remark?: string
  invoiceAmount?: number
  taxAmount?: number
}

/** 发票修改（仅未核对） */
export interface InvoiceUpdatePayload {
  invoiceAmount: number
  taxAmount: number
  invoiceDate?: string
  remark?: string
}

/** 付款单列表项 */
export interface PaymentListItem {
  id: number
  paymentNo: string
  invoiceId: number
  invoiceNo: string
  orderId: number
  orderNo: string
  supplierId: number
  supplierName: string
  paymentDate: string
  paymentAmount: number
  paymentMethod: string
  status: 'PENDING' | 'PAID' | 'REJECTED'
  createTime: string
}

/** 付款单创建 */
export interface PaymentCreatePayload {
  invoiceId: number
  paymentAmount?: number
  paymentDate?: string
  paymentMethod?: string
  remark?: string
}

/** 库存项 */
export interface InventoryItem {
  id: number
  materialId: number
  materialCode: string
  materialName: string
  warehouse: string
  quantity: number
  safetyStock: number
  lastUpdateTime: string
  version: number
}

/** 应付对账列表项 */
export interface ApSettlementListItem {
  id: number
  matchNo: string
  orderId: number
  orderNo: string
  receiptId?: number
  receiptNo?: string
  invoiceId?: number
  invoiceNo?: string
  supplierId: number
  supplierName: string
  orderAmount: number
  receiptAmount: number
  invoiceAmount: number
  diffAmount: number
  qtyDiff: number
  priceDiff: number
  taxDiff: number
  diffType?: 'QTY' | 'PRICE' | 'TAX' | 'EXCHANGE' | 'AMOUNT'
  matchStatus: 'MATCHED' | 'PARTIAL_QTY' | 'PARTIAL_AMOUNT' | 'HOLD' | 'UNMATCHED' | 'CLOSED' | 'REVERSED'
  matchType: 'AUTO' | 'MANUAL' | 'FORCE'
  matchRuleCode?: string
  currency: string
  tolerance: number
  matchedBy?: number
  matchedByName?: string
  matchedTime?: string
  holdReason?: string
  resolution?: string
  remark?: string
  updateTime: string
}

/** 暂估应付列表项 */
export interface AccrualItem {
  id: number
  accrualNo: string
  receiptId: number
  receiptNo?: string
  orderId: number
  orderNo?: string
  supplierId: number
  supplierName?: string
  materialId: number
  materialCode?: string
  materialName?: string
  accrualQty: number
  accrualPrice: number
  accrualAmount: number
  accrualDate: string
  accrualPeriod: string
  status: 'ACCRUED' | 'REVERSED'
  reverseInvoiceId?: number
  reverseInvoiceNo?: string
  reverseDate?: string
  reverseAmount?: number
  diffAmount?: number
  accrualMode: 'SINGLE' | 'MONTHLY'
  remark?: string
  createTime: string
}

/** 供应商应付余额 */
export interface SupplierBalanceVO {
  supplierId: number
  supplierName: string
  unpaidAmount: number
  unpaidInvoiceCount: number
  paidAmount: number
  balance: number
}

// ---------------- AI Agent ----------------

/** Agent 预警 */
export interface AgentAlertVO {
  alertId: string
  alertType: string
  /** 业务域 PURCHASE/INVENTORY/FINANCE */
  bizDomain?: string
  /** 命中规则编码 */
  ruleCode?: string
  ruleId?: number
  level: 'INFO' | 'WARNING' | 'CRITICAL'
  title: string
  content: string
  refId: number
  refNo: string
  /** 引用单据类型 ORDER/RECEIPT/INVOICE/PAYMENT/MATERIAL */
  refType?: string
  createTime: string
  handled: boolean
  ignored?: boolean
  readFlag?: boolean
  assigneeId?: number
  assignee?: string
  handleBy?: string
  handleTime?: string
  handleNote?: string
  actionPath?: string
  actions?: AlertActionVO[]
}

/** 预警处理流水（审计时间线） */
export interface AlertActionVO {
  id: number
  alertId: string
  action: 'CREATE' | 'READ' | 'ASSIGN' | 'TRANSFER' | 'HANDLE' | 'IGNORE' | 'RESTORE' | 'ESCALATE'
  fromUser?: string
  toUser?: string
  operator?: string
  operatorId?: number
  note?: string
  createTime: string
}

/** 预警规则配置 */
export interface AgentAlertRule {
  id?: number
  ruleCode: string
  ruleName: string
  bizDomain: 'PURCHASE' | 'INVENTORY' | 'FINANCE'
  alertType: string
  level: 'INFO' | 'WARNING' | 'CRITICAL'
  thresholdUnit?: string
  threshold?: number
  extraConfig?: string
  enabled: number
  scanCron?: string
  notifyChannel?: string
  notifyRoles?: string
  ownerRole?: string
  remark?: string
  createBy?: string
  createTime?: string
  updateBy?: string
  updateTime?: string
}

/** AI 建议（辅助审批/发票校验/付款） */
export interface SuggestionVO {
  type: 'APPROVAL' | 'INVOICE_CHECK' | 'PAYMENT'
  refId: number
  refNo?: string
  suggestion: string
  risk: 'LOW' | 'MEDIUM' | 'HIGH'
  aiGenerated: boolean
}
