-- ============================================================
-- 采购订单管理练习项目 · 建表脚本（可重复执行）
-- 详细设计见 docs/01-database-schema.md
-- ============================================================

-- ---------------- 系统表 ----------------
CREATE TABLE IF NOT EXISTS sys_user (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
  username     VARCHAR(64)     NOT NULL COMMENT '登录名',
  real_name    VARCHAR(64)     NOT NULL COMMENT '姓名',
  dept_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '部门ID(字典 dept)',
  mobile       VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
  email        VARCHAR(64)     DEFAULT NULL COMMENT '邮箱',
  status       TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_by    BIGINT UNSIGNED DEFAULT NULL,
  create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by    BIGINT UNSIGNED DEFAULT NULL,
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted      TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS sys_role (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role_code   VARCHAR(32)     NOT NULL COMMENT 'ADMIN/BUYER/PURCHASE_MANAGER/DIRECTOR',
  role_name   VARCHAR(64)     NOT NULL COMMENT '角色名称',
  description VARCHAR(255)    DEFAULT NULL,
  sort        INT             NOT NULL DEFAULT 0,
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

CREATE TABLE IF NOT EXISTS sys_dict (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dict_type  VARCHAR(32)     NOT NULL COMMENT 'order_status/currency/unit/dept/approval_node',
  dict_code  VARCHAR(32)     NOT NULL COMMENT '字典码',
  dict_name  VARCHAR(64)     NOT NULL COMMENT '展示名',
  dict_value VARCHAR(128)    DEFAULT NULL COMMENT '扩展值(色值等)',
  sort       INT             NOT NULL DEFAULT 0,
  remark     VARCHAR(255)    DEFAULT NULL,
  deleted    TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_code (dict_type, dict_code),
  KEY idx_type (dict_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='基础字典表';

-- ---------------- 基础数据表 ----------------
CREATE TABLE IF NOT EXISTS base_supplier (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  supplier_code  VARCHAR(32)     NOT NULL COMMENT '供应商编码',
  supplier_name  VARCHAR(128)    NOT NULL COMMENT '供应商名称',
  contact_name   VARCHAR(64)     DEFAULT NULL COMMENT '联系人',
  contact_phone  VARCHAR(20)     DEFAULT NULL COMMENT '联系电话',
  address        VARCHAR(255)    DEFAULT NULL COMMENT '地址',
  status         TINYINT         NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  create_by      BIGINT UNSIGNED DEFAULT NULL,
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT UNSIGNED DEFAULT NULL,
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_supplier_code (supplier_code),
  KEY idx_name (supplier_name),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='供应商表';

CREATE TABLE IF NOT EXISTS base_material (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  material_code  VARCHAR(32)     NOT NULL COMMENT '物料编码',
  material_name  VARCHAR(128)    NOT NULL COMMENT '物料名称',
  spec           VARCHAR(128)    DEFAULT NULL COMMENT '规格型号',
  unit           VARCHAR(16)     NOT NULL COMMENT '计量单位',
  category       VARCHAR(64)     DEFAULT NULL COMMENT '物料分类',
  ref_price      DECIMAL(18,4)   DEFAULT NULL COMMENT '参考单价(不含税)',
  tax_rate       DECIMAL(5,2)    NOT NULL DEFAULT 13.00 COMMENT '默认税率(%)',
  status         TINYINT         NOT NULL DEFAULT 1,
  create_by      BIGINT UNSIGNED DEFAULT NULL,
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT UNSIGNED DEFAULT NULL,
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_code (material_code),
  KEY idx_name (material_name),
  KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='物料表';

-- ---------------- 业务表 ----------------
CREATE TABLE IF NOT EXISTS po_order (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_no      VARCHAR(32)     NOT NULL COMMENT '订单编号 POyyyyMMddNNNN',
  title         VARCHAR(128)    NOT NULL COMMENT '订单标题',
  supplier_id   BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  buyer_id      BIGINT UNSIGNED NOT NULL COMMENT '采购员ID',
  dept_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '采购部门',
  currency      VARCHAR(8)      NOT NULL DEFAULT 'CNY' COMMENT '币种',
  order_date    DATE            NOT NULL COMMENT '订单日期',
  expect_date   DATE            DEFAULT NULL COMMENT '期望到货日期',
  total_qty     DECIMAL(18,3)   NOT NULL DEFAULT 0 COMMENT '数量合计',
  total_amount  DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '含税金额合计',
  status        VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/APPROVING/APPROVED/REJECTED/CANCELED',
  current_node  INT             NOT NULL DEFAULT 0 COMMENT '当前审批节点 1经理 2总监 0无',
  submit_time   DATETIME        DEFAULT NULL COMMENT '最近提交时间',
  finish_time   DATETIME        DEFAULT NULL COMMENT '审批完成时间',
  version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
  remark        VARCHAR(500)    DEFAULT NULL COMMENT '备注',
  create_by     BIGINT UNSIGNED DEFAULT NULL,
  create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT UNSIGNED DEFAULT NULL,
  update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_no (order_no),
  KEY idx_status (status),
  KEY idx_order_date (order_date),
  KEY idx_supplier (supplier_id),
  KEY idx_buyer (buyer_id),
  KEY idx_status_date (status, order_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单主表';

CREATE TABLE IF NOT EXISTS po_order_item (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id       BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  line_no        INT             NOT NULL COMMENT '行号 10/20/30',
  material_id    BIGINT UNSIGNED NOT NULL COMMENT '物料ID',
  material_name  VARCHAR(128)    NOT NULL COMMENT '物料名称(冗余快照)',
  spec           VARCHAR(128)    DEFAULT NULL COMMENT '规格(冗余)',
  unit           VARCHAR(16)     NOT NULL COMMENT '单位(冗余)',
  qty            DECIMAL(18,3)   NOT NULL COMMENT '数量',
  price          DECIMAL(18,4)   NOT NULL COMMENT '不含税单价',
  tax_rate       DECIMAL(5,2)    NOT NULL DEFAULT 13.00 COMMENT '税率(%)',
  amount         DECIMAL(18,2)   NOT NULL COMMENT '含税金额',
  remark         VARCHAR(255)    DEFAULT NULL,
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_line (order_id, line_no),
  KEY idx_order (order_id),
  KEY idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';

CREATE TABLE IF NOT EXISTS po_approval_task (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id     BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  node_seq     INT             NOT NULL COMMENT '节点序号 1/2',
  node_role    VARCHAR(32)     NOT NULL COMMENT '审批角色',
  node_name    VARCHAR(64)     NOT NULL COMMENT '节点名称',
  approver_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '审批人',
  status       VARCHAR(20)     NOT NULL DEFAULT 'WAITING' COMMENT 'WAITING/PENDING/APPROVED/REJECTED/INVALID',
  approve_time DATETIME        DEFAULT NULL COMMENT '审批时间',
  opinion      VARCHAR(500)    DEFAULT NULL COMMENT '审批意见',
  version      INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
  create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_node (order_id, node_seq),
  KEY idx_order (order_id),
  KEY idx_status_role (status, node_role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批任务表';

CREATE TABLE IF NOT EXISTS po_approval_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id     BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  node_seq     INT             DEFAULT NULL COMMENT '节点序号',
  node_role    VARCHAR(32)     DEFAULT NULL COMMENT '节点角色',
  operator_id  BIGINT UNSIGNED NOT NULL COMMENT '操作人',
  action       VARCHAR(32)     NOT NULL COMMENT 'SUBMIT/APPROVE/REJECT/WITHDRAW/RESUBMIT/CANCEL/TIMEOUT_REMIND',
  from_status  VARCHAR(20)     DEFAULT NULL COMMENT '变更前状态',
  to_status    VARCHAR(20)     DEFAULT NULL COMMENT '变更后状态',
  opinion      VARCHAR(500)    DEFAULT NULL COMMENT '意见',
  operate_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_operator (operator_id),
  KEY idx_operate_time (operate_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审批流水表';

-- ---------------- 采购 P2P 全流程业务表 ----------------
-- 6 张新表统一规范：主键 id、审计字段、逻辑删除、金额 DECIMAL(18,2)、状态 VARCHAR(20)

-- 收货单主表
CREATE TABLE IF NOT EXISTS po_receipt (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  receipt_no    VARCHAR(32)     NOT NULL COMMENT '收货单编号 RCPTyyyyMMddNNNN',
  order_id      BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  supplier_id   BIGINT UNSIGNED NOT NULL COMMENT '供应商ID(冗余)',
  receipt_date  DATE            NOT NULL COMMENT '收货日期',
  total_qty     DECIMAL(18,3)   NOT NULL DEFAULT 0 COMMENT '实收数量合计',
  total_amount  DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '含税金额合计',
  status        VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/CONFIRMED/CANCELED',
  remark        VARCHAR(500)    DEFAULT NULL COMMENT '备注',
  create_by     BIGINT UNSIGNED DEFAULT NULL,
  create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by     BIGINT UNSIGNED DEFAULT NULL,
  update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_receipt_no (receipt_no),
  KEY idx_order (order_id),
  KEY idx_supplier (supplier_id),
  KEY idx_status (status),
  KEY idx_receipt_date (receipt_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购收货单主表';

-- 收货单明细
CREATE TABLE IF NOT EXISTS po_receipt_item (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  receipt_id     BIGINT UNSIGNED NOT NULL COMMENT '收货单ID',
  order_item_id  BIGINT UNSIGNED NOT NULL COMMENT '订单明细ID',
  material_id    BIGINT UNSIGNED NOT NULL COMMENT '物料ID',
  material_name  VARCHAR(128)    NOT NULL COMMENT '物料名称(冗余快照)',
  spec           VARCHAR(128)    DEFAULT NULL COMMENT '规格(冗余)',
  unit           VARCHAR(16)     NOT NULL COMMENT '单位(冗余)',
  ordered_qty    DECIMAL(18,3)   NOT NULL COMMENT '订购数量',
  received_qty   DECIMAL(18,3)   NOT NULL COMMENT '实收数量',
  unit_price     DECIMAL(18,4)   NOT NULL COMMENT '不含税单价(冗余快照)',
  tax_rate       DECIMAL(5,2)    NOT NULL DEFAULT 13.00 COMMENT '税率(%)',
  tax_amount     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '税额',
  amount         DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '含税金额',
  remark         VARCHAR(255)    DEFAULT NULL,
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_receipt (receipt_id),
  KEY idx_order_item (order_item_id),
  KEY idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购收货单明细表';

-- 采购发票
CREATE TABLE IF NOT EXISTS po_invoice (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  invoice_no     VARCHAR(32)     NOT NULL COMMENT '发票编号 INVyyyyMMddNNNN',
  receipt_id     BIGINT UNSIGNED NOT NULL COMMENT '收货单ID',
  order_id       BIGINT UNSIGNED NOT NULL COMMENT '订单ID(冗余)',
  supplier_id    BIGINT UNSIGNED NOT NULL COMMENT '供应商ID(冗余)',
  invoice_date   DATE            NOT NULL COMMENT '开票日期',
  invoice_amount DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '发票金额(不含税)',
  tax_amount     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '税额',
  total_amount   DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '价税合计',
  invoice_type  VARCHAR(8)      NOT NULL DEFAULT 'BLUE' COMMENT 'BLUE 蓝字(正常)/RED 红字(红冲)',
  related_invoice_id BIGINT UNSIGNED DEFAULT NULL COMMENT '红蓝关联：红字发票指向被红冲的原发票ID',
  related_invoice_no VARCHAR(32) DEFAULT NULL COMMENT '被红冲原发票号',
  invoice_status VARCHAR(20)     NOT NULL DEFAULT 'UNCHECKED' COMMENT 'UNCHECKED/CHECKED/PAID/RED_FLUSHED',
  remark         VARCHAR(500)    DEFAULT NULL COMMENT '备注',
  create_by      BIGINT UNSIGNED DEFAULT NULL,
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT UNSIGNED DEFAULT NULL,
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_invoice_no (invoice_no),
  KEY idx_receipt (receipt_id),
  KEY idx_order (order_id),
  KEY idx_supplier (supplier_id),
  KEY idx_status (invoice_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购发票表';

-- 付款单
CREATE TABLE IF NOT EXISTS po_payment (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  payment_no     VARCHAR(32)     NOT NULL COMMENT '付款单编号 PAYyyyyMMddNNNN',
  invoice_id     BIGINT UNSIGNED NOT NULL COMMENT '发票ID',
  order_id       BIGINT UNSIGNED NOT NULL COMMENT '订单ID(冗余)',
  supplier_id    BIGINT UNSIGNED NOT NULL COMMENT '供应商ID(冗余)',
  payment_date   DATE            NOT NULL COMMENT '付款日期',
  payment_amount DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '付款金额',
  payment_method VARCHAR(20)     NOT NULL DEFAULT 'BANK_TRANSFER' COMMENT 'BANK_TRANSFER/ALIPAY/WECHAT/CASH',
  status         VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/PAID/REJECTED',
  remark         VARCHAR(500)    DEFAULT NULL COMMENT '备注',
  create_by      BIGINT UNSIGNED DEFAULT NULL,
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      BIGINT UNSIGNED DEFAULT NULL,
  update_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_payment_no (payment_no),
  KEY idx_invoice (invoice_id),
  KEY idx_order (order_id),
  KEY idx_supplier (supplier_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='付款单表';

-- 应付对账/三单匹配
CREATE TABLE IF NOT EXISTS po_ap_settlement (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  match_no      VARCHAR(32)     NOT NULL COMMENT '结算单号 MATCHyyyyMMddNNNN',
  order_id      BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  receipt_id    BIGINT UNSIGNED COMMENT '收货单ID（可为空，支持一票多单）',
  invoice_id    BIGINT UNSIGNED COMMENT '发票ID（可为空，支持分批收货）',
  supplier_id   BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  order_amount  DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '订单金额',
  receipt_amount DECIMAL(18,2)  NOT NULL DEFAULT 0 COMMENT '收货金额',
  invoice_amount DECIMAL(18,2)  NOT NULL DEFAULT 0 COMMENT '发票金额',
  diff_amount   DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '差异金额(发票-收货)',
  qty_diff      DECIMAL(18,3)   NOT NULL DEFAULT 0 COMMENT '数量差异',
  price_diff    DECIMAL(18,4)   NOT NULL DEFAULT 0 COMMENT '单价差异',
  tax_diff      DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '税额差异',
  diff_type     VARCHAR(20)     DEFAULT NULL COMMENT '差异类型：QTY/PRICE/TAX/EXCHANGE',
  match_status  VARCHAR(20)     NOT NULL DEFAULT 'UNMATCHED' COMMENT 'MATCHED/PARTIAL_QTY/PARTIAL_AMOUNT/HOLD/UNMATCHED/CLOSED/REVERSED',
  match_type    VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT 'AUTO/MANUAL/FORCE',
  match_rule_code VARCHAR(32)   DEFAULT NULL COMMENT '使用的匹配规则编码',
  match_batch   VARCHAR(32)     DEFAULT NULL COMMENT '匹配批次号',
  currency      VARCHAR(10)     NOT NULL DEFAULT 'CNY' COMMENT '币种',
  tolerance     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '容差金额',
  matched_by    BIGINT UNSIGNED DEFAULT NULL COMMENT '匹配人ID',
  matched_time  DATETIME        DEFAULT NULL COMMENT '匹配时间',
  hold_reason   VARCHAR(500)    DEFAULT NULL COMMENT '挂起原因',
  resolution    VARCHAR(200)    DEFAULT NULL COMMENT '处理方式：补收/退票/调价/损耗放行/审批强制',
  remark        VARCHAR(500)    DEFAULT NULL COMMENT '备注',
  version       INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_match_no (match_no),
  KEY idx_order (order_id),
  KEY idx_supplier (supplier_id),
  KEY idx_invoice (invoice_id),
  KEY idx_status (match_status),
  KEY idx_matched_time (matched_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应付对账/三单匹配表（用友式）';

-- 结算单行级明细
CREATE TABLE IF NOT EXISTS po_ap_settlement_item (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  settlement_id   BIGINT UNSIGNED NOT NULL COMMENT '结算单ID',
  order_item_id   BIGINT UNSIGNED DEFAULT NULL COMMENT '订单明细ID',
  receipt_item_id BIGINT UNSIGNED DEFAULT NULL COMMENT '收货明细ID',
  invoice_item_id BIGINT UNSIGNED DEFAULT NULL COMMENT '发票明细ID',
  material_id     BIGINT UNSIGNED NOT NULL COMMENT '物料ID',
  material_code   VARCHAR(32)     NOT NULL COMMENT '物料编码',
  material_name   VARCHAR(128)    NOT NULL COMMENT '物料名称',
  ordered_qty     DECIMAL(18,3)   DEFAULT 0 COMMENT '订购数量',
  received_qty    DECIMAL(18,3)   DEFAULT 0 COMMENT '实收数量',
  invoiced_qty    DECIMAL(18,3)   DEFAULT 0 COMMENT '开票数量',
  qty_diff        DECIMAL(18,3)   DEFAULT 0 COMMENT '数量差异',
  ordered_price   DECIMAL(18,4)   DEFAULT 0 COMMENT '订购单价',
  received_price  DECIMAL(18,4)   DEFAULT 0 COMMENT '收货单价',
  invoiced_price  DECIMAL(18,4)   DEFAULT 0 COMMENT '开票单价',
  price_diff      DECIMAL(18,4)   DEFAULT 0 COMMENT '单价差异',
  ordered_amount  DECIMAL(18,2)   DEFAULT 0 COMMENT '订购金额',
  received_amount DECIMAL(18,2)   DEFAULT 0 COMMENT '收货金额',
  invoiced_amount DECIMAL(18,2)   DEFAULT 0 COMMENT '开票金额',
  amount_diff     DECIMAL(18,2)   DEFAULT 0 COMMENT '金额差异',
  tax_diff        DECIMAL(18,2)   DEFAULT 0 COMMENT '税额差异',
  diff_type       VARCHAR(20)     DEFAULT NULL COMMENT 'QTY/PRICE/TAX/EXCHANGE',
  match_status    VARCHAR(20)     NOT NULL DEFAULT 'UNMATCHED' COMMENT '行匹配状态',
  create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_settlement (settlement_id),
  KEY idx_material (material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算单行级明细';

-- 匹配规则配置
CREATE TABLE IF NOT EXISTS po_match_rule (
  id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rule_code             VARCHAR(32)     NOT NULL COMMENT '规则编码',
  rule_name             VARCHAR(128)    NOT NULL COMMENT '规则名称',
  scope_type            VARCHAR(20)     NOT NULL DEFAULT 'GLOBAL' COMMENT 'GLOBAL/SUPPLIER/MATERIAL_CATEGORY/CURRENCY',
  scope_value           VARCHAR(128)    DEFAULT NULL COMMENT '适用范围值（供应商ID/物料分类ID/币种）',
  qty_tolerance         DECIMAL(18,3)   DEFAULT 0 COMMENT '数量容差',
  qty_tolerance_unit    VARCHAR(20)     DEFAULT 'PERCENT' COMMENT 'PERCENT/ABSOLUTE',
  amount_tolerance      DECIMAL(18,2)   DEFAULT 0 COMMENT '金额容差',
  amount_tolerance_unit VARCHAR(20)     DEFAULT 'PERCENT' COMMENT 'PERCENT/ABSOLUTE',
  auto_settle           TINYINT         NOT NULL DEFAULT 0 COMMENT '是否自动结算：0否/1是',
  accrual_mode          VARCHAR(20)     DEFAULT 'SINGLE' COMMENT '暂估回冲方式：SINGLE(单到)/MONTHLY(月初)',
  allow_force_match     TINYINT         NOT NULL DEFAULT 0 COMMENT '是否允许强制匹配',
  approval_role         VARCHAR(32)     DEFAULT NULL COMMENT '强制匹配审批角色',
  enabled               TINYINT         NOT NULL DEFAULT 1 COMMENT '启用状态',
  create_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time           DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted               TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_code (rule_code),
  KEY idx_scope (scope_type, scope_value)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匹配规则配置';

-- 暂估应付
CREATE TABLE IF NOT EXISTS po_accrual (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  accrual_no          VARCHAR(32)     NOT NULL COMMENT '暂估单号 ACCyyyyMMddNNNN',
  receipt_id          BIGINT UNSIGNED NOT NULL COMMENT '收货单ID',
  order_id            BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
  supplier_id         BIGINT UNSIGNED NOT NULL COMMENT '供应商ID',
  material_id         BIGINT UNSIGNED NOT NULL COMMENT '物料ID',
  accrual_qty         DECIMAL(18,3)   NOT NULL COMMENT '暂估数量',
  accrual_price       DECIMAL(18,4)   NOT NULL COMMENT '暂估单价',
  accrual_amount      DECIMAL(18,2)   NOT NULL COMMENT '暂估金额',
  accrual_date        DATE            NOT NULL COMMENT '暂估日期',
  accrual_period      VARCHAR(7)      NOT NULL COMMENT '暂估期间（yyyy-MM）',
  status              VARCHAR(20)     NOT NULL DEFAULT 'ACCRUED' COMMENT 'ACCRUED/REVERSED',
  reverse_invoice_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '回冲发票ID',
  reverse_date        DATE            DEFAULT NULL COMMENT '回冲日期',
  reverse_amount      DECIMAL(18,2)   DEFAULT NULL COMMENT '回冲金额',
  diff_amount         DECIMAL(18,2)   DEFAULT 0 COMMENT '暂估与实际差额',
  accrual_mode        VARCHAR(20)     NOT NULL DEFAULT 'SINGLE' COMMENT 'SINGLE/MONTHLY',
  remark              VARCHAR(500)    DEFAULT NULL,
  create_by           BIGINT UNSIGNED DEFAULT NULL,
  create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by           BIGINT UNSIGNED DEFAULT NULL,
  update_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted             TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_accrual_no (accrual_no),
  KEY idx_receipt (receipt_id),
  KEY idx_supplier (supplier_id),
  KEY idx_period (accrual_period),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='暂估应付';

-- 库存
CREATE TABLE IF NOT EXISTS po_inventory (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  material_id     BIGINT UNSIGNED NOT NULL COMMENT '物料ID',
  material_code   VARCHAR(32)     NOT NULL COMMENT '物料编码(冗余)',
  material_name   VARCHAR(128)    NOT NULL COMMENT '物料名称(冗余)',
  warehouse       VARCHAR(64)     NOT NULL DEFAULT '主仓库' COMMENT '仓库',
  quantity        DECIMAL(18,3)   NOT NULL DEFAULT 0 COMMENT '当前库存',
  safety_stock    DECIMAL(18,3)   NOT NULL DEFAULT 0 COMMENT '安全库存',
  last_update_time DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁',
  create_by       BIGINT UNSIGNED DEFAULT NULL,
  create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by       BIGINT UNSIGNED DEFAULT NULL,
  update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_material_warehouse (material_id, warehouse),
  KEY idx_material (material_id),
  KEY idx_quantity (quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存表';

-- AI 预警记录（持久化，重启不丢、可追溯历史）
CREATE TABLE IF NOT EXISTS agent_alert (
  alert_id    VARCHAR(64)     NOT NULL COMMENT '预警ID(UUID)',
  alert_type  VARCHAR(32)     NOT NULL COMMENT 'OVERDUE_ORDER/LOW_STOCK/PRICE_ABNORMAL/SYSTEM',
  biz_domain  VARCHAR(16)     NOT NULL DEFAULT 'PURCHASE' COMMENT '业务域 PURCHASE/INVENTORY/FINANCE',
  rule_code   VARCHAR(64)     DEFAULT NULL COMMENT '命中规则编码',
  rule_id     BIGINT UNSIGNED DEFAULT NULL COMMENT '命中规则ID',
  level       VARCHAR(16)     NOT NULL DEFAULT 'INFO' COMMENT 'INFO/WARNING/CRITICAL',
  title       VARCHAR(255)    NOT NULL COMMENT '预警标题',
  content     TEXT            COMMENT '预警内容(含AI建议)',
  ref_id      BIGINT UNSIGNED DEFAULT NULL COMMENT '业务引用ID',
  ref_no      VARCHAR(64)     DEFAULT NULL COMMENT '业务引用编号',
  ref_type    VARCHAR(32)     DEFAULT NULL COMMENT '引用单据类型 ORDER/RECEIPT/INVOICE/PAYMENT/MATERIAL',
  action_path VARCHAR(255)    DEFAULT NULL COMMENT '前端跳转路径',
  handled     TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已处理 0未处理 1已处理',
  ignored     TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已忽略(不再显示) 0显示 1忽略',
  read_flag   TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已读 0未读 1已读',
  assignee_id BIGINT UNSIGNED DEFAULT NULL COMMENT '当前责任人用户ID',
  assignee    VARCHAR(64)     DEFAULT NULL COMMENT '当前责任人',
  handle_by   VARCHAR(64)     DEFAULT NULL COMMENT '处理人',
  handle_time DATETIME        DEFAULT NULL COMMENT '处理时间',
  handle_note VARCHAR(500)    DEFAULT NULL COMMENT '处理意见',
  notified    TINYINT         NOT NULL DEFAULT 0 COMMENT '是否已推送通知 0否 1是',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '生成时间',
  PRIMARY KEY (alert_id),
  KEY idx_create_time (create_time),
  KEY idx_handled (handled),
  KEY idx_type_ref (alert_type, ref_id),
  KEY idx_assignee (assignee_id),
  KEY idx_rule_code (rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 预警记录表';

-- 预警规则配置表（业务角色自助维护阈值/级别/启停）
CREATE TABLE IF NOT EXISTS agent_alert_rule (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  rule_code      VARCHAR(64)     NOT NULL COMMENT '规则编码，与评估器绑定',
  rule_name      VARCHAR(128)    NOT NULL COMMENT '规则名称',
  biz_domain     VARCHAR(16)     NOT NULL COMMENT '业务域 PURCHASE/INVENTORY/FINANCE',
  alert_type     VARCHAR(32)     NOT NULL COMMENT '预警类型（与 agent_alert.alert_type 对应）',
  level          VARCHAR(16)     NOT NULL DEFAULT 'WARNING' COMMENT '预警级别 INFO/WARNING/CRITICAL',
  threshold_unit VARCHAR(32)     DEFAULT NULL COMMENT '阈值单位 DAY/PERCENT/AMOUNT/QTY',
  threshold      DECIMAL(18,4)   DEFAULT NULL COMMENT '阈值',
  extra_config   VARCHAR(1000)   DEFAULT NULL COMMENT '扩展参数(JSON)',
  enabled        TINYINT         NOT NULL DEFAULT 1 COMMENT '是否启用 0停用 1启用',
  scan_cron      VARCHAR(64)     DEFAULT NULL COMMENT '扫描周期(留空用全局周期)',
  notify_channel VARCHAR(128)    NOT NULL DEFAULT 'INAPP' COMMENT '通知渠道，逗号分隔 INAPP/EMAIL/SSE',
  notify_roles   VARCHAR(255)    DEFAULT NULL COMMENT '通知角色，逗号分隔',
  owner_role     VARCHAR(64)     DEFAULT NULL COMMENT '规则归属角色(自助配置)',
  remark         VARCHAR(255)    DEFAULT NULL COMMENT '备注',
  create_by      VARCHAR(64)     DEFAULT NULL,
  create_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_by      VARCHAR(64)     DEFAULT NULL,
  update_time    DATETIME        DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule_code (rule_code),
  KEY idx_domain_enabled (biz_domain, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预警规则配置表';

-- 预警处理流水（指派/转办/处理/忽略/恢复，完整审计）
CREATE TABLE IF NOT EXISTS agent_alert_action (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  alert_id    VARCHAR(64)     NOT NULL COMMENT '预警ID',
  action      VARCHAR(32)     NOT NULL COMMENT 'CREATE/READ/ASSIGN/TRANSFER/HANDLE/IGNORE/RESTORE/ESCALATE',
  from_user   VARCHAR(64)     DEFAULT NULL COMMENT '操作前责任人',
  to_user     VARCHAR(64)     DEFAULT NULL COMMENT '操作后责任人',
  operator    VARCHAR(64)     DEFAULT NULL COMMENT '操作人',
  operator_id BIGINT UNSIGNED DEFAULT NULL COMMENT '操作人ID',
  note        VARCHAR(500)    DEFAULT NULL COMMENT '处理意见/备注',
  create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_alert_id (alert_id),
  KEY idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预警处理流水表';

-- ---------------- Excel 导入框架 ----------------
-- 导入批次表（一次上传对应一条批次记录）
CREATE TABLE IF NOT EXISTS sys_import_batch (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  batch_no         VARCHAR(32)     NOT NULL COMMENT '批次号 IMPyyyyMMddNNNN',
  biz_type         VARCHAR(32)     NOT NULL COMMENT '业务类型 ORDER/RECEIPT/INVOICE/PAYMENT',
  template_version VARCHAR(16)     NOT NULL DEFAULT 'V1' COMMENT '模板版本',
  file_name        VARCHAR(255)    DEFAULT NULL COMMENT '原始文件名',
  file_fingerprint VARCHAR(64)     DEFAULT NULL COMMENT '文件 SHA-256 指纹',
  status           VARCHAR(20)     NOT NULL DEFAULT 'UPLOADED' COMMENT 'UPLOADED/PARSING/VALIDATED/CONFIRMED/FAILED/CANCELED',
  total_rows       INT             NOT NULL DEFAULT 0 COMMENT '总行数',
  valid_rows       INT             NOT NULL DEFAULT 0 COMMENT '有效行数',
  warning_rows     INT             NOT NULL DEFAULT 0 COMMENT '警告行数',
  error_rows       INT             NOT NULL DEFAULT 0 COMMENT '错误行数',
  summary          VARCHAR(500)    DEFAULT NULL COMMENT '导入摘要',
  create_by        BIGINT UNSIGNED DEFAULT NULL,
  create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  confirm_by       BIGINT UNSIGNED DEFAULT NULL,
  confirm_time     DATETIME        DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_batch_no (batch_no),
  KEY idx_fingerprint (file_fingerprint),
  KEY idx_biz_status (biz_type, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Excel 导入批次表';

-- 导入行暂存表（校验通过前不写入业务表）
CREATE TABLE IF NOT EXISTS sys_import_row (
  id        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  batch_id  BIGINT UNSIGNED NOT NULL COMMENT '批次ID',
  row_index INT             NOT NULL COMMENT 'Excel 行号（从数据行开始）',
  row_data  JSON            COMMENT '原始行数据 JSON',
  status    VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/VALID/WARNING/ERROR',
  biz_id    BIGINT UNSIGNED DEFAULT NULL COMMENT '业务ID（确认成功后写入）',
  PRIMARY KEY (id),
  KEY idx_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Excel 导入行暂存表';

-- 导入错误记录表
CREATE TABLE IF NOT EXISTS sys_import_error (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  batch_id      BIGINT UNSIGNED NOT NULL COMMENT '批次ID',
  row_index     INT             DEFAULT NULL COMMENT 'Excel 行号',
  field_name    VARCHAR(64)     DEFAULT NULL COMMENT '字段名',
  raw_value     VARCHAR(500)    DEFAULT NULL COMMENT 'Excel 原始值',
  error_level   VARCHAR(20)     NOT NULL COMMENT 'ERROR/WARNING',
  error_code    VARCHAR(32)     DEFAULT NULL COMMENT '错误码',
  error_message VARCHAR(500)    NOT NULL COMMENT '错误描述',
  suggestion    VARCHAR(500)    DEFAULT NULL COMMENT '修复建议',
  PRIMARY KEY (id),
  KEY idx_batch (batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Excel 导入错误记录表';
