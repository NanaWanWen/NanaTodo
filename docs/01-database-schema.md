# 数据库详细设计（MySQL 8）

> 配套文档：`00-technical-solution.md` 第 2 章 ｜ 本文输出可直接执行的 DDL 与初始化数据

## 1. 建库与规范

```sql
CREATE DATABASE IF NOT EXISTS yonerp_po
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_general_ci;
USE yonerp_po;
```

命名与字段规范：

| 项 | 规范 |
| --- | --- |
| 表前缀 | 系统表 `sys_`、基础数据 `base_`、业务表 `po_` |
| 主键 | `id BIGINT UNSIGNED AUTO_INCREMENT`（统一命名 `id`） |
| 金额 | `DECIMAL(18,2)`；单价 `DECIMAL(18,4)`；数量 `DECIMAL(18,3)`；税率 `DECIMAL(5,2)` |
| 状态 | `VARCHAR(20)` 存英文码，中文展示由字典表映射 |
| 布尔/枚举 | `TINYINT`（0/1）或 `VARCHAR` 码值 |
| 逻辑删除 | 全表 `deleted TINYINT DEFAULT 0` |
| 审计字段 | `create_by BIGINT`、`create_time DATETIME`、`update_by BIGINT`、`update_time DATETIME` |
| 时间默认值 | `DEFAULT CURRENT_TIMESTAMP` / `ON UPDATE CURRENT_TIMESTAMP` |

## 2. 完整 DDL

### 2.1 系统表

```sql
-- 用户表
CREATE TABLE sys_user (
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

-- 角色表
CREATE TABLE sys_role (
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

-- 用户角色关联
CREATE TABLE sys_user_role (
  id      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  role_id BIGINT UNSIGNED NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_role (user_id, role_id),
  KEY idx_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- 基础字典表
CREATE TABLE sys_dict (
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
```

### 2.2 基础数据表

```sql
-- 供应商
CREATE TABLE base_supplier (
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

-- 物料
CREATE TABLE base_material (
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
```

### 2.3 业务表

```sql
-- 采购订单主表
CREATE TABLE po_order (
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

-- 采购订单明细
CREATE TABLE po_order_item (
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

-- 审批任务
CREATE TABLE po_approval_task (
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

-- 审批流水
CREATE TABLE po_approval_log (
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
```

## 3. 表关系说明

| 关系 | 主表 → 子表 | 外键字段 | 级联策略 |
| --- | --- | --- | --- |
| 一对多 | `po_order` → `po_order_item` | `order_id` | 逻辑删除，应用级维护，不加物理外键约束 |
| 一对多 | `po_order` → `po_approval_task` | `order_id` | 同上 |
| 一对多 | `po_order` → `po_approval_log` | `order_id` | 同上 |
| 多对一 | `po_order` → `base_supplier` | `supplier_id` | 供应商停用不影响历史单据 |
| 多对一 | `po_order` → `sys_user` | `buyer_id` | 用户不物理删除 |
| 多对一 | `po_order_item` → `base_material` | `material_id` | 明细冗余物料名称/规格/单位 |
| 多对多 | `sys_user` ↔ `sys_role` | `sys_user_role` | 联合唯一 |

> 设计取舍：**不使用数据库物理外键**，改用应用级校验 + 索引。原因：企业 ERP 中物理外键会阻碍数据归档、批量导入与分库演进；YonBIP 等平台同样以逻辑关联为主。

冗余字段说明：明细表冗余 `material_name/spec/unit`，保证历史单据不因物料改名而变化；单价与税率以明细为准，物料的 `ref_price` 仅作下单时默认值。

## 4. 索引设计说明

| 表 | 索引 | 目的 |
| --- | --- | --- |
| `po_order` | `idx_status_date(status, order_date)` | 列表按状态 + 日期筛选（最常用组合） |
| `po_order` | `idx_buyer(buyer_id)` | 采购员"仅看本人"数据权限过滤 |
| `po_order` | `idx_supplier(supplier_id)` | 按供应商筛选与关联查询 |
| `po_order_item` | `uk_order_line(order_id, line_no)` | 行号唯一 + 明细按订单加载 |
| `po_approval_task` | `uk_order_node(order_id, node_seq)` | 防止重复生成节点 |
| `po_approval_task` | `idx_status_role(status, node_role)` | 待办列表"我的待审"查询 |
| `po_approval_log` | `idx_order(order_id)` | 详情页审批时间线 |

## 5. 初始化数据

```sql
-- 角色
INSERT INTO sys_role (id, role_code, role_name, description, sort) VALUES
 (1, 'ADMIN',             '管理员',   '系统管理与基础数据维护', 1),
 (2, 'BUYER',             '采购员',   '创建与维护采购订单',     2),
 (3, 'PURCHASE_MANAGER',  '采购经理', '一级审批',               3),
 (4, 'DIRECTOR',          '总监',     '二级审批',               4);

-- 用户（简化登录：无密码，选择即登录）
INSERT INTO sys_user (id, username, real_name, dept_id, status) VALUES
 (1, 'admin',    '系统管理员', 1, 1),
 (2, 'buyer01',  '张采购',     2, 1),
 (3, 'buyer02',  '李采购',     2, 1),
 (4, 'manager1', '王经理',     3, 1),
 (5, 'director1','赵总监',     4, 1);

INSERT INTO sys_user_role (user_id, role_id) VALUES
 (1,1),(2,2),(3,2),(4,3),(5,4);

-- 字典
INSERT INTO sys_dict (dict_type, dict_code, dict_name, dict_value, sort) VALUES
 ('order_status','DRAFT',    '草稿',   '#8C8C8C',1),
 ('order_status','APPROVING','审批中', '#1677FF',2),
 ('order_status','APPROVED', '已审批', '#52C41A',3),
 ('order_status','REJECTED', '已驳回', '#FF4D4F',4),
 ('order_status','CANCELED', '已取消', '#BFBFBF',5),
 ('approval_node','1','采购经理审批','PURCHASE_MANAGER',1),
 ('approval_node','2','总监审批',    'DIRECTOR',2),
 ('currency','CNY','人民币','¥',1),
 ('currency','USD','美元',  '$',2),
 ('unit','个','个','',1),('unit','台','台','',2),
 ('unit','千克','千克','',3),('unit','米','米','',4),
 ('dept','1','信息部','',1),('dept','2','采购部','',2),
 ('dept','3','采购管理部','',3),('dept','4','总经办','',4);

-- 供应商
INSERT INTO base_supplier (id, supplier_code, supplier_name, contact_name, contact_phone, address) VALUES
 (1001,'SUP001','晨光办公用品有限公司','周敏','13800000001','上海市闵行区申长路 100 号'),
 (1002,'SUP002','联想（上海）信息技术','吴强','13800000002','上海市浦东新区张江路 88 号'),
 (1003,'SUP003','苏州恒力精密机械','孙丽','13800000003','苏州市工业园区星湖街 328 号'),
 (1004,'SUP004','杭州云栖电子元器件','郑浩','13800000004','杭州市滨江区江陵路 66 号');

-- 物料
INSERT INTO base_material (id, material_code, material_name, spec, unit, category, ref_price, tax_rate) VALUES
 (2001,'MAT001','A4 复印纸','70g/500 张/包','个','办公耗材',25.0000,13.00),
 (2002,'MAT002','中性笔','0.5mm 黑色/12 支','个','办公耗材',18.0000,13.00),
 (2003,'MAT003','笔记本电脑','ThinkPad T14 i7/16G/512G','台','电子设备',6800.0000,13.00),
 (2004,'MAT004','显示器','27 寸 2K  IPS','台','电子设备',1200.0000,13.00),
 (2005,'MAT005','线性导轨','HGR20-1000mm','米','机械配件',185.0000,13.00),
 (2006,'MAT006','伺服电机','750W 带刹车','台','机械配件',1450.0000,13.00);
```

## 6. 与缓存的一致性约定

| 表 | 是否缓存 | 写操作后的缓存动作 |
| --- | --- | --- |
| `sys_user` / `sys_role` / `sys_user_role` | 是（列表 30min） | 清 `po:cache:user:list`、相关 `po:session:{id}:roles` |
| `base_supplier` | 是（30min） | 清 `po:cache:supplier:{id}`、`po:cache:supplier:list` |
| `base_material` | 是（30min） | 清 `po:cache:material:{id}`、`po:cache:material:list` |
| `sys_dict` | 是（2h） | 清 `po:cache:dict:{type}` |
| `po_order` / `po_order_item` | **否** | 清 `po:report:*`、`po:badge:*` |
| `po_approval_task` / `po_approval_log` | **否** | 清 `po:badge:todo:*`、`po:report:*` |
