package com.yonerp.po.base.controller;

import com.yonerp.po.base.dto.MaterialRequest;
import com.yonerp.po.base.service.MaterialService;
import com.yonerp.po.base.vo.MaterialVO;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 物料接口：所有人可读，仅管理员可写
 */
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping
    public Result<List<MaterialVO>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) String category) {
        return Result.ok(materialService.list(keyword, category));
    }

    @GetMapping("/{id}")
    public Result<MaterialVO> detail(@PathVariable Long id) {
        return Result.ok(materialService.get(id));
    }

    @PostMapping
    @RequireRole(RoleCode.ADMIN)
    public Result<Long> create(@Valid @RequestBody MaterialRequest request) {
        request.setId(null);
        return Result.ok(materialService.save(request));
    }

    @PutMapping("/{id}")
    @RequireRole(RoleCode.ADMIN)
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody MaterialRequest request) {
        request.setId(id);
        return Result.ok(materialService.save(request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleCode.ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        materialService.delete(id);
        return Result.ok();
    }
}
