package com.yonerp.po.common;

/**
 * 错误码定义（分段：1xxx 参数 2xxx 业务 3xxx 权限 4xxx 并发与限流 5xxx 系统）
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final int SUCCESS = 0;

    /** 参数校验失败 */
    public static final int PARAM_ERROR = 1001;
    /** 数据不存在 */
    public static final int NOT_FOUND = 1002;

    /** 订单状态不允许该操作 */
    public static final int STATUS_NOT_ALLOW = 2001;
    /** 明细为空或金额非法 */
    public static final int ITEM_INVALID = 2002;
    /** 意见/原因必填 */
    public static final int OPINION_REQUIRED = 2003;
    /** 基础数据被引用不可删除 */
    public static final int DATA_IN_USE = 2004;

    /** 未登录或会话失效 */
    public static final int UNAUTHORIZED = 3001;
    /** 角色无权限 */
    public static final int FORBIDDEN_ROLE = 3002;
    /** 数据越权 */
    public static final int FORBIDDEN_DATA = 3003;

    /** 重复提交 */
    public static final int DUPLICATE_SUBMIT = 4001;
    /** 并发冲突（乐观锁失败） */
    public static final int CONCURRENT_CONFLICT = 4002;
    /** 获取锁失败 */
    public static final int LOCK_FAILED = 4003;
    /** 请求过于频繁 */
    public static final int RATE_LIMITED = 4004;

    /** 系统异常 */
    public static final int SYSTEM_ERROR = 5001;
}
