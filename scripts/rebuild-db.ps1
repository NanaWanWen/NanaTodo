# ============================================================
# YonErp 全量重建数据库（yonerp_po）
#
# 用途：schema/data 结构调整后，一键重置到干净的自洽状态。
# 流程：确认 → DROP + CREATE 数据库 → 执行 schema.sql → 执行 data.sql → 校验
# 影响：会清空全部数据（含运行时就手工创建的订单/结算单/预警/审批流水）。
# 执行后需重启后端应用（DROP 后旧连接失效）；
# 报表 Redis 缓存 5 分钟 TTL 内可能仍是旧值，属正常现象。
# ============================================================
param(
    [switch]$Yes,
    [string]$DbName = "yonerp_po",
    [string]$Password
)

$ErrorActionPreference = "Stop"

$mysql = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
if (-not (Test-Path $mysql)) {
    $mysql = (Get-Command mysql -ErrorAction SilentlyContinue).Source
}
if (-not $mysql) {
    Write-Host "未找到 mysql 客户端，请确认 MySQL 已安装并加入 PATH" -ForegroundColor Red
    exit 1
}

# 密码优先级：-Password 参数 > MYSQL_PWD 环境变量 > 交互输入（非交互场景下用前两者，避免卡住）
if (-not $Password) { $Password = $env:MYSQL_PWD }
if (-not $Password) {
    $secure = Read-Host "MySQL root 密码（回车使用 application.yml 默认值）" -AsSecureString
    if ($secure.Length -gt 0) {
        $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto(
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    } else {
        $Password = "wen911818"
    }
}

if (-not $Yes) {
    Write-Host ""
    Write-Host "即将全量重建数据库 [$DbName]：" -ForegroundColor Yellow
    Write-Host "  - 清空全部数据（含运行时就手工创建的订单/结算单/预警/审批流水）"
    Write-Host "  - 重新执行 schema.sql（建表）+ data.sql（50 笔订单全链路种子）"
    $confirm = Read-Host "确认继续？(y/N)"
    if ($confirm -notmatch '^[yY]') {
        Write-Host "已取消" -ForegroundColor DarkGray
        exit 0
    }
}

$repo = Split-Path -Parent $PSScriptRoot
$schema = Join-Path $repo "backend\src\main\resources\schema.sql"
$data = Join-Path $repo "backend\src\main\resources\data.sql"

function Invoke-Sql([string]$sql) {
    & $mysql -uroot --default-character-set=utf8mb4 $DbName -N -B -e $sql
}

# 用 MYSQL_PWD 传密码：避免 -p 明文参数，也避免 mysql 的 stderr 告警在 ErrorActionPreference=Stop 下被当成终止错误
$env:MYSQL_PWD = $Password

Write-Host "`n[1/4] 重建空库..." -ForegroundColor Cyan
& $mysql -uroot --default-character-set=utf8mb4 -e "DROP DATABASE IF EXISTS $DbName; CREATE DATABASE $DbName CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
if ($LASTEXITCODE -ne 0) { Write-Host "重建数据库失败" -ForegroundColor Red; exit 1 }

Write-Host "[2/4] 执行 schema.sql..." -ForegroundColor Cyan
& $mysql -uroot --default-character-set=utf8mb4 $DbName -e "source $schema"
if ($LASTEXITCODE -ne 0) { Write-Host "schema.sql 执行失败" -ForegroundColor Red; exit 1 }

Write-Host "[3/4] 执行 data.sql..." -ForegroundColor Cyan
& $mysql -uroot --default-character-set=utf8mb4 $DbName -e "source $data"
if ($LASTEXITCODE -ne 0) { Write-Host "data.sql 执行失败" -ForegroundColor Red; exit 1 }

Write-Host "[4/4] 数据校验..." -ForegroundColor Cyan
$checks = @(
    @{ Name = "供应商";     Sql = "SELECT COUNT(*) FROM base_supplier";          Expect = 6 },
    @{ Name = "物料";       Sql = "SELECT COUNT(*) FROM base_material";          Expect = 18 },
    @{ Name = "采购订单";   Sql = "SELECT COUNT(*) FROM po_order";               Expect = 50 },
    @{ Name = "订单明细";   Sql = "SELECT COUNT(*) FROM po_order_item";          Expect = 67 },
    @{ Name = "审批任务";   Sql = "SELECT COUNT(*) FROM po_approval_task";       Expect = 84 },
    @{ Name = "收货单";     Sql = "SELECT COUNT(*) FROM po_receipt";             Expect = 27 },
    @{ Name = "收货明细";   Sql = "SELECT COUNT(*) FROM po_receipt_item";        Expect = 35 },
    @{ Name = "发票";       Sql = "SELECT COUNT(*) FROM po_invoice";             Expect = 20 },
    @{ Name = "付款单";     Sql = "SELECT COUNT(*) FROM po_payment";             Expect = 14 },
    @{ Name = "结算单";     Sql = "SELECT COUNT(*) FROM po_ap_settlement";       Expect = 25 },
    @{ Name = "结算行明细"; Sql = "SELECT COUNT(*) FROM po_ap_settlement_item";  Expect = 38 },
    @{ Name = "暂估应付";   Sql = "SELECT COUNT(*) FROM po_accrual";             Expect = 35 },
    @{ Name = "匹配规则";   Sql = "SELECT COUNT(*) FROM po_match_rule";          Expect = 5 },
    @{ Name = "库存";       Sql = "SELECT COUNT(*) FROM po_inventory";           Expect = 18 }
)

$failed = 0
foreach ($c in $checks) {
    $actual = (Invoke-Sql $c.Sql) 2>$null
    $ok = [int]$actual -eq $c.Expect
    if (-not $ok) { $failed++ }
    $mark = if ($ok) { "OK  " } else { "FAIL" }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("  [{0}] {1,-10} 期望 {2,4} / 实际 {3}" -f $mark, $c.Name, $c.Expect, $actual) -ForegroundColor $color
}

Write-Host "`n一致性校验（均应为 0）：" -ForegroundColor Cyan
$consistency = @(
    @{ Name = "结算单头/行金额偏差";     Sql = "SELECT COUNT(*) FROM (SELECT s.id, s.receipt_amount, IFNULL(i.amt, 0) AS item_amt FROM po_ap_settlement s LEFT JOIN (SELECT settlement_id, SUM(received_amount) AS amt FROM po_ap_settlement_item GROUP BY settlement_id) i ON i.settlement_id = s.id) t WHERE ABS(t.receipt_amount - t.item_amt) > 0.01" },
    @{ Name = "结算行明细 vs 收货明细";  Sql = "SELECT COUNT(*) FROM po_ap_settlement_item si JOIN po_receipt_item ri ON ri.id = si.receipt_item_id WHERE ABS(si.received_amount - ri.amount) > 0.01" },
    @{ Name = "结算行明细 vs 订单明细";  Sql = "SELECT COUNT(*) FROM po_ap_settlement_item si JOIN po_order_item oi ON oi.id = si.order_item_id WHERE ABS(si.ordered_amount - oi.amount) > 0.01" },
    @{ Name = "结算单发票 vs 发票表";    Sql = "SELECT COUNT(*) FROM po_ap_settlement s JOIN po_invoice inv ON inv.id = s.invoice_id WHERE ABS(s.invoice_amount - inv.total_amount) > 0.01" },
    @{ Name = "发票 vs 订单收货合计";    Sql = "SELECT COUNT(*) FROM po_invoice inv WHERE ABS(inv.total_amount - (SELECT COALESCE(SUM(r.total_amount),0) FROM po_receipt r WHERE r.order_id = inv.order_id)) > 0.01 AND inv.id NOT IN (7203, 7205, 7209, 7215, 7216)" },
    @{ Name = "付款金额 vs 发票";        Sql = "SELECT COUNT(*) FROM po_payment p JOIN po_invoice inv ON inv.id = p.invoice_id WHERE ABS(p.payment_amount - inv.total_amount) > 0.01" },
    @{ Name = "暂估金额 vs 收货明细";    Sql = "SELECT COUNT(*) FROM po_accrual a JOIN po_receipt_item ri ON ri.receipt_id = a.receipt_id AND ri.material_id = a.material_id WHERE ABS(a.accrual_amount - ri.amount) > 0.01" },
    @{ Name = "非 MATCHED 订单的付款单"; Sql = "SELECT COUNT(*) FROM po_payment p JOIN po_ap_settlement s ON s.order_id = p.order_id WHERE s.match_status <> 'MATCHED'" }
)
foreach ($c in $consistency) {
    $actual = (Invoke-Sql $c.Sql) 2>$null
    $ok = [int]$actual -eq 0
    if (-not $ok) { $failed++ }
    Write-Host ("  [{0}] {1} = {2}" -f $(if ($ok) { "OK  " } else { "FAIL" }), $c.Name, $actual) -ForegroundColor $(if ($ok) { "Green" } else { "Red" })
}

Write-Host "`n结算单状态分布：" -ForegroundColor Cyan
& $mysql -uroot -p"$password" --table $DbName -e "SELECT match_status, COUNT(*) cnt FROM po_ap_settlement GROUP BY match_status;"
Write-Host "暂估状态分布：" -ForegroundColor Cyan
& $mysql -uroot -p"$password" --table $DbName -e "SELECT status, COUNT(*) cnt FROM po_accrual GROUP BY status;"

if ($failed -gt 0) {
    Write-Host "`n重建完成，但有 $failed 项校验未通过，请检查上方输出" -ForegroundColor Red
    exit 1
}

Write-Host "`n重建完成，全部校验通过。" -ForegroundColor Green
Write-Host "下一步：重启后端应用（DROP 后旧连接已失效）；启动时会重放 schema/data（幂等，无副作用）。" -ForegroundColor Yellow
