package com.yonerp.po.order.controller;

import com.yonerp.po.common.PageResult;
import com.yonerp.po.common.Result;
import com.yonerp.po.common.enums.RoleCode;
import com.yonerp.po.order.dto.OrderItemRequest;
import com.yonerp.po.order.dto.OrderQuery;
import com.yonerp.po.order.dto.OrderSaveRequest;
import com.yonerp.po.order.service.OrderService;
import com.yonerp.po.order.vo.OrderDetailVO;
import com.yonerp.po.order.vo.OrderListVO;
import com.yonerp.po.security.RequireRole;
import jakarta.validation.Valid;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 采购订单接口
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public Result<PageResult<OrderListVO>> page(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long supplierId,
                                                @RequestParam(required = false) Long buyerId,
                                                @RequestParam(required = false) Long deptId,
                                                @RequestParam(required = false) Long materialId,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate startDate,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate endDate,
                                                @RequestParam(defaultValue = "1") long pageNo,
                                                @RequestParam(defaultValue = "20") long pageSize) {
        OrderQuery query = new OrderQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setSupplierId(supplierId);
        query.setBuyerId(buyerId);
        query.setDeptId(deptId);
        query.setMaterialId(materialId);
        query.setStartDate(startDate);
        query.setEndDate(endDate);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        return Result.ok(orderService.page(query));
    }

    @GetMapping("/{id}")
    public Result<OrderDetailVO> detail(@PathVariable Long id) {
        return Result.ok(orderService.detail(id));
    }

    @PostMapping
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Long> create(@Valid @RequestBody OrderSaveRequest request) {
        return Result.ok(orderService.create(request));
    }

    @PutMapping("/{id}")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody OrderSaveRequest request) {
        orderService.update(id, request);
        return Result.ok();
    }

    @PostMapping("/{id}/adjust-price")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> adjustPrice(@PathVariable Long id, @Valid @RequestBody List<OrderItemRequest> items) {
        orderService.adjustPrice(id, items);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> delete(@PathVariable Long id) {
        orderService.delete(id);
        return Result.ok();
    }

    @DeleteMapping("/batch")
    @RequireRole({RoleCode.BUYER, RoleCode.ADMIN})
    public Result<Void> deleteBatch(@RequestBody BatchDeleteRequest request) {
        orderService.deleteBatch(request.getIds());
        return Result.ok();
    }

    @Data
    public static class BatchDeleteRequest {

        private List<Long> ids;
    }
}
