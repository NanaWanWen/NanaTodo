package com.yonerp.po.tplus.controller;

import com.yonerp.po.common.Result;
import com.yonerp.po.tplus.service.TplusSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** T+ 只读同步预览接口。只提供 GET，不提供写入 T+ 或本地业务数据的接口。 */
@RestController
@RequestMapping("/api/tplus")
@RequiredArgsConstructor
public class TplusSyncController {

    private final TplusSyncService syncService;

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.ok(syncService.preview());
    }
}
