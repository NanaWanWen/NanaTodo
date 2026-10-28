package com.yonerp.po.ap.service;

import com.yonerp.po.ap.entity.PoMatchRule;

import java.util.List;

/**
 * 匹配规则配置服务
 */
public interface MatchRuleService {

    List<PoMatchRule> list();

    PoMatchRule create(PoMatchRule rule);

    PoMatchRule update(Long id, PoMatchRule rule);

    void delete(Long id);

    void toggle(Long id, boolean enabled);

    /**
     * 按供应商解析生效规则（供应商优先，回退全局）
     */
    PoMatchRule resolve(Long supplierId);

    /**
     * 按供应商 + 物料分类 + 币种解析生效规则（四级回退：供应商 → 物料分类 → 币种 → 全局）
     */
    PoMatchRule resolve(Long supplierId, String materialCategory, String currency);
}
