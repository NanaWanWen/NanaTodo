# T+ Cloud 只读同步

## 当前状态

YonErp 已接入 T+ Cloud 的只读预览能力，默认关闭，不会修改 T+ 数据，也不会修改 YonErp 本地业务表。

自建应用认证只需三个参数，不需要手工填写 `openToken`：

```text
appKey + appSecret + accountId
```

## 已确认的 T+ 接口

Token 获取（已确认）：

```text
POST https://openapi.chanjet.com/auth/v2/getToken
```

请求体：

```json
{
  "appKey": "你的AppKey",
  "appSecret": "你的AppSecret",
  "accountId": "1243337607745442"
}
```

返回：

```json
{
  "code": 200,
  "msg": "success",
  "data": {
    "openToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.xxxxxx",
    "expire": 518400
  }
}
```

程序自动读取 `data.openToken`，并按 `data.expire` 缓存，提前 5 分钟刷新。

往来单位分页查询（已确认）：

```text
POST https://openapi.chanjet.com/tplus/api/v2/partner/QueryPage
```

请求头：

```text
appKey: ${TPLUS_APP_KEY}
appSecret: ${TPLUS_APP_SECRET}
openToken: 程序自动获取
Content-Type: application/json
```

请求体：

```json
{
  "param": {
    "PageSize": 100,
    "PageIndex": 1,
    "SelectFields": "ID,Code,Name,Disabled,Ts"
  }
}
```

## 本地配置

在项目根目录创建 `.env`，不要提交到 Git：

```env
TPLUS_ENABLED=true
TPLUS_DRY_RUN=true

TPLUS_ACCOUNT_ID=1243337607745442
TPLUS_APP_KEY=你的appKey
TPLUS_APP_SECRET=你的appSecret

TPLUS_TOKEN_URL=https://openapi.chanjet.com/auth/v2/getToken
TPLUS_BASE_URL=https://openapi.chanjet.com

TPLUS_SUPPLIER_PATH=/tplus/api/v2/partner/QueryPage
TPLUS_MATERIAL_PATH=
TPLUS_PURCHASE_ORDER_PATH=
TPLUS_INVENTORY_PATH=
```

`TPLUS_APP_SECRET` 只放本地环境变量，不要写入源码、配置示例、截图或日志。

PowerShell 临时注入示例：

```powershell
$env:TPLUS_ENABLED="true"
$env:TPLUS_ACCOUNT_ID="1243337607745442"
$env:TPLUS_APP_KEY="你的appKey"
$env:TPLUS_APP_SECRET="你的appSecret"
cd C:\Users\wensh\Desktop\YonErp\backend
mvn spring-boot:run
```

## 认证链路

```text
TplusProperties
    ↓ 读取 appKey / appSecret / accountId
TplusAuthClient
    ↓ POST /auth/v2/getToken
    ↓ 缓存 data.openToken，提前 5 分钟刷新
TplusClient
    ↓ 自动携带 openToken 请求头
供应商 / 存货 / 采购订单 / 库存 查询接口
```

## 验证接口

```text
GET /api/tplus/status
```

返回字段说明：

| 字段 | 含义 |
|---|---|
| `enabled` | 是否开启 T+ 同步 |
| `dryRun` | 是否只做预览 |
| `accountId` | 云企业 ID |
| `configured` | 凭证与地址是否完整 |
| `tokenAcquired` | Token 是否获取成功 |
| `tokenError` | Token 失败原因，不含密钥 |
| `suppliers` | 供应商第一页数量摘要 |

未开启或凭证不完整时返回 `configured=false`，不会请求 T+。Token 获取失败时返回 `tokenAcquired=false`，不再发起业务查询，本地数据保持不变。

## 后续接入边界

存货、采购订单、库存查询路径必须从 T+ 开放平台对应文档确认后再填入：

```env
TPLUS_MATERIAL_PATH=
TPLUS_PURCHASE_ORDER_PATH=
TPLUS_INVENTORY_PATH=
```

确认返回字段后，才能实现本地表的字段映射、外部 ID 幂等和正式同步。当前调度任务只在 `TPLUS_ENABLED=true` 且 `TPLUS_DRY_RUN=true` 时运行预览，T+ 不可用不会影响 YonErp 启动。

## 安全边界

- 只调用查询接口，不向 T+ 写入、修改或删除数据。
- `appSecret` 和 `openToken` 不写入日志和接口返回。
- Token 仅按有效期缓存，过期自动刷新。
- 查询失败保留本地数据，不覆盖非空字段。

## 相关文件

```text
backend/src/main/java/com/yonerp/po/tplus/config/TplusProperties.java
backend/src/main/java/com/yonerp/po/tplus/client/TplusAuthClient.java
backend/src/main/java/com/yonerp/po/tplus/client/TplusClient.java
backend/src/main/java/com/yonerp/po/tplus/service/TplusSyncService.java
backend/src/main/java/com/yonerp/po/tplus/controller/TplusSyncController.java
backend/src/main/java/com/yonerp/po/tplus/job/TplusSyncJob.java
```
