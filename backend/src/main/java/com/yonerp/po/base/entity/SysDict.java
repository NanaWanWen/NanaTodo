package com.yonerp.po.base.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 基础字典
 */
@Data
@TableName("sys_dict")
public class SysDict {

    private Long id;

    private String dictType;

    private String dictCode;

    private String dictName;

    private String dictValue;

    private Integer sort;

    private String remark;

    private Integer deleted;
}
