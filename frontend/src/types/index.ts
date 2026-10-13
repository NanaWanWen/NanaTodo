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
