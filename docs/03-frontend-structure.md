# 前端结构设计

> 配套文档：`00-technical-solution.md` 第 1、6 章 ｜ 本文定义路由、页面、组件树、状态管理与字段布局

## 1. 技术选型与工程规范

| 项 | 选型 | 说明 |
| --- | --- | --- |
| 构建 | Vite 5 + TypeScript 5 | 固定版本，避免工程不可用 |
| 框架 | React 18 | 函数组件 + Hooks |
| UI | Ant Design 5（组件基底）+ TailwindCSS 3.4（布局与细节微调） | 企业后台信息密度 + 灵活排版 |
| 路由 | React Router 6 | 嵌套路由 + 角色守卫 |
| 服务端数据 | TanStack Query | 缓存 30s、失效重取、请求态管理 |
| 全局状态 | Zustand（持久化） | 仅存当前用户/角色，不存业务数据 |
| 请求 | axios 实例 + 拦截器 | 统一注入 `X-User-Id` / `X-Role-Code` / `Idempotency-Key` |
| 图表 | recharts | 饼图（按状态）+ 柱状图（按时间） |
| 图标 | lucide-react、react-icons | |

工程约束：

- 单文件不超过 300 行，超过即拆分组件。
- `tsconfig.app.json` 设置 `verbatimModuleSyntax: false`、`noUnusedLocals: false`、`noUnusedParameters: false`。
- `vite.config.ts` 设 `server.host = '0.0.0.0'`、`allowedHosts: true`，代理 `/api` → `http://localhost:8080`。
- 类型定义在 `src/types` 并与后端 VO/DTO 对齐，统一 `export`。
- 不使用 try/catch 静默吞错，错误通过 `console.error` 输出并转换为用户可见提示。
- 交互元素一律使用原生语义标签：按钮用 `<button>`，输入用 `<input>`（AntD 组件内部即语义标签），禁止 div 模拟。

## 2. 路由表

| 路径 | 页面 | 可访问角色 | 说明 |
| --- | --- | --- | --- |
| `/login` | 角色选择页 | 全部 | 选择用户 + 角色，写入 Redis 会话 |
| `/` | 主框架（Layout） | 登录 | 侧边菜单 + 顶栏 |
| `/dashboard` | 工作台（可选） | 登录 | 待办与统计卡 |
| `/orders` | 订单列表 | 全部 | 核心列表页 |
| `/orders/create` | 订单新建 | BUYER / ADMIN | |
| `/orders/:id/edit` | 订单编辑 | BUYER(本人) / ADMIN | 状态非 DRAFT/REJECTED 跳转详情 |
| `/orders/:id` | 订单详情 | 全部 | 含明细、审批时间线、操作区 |
| `/approvals` | 我的待办 | PURCHASE_MANAGER / DIRECTOR | 带角标 |
| `/base` | 基础数据 | 全部（写需 ADMIN） | Tab：供应商 / 物料 / 用户角色 |
| `/reports` | 报表统计 | 全部 | |
| `/receipts` | 收货列表 | BUYER / ADMIN | |
| `/receipts/:id` | 收货详情 | BUYER / ADMIN | 含明细 + 确认/取消操作 |
| `/invoices` | 发票列表 | BUYER / ADMIN | |
| `/invoices/:id` | 发票详情 | BUYER / ADMIN | 含核对/撤销操作 |
| `/payments` | 付款列表 | BUYER / ADMIN | |
| `/payments/:id` | 付款详情 | BUYER / ADMIN | 含确认/驳回操作 |
| `/stock` | 库存管理 | BUYER / ADMIN | 含预警高亮 + 安全库存编辑 |
| `/ap-settlements` | 应付对账 | BUYER / ADMIN | 三单匹配列表 |
| `/agent-chat` | AI 对话 | 全部 | SSE 流式，支持规则模式表格渲染 |
| `/agent-alerts` | AI 预警 | 全部 | 30 秒轮询，支持标记已处理 |
| `403` | 无权限页 | 全部 | |
| `*` | 404 | 全部 | |

守卫规则：未选择用户 → 跳 `/login`；角色不匹配路由 → 跳 `403`；后端返回 3001 → 清本地状态并跳 `/login`。

## 3. 页面与组件树

```
src/
├── main.tsx                     应用入口（QueryClientProvider + ConfigProvider）
├── App.tsx                      路由注册
├── api/
│   ├── request.ts               axios 实例、拦截器（注入头、统一错误）
│   ├── auth.ts  orders.ts  approvals.ts  base.ts  reports.ts
├── layouts/
│   ├── MainLayout.tsx           顶栏 + 侧边菜单 + 内容区（固定顶栏，内容区 pt-16）
│   ├── AppHeader.tsx            品牌区 + 用户信息 + 角色切换下拉 + 退出
│   └── AppSider.tsx             菜单（按角色过滤，待办项角标）
├── router/
│   ├── index.tsx                路由表
│   └── RoleGuard.tsx            角色守卫
├── store/
│   └── useAuthStore.ts          Zustand：currentUser、currentRole、persist
├── types/
│   ├── order.ts  base.ts  report.ts  auth.ts  common.ts
├── components/
│   ├── StatusTag.tsx            状态胶囊（语义色）
│   ├── MoneyText.tsx            金额等宽右对齐展示
│   ├── PageCard.tsx             统一卡片容器
│   ├── FilterBar.tsx            筛选区容器
│   └── DictSelect.tsx           字典下拉（读 /api/dict/{type}）
└── pages/
    ├── Login/                   角色选择页
    │   ├── index.tsx
    │   └── RolePreview.tsx      当前角色可访问模块预览
    ├── OrderList/
    │   ├── index.tsx
    │   ├── OrderFilter.tsx      关键字/状态/日期/供应商
    │   └── OrderTable.tsx       表格 + 行操作
    ├── OrderEdit/
    │   ├── index.tsx
    │   ├── OrderForm.tsx        表头表单
    │   ├── ItemTable.tsx        可编辑明细表
    │   └── AmountFooter.tsx     合计与保存/提交
    ├── OrderDetail/
    │   ├── index.tsx
    │   ├── OrderInfo.tsx        只读头部信息
    │   ├── ItemView.tsx         明细只读表
    │   ├── ApprovalTimeline.tsx 审批流水时间线
    │   └── ActionBar.tsx        同意/驳回/撤回/取消（按后端 actions 渲染）
    ├── Approvals/
    │   ├── index.tsx
    │   └── ApprovalTable.tsx
    ├── BaseData/
    │   ├── index.tsx            Tabs 容器
    │   ├── SupplierPanel.tsx
    │   ├── MaterialPanel.tsx
    │   └── UserRolePanel.tsx
    ├── Reports/
    │   ├── index.tsx
    │   ├── SummaryCards.tsx     统计卡
    │   ├── StatusPieChart.tsx   recharts 饼图
    │   └── TimeBarChart.tsx     recharts 柱状图 + 粒度切换
    ├── ReceiptList/
    │   ├── index.tsx
    │   └── ReceiptCreate.tsx    新建收货单抽屉
    ├── ReceiptDetail/
    │   └── index.tsx            详情 + 确认/取消操作
    ├── InvoiceList/
    │   ├── index.tsx
    │   └── InvoiceCreate.tsx
    ├── InvoiceDetail/
    │   └── index.tsx
    ├── PaymentList/
    │   ├── index.tsx
    │   └── PaymentCreate.tsx
    ├── PaymentDetail/
    │   └── index.tsx
    ├── StockList/
    │   └── index.tsx            库存表格 + 预警高亮 + 安全库存编辑
    ├── ApSettlementList/
    │   └── index.tsx            三单匹配列表
    ├── AgentChat/
    │   └── index.tsx            SSE 流式聊天面板（规则模式自动渲染表格）
    └── AgentAlerts/
        └── index.tsx            预警列表（30s 轮询 + 标记已处理）
```

## 4. 状态管理

| 状态 | 存放位置 | 说明 |
| --- | --- | --- |
| 当前用户、当前角色 | Zustand（localStorage 持久化） | 切换角色后刷新菜单权限 |
| 订单列表/详情/报表数据 | TanStack Query | `staleTime: 30s`；写操作成功后 `invalidateQueries` |
| 字典、供应商/物料下拉 | TanStack Query（长 `staleTime`） | 后端已做 Redis 缓存，前端二次缓存 |
| 表单内部状态 | 组件 `useState` / AntD Form | 不进全局 |
| 请求中状态 | TanStack Query `isLoading` / `useMutation` | 按钮自动 `loading` 防重复点击 |

请求拦截器逻辑：

```text
请求：注入 X-User-Id = store.userId，X-Role-Code = store.currentRole；
      动作接口（submit/approve/cancel/withdraw）自动生成并注入 Idempotency-Key(UUID)
响应：code === 0 → 返回 data
      code === 3001 → 清空 store → 跳转 /login
      code === 4001 → message.warning('操作正在处理，请勿重复提交')
      code === 4002 → message.warning('单据已变更，请刷新后重试') + invalidate
      其他非 0 → message.error(message) 并 console.error 记录
```

## 5. 页面字段布局

### 5.1 角色选择页 `/login`

- 左：用户下拉（`/api/auth/users`）+ 角色下拉（该用户拥有的角色）+ 进入按钮
- 右：当前角色可访问模块预览卡（订单/审批/基础数据/报表 的读写标识）
- 进入后写入 Zustand 并跳 `/orders`

### 5.2 订单列表页 `/orders`

- 筛选区：关键字（编号/标题）、状态下拉、供应商下拉、日期区间、查询/重置
- 操作区：新建订单（BUYER/ADMIN 可见）、批量删除（仅草稿可选中）、导出视图（二期）
- 表格列：订单编号、标题、供应商、采购员、订单日期、数量、金额（右对齐等宽）、状态胶囊、当前节点、创建时间、操作（查看/编辑/删除/提交）
- 底部：分页（20 条/页），可配列排序

### 5.3 订单编辑页 `/orders/:id/edit`

- 卡片一「基本信息」：订单编号（新建时自动生成、不可编辑）、标题\*、供应商\*（下拉）、币种、订单日期\*、期望到货日期、备注
- 卡片二「物料明细」：可编辑表格（物料下拉 → 自动带出规格/单位/参考单价/税率）、数量、单价、税率、金额（自动计算、只读）、行备注；支持增行/删行/复制行
- 卡片三「金额合计」：数量合计、金额合计（由前端试算，后端为准）
- 底部固定操作条：保存草稿、提交审批（提交时生成幂等键）、取消

### 5.4 订单详情页 `/orders/:id`

- 卡片一「订单信息」：编号、标题、供应商、采购员、币种、订单日期、期望到货日期、金额合计、状态、当前节点、备注、创建人/时间
- 卡片二「物料明细」：只读表格
- 卡片三「审批进度」：当前节点高亮 + 审批流水时间线（操作人、动作、意见、时间）
- 操作区：按后端返回的 `actions` 渲染按钮（提交/撤回/取消；审批角色在待办页与详情页可见同意/驳回），驳回需弹窗填写意见

### 5.5 基础数据页 `/base`

- Tabs：供应商 / 物料 / 用户与角色
- 每个 Tab：筛选 + 表格 + 新增按钮（仅 ADMIN 可见）+ 抽屉表单（编辑）
- 删除前置校验失败时展示后端 2004 提示

### 5.6 报表页 `/reports`

- 顶部统计卡：总单量、总金额、审批中数量、已审批数量
- 中部：按状态统计饼图（recharts `PieChart`）
- 下部：按时间统计柱状图（月/日粒度切换，双轴：金额 + 单量）
- 右上角：数据更新时间（来自 Redis 缓存时间）+ 手动刷新按钮

## 6. 按钮级权限矩阵（前端显隐）

| 按钮 | ADMIN | BUYER | PURCHASE_MANAGER | DIRECTOR |
| --- | :-: | :-: | :-: | :-: |
| 新建订单 | ✅ | ✅ | ❌ | ❌ |
| 编辑/删除（本人草稿） | ✅ | ✅ | ❌ | ❌ |
| 提交审批 | ✅ | ✅ | ❌ | ❌ |
| 撤回 | ✅ | ✅ | ❌ | ❌ |
| 取消 | ✅ | ✅（草稿/驳回） | ❌ | ❌ |
| 同意/驳回（node=1） | ❌ | ❌ | ✅ | ❌ |
| 同意/驳回（node=2） | ❌ | ❌ | ❌ | ✅ |
| 基础数据新增/编辑/删除 | ✅ | ❌（只读） | ❌（只读） | ❌（只读） |
| 用户角色管理 | ✅ | ❌ | ❌ | ❌ |
| 菜单「我的待办」 | ❌ | ❌ | ✅ | ✅ |

前端仅做显隐与禁用，**后端为真实防线**；详情页按钮以接口返回的 `actions` 为准，前端不重复实现规则。

## 7. 设计风格落地

| 元素 | 落地方式 |
| --- | --- |
| 顶栏 | 固定定位（`fixed`），深蓝渐变 `#1C2B45 → #1677FF`，玻璃质感（`backdrop-blur`），高度 64px |
| 内容区 | `pt-16` 避开顶栏，背景 `#F5F7FA`，卡片白色 + 8px 圆角 + 柔和阴影 |
| 侧边菜单 | 可折叠，深色 `#001529`，选中项高亮 + 左侧色条 |
| 状态标签 | 胶囊语义色：草稿 `#8C8C8C`、审批中 `#1677FF`、已审批 `#52C41A`、已驳回 `#FF4D4F`、已取消 `#BFBFBF` |
| 金额 | 右对齐 + 等宽字体（`tabular-nums`） |
| 表格 | 行悬浮高亮，弱化斑马纹，操作列固定右侧 |
| 动效 | 页面/弹层 160ms 缓动，按钮加载态，操作结果 Toast |
| 字体 | PingFang SC；标题 24/600，子标题 16/500，正文 14/400 |

## 8. 与后端的联调要点

1. 开发期通过 Vite 代理 `/api` 到 `http://localhost:8080`，避免跨域配置反复。
2. 每次写操作成功后 `invalidateQueries`（列表、详情、待办、报表），保证与后端缓存清除一致。
3. 报表数据可能来自 Redis 缓存（5 分钟），页面展示"数据更新时间"避免误解。
4. 后端返回 `actions` 决定按钮渲染，避免前后端规则不一致。
5. 切换角色后需重新拉取所有 Query（角色影响数据范围与报表口径）。
