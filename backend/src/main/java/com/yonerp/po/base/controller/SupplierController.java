package com.yonerp.po.base.controller;

import com.yonerp.po.base.dto.SupplierRequest;
import com.yonerp.po.base.service.SupplierService;
import com.yonerp.po.base.vo.SupplierVO;
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
 * 供应商接口：所有人可读，仅管理员可写
 */
@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @GetMapping
    public Result<List<SupplierVO>> list(@RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status) {
        return Result.ok(supplierService.list(keyword, status));
    }

    @GetMapping("/{id}")
    public Result<SupplierVO> detail(@PathVariable Long id) {
        return Result.ok(supplierService.get(id));
    }

    @PostMapping
    @RequireRole(RoleCode.ADMIN)
    public Result<Long> create(@Valid @RequestBody SupplierRequest request) {
        request.setId(null);
        return Result.ok(supplierService.save(request));
    }

    @PutMapping("/{id}")
    @RequireRole(RoleCode.ADMIN)
    public Result<Long> update(@PathVariable Long id, @Valid @RequestBody SupplierRequest request) {
        request.setId(id);
        return Result.ok(supplierService.save(request));
    }

    @DeleteMapping("/{id}")
    @RequireRole(RoleCode.ADMIN)
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok();
    }
}
