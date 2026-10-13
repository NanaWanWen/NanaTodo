package com.yonerp.po.base.service;

import com.yonerp.po.base.vo.DictVO;

import java.util.List;

/**
 * 字典服务
 */
public interface DictService {

    /**
     * 按类型查询字典项（走缓存，2 小时）
     */
    List<DictVO> list(String type);

    /**
     * 清除指定类型的字典缓存
     */
    void evict(String type);
}
