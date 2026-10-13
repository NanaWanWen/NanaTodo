# YonErp · 采购订单管理练习项目

模拟企业级 ERP 采购流程的练习项目，为学习用友 BIP / YonBIP 打基础。前后端分离单体应用，本地开发环境运行。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 前端 | React 18 + TypeScript 5 + Vite 5 + Ant Design 5 + TailwindCSS + TanStack Query + Zustand + recharts（**需 Node 18+**） |
| 后端 | JDK 17 + Spring Boot 3.2.5（Maven 单模块）+ MyBatis-Plus 3.5.7 + Jakarta Validation |
| 数据库 | MySQL 8 |
| 中间件 | Redis 7（会话、基础数据缓存、报表缓存、防重/限流/锁、单号序列） |
| 部署 | 本地开发环境（docker-compose 启动 MySQL 与 Redis） |

## 文档索引

| 文档 | 内容 |
| --- | --- |
| `00-technical-solution.md` | 完整技术方案：架构、数据库、Redis、API、状态机、权限、异常、实施计划 |
| `01-database-schema.md` | 数据库详细设计：完整 DDL、索引、字典与初始化数据、缓存一致性约定 |
| `02-api-design.md` | API 详细设计：请求/响应示例、错误码、权限与 Redis 参与矩阵 |
| `03-frontend-structure.md` | 前端结构：路由、页面组件树、状态管理、字段布局、权限矩阵 |
| `04-redis-design.md` | Redis 详细设计：Key 手册、TTL、降级、配置与运维自检 |

## 项目边界

**做**：订单主从 CRUD、两级审批状态机、基础数据（供应商/物料）、报表统计、四角色权限、参数与流转校验、Redis 缓存与并发控制。

**不做**：收货入库与库存、应付发票、真实多租户、工作流引擎、消息队列、微服务、移动端、真实密码与令牌鉴权。

## 角色

| 角色码 | 名称 | 核心职责 |
| --- | --- | --- |
| `ADMIN` | 管理员 | 用户角色与基础数据维护、全部订单可管、已审批单异常取消 |
| `BUYER` | 采购员 | 新建/编辑/删除本人订单、提交、撤回、取消；仅看本人数据 |
| `PURCHASE_MANAGER` | 采购经理 | 一级审批（node=1） |
| `DIRECTOR` | 总监 | 二级审批（node=2） |

> 多级审批需要区分审批节点身份，故将原"审批人"单角色拆分为采购经理与总监两级。

## 订单状态

`DRAFT 草稿` → `APPROVING 审批中`（采购经理 → 总监）→ `APPROVED 已审批` / `REJECTED 已驳回`；草稿与已驳回可 `CANCELED 已取消`；审批中可撤回回草稿。详见 `00-technical-solution.md` 第 5 章。

## 本地启动

前置条件：JDK 17+、Maven 3.8+、Node 18+、Docker（或本地已装 MySQL 8 与 Redis 7）。

```bash
# 1. 启动 MySQL 8 + Redis 7
docker compose up -d

# 2. 启动后端（8080）：首次启动自动执行 schema.sql 与 data.sql
cd backend
mvn spring-boot:run

# 3. 启动前端（5173）
cd frontend
npm install
npm run dev
```

访问 `http://localhost:5173` 选择用户与角色进入；接口文档 `http://localhost:8080/swagger-ui.html`。

手动初始化数据库（如未使用 `spring.sql.init`）：

```bash
mysql -h127.0.0.1 -uroot -proot123 yonerp_po < backend/src/main/resources/schema.sql
mysql -h127.0.0.1 -uroot -proot123 yonerp_po < backend/src/main/resources/data.sql
```

在 IDEA 中运行：

1. 打开 `backend` 目录，Maven 导入后运行 `PoApplication`（JDK 17）。
2. 前端：Terminal 执行 `npm run dev`，Node 解释器需选择 18 及以上，否则 Vite 5 会因缺少全局 `crypto` 报错。

## 冒烟清单

| 场景 | 预期 |
| --- | --- |
| 未带 `X-User-Id` 访问 `/api/orders` | 返回 3001 |
| 采购员查看他人订单 | 返回 3003 |
| 采购员提交后查看待办 | 采购经理待办 +1，角标更新 |
| 经理通过 → 总监通过 | 订单状态 `APPROVED`，流水 4 条 |
| 总监驳回（意见必填） | 订单 `REJECTED`，后续任务作废 |
| 连续两次提交（同幂等键） | 第二次返回 4001 |
| 停止 Redis 后走完流程 | 业务可用，缓存回源数据库 |
| 供应商被订单引用时删除 | 返回 2004 |

## 目录

```
YonErp/
├── docker-compose.yml   MySQL 8 + Redis 7
├── docs/                设计文档
├── backend/             Spring Boot 后端
└── frontend/            React 前端
```
