package com.yonerp.po.base.service;

import com.yonerp.po.base.dto.MaterialRequest;
import com.yonerp.po.base.vo.MaterialVO;

import java.util.List;

/**
 * 物料服务
 */
public interface MaterialService {

    /**
     * 列表（无筛选条件时走缓存）
     */
    List<MaterialVO> list(String keyword, String category);

    /**
     * 详情（走缓存）
     */
    MaterialVO get(Long id);

    /**
     * 新增或修改
     */
    Long save(MaterialRequest request);

    /**
     * 删除（被订单明细引用时不允许删除）
     */
    void delete(Long id);
}
