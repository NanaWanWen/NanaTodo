# YonErp AI Agent 与预警中心改造（plan3）

## 一、目标

1. **对话助手升级为真 AI Agent**：通义千问通过 LangChain4j `AiServices` 自主编排工具（Tool Calling），取代当前「规则取数 + LLM 润色文案」；重启（含 IDE 启动）后仍能连上千问；算力不足时对话流内提示并自动降级。
2. **预警中心去重**：同一问题未处理前不重复提醒；处理/忽略后问题再现才重新提醒。
3. **规则变更重建语义**：修改规则 A → 清除 A 的历史预警并按新规则重建；未修改的规则 B 保持原样。
4. **消息实时可靠**：预警消息不丢、及时送达，支持手动刷新。
5. **产出**：`docs/plan3.md`、`docs/main.txt`（简历项目经验）。

## 二、现状诊断（改造前，已验证）

### 2.1 对话侧

| 问题 | 证据 |
| --- | --- |
| 不是真 Agent | `llmChat()` = `fetchToolData()`（关键词规则取数）→ 注入 SystemMessage → `chatLanguageModel.generate()` 单次生成。四个 Tool 类的 `@Tool` 从未被 AiServices 注册，LLM 无法自主编排 |
| QwenChatModel 工具支持不足 | `LangChain4jConfig` 中 dashscope 分支用 `QwenChatModel`（langchain4j-dashscope 0.36.2），function calling 支持有限 |
| .env 加载依赖工作目录 | `spring.config.import: optional:file:../.env[.properties]` 相对 JVM `user.dir` 解析。从 `backend/` 用 `mvn spring-boot:run` 启动 → 命中 `YonErp/.env`；从 IDE 启动（工作目录 `YonErp`）→ 解析到 `Desktop/.env`，Key 为空 → `isAvailable()=false` → 直接走规则降级 |

### 2.2 预警侧

| 问题 | 证据 |
| --- | --- |
| 重复预警（119→430→1677） | `isDuplicate()` 中 Redis 锁 `setIfAbsent(...,10min)` 抢到锁即 `return false`（**放行生成**），跳过数据库去重；扫描间隔 5min < 锁 TTL 10min → 每 10 分钟同一 `(ruleCode,refId)` 复制 1 条 |
| 消息被吞（4 个根因） | ① 前端 `error` 事件仅 `setSseConnected(false)`，**无任何重连**，后端 `SseEmitter(300_000L)` 5 分钟超时后永久断开；② `SSE_CONNECTIONS.put(userId, emitter)` 单值覆盖，多标签页时后开覆盖先开；③ 前端 SSE `useEffect` 依赖 `showHandled`，切换开关即销毁重建连接；④ 无心跳，超时被动断开；推送失败仅 remove 不补偿 |

## 三、改造内容与实现

### 任务 1：LLM 连接保障（.env 多路径 + 通义千问兼容端点）

**目标**：无论从 `mvn` 还是 IDE 启动，重启后都能连上千问。

- `application.yml`：`spring.config.import` 改为多候选
  `optional:file:.env[.properties],optional:file:../.env[.properties]`（两种工作目录均可命中）
- `LangChain4jConfig.java`：dashscope 分支由 `QwenChatModel` 改为 `OpenAiChatModel`
  - `baseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1")`
  - `modelName` 沿用 `yonerp.agent.dashscope-model`（默认 `qwen-max`，不稳定时备选 `qwen-plus`）
  - `temperature(0.3)`、`maxTokens(2048)`、`timeout`
  - 保留 zhipu 分支与 Key 缺失时返回 null 的降级能力

### 任务 2：AiServices 构建真 Agent（LLM 自主 Tool Calling）

- 新增 `agent/ai/PurchaseAgent.java`（`@SystemMessage` 要求先取数再作答）
- `LangChain4jConfig` 新增 Bean，注册 4 个工具类上的 `@Tool`：

```java
AiServices.builder(PurchaseAgent.class)
        .chatLanguageModel(chatLanguageModel)
        .tools(orderTool, reportTool, inventoryTool, settlementTool)
        .build();
```

- `AgentServiceImpl.llmChat()` 改为 `purchaseAgent.chat(message)`
- 移除旧 `buildMessages()` 与 `SYSTEM_PROMPT`（提示词迁移至 Agent 接口）
- 保留「回答缺少结构化数据时由规则取数兜底拼接」，保证前端一定能渲染表格

### 任务 3：算力不足检测 + 界面提示 + 本地规则降级

- 异常识别 `isCapacityInsufficient()`：遍历异常链，按 `429` / `throttl` / `quota` / `rate limit` / `capacity` / `timeout` / `SocketTimeoutException` 判定
- `degradeWithNotice()`：判定算力不足 → 提示文案 `算力不足，已切换本地规则模式`；其他异常 → `AI 服务暂时不可用，已切换本地规则模式`
- 提示写入结果 JSON 的 note 字段（而非简单拼接），保证前端一定能渲染出提示文案

### 任务 4：预警去重（生命周期去重，不重复提醒）

```java
// ① 数据库权威去重：存在未处理且未忽略的预警 → 判定重复，跳过
Long count = alertMapper.selectCount(...eq(ruleCode).eq(refId).eq(handled,0).eq(ignored,0));
if (count > 0) return true;
// ② 无历史 → Redis 仅作并发防重（TTL = 扫描间隔 × 3）
return !Boolean.TRUE.equals(setIfAbsent(lockKey, "1", Duration.ofMinutes(scanInterval * 3)));
```

- 语义结果：未处理 → 不重复提醒；已处理/已忽略 → 问题再现时可重新提醒

### 任务 5：规则变更 → 该规则预警清除重建

- `update()`：比对实际传入的 `threshold` / `thresholdUnit` / `level` / `enabled`，变更或被禁用 → 清除该规则名下预警（未修改的规则不受影响）
- 清理后若规则仍启用 → 立即 `scanRule(rule)` 按新配置重建
- `delete()`：删除规则时同步清理其名下预警
- 兼容历史数据 `ruleId` 为空的情况，同时按 `ruleCode` 兜底清理
- **清除预警时同步释放该规则名下的 Redis 去重锁**（`alert:dedup:{ruleCode}:*`）：否则重建阶段会被残留锁误判为「重复」而生成不出来
- **重建使用「数据库原记录 + 仅覆盖本次传入字段」的合并对象**：避免调用方只提交阈值时 `ruleCode` 缺失导致无法定位评估器

### 任务 6：预警消息实时可靠性（防吞消息）

**后端** `AlertNotifyService.java`

- 连接容器改为 `Map<Long, CopyOnWriteArraySet<SseEmitter>>`，支持多标签页（消除覆盖丢消息）
- 新增心跳：每 30s 向所有连接发送心跳帧（早于 300s 超时），避免被动断开
- 推送失败仅移除失效连接，不影响其他连接

**前端** `frontend/src/pages/AgentAlerts/index.tsx`

- `error` 事件 → 指数退避重连（1s→2s→4s→…→上限 30s）
- `CLOSED` 状态才手动重连，避免与浏览器自动重连冲突
- 重连成功（`open`）→ 立即 `fetchAlerts()` 补偿断连期间消息
- SSE `useEffect` 依赖改为仅 `[connectSse]`，用 ref 持有最新回调，避免切换开关误重建连接
- 新增 `visibilitychange` 监听：回到前台立即刷新
- 连接状态 Tag：已连接 / 重连中 / 未连接
- 保留并增强「立即扫描」「手动刷新」按钮

### 任务 7：端到端验证 + 历史重复数据清理

- 清理 `agent_alert`：共删除重复预警 **1244 条**，并按 `alert_type` 补齐 57 条历史遗留的 `rule_code`，再次去重后活跃预警由 1677 降至 **375** 条
- 清理孤立的处理流水 `agent_alert_action`
- 验证结果：
  1. 千问连通：`DashScope OpenAI-compatible model [qwen-max]` + `PurchaseAgent ... tools[OrderTool, ReportTool, InventoryTool, SettlementTool]` 均初始化成功（多次重启均生效）
  2. Agent 自主调用工具：提问「库存低于安全库存的物料有哪些？」→ 返回显示器（MAT004）库存 80 < 安全库存 100 的真实数据
  3. 连续两次手动扫描，新增均为 **0**（去重生效，不再重复提醒）
  4. 规则变更重建：阈值 1.0→0.5 清除后重建为 0 条、0.5→1.0 重建、1.0→1.2 重建出 1 条、恢复 1.0 后保持 1 条；期间 OVERDUE_ORDER(213) / APPROVAL_TIMEOUT(69) / OVERSTOCK(2) / PRICE_ABNORMAL(1) 等未修改规则数量完全不受影响
  5. SSE 可靠性：连接建立即推送 `unread-count`，并在 30s 心跳周期内收到心跳帧 `:hb`；多连接容器与前端重连补偿已落地

### 任务 8：产出文档

- `docs/plan3.md`：本文档
- `docs/main.txt`：简历可直接复制的「项目经验」条目

## 四、涉及文件

**后端**

- `backend/src/main/resources/application.yml`
- `backend/src/main/java/com/yonerp/po/agent/config/LangChain4jConfig.java`
- `backend/src/main/java/com/yonerp/po/agent/ai/PurchaseAgent.java`（新增）
- `backend/src/main/java/com/yonerp/po/agent/service/impl/AgentServiceImpl.java`
- `backend/src/main/java/com/yonerp/po/agent/service/AlertRuleService.java`
- `backend/src/main/java/com/yonerp/po/agent/service/AlertNotifyService.java`
- `backend/src/main/java/com/yonerp/po/agent/tool/`（OrderTool / ReportTool / InventoryTool / SettlementTool，已有 `@Tool`）

**前端**

- `frontend/src/pages/AgentAlerts/index.tsx`

**产出**

- `docs/plan3.md`、`docs/main.txt`

## 五、验证结论

| 项 | 结果 |
| --- | --- |
| 千问连接（重启后） | ✅ 兼容端点初始化，多次重启均生效 |
| Agent 自主 Tool Calling | ✅ 返回真实业务数据（显示器 80 < 100） |
| 算力不足提示 + 降级 | ✅ 已实现（识别限流/配额/超时，提示写入 note），待限流场景实测 |
| 预警去重 | ✅ 连续扫描新增 0，不再重复提醒 |
| 历史数据清理 | ✅ 删除重复 1244 条，活跃预警 1677 → 375 |
| 规则变更清除重建 | ✅ 已实测（改 A 清 A 并重建，未改规则数量不变） |
| 消息可靠性 | ✅ SSE 心跳帧实测收到；多连接容器、前端重连 + 补偿已落地（浏览器端待实际联调） |

## 五点五、页面「对不上」问题修复（前端）

现象：预警规则配置页「暂无数据」、预警中心白屏，而数据库里 `agent_alert_rule` = 7、`agent_alert` = 377，后端接口返回正常。

| 现象 | 根因 | 修复 |
| --- | --- | --- |
| 预警中心白屏 | `AgentAlerts/index.tsx` 中 `const fetchRef = useRef(fetchAlerts)` 引用了下方 `const` 声明的 `fetchAlerts`，命中**暂时性死区（TDZ）**，渲染即抛错，组件整页崩溃 | `fetchRef` 初始值改为空实现 `async () => {}`，真实函数由后续同步 effect 赋值 |
| 规则页「暂无数据」 | `AlertRules/index.tsx` 使用 `useQuery({ onSuccess })`，项目为 **@tanstack/react-query v5**，该回调已被移除，`setRules` 从不执行 | 改为在 `useEffect([data])` 中同步；`loading` 由 `isFetching` 驱动 |
| 删除规则点不动 | `Modal.confirm` 使用了 `onConfirm`（antd v5 应为 `onOk`） | 改为 `onOk` |
| 生产构建失败（`tsc -b`） | `agent.ts` 缺 `AlertActionVO` 类型导入、`ImportModal` 缺 `Spin` 导入、`MainLayout` header 允许 `null`、规则表 `render` 形参隐式 any | 全部补齐，`npm run build` 通过 |

同时补齐「规则 ↔ 预警」对应关系：

- 规则配置页新增**当前预警数**列（点击跳转到预警中心并按该规则过滤），保存后提示"该规则的预警已按新规则重建"，并失效相关查询缓存。
- 预警中心新增**规则下拉筛选**（支持 `/agent-alerts?rule=XXX` 深链），卡片标签展示**规则中文名 + 规则编码**，历史无 `ruleCode` 的预警回退按 `alertType` 显示。

实测（改 A 只重建 A、B 不变）：

| 操作 | APPROVAL_TIMEOUT | OVERDUE_ORDER |
| --- | --- | --- |
| 改前 | 69 | 213 |
| 阈值 24h → 48h（重建，create_time 全部刷新） | 69 | 213 |
| 阈值 → 100000h | 0 | 213 |
| 恢复 24h | 69 | 213（总数 377 不变） |

## 五点六、对话「AI 服务暂时不可用」根因修复

现象：问「审批中的订单有哪些？」稳定降级到规则模式，提示「AI 服务暂时不可用」；而「库存低于安全库存」等问法正常。`WARN ... falling back to rule mode: Cannot invoke "Object.getClass()" because "argument" is null`。

| 项 | 说明 |
| --- | --- |
| 根因 | 通义千问调用带**可选参数**的工具时显式传 `null`（如 `"supplierId": null`），langchain4j 0.36.2 `DefaultToolExecutor.coerceArgument` 对 null 做类型转换抛 NPE，整次对话失败降级。无参工具（库存预警/汇总）不受影响，带可选参数的 `queryOrders`（status/supplierId/beforeDate 可空）必挂 |
| 规则兜底缺陷 | 「审批中的订单有哪些」是倒装语序，规则引擎只匹配 `哪些订单`，匹配不到 → 降级后答非所问 |
| 修复 ① | 新增 `agent/ai/NullSafeTools.java`：执行工具前把参数 JSON 中的显式 null 字段剔除（等价未传），再交给 `DefaultToolExecutor`；`LangChain4jConfig` 改用 `AiServices.tools(Map<ToolSpecification, ToolExecutor>)` 接入 |
| 修复 ② | `AgentServiceImpl` 订单列表意图放宽为 `哪些/订单列表/查询订单/列出/订单明细/清单`，兼容倒装语序（`parseStatus` 本就支持「审批中」） |
| 验证 | 「审批中的订单有哪些？」「逾期订单有哪些？」「本月我的采购订单总额？」「各供应商应付余额？」连续多次均走真 LLM 返回真实数据，无降级 |

## 六、遗留与后续建议

- **演示数据老化**：300 条演示订单的 `expect_date` 固定为 2026-04-14 起的 183 天，随时间推移会集体逾期，导致 `OVERDUE_ORDER` 基数虚高（当前 213 条）。建议后续将 `data.sql` 的日期改为基于 `CURDATE()` 动态生成。
- **`qwen-max` 工具调用稳定性**：若后续出现工具调用不稳定，可将 `dashscope-model` 切换为 `qwen-plus`。
- 邮件通知渠道仍为 TODO（预留 `notifyChannel` 字段）。
