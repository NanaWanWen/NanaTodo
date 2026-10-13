package com.yonerp.po.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yonerp.po.base.dto.SupplierRequest;
import com.yonerp.po.base.entity.Supplier;
import com.yonerp.po.base.mapper.SupplierMapper;
import com.yonerp.po.base.service.SupplierService;
import com.yonerp.po.base.vo.SupplierVO;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.order.entity.PoOrder;
import com.yonerp.po.order.mapper.PoOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 供应商服务实现：读走缓存，写后清除缓存（不更新缓存）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplierServiceImpl implements SupplierService {

    private static final TypeReference<List<SupplierVO>> LIST_TYPE = new TypeReference<>() {
    };

    private final SupplierMapper supplierMapper;

    private final PoOrderMapper orderMapper;

    private final CacheService cacheService;

    @Override
    public List<SupplierVO> list(String keyword, Integer status) {
        boolean useCache = !StringUtils.hasText(keyword) && status == null;
        if (useCache) {
            List<SupplierVO> cached = cacheService.get(RedisKeys.cacheSupplierList(), LIST_TYPE).orElse(null);
            if (cached != null) {
                return cached;
            }
        }
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(item -> item.like(Supplier::getSupplierCode, keyword)
                    .or().like(Supplier::getSupplierName, keyword));
        }
        if (status != null) {
            wrapper.eq(Supplier::getStatus, status);
        }
        wrapper.orderByAsc(Supplier::getId);
        List<SupplierVO> result = new ArrayList<>();
        for (Supplier entity : supplierMapper.selectList(wrapper)) {
            result.add(toVO(entity));
        }
        if (useCache) {
            cacheService.set(RedisKeys.cacheSupplierList(), result, Duration.ofMinutes(30), 300);
        }
        return result;
    }

    @Override
    public SupplierVO get(Long id) {
        SupplierVO cached = cacheService.get(RedisKeys.cacheSupplier(id), SupplierVO.class).orElse(null);
        if (cached != null) {
            return cached;
        }
        Supplier entity = supplierMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("供应商不存在");
        }
        SupplierVO vo = toVO(entity);
        cacheService.set(RedisKeys.cacheSupplier(id), vo, Duration.ofMinutes(30), 300);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(SupplierRequest request) {
        checkCodeUnique(request);
        Supplier entity = new Supplier();
        entity.setId(request.getId());
        entity.setSupplierCode(request.getSupplierCode().trim());
        entity.setSupplierName(request.getSupplierName().trim());
        entity.setContactName(request.getContactName());
        entity.setContactPhone(request.getContactPhone());
        entity.setAddress(request.getAddress());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());

        if (entity.getId() == null) {
            supplierMapper.insert(entity);
        } else {
            Supplier exist = supplierMapper.selectById(entity.getId());
            if (exist == null) {
                throw BizException.notFound("供应商不存在");
            }
            if (supplierMapper.updateById(entity) == 0) {
                throw BizException.concurrentConflict();
            }
        }
        evict(entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Supplier entity = supplierMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("供应商不存在");
        }
        Long used = orderMapper.selectCount(new LambdaQueryWrapper<PoOrder>()
                .eq(PoOrder::getSupplierId, id));
        if (used != null && used > 0) {
            throw new BizException(2004, "该供应商已被 " + used + " 张采购订单引用，不可删除，请先停用");
        }
        supplierMapper.deleteById(id);
        evict(id);
        log.info("删除供应商：id={}, code={}", id, entity.getSupplierCode());
    }

    private void checkCodeUnique(SupplierRequest request) {
        LambdaQueryWrapper<Supplier> wrapper = new LambdaQueryWrapper<Supplier>()
                .eq(Supplier::getSupplierCode, request.getSupplierCode().trim());
        if (request.getId() != null) {
            wrapper.ne(Supplier::getId, request.getId());
        }
        if (supplierMapper.selectCount(wrapper) > 0) {
            throw BizException.param("供应商编码已存在：" + request.getSupplierCode());
        }
    }

    private void evict(Long id) {
        cacheService.delete(RedisKeys.cacheSupplierList());
        if (id != null) {
            cacheService.delete(RedisKeys.cacheSupplier(id));
        }
    }

    private SupplierVO toVO(Supplier entity) {
        SupplierVO vo = new SupplierVO();
        vo.setId(entity.getId());
        vo.setSupplierCode(entity.getSupplierCode());
        vo.setSupplierName(entity.getSupplierName());
        vo.setContactName(entity.getContactName());
        vo.setContactPhone(entity.getContactPhone());
        vo.setAddress(entity.getAddress());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
