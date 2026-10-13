# Redis 详细设计

> 配套文档：`00-technical-solution.md` 第 3 章 ｜ 本文为可落地的 Key 手册与运维说明

## 1. 定位与原则

1. **MySQL 是唯一真源**：所有业务状态、金额、审批结果以 DB 为准。
2. **Redis 只做四类事**：会话上下文、低频变更数据缓存、统计结果缓存、防重/限流/锁/序列。
3. **不缓存订单与审批实体**：状态高频变更，缓存极易不一致。
4. **Redis 故障不阻断业务**：任何 Redis 操作都需异常兜底，最坏情况退化为"直连 MySQL"。
5. **数据可丢失**：Redis 中全部数据均可重建，不承载不可恢复信息。

## 2. Key 手册

命名规范：`po:{域}:{对象}:{标识}`，全小写，冒号分隔，禁止空格与中文。

| # | Key | 类型 | 值示例 | TTL | 写入点 | 失效点 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `po:session:{userId}` | Hash | `{userId, username, realName, currentRole, loginTime}` | 8h（访问续期） | 登录/切换角色 | 退出、管理员改角色 |
| 2 | `po:session:{userId}:roles` | Set | `{BUYER, PURCHASE_MANAGER}` | 8h | 登录、分配角色 | 管理员改角色 |
| 3 | `po:cache:user:list` | String(JSON) | 用户数组 | 30min+抖动 | 首次查询 | 用户增删改 |
| 4 | `po:cache:supplier:{id}` | String(JSON) | 供应商对象 | 30min+抖动 | 详情查询 | 供应商增删改 |
| 5 | `po:cache:supplier:list` | String(JSON) | 供应商数组 | 30min+抖动 | 列表/下拉查询 | 供应商增删改 |
| 6 | `po:cache:material:{id}` | String(JSON) | 物料对象 | 30min+抖动 | 详情查询 | 物料增删改 |
| 7 | `po:cache:material:list` | String(JSON) | 物料数组 | 30min+抖动 | 列表/下拉查询 | 物料增删改 |
| 8 | `po:cache:dict:{type}` | String(JSON) | 字典项数组 | 2h | 启动预热/首次查询 | 字典变更 |
| 9 | `po:report:status:{scope}:{hash}` | String(JSON) | 状态统计数组 | 5min | 报表查询 | 任意订单写操作 |
| 10 | `po:report:time:{granularity}:{hash}` | String(JSON) | 时间统计 | 5min | 报表查询 | 任意订单写操作 |
| 11 | `po:report:summary:{scope}:{hash}` | String(JSON) | 汇总卡数据 | 5min | 报表查询 | 任意订单写操作 |
| 12 | `po:badge:todo:{roleCode}` | String | `6` | 2min | 菜单加载 | 审批任务变化 |
| 13 | `po:seq:order:{yyyyMMdd}` | String | `12` | 次日 0 点 | 新建订单 | 自然过期 |
| 14 | `po:idem:{idempotencyKey}` | String | `1` | 10s | 动作接口 | 完成/超时 |
| 15 | `po:lock:action:{orderId}:{action}` | String | `uuid` | 10s | 提交/撤回/取消 | `finally` 按 value 释放 |
| 16 | `po:lock:approve:{taskId}` | String | `uuid` | 15s | 审批动作 | 落库后释放 |
| 17 | `po:rate:{userId}:{apiCode}` | String | 计数 | 60s | 每次请求 | 窗口过期 |
| 18 | `po:timeout:zset` | ZSET | member=`orderId`，score=到期时间戳 | — | 提交审批 | 审批完成/撤回/取消 |

`{scope}` 取值：`ALL`（管理员/审批角色）或 `USER:{userId}`（采购员）；`{hash}` 为查询参数的 MD5 前 8 位。

## 3. 过期策略

| 类别 | TTL | 抖动 | 续期 |
| --- | --- | --- | --- |
| 会话 | 8h | 无 | 每次请求 `EXPIRE` 续期 |
| 基础数据/用户列表 | 30min | 0~300s 随机 | 否 |
| 字典 | 2h | 0~120s 随机 | 否 |
| 报表 | 5min | 0~60s 随机 | 否 |
| 角标 | 2min | 无 | 否 |
| 幂等 | 10s | 无 | 否 |
| 锁 | 10~15s（>业务最大耗时） | 无 | 否 |
| 限流 | 60s 固定窗口 | 无 | 否 |
| 序列 | 跨天自然切换 | 无 | 否 |

附加规则：

- 空结果写入占位 `"__NULL__"`，TTL 60s，防缓存穿透。
- 热点 Key（字典、供应商/物料列表）应用启动预热，防冷启动击穿。
- 单 Key 值大小控制在 100KB 以内；下拉列表只缓存精简字段。

## 4. 更新与失效策略

```
读路径（Cache-Aside）：
  GET key → 命中返回 → 未命中：查 MySQL → 空结果写占位 → 非空写缓存 → 返回
  Redis 异常 → 记 WARN → 直连 MySQL

写路径：
  MySQL 更新（事务）→ 事务提交后 → 删除缓存（不更新缓存）
```

| 业务动作 | 缓存动作 |
| --- | --- |
| 供应商/物料/用户 增删改 | 删对应 `po:cache:*`（单条 + 列表） |
| 分配角色 | 删 `po:session:{userId}:roles` |
| 订单 新增/编辑/删除 | 删 `po:report:*` |
| 提交/撤回/取消/审批 | 删 `po:report:*` + `po:badge:todo:*`；ZSET 增删成员 |
| 字典变更 | 删 `po:cache:dict:{type}` 并重新预热 |

批量删除用 `SCAN` 匹配前缀后 `DEL`（练习环境可简化为 `KEYS po:report:*`，生产禁用）。

## 5. 锁与幂等实现要点

```text
加锁：SET po:lock:approve:{taskId} {uuid} NX EX 15
释放：Lua 脚本 —— if redis.call('get',KEYS[1])==ARGV[1] then return redis.call('del',KEYS[1]) end
```

- 锁 value 必须是唯一 UUID，释放时校验，防止误删他人锁。
- 锁必须在 `finally` 释放，避免业务异常导致锁长期占用（TTL 是第二道保险）。
- 幂等键优先取请求头 `Idempotency-Key`，缺省时用 `userId + 接口 + 订单ID + 分钟` 生成兜底键。
- 幂等拦截返回 4001，前端提示"操作正在处理，请勿重复提交"。
- 锁获取失败返回 4003，前端提示"该单据正在被他人处理，请稍后重试"。

## 6. 降级策略

| 能力 | Redis 不可用时 | 用户可见影响 |
| --- | --- | --- |
| 会话读取 | 回源 MySQL 查用户与角色 | 无感（性能略降） |
| 会话写入 | 跳过，记 WARN | 下次请求重新解析 |
| 基础数据缓存 | 直连 MySQL | 无感 |
| 报表缓存 | 每次实时聚合 | 报表变慢 |
| 幂等 | 放行，由状态机与乐观锁兜底 | 极端情况可能重复提示 |
| 互斥锁 | 放行，由 DB 乐观锁兜底 | 并发冲突提示 4002 |
| 限流 | 跳过 | 无感 |
| 单号序列 | `MAX(order_no)+1` | 无感 |
| 超时扫描 | 跳过本轮 | 超时标记延迟 |

统一封装：所有 Redis 访问集中在 `CacheService` / `LockService` / `SessionStore`，内部捕获 `RedisConnectionFailureException` 并降级，禁止异常冒泡到 Controller。

## 7. 配置与本地部署

`application.yml`：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 3s
      lettuce:
        pool:
          enabled: true
          max-active: 16
          max-idle: 8
          min-idle: 2
  cache:
    type: redis
    redis:
      time-to-live: 1800000   # 默认 30min，按 cacheName 覆盖
      cache-null-values: true
```

`docker-compose.yml`（本地一键启动 MySQL 8 + Redis 7）：

```yaml
services:
  mysql:
    image: mysql:8.0
    ports: ["3306:3306"]
    environment:
      MYSQL_ROOT_PASSWORD: root123
      MYSQL_DATABASE: yonerp_po
      TZ: Asia/Shanghai
    command: --default-time-zone=+08:00
    volumes: [ "mysql-data:/var/lib/mysql" ]
  redis:
    image: redis:7.2
    ports: ["6379:6379"]
    command: [ "redis-server", "--appendonly", "yes" ]
    volumes: [ "redis-data:/data" ]
volumes:
  mysql-data: {}
  redis-data: {}
```

Windows 也可使用 WSL + Docker Desktop，或直接安装 Memurai / Redis for Windows，效果等价。

`RedisConfig` 要点：

- `RedisTemplate<String,Object>`：`StringRedisSerializer` 作 key，`GenericJackson2JsonRedisSerializer` 作 value，禁用 JDK 原生序列化。
- `RedisCacheManager`：按 cacheName 配置 TTL（`supplier/material/user` 30min、`dict` 2h、`report` 5min），统一 Key 前缀与空值缓存。
- 开启 `@EnableCaching`，缓存方法标注 `@Cacheable(cacheNames="supplier", key="#id", unless="#result == null")`。

## 8. 观测与自检

| 项 | 做法 |
| --- | --- |
| 命中率 | 缓存命中/未命中计数日志（WARN 级别采样），报表页可显示"数据更新时间" |
| Key 检查 | `redis-cli --scan --pattern 'po:*'`、`TTL po:cache:dict:order_status` |
| 手动清缓存 | `redis-cli --scan --pattern 'po:report:*' | xargs redis-cli DEL` |
| 锁残留检查 | `redis-cli --scan --pattern 'po:lock:*'`（应为空或极少） |
| 会话检查 | `HGETALL po:session:2`、`TTL po:session:2` |
| 降级演练 | 停止 Redis 后完整走一遍"新建 → 提交 → 审批 → 报表"，业务应可用 |

## 9. 严禁事项

1. 禁止缓存订单主表、明细、审批任务、流水实体。
2. 禁止用 `KEYS` 命令做生产批量删除（练习环境除外）。
3. 禁止使用 JDK 原生序列化。
4. 禁止在事务内执行缓存删除（应事务提交后执行，避免回滚导致缓存与 DB 不一致）。
5. 禁止把 Redis 当作消息队列或延迟队列使用（二期如需要再评估）。
6. 禁止无 TTL 的缓存 Key（`po:timeout:zset` 除外，需主动清理）。
