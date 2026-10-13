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
