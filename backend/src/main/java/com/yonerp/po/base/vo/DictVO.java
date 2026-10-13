package com.yonerp.po.base.vo;

import lombok.Data;

/**
 * 字典视图
 */
@Data
public class DictVO {

    private String dictCode;

    private String dictName;

    private String dictValue;

    private Integer sort;
}
