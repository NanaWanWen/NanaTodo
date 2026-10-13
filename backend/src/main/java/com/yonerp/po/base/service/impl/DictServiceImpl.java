package com.yonerp.po.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yonerp.po.base.entity.SysDict;
import com.yonerp.po.base.mapper.SysDictMapper;
import com.yonerp.po.base.service.DictService;
import com.yonerp.po.base.vo.DictVO;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.RedisKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 字典服务实现：热点数据，缓存 2 小时并在启动时预热
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DictServiceImpl implements DictService {

    private static final TypeReference<List<DictVO>> LIST_TYPE = new TypeReference<>() {
    };

    /** 启动时预热的字典类型 */
    public static final List<String> WARM_TYPES =
            List.of("order_status", "approval_node", "currency", "unit", "dept");

    private final SysDictMapper dictMapper;

    private final CacheService cacheService;

    @Override
    public List<DictVO> list(String type) {
        if (!StringUtils.hasText(type)) {
            return List.of();
        }
        List<DictVO> cached = cacheService.get(RedisKeys.cacheDict(type), LIST_TYPE).orElse(null);
        if (cached != null) {
            return cached;
        }
        List<SysDict> entities = dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .orderByAsc(SysDict::getSort));
        List<DictVO> result = new ArrayList<>();
        for (SysDict entity : entities) {
            DictVO vo = new DictVO();
            vo.setDictCode(entity.getDictCode());
            vo.setDictName(entity.getDictName());
            vo.setDictValue(entity.getDictValue());
            vo.setSort(entity.getSort());
            result.add(vo);
        }
        cacheService.set(RedisKeys.cacheDict(type), result, Duration.ofHours(2), 120);
        return result;
    }

    @Override
    public void evict(String type) {
        cacheService.delete(RedisKeys.cacheDict(type));
    }

    /**
     * 预热热点字典，避免冷启动击穿
     */
    public void warmUp() {
        for (String type : WARM_TYPES) {
            List<DictVO> items = list(type);
            log.info("预热字典：type={}, 数量={}", type, items.size());
        }
    }
}
