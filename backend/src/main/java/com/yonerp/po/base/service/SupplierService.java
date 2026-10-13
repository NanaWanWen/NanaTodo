package com.yonerp.po.base.service;

import com.yonerp.po.base.dto.SupplierRequest;
import com.yonerp.po.base.vo.SupplierVO;

import java.util.List;

/**
 * 供应商服务
 */
public interface SupplierService {

    /**
     * 列表（无筛选条件时走缓存）
     */
    List<SupplierVO> list(String keyword, Integer status);

    /**
     * 详情（走缓存）
     */
    SupplierVO get(Long id);

    /**
     * 新增或修改
     */
    Long save(SupplierRequest request);

    /**
     * 删除（被订单引用时不允许删除）
     */
    void delete(Long id);
}
