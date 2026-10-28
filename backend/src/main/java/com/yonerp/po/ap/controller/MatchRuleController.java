package com.yonerp.po.ap.controller;

import com.yonerp.po.ap.entity.PoMatchRule;
import com.yonerp.po.ap.service.MatchRuleService;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 匹配规则配置接口
 */
@RestController
@RequestMapping("/api/ap/match-rules")
@RequiredArgsConstructor
public class MatchRuleController {

    private final MatchRuleService matchRuleService;

    @GetMapping
    public Result<List<PoMatchRule>> list() {
        return Result.ok(matchRuleService.list());
    }

    @PostMapping
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<PoMatchRule> create(@RequestBody PoMatchRule rule) {
        return Result.ok(matchRuleService.create(rule));
    }

    @PutMapping("/{id}")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<PoMatchRule> update(@PathVariable Long id, @RequestBody PoMatchRule rule) {
        return Result.ok(matchRuleService.update(id, rule));
    }

    @DeleteMapping("/{id}")
    @RequireRole({RoleCode.ADMIN})
    public Result<Void> delete(@PathVariable Long id) {
        matchRuleService.delete(id);
        return Result.ok();
    }

    @PatchMapping("/{id}/toggle")
    @RequireRole({RoleCode.ADMIN, RoleCode.PURCHASE_MANAGER})
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enabled) {
        matchRuleService.toggle(id, enabled);
        return Result.ok();
    }
}
