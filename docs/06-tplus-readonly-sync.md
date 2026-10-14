# T+ Cloud 只读同步

## 当前状态

YonErp 已接入 T+ Cloud 的只读预览能力，默认关闭，不会修改 T+ 数据，也不会修改 YonErp 本地业务表。

已确认的 T+ Cloud 往来单位分页查询接口：

```text
POST https://openapi.chanjet.com/tplus/api/v2/partner/QueryPage
```

请求头：

```text
appKey: ${TPLUS_APP_KEY}
appSecret: ${TPLUS_APP_SECRET}
openToken: ${TPLUS_OPEN_TOKEN}
Content-Type: application/json
```

请求体示例：

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
TPLUS_ENABLED=false
TPLUS_DRY_RUN=true
TPLUS_ENTERPRISE_ID=1243333710211666
TPLUS_ACCOUNT_ID=
TPLUS_APP_KEY=你的appKey
TPLUS_APP_SECRET=你的appSecret
TPLUS_OPEN_TOKEN=你的openToken
TPLUS_BASE_URL=https://openapi.chanjet.com
TPLUS_SUPPLIER_PATH=/tplus/api/v2/partner/QueryPage
TPLUS_MATERIAL_PATH=
TPLUS_PURCHASE_ORDER_PATH=
TPLUS_INVENTORY_PATH=
TPLUS_PAGE_SIZE=100
TPLUS_SYNC_INTERVAL=PT10M
```

`TPLUS_APP_SECRET`、`TPLUS_OPEN_TOKEN` 只放本地环境变量，不要写入源码、截图或日志。

## 验证接口

启动后端后，用已登录的 YonErp 会话访问：

```text
GET /api/tplus/status
```

未开启或凭证不完整时会返回 `configured=false`，不会请求 T+。

当配置完整并将 `TPLUS_ENABLED=true` 后，该接口会调用各已配置的查询路径，但仍只返回第一页摘要，不会写入 YonErp 数据库。

## 后续接入边界

存货、采购订单、库存查询路径必须从 T+ 开放平台对应文档确认后再填入：

```env
TPLUS_MATERIAL_PATH=
TPLUS_PURCHASE_ORDER_PATH=
TPLUS_INVENTORY_PATH=
```

确认返回字段后，才能实现本地表的字段映射、外部 ID 幂等和正式同步。当前调度任务只在 `TPLUS_ENABLED=true` 且 `TPLUS_DRY_RUN=true` 时运行预览，T+ 不可用不会影响 YonErp 启动。
