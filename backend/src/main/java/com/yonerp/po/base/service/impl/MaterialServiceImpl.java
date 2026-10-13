package com.yonerp.po.base.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yonerp.po.base.dto.MaterialRequest;
import com.yonerp.po.base.entity.Material;
import com.yonerp.po.base.mapper.MaterialMapper;
import com.yonerp.po.base.service.MaterialService;
import com.yonerp.po.base.vo.MaterialVO;
import com.yonerp.po.common.BizException;
import com.yonerp.po.common.CacheService;
import com.yonerp.po.common.RedisKeys;
import com.yonerp.po.order.entity.PoOrderItem;
import com.yonerp.po.order.mapper.PoOrderItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 物料服务实现：读走缓存，写后清除缓存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MaterialServiceImpl implements MaterialService {

    private static final TypeReference<List<MaterialVO>> LIST_TYPE = new TypeReference<>() {
    };

    private final MaterialMapper materialMapper;

    private final PoOrderItemMapper orderItemMapper;

    private final CacheService cacheService;

    @Override
    public List<MaterialVO> list(String keyword, String category) {
        boolean useCache = !StringUtils.hasText(keyword) && !StringUtils.hasText(category);
        if (useCache) {
            List<MaterialVO> cached = cacheService.get(RedisKeys.cacheMaterialList(), LIST_TYPE).orElse(null);
            if (cached != null) {
                return cached;
            }
        }
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(item -> item.like(Material::getMaterialCode, keyword)
                    .or().like(Material::getMaterialName, keyword));
        }
        if (StringUtils.hasText(category)) {
            wrapper.eq(Material::getCategory, category);
        }
        wrapper.orderByAsc(Material::getId);
        List<MaterialVO> result = new ArrayList<>();
        for (Material entity : materialMapper.selectList(wrapper)) {
            result.add(toVO(entity));
        }
        if (useCache) {
            cacheService.set(RedisKeys.cacheMaterialList(), result, Duration.ofMinutes(30), 300);
        }
        return result;
    }

    @Override
    public MaterialVO get(Long id) {
        MaterialVO cached = cacheService.get(RedisKeys.cacheMaterial(id), MaterialVO.class).orElse(null);
        if (cached != null) {
            return cached;
        }
        Material entity = materialMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("物料不存在");
        }
        MaterialVO vo = toVO(entity);
        cacheService.set(RedisKeys.cacheMaterial(id), vo, Duration.ofMinutes(30), 300);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(MaterialRequest request) {
        checkCodeUnique(request);
        Material entity = new Material();
        entity.setId(request.getId());
        entity.setMaterialCode(request.getMaterialCode().trim());
        entity.setMaterialName(request.getMaterialName().trim());
        entity.setSpec(request.getSpec());
        entity.setUnit(request.getUnit().trim());
        entity.setCategory(request.getCategory());
        entity.setRefPrice(request.getRefPrice());
        entity.setTaxRate(request.getTaxRate() == null ? new BigDecimal("13.00") : request.getTaxRate());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());

        if (entity.getId() == null) {
            materialMapper.insert(entity);
        } else {
            Material exist = materialMapper.selectById(entity.getId());
            if (exist == null) {
                throw BizException.notFound("物料不存在");
            }
            if (materialMapper.updateById(entity) == 0) {
                throw BizException.concurrentConflict();
            }
        }
        evict(entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Material entity = materialMapper.selectById(id);
        if (entity == null) {
            throw BizException.notFound("物料不存在");
        }
        Long used = orderItemMapper.selectCount(new LambdaQueryWrapper<PoOrderItem>()
                .eq(PoOrderItem::getMaterialId, id));
        if (used != null && used > 0) {
            throw new BizException(2004, "该物料已被 " + used + " 条订单明细引用，不可删除，请先停用");
        }
        materialMapper.deleteById(id);
        evict(id);
        log.info("删除物料：id={}, code={}", id, entity.getMaterialCode());
    }

    private void checkCodeUnique(MaterialRequest request) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<Material>()
                .eq(Material::getMaterialCode, request.getMaterialCode().trim());
        if (request.getId() != null) {
            wrapper.ne(Material::getId, request.getId());
        }
        if (materialMapper.selectCount(wrapper) > 0) {
            throw BizException.param("物料编码已存在：" + request.getMaterialCode());
        }
    }

    private void evict(Long id) {
        cacheService.delete(RedisKeys.cacheMaterialList());
        if (id != null) {
            cacheService.delete(RedisKeys.cacheMaterial(id));
        }
    }

    private MaterialVO toVO(Material entity) {
        MaterialVO vo = new MaterialVO();
        vo.setId(entity.getId());
        vo.setMaterialCode(entity.getMaterialCode());
        vo.setMaterialName(entity.getMaterialName());
        vo.setSpec(entity.getSpec());
        vo.setUnit(entity.getUnit());
        vo.setCategory(entity.getCategory());
        vo.setRefPrice(entity.getRefPrice());
        vo.setTaxRate(entity.getTaxRate());
        vo.setStatus(entity.getStatus());
        return vo;
    }
}
