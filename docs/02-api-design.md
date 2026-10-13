# API 接口详细设计

> 配套文档：`00-technical-solution.md` 第 4 章 ｜ 本文给出请求/响应 JSON 示例、权限与 Redis 参与标注

## 1. 通用约定

### 1.1 请求头

| 头 | 必填 | 示例 | 说明 |
| --- | --- | --- | --- |
| `Content-Type` | 是 | `application/json;charset=UTF-8` | |
| `X-User-Id` | 是 | `2` | 当前用户 ID |
| `X-Role-Code` | 否 | `BUYER` | 当前角色，缺省取默认角色 |
| `Idempotency-Key` | 否 | `9f8e...uuid` | 动作接口防重 |

### 1.2 统一响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "timestamp": 1760000000000
}
```

分页 `data` 结构：

```json
{ "records": [], "total": 128, "pageNo": 1, "pageSize": 20, "pages": 7 }
```

### 1.3 错误码

| 码 | 含义 | HTTP |
| --- | --- | --- |
| 0 | 成功 | 200 |
| 1001 | 参数校验失败 | 400 |
| 1002 | 数据不存在 | 404 |
| 2001 | 状态不允许该操作 | 400 |
| 2002 | 明细为空或金额非法 | 400 |
| 2003 | 驳回意见必填 | 400 |
| 2004 | 基础数据被引用不可删除 | 400 |
| 3001 | 未登录/会话失效 | 401 |
| 3002 | 角色无权限 | 403 |
| 3003 | 数据越权 | 403 |
| 4001 | 重复提交 | 409 |
| 4002 | 并发冲突（乐观锁） | 409 |
| 4003 | 获取锁失败 | 409 |
| 4004 | 请求过于频繁 | 429 |
| 5001 | 系统异常 | 500 |

---

## 2. 认证与会话 `/api/auth`

### 2.1 GET `/api/auth/users` 演示用户列表

权限：匿名 ｜ Redis：缓存 `po:cache:user:list` 30min

```json
// 响应 data
[ { "id": 2, "username": "buyer01", "realName": "张采购", "roles": ["BUYER"] } ]
```

### 2.2 POST `/api/auth/login` 登录（选择用户）

权限：匿名 ｜ Redis：写 `po:session:{userId}` + `po:session:{userId}:roles`，TTL 8h

```json
// 请求
{ "userId": 2 }
// 响应 data
{ "userId": 2, "username": "buyer01", "realName": "张采购",
  "roles": ["BUYER"], "currentRole": "BUYER", "loginTime": "2026-10-13 10:00:00" }
```

### 2.3 POST `/api/auth/switch-role` 切换角色

权限：登录 ｜ Redis：覆写 `po:session:{userId}.currentRole`，刷新 TTL

```json
// 请求
{ "roleCode": "PURCHASE_MANAGER" }
// 响应 data
{ "currentRole": "PURCHASE_MANAGER" }
```

校验：角色必须属于该用户，否则 3002。

### 2.4 GET `/api/auth/current` 当前上下文

权限：登录 ｜ Redis：读 `po:session:{userId}`

```json
{ "userId": 2, "username": "buyer01", "realName": "张采购",
  "currentRole": "BUYER", "roles": ["BUYER"], "todoCount": 0 }
```

### 2.5 POST `/api/auth/logout` 退出

权限：登录 ｜ Redis：删除 `po:session:{userId}*`

---

## 3. 基础数据

### 3.1 GET `/api/suppliers` 供应商列表

权限：登录 ｜ Redis：缓存 `po:cache:supplier:list` 30min

参数：`keyword`、`status`、`pageNo`、`pageSize`（`pageSize=0` 时返回全部，用于下拉）

```json
// data.records
[ { "id": 1001, "supplierCode": "SUP001", "supplierName": "晨光办公用品有限公司",
    "contactName": "周敏", "contactPhone": "13800000001", "status": 1 } ]
```

### 3.2 POST `/api/suppliers` 新增供应商

权限：ADMIN ｜ Redis：`@CacheEvict` 清 `po:cache:supplier:list`

```json
// 请求
{ "supplierCode": "SUP005", "supplierName": "宁波华远模具", "contactName": "何伟",
  "contactPhone": "13800000005", "address": "宁波市北仑区港口路 9 号", "status": 1 }
// 响应 data
{ "id": 1005 }
```

校验：`supplierCode` 必填且唯一、`supplierName` 必填 ≤128 字符。

### 3.3 PUT `/api/suppliers/{id}` 修改 ｜ DELETE `/api/suppliers/{id}` 删除

权限：ADMIN ｜ Redis：清 `po:cache:supplier:{id}` 与 list

删除校验：若存在引用该供应商的未删除订单 → 2004。

### 3.4 GET `/api/materials` 物料列表

参数：`keyword`、`category`、`pageNo`、`pageSize`（下拉时带 `ref_price`/`tax_rate`/`unit` 供明细自动带出）

### 3.5 POST/PUT/DELETE `/api/materials`（`/{id}`）

权限：ADMIN ｜ Redis：清 `po:cache:material:*`；删除前校验引用（2004）

### 3.6 GET `/api/dict/{type}` 字典

权限：登录 ｜ Redis：缓存 `po:cache:dict:{type}` 2h，启动预热

```json
// /api/dict/order_status
[ { "dictCode": "DRAFT", "dictName": "草稿", "dictValue": "#8C8C8C", "sort": 1 } ]
```

### 3.7 用户与角色（ADMIN）

| 方法 | 路径 | 说明 | Redis |
| --- | --- | --- | --- |
| GET | `/api/users` | 用户分页列表 | 清/读 `po:cache:user:list` |
| POST | `/api/users` | 新增用户 | 清 list |
| PUT | `/api/users/{id}` | 修改用户 | 清 list + `po:session:{id}` |
| DELETE | `/api/users/{id}` | 停用（逻辑删） | 清 list + 会话 |
| GET | `/api/roles` | 角色列表 | 缓存 |
| PUT | `/api/users/{id}/roles` | 分配角色 `{ "roleIds": [2,3] }` | 清 `po:session:{id}:roles` |

---

## 4. 采购订单 `/api/orders`

### 4.1 GET `/api/orders` 分页列表

权限：登录（BUYER 强制过滤 `buyer_id = 当前用户`）｜ Redis：不缓存（实时性优先）

参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `keyword` | string | 匹配订单编号/标题 |
| `status` | string | `DRAFT/APPROVING/APPROVED/REJECTED/CANCELED` |
| `supplierId` | long | 供应商 |
| `buyerId` | long | 采购员（ADMIN 可用） |
| `startDate` / `endDate` | date | 按 `order_date` 区间 |
| `pageNo` / `pageSize` | int | 默认 1 / 20 |
| `sortField` / `sortOrder` | string | 默认 `create_time desc` |

```json
// data.records
[ { "id": 1, "orderNo": "PO202610130001", "title": "2026 年 Q4 办公耗材采购",
    "supplierId": 1001, "supplierName": "晨光办公用品有限公司",
    "buyerId": 2, "buyerName": "张采购", "orderDate": "2026-10-13",
    "totalQty": 50.000, "totalAmount": 706.25, "status": "APPROVING",
    "currentNode": 1, "createTime": "2026-10-13 10:12:00" } ]
```

### 4.2 GET `/api/orders/{id}` 详情

权限：登录（BUYER 需本人）

```json
{
  "id": 1, "orderNo": "PO202610130001", "title": "...", "supplierId": 1001,
  "supplierName": "晨光办公用品有限公司", "buyerId": 2, "buyerName": "张采购",
  "currency": "CNY", "orderDate": "2026-10-13", "expectDate": "2026-10-25",
  "totalQty": 50.000, "totalAmount": 706.25, "status": "APPROVING",
  "currentNode": 1, "version": 1, "remark": "季度常规采购",
  "items": [
    { "id": 11, "lineNo": 10, "materialId": 2001, "materialName": "A4 复印纸",
      "spec": "70g/500 张/包", "unit": "个", "qty": 50.000,
      "price": 12.5000, "taxRate": 13.00, "amount": 706.25 }
  ],
  "tasks": [
    { "id": 21, "nodeSeq": 1, "nodeRole": "PURCHASE_MANAGER", "nodeName": "采购经理审批",
      "status": "PENDING", "approveTime": null, "opinion": null },
    { "id": 22, "nodeSeq": 2, "nodeRole": "DIRECTOR", "nodeName": "总监审批",
      "status": "WAITING", "approveTime": null, "opinion": null }
  ],
  "logs": [
    { "id": 31, "nodeSeq": 0, "action": "SUBMIT", "fromStatus": "DRAFT",
      "toStatus": "APPROVING", "operatorName": "张采购",
      "operateTime": "2026-10-13 10:20:00", "opinion": null }
  ],
  "actions": ["withdraw"]
}
```

> `actions` 由后端根据「当前用户角色 + 订单状态 + 当前节点」计算可操作动作，前端据此渲染按钮（前端不自行推断规则）。

### 4.3 POST `/api/orders` 新建草稿

权限：BUYER / ADMIN ｜ Redis：`INCR po:seq:order:{yyyyMMdd}` 生成单号；清 `po:report:*`

```json
// 请求
{
  "title": "2026 年 Q4 办公耗材采购",
  "supplierId": 1001,
  "currency": "CNY",
  "orderDate": "2026-10-13",
  "expectDate": "2026-10-25",
  "remark": "季度常规采购",
  "items": [
    { "lineNo": 10, "materialId": 2001, "qty": 50, "price": 12.5000, "taxRate": 13.00 },
    { "lineNo": 20, "materialId": 2002, "qty": 20, "price": 18.0000, "taxRate": 13.00 }
  ]
}
// 响应 data
{ "id": 1, "orderNo": "PO202610130001", "status": "DRAFT",
  "totalQty": 70.000, "totalAmount": 1113.00 }
```

校验：`title`、`supplierId`、`orderDate` 必填；`items` ≥1；`materialId` 存在；`qty>0`、`price>0`、`0≤taxRate≤100`；服务端重算 `amount = qty × price × (1 + taxRate/100)`（HALF_UP 保留 2 位）并汇总。

### 4.4 PUT `/api/orders/{id}` 编辑

权限：BUYER(本人) / ADMIN ｜ 状态限制：`DRAFT` 或 `REJECTED` ｜ Redis：清 `po:report:*`

请求体同 4.3（明细整体覆盖保存：以传入明细为准，缺失行逻辑删除）；需带 `version` 做乐观锁，不匹配 → 4002。

### 4.5 DELETE `/api/orders/{id}` ｜ DELETE `/api/orders/batch` `{ "ids": [1,2] }`

权限：BUYER(本人) / ADMIN ｜ 状态限制：仅 `DRAFT` ｜ 逻辑删除 ｜ Redis：清 `po:report:*`

---

## 5. 状态动作与审批

动作接口统一：`POST /api/orders/{id}/actions/{action}`，`action ∈ submit | withdraw | cancel`

通用流程：会话解析 → 角色/归属校验 → 状态机校验 → 幂等键 → 动作锁 → 业务执行（事务）→ 清缓存 → 释放锁。

### 5.1 submit 提交审批

权限：BUYER(本人) / ADMIN ｜ 源状态：`DRAFT`、`REJECTED` ｜ Redis：幂等 + `po:lock:action:{id}:submit` + 清报表/角标 + 写 `po:timeout:zset`

```json
// 请求
{ "remark": "请审批", "version": 0 }
// 响应 data
{ "id": 1, "status": "APPROVING", "currentNode": 1, "submitTime": "2026-10-13 10:20:00" }
```

副作用：生成 `node_seq=1 PENDING`、`node_seq=2 WAITING` 两条任务；写 `SUBMIT` 流水；`submit_time` 落库。

### 5.2 withdraw 撤回

权限：提交人本人 / ADMIN ｜ 源状态：`APPROVING` ｜ Redis：动作锁 + 清缓存 + 移除超时成员

```json
{ "remark": "信息填错，撤回修改", "version": 1 }
```

副作用：任务全部置 `INVALID`，订单回 `DRAFT`，`current_node=0`，写 `WITHDRAW` 流水。

### 5.3 cancel 取消

权限：BUYER(本人，`DRAFT/REJECTED`) / ADMIN（含 `APPROVED` 异常取消）｜ Redis：动作锁 + 清缓存

```json
{ "reason": "供应商停产，取消本次采购", "version": 2 }
```

`reason` 必填（2003 类校验）；写 `CANCEL` 流水。

### 5.4 POST `/api/approvals/{taskId}/approve` 审批通过

权限：当前用户角色 == 任务 `node_role`，且任务状态 `PENDING` ｜ Redis：幂等 + `po:lock:approve:{taskId}` + DB 乐观锁

```json
// 请求
{ "opinion": "价格合理，同意采购", "version": 0 }
// 响应 data
{ "taskId": 21, "taskStatus": "APPROVED", "orderStatus": "APPROVING", "currentNode": 2 }
```

推进规则：非末节点 → 下一条置 `PENDING`，`current_node+1`，订单仍 `APPROVING`；末节点 → 订单 `APPROVED`，`current_node=0`，`finish_time=now`。

### 5.5 POST `/api/approvals/{taskId}/reject` 审批驳回

权限：同 5.4 ｜ `opinion` 必填（2003）

```json
{ "opinion": "单价高于框架协议，请重新议价", "version": 0 }
// 响应 data
{ "taskId": 21, "taskStatus": "REJECTED", "orderStatus": "REJECTED", "currentNode": 0 }
```

副作用：后续 `WAITING` 任务置 `INVALID`，订单 `REJECTED`，写 `REJECT` 流水。

### 5.6 GET `/api/approvals/todo` 我的待办

权限：PURCHASE_MANAGER / DIRECTOR ｜ Redis：`po:badge:todo:{roleCode}` 计数缓存 2min

参数：`pageNo`、`pageSize`、`keyword`

```json
// data.records
[ { "taskId": 21, "orderId": 1, "orderNo": "PO202610130001", "title": "...",
    "totalAmount": 706.25, "buyerName": "张采购", "submitTime": "2026-10-13 10:20:00",
    "nodeSeq": 1, "overtime": false } ]
```

---

## 6. 报表 `/api/reports`

| 接口 | 参数 | 权限 | Redis |
| --- | --- | --- | --- |
| GET `/api/reports/summary` | `startDate`、`endDate` | 登录（BUYER 仅本人） | 缓存 5min |
| GET `/api/reports/by-status` | 同上 | 同上 | 缓存 5min |
| GET `/api/reports/by-time` | `granularity=month\|day`、`from`、`to` | 同上 | 缓存 5min |
| GET `/api/reports/orders/export` | 同列表参数 | 登录 | 限流 `po:rate:{userId}:export` |

`summary` 响应：

```json
{ "totalCount": 128, "totalAmount": 1586320.50,
  "approvingCount": 6, "approvingAmount": 84210.00,
  "approvedCount": 102, "rejectedCount": 9, "canceledCount": 5, "draftCount": 6 }
```

`by-status` 响应（饼图）：

```json
[ { "status": "DRAFT", "statusName": "草稿", "count": 6, "amount": 42100.00, "color": "#8C8C8C" } ]
```

`by-time` 响应（柱状图）：

```json
{
  "granularity": "month",
  "points": [ { "period": "2026-08", "count": 32, "amount": 402100.00 },
              { "period": "2026-09", "count": 45, "amount": 518300.00 } ]
}
```

统计口径：仅统计 `deleted=0` 的订单；BUYER 角色附加 `buyer_id` 条件；金额取 `total_amount`。

---

## 7. Redis 参与汇总

| 接口类别 | 会话校验 | 幂等 | 锁 | 限流 | 缓存 |
| --- | :-: | :-: | :-: | :-: | :-: |
| `/api/auth/login`、`switch-role` | — | — | — | — | 写会话 |
| 其他所有接口 | ✅ | — | — | — | 读会话 |
| 订单新增/编辑/删除 | ✅ | 可选 | — | — | 清 `po:report:*` |
| `actions/submit` | ✅ | ✅ | ✅ | — | 清报表 + 角标 |
| `actions/withdraw`、`cancel` | ✅ | ✅ | ✅ | — | 清缓存 |
| `approvals/approve`、`reject` | ✅ | ✅ | ✅（审批锁） | — | 清缓存 |
| `reports/*` | ✅ | — | — | 可选 | **读缓存 5min** |
| `reports/orders/export` | ✅ | ✅ | — | ✅ | — |
| `suppliers`/`materials` 写 | ✅ | — | — | — | 清 `po:cache:*` |
