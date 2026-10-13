package com.yonerp.po.inventory.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存
 */
@Data
@TableName("po_inventory")
public class PoInventory {

    private Long id;

    /** 物料ID */
    private Long materialId;

    /** 物料编码(冗余) */
    private String materialCode;

    /** 物料名称(冗余) */
    private String materialName;

    /** 仓库 */
    private String warehouse;

    /** 当前库存 */
    private BigDecimal quantity;

    /** 安全库存 */
    private BigDecimal safetyStock;

    /** 最后更新时间 */
    private LocalDateTime lastUpdateTime;

    /** 乐观锁 */
    @Version
    private Integer version;

    private Long createBy;

    private LocalDateTime createTime;

    private Long updateBy;

    private LocalDateTime updateTime;

    private Integer deleted;
}
