package com.yonerp.po.ap.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonerp.po.ap.entity.PoMatchRule;
import com.yonerp.po.ap.mapper.MatchRuleMapper;
import com.yonerp.po.ap.service.MatchRuleService;
import com.yonerp.po.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 匹配规则配置服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchRuleServiceImpl implements MatchRuleService {

    private final MatchRuleMapper matchRuleMapper;

    @Override
    public List<PoMatchRule> list() {
        return matchRuleMapper.selectList(new LambdaQueryWrapper<PoMatchRule>()
                .eq(PoMatchRule::getDeleted, 0)
                .orderByAsc(PoMatchRule::getScopeType)
                .orderByAsc(PoMatchRule::getRuleCode));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoMatchRule create(PoMatchRule rule) {
        validate(rule);
        long count = matchRuleMapper.selectCount(new LambdaQueryWrapper<PoMatchRule>()
                .eq(PoMatchRule::getRuleCode, rule.getRuleCode()));
        if (count > 0) {
            throw BizException.param("规则编码已存在：" + rule.getRuleCode());
        }
        rule.setId(null);
        rule.setDeleted(0);
        if (rule.getEnabled() == null) {
            rule.setEnabled(1);
        }
        matchRuleMapper.insert(rule);
        log.info("新增匹配规则：code={}, scope={}", rule.getRuleCode(), rule.getScopeType());
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PoMatchRule update(Long id, PoMatchRule rule) {
        PoMatchRule exist = matchRuleMapper.selectById(id);
        if (exist == null || Integer.valueOf(1).equals(exist.getDeleted())) {
            throw BizException.notFound("匹配规则不存在");
        }
        validate(rule);
        rule.setId(id);
        rule.setRuleCode(exist.getRuleCode());
        matchRuleMapper.updateById(rule);
        log.info("更新匹配规则：code={}, 容差=金额{}%@ 数量{}%", exist.getRuleCode(),
                rule.getAmountTolerance(), rule.getQtyTolerance());
        return matchRuleMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        PoMatchRule exist = matchRuleMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("匹配规则不存在");
        }
        if ("GLOBAL".equals(exist.getScopeType()) && "DEFAULT".equals(exist.getRuleCode())) {
            throw BizException.statusNotAllow("全局默认规则不允许删除");
        }
        exist.setDeleted(1);
        matchRuleMapper.updateById(exist);
        log.info("删除匹配规则：code={}", exist.getRuleCode());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, boolean enabled) {
        PoMatchRule exist = matchRuleMapper.selectById(id);
        if (exist == null) {
            throw BizException.notFound("匹配规则不存在");
        }
        exist.setEnabled(enabled ? 1 : 0);
        matchRuleMapper.updateById(exist);
        log.info("切换匹配规则状态：code={}, enabled={}", exist.getRuleCode(), enabled);
    }

    @Override
    public PoMatchRule resolve(Long supplierId) {
        return resolve(supplierId, null, null);
    }

    @Override
    public PoMatchRule resolve(Long supplierId, String materialCategory, String currency) {
        // 与 MatchEngine 一致的四级回退：供应商 → 物料分类 → 币种 → 全局
        if (supplierId != null) {
            PoMatchRule supplierRule = matchRuleMapper.selectOne(new LambdaQueryWrapper<PoMatchRule>()
                    .eq(PoMatchRule::getScopeType, "SUPPLIER")
                    .eq(PoMatchRule::getScopeValue, String.valueOf(supplierId))
                    .eq(PoMatchRule::getEnabled, 1)
                    .eq(PoMatchRule::getDeleted, 0));
            if (supplierRule != null) {
                return supplierRule;
            }
        }
        if (materialCategory != null && !materialCategory.isBlank()) {
            PoMatchRule categoryRule = matchRuleMapper.selectOne(new LambdaQueryWrapper<PoMatchRule>()
                    .eq(PoMatchRule::getScopeType, "MATERIAL_CATEGORY")
                    .eq(PoMatchRule::getScopeValue, materialCategory)
                    .eq(PoMatchRule::getEnabled, 1)
                    .eq(PoMatchRule::getDeleted, 0));
            if (categoryRule != null) {
                return categoryRule;
            }
        }
        if (currency != null && !currency.isBlank()) {
            PoMatchRule currencyRule = matchRuleMapper.selectOne(new LambdaQueryWrapper<PoMatchRule>()
                    .eq(PoMatchRule::getScopeType, "CURRENCY")
                    .eq(PoMatchRule::getScopeValue, currency)
                    .eq(PoMatchRule::getEnabled, 1)
                    .eq(PoMatchRule::getDeleted, 0));
            if (currencyRule != null) {
                return currencyRule;
            }
        }
        PoMatchRule global = matchRuleMapper.selectOne(new LambdaQueryWrapper<PoMatchRule>()
                .eq(PoMatchRule::getScopeType, "GLOBAL")
                .eq(PoMatchRule::getEnabled, 1)
                .eq(PoMatchRule::getDeleted, 0)
                .last("LIMIT 1"));
        if (global != null) {
            return global;
        }
        PoMatchRule fallback = new PoMatchRule();
        fallback.setRuleCode("DEFAULT");
        fallback.setRuleName("内置兜底规则");
        fallback.setAmountTolerance(new BigDecimal("1.00"));
        fallback.setAmountToleranceUnit("PERCENT");
        fallback.setQtyTolerance(BigDecimal.ZERO);
        fallback.setQtyToleranceUnit("PERCENT");
        return fallback;
    }

    private void validate(PoMatchRule rule) {
        if (rule.getRuleCode() == null || rule.getRuleCode().isBlank()) {
            throw BizException.param("规则编码不能为空");
        }
        if (rule.getRuleName() == null || rule.getRuleName().isBlank()) {
            throw BizException.param("规则名称不能为空");
        }
        if (rule.getScopeType() == null || rule.getScopeType().isBlank()) {
            throw BizException.param("适用范围不能为空");
        }
        if (!"GLOBAL".equals(rule.getScopeType())
                && (rule.getScopeValue() == null || rule.getScopeValue().isBlank())) {
            throw BizException.param("非全局规则必须填写适用范围值");
        }
    }
}
