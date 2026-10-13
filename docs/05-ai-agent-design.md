# AI Agent 子系统详细设计

> 配套文档：`00-technical-solution.md` ｜ 本文说明 Agent 架构、工具清单、Prompt 设计与 LLM 配置

## 1. 总体架构

```
React SPA
  AgentChatPanel（SSE 流式，规则模式自动渲染表格）
  AgentAlertsDrawer（预警通知，30s 轮询）
        │ HTTP + SSE（/api/agent/chat, /api/agent/alerts）
Spring Boot 3.2
  AgentController
    ├── /api/agent/chat（SSE 流式）
    ├── /api/agent/alerts（轮询预警）
    └── /api/agent/suggest/{type}（辅助审批/发票校验/付款建议）
        │
  AgentServiceImpl（双模式运行）
    ├── LLM 模式：配置 API Key 时通过 LangChain4j 调用通义千问/智谱
    └── 规则降级模式：无 API Key 或 LLM 调用失败时，关键词匹配 + 直接调用工具
        │
  LangChain4j Tools（@Tool 注解）
    ├── ReportTool    → ReportService（报表统计/排行）
    ├── OrderTool     → OrderService（订单查询/逾期订单）
    ├── InventoryTool → InventoryService（库存/预警）
    ├── SettlementTool→ ApSettlementService（三单匹配/供应商余额）
    ├── ReceiptTool   → ReceiptService（收货单查询）
    └── AlertScanJob  定时扫描（每 5 分钟）
```

## 2. 双模式运行设计

### 2.1 LLM 模式

- 启用条件：`DASHSCOPE_API_KEY` 或 `ZHIPU_API_KEY` 环境变量非空
- 配置：`application.yml` → `yonerp.agent.provider`（`dashscope` / `zhipu`）
- 调用方式：LangChain4j `ChatLanguageModel.generate(List<ChatMessage>)` 同步调用，响应按 10 字符分块模拟 SSE 流式
- 失败降级：LLM 调用抛异常时自动捕获，降级为规则模式，并在日志记录 `log.warn`

### 2.2 规则降级模式

- 无 API Key 时直接可用，保证 Agent 功能始终可演示
- 关键词匹配规则：

| 关键词 | 触发工具 | 返回类型 |
| --- | --- | --- |
| 逾期 / overdue | `OrderTool.queryOverdueOrders()` | JSON（type=overdue_orders） |
| 库存 / 预警 / 安全库存 | `InventoryTool.lowStockAlerts()` | JSON（type=low_stock） |
| 供应商 / 应付 / 余额 / 付款 | `SettlementTool.supplierBalances()` | JSON（type=supplier_balance） |
| 报表 / 统计 / 排行 / 金额 | `ReportTool.rank("supplier", 10)` | JSON（type=report_rank） |
| 汇总 / 总额 / 总订单 | `ReportTool.summary()` | JSON（type=report_summary） |

- 前端 `AgentChat` 页面自动识别 JSON 响应，提取 `data` 字段渲染为 Ant Design 表格，`note` 字段展示为提示标签

## 3. 工具清单（Tools）

### 3.1 ReportTool

```java
@Tool("查询采购报表，按供应商/采购员分组统计订单金额与数量（TOP N）")
public String rank(@P("分组维度：supplier/buyer") String groupBy, int topN)

@Tool("查询采购订单汇总（总数/总金额/审批中数量/已审批数量）")
public String summary()
```

### 3.2 OrderTool

```java
@Tool("查询订单列表，可按状态/供应商筛选")
public String queryOrders(@P("状态：APPROVING/APPROVED 等，可空") String status,
                          @P("供应商ID，可空") Long supplierId)

@Tool("查询逾期订单（期望到货日期已过且仍为审批中）")
public String queryOverdueOrders()
```

### 3.3 InventoryTool

```java
@Tool("查询库存低于安全库存的物料列表")
public String lowStockAlerts()

@Tool("查询指定物料库存详情")
public String getInventory(@P("物料ID") Long materialId)
```

### 3.4 SettlementTool

```java
@Tool("查询各供应商应付余额（未付款发票金额合计 - 已付款金额）")
public String supplierBalances()

@Tool("查询三单匹配记录列表")
public String querySettlements(@P("状态：MATCHED/PARTIAL/UNMATCHED，可空") String matchStatus)
```

### 3.5 ReceiptTool

```java
@Tool("查询收货单列表，可按订单ID/状态筛选")
public String queryReceipts(@P("订单ID，可空") Long orderId,
                            @P("状态：DRAFT/CONFIRMED/CANCELED，可空") String status)
```

## 4. 预警机制（AlertScanJob）

### 4.1 扫描频率

- 启动后 1 分钟首次执行，之后每 5 分钟一次（`@Scheduled(fixedRate=300000, initialDelay=60000)`）

### 4.2 扫描内容

| 预警类型 | 触发条件 | 去重策略 |
| --- | --- | --- |
| `OVERDUE_ORDER` | 订单状态为 `APPROVING` 且 `expect_date < today` | Redis key `alert:dedup:overdue:{orderId}`，TTL 24h |
| `LOW_STOCK` | `po_inventory.quantity <= po_inventory.safety_stock` | Redis key `alert:dedup:lowstock:{materialId}`，TTL 12h |

### 4.3 预警存储

- 列表：Redis List `po:alert:list`（JSON 序列化），TTL 1 小时
- 单条：Redis String `po:alert:item:{alertId}`，TTL 1 小时
- 前端每 30 秒轮询 `GET /api/agent/alerts`，拉取未处理预警
- `POST /api/agent/alerts/{alertId}/handle` 标记已处理（更新 List 中对应条目）

## 5. LLM 配置

### 5.1 环境变量

```bash
# .env（.gitignore 排除）
DASHSCOPE_API_KEY=sk-xxx          # 通义千问（百炼平台 https://dashscope.aliyun.com）
ZHIPU_API_KEY=                    # 智谱 GLM（可选，https://open.bigmodel.cn）
AGENT_LLM_PROVIDER=dashscope      # 提供商选择
```

### 5.2 application.yml

```yaml
yonerp:
  agent:
    provider: ${AGENT_LLM_PROVIDER:dashscope}
    dashscope-api-key: ${DASHSCOPE_API_KEY:}
    dashscope-model: ${DASHSCOPE_MODEL:qwen-max}
    zhipu-api-key: ${ZHIPU_API_KEY:}
    zhipu-model: ${ZHIPU_MODEL:glm-4-plus}
    timeout: 60
    alert-scan-interval-minutes: 5
```

### 5.3 LangChain4j Bean 创建逻辑

```
若 provider=dashscope 且 DASHSCOPE_API_KEY 非空
  → QwenChatModel.builder().apiKey(...).modelName("qwen-max").temperature(0.7f).maxTokens(2048)

若 provider=zhipu 且 ZHIPU_API_KEY 非空
  → OpenAiChatModel.builder().baseUrl("https://open.bigmodel.cn/api/paas/v4")
      .apiKey(...).modelName("glm-4-plus").temperature(0.7).maxTokens(2048)

其他情况
  → Bean 为 null，AgentServiceImpl 注入 null，自动运行规则模式
```

### 5.4 系统 Prompt

```text
你是采购订单管理系统的 AI 助手，负责帮采购员查询报表、生成预警、辅助审批、智能单据。
规则：
1. 回答简洁，先给结论再给数据；涉及金额用"元"为单位。
2. 如果用户问"哪些订单逾期了"，说明期望到货日期已过但仍在审批中的订单。
3. 涉及审批/付款建议时，给出建议但标注"仅供参考，请人工确认"。
```

## 6. 前端 Agent 页面

### 6.1 AgentChat（`/agent-chat`）

- 顶部：演示模式提示 + 预设示例问题（逾期订单/库存预警/供应商余额/报表排行/订单汇总）
- 中部：对话记录区（自动滚动，流式生成中带光标动画）
- 底部：输入框 + 发送按钮
- 规则模式响应自动解析 JSON → 渲染 Ant Design 表格（`note` 字段展示为蓝色标签）

### 6.2 AgentAlerts（`/agent-alerts`）

- 顶部：刷新按钮 + 轮询状态提示（每 30 秒自动刷新）
- 中部：预警卡片列表（左侧色条按等级着色：CRITICAL 红 / WARNING 黄 / INFO 蓝）
- 每张卡片：类型标签 + 标题 + 关联单号 + 详情 + 生成时间 + "标记已处理"按钮

## 7. 扩展方向（后续）

- [ ] 智能单据：`ChatAgent` 调 `generateReceipt(orderId)` Tool 生成收货单草稿，采购员确认后提交
- [ ] 辅助审批：订单提交时同步调用 `ApprovalAgent.suggestApproval`，结果写入 `po_approval_task.suggestion`
- [ ] 发票校验：发票创建时调用 `InvoiceAgent.suggestInvoiceCheck`，差异超过 1% 自动标记
- [ ] 真正的 SSE 流式（替换当前 10 字符分块模拟，改为 LangChain4j `StreamingChatLanguageModel`）
- [ ] Agent 会话历史持久化（当前仅内存，重启丢失）
- [ ] 多轮对话上下文管理（当前 history 最多 10 条）
