# YonErp 采购结算模块用友式改造（plan4）

## 一、目标

将简化的「整单金额比对 + 写死 1% 容差 + 手工按钮触发」三单匹配，升级为用友 U8/U9 式企业级采购结算体系：

1. **行级数量池多对多勾对**：支持一单多收、一票多单、分批收货、合单开票，按「供应商 + 物料」建池，先进先出分配。
2. **多维可配容差**：组织/供应商/物料分类/币种四个维度，比例容差 + 绝对额容差取大，可配置自动结算开关。
3. **暂估与回冲**：货到票未到月末暂估，票到单到回冲或月初统一回冲，差额进成本差异。
4. **差异处理闭环**：超容差挂起（HOLD）+ 预警 + 补收货/退票重开/调价/损耗放行/强制匹配（DIRECTOR 审批留痕）。
5. **事件驱动自动结算**：收货确认、发票校验后异步触发，取消「人肉点按钮」。
6. **付款联动**：付款申请校验结算状态，非 MATCHED 拦截。
7. **预警口径修正**：只报真差异，消除 91 条噪音告警。
8. **产出**：`docs/plan4.md`、`docs/main.txt`（追加项目经验）。

## 二、现状诊断（改造前，已验证）

| 问题 | 证据 |
| --- | --- |
| 整单金额比对 | `ApSettlementServiceImpl.match()` 直接比较 `order.getTotalAmount()` 与发票总额，无行级概念，一单多收/合单开票场景必现假差异 |
| 容差写死 | `if (diff.compareTo(tolerance) > 0)` 中 `tolerance` 为固定 `order.getTotalAmount().multiply(0.01)`，不同供应商/物料/币种无法差异化 |
| 手工触发 | 仅前端「触发匹配」按钮调 `POST /ap/settlements/{orderId}/match`，收货确认/发票校验后系统不会自动结算 |
| 状态机残缺 | `match_status` 只有 `UNMATCHED/PARTIAL/MATCHED` 三态，无挂起、无处理方式、无匹配人/时间/批次，差异无法追溯 |
| 无暂估能力 | 货到票未到时应付账龄失真，月末结账无暂估凭证来源 |
| 预警口径错 | `InvoiceMismatchEvaluator` 对 `UNMATCHED`（票未到）也告警，与「真差异」混在一起，演示数据下 91 条噪音 |
| 付款无联动 | `PaymentServiceImpl.create` 只校验发票 `CHECKED`，不校验三单匹配结果，差异未解决即可付款 |

## 三、改造内容与实现

### 任务 1：数据模型重构（schema.sql）

**扩展 `po_ap_settlement`**：新增 `match_no`（唯一）、`match_batch`、`match_type`（AUTO/MANUAL/FORCE）、`match_rule_code`、`currency`、`diff_type`（QTY/PRICE/TAX/EXCHANGE）、`qty_diff`、`price_diff`、`tax_diff`、`status`（MATCHED/PARTIAL_QTY/PARTIAL_AMOUNT/HOLD/UNMATCHED/CLOSED/REVERSED）、`matched_by`、`matched_time`、`hold_reason`、`resolution`、`version`，并加 `uk_match_no`、`idx_status`、`idx_matched_time`。

**新增三张表**：

- `po_ap_settlement_item`：行级匹配明细，订单/收货/发票三侧的数量、单价、金额与差异并列存储，`diff_type` + `match_status` 行级状态。
- `po_match_rule`：匹配规则配置，`scope_type`（GLOBAL/SUPPLIER/MATERIAL_CATEGORY/CURRENCY）+ `scope_value`，数量/金额容差分别支持 `PERCENT`/`ABSOLUTE`，`auto_settle`、`accrual_mode`（SINGLE/MONTHLY）、`allow_force_match`、`approval_role`。
- `po_accrual`：暂估应付，`accrual_period`（yyyy-MM）分组，`status`（ACCRUED/REVERSED）+ `reverse_invoice_id`/`reverse_date`/`diff_amount` 支撑单到回冲与月初回冲差额追溯。

`data.sql` 同步刷新：规则种子（全局默认 + 进口物料供应商专项 + 外币币种专项）、演示结算单与行级明细、暂估单，避免「看起来全是差异」的假象。

### 任务 2：实体与 Mapper

- `PoApSettlement` 扩展全部新字段；新增 `PoApSettlementItem`、`PoMatchRule`、`PoAccrual` 实体。
- 对应 4 个 Mapper（`ApSettlementMapper`、`ApSettlementItemMapper`、`MatchRuleMapper`、`AccrualMapper`）继承 BaseMapper，LambdaQuery 查询。

### 任务 3：MatchEngine 匹配引擎（数量池多对多勾对）

`matchByPool(supplierId, materialId)` 核心算法：

1. **建池**：查该供应商+物料下所有已确认收货明细（未核销数量 > 0）与已校验发票明细（未核销数量 > 0）。
2. **多对多勾对**：两侧均按「收货/开票时间升序」排序（先进先出），双指针顺序核销，一条收货可被多张发票勾走，一张发票也可勾多条收货；天然覆盖一单多收、一票多单、分批收货、合单开票。
3. **差异判定**（行级，按序判定）：数量差 → 价格差 → 税额差 → 汇差，写入 `po_ap_settlement_item.diff_type` 与对应差异列。
4. **容差判定**：`resolveRule(supplierId, materialId, currency)` 解析生效规则（供应商 > 物料分类 > 币种 > 全局），数量与金额分别取「比例容差 vs 绝对额容差」较大值。
5. **结果落库**：写 `po_ap_settlement`（头）+ `po_ap_settlement_item`（行）；行级全过 → MATCHED；行级有差但 ≤ 容差 → PARTIAL_*；超容差或规则要求人工 → HOLD（写 `hold_reason`）。
6. **兼容入口**：`matchByOrder(orderId)` 内部按订单取「供应商 + 物料集合」逐池匹配后汇总；`matchBatch(orderIds)` 分页（100 单/批）批量执行，供定时任务兜底。
7. **幂等**：`match_no` 唯一索引 + 先查后写，重复触发不会重复生成结算单。

### 任务 4：AccrualService 暂估服务

- `accrueByReceipt(receiptId)`：收货确认且无对应校验发票时，按订单价（无订单价则计划价）生成暂估，`accrual_mode` 取规则配置。
- `reverseByInvoice(invoiceId)`：发票校验时按 `SINGLE` 模式单到回冲，红冲暂估并计算 `diff_amount`（暂估 − 实际），差额转材料成本差异。
- `monthlyReverse(period)`：`MONTHLY` 模式月初统一回冲整个期间。
- `monthlyAccrue(period)`：月末批量兜底，扫当期全部货到票未到收货单。

### 任务 5：ApSettlementService 重构

| 方法 | 语义 |
| --- | --- |
| `match(orderId)` | 保留原接口，内部委托 `MatchEngine.matchByOrder` |
| `autoSettle(orderIds)` | 批量自动结算（事件监听 / 定时任务调用） |
| `manualSettle(cmd)` | 手工结算，`match_type=MANUAL`，前端勾选勾对结果提交 |
| `forceSettle(id, cmd)` | 强制匹配，`match_type=FORCE`，要求 `reason` + `resolution`，仅规则 `allow_force_match=1` 时可用 |
| `resolveDiff(id, cmd)` | 差异处理，写 `resolution`（补收/退票/调价/损耗放行）与备注，转 MATCHED 或继续 HOLD |
| `page(query)` | 多维筛选（状态/类型/差异类型/供应商/关键字）+ 分页 |

### 任务 6：事件驱动自动结算

- `ReceiptServiceImpl.confirm`：事务提交后 `eventPublisher.publishEvent(new ReceiptConfirmedEvent(...))`。
- `InvoiceServiceImpl.check`：事务提交后发布 `InvoiceCheckedEvent`。
- `SettlementEventListener`：`@TransactionalEventListener(AFTER_COMMIT)` + `@Async`（`taskExecutor`，新增 `AsyncConfig`，CallerRunsPolicy 兜底）+ `REQUIRES_NEW` 独立事务，失败仅记日志不反向影响业务；发票事件额外触发暂估回冲。

### 任务 7：预警口径修正

- `InvoiceMismatchEvaluator`：只查 `status IN ('HOLD','PARTIAL_QTY','PARTIAL_AMOUNT')` 且差异金额 > 阈值（AMOUNT 口径，缺省 0）的记录；差异超容差 2 倍升级 CRITICAL；UNMATCHED（票未到）不再告警。
- 新增 `AccrualPendingEvaluator`：暂估超过 N 天（默认 30，单位 DAY）未回冲告警，超 2 倍升级 CRITICAL。
- `AlertRuleInitializer` 增加 `ACCRUAL_PENDING` 默认配置；`AlertRuleService` 建议文案补充两条。

### 任务 8：付款联动

`PaymentServiceImpl.create` 在发票 CHECKED 校验之后增加结算状态校验：查该发票对应结算单，不存在则拦截「尚未完成三单匹配」，非 `MATCHED` 则拦截「请先处理差异后再付款」，实现「差异不解决、付款走不通」。

### 任务 9：后端 API

- `ApSettlementController`：列表多维筛选、`POST /{orderId}/match`、`POST /auto-settle`（批量）、`POST /manual-settle`、`POST /{id}/force-settle`（DIRECTOR）、`POST /{id}/resolve-diff`、`GET /balances`。
- `AccrualController`：`/by-receipt/{id}`、`/reverse-by-invoice/{id}`、`/monthly-accrue`、`/monthly-reverse`。
- `MatchRuleController`：CRUD + `/toggle`。
- 全部写操作走 `@RequireRole` 权限注解 + 幂等键（`Idempotency-Key`）。

### 任务 10：前端改造

| 页面 | 内容 |
| --- | --- |
| `ApSettlementList` | 重构：筛选区（订单/状态/匹配类型/差异类型/关键字）、统计卡片（总数/已匹配/挂起/供应商数）、表格（结算单号、匹配类型、状态、差异类型、差异金额红色高亮、匹配人/时间）、批量自动结算、行级明细抽屉（头信息 + 金额对比 + 差异明细） |
| `ManualSettleModal` | 手工结算弹窗：三单金额对比卡片、差异红色警示区、备注填写 |
| `Accruals` | 暂估/回冲页：期间筛选、暂估列表（数量/单价/金额/状态/回冲日期/差额）、月末批量暂估、月初批量回冲（二次确认） |
| `MatchRules` | 匹配规则配置页：表格（容差/自动结算/暂估方式/强制匹配/审批角色/启停开关）+ 新增编辑弹窗，交互范式复用预警规则配置 |
| `DiffWorkbench` | 差异处理工作台：复用预警中心「指派/转办/处理/忽略 + 审计时间线」交互，处理动作五选（补收货/退票重开/调价/损耗放行/强制匹配），强制匹配表单含原因说明与原值→新值留痕 |
| 路由/菜单 | `App.tsx` 注册 3 条新路由（`ap-accruals`/`ap-match-rules`/`ap-diff-workbench`），`MainLayout` 增加「暂估与回冲」「差异处理工作台」「匹配规则配置」入口 |

## 四、目录结构（本次新增/改动）

```
backend/src/main/java/com/yonerp/po/ap/
├── entity/  PoApSettlement（扩展）、PoApSettlementItem、PoMatchRule、PoAccrual
├── mapper/  ApSettlementMapper、ApSettlementItemMapper、MatchRuleMapper、AccrualMapper
├── service/ MatchEngine、AccrualService、MatchRuleService、ApSettlementService（重构）
├── controller/ ApSettlementController（扩展）、AccrualController、MatchRuleController
├── event/   ReceiptConfirmedEvent、InvoiceCheckedEvent、SettlementEventListener
└── dto/     ManualSettleCmd、ForceSettleCmd、ResolveCmd

backend/src/main/resources/schema.sql / data.sql（四张表 + 规则/演示数据）
backend/src/main/java/com/yonerp/po/config/AsyncConfig.java（@EnableAsync + taskExecutor）
backend/src/main/java/com/yonerp/po/agent/rule/InvoiceMismatchEvaluator（口径修正）
backend/src/main/java/com/yonerp/po/agent/rule/AccrualPendingEvaluator（新增）

frontend/src/
├── pages/ApSettlementList/index.tsx（重构）
├── pages/Accruals/index.tsx（新增）
├── pages/MatchRules/index.tsx（新增）
├── pages/DiffWorkbench/index.tsx（新增）
├── components/ManualSettleModal.tsx（新增）
├── api/ap.ts（新增 autoSettle/manualSettle/forceSettle/resolveDiff）
├── types/index.ts（ApSettlementListItem 扩展 20 字段）
├── App.tsx（3 条新路由）
└── layouts/MainLayout.tsx（3 个菜单入口）
```

## 五、性能与可靠性

- **批量结算分页**：`matchBatch` 每批 100 单，避免长事务。
- **异步不阻塞**：事件监听 `@Async` + `REQUIRES_NEW`，收货/发票主流程不受结算耗时影响；线程池拒绝策略 CallerRunsPolicy 防任务丢失。
- **幂等**：`match_no` 唯一索引 + 先查后写；写接口 `Idempotency-Key` 防重复提交。
- **审计留痕**：强制匹配/差异处理记录 `matched_by`、`matched_time`、`match_rule_code`、原值→新值、原因，前端审计时间线展示。
- **权限**：强制匹配 `@RequireRole(DIRECTOR)`，规则配置 ADMIN/PURCHASE_MANAGER，批量结算 PURCHASE_MANAGER。

## 六、注意事项

- **数据迁移**：存量 `po_ap_settlement` 的 `match_status` 映射到新 `status`，新字段填默认值（`match_type=MANUAL`、`currency=CNY`）。
- **预警自愈**：口径修正后原 91 条 UNMATCHED 噪音不再生成，无需手工清理。
- **暂估期间**：月末暂估按 `accrual_period` 分组，月初回冲按期间批量，两种模式由规则 `accrual_mode` 决定。
- **行级明细回填**：历史数据无 `po_ap_settlement_item` 行记录，差异明细仅对改造后新生成的结算单可见。

## 七、联调修复轮（白屏 + 数据补全，2026-10-18）

### 7.1 应付对账页白屏根因与修复

**根因**：`ApSettlementList` 对状态枚举映射表直接取属性（`MATCH_META[status].color`），存量库中映射表之外的值（旧枚举 `PARTIAL`、`match_type` 为 NULL）导致渲染期 `TypeError`；应用无 ErrorBoundary，React 18 直接卸载整棵树 → 整页变白。

**修复**：
- 新增 `frontend/src/utils/matchMeta.ts`：`matchStatusMeta()` / `matchTypeMeta()` 统一取标签与颜色，未知枚举原样展示并回落 `default` 色，不再抛异常；`ApSettlementList`、`DiffWorkbench` 两页共用。
- 新增 `frontend/src/components/ErrorBoundary.tsx`（`componentDidCatch` + `console.error` + 错误卡片 + 刷新/返回工作台），`main.tsx` 根级包裹——今后任何页面崩溃只降级该页，不再白屏。
- `data.sql` 清理段自动删除旧枚举/缺 `match_no`/缺 `match_type` 的脏结算行，从数据侧消除触发源。

### 7.2 后端缺口补齐

| 缺口 | 修复 |
| --- | --- |
| `GET /api/ap/accruals` 不存在，暂估页 404 空表 | `AccrualService.page(AccrualQuery)` + `AccrualVO`（回填供应商/订单/物料/发票名称），`AccrualController` 加分页筛选接口 |
| `matchStatus` 用 `eq` 匹配，工作台传逗号分隔多状态查不到数据 | `page()` 支持逗号分隔 `in` 查询 |
| `resolveDiff` 只认中文动作码，前端发英文码必报错 | 统一为 `ADD_RECEIPT/REDO_INVOICE/ADJUST_PRICE/ALLOW_LOSS/FORCE_MATCH` |
| `matchByOrder` 每次 INSERT，点「重新匹配」产生重复结算单 | 改为幂等：同订单已存在结算单则原地更新并重建行级明细 |
| 状态判定把数量差异吞进金额容差（9006 会被判成 MATCHED） | 数量差异优先判定；金额差异超容差 2 倍自动 HOLD 并写 `hold_reason` |
| 行级明细无发票侧数据，抽屉三单对照缺一列 | `createSettlementItems` 按「发票总额/收货总额」系数分摊发票侧单价与金额，差异类型细化到 PRICE |
| `matchByPool`/`monthlyAccrue`/按物料分行暂估均为 TODO | 数量池按供应商+物料聚合已确认收货单逐单勾对；月末批量暂估扫货到票未到；暂估按物料分行，回冲差额按行占比分摊 |
| 匹配规则只解析供应商/全局两级，物料分类与币种规则是死配置 | 四级回退：供应商 → 物料分类 → 币种 → 全局（引擎与 `MatchRuleServiceImpl` 一致） |
| 差异工作台「指派/转办」为假成功提示 | 接入真实预警 API（按 `refId` 关联 INVOICE_MISMATCH 预警），审计时间线合并真实预警流水；无关联预警时禁用 |

### 7.3 data.sql 数据补全（全库自洽，已用临时库实测）

- **补种子**：`po_ap_settlement_item` 156 行（演示 6 行 + 扩展 150 行，三单三侧对照）；`po_accrual` 155 行（演示 5 行 + 扩展 150 行，ACCRUED 76 / REVERSED 79 两态齐备）；`po_match_rule` 补币种维度规则并修正物料分类取值为真实分类「机械配件」。
- **修正不一致**：演示 9006 的 `qty_diff` 0 → 500（中性笔未到货）、`diff_type=QTY`；扩展段 `MOD(n,20)=13` 行原本 `PARTIAL_AMOUNT` 但差异金额为 0（发票总额恰等于收货总额），改为发票单价上浮 0.75%，差异真实可预警；`MOD(n,20)=15` 行上浮 5% 构成 HOLD。
- **规则联动**：容差按供应商取生效规则（1001→0.5%、1002→绝对 500、其余 1%），与 `match_rule_code` 一致；付款单只对 MATCHED 订单生成（实测 `pay_on_unmatched=0`）。
- **清理与幂等**：演示段与扩展段改为「先删后插」，旧枚举脏数据、孤儿行级明细、旧口径 INVOICE_MISMATCH 噪音预警启动时自动清除；data.sql 重放两次计数一致（153/156/155/5/47），实测通过。
- **存量升级**：data.sql 头部补充 `ALTER TABLE po_ap_settlement` 手工升级说明（MySQL 无 `ADD COLUMN IF NOT EXISTS`，`schema.sql` 的 `CREATE TABLE IF NOT EXISTS` 不会给旧表补列）。

### 7.4 验证结论

- 后端 `mvn compile` 通过；前端 `tsc --noEmit` 通过；lint 零新增问题。
- 临时库实测：schema + data.sql 两次执行均无错误；结算单头金额与行级明细汇总偏差为 0；付款单 0 笔落在未匹配结算单上；暂估 ACCRUED/REVERSED 两态与回冲差额齐备；旧 `PARTIAL` 脏行重放后自动清除。

## 八、全量重建与 50 单数据模型（2026-10-18）

### 8.1 为什么重建

联调时发现正式库 `yonerp_po` 仍停留在 plan4 改造前的结构：`po_ap_settlement` 缺 17 个字段，`po_ap_settlement_item`/`po_match_rule`/`po_accrual` 三张表不存在（`schema.sql` 的 `CREATE TABLE IF NOT EXISTS` 不会给旧表补列，`data.sql` 的 AP 种子自改造起每次启动都静默失败）；库里还残留旧枚举 `PARTIAL` 行与 93 条旧口径噪音预警。报表页「卡片 330 单 vs 表格 230 单」经实测为口径差异（卡片=全部口径，表格=生效口径=审批中 69 + 已审批 161，金额分文不差），已在页面补充口径说明；但数据层必须重建才能自洽。

### 8.2 一键重建脚本

`scripts/rebuild-db.ps1`：确认 → `DROP + CREATE DATABASE`（utf8mb4）→ 执行 `schema.sql` → 执行 `data.sql` → 14 项计数校验 + 8 项一致性校验 → 打印结算单/暂估状态分布。执行后需重启后端应用（DROP 后 HikariCP 旧连接失效）；报表 Redis 缓存 5 分钟 TTL 内可能仍是旧值。

### 8.3 新数据模型（围绕 50 笔采购订单全链路）

| 层 | 数据 |
| --- | --- |
| 主数据 | 6 家供应商、18 个物料（办公耗材/电子设备/机械配件/原材料/低值易耗品 5 分类）、5 条四维度匹配规则（全局 1% / 供应商 1001 0.5% / 供应商 1002 绝对 500 / 机械配件 2% 且不自动结算 / USD 币种 3%） |
| 订单 | 50 笔（9001~9050）+ 67 行明细；状态：已审批 25 / 审批中 10 / 草稿 8 / 已驳回 4 / 已取消 3；两级审批任务 84 条 + 流水 96 条（由订单状态推导生成，保证一致） |
| 收货 | 27 张收货单 + 35 行明细；含部分收货（9006 中性笔未到、9011 A3 纸未到、9026 铝型材 180/300、9028 缠绕膜未到）与分批收货（9008/9038 各两批） |
| 发票 | 20 张；含 5 张单价差异票（9003 ×1.015、9005 ×1.05、9012 ×1.015、9033 ×1.06、9034 ×1.025）与 2 张合单开票（9008、9038） |
| 付款 | 14 笔，全部落在 MATCHED 订单上（付款联动）；12 笔已付款 + 2 笔待付款 |
| 结算 | 25 张结算单 + 38 行行级明细；MATCHED 14 / PARTIAL_AMOUNT 2 / HOLD 3 / PARTIAL_QTY 1 / UNMATCHED 5 |
| 暂估 | 35 行：ACCRUED 6（货到票未到）/ REVERSED 29（票到回冲，含差额转成本差异 7057.42 元） |
| 库存 | 18 行，数量=累计入库；4 个物料低于安全库存（文件柜/服务器/减速机/钢板）触发库存预警 |

### 8.4 配套引擎修正

- **多收货单匹配**：`matchByOrder` 合并同一订单全部收货单的金额与明细，支持分批收货 + 合单开票（此前只取第一张收货单）。
- **状态判定按严重程度排序**：无差异 → MATCHED；规则未启用自动结算 → HOLD；金额差异超容差 2 倍 → HOLD；数量差异超容差 → PARTIAL_QTY；金额差异超容差 → PARTIAL_AMOUNT；其余 → MATCHED。
- **暂估按订单回冲**：`reverseByInvoice` 由按收货单改为按订单，合单开票时多张收货单的暂估一次性回冲，差额按行占比分摊。
- **暂估/月末批量暂估防重**：票已到（按订单判断）的收货单不再暂估。
- **报表口径说明**：卡片标注「全部口径」，新增口径说明条，解释卡片与表格的筛选口径差异；匹配状态列改用 `matchStatusMeta` 支持新枚举。

### 8.5 验证结论（临时库实测）

- schema + data.sql 全量执行零错误；14 项计数校验全部符合预期（50 订单/67 明细/84 审批任务/27 收货/20 发票/14 付款/25 结算单/38 行明细/35 暂估/5 规则）。
- 8 项一致性校验全部为 0：结算单头/行金额、结算行 vs 收货明细、结算行 vs 订单明细、结算单 vs 发票表、发票 vs 订单收货合计（合单票按订单汇总）、付款 vs 发票、暂估 vs 收货明细、非 MATCHED 订单的付款单。
- 结算单五态分布 MATCHED 14 / PARTIAL_AMOUNT 2 / HOLD 3 / PARTIAL_QTY 1 / UNMATCHED 5；差异金额合计真实非零（HOLD 6070.93 + PARTIAL_AMOUNT 986.49）。

## 九、差异处理闭环增强（A 打底 + B 状态分组）

用户反馈：「已经退票重开，怎么还在挂起待处理」——根因是 `resolveDiff` 只登记动作、状态留在 HOLD（HOLD 是付款闸门，不能因为登了记就放行），但用户侧没有「已有人在跟进」的状态反馈，预警也继续刷。本增强把「登记处理」和「差异真正解决」拆开，分两组管理。

### A 档：处理动作打底

- **处理意见必填**：`ResolveCmd.remark` 加 `@NotBlank`，`ApSettlementController.resolveDiff` 加 `@Valid`，空意见直接 400 `请填写处理意见（差异原因与处理依据）`，审计时间线才有追溯价值。
- **登记即写入等待态挂起原因**：异步动作（补收货/退票重开/调价）登记时，把 `hold_reason` 重写为 `已登记【动作】，等待…｜登记人：X｜登记时间：…｜处理意见：…`，工作台与审计时间线一眼可见「谁、何时、为什么」登记了什么。
- **列表/抽屉展示处理动作**：`ApSettlementList`、`DiffWorkbench` 增加「处理动作」列（`resolutionMeta` 中文映射：补收货/退票重开/调价/合理损耗放行/领导审批强制匹配），工作台处理抽屉按 `RESOLUTION_NEXT_STEP` 提示下一步（补录收货单 / 重开发票 / 改订单价后点重新匹配）。
- **登记后即时关闭关联预警**：前端 `DiffWorkbench.resolveMutation.onSuccess` 额外调 `agentScanAlerts()`，让失效预警清理（见下）立即生效，避免登记后预警还挂着。

### B 档：状态分组（待处理 vs 处理中）

新增三个等待态（枚举与 `matchStatusMeta`/`WAITING_STATUSES` 同步）：

| 状态 | 含义 | 进入 | 退出 |
| --- | --- | --- | --- |
| `WAITING_RECEIPT` | 已登记补收货 | `resolveDiff(ADD_RECEIPT)` | 新收货确认触发重新匹配 |
| `WAITING_INVOICE` | 已登记退票重开 | `resolveDiff(REDO_INVOICE)` | 新发票校验触发重新匹配 |
| `WAITING_ORDER` | 已登记调价 | `resolveDiff(ADJUST_PRICE)` | 订单改价后点「重新匹配」 |

- **差异工作台拆两组**：`待处理差异`（HOLD/PARTIAL_QTY/PARTIAL_AMOUNT，无人跟进，超容差自动预警）+ `处理中（已登记，等待新单据）`（WAITING_*，新单据触发重新匹配后自动回正常流程，期间不重复预警、也不能付款）。
- **付款联动自动生效**：付款拦截条件仍是「非 MATCHED 放行」，等待态自然被拦（无需改付款代码）。
- **预警自动退出（核心）**：`InvoiceMismatchEvaluator` 仍只报 HOLD/PARTIAL_*；`AlertRuleService.scanAll()` 末尾新增 `closeStaleMismatchAlerts()`——扫描时自动关闭「已转入等待态/已匹配/已关闭」结算单的未处理 INVOICE_MISMATCH 预警（写 HANDLE 流水留痕 + 清除 Redis 去重锁）。差异未解决的单据继续刷预警；差异再现（重新匹配推回 HOLD）时下一轮扫描立即重新生成预警（去重锁已清，不用等 TTL）。

### 完整闭环验证（已实测）

1. 扫描生成差异预警（HOLD/PARTIAL 各一条，设 Redis 去重锁 15min）。
2. 工作台登记「退票重开」→ 结算单 `WAITING_INVOICE`，`hold_reason` 含登记人/时间/意见；关联预警被 `closeStaleMismatchAlerts` 置 `handled=1` 并清锁。
3. 供应商重开发票并校验 → 事件驱动 `matchByOrder` 重新匹配，状态按新单据推回 `HOLD`（引擎覆盖写入等待态，天然闭环）。
4. 下一轮扫描：因去重锁已清且旧预警已关闭，重新生成未处理预警 → 回到「待处理」组，提醒差异仍在。

> 语义结论：**登记动作 ≠ 差异解决**。补收货/退票重开/调价只是「已有人在跟进」，差异要等新收货确认 / 新发票核对 / 订单改价后重新匹配才真正消失；期间付款继续被拦、预警不再重复打扰。只有「合理损耗放行」「领导审批强制匹配」才直接放行（后者走 DIRECTOR 审批）。
