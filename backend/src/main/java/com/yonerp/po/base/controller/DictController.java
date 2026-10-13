package com.yonerp.po.base.controller;

import com.yonerp.po.base.service.DictService;
import com.yonerp.po.base.vo.DictVO;
import com.yonerp.po.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 字典接口
 */
@RestController
@RequestMapping("/api/dict")
@RequiredArgsConstructor
public class DictController {

    private final DictService dictService;

    @GetMapping("/{type}")
    public Result<List<DictVO>> list(@PathVariable String type) {
        return Result.ok(dictService.list(type));
    }
}
