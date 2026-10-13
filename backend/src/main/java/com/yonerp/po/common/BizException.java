package com.yonerp.po.common;

import lombok.Getter;

/**
 * 业务异常：所有可预期的业务/权限/并发错误均抛出该异常，由全局处理器转换为统一响应
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    private final int httpStatus;

    public BizException(int code, String message) {
        this(code, message, 400);
    }

    public BizException(int code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public static BizException param(String message) {
        return new BizException(ErrorCode.PARAM_ERROR, message, 400);
    }

    public static BizException notFound(String message) {
        return new BizException(ErrorCode.NOT_FOUND, message, 404);
    }

    public static BizException statusNotAllow(String message) {
        return new BizException(ErrorCode.STATUS_NOT_ALLOW, message, 400);
    }

    public static BizException unauthorized() {
        return new BizException(ErrorCode.UNAUTHORIZED, "会话已失效，请重新选择用户登录", 401);
    }

    public static BizException forbiddenRole() {
        return new BizException(ErrorCode.FORBIDDEN_ROLE, "当前角色无此操作权限", 403);
    }

    public static BizException forbiddenData() {
        return new BizException(ErrorCode.FORBIDDEN_DATA, "无权访问他人数据", 403);
    }

    public static BizException duplicateSubmit() {
        return new BizException(ErrorCode.DUPLICATE_SUBMIT, "操作正在处理，请勿重复提交", 409);
    }

    public static BizException concurrentConflict() {
        return new BizException(ErrorCode.CONCURRENT_CONFLICT, "单据已被他人变更，请刷新后重试", 409);
    }

    public static BizException lockFailed() {
        return new BizException(ErrorCode.LOCK_FAILED, "该单据正在被处理，请稍后重试", 409);
    }

    public static BizException rateLimited() {
        return new BizException(ErrorCode.RATE_LIMITED, "请求过于频繁，请稍后再试", 429);
    }
}
